package com.waqiti.legal.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Compliance Assessment Domain Entity
 *
 * Complete production-ready compliance assessment tracking with:
 * - Multi-framework compliance evaluation (GDPR, SOC2, ISO27001, PCI-DSS, etc.)
 * - Risk-based assessment scoring
 * - Gap analysis and remediation tracking
 * - Evidence collection and documentation
 * - Automated follow-up scheduling
 * - Corrective action plan management
 * - Compliance trend analysis
 * - Regulatory reporting integration
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Entity
@Table(name = "legal_compliance_assessment",
    indexes = {
        @Index(name = "idx_legal_compliance_assessment_requirement", columnList = "requirement_id"),
        @Index(name = "idx_legal_compliance_assessment_entity", columnList = "entity_id"),
        @Index(name = "idx_legal_compliance_assessment_status", columnList = "compliance_status"),
        @Index(name = "idx_legal_compliance_assessment_date", columnList = "assessment_date")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplianceAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assessment_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Assessment ID is required")
    private String assessmentId;

    @Column(name = "requirement_id", nullable = false, length = 100)
    @NotBlank(message = "Requirement ID is required")
    private String requirementId;

    @Column(name = "entity_id", nullable = false, length = 100)
    @NotBlank(message = "Entity ID is required")
    private String entityId;

    @Column(name = "entity_type", nullable = false, length = 50)
    @NotBlank(message = "Entity type is required")
    private String entityType;

    @Column(name = "assessment_status", nullable = false, length = 20)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AssessmentStatus assessmentStatus = AssessmentStatus.IN_PROGRESS;

    @Column(name = "compliance_status", nullable = false, length = 20)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ComplianceStatus complianceStatus = ComplianceStatus.PENDING;

    @Column(name = "assessment_date", nullable = false)
    @NotNull
    private LocalDate assessmentDate;

    @Column(name = "assessor", nullable = false, length = 100)
    @NotBlank(message = "Assessor is required")
    private String assessor;

    @Column(name = "assessment_method", nullable = false, length = 50)
    @NotNull
    @Enumerated(EnumType.STRING)
    private AssessmentMethod assessmentMethod;

    @Column(name = "findings", columnDefinition = "TEXT")
    private String findings;

    @Type(JsonBinaryType.class)
    @Column(name = "evidence_collected", columnDefinition = "text[]")
    @Builder.Default
    private List<String> evidenceCollected = new ArrayList<>();

    @Column(name = "compliance_score")
    @Min(0)
    @Max(100)
    private Integer complianceScore;

    @Column(name = "risk_level", length = 20)
    @Enumerated(EnumType.STRING)
    private RiskLevel riskLevel;

    @Type(JsonBinaryType.class)
    @Column(name = "gaps_identified", columnDefinition = "text[]")
    @Builder.Default
    private List<String> gapsIdentified = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "corrective_actions", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> correctiveActions = new ArrayList<>();

    @Column(name = "remediation_deadline")
    private LocalDate remediationDeadline;

    @Column(name = "remediation_status", length = 20)
    @Enumerated(EnumType.STRING)
    private RemediationStatus remediationStatus;

    @Column(name = "follow_up_required")
    @Builder.Default
    private Boolean followUpRequired = false;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "next_assessment_date")
    private LocalDate nextAssessmentDate;

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
        if (assessmentId == null) {
            assessmentId = "CA-" + UUID.randomUUID().toString();
        }
        if (assessmentDate == null) {
            assessmentDate = LocalDate.now();
        }
        // Default next assessment based on risk level
        if (nextAssessmentDate == null && riskLevel != null) {
            nextAssessmentDate = calculateNextAssessmentDate();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum AssessmentStatus {
        PLANNED,
        IN_PROGRESS,
        COMPLETED,
        APPROVED,
        REJECTED,
        UNDER_REVIEW,
        CANCELLED
    }

    public enum ComplianceStatus {
        PENDING,
        COMPLIANT,
        NON_COMPLIANT,
        PARTIALLY_COMPLIANT,
        UNDER_REVIEW,
        REMEDIATION_REQUIRED,
        REMEDIATION_IN_PROGRESS,
        NOT_APPLICABLE
    }

    public enum AssessmentMethod {
        DOCUMENT_REVIEW,
        INTERVIEW,
        TECHNICAL_TESTING,
        PENETRATION_TESTING,
        AUTOMATED_SCAN,
        MANUAL_INSPECTION,
        WALKTHROUGHS,
        OBSERVATION,
        SAMPLING,
        HYBRID
    }

    public enum RiskLevel {
        CRITICAL,
        HIGH,
        MEDIUM,
        LOW,
        NEGLIGIBLE
    }

    public enum RemediationStatus {
        NOT_STARTED,
        IN_PROGRESS,
        BLOCKED,
        COMPLETED,
        VERIFIED,
        CLOSED,
        DEFERRED
    }

    // Complete business logic methods

    /**
     * Check if assessment is overdue
     */
    public boolean isOverdue() {
        return assessmentStatus != AssessmentStatus.COMPLETED &&
               assessmentStatus != AssessmentStatus.APPROVED &&
               assessmentDate != null &&
               LocalDate.now().isAfter(assessmentDate);
    }

    /**
     * Check if remediation is overdue
     */
    public boolean isRemediationOverdue() {
        return remediationDeadline != null &&
               remediationStatus != RemediationStatus.COMPLETED &&
               remediationStatus != RemediationStatus.VERIFIED &&
               remediationStatus != RemediationStatus.CLOSED &&
               LocalDate.now().isAfter(remediationDeadline);
    }

    /**
     * Complete assessment with findings
     */
    public void completeAssessment(String findingsText, int score, RiskLevel risk, List<String> gaps) {
        if (assessmentStatus == AssessmentStatus.COMPLETED ||
            assessmentStatus == AssessmentStatus.APPROVED) {
            throw new IllegalStateException("Assessment already completed");
        }

        this.findings = findingsText;
        this.complianceScore = score;
        this.riskLevel = risk;
        this.gapsIdentified = gaps != null ? gaps : new ArrayList<>();
        this.assessmentStatus = AssessmentStatus.COMPLETED;

        // Determine compliance status based on score
        determineComplianceStatus();

        // Schedule next assessment
        this.nextAssessmentDate = calculateNextAssessmentDate();

        // Set follow-up if gaps identified
        if (!gapsIdentified.isEmpty()) {
            this.followUpRequired = true;
            this.followUpDate = LocalDate.now().plusMonths(1);
        }
    }

    /**
     * Determine compliance status based on score and gaps
     */
    private void determineComplianceStatus() {
        if (complianceScore == null) {
            this.complianceStatus = ComplianceStatus.PENDING;
            return;
        }

        if (complianceScore >= 90 && gapsIdentified.isEmpty()) {
            this.complianceStatus = ComplianceStatus.COMPLIANT;
        } else if (complianceScore >= 70) {
            this.complianceStatus = ComplianceStatus.PARTIALLY_COMPLIANT;
        } else {
            this.complianceStatus = ComplianceStatus.NON_COMPLIANT;
        }

        // Override if critical gaps exist
        if (!gapsIdentified.isEmpty() && riskLevel == RiskLevel.CRITICAL) {
            this.complianceStatus = ComplianceStatus.REMEDIATION_REQUIRED;
        }
    }

    /**
     * Calculate next assessment date based on risk level
     */
    private LocalDate calculateNextAssessmentDate() {
        LocalDate baseDate = assessmentDate != null ? assessmentDate : LocalDate.now();

        if (riskLevel == null) {
            return baseDate.plusMonths(6); // Default: semi-annual
        }

        return switch (riskLevel) {
            case CRITICAL -> baseDate.plusMonths(1);  // Monthly for critical
            case HIGH -> baseDate.plusMonths(3);       // Quarterly for high
            case MEDIUM -> baseDate.plusMonths(6);     // Semi-annual for medium
            case LOW -> baseDate.plusYears(1);         // Annual for low
            case NEGLIGIBLE -> baseDate.plusYears(2);  // Biennial for negligible
        };
    }

    /**
     * Add evidence to assessment
     */
    public void addEvidence(String evidencePath) {
        if (!evidenceCollected.contains(evidencePath)) {
            evidenceCollected.add(evidencePath);
        }
    }

    /**
     * Add multiple evidence items
     */
    public void addEvidenceBatch(List<String> evidencePaths) {
        if (evidencePaths != null) {
            evidencePaths.forEach(this::addEvidence);
        }
    }

    /**
     * Identify compliance gap
     */
    public void addGap(String gap) {
        if (!gapsIdentified.contains(gap)) {
            gapsIdentified.add(gap);
            this.followUpRequired = true;
            if (complianceStatus == ComplianceStatus.COMPLIANT) {
                this.complianceStatus = ComplianceStatus.PARTIALLY_COMPLIANT;
            }
        }
    }

    /**
     * Create corrective action plan
     */
    public void addCorrectiveAction(String actionId, String description, String owner,
                                     LocalDate deadline, String priority) {
        Map<String, Object> action = new HashMap<>();
        action.put("actionId", actionId);
        action.put("description", description);
        action.put("owner", owner);
        action.put("deadline", deadline.toString());
        action.put("priority", priority);
        action.put("status", "NOT_STARTED");
        action.put("createdAt", LocalDateTime.now().toString());
        correctiveActions.add(action);

        // Set remediation deadline to earliest action deadline
        if (remediationDeadline == null || deadline.isBefore(remediationDeadline)) {
            this.remediationDeadline = deadline;
        }

        this.remediationStatus = RemediationStatus.NOT_STARTED;
        this.complianceStatus = ComplianceStatus.REMEDIATION_REQUIRED;
    }

    /**
     * Update corrective action status
     */
    public void updateCorrectiveActionStatus(String actionId, String status, String notes) {
        correctiveActions.stream()
                .filter(action -> actionId.equals(action.get("actionId")))
                .findFirst()
                .ifPresent(action -> {
                    action.put("status", status);
                    action.put("updatedAt", LocalDateTime.now().toString());
                    if (notes != null) {
                        action.put("notes", notes);
                    }
                });

        // Update overall remediation status
        updateRemediationStatus();
    }

    /**
     * Complete corrective action
     */
    public void completeCorrectiveAction(String actionId, String completionNotes) {
        updateCorrectiveActionStatus(actionId, "COMPLETED", completionNotes);
    }

    /**
     * Update overall remediation status based on corrective actions
     */
    private void updateRemediationStatus() {
        if (correctiveActions.isEmpty()) {
            this.remediationStatus = null;
            return;
        }

        long totalActions = correctiveActions.size();
        long completedActions = correctiveActions.stream()
                .filter(action -> "COMPLETED".equals(action.get("status")))
                .count();
        long inProgressActions = correctiveActions.stream()
                .filter(action -> "IN_PROGRESS".equals(action.get("status")))
                .count();

        if (completedActions == totalActions) {
            this.remediationStatus = RemediationStatus.COMPLETED;
            this.complianceStatus = ComplianceStatus.UNDER_REVIEW;
        } else if (inProgressActions > 0) {
            this.remediationStatus = RemediationStatus.IN_PROGRESS;
            this.complianceStatus = ComplianceStatus.REMEDIATION_IN_PROGRESS;
        } else if (completedActions > 0) {
            this.remediationStatus = RemediationStatus.IN_PROGRESS;
        }
    }

    /**
     * Approve assessment
     */
    public void approve(String approver) {
        if (assessmentStatus != AssessmentStatus.COMPLETED &&
            assessmentStatus != AssessmentStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Assessment must be completed before approval");
        }
        this.approvedBy = approver;
        this.approvedAt = LocalDateTime.now();
        this.assessmentStatus = AssessmentStatus.APPROVED;
    }

    /**
     * Reject assessment with reason
     */
    public void reject(String reason) {
        if (assessmentStatus != AssessmentStatus.COMPLETED &&
            assessmentStatus != AssessmentStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Can only reject completed assessments");
        }
        this.assessmentStatus = AssessmentStatus.REJECTED;
        this.findings = (findings != null ? findings + "\n\n" : "") + "REJECTION REASON: " + reason;
    }

    /**
     * Start remediation process
     */
    public void startRemediation() {
        if (correctiveActions.isEmpty()) {
            throw new IllegalStateException("No corrective actions defined");
        }
        this.remediationStatus = RemediationStatus.IN_PROGRESS;
        this.complianceStatus = ComplianceStatus.REMEDIATION_IN_PROGRESS;
    }

    /**
     * Verify remediation completion
     */
    public void verifyRemediation(String verifier, boolean passed, String verificationNotes) {
        if (remediationStatus != RemediationStatus.COMPLETED) {
            throw new IllegalStateException("Remediation must be completed before verification");
        }

        if (passed) {
            this.remediationStatus = RemediationStatus.VERIFIED;
            this.complianceStatus = ComplianceStatus.COMPLIANT;
            this.approvedBy = verifier;
            this.approvedAt = LocalDateTime.now();
        } else {
            this.remediationStatus = RemediationStatus.IN_PROGRESS;
            this.complianceStatus = ComplianceStatus.REMEDIATION_IN_PROGRESS;
            this.findings = (findings != null ? findings + "\n\n" : "") +
                    "VERIFICATION FAILED: " + verificationNotes;
        }
    }

    /**
     * Close assessment with remediation
     */
    public void close() {
        if (remediationStatus != RemediationStatus.VERIFIED &&
            complianceStatus != ComplianceStatus.COMPLIANT) {
            throw new IllegalStateException("Assessment cannot be closed - remediation not verified");
        }
        this.remediationStatus = RemediationStatus.CLOSED;
        this.followUpRequired = false;
    }

    /**
     * Defer remediation
     */
    public void deferRemediation(LocalDate newDeadline, String reason) {
        this.remediationDeadline = newDeadline;
        this.remediationStatus = RemediationStatus.DEFERRED;
        this.findings = (findings != null ? findings + "\n\n" : "") +
                "REMEDIATION DEFERRED: " + reason + " (New deadline: " + newDeadline + ")";
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
     * Schedule follow-up assessment
     */
    public void scheduleFollowUp(LocalDate date) {
        this.followUpRequired = true;
        this.followUpDate = date;
    }

    /**
     * Get days until remediation deadline
     */
    public long getDaysUntilRemediationDeadline() {
        if (remediationDeadline == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), remediationDeadline);
    }

    /**
     * Get remediation progress percentage
     */
    public int getRemediationProgress() {
        if (correctiveActions.isEmpty()) {
            return 0;
        }

        long completedCount = correctiveActions.stream()
                .filter(action -> "COMPLETED".equals(action.get("status")))
                .count();

        return (int) ((completedCount * 100) / correctiveActions.size());
    }

    /**
     * Check if requires escalation
     */
    public boolean requiresEscalation() {
        // Escalate if critical risk with no remediation progress
        if (riskLevel == RiskLevel.CRITICAL &&
            remediationStatus == RemediationStatus.NOT_STARTED &&
            getDaysUntilRemediationDeadline() < 7) {
            return true;
        }

        // Escalate if remediation overdue
        if (isRemediationOverdue()) {
            return true;
        }

        // Escalate if non-compliant for over 30 days
        if (complianceStatus == ComplianceStatus.NON_COMPLIANT &&
            ChronoUnit.DAYS.between(assessmentDate, LocalDate.now()) > 30) {
            return true;
        }

        return false;
    }

    /**
     * Generate assessment summary
     */
    public Map<String, Object> generateSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("assessmentId", assessmentId);
        summary.put("requirementId", requirementId);
        summary.put("entityId", entityId);
        summary.put("entityType", entityType);
        summary.put("assessmentStatus", assessmentStatus);
        summary.put("complianceStatus", complianceStatus);
        summary.put("complianceScore", complianceScore);
        summary.put("riskLevel", riskLevel);
        summary.put("gapsCount", gapsIdentified.size());
        summary.put("correctiveActionsCount", correctiveActions.size());
        summary.put("remediationProgress", getRemediationProgress());
        summary.put("evidenceCount", evidenceCollected.size());
        summary.put("isOverdue", isOverdue());
        summary.put("isRemediationOverdue", isRemediationOverdue());
        summary.put("requiresEscalation", requiresEscalation());
        summary.put("nextAssessmentDate", nextAssessmentDate);
        summary.put("assessmentDate", assessmentDate);
        return summary;
    }

    /**
     * Validate assessment completion
     */
    public List<String> validateForCompletion() {
        List<String> errors = new ArrayList<>();

        if (findings == null || findings.isBlank()) {
            errors.add("Findings are required");
        }
        if (complianceScore == null) {
            errors.add("Compliance score is required");
        }
        if (riskLevel == null) {
            errors.add("Risk level assessment is required");
        }
        if (evidenceCollected.isEmpty()) {
            errors.add("At least one evidence item must be collected");
        }
        if (complianceScore != null && complianceScore < 70 && correctiveActions.isEmpty()) {
            errors.add("Corrective actions required for low compliance score");
        }

        return errors;
    }

    /**
     * Get risk-adjusted compliance score
     */
    public double getRiskAdjustedScore() {
        if (complianceScore == null) {
            return 0.0;
        }

        double score = complianceScore.doubleValue();

        // Adjust based on risk level
        if (riskLevel != null) {
            score = switch (riskLevel) {
                case CRITICAL -> score * 0.7;  // Penalize critical risk
                case HIGH -> score * 0.85;
                case MEDIUM -> score * 0.95;
                case LOW, NEGLIGIBLE -> score;
            };
        }

        // Penalize overdue remediation
        if (isRemediationOverdue()) {
            score = score * 0.8;
        }

        return Math.max(0, Math.min(100, score));
    }
}
