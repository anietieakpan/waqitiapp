package com.waqiti.frauddetection.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.frauddetection.service.FraudAlertService;
import com.waqiti.frauddetection.service.FraudValidationService;
import com.waqiti.frauddetection.repository.FraudAlertRepository;
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
 * DLQ Consumer for fraud alert failures.
 * Handles critical fraud alert processing errors with immediate notification escalation.
 */
@Component
@Slf4j
public class FraudAlertDlqConsumer extends BaseDlqConsumer {

    private final FraudAlertService fraudAlertService;
    private final FraudValidationService fraudValidationService;
    private final FraudAlertRepository fraudAlertRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public FraudAlertDlqConsumer(DlqHandler dlqHandler,
                               AuditService auditService,
                               NotificationService notificationService,
                               MeterRegistry meterRegistry,
                               FraudAlertService fraudAlertService,
                               FraudValidationService fraudValidationService,
                               FraudAlertRepository fraudAlertRepository,
                               KafkaTemplate<String, Object> kafkaTemplate) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.fraudAlertService = fraudAlertService;
        this.fraudValidationService = fraudValidationService;
        this.fraudAlertRepository = fraudAlertRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
        topics = {"fraud-alert.DLQ"},
        groupId = "fraud-alert-dlq-consumer-group",
        containerFactory = "criticalFraudKafkaListenerContainerFactory"
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "fraud-alert-dlq", fallbackMethod = "handleFraudAlertDlqFallback")
    public void handleFraudAlertDlq(@Payload Object originalMessage,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  Acknowledgment acknowledgment,
                                  @Header Map<String, Object> headers) {

        log.info("Processing fraud alert DLQ message: topic={}, partition={}, offset={}",
            topic, partition, offset);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String alertId = extractAlertId(originalMessage);
            String customerId = extractCustomerId(originalMessage);
            String alertType = extractAlertType(originalMessage);
            String severity = extractSeverity(originalMessage);
            String alertStatus = extractAlertStatus(originalMessage);
            String correlationId = extractCorrelationId(null, originalMessage);

            log.info("Processing fraud alert DLQ: alertId={}, customerId={}, alertType={}, severity={}, messageId={}",
                alertId, customerId, alertType, severity, messageId);

            // Generate critical security alerts for failed alert processing
            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

            // Handle emergency alert routing
            handleEmergencyAlertRouting(alertId, customerId, alertType, severity, originalMessage, messageId);

            // Trigger manual alert review
            triggerManualAlertReview(alertId, customerId, alertType, severity, messageId);

        } catch (Exception e) {
            log.error("Error in fraud alert DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "fraud-alert-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "FRAUD_ALERTING";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String alertType = extractAlertType(originalMessage);
        String severity = extractSeverity(originalMessage);
        return isCriticalAlert(alertType, severity);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String alertId = extractAlertId(originalMessage);
        String customerId = extractCustomerId(originalMessage);
        String alertType = extractAlertType(originalMessage);
        String severity = extractSeverity(originalMessage);

        try {
            String alertTitle = String.format("FRAUD ALERT CRITICAL: Alert Processing Failed - %s",
                alertType != null ? alertType : "Unknown Alert");
            String alertMessage = String.format(
                "🚨 FRAUD ALERT SYSTEM FAILURE 🚨\n\n" +
                "A fraud alert operation has FAILED and requires IMMEDIATE attention:\n\n" +
                "Alert ID: %s\n" +
                "Customer ID: %s\n" +
                "Alert Type: %s\n" +
                "Severity: %s\n" +
                "Error: %s\n\n" +
                "🚨 CRITICAL: Failed fraud alerts can result in unnotified security threats.\n" +
                "IMMEDIATE fraud alert system and notification escalation required.",
                alertId != null ? alertId : "unknown",
                customerId != null ? customerId : "unknown",
                alertType != null ? alertType : "unknown",
                severity != null ? severity : "unknown",
                exceptionMessage
            );

            notificationService.sendFraudAlert(alertTitle, alertMessage, "CRITICAL");
            notificationService.sendSecurityAlert("URGENT: Fraud Alert System Failed", alertMessage, "CRITICAL");

        } catch (Exception e) {
            log.error("Failed to send fraud alert DLQ notifications: {}", e.getMessage());
        }
    }

    private void handleEmergencyAlertRouting(String alertId, String customerId, String alertType, String severity,
                                           Object originalMessage, String messageId) {
        try {
            if (isCriticalAlert(alertType, severity)) {
                // Route critical alerts through emergency channels
                kafkaTemplate.send("emergency-fraud-alerts", Map.of(
                    "alertId", alertId != null ? alertId : "unknown",
                    "customerId", customerId != null ? customerId : "unknown",
                    "alertType", alertType,
                    "severity", severity,
                    "escalationReason", "DLQ_FAILURE",
                    "messageId", messageId,
                    "timestamp", Instant.now()
                ));
            }
        } catch (Exception e) {
            log.error("Error handling emergency alert routing: alertId={}, error={}", alertId, e.getMessage());
        }
    }

    private void triggerManualAlertReview(String alertId, String customerId, String alertType, String severity, String messageId) {
        try {
            kafkaTemplate.send("manual-alert-review-queue", Map.of(
                "alertId", alertId != null ? alertId : "unknown",
                "customerId", customerId != null ? customerId : "unknown",
                "alertType", alertType,
                "severity", severity,
                "reviewReason", "FRAUD_ALERT_DLQ_FAILURE",
                "priority", "HIGH",
                "messageId", messageId,
                "timestamp", Instant.now()
            ));
        } catch (Exception e) {
            log.error("Error triggering manual alert review: alertId={}, error={}", alertId, e.getMessage());
        }
    }

    public void handleFraudAlertDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                          int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        notificationService.sendExecutiveAlert(
            "EMERGENCY: Fraud Alert DLQ Circuit Breaker",
            String.format("CRITICAL ALERT FAILURE: Fraud alert DLQ circuit breaker triggered. " +
                "Complete failure of fraud alert systems. IMMEDIATE ESCALATION REQUIRED.")
        );
    }

    private boolean isCriticalAlert(String alertType, String severity) {
        return (alertType != null && alertType.contains("CRITICAL")) ||
               (severity != null && (severity.contains("HIGH") || severity.contains("CRITICAL")));
    }

    // Data extraction methods
    private String extractAlertId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object alertId = messageMap.get("alertId");
                if (alertId == null) alertId = messageMap.get("id");
                return alertId != null ? alertId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract alertId from message: {}", e.getMessage());
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
            log.debug("Could not extract customerId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAlertType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object alertType = messageMap.get("alertType");
                if (alertType == null) alertType = messageMap.get("type");
                return alertType != null ? alertType.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract alertType from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractSeverity(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object severity = messageMap.get("severity");
                return severity != null ? severity.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract severity from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAlertStatus(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object status = messageMap.get("alertStatus");
                if (status == null) status = messageMap.get("status");
                return status != null ? status.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract alertStatus from message: {}", e.getMessage());
        }
        return null;
    }
}