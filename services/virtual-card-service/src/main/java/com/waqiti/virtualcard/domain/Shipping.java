package com.waqiti.virtualcard.domain;

import com.waqiti.virtualcard.domain.enums.*;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.time.Instant;
import java.util.List;

/**
 * Shipping entity for tracking physical card shipping
 */
@Entity
@Table(name = "card_shipping", indexes = {
    @Index(name = "idx_shipping_card_id", columnList = "card_id"),
    @Index(name = "idx_shipping_order_id", columnList = "order_id"),
    @Index(name = "idx_shipping_tracking_number", columnList = "tracking_number"),
    @Index(name = "idx_shipping_status", columnList = "status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Shipping {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    @Column(name = "order_id", nullable = false)
    private String orderId;
    
    @Column(name = "card_id", nullable = false)
    private String cardId;
    
    @Column(name = "tracking_number", unique = true)
    private String trackingNumber;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private ShippingMethod method;
    
    @Embedded
    private ShippingAddress address;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ShippingStatus status;
    
    @Column(name = "carrier")
    private String carrier;
    
    @Column(name = "service_type")
    private String serviceType;
    
    @Column(name = "estimated_delivery")
    private Instant estimatedDelivery;
    
    @Column(name = "actual_delivery")
    private Instant actualDelivery;
    
    @Column(name = "shipped_at")
    private Instant shippedAt;
    
    @Column(name = "current_location")
    private String currentLocation;
    
    @Column(name = "last_update")
    private Instant lastUpdate;
    
    @Column(name = "delivery_attempts")
    @Builder.Default
    private int deliveryAttempts = 0;
    
    @Column(name = "delivery_notes", length = 1000)
    private String deliveryNotes;
    
    @ElementCollection
    @CollectionTable(name = "shipping_events", joinColumns = @JoinColumn(name = "shipping_id"))
    private List<ShippingEvent> events;
    
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