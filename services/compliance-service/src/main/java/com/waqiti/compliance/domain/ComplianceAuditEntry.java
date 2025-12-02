package com.waqiti.compliance.domain;

import lombok.*;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable audit entry for compliance decisions
 * Provides cryptographic integrity and chain of custody
 */
@Entity
@Table(name = "compliance_audit_trail", indexes = {
    @Index(name = "idx_audit_transaction", columnList = "transaction_id"),
    @Index(name = "idx_audit_performed_at", columnList = "performed_at"),
    @Index(name = "idx_audit_performed_by", columnList = "performed_by"),
    @Index(name = "idx_audit_decision_type", columnList = "decision_type"),
    @Index(name = "idx_audit_risk_score", columnList = "risk_score"),
    @Index(name = "idx_audit_integrity_hash", columnList = "integrity_hash", unique = true)
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"context", "reviewDetails"})
@EqualsAndHashCode(of = {"id", "integrityHash"})
public class ComplianceAuditEntry {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;
    
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private String transactionId;
    
    @Column(name = "decision_id", updatable = false)
    private UUID decisionId;
    
    @Column(name = "decision_type", updatable = false)
    private String decisionType;
    
    @Column(name = "decision", updatable = false)
    private String decision;
    
    @Column(name = "risk_score", updatable = false)
    private Integer riskScore;
    
    @Column(name = "action_type", updatable = false)
    private String actionType;
    
    @Column(name = "action", updatable = false)
    private String action;
    
    @Column(name = "check_type", updatable = false)
    private String checkType;
    
    @Column(name = "check_result", updatable = false)
    private String checkResult;
    
    @Column(name = "performed_by", nullable = false, updatable = false)
    private String performedBy;
    
    @Column(name = "performed_at", nullable = false, updatable = false)
    private LocalDateTime performedAt;
    
    @Column(name = "reason", length = 1000, updatable = false)
    private String reason;
    
    @Column(name = "justification", length = 2000, updatable = false)
    private String justification;
    
    @Type(type = "jsonb")
    @Column(name = "context", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> context;
    
    @Type(type = "jsonb")
    @Column(name = "review_details", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> reviewDetails;
    
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> metadata;
    
    @ElementCollection
    @CollectionTable(name = "compliance_audit_rules_fired", 
        joinColumns = @JoinColumn(name = "audit_id"))
    @Column(name = "rule_name")
    private List<String> rulesFired;
    
    @ElementCollection
    @CollectionTable(name = "compliance_audit_flags_raised", 
        joinColumns = @JoinColumn(name = "audit_id"))
    @Column(name = "flag_name")
    private List<String> flagsRaised;
    
    @Column(name = "ip_address", updatable = false)
    private String ipAddress;
    
    @Column(name = "user_agent", updatable = false)
    private String userAgent;
    
    @Column(name = "session_id", updatable = false)
    private String sessionId;
    
    @Column(name = "execution_time_ms", updatable = false)
    private Long executionTimeMs;
    
    // Chain of custody fields
    @Column(name = "previous_entry_id", updatable = false)
    private UUID previousEntryId;
    
    @Column(name = "previous_hash", updatable = false)
    private String previousHash;
    
    @Column(name = "integrity_hash", nullable = false, unique = true, updatable = false)
    private String integrityHash;
    
    // Override tracking
    @Column(name = "original_decision", updatable = false)
    private String originalDecision;
    
    @Column(name = "override_decision", updatable = false)
    private String overrideDecision;
    
    @Column(name = "override_reason", length = 1000, updatable = false)
    private String overrideReason;
    
    @Column(name = "approval_ticket", updatable = false)
    private String approvalTicket;
    
    // Flags
    @Column(name = "is_critical_action", updatable = false)
    private boolean isCriticalAction;
    
    @Column(name = "requires_second_review", updatable = false)
    private boolean requiresSecondReview;
    
    @Column(name = "second_review_completed", updatable = false)
    private boolean secondReviewCompleted;
    
    @Column(name = "second_reviewer_id", updatable = false)
    private String secondReviewerId;
    
    @Column(name = "second_review_at", updatable = false)
    private LocalDateTime secondReviewAt;
    
    // Error tracking
    @Column(name = "error", length = 1000, updatable = false)
    private String error;
    
    // Ensure immutability - no setters after initial creation
    @PrePersist
    @PreUpdate
    protected void ensureImmutability() {
        if (this.getDatabaseId() != null) {
            throw new IllegalStateException("Audit entries are immutable and cannot be modified");
        }
    }
    
    // Internal database ID (hidden from API)
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "db_id", insertable = false, updatable = false)
    private Long databaseId;
    
    /**
     * Verify the integrity of this audit entry
     */
    public boolean verifyIntegrity(String expectedHash) {
        return this.integrityHash != null && this.integrityHash.equals(expectedHash);
    }
    
    /**
     * Check if this entry is linked correctly to the previous entry
     */
    public boolean isChainValid(ComplianceAuditEntry previousEntry) {
        if (previousEntry == null) {
            return this.previousEntryId == null && this.previousHash == null;
        }
        
        return previousEntry.getId().equals(this.previousEntryId) &&
               previousEntry.getIntegrityHash().equals(this.previousHash);
    }
}