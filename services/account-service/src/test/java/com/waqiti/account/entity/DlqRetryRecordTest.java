package com.waqiti.account.entity;

import com.waqiti.account.entity.DlqRetryRecord.RetryStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DlqRetryRecord entity
 *
 * @author Waqiti Platform Team
 */
class DlqRetryRecordTest {

    private DlqRetryRecord retryRecord;

    @BeforeEach
    void setUp() {
        retryRecord = DlqRetryRecord.builder()
            .originalTopic("test.topic")
            .originalPartition(0)
            .originalOffset(12345L)
            .originalKey("test-key")
            .payload("{\"test\": \"payload\"}")
            .exceptionMessage("Test exception")
            .exceptionClass("TestException")
            .failedAt(LocalDateTime.now().minusHours(1))
            .failureReason("Test failure")
            .retryAttempt(0)
            .maxRetryAttempts(3)
            .nextRetryAt(LocalDateTime.now().plusSeconds(5))
            .backoffDelayMs(5000L)
            .handlerName("TestHandler")
            .correlationId(UUID.randomUUID().toString())
            .build();

        // Simulate @PrePersist
        retryRecord.onCreate();
    }

    @Test
    void onCreate_ShouldSetDefaultValues() {
        // Given a new record
        DlqRetryRecord record = new DlqRetryRecord();

        // When onCreate is called
        record.onCreate();

        // Then defaults should be set
        assertThat(record.getCreatedAt()).isNotNull();
        assertThat(record.getUpdatedAt()).isNotNull();
        assertThat(record.getStatus()).isEqualTo(RetryStatus.PENDING);
        assertThat(record.getRetryAttempt()).isEqualTo(0);
        assertThat(record.getMaxRetryAttempts()).isEqualTo(3);
        assertThat(record.getCreatedBy()).isEqualTo("system");
    }

    @Test
    void shouldRetry_WhenAllConditionsMet() {
        // Given a pending retry that is due
        retryRecord.setStatus(RetryStatus.PENDING);
        retryRecord.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        retryRecord.setRetryAttempt(1);
        retryRecord.setMaxRetryAttempts(3);

        // When checking shouldRetry
        boolean shouldRetry = retryRecord.shouldRetry();

        // Then should return true
        assertTrue(shouldRetry);
    }

    @Test
    void shouldRetry_ShouldReturnFalse_WhenStatusIsNotPending() {
        // Given a non-pending retry
        retryRecord.setStatus(RetryStatus.RETRYING);
        retryRecord.setNextRetryAt(LocalDateTime.now().minusSeconds(1));

        // When checking shouldRetry
        boolean shouldRetry = retryRecord.shouldRetry();

        // Then should return false
        assertFalse(shouldRetry);
    }

    @Test
    void shouldRetry_ShouldReturnFalse_WhenNotYetDue() {
        // Given a pending retry that is not yet due
        retryRecord.setStatus(RetryStatus.PENDING);
        retryRecord.setNextRetryAt(LocalDateTime.now().plusHours(1));

        // When checking shouldRetry
        boolean shouldRetry = retryRecord.shouldRetry();

        // Then should return false
        assertFalse(shouldRetry);
    }

    @Test
    void shouldRetry_ShouldReturnFalse_WhenMaxAttemptsExceeded() {
        // Given a retry at max attempts
        retryRecord.setStatus(RetryStatus.PENDING);
        retryRecord.setNextRetryAt(LocalDateTime.now().minusSeconds(1));
        retryRecord.setRetryAttempt(3);
        retryRecord.setMaxRetryAttempts(3);

        // When checking shouldRetry
        boolean shouldRetry = retryRecord.shouldRetry();

        // Then should return false
        assertFalse(shouldRetry);
    }

    @Test
    void incrementRetryAttempt_ShouldIncrementAndCalculateNextRetry() {
        // Given a record with 1 retry attempt
        retryRecord.setRetryAttempt(1);
        long backoffMs = 10000L;

        LocalDateTime beforeIncrement = LocalDateTime.now();

        // When incrementing retry attempt
        retryRecord.incrementRetryAttempt(backoffMs);

        // Then retry attempt should be incremented
        assertThat(retryRecord.getRetryAttempt()).isEqualTo(2);

        // And backoff delay should be updated
        assertThat(retryRecord.getBackoffDelayMs()).isEqualTo(backoffMs);

        // And next retry time should be calculated
        assertThat(retryRecord.getNextRetryAt())
            .isAfter(beforeIncrement)
            .isBefore(LocalDateTime.now().plusNanos(backoffMs * 1_000_000).plusSeconds(1));

        // And updated timestamp should be set
        assertThat(retryRecord.getUpdatedAt()).isNotNull();
    }

    @Test
    void markSuccess_ShouldUpdateStatusAndTimestamp() {
        // Given a pending retry
        retryRecord.setStatus(RetryStatus.PENDING);
        LocalDateTime before = LocalDateTime.now();

        // When marking as success
        retryRecord.markSuccess();

        // Then status should be SUCCESS
        assertThat(retryRecord.getStatus()).isEqualTo(RetryStatus.SUCCESS);

        // And updated timestamp should be set
        assertThat(retryRecord.getUpdatedAt()).isAfterOrEqualTo(before);
    }

    @Test
    void markFailed_ShouldUpdateStatusAndReason() {
        // Given a pending retry
        retryRecord.setStatus(RetryStatus.PENDING);
        String failureReason = "Max retries exceeded";

        // When marking as failed
        retryRecord.markFailed(failureReason);

        // Then status should be FAILED
        assertThat(retryRecord.getStatus()).isEqualTo(RetryStatus.FAILED);

        // And failure reason should be set
        assertThat(retryRecord.getFailureReason()).isEqualTo(failureReason);

        // And updated timestamp should be set
        assertThat(retryRecord.getUpdatedAt()).isNotNull();
    }

    @Test
    void cancel_ShouldUpdateStatusAndReason() {
        // Given a pending retry
        retryRecord.setStatus(RetryStatus.PENDING);
        String cancelReason = "Manual intervention";

        // When cancelling
        retryRecord.cancel(cancelReason);

        // Then status should be CANCELLED
        assertThat(retryRecord.getStatus()).isEqualTo(RetryStatus.CANCELLED);

        // And failure reason should be set
        assertThat(retryRecord.getFailureReason()).isEqualTo(cancelReason);

        // And updated timestamp should be set
        assertThat(retryRecord.getUpdatedAt()).isNotNull();
    }

    @Test
    void builder_ShouldCreateValidRecord() {
        // When building a record
        DlqRetryRecord record = DlqRetryRecord.builder()
            .originalTopic("account.events")
            .originalPartition(2)
            .originalOffset(54321L)
            .payload("{\"accountId\": \"123\"}")
            .failedAt(LocalDateTime.now())
            .nextRetryAt(LocalDateTime.now().plusSeconds(10))
            .handlerName("AccountEventsHandler")
            .build();

        // Then record should be created
        assertNotNull(record);
        assertThat(record.getOriginalTopic()).isEqualTo("account.events");
        assertThat(record.getOriginalPartition()).isEqualTo(2);
        assertThat(record.getOriginalOffset()).isEqualTo(54321L);
    }

    @Test
    void toString_ShouldExcludeSensitiveFields() {
        // When converting to string
        String toString = retryRecord.toString();

        // Then sensitive fields should be excluded
        assertThat(toString).doesNotContain("payload");
        assertThat(toString).doesNotContain("exceptionStackTrace");
    }

    @ParameterizedTest
    @EnumSource(RetryStatus.class)
    void allRetryStatuses_ShouldBeValid(RetryStatus status) {
        // When setting any valid status
        retryRecord.setStatus(status);

        // Then status should be set
        assertThat(retryRecord.getStatus()).isEqualTo(status);
    }

    @Test
    void onUpdate_ShouldUpdateTimestamp() {
        // Given a record
        LocalDateTime originalUpdatedAt = retryRecord.getUpdatedAt();

        // When onUpdate is called
        try {
            Thread.sleep(10); // Small delay to ensure timestamp difference
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        retryRecord.onUpdate();

        // Then updated timestamp should be more recent
        assertThat(retryRecord.getUpdatedAt()).isAfter(originalUpdatedAt);
    }
}
