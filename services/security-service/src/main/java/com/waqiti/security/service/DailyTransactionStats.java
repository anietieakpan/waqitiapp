package com.waqiti.security.service;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data class for daily transaction statistics
 */
@Data
@Builder
public class DailyTransactionStats {
    private LocalDate date;
    private int transactionCount;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
}