package com.waqiti.security.entity;

import com.waqiti.security.dto.FraudResponseDecision;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Entity representing a fraud case for tracking and audit purposes.
 * Maintains complete history of fraud detection and response actions.
 */
@Entity
@Table(name = "fraud_cases", indexes = {
    @Index(name = "idx_fraud_case_id", columnList = "caseId"),
    @Index(name = "idx_fraud_transaction_id", columnList = "transactionId"),
    @Index(name = "idx_fraud_user_id", columnList = "userId"),
    @Index(name = "idx_fraud_status", columnList = "status"),
    @Index(name = "idx_fraud_created_at", columnList = "createdAt"),
    @Index(name = "idx_fraud_severity", columnList = "severity")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @Column(nullable = false, unique = true)
    private String caseId;
    
    @Column(nullable = false)
    private String correlationId;
    
    @Column(nullable = false)
    private String eventId;
    
    @Column(nullable = false)
    private String transactionId;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;
    
    @Column(length = 3)
    private String currency;
    
    @Column(nullable = false)
    private String severity;
    
    @Column(nullable = false)
    private Double riskScore;
    
    @Column(nullable = false)
    private String fraudType;
    
    @Column(nullable = false)
    private String source;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    
    @Enumerated(EnumType.STRING)
    private FraudResponseDecision.Decision decision;
    
    @Column(length = 1000)
    private String decisionReason;
    
    @OneToMany(mappedBy = "fraudCase", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FraudAction> actions;
    
    @Column(nullable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime processedAt;
    
    private LocalDateTime resolvedAt;
    
    private Long processingTimeMs;
    
    @Column(length = 2000)
    private String errorMessage;
    
    // Investigation details
    private String assignedAnalyst;
    private LocalDateTime assignedAt;
    private String investigationNotes;
    private String resolution;
    private Boolean falsePositive;
    private String falsePositiveReason;
    
    // Financial impact
    @Column(precision = 19, scale = 2)
    private BigDecimal preventedLoss;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal actualLoss;
    
    @Column(precision = 19, scale = 2)
    private BigDecimal recoveredAmount;
    
    // Compliance tracking
    private String sarId;
    private LocalDateTime sarFiledAt;
    private String complianceStatus;
    private String regulatoryReportId;
    
    // Evidence and documentation
    @ElementCollection
    @CollectionTable(name = "fraud_case_evidence", joinColumns = @JoinColumn(name = "case_id"))
    @MapKeyColumn(name = "evidence_key")
    @Column(name = "evidence_value", length = 2000)
    private Map<String, String> evidenceLinks;
    
    // Related entities
    @ElementCollection
    @CollectionTable(name = "fraud_case_related_transactions", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "transaction_id")
    private List<String> relatedTransactionIds;
    
    @ElementCollection
    @CollectionTable(name = "fraud_case_related_users", joinColumns = @JoinColumn(name = "case_id"))
    @Column(name = "user_id")
    private List<String> relatedUserIds;
    
    // Metadata storage
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> metadata;
    
    // Audit fields
    private String createdBy;
    private String updatedBy;
    private LocalDateTime updatedAt;
    
    @Version
    private Long version;
    
    /**
     * Case status enumeration
     */
    public enum Status {
        PROCESSING("Case is being processed"),
        PROCESSED("Case has been processed"),
        INVESTIGATING("Under investigation"),
        ESCALATED("Escalated to senior team"),
        RESOLVED("Case resolved"),
        CLOSED("Case closed"),
        ERROR("Processing error occurred"),
        PENDING_REVIEW("Awaiting manual review");
        
        private final String description;
        
        Status(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Pre-persist lifecycle callback
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = Status.PROCESSING;
        }
    }
    
    /**
     * Pre-update lifecycle callback
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    /**
     * Convenience methods
     */
    public boolean isResolved() {
        return status == Status.RESOLVED || status == Status.CLOSED;
    }
    
    public boolean requiresInvestigation() {
        return status == Status.INVESTIGATING || status == Status.PENDING_REVIEW;
    }
    
    public boolean hasFinancialImpact() {
        return (actualLoss != null && actualLoss.compareTo(BigDecimal.ZERO) > 0) ||
               (preventedLoss != null && preventedLoss.compareTo(BigDecimal.ZERO) > 0);
    }
}