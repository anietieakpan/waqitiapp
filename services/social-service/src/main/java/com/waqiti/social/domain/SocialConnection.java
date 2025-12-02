package com.waqiti.social.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "social_connections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialConnection {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private UUID userId;
    
    @Column(name = "connected_user_id", nullable = false)
    private UUID connectedUserId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "relationship_type", nullable = false, length = 20)
    private RelationshipType relationshipType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ConnectionStatus status = ConnectionStatus.PENDING;
    
    @Column(name = "nickname", length = 100)
    private String nickname;
    
    @Column(name = "is_favorite")
    private Boolean isFavorite = false;
    
    @Column(name = "is_blocked")
    private Boolean isBlocked = false;
    
    @Column(name = "trust_level")
    private Integer trustLevel = 50; // 0-100 scale
    
    @Column(name = "transaction_limit", precision = 19, scale = 2)
    private java.math.BigDecimal transactionLimit;
    
    @Column(name = "monthly_limit", precision = 19, scale = 2)
    private java.math.BigDecimal monthlyLimit;
    
    @Type(type = "jsonb")
    @Column(name = "privacy_settings", columnDefinition = "jsonb")
    private Map<String, Object> privacySettings;
    
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "connected_via", length = 50)
    private String connectedVia; // PHONE, EMAIL, USERNAME, QR_CODE, NFC
    
    @Column(name = "first_transaction_at")
    private LocalDateTime firstTransactionAt;
    
    @Column(name = "last_transaction_at")
    private LocalDateTime lastTransactionAt;
    
    @Column(name = "total_transactions")
    private Long totalTransactions = 0L;
    
    @Column(name = "total_amount_sent", precision = 19, scale = 2)
    private java.math.BigDecimal totalAmountSent = java.math.BigDecimal.ZERO;
    
    @Column(name = "total_amount_received", precision = 19, scale = 2)
    private java.math.BigDecimal totalAmountReceived = java.math.BigDecimal.ZERO;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;
    
    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum RelationshipType {
        FRIEND,
        FAMILY,
        COLLEAGUE,
        BUSINESS,
        ACQUAINTANCE,
        OTHER
    }
    
    public enum ConnectionStatus {
        PENDING,
        ACCEPTED,
        BLOCKED,
        REJECTED,
        REMOVED
    }
    
    public boolean isActive() {
        return status == ConnectionStatus.ACCEPTED && !isBlocked;
    }
    
    public boolean canTransact() {
        return isActive() && transactionLimit != null && 
               transactionLimit.compareTo(java.math.BigDecimal.ZERO) > 0;
    }
}