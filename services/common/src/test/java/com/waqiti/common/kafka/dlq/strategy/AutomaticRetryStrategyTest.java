package com.waqiti.common.kafka.dlq.strategy;

import com.waqiti.common.kafka.dlq.DlqStatus;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AutomaticRetryStrategy.
 *
 * Tests recovery behavior for:
 * - Successful retries
 * - Failed retries with backoff calculation
 * - Max retries exceeded
 * - Transient vs permanent error classification
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AutomaticRetryStrategy Tests")
class AutomaticRetryStrategyTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private MeterRegistry meterRegistry;
    private AutomaticRetryStrategy strategy;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        strategy = new AutomaticRetryStrategy(kafkaTemplate, meterRegistry);
    }

    @Test
    @DisplayName("Should successfully retry message to original topic")
    void shouldSuccessfullyRetryMessage() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", 1);

        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        // When
        RecoveryStrategyHandler.RecoveryResult result = strategy.recover(record);

        // Then
        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("successfully republished");
        verify(kafkaTemplate).send(eq("payment-initiated"), any(), any());

        // Verify metrics
        Counter counter = meterRegistry.find("dlq.retry.success").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should schedule retry with backoff when send fails")
    void shouldScheduleRetryWithBackoff() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", 2);

        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(future);

        // When
        RecoveryStrategyHandler.RecoveryResult result = strategy.recover(record);

        // Then
        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isTrue();
        assertThat(result.nextRetryDelaySeconds()).isEqualTo(120); // Second retry = 2 minutes

        Counter counter = meterRegistry.find("dlq.retry.failure").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Should return permanent failure when max retries exceeded")
    void shouldReturnPermanentFailureWhenMaxRetriesExceeded() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", 10);

        // When
        RecoveryStrategyHandler.RecoveryResult result = strategy.recover(record);

        // Then
        assertThat(result.success()).isFalse();
        assertThat(result.retryable()).isFalse();
        assertThat(result.message()).contains("Max retries");
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    @Test
    @DisplayName("Should handle transient errors (network, timeout)")
    void shouldHandleTransientErrors() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", 1);
        record.setLastFailureReason("TimeoutException: Connection timed out");

        // When
        boolean canHandle = strategy.canHandle(record);

        // Then
        assertThat(canHandle).isTrue();
    }

    @Test
    @DisplayName("Should NOT handle validation errors")
    void shouldNotHandleValidationErrors() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", 1);
        record.setLastFailureReason("ValidationException: Invalid payload");

        // When
        boolean canHandle = strategy.canHandle(record);

        // Then
        assertThat(canHandle).isFalse();
    }

    @Test
    @DisplayName("Should NOT handle schema errors")
    void shouldNotHandleSchemaErrors() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", 1);
        record.setLastFailureReason("SerializationException: Cannot deserialize");

        // When
        boolean canHandle = strategy.canHandle(record);

        // Then
        assertThat(canHandle).isFalse();
    }

    @Test
    @DisplayName("Should NOT handle messages exceeding max retries")
    void shouldNotHandleMaxRetriesExceeded() {
        // Given
        DlqRecordEntity record = createDlqRecord("payment-initiated", 10);

        // When
        boolean canHandle = strategy.canHandle(record);

        // Then
        assertThat(canHandle).isFalse();
    }

    @Test
    @DisplayName("Should return correct strategy name")
    void shouldReturnCorrectStrategyName() {
        assertThat(strategy.getStrategyName()).isEqualTo("AUTOMATIC_RETRY");
    }

    private DlqRecordEntity createDlqRecord(String topic, int retryCount) {
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
                .retryCount(retryCount)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
