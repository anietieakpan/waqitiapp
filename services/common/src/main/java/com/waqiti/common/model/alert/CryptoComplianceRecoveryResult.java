package com.waqiti.common.model.alert;

import com.waqiti.common.dlq.BaseDlqRecoveryResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Recovery result for crypto compliance DLQ processing.
 * Tracks the outcome of attempting to recover failed crypto compliance checks.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CryptoComplianceRecoveryResult extends BaseDlqRecoveryResult {

    private String transactionId;
    private String walletAddress;
    private String cryptoCurrency;
    private BigDecimal amount;
    private String complianceCheckType;
    private boolean compliancePassed;
    private List<String> violationFlags;
    private String riskScore;
    private String regulatoryStatus;
    private boolean requiresManualReview;
    private boolean transactionBlocked;
    private Instant checkCompletedTimestamp;
    private String blockchainNetwork;
    private String kycStatus;
    private boolean sanctionScreenPassed;

    @Override
    public String getRecoveryStatus() {
        if (isRecovered()) {
            return String.format("Crypto compliance recovered: txId=%s, passed=%s, blocked=%s",
                    transactionId, compliancePassed, transactionBlocked);
        } else {
            return String.format("Crypto compliance recovery failed: txId=%s, reason=%s",
                    transactionId, getFailureReason());
        }
    }

    public boolean isCriticalViolation() {
        return violationFlags != null &&
               (violationFlags.contains("SANCTIONS_HIT") ||
                violationFlags.contains("HIGH_RISK_JURISDICTION") ||
                violationFlags.contains("AML_ALERT"));
    }

    public boolean requiresRegulatoryReporting() {
        return isCriticalViolation() || "BLOCKED".equals(regulatoryStatus);
    }

    public boolean isHighRisk() {
        return "HIGH".equalsIgnoreCase(riskScore) || "CRITICAL".equalsIgnoreCase(riskScore);
    }
}
