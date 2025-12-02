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
 * DLQ Consumer for compliance events retry failures.
 * Handles critical compliance event processing failures requiring regulatory attention.
 */
@Component
@Slf4j
public class ComplianceEventsRetryDlqConsumer extends BaseDlqConsumer {

    private final ComplianceService complianceService;

    public ComplianceEventsRetryDlqConsumer(DlqHandler dlqHandler,
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
        topics = {"compliance-events-retry-dlq"},
        groupId = "compliance-events-retry-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "compliance-events-retry-dlq", fallbackMethod = "handleComplianceEventsRetryDlqFallback")
    public void handleComplianceEventsRetryDlq(@Payload Object originalMessage,
                                              @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                              @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                              @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                              @Header(KafkaHeaders.OFFSET) long offset,
                                              Acknowledgment acknowledgment,
                                              @Header Map<String, Object> headers) {

        String correlationId = extractCorrelationId(headers, originalMessage);
        log.info("Processing compliance events retry DLQ message: topic={}, partition={}, offset={}, correlationId={}",
            topic, partition, offset, correlationId);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String complianceId = extractComplianceId(originalMessage);
            String eventType = extractEventType(originalMessage);
            String regulatoryType = extractRegulatoryType(originalMessage);

            log.info("Processing compliance events retry DLQ: complianceId={}, eventType={}, regulatory={}, messageId={}",
                complianceId, eventType, regulatoryType, messageId);

            if (complianceId != null) {
                assessRegulatoryImpact(complianceId, regulatoryType, originalMessage, messageId);
                handleComplianceReporting(complianceId, originalMessage, messageId);
            }

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

        } catch (Exception e) {
            log.error("Error in compliance events retry DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "compliance-events-retry-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "REGULATORY_COMPLIANCE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String eventType = extractEventType(originalMessage);
        return "REGULATORY_REPORTING".equals(eventType) || "AUDIT_TRAIL".equals(eventType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String complianceId = extractComplianceId(originalMessage);
        String eventType = extractEventType(originalMessage);

        try {
            notificationService.sendComplianceAlert(
                String.format("CRITICAL: Compliance Event Retry Failed - %s", eventType),
                String.format("Compliance event retry %s failed for ID %s. Error: %s",
                    eventType, complianceId, exceptionMessage),
                "URGENT"
            );

        } catch (Exception e) {
            log.error("Failed to send compliance events retry DLQ alerts: {}", e.getMessage());
        }
    }

    private void assessRegulatoryImpact(String complianceId, String regulatoryType, Object originalMessage, String messageId) {
        if ("SOX_COMPLIANCE".equals(regulatoryType) || "BSA_AML".equals(regulatoryType)) {
            notificationService.sendExecutiveAlert(
                "Critical Regulatory Compliance Failure",
                String.format("Critical regulatory event %s failed for compliance ID %s. " +
                    "Immediate regulatory attention required.", regulatoryType, complianceId)
            );
        }
    }

    private void handleComplianceReporting(String complianceId, Object originalMessage, String messageId) {
        String eventType = extractEventType(originalMessage);
        if (eventType != null && eventType.contains("REPORT")) {
            notificationService.sendComplianceAlert(
                "Compliance Reporting Failed",
                String.format("Compliance reporting event failed for ID %s. " +
                    "Review regulatory reporting deadlines.", complianceId),
                "HIGH"
            );
        }
    }

    public void handleComplianceEventsRetryDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                      int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String complianceId = extractComplianceId(originalMessage);
        if (complianceId != null) {
            try {
                complianceService.markForUrgentReview(complianceId, "COMPLIANCE_EVENTS_RETRY_DLQ_CIRCUIT_BREAKER");
            } catch (Exception e) {
                log.error("Error in compliance events retry DLQ fallback: {}", e.getMessage());
            }
        }
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

    private String extractRegulatoryType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object regulatoryType = messageMap.get("regulatoryType");
                return regulatoryType != null ? regulatoryType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract regulatoryType from message: {}", e.getMessage());
        }
        return null;
    }
}