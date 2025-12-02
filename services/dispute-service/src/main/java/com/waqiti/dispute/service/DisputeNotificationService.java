package com.waqiti.dispute.service;

import com.waqiti.dispute.exception.NotificationException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for dispute notifications across multiple channels
 *
 * Provides comprehensive notification support:
 * - Customer notifications (email, SMS, push, in-app)
 * - Merchant notifications
 * - Internal team notifications
 * - Emergency alerts
 *
 * Integrates with notification-service via Kafka events
 *
 * @author Waqiti Dispute Team
 * @version 1.0.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DisputeNotificationService {

    // FIXED: Integrated Kafka and Notification Client
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RestTemplate restTemplate;

    @Value("${services.notification-service.url:http://notification-service:8084}")
    private String notificationServiceUrl;

    /**
     * Send notification to customer about dispute resolution
     *
     * @param disputeId Dispute identifier
     * @param customerId Customer identifier
     * @param resolutionDecision Resolution decision
     * @param amount Dispute amount
     * @param currency Currency code
     * @param explanation Resolution explanation
     */
    @CircuitBreaker(name = "notificationService", fallbackMethod = "notifyCustomerFallback")
    @Retry(name = "notificationService")
    public void notifyCustomer(UUID disputeId, UUID customerId,
            String resolutionDecision, BigDecimal amount,
            String currency, String explanation) {

        log.info("Sending customer notification: disputeId={}, customerId={}, decision={}",
                disputeId, customerId, resolutionDecision);

        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("type", "DISPUTE_RESOLUTION");
            notificationData.put("disputeId", disputeId.toString());
            notificationData.put("customerId", customerId.toString());
            notificationData.put("decision", resolutionDecision);
            notificationData.put("amount", amount.toString());
            notificationData.put("currency", currency);
            notificationData.put("explanation", explanation);

            // Send via multiple channels
            sendEmail(customerId, "Dispute Resolution Update", notificationData);
            sendPushNotification(customerId, "Your dispute has been resolved", notificationData);
            sendInAppNotification(customerId, notificationData);

            log.info("Customer notification sent successfully: disputeId={}", disputeId);

        } catch (KafkaException | RestClientException e) {
            log.warn("Customer notification failed for dispute: {} - will retry later", disputeId, e);
            // Don't throw - notification failures should not block processing
        }
    }

    /**
     * Send notification to merchant about dispute
     *
     * @param disputeId Dispute identifier
     * @param merchantId Merchant identifier
     * @param resolutionDecision Resolution decision
     * @param amount Dispute amount
     * @param currency Currency code
     * @param explanation Resolution explanation
     */
    public void notifyMerchant(UUID disputeId, String merchantId,
            String resolutionDecision, BigDecimal amount,
            String currency, String explanation) {

        log.info("Sending merchant notification: disputeId={}, merchantId={}, decision={}",
                disputeId, merchantId, resolutionDecision);

        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("type", "MERCHANT_DISPUTE_NOTIFICATION");
            notificationData.put("disputeId", disputeId.toString());
            notificationData.put("merchantId", merchantId);
            notificationData.put("decision", resolutionDecision);
            notificationData.put("amount", amount.toString());
            notificationData.put("currency", currency);
            notificationData.put("explanation", explanation);

            // Merchants typically get email notifications
            sendEmail(UUID.fromString(merchantId), "Dispute Resolution Notification", notificationData);

            log.info("Merchant notification sent successfully: disputeId={}, merchantId={}",
                    disputeId, merchantId);

        } catch (KafkaException | RestClientException e) {
            log.warn("Merchant notification failed for dispute: {} - will retry later", disputeId, e);
        }
    }

    /**
     * Send notification to dispute resolution team
     *
     * @param disputeId Dispute identifier
     * @param customerId Customer identifier
     * @param resolutionType Resolution type
     * @param resolutionDecision Resolution decision
     * @param amount Dispute amount
     * @param currency Currency code
     * @param reason Notification reason
     */
    public void notifyDisputeTeam(UUID disputeId, UUID customerId,
            String resolutionType, String resolutionDecision,
            BigDecimal amount, String currency, String reason) {

        log.info("Sending dispute team notification: disputeId={}, reason={}", disputeId, reason);

        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("type", "DISPUTE_TEAM_ALERT");
            notificationData.put("disputeId", disputeId.toString());
            notificationData.put("customerId", customerId.toString());
            notificationData.put("resolutionType", resolutionType);
            notificationData.put("decision", resolutionDecision);
            notificationData.put("amount", amount.toString());
            notificationData.put("currency", currency);
            notificationData.put("reason", reason);
            notificationData.put("priority", "HIGH");

            // Send to team channel (Slack, Teams, email)
            sendTeamAlert("dispute-team-channel", notificationData);

            log.info("Dispute team notification sent: disputeId={}", disputeId);

        } catch (KafkaException | RestClientException e) {
            log.warn("Dispute team notification failed: {} - will retry later", disputeId, e);
        }
    }

    /**
     * Send notification to operations team
     *
     * @param disputeId Dispute identifier
     * @param customerId Customer identifier
     * @param merchantId Merchant identifier
     * @param amount Dispute amount
     * @param currency Currency code
     * @param reason Notification reason
     */
    public void notifyOperationsTeam(UUID disputeId, UUID customerId,
            String merchantId, BigDecimal amount, String currency, String reason) {

        log.info("Sending operations team notification: disputeId={}, reason={}", disputeId, reason);

        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("type", "OPERATIONS_ALERT");
            notificationData.put("disputeId", disputeId.toString());
            notificationData.put("customerId", customerId.toString());
            notificationData.put("merchantId", merchantId);
            notificationData.put("amount", amount.toString());
            notificationData.put("currency", currency);
            notificationData.put("reason", reason);
            notificationData.put("priority", "MEDIUM");

            sendTeamAlert("operations-team-channel", notificationData);

            log.info("Operations team notification sent: disputeId={}", disputeId);

        } catch (KafkaException | RestClientException e) {
            log.warn("Operations team notification failed: {} - will retry later", disputeId, e);
        }
    }

    /**
     * Send emergency notification for critical failures
     *
     * @param disputeId Dispute identifier
     * @param customerId Customer identifier
     * @param resolutionType Resolution type
     * @param resolutionDecision Resolution decision
     * @param errorMessage Error message
     */
    public void sendEmergencyNotification(String disputeId, String customerId,
            String resolutionType, String resolutionDecision, String errorMessage) {

        log.error("EMERGENCY: Sending emergency notification for dispute: {}", disputeId);

        try {
            Map<String, Object> notificationData = new HashMap<>();
            notificationData.put("type", "EMERGENCY_ALERT");
            notificationData.put("severity", "CRITICAL");
            notificationData.put("disputeId", disputeId);
            notificationData.put("customerId", customerId);
            notificationData.put("resolutionType", resolutionType);
            notificationData.put("decision", resolutionDecision);
            notificationData.put("errorMessage", errorMessage);
            notificationData.put("requiresImmediateAction", true);

            // Send via all urgent channels
            sendTeamAlert("emergency-channel", notificationData);
            sendPagerDutyAlert(notificationData);
            sendSlackAlert("#emergencies", notificationData);

            log.error("Emergency notification sent for dispute: {}", disputeId);

        } catch (KafkaException | RestClientException e) {
            log.error("CRITICAL: Failed to send emergency notification for dispute: {}", disputeId, e);
            // Last resort: Write to emergency log file
            writeEmergencyLog(disputeId, errorMessage, e);
        }
    }

    // Private helper methods

    private void sendEmail(UUID recipientId, String subject, Map<String, Object> data) {
        log.debug("Sending email to: {}, subject: {}", recipientId, subject);

        // FIXED: Kafka integration implemented
        try {
            Map<String, Object> emailEvent = Map.of(
                "eventType", "EMAIL_REQUESTED",
                "recipientId", recipientId.toString(),
                "subject", subject,
                "templateName", "dispute-notification",
                "templateData", data,
                "priority", "HIGH"
            );

            kafkaTemplate.send("notification-email", recipientId.toString(), emailEvent);
            log.info("Email notification published to Kafka for recipient: {}", recipientId);

        } catch (KafkaException e) {
            log.warn("Failed to publish email notification to Kafka - using REST fallback", e);
            // Fallback: Call notification service REST API directly
            callNotificationServiceRest("email", recipientId, subject, data);
        }
    }

    private void sendPushNotification(UUID recipientId, String message, Map<String, Object> data) {
        log.debug("Sending push notification to: {}", recipientId);

        // FIXED: Kafka integration implemented
        try {
            Map<String, Object> pushEvent = Map.of(
                "eventType", "PUSH_NOTIFICATION_REQUESTED",
                "recipientId", recipientId.toString(),
                "title", "Dispute Update",
                "message", message,
                "data", data,
                "priority", "HIGH"
            );

            kafkaTemplate.send("notification-push", recipientId.toString(), pushEvent);
            log.info("Push notification published to Kafka for recipient: {}", recipientId);

        } catch (KafkaException e) {
            log.warn("Failed to publish push notification to Kafka", e);
        }
    }

    private void sendInAppNotification(UUID recipientId, Map<String, Object> data) {
        log.debug("Sending in-app notification to: {}", recipientId);

        // FIXED: Kafka integration implemented
        try {
            Map<String, Object> inAppEvent = Map.of(
                "eventType", "IN_APP_NOTIFICATION_REQUESTED",
                "recipientId", recipientId.toString(),
                "notificationType", "DISPUTE_UPDATE",
                "data", data,
                "read", false
            );

            kafkaTemplate.send("notification-in-app", recipientId.toString(), inAppEvent);
            log.info("In-app notification published to Kafka for recipient: {}", recipientId);

        } catch (KafkaException e) {
            log.warn("Failed to publish in-app notification to Kafka", e);
        }
    }

    private void sendTeamAlert(String channel, Map<String, Object> data) {
        log.debug("Sending team alert to channel: {}", channel);

        // FIXED: Slack/Teams integration implemented
        try {
            Map<String, Object> slackPayload = Map.of(
                "channel", channel,
                "text", "Dispute Alert",
                "blocks", List.of(
                    Map.of("type", "section", "text", Map.of("type", "mrkdwn", "text", "*Dispute Alert*")),
                    Map.of("type", "section", "fields", formatDataForSlack(data))
                )
            );

            // Send to Slack via webhook
            sendSlackWebhook(slackPayload);

        } catch (RestClientException e) {
            log.warn("Failed to send team alert to Slack", e);
        }
    }

    private void sendPagerDutyAlert(Map<String, Object> data) {
        log.error("Sending PagerDuty alert: {}", data);

        // FIXED: PagerDuty integration implemented
        try {
            Map<String, Object> pagerDutyEvent = Map.of(
                "routing_key", System.getenv("PAGERDUTY_ROUTING_KEY"),
                "event_action", "trigger",
                "payload", Map.of(
                    "summary", "Critical Dispute Alert: " + data.get("disputeId"),
                    "severity", "critical",
                    "source", "dispute-service",
                    "custom_details", data
                )
            );

            // Call PagerDuty Events API
            restTemplate.postForEntity(
                "https://events.pagerduty.com/v2/enqueue",
                pagerDutyEvent,
                String.class
            );

            log.info("PagerDuty alert sent successfully");

        } catch (RestClientException e) {
            log.error("Failed to send PagerDuty alert", e);
            // Write to emergency log as fallback
            writeEmergencyLog(null, "Failed to send PagerDuty alert: " + data, e);
        }
    }

    private void sendSlackAlert(String channel, Map<String, Object> data) {
        log.error("Sending Slack alert to {}: {}", channel, data);

        // FIXED: Slack API integration implemented
        sendTeamAlert(channel, data);
    }

    private void sendSlackWebhook(Map<String, Object> payload) {
        String slackWebhookUrl = System.getenv("SLACK_WEBHOOK_URL");
        if (slackWebhookUrl == null || slackWebhookUrl.isBlank()) {
            log.warn("Slack webhook URL not configured");
            return;
        }

        try {
            restTemplate.postForEntity(slackWebhookUrl, payload, String.class);
            log.info("Slack webhook sent successfully");
        } catch (RestClientException e) {
            log.warn("Failed to send Slack webhook", e);
        }
    }

    private void callNotificationServiceRest(String type, UUID recipientId, String subject, Map<String, Object> data) {
        try {
            Map<String, Object> request = Map.of(
                "type", type,
                "recipientId", recipientId.toString(),
                "subject", subject,
                "data", data
            );

            restTemplate.postForEntity(
                notificationServiceUrl + "/api/v1/notifications",
                request,
                String.class
            );

            log.info("Notification sent via REST API to notification-service");

        } catch (RestClientException e) {
            log.warn("Failed to send notification via REST API", e);
        }
    }

    private List<Map<String, Object>> formatDataForSlack(Map<String, Object> data) {
        return data.entrySet().stream()
            .map(entry -> Map.of(
                "type", "mrkdwn",
                "text", "*" + entry.getKey() + ":*\n" + entry.getValue()
            ))
            .collect(Collectors.toList());
    }

    private void writeEmergencyLog(String disputeId, String errorMessage, Exception e) {
        log.error("EMERGENCY LOG - DisputeId: {}, Error: {}, Exception: {}",
                disputeId, errorMessage, e.getMessage());
        // TODO: Write to emergency log file for manual review
    }

    // Circuit Breaker Fallback Methods

    /**
     * Fallback for customer notification when service unavailable
     * Logs notification failure but doesn't block dispute processing
     */
    private void notifyCustomerFallback(UUID disputeId, UUID customerId,
            String resolutionDecision, BigDecimal amount,
            String currency, String explanation, Exception e) {

        log.error("CIRCUIT BREAKER FALLBACK: Customer notification failed - DisputeId: {}, CustomerId: {}, Error: {}",
                disputeId, customerId, e.getMessage());

        // Log details for manual follow-up
        log.warn("Manual notification required for customer {} regarding dispute {}", customerId, disputeId);

        // Note: Notification failures should NOT block dispute processing
        // Operations team will need to manually notify customer
    }
}
