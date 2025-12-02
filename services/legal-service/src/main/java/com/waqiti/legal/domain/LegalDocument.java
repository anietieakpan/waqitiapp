package com.waqiti.legal.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Legal Document Domain Entity
 *
 * Represents legal documents with versioning, encryption, and retention policies
 * Supports multi-jurisdiction compliance and audit trail requirements
 *
 * Complete production-ready implementation with:
 * - Document lifecycle management
 * - Version control
 * - Encryption support
 * - Retention policies
 * - Audit trail
 * - Multi-party support
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-17
 */
@Entity
@Table(name = "legal_document",
    indexes = {
        @Index(name = "idx_legal_document_type", columnList = "document_type"),
        @Index(name = "idx_legal_document_category", columnList = "document_category"),
        @Index(name = "idx_legal_document_status", columnList = "document_status"),
        @Index(name = "idx_legal_document_jurisdiction", columnList = "jurisdiction"),
        @Index(name = "idx_legal_document_effective", columnList = "effective_date")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Document ID is required")
    private String documentId;

    @Column(name = "document_name", nullable = false)
    @NotBlank(message = "Document name is required")
    @Size(max = 255)
    private String documentName;

    @Column(name = "document_type", nullable = false, length = 50)
    @NotNull
    @Enumerated(EnumType.STRING)
    private DocumentType documentType;

    @Column(name = "document_category", nullable = false, length = 100)
    @NotBlank(message = "Document category is required")
    private String documentCategory;

    @Column(name = "document_status", nullable = false, length = 20)
    @NotNull
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DocumentStatus documentStatus = DocumentStatus.DRAFT;

    @Column(name = "version", nullable = false, length = 50)
    @NotBlank
    @Builder.Default
    private String version = "1.0";

    @Column(name = "previous_version_id", length = 100)
    private String previousVersionId;

    @Column(name = "jurisdiction", nullable = false, length = 100)
    @NotBlank(message = "Jurisdiction is required")
    private String jurisdiction;

    @Column(name = "applicable_law")
    private String applicableLaw;

    @Column(name = "document_language", length = 10)
    @Builder.Default
    private String documentLanguage = "en";

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Column(name = "renewal_date")
    private LocalDate renewalDate;

    @Column(name = "auto_renewal")
    @Builder.Default
    private Boolean autoRenewal = false;

    @Column(name = "file_path", nullable = false, length = 1000)
    @NotBlank(message = "File path is required")
    private String filePath;

    @Column(name = "file_size_bytes")
    private Integer fileSizeBytes;

    @Column(name = "file_format", nullable = false, length = 20)
    @NotBlank
    private String fileFormat;

    @Column(name = "checksum", length = 64)
    private String checksum;

    @Column(name = "encrypted")
    @Builder.Default
    private Boolean encrypted = true;

    @Column(name = "encryption_key_id")
    private String encryptionKeyId;

    @Column(name = "original_document_path", length = 1000)
    private String originalDocumentPath;

    @Column(name = "signed_document_path", length = 1000)
    private String signedDocumentPath;

    @Type(JsonBinaryType.class)
    @Column(name = "parties", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, Object> parties = new HashMap<>();

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    @Type(JsonBinaryType.class)
    @Column(name = "clauses", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> clauses = new HashMap<>();

    @Type(JsonBinaryType.class)
    @Column(name = "obligations", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> obligations = new HashMap<>();

    @Type(JsonBinaryType.class)
    @Column(name = "penalties", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> penalties = new HashMap<>();

    @Column(name = "confidentiality_level", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConfidentialityLevel confidentialityLevel = ConfidentialityLevel.CONFIDENTIAL;

    @Column(name = "retention_years")
    @Builder.Default
    private Integer retentionYears = 7;

    @Column(name = "destruction_date")
    private LocalDate destructionDate;

    @Type(JsonBinaryType.class)
    @Column(name = "tags", columnDefinition = "text[]")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "related_documents", columnDefinition = "text[]")
    @Builder.Default
    private List<String> relatedDocuments = new ArrayList<>();

    @Column(name = "approval_workflow_id", length = 100)
    private String approvalWorkflowId;

    @Column(name = "requires_approval")
    @Builder.Default
    private Boolean requiresApproval = true;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Type(JsonBinaryType.class)
    @Column(name = "metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

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
        if (documentId == null) {
            documentId = "DOC-" + UUID.randomUUID().toString();
        }
        // Calculate destruction date based on retention policy
        if (destructionDate == null && retentionYears != null && effectiveDate != null) {
            destructionDate = effectiveDate.plusYears(retentionYears);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum DocumentType {
        CONTRACT,
        TERMS_OF_SERVICE,
        PRIVACY_POLICY,
        COMPLIANCE_REPORT,
        REGULATORY_FILING,
        COURT_DOCUMENT,
        LEGAL_OPINION,
        MEMORANDUM,
        SETTLEMENT_AGREEMENT,
        POWER_OF_ATTORNEY,
        NDA,
        LICENSE_AGREEMENT,
        AMENDMENT,
        ADDENDUM,
        SUBPOENA,
        COURT_ORDER,
        OTHER
    }

    public enum DocumentStatus {
        DRAFT,
        PENDING_REVIEW,
        UNDER_REVIEW,
        APPROVED,
        REJECTED,
        EXECUTED,
        ACTIVE,
        EXPIRED,
        TERMINATED,
        ARCHIVED,
        PENDING_SIGNATURE,
        FULLY_EXECUTED
    }

    public enum ConfidentialityLevel {
        PUBLIC,
        INTERNAL,
        CONFIDENTIAL,
        HIGHLY_CONFIDENTIAL,
        ATTORNEY_CLIENT_PRIVILEGED
    }

    // Complete business logic methods

    /**
     * Check if document has expired
     */
    public boolean isExpired() {
        return expirationDate != null && LocalDate.now().isAfter(expirationDate);
    }

    /**
     * Check if document needs renewal (30 days before renewal date)
     */
    public boolean needsRenewal() {
        return renewalDate != null && LocalDate.now().isAfter(renewalDate.minusDays(30));
    }

    /**
     * Check if document is in active status
     */
    public boolean isActive() {
        return (documentStatus == DocumentStatus.ACTIVE ||
                documentStatus == DocumentStatus.EXECUTED ||
                documentStatus == DocumentStatus.FULLY_EXECUTED) &&
                !isExpired();
    }

    /**
     * Check if document should be destroyed per retention policy
     */
    public boolean requiresDestruction() {
        return destructionDate != null && LocalDate.now().isAfter(destructionDate);
    }

    /**
     * Calculate days until expiration
     */
    public int getDaysUntilExpiration() {
        if (expirationDate == null) {
            return Integer.MAX_VALUE;
        }
        return (int) java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expirationDate);
    }

    /**
     * Check if document requires approval
     */
    public boolean isPendingApproval() {
        return requiresApproval &&
               (documentStatus == DocumentStatus.PENDING_REVIEW ||
                documentStatus == DocumentStatus.UNDER_REVIEW);
    }

    /**
     * Approve document
     */
    public void approve(String approver) {
        if (!requiresApproval) {
            throw new IllegalStateException("Document does not require approval");
        }
        if (documentStatus != DocumentStatus.PENDING_REVIEW &&
            documentStatus != DocumentStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Document is not in reviewable state");
        }
        this.approvedBy = approver;
        this.approvedAt = LocalDateTime.now();
        this.documentStatus = DocumentStatus.APPROVED;
    }

    /**
     * Reject document with reason
     */
    public void reject(String reason) {
        if (documentStatus != DocumentStatus.PENDING_REVIEW &&
            documentStatus != DocumentStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Document is not in reviewable state");
        }
        this.rejectionReason = reason;
        this.documentStatus = DocumentStatus.REJECTED;
    }

    /**
     * Create new version of document
     */
    public LegalDocument createNewVersion(String newVersion, String createdBy) {
        return LegalDocument.builder()
                .documentName(this.documentName)
                .documentType(this.documentType)
                .documentCategory(this.documentCategory)
                .documentStatus(DocumentStatus.DRAFT)
                .version(newVersion)
                .previousVersionId(this.documentId)
                .jurisdiction(this.jurisdiction)
                .applicableLaw(this.applicableLaw)
                .documentLanguage(this.documentLanguage)
                .parties(new HashMap<>(this.parties))
                .confidentialityLevel(this.confidentialityLevel)
                .retentionYears(this.retentionYears)
                .requiresApproval(this.requiresApproval)
                .encrypted(this.encrypted)
                .createdBy(createdBy)
                .build();
    }

    /**
     * Add party to document
     */
    public void addParty(String partyId, String partyRole, Map<String, Object> partyDetails) {
        Map<String, Object> partyInfo = new HashMap<>(partyDetails);
        partyInfo.put("role", partyRole);
        partyInfo.put("addedAt", LocalDateTime.now().toString());
        this.parties.put(partyId, partyInfo);
    }

    /**
     * Remove party from document
     */
    public void removeParty(String partyId) {
        this.parties.remove(partyId);
    }

    /**
     * Add tag for categorization
     */
    public void addTag(String tag) {
        if (!this.tags.contains(tag)) {
            this.tags.add(tag);
        }
    }

    /**
     * Link related document
     */
    public void linkDocument(String documentId) {
        if (!this.relatedDocuments.contains(documentId)) {
            this.relatedDocuments.add(documentId);
        }
    }

    /**
     * Execute document (mark as fully executed)
     */
    public void execute() {
        if (documentStatus != DocumentStatus.APPROVED &&
            documentStatus != DocumentStatus.PENDING_SIGNATURE) {
            throw new IllegalStateException("Document must be approved before execution");
        }
        this.documentStatus = DocumentStatus.FULLY_EXECUTED;
        if (this.effectiveDate == null) {
            this.effectiveDate = LocalDate.now();
        }
    }

    /**
     * Archive document
     */
    public void archive() {
        if (documentStatus == DocumentStatus.DRAFT) {
            throw new IllegalStateException("Cannot archive draft documents");
        }
        this.documentStatus = DocumentStatus.ARCHIVED;
    }

    /**
     * Check if document can be modified
     */
    public boolean isModifiable() {
        return documentStatus == DocumentStatus.DRAFT ||
               documentStatus == DocumentStatus.REJECTED;
    }

    /**
     * Validate document for approval
     */
    public List<String> validateForApproval() {
        List<String> errors = new ArrayList<>();

        if (parties == null || parties.isEmpty()) {
            errors.add("Document must have at least one party");
        }
        if (filePath == null || filePath.isBlank()) {
            errors.add("Document file path is required");
        }
        if (effectiveDate == null) {
            errors.add("Effective date is required");
        }
        if (encrypted && (encryptionKeyId == null || encryptionKeyId.isBlank())) {
            errors.add("Encryption key ID required for encrypted documents");
        }
        if (checksum == null || checksum.isBlank()) {
            errors.add("Document checksum is required for integrity verification");
        }

        return errors;
    }
}
