package com.waqiti.account.kafka.dlq;

import com.waqiti.account.entity.ManualReviewRecord;
import com.waqiti.account.entity.PermanentFailureRecord;
import com.waqiti.account.kafka.dlq.RecoveryDecision.RecoveryStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RecoveryDecision
 *
 * @author Waqiti Platform Team
 */
class RecoveryDecisionTest {

    @Test
    void retry_ShouldCreateRetryDecision() {
        // When creating retry decision
        RecoveryDecision decision = RecoveryDecision.retry("Database timeout");

        // Then decision should be RETRY
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.RETRY);
        assertThat(decision.getReason()).isEqualTo("Database timeout");
    }

    @Test
    void retryWithAction_ShouldCreateRetryDecisionWithAction() {
        // When creating retry decision with action
        RecoveryDecision decision = RecoveryDecision.retry(
            "External service unavailable", "Retry with circuit breaker");

        // Then decision should have action
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.RETRY);
        assertThat(decision.getReason()).isEqualTo("External service unavailable");
        assertThat(decision.getRecoveryAction()).isEqualTo("Retry with circuit breaker");
    }

    @Test
    void manualReview_ShouldCreateManualReviewDecision() {
        // When creating manual review decision
        RecoveryDecision decision = RecoveryDecision.manualReview(
            ManualReviewRecord.ReviewPriority.HIGH, "Compliance check failed");

        // Then decision should be MANUAL_REVIEW with priority
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.MANUAL_REVIEW);
        assertThat(decision.getPriority()).isEqualTo(ManualReviewRecord.ReviewPriority.HIGH);
        assertThat(decision.getReason()).isEqualTo("Compliance check failed");
    }

    @Test
    void criticalReview_ShouldCreateCriticalPriorityReview() {
        // When creating critical review
        RecoveryDecision decision = RecoveryDecision.criticalReview("First account creation failed");

        // Then decision should be CRITICAL priority
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.MANUAL_REVIEW);
        assertThat(decision.getPriority()).isEqualTo(ManualReviewRecord.ReviewPriority.CRITICAL);
        assertThat(decision.getReason()).isEqualTo("First account creation failed");
    }

    @Test
    void highPriorityReview_ShouldCreateHighPriorityReview() {
        // When creating high priority review
        RecoveryDecision decision = RecoveryDecision.highPriorityReview("Database failure after retries");

        // Then decision should be HIGH priority
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.MANUAL_REVIEW);
        assertThat(decision.getPriority()).isEqualTo(ManualReviewRecord.ReviewPriority.HIGH);
        assertThat(decision.getReason()).isEqualTo("Database failure after retries");
    }

    @Test
    void permanentFailure_ShouldCreatePermanentFailureDecision() {
        // When creating permanent failure
        RecoveryDecision decision = RecoveryDecision.permanentFailure(
            PermanentFailureRecord.FailureCategory.BUSINESS_RULE_VIOLATION,
            PermanentFailureRecord.BusinessImpact.MEDIUM,
            "Validation failed");

        // Then decision should be PERMANENT_FAILURE
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.PERMANENT_FAILURE);
        assertThat(decision.getFailureCategory())
            .isEqualTo(PermanentFailureRecord.FailureCategory.BUSINESS_RULE_VIOLATION);
        assertThat(decision.getBusinessImpact())
            .isEqualTo(PermanentFailureRecord.BusinessImpact.MEDIUM);
        assertThat(decision.getReason()).isEqualTo("Validation failed");
    }

    @Test
    void permanentFailureWithImpact_ShouldIncludeFinancialData() {
        // When creating permanent failure with financial impact
        BigDecimal financialImpact = new BigDecimal("1500.00");
        RecoveryDecision decision = RecoveryDecision.permanentFailureWithImpact(
            PermanentFailureRecord.FailureCategory.COMPLIANCE_BLOCK,
            PermanentFailureRecord.BusinessImpact.HIGH,
            "Sanctioned customer",
            "Potential regulatory fine",
            financialImpact);

        // Then decision should include financial data
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.PERMANENT_FAILURE);
        assertThat(decision.getBusinessImpact()).isEqualTo(PermanentFailureRecord.BusinessImpact.HIGH);
        assertThat(decision.getImpactDescription()).isEqualTo("Potential regulatory fine");
        assertThat(decision.getFinancialImpact()).isEqualByComparingTo(financialImpact);
    }

    @Test
    void discard_ShouldCreateDiscardDecision() {
        // When creating discard decision
        RecoveryDecision decision = RecoveryDecision.discard("Duplicate message");

        // Then decision should be DISCARD
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.DISCARD);
        assertThat(decision.getReason()).isEqualTo("Duplicate message");
    }

    @ParameterizedTest
    @MethodSource("provideDatabaseErrorScenarios")
    void forDatabaseError_ShouldClassifyCorrectly(String exception, int retryAttempt, RecoveryStrategy expectedStrategy) {
        // When classifying database error
        RecoveryDecision decision = RecoveryDecision.forDatabaseError(exception, retryAttempt);

        // Then decision should match expected strategy
        assertThat(decision.getStrategy()).isEqualTo(expectedStrategy);
        assertThat(decision.getReason()).contains(exception);
    }

    @Test
    void forExternalServiceError_ShouldRetryBeforeManualReview() {
        // When classifying external service error with low retry count
        RecoveryDecision decision = RecoveryDecision.forExternalServiceError(
            "user-service", "Connection timeout", 1);

        // Then should RETRY
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.RETRY);

        // When retry count exceeds threshold
        decision = RecoveryDecision.forExternalServiceError(
            "user-service", "Connection timeout", 3);

        // Then should escalate to MANUAL_REVIEW
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.MANUAL_REVIEW);
        assertThat(decision.getPriority()).isEqualTo(ManualReviewRecord.ReviewPriority.HIGH);
    }

    @Test
    void forBusinessRuleViolation_ShouldBePermanentFailure() {
        // When classifying business rule violation
        RecoveryDecision decision = RecoveryDecision.forBusinessRuleViolation(
            "Max accounts exceeded");

        // Then should be PERMANENT_FAILURE
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.PERMANENT_FAILURE);
        assertThat(decision.getFailureCategory())
            .isEqualTo(PermanentFailureRecord.FailureCategory.BUSINESS_RULE_VIOLATION);
        assertThat(decision.getBusinessImpact())
            .isEqualTo(PermanentFailureRecord.BusinessImpact.MEDIUM);
    }

    @Test
    void forDataValidationError_ShouldBePermanentFailure() {
        // When classifying data validation error
        RecoveryDecision decision = RecoveryDecision.forDataValidationError("Invalid email format");

        // Then should be PERMANENT_FAILURE with LOW impact
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.PERMANENT_FAILURE);
        assertThat(decision.getFailureCategory())
            .isEqualTo(PermanentFailureRecord.FailureCategory.DATA_VALIDATION_ERROR);
        assertThat(decision.getBusinessImpact())
            .isEqualTo(PermanentFailureRecord.BusinessImpact.LOW);
    }

    @Test
    void forCorruptedData_ShouldBePermanentFailure() {
        // When classifying corrupted data
        RecoveryDecision decision = RecoveryDecision.forCorruptedData("Cannot deserialize JSON");

        // Then should be PERMANENT_FAILURE
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.PERMANENT_FAILURE);
        assertThat(decision.getFailureCategory())
            .isEqualTo(PermanentFailureRecord.FailureCategory.UNRECOVERABLE_ERROR);
    }

    @Test
    void forDuplicateOperation_ShouldDiscard() {
        // When classifying duplicate operation
        RecoveryDecision decision = RecoveryDecision.forDuplicateOperation();

        // Then should DISCARD
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.DISCARD);
        assertThat(decision.getReason()).contains("Duplicate");
    }

    @Test
    void forComplianceBlock_ShouldRetryOnceThenCriticalReview() {
        // When first compliance failure
        RecoveryDecision decision = RecoveryDecision.forComplianceBlock("KYC check failed", 0);

        // Then should RETRY
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.RETRY);

        // When second compliance failure
        decision = RecoveryDecision.forComplianceBlock("KYC check failed", 2);

        // Then should escalate to CRITICAL review
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.MANUAL_REVIEW);
        assertThat(decision.getPriority()).isEqualTo(ManualReviewRecord.ReviewPriority.CRITICAL);
    }

    @Test
    void forResourceNotFound_ShouldBePermanentFailure() {
        // When classifying resource not found
        RecoveryDecision decision = RecoveryDecision.forResourceNotFound("Account", "12345");

        // Then should be PERMANENT_FAILURE
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.PERMANENT_FAILURE);
        assertThat(decision.getFailureCategory())
            .isEqualTo(PermanentFailureRecord.FailureCategory.RESOURCE_NOT_FOUND);
        assertThat(decision.getReason()).contains("Account").contains("12345");
    }

    @Test
    void forInvalidState_ShouldBePermanentFailure() {
        // When classifying invalid state
        RecoveryDecision decision = RecoveryDecision.forInvalidState("Account already closed");

        // Then should be PERMANENT_FAILURE
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.PERMANENT_FAILURE);
        assertThat(decision.getFailureCategory())
            .isEqualTo(PermanentFailureRecord.FailureCategory.INVALID_STATE);
        assertThat(decision.getBusinessImpact())
            .isEqualTo(PermanentFailureRecord.BusinessImpact.MEDIUM);
    }

    @Test
    void forMaxRetriesExceeded_ShouldBePermanentFailureWithHighImpact() {
        // When classifying max retries exceeded
        RecoveryDecision decision = RecoveryDecision.forMaxRetriesExceeded("Database timeout");

        // Then should be PERMANENT_FAILURE with HIGH impact
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.PERMANENT_FAILURE);
        assertThat(decision.getFailureCategory())
            .isEqualTo(PermanentFailureRecord.FailureCategory.MAX_RETRIES_EXCEEDED);
        assertThat(decision.getBusinessImpact())
            .isEqualTo(PermanentFailureRecord.BusinessImpact.HIGH);
        assertThat(decision.getReason()).contains("Database timeout");
    }

    @Test
    void builder_ShouldCreateCustomDecision() {
        // When using builder directly
        RecoveryDecision decision = RecoveryDecision.builder()
            .strategy(RecoveryStrategy.PERMANENT_FAILURE)
            .reason("Custom failure")
            .failureCategory(PermanentFailureRecord.FailureCategory.OTHER)
            .businessImpact(PermanentFailureRecord.BusinessImpact.CRITICAL)
            .impactDescription("System outage")
            .financialImpact(new BigDecimal("50000.00"))
            .build();

        // Then all fields should be set
        assertThat(decision.getStrategy()).isEqualTo(RecoveryStrategy.PERMANENT_FAILURE);
        assertThat(decision.getReason()).isEqualTo("Custom failure");
        assertThat(decision.getFailureCategory())
            .isEqualTo(PermanentFailureRecord.FailureCategory.OTHER);
        assertThat(decision.getBusinessImpact())
            .isEqualTo(PermanentFailureRecord.BusinessImpact.CRITICAL);
        assertThat(decision.getImpactDescription()).isEqualTo("System outage");
        assertThat(decision.getFinancialImpact()).isEqualByComparingTo("50000.00");
    }

    // ========== TEST DATA PROVIDERS ==========

    private static Stream<Arguments> provideDatabaseErrorScenarios() {
        return Stream.of(
            Arguments.of("Connection timeout", 0, RecoveryStrategy.RETRY),
            Arguments.of("Connection timeout", 1, RecoveryStrategy.RETRY),
            Arguments.of("Connection timeout", 2, RecoveryStrategy.RETRY),
            Arguments.of("Connection timeout", 3, RecoveryStrategy.MANUAL_REVIEW),
            Arguments.of("Deadlock", 0, RecoveryStrategy.RETRY),
            Arguments.of("Deadlock", 5, RecoveryStrategy.MANUAL_REVIEW)
        );
    }
}
