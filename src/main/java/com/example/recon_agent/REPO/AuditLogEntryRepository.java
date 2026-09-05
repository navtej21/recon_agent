package com.example.recon_agent.REPO;

import com.example.recon_agent.MODEL.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, Long> {

    // Full audit history for one transaction ref, across every source it
    // appeared in - answers "what happened to UTR123456?" with no joins.

    List<AuditLogEntry> findByTransactionRefOrderByTimestampAsc(String transactionRef);
}