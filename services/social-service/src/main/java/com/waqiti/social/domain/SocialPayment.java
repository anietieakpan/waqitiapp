package com.waqiti.social.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "social_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialPayment {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "payment_id", unique = true, nullable = false, length = 50)
    private String paymentId;
    
    @Column(name = "sender_id", nullable = false)
    private UUID senderId;
    
    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";
    
    @Column(name = "fee_amount", precision = 19, scale = 2)
    private BigDecimal feeAmount = BigDecimal.ZERO;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 20)
    private PaymentType paymentType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;
    
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;
    
    @Column(name = "emoji", length = 10)
    private String emoji;
    
    @Column(name = "is_public")
    private Boolean isPublic = false;
    
    @Column(name = "visibility", length = 20)
    private String visibility = "PRIVATE"; // PRIVATE, FRIENDS, PUBLIC
    
    @Type(type = "jsonb")
    @Column(name = "media_attachments", columnDefinition = "jsonb")
    private List<String> mediaAttachments; // URLs to images/videos
    
    @Type(type = "jsonb")
    @Column(name = "tags", columnDefinition = "jsonb")
    private List<String> tags;
    
    @Column(name = "location", length = 200)
    private String location;
    
    @Column(name = "occasion", length = 100)
    private String occasion; // BIRTHDAY, WEDDING, DINNER, etc.
    
    @Column(name = "request_expires_at")
    private LocalDateTime requestExpiresAt;
    
    @Type(type = "jsonb")
    @Column(name = "split_details", columnDefinition = "jsonb")
    private Map<String, Object> splitDetails; // For bill splitting
    
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "reference_id", length = 100)
    private String referenceId; // External reference
    
    @Column(name = "group_id")
    private UUID groupId; // For group payments
    
    @Column(name = "recurring_payment_id")
    private UUID recurringPaymentId;
    
    @Column(name = "tip_amount", precision = 19, scale = 2)
    private BigDecimal tipAmount = BigDecimal.ZERO;
    
    @Column(name = "cashback_amount", precision = 19, scale = 2)
    private BigDecimal cashbackAmount = BigDecimal.ZERO;
    
    @Column(name = "rewards_earned", precision = 19, scale = 2)
    private BigDecimal rewardsEarned = BigDecimal.ZERO;
    
    @Column(name = "source_method", length = 50)
    private String sourceMethod; // WALLET, BANK_ACCOUNT, CARD
    
    @Column(name = "initiated_via", length = 50)
    private String initiatedVia; // APP, SMS, EMAIL, QR_CODE, NFC, VOICE
    
    @Column(name = "device_info", length = 500)
    private String deviceInfo;
    
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
    
    @Column(name = "transaction_id", length = 100)
    private String transactionId; // Core payment system transaction ID
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "requested_at")
    private LocalDateTime requestedAt;
    
    @Column(name = "sent_at")
    private LocalDateTime sentAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "failed_at")
    private LocalDateTime failedAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "failure_reason", length = 255)
    private String failureReason;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (paymentId == null) {
            paymentId = "SP_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum PaymentType {
        SEND,           // Direct send
        REQUEST,        // Payment request
        SPLIT,          // Bill split
        GROUP,          // Group payment
        RECURRING,      // Recurring payment
        TIP,            // Tip payment
        GIFT,           // Gift payment
        CHARITY,        // Charity donation
        REFUND,         // Refund payment
        REWARD          // Reward payment
    }
    
    public enum PaymentStatus {
        PENDING,        // Payment created but not processed
        REQUESTED,      // Payment requested (waiting for approval)
        APPROVED,       // Payment approved (ready to process)
        PROCESSING,     // Payment being processed
        COMPLETED,      // Payment successfully completed
        FAILED,         // Payment failed
        CANCELLED,      // Payment cancelled
        EXPIRED,        // Payment request expired
        DECLINED,       // Payment request declined
        REFUNDED        // Payment refunded
    }
    
    public boolean isActive() {
        return status == PaymentStatus.PENDING || 
               status == PaymentStatus.REQUESTED || 
               status == PaymentStatus.APPROVED || 
               status == PaymentStatus.PROCESSING;
    }
    
    public boolean isCompleted() {
        return status == PaymentStatus.COMPLETED;
    }
    
    public boolean canBeCancelled() {
        return status == PaymentStatus.PENDING || 
               status == PaymentStatus.REQUESTED || 
               status == PaymentStatus.APPROVED;
    }
    
    public BigDecimal getTotalAmount() {
        return amount.add(feeAmount).add(tipAmount);
    }
}