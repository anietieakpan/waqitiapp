package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Engagement Entity
 *
 * Represents customer engagement metrics including interaction patterns,
 * response rates, and engagement scoring for marketing and retention.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_engagement", indexes = {
    @Index(name = "idx_customer_engagement_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_engagement_tier", columnList = "engagement_tier"),
    @Index(name = "idx_customer_engagement_score", columnList = "engagement_score"),
    @Index(name = "idx_customer_engagement_last", columnList = "last_engagement_date")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "engagementId")
public class CustomerEngagement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "engagement_id", unique = true, nullable = false, length = 100)
    private String engagementId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "engagement_type", length = 50)
    private EngagementType engagementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "engagement_channel", length = 50)
    private EngagementChannel engagementChannel;

    @Column(name = "engagement_score", precision = 5, scale = 2)
    private BigDecimal engagementScore;

    @Column(name = "interaction_count")
    @Builder.Default
    private Integer interactionCount = 0;

    @Column(name = "last_engagement_date")
    private LocalDateTime lastEngagementDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "engagement_frequency", length = 20)
    private EngagementFrequency engagementFrequency;

    @Column(name = "response_rate", precision = 5, scale = 4)
    private BigDecimal responseRate;

    @Column(name = "click_through_rate", precision = 5, scale = 4)
    private BigDecimal clickThroughRate;

    @Column(name = "conversion_rate", precision = 5, scale = 4)
    private BigDecimal conversionRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "engagement_tier", length = 20)
    private EngagementTier engagementTier;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum EngagementType {
        MARKETING,
        TRANSACTIONAL,
        SUPPORT,
        EDUCATIONAL,
        PROMOTIONAL,
        SERVICE_UPDATE,
        PRODUCT_USAGE,
        FEEDBACK,
        OTHER
    }

    public enum EngagementChannel {
        EMAIL,
        SMS,
        PUSH,
        IN_APP,
        CALL,
        CHAT,
        SOCIAL_MEDIA,
        WEB,
        MOBILE,
        OTHER
    }

    public enum EngagementFrequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        QUARTERLY,
        RARELY,
        INACTIVE
    }

    public enum EngagementTier {
        HIGH,
        MEDIUM,
        LOW
    }

    /**
     * Check if customer is highly engaged
     *
     * @return true if engagement tier is HIGH
     */
    public boolean isHighlyEngaged() {
        return engagementTier == EngagementTier.HIGH;
    }

    /**
     * Check if customer has low engagement
     *
     * @return true if engagement tier is LOW
     */
    public boolean hasLowEngagement() {
        return engagementTier == EngagementTier.LOW;
    }

    /**
     * Check if customer is inactive
     *
     * @return true if engagement frequency is INACTIVE
     */
    public boolean isInactive() {
        return engagementFrequency == EngagementFrequency.INACTIVE;
    }

    /**
     * Check if customer has high response rate
     *
     * @return true if response rate is greater than 0.7
     */
    public boolean hasHighResponseRate() {
        return responseRate != null &&
               responseRate.compareTo(new BigDecimal("0.70")) > 0;
    }

    /**
     * Check if customer has high conversion rate
     *
     * @return true if conversion rate is greater than 0.5
     */
    public boolean hasHighConversionRate() {
        return conversionRate != null &&
               conversionRate.compareTo(new BigDecimal("0.50")) > 0;
    }

    /**
     * Get days since last engagement
     *
     * @return days since last engagement, null if never engaged
     */
    public Long getDaysSinceLastEngagement() {
        if (lastEngagementDate == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(lastEngagementDate, LocalDateTime.now());
    }

    /**
     * Increment interaction count
     */
    public void incrementInteractionCount() {
        if (this.interactionCount == null) {
            this.interactionCount = 1;
        } else {
            this.interactionCount++;
        }
        this.lastEngagementDate = LocalDateTime.now();
    }

    /**
     * Update engagement score
     *
     * @param score the new engagement score
     */
    public void updateEngagementScore(BigDecimal score) {
        this.engagementScore = score;
        updateEngagementTier();
    }

    /**
     * Update engagement tier based on score
     */
    private void updateEngagementTier() {
        if (engagementScore == null) {
            this.engagementTier = EngagementTier.LOW;
        } else if (engagementScore.compareTo(new BigDecimal("70")) >= 0) {
            this.engagementTier = EngagementTier.HIGH;
        } else if (engagementScore.compareTo(new BigDecimal("40")) >= 0) {
            this.engagementTier = EngagementTier.MEDIUM;
        } else {
            this.engagementTier = EngagementTier.LOW;
        }
    }

    /**
     * Calculate overall engagement health score
     *
     * @return health score (0-100)
     */
    public BigDecimal calculateHealthScore() {
        if (engagementScore == null && responseRate == null && clickThroughRate == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal score = BigDecimal.ZERO;
        int factors = 0;

        if (engagementScore != null) {
            score = score.add(engagementScore);
            factors++;
        }

        if (responseRate != null) {
            score = score.add(responseRate.multiply(new BigDecimal("100")));
            factors++;
        }

        if (clickThroughRate != null) {
            score = score.add(clickThroughRate.multiply(new BigDecimal("100")));
            factors++;
        }

        if (factors == 0) {
            return BigDecimal.ZERO;
        }

        return score.divide(new BigDecimal(factors), 2, java.math.RoundingMode.HALF_UP);
    }
}
