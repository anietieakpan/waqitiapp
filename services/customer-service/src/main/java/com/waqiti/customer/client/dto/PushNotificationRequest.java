package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request DTO for sending push notifications to notification-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushNotificationRequest {

    /**
     * Recipient user ID
     */
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Device token (if targeting specific device)
     */
    private String deviceToken;

    /**
     * Notification title
     */
    @NotBlank(message = "Title is required")
    private String title;

    /**
     * Notification message
     */
    @NotBlank(message = "Message is required")
    private String message;

    /**
     * Notification badge count
     */
    private Integer badge;

    /**
     * Sound to play
     */
    private String sound;

    /**
     * Icon to display
     */
    private String icon;

    /**
     * Click action URL
     */
    private String clickAction;

    /**
     * Image URL
     */
    private String imageUrl;

    /**
     * Custom data payload
     */
    private Map<String, Object> data;

    /**
     * Priority level (HIGH, NORMAL)
     */
    private String priority;

    /**
     * Time-to-live in seconds
     */
    private Integer ttl;

    /**
     * Notification category
     */
    private String category;

    /**
     * Whether to collapse notifications
     */
    private String collapseKey;
}
