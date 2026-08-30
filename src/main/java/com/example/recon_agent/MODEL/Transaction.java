package com.example.recon_agent.MODEL;

import com.example.recon_agent.ENUM.SourceSystem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The common shape every raw record from all 3 sources normalizes into.
 * This is the single most important design decision in the project: it lets
 * the matching engine treat Razorpay/Bank/Ledger rows uniformly instead of
 * writing separate pairwise comparison logic for each source pair.
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceSystem sourceSystem;

    /**
     * The join key - UTR / transaction ID / order ID depending on source.
     * Normalized (trimmed, uppercased) at ingestion time before storage.
     */
    @Column(nullable = false)
    private String externalRef;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate transactionDate;

    /**
     * The original, un-normalized row as ingested - kept verbatim for
     * audit/debugging so nothing is lost in translation to the common model.
     */
    @Column(columnDefinition = "TEXT")
    private String rawRecord;

    /**
     * Set once this transaction has been consumed by a MatchResult, so the
     * matching engine's greedy pass knows not to reuse it as a candidate.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean consumed = false;
}