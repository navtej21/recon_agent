package com.example.recon_agent.REPO;

import com.example.recon_agent.ENUM.MatchType;
import com.example.recon_agent.MODEL.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchResultRepo extends JpaRepository<MatchResult,Long> {
    List<MatchResult> findByMatchType(MatchType matchType);

    @Query("SELECT mr FROM MatchResult mr JOIN mr.transactions t WHERE t.externalRef = :ref")
    List<MatchResult> findByTransactionRef(@Param("ref") String ref);
}
