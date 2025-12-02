package com.waqiti.currency.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Product Review Request
 *
 * Represents a request for product team to review currency pair support
 */
@Data
@Builder
public class ProductReviewRequest {
    private String conversionId;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal amount;
    private String correlationId;
    private ReviewType reviewType;
    private Priority priority;
    private String assignedTo;
    private boolean requiresEngineering;
    private String customerId;
    private int demandCount;
}
