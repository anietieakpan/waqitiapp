package com.waqiti.compliance.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Regulatory Filing Model
 * 
 * CRITICAL: Represents regulatory filings including CTR, SAR, and other compliance reports.
 * Ensures proper tracking and management of regulatory obligations.
 * 
 * FILING TYPES:
 * - CTR: Currency Transaction Report (>$10,000 cash)
 * - SAR: Suspicious Activity Report
 * - MIL: Monetary Instrument Log (structured transactions)
 * - OFAC: OFAC sanctions compliance report
 * - BSA: Bank Secrecy Act compliance report
 * - STATE: State regulator filing
 * 
 * REGULATORY IMPACT:
 * - Required under BSA, USA PATRIOT Act
 * - Subject to civil and criminal penalties
 * - Maintains licensing and regulatory standing
 * - Critical for ongoing business operations
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Entity
@Table(name = "regulatory_filings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class RegulatoryFiling {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    // Filing identification
    @Column(nullable = false)
    private String filingType; // CTR, SAR, MIL, OFAC, BSA, STATE
    
    private String filingNumber; // Generated filing number
    private String confirmationNumber; // Regulatory body confirmation
    private String correlationId; // For tracking related filings

    // Regulatory body information
    @Column(nullable = false)
    private String regulatoryBody; // FinCEN, OFAC, FBI, DEA, etc.
    
    private String regulatoryContact;
    private String regulatoryRegion;

    // Filing content
    @Column(length = 2000)
    private String description;
    
    private String filingReason;
    private String violationType;
    
    // Financial information
    @Column(precision = 19, scale = 4)
    private BigDecimal amount;
    
    private String currency = "USD";
    private String relatedTransactionId;
    
    // Filing status and workflow
    @Column(nullable = false)
    private String filingStatus; // PENDING, SUBMITTED, ACKNOWLEDGED, REJECTED, COMPLETED
    
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL
    private boolean urgent = false;
    private boolean expedited = false;

    // Timeline tracking
    @Column(nullable = false)
    private LocalDateTime dueDate;
    
    private LocalDateTime submittedAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime completedAt;
    
    // Filing metadata
    private String submittedBy;
    private String filingMethod = "ELECTRONIC"; // ELECTRONIC, PAPER, PHONE
    private String filingChannel; // DIRECT, THIRD_PARTY, SYSTEM
    
    // Review and approval
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    
    @Column(length = 1000)
    private String reviewNotes;
    
    private boolean requiresApproval = false;
    private String approvedBy;
    private LocalDateTime approvedAt;

    // Status updates
    private String statusUpdateReason;
    private LocalDateTime lastStatusUpdate;
    
    // Compliance tracking
    private boolean documentationComplete = false;
    private boolean followUpRequired = false;
    private LocalDateTime followUpDate;
    
    // Response tracking
    private String regulatoryResponse;
    private LocalDateTime responseReceivedAt;
    private boolean furtherActionRequired = false;
    
    // Risk and impact assessment
    private String riskLevel = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL
    private BigDecimal riskScore;
    private String businessImpact;
    
    // Related entities
    private String relatedCaseId;
    private String relatedSarId;
    private String parentFilingId; // For amendments or follow-ups
    
    // Audit fields
    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private UUID createdBy;
    private UUID updatedBy;

    // Business logic methods

    public boolean isOverdue() {
        if (dueDate == null) {
            return false;
        }
        
        return LocalDateTime.now().isAfter(dueDate) && 
               !"SUBMITTED".equals(filingStatus) && 
               !"ACKNOWLEDGED".equals(filingStatus) &&
               !"COMPLETED".equals(filingStatus);
    }

    public boolean isUrgent() {
        return urgent || "HIGH".equals(priority) || "CRITICAL".equals(priority);
    }

    public boolean requiresImmediateAttention() {
        return isOverdue() || isUrgent() || expedited;
    }

    public long getDaysUntilDue() {
        if (dueDate == null) {
            return 0;
        }
        
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), dueDate);
    }

    public boolean isWithinFilingWindow() {
        return getDaysUntilDue() >= 0;
    }

    public String getFilingStatusSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(filingType).append("-").append(id.toString().substring(0, 8).toUpperCase());
        summary.append(" [").append(filingStatus).append("]");
        
        if (isOverdue()) {
            summary.append(" OVERDUE");
        }
        
        if (isUrgent()) {
            summary.append(" ").append(priority);
        }
        
        if (expedited) {
            summary.append(" EXPEDITED");
        }
        
        return summary.toString();
    }

    public boolean hasRegulatoryResponse() {
        return regulatoryResponse != null && responseReceivedAt != null;
    }

    public boolean isCompliant() {
        return "SUBMITTED".equals(filingStatus) || 
               "ACKNOWLEDGED".equals(filingStatus) || 
               "COMPLETED".equals(filingStatus);
    }

    public String getComplianceStatus() {
        if (isOverdue()) {
            return "NON_COMPLIANT_OVERDUE";
        }
        
        if (isCompliant()) {
            return "COMPLIANT";
        }
        
        if ("PENDING".equals(filingStatus)) {
            return isWithinFilingWindow() ? "PENDING_COMPLIANT" : "PENDING_AT_RISK";
        }
        
        if ("REJECTED".equals(filingStatus)) {
            return "NON_COMPLIANT_REJECTED";
        }
        
        return "UNKNOWN";
    }

    public boolean isCriticalFiling() {
        return "CTR".equals(filingType) || 
               "SAR".equals(filingType) || 
               "OFAC".equals(filingType) ||
               "CRITICAL".equals(priority);
    }

    public String getFilingDescription() {
        StringBuilder desc = new StringBuilder();
        desc.append(filingType).append(" filing for ");
        
        if (amount != null) {
            desc.append(currency).append(" ").append(amount);
        }
        
        if (violationType != null) {
            desc.append(" - ").append(violationType);
        }
        
        if (description != null && !description.isEmpty()) {
            desc.append(" (").append(description.substring(0, Math.min(50, description.length())));
            if (description.length() > 50) {
                desc.append("...");
            }
            desc.append(")");
        }
        
        return desc.toString();
    }

    @PrePersist
    protected void onCreate() {
        if (filingStatus == null) {
            filingStatus = "PENDING";
        }
        if (priority == null) {
            priority = "MEDIUM";
        }
        if (currency == null) {
            currency = "USD";
        }
        if (regulatoryBody == null) {
            regulatoryBody = "FinCEN";
        }
        if (filingMethod == null) {
            filingMethod = "ELECTRONIC";
        }
        if (riskLevel == null) {
            riskLevel = "MEDIUM";
        }
        // Generate filing number if not set
        if (filingNumber == null) {
            filingNumber = filingType + "-" + LocalDateTime.now().getYear() + "-" + 
                          String.format("%08d", Math.abs(id.hashCode() % 100000000));
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastStatusUpdate = LocalDateTime.now();
    }
}