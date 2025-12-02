package com.waqiti.billpayment.client.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request to send notification to user
 * Supports multiple notification channels
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    @NotBlank(message = "User ID is required")
    private String userId;

    @NotBlank(message = "Channel is required")
    private String channel; // EMAIL, SMS, PUSH

    private String title;

    @NotBlank(message = "Message is required")
    private String message;

    private String templateId;

    private Map<String, Object> templateData;

    @Builder.Default
    private String priority = "NORMAL"; // HIGH, NORMAL, LOW

    private Map<String, Object> metadata;
}
