package com.waqiti.payment.entity;

import com.waqiti.payment.model.FundReleaseStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "fund_releases",
    indexes = {
        @Index(name = "idx_fund_release_merchant_id", columnList = "merchant_id"),
        @Index(name = "idx_fund_release_status", columnList = "status"),
        @Index(name = "idx_fund_release_scheduled_time", columnList = "scheduled_release_time"),
        @Index(name = "idx_fund_release_created_at", columnList = "created_at"),
        @Index(name = "idx_fund_release_release_id", columnList = "release_id", unique = true)
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FundRelease {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;
    
    @Column(name = "release_id", nullable = false, unique = true, length = 100)
    private String releaseId;
    
    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;
    
    @Column(name = "order_id", length = 100)
    private String orderId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "release_type", nullable = false, length = 50)
    private FundReleaseType releaseType;
    
    @Column(name = "source_account", length = 100)
    private String sourceAccount;
    
    @Column(name = "destination_account", nullable = false, length = 100)
    private String destinationAccount;
    
    @Column(name = "scheduled_release_time")
    private Instant scheduledReleaseTime;
    
    @Column(name = "released_at")
    private Instant releasedAt;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private FundReleaseStatus status;
    
    @Column(name = "transaction_id", length = 100)
    private String transactionId;
    
    @Column(name = "batch_id", length = 100)
    private String batchId;
    
    @Column(name = "review_id", length = 100)
    private String reviewId;
    
    @Column(name = "processing_result", columnDefinition = "TEXT")
    private String processingResult;
    
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;
    
    @Column(name = "retry_count")
    private Integer retryCount;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "last_updated")
    private Instant lastUpdated;
    
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "fund_release_metadata", 
        joinColumns = @JoinColumn(name = "fund_release_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", length = 1000)
    private Map<String, String> metadata = new HashMap<>();
    
    @Version
    @Column(name = "version")
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        lastUpdated = Instant.now();
        if (retryCount == null) {
            retryCount = 0;
        }
        if (currency == null) {
            currency = "USD";
        }
        if (status == null) {
            status = FundReleaseStatus.PENDING;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = Instant.now();
    }
    
    public enum FundReleaseType {
        INSTANT, STANDARD, SCHEDULED, BATCH, MANUAL
    }
}