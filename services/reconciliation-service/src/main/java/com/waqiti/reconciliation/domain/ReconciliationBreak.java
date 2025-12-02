package com.waqiti.reconciliation.domain;

import com.waqiti.common.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_breaks", indexes = {
    @Index(name = "idx_break_type", columnList = "breakType"),
    @Index(name = "idx_severity", columnList = "severity"),
    @Index(name = "idx_status", columnList = "status"),
    @Index(name = "idx_account_number", columnList = "accountNumber"),
    @Index(name = "idx_detected_at", columnList = "detectedAt"),
    @Index(name = "idx_assigned_to", columnList = "assignedTo")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReconciliationBreak extends BaseEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "break_reference", nullable = false, unique = true, length = 50)
    private String breakReference;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "break_type", nullable = false)
    private BreakType breakType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;
    
    @Column(name = "account_number", nullable = false, length = 50)
    private String accountNumber;
    
    @Column(name = "currency", length = 3, nullable = false)
    private String currency;
    
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;
    
    @Column(name = "variance_amount", precision = 19, scale = 4)
    private BigDecimal varianceAmount;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;
    
    @Column(name = "detected_by", length = 100)
    private String detectedBy;
    
    @Column(name = "detection_method", length = 100)
    private String detectionMethod;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BreakStatus status;
    
    @Column(name = "assigned_to", length = 100)
    private String assignedTo;
    
    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;
    
    @Column(name = "investigation_started_at")
    private LocalDateTime investigationStartedAt;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "resolved_by", length = 100)
    private String resolvedBy;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_type")
    private ResolutionType resolutionType;
    
    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;
    
    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;
    
    @Column(name = "corrective_action", columnDefinition = "TEXT")
    private String correctiveAction;
    
    @Column(name = "prevention_measures", columnDefinition = "TEXT")
    private String preventionMeasures;
    
    @Column(name = "due_date")
    private LocalDateTime dueDate;
    
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;
    
    @Column(name = "escalated_to", length = 100)
    private String escalatedTo;
    
    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;
    
    @Column(name = "reconciliation_run_id")
    private UUID reconciliationRunId;
    
    @ElementCollection
    @CollectionTable(name = "reconciliation_break_entries", 
                     joinColumns = @JoinColumn(name = "break_id"))
    @Column(name = "entry_id")
    private List<UUID> affectedEntryIds;
    
    @ElementCollection
    @CollectionTable(name = "reconciliation_break_attachments", 
                     joinColumns = @JoinColumn(name = "break_id"))
    @Column(name = "attachment_path", columnDefinition = "TEXT")
    private List<String> attachments;
    
    @ElementCollection
    @CollectionTable(name = "reconciliation_break_comments", 
                     joinColumns = @JoinColumn(name = "break_id"))
    @MapKeyColumn(name = "comment_timestamp")
    @Column(name = "comment_text", columnDefinition = "TEXT")
    private Map<LocalDateTime, String> comments;
    
    @ElementCollection
    @CollectionTable(name = "reconciliation_break_metadata", 
                     joinColumns = @JoinColumn(name = "break_id"))
    @MapKeyColumn(name = "metadata_key")
    @Column(name = "metadata_value", columnDefinition = "TEXT")
    private Map<String, String> metadata;
    
    @Column(name = "auto_resolution_attempted", nullable = false)
    private Boolean autoResolutionAttempted = false;
    
    @Column(name = "auto_resolution_successful", nullable = false)
    private Boolean autoResolutionSuccessful = false;
    
    @Column(name = "requires_regulatory_reporting", nullable = false)
    private Boolean requiresRegulatoryReporting = false;
    
    @Column(name = "regulatory_reported_at")
    private LocalDateTime regulatoryReportedAt;
    
    @Column(name = "regulatory_reference", length = 100)
    private String regulatoryReference;
    
    @Column(name = "business_impact", columnDefinition = "TEXT")
    private String businessImpact;
    
    @Column(name = "financial_impact", precision = 19, scale = 4)
    private BigDecimal financialImpact;

    @Column(name = "break_id")
    @Builder.Default
    private UUID breakId = UUID.randomUUID();

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @OneToMany(mappedBy = "reconciliationBreak", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<ReconciliationVariance> variances;

    @Column(name = "resolution_method", length = 100)
    private String resolutionMethod;
    
    public enum BreakType {
        UNMATCHED_INTERNAL,
        UNMATCHED_EXTERNAL,
        AMOUNT_VARIANCE,
        DATE_VARIANCE,
        DUPLICATE_ENTRY,
        MISSING_ENTRY,
        BALANCE_DISCREPANCY,
        BALANCE_VARIANCE,
        SYSTEM_ERROR,
        DATA_QUALITY_ISSUE,
        TIMING_DIFFERENCE,
        CURRENCY_MISMATCH,
        REFERENCE_MISMATCH,
        MANUAL_ADJUSTMENT_NEEDED,
        NOSTRO_BREAK,
        CLEARING_BREAK,
        SETTLEMENT_BREAK,
        TRANSACTION_LEDGER_MISMATCH,
        SETTLEMENT_MISMATCH,
        GENERAL_LEDGER_IMBALANCE
    }
    
    public enum Severity {
        LOW(1),
        MEDIUM(2),
        HIGH(3),
        CRITICAL(4),
        EMERGENCY(5);
        
        private final int level;
        
        Severity(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
    }
    
    public enum BreakStatus {
        NEW,
        OPEN,
        ASSIGNED,
        INVESTIGATING,
        PENDING_RESOLUTION,
        RESOLVED,
        CLOSED,
        CANCELLED,
        ESCALATED,
        DEFERRED,
        AWAITING_APPROVAL,
        MANUAL_REVIEW_REQUIRED
    }
    
    public enum ResolutionType {
        MATCHED,
        MANUAL_ADJUSTMENT,
        SYSTEM_CORRECTION,
        WRITTEN_OFF,
        REVERSED,
        CANCELLED,
        TIMING_DIFFERENCE,
        LEGITIMATE_VARIANCE,
        DATA_CORRECTION,
        PROCESS_IMPROVEMENT
    }
    
    public boolean isOverdue() {
        return dueDate != null && LocalDateTime.now().isAfter(dueDate) && !isResolved();
    }
    
    public boolean isResolved() {
        return status == BreakStatus.RESOLVED || status == BreakStatus.CLOSED;
    }
    
    public boolean requiresEscalation() {
        if (severity == Severity.CRITICAL || severity == Severity.EMERGENCY) {
            return true;
        }
        
        // Escalate if open for more than defined period based on severity
        if (detectedAt != null) {
            LocalDateTime now = LocalDateTime.now();
            long hoursOpen = java.time.Duration.between(detectedAt, now).toHours();
            
            return switch (severity) {
                case HIGH -> hoursOpen > 4;
                case MEDIUM -> hoursOpen > 24;
                case LOW -> hoursOpen > 72;
                default -> false;
            };
        }
        
        return false;
    }
    
    public void addComment(String comment, String commentBy) {
        if (comments == null) {
            comments = new java.util.HashMap<>();
        }
        
        String timestampedComment = String.format("[%s] %s: %s", 
            LocalDateTime.now(), commentBy, comment);
        comments.put(LocalDateTime.now(), timestampedComment);
    }
    
    public void escalate(String escalatedTo, String reason) {
        this.status = BreakStatus.ESCALATED;
        this.escalatedAt = LocalDateTime.now();
        this.escalatedTo = escalatedTo;
        this.escalationReason = reason;
        
        addComment("Break escalated to " + escalatedTo + ". Reason: " + reason, "SYSTEM");
    }
    
    public void resolve(ResolutionType resolutionType, String resolvedBy, String notes) {
        this.status = BreakStatus.RESOLVED;
        this.resolutionType = resolutionType;
        this.resolvedAt = LocalDateTime.now();
        this.resolvedBy = resolvedBy;
        this.resolutionNotes = notes;
        
        addComment("Break resolved: " + resolutionType + ". " + notes, resolvedBy);
    }
    
    public long getAgeInHours() {
        if (detectedAt == null) return 0;
        
        LocalDateTime endTime = resolvedAt != null ? resolvedAt : LocalDateTime.now();
        return java.time.Duration.between(detectedAt, endTime).toHours();
    }
    
    public long getResolutionTimeInHours() {
        if (detectedAt == null || resolvedAt == null) return 0;
        
        return java.time.Duration.between(detectedAt, resolvedAt).toHours();
    }
    
    public boolean isWithinSLA() {
        if (!isResolved()) return false;
        
        long resolutionHours = getResolutionTimeInHours();
        
        return switch (severity) {
            case EMERGENCY -> resolutionHours <= 1;
            case CRITICAL -> resolutionHours <= 4;
            case HIGH -> resolutionHours <= 24;
            case MEDIUM -> resolutionHours <= 72;
            case LOW -> resolutionHours <= 168; // 7 days
        };
    }
}