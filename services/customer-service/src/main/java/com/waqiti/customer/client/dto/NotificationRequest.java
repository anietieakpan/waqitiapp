package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Request DTO for sending notifications to notification-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    /**
     * Recipient user ID
     */
    @NotBlank(message = "Recipient ID is required")
    private String recipientId;

    /**
     * Notification type (EMAIL, SMS, PUSH, IN_APP, etc.)
     */
    @NotBlank(message = "Notification type is required")
    private String notificationType;

    /**
     * Notification subject/title
     */
    @NotBlank(message = "Subject is required")
    private String subject;

    /**
     * Notification message content
     */
    @NotBlank(message = "Message is required")
    private String message;

    /**
     * Priority level (LOW, MEDIUM, HIGH, URGENT)
     */
    @NotNull(message = "Priority is required")
    private String priority;

    /**
     * Notification category
     */
    private String category;

    /**
     * Template ID (if using template)
     */
    private String templateId;

    /**
     * Template parameters
     */
    private Map<String, Object> templateParameters;

    /**
     * Additional metadata
     */
    private Map<String, String> metadata;

    /**
     * Whether to send immediately
     */
    private Boolean sendImmediately;
}
