package com.example.recon_agent.MODEL;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@AllArgsConstructor
public class ReconExceptionModel {

    private Long id;
    private String category;
    private String status;
    private String llmExplanation;
    private Instant createdAt;
    private List<TransactionSummaryDto> transactions;
}
