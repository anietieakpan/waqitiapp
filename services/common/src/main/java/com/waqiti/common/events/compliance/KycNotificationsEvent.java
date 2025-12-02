package com.waqiti.common.events.compliance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycNotificationsEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Event ID is required")
    @JsonProperty("event_id")
    private String eventId;

    @NotBlank(message = "Correlation ID is required")
    @JsonProperty("correlation_id")
    private String correlationId;

    @NotNull(message = "Timestamp is required")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @JsonProperty("event_version")
    private String eventVersion = "1.0";

    @NotBlank(message = "Event source is required")
    @JsonProperty("source")
    private String source;

    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "Verification ID is required")
    @JsonProperty("verification_id")
    private String verificationId;

    @NotBlank(message = "Notification type is required")
    @JsonProperty("notification_type")
    private String notificationType;

    @NotBlank(message = "Event type is required")
    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("kyc_status")
    private String kycStatus;

    @JsonProperty("kyc_level")
    private String kycLevel;

    @JsonProperty("notification_channel")
    private String notificationChannel;

    @JsonProperty("recipient")
    private String recipient;

    @JsonProperty("subject")
    private String subject;

    @JsonProperty("message")
    private String message;

    @JsonProperty("priority")
    private String priority;

    @JsonProperty("delivery_attempts")
    private Integer deliveryAttempts;

    @JsonProperty("metadata")
    private String metadata;

    @JsonProperty("scheduled_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime scheduledAt;
}