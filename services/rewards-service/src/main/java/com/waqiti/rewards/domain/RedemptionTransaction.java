package com.waqiti.rewards.domain;

import com.waqiti.rewards.enums.RedemptionMethod;
import com.waqiti.rewards.enums.RedemptionStatus;
import com.waqiti.rewards.enums.RedemptionType;
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
@Table(name = "redemption_transactions",
    indexes = {
        @Index(name = "idx_redemption_user_id", columnList = "user_id"),
        @Index(name = "idx_redemption_type", columnList = "type"),
        @Index(name = "idx_redemption_status", columnList = "status"),
        @Index(name = "idx_redemption_processed_at", columnList = "processed_at")
    }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class RedemptionTransaction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private RedemptionType type;
    
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "points")
    private Long points;
    
    @Column(name = "currency", length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false)
    private RedemptionMethod method;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private RedemptionStatus status = RedemptionStatus.PENDING;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "reference_number", unique = true)
    private String referenceNumber;
    
    @Column(name = "external_transaction_id")
    private String externalTransactionId;
    
    @Column(name = "processed_at")
    private Instant processedAt;
    
    @Column(name = "completed_at")
    private Instant completedAt;
    
    @Column(name = "failed_at")
    private Instant failedAt;
    
    @Column(name = "failure_reason")
    private String failureReason;
    
    @ElementCollection
    @CollectionTable(name = "redemption_metadata", 
        joinColumns = @JoinColumn(name = "redemption_id"))
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
    
    @PrePersist
    protected void onCreate() {
        if (referenceNumber == null) {
            referenceNumber = "RED-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
    }
}