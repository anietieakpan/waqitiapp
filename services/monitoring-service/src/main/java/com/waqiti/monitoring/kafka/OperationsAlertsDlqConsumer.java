package com.waqiti.monitoring.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.dlq.DlqHandler;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.monitoring.service.AlertService;
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
 * DLQ Consumer for operations alerts that failed to process.
 * Handles critical operational alert failures affecting system monitoring.
 */
@Component
@Slf4j
public class OperationsAlertsDlqConsumer extends BaseDlqConsumer {

    private final AlertService alertService;

    public OperationsAlertsDlqConsumer(DlqHandler dlqHandler,
                                      AuditService auditService,
                                      NotificationService notificationService,
                                      MeterRegistry meterRegistry,
                                      AlertService alertService) {
        super(dlqHandler, auditService, notificationService, meterRegistry);
        this.alertService = alertService;
    }

    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 5000, multiplier = 2.0),
        include = {Exception.class}
    )
    @KafkaListener(
        topics = {"operations-alerts-dlq"},
        groupId = "operations-alerts-dlq-consumer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation.level=read_committed",
            "spring.kafka.consumer.enable.auto.commit=false"
        }
    )
    @DltHandler
    @Transactional
    @CircuitBreaker(name = "operations-alerts-dlq", fallbackMethod = "handleOperationsAlertsDlqFallback")
    public void handleOperationsAlertsDlq(@Payload Object originalMessage,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         Acknowledgment acknowledgment,
                                         @Header Map<String, Object> headers) {

        String correlationId = extractCorrelationId(headers, originalMessage);
        log.info("Processing operations alerts DLQ message: topic={}, partition={}, offset={}, correlationId={}",
            topic, partition, offset, correlationId);

        super.handleDlqMessage(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, headers);
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic,
                                            String exceptionMessage, String messageId) {
        try {
            String alertId = extractAlertId(originalMessage);
            String alertType = extractAlertType(originalMessage);
            String severity = extractSeverity(originalMessage);

            log.info("Processing operations alerts DLQ: alertId={}, type={}, severity={}, messageId={}",
                alertId, alertType, severity, messageId);

            if (alertId != null) {
                assessSystemImpact(alertId, alertType, severity, originalMessage, messageId);
                handleOperationalEscalation(alertId, originalMessage, messageId);
            }

            generateDomainSpecificAlerts(originalMessage, topic, exceptionMessage, messageId);

        } catch (Exception e) {
            log.error("Error in operations alerts DLQ domain-specific processing: messageId={}, error={}",
                messageId, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    protected String getConsumerName() {
        return "operations-alerts-dlq";
    }

    @Override
    protected String getBusinessDomain() {
        return "SYSTEM_MONITORING";
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        String severity = extractSeverity(originalMessage);
        return "CRITICAL".equals(severity) || "HIGH".equals(severity);
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic,
                                              String exceptionMessage, String messageId) {
        String alertId = extractAlertId(originalMessage);
        String alertType = extractAlertType(originalMessage);
        String severity = extractSeverity(originalMessage);

        try {
            notificationService.sendOperationalAlert(
                String.format("CRITICAL: Operations Alert Failed - %s", alertType),
                String.format("Operations alert %s (severity: %s) failed for alert ID %s. Error: %s",
                    alertType, severity, alertId, exceptionMessage),
                "URGENT"
            );

            // Escalate critical alerts to operations team
            if ("CRITICAL".equals(severity)) {
                notificationService.sendExecutiveAlert(
                    "Critical Operations Alert Processing Failed",
                    String.format("Critical alert %s failed processing. " +
                        "System monitoring may be compromised.", alertType)
                );
            }

        } catch (Exception e) {
            log.error("Failed to send operations alerts DLQ alerts: {}", e.getMessage());
        }
    }

    private void assessSystemImpact(String alertId, String alertType, String severity, Object originalMessage, String messageId) {
        if ("SYSTEM_DOWN".equals(alertType) || "SERVICE_UNAVAILABLE".equals(alertType)) {
            notificationService.sendCriticalAlert(
                "System Availability Alert Failed",
                String.format("System availability alert %s (severity: %s) failed for alert %s. " +
                    "System status monitoring may be affected.", alertType, severity, alertId),
                Map.of("alertId", alertId, "alertType", alertType, "severity", severity, "urgency", "IMMEDIATE")
            );
        }
    }

    private void handleOperationalEscalation(String alertId, Object originalMessage, String messageId) {
        String alertType = extractAlertType(originalMessage);
        if (alertType != null && alertType.contains("PERFORMANCE")) {
            notificationService.sendOperationalAlert(
                "Performance Alert Processing Failed",
                String.format("Performance alert %s failed processing. " +
                    "System performance monitoring may be degraded.", alertId),
                "HIGH"
            );
        }
    }

    public void handleOperationsAlertsDlqFallback(Object originalMessage, String topic, String exceptionMessage,
                                                 int partition, long offset, Acknowledgment acknowledgment, Exception ex) {
        super.handleDlqFallback(originalMessage, topic, exceptionMessage, partition, offset, acknowledgment, ex);

        String alertId = extractAlertId(originalMessage);
        if (alertId != null) {
            try {
                alertService.markForUrgentReview(alertId, "OPERATIONS_ALERTS_DLQ_CIRCUIT_BREAKER");
            } catch (Exception e) {
                log.error("Error in operations alerts DLQ fallback: {}", e.getMessage());
            }
        }
    }

    private String extractAlertId(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object alertId = messageMap.get("alertId");
                return alertId != null ? alertId.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not extract alertId from message: {}", e.getMessage());
        }
        return null;
    }

    private String extractAlertType(Object originalMessage) {
        try {
            if (originalMessage instanceof Map) {
                Map<?, ?> messageMap = (Map<?, ?>) originalMessage;
                Object alertType = messageMap.get("alertType");
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
}