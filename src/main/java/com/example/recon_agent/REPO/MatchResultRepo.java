package com.example.recon_agent.REPO;

import com.example.recon_agent.ENUM.MatchType;
import com.example.recon_agent.MODEL.MatchResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MatchResultRepo extends JpaRepository<MatchResult,Long> {
    List<MatchResult> findByMatchType(MatchType matchType);
}
