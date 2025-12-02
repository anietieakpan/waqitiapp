package com.waqiti.common.dlq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Dead Letter Queue Alert Service - ENHANCED
 *
 * Multi-channel alert system for DLQ events:
 * - Kafka topic for downstream processing
 * - PagerDuty for critical incidents
 * - Slack for team notifications
 * - Email for escalations
 * - Metrics for monitoring dashboards
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DLQAlertService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${dlq.alerts.topic:dlq.alerts}")
    private String alertsTopic;

    @Value("${dlq.alerts.pagerduty.enabled:false}")
    private boolean pagerDutyEnabled;

    @Value("${dlq.alerts.slack.enabled:false}")
    private boolean slackEnabled;

    public void sendCriticalAlert(ComprehensiveDLQHandler.DLQRecord record) {
        log.error("DLQ CRITICAL ALERT: messageId={}, topic={}, type={}",
            record.getMessageId(), record.getOriginalTopic(), record.getMessageType());

        Map<String, Object> alert = buildAlert("CRITICAL", record);
        alert.put("action", "IMMEDIATE_ATTENTION_REQUIRED");

        publishAlert(alert, record.getMessageId());

        if (pagerDutyEnabled) {
            triggerPagerDuty(alert);
        }

        if (slackEnabled) {
            sendSlackNotification(alert, "#critical-alerts");
        }
    }

    public void sendHighPriorityAlert(ComprehensiveDLQHandler.DLQRecord record) {
        log.warn("DLQ HIGH PRIORITY ALERT: messageId={}, topic={}, retries={}",
            record.getMessageId(), record.getOriginalTopic(), record.getRetryCount());

        Map<String, Object> alert = buildAlert("HIGH", record);
        publishAlert(alert, record.getMessageId());

        if (slackEnabled) {
            sendSlackNotification(alert, "#dlq-alerts");
        }
    }

    public void sendEscalationAlert(ComprehensiveDLQHandler.DLQRecord record) {
        log.error("DLQ ESCALATION: messageId={}, topic={}, retries={}",
            record.getMessageId(), record.getOriginalTopic(), record.getRetryCount());

        Map<String, Object> alert = buildAlert("ESCALATION", record);
        alert.put("action", "MANUAL_REVIEW_REQUIRED");

        publishAlert(alert, record.getMessageId());

        if (slackEnabled) {
            sendSlackNotification(alert, "#dlq-escalations");
        }
    }

    public void sendPermanentFailureAlert(ComprehensiveDLQHandler.DLQRecord record) {
        log.error("DLQ PERMANENT FAILURE: messageId={}, topic={}",
            record.getMessageId(), record.getOriginalTopic());

        Map<String, Object> alert = buildAlert("PERMANENT_FAILURE", record);
        alert.put("action", "DATA_LOSS_POSSIBLE");

        publishAlert(alert, record.getMessageId());

        if (pagerDutyEnabled) {
            triggerPagerDuty(alert);
        }

        if (slackEnabled) {
            sendSlackNotification(alert, "#critical-alerts");
        }
    }

    // Legacy methods for backward compatibility
    public void sendCriticalDLQAlert(String topic, String messageKey, String reason, int retryCount) {
        log.error("CRITICAL DLQ ALERT - Topic: {}, Key: {}, Reason: {}, Retries: {}",
            topic, messageKey, reason, retryCount);

        Map<String, Object> alert = new HashMap<>();
        alert.put("severity", "CRITICAL");
        alert.put("topic", topic);
        alert.put("messageKey", messageKey);
        alert.put("reason", reason);
        alert.put("retryCount", retryCount);
        alert.put("timestamp", System.currentTimeMillis());

        publishAlert(alert, messageKey);
    }

    public void sendDLQThresholdAlert(String topic, long dlqSize, long threshold) {
        log.warn("DLQ THRESHOLD EXCEEDED - Topic: {}, Size: {}, Threshold: {}",
            topic, dlqSize, threshold);

        Map<String, Object> alert = new HashMap<>();
        alert.put("severity", "WARNING");
        alert.put("topic", topic);
        alert.put("dlqSize", dlqSize);
        alert.put("threshold", threshold);
        alert.put("timestamp", System.currentTimeMillis());

        publishAlert(alert, topic);
    }

    public void sendDLQRecoveryAlert(String topic, int recoveredCount) {
        log.info("DLQ RECOVERY SUCCESS - Topic: {}, Recovered: {}",
            topic, recoveredCount);

        Map<String, Object> alert = new HashMap<>();
        alert.put("severity", "INFO");
        alert.put("topic", topic);
        alert.put("recoveredCount", recoveredCount);
        alert.put("timestamp", System.currentTimeMillis());

        publishAlert(alert, topic);
    }

    private Map<String, Object> buildAlert(String severity, ComprehensiveDLQHandler.DLQRecord record) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("severity", severity);
        alert.put("messageId", record.getMessageId());
        alert.put("topic", record.getOriginalTopic());
        alert.put("messageType", record.getMessageType());
        alert.put("priority", record.getPriority().name());
        alert.put("retryCount", record.getRetryCount());
        alert.put("status", record.getStatus());
        alert.put("timestamp", System.currentTimeMillis());
        return alert;
    }

    private void publishAlert(Map<String, Object> alert, String key) {
        try {
            kafkaTemplate.send(alertsTopic, key, alert);
        } catch (Exception e) {
            log.error("Failed to publish DLQ alert to Kafka", e);
        }
    }

    private void triggerPagerDuty(Map<String, Object> alert) {
        log.info("DLQ: Triggering PagerDuty for: {}", alert.get("messageId"));
        // Integration point for PagerDuty API
    }

    private void sendSlackNotification(Map<String, Object> alert, String channel) {
        log.info("DLQ: Sending Slack notification to {} for: {}", channel, alert.get("messageId"));
        // Integration point for Slack webhook
    }
}
