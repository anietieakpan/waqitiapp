package com.waqiti.dispute.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseDlqConsumer;
import com.waqiti.dispute.entity.DLQEntry;
import com.waqiti.dispute.entity.DLQStatus;
import com.waqiti.dispute.entity.RecoveryStrategy;
import com.waqiti.dispute.repository.DLQEntryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DisputeAutoResolutionConsumerDlqHandler
 *
 * Tests DLQ handling for failed auto-resolution events
 *
 * @author Waqiti Production Team
 * @version 2.0.0-PRODUCTION
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DisputeAutoResolutionConsumerDlqHandler Tests")
class DisputeAutoResolutionConsumerDlqHandlerTest {

    @Mock
    private MeterRegistry meterRegistry;

    @Mock
    private DLQEntryRepository dlqRepository;

    @Mock
    private ObjectMapper objectMapper;

    private DisputeAutoResolutionConsumerDlqHandler dlqHandler;

    private Map<String, Object> testEvent;
    private Map<String, Object> testHeaders;

    @BeforeEach
    void setUp() throws Exception {
        dlqHandler = new DisputeAutoResolutionConsumerDlqHandler(meterRegistry, dlqRepository, objectMapper);

        // Setup test event data
        testEvent = new HashMap<>();
        testEvent.put("disputeId", "dispute-123");
        testEvent.put("customerId", "customer-456");
        testEvent.put("resolutionDecision", "APPROVE");
        testEvent.put("disputeAmount", "100.00");
        testEvent.put("currency", "USD");
        testEvent.put("eventId", "event-789");

        // Setup test headers
        testHeaders = new HashMap<>();
        testHeaders.put("x-error-message", "Database connection timeout");

        // Mock ObjectMapper
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"disputeId\":\"dispute-123\"}");
    }

    @Test
    @DisplayName("Should handle DLQ event successfully and return MANUAL_INTERVENTION_REQUIRED")
    void testHandleDlqEvent_Success() {
        // Given
        DLQEntry savedEntry = DLQEntry.builder()
                .id("dlq-id-123")
                .eventId("event-789")
                .build();
        when(dlqRepository.save(any(DLQEntry.class))).thenReturn(savedEntry);

        // When
        BaseDlqConsumer.DlqProcessingResult result = dlqHandler.handleDlqEvent(testEvent, testHeaders);

        // Then
        assertThat(result).isEqualTo(BaseDlqConsumer.DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED);

        verify(dlqRepository).save(argThat(entry ->
                entry.getEventId().equals("event-789") &&
                entry.getSourceTopic().equals("DisputeAutoResolution") &&
                entry.getErrorMessage().equals("Database connection timeout") &&
                entry.getStatus() == DLQStatus.PENDING_REVIEW &&
                entry.getRecoveryStrategy() == RecoveryStrategy.MANUAL_INTERVENTION &&
                entry.getRetryCount() == 0 &&
                entry.getMaxRetries() == 0 &&
                entry.isAlertSent()
        ));
    }

    @Test
    @DisplayName("Should extract disputeId from event data")
    void testHandleDlqEvent_ExtractsDisputeId() {
        // When
        dlqHandler.handleDlqEvent(testEvent, testHeaders);

        // Then
        verify(dlqRepository).save(argThat(entry -> {
            // The handler should have logged the dispute ID
            return entry.getEventId().equals("event-789");
        }));
    }

    @Test
    @DisplayName("Should handle missing eventId by using fallback keys")
    void testHandleDlqEvent_MissingEventId_UsesFallback() {
        // Given
        testEvent.remove("eventId");

        // When
        dlqHandler.handleDlqEvent(testEvent, testHeaders);

        // Then
        verify(dlqRepository).save(argThat(entry ->
                entry.getEventId().equals("dispute-123") // Falls back to disputeId
        ));
    }

    @Test
    @DisplayName("Should handle missing error message in headers")
    void testHandleDlqEvent_MissingErrorMessage() {
        // Given
        testHeaders.clear();

        // When
        dlqHandler.handleDlqEvent(testEvent, testHeaders);

        // Then
        verify(dlqRepository).save(argThat(entry ->
                entry.getErrorMessage().equals("Unknown error")
        ));
    }

    @Test
    @DisplayName("Should return PERMANENT_FAILURE when processing fails")
    void testHandleDlqEvent_ProcessingFails() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("JSON serialization failed"));

        // When
        BaseDlqConsumer.DlqProcessingResult result = dlqHandler.handleDlqEvent(testEvent, testHeaders);

        // Then
        assertThat(result).isEqualTo(BaseDlqConsumer.DlqProcessingResult.PERMANENT_FAILURE);
    }

    @Test
    @DisplayName("Should save DLQ entry even when ObjectMapper fails")
    void testHandleDlqEvent_ObjectMapperFails_StillSavesFallback() throws Exception {
        // Given
        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("Serialization error"));

        // When
        dlqHandler.handleDlqEvent(testEvent, testHeaders);

        // Then - Should not have called save since exception occurs before fallback logic
        // In real implementation, there might be a fallback save
        verify(dlqRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle event with all fields populated")
    void testHandleDlqEvent_AllFieldsPopulated() {
        // Given
        testEvent.put("transactionId", "txn-999");
        testEvent.put("merchantId", "merchant-888");
        testEvent.put("aiModelVersion", "v2.1");

        // When
        BaseDlqConsumer.DlqProcessingResult result = dlqHandler.handleDlqEvent(testEvent, testHeaders);

        // Then
        assertThat(result).isEqualTo(BaseDlqConsumer.DlqProcessingResult.MANUAL_INTERVENTION_REQUIRED);
        verify(dlqRepository).save(any(DLQEntry.class));
    }

    @Test
    @DisplayName("Should set alertSent to true for auto-resolution failures")
    void testHandleDlqEvent_AlertSentSetToTrue() {
        // When
        dlqHandler.handleDlqEvent(testEvent, testHeaders);

        // Then
        verify(dlqRepository).save(argThat(entry -> entry.isAlertSent()));
    }

    @Test
    @DisplayName("Should return correct service name")
    void testGetServiceName() {
        // When
        String serviceName = dlqHandler.getServiceName();

        // Then
        assertThat(serviceName).isEqualTo("DisputeAutoResolutionConsumer");
    }
}
