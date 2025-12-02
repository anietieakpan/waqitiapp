package com.waqiti.legal.domain;

import jakarta.persistence.*;
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
 * Subpoena Domain Entity
 *
 * Complete production-ready subpoena processing with:
 * - RFPA (Right to Financial Privacy Act) compliance
 * - 12-step zero-tolerance processing workflow
 * - Customer notification tracking
 * - Document production with Bates numbering
 * - Compliance certification
 * - Legal hold management
 * - Audit trail for court proceedings
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-17
 */
@Entity
@Table(name = "subpoena",
    indexes = {
        @Index(name = "idx_subpoena_customer", columnList = "customer_id"),
        @Index(name = "idx_subpoena_case_number", columnList = "case_number"),
        @Index(name = "idx_subpoena_status", columnList = "status"),
        @Index(name = "idx_subpoena_deadline", columnList = "response_deadline"),
        @Index(name = "idx_subpoena_issuing_court", columnList = "issuing_court")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subpoena {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subpoena_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Subpoena ID is required")
    private String subpoenaId;

    @Column(name = "customer_id", nullable = false, length = 100)
    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @Column(name = "case_number", nullable = false, length = 100)
    @NotBlank(message = "Case number is required")
    private String caseNumber;

    @Column(name = "issuing_court", nullable = false)
    @NotBlank(message = "Issuing court is required")
    private String issuingCourt;

    @Column(name = "issuing_party")
    private String issuingParty;

    @Column(name = "issuance_date", nullable = false)
    @NotNull(message = "Issuance date is required")
    private LocalDate issuanceDate;

    @Column(name = "response_deadline", nullable = false)
    @NotNull(message = "Response deadline is required")
    private LocalDate responseDeadline;

    @Column(name = "subpoena_type", nullable = false, length = 50)
    @NotNull
    @Enumerated(EnumType.STRING)
    private SubpoenaType subpoenaType;

    @Column(name = "status", nullable = false, length = 20)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SubpoenaStatus status = SubpoenaStatus.RECEIVED;

    @Column(name = "requested_records", columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "Requested records description is required")
    private String requestedRecords;

    @Column(name = "scope_description", columnDefinition = "TEXT")
    private String scopeDescription;

    @Column(name = "time_period_start")
    private LocalDate timePeriodStart;

    @Column(name = "time_period_end")
    private LocalDate timePeriodEnd;

    @Column(name = "validated")
    @Builder.Default
    private Boolean validated = false;

    @Column(name = "validation_date")
    private LocalDateTime validationDate;

    @Column(name = "validated_by", length = 100)
    private String validatedBy;

    @Column(name = "invalid_reason", columnDefinition = "TEXT")
    private String invalidReason;

    @Column(name = "customer_notification_required")
    @Builder.Default
    private Boolean customerNotificationRequired = true;

    @Column(name = "customer_notified")
    @Builder.Default
    private Boolean customerNotified = false;

    @Column(name = "customer_notification_date")
    private LocalDateTime customerNotificationDate;

    @Column(name = "customer_notification_method", length = 50)
    private String customerNotificationMethod;

    @Column(name = "rfpa_exception_applied")
    @Builder.Default
    private Boolean rfpaExceptionApplied = false;

    @Column(name = "rfpa_exception_type", length = 100)
    private String rfpaExceptionType;

    @Type(JsonBinaryType.class)
    @Column(name = "records_gathered", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> recordsGathered = new ArrayList<>();

    @Column(name = "total_records_count")
    @Builder.Default
    private Integer totalRecordsCount = 0;

    @Column(name = "redaction_performed")
    @Builder.Default
    private Boolean redactionPerformed = false;

    @Column(name = "privileged_records_count")
    @Builder.Default
    private Integer privilegedRecordsCount = 0;

    @Column(name = "document_production_prepared")
    @Builder.Default
    private Boolean documentProductionPrepared = false;

    @Column(name = "bates_numbering_range")
    private String batesNumberingRange;

    @Column(name = "production_start_bates")
    private String productionStartBates;

    @Column(name = "production_end_bates")
    private String productionEndBates;

    @Column(name = "production_format", length = 50)
    @Builder.Default
    private String productionFormat = "PDF";

    @Column(name = "records_certified")
    @Builder.Default
    private Boolean recordsCertified = false;

    @Column(name = "certification_date")
    private LocalDateTime certificationDate;

    @Column(name = "certified_by", length = 100)
    private String certifiedBy;

    @Column(name = "certification_statement", columnDefinition = "TEXT")
    private String certificationStatement;

    @Column(name = "submitted_to_court")
    @Builder.Default
    private Boolean submittedToCourt = false;

    @Column(name = "submission_date")
    private LocalDateTime submissionDate;

    @Column(name = "submission_method", length = 50)
    private String submissionMethod;

    @Column(name = "submission_tracking_number")
    private String submissionTrackingNumber;

    @Column(name = "compliance_certificate_filed")
    @Builder.Default
    private Boolean complianceCertificateFiled = false;

    @Column(name = "compliance_certificate_date")
    private LocalDateTime complianceCertificateDate;

    @Column(name = "compliance_certificate_path")
    private String complianceCertificatePath;

    @Column(name = "legal_hold_applied")
    @Builder.Default
    private Boolean legalHoldApplied = false;

    @Column(name = "legal_hold_id")
    private String legalHoldId;

    @Column(name = "outside_counsel_engaged")
    @Builder.Default
    private Boolean outsideCounselEngaged = false;

    @Column(name = "outside_counsel_name")
    private String outsideCounselName;

    @Column(name = "escalated_to_legal_counsel")
    @Builder.Default
    private Boolean escalatedToLegalCounsel = false;

    @Column(name = "escalation_reason", columnDefinition = "TEXT")
    private String escalationReason;

    @Column(name = "escalation_date")
    private LocalDateTime escalationDate;

    @Type(JsonBinaryType.class)
    @Column(name = "processing_notes", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> processingNotes = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "audit_trail", columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, Object>> auditTrail = new ArrayList<>();

    @Column(name = "completed")
    @Builder.Default
    private Boolean completed = false;

    @Column(name = "completion_date")
    private LocalDateTime completionDate;

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "priority_level", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PriorityLevel priorityLevel = PriorityLevel.HIGH;

    @Column(name = "created_by", nullable = false, length = 100)
    @NotBlank
    @Builder.Default
    private String createdBy = "SYSTEM";

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
        if (subpoenaId == null) {
            subpoenaId = "SUB-" + UUID.randomUUID().toString();
        }
        // Apply legal hold immediately upon creation
        if (!legalHoldApplied) {
            legalHoldApplied = true;
            legalHoldId = "LH-" + subpoenaId;
            addAuditEntry("LEGAL_HOLD_APPLIED", "Automatic legal hold applied upon subpoena receipt", "SYSTEM");
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum SubpoenaType {
        CRIMINAL_SUBPOENA,
        CIVIL_SUBPOENA,
        ADMINISTRATIVE_SUBPOENA,
        GRAND_JURY_SUBPOENA,
        COURT_ORDER,
        SEARCH_WARRANT,
        SUMMONS,
        DEPOSITION_SUBPOENA
    }

    public enum SubpoenaStatus {
        RECEIVED,
        VALIDATING,
        INVALID,
        VALIDATED,
        CUSTOMER_NOTIFICATION_PENDING,
        CUSTOMER_NOTIFIED,
        GATHERING_RECORDS,
        RECORDS_GATHERED,
        REDACTING,
        PREPARING_PRODUCTION,
        PRODUCTION_READY,
        CERTIFYING,
        CERTIFIED,
        SUBMITTED,
        COMPLETED,
        ESCALATED
    }

    public enum PriorityLevel {
        CRITICAL,
        HIGH,
        NORMAL,
        LOW
    }

    // Complete business logic methods

    /**
     * Check if subpoena is overdue
     */
    public boolean isOverdue() {
        return !completed && LocalDate.now().isAfter(responseDeadline);
    }

    /**
     * Get days until deadline
     */
    public long getDaysUntilDeadline() {
        return ChronoUnit.DAYS.between(LocalDate.now(), responseDeadline);
    }

    /**
     * Check if approaching deadline (within 5 days)
     */
    public boolean isApproachingDeadline() {
        long days = getDaysUntilDeadline();
        return days > 0 && days <= 5;
    }

    /**
     * Validate subpoena authenticity and jurisdiction
     */
    public void validate(String validatorId, boolean isValid, String reason) {
        this.validated = isValid;
        this.validationDate = LocalDateTime.now();
        this.validatedBy = validatorId;

        if (isValid) {
            this.status = SubpoenaStatus.VALIDATED;
            addAuditEntry("VALIDATED", "Subpoena validated successfully", validatorId);
        } else {
            this.status = SubpoenaStatus.INVALID;
            this.invalidReason = reason;
            addAuditEntry("INVALID", "Subpoena marked as invalid: " + reason, validatorId);
        }
    }

    /**
     * Mark customer as notified per RFPA requirements
     */
    public void markCustomerNotified(String method) {
        if (!customerNotificationRequired) {
            throw new IllegalStateException("Customer notification not required for this subpoena type");
        }
        this.customerNotified = true;
        this.customerNotificationDate = LocalDateTime.now();
        this.customerNotificationMethod = method;
        this.status = SubpoenaStatus.CUSTOMER_NOTIFIED;
        addAuditEntry("CUSTOMER_NOTIFIED", "Customer notified via " + method, createdBy);
    }

    /**
     * Apply RFPA exception (law enforcement, etc.)
     */
    public void applyRfpaException(String exceptionType, String reason) {
        this.rfpaExceptionApplied = true;
        this.rfpaExceptionType = exceptionType;
        this.customerNotificationRequired = false;
        addAuditEntry("RFPA_EXCEPTION_APPLIED", "Exception: " + exceptionType + " - " + reason, createdBy);
    }

    /**
     * Add gathered record
     */
    public void addGatheredRecord(String recordId, String recordType, String description, boolean privileged) {
        Map<String, Object> record = new HashMap<>();
        record.put("recordId", recordId);
        record.put("recordType", recordType);
        record.put("description", description);
        record.put("privileged", privileged);
        record.put("gatheredAt", LocalDateTime.now().toString());

        recordsGathered.add(record);
        totalRecordsCount++;

        if (privileged) {
            privilegedRecordsCount++;
        }
    }

    /**
     * Complete records gathering
     */
    public void completeRecordsGathering(int totalCount) {
        this.totalRecordsCount = totalCount;
        this.status = SubpoenaStatus.RECORDS_GATHERED;
        addAuditEntry("RECORDS_GATHERED", "Gathered " + totalCount + " records", assignedTo);
    }

    /**
     * Perform redaction
     */
    public void performRedaction(int privilegedCount) {
        this.redactionPerformed = true;
        this.privilegedRecordsCount = privilegedCount;
        this.status = SubpoenaStatus.PREPARING_PRODUCTION;
        addAuditEntry("REDACTION_PERFORMED", "Redacted " + privilegedCount + " privileged records", assignedTo);
    }

    /**
     * Prepare document production with Bates numbering
     */
    public void prepareDocumentProduction(String startBates, String endBates, String format) {
        this.documentProductionPrepared = true;
        this.productionStartBates = startBates;
        this.productionEndBates = endBates;
        this.batesNumberingRange = startBates + " - " + endBates;
        this.productionFormat = format;
        this.status = SubpoenaStatus.PRODUCTION_READY;
        addAuditEntry("PRODUCTION_PREPARED", "Bates range: " + batesNumberingRange, assignedTo);
    }

    /**
     * Certify business records authenticity
     */
    public void certifyRecords(String certifierId, String statement) {
        this.recordsCertified = true;
        this.certificationDate = LocalDateTime.now();
        this.certifiedBy = certifierId;
        this.certificationStatement = statement;
        this.status = SubpoenaStatus.CERTIFIED;
        addAuditEntry("RECORDS_CERTIFIED", "Records certified by " + certifierId, certifierId);
    }

    /**
     * Submit to issuing party/court
     */
    public void submitToCourt(String method, String trackingNumber) {
        if (!recordsCertified) {
            throw new IllegalStateException("Records must be certified before submission");
        }
        this.submittedToCourt = true;
        this.submissionDate = LocalDateTime.now();
        this.submissionMethod = method;
        this.submissionTrackingNumber = trackingNumber;
        this.status = SubpoenaStatus.SUBMITTED;
        addAuditEntry("SUBMITTED_TO_COURT", "Submitted via " + method + ", tracking: " + trackingNumber, assignedTo);
    }

    /**
     * File compliance certificate
     */
    public void fileComplianceCertificate(String certificatePath) {
        this.complianceCertificateFiled = true;
        this.complianceCertificateDate = LocalDateTime.now();
        this.complianceCertificatePath = certificatePath;
        addAuditEntry("COMPLIANCE_CERTIFICATE_FILED", "Certificate filed: " + certificatePath, assignedTo);
    }

    /**
     * Complete subpoena processing
     */
    public void complete() {
        if (!submittedToCourt) {
            throw new IllegalStateException("Subpoena must be submitted before marking complete");
        }
        if (!complianceCertificateFiled) {
            throw new IllegalStateException("Compliance certificate must be filed before marking complete");
        }
        this.completed = true;
        this.completionDate = LocalDateTime.now();
        this.status = SubpoenaStatus.COMPLETED;
        addAuditEntry("COMPLETED", "Subpoena processing completed successfully", assignedTo);
    }

    /**
     * Escalate to legal counsel
     */
    public void escalateToLegalCounsel(String reason) {
        this.escalatedToLegalCounsel = true;
        this.escalationReason = reason;
        this.escalationDate = LocalDateTime.now();
        this.status = SubpoenaStatus.ESCALATED;
        this.priorityLevel = PriorityLevel.CRITICAL;
        addAuditEntry("ESCALATED", "Escalated to legal counsel: " + reason, assignedTo);
    }

    /**
     * Engage outside counsel
     */
    public void engageOutsideCounsel(String counselName) {
        this.outsideCounselEngaged = true;
        this.outsideCounselName = counselName;
        addAuditEntry("OUTSIDE_COUNSEL_ENGAGED", "Engaged " + counselName, assignedTo);
    }

    /**
     * Add processing note
     */
    public void addProcessingNote(String note, String author) {
        Map<String, Object> noteEntry = new HashMap<>();
        noteEntry.put("note", note);
        noteEntry.put("author", author);
        noteEntry.put("timestamp", LocalDateTime.now().toString());
        processingNotes.add(noteEntry);
    }

    /**
     * Add audit trail entry
     */
    public void addAuditEntry(String action, String description, String actor) {
        Map<String, Object> auditEntry = new HashMap<>();
        auditEntry.put("action", action);
        auditEntry.put("description", description);
        auditEntry.put("actor", actor);
        auditEntry.put("timestamp", LocalDateTime.now().toString());
        auditEntry.put("status", status.toString());
        auditTrail.add(auditEntry);
    }

    /**
     * Assign to handler
     */
    public void assignTo(String userId) {
        this.assignedTo = userId;
        addAuditEntry("ASSIGNED", "Assigned to " + userId, "SYSTEM");
    }

    /**
     * Get compliance summary
     */
    public Map<String, Object> getComplianceSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("subpoenaId", subpoenaId);
        summary.put("caseNumber", caseNumber);
        summary.put("validated", validated);
        summary.put("customerNotified", customerNotificationRequired ? customerNotified : "N/A");
        summary.put("recordsGathered", totalRecordsCount);
        summary.put("privilegedRecordsRedacted", privilegedRecordsCount);
        summary.put("productionPrepared", documentProductionPrepared);
        summary.put("batesRange", batesNumberingRange);
        summary.put("certified", recordsCertified);
        summary.put("submitted", submittedToCourt);
        summary.put("complianceCertificateFiled", complianceCertificateFiled);
        summary.put("completed", completed);
        summary.put("daysUntilDeadline", getDaysUntilDeadline());
        summary.put("isOverdue", isOverdue());
        return summary;
    }

    /**
     * Check if all RFPA requirements are met
     */
    public boolean isRfpaCompliant() {
        // If notification required, must be notified
        if (customerNotificationRequired && !customerNotified) {
            return false;
        }
        // Must have valid records gathering and certification
        return validated && totalRecordsCount > 0 && recordsCertified;
    }
}
