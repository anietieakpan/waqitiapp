package com.waqiti.currency.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * Currency Conversion Recovery Result
 */
@Data
@Builder
public class CurrencyConversionRecoveryResult {
    private String conversionId;
    private String customerId;
    private String accountId;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal originalAmount;
    private BigDecimal convertedAmount;
    private BigDecimal exchangeRate;
    private String conversionStatus;
    private String conversionDetails;
    private String failureReason;
    private Instant conversionTime;
    private Duration processingTime;
    private boolean converted;
    private boolean exchangeRateUnavailable;
    private boolean currencyPairUnsupported;
    private boolean hasAccountUpdate;
    private boolean hasDebitedAmount;
    private String escalationId;
    private String correlationId;

    public boolean isConverted() {
        return converted;
    }

    public boolean isExchangeRateUnavailable() {
        return exchangeRateUnavailable;
    }

    public boolean isCurrencyPairUnsupported() {
        return currencyPairUnsupported;
    }

    public boolean hasAccountUpdate() {
        return hasAccountUpdate;
    }

    public boolean hasDebitedAmount() {
        return hasDebitedAmount;
    }
}
