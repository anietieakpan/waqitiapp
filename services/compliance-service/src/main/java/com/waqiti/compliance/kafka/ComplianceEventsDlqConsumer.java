package com.waqiti.compliance.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.compliance.service.ComplianceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Map;

/**
 * DLQ Consumer for general compliance events that failed to process.
 * Handles critical compliance failures affecting regulatory compliance operations.
 */
@Component
@Slf4j
public class ComplianceEventsDlqConsumer extends BaseDlqConsumer {

    private final ComplianceService complianceService;

    public ComplianceEventsDlqConsumer(DlqHandler dlqHandler,
                                      AuditService auditService,
                                      NotificationService notificationService,
                                      MeterRegistry meterRegistry,
                                      ComplianceService complianceService) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.complianceService = complianceService;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"compliance-events-DLQ"},
        groupId = "compliance-events-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "compliance-events-dlq", fallbackMethod = "handleComplianceEventsDlqFallback")
    public void handleComplianceEventsDlq(@Payload Object originalMessage,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         Acknowledgment acknowledgment,
                                         @Header Map<String, Object> headers) {

        String correlationId = extractCorrelationId(headers, originalMessage);
        log.info("Processing compliance events DLQ message: topic={}, partition={}, offset={}, correlationId={}",
            topic, partition, offset, correlationId);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String complianceId = extractComplianceId(originalMessage);
            String eventType = extractEventType(originalMessage);

            log.info("Processing compliance events DLQ: complianceId={}, eventType={}, messageId={}",
                complianceId, eventType, messageId);

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

        } catch (Exception e) {
            log.error("Error in compliance events DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "compliance-events-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "REGULATORY_COMPLIANCE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        return true; // All compliance events are critical
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String complianceId = extractComplianceId(originalMessage);
        String eventType = extractEventType(originalMessage);

        try {
            notificationService.sendComplianceAlert(
                String.format("CRITICAL: Compliance Event Failed - %s", eventType),
                String.format("Compliance event %s failed for ID %s. Error: %s",
                    eventType, complianceId, exceptionMessage),
                "URGENT"
            );

        } catch (Exception e) {
            log.error("Failed to send compliance events DLQ alerts: {}", e.getMessage());
        }
    }

    public void handleComplianceEventsDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                 int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);
    }

    private String extractComplianceId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object complianceId = messageMap.get("complianceId");
                return complianceId != null ? complianceId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract complianceId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractEventType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object eventType = messageMap.get("eventType");
                return eventType != null ? eventType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract eventType from message: {}", e.getMessage());
        }
        return null;
    }
}