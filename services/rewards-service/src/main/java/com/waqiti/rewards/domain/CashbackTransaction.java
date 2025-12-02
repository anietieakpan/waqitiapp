package com.waqiti.rewards.domain;

import com.waqiti.rewards.enums.CashbackSource;
import com.waqiti.rewards.enums.CashbackStatus;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "cashback_transactions",
    indexes = {
        @Index(name = "idx_cashback_user_id", columnList = "user_id"),
        @Index(name = "idx_cashback_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_cashback_status", columnList = "status"),
        @Index(name = "idx_cashback_earned_at", columnList = "earned_at"),
        @Index(name = "idx_cashback_merchant_id", columnList = "merchant_id")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class CashbackTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private RewardsAccount account;
    
    @Column(name = "transaction_id")
    private String transactionId;
    
    @Column(name = "merchant_id")
    private String merchantId;
    
    @Column(name = "merchant_name")
    private String merchantName;
    
    @Column(name = "merchant_category")
    private String merchantCategory;
    
    @Column(name = "transaction_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal transactionAmount;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Column(name = "cashback_rate", nullable = false, precision = 5, scale = 4)
    private BigDecimal cashbackRate;
    
    @Column(name = "cashback_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal cashbackAmount;
    
    @Column(name = "campaign_id")
    private String campaignId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private CashbackStatus status = CashbackStatus.PENDING;
    
    @Column(name = "earned_at", nullable = false)
    private Instant earnedAt;
    
    @Column(name = "processed_at")
    private Instant processedAt;
    
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    @Column(name = "description")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "source")
    @Builder.Default
    private CashbackSource source = CashbackSource.TRANSACTION;
    
    @ElementCollection
    @CollectionTable(name = "cashback_metadata", 
        joinColumns = @JoinColumn(name = "cashback_id"))
    @MapKeyColumn(name = "key")
    @Column(name = "value")
    @Builder.Default
    private Map<String, String> metadata = new HashMap<>();
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    // Business methods
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
    
    public boolean canBeProcessed() {
        return status == CashbackStatus.PENDING && !isExpired();
    }
    
    public void markAsEarned() {
        if (status != CashbackStatus.PENDING) {
            throw new IllegalStateException("Can only mark pending cashback as earned");
        }
        this.status = CashbackStatus.EARNED;
        this.processedAt = Instant.now();
    }
    
    public void markAsFailed(String reason) {
        this.status = CashbackStatus.FAILED;
        this.processedAt = Instant.now();
        this.metadata.put("failure_reason", reason);
    }
    
    public void markAsExpired() {
        if (status != CashbackStatus.PENDING) {
            throw new IllegalStateException("Can only expire pending cashback");
        }
        this.status = CashbackStatus.EXPIRED;
        this.processedAt = Instant.now();
    }
}