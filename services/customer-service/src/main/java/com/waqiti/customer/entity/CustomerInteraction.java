package com.waqiti.customer.entity;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Customer Interaction Entity
 *
 * Represents interactions with customers including calls, meetings, emails,
 * and other communication channels for relationship management.
 *
 * @author Waqiti Platform Team
 * @version 1.0
 * @since 2025-11-19
 */
@Entity
@Table(name = "customer_interaction", indexes = {
    @Index(name = "idx_customer_interaction_customer", columnList = "customer_id"),
    @Index(name = "idx_customer_interaction_type", columnList = "interaction_type"),
    @Index(name = "idx_customer_interaction_channel", columnList = "interaction_channel"),
    @Index(name = "idx_customer_interaction_date", columnList = "interaction_date")
})
@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
@EntityListeners(AuditingEntityListener.class)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "customer")
@EqualsAndHashCode(of = "interactionId")
public class CustomerInteraction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "interaction_id", unique = true, nullable = false, length = 100)
    private String interactionId;

    @Column(name = "customer_id", nullable = false, length = 100)
    private String customerId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", referencedColumnName = "customer_id", insertable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_type", nullable = false, length = 50)
    private InteractionType interactionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "interaction_channel", nullable = false, length = 50)
    private InteractionChannel interactionChannel;

    @Column(name = "interaction_date", nullable = false)
    @Builder.Default
    private LocalDateTime interactionDate = LocalDateTime.now();

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "subject", length = 255)
    private String subject;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "sentiment", length = 20)
    private Sentiment sentiment;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 50)
    private Outcome outcome;

    @Column(name = "handled_by", nullable = false, length = 100)
    private String handledBy;

    @Column(name = "follow_up_required")
    @Builder.Default
    private Boolean followUpRequired = false;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Enums
    public enum InteractionType {
        INQUIRY,
        COMPLAINT,
        FEEDBACK,
        SUPPORT,
        SALES,
        MARKETING,
        FOLLOW_UP,
        ONBOARDING,
        ACCOUNT_UPDATE,
        TRANSACTION_QUERY,
        PRODUCT_INQUIRY,
        SERVICE_REQUEST,
        COLLECTION,
        RETENTION,
        CROSS_SELL,
        UPSELL,
        SURVEY,
        ALERT_NOTIFICATION,
        OTHER
    }

    public enum InteractionChannel {
        PHONE,
        EMAIL,
        SMS,
        CHAT,
        VIDEO_CALL,
        IN_PERSON,
        MOBILE_APP,
        WEB_PORTAL,
        SOCIAL_MEDIA,
        MAIL,
        FAX,
        CHATBOT,
        VOICE_ASSISTANT,
        OTHER
    }

    public enum Sentiment {
        VERY_POSITIVE,
        POSITIVE,
        NEUTRAL,
        NEGATIVE,
        VERY_NEGATIVE
    }

    public enum Outcome {
        RESOLVED,
        PENDING,
        ESCALATED,
        REFERRED,
        CLOSED,
        CANCELLED,
        CONVERTED,
        NOT_INTERESTED,
        FOLLOW_UP_SCHEDULED,
        NO_RESPONSE,
        OTHER
    }

    /**
     * Check if follow-up is required
     *
     * @return true if follow-up required
     */
    public boolean isFollowUpRequired() {
        return followUpRequired != null && followUpRequired;
    }

    /**
     * Check if follow-up is overdue
     *
     * @return true if follow-up date has passed
     */
    public boolean isFollowUpOverdue() {
        if (followUpDate == null || !isFollowUpRequired()) {
            return false;
        }
        return LocalDate.now().isAfter(followUpDate);
    }

    /**
     * Check if interaction was positive
     *
     * @return true if sentiment is positive or very positive
     */
    public boolean isPositive() {
        return sentiment == Sentiment.POSITIVE || sentiment == Sentiment.VERY_POSITIVE;
    }

    /**
     * Check if interaction was negative
     *
     * @return true if sentiment is negative or very negative
     */
    public boolean isNegative() {
        return sentiment == Sentiment.NEGATIVE || sentiment == Sentiment.VERY_NEGATIVE;
    }

    /**
     * Check if interaction was resolved
     *
     * @return true if outcome is resolved
     */
    public boolean isResolved() {
        return outcome == Outcome.RESOLVED;
    }

    /**
     * Check if interaction was escalated
     *
     * @return true if outcome is escalated
     */
    public boolean isEscalated() {
        return outcome == Outcome.ESCALATED;
    }

    /**
     * Get duration in minutes
     *
     * @return duration in minutes
     */
    public Integer getDurationMinutes() {
        if (durationSeconds == null) {
            return null;
        }
        return durationSeconds / 60;
    }

    /**
     * Get duration in hours
     *
     * @return duration in hours
     */
    public Double getDurationHours() {
        if (durationSeconds == null) {
            return null;
        }
        return durationSeconds / 3600.0;
    }

    /**
     * Schedule follow-up
     *
     * @param followUpDate the follow-up date
     */
    public void scheduleFollowUp(LocalDate followUpDate) {
        this.followUpRequired = true;
        this.followUpDate = followUpDate;
    }

    /**
     * Complete follow-up
     */
    public void completeFollowUp() {
        this.followUpRequired = false;
        this.followUpDate = null;
    }

    /**
     * Set metadata value
     *
     * @param key the metadata key
     * @param value the metadata value
     */
    public void setMetadataValue(String key, Object value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    /**
     * Get metadata value
     *
     * @param key the metadata key
     * @return the metadata value, or null if not found
     */
    public Object getMetadataValue(String key) {
        if (this.metadata == null) {
            return null;
        }
        return this.metadata.get(key);
    }
}
