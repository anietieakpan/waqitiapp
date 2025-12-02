package com.waqiti.legal.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Legal Audit Domain Entity
 *
 * Complete production-ready legal audit management with:
 * - Comprehensive audit planning and execution
 * - Multi-entity and multi-document audit scope
 * - Risk-based findings classification
 * - Compliance rate calculation
 * - Action item tracking and remediation
 * - Audit team coordination
 * - Management response workflow
 * - Follow-up scheduling and tracking
 * - Audit report generation
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Entity
@Table(name = "legal_audit",
    indexes = {
        @Index(name = "idx_legal_audit_type", columnList = "audit_type"),
        @Index(name = "idx_legal_audit_status", columnList = "audit_status"),
        @Index(name = "idx_legal_audit_start_date", columnList = "audit_start_date")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "audit_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Audit ID is required")
    private String auditId;

    @Column(name = "audit_name", nullable = false)
    @NotBlank(message = "Audit name is required")
    private String auditName;

    @Column(name = "audit_type", nullable = false, length = 50)
    @NotNull
    @Enumerated(EnumType.STRING)
    private AuditType auditType;

    @Column(name = "audit_scope", columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "Audit scope is required")
    private String auditScope;

    @Column(name = "audit_status", nullable = false, length = 20)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AuditStatus auditStatus = AuditStatus.PLANNED;

    @Column(name = "audit_start_date", nullable = false)
    @NotNull(message = "Audit start date is required")
    private LocalDate auditStartDate;

    @Column(name = "audit_end_date")
    private LocalDate auditEndDate;

    @Column(name = "auditor", nullable = false, length = 100)
    @NotBlank(message = "Auditor is required")
    private String auditor;

    @Type(JsonBinaryType.class)
    @Column(name = "audit_team", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> auditTeam = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "entities_audited", columnDefinition = "text[]")
    @Builder.Default
    private List<String> entitiesAudited = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "documents_reviewed", columnDefinition = "text[]")
    @Builder.Default
    private List<String> documentsReviewed = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "contracts_reviewed", columnDefinition = "text[]")
    @Builder.Default
    private List<String> contractsReviewed = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "findings", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> findings = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "recommendations", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> recommendations = new ArrayList<>();

    @Column(name = "high_risk_findings")
    @Builder.Default
    private Integer highRiskFindings = 0;

    @Column(name = "medium_risk_findings")
    @Builder.Default
    private Integer mediumRiskFindings = 0;

    @Column(name = "low_risk_findings")
    @Builder.Default
    private Integer lowRiskFindings = 0;

    @Column(name = "compliance_rate", precision = 5, scale = 4)
    @DecimalMin(value = "0.0000")
    @DecimalMax(value = "1.0000")
    private BigDecimal complianceRate;

    @Column(name = "overall_assessment", length = 20)
    @Enumerated(EnumType.STRING)
    private OverallAssessment overallAssessment;

    @Type(JsonBinaryType.class)
    @Column(name = "action_items", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> actionItems = new ArrayList<>();

    @Column(name = "follow_up_required")
    @Builder.Default
    private Boolean followUpRequired = false;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "audit_report_path", length = 1000)
    private String auditReportPath;

    @Column(name = "management_response", columnDefinition = "TEXT")
    private String managementResponse;

    @Column(name = "created_by", nullable = false, length = 100)
    @NotBlank(message = "Created by is required")
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @NotNull
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @NotNull
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (auditId == null) {
            auditId = "AUD-" + UUID.randomUUID().toString();
        }
        if (auditStartDate == null) {
            auditStartDate = LocalDate.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum AuditType {
        COMPLIANCE_AUDIT,
        CONTRACT_AUDIT,
        RISK_AUDIT,
        OPERATIONAL_AUDIT,
        FINANCIAL_AUDIT,
        REGULATORY_AUDIT,
        INTERNAL_AUDIT,
        EXTERNAL_AUDIT,
        DUE_DILIGENCE,
        VENDOR_AUDIT,
        SECURITY_AUDIT,
        PRIVACY_AUDIT,
        INTELLECTUAL_PROPERTY_AUDIT,
        EMPLOYMENT_AUDIT,
        LITIGATION_READINESS,
        OTHER
    }

    public enum AuditStatus {
        PLANNED,
        IN_PROGRESS,
        FIELDWORK_COMPLETE,
        DRAFT_REPORT,
        MANAGEMENT_REVIEW,
        FINAL_REPORT,
        COMPLETED,
        FOLLOW_UP,
        CLOSED,
        CANCELLED
    }

    public enum OverallAssessment {
        EXCELLENT,      // >90% compliance, no high-risk findings
        SATISFACTORY,   // 75-90% compliance, few high-risk findings
        NEEDS_IMPROVEMENT, // 50-75% compliance, some high-risk findings
        UNSATISFACTORY, // <50% compliance, many high-risk findings
        CRITICAL        // <25% compliance, critical findings
    }

    // Complete business logic methods

    /**
     * Check if audit is overdue
     */
    public boolean isOverdue() {
        if (auditStatus == AuditStatus.COMPLETED ||
            auditStatus == AuditStatus.CLOSED ||
            auditStatus == AuditStatus.CANCELLED) {
            return false;
        }

        if (auditEndDate == null) {
            // Default 30 days for audit completion
            return ChronoUnit.DAYS.between(auditStartDate, LocalDate.now()) > 30;
        }

        return LocalDate.now().isAfter(auditEndDate);
    }

    /**
     * Get audit duration in days
     */
    public long getAuditDurationDays() {
        LocalDate endDate = auditEndDate != null ? auditEndDate : LocalDate.now();
        return ChronoUnit.DAYS.between(auditStartDate, endDate);
    }

    /**
     * Start audit
     */
    public void startAudit() {
        if (auditStatus != AuditStatus.PLANNED) {
            throw new IllegalStateException("Can only start planned audits");
        }
        this.auditStatus = AuditStatus.IN_PROGRESS;
        this.auditStartDate = LocalDate.now();
    }

    /**
     * Add audit team member
     */
    public void addTeamMember(String memberId, String memberName, String role, String expertise) {
        Map<String, Object> member = new HashMap<>();
        member.put("memberId", memberId);
        member.put("memberName", memberName);
        member.put("role", role);
        member.put("expertise", expertise);
        member.put("addedAt", LocalDateTime.now().toString());
        auditTeam.add(member);
    }

    /**
     * Add entity to audit scope
     */
    public void addEntityToScope(String entityId) {
        if (!entitiesAudited.contains(entityId)) {
            entitiesAudited.add(entityId);
        }
    }

    /**
     * Add document to review list
     */
    public void addDocumentReviewed(String documentId) {
        if (!documentsReviewed.contains(documentId)) {
            documentsReviewed.add(documentId);
        }
    }

    /**
     * Add contract to review list
     */
    public void addContractReviewed(String contractId) {
        if (!contractsReviewed.contains(contractId)) {
            contractsReviewed.add(contractId);
        }
    }

    /**
     * Add finding
     */
    public void addFinding(String findingId, String title, String description,
                           String riskLevel, String affectedEntity, String recommendation) {
        Map<String, Object> finding = new HashMap<>();
        finding.put("findingId", findingId);
        finding.put("title", title);
        finding.put("description", description);
        finding.put("riskLevel", riskLevel);
        finding.put("affectedEntity", affectedEntity);
        finding.put("recommendation", recommendation);
        finding.put("identifiedAt", LocalDateTime.now().toString());
        finding.put("status", "OPEN");
        findings.add(finding);

        // Update risk counters
        updateRiskCounters(riskLevel, 1);

        // Recalculate overall assessment
        calculateOverallAssessment();
    }

    /**
     * Update risk counters
     */
    private void updateRiskCounters(String riskLevel, int delta) {
        switch (riskLevel.toUpperCase()) {
            case "HIGH", "CRITICAL" -> highRiskFindings += delta;
            case "MEDIUM" -> mediumRiskFindings += delta;
            case "LOW" -> lowRiskFindings += delta;
        }
    }

    /**
     * Resolve finding
     */
    public void resolveFinding(String findingId, String resolution, String resolvedBy) {
        findings.stream()
                .filter(f -> findingId.equals(f.get("findingId")))
                .findFirst()
                .ifPresent(f -> {
                    String riskLevel = (String) f.get("riskLevel");
                    f.put("status", "RESOLVED");
                    f.put("resolution", resolution);
                    f.put("resolvedBy", resolvedBy);
                    f.put("resolvedAt", LocalDateTime.now().toString());

                    // Update risk counters
                    updateRiskCounters(riskLevel, -1);
                });

        // Recalculate overall assessment
        calculateOverallAssessment();
    }

    /**
     * Add recommendation
     */
    public void addRecommendation(String recommendationId, String title, String description,
                                   String priority, String owner, LocalDate targetDate) {
        Map<String, Object> recommendation = new HashMap<>();
        recommendation.put("recommendationId", recommendationId);
        recommendation.put("title", title);
        recommendation.put("description", description);
        recommendation.put("priority", priority);
        recommendation.put("owner", owner);
        recommendation.put("targetDate", targetDate != null ? targetDate.toString() : null);
        recommendation.put("status", "PENDING");
        recommendation.put("createdAt", LocalDateTime.now().toString());
        recommendations.add(recommendation);
    }

    /**
     * Implement recommendation
     */
    public void implementRecommendation(String recommendationId, String implementationNotes) {
        recommendations.stream()
                .filter(r -> recommendationId.equals(r.get("recommendationId")))
                .findFirst()
                .ifPresent(r -> {
                    r.put("status", "IMPLEMENTED");
                    r.put("implementationNotes", implementationNotes);
                    r.put("implementedAt", LocalDateTime.now().toString());
                });
    }

    /**
     * Add action item
     */
    public void addActionItem(String actionId, String description, String assignedTo,
                              LocalDate dueDate, String priority) {
        Map<String, Object> actionItem = new HashMap<>();
        actionItem.put("actionId", actionId);
        actionItem.put("description", description);
        actionItem.put("assignedTo", assignedTo);
        actionItem.put("dueDate", dueDate != null ? dueDate.toString() : null);
        actionItem.put("priority", priority);
        actionItem.put("status", "OPEN");
        actionItem.put("createdAt", LocalDateTime.now().toString());
        actionItems.add(actionItem);
    }

    /**
     * Complete action item
     */
    public void completeActionItem(String actionId, String completionNotes) {
        actionItems.stream()
                .filter(a -> actionId.equals(a.get("actionId")))
                .findFirst()
                .ifPresent(a -> {
                    a.put("status", "COMPLETED");
                    a.put("completionNotes", completionNotes);
                    a.put("completedAt", LocalDateTime.now().toString());
                });
    }

    /**
     * Calculate compliance rate
     */
    public void calculateComplianceRate(int totalControls, int compliantControls) {
        if (totalControls == 0) {
            this.complianceRate = BigDecimal.ZERO;
            return;
        }

        this.complianceRate = BigDecimal.valueOf(compliantControls)
                .divide(BigDecimal.valueOf(totalControls), 4, RoundingMode.HALF_UP);

        // Update overall assessment
        calculateOverallAssessment();
    }

    /**
     * Calculate overall assessment
     */
    private void calculateOverallAssessment() {
        if (complianceRate == null) {
            this.overallAssessment = null;
            return;
        }

        double rate = complianceRate.doubleValue();
        int totalFindings = highRiskFindings + mediumRiskFindings + lowRiskFindings;

        if (rate >= 0.90 && highRiskFindings == 0) {
            this.overallAssessment = OverallAssessment.EXCELLENT;
        } else if (rate >= 0.75 && highRiskFindings <= 2) {
            this.overallAssessment = OverallAssessment.SATISFACTORY;
        } else if (rate >= 0.50) {
            this.overallAssessment = OverallAssessment.NEEDS_IMPROVEMENT;
        } else if (rate >= 0.25) {
            this.overallAssessment = OverallAssessment.UNSATISFACTORY;
        } else {
            this.overallAssessment = OverallAssessment.CRITICAL;
        }

        // Downgrade if too many high-risk findings
        if (highRiskFindings >= 5) {
            this.overallAssessment = OverallAssessment.CRITICAL;
        } else if (highRiskFindings >= 3 && overallAssessment == OverallAssessment.EXCELLENT) {
            this.overallAssessment = OverallAssessment.SATISFACTORY;
        }
    }

    /**
     * Complete fieldwork
     */
    public void completeFieldwork() {
        if (auditStatus != AuditStatus.IN_PROGRESS) {
            throw new IllegalStateException("Audit must be in progress to complete fieldwork");
        }
        this.auditStatus = AuditStatus.FIELDWORK_COMPLETE;
    }

    /**
     * Generate draft report
     */
    public void generateDraftReport(String reportPath) {
        if (auditStatus != AuditStatus.FIELDWORK_COMPLETE) {
            throw new IllegalStateException("Fieldwork must be complete before generating draft report");
        }
        this.auditReportPath = reportPath;
        this.auditStatus = AuditStatus.DRAFT_REPORT;
    }

    /**
     * Submit for management review
     */
    public void submitForManagementReview() {
        if (auditStatus != AuditStatus.DRAFT_REPORT) {
            throw new IllegalStateException("Draft report must be generated before management review");
        }
        this.auditStatus = AuditStatus.MANAGEMENT_REVIEW;
    }

    /**
     * Add management response
     */
    public void addManagementResponse(String response) {
        if (auditStatus != AuditStatus.MANAGEMENT_REVIEW) {
            throw new IllegalStateException("Audit must be in management review to add response");
        }
        this.managementResponse = response;
    }

    /**
     * Finalize report
     */
    public void finalizeReport(String finalReportPath) {
        if (auditStatus != AuditStatus.MANAGEMENT_REVIEW) {
            throw new IllegalStateException("Management review must be complete before finalizing");
        }
        if (managementResponse == null || managementResponse.isBlank()) {
            throw new IllegalStateException("Management response is required before finalizing");
        }
        this.auditReportPath = finalReportPath;
        this.auditStatus = AuditStatus.FINAL_REPORT;
    }

    /**
     * Complete audit
     */
    public void complete() {
        if (auditStatus != AuditStatus.FINAL_REPORT) {
            throw new IllegalStateException("Final report must be issued before completing audit");
        }
        this.auditStatus = AuditStatus.COMPLETED;
        this.auditEndDate = LocalDate.now();

        // Determine if follow-up needed
        if (highRiskFindings > 0 || mediumRiskFindings > 0) {
            this.followUpRequired = true;
            this.followUpDate = LocalDate.now().plusMonths(3);
        }
    }

    /**
     * Schedule follow-up
     */
    public void scheduleFollowUp(LocalDate date) {
        this.followUpRequired = true;
        this.followUpDate = date;
    }

    /**
     * Start follow-up audit
     */
    public void startFollowUp() {
        if (!followUpRequired) {
            throw new IllegalStateException("Follow-up not required");
        }
        this.auditStatus = AuditStatus.FOLLOW_UP;
    }

    /**
     * Close audit
     */
    public void closeAudit() {
        if (auditStatus != AuditStatus.COMPLETED && auditStatus != AuditStatus.FOLLOW_UP) {
            throw new IllegalStateException("Audit must be completed before closing");
        }

        // Check if all action items are completed
        long openActions = actionItems.stream()
                .filter(a -> "OPEN".equals(a.get("status")))
                .count();

        if (openActions > 0) {
            throw new IllegalStateException("All action items must be completed before closing audit");
        }

        this.auditStatus = AuditStatus.CLOSED;
    }

    /**
     * Cancel audit
     */
    public void cancel(String reason) {
        this.auditStatus = AuditStatus.CANCELLED;
        this.managementResponse = "AUDIT CANCELLED: " + reason;
    }

    /**
     * Get total findings count
     */
    public int getTotalFindings() {
        return highRiskFindings + mediumRiskFindings + lowRiskFindings;
    }

    /**
     * Get open findings count
     */
    public long getOpenFindings() {
        return findings.stream()
                .filter(f -> "OPEN".equals(f.get("status")))
                .count();
    }

    /**
     * Get open action items count
     */
    public long getOpenActionItems() {
        return actionItems.stream()
                .filter(a -> "OPEN".equals(a.get("status")))
                .count();
    }

    /**
     * Get action item completion percentage
     */
    public int getActionItemCompletionPercentage() {
        if (actionItems.isEmpty()) {
            return 100;
        }

        long completedCount = actionItems.stream()
                .filter(a -> "COMPLETED".equals(a.get("status")))
                .count();

        return (int) ((completedCount * 100) / actionItems.size());
    }

    /**
     * Check if follow-up is due
     */
    public boolean isFollowUpDue() {
        return followUpRequired &&
               followUpDate != null &&
               !LocalDate.now().isBefore(followUpDate);
    }

    /**
     * Generate audit summary
     */
    public Map<String, Object> generateSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("auditId", auditId);
        summary.put("auditName", auditName);
        summary.put("auditType", auditType);
        summary.put("auditStatus", auditStatus);
        summary.put("auditor", auditor);
        summary.put("auditStartDate", auditStartDate);
        summary.put("auditEndDate", auditEndDate);
        summary.put("durationDays", getAuditDurationDays());
        summary.put("isOverdue", isOverdue());
        summary.put("entitiesAuditedCount", entitiesAudited.size());
        summary.put("documentsReviewedCount", documentsReviewed.size());
        summary.put("contractsReviewedCount", contractsReviewed.size());
        summary.put("totalFindings", getTotalFindings());
        summary.put("highRiskFindings", highRiskFindings);
        summary.put("mediumRiskFindings", mediumRiskFindings);
        summary.put("lowRiskFindings", lowRiskFindings);
        summary.put("openFindings", getOpenFindings());
        summary.put("complianceRate", complianceRate);
        summary.put("overallAssessment", overallAssessment);
        summary.put("recommendationsCount", recommendations.size());
        summary.put("actionItemsCount", actionItems.size());
        summary.put("openActionItems", getOpenActionItems());
        summary.put("actionItemCompletionPercentage", getActionItemCompletionPercentage());
        summary.put("followUpRequired", followUpRequired);
        summary.put("isFollowUpDue", isFollowUpDue());
        return summary;
    }

    /**
     * Validate audit for completion
     */
    public List<String> validateForCompletion() {
        List<String> errors = new ArrayList<>();

        if (findings.isEmpty()) {
            errors.add("At least one finding must be documented");
        }
        if (complianceRate == null) {
            errors.add("Compliance rate must be calculated");
        }
        if (auditReportPath == null || auditReportPath.isBlank()) {
            errors.add("Audit report must be generated");
        }
        if (managementResponse == null || managementResponse.isBlank()) {
            errors.add("Management response is required");
        }
        if (overallAssessment == null) {
            errors.add("Overall assessment must be determined");
        }

        return errors;
    }

    /**
     * Get audit health score (0-100)
     */
    public int getAuditHealthScore() {
        int score = 100;

        // Deduct for high-risk findings
        score -= (highRiskFindings * 15);

        // Deduct for medium-risk findings
        score -= (mediumRiskFindings * 5);

        // Deduct for low-risk findings
        score -= (lowRiskFindings * 2);

        // Deduct if overdue
        if (isOverdue()) {
            score -= 20;
        }

        // Add for compliance rate
        if (complianceRate != null) {
            score += (int) (complianceRate.doubleValue() * 20);
        }

        return Math.max(0, Math.min(100, score));
    }
}
