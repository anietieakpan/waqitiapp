package com.waqiti.common.compensation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Alerting service for compensation system.
 * Sends notifications to operations team for critical compensation events.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CompensationAlertService {

    private final KafkaTemplate<String, Map<String, Object>> alertKafkaTemplate;
    private static final String ALERT_TOPIC = "system-alerts";

    /**
     * Send alert for critical compensation
     */
    public void sendCriticalCompensationAlert(CompensationTransaction compensation) {
        Map<String, Object> alert = createBaseAlert("CRITICAL_COMPENSATION_QUEUED", "CRITICAL");
        alert.put("compensationId", compensation.getCompensationId());
        alert.put("type", compensation.getType());
        alert.put("originalTransactionId", compensation.getOriginalTransactionId());
        alert.put("reason", compensation.getReason());

        sendAlert(alert);
    }

    /**
     * Send alert for compensation requiring manual intervention
     */
    public void sendManualInterventionRequired(
            CompensationTransaction compensation, Exception error) {

        Map<String, Object> alert = createBaseAlert(
            "COMPENSATION_MANUAL_INTERVENTION_REQUIRED", "HIGH");

        alert.put("compensationId", compensation.getCompensationId());
        alert.put("type", compensation.getType());
        alert.put("retries", compensation.getCurrentRetry());
        alert.put("error", error.getMessage());
        alert.put("originalTransactionId", compensation.getOriginalTransactionId());

        sendAlert(alert);

        log.error("MANUAL INTERVENTION REQUIRED - Compensation: {} - Error: {}",
            compensation.getCompensationId(), error.getMessage(), error);
    }

    /**
     * Send alert for compensation system error
     */
    public void sendCompensationSystemError(String message, Exception error) {
        Map<String, Object> alert = createBaseAlert("COMPENSATION_SYSTEM_ERROR", "CRITICAL");
        alert.put("message", message);
        alert.put("error", error.getMessage());
        alert.put("stackTrace", getStackTraceString(error));

        sendAlert(alert);

        log.error("COMPENSATION SYSTEM ERROR - {}", message, error);
    }

    /**
     * Send alert for compensation sent to DLQ
     */
    public void sendCompensationDLQAlert(CompensationTransaction compensation) {
        Map<String, Object> alert = createBaseAlert("COMPENSATION_SENT_TO_DLQ", "HIGH");
        alert.put("compensationId", compensation.getCompensationId());
        alert.put("type", compensation.getType());
        alert.put("retries", compensation.getCurrentRetry());
        alert.put("originalTransactionId", compensation.getOriginalTransactionId());

        sendAlert(alert);

        log.error("COMPENSATION SENT TO DLQ - {}", compensation.getCompensationId());
    }

    /**
     * Create base alert structure
     */
    private Map<String, Object> createBaseAlert(String alertType, String severity) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", alertType);
        alert.put("severity", severity);
        alert.put("timestamp", LocalDateTime.now().toString());
        alert.put("service", "compensation-service");
        return alert;
    }

    /**
     * Send alert to Kafka topic
     */
    private void sendAlert(Map<String, Object> alert) {
        try {
            alertKafkaTemplate.send(ALERT_TOPIC, alert.get("alertType").toString(), alert);
        } catch (Exception e) {
            log.error("Failed to send alert to Kafka", e);
            // Fallback: at minimum log to console so ops can see it
            log.error("ALERT (Kafka send failed): {}", alert);
        }
    }

    /**
     * Get stack trace as string
     */
    private String getStackTraceString(Exception error) {
        java.io.StringWriter sw = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(sw));
        return sw.toString();
    }
}
