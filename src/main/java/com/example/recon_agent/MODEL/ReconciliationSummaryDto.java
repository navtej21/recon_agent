package com.example.recon_agent.MODEL;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class ReconciliationSummaryDto {

    private int totalTransactions;
    private int totalMatchResults;
    private Map<String,Long> matchesByType;
    private Map<String,Long> exceptionsByCategory;
    double matchRatePercent;
}
