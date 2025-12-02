package com.waqiti.account.kafka.dlq;

import com.waqiti.account.entity.ManualReviewRecord;
import com.waqiti.account.entity.PermanentFailureRecord;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * Recovery decision for DLQ message processing
 *
 * <p>Encapsulates the classification result and recovery strategy
 * for a failed message.</p>
 *
 * @author Waqiti Platform Team
 * @since 1.0.0
 */
@Getter
@Builder
public class RecoveryDecision {

    /**
     * Recovery strategy to apply
     */
    private RecoveryStrategy strategy;

    /**
     * Human-readable reason for this decision
     */
    private String reason;

    /**
     * Optional recovery action to attempt (for RETRY strategy)
     */
    private String recoveryAction;

    /**
     * Priority for manual review (required if strategy = MANUAL_REVIEW)
     */
    private ManualReviewRecord.ReviewPriority priority;

    /**
     * Failure category (required if strategy = PERMANENT_FAILURE)
     */
    private PermanentFailureRecord.FailureCategory failureCategory;

    /**
     * Business impact assessment (for PERMANENT_FAILURE)
     */
    private PermanentFailureRecord.BusinessImpact businessImpact;

    /**
     * Impact description (for PERMANENT_FAILURE)
     */
    private String impactDescription;

    /**
     * Financial impact amount (for PERMANENT_FAILURE)
     */
    private BigDecimal financialImpact;

    /**
     * Recovery strategy enumeration
     */
    public enum RecoveryStrategy {
        /**
         * Retry with exponential backoff (max 3 attempts)
         */
        RETRY,

        /**
         * Queue for manual operations team review
         */
        MANUAL_REVIEW,

        /**
         * Record as permanent failure (7-year audit retention)
         */
        PERMANENT_FAILURE,

        /**
         * Discard message (use sparingly)
         */
        DISCARD
    }

    // ========== FACTORY METHODS FOR COMMON SCENARIOS ==========

    /**
     * Create retry decision for transient errors
     */
    public static RecoveryDecision retry(String reason) {
        return RetryDecisionBuilder.builder()
            .strategy(RecoveryStrategy.RETRY)
            .reason(reason)
            .build();
    }

    /**
     * Create retry decision with recovery action
     */
    public static RecoveryDecision retry(String reason, String recoveryAction) {
        return RecoveryDecision.builder()
            .strategy(RecoveryStrategy.RETRY)
            .reason(reason)
            .recoveryAction(recoveryAction)
            .build();
    }

    /**
     * Create manual review decision with priority
     */
    public static RecoveryDecision manualReview(
            ManualReviewRecord.ReviewPriority priority, String reason) {
        return RecoveryDecision.builder()
            .strategy(RecoveryStrategy.MANUAL_REVIEW)
            .priority(priority)
            .reason(reason)
            .build();
    }

    /**
     * Create critical manual review (15min SLA)
     */
    public static RecoveryDecision criticalReview(String reason) {
        return manualReview(ManualReviewRecord.ReviewPriority.CRITICAL, reason);
    }

    /**
     * Create high priority manual review (1hr SLA)
     */
    public static RecoveryDecision highPriorityReview(String reason) {
        return manualReview(ManualReviewRecord.ReviewPriority.HIGH, reason);
    }

    /**
     * Create permanent failure decision
     */
    public static RecoveryDecision permanentFailure(
            PermanentFailureRecord.FailureCategory category,
            PermanentFailureRecord.BusinessImpact impact,
            String reason) {
        return RecoveryDecision.builder()
            .strategy(RecoveryStrategy.PERMANENT_FAILURE)
            .failureCategory(category)
            .businessImpact(impact)
            .reason(reason)
            .build();
    }

    /**
     * Create permanent failure with financial impact
     */
    public static RecoveryDecision permanentFailureWithImpact(
            PermanentFailureRecord.FailureCategory category,
            PermanentFailureRecord.BusinessImpact impact,
            String reason,
            String impactDescription,
            BigDecimal financialImpact) {
        return RecoveryDecision.builder()
            .strategy(RecoveryStrategy.PERMANENT_FAILURE)
            .failureCategory(category)
            .businessImpact(impact)
            .reason(reason)
            .impactDescription(impactDescription)
            .financialImpact(financialImpact)
            .build();
    }

    /**
     * Create discard decision
     */
    public static RecoveryDecision discard(String reason) {
        return RecoveryDecision.builder()
            .strategy(RecoveryStrategy.DISCARD)
            .reason(reason)
            .build();
    }

    // ========== COMMON ERROR PATTERN CLASSIFIERS ==========

    /**
     * Classify database connectivity errors
     */
    public static RecoveryDecision forDatabaseError(String exceptionMessage, int retryAttempt) {
        if (retryAttempt < 3) {
            return retry("Transient database error: " + exceptionMessage);
        } else {
            return highPriorityReview("Database connectivity failure after max retries: " + exceptionMessage);
        }
    }

    /**
     * Classify external service errors (circuit breaker, timeout, 503)
     */
    public static RecoveryDecision forExternalServiceError(
            String serviceName, String exceptionMessage, int retryAttempt) {
        if (retryAttempt < 3) {
            return retry("External service unavailable: " + serviceName);
        } else {
            return highPriorityReview(
                "External service (" + serviceName + ") unavailable after retries: " + exceptionMessage);
        }
    }

    /**
     * Classify business rule validation failures
     */
    public static RecoveryDecision forBusinessRuleViolation(String validationError) {
        return permanentFailure(
            PermanentFailureRecord.FailureCategory.BUSINESS_RULE_VIOLATION,
            PermanentFailureRecord.BusinessImpact.MEDIUM,
            "Business rule validation failed: " + validationError);
    }

    /**
     * Classify data validation errors
     */
    public static RecoveryDecision forDataValidationError(String validationError) {
        return permanentFailure(
            PermanentFailureRecord.FailureCategory.DATA_VALIDATION_ERROR,
            PermanentFailureRecord.BusinessImpact.LOW,
            "Data validation error: " + validationError);
    }

    /**
     * Classify deserialization/corruption errors
     */
    public static RecoveryDecision forCorruptedData(String error) {
        return permanentFailure(
            PermanentFailureRecord.FailureCategory.UNRECOVERABLE_ERROR,
            PermanentFailureRecord.BusinessImpact.LOW,
            "Data corruption - cannot deserialize: " + error);
    }

    /**
     * Classify duplicate/idempotency violations
     */
    public static RecoveryDecision forDuplicateOperation() {
        return discard("Duplicate operation - idempotency violation");
    }

    /**
     * Classify compliance/KYC failures
     */
    public static RecoveryDecision forComplianceBlock(String reason, int retryAttempt) {
        if (retryAttempt < 2) {
            return retry("Compliance check failure - retrying: " + reason);
        } else {
            return criticalReview("Compliance check failure requires review: " + reason);
        }
    }

    /**
     * Classify resource not found errors
     */
    public static RecoveryDecision forResourceNotFound(String resourceType, String resourceId) {
        return permanentFailure(
            PermanentFailureRecord.FailureCategory.RESOURCE_NOT_FOUND,
            PermanentFailureRecord.BusinessImpact.MEDIUM,
            resourceType + " not found: " + resourceId);
    }

    /**
     * Classify invalid state errors
     */
    public static RecoveryDecision forInvalidState(String stateError) {
        return permanentFailure(
            PermanentFailureRecord.FailureCategory.INVALID_STATE,
            PermanentFailureRecord.BusinessImpact.MEDIUM,
            "Invalid state: " + stateError);
    }

    /**
     * Classify max retries exceeded
     */
    public static RecoveryDecision forMaxRetriesExceeded(String originalReason) {
        return permanentFailure(
            PermanentFailureRecord.FailureCategory.MAX_RETRIES_EXCEEDED,
            PermanentFailureRecord.BusinessImpact.HIGH,
            "Max retry attempts exceeded - original error: " + originalReason);
    }

    // ========== BUILDER EXTENSION FOR FLUENT API ==========

    /**
     * Custom builder for retry decisions
     */
    public static class RetryDecisionBuilder {
        private String reason;
        private String recoveryAction;

        public static RetryDecisionBuilder builder() {
            return new RetryDecisionBuilder();
        }

        public RetryDecisionBuilder strategy(RecoveryStrategy strategy) {
            return this;
        }

        public RetryDecisionBuilder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public RetryDecisionBuilder recoveryAction(String recoveryAction) {
            this.recoveryAction = recoveryAction;
            return this;
        }

        public RecoveryDecision build() {
            return RecoveryDecision.builder()
                .strategy(RecoveryStrategy.RETRY)
                .reason(reason)
                .recoveryAction(recoveryAction)
                .build();
        }
    }
}
