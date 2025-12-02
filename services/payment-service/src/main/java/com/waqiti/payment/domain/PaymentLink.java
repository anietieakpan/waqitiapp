package com.waqiti.payment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Payment Link entity for generating shareable payment links
 * Allows users to create links that others can use to send them money
 */
@Entity
@Table(name = "payment_links")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentLink {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "link_id", nullable = false, unique = true)
    private String linkId; // Short, shareable identifier
    
    @Column(name = "creator_id", nullable = false)
    private UUID creatorId;
    
    @Column(name = "title", nullable = false, length = 100)
    private String title;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "amount", precision = 19, scale = 2)
    private BigDecimal amount; // null for flexible amount
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "min_amount", precision = 19, scale = 2)
    private BigDecimal minAmount;
    
    @Column(name = "max_amount", precision = 19, scale = 2)
    private BigDecimal maxAmount;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false)
    private PaymentLinkType linkType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentLinkStatus status;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "max_uses")
    private Integer maxUses; // null for unlimited
    
    @Column(name = "current_uses", nullable = false)
    @Builder.Default
    private Integer currentUses = 0;
    
    @Column(name = "total_collected", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalCollected = BigDecimal.ZERO;
    
    @Column(name = "requires_note")
    @Builder.Default
    private Boolean requiresNote = false;
    
    @Column(name = "custom_message", length = 1000)
    private String customMessage;
    
    @ElementCollection
    @CollectionTable(name = "payment_link_metadata", joinColumns = @JoinColumn(name = "payment_link_id"))
    @MapKeyColumn(name = "meta_key")
    @Column(name = "meta_value")
    private Map<String, String> metadata;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;
    
    // Business logic methods
    
    public boolean isActive() {
        return status == PaymentLinkStatus.ACTIVE;
    }
    
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    public boolean hasReachedMaxUses() {
        return maxUses != null && currentUses >= maxUses;
    }
    
    public boolean canAcceptPayment() {
        return isActive() && !isExpired() && !hasReachedMaxUses();
    }
    
    public void incrementUsage() {
        this.currentUses++;
        this.lastUsedAt = LocalDateTime.now();
        
        // Auto-deactivate if max uses reached
        if (hasReachedMaxUses()) {
            this.status = PaymentLinkStatus.COMPLETED;
        }
    }
    
    public void addToTotalCollected(BigDecimal amount) {
        this.totalCollected = this.totalCollected.add(amount);
    }
    
    public void deactivate() {
        this.status = PaymentLinkStatus.INACTIVE;
    }
    
    public void activate() {
        if (status == PaymentLinkStatus.INACTIVE) {
            this.status = PaymentLinkStatus.ACTIVE;
        }
    }
    
    public void expire() {
        this.status = PaymentLinkStatus.EXPIRED;
    }
    
    public void complete() {
        this.status = PaymentLinkStatus.COMPLETED;
    }
    
    public boolean isFlexibleAmount() {
        return amount == null;
    }
    
    public boolean isAmountValid(BigDecimal paymentAmount) {
        if (!isFlexibleAmount()) {
            return amount.compareTo(paymentAmount) == 0;
        }
        
        boolean aboveMin = minAmount == null || paymentAmount.compareTo(minAmount) >= 0;
        boolean belowMax = maxAmount == null || paymentAmount.compareTo(maxAmount) <= 0;
        
        return aboveMin && belowMax;
    }
    
    public String getShareableUrl() {
        return String.format("/pay/%s", linkId);
    }
    
    public enum PaymentLinkType {
        REQUEST_MONEY,      // Request specific amount
        DONATION,           // Accept any amount within range
        INVOICE,            // Business invoice payment
        SUBSCRIPTION,       // Recurring payment setup
        EVENT_TICKET,       // Event or ticket payment
        PRODUCT_SALE        // Product purchase
    }
    
    public enum PaymentLinkStatus {
        ACTIVE,     // Currently accepting payments
        INACTIVE,   // Temporarily disabled
        EXPIRED,    // Past expiration date
        COMPLETED,  // Reached max uses or manually completed
        CANCELLED   // Permanently cancelled
    }
}