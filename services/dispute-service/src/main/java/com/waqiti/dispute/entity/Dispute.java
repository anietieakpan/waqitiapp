package com.waqiti.dispute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dispute entity representing a transaction dispute
 */
@Entity
@Table(name = "disputes", indexes = {
    @Index(name = "idx_dispute_transaction", columnList = "transaction_id"),
    @Index(name = "idx_dispute_user", columnList = "user_id"),
    @Index(name = "idx_dispute_status", columnList = "status"),
    @Index(name = "idx_dispute_created", columnList = "created_at"),
    @Index(name = "idx_dispute_priority", columnList = "priority")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dispute {
    
    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;
    
    @Column(name = "transaction_id", nullable = false, length = 36)
    private String transactionId;
    
    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;
    
    @Column(name = "merchant_id", length = 36)
    private String merchantId;
    
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "dispute_type", nullable = false, length = 30)
    private DisputeType disputeType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DisputeStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private DisputePriority priority;
    
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "last_updated", nullable = false)
    private LocalDateTime lastUpdated;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolution_reason", length = 500)
    private String resolutionReason;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_decision", length = 30)
    private ResolutionDecision resolutionDecision;
    
    @Column(name = "refund_amount", precision = 19, scale = 4)
    private BigDecimal refundAmount;
    
    // Chargeback specific fields
    @Column(name = "chargeback_code", length = 20)
    private String chargebackCode;
    
    @Column(name = "chargeback_reason", length = 500)
    private String chargebackReason;
    
    @Column(name = "chargeback_amount", precision = 19, scale = 4)
    private BigDecimal chargebackAmount;
    
    @Column(name = "provider_case_id", length = 100)
    private String providerCaseId;
    
    // Evidence tracking
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "dispute_evidence_ids", joinColumns = @JoinColumn(name = "dispute_id"))
    @Column(name = "evidence_id")
    private List<String> evidenceIds = new ArrayList<>();
    
    // Escalation tracking
    @Column(name = "escalation_level", nullable = false)
    private int escalationLevel = 0;
    
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;
    
    @Column(name = "escalation_reason", length = 500)
    private String escalationReason;
    
    // Financial tracking
    @Column(name = "funds_locked", nullable = false)
    private boolean fundsLocked = false;
    
    @Column(name = "funds_locked_at")
    private LocalDateTime fundsLockedAt;
    
    @Column(name = "funds_released_at")
    private LocalDateTime fundsReleasedAt;
    
    // Auto-resolution
    @Column(name = "auto_resolution_eligible", nullable = false)
    private boolean autoResolutionEligible = false;
    
    @Column(name = "auto_resolution_attempted", nullable = false)
    private boolean autoResolutionAttempted = false;
    
    @Column(name = "auto_resolution_score")
    private Double autoResolutionScore;
    
    // Communication tracking
    @Column(name = "customer_contacted", nullable = false)
    private boolean customerContacted = false;
    
    @Column(name = "merchant_contacted", nullable = false)
    private boolean merchantContacted = false;
    
    @Column(name = "last_communication_at")
    private LocalDateTime lastCommunicationAt;
    
    // Metadata
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "dispute_metadata", joinColumns = @JoinColumn(name = "dispute_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value")
    private Map<String, String> metadata;
    
    // Audit fields
    @Column(name = "created_by", length = 100)
    private String createdBy;
    
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;
    
    @Version
    @Column(name = "version")
    private Long version;
    
    /**
     * Check if dispute is open
     */
    public boolean isOpen() {
        return status != DisputeStatus.CLOSED && 
               status != DisputeStatus.RESOLVED && 
               status != DisputeStatus.REJECTED;
    }
    
    /**
     * Check if dispute requires urgent attention
     */
    public boolean isUrgent() {
        return priority == DisputePriority.CRITICAL || 
               disputeType == DisputeType.CHARGEBACK ||
               (priority == DisputePriority.HIGH && escalationLevel > 0);
    }
    
    /**
     * Check if dispute is within SLA
     */
    public boolean isWithinSla(int slaDays) {
        if (resolvedAt != null) {
            return true; // Already resolved
        }
        
        LocalDateTime deadline = createdAt.plusDays(slaDays);
        return LocalDateTime.now().isBefore(deadline);
    }
    
    /**
     * Calculate dispute age in days
     */
    public long getAgeInDays() {
        LocalDateTime endTime = resolvedAt != null ? resolvedAt : LocalDateTime.now();
        return java.time.Duration.between(createdAt, endTime).toDays();
    }
}