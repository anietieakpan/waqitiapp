package com.waqiti.purchaseprotection.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * Protection policy entity representing purchase protection insurance.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Getter
@Setter
@Entity
@Table(name = "protection_policies", indexes = {
    @Index(name = "idx_policies_transaction", columnList = "transaction_id"),
    @Index(name = "idx_policies_buyer", columnList = "buyer_id"),
    @Index(name = "idx_policies_seller", columnList = "seller_id"),
    @Index(name = "idx_policies_status", columnList = "status"),
    @Index(name = "idx_policies_end_date", columnList = "end_date")
})
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProtectionPolicy {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(name = "transaction_id", nullable = false, unique = true)
    private String transactionId;
    
    @Column(name = "buyer_id", nullable = false)
    private String buyerId;
    
    @Column(name = "seller_id", nullable = false)
    private String sellerId;
    
    @Column(name = "transaction_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal transactionAmount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "coverage_type", nullable = false)
    private CoverageType coverageType;
    
    @Column(name = "coverage_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal coverageAmount;
    
    @Column(name = "protection_fee", nullable = false, precision = 19, scale = 4)
    private BigDecimal protectionFee;
    
    @Column(name = "start_date", nullable = false)
    private Instant startDate;
    
    @Column(name = "end_date", nullable = false)
    private Instant endDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PolicyStatus status;
    
    @Column(name = "risk_score")
    private Double riskScore;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;
    
    @Column(name = "seller_verified")
    private boolean sellerVerified;
    
    @Column(name = "seller_rating")
    private Double sellerRating;
    
    @Column(name = "requires_escrow")
    private boolean requiresEscrow;
    
    @Column(name = "escrow_id")
    private String escrowId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "escrow_status")
    private EscrowStatus escrowStatus;
    
    @Column(name = "item_description", length = 1000)
    private String itemDescription;
    
    @Column(name = "item_category")
    private String itemCategory;
    
    @ElementCollection
    @CollectionTable(name = "purchase_evidence", joinColumns = @JoinColumn(name = "policy_id"))
    @Column(name = "evidence_url")
    private List<String> purchaseEvidence;
    
    @Column(name = "fee_collected")
    private boolean feeCollected;
    
    @Column(name = "total_fees", precision = 19, scale = 4)
    @Builder.Default
    private BigDecimal totalFees = BigDecimal.ZERO;
    
    @Column(name = "has_active_claim")
    private boolean hasActiveClaim;
    
    @Column(name = "last_claim_at")
    private Instant lastClaimAt;
    
    @Column(name = "extended")
    private boolean extended;
    
    @Column(name = "extension_count")
    @Builder.Default
    private Integer extensionCount = 0;
    
    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ProtectionClaim> claims;
    
    @ElementCollection
    @CollectionTable(name = "policy_metadata", joinColumns = @JoinColumn(name = "policy_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if policy is currently active.
     */
    public boolean isActive() {
        return status == PolicyStatus.ACTIVE && 
               Instant.now().isBefore(endDate);
    }
    
    /**
     * Check if policy can be extended.
     */
    public boolean canExtend() {
        return isActive() && extensionCount < 3;
    }
}

/**
 * Coverage type enumeration.
 */
enum CoverageType {
    BASIC,      // 75% coverage, 30 days
    STANDARD,   // 100% coverage, 60 days
    PREMIUM,    // 110% coverage, 90 days
    EXTENDED    // 125% coverage, 180 days
}

/**
 * Policy status enumeration.
 */
enum PolicyStatus {
    ACTIVE,
    EXPIRED,
    CLAIMED,
    CANCELLED,
    SUSPENDED
}

/**
 * Risk level enumeration.
 */
enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Escrow status enumeration.
 */
enum EscrowStatus {
    NOT_REQUIRED,
    HOLDING,
    RELEASED_TO_SELLER,
    RELEASED_TO_BUYER,
    DISPUTED
}