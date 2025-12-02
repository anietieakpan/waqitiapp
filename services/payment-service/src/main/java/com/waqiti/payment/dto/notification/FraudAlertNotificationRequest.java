/**
 * Fraud Alert Notification Request DTO
 * Used for sending fraud alerts to users following containment execution
 */
package com.waqiti.payment.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAlertNotificationRequest {
    
    /**
     * User ID to send notification to
     */
    @NotBlank(message = "User ID is required")
    private String userId;
    
    /**
     * Alert ID that triggered the notification
     */
    @NotBlank(message = "Alert ID is required")
    private String alertId;
    
    /**
     * Alert type
     */
    @NotBlank(message = "Alert type is required")
    private String alertType;
    
    /**
     * Type of fraud detected
     */
    @NotBlank(message = "Fraud type is required")
    private String fraudType;
    
    /**
     * Severity level (LOW, MEDIUM, HIGH, CRITICAL)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "Severity must be LOW, MEDIUM, HIGH, or CRITICAL")
    private String severity;
    
    /**
     * Containment actions taken
     */
    @NotEmpty(message = "Containment actions are required")
    private List<String> containmentActions;
    
    /**
     * Whether account was suspended
     */
    private Boolean accountSuspended;
    
    /**
     * Whether cards were blocked
     */
    private Boolean cardsBlocked;
    
    /**
     * Whether transaction was blocked
     */
    private Boolean transactionBlocked;
    
    /**
     * Main notification message for the user
     */
    @NotBlank(message = "Message is required")
    @Size(max = 1000, message = "Message cannot exceed 1000 characters")
    private String message;
    
    /**
     * Action required message for the user
     */
    @Size(max = 500, message = "Action required message cannot exceed 500 characters")
    private String actionRequired;
    
    /**
     * Notification channels to use (EMAIL, SMS, PUSH_NOTIFICATION, PHONE_CALL)
     */
    @NotEmpty(message = "At least one notification channel is required")
    private List<String> channels;
    
    /**
     * Priority level (LOW, MEDIUM, HIGH, URGENT)
     */
    @Pattern(regexp = "LOW|MEDIUM|HIGH|URGENT", message = "Priority must be LOW, MEDIUM, HIGH, or URGENT")
    private String priority;
    
    /**
     * Notification template to use
     */
    private String template;
    
    /**
     * Template variables for personalization
     */
    private Map<String, Object> templateVariables;
    
    /**
     * Notification title/subject
     */
    @Size(max = 200, message = "Title cannot exceed 200 characters")
    private String title;
    
    /**
     * Rich content for push notifications
     */
    private Map<String, Object> richContent;
    
    /**
     * Delivery preferences
     */
    private Map<String, Object> deliveryPreferences;
    
    /**
     * Whether to respect user's notification preferences
     */
    private Boolean respectUserPreferences;
    
    /**
     * Whether this is an emergency override
     */
    private Boolean emergencyOverride;
    
    /**
     * Scheduled delivery time (null for immediate)
     */
    private Instant scheduledDeliveryTime;
    
    /**
     * Expiry time for the notification
     */
    private Instant expiryTime;
    
    /**
     * Whether delivery confirmation is required
     */
    private Boolean requireDeliveryConfirmation;
    
    /**
     * Whether read receipt is required
     */
    private Boolean requireReadReceipt;
    
    /**
     * Maximum retry attempts
     */
    @Min(value = 0, message = "Retry attempts cannot be negative")
    @Max(value = 10, message = "Retry attempts cannot exceed 10")
    private Integer maxRetryAttempts;
    
    /**
     * Retry interval in minutes
     */
    @Min(value = 1, message = "Retry interval must be at least 1 minute")
    private Integer retryIntervalMinutes;
    
    /**
     * Fallback channels if primary fails
     */
    private List<String> fallbackChannels;
    
    /**
     * Contact preferences override
     */
    private Map<String, String> contactPreferencesOverride;
    
    /**
     * Additional notification metadata
     */
    private Map<String, Object> notificationMetadata;
    
    /**
     * Security context for the notification
     */
    private Map<String, Object> securityContext;
    
    /**
     * Locale for localization
     */
    @Pattern(regexp = "[a-z]{2}(-[A-Z]{2})?", message = "Locale must be in format 'en' or 'en-US'")
    private String locale;
    
    /**
     * Time zone for time-sensitive content
     */
    private String timeZone;
    
    /**
     * Callback URL for delivery status
     */
    private String callbackUrl;
    
    /**
     * Tags for categorization
     */
    private List<String> tags;
    
    /**
     * Business context
     */
    private Map<String, Object> businessContext;
    
    /**
     * Correlation ID for tracking
     */
    private String correlationId;
    
    /**
     * Request timestamp
     */
    private Instant timestamp;
    
    /**
     * Source system sending the notification
     */
    private String sourceSystem;
    
    /**
     * Requesting user/service
     */
    private String requestedBy;
}