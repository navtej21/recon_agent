package com.example.recon_agent.MODEL;

import com.example.recon_agent.ENUM.MatchType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The outcome of running one transaction (or group of candidates) through
 * the matching engine. Holds however many Transaction records ended up
 * linked together (2 or 3, depending on how many sources agreed) plus the
 * confidence tier and which rule produced the result.
 */
@Entity
@Table(name = "match_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany
    @JoinTable(
            name = "match_result_transactions",
            joinColumns = @JoinColumn(name = "match_result_id"),
            inverseJoinColumns = @JoinColumn(name = "transaction_id")
    )
    @Builder.Default
    private List<Transaction> transactions = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchType matchType;

    /**
     * 1.0 for EXACT; the weighted amount+date score (0.0-1.0) for fuzzy tiers.
     */
    @Column(nullable = false)
    private double confidenceScore;

    /**
     * Human-readable name of whichever rule fired - e.g. "EXACT_REF_AND_AMOUNT",
     * "FUZZY_AMOUNT_DATE_TOLERANCE" - surfaced in the audit trail.
     */
    @Column(nullable = false)
    private String ruleApplied;

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}