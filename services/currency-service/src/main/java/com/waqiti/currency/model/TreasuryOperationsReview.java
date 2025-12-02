package com.waqiti.currency.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Treasury Operations Review Entity
 *
 * Represents a failed currency conversion that requires treasury team review
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TreasuryOperationsReview {

    private String conversionId;
    private String customerId;
    private String sourceCurrency;
    private String targetCurrency;
    private String fromCurrency;  // Alias for sourceCurrency
    private String toCurrency;     // Alias for targetCurrency
    private BigDecimal amount;
    private String failureReason;
    private ReviewStatus status;
    private Priority priority;
    private Instant escalatedAt;
    private Instant reviewedAt;
    private Instant timestamp;
    private String assignee;
    private String assignedTo;     // Alias for assignee
    private String resolution;
    private String eventData;
    private String correlationId;
    private String sourceTopic;
    private String messageKey;
    private String messageValue;
    private boolean requiresFinancialAnalysis;
}
