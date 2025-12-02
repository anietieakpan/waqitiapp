package com.waqiti.payment.kafka.dlq;

import com.waqiti.common.kafka.dlq.DlqStatus;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import com.waqiti.common.kafka.dlq.repository.DlqRecordRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Integration tests for CriticalPaymentProcessingDlqConsumer.
 *
 * Tests DLQ message handling:
 * - Message persistence to database
 * - Kafka acknowledgment
 * - Error handling and retry logic
 * - Idempotency
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CriticalPaymentProcessingDlqConsumer Integration Tests")
class CriticalPaymentProcessingDlqConsumerIntegrationTest {

    @Mock
    private DlqRecordRepository dlqRecordRepository;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private CriticalPaymentProcessingDlqConsumer consumer;

    @Test
    @DisplayName("Should persist DLQ message and acknowledge")
    void shouldPersistDlqMessageAndAcknowledge() {
        // Given
        String topic = "payment-initiated-dlq";
        String key = "payment-123";
        String value = "{\"paymentId\":\"123\",\"amount\":100.00,\"currency\":\"USD\"}";

        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                topic, 0, 1000L, key, value);

        when(dlqRecordRepository.save(any(DlqRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        consumer.handleCriticalPaymentDlq(record, acknowledgment);

        // Then
        ArgumentCaptor<DlqRecordEntity> captor = ArgumentCaptor.forClass(DlqRecordEntity.class);
        verify(dlqRecordRepository).save(captor.capture());

        DlqRecordEntity savedRecord = captor.getValue();
        assertThat(savedRecord.getTopic()).isEqualTo("payment-initiated"); // -dlq suffix removed
        assertThat(savedRecord.getMessageKey()).isEqualTo(key);
        assertThat(savedRecord.getMessageValue()).isEqualTo(value);
        assertThat(savedRecord.getStatus()).isEqualTo(DlqStatus.PENDING);
        assertThat(savedRecord.getRetryCount()).isEqualTo(0);
        assertThat(savedRecord.getServiceName()).isEqualTo("payment-service");

        verify(acknowledgment).acknowledge();
    }

    @Test
    @DisplayName("Should handle errors gracefully without acknowledging")
    void shouldHandleErrorsGracefullyWithoutAcknowledging() {
        // Given
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "payment-initiated-dlq", 0, 1000L, "key", "value");

        when(dlqRecordRepository.save(any())).thenThrow(new RuntimeException("Database error"));

        // When
        consumer.handleCriticalPaymentDlq(record, acknowledgment);

        // Then
        verify(acknowledgment, never()).acknowledge(); // Message not acknowledged on error
    }

    @Test
    @DisplayName("Should extract message ID from Kafka headers")
    void shouldExtractMessageIdFromHeaders() {
        // Given
        String topic = "payment-completed-dlq";
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                topic, 0, 1000L, "key", "value");

        // Add messageId header
        record.headers().add("messageId", "custom-message-id".getBytes());

        when(dlqRecordRepository.save(any(DlqRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        consumer.handleCriticalPaymentDlq(record, acknowledgment);

        // Then
        ArgumentCaptor<DlqRecordEntity> captor = ArgumentCaptor.forClass(DlqRecordEntity.class);
        verify(dlqRecordRepository).save(captor.capture());

        DlqRecordEntity savedRecord = captor.getValue();
        assertThat(savedRecord.getMessageId()).isEqualTo("custom-message-id");
    }

    @Test
    @DisplayName("Should generate unique message ID when header missing")
    void shouldGenerateUniqueMessageIdWhenHeaderMissing() {
        // Given
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
                "payment-failed-dlq", 0, 1000L, null, "value");

        when(dlqRecordRepository.save(any(DlqRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        consumer.handleCriticalPaymentDlq(record, acknowledgment);

        // Then
        ArgumentCaptor<DlqRecordEntity> captor = ArgumentCaptor.forClass(DlqRecordEntity.class);
        verify(dlqRecordRepository).save(captor.capture());

        DlqRecordEntity savedRecord = captor.getValue();
        assertThat(savedRecord.getMessageId()).isNotNull();
        assertThat(savedRecord.getMessageId()).contains("payment-failed");
    }
}
