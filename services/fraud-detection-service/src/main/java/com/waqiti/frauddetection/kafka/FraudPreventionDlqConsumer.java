package com.waqiti.frauddetection.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.frauddetection.service.FraudPreventionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Instant;
import java.util.Map;

/**
 * DLQ Consumer for fraud prevention failures.
 * Handles critical fraud prevention processing errors with immediate security escalation.
 */
@Component
@Slf4j
public class FraudPreventionDlqConsumer extends BaseDlqConsumer {

    private final FraudPreventionService fraudPreventionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FraudPreventionDlqConsumer(DlqHandler dlqHandler,
                                    AuditService auditService,
                                    NotificationService notificationService,
                                    MeterRegistry meterRegistry,
                                    FraudPreventionService fraudPreventionService,
                                    KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.fraudPreventionService = fraudPreventionService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"fraud-prevention.DLQ"},
        groupId = "fraud-prevention-dlq-consumer-group",
        containerFactory = "criticalFraudKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "fraud-prevention-dlq", fallbackMethod = "handleFraudPreventionDlqFallback")
    public void handleFraudPreventionDlq(@Payload Object originalMessage,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                       @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                       @Header(KafkaHeaders.OFFSET) long offset,
                                       Acknowledgment acknowledgment,
                                       @Header Map<String, Object> headers) {

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String preventionId = extractPreventionId(originalMessage);
            String customerId = extractCustomerId(originalMessage);
            String preventionType = extractPreventionType(originalMessage);

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            kafkaTemplate.send("manual-prevention-review-queue", Map.of(
                "preventionId", preventionId != null ? preventionId : "unknown",
                "customerId", customerId != null ? customerId : "unknown",
                "preventionType", preventionType,
                "reviewReason", "FRAUD_PREVENTION_DLQ_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "timestamp", Instant.now()
            ));

        } catch (Exception e) {
            log.error("Error in fraud prevention DLQ processing: messageId={}, error={}", messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "fraud-prevention-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FRAUD_PREVENTION";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        return true; // All fraud prevention failures are critical
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String preventionId = extractPreventionId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        String preventionType = extractPreventionType(originalMessage);

        try {
            String alertMessage = String.format(
                "🚨 FRAUD PREVENTION FAILURE 🚨\n\n" +
                "Prevention ID: %s\nCustomer ID: %s\nType: %s\nError: %s\n\n" +
                "IMMEDIATE fraud prevention system escalation required.",
                preventionId != null ? preventionId : "unknown",
                customerId != null ? customerId : "unknown",
                preventionType != null ? preventionType : "unknown",
                exceptionMessage
            );

            notificationService.sendFraudAlert("CRITICAL: Fraud Prevention Failed", alertMessage, "CRITICAL");
            notificationService.sendSecurityAlert("Fraud Prevention System Alert", alertMessage, "HIGH");

        } catch (Exception e) {
            log.error("Failed to send fraud prevention DLQ notifications: {}", e.getMessage());
        }
    }

    public void handleFraudPreventionDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                               int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        notificationService.sendExecutiveAlert(
            "EMERGENCY: Fraud Prevention DLQ Circuit Breaker",
            "CRITICAL: Fraud prevention systems circuit breaker triggered. IMMEDIATE ESCALATION REQUIRED."
        );
    }

    // Data extraction methods
    private String extractPreventionId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object id = messageMap.get("preventionId");
                if (id == null) id = messageMap.get("id");
                return id != null ? id.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract preventionId: {}", e.getMessage());
        }
        return null;
    }

    private String extractCustomerId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object customerId = messageMap.get("customerId");
                if (customerId == null) customerId = messageMap.get("userId");
                return customerId != null ? customerId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract customerId: {}", e.getMessage());
        }
        return null;
    }

    private String extractPreventionType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object type = messageMap.get("preventionType");
                if (type == null) type = messageMap.get("type");
                return type != null ? type.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract preventionType: {}", e.getMessage());
        }
        return null;
    }
}