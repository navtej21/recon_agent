package com.example.recon_agent.MODEL;

import com.example.recon_agent.ENUM.SourceSystem;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * One row per decision the matching engine makes, written as it runs.
 * This is what makes the pipeline auditable rather than a black box -
 * every transaction's fate (matched or not, which rule, what confidence)
 * is traceable after the fact. Deliberately denormalized (stores the ref
 * directly rather than joining) so the log reads cleanly on its own even
 * if the underlying Transaction/MatchResult rows are queried separately.
 */
@Entity
@Table(name = "audit_log_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String transactionRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceSystem sourceSystem;

    /**
     * "MATCHED" or "UNMATCHED" - the top-line outcome for this record.
     */
    @Column(nullable = false)
    private String decision;

    @Column(nullable = false)
    private String ruleFired;

    @Column(nullable = false)
    private double confidenceScore;
}