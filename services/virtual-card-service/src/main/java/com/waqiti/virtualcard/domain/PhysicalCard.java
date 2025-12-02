package com.waqiti.virtualcard.domain;

import com.waqiti.virtualcard.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Physical Card entity representing physical payment cards
 */
@Entity
@Table(name = "physical_cards", indexes = {
    @Index(name = "idx_physical_card_user_id", columnList = "user_id"),
    @Index(name = "idx_physical_card_status", columnList = "status"),
    @Index(name = "idx_physical_card_provider_id", columnList = "provider_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PhysicalCard {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    @Column(name = "order_id", nullable = false)
    private String orderId;
    
    @Column(name = "original_card_id")
    private String originalCardId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "provider_id", nullable = false, unique = true)
    private String providerId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CardType type;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "brand", nullable = false)
    private CardBrand brand;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CardStatus status;
    
    @Embedded
    private CardDesign design;
    
    @Embedded
    private CardPersonalization personalization;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Column(name = "balance", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;
    
    @Column(name = "last_four_digits", length = 4)
    private String lastFourDigits;
    
    @Column(name = "expiry_month")
    private Integer expiryMonth;
    
    @Column(name = "expiry_year")
    private Integer expiryYear;
    
    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;
    
    @Column(name = "delivered_at")
    private Instant deliveredAt;
    
    @Column(name = "activated_at")
    private Instant activatedAt;
    
    @Column(name = "blocked_at")
    private Instant blockedAt;
    
    @Column(name = "replaced_at")
    private Instant replacedAt;
    
    @Column(name = "closed_at")
    private Instant closedAt;
    
    @Column(name = "estimated_delivery")
    private Instant estimatedDelivery;
    
    @Column(name = "production_updated_at")
    private Instant productionUpdatedAt;
    
    @Column(name = "block_reason")
    private String blockReason;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "replacement_reason")
    private ReplacementReason replacementReason;
    
    @Builder.Default
    @Column(name = "is_replacement")
    private boolean isReplacement = false;
    
    @Builder.Default
    @Column(name = "pin_set")
    private boolean pinSet = false;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}