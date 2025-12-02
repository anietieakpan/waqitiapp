package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Satisfaction Entity
 *
 * Represents customer satisfaction metrics including CSAT, NPS, CES scores,
 * and detailed quality ratings for experience management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_satisfaction", indexes = {
    @Index(name = "idx_customer_satisfaction_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_satisfaction_survey_type", columnList = "survey_type"),
    @Index(name = "idx_customer_satisfaction_date", columnList = "survey_date"),
    @Index(name = "idx_customer_satisfaction_overall", columnList = "overall_satisfaction")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "satisfactionId")
public class CustomerSatisfaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "satisfaction_id", unique = true, nullable = false, length = 100)
    private String satisfactionId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "survey_type", nullable = false, length = 50)
    private SurveyType surveyType;

    @Column(name = "csat_score")
    private Integer csatScore; // 1-5 scale (Customer Satisfaction Score)

    @Column(name = "nps_score")
    private Integer npsScore; // -100 to 100 scale (Net Promoter Score)

    @Column(name = "ces_score")
    private Integer cesScore; // 1-7 scale (Customer Effort Score)

    @Column(name = "overall_satisfaction", precision = 3, scale = 2)
    private BigDecimal overallSatisfaction; // 0-100 scale

    @Column(name = "service_quality_score", precision = 3, scale = 2)
    private BigDecimal serviceQualityScore; // 0-100 scale

    @Column(name = "product_quality_score", precision = 3, scale = 2)
    private BigDecimal productQualityScore; // 0-100 scale

    @Column(name = "value_for_money_score", precision = 3, scale = 2)
    private BigDecimal valueForMoneyScore; // 0-100 scale

    @Column(name = "survey_date", nullable = false)
    @Builder.Default
    private LocalDate surveyDate = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "survey_channel", length = 50)
    private SurveyChannel surveyChannel;

    @Column(name = "response_time_seconds")
    private Integer responseTimeSeconds;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum SurveyType {
        CSAT,
        NPS,
        CES,
        TRANSACTIONAL,
        RELATIONSHIP,
        ONBOARDING,
        POST_PURCHASE,
        POST_SUPPORT,
        QUARTERLY,
        ANNUAL,
        EXIT,
        OTHER
    }

    public enum SurveyChannel {
        EMAIL,
        SMS,
        IN_APP,
        WEB,
        PHONE,
        CHAT,
        OTHER
    }

    /**
     * Check if customer is satisfied (CSAT >= 4)
     *
     * @return true if satisfied
     */
    public boolean isSatisfied() {
        return csatScore != null && csatScore >= 4;
    }

    /**
     * Check if customer is dissatisfied (CSAT <= 2)
     *
     * @return true if dissatisfied
     */
    public boolean isDissatisfied() {
        return csatScore != null && csatScore <= 2;
    }

    /**
     * Check if customer is a promoter (NPS 9-10)
     *
     * @return true if promoter
     */
    public boolean isPromoter() {
        return npsScore != null && npsScore >= 9;
    }

    /**
     * Check if customer is a detractor (NPS 0-6)
     *
     * @return true if detractor
     */
    public boolean isDetractor() {
        return npsScore != null && npsScore <= 6;
    }

    /**
     * Check if customer is passive (NPS 7-8)
     *
     * @return true if passive
     */
    public boolean isPassive() {
        return npsScore != null && npsScore >= 7 && npsScore <= 8;
    }

    /**
     * Check if low effort (CES 1-3)
     *
     * @return true if low effort
     */
    public boolean isLowEffort() {
        return cesScore != null && cesScore <= 3;
    }

    /**
     * Check if high effort (CES 5-7)
     *
     * @return true if high effort
     */
    public boolean isHighEffort() {
        return cesScore != null && cesScore >= 5;
    }

    /**
     * Check if overall satisfaction is high (>= 70)
     *
     * @return true if highly satisfied
     */
    public boolean isHighlySatisfied() {
        return overallSatisfaction != null &&
               overallSatisfaction.compareTo(new BigDecimal("70")) >= 0;
    }

    /**
     * Check if overall satisfaction is low (< 50)
     *
     * @return true if low satisfaction
     */
    public boolean hasLowSatisfaction() {
        return overallSatisfaction != null &&
               overallSatisfaction.compareTo(new BigDecimal("50")) < 0;
    }

    /**
     * Get response time in minutes
     *
     * @return response time in minutes
     */
    public Integer getResponseTimeMinutes() {
        if (responseTimeSeconds == null) {
            return null;
        }
        return responseTimeSeconds / 60;
    }

    /**
     * Calculate composite satisfaction score
     *
     * @return composite score (0-100)
     */
    public BigDecimal calculateCompositeScore() {
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;

        if (overallSatisfaction != null) {
            total = total.add(overallSatisfaction);
            count++;
        }

        if (serviceQualityScore != null) {
            total = total.add(serviceQualityScore);
            count++;
        }

        if (productQualityScore != null) {
            total = total.add(productQualityScore);
            count++;
        }

        if (valueForMoneyScore != null) {
            total = total.add(valueForMoneyScore);
            count++;
        }

        if (count == 0) {
            return BigDecimal.ZERO;
        }

        return total.divide(new BigDecimal(count), 2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Get satisfaction level description
     *
     * @return satisfaction level
     */
    public String getSatisfactionLevel() {
        if (overallSatisfaction == null) {
            return "UNKNOWN";
        }

        if (overallSatisfaction.compareTo(new BigDecimal("80")) >= 0) {
            return "VERY_SATISFIED";
        } else if (overallSatisfaction.compareTo(new BigDecimal("60")) >= 0) {
            return "SATISFIED";
        } else if (overallSatisfaction.compareTo(new BigDecimal("40")) >= 0) {
            return "NEUTRAL";
        } else if (overallSatisfaction.compareTo(new BigDecimal("20")) >= 0) {
            return "DISSATISFIED";
        } else {
            return "VERY_DISSATISFIED";
        }
    }

    /**
     * Convert CSAT to percentage
     *
     * @return CSAT as percentage (0-100)
     */
    public BigDecimal getCsatPercentage() {
        if (csatScore == null) {
            return null;
        }
        return new BigDecimal(csatScore).multiply(new BigDecimal("20")); // Convert 1-5 to 20-100
    }

    /**
     * Convert CES to percentage
     *
     * @return CES as percentage (0-100)
     */
    public BigDecimal getCesPercentage() {
        if (cesScore == null) {
            return null;
        }
        // Inverted: lower effort is better, so 7 is worst (0%) and 1 is best (100%)
        return new BigDecimal(8 - cesScore).multiply(new BigDecimal("100")).divide(new BigDecimal("7"), 2, java.math.RoundingMode.HALF_UP);
    }
}
