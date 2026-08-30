package com.example.recon_agent.MODEL;


import com.example.recon_agent.ENUM.ExceptionCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Created for every UNMATCHED MatchResult. Rule-based classification fills
 * in the category first; llmExplanation is only populated for the
 * UNEXPLAINED residual bucket that needs LLM reasoning.
 */
@Entity
@Table(name = "recon_exceptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "match_result_id", nullable = false)
    private MatchResult matchResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionCategory category;

    /**
     * Plain-English root-cause explanation from Claude - populated only
     * when rule-based classification lands on UNEXPLAINED. Left null for
     * the cases a fixed rule already accounts for, to avoid unnecessary
     * API calls.
     */
    @Column(columnDefinition = "TEXT")
    private String llmExplanation;

    @Column(nullable = false)
    @Builder.Default
    private String status = "OPEN";

    @Column(nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}