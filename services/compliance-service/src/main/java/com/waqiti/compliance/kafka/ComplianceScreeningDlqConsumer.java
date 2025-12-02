package com.waqiti.compliance.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.compliance.service.ComplianceScreeningService;
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
 * DLQ Consumer for compliance screening failures.
 * Handles critical compliance screening processing errors with immediate regulatory escalation.
 */
@Component
@Slf4j
public class ComplianceScreeningDlqConsumer extends BaseDlqConsumer {

    private final ComplianceScreeningService complianceScreeningService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ComplianceScreeningDlqConsumer(DlqHandler dlqHandler,
                                        AuditService auditService,
                                        NotificationService notificationService,
                                        MeterRegistry meterRegistry,
                                        ComplianceScreeningService complianceScreeningService,
                                        KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.complianceScreeningService = complianceScreeningService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"compliance-screening.DLQ"},
        groupId = "compliance-screening-dlq-consumer-group",
        containerFactory = "criticalComplianceKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "compliance-screening-dlq", fallbackMethod = "handleComplianceScreeningDlqFallback")
    public void handleComplianceScreeningDlq(@Payload Object originalMessage,
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
            String screeningId = extractScreeningId(originalMessage);
            String customerId = extractCustomerId(originalMessage);
            String screeningType = extractScreeningType(originalMessage);
            String riskLevel = extractRiskLevel(originalMessage);

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Handle emergency screening bypass for critical transactions
            if (isCriticalScreening(screeningType)) {
                handleEmergencyScreeningEscalation(screeningId, customerId, screeningType, messageId);
            }

            kafkaTemplate.send("manual-compliance-screening-review-queue", Map.of(
                "screeningId", screeningId != null ? screeningId : "unknown",
                "customerId", customerId != null ? customerId : "unknown",
                "screeningType", screeningType,
                "riskLevel", riskLevel,
                "reviewReason", "COMPLIANCE_SCREENING_DLQ_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "timestamp", Instant.now()
            ));

        } catch (Exception e) {
            log.error("Error in compliance screening DLQ processing: messageId={}, error={}", messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "compliance-screening-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "COMPLIANCE";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String screeningType = extractScreeningType(originalMessage);
        return isCriticalScreening(screeningType);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String screeningId = extractScreeningId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        String screeningType = extractScreeningType(originalMessage);
        String riskLevel = extractRiskLevel(originalMessage);

        try {
            String alertMessage = String.format(
                "🚨 COMPLIANCE SCREENING FAILURE 🚨\n\n" +
                "Screening ID: %s\nCustomer ID: %s\nType: %s\nRisk Level: %s\nError: %s\n\n" +
                "CRITICAL: Failed compliance screening may violate regulatory requirements.\n" +
                "IMMEDIATE compliance and regulatory escalation required.",
                screeningId != null ? screeningId : "unknown",
                customerId != null ? customerId : "unknown",
                screeningType != null ? screeningType : "unknown",
                riskLevel != null ? riskLevel : "unknown",
                exceptionMessage
            );

            notificationService.sendComplianceAlert("CRITICAL: Compliance Screening Failed", alertMessage, "CRITICAL");
            notificationService.sendRegulatoryAlert("Regulatory Screening Risk", alertMessage, "HIGH");

            if (isAMLScreening(screeningType)) {
                notificationService.sendAMLAlert("AML Screening Failed", alertMessage, "CRITICAL");
            }

            if (isSanctionsScreening(screeningType)) {
                notificationService.sendSanctionsAlert("Sanctions Screening Failed", alertMessage, "CRITICAL");
            }

        } catch (Exception e) {
            log.error("Failed to send compliance screening DLQ notifications: {}", e.getMessage());
        }
    }

    private void handleEmergencyScreeningEscalation(String screeningId, String customerId, String screeningType, String messageId) {
        try {
            kafkaTemplate.send("emergency-compliance-escalation", Map.of(
                "screeningId", screeningId,
                "customerId", customerId,
                "screeningType", screeningType,
                "escalationReason", "CRITICAL_SCREENING_DLQ_FAILURE",
                "messageId", messageId,
                "timestamp", Instant.now()
            ));

            notificationService.sendExecutiveAlert(
                "EMERGENCY: Critical Compliance Screening Failed",
                String.format("Critical compliance screening %s failed for customer %s. " +
                    "IMMEDIATE C-LEVEL and regulatory escalation required.", screeningType, customerId)
            );
        } catch (Exception e) {
            log.error("Error handling emergency screening escalation: {}", e.getMessage());
        }
    }

    public void handleComplianceScreeningDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                   int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        notificationService.sendExecutiveAlert(
            "EMERGENCY: Compliance Screening DLQ Circuit Breaker",
            "CRITICAL: Compliance screening systems circuit breaker triggered. IMMEDIATE REGULATORY ESCALATION REQUIRED."
        );
    }

    private boolean isCriticalScreening(String screeningType) {
        return screeningType != null && (
            screeningType.contains("AML") || screeningType.contains("SANCTIONS") ||
            screeningType.contains("PEP") || screeningType.contains("OFAC")
        );
    }

    private boolean isAMLScreening(String screeningType) {
        return screeningType != null && screeningType.contains("AML");
    }

    private boolean isSanctionsScreening(String screeningType) {
        return screeningType != null && (screeningType.contains("SANCTIONS") || screeningType.contains("OFAC"));
    }

    // Data extraction methods
    private String extractScreeningId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object id = messageMap.get("screeningId");
                if (id == null) id = messageMap.get("id");
                return id != null ? id.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract screeningId: {}", e.getMessage());
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

    private String extractScreeningType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object type = messageMap.get("screeningType");
                if (type == null) type = messageMap.get("type");
                return type != null ? type.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract screeningType: {}", e.getMessage());
        }
        return null;
    }

    private String extractRiskLevel(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object risk = messageMap.get("riskLevel");
                if (risk == null) risk = messageMap.get("risk");
                return risk != null ? risk.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract riskLevel: {}", e.getMessage());
        }
        return null;
    }
}