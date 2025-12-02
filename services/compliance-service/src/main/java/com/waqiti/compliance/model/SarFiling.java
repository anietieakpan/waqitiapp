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
 * SAR Filing Model
 * 
 * CRITICAL: Represents Suspicious Activity Report (SAR) filings for regulatory compliance.
 * Ensures BSA and FinCEN SAR filing requirements are met.
 * 
 * REGULATORY IMPACT:
 * - Mandated by BSA Section 5318(g)
 * - Required under 31 CFR 1020.320
 * - Subject to FinCEN enforcement
 * - Criminal penalties for non-compliance
 * 
 * FILING REQUIREMENTS:
 * - Must be filed within 30 days of detection
 * - Requires comprehensive documentation
 * - Subject to audit and examination
 * - Maintains strict confidentiality
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-15
 */
@Entity
@Table(name = "sar_filings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class SarFiling {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    // SAR identification
    private String sarNumber; // Generated SAR number
    private String filingConfirmationId; // Regulatory body confirmation
    private String scheduleId; // For scheduled filings

    // Suspicious activity details
    @Column(nullable = false)
    private String suspiciousActivity;
    
    @Column(precision = 19, scale = 4)
    private BigDecimal transactionAmount;
    
    private String currency = "USD";
    
    @Column(length = 2000)
    private String description;
    
    private String filingReason;

    // Filing status and workflow
    @Column(nullable = false)
    private String filingStatus; // PENDING, SUBMITTED, ACKNOWLEDGED, REJECTED
    
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL
    
    private boolean reportingRequired = true;
    private boolean expeditedFiling = false;

    // Timeline tracking
    @Column(nullable = false)
    private LocalDateTime detectedAt;
    
    private LocalDateTime submittedAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime scheduledFilingTime;
    
    // Filing metadata
    private String submittedBy;
    private String regulatoryBody = "FinCEN";
    private String filingMethod = "ELECTRONIC";
    
    // Review and approval
    private LocalDateTime reviewedAt;
    private String reviewedBy;
    
    @Column(length = 1000)
    private String reviewNotes;
    
    private boolean requiresManagerialApproval = false;
    private String approvedBy;
    private LocalDateTime approvedAt;

    // Status updates
    private String statusUpdateReason;
    private LocalDateTime lastStatusUpdate;
    
    // Related entities
    private String relatedCaseId;
    private String investigationId;
    
    // Compliance tracking
    private boolean confidentialityMaintained = true;
    private boolean documentationComplete = false;
    private boolean followUpRequired = false;
    private LocalDateTime followUpDate;
    
    // Risk assessment
    private String riskLevel = "MEDIUM"; // LOW, MEDIUM, HIGH, CRITICAL
    private BigDecimal riskScore;
    
    // Regulatory response
    private String regulatoryResponse;
    private LocalDateTime responseReceivedAt;
    private boolean furtherActionRequired = false;
    
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
        if (detectedAt == null) {
            return false;
        }
        
        LocalDateTime deadline = detectedAt.plusDays(30); // 30-day filing requirement
        return LocalDateTime.now().isAfter(deadline) && 
               !"SUBMITTED".equals(filingStatus) && 
               !"ACKNOWLEDGED".equals(filingStatus);
    }

    public boolean isUrgent() {
        return "HIGH".equals(priority) || "CRITICAL".equals(priority);
    }

    public boolean requiresImmediateAttention() {
        return isOverdue() || isUrgent() || expeditedFiling;
    }

    public long getDaysUntilDeadline() {
        if (detectedAt == null) {
            return 30;
        }
        
        LocalDateTime deadline = detectedAt.plusDays(30);
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDateTime.now(), deadline);
    }

    public boolean isWithinFilingWindow() {
        return getDaysUntilDeadline() >= 0;
    }

    public String getFilingStatusSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("SAR-").append(id.toString().substring(0, 8).toUpperCase());
        summary.append(" [").append(filingStatus).append("]");
        
        if (isOverdue()) {
            summary.append(" OVERDUE");
        }
        
        if (isUrgent()) {
            summary.append(" ").append(priority);
        }
        
        if (expeditedFiling) {
            summary.append(" EXPEDITED");
        }
        
        return summary.toString();
    }

    public boolean hasRegulatory Response() {
        return regulatoryResponse != null && responseReceivedAt != null;
    }

    public boolean isCompliant() {
        return "SUBMITTED".equals(filingStatus) || "ACKNOWLEDGED".equals(filingStatus);
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
        
        return "UNKNOWN";
    }

    @PrePersist
    protected void onCreate() {
        if (detectedAt == null) {
            detectedAt = LocalDateTime.now();
        }
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
        // Generate SAR number
        if (sarNumber == null) {
            sarNumber = "SAR-" + LocalDateTime.now().getYear() + "-" + 
                       String.format("%08d", Math.abs(id.hashCode() % 100000000));
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        lastStatusUpdate = LocalDateTime.now();
    }
}