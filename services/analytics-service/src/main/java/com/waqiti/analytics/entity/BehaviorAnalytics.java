package com.waqiti.analytics.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Entity representing user behavior analytics
 */
@Entity
@Table(name = "behavior_analytics", indexes = {
    @Index(name = "idx_behavior_user", columnList = "userId"),
    @Index(name = "idx_behavior_session", columnList = "sessionId"),
    @Index(name = "idx_behavior_date", columnList = "activityDate"),
    @Index(name = "idx_behavior_type", columnList = "behaviorType")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class BehaviorAnalytics {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String sessionId;

    @Column(nullable = false)
    private LocalDateTime activityDate;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private BehaviorType behaviorType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ActivityType activityType;

    @Column
    private String deviceType;

    @Column
    private String platform;

    @Column
    private String appVersion;

    @Column
    private String location;

    @Column
    private String ipAddress;

    // User interaction metrics
    @Column
    private Integer sessionDurationSeconds;

    @Column
    private Integer pageViews;

    @Column
    private Integer clickCount;

    @Column
    private Integer scrollDepthPercentage;

    @Column
    private BigDecimal transactionAmount;

    @Column
    private String transactionCategory;

    // Behavioral patterns
    @Column
    private LocalTime typicalActivityStartTime;

    @Column
    private LocalTime typicalActivityEndTime;

    @Column
    private String preferredFeatures;

    @Column
    private String unusualActivity;

    @Column
    private Double engagementScore; // 0.0 to 100.0

    @Column
    private Double riskScore; // 0.0 to 100.0

    // Navigation patterns
    @Column(columnDefinition = "TEXT")
    private String navigationPath;

    @Column
    private String entryPoint;

    @Column
    private String exitPoint;

    @Column
    private Boolean completedGoal;

    @Column
    private String goalType;

    // Feature usage
    @Column
    private String featuresUsed;

    @Column
    private Integer featureInteractionCount;

    @Column
    private String mostUsedFeature;

    // Anomaly detection
    @Column
    private Boolean isAnomaly;

    @Column(columnDefinition = "TEXT")
    private String anomalyReasons;

    @Column
    private Double anomalyScore;

    // User preferences learned
    @Column
    private String preferredTransactionTime;

    @Column
    private String preferredPaymentMethod;

    @Column
    private String preferredMerchants;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum BehaviorType {
        NORMAL, SUSPICIOUS, FRAUDULENT, NEW_PATTERN, RETURNING_PATTERN
    }

    public enum ActivityType {
        LOGIN, LOGOUT, TRANSACTION, TRANSFER, PAYMENT, 
        PROFILE_UPDATE, SETTINGS_CHANGE, CARD_MANAGEMENT,
        ACCOUNT_VIEW, STATEMENT_DOWNLOAD, SUPPORT_CONTACT,
        FEATURE_EXPLORATION, OFFER_INTERACTION, OTHER
    }
}