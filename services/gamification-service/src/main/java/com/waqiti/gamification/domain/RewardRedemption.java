package com.waqiti.gamification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "reward_redemptions", indexes = {
    @Index(name = "idx_reward_redemption_user_id", columnList = "user_id"),
    @Index(name = "idx_reward_redemption_status", columnList = "status"),
    @Index(name = "idx_reward_redemption_date", columnList = "redeemed_at"),
    @Index(name = "idx_reward_redemption_code", columnList = "redemption_code")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardRedemption {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "redemption_code", nullable = false, unique = true, length = 50)
    private String redemptionCode;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reward_id", nullable = false)
    private Reward reward;
    
    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private RedemptionStatus status = RedemptionStatus.PENDING;
    
    @Column(name = "points_spent", nullable = false)
    private Long pointsSpent;
    
    @Column(name = "cash_value", precision = 19, scale = 2)
    private BigDecimal cashValue;
    
    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 1;
    
    @Column(name = "redeemed_at", nullable = false)
    private LocalDateTime redeemedAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "delivery_method")
    @Enumerated(EnumType.STRING)
    private Reward.DeliveryMethod deliveryMethod;
    
    @Column(name = "delivery_address", columnDefinition = "TEXT")
    private String deliveryAddress;
    
    @Column(name = "delivery_email", length = 200)
    private String deliveryEmail;
    
    @Column(name = "delivery_phone", length = 20)
    private String deliveryPhone;
    
    @Column(name = "tracking_number", length = 100)
    private String trackingNumber;
    
    @Column(name = "voucher_code", length = 100)
    private String voucherCode;
    
    @Column(name = "voucher_url", length = 500)
    private String voucherUrl;
    
    @Column(name = "voucher_instructions", columnDefinition = "TEXT")
    private String voucherInstructions;
    
    @Column(name = "provider_reference", length = 100)
    private String providerReference;
    
    @Column(name = "provider_status", length = 50)
    private String providerStatus;
    
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    
    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private Integer retryCount = 0;
    
    @Column(name = "last_retry_at")
    private LocalDateTime lastRetryAt;
    
    @Column(name = "notification_sent", nullable = false)
    @Builder.Default
    private Boolean notificationSent = false;
    
    @Column(name = "user_rating", precision = 3, scale = 2)
    private BigDecimal userRating;
    
    @Column(name = "user_review", columnDefinition = "TEXT")
    private String userReview;
    
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;
    
    @Column(name = "is_gift", nullable = false)
    @Builder.Default
    private Boolean isGift = false;
    
    @Column(name = "gift_recipient_email", length = 200)
    private String giftRecipientEmail;
    
    @Column(name = "gift_message", length = 500)
    private String giftMessage;
    
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    public enum RedemptionStatus {
        PENDING,
        PROCESSING,
        CONFIRMED,
        SHIPPED,
        DELIVERED,
        COMPLETED,
        FAILED,
        CANCELLED,
        REFUNDED,
        EXPIRED
    }
    
    public boolean isActive() {
        return status == RedemptionStatus.CONFIRMED || 
               status == RedemptionStatus.SHIPPED || 
               status == RedemptionStatus.DELIVERED;
    }
    
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
    }
    
    public boolean canBeReviewed() {
        return status == RedemptionStatus.DELIVERED || status == RedemptionStatus.COMPLETED;
    }
    
    public boolean isCompleted() {
        return status == RedemptionStatus.COMPLETED || status == RedemptionStatus.DELIVERED;
    }
    
    public boolean hasFailed() {
        return status == RedemptionStatus.FAILED || status == RedemptionStatus.CANCELLED;
    }
}