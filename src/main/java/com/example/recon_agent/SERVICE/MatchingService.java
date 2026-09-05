package com.example.recon_agent.SERVICE;

import com.example.recon_agent.AI_LAYER.ClaudeApiClient;
import com.example.recon_agent.ENUM.ExceptionCategory;
import com.example.recon_agent.ENUM.MatchType;
import com.example.recon_agent.ENUM.SourceSystem;
import com.example.recon_agent.MODEL.AuditLogEntry;
import com.example.recon_agent.MODEL.MatchResult;
import com.example.recon_agent.MODEL.ReconException;
import com.example.recon_agent.MODEL.Transaction;
import com.example.recon_agent.REPO.AuditLogEntryRepository;
import com.example.recon_agent.REPO.MatchResultRepo;
import com.example.recon_agent.REPO.ReconExceptionRepo;
import com.example.recon_agent.REPO.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * The core reconciliation logic, run as 4 ordered passes:
 *   1. Duplicate detection - must run first, removes ambiguous refs from the pool
 *   2. Exact match        - identical ref + identical amount across sources
 *   3. Fuzzy match         - amount/date tolerance scoring on what's left
 *   4. Residual/unmatched  - whatever remains gets classified MISSING or UNEXPLAINED
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchingService {

    private static final int DATE_TOLERANCE_DAYS = 3;
    private static final double FUZZY_HIGH_THRESHOLD = 0.85;
    private static final double FUZZY_LOW_THRESHOLD = 0.60;

    private final TransactionRepository transactionRepository;
    private final MatchResultRepo matchResultRepository;
    private final ReconExceptionRepo reconExceptionRepository;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final ClaudeApiClient claudeApiClient;

    /** Runs all 4 passes in the correct order. Returns a summary count per pass. */
    public Map<String, Integer> runFullPipeline() {
        Map<String, Integer> summary = new LinkedHashMap<>();
        summary.put("duplicates", runDuplicateDetectionPass());
        summary.put("exactMatches", runExactMatchPass());
        summary.put("fuzzyMatches", runFuzzyMatchPass());
        summary.put("residualUnmatched", runResidualPass());
        log.info("Matching pipeline complete: {}", summary);
        return summary;
    }

    // ---------- Pass 1: Duplicate Detection ----------

    /**
     * Groups ALL unconsumed transactions (across all 3 sources) by externalRef.
     * If any single source contributes more than one row for a ref, the
     * entire cross-source group for that ref is pulled out together - not
     * just the duplicated rows - so the "clean" rows from the other sources
     * don't leak into later passes and get misclassified as MISSING.
     */
    public int runDuplicateDetectionPass() {
        List<Transaction> unconsumed = transactionRepository.findByConsumedFalse();
        Map<String, List<Transaction>> byRef = indexByExternalRef(unconsumed);

        int duplicateGroups = 0;
        for (List<Transaction> group : byRef.values()) {
            Map<SourceSystem, Long> perSourceCounts = group.stream()
                    .collect(Collectors.groupingBy(Transaction::getSourceSystem, Collectors.counting()));

            boolean hasDuplicateWithinSource = perSourceCounts.values().stream().anyMatch(c -> c > 1);
            if (hasDuplicateWithinSource) {
                createDuplicateException(group);
                duplicateGroups++;
            }
        }
        log.info("Duplicate-detection pass complete: {} duplicate groups flagged", duplicateGroups);
        return duplicateGroups;
    }

    private void createDuplicateException(List<Transaction> group) {
        MatchResult matchResult = MatchResult.builder()
                .transactions(new ArrayList<>(group))
                .matchType(MatchType.UNMATCHED)
                .confidenceScore(0.0)
                .ruleApplied("DUPLICATE_DETECTED")
                .build();
        matchResultRepository.save(matchResult);

        for (Transaction txn : group) {
            markConsumedAndLog(txn, "UNMATCHED", "DUPLICATE_DETECTED", 0.0);
        }

        reconExceptionRepository.save(ReconException.builder()
                .matchResult(matchResult)
                .category(ExceptionCategory.DUPLICATE)
                .status("OPEN")
                .build());
    }

    // ---------- Pass 2: Exact Match ----------

    public int runExactMatchPass() {
        List<Transaction> razorpayTxns = unconsumedBySource(SourceSystem.RAZORPAY_SETTLEMENT);
        Map<String, List<Transaction>> bankIndex = indexByExternalRef(unconsumedBySource(SourceSystem.BANK_STATEMENT));
        Map<String, List<Transaction>> ledgerIndex = indexByExternalRef(unconsumedBySource(SourceSystem.MERCHANT_LEDGER));

        int exactMatchCount = 0;
        for (Transaction rzp : razorpayTxns) {
            if (rzp.isConsumed()) continue;

            List<Transaction> bankCandidates = bankIndex.get(rzp.getExternalRef());
            List<Transaction> ledgerCandidates = ledgerIndex.get(rzp.getExternalRef());

            if (bankCandidates == null || bankCandidates.size() != 1) continue;
            if (ledgerCandidates == null || ledgerCandidates.size() != 1) continue;

            Transaction bank = bankCandidates.get(0);
            Transaction ledger = ledgerCandidates.get(0);
            if (bank.isConsumed() || ledger.isConsumed()) continue;

            boolean amountsAgree = rzp.getAmount().compareTo(bank.getAmount()) == 0
                    && rzp.getAmount().compareTo(ledger.getAmount()) == 0;

            if (amountsAgree) {
                createMatch(List.of(rzp, bank, ledger), MatchType.EXACT, 1.0, "EXACT_REF_AND_AMOUNT");
                exactMatchCount++;
            }
        }
        log.info("Exact-match pass complete: {} matches created", exactMatchCount);
        return exactMatchCount;
    }

    // ---------- Pass 3: Fuzzy Match ----------

    public int runFuzzyMatchPass() {
        List<Transaction> razorpayTxns = unconsumedBySource(SourceSystem.RAZORPAY_SETTLEMENT);
        Map<String, Transaction> bankIndex = indexSingleByExternalRef(unconsumedBySource(SourceSystem.BANK_STATEMENT));
        Map<String, Transaction> ledgerIndex = indexSingleByExternalRef(unconsumedBySource(SourceSystem.MERCHANT_LEDGER));

        int fuzzyMatchCount = 0;
        for (Transaction rzp : razorpayTxns) {
            if (rzp.isConsumed()) continue;

            Transaction bank = bankIndex.get(rzp.getExternalRef());
            Transaction ledger = ledgerIndex.get(rzp.getExternalRef());

            // Both sides must be present - if either is missing, this is the
            // residual pass's job (MISSING classification), not fuzzy's.
            if (bank == null || ledger == null) continue;
            if (bank.isConsumed() || ledger.isConsumed()) continue;

            double scoreVsBank = closenessScore(rzp, bank);
            double scoreVsLedger = closenessScore(rzp, ledger);
            double confidence = Math.min(scoreVsBank, scoreVsLedger);

            MatchType tier = confidence >= FUZZY_HIGH_THRESHOLD ? MatchType.FUZZY_HIGH
                    : confidence >= FUZZY_LOW_THRESHOLD ? MatchType.FUZZY_LOW
                    : null;

            if (tier != null) {
                createMatch(List.of(rzp, bank, ledger), tier, confidence, "FUZZY_AMOUNT_DATE_TOLERANCE");
                fuzzyMatchCount++;
            }
            // else: leave unconsumed - falls through to the residual pass
        }
        log.info("Fuzzy-match pass complete: {} matches created", fuzzyMatchCount);
        return fuzzyMatchCount;
    }

    /** Weighted score: 0.7 amount closeness + 0.3 date closeness, each clamped to [0,1]. */
    private double closenessScore(Transaction a, Transaction b) {
        BigDecimal diff = a.getAmount().subtract(b.getAmount()).abs();
        double amountCloseness = a.getAmount().signum() == 0
                ? 0.0
                : Math.max(0.0, 1 - (diff.doubleValue() / a.getAmount().doubleValue()));

        long diffDays = Math.abs(ChronoUnit.DAYS.between(a.getTransactionDate(), b.getTransactionDate()));
        double dateCloseness = Math.max(0.0, 1 - ((double) diffDays / DATE_TOLERANCE_DAYS));

        return 0.7 * amountCloseness + 0.3 * dateCloseness;
    }

    // ---------- Pass 4: Residual / Unmatched ----------

    /**
     * Whatever's left unconsumed after passes 1-3 gets grouped by
     * externalRef FIRST (same principle as duplicate detection) - so all
     * remaining rows for the same real-world transaction become ONE
     * exception, not one per leftover row. Without this grouping, a single
     * missing-transaction event produces two separate exceptions (one
     * MISSING for the Razorpay row, one spurious UNEXPLAINED for whichever
     * side survived) - which misrepresents the true exception count.
     */
    public int runResidualPass() {
        List<Transaction> remaining = transactionRepository.findByConsumedFalse();
        Map<String, List<Transaction>> byRef = indexByExternalRef(remaining);
        int residualCount = 0;

        for (List<Transaction> group : byRef.values()) {
            ExceptionCategory category = classifyResidualGroup(group);
            String rule = category == ExceptionCategory.MISSING ? "NO_CANDIDATE_FOUND" : "UNRESOLVED_AFTER_ALL_PASSES";

            MatchResult matchResult = MatchResult.builder()
                    .transactions(new ArrayList<>(group))
                    .matchType(MatchType.UNMATCHED)
                    .confidenceScore(0.0)
                    .ruleApplied(rule)
                    .build();
            matchResultRepository.save(matchResult);

            for (Transaction txn : group) {
                markConsumedAndLog(txn, "UNMATCHED", rule, 0.0);
            }

            String llmExplanation = null;
            if (category == ExceptionCategory.UNEXPLAINED) {
                var explanation = claudeApiClient.explainException(buildExceptionContext(group));
                llmExplanation = explanation.getLikelyCause() + " | Recommendation: " + explanation.getRecommendation();
            }

            reconExceptionRepository.save(ReconException.builder()
                    .matchResult(matchResult)
                    .category(category)
                    .llmExplanation(llmExplanation)
                    .status("OPEN")
                    .build());

            residualCount++;
        }
        log.info("Residual pass complete: {} unmatched groups classified", residualCount);
        return residualCount;
    }

    /**
     * Classifies a whole group of leftover rows sharing one externalRef.
     * MISSING if Razorpay is present but Bank or Ledger is absent from the
     * group - the classic "didn't sync" case. Otherwise UNEXPLAINED, left
     * for Phase 2's LLM to reason about.
     */
    private ExceptionCategory classifyResidualGroup(List<Transaction> group) {
        boolean hasRazorpay = group.stream().anyMatch(t -> t.getSourceSystem() == SourceSystem.RAZORPAY_SETTLEMENT);
        boolean hasBank = group.stream().anyMatch(t -> t.getSourceSystem() == SourceSystem.BANK_STATEMENT);
        boolean hasLedger = group.stream().anyMatch(t -> t.getSourceSystem() == SourceSystem.MERCHANT_LEDGER);

        if (hasRazorpay && (!hasBank || !hasLedger)) {
            return ExceptionCategory.MISSING;
        }
        return ExceptionCategory.UNEXPLAINED;
    }

    // ---------- Shared helpers ----------

    private void createMatch(List<Transaction> group, MatchType type, double confidence, String rule) {
        MatchResult matchResult = MatchResult.builder()
                .transactions(new ArrayList<>(group))
                .matchType(type)
                .confidenceScore(confidence)
                .ruleApplied(rule)
                .build();
        matchResultRepository.save(matchResult);

        for (Transaction txn : group) {
            markConsumedAndLog(txn, "MATCHED", rule, confidence);
        }
    }

    private void markConsumedAndLog(Transaction txn, String decision, String rule, double confidence) {
        txn.setConsumed(true);
        transactionRepository.save(txn);

        auditLogEntryRepository.save(AuditLogEntry.builder()
                .timestamp(Instant.now())
                .transactionRef(txn.getExternalRef())
                .sourceSystem(txn.getSourceSystem())
                .decision(decision)
                .ruleFired(rule)
                .confidenceScore(confidence)
                .build());
    }

    private List<Transaction> unconsumedBySource(SourceSystem source) {
        return transactionRepository.findBySourceSystem(source).stream()
                .filter(t -> !t.isConsumed())
                .collect(Collectors.toList());
    }

    private Map<String, List<Transaction>> indexByExternalRef(List<Transaction> transactions) {
        Map<String, List<Transaction>> index = new HashMap<>();
        for (Transaction txn : transactions) {
            index.computeIfAbsent(txn.getExternalRef(), k -> new ArrayList<>()).add(txn);
        }
        return index;
    }

    private Map<String, Transaction> indexSingleByExternalRef(List<Transaction> transactions) {
        Map<String, Transaction> index = new HashMap<>();
        for (Transaction txn : transactions) {
            index.put(txn.getExternalRef(), txn); // safe: duplicates already removed by pass 1
        }
        return index;
    }


    private String buildExceptionContext(List<Transaction> group){
        StringBuilder sb=new StringBuilder();
        for(Transaction txn:group){
            sb.append(String.format("Source:%s,Ref:%s,Amount:%s,Date:%s%n",txn.getSourceSystem(),txn.getExternalRef(),txn.getAmount(),txn.getTransactionDate()));
        }
        return sb.toString();
    }
}