package com.waqiti.saga.events.producers;

import com.waqiti.common.events.saga.SagaStepCompletedEvent;
import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.saga.domain.SagaExecution;
import com.waqiti.saga.domain.SagaStep;
import com.waqiti.saga.domain.StepStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade event producer for saga step completion events.
 * Publishes saga-step-completed events when individual saga steps finish.
 * 
 * Features:
 * - Distributed transaction coordination
 * - Saga orchestration event publishing
 * - Compensation workflow triggering
 * - Comprehensive saga state tracking
 * - Failure handling and recovery
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaStepCompletedEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final String TOPIC = "saga-step-completed";

    /**
     * Publishes saga step completed event after step completion
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleSagaStepCompleted(SagaStepCompletedEventData eventData) {
        try {
            log.info("Publishing saga step completed event for saga: {} step: {}", 
                    eventData.getSagaId(), eventData.getStepName());

            // Create event
            SagaStepCompletedEvent event = createSagaStepCompletedEvent(eventData);

            // Publish event
            publishEvent(event, eventData);

            // Metrics
            metricsService.incrementCounter("saga.step.completed.event.published",
                Map.of(
                    "saga_type", eventData.getSagaType(),
                    "step_name", eventData.getStepName(),
                    "step_status", eventData.getStepStatus().toString()
                ));

            log.info("Successfully published saga step completed event: {} for saga: {} step: {}", 
                    event.getEventId(), eventData.getSagaId(), eventData.getStepName());

        } catch (Exception e) {
            log.error("Failed to publish saga step completed event for saga {} step {}: {}", 
                    eventData.getSagaId(), eventData.getStepName(), e.getMessage(), e);
            
            metricsService.incrementCounter("saga.step.completed.event.publish_failed");
            
            auditLogger.logError("SAGA_STEP_COMPLETED_EVENT_PUBLISH_FAILED",
                "system", eventData.getSagaId(), e.getMessage(),
                Map.of(
                    "sagaId", eventData.getSagaId(),
                    "stepName", eventData.getStepName()
                ));
        }
    }

    /**
     * Manually publish saga step completed event
     */
    public CompletableFuture<SendResult<String, Object>> publishSagaStepCompleted(
            SagaExecution saga, SagaStep step) {
        
        SagaStepCompletedEventData eventData = SagaStepCompletedEventData.builder()
            .sagaId(saga.getId())
            .sagaType(saga.getSagaType())
            .stepId(step.getId())
            .stepName(step.getStepName())
            .stepStatus(step.getStatus())
            .stepOutput(step.getOutput())
            .stepError(step.getErrorMessage())
            .executionTimeMs(step.getExecutionTimeMs())
            .completedAt(step.getCompletedAt())
            .compensationRequired(step.isCompensationRequired())
            .nextSteps(saga.getNextSteps(step))
            .sagaContext(saga.getContext())
            .build();

        SagaStepCompletedEvent event = createSagaStepCompletedEvent(eventData);
        return publishEvent(event, eventData);
    }

    private SagaStepCompletedEvent createSagaStepCompletedEvent(SagaStepCompletedEventData eventData) {
        return SagaStepCompletedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .sagaId(eventData.getSagaId())
            .sagaType(eventData.getSagaType())
            .stepId(eventData.getStepId())
            .stepName(eventData.getStepName())
            .stepStatus(eventData.getStepStatus().toString())
            .stepOutput(sanitizeStepOutput(eventData.getStepOutput()))
            .stepError(eventData.getStepError())
            .executionTimeMs(eventData.getExecutionTimeMs())
            .completedAt(eventData.getCompletedAt())
            .compensationRequired(eventData.isCompensationRequired())
            .nextSteps(eventData.getNextSteps())
            .sagaContext(sanitizeSagaContext(eventData.getSagaContext()))
            .timestamp(LocalDateTime.now())
            .build();
    }

    private Map<String, Object> sanitizeStepOutput(Map<String, Object> stepOutput) {
        if (stepOutput == null) {
            return null;
        }

        // Remove sensitive data from step output
        Map<String, Object> sanitized = new java.util.HashMap<>(stepOutput);
        
        // Remove sensitive financial data
        sanitized.remove("accountNumber");
        sanitized.remove("routingNumber");
        sanitized.remove("cardNumber");
        sanitized.remove("cvv");
        sanitized.remove("pin");
        sanitized.remove("apiKey");
        sanitized.remove("secret");
        sanitized.remove("token");
        
        return sanitized;
    }

    private Map<String, Object> sanitizeSagaContext(Map<String, Object> sagaContext) {
        if (sagaContext == null) {
            return null;
        }

        // Remove sensitive data from saga context but keep operational data
        Map<String, Object> sanitized = new java.util.HashMap<>(sagaContext);
        
        // Remove user PII but keep IDs for operational tracking
        sanitized.remove("userEmail");
        sanitized.remove("userPhone");
        sanitized.remove("userSSN");
        
        // Keep transaction IDs and operational data
        // sanitized.keep("transactionId", "paymentId", "walletId", etc.)
        
        return sanitized;
    }

    private CompletableFuture<SendResult<String, Object>> publishEvent(
            SagaStepCompletedEvent event, SagaStepCompletedEventData eventData) {
        try {
            // Use saga ID as partition key for ordering
            String partitionKey = eventData.getSagaId();

            var producerRecord = new org.apache.kafka.clients.producer.ProducerRecord<>(
                TOPIC, 
                partitionKey, 
                event
            );
            
            // Add headers for saga coordination
            producerRecord.headers().add("event-type", "saga-step-completed".getBytes());
            producerRecord.headers().add("saga-id", eventData.getSagaId().getBytes());
            producerRecord.headers().add("saga-type", eventData.getSagaType().getBytes());
            producerRecord.headers().add("step-name", eventData.getStepName().getBytes());
            producerRecord.headers().add("step-status", eventData.getStepStatus().toString().getBytes());
            producerRecord.headers().add("correlation-id", UUID.randomUUID().toString().getBytes());
            producerRecord.headers().add("source-service", "saga-orchestration-service".getBytes());
            producerRecord.headers().add("event-version", "1.0".getBytes());

            // Add compensation flag if needed
            if (eventData.isCompensationRequired()) {
                producerRecord.headers().add("compensation-required", "true".getBytes());
            }

            CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(producerRecord);

            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send saga step completed event for saga {} step {}: {}", 
                            eventData.getSagaId(), eventData.getStepName(), ex.getMessage(), ex);
                    
                    metricsService.incrementCounter("saga.step.completed.event.send_failed",
                        Map.of("step_name", eventData.getStepName()));
                } else {
                    log.debug("Saga step completed event sent successfully: partition={}, offset={}", 
                            result.getRecordMetadata().partition(), 
                            result.getRecordMetadata().offset());
                    
                    // Create audit trail
                    auditLogger.logSagaEvent(
                        "SAGA_STEP_COMPLETED_EVENT_PUBLISHED",
                        "system",
                        event.getEventId(),
                        eventData.getSagaType(),
                        "saga_event_producer",
                        true,
                        Map.of(
                            "sagaId", eventData.getSagaId(),
                            "stepId", eventData.getStepId(),
                            "stepName", eventData.getStepName(),
                            "stepStatus", eventData.getStepStatus().toString(),
                            "executionTimeMs", eventData.getExecutionTimeMs() != null ? eventData.getExecutionTimeMs().toString() : "N/A",
                            "compensationRequired", String.valueOf(eventData.isCompensationRequired()),
                            "topic", TOPIC,
                            "partition", String.valueOf(result.getRecordMetadata().partition()),
                            "offset", String.valueOf(result.getRecordMetadata().offset())
                        )
                    );
                }
            });

            return future;

        } catch (Exception e) {
            log.error("Exception while publishing saga step completed event for saga {} step {}: {}", 
                    eventData.getSagaId(), eventData.getStepName(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Data class for saga step completion event information
     */
    @lombok.Data
    @lombok.Builder
    public static class SagaStepCompletedEventData {
        private String sagaId;
        private String sagaType;
        private String stepId;
        private String stepName;
        private StepStatus stepStatus;
        private Map<String, Object> stepOutput;
        private String stepError;
        private Long executionTimeMs;
        private LocalDateTime completedAt;
        private boolean compensationRequired;
        private java.util.List<String> nextSteps;
        private Map<String, Object> sagaContext;
    }
}