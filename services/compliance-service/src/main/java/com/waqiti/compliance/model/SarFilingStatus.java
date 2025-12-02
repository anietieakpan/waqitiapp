package com.waqiti.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SAR Filing Status Model
 * 
 * Tracks the status and history of SAR filings
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sar_filing_status")
public class SarFilingStatus {
    
    @Id
    @Column(name = "sar_id")
    private String sarId;
    
    @Column(name = "user_id", nullable = false)
    private String userId;
    
    @Column(name = "case_id")
    private String caseId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private FilingStatus status;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private FilingPriority priority;
    
    @Column(name = "category")
    private String category;
    
    @Column(name = "violation_type")
    private String violationType;
    
    // Filing dates
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "scheduled_filing_date")
    private LocalDateTime scheduledFilingDate;
    
    @Column(name = "actual_filing_date")
    private LocalDateTime actualFilingDate;
    
    @Column(name = "deadline")
    private LocalDateTime deadline;
    
    // Regulatory information
    @ElementCollection
    @CollectionTable(name = "sar_regulatory_bodies", 
                     joinColumns = @JoinColumn(name = "sar_id"))
    @Column(name = "regulatory_body")
    private List<String> regulatoryBodies;
    
    @Column(name = "filing_confirmation_id")
    private String filingConfirmationId;
    
    @Column(name = "regulatory_receipt_number")
    private String regulatoryReceiptNumber;
    
    // Status tracking
    @Column(name = "is_expedited")
    private boolean isExpedited;
    
    @Column(name = "is_overdue")
    private boolean isOverdue;
    
    @Column(name = "requires_follow_up")
    private boolean requiresFollowUp;
    
    // Notes and history
    @Column(name = "notes", length = 2000)
    private String notes;
    
    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;
    
    @Column(name = "updated_by")
    private String updatedBy;
    
    // Error handling and retry fields
    @Column(name = "retry_count")
    private Integer retryCount;
    
    @Column(name = "last_error", length = 1000)
    private String lastError;
    
    /**
     * SAR filing status enumeration
     */
    public enum FilingStatus {
        DRAFT,                  // SAR being prepared
        PENDING_REVIEW,         // Awaiting compliance review
        APPROVED,               // Approved for filing
        SCHEDULED,              // Scheduled for filing
        FILING_IN_PROGRESS,     // Currently being filed
        FILED,                  // Successfully filed
        ACKNOWLEDGED,           // Receipt from regulator
        REJECTED,               // Rejected by regulator
        RESUBMISSION_REQUIRED,  // Needs resubmission
        CANCELLED,              // Filing cancelled
        FOLLOW_UP_REQUIRED,     // Requires follow-up action
        OVERDUE,                // Past filing deadline
        EMERGENCY_FILING,       // Emergency filing in progress
        FAILED                  // Filing failed after retries
    }
    
    /**
     * Filing priority enumeration
     */
    public enum FilingPriority {
        EMERGENCY,      // File immediately
        CRITICAL,       // File within 6 hours
        IMMEDIATE,      // File within 24 hours
        URGENT,         // File within 48 hours
        HIGH,           // File within 5 days
        STANDARD,       // File within 30 days
        LOW             // File within 60 days
    }
    
    /**
     * Check if filing is overdue
     */
    public boolean isOverdue() {
        if (deadline == null || status == FilingStatus.FILED || status == FilingStatus.ACKNOWLEDGED) {
            return false;
        }
        return LocalDateTime.now().isAfter(deadline);
    }
    
    /**
     * Calculate days until deadline
     */
    public long getDaysUntilDeadline() {
        if (deadline == null) {
            return 0;
        }
        return java.time.Duration.between(LocalDateTime.now(), deadline).toDays();
    }
    
    /**
     * Check if filing is expedited
     */
    public boolean isExpedited() {
        return priority == FilingPriority.IMMEDIATE || priority == FilingPriority.URGENT;
    }
}