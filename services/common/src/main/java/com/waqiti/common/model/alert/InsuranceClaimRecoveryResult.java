package com.waqiti.common.model.alert;

import com.waqiti.common.dlq.BaseDlqRecoveryResult;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Recovery result for insurance claim DLQ processing.
 * Tracks the outcome of attempting to recover failed insurance claim operations.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class InsuranceClaimRecoveryResult extends BaseDlqRecoveryResult {

    private String claimId;
    private String policyNumber;
    private String policyHolderId;
    private String claimType; // AUTO, HEALTH, LIFE, PROPERTY, LIABILITY
    private BigDecimal claimAmount;
    private String claimStatus; // SUBMITTED, UNDER_REVIEW, APPROVED, DENIED, PAID
    private boolean claimProcessed;
    private String processingOutcome;
    private Instant claimSubmissionDate;
    private Instant claimProcessedDate;
    private String adjusterId;
    private String denialReason;
    private BigDecimal approvedAmount;
    private boolean fraudCheckPassed;
    private List<String> fraudFlags;
    private boolean requiresManualReview;
    private String regulatoryStatus;
    private Integer slaDaysRemaining;
    private boolean slaBreached;

    // Additional fields for DLQ consumer compatibility
    private String processingDetails;
    private String resolutionType;
    private String paymentMethod;
    private java.time.Duration processingTime;
    private List<String> fraudIndicators;
    private String violationType;
    private String reviewReason;
    private String failureReason;
    private Instant claimDeadline;
    private String claimComplexity;
    private String specialtyRequired;

    @Override
    public String getRecoveryStatus() {
        if (isRecovered()) {
            return String.format("Insurance claim recovered: claimId=%s, status=%s, amount=%s, processed=%s",
                    claimId, claimStatus, claimAmount, claimProcessed);
        } else {
            return String.format("Insurance claim recovery failed: claimId=%s, reason=%s",
                    claimId, getFailureReason());
        }
    }

    public boolean isHighValueClaim() {
        return claimAmount != null && claimAmount.compareTo(BigDecimal.valueOf(50000)) > 0;
    }

    public boolean isFraudSuspected() {
        return fraudFlags != null && !fraudFlags.isEmpty();
    }

    public boolean isApproved() {
        return "APPROVED".equalsIgnoreCase(claimStatus);
    }

    public boolean isDenied() {
        return "DENIED".equalsIgnoreCase(claimStatus);
    }

    public boolean isUnderReview() {
        return "UNDER_REVIEW".equalsIgnoreCase(claimStatus);
    }

    public boolean requiresEscalation() {
        return slaBreached || isHighValueClaim() || isFraudSuspected();
    }

    public boolean requiresRegulatoryReporting() {
        return "REPORTED".equalsIgnoreCase(regulatoryStatus) ||
               (isHighValueClaim() && isDenied());
    }

    public BigDecimal getVarianceAmount() {
        if (claimAmount != null && approvedAmount != null) {
            return claimAmount.subtract(approvedAmount);
        }
        return BigDecimal.ZERO;
    }

    public boolean hasPartialApproval() {
        return approvedAmount != null &&
               claimAmount != null &&
               approvedAmount.compareTo(claimAmount) < 0 &&
               approvedAmount.compareTo(BigDecimal.ZERO) > 0;
    }

    public String getClaimPriority() {
        if (slaBreached || (slaDaysRemaining != null && slaDaysRemaining < 2)) {
            return "P0";
        } else if (isHighValueClaim() || isFraudSuspected()) {
            return "P1";
        } else if (requiresManualReview) {
            return "P2";
        } else {
            return "P3";
        }
    }

    // Compatibility methods for DLQ consumer
    public String getPolicyId() {
        return policyNumber;
    }

    public List<String> getFraudIndicators() {
        return fraudIndicators != null ? fraudIndicators : fraudFlags;
    }

    public boolean isNearingClaimDeadline() {
        if (claimDeadline == null) return false;
        Instant now = Instant.now();
        Duration remaining = Duration.between(now, claimDeadline);
        return remaining.toDays() <= 2;
    }

    public boolean requiresRegulatoryBreach() {
        return violationType != null && (
                violationType.contains("LARGE_CLAIM") ||
                violationType.contains("STATE_REQUIREMENTS") ||
                violationType.contains("DOCUMENTATION")
        );
    }

    public boolean hasRegulatoryViolation() {
        return violationType != null && !violationType.isEmpty();
    }

    public boolean isSevereFraud() {
        List<String> indicators = getFraudIndicators();
        return indicators != null && indicators.size() >= 4;
    }

    public boolean hasFraudAnalysis() {
        return fraudFlags != null || fraudIndicators != null;
    }

    public double getFraudScore() {
        List<String> indicators = getFraudIndicators();
        if (indicators == null || indicators.isEmpty()) return 0.0;
        return Math.min(indicators.size() * 0.25, 1.0);
    }
}
