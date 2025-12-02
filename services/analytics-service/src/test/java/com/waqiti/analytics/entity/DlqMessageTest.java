package com.waqiti.analytics.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for DlqMessage Entity
 *
 * Tests business logic, state transitions, and validation methods
 * in the DlqMessage entity.
 *
 * @author Waqiti Analytics Team
 * @version 1.0.0-PRODUCTION
 * @since 2025-11-15
 */
@DisplayName("DlqMessage Entity Tests")
class DlqMessageTest {

    @Nested
    @DisplayName("State Transition Tests")
    class StateTransitionTests {

        @Test
        @DisplayName("Should mark message as recovered with action description")
        void shouldMarkAsRecovered() {
            // Given
            DlqMessage message = createPendingMessage();
            String recoveryAction = "Successfully reprocessed after retry #2";

            // When
            message.markAsRecovered(recoveryAction);

            // Then
            assertThat(message.getStatus()).isEqualTo(DlqMessage.DlqStatus.RECOVERED);
            assertThat(message.getRecoveryAction()).isEqualTo(recoveryAction);
            assertThat(message.getProcessedAt()).isNotNull();
            assertThat(message.getProcessedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        @DisplayName("Should mark message as failed with reason")
        void shouldMarkAsFailed() {
            // Given
            DlqMessage message = createPendingMessage();
            String failureReason = "Max retry attempts (3) exceeded";

            // When
            message.markAsFailed(failureReason);

            // Then
            assertThat(message.getStatus()).isEqualTo(DlqMessage.DlqStatus.FAILED);
            assertThat(message.getFailureReason()).isEqualTo(failureReason);
            assertThat(message.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("Should mark message as requiring manual review")
        void shouldMarkAsManualReviewRequired() {
            // Given
            DlqMessage message = createPendingMessage();
            String reason = "Invalid data format - cannot parse JSON";

            // When
            message.markAsManualReviewRequired(reason);

            // Then
            assertThat(message.getStatus()).isEqualTo(DlqMessage.DlqStatus.MANUAL_REVIEW_REQUIRED);
            assertThat(message.getFailureReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("Should increment retry count and update status")
        void shouldIncrementRetryCount() {
            // Given
            DlqMessage message = createPendingMessage();
            int initialRetryCount = message.getRetryCount();

            // When
            message.incrementRetryCount();

            // Then
            assertThat(message.getRetryCount()).isEqualTo(initialRetryCount + 1);
            assertThat(message.getStatus()).isEqualTo(DlqMessage.DlqStatus.RETRY_IN_PROGRESS);
            assertThat(message.getLastRetryAt()).isNotNull();
            assertThat(message.getLastRetryAt()).isBeforeOrEqualTo(LocalDateTime.now());
        }
    }

    @Nested
    @DisplayName("Retry Eligibility Tests")
    class RetryEligibilityTests {

        @Test
        @DisplayName("Should be eligible for retry when status is PENDING_REVIEW and retry count below max")
        void shouldBeEligibleForRetry() {
            // Given
            DlqMessage message = DlqMessage.builder()
                .status(DlqMessage.DlqStatus.PENDING_REVIEW)
                .retryCount(1)
                .maxRetryAttempts(3)
                .build();

            // When
            boolean eligible = message.isEligibleForRetry();

            // Then
            assertThat(eligible).isTrue();
        }

        @Test
        @DisplayName("Should NOT be eligible when retry count equals max attempts")
        void shouldNotBeEligibleWhenMaxRetriesReached() {
            // Given
            DlqMessage message = DlqMessage.builder()
                .status(DlqMessage.DlqStatus.PENDING_REVIEW)
                .retryCount(3)
                .maxRetryAttempts(3)
                .build();

            // When
            boolean eligible = message.isEligibleForRetry();

            // Then
            assertThat(eligible).isFalse();
        }

        @Test
        @DisplayName("Should NOT be eligible when retry count exceeds max attempts")
        void shouldNotBeEligibleWhenRetriesExceeded() {
            // Given
            DlqMessage message = DlqMessage.builder()
                .status(DlqMessage.DlqStatus.PENDING_REVIEW)
                .retryCount(5)
                .maxRetryAttempts(3)
                .build();

            // When
            boolean eligible = message.isEligibleForRetry();

            // Then
            assertThat(eligible).isFalse();
        }

        @Test
        @DisplayName("Should NOT be eligible when status is not PENDING_REVIEW")
        void shouldNotBeEligibleWhenNotPendingReview() {
            // Given
            DlqMessage message = DlqMessage.builder()
                .status(DlqMessage.DlqStatus.RETRY_IN_PROGRESS)
                .retryCount(1)
                .maxRetryAttempts(3)
                .build();

            // When
            boolean eligible = message.isEligibleForRetry();

            // Then
            assertThat(eligible).isFalse();
        }

        @Test
        @DisplayName("Should NOT be eligible when status is RECOVERED")
        void shouldNotBeEligibleWhenRecovered() {
            // Given
            DlqMessage message = DlqMessage.builder()
                .status(DlqMessage.DlqStatus.RECOVERED)
                .retryCount(1)
                .maxRetryAttempts(3)
                .build();

            // When
            boolean eligible = message.isEligibleForRetry();

            // Then
            assertThat(eligible).isFalse();
        }

        @Test
        @DisplayName("Should NOT be eligible when status is FAILED")
        void shouldNotBeEligibleWhenFailed() {
            // Given
            DlqMessage message = DlqMessage.builder()
                .status(DlqMessage.DlqStatus.FAILED)
                .retryCount(3)
                .maxRetryAttempts(3)
                .build();

            // When
            boolean eligible = message.isEligibleForRetry();

            // Then
            assertThat(eligible).isFalse();
        }
    }

    @Nested
    @DisplayName("Staleness Detection Tests")
    class StalenessDetectionTests {

        @Test
        @DisplayName("Should detect stale message when pending beyond threshold")
        void shouldDetectStaleMessage() {
            // Given
            LocalDateTime oldTimestamp = LocalDateTime.now().minusHours(25);
            DlqMessage message = DlqMessage.builder()
                .status(DlqMessage.DlqStatus.PENDING_REVIEW)
                .receivedAt(oldTimestamp)
                .build();

            // When
            boolean isStale = message.isStale(24);

            // Then
            assertThat(isStale).isTrue();
        }

        @Test
        @DisplayName("Should NOT detect as stale when pending within threshold")
        void shouldNotDetectAsStaleWhenWithinThreshold() {
            // Given
            LocalDateTime recentTimestamp = LocalDateTime.now().minusHours(20);
            DlqMessage message = DlqMessage.builder()
                .status(DlqMessage.DlqStatus.PENDING_REVIEW)
                .receivedAt(recentTimestamp)
                .build();

            // When
            boolean isStale = message.isStale(24);

            // Then
            assertThat(isStale).isFalse();
        }

        @Test
        @DisplayName("Should NOT detect as stale when status is not PENDING_REVIEW")
        void shouldNotDetectAsStaleWhenNotPending() {
            // Given
            LocalDateTime oldTimestamp = LocalDateTime.now().minusHours(48);
            DlqMessage message = DlqMessage.builder()
                .status(DlqMessage.DlqStatus.RECOVERED)
                .receivedAt(oldTimestamp)
                .build();

            // When
            boolean isStale = message.isStale(24);

            // Then
            assertThat(isStale).isFalse();
        }

        @Test
        @DisplayName("Should handle edge case when exactly at threshold")
        void shouldHandleExactThresholdEdgeCase() {
            // Given
            LocalDateTime exactThresholdTimestamp = LocalDateTime.now().minusHours(24);
            DlqMessage message = DlqMessage.builder()
                .status(DlqMessage.DlqStatus.PENDING_REVIEW)
                .receivedAt(exactThresholdTimestamp)
                .build();

            // When
            boolean isStale = message.isStale(24);

            // Then
            // Should not be stale if exactly at threshold (uses isBefore, not isBeforeOrEqual)
            assertThat(isStale).isFalse();
        }
    }

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderPatternTests {

        @Test
        @DisplayName("Should build message with all required fields")
        void shouldBuildMessageWithRequiredFields() {
            // Given
            String originalTopic = "analytics.events";
            String dlqTopic = "analytics.events.dlq";
            String messageValue = "{\"test\":\"data\"}";
            String correlationId = UUID.randomUUID().toString();

            // When
            DlqMessage message = DlqMessage.builder()
                .originalTopic(originalTopic)
                .dlqTopic(dlqTopic)
                .messageValue(messageValue)
                .correlationId(correlationId)
                .build();

            // Then
            assertThat(message.getOriginalTopic()).isEqualTo(originalTopic);
            assertThat(message.getDlqTopic()).isEqualTo(dlqTopic);
            assertThat(message.getMessageValue()).isEqualTo(messageValue);
            assertThat(message.getCorrelationId()).isEqualTo(correlationId);
        }

        @Test
        @DisplayName("Should apply default values via builder")
        void shouldApplyDefaultValues() {
            // When
            DlqMessage message = DlqMessage.builder()
                .originalTopic("test.topic")
                .dlqTopic("test.topic.dlq")
                .messageValue("{}")
                .correlationId("test-id")
                .build();

            // Then
            assertThat(message.getRetryCount()).isEqualTo(0);
            assertThat(message.getMaxRetryAttempts()).isEqualTo(3);
            assertThat(message.getStatus()).isEqualTo(DlqMessage.DlqStatus.PENDING_REVIEW);
            assertThat(message.getSeverity()).isEqualTo(DlqMessage.Severity.MEDIUM);
            assertThat(message.getVersion()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Should override default values when explicitly set")
        void shouldOverrideDefaultValues() {
            // When
            DlqMessage message = DlqMessage.builder()
                .originalTopic("test.topic")
                .dlqTopic("test.topic.dlq")
                .messageValue("{}")
                .correlationId("test-id")
                .retryCount(2)
                .maxRetryAttempts(5)
                .status(DlqMessage.DlqStatus.RETRY_IN_PROGRESS)
                .severity(DlqMessage.Severity.HIGH)
                .build();

            // Then
            assertThat(message.getRetryCount()).isEqualTo(2);
            assertThat(message.getMaxRetryAttempts()).isEqualTo(5);
            assertThat(message.getStatus()).isEqualTo(DlqMessage.DlqStatus.RETRY_IN_PROGRESS);
            assertThat(message.getSeverity()).isEqualTo(DlqMessage.Severity.HIGH);
        }
    }

    @Nested
    @DisplayName("Severity Enum Tests")
    class SeverityEnumTests {

        @Test
        @DisplayName("Should have all expected severity levels")
        void shouldHaveAllSeverityLevels() {
            // When/Then
            assertThat(DlqMessage.Severity.values())
                .containsExactlyInAnyOrder(
                    DlqMessage.Severity.LOW,
                    DlqMessage.Severity.MEDIUM,
                    DlqMessage.Severity.HIGH,
                    DlqMessage.Severity.CRITICAL
                );
        }
    }

    @Nested
    @DisplayName("Status Enum Tests")
    class StatusEnumTests {

        @Test
        @DisplayName("Should have all expected status values")
        void shouldHaveAllStatusValues() {
            // When/Then
            assertThat(DlqMessage.DlqStatus.values())
                .containsExactlyInAnyOrder(
                    DlqMessage.DlqStatus.PENDING_REVIEW,
                    DlqMessage.DlqStatus.RETRY_IN_PROGRESS,
                    DlqMessage.DlqStatus.RECOVERED,
                    DlqMessage.DlqStatus.FAILED,
                    DlqMessage.DlqStatus.MANUAL_REVIEW_REQUIRED,
                    DlqMessage.DlqStatus.ARCHIVED
                );
        }
    }

    // Helper method to create a pending message for testing
    private DlqMessage createPendingMessage() {
        return DlqMessage.builder()
            .originalTopic("test.topic")
            .dlqTopic("test.topic.dlq")
            .messageValue("{\"test\":\"data\"}")
            .correlationId(UUID.randomUUID().toString())
            .retryCount(1)
            .maxRetryAttempts(3)
            .status(DlqMessage.DlqStatus.PENDING_REVIEW)
            .severity(DlqMessage.Severity.MEDIUM)
            .build();
    }
}
