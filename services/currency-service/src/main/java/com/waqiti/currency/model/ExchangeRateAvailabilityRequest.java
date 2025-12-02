package com.waqiti.currency.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Exchange Rate Availability Request
 *
 * Represents a queued request waiting for exchange rate availability
 */
@Data
@Builder
public class ExchangeRateAvailabilityRequest {
    private String conversionId;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal amount;
    private String correlationId;
    private Priority priority;
    private int retryCount;
    private int maxRetries;
    private Instant nextRetryTime;
    private Instant createdAt;
    private String customerId;
    private String accountId;
}
