package com.waqiti.security.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity representing individual actions taken in response to fraud detection.
 * Tracks all automated and manual actions for audit and compliance.
 */
@Entity
@Table(name = "fraud_actions", indexes = {
    @Index(name = "idx_action_case_id", columnList = "fraud_case_id"),
    @Index(name = "idx_action_type", columnList = "actionType"),
    @Index(name = "idx_action_status", columnList = "status"),
    @Index(name = "idx_action_timestamp", columnList = "timestamp")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudAction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fraud_case_id", nullable = false)
    private FraudCase fraudCase;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionType actionType;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    private LocalDateTime completedAt;
    
    @Column(length = 2000)
    private String details;
    
    @Column(length = 1000)
    private String errorMessage;
    
    private String executedBy;
    
    private String targetEntityId;
    
    private String targetEntityType;
    
    // Result tracking
    private String resultCode;
    private String resultMessage;
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> resultData;
    
    // Retry tracking
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private String retryReason;
    
    // Approval workflow
    private Boolean requiresApproval;
    private String approvedBy;
    private LocalDateTime approvedAt;
    private String approvalNotes;
    
    // Reversal tracking
    private Boolean reversible;
    private Boolean reversed;
    private String reversalId;
    private LocalDateTime reversedAt;
    private String reversedBy;
    private String reversalReason;
    
    // Audit metadata
    @Convert(converter = JsonMapConverter.class)
    @Column(columnDefinition = "JSON")
    private Map<String, Object> metadata;
    
    @Version
    private Long version;
    
    /**
     * Action type enumeration
     */
    public enum ActionType {
        // Transaction actions
        BLOCK_TRANSACTION("Block transaction processing"),
        REVERSE_TRANSACTION("Reverse completed transaction"),
        HOLD_FUNDS("Place hold on funds"),
        RELEASE_FUNDS("Release held funds"),
        
        // User account actions
        BLOCK_USER("Block user account"),
        SUSPEND_USER("Temporarily suspend user"),
        BLACKLIST_USER("Add user to blacklist"),
        RESTRICT_USER("Apply restrictions to user"),
        REQUEST_2FA("Request two-factor authentication"),
        FORCE_PASSWORD_RESET("Force password reset"),
        
        // Investigation actions
        CREATE_CASE("Create investigation case"),
        ESCALATE_CASE("Escalate to senior team"),
        ASSIGN_ANALYST("Assign to analyst"),
        REQUEST_DOCUMENTS("Request additional documents"),
        
        // Monitoring actions
        ENHANCE_MONITORING("Enable enhanced monitoring"),
        ADD_WATCHLIST("Add to watchlist"),
        UPDATE_RISK_SCORE("Update risk score"),
        
        // Notification actions
        NOTIFY_USER("Send notification to user"),
        NOTIFY_MERCHANT("Send notification to merchant"),
        NOTIFY_AUTHORITIES("Notify authorities"),
        NOTIFY_SECURITY_TEAM("Notify security team"),
        
        // Compliance actions
        FILE_SAR("File Suspicious Activity Report"),
        FILE_CTR("File Currency Transaction Report"),
        COMPLIANCE_REVIEW("Submit for compliance review"),
        
        // System actions
        UPDATE_ML_MODEL("Update ML model with case data"),
        UPDATE_RULES("Update fraud detection rules"),
        QUARANTINE_SESSION("Quarantine user session"),
        INVALIDATE_TOKENS("Invalidate authentication tokens");
        
        private final String description;
        
        ActionType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    /**
     * Action status enumeration
     */
    public enum Status {
        PENDING("Action pending execution"),
        IN_PROGRESS("Action in progress"),
        SUCCESS("Action completed successfully"),
        FAILED("Action failed"),
        CANCELLED("Action cancelled"),
        REQUIRES_APPROVAL("Awaiting approval"),
        APPROVED("Approved for execution"),
        REJECTED("Approval rejected"),
        RETRY_SCHEDULED("Scheduled for retry"),
        REVERSED("Action has been reversed");
        
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
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
        if (status == null) {
            status = Status.PENDING;
        }
        if (attemptCount == null) {
            attemptCount = 0;
        }
    }
    
    /**
     * Convenience methods
     */
    public boolean isCompleted() {
        return status == Status.SUCCESS || status == Status.FAILED || 
               status == Status.CANCELLED || status == Status.REVERSED;
    }
    
    public boolean canRetry() {
        return status == Status.FAILED && attemptCount < 3 && !isUserAction();
    }
    
    public boolean isUserAction() {
        return actionType == ActionType.BLOCK_USER || 
               actionType == ActionType.SUSPEND_USER ||
               actionType == ActionType.BLACKLIST_USER ||
               actionType == ActionType.RESTRICT_USER;
    }
    
    public boolean isTransactionAction() {
        return actionType == ActionType.BLOCK_TRANSACTION ||
               actionType == ActionType.REVERSE_TRANSACTION ||
               actionType == ActionType.HOLD_FUNDS ||
               actionType == ActionType.RELEASE_FUNDS;
    }
    
    public boolean isComplianceAction() {
        return actionType == ActionType.FILE_SAR ||
               actionType == ActionType.FILE_CTR ||
               actionType == ActionType.COMPLIANCE_REVIEW ||
               actionType == ActionType.NOTIFY_AUTHORITIES;
    }
}