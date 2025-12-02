package com.waqiti.rewards.domain;

import com.waqiti.rewards.enums.PointsSource;
import com.waqiti.rewards.enums.PointsStatus;
import com.waqiti.rewards.enums.PointsTransactionType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "points_transactions",
    indexes = {
        @Index(name = "idx_points_user_id", columnList = "user_id"),
        @Index(name = "idx_points_type", columnList = "type"),
        @Index(name = "idx_points_status", columnList = "status"),
        @Index(name = "idx_points_processed_at", columnList = "processed_at"),
        @Index(name = "idx_points_expires_at", columnList = "expires_at")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class PointsTransaction {
    
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
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PointsTransactionType type;
    
    @Column(name = "points", nullable = false)
    private Long points;
    
    @Column(name = "description")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    @Builder.Default
    private PointsSource source = PointsSource.TRANSACTION;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private PointsStatus status = PointsStatus.PENDING;
    
    @Column(name = "processed_at")
    private Instant processedAt;
    
    @Column(name = "expires_at")
    private Instant expiresAt;
    
    @Column(name = "redemption_id")
    private UUID redemptionId;
    
    @Column(name = "campaign_id")
    private String campaignId;
    
    @ElementCollection
    @CollectionTable(name = "points_metadata", 
        joinColumns = @JoinColumn(name = "points_transaction_id"))
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
        return status == PointsStatus.PENDING && !isExpired();
    }
    
    public void markAsCompleted() {
        if (status != PointsStatus.PENDING) {
            throw new IllegalStateException("Can only complete pending points transaction");
        }
        this.status = PointsStatus.COMPLETED;
        this.processedAt = Instant.now();
    }
    
    public void markAsFailed(String reason) {
        this.status = PointsStatus.FAILED;
        this.processedAt = Instant.now();
        this.metadata.put("failure_reason", reason);
    }
    
    public void markAsExpired() {
        if (status != PointsStatus.PENDING && status != PointsStatus.COMPLETED) {
            throw new IllegalStateException("Can only expire pending or completed points");
        }
        this.status = PointsStatus.EXPIRED;
        this.processedAt = Instant.now();
    }
    
    public boolean isDebit() {
        return type == PointsTransactionType.REDEEMED || 
               type == PointsTransactionType.EXPIRED ||
               type == PointsTransactionType.ADJUSTED_DOWN;
    }
    
    public boolean isCredit() {
        return type == PointsTransactionType.EARNED || 
               type == PointsTransactionType.BONUS ||
               type == PointsTransactionType.ADJUSTED_UP;
    }
}