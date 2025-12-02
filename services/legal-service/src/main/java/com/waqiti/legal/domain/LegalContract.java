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
 * Legal Contract Domain Entity
 *
 * Complete production-ready contract management with:
 * - Full contract lifecycle (draft → negotiation → execution → renewal/termination)
 * - Multi-party contract support
 * - Payment and milestone tracking
 * - SLA and obligation management
 * - Automated renewal and expiration handling
 * - Compliance and risk tracking
 * - Amendment history
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-17
 */
@Entity
@Table(name = "legal_contract",
    indexes = {
        @Index(name = "idx_legal_contract_type", columnList = "contract_type"),
        @Index(name = "idx_legal_contract_status", columnList = "contract_status"),
        @Index(name = "idx_legal_contract_primary_party", columnList = "primary_party_id"),
        @Index(name = "idx_legal_contract_counterparty", columnList = "counterparty_id"),
        @Index(name = "idx_legal_contract_start_date", columnList = "contract_start_date"),
        @Index(name = "idx_legal_contract_end_date", columnList = "contract_end_date")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalContract {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "contract_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Contract ID is required")
    private String contractId;

    @Column(name = "contract_name", nullable = false)
    @NotBlank(message = "Contract name is required")
    @Size(max = 255)
    private String contractName;

    @Column(name = "contract_type", nullable = false, length = 50)
    @NotNull
    @Enumerated(EnumType.STRING)
    private ContractType contractType;

    @Column(name = "contract_status", nullable = false, length = 20)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ContractStatus contractStatus = ContractStatus.DRAFT;

    @Column(name = "contract_value", precision = 18, scale = 2)
    @DecimalMin(value = "0.00", message = "Contract value cannot be negative")
    private BigDecimal contractValue;

    @Column(name = "currency_code", length = 3)
    @Builder.Default
    private String currencyCode = "USD";

    @Type(JsonBinaryType.class)
    @Column(name = "parties", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> parties = new HashMap<>();

    @Column(name = "primary_party_id", nullable = false, length = 100)
    @NotBlank(message = "Primary party ID is required")
    private String primaryPartyId;

    @Column(name = "counterparty_id", nullable = false, length = 100)
    @NotBlank(message = "Counterparty ID is required")
    private String counterpartyId;

    @Column(name = "counterparty_name", nullable = false)
    @NotBlank
    @Size(max = 255)
    private String counterpartyName;

    @Column(name = "counterparty_type", nullable = false, length = 50)
    @NotBlank
    private String counterpartyType;

    @Column(name = "contract_start_date", nullable = false)
    @NotNull(message = "Contract start date is required")
    private LocalDate contractStartDate;

    @Column(name = "contract_end_date")
    private LocalDate contractEndDate;

    @Column(name = "contract_term_months")
    @Min(value = 1, message = "Contract term must be at least 1 month")
    private Integer contractTermMonths;

    @Type(JsonBinaryType.class)
    @Column(name = "renewal_terms", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> renewalTerms = new HashMap<>();

    @Column(name = "termination_clause", columnDefinition = "TEXT")
    private String terminationClause;

    @Column(name = "early_termination_penalty", precision = 18, scale = 2)
    private BigDecimal earlyTerminationPenalty;

    @Column(name = "notice_period_days")
    @Builder.Default
    private Integer noticePeriodDays = 30;

    @Column(name = "payment_terms", length = 100)
    private String paymentTerms;

    @Type(JsonBinaryType.class)
    @Column(name = "payment_schedule", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> paymentSchedule = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "performance_obligations", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> performanceObligations = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "deliverables", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> deliverables = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "milestones", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> milestones = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "service_level_agreements", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> serviceLevelAgreements = new HashMap<>();

    @Column(name = "warranties", columnDefinition = "TEXT")
    private String warranties;

    @Column(name = "indemnification_clause", columnDefinition = "TEXT")
    private String indemnificationClause;

    @Column(name = "liability_cap", precision = 18, scale = 2)
    private BigDecimal liabilityCap;

    @Column(name = "dispute_resolution_method", length = 100)
    private String disputeResolutionMethod;

    @Column(name = "governing_law")
    private String governingLaw;

    @Column(name = "jurisdiction", length = 100)
    private String jurisdiction;

    @Column(name = "confidentiality_clause", columnDefinition = "TEXT")
    private String confidentialityClause;

    @Column(name = "non_compete_clause", columnDefinition = "TEXT")
    private String nonCompeteClause;

    @Column(name = "intellectual_property_rights", columnDefinition = "TEXT")
    private String intellectualPropertyRights;

    @Column(name = "force_majeure_clause", columnDefinition = "TEXT")
    private String forceMajeureClause;

    @Type(JsonBinaryType.class)
    @Column(name = "amendment_history", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> amendmentHistory = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "document_ids", columnDefinition = "text[]")
    @Builder.Default
    private List<String> documentIds = new ArrayList<>();

    @Column(name = "signed")
    @Builder.Default
    private Boolean signed = false;

    @Type(JsonBinaryType.class)
    @Column(name = "signatures", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> signatures = new HashMap<>();

    @Column(name = "executed_date")
    private LocalDate executedDate;

    @Column(name = "notarized")
    @Builder.Default
    private Boolean notarized = false;

    @Type(JsonBinaryType.class)
    @Column(name = "notary_details", columnDefinition = "jsonb")
    private Map<String, Object> notaryDetails;

    @Column(name = "compliance_status", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ComplianceStatus complianceStatus = ComplianceStatus.PENDING;

    @Column(name = "last_review_date")
    private LocalDate lastReviewDate;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "risk_rating", length = 20)
    @Enumerated(EnumType.STRING)
    private RiskRating riskRating;

    @Type(JsonBinaryType.class)
    @Column(name = "risk_factors", columnDefinition = "text[]")
    @Builder.Default
    private List<String> riskFactors = new ArrayList<>();

    @Column(name = "contract_manager", length = 100)
    private String contractManager;

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
        if (contractId == null) {
            contractId = "CTR-" + UUID.randomUUID().toString();
        }
        // Calculate end date if term is provided
        if (contractEndDate == null && contractTermMonths != null && contractStartDate != null) {
            contractEndDate = contractStartDate.plusMonths(contractTermMonths);
        }
        // Schedule next review (quarterly by default)
        if (nextReviewDate == null && contractStartDate != null) {
            nextReviewDate = contractStartDate.plusMonths(3);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum ContractType {
        SERVICE_AGREEMENT,
        VENDOR_AGREEMENT,
        PARTNERSHIP_AGREEMENT,
        LICENSE_AGREEMENT,
        EMPLOYMENT_AGREEMENT,
        CONSULTING_AGREEMENT,
        NDA,
        SLA,
        PURCHASE_AGREEMENT,
        LEASE_AGREEMENT,
        LOAN_AGREEMENT,
        SUBSCRIPTION_AGREEMENT,
        FRANCHISE_AGREEMENT,
        DISTRIBUTION_AGREEMENT,
        MASTER_SERVICE_AGREEMENT,
        STATEMENT_OF_WORK,
        OTHER
    }

    public enum ContractStatus {
        DRAFT,
        UNDER_NEGOTIATION,
        PENDING_APPROVAL,
        APPROVED,
        PENDING_SIGNATURE,
        PARTIALLY_SIGNED,
        FULLY_EXECUTED,
        ACTIVE,
        SUSPENDED,
        EXPIRED,
        TERMINATED,
        RENEWED,
        BREACHED
    }

    public enum ComplianceStatus {
        PENDING,
        COMPLIANT,
        NON_COMPLIANT,
        UNDER_REVIEW,
        REMEDIATION_REQUIRED
    }

    public enum RiskRating {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    // Complete business logic methods

    /**
     * Check if contract is active
     */
    public boolean isActive() {
        return contractStatus == ContractStatus.ACTIVE &&
               !isExpired() &&
               (contractStartDate == null || !LocalDate.now().isBefore(contractStartDate));
    }

    /**
     * Check if contract has expired
     */
    public boolean isExpired() {
        return contractEndDate != null && LocalDate.now().isAfter(contractEndDate);
    }

    /**
     * Check if contract is approaching expiration (within 90 days)
     */
    public boolean isApproachingExpiration() {
        if (contractEndDate == null) {
            return false;
        }
        long daysUntilExpiration = ChronoUnit.DAYS.between(LocalDate.now(), contractEndDate);
        return daysUntilExpiration > 0 && daysUntilExpiration <= 90;
    }

    /**
     * Get days until expiration
     */
    public long getDaysUntilExpiration() {
        if (contractEndDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), contractEndDate);
    }

    /**
     * Check if contract is fully signed
     */
    public boolean isFullySigned() {
        return signed && signatures != null && signatures.size() >= 2;
    }

    /**
     * Execute contract (mark as fully executed)
     */
    public void execute(LocalDate executionDate) {
        if (!isFullySigned()) {
            throw new IllegalStateException("Contract must be fully signed before execution");
        }
        if (contractStatus != ContractStatus.PENDING_SIGNATURE &&
            contractStatus != ContractStatus.PARTIALLY_SIGNED &&
            contractStatus != ContractStatus.APPROVED) {
            throw new IllegalStateException("Contract is not in executable state");
        }
        this.executedDate = executionDate != null ? executionDate : LocalDate.now();
        this.contractStatus = ContractStatus.FULLY_EXECUTED;
        // Auto-activate if start date is today or in the past
        if (contractStartDate != null && !LocalDate.now().isBefore(contractStartDate)) {
            this.contractStatus = ContractStatus.ACTIVE;
        }
    }

    /**
     * Add signature to contract
     */
    public void addSignature(String partyId, String signerName, String signatureData, LocalDateTime signedAt) {
        if (signatures == null) {
            signatures = new HashMap<>();
        }
        Map<String, Object> signatureInfo = new HashMap<>();
        signatureInfo.put("signerName", signerName);
        signatureInfo.put("signatureData", signatureData);
        signatureInfo.put("signedAt", signedAt.toString());
        signatureInfo.put("verified", true);
        signatures.put(partyId, signatureInfo);

        // Update contract status based on signature count
        if (contractStatus == ContractStatus.PENDING_SIGNATURE) {
            contractStatus = ContractStatus.PARTIALLY_SIGNED;
        }
        if (signatures.size() >= parties.size()) {
            this.signed = true;
            contractStatus = ContractStatus.FULLY_EXECUTED;
        }
    }

    /**
     * Terminate contract
     */
    public void terminate(String reason, LocalDate terminationDate, BigDecimal penalty) {
        if (contractStatus != ContractStatus.ACTIVE &&
            contractStatus != ContractStatus.SUSPENDED) {
            throw new IllegalStateException("Can only terminate active or suspended contracts");
        }
        this.contractStatus = ContractStatus.TERMINATED;
        this.contractEndDate = terminationDate != null ? terminationDate : LocalDate.now();

        // Record termination in metadata
        Map<String, Object> terminationInfo = new HashMap<>();
        terminationInfo.put("reason", reason);
        terminationInfo.put("terminationDate", contractEndDate.toString());
        terminationInfo.put("penalty", penalty != null ? penalty.toString() : "0.00");
        amendmentHistory.add(terminationInfo);
    }

    /**
     * Renew contract
     */
    public LegalContract renew(Integer newTermMonths, BigDecimal newValue, String renewedBy) {
        if (!canBeRenewed()) {
            throw new IllegalStateException("Contract cannot be renewed in current state");
        }

        LocalDate newStartDate = contractEndDate != null ?
                contractEndDate.plusDays(1) : LocalDate.now();

        return LegalContract.builder()
                .contractName(this.contractName + " (Renewed)")
                .contractType(this.contractType)
                .contractStatus(ContractStatus.DRAFT)
                .contractValue(newValue != null ? newValue : this.contractValue)
                .currencyCode(this.currencyCode)
                .parties(new HashMap<>(this.parties))
                .primaryPartyId(this.primaryPartyId)
                .counterpartyId(this.counterpartyId)
                .counterpartyName(this.counterpartyName)
                .counterpartyType(this.counterpartyType)
                .contractStartDate(newStartDate)
                .contractTermMonths(newTermMonths != null ? newTermMonths : this.contractTermMonths)
                .renewalTerms(new HashMap<>(this.renewalTerms))
                .paymentTerms(this.paymentTerms)
                .governingLaw(this.governingLaw)
                .jurisdiction(this.jurisdiction)
                .disputeResolutionMethod(this.disputeResolutionMethod)
                .contractManager(this.contractManager)
                .createdBy(renewedBy)
                .build();
    }

    /**
     * Check if contract can be renewed
     */
    public boolean canBeRenewed() {
        return (contractStatus == ContractStatus.ACTIVE || isApproachingExpiration()) &&
               renewalTerms != null &&
               !renewalTerms.isEmpty();
    }

    /**
     * Add amendment to contract
     */
    public void addAmendment(String amendmentDescription, Map<String, Object> changes, String amendedBy) {
        Map<String, Object> amendment = new HashMap<>();
        amendment.put("description", amendmentDescription);
        amendment.put("changes", changes);
        amendment.put("amendedBy", amendedBy);
        amendment.put("amendedAt", LocalDateTime.now().toString());
        amendment.put("previousStatus", contractStatus.toString());
        amendmentHistory.add(amendment);
    }

    /**
     * Add milestone
     */
    public void addMilestone(String name, String description, LocalDate dueDate, BigDecimal value) {
        Map<String, Object> milestone = new HashMap<>();
        milestone.put("id", UUID.randomUUID().toString());
        milestone.put("name", name);
        milestone.put("description", description);
        milestone.put("dueDate", dueDate.toString());
        milestone.put("value", value != null ? value.toString() : null);
        milestone.put("status", "PENDING");
        milestone.put("createdAt", LocalDateTime.now().toString());
        milestones.add(milestone);
    }

    /**
     * Complete milestone
     */
    public void completeMilestone(String milestoneId, LocalDate completionDate) {
        milestones.stream()
                .filter(m -> milestoneId.equals(m.get("id")))
                .findFirst()
                .ifPresent(m -> {
                    m.put("status", "COMPLETED");
                    m.put("completedAt", completionDate.toString());
                });
    }

    /**
     * Add deliverable
     */
    public void addDeliverable(String name, String description, LocalDate dueDate, String owner) {
        Map<String, Object> deliverable = new HashMap<>();
        deliverable.put("id", UUID.randomUUID().toString());
        deliverable.put("name", name);
        deliverable.put("description", description);
        deliverable.put("dueDate", dueDate.toString());
        deliverable.put("owner", owner);
        deliverable.put("status", "PENDING");
        deliverables.add(deliverable);
    }

    /**
     * Calculate contract value per month
     */
    public BigDecimal getMonthlyValue() {
        if (contractValue == null || contractTermMonths == null || contractTermMonths == 0) {
            return BigDecimal.ZERO;
        }
        return contractValue.divide(BigDecimal.valueOf(contractTermMonths), 2, RoundingMode.HALF_UP);
    }

    /**
     * Validate contract for execution
     */
    public List<String> validateForExecution() {
        List<String> errors = new ArrayList<>();

        if (!isFullySigned()) {
            errors.add("Contract must be fully signed");
        }
        if (contractStartDate == null) {
            errors.add("Contract start date is required");
        }
        if (parties == null || parties.size() < 2) {
            errors.add("Contract must have at least 2 parties");
        }
        if (documentIds == null || documentIds.isEmpty()) {
            errors.add("Contract must have at least one associated document");
        }
        if (governingLaw == null || governingLaw.isBlank()) {
            errors.add("Governing law is required");
        }

        return errors;
    }

    /**
     * Check if contract needs review
     */
    public boolean needsReview() {
        return nextReviewDate != null && LocalDate.now().isAfter(nextReviewDate);
    }

    /**
     * Update compliance status
     */
    public void updateComplianceStatus(ComplianceStatus newStatus, List<String> findings) {
        this.complianceStatus = newStatus;
        this.lastReviewDate = LocalDate.now();
        this.nextReviewDate = LocalDate.now().plusMonths(3);

        if (findings != null && !findings.isEmpty()) {
            this.riskFactors.addAll(findings);
        }
    }
}
