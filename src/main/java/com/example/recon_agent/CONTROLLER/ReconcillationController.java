package com.example.recon_agent.CONTROLLER;


import com.example.recon_agent.ENUM.ExceptionCategory;
import com.example.recon_agent.MODEL.*;
import com.example.recon_agent.REPO.AuditLogEntryRepository;
import com.example.recon_agent.REPO.MatchResultRepo;
import com.example.recon_agent.REPO.ReconExceptionRepo;
import com.example.recon_agent.REPO.TransactionRepository;
import com.example.recon_agent.SERVICE.MatchingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReconcillationController {

    private final TransactionRepository transactionRepository;
    private  final MatchResultRepo matchResultRepo;
    private final ReconExceptionRepo reconExceptionRepo;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final MatchingService matchingService;


    @GetMapping("/summary")
    public ReconciliationSummaryDto getSummary(){

        long totalTransactions=transactionRepository.count();
        var allMatches=matchResultRepo.findAll();

        Map<String, Long> matchesByType = allMatches.stream()
                .collect(Collectors.groupingBy(m -> m.getMatchType().name(), Collectors.counting()));

        Map<String, Long> exceptionsByCategory = reconExceptionRepo.findAll().stream()
                .collect(Collectors.groupingBy(e -> e.getCategory().name(), Collectors.counting()));

        long resolvedWithConfidence = matchesByType.getOrDefault("EXACT", 0L)
                + matchesByType.getOrDefault("FUZZY_HIGH", 0L)
                + matchesByType.getOrDefault("FUZZY_LOW", 0L);

        double matchRate = allMatches.isEmpty() ? 0.0
                : (resolvedWithConfidence * 100.0) / allMatches.size();

        return new ReconciliationSummaryDto((int)totalTransactions,allMatches.size(),matchesByType,exceptionsByCategory,matchRate);

    }



    @GetMapping("/exceptions")
    public List<ReconExceptionModel> getExceptions(
            @RequestParam(required = false) String category) {

        List<ReconException> exceptions = category != null
                ? reconExceptionRepo.findByCategory(ExceptionCategory.valueOf(category.toUpperCase()))
                : reconExceptionRepo.findAll();

        return exceptions.stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private ReconExceptionModel toDto(ReconException exception) {
        List<TransactionSummaryDto> txnDtos = exception.getMatchResult().getTransactions().stream()
                .map(t -> new TransactionSummaryDto(
                        t.getSourceSystem().name(), t.getExternalRef(), t.getAmount(), t.getTransactionDate()))
                .collect(Collectors.toList());

        return new ReconExceptionModel(
                exception.getId(),
                exception.getCategory().name(),
                exception.getStatus(),
                exception.getLlmExplanation(),
                exception.getCreatedAt(),
                txnDtos
        );
    }

    @GetMapping("/audit-trail/{ref}")
    public List<AuditLogEntry> getAuditTrail(@PathVariable String ref) {
        return auditLogEntryRepository.findByTransactionRefOrderByTimestampAsc(ref.trim().toUpperCase());
    }

    @PostMapping("/run")
    public Map<String, Integer> runReconciliation() {
        return matchingService.runFullPipeline();
    }
}
