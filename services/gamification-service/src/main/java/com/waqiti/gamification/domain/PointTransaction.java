package com.waqiti.gamification.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "point_transactions", indexes = {
    @Index(name = "idx_point_transaction_user_id", columnList = "user_id"),
    @Index(name = "idx_point_transaction_type", columnList = "transaction_type"),
    @Index(name = "idx_point_transaction_created_at", columnList = "created_at"),
    @Index(name = "idx_point_transaction_event_type", columnList = "event_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_points_id", nullable = false)
    private UserPoints userPoints;
    
    @Column(name = "transaction_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;
    
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;
    
    @Column(name = "points_amount", nullable = false)
    private Long pointsAmount;
    
    @Column(name = "base_points", nullable = false)
    private Long basePoints;
    
    @Column(name = "multiplier", precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal multiplier = BigDecimal.ONE;
    
    @Column(name = "bonus_points")
    @Builder.Default
    private Long bonusPoints = 0L;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Column(name = "reference_id", length = 100)
    private String referenceId;
    
    @Column(name = "reference_type", length = 50)
    private String referenceType;
    
    @Column(name = "source_service", length = 50)
    private String sourceService;
    
    @Column(name = "challenge_id")
    private Long challengeId;
    
    @Column(name = "badge_id")
    private Long badgeId;
    
    @Column(name = "level_before")
    @Enumerated(EnumType.STRING)
    private UserPoints.Level levelBefore;
    
    @Column(name = "level_after")
    @Enumerated(EnumType.STRING)
    private UserPoints.Level levelAfter;
    
    @Column(name = "balance_before", nullable = false)
    private Long balanceBefore;
    
    @Column(name = "balance_after", nullable = false)
    private Long balanceAfter;
    
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
    
    @Column(name = "is_expired", nullable = false)
    @Builder.Default
    private Boolean isExpired = false;
    
    @Column(name = "is_reversed", nullable = false)
    @Builder.Default
    private Boolean isReversed = false;
    
    @Column(name = "reversal_reason", length = 500)
    private String reversalReason;
    
    @Column(name = "reversed_by")
    private String reversedBy;
    
    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;
    
    @Column(name = "metadata", columnDefinition = "JSONB")
    private String metadata;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    public enum TransactionType {
        EARNED,
        REDEEMED,
        EXPIRED,
        BONUS,
        PENALTY,
        ADJUSTMENT,
        TRANSFERRED,
        REVERSED
    }
    
    public boolean isLevelUp() {
        return levelBefore != null && levelAfter != null && 
               levelBefore.ordinal() < levelAfter.ordinal();
    }
    
    public boolean isPointsPositive() {
        return pointsAmount > 0;
    }
    
    public boolean isPointsNegative() {
        return pointsAmount < 0;
    }
}