package com.waqiti.customer.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Feedback Entity
 *
 * Represents customer feedback including ratings, NPS scores, sentiment analysis,
 * and responses for continuous improvement and quality monitoring.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_feedback", indexes = {
    @Index(name = "idx_customer_feedback_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_feedback_type", columnList = "feedback_type"),
    @Index(name = "idx_customer_feedback_rating", columnList = "rating"),
    @Index(name = "idx_customer_feedback_sentiment", columnList = "sentiment"),
    @Index(name = "idx_customer_feedback_submitted", columnList = "submitted_at")
})
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "feedbackId")
public class CustomerFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "feedback_id", unique = true, nullable = false, length = 100)
    private String feedbackId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_type", nullable = false, length = 50)
    private FeedbackType feedbackType;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_source", length = 50)
    private FeedbackSource feedbackSource;

    @Column(name = "rating")
    private Integer rating; // 1-5 scale

    @Column(name = "nps_score")
    private Integer npsScore; // -100 to 100 scale

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment", length = 20)
    private Sentiment sentiment;

    @Column(name = "feedback_text", columnDefinition = "TEXT")
    private String feedbackText;

    @Enumerated(EnumType.STRING)
    @Column(name = "feedback_category", length = 50)
    private FeedbackCategory feedbackCategory;

    @Column(name = "submitted_at", nullable = false)
    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "action_taken", columnDefinition = "TEXT")
    private String actionTaken;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Enums
    public enum FeedbackType {
        PRODUCT,
        SERVICE,
        SUPPORT,
        FEATURE_REQUEST,
        BUG_REPORT,
        GENERAL,
        NPS_SURVEY,
        SATISFACTION_SURVEY,
        EXIT_SURVEY,
        OTHER
    }

    public enum FeedbackSource {
        EMAIL,
        SMS,
        PHONE,
        CHAT,
        WEB_FORM,
        MOBILE_APP,
        IN_APP,
        SOCIAL_MEDIA,
        SURVEY,
        REVIEW_SITE,
        OTHER
    }

    public enum Sentiment {
        POSITIVE,
        NEUTRAL,
        NEGATIVE
    }

    public enum FeedbackCategory {
        USABILITY,
        PERFORMANCE,
        RELIABILITY,
        FEATURES,
        PRICING,
        CUSTOMER_SERVICE,
        SECURITY,
        ACCESSIBILITY,
        DOCUMENTATION,
        ONBOARDING,
        OTHER
    }

    /**
     * Check if feedback is positive
     *
     * @return true if sentiment is POSITIVE
     */
    public boolean isPositive() {
        return sentiment == Sentiment.POSITIVE;
    }

    /**
     * Check if feedback is negative
     *
     * @return true if sentiment is NEGATIVE
     */
    public boolean isNegative() {
        return sentiment == Sentiment.NEGATIVE;
    }

    /**
     * Check if feedback has been responded to
     *
     * @return true if response exists
     */
    public boolean isResponded() {
        return respondedAt != null && responseText != null;
    }

    /**
     * Check if action was taken
     *
     * @return true if action taken is documented
     */
    public boolean hasActionTaken() {
        return actionTaken != null && !actionTaken.isEmpty();
    }

    /**
     * Check if customer is a promoter (NPS >= 9)
     *
     * @return true if promoter
     */
    public boolean isPromoter() {
        return npsScore != null && npsScore >= 9;
    }

    /**
     * Check if customer is a detractor (NPS <= 6)
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
     * Check if rating is high (4-5)
     *
     * @return true if high rating
     */
    public boolean hasHighRating() {
        return rating != null && rating >= 4;
    }

    /**
     * Check if rating is low (1-2)
     *
     * @return true if low rating
     */
    public boolean hasLowRating() {
        return rating != null && rating <= 2;
    }

    /**
     * Respond to feedback
     *
     * @param response the response text
     */
    public void respond(String response) {
        this.responseText = response;
        this.respondedAt = LocalDateTime.now();
    }

    /**
     * Document action taken
     *
     * @param action the action taken description
     */
    public void documentAction(String action) {
        this.actionTaken = action;
    }

    /**
     * Get response time in hours
     *
     * @return response time in hours, null if not responded
     */
    public Long getResponseTimeHours() {
        if (respondedAt == null || submittedAt == null) {
            return null;
        }
        return java.time.temporal.ChronoUnit.HOURS.between(submittedAt, respondedAt);
    }
}
