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
 * Legal Case Domain Entity
 *
 * Complete production-ready litigation management with:
 * - Full case lifecycle (filing → discovery → trial → judgment → appeal)
 * - Multi-party case management
 * - Hearing and deadline tracking
 * - Evidence and witness management
 * - Settlement negotiation tracking
 * - Legal fee budgeting and tracking
 * - Outside counsel coordination
 * - Risk assessment and case strategy
 * - Court document management
 * - Appeal workflow
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Entity
@Table(name = "legal_case",
    indexes = {
        @Index(name = "idx_legal_case_number", columnList = "case_number"),
        @Index(name = "idx_legal_case_type", columnList = "case_type"),
        @Index(name = "idx_legal_case_status", columnList = "case_status"),
        @Index(name = "idx_legal_case_filed_date", columnList = "filed_date"),
        @Index(name = "idx_legal_case_manager", columnList = "case_manager")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "case_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Case ID is required")
    private String caseId;

    @Column(name = "case_number", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Case number is required")
    private String caseNumber;

    @Column(name = "case_name", nullable = false)
    @NotBlank(message = "Case name is required")
    private String caseName;

    @Column(name = "case_type", nullable = false, length = 50)
    @NotNull
    @Enumerated(EnumType.STRING)
    private CaseType caseType;

    @Column(name = "case_status", nullable = false, length = 20)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CaseStatus caseStatus = CaseStatus.OPEN;

    @Column(name = "case_priority", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private CasePriority casePriority = CasePriority.NORMAL;

    @Column(name = "filed_date", nullable = false)
    @NotNull(message = "Filed date is required")
    private LocalDate filedDate;

    @Column(name = "court_name")
    private String courtName;

    @Column(name = "jurisdiction", nullable = false, length = 100)
    @NotBlank(message = "Jurisdiction is required")
    private String jurisdiction;

    @Column(name = "judge_name")
    private String judgeName;

    @Column(name = "plaintiff_name", nullable = false)
    @NotBlank(message = "Plaintiff name is required")
    private String plaintiffName;

    @Column(name = "plaintiff_representation")
    private String plaintiffRepresentation;

    @Column(name = "defendant_name", nullable = false)
    @NotBlank(message = "Defendant name is required")
    private String defendantName;

    @Column(name = "defendant_representation")
    private String defendantRepresentation;

    @Column(name = "case_description", columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "Case description is required")
    private String caseDescription;

    @Type(JsonBinaryType.class)
    @Column(name = "legal_claims", columnDefinition = "text[]")
    @Builder.Default
    private List<String> legalClaims = new ArrayList<>();

    @Column(name = "relief_sought", columnDefinition = "TEXT")
    private String reliefSought;

    @Column(name = "amount_in_dispute", precision = 18, scale = 2)
    @DecimalMin(value = "0.00", message = "Amount cannot be negative")
    private BigDecimal amountInDispute;

    @Column(name = "currency_code", length = 3)
    @Builder.Default
    private String currencyCode = "USD";

    @Type(JsonBinaryType.class)
    @Column(name = "hearing_dates", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> hearingDates = new ArrayList<>();

    @Column(name = "next_hearing_date")
    private LocalDate nextHearingDate;

    @Column(name = "trial_date")
    private LocalDate trialDate;

    @Type(JsonBinaryType.class)
    @Column(name = "deadlines", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> deadlines = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "case_milestones", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> caseMilestones = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "case_documents", columnDefinition = "text[]")
    @Builder.Default
    private List<String> caseDocuments = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "evidence_submitted", columnDefinition = "text[]")
    @Builder.Default
    private List<String> evidenceSubmitted = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "witness_list", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> witnessList = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "expert_witnesses", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> expertWitnesses = new ArrayList<>();

    @Column(name = "settlement_discussions")
    @Builder.Default
    private Boolean settlementDiscussions = false;

    @Column(name = "settlement_amount", precision = 18, scale = 2)
    private BigDecimal settlementAmount;

    @Column(name = "settlement_terms", columnDefinition = "TEXT")
    private String settlementTerms;

    @Column(name = "outcome", length = 100)
    private String outcome;

    @Column(name = "judgment_date")
    private LocalDate judgmentDate;

    @Column(name = "judgment_summary", columnDefinition = "TEXT")
    private String judgmentSummary;

    @Column(name = "appeal_filed")
    @Builder.Default
    private Boolean appealFiled = false;

    @Column(name = "appeal_deadline")
    private LocalDate appealDeadline;

    @Column(name = "case_manager", nullable = false, length = 100)
    @NotBlank(message = "Case manager is required")
    private String caseManager;

    @Column(name = "outside_counsel")
    private String outsideCounsel;

    @Column(name = "legal_fees_budget", precision = 18, scale = 2)
    private BigDecimal legalFeesBudget;

    @Column(name = "legal_fees_incurred", precision = 18, scale = 2)
    @Builder.Default
    private BigDecimal legalFeesIncurred = BigDecimal.ZERO;

    @Column(name = "risk_assessment", columnDefinition = "TEXT")
    private String riskAssessment;

    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

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
        if (caseId == null) {
            caseId = "CASE-" + UUID.randomUUID().toString();
        }
        if (filedDate == null) {
            filedDate = LocalDate.now();
        }
        // Add initial milestone
        addMilestone("CASE_FILED", "Case filed with court", filedDate, "COMPLETED");
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum CaseType {
        CIVIL_LITIGATION,
        CRIMINAL_PROSECUTION,
        CRIMINAL_DEFENSE,
        INTELLECTUAL_PROPERTY,
        EMPLOYMENT_DISPUTE,
        CONTRACT_DISPUTE,
        BREACH_OF_CONTRACT,
        NEGLIGENCE,
        PERSONAL_INJURY,
        PRODUCT_LIABILITY,
        CLASS_ACTION,
        ARBITRATION,
        ADMINISTRATIVE_HEARING,
        REGULATORY_ENFORCEMENT,
        APPEAL,
        BANKRUPTCY,
        REAL_ESTATE_DISPUTE,
        FAMILY_LAW,
        SECURITIES_LITIGATION,
        ANTITRUST,
        OTHER
    }

    public enum CaseStatus {
        OPEN,
        ACTIVE,
        DISCOVERY,
        MOTION_PRACTICE,
        SETTLEMENT_NEGOTIATIONS,
        TRIAL_SCHEDULED,
        IN_TRIAL,
        AWAITING_JUDGMENT,
        JUDGMENT_ENTERED,
        SETTLED,
        DISMISSED,
        CLOSED,
        APPEALED,
        WON,
        LOST
    }

    public enum CasePriority {
        CRITICAL,
        HIGH,
        NORMAL,
        LOW
    }

    // Complete business logic methods

    /**
     * Check if case is active
     */
    public boolean isActive() {
        return caseStatus == CaseStatus.OPEN ||
               caseStatus == CaseStatus.ACTIVE ||
               caseStatus == CaseStatus.DISCOVERY ||
               caseStatus == CaseStatus.MOTION_PRACTICE ||
               caseStatus == CaseStatus.SETTLEMENT_NEGOTIATIONS ||
               caseStatus == CaseStatus.TRIAL_SCHEDULED ||
               caseStatus == CaseStatus.IN_TRIAL ||
               caseStatus == CaseStatus.AWAITING_JUDGMENT;
    }

    /**
     * Get case age in days
     */
    public long getCaseAgeDays() {
        return ChronoUnit.DAYS.between(filedDate, LocalDate.now());
    }

    /**
     * Get days until next hearing
     */
    public long getDaysUntilNextHearing() {
        if (nextHearingDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), nextHearingDate);
    }

    /**
     * Add hearing
     */
    public void addHearing(String hearingType, LocalDate hearingDate, String location, String purpose) {
        Map<String, Object> hearing = new HashMap<>();
        hearing.put("hearingId", UUID.randomUUID().toString());
        hearing.put("hearingType", hearingType);
        hearing.put("hearingDate", hearingDate.toString());
        hearing.put("location", location);
        hearing.put("purpose", purpose);
        hearing.put("status", "SCHEDULED");
        hearing.put("createdAt", LocalDateTime.now().toString());
        hearingDates.add(hearing);

        // Update next hearing date
        if (nextHearingDate == null || hearingDate.isBefore(nextHearingDate)) {
            nextHearingDate = hearingDate;
        }
    }

    /**
     * Complete hearing
     */
    public void completeHearing(String hearingId, String outcome, String notes) {
        hearingDates.stream()
                .filter(h -> hearingId.equals(h.get("hearingId")))
                .findFirst()
                .ifPresent(h -> {
                    h.put("status", "COMPLETED");
                    h.put("outcome", outcome);
                    h.put("notes", notes);
                    h.put("completedAt", LocalDateTime.now().toString());
                });

        // Update next hearing date
        updateNextHearingDate();
    }

    /**
     * Update next hearing date from scheduled hearings
     */
    private void updateNextHearingDate() {
        nextHearingDate = hearingDates.stream()
                .filter(h -> "SCHEDULED".equals(h.get("status")))
                .map(h -> LocalDate.parse((String) h.get("hearingDate")))
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    /**
     * Add deadline
     */
    public void addDeadline(String deadlineType, LocalDate dueDate, String description, String priority) {
        Map<String, Object> deadline = new HashMap<>();
        deadline.put("deadlineId", UUID.randomUUID().toString());
        deadline.put("deadlineType", deadlineType);
        deadline.put("dueDate", dueDate.toString());
        deadline.put("description", description);
        deadline.put("priority", priority);
        deadline.put("status", "PENDING");
        deadline.put("createdAt", LocalDateTime.now().toString());
        deadlines.add(deadline);
    }

    /**
     * Meet deadline
     */
    public void meetDeadline(String deadlineId, String notes) {
        deadlines.stream()
                .filter(d -> deadlineId.equals(d.get("deadlineId")))
                .findFirst()
                .ifPresent(d -> {
                    d.put("status", "MET");
                    d.put("completedAt", LocalDateTime.now().toString());
                    d.put("notes", notes);
                });
    }

    /**
     * Get overdue deadlines
     */
    public List<Map<String, Object>> getOverdueDeadlines() {
        return deadlines.stream()
                .filter(d -> "PENDING".equals(d.get("status")))
                .filter(d -> {
                    LocalDate dueDate = LocalDate.parse((String) d.get("dueDate"));
                    return LocalDate.now().isAfter(dueDate);
                })
                .toList();
    }

    /**
     * Add milestone
     */
    public void addMilestone(String milestoneType, String description, LocalDate date, String status) {
        Map<String, Object> milestone = new HashMap<>();
        milestone.put("milestoneId", UUID.randomUUID().toString());
        milestone.put("milestoneType", milestoneType);
        milestone.put("description", description);
        milestone.put("date", date.toString());
        milestone.put("status", status);
        milestone.put("createdAt", LocalDateTime.now().toString());
        caseMilestones.add(milestone);
    }

    /**
     * Add witness
     */
    public void addWitness(String witnessName, String witnessType, String contactInfo,
                           String testimony, boolean expert) {
        Map<String, Object> witness = new HashMap<>();
        witness.put("witnessId", UUID.randomUUID().toString());
        witness.put("name", witnessName);
        witness.put("type", witnessType);
        witness.put("contactInfo", contactInfo);
        witness.put("testimony", testimony);
        witness.put("isExpert", expert);
        witness.put("addedAt", LocalDateTime.now().toString());

        if (expert) {
            expertWitnesses.add(witness);
        } else {
            witnessList.add(witness);
        }
    }

    /**
     * Add evidence
     */
    public void addEvidence(String evidenceId) {
        if (!evidenceSubmitted.contains(evidenceId)) {
            evidenceSubmitted.add(evidenceId);
        }
    }

    /**
     * Add case document
     */
    public void addDocument(String documentId) {
        if (!caseDocuments.contains(documentId)) {
            caseDocuments.add(documentId);
        }
    }

    /**
     * Start settlement discussions
     */
    public void startSettlementDiscussions() {
        if (!isActive()) {
            throw new IllegalStateException("Cannot start settlement for inactive case");
        }
        this.settlementDiscussions = true;
        this.caseStatus = CaseStatus.SETTLEMENT_NEGOTIATIONS;
        addMilestone("SETTLEMENT_STARTED", "Settlement negotiations initiated",
                LocalDate.now(), "IN_PROGRESS");
    }

    /**
     * Record settlement offer
     */
    public void recordSettlementOffer(BigDecimal amount, String terms) {
        if (!settlementDiscussions) {
            startSettlementDiscussions();
        }
        this.settlementAmount = amount;
        this.settlementTerms = terms;
    }

    /**
     * Settle case
     */
    public void settle(BigDecimal finalAmount, String finalTerms) {
        if (!settlementDiscussions) {
            throw new IllegalStateException("Settlement discussions not initiated");
        }
        this.settlementAmount = finalAmount;
        this.settlementTerms = finalTerms;
        this.caseStatus = CaseStatus.SETTLED;
        this.outcome = "SETTLED";
        addMilestone("CASE_SETTLED", "Case settled for " + finalAmount + " " + currencyCode,
                LocalDate.now(), "COMPLETED");
    }

    /**
     * Schedule trial
     */
    public void scheduleTrial(LocalDate date) {
        this.trialDate = date;
        this.caseStatus = CaseStatus.TRIAL_SCHEDULED;
        addMilestone("TRIAL_SCHEDULED", "Trial scheduled", date, "SCHEDULED");
    }

    /**
     * Start trial
     */
    public void startTrial() {
        if (trialDate == null) {
            throw new IllegalStateException("Trial date must be set before starting trial");
        }
        this.caseStatus = CaseStatus.IN_TRIAL;
        addMilestone("TRIAL_STARTED", "Trial commenced", LocalDate.now(), "IN_PROGRESS");
    }

    /**
     * Enter judgment
     */
    public void enterJudgment(String outcomeDescription, LocalDate judgmentDateValue,
                              String summary, boolean favorable) {
        this.outcome = outcomeDescription;
        this.judgmentDate = judgmentDateValue;
        this.judgmentSummary = summary;
        this.caseStatus = CaseStatus.JUDGMENT_ENTERED;

        if (favorable) {
            this.caseStatus = CaseStatus.WON;
        } else {
            this.caseStatus = CaseStatus.LOST;
        }

        // Calculate appeal deadline (typically 30 days)
        this.appealDeadline = judgmentDateValue.plusDays(30);

        addMilestone("JUDGMENT_ENTERED", "Judgment: " + outcomeDescription,
                judgmentDateValue, "COMPLETED");
    }

    /**
     * File appeal
     */
    public void fileAppeal(String appealReason) {
        if (judgmentDate == null) {
            throw new IllegalStateException("Cannot appeal before judgment");
        }
        if (LocalDate.now().isAfter(appealDeadline)) {
            throw new IllegalStateException("Appeal deadline has passed");
        }
        this.appealFiled = true;
        this.caseStatus = CaseStatus.APPEALED;
        this.internalNotes = (internalNotes != null ? internalNotes + "\n\n" : "") +
                "APPEAL FILED: " + appealReason + " on " + LocalDate.now();
        addMilestone("APPEAL_FILED", "Appeal filed: " + appealReason, LocalDate.now(), "FILED");
    }

    /**
     * Dismiss case
     */
    public void dismiss(String dismissalReason) {
        this.caseStatus = CaseStatus.DISMISSED;
        this.outcome = "DISMISSED: " + dismissalReason;
        addMilestone("CASE_DISMISSED", dismissalReason, LocalDate.now(), "COMPLETED");
    }

    /**
     * Close case
     */
    public void close(String closureReason) {
        if (isActive()) {
            throw new IllegalStateException("Cannot close active case - resolve or dismiss first");
        }
        this.caseStatus = CaseStatus.CLOSED;
        this.internalNotes = (internalNotes != null ? internalNotes + "\n\n" : "") +
                "CASE CLOSED: " + closureReason + " on " + LocalDate.now();
        addMilestone("CASE_CLOSED", closureReason, LocalDate.now(), "COMPLETED");
    }

    /**
     * Track legal fees
     */
    public void addLegalFees(BigDecimal amount, String description) {
        if (legalFeesIncurred == null) {
            legalFeesIncurred = BigDecimal.ZERO;
        }
        legalFeesIncurred = legalFeesIncurred.add(amount);

        this.internalNotes = (internalNotes != null ? internalNotes + "\n\n" : "") +
                "LEGAL FEES: " + amount + " " + currencyCode + " - " + description +
                " (Total: " + legalFeesIncurred + ") on " + LocalDate.now();
    }

    /**
     * Check if budget is exceeded
     */
    public boolean isBudgetExceeded() {
        if (legalFeesBudget == null || legalFeesIncurred == null) {
            return false;
        }
        return legalFeesIncurred.compareTo(legalFeesBudget) > 0;
    }

    /**
     * Get budget utilization percentage
     */
    public int getBudgetUtilizationPercentage() {
        if (legalFeesBudget == null || legalFeesBudget.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        if (legalFeesIncurred == null) {
            return 0;
        }
        BigDecimal percentage = legalFeesIncurred
                .multiply(BigDecimal.valueOf(100))
                .divide(legalFeesBudget, 0, RoundingMode.HALF_UP);
        return percentage.intValue();
    }

    /**
     * Engage outside counsel
     */
    public void engageOutsideCounsel(String counselName, BigDecimal budget) {
        this.outsideCounsel = counselName;
        if (budget != null) {
            this.legalFeesBudget = budget;
        }
        this.internalNotes = (internalNotes != null ? internalNotes + "\n\n" : "") +
                "OUTSIDE COUNSEL ENGAGED: " + counselName + " on " + LocalDate.now();
    }

    /**
     * Update risk assessment
     */
    public void updateRiskAssessment(String assessment) {
        this.riskAssessment = assessment;
    }

    /**
     * Check if requires urgent attention
     */
    public boolean requiresUrgentAttention() {
        // Urgent if hearing within 7 days
        if (getDaysUntilNextHearing() <= 7 && getDaysUntilNextHearing() > 0) {
            return true;
        }

        // Urgent if has overdue deadlines
        if (!getOverdueDeadlines().isEmpty()) {
            return true;
        }

        // Urgent if trial within 14 days
        if (trialDate != null && ChronoUnit.DAYS.between(LocalDate.now(), trialDate) <= 14) {
            return true;
        }

        // Urgent if appeal deadline approaching (within 7 days)
        if (appealDeadline != null && !appealFiled &&
            ChronoUnit.DAYS.between(LocalDate.now(), appealDeadline) <= 7) {
            return true;
        }

        return false;
    }

    /**
     * Generate case summary
     */
    public Map<String, Object> generateCaseSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("caseId", caseId);
        summary.put("caseNumber", caseNumber);
        summary.put("caseName", caseName);
        summary.put("caseType", caseType);
        summary.put("caseStatus", caseStatus);
        summary.put("plaintiff", plaintiffName);
        summary.put("defendant", defendantName);
        summary.put("jurisdiction", jurisdiction);
        summary.put("filedDate", filedDate);
        summary.put("caseAgeDays", getCaseAgeDays());
        summary.put("amountInDispute", amountInDispute);
        summary.put("nextHearingDate", nextHearingDate);
        summary.put("trialDate", trialDate);
        summary.put("isActive", isActive());
        summary.put("requiresUrgentAttention", requiresUrgentAttention());
        summary.put("legalFeesIncurred", legalFeesIncurred);
        summary.put("budgetUtilization", getBudgetUtilizationPercentage());
        summary.put("isBudgetExceeded", isBudgetExceeded());
        summary.put("witnessCount", witnessList.size());
        summary.put("expertWitnessCount", expertWitnesses.size());
        summary.put("evidenceCount", evidenceSubmitted.size());
        summary.put("documentCount", caseDocuments.size());
        summary.put("settlementDiscussions", settlementDiscussions);
        summary.put("outcome", outcome);
        return summary;
    }

    /**
     * Validate case for trial
     */
    public List<String> validateForTrial() {
        List<String> errors = new ArrayList<>();

        if (witnessList.isEmpty() && expertWitnesses.isEmpty()) {
            errors.add("At least one witness must be identified");
        }
        if (evidenceSubmitted.isEmpty()) {
            errors.add("Evidence must be submitted before trial");
        }
        if (caseDocuments.isEmpty()) {
            errors.add("Case documents must be filed");
        }
        if (legalClaims.isEmpty()) {
            errors.add("Legal claims must be specified");
        }

        return errors;
    }
}
