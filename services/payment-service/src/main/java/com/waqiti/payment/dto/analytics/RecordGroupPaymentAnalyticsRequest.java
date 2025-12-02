package com.waqiti.payment.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for recording group payment analytics data
 * 
 * This request captures comprehensive analytics data for group payments including:
 * - Transaction volume and value metrics
 * - User behavior patterns
 * - Group payment trends
 * - Participant engagement data
 * - Payment completion rates
 * - Geographic and demographic insights
 * - Performance metrics
 * 
 * ANALYTICS CATEGORIES:
 * - Transaction Metrics: Volume, value, frequency
 * - User Behavior: Creation patterns, participation rates
 * - Performance: Completion times, success rates
 * - Business Intelligence: Revenue, growth trends
 * - Risk Analytics: Dispute rates, fraud indicators
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
public class RecordGroupPaymentAnalyticsRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for the group payment
     */
    @NotBlank(message = "Group payment ID is required")
    @Size(max = 100, message = "Group payment ID must not exceed 100 characters")
    private String groupPaymentId;

    /**
     * Type of event that triggered this analytics record
     * Examples: GROUP_PAYMENT_CREATED, GROUP_PAYMENT_UPDATED, GROUP_PAYMENT_SETTLED
     */
    @NotBlank(message = "Event type is required")
    @Size(max = 50, message = "Event type must not exceed 50 characters")
    private String eventType;

    /**
     * User ID of the group payment creator
     */
    @NotBlank(message = "Created by user ID is required")
    @Size(max = 50, message = "Created by user ID must not exceed 50 characters")
    private String createdBy;

    /**
     * Total amount of the group payment
     */
    @NotNull(message = "Total amount is required")
    @DecimalMin(value = "0.01", message = "Total amount must be greater than zero")
    private BigDecimal totalAmount;

    /**
     * Currency code (ISO 4217 format)
     */
    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-character ISO code")
    private String currency;

    /**
     * Current status of the group payment
     */
    @NotBlank(message = "Status is required")
    @Size(max = 30, message = "Status must not exceed 30 characters")
    private String status;

    /**
     * Type of split used for the group payment
     * Examples: EQUAL, PERCENTAGE, CUSTOM, BY_ITEM
     */
    @Size(max = 20, message = "Split type must not exceed 20 characters")
    private String splitType;

    /**
     * Number of participants in the group payment
     */
    @NotNull(message = "Participant count is required")
    @Min(value = 1, message = "Participant count must be at least 1")
    private Integer participantCount;

    /**
     * Number of participants who have paid
     */
    @Min(value = 0, message = "Paid participant count cannot be negative")
    private Integer paidParticipantCount;

    /**
     * Category of the group payment
     * Examples: DINING, TRAVEL, UTILITIES, ENTERTAINMENT, GROCERIES
     */
    @Size(max = 50, message = "Category must not exceed 50 characters")
    private String category;

    /**
     * Subcategory for more detailed classification
     */
    @Size(max = 50, message = "Subcategory must not exceed 50 characters")
    private String subcategory;

    /**
     * Geographic information about the payment
     */
    private GeographicData geographicData;

    /**
     * Time-based analytics data
     */
    private TimeAnalytics timeAnalytics;

    /**
     * User demographic and behavior data
     */
    private UserAnalytics userAnalytics;

    /**
     * Payment method analytics
     */
    private List<PaymentMethodAnalytics> paymentMethodAnalytics;

    /**
     * Device and platform information
     */
    private DeviceAnalytics deviceAnalytics;

    /**
     * Performance metrics
     */
    private PerformanceMetrics performanceMetrics;

    /**
     * Business metrics
     */
    private BusinessMetrics businessMetrics;

    /**
     * Engagement metrics
     */
    private EngagementMetrics engagementMetrics;

    /**
     * Risk and fraud indicators
     */
    private RiskMetrics riskMetrics;

    /**
     * Correlation ID for tracing across services
     */
    @Size(max = 100, message = "Correlation ID must not exceed 100 characters")
    private String correlationId;

    /**
     * Timestamp when the event occurred
     */
    @NotNull(message = "Timestamp is required")
    private Instant timestamp;

    /**
     * Additional custom analytics data
     */
    private Map<String, Object> customMetrics;

    /**
     * Source system that generated this analytics event
     */
    @Builder.Default
    private String sourceSystem = "payment-service";

    /**
     * Version of the analytics data format
     */
    @Builder.Default
    private String version = "1.0";

    /**
     * Nested class for geographic data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GeographicData implements Serializable {
        private static final long serialVersionUID = 1L;

        private String country;
        private String state;
        private String city;
        private String zipCode;
        private Double latitude;
        private Double longitude;
        private String timezone;
        private String region;
    }

    /**
     * Nested class for time-based analytics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TimeAnalytics implements Serializable {
        private static final long serialVersionUID = 1L;

        private String dayOfWeek;
        private Integer hourOfDay;
        private String timeZone;
        private String season;
        private Boolean isWeekend;
        private Boolean isHoliday;
        private String quarterOfYear;
    }

    /**
     * Nested class for user analytics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserAnalytics implements Serializable {
        private static final long serialVersionUID = 1L;

        private String userSegment;
        private String ageGroup;
        private Integer accountAge;
        private Integer previousGroupPayments;
        private BigDecimal averageGroupPaymentAmount;
        private String userTier;
        private Boolean isFirstTimeCreator;
        private Integer totalFriends;
    }

    /**
     * Nested class for payment method analytics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodAnalytics implements Serializable {
        private static final long serialVersionUID = 1L;

        private String paymentMethod;
        private String cardType;
        private String bankName;
        private Integer usageCount;
        private Boolean isDefault;
        private String processingTime;
    }

    /**
     * Nested class for device analytics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceAnalytics implements Serializable {
        private static final long serialVersionUID = 1L;

        private String platform;
        private String deviceType;
        private String osVersion;
        private String appVersion;
        private String browser;
        private String ipAddress;
        private String userAgent;
    }

    /**
     * Nested class for performance metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long creationDuration;
        private Long averagePaymentTime;
        private Long timeToCompletion;
        private Double successRate;
        private Integer retryCount;
        private String errorCategory;
    }

    /**
     * Nested class for business metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BusinessMetrics implements Serializable {
        private static final long serialVersionUID = 1L;

        private BigDecimal feeAmount;
        private BigDecimal revenueGenerated;
        private String promotionCode;
        private BigDecimal discountAmount;
        private String merchantCategory;
        private Boolean isRecurring;
    }

    /**
     * Nested class for engagement metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EngagementMetrics implements Serializable {
        private static final long serialVersionUID = 1L;

        private Integer remindersSent;
        private Integer notificationsSent;
        private Integer appOpens;
        private Integer shareCount;
        private Double participantEngagementRate;
        private Long averageResponseTime;
    }

    /**
     * Nested class for risk metrics
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskMetrics implements Serializable {
        private static final long serialVersionUID = 1L;

        private Double riskScore;
        private String riskLevel;
        private List<String> riskFactors;
        private Boolean requiresReview;
        private String fraudIndicators;
        private Double velocityScore;
        private Boolean isHighValue;
    }
}