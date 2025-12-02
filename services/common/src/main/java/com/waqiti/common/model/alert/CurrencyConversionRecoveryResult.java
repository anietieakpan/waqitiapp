package com.waqiti.common.model.alert;

import com.waqiti.common.dlq.BaseDlqRecoveryResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Recovery result for currency conversion DLQ processing.
 * Tracks the outcome of attempting to recover failed currency conversion operations.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CurrencyConversionRecoveryResult extends BaseDlqRecoveryResult {

    private String conversionId;
    private String sourceCurrency;
    private String targetCurrency;
    private BigDecimal sourceAmount;
    private BigDecimal targetAmount;
    private BigDecimal exchangeRate;
    private String rateProvider;
    private boolean conversionSuccessful;
    private Instant conversionTimestamp;
    private String customerId;
    private String transactionReference;
    private BigDecimal appliedFee;
    private String feeType;
    private boolean rateStale;
    private Integer rateAgeSeconds;

    // Extended fields for consumer compatibility
    private boolean rateUnavailable;
    private String rateUnavailableReason;
    private boolean unsupportedPair;
    private String unsupportedPairReason;
    private boolean complianceHold;
    private java.util.List<String> complianceViolations;
    private String complianceRiskLevel;
    private boolean treasuryEscalated;
    private String treasuryEscalationId;
    private boolean emergencyProtocolExecuted;
    private java.util.List<String> emergencyMeasures;
    private boolean refundInitiated;
    private BigDecimal refundAmount;
    private java.time.Duration processingTime;
    private Instant rateTimestamp;

    @Override
    public String getRecoveryStatus() {
        if (isRecovered()) {
            return String.format("Currency conversion recovered: %s %s -> %s %s, rate=%.6f",
                    sourceAmount, sourceCurrency, targetAmount, targetCurrency, exchangeRate);
        } else {
            return String.format("Currency conversion recovery failed: %s->%s, reason=%s",
                    sourceCurrency, targetCurrency, getFailureReason());
        }
    }

    public boolean isRateExpired() {
        return rateStale || (rateAgeSeconds != null && rateAgeSeconds > 300); // 5 minutes
    }

    public boolean requiresRateRefresh() {
        return !conversionSuccessful && isRateExpired();
    }

    public boolean isCrossBorderConversion() {
        return !sourceCurrency.equals(targetCurrency);
    }

    public BigDecimal getTotalAmount() {
        if (targetAmount != null && appliedFee != null) {
            return targetAmount.add(appliedFee);
        }
        return targetAmount;
    }

    public boolean isHighValueConversion() {
        // Consider high value if source amount > 10000 or target amount > 10000
        return (sourceAmount != null && sourceAmount.compareTo(BigDecimal.valueOf(10000)) > 0) ||
               (targetAmount != null && targetAmount.compareTo(BigDecimal.valueOf(10000)) > 0);
    }

    // Compatibility methods

    public boolean isRateUnavailable() {
        return rateUnavailable;
    }

    public boolean isUnsupportedPair() {
        return unsupportedPair;
    }

    public boolean isComplianceHold() {
        return complianceHold;
    }

    public boolean isTreasuryEscalated() {
        return treasuryEscalated;
    }

    public boolean isEmergencyProtocolExecuted() {
        return emergencyProtocolExecuted;
    }

    public boolean isRefundInitiated() {
        return refundInitiated;
    }

    public boolean requiresManualReview() {
        return complianceHold || treasuryEscalated || unsupportedPair;
    }

    public boolean isCriticalFailure() {
        return emergencyProtocolExecuted ||
               (treasuryEscalated && isHighValueConversion());
    }
}
