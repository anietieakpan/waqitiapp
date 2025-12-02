package com.waqiti.legal.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
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
 * Bankruptcy Case Domain Entity
 *
 * Complete production-ready bankruptcy case tracking with:
 * - Multi-chapter bankruptcy support (7, 11, 13)
 * - Automatic stay enforcement
 * - Creditor claim management
 * - Proof of claim filing
 * - Asset and liability tracking
 * - Repayment plan management
 * - Trustee coordination
 * - Court order compliance
 * - 341 meeting tracking
 * - Discharge monitoring
 *
 * Supports:
 * - Chapter 7 (Liquidation)
 * - Chapter 11 (Reorganization)
 * - Chapter 13 (Individual Debt Adjustment)
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Entity
@Table(name = "bankruptcy_case",
    indexes = {
        @Index(name = "idx_bankruptcy_case_number", columnList = "case_number", unique = true),
        @Index(name = "idx_bankruptcy_customer", columnList = "customer_id"),
        @Index(name = "idx_bankruptcy_chapter", columnList = "bankruptcy_chapter"),
        @Index(name = "idx_bankruptcy_status", columnList = "case_status"),
        @Index(name = "idx_bankruptcy_filing_date", columnList = "filing_date"),
        @Index(name = "idx_bankruptcy_automatic_stay", columnList = "automatic_stay_active")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankruptcyCase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "bankruptcy_id", unique = true, nullable = false, length = 100)
    private String bankruptcyId;

    @Column(name = "case_number", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Case number is required")
    private String caseNumber;

    @Column(name = "customer_id", nullable = false, length = 100)
    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @Column(name = "customer_name", nullable = false)
    @NotBlank(message = "Customer name is required")
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "bankruptcy_chapter", nullable = false, length = 20)
    @NotNull(message = "Bankruptcy chapter is required")
    private BankruptcyChapter bankruptcyChapter;

    @Enumerated(EnumType.STRING)
    @Column(name = "case_status", nullable = false, length = 30)
    @NotNull(message = "Case status is required")
    private BankruptcyStatus caseStatus;

    @Column(name = "filing_date", nullable = false)
    @NotNull(message = "Filing date is required")
    private LocalDate filingDate;

    @Column(name = "court_district", nullable = false, length = 100)
    @NotBlank(message = "Court district is required")
    private String courtDistrict;

    @Column(name = "court_division", length = 100)
    private String courtDivision;

    @Column(name = "judge_name", length = 255)
    private String judgeName;

    @Column(name = "trustee_name", length = 255)
    private String trusteeName;

    @Column(name = "trustee_email", length = 255)
    private String trusteeEmail;

    @Column(name = "trustee_phone", length = 20)
    private String trusteePhone;

    @Column(name = "debtor_attorney", length = 255)
    private String debtorAttorney;

    @Column(name = "debtor_attorney_firm", length = 255)
    private String debtorAttorneyFirm;

    @Column(name = "total_debt_amount", precision = 18, scale = 2)
    private BigDecimal totalDebtAmount;

    @Column(name = "secured_debt_amount", precision = 18, scale = 2)
    private BigDecimal securedDebtAmount;

    @Column(name = "unsecured_debt_amount", precision = 18, scale = 2)
    private BigDecimal unsecuredDebtAmount;

    @Column(name = "priority_debt_amount", precision = 18, scale = 2)
    private BigDecimal priorityDebtAmount;

    @Column(name = "total_assets_value", precision = 18, scale = 2)
    private BigDecimal totalAssetsValue;

    @Column(name = "currency_code", length = 3)
    private String currencyCode = "USD";

    @Column(name = "waqiti_claim_amount", precision = 18, scale = 2)
    private BigDecimal waqitiClaimAmount;

    @Column(name = "claim_classification", length = 50)
    private String claimClassification; // SECURED, UNSECURED_PRIORITY, UNSECURED_NONPRIORITY

    @Column(name = "proof_of_claim_filed")
    private Boolean proofOfClaimFiled = false;

    @Column(name = "proof_of_claim_date")
    private LocalDate proofOfClaimDate;

    @Column(name = "proof_of_claim_bar_date")
    private LocalDate proofOfClaimBarDate;

    @Column(name = "automatic_stay_active", nullable = false)
    private Boolean automaticStayActive = true;

    @Column(name = "automatic_stay_date")
    private LocalDate automaticStayDate;

    @Column(name = "automatic_stay_lifted_date")
    private LocalDate automaticStayLiftedDate;

    @Column(name = "stay_relief_motion_filed")
    private Boolean stayReliefMotionFiled = false;

    @Column(name = "accounts_frozen")
    private Boolean accountsFrozen = false;

    @Column(name = "frozen_account_ids", columnDefinition = "TEXT[]")
    private List<String> frozenAccountIds = new ArrayList<>();

    @Column(name = "pending_transactions_cancelled")
    private Boolean pendingTransactionsCancelled = false;

    @Column(name = "cancelled_transaction_ids", columnDefinition = "TEXT[]")
    private List<String> cancelledTransactionIds = new ArrayList<>();

    @Column(name = "meeting_341_scheduled")
    private Boolean meeting341Scheduled = false;

    @Column(name = "meeting_341_date")
    private LocalDateTime meeting341Date;

    @Column(name = "meeting_341_location", columnDefinition = "TEXT")
    private String meeting341Location;

    @Column(name = "meeting_341_attended")
    private Boolean meeting341Attended = false;

    @Column(name = "reaffirmation_agreement_requested")
    private Boolean reaffirmationAgreementRequested = false;

    @Column(name = "reaffirmation_agreement_approved")
    private Boolean reaffirmationAgreementApproved = false;

    @Type(JsonBinaryType.class)
    @Column(name = "repayment_plan", columnDefinition = "jsonb")
    private Map<String, Object> repaymentPlan; // Chapter 13 only

    @Column(name = "plan_payment_amount", precision = 18, scale = 2)
    private BigDecimal planPaymentAmount; // Chapter 13 monthly payment

    @Column(name = "plan_duration_months")
    private Integer planDurationMonths; // Chapter 13 plan duration

    @Column(name = "plan_confirmed")
    private Boolean planConfirmed = false;

    @Column(name = "plan_confirmation_date")
    private LocalDate planConfirmationDate;

    @Type(JsonBinaryType.class)
    @Column(name = "exempt_assets", columnDefinition = "jsonb")
    private List<Map<String, Object>> exemptAssets; // Chapter 7 exempt property

    @Type(JsonBinaryType.class)
    @Column(name = "non_exempt_assets", columnDefinition = "jsonb")
    private List<Map<String, Object>> nonExemptAssets; // Chapter 7 liquidation assets

    @Column(name = "liquidation_proceeding")
    private Boolean liquidationProceeding = false;

    @Column(name = "discharge_granted")
    private Boolean dischargeGranted = false;

    @Column(name = "discharge_date")
    private LocalDate dischargeDate;

    @Column(name = "discharge_type", length = 50)
    private String dischargeType; // FULL, PARTIAL

    @Column(name = "dismissed")
    private Boolean dismissed = false;

    @Column(name = "dismissal_date")
    private LocalDate dismissalDate;

    @Column(name = "dismissal_reason", columnDefinition = "TEXT")
    private String dismissalReason;

    @Column(name = "converted_to_chapter", length = 20)
    private String convertedToChapter;

    @Column(name = "conversion_date")
    private LocalDate conversionDate;

    @Type(JsonBinaryType.class)
    @Column(name = "creditor_list", columnDefinition = "jsonb")
    private List<Map<String, Object>> creditorList = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "court_orders", columnDefinition = "jsonb")
    private List<Map<String, Object>> courtOrders = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "motions_filed", columnDefinition = "jsonb")
    private List<Map<String, Object>> motionsFiled = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "payments_received", columnDefinition = "jsonb")
    private List<Map<String, Object>> paymentsReceived = new ArrayList<>();

    @Column(name = "total_payments_received", precision = 18, scale = 2)
    private BigDecimal totalPaymentsReceived = BigDecimal.ZERO;

    @Column(name = "expected_recovery_percentage", precision = 5, scale = 2)
    private BigDecimal expectedRecoveryPercentage;

    @Column(name = "actual_recovery_amount", precision = 18, scale = 2)
    private BigDecimal actualRecoveryAmount = BigDecimal.ZERO;

    @Type(JsonBinaryType.class)
    @Column(name = "internal_notes", columnDefinition = "jsonb")
    private List<Map<String, Object>> internalNotes = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "audit_trail", columnDefinition = "jsonb")
    private List<Map<String, Object>> auditTrail = new ArrayList<>();

    @Column(name = "credit_reporting_flagged")
    private Boolean creditReportingFlagged = false;

    @Column(name = "credit_reporting_flag_date")
    private LocalDate creditReportingFlagDate;

    @Column(name = "all_departments_notified")
    private Boolean allDepartmentsNotified = false;

    @Column(name = "notification_sent_date")
    private LocalDateTime notificationSentDate;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "case_manager", length = 100)
    private String caseManager;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    @Column(name = "created_by", nullable = false, length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // ========== Enums ==========

    /**
     * Bankruptcy Chapter Types
     */
    public enum BankruptcyChapter {
        CHAPTER_7,      // Liquidation (individuals/businesses)
        CHAPTER_11,     // Reorganization (businesses/high-debt individuals)
        CHAPTER_12,     // Family Farmer/Fisherman
        CHAPTER_13,     // Individual Debt Adjustment (wage earners)
        CHAPTER_15      // Cross-border insolvency
    }

    /**
     * Bankruptcy Case Status
     */
    public enum BankruptcyStatus {
        FILED,                  // Petition filed
        PENDING_REVIEW,         // Initial review
        AUTOMATIC_STAY_ACTIVE,  // Stay in effect
        MEETING_341_SCHEDULED,  // Creditors meeting scheduled
        MEETING_341_COMPLETED,  // Creditors meeting held
        PLAN_SUBMITTED,         // Chapter 13 plan submitted
        PLAN_CONFIRMED,         // Chapter 13 plan approved
        LIQUIDATION,            // Chapter 7 liquidation
        REORGANIZATION,         // Chapter 11 reorganization
        PLAN_PERFORMING,        // Chapter 13 payments current
        PLAN_DELINQUENT,        // Chapter 13 payments behind
        DISCHARGE_PENDING,      // Awaiting discharge
        DISCHARGED,             // Debts discharged
        DISMISSED,              // Case dismissed
        CONVERTED,              // Converted to different chapter
        CLOSED                  // Case closed
    }

    // ========== Business Logic Methods ==========

    /**
     * Check if case is still active
     */
    public boolean isActive() {
        return !dismissed && !dischargeGranted &&
               caseStatus != BankruptcyStatus.CLOSED &&
               caseStatus != BankruptcyStatus.DISMISSED &&
               caseStatus != BankruptcyStatus.DISCHARGED;
    }

    /**
     * Check if automatic stay is currently in effect
     */
    public boolean isAutomaticStayInEffect() {
        return automaticStayActive &&
               automaticStayLiftedDate == null &&
               isActive();
    }

    /**
     * Get days since filing
     */
    public long getDaysSinceFiling() {
        return ChronoUnit.DAYS.between(filingDate, LocalDate.now());
    }

    /**
     * Check if 341 meeting is overdue
     */
    public boolean isMeeting341Overdue() {
        if (!meeting341Scheduled || meeting341Attended) {
            return false;
        }
        return meeting341Date != null && meeting341Date.isBefore(LocalDateTime.now());
    }

    /**
     * Check if proof of claim deadline is approaching
     */
    public boolean isProofOfClaimDeadlineApproaching() {
        if (proofOfClaimFiled || proofOfClaimBarDate == null) {
            return false;
        }
        long daysUntilDeadline = ChronoUnit.DAYS.between(LocalDate.now(), proofOfClaimBarDate);
        return daysUntilDeadline >= 0 && daysUntilDeadline <= 14; // 14 days warning
    }

    /**
     * Check if proof of claim is overdue
     */
    public boolean isProofOfClaimOverdue() {
        return !proofOfClaimFiled &&
               proofOfClaimBarDate != null &&
               LocalDate.now().isAfter(proofOfClaimBarDate);
    }

    /**
     * Enforce automatic stay
     */
    public void enforceAutomaticStay(LocalDate stayDate) {
        this.automaticStayActive = true;
        this.automaticStayDate = stayDate;
        this.caseStatus = BankruptcyStatus.AUTOMATIC_STAY_ACTIVE;
        addAuditEntry("AUTOMATIC_STAY_ENFORCED", "Automatic stay enforced", "SYSTEM");
    }

    /**
     * Lift automatic stay
     */
    public void liftAutomaticStay(LocalDate liftDate, String reason) {
        this.automaticStayActive = false;
        this.automaticStayLiftedDate = liftDate;
        addAuditEntry("AUTOMATIC_STAY_LIFTED", "Automatic stay lifted: " + reason, "COURT");
    }

    /**
     * Freeze customer accounts
     */
    public void freezeAccounts(List<String> accountIds) {
        this.accountsFrozen = true;
        this.frozenAccountIds = new ArrayList<>(accountIds);
        addAuditEntry("ACCOUNTS_FROZEN",
            "Froze " + accountIds.size() + " accounts", "SYSTEM");
    }

    /**
     * Cancel pending transactions
     */
    public void cancelPendingTransactions(List<String> transactionIds) {
        this.pendingTransactionsCancelled = true;
        this.cancelledTransactionIds = new ArrayList<>(transactionIds);
        addAuditEntry("TRANSACTIONS_CANCELLED",
            "Cancelled " + transactionIds.size() + " pending transactions", "SYSTEM");
    }

    /**
     * File proof of claim
     */
    public void fileProofOfClaim(BigDecimal claimAmount, String classification, LocalDate fileDate) {
        this.waqitiClaimAmount = claimAmount;
        this.claimClassification = classification;
        this.proofOfClaimFiled = true;
        this.proofOfClaimDate = fileDate;
        addAuditEntry("PROOF_OF_CLAIM_FILED",
            "Proof of claim filed: " + claimAmount, "LEGAL_TEAM");
    }

    /**
     * Schedule 341 creditors meeting
     */
    public void schedule341Meeting(LocalDateTime meetingDate, String location) {
        this.meeting341Scheduled = true;
        this.meeting341Date = meetingDate;
        this.meeting341Location = location;
        this.caseStatus = BankruptcyStatus.MEETING_341_SCHEDULED;
        addAuditEntry("MEETING_341_SCHEDULED",
            "341 meeting scheduled for " + meetingDate, "TRUSTEE");
    }

    /**
     * Record 341 meeting attendance
     */
    public void record341Attendance() {
        this.meeting341Attended = true;
        this.caseStatus = BankruptcyStatus.MEETING_341_COMPLETED;
        addAuditEntry("MEETING_341_ATTENDED", "Attended 341 creditors meeting", "WAQITI");
    }

    /**
     * Submit Chapter 13 repayment plan
     */
    public void submitRepaymentPlan(Map<String, Object> plan,
                                   BigDecimal monthlyPayment,
                                   int durationMonths) {
        if (bankruptcyChapter != BankruptcyChapter.CHAPTER_13) {
            throw new IllegalStateException("Repayment plan only applicable to Chapter 13");
        }
        this.repaymentPlan = new HashMap<>(plan);
        this.planPaymentAmount = monthlyPayment;
        this.planDurationMonths = durationMonths;
        this.caseStatus = BankruptcyStatus.PLAN_SUBMITTED;
        addAuditEntry("REPAYMENT_PLAN_SUBMITTED",
            "Submitted plan: $" + monthlyPayment + "/mo for " + durationMonths + " months",
            "DEBTOR_ATTORNEY");
    }

    /**
     * Confirm Chapter 13 plan
     */
    public void confirmPlan(LocalDate confirmationDate) {
        this.planConfirmed = true;
        this.planConfirmationDate = confirmationDate;
        this.caseStatus = BankruptcyStatus.PLAN_CONFIRMED;
        addAuditEntry("PLAN_CONFIRMED", "Repayment plan confirmed by court", "COURT");
    }

    /**
     * Record plan payment
     */
    public void recordPlanPayment(BigDecimal amount, LocalDate paymentDate, String paymentSource) {
        Map<String, Object> payment = new HashMap<>();
        payment.put("amount", amount);
        payment.put("paymentDate", paymentDate.toString());
        payment.put("paymentSource", paymentSource);
        payment.put("recordedAt", LocalDateTime.now().toString());

        paymentsReceived.add(payment);
        totalPaymentsReceived = totalPaymentsReceived.add(amount);

        addAuditEntry("PAYMENT_RECEIVED",
            "Received payment: $" + amount, "TRUSTEE");
    }

    /**
     * Identify Chapter 7 exempt assets
     */
    public void identifyExemptAssets(List<Map<String, Object>> assets) {
        if (bankruptcyChapter != BankruptcyChapter.CHAPTER_7) {
            throw new IllegalStateException("Exempt assets only applicable to Chapter 7");
        }
        this.exemptAssets = new ArrayList<>(assets);
        addAuditEntry("EXEMPT_ASSETS_IDENTIFIED",
            "Identified " + assets.size() + " exempt assets", "DEBTOR_ATTORNEY");
    }

    /**
     * Mark for liquidation
     */
    public void markForLiquidation(List<Map<String, Object>> assetsToLiquidate) {
        this.nonExemptAssets = new ArrayList<>(assetsToLiquidate);
        this.liquidationProceeding = true;
        this.caseStatus = BankruptcyStatus.LIQUIDATION;
        addAuditEntry("LIQUIDATION_INITIATED",
            "Liquidation of " + assetsToLiquidate.size() + " assets", "TRUSTEE");
    }

    /**
     * Grant discharge
     */
    public void grantDischarge(LocalDate dischargeDate, String dischargeType) {
        this.dischargeGranted = true;
        this.dischargeDate = dischargeDate;
        this.dischargeType = dischargeType;
        this.caseStatus = BankruptcyStatus.DISCHARGED;
        this.automaticStayActive = false;
        addAuditEntry("DISCHARGE_GRANTED",
            dischargeType + " discharge granted", "COURT");
    }

    /**
     * Dismiss case
     */
    public void dismiss(LocalDate dismissalDate, String reason) {
        this.dismissed = true;
        this.dismissalDate = dismissalDate;
        this.dismissalReason = reason;
        this.caseStatus = BankruptcyStatus.DISMISSED;
        this.automaticStayActive = false;
        addAuditEntry("CASE_DISMISSED", "Case dismissed: " + reason, "COURT");
    }

    /**
     * Convert to different chapter
     */
    public void convertToChapter(BankruptcyChapter newChapter, LocalDate conversionDate) {
        this.convertedToChapter = newChapter.name();
        this.conversionDate = conversionDate;
        this.bankruptcyChapter = newChapter;
        this.caseStatus = BankruptcyStatus.CONVERTED;
        addAuditEntry("CHAPTER_CONVERTED",
            "Case converted to " + newChapter, "DEBTOR_ATTORNEY");
    }

    /**
     * Add creditor to list
     */
    public void addCreditor(String creditorName, String creditorType, BigDecimal claimAmount) {
        Map<String, Object> creditor = new HashMap<>();
        creditor.put("creditorName", creditorName);
        creditor.put("creditorType", creditorType);
        creditor.put("claimAmount", claimAmount);
        creditor.put("addedAt", LocalDateTime.now().toString());

        creditorList.add(creditor);
    }

    /**
     * Add court order
     */
    public void addCourtOrder(String orderType, String orderDescription, LocalDate orderDate) {
        Map<String, Object> order = new HashMap<>();
        order.put("orderType", orderType);
        order.put("description", orderDescription);
        order.put("orderDate", orderDate.toString());
        order.put("recordedAt", LocalDateTime.now().toString());

        courtOrders.add(order);
        addAuditEntry("COURT_ORDER_RECEIVED", orderType + ": " + orderDescription, "COURT");
    }

    /**
     * Flag credit reporting agencies
     */
    public void flagCreditReporting(LocalDate flagDate) {
        this.creditReportingFlagged = true;
        this.creditReportingFlagDate = flagDate;
        addAuditEntry("CREDIT_REPORTING_FLAGGED",
            "Credit bureaus notified of bankruptcy", "SYSTEM");
    }

    /**
     * Mark all departments notified
     */
    public void markDepartmentsNotified(LocalDateTime notificationDate) {
        this.allDepartmentsNotified = true;
        this.notificationSentDate = notificationDate;
        addAuditEntry("DEPARTMENTS_NOTIFIED",
            "All internal departments notified", "SYSTEM");
    }

    /**
     * Add internal note
     */
    public void addInternalNote(String note, String author) {
        Map<String, Object> noteEntry = new HashMap<>();
        noteEntry.put("note", note);
        noteEntry.put("author", author);
        noteEntry.put("timestamp", LocalDateTime.now().toString());

        internalNotes.add(noteEntry);
    }

    /**
     * Add audit trail entry
     */
    private void addAuditEntry(String action, String description, String actor) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("action", action);
        entry.put("description", description);
        entry.put("actor", actor);
        entry.put("timestamp", LocalDateTime.now().toString());

        auditTrail.add(entry);
    }

    /**
     * Assign case to team member
     */
    public void assignTo(String userId) {
        this.assignedTo = userId;
        addAuditEntry("CASE_ASSIGNED", "Case assigned to " + userId, "SYSTEM");
    }

    /**
     * Get case summary
     */
    public Map<String, Object> getCaseSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("bankruptcyId", bankruptcyId);
        summary.put("caseNumber", caseNumber);
        summary.put("chapter", bankruptcyChapter.name());
        summary.put("status", caseStatus.name());
        summary.put("filingDate", filingDate.toString());
        summary.put("daysSinceFiling", getDaysSinceFiling());
        summary.put("automaticStayActive", isAutomaticStayInEffect());
        summary.put("totalDebt", totalDebtAmount);
        summary.put("waqitiClaim", waqitiClaimAmount);
        summary.put("proofOfClaimFiled", proofOfClaimFiled);
        summary.put("dischargeGranted", dischargeGranted);
        summary.put("dismissed", dismissed);
        return summary;
    }

    /**
     * Calculate expected recovery
     */
    public BigDecimal calculateExpectedRecovery() {
        if (waqitiClaimAmount == null || expectedRecoveryPercentage == null) {
            return BigDecimal.ZERO;
        }
        return waqitiClaimAmount.multiply(expectedRecoveryPercentage)
                               .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.FLOOR); // Recovery rounds down (conservative)
    }

    // ========== Lifecycle Hooks ==========

    @PrePersist
    protected void onCreate() {
        if (bankruptcyId == null || bankruptcyId.isEmpty()) {
            bankruptcyId = "BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        // Set initial status if not set
        if (caseStatus == null) {
            caseStatus = BankruptcyStatus.FILED;
        }

        // Enforce automatic stay on creation
        if (automaticStayDate == null) {
            automaticStayDate = LocalDate.now();
        }

        // Initialize collections
        if (auditTrail == null) {
            auditTrail = new ArrayList<>();
        }
        if (internalNotes == null) {
            internalNotes = new ArrayList<>();
        }
        if (creditorList == null) {
            creditorList = new ArrayList<>();
        }

        addAuditEntry("CASE_CREATED",
            "Bankruptcy case created: " + bankruptcyChapter,
            createdBy != null ? createdBy : "SYSTEM");
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
