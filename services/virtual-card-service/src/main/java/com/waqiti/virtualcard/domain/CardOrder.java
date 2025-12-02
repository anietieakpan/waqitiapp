package com.waqiti.virtualcard.domain;

import com.waqiti.virtualcard.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Card Order entity for tracking physical card orders
 */
@Entity
@Table(name = "card_orders", indexes = {
    @Index(name = "idx_card_order_user_id", columnList = "user_id"),
    @Index(name = "idx_card_order_status", columnList = "status"),
    @Index(name = "idx_card_order_type", columnList = "type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class CardOrder {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "original_card_id")
    private String originalCardId;
    
    @Column(name = "provider_order_id")
    private String providerOrderId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CardType type;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "brand", nullable = false)
    private CardBrand brand;
    
    @Embedded
    private CardDesign design;
    
    @Embedded
    private CardPersonalization personalization;
    
    @Embedded
    private ShippingAddress shippingAddress;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_method", nullable = false)
    private ShippingMethod shippingMethod;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;
    
    @Column(name = "order_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal orderFee = BigDecimal.ZERO;
    
    @Column(name = "shipping_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal shippingFee = BigDecimal.ZERO;
    
    @Column(name = "total_fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal totalFee = BigDecimal.ZERO;
    
    @Column(name = "ordered_at", nullable = false)
    private Instant orderedAt;
    
    @Column(name = "submitted_at")
    private Instant submittedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    
    @Column(name = "estimated_delivery")
    private Instant estimatedDelivery;
    
    @Builder.Default
    @Column(name = "is_replacement")
    private boolean isReplacement = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "replacement_reason")
    private ReplacementReason replacementReason;
    
    @Column(name = "notes", length = 1000)
    private String notes;
    
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