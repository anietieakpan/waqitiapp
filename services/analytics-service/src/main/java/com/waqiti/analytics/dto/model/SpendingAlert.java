package com.waqiti.analytics.dto.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Spending Alert Data Transfer Object
 * 
 * Represents spending alert configuration and threshold definitions for financial monitoring.
 * This DTO follows Domain-Driven Design principles and includes comprehensive
 * validation, notification preferences, and alert management capabilities.
 * 
 * <p>Used for:
 * <ul>
 *   <li>Budget threshold monitoring and alerting</li>
 *   <li>Unusual spending pattern detection</li>
 *   <li>Real-time spending notifications</li>
 *   <li>Risk management and fraud prevention</li>
 *   <li>Personalized financial wellness alerts</li>
 *   <li>Custom spending goal tracking</li>
 * </ul>
 * 
 * <p>Version: 1.0
 * <p>API Version: v1
 * 
 * @author Waqiti Analytics Team
 * @since 1.0.0
 * @version 1.0
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"alertMetadata"})
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(
    name = "SpendingAlert",
    description = "Spending alert configuration with threshold definitions and notification preferences",
    example = """
        {
          "alertId": "alert_12345",
          "userId": "user_67890",
          "alertName": "Monthly Budget Alert",
          "alertType": "BUDGET_THRESHOLD",
          "severity": "HIGH",
          "isActive": true,
          "thresholdAmount": 2500.00,
          "thresholdPercentage": 90.0,
          "timePeriod": "MONTHLY",
          "notificationChannels": ["EMAIL", "PUSH_NOTIFICATION"],
          "currency": "USD",
          "version": "1.0"
        }
        """
)
public class SpendingAlert implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this alert
     */
    @NotBlank(message = "Alert ID cannot be blank")
    @JsonProperty("alertId")
    @JsonPropertyDescription("Unique identifier for this alert")
    @Schema(
        description = "Unique identifier for this alert",
        example = "alert_12345",
        required = true
    )
    private String alertId;

    /**
     * User ID this alert belongs to
     */
    @NotBlank(message = "User ID cannot be blank")
    @JsonProperty("userId")
    @JsonPropertyDescription("User ID this alert belongs to")
    @Schema(
        description = "User ID this alert belongs to",
        example = "user_67890",
        required = true
    )
    private String userId;

    /**
     * Human-readable name for the alert
     */
    @NotBlank(message = "Alert name cannot be blank")
    @Size(min = 1, max = 100, message = "Alert name must be between 1 and 100 characters")
    @JsonProperty("alertName")
    @JsonPropertyDescription("Human-readable name for the alert")
    @Schema(
        description = "Human-readable name for the alert",
        example = "Monthly Budget Alert",
        minLength = 1,
        maxLength = 100,
        required = true
    )
    private String alertName;

    /**
     * Type of alert
     */
    @NotNull(message = "Alert type cannot be null")
    @JsonProperty("alertType")
    @JsonPropertyDescription("Type of alert (BUDGET_THRESHOLD, UNUSUAL_SPENDING, CATEGORY_LIMIT, etc.)")
    @Schema(
        description = "Type of alert",
        example = "BUDGET_THRESHOLD",
        allowableValues = {"BUDGET_THRESHOLD", "UNUSUAL_SPENDING", "CATEGORY_LIMIT", "DAILY_LIMIT", "WEEKLY_LIMIT", "MONTHLY_LIMIT", "FRAUD_DETECTION", "MERCHANT_ALERT", "VELOCITY_ALERT"},
        required = true
    )
    private AlertType alertType;

    /**
     * Severity level of the alert
     */
    @JsonProperty("severity")
    @JsonPropertyDescription("Severity level of the alert (LOW, MEDIUM, HIGH, CRITICAL)")
    @Schema(
        description = "Severity level of the alert",
        example = "HIGH",
        allowableValues = {"LOW", "MEDIUM", "HIGH", "CRITICAL"}
    )
    private AlertSeverity severity;

    /**
     * Whether the alert is currently active
     */
    @NotNull(message = "Active status cannot be null")
    @JsonProperty("isActive")
    @JsonPropertyDescription("Whether the alert is currently active")
    @Schema(
        description = "Whether the alert is currently active",
        example = "true",
        required = true
    )
    private Boolean isActive;

    /**
     * Threshold amount for triggering the alert
     */
    @DecimalMin(value = "0.00", message = "Threshold amount must be non-negative")
    @JsonProperty("thresholdAmount")
    @JsonPropertyDescription("Threshold amount for triggering the alert")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.##")
    @Schema(
        description = "Threshold amount for triggering the alert",
        example = "2500.00",
        minimum = "0"
    )
    private BigDecimal thresholdAmount;

    /**
     * Threshold percentage for triggering the alert
     */
    @DecimalMin(value = "0.0", message = "Threshold percentage must be non-negative")
    @JsonProperty("thresholdPercentage")
    @JsonPropertyDescription("Threshold percentage for triggering the alert")
    @JsonFormat(shape = JsonFormat.Shape.NUMBER, pattern = "#.#")
    @Schema(
        description = "Threshold percentage for triggering the alert",
        example = "90.0",
        minimum = "0"
    )
    private BigDecimal thresholdPercentage;

    /**
     * Time period for the alert evaluation
     */
    @JsonProperty("timePeriod")
    @JsonPropertyDescription("Time period for the alert evaluation (DAILY, WEEKLY, MONTHLY, YEARLY)")
    @Schema(
        description = "Time period for the alert evaluation",
        example = "MONTHLY",
        allowableValues = {"DAILY", "WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY", "REAL_TIME"}
    )
    private TimePeriod timePeriod;

    /**
     * Category this alert applies to (null for all categories)
     */
    @JsonProperty("categoryFilter")
    @JsonPropertyDescription("Category this alert applies to (null for all categories)")
    @Schema(
        description = "Category this alert applies to",
        example = "Groceries"
    )
    private String categoryFilter;

    /**
     * Merchant this alert applies to (null for all merchants)
     */
    @JsonProperty("merchantFilter")
    @JsonPropertyDescription("Merchant this alert applies to (null for all merchants)")
    @Schema(
        description = "Merchant this alert applies to",
        example = "Amazon"
    )
    private String merchantFilter;

    /**
     * Notification channels for this alert
     */
    @JsonProperty("notificationChannels")
    @JsonPropertyDescription("List of notification channels for this alert")
    @Schema(
        description = "List of notification channels",
        example = "[\"EMAIL\", \"PUSH_NOTIFICATION\"]"
    )
    private List<NotificationChannel> notificationChannels;

    /**
     * Cooldown period between notifications (in minutes)
     */
    @DecimalMin(value = "0", message = "Cooldown period must be non-negative")
    @JsonProperty("cooldownMinutes")
    @JsonPropertyDescription("Cooldown period between notifications in minutes")
    @Schema(
        description = "Cooldown period between notifications in minutes",
        example = "60",
        minimum = "0"
    )
    private Integer cooldownMinutes;

    /**
     * Whether to send digest notifications
     */
    @JsonProperty("sendDigest")
    @JsonPropertyDescription("Whether to send periodic digest notifications")
    @Schema(
        description = "Whether to send periodic digest notifications",
        example = "true"
    )
    private Boolean sendDigest;

    /**
     * Custom message template for notifications
     */
    @Size(max = 500, message = "Message template must not exceed 500 characters")
    @JsonProperty("messageTemplate")
    @JsonPropertyDescription("Custom message template for notifications")
    @Schema(
        description = "Custom message template for notifications",
        example = "You've spent ${amount} which is ${percentage}% of your ${period} budget.",
        maxLength = 500
    )
    private String messageTemplate;

    /**
     * Alert configuration metadata
     */
    @JsonProperty("alertConditions")
    @JsonPropertyDescription("Advanced alert conditions and rules")
    @Schema(description = "Advanced alert conditions and rules")
    private AlertConditions alertConditions;

    /**
     * Currency code for monetary amounts
     */
    @JsonProperty("currency")
    @JsonPropertyDescription("ISO 4217 currency code")
    @Schema(
        description = "ISO 4217 currency code",
        example = "USD",
        pattern = "^[A-Z]{3}$"
    )
    private String currency;

    /**
     * When this alert was created
     */
    @JsonProperty("createdAt")
    @JsonPropertyDescription("When this alert was created")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Schema(
        description = "When this alert was created",
        format = "date-time"
    )
    private Instant createdAt;

    /**
     * When this alert was last modified
     */
    @JsonProperty("lastModifiedAt")
    @JsonPropertyDescription("When this alert was last modified")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Schema(
        description = "When this alert was last modified",
        format = "date-time"
    )
    private Instant lastModifiedAt;

    /**
     * When this alert was last triggered
     */
    @JsonProperty("lastTriggeredAt")
    @JsonPropertyDescription("When this alert was last triggered")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    @Schema(
        description = "When this alert was last triggered",
        format = "date-time"
    )
    private Instant lastTriggeredAt;

    /**
     * Number of times this alert has been triggered
     */
    @JsonProperty("triggerCount")
    @JsonPropertyDescription("Number of times this alert has been triggered")
    @Schema(
        description = "Number of times this alert has been triggered",
        example = "5"
    )
    private Integer triggerCount;

    /**
     * DTO version for API evolution support
     */
    @JsonProperty("version")
    @JsonPropertyDescription("DTO version for API compatibility")
    @Schema(
        description = "DTO version for API compatibility",
        example = "1.0",
        defaultValue = "1.0"
    )
    @Builder.Default
    private String version = "1.0";

    /**
     * Alert metadata for additional information
     */
    @JsonProperty("alertMetadata")
    @JsonPropertyDescription("Additional metadata for alert management")
    @Schema(description = "Additional metadata for alert management")
    private AlertMetadata alertMetadata;

    /**
     * Checks if the alert should trigger based on current spending
     * 
     * @param currentAmount current spending amount
     * @param budgetAmount budget amount (if applicable)
     * @return true if alert should trigger
     */
    public boolean shouldTrigger(BigDecimal currentAmount, BigDecimal budgetAmount) {
        if (!Boolean.TRUE.equals(isActive) || currentAmount == null) {
            return false;
        }

        // Check amount threshold
        if (thresholdAmount != null && currentAmount.compareTo(thresholdAmount) >= 0) {
            return true;
        }

        // Check percentage threshold
        if (thresholdPercentage != null && budgetAmount != null && budgetAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentPercentage = currentAmount.divide(budgetAmount, 4, java.math.RoundingMode.HALF_UP)
                                                      .multiply(BigDecimal.valueOf(100));
            return currentPercentage.compareTo(thresholdPercentage) >= 0;
        }

        return false;
    }

    /**
     * Checks if the alert is in cooldown period
     * 
     * @return true if in cooldown period
     */
    public boolean isInCooldown() {
        if (cooldownMinutes == null || lastTriggeredAt == null) {
            return false;
        }
        
        Instant cooldownEnd = lastTriggeredAt.plusSeconds(cooldownMinutes * 60L);
        return Instant.now().isBefore(cooldownEnd);
    }

    /**
     * Gets the effective notification channels (removes duplicates, handles null)
     * 
     * @return effective notification channels
     */
    public List<NotificationChannel> getEffectiveNotificationChannels() {
        if (notificationChannels == null || notificationChannels.isEmpty()) {
            return List.of(NotificationChannel.EMAIL); // Default fallback
        }
        return notificationChannels.stream().distinct().toList();
    }

    /**
     * Validates the business rules for this alert
     * 
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return alertId != null && !alertId.trim().isEmpty()
            && userId != null && !userId.trim().isEmpty()
            && alertName != null && !alertName.trim().isEmpty()
            && alertType != null
            && isActive != null
            && (thresholdAmount != null || thresholdPercentage != null) // At least one threshold must be set
            && (thresholdAmount == null || thresholdAmount.compareTo(BigDecimal.ZERO) >= 0)
            && (thresholdPercentage == null || thresholdPercentage.compareTo(BigDecimal.ZERO) >= 0);
    }

    /**
     * Creates a sanitized copy suitable for external APIs
     * 
     * @return sanitized copy without internal metadata
     */
    public SpendingAlert sanitizedCopy() {
        return this.toBuilder()
            .alertMetadata(null)
            .build();
    }

    /**
     * Alert type enumeration
     */
    public enum AlertType {
        BUDGET_THRESHOLD,
        UNUSUAL_SPENDING,
        CATEGORY_LIMIT,
        DAILY_LIMIT,
        WEEKLY_LIMIT,
        MONTHLY_LIMIT,
        FRAUD_DETECTION,
        MERCHANT_ALERT,
        VELOCITY_ALERT
    }

    /**
     * Alert severity enumeration
     */
    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    /**
     * Time period enumeration
     */
    public enum TimePeriod {
        DAILY, WEEKLY, MONTHLY, QUARTERLY, YEARLY, REAL_TIME
    }

    /**
     * Notification channel enumeration
     */
    public enum NotificationChannel {
        EMAIL, SMS, PUSH_NOTIFICATION, IN_APP, WEBHOOK
    }

    /**
     * Alert conditions inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Advanced alert conditions and rules")
    public static class AlertConditions implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("minimumTransactionAmount")
        @Schema(description = "Minimum transaction amount to consider", example = "10.00")
        private BigDecimal minimumTransactionAmount;

        @JsonProperty("maximumTransactionAmount")
        @Schema(description = "Maximum transaction amount to consider", example = "5000.00")
        private BigDecimal maximumTransactionAmount;

        @JsonProperty("requiredConsecutiveOccurrences")
        @Schema(description = "Number of consecutive occurrences required", example = "3")
        private Integer requiredConsecutiveOccurrences;

        @JsonProperty("timeWindowMinutes")
        @Schema(description = "Time window for evaluation in minutes", example = "1440")
        private Integer timeWindowMinutes;

        @JsonProperty("excludeWeekends")
        @Schema(description = "Whether to exclude weekend transactions", example = "false")
        private Boolean excludeWeekends;

        @JsonProperty("excludeHolidays")
        @Schema(description = "Whether to exclude holiday transactions", example = "true")
        private Boolean excludeHolidays;
    }

    /**
     * Alert metadata inner class
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Additional metadata for alert management")
    public static class AlertMetadata implements Serializable {
        
        @Serial
        private static final long serialVersionUID = 1L;

        @JsonProperty("createdBy")
        @Schema(description = "Who created this alert", example = "user")
        private String createdBy;

        @JsonProperty("alertSource")
        @Schema(description = "Source system that created this alert", example = "web_app")
        private String alertSource;

        @JsonProperty("tags")
        @Schema(description = "Tags for alert categorization")
        private List<String> tags;

        @JsonProperty("priority")
        @Schema(description = "Internal priority ranking", example = "5")
        private Integer priority;

        @JsonProperty("isSystemGenerated")
        @Schema(description = "Whether this alert was system-generated", example = "false")
        private Boolean isSystemGenerated;

        @JsonProperty("relatedAlerts")
        @Schema(description = "IDs of related alerts")
        private List<String> relatedAlerts;
    }

    /**
     * Custom equals method for better performance
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SpendingAlert that)) return false;
        return Objects.equals(alertId, that.alertId) && 
               Objects.equals(userId, that.userId);
    }

    /**
     * Custom hashCode method for better performance
     */
    @Override
    public int hashCode() {
        return Objects.hash(alertId, userId);
    }
}