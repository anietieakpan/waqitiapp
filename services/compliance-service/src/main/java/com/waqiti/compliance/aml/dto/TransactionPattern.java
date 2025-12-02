package com.waqiti.compliance.aml.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction pattern for AML analysis
 */
@Data
@Builder
public class TransactionPattern {
    private String userId;
    private int transactionCount;
    private BigDecimal totalAmount;
    private BigDecimal averageAmount;
    private BigDecimal maxAmount;
    private BigDecimal minAmount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private long durationMinutes;
}
