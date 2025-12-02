package com.waqiti.common.model.alert;

import com.waqiti.common.dlq.BaseDlqRecoveryResult;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Recovery result for Slack notification DLQ processing.
 * Tracks the outcome of attempting to recover failed Slack notifications.
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SlackNotificationRecoveryResult extends BaseDlqRecoveryResult {

    private String notificationId;
    private String channel;
    private String messageType;
    private String recipientUserId;
    private String recipientChannelId;
    private boolean messageSent;
    private String slackResponse;
    private Integer retryAttempt;
    private String webhookUrl;
    private Instant sentTimestamp;

    // Additional fields for DLQ consumer compatibility
    private boolean delivered;
    private String deliveryStatus;
    private String deliveryDetails;
    private Instant deliveryTime;
    private java.time.Duration processingTime;
    private String alertType;
    private String message;
    private boolean criticalAlert;
    private boolean channelUnavailable;
    private String failureReason;
    private String escalationId;
    private boolean hasAssociatedEscalation;
    private String escalationPath;
    private java.util.List<String> recipientEmails;
    private boolean p0Alert;
    private java.util.List<String> onCallPhoneNumbers;
    private String escalationLevel;
    private java.util.List<String> alternativeChannels;
    private java.util.List<String> fallbackChannels;

    @Override
    public String getRecoveryStatus() {
        if (isRecovered()) {
            return String.format("Slack notification recovered: channel=%s, messageType=%s, sent=%s",
                    channel, messageType, messageSent);
        } else {
            return String.format("Slack notification recovery failed: channel=%s, reason=%s",
                    channel, getFailureReason());
        }
    }

    public boolean isChannelNotification() {
        return recipientChannelId != null && !recipientChannelId.isEmpty();
    }

    public boolean isDirectMessage() {
        return recipientUserId != null && !recipientUserId.isEmpty();
    }

    public boolean requiresRetry() {
        return !isRecovered() && retryAttempt != null && retryAttempt < 3;
    }

    // Compatibility methods for DLQ consumer
    public boolean isDelivered() {
        return delivered || messageSent;
    }

    public boolean isCriticalAlert() {
        return criticalAlert || (alertType != null && (
                alertType.contains("CRITICAL") ||
                alertType.contains("P0") ||
                alertType.contains("EMERGENCY")
        ));
    }

    public boolean isChannelUnavailable() {
        return channelUnavailable || (failureReason != null &&
                failureReason.contains("channel_not_found"));
    }

    public boolean isP0Alert() {
        return p0Alert || (alertType != null && alertType.contains("P0"));
    }

    public String getMessage() {
        return message != null ? message : "";
    }

    public String getAlertType() {
        return alertType != null ? alertType : messageType;
    }

    public Instant getDeliveryTime() {
        return deliveryTime != null ? deliveryTime : sentTimestamp;
    }

    public String getDeliveryStatus() {
        if (deliveryStatus != null) return deliveryStatus;
        if (isDelivered()) return "DELIVERED";
        if (isChannelUnavailable()) return "CHANNEL_UNAVAILABLE";
        if (isCriticalAlert() && !isDelivered()) return "ESCALATED";
        return "FAILED";
    }
}
