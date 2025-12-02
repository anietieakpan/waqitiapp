package com.waqiti.common.kafka.dlq.strategy;

import com.waqiti.common.kafka.dlq.DlqStatus;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import com.waqiti.common.kafka.dlq.repository.DlqRecordRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SkipStrategy.
 *
 * Tests message skipping for:
 * - Validation errors
 * - Schema/serialization errors
 * - Duplicate messages
 * - Obsolete events
 * - Test messages
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SkipStrategy Tests")
class SkipStrategyTest {

    @Mock
    private DlqRecordRepository dlqRecordRepository;

    private MeterRegistry meterRegistry;
    private SkipStrategy strategy;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        strategy = new SkipStrategy(dlqRecordRepository, meterRegistry);
    }

    @Test
    @DisplayName("Should skip validation error messages")
    void shouldSkipValidationErrors() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", "ValidationException: Invalid amount");
        when(dlqRecordRepository.save(any())).thenReturn(record);

        // When
        RecoveryStrategyHandler.RecoveryResult result = strategy.recover(record);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Validation error");

        // Verify status updated
        ArgumentCaptor<DlqRecordEntity> captor = ArgumentCaptor.forClass(DlqRecordEntity.class);
        verify(dlqRecordRepository).save(captor.capture());
        DlqRecordEntity saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(DlqStatus.PARKED);
        assertThat(saved.getParkedReason()).contains("skipped");
    }

    @Test
    @DisplayName("Should skip schema/serialization errors")
    void shouldSkipSchemaErrors() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated",
                "SerializationException: Cannot deserialize message");
        when(dlqRecordRepository.save(any())).thenReturn(record);

        // When
        RecoveryStrategyHandler.RecoveryResult result = strategy.recover(record);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Schema/serialization error");
    }

    @Test
    @DisplayName("Should skip obsolete events (>30 days old)")
    void shouldSkipObsoleteEvents() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", "Timeout");
        record.setCreatedAt(LocalDateTime.now().minusDays(35));
        when(dlqRecordRepository.save(any())).thenReturn(record);

        // When
        RecoveryStrategyHandler.RecoveryResult result = strategy.recover(record);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Obsolete event");
    }

    @Test
    @DisplayName("Should skip test messages")
    void shouldSkipTestMessages() {
        // Given
        DlqRecordEntity record = createDlqRecord("test-payment-initiated", "Error");
        when(dlqRecordRepository.save(any())).thenReturn(record);

        // When
        RecoveryStrategyHandler.RecoveryResult result = strategy.recover(record);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Test/debug message");
    }

    @Test
    @DisplayName("Should skip messages with test flag in payload")
    void shouldSkipMessagesWithTestFlag() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", "Error");
        record.setMessageValue("{\"test\":true,\"amount\":100}");
        when(dlqRecordRepository.save(any())).thenReturn(record);

        // When
        RecoveryStrategyHandler.RecoveryResult result = strategy.recover(record);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("Test/debug message");
    }

    @Test
    @DisplayName("Should handle validation errors in canHandle")
    void shouldHandleValidationErrors() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", "ValidationException");

        // When
        boolean canHandle = strategy.canHandle(record);

        // Then
        assertThat(canHandle).isTrue();
    }

    @Test
    @DisplayName("Should handle max retries exceeded")
    void shouldHandleMaxRetriesExceeded() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", "Error");
        record.setRetryCount(10);

        // When
        boolean canHandle = strategy.canHandle(record);

        // Then
        assertThat(canHandle).isTrue();
    }

    @Test
    @DisplayName("Should NOT handle transient errors")
    void shouldNotHandleTransientErrors() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", "TimeoutException");
        record.setRetryCount(1);

        // When
        boolean canHandle = strategy.canHandle(record);

        // Then
        assertThat(canHandle).isFalse();
    }

    @Test
    @DisplayName("Should return correct strategy name")
    void shouldReturnCorrectStrategyName() {
        assertThat(strategy.getStrategyName()).isEqualTo("SKIP");
    }

    private DlqRecordEntity createDlqRecord(String topic, String failureReason) {
        return DlqRecordEntity.builder()
                .id(UUID.randomUUID())
                .messageId("test-message-" + UUID.randomUUID())
                .topic(topic)
                .partition(0)
                .offset(1000L)
                .messageKey("test-key")
                .messageValue("{\"amount\":100.00,\"currency\":\"USD\"}")
                .serviceName("test-service")
                .status(DlqStatus.PENDING)
                .retryCount(1)
                .lastFailureReason(failureReason)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
