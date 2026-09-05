package com.example.recon_agent.MODEL;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionSummaryDto(
        String sourceSystem,
        String externalRef,
        BigDecimal amount,
        LocalDate transactionDate
) {}