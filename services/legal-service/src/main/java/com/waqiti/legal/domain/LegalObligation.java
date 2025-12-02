package com.waqiti.legal.domain;

import jakarta.persistence.*;
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
 * Legal Obligation Domain Entity
 *
 * Complete production-ready contractual obligation tracking with:
 * - Contract and document obligation extraction
 * - Performance monitoring and compliance tracking
 * - Recurring obligation management
 * - Deliverable tracking with acceptance criteria
 * - Performance bond management
 * - Breach detection and penalty calculation
 * - Automated notification and escalation
 * - Remediation action tracking
 * - Multi-party responsibility assignment
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Entity
@Table(name = "legal_obligation",
    indexes = {
        @Index(name = "idx_legal_obligation_type", columnList = "obligation_type"),
        @Index(name = "idx_legal_obligation_contract", columnList = "contract_id"),
        @Index(name = "idx_legal_obligation_status", columnList = "obligation_status"),
        @Index(name = "idx_legal_obligation_due_date", columnList = "due_date"),
        @Index(name = "idx_legal_obligation_responsible", columnList = "responsible_party")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalObligation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "obligation_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Obligation ID is required")
    private String obligationId;

    @Column(name = "obligation_name", nullable = false)
    @NotBlank(message = "Obligation name is required")
    private String obligationName;

    @Column(name = "obligation_type", nullable = false, length = 50)
    @NotNull
    @Enumerated(EnumType.STRING)
    private ObligationType obligationType;

    @Column(name = "contract_id", length = 100)
    private String contractId;

    @Column(name = "document_id", length = 100)
    private String documentId;

    @Column(name = "obligation_description", columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "Obligation description is required")
    private String obligationDescription;

    @Column(name = "responsible_party", nullable = false, length = 100)
    @NotBlank(message = "Responsible party is required")
    private String responsibleParty;

    @Column(name = "counterparty", length = 100)
    private String counterparty;

    @Column(name = "obligation_status", nullable = false, length = 20)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ObligationStatus obligationStatus = ObligationStatus.PENDING;

    @Column(name = "due_date", nullable = false)
    @NotNull(message = "Due date is required")
    private LocalDate dueDate;

    @Column(name = "completion_date")
    private LocalDate completionDate;

    @Column(name = "recurring")
    @Builder.Default
    private Boolean recurring = false;

    @Column(name = "recurrence_frequency", length = 20)
    @Enumerated(EnumType.STRING)
    private RecurrenceFrequency recurrenceFrequency;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Type(JsonBinaryType.class)
    @Column(name = "deliverables", columnDefinition = "text[]")
    @Builder.Default
    private List<String> deliverables = new ArrayList<>();

    @Column(name = "acceptance_criteria", columnDefinition = "TEXT")
    private String acceptanceCriteria;

    @Column(name = "penalty_for_breach", precision = 18, scale = 2)
    @DecimalMin(value = "0.00", message = "Penalty cannot be negative")
    private BigDecimal penaltyForBreach;

    @Column(name = "performance_bond_required")
    @Builder.Default
    private Boolean performanceBondRequired = false;

    @Column(name = "performance_bond_amount", precision = 18, scale = 2)
    private BigDecimal performanceBondAmount;

    @Column(name = "monitoring_frequency", length = 20)
    @Enumerated(EnumType.STRING)
    private MonitoringFrequency monitoringFrequency;

    @Column(name = "last_review_date")
    private LocalDate lastReviewDate;

    @Column(name = "compliance_status", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ComplianceStatus complianceStatus = ComplianceStatus.PENDING;

    @Type(JsonBinaryType.class)
    @Column(name = "issues_identified", columnDefinition = "text[]")
    @Builder.Default
    private List<String> issuesIdentified = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "remediation_actions", columnDefinition = "text[]")
    @Builder.Default
    private List<String> remediationActions = new ArrayList<>();

    @Column(name = "notifications_sent")
    @Builder.Default
    private Integer notificationsSent = 0;

    @Column(name = "escalation_level")
    @Builder.Default
    private Integer escalationLevel = 0;

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
        if (obligationId == null) {
            obligationId = "OBL-" + UUID.randomUUID().toString();
        }
        // Set initial next due date for recurring obligations
        if (recurring && nextDueDate == null && recurrenceFrequency != null) {
            nextDueDate = calculateNextDueDate(dueDate);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum ObligationType {
        PAYMENT,
        DELIVERY,
        PERFORMANCE,
        REPORTING,
        COMPLIANCE,
        CONFIDENTIALITY,
        NON_COMPETE,
        INDEMNIFICATION,
        INSURANCE,
        AUDIT_RIGHTS,
        DATA_PROTECTION,
        NOTIFICATION,
        RENEWAL,
        TERMINATION_NOTICE,
        WARRANTY,
        MAINTENANCE,
        SUPPORT,
        TRAINING,
        CONSULTING,
        OTHER
    }

    public enum ObligationStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        VERIFIED,
        ACCEPTED,
        REJECTED,
        OVERDUE,
        BREACHED,
        WAIVED,
        CANCELLED
    }

    public enum RecurrenceFrequency {
        DAILY,
        WEEKLY,
        BIWEEKLY,
        MONTHLY,
        QUARTERLY,
        SEMI_ANNUAL,
        ANNUAL,
        BIENNIAL
    }

    public enum MonitoringFrequency {
        CONTINUOUS,
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        ANNUAL,
        AD_HOC
    }

    public enum ComplianceStatus {
        PENDING,
        COMPLIANT,
        NON_COMPLIANT,
        UNDER_REVIEW,
        REMEDIATION_REQUIRED,
        AT_RISK
    }

    // Complete business logic methods

    /**
     * Check if obligation is overdue
     */
    public boolean isOverdue() {
        return obligationStatus != ObligationStatus.COMPLETED &&
               obligationStatus != ObligationStatus.VERIFIED &&
               obligationStatus != ObligationStatus.ACCEPTED &&
               obligationStatus != ObligationStatus.CANCELLED &&
               obligationStatus != ObligationStatus.WAIVED &&
               LocalDate.now().isAfter(dueDate);
    }

    /**
     * Get days until due date
     */
    public long getDaysUntilDue() {
        return ChronoUnit.DAYS.between(LocalDate.now(), dueDate);
    }

    /**
     * Get days overdue (negative if not overdue)
     */
    public long getDaysOverdue() {
        if (!isOverdue()) {
            return 0;
        }
        return ChronoUnit.DAYS.between(dueDate, LocalDate.now());
    }

    /**
     * Check if approaching due date (within 7 days)
     */
    public boolean isApproachingDueDate() {
        long daysUntil = getDaysUntilDue();
        return daysUntil > 0 && daysUntil <= 7 &&
               obligationStatus != ObligationStatus.COMPLETED &&
               obligationStatus != ObligationStatus.VERIFIED;
    }

    /**
     * Start obligation performance
     */
    public void startPerformance() {
        if (obligationStatus != ObligationStatus.PENDING) {
            throw new IllegalStateException("Obligation must be in PENDING status to start");
        }
        this.obligationStatus = ObligationStatus.IN_PROGRESS;
        this.complianceStatus = ComplianceStatus.UNDER_REVIEW;
    }

    /**
     * Complete obligation
     */
    public void complete(LocalDate completionDateValue) {
        if (obligationStatus == ObligationStatus.COMPLETED ||
            obligationStatus == ObligationStatus.VERIFIED ||
            obligationStatus == ObligationStatus.ACCEPTED) {
            throw new IllegalStateException("Obligation already completed");
        }

        this.completionDate = completionDateValue != null ? completionDateValue : LocalDate.now();
        this.obligationStatus = ObligationStatus.COMPLETED;
        this.complianceStatus = ComplianceStatus.COMPLIANT;

        // Handle recurring obligations
        if (recurring && recurrenceFrequency != null) {
            scheduleNextOccurrence();
        }
    }

    /**
     * Verify obligation completion
     */
    public void verify(String verifier, boolean accepted, String notes) {
        if (obligationStatus != ObligationStatus.COMPLETED) {
            throw new IllegalStateException("Obligation must be completed before verification");
        }

        if (accepted) {
            this.obligationStatus = ObligationStatus.ACCEPTED;
            this.complianceStatus = ComplianceStatus.COMPLIANT;
        } else {
            this.obligationStatus = ObligationStatus.REJECTED;
            this.complianceStatus = ComplianceStatus.NON_COMPLIANT;
            addIssue("Verification failed: " + notes);
        }

        this.lastReviewDate = LocalDate.now();
    }

    /**
     * Mark obligation as breached
     */
    public void markAsBreached(String reason) {
        this.obligationStatus = ObligationStatus.BREACHED;
        this.complianceStatus = ComplianceStatus.NON_COMPLIANT;
        addIssue("BREACH: " + reason);

        // Apply penalty if specified
        if (penaltyForBreach != null && penaltyForBreach.compareTo(BigDecimal.ZERO) > 0) {
            addIssue("Penalty applicable: " + penaltyForBreach);
        }
    }

    /**
     * Calculate penalty for breach
     */
    public BigDecimal calculateBreachPenalty() {
        if (obligationStatus != ObligationStatus.BREACHED &&
            obligationStatus != ObligationStatus.OVERDUE) {
            return BigDecimal.ZERO;
        }

        if (penaltyForBreach == null) {
            return BigDecimal.ZERO;
        }

        // For overdue, apply penalty
        if (isOverdue()) {
            return penaltyForBreach;
        }

        return BigDecimal.ZERO;
    }

    /**
     * Waive obligation
     */
    public void waive(String reason) {
        this.obligationStatus = ObligationStatus.WAIVED;
        addIssue("WAIVED: " + reason);
    }

    /**
     * Cancel obligation
     */
    public void cancel(String reason) {
        this.obligationStatus = ObligationStatus.CANCELLED;
        addIssue("CANCELLED: " + reason);
    }

    /**
     * Schedule next occurrence for recurring obligation
     */
    private void scheduleNextOccurrence() {
        if (!recurring || recurrenceFrequency == null) {
            return;
        }

        LocalDate currentDue = nextDueDate != null ? nextDueDate : dueDate;
        this.nextDueDate = calculateNextDueDate(currentDue);
    }

    /**
     * Calculate next due date based on recurrence frequency
     */
    private LocalDate calculateNextDueDate(LocalDate fromDate) {
        if (recurrenceFrequency == null) {
            return null;
        }

        return switch (recurrenceFrequency) {
            case DAILY -> fromDate.plusDays(1);
            case WEEKLY -> fromDate.plusWeeks(1);
            case BIWEEKLY -> fromDate.plusWeeks(2);
            case MONTHLY -> fromDate.plusMonths(1);
            case QUARTERLY -> fromDate.plusMonths(3);
            case SEMI_ANNUAL -> fromDate.plusMonths(6);
            case ANNUAL -> fromDate.plusYears(1);
            case BIENNIAL -> fromDate.plusYears(2);
        };
    }

    /**
     * Create next instance of recurring obligation
     */
    public LegalObligation createNextInstance(String creatorId) {
        if (!recurring || nextDueDate == null) {
            throw new IllegalStateException("Obligation is not recurring or next due date not set");
        }

        return LegalObligation.builder()
                .obligationName(this.obligationName)
                .obligationType(this.obligationType)
                .contractId(this.contractId)
                .documentId(this.documentId)
                .obligationDescription(this.obligationDescription)
                .responsibleParty(this.responsibleParty)
                .counterparty(this.counterparty)
                .obligationStatus(ObligationStatus.PENDING)
                .dueDate(this.nextDueDate)
                .recurring(true)
                .recurrenceFrequency(this.recurrenceFrequency)
                .deliverables(new ArrayList<>(this.deliverables))
                .acceptanceCriteria(this.acceptanceCriteria)
                .penaltyForBreach(this.penaltyForBreach)
                .performanceBondRequired(this.performanceBondRequired)
                .performanceBondAmount(this.performanceBondAmount)
                .monitoringFrequency(this.monitoringFrequency)
                .createdBy(creatorId)
                .build();
    }

    /**
     * Add issue
     */
    public void addIssue(String issue) {
        if (!issuesIdentified.contains(issue)) {
            issuesIdentified.add(issue);
            this.complianceStatus = ComplianceStatus.NON_COMPLIANT;
        }
    }

    /**
     * Add remediation action
     */
    public void addRemediationAction(String action) {
        if (!remediationActions.contains(action)) {
            remediationActions.add(action);
            this.complianceStatus = ComplianceStatus.REMEDIATION_REQUIRED;
        }
    }

    /**
     * Send notification
     */
    public void sendNotification() {
        this.notificationsSent++;
    }

    /**
     * Escalate obligation
     */
    public void escalate(String reason) {
        this.escalationLevel++;
        addIssue("ESCALATED (Level " + escalationLevel + "): " + reason);
    }

    /**
     * Check if requires notification
     */
    public boolean requiresNotification() {
        // Notify 7 days before due date
        if (isApproachingDueDate() && notificationsSent == 0) {
            return true;
        }

        // Notify when overdue
        if (isOverdue() && notificationsSent < 3) {
            long daysOverdue = getDaysOverdue();
            // Send reminders every 3 days
            return daysOverdue % 3 == 0;
        }

        return false;
    }

    /**
     * Check if requires escalation
     */
    public boolean requiresEscalation() {
        // Escalate if overdue by 7 days
        if (getDaysOverdue() >= 7 && escalationLevel == 0) {
            return true;
        }

        // Escalate if breached
        if (obligationStatus == ObligationStatus.BREACHED && escalationLevel == 0) {
            return true;
        }

        // Escalate if rejected and not remediated
        if (obligationStatus == ObligationStatus.REJECTED &&
            remediationActions.isEmpty() &&
            escalationLevel < 2) {
            return true;
        }

        return false;
    }

    /**
     * Add deliverable
     */
    public void addDeliverable(String deliverable) {
        if (!deliverables.contains(deliverable)) {
            deliverables.add(deliverable);
        }
    }

    /**
     * Review compliance
     */
    public void reviewCompliance(ComplianceStatus status, List<String> findings) {
        this.complianceStatus = status;
        this.lastReviewDate = LocalDate.now();

        if (findings != null && !findings.isEmpty()) {
            findings.forEach(this::addIssue);
        }

        // Determine if at risk
        if (status == ComplianceStatus.NON_COMPLIANT && getDaysUntilDue() <= 14) {
            this.complianceStatus = ComplianceStatus.AT_RISK;
        }
    }

    /**
     * Get performance bond status
     */
    public String getPerformanceBondStatus() {
        if (!performanceBondRequired) {
            return "NOT_REQUIRED";
        }

        if (performanceBondAmount == null || performanceBondAmount.compareTo(BigDecimal.ZERO) == 0) {
            return "NOT_POSTED";
        }

        if (obligationStatus == ObligationStatus.COMPLETED ||
            obligationStatus == ObligationStatus.ACCEPTED) {
            return "RELEASED";
        }

        if (obligationStatus == ObligationStatus.BREACHED) {
            return "FORFEITED";
        }

        return "ACTIVE";
    }

    /**
     * Generate obligation summary
     */
    public Map<String, Object> generateSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("obligationId", obligationId);
        summary.put("obligationName", obligationName);
        summary.put("obligationType", obligationType);
        summary.put("responsibleParty", responsibleParty);
        summary.put("obligationStatus", obligationStatus);
        summary.put("complianceStatus", complianceStatus);
        summary.put("dueDate", dueDate);
        summary.put("completionDate", completionDate);
        summary.put("isOverdue", isOverdue());
        summary.put("daysUntilDue", getDaysUntilDue());
        summary.put("daysOverdue", getDaysOverdue());
        summary.put("isApproachingDueDate", isApproachingDueDate());
        summary.put("recurring", recurring);
        summary.put("nextDueDate", nextDueDate);
        summary.put("penaltyForBreach", penaltyForBreach);
        summary.put("calculatedPenalty", calculateBreachPenalty());
        summary.put("performanceBondStatus", getPerformanceBondStatus());
        summary.put("issuesCount", issuesIdentified.size());
        summary.put("remediationActionsCount", remediationActions.size());
        summary.put("escalationLevel", escalationLevel);
        summary.put("requiresNotification", requiresNotification());
        summary.put("requiresEscalation", requiresEscalation());
        return summary;
    }

    /**
     * Validate obligation for activation
     */
    public List<String> validateForActivation() {
        List<String> errors = new ArrayList<>();

        if (responsibleParty == null || responsibleParty.isBlank()) {
            errors.add("Responsible party is required");
        }
        if (dueDate == null) {
            errors.add("Due date is required");
        }
        if (obligationDescription == null || obligationDescription.isBlank()) {
            errors.add("Obligation description is required");
        }
        if (recurring && recurrenceFrequency == null) {
            errors.add("Recurrence frequency required for recurring obligations");
        }
        if (performanceBondRequired &&
            (performanceBondAmount == null || performanceBondAmount.compareTo(BigDecimal.ZERO) == 0)) {
            errors.add("Performance bond amount required when bond is required");
        }

        return errors;
    }

    /**
     * Get compliance score (0-100)
     */
    public int getComplianceScore() {
        if (obligationStatus == ObligationStatus.ACCEPTED) {
            return 100;
        }
        if (obligationStatus == ObligationStatus.COMPLETED) {
            return 90;
        }
        if (obligationStatus == ObligationStatus.BREACHED) {
            return 0;
        }
        if (isOverdue()) {
            long daysOverdue = getDaysOverdue();
            return Math.max(0, 50 - (int) (daysOverdue * 5));
        }
        if (obligationStatus == ObligationStatus.IN_PROGRESS) {
            return 70;
        }
        return 50; // PENDING
    }
}
