package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Card Design entity for managing available card designs
 */
@Entity
@Table(name = "card_designs", indexes = {
    @Index(name = "idx_card_design_active", columnList = "active"),
    @Index(name = "idx_card_design_premium", columnList = "is_premium")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
@Embeddable
public class CardDesign {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private String id;
    
    @Column(name = "design_code", unique = true, nullable = false)
    private String designCode;
    
    @Column(name = "name", nullable = false)
    private String name;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Column(name = "category")
    private String category;
    
    @Column(name = "image_url")
    private String imageUrl;
    
    @Column(name = "thumbnail_url")
    private String thumbnailUrl;
    
    @Column(name = "preview_url")
    private String previewUrl;
    
    @Builder.Default
    @Column(name = "is_premium")
    private boolean isPremium = false;
    
    @Column(name = "fee", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal fee = BigDecimal.ZERO;
    
    @Builder.Default
    @Column(name = "active")
    private boolean active = true;
    
    @Builder.Default
    @Column(name = "is_default")
    private boolean isDefault = false;
    
    @Column(name = "sort_order")
    @Builder.Default
    private int sortOrder = 0;
    
    @Column(name = "available_from")
    private Instant availableFrom;
    
    @Column(name = "available_until")
    private Instant availableUntil;
    
    @Column(name = "max_orders")
    private Integer maxOrders;
    
    @Column(name = "current_orders")
    @Builder.Default
    private int currentOrders = 0;
    
    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
    
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
    
    public boolean isAvailable() {
        Instant now = Instant.now();
        return active && 
               (availableFrom == null || !now.isBefore(availableFrom)) &&
               (availableUntil == null || !now.isAfter(availableUntil)) &&
               (maxOrders == null || currentOrders < maxOrders);
    }
}