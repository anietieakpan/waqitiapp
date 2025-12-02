package com.waqiti.virtualcard.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Virtual Card Entity
 * Represents a virtual payment card in the system
 */
@Entity
@Table(name = "virtual_cards", indexes = {
    @Index(name = "idx_user_id", columnList = "user_id"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_card_type", columnList = "card_type"),
    @Index(name = "idx_created_at", columnList = "created_at"),
    @Index(name = "idx_network_token", columnList = "network_token")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class VirtualCard {
    
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @EqualsAndHashCode.Include
    private String id;
    
    /**
     * Version for optimistic locking to prevent concurrent modification issues
     * Critical for financial operations like totalSpent and usageCount updates
     */
    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 30)
    private CardType cardType;
    
    @Column(name = "card_purpose", length = 200)
    private String cardPurpose;
    
    @Column(name = "encrypted_card_number", nullable = false, length = 500)
    private String encryptedCardNumber;
    
    @Column(name = "masked_card_number", nullable = false, length = 19)
    private String maskedCardNumber;

    // PCI DSS FIX: CVV storage removed per PCI DSS Requirement 3.2.2
    // CVV must NEVER be stored, even if encrypted
    // CVV is only validated during authorization and immediately discarded

    @Column(name = "expiry_month", nullable = false)
    private int expiryMonth;
    
    @Column(name = "expiry_year", nullable = false)
    private int expiryYear;
    
    @Column(name = "cardholder_name", nullable = false, length = 100)
    private String cardholderName;
    
    @Column(name = "billing_address", columnDefinition = "TEXT")
    private String billingAddress;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CardStatus status;
    
    @Column(name = "is_locked", nullable = false)
    private boolean isLocked;
    
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;
    
    @Column(name = "lock_reason", length = 200)
    private String lockReason;
    
    @Column(name = "is_pin_enabled", nullable = false)
    private boolean isPinEnabled;
    
    @Column(name = "encrypted_pin", length = 500)
    private String encryptedPin;
    
    @Column(name = "is_3ds_enabled", nullable = false)
    private boolean is3dsEnabled;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "card_network", nullable = false, length = 20)
    private CardNetwork cardNetwork;
    
    @Column(name = "network_token", unique = true, length = 100)
    private String networkToken;
    
    @Column(name = "network_status", length = 50)
    private String networkStatus;
    
    @Column(name = "card_color", length = 7)
    private String cardColor;
    
    @Column(name = "card_design", length = 50)
    private String cardDesign;
    
    @Column(name = "nickname", length = 50)
    private String nickname;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "activated_at")
    private LocalDateTime activatedAt;
    
    @Column(name = "terminated_at")
    private LocalDateTime terminatedAt;
    
    @Column(name = "termination_reason", length = 200)
    private String terminationReason;
    
    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    // PCI DSS FIX: CVV rotation tracking removed (no CVV storage)
    // Card number can be rotated for security, but CVV is never stored
    @Column(name = "card_rotated_at")
    private LocalDateTime cardRotatedAt;

    @Column(name = "usage_count", nullable = false)
    private Long usageCount;
    
    @Column(name = "total_spent", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalSpent;
    
    @ElementCollection
    @CollectionTable(
        name = "virtual_card_metadata",
        joinColumns = @JoinColumn(name = "card_id")
    )
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    private Map<String, String> metadata;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (usageCount == null) {
            usageCount = 0L;
        }
        if (totalSpent == null) {
            totalSpent = BigDecimal.ZERO;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Check if card can process transactions
     */
    public boolean canProcessTransaction() {
        return status == CardStatus.ACTIVE && 
               !isLocked && 
               LocalDateTime.now().isBefore(
                   LocalDateTime.of(expiryYear, expiryMonth, 1, 0, 0).plusMonths(1)
               );
    }
    
    /**
     * Check if card is expired
     */
    public boolean isExpired() {
        LocalDateTime expiryDate = LocalDateTime.of(expiryYear, expiryMonth, 1, 0, 0).plusMonths(1);
        return LocalDateTime.now().isAfter(expiryDate);
    }
}