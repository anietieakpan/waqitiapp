package com.waqiti.payment.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for group payment notification requests
 * 
 * This request handles all types of group payment notifications including:
 * - Group payment creation alerts
 * - Payment status updates
 * - Participant payment confirmations
 * - Settlement notifications
 * - Reminder notifications
 * - Cancellation alerts
 * 
 * NOTIFICATION TYPES SUPPORTED:
 * - GROUP_PAYMENT_CREATED: Initial creation notification
 * - GROUP_PAYMENT_UPDATED: General update notification
 * - GROUP_PAYMENT_SETTLED: Final settlement notification
 * - GROUP_PAYMENT_CANCELLED: Cancellation notification
 * - PARTICIPANT_PAID: Individual participant payment notification
 * - GROUP_PAYMENT_FULLY_PAID: All participants paid notification
 * - GROUP_PAYMENT_CREATED_CONFIRMATION: Creator confirmation
 * - GROUP_PAYMENT_REMINDER: Payment reminder
 * 
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupPaymentNotificationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Target user ID for the notification
     */
    @NotBlank(message = "User ID is required")
    @Size(max = 50, message = "User ID must not exceed 50 characters")
    private String userId;

    /**
     * Group payment ID that triggered the notification
     */
    @NotBlank(message = "Group payment ID is required")
    @Size(max = 100, message = "Group payment ID must not exceed 100 characters")
    private String groupPaymentId;

    /**
     * Type of notification being sent
     */
    @NotBlank(message = "Notification type is required")
    @Size(max = 50, message = "Notification type must not exceed 50 characters")
    private String notificationType;

    /**
     * Title/subject of the notification
     */
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    /**
     * Main notification message content
     */
    @NotBlank(message = "Message is required")
    @Size(max = 1000, message = "Message must not exceed 1000 characters")
    private String message;

    /**
     * Name of the group payment creator (for context)
     */
    @Size(max = 100, message = "Creator name must not exceed 100 characters")
    private String creatorName;

    /**
     * Amount owed by the recipient (for participant notifications)
     */
    private BigDecimal amountOwed;

    /**
     * Amount already paid by the recipient
     */
    private BigDecimal amountPaid;

    /**
     * Total amount of the group payment
     */
    private BigDecimal totalAmount;

    /**
     * Currency code (ISO 4217 format)
     */
    @Size(min = 3, max = 3, message = "Currency must be a 3-character ISO code")
    private String currency;

    /**
     * Due date for the group payment
     */
    private Instant dueDate;

    /**
     * Number of participants in the group payment
     */
    @Min(value = 1, message = "Participant count must be at least 1")
    private Integer participantCount;

    /**
     * Number of participants who have already paid
     */
    @Min(value = 0, message = "Paid participant count cannot be negative")
    private Integer paidParticipantCount;

    /**
     * Deep link URL for the mobile app or web interface
     */
    @Size(max = 500, message = "Deep link URL must not exceed 500 characters")
    private String deepLinkUrl;

    /**
     * List of notification channels to use
     * Examples: EMAIL, PUSH_NOTIFICATION, SMS, IN_APP
     */
    @NotNull(message = "At least one notification channel is required")
    @Size(min = 1, message = "At least one notification channel is required")
    private List<String> channels;

    /**
     * Priority level of the notification
     * Values: LOW, MEDIUM, HIGH, URGENT
     */
    @Builder.Default
    private String priority = "MEDIUM";

    /**
     * Whether this notification should be stored for later retrieval
     */
    @Builder.Default
    private boolean persistent = true;

    /**
     * Whether this notification should trigger a push notification badge update
     */
    @Builder.Default
    private boolean updateBadge = true;

    /**
     * Custom sound to play for push notifications
     */
    @Size(max = 50, message = "Custom sound name must not exceed 50 characters")
    private String customSound;

    /**
     * Notification category for grouping and filtering
     */
    @Builder.Default
    private String category = "GROUP_PAYMENT";

    /**
     * Additional action buttons for rich notifications
     */
    private List<NotificationAction> actions;

    /**
     * Template ID for styled notifications
     */
    @Size(max = 50, message = "Template ID must not exceed 50 characters")
    private String templateId;

    /**
     * Template data for personalized content
     */
    private Map<String, Object> templateData;

    /**
     * Correlation ID for tracing across services
     */
    @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
    private String correlationId;

    /**
     * Timestamp when the notification should be sent
     */
    @NotNull(message = "Timestamp is required")
    private Instant timestamp;

    /**
     * Optional delay before sending the notification
     */
    private Long delaySeconds;

    /**
     * Expiration time for the notification
     */
    private Instant expiresAt;

    /**
     * Additional metadata for analytics and tracking
     */
    private Map<String, Object> metadata;

    /**
     * A/B testing group for notification variations
     */
    @Size(max = 50, message = "Test group must not exceed 50 characters")
    private String testGroup;

    /**
     * Source system that generated this notification
     */
    @Builder.Default
    private String sourceSystem = "payment-service";

    /**
     * Nested class for notification actions
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationAction implements Serializable {
        private static final long serialVersionUID = 1L;

        /**
         * Unique identifier for the action
         */
        private String actionId;

        /**
         * Display text for the action button
         */
        private String title;

        /**
         * URL or deep link to execute when action is tapped
         */
        private String url;

        /**
         * Whether this action should open the app
         */
        @Builder.Default
        private boolean openApp = true;

        /**
         * Icon to display with the action (optional)
         */
        private String icon;

        /**
         * Action type for analytics
         */
        private String actionType;
    }
}