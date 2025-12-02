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
 * Legal Opinion Domain Entity
 *
 * Complete production-ready legal advisory management with:
 * - Formal legal opinion authoring and approval
 * - Case law and statutory citation tracking
 * - Risk assessment and confidence scoring
 * - Multi-jurisdictional legal analysis
 * - Attorney work product privilege
 * - Opinion validity and expiration management
 * - Peer review workflow
 * - Client advisory tracking
 * - Legal research documentation
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Entity
@Table(name = "legal_opinion",
    indexes = {
        @Index(name = "idx_legal_opinion_type", columnList = "opinion_type"),
        @Index(name = "idx_legal_opinion_jurisdiction", columnList = "jurisdiction"),
        @Index(name = "idx_legal_opinion_date", columnList = "opinion_date"),
        @Index(name = "idx_legal_opinion_client", columnList = "client_id")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LegalOpinion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "opinion_id", unique = true, nullable = false, length = 100)
    @NotBlank(message = "Opinion ID is required")
    private String opinionId;

    @Column(name = "opinion_title", nullable = false)
    @NotBlank(message = "Opinion title is required")
    private String opinionTitle;

    @Column(name = "opinion_type", nullable = false, length = 50)
    @NotNull
    @Enumerated(EnumType.STRING)
    private OpinionType opinionType;

    @Column(name = "matter_description", columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "Matter description is required")
    private String matterDescription;

    @Type(JsonBinaryType.class)
    @Column(name = "legal_questions", columnDefinition = "text[]")
    @Builder.Default
    private List<String> legalQuestions = new ArrayList<>();

    @Column(name = "jurisdiction", nullable = false, length = 100)
    @NotBlank(message = "Jurisdiction is required")
    private String jurisdiction;

    @Type(JsonBinaryType.class)
    @Column(name = "applicable_laws", columnDefinition = "text[]")
    @Builder.Default
    private List<String> applicableLaws = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "case_law_references", columnDefinition = "text[]")
    @Builder.Default
    private List<String> caseLawReferences = new ArrayList<>();

    @Type(JsonBinaryType.class)
    @Column(name = "statutory_references", columnDefinition = "text[]")
    @Builder.Default
    private List<String> statutoryReferences = new ArrayList<>();

    @Column(name = "legal_analysis", columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "Legal analysis is required")
    private String legalAnalysis;

    @Column(name = "conclusions", columnDefinition = "TEXT", nullable = false)
    @NotBlank(message = "Conclusions are required")
    private String conclusions;

    @Type(JsonBinaryType.class)
    @Column(name = "recommendations", columnDefinition = "text[]")
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();

    @Column(name = "risk_assessment", columnDefinition = "TEXT")
    private String riskAssessment;

    @Column(name = "confidence_level", length = 20)
    @Enumerated(EnumType.STRING)
    private ConfidenceLevel confidenceLevel;

    @Column(name = "limitations", columnDefinition = "TEXT")
    private String limitations;

    @Column(name = "assumptions", columnDefinition = "TEXT")
    private String assumptions;

    @Column(name = "author_name", nullable = false)
    @NotBlank(message = "Author name is required")
    private String authorName;

    @Column(name = "author_credentials", length = 500)
    private String authorCredentials;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "opinion_date", nullable = false)
    @NotNull(message = "Opinion date is required")
    private LocalDate opinionDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "update_required")
    @Builder.Default
    private Boolean updateRequired = false;

    @Column(name = "confidentiality_level", length = 20)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ConfidentialityLevel confidentialityLevel = ConfidentialityLevel.ATTORNEY_CLIENT_PRIVILEGED;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(name = "related_case_id", length = 100)
    private String relatedCaseId;

    @Column(name = "related_contract_id", length = 100)
    private String relatedContractId;

    @Type(JsonBinaryType.class)
    @Column(name = "document_references", columnDefinition = "text[]")
    @Builder.Default
    private List<String> documentReferences = new ArrayList<>();

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
        if (opinionId == null) {
            opinionId = "OPN-" + UUID.randomUUID().toString();
        }
        if (opinionDate == null) {
            opinionDate = LocalDate.now();
        }
        // Set default expiry (1 year for most opinions)
        if (expiryDate == null) {
            expiryDate = opinionDate.plusYears(1);
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Enums
    public enum OpinionType {
        FORMAL_LEGAL_OPINION,
        MEMORANDUM,
        ADVISORY,
        RESEARCH_MEMO,
        INTERNAL_GUIDANCE,
        CLIENT_ADVISORY,
        RISK_ANALYSIS,
        COMPLIANCE_OPINION,
        TRANSACTION_OPINION,
        LITIGATION_ASSESSMENT,
        REGULATORY_INTERPRETATION,
        CORPORATE_GOVERNANCE,
        INTELLECTUAL_PROPERTY,
        TAX_OPINION,
        SECURITIES_OPINION,
        REAL_ESTATE_OPINION,
        EMPLOYMENT_LAW,
        OTHER
    }

    public enum ConfidenceLevel {
        VERY_HIGH,      // 90-100% confident
        HIGH,           // 75-89% confident
        MODERATE,       // 50-74% confident
        LOW,            // 25-49% confident
        VERY_LOW,       // 0-24% confident
        INCONCLUSIVE    // Cannot determine
    }

    public enum ConfidentialityLevel {
        PUBLIC,
        INTERNAL,
        CONFIDENTIAL,
        HIGHLY_CONFIDENTIAL,
        ATTORNEY_CLIENT_PRIVILEGED,
        ATTORNEY_WORK_PRODUCT
    }

    // Complete business logic methods

    /**
     * Check if opinion is expired
     */
    public boolean isExpired() {
        return expiryDate != null && LocalDate.now().isAfter(expiryDate);
    }

    /**
     * Check if opinion is approaching expiration (within 60 days)
     */
    public boolean isApproachingExpiration() {
        if (expiryDate == null) {
            return false;
        }
        long daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        return daysUntilExpiry > 0 && daysUntilExpiry <= 60;
    }

    /**
     * Get days until expiration
     */
    public long getDaysUntilExpiration() {
        if (expiryDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }

    /**
     * Check if opinion is valid
     */
    public boolean isValid() {
        return !isExpired() && !updateRequired && approvedBy != null;
    }

    /**
     * Add legal question
     */
    public void addLegalQuestion(String question) {
        if (!legalQuestions.contains(question)) {
            legalQuestions.add(question);
        }
    }

    /**
     * Add applicable law reference
     */
    public void addApplicableLaw(String law) {
        if (!applicableLaws.contains(law)) {
            applicableLaws.add(law);
        }
    }

    /**
     * Add case law citation
     */
    public void addCaseLawReference(String citation) {
        if (!caseLawReferences.contains(citation)) {
            caseLawReferences.add(citation);
        }
    }

    /**
     * Add statutory reference
     */
    public void addStatutoryReference(String reference) {
        if (!statutoryReferences.contains(reference)) {
            statutoryReferences.add(reference);
        }
    }

    /**
     * Add recommendation
     */
    public void addRecommendation(String recommendation) {
        if (!recommendations.contains(recommendation)) {
            recommendations.add(recommendation);
        }
    }

    /**
     * Add document reference
     */
    public void addDocumentReference(String documentId) {
        if (!documentReferences.contains(documentId)) {
            documentReferences.add(documentId);
        }
    }

    /**
     * Submit for review
     */
    public void submitForReview(String reviewer) {
        if (reviewedBy != null) {
            throw new IllegalStateException("Opinion already under review or reviewed");
        }
        this.reviewedBy = reviewer;
    }

    /**
     * Complete review
     */
    public void completeReview(String reviewer, boolean requiresRevision, String reviewNotes) {
        this.reviewedBy = reviewer;

        if (requiresRevision) {
            this.updateRequired = true;
            this.limitations = (limitations != null ? limitations + "\n\n" : "") +
                    "REVIEW NOTES: " + reviewNotes;
        }
    }

    /**
     * Approve opinion
     */
    public void approve(String approver) {
        if (reviewedBy == null) {
            throw new IllegalStateException("Opinion must be reviewed before approval");
        }
        if (updateRequired) {
            throw new IllegalStateException("Opinion requires updates before approval");
        }
        this.approvedBy = approver;
    }

    /**
     * Revise opinion
     */
    public void revise(String revisedAnalysis, String revisedConclusions) {
        this.legalAnalysis = revisedAnalysis;
        this.conclusions = revisedConclusions;
        this.updateRequired = false;
        this.reviewedBy = null; // Requires re-review
        this.approvedBy = null; // Requires re-approval
    }

    /**
     * Mark as requiring update
     */
    public void markForUpdate(String reason) {
        this.updateRequired = true;
        this.limitations = (limitations != null ? limitations + "\n\n" : "") +
                "UPDATE REQUIRED: " + reason + " (Marked on " + LocalDate.now() + ")";
    }

    /**
     * Extend expiry date
     */
    public void extendExpiry(LocalDate newExpiryDate, String reason) {
        if (newExpiryDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("New expiry date cannot be in the past");
        }
        this.expiryDate = newExpiryDate;
        this.assumptions = (assumptions != null ? assumptions + "\n\n" : "") +
                "EXPIRY EXTENDED: " + reason + " (Extended to " + newExpiryDate + ")";
    }

    /**
     * Create updated version
     */
    public LegalOpinion createUpdatedVersion(String updatedBy) {
        return LegalOpinion.builder()
                .opinionTitle(this.opinionTitle + " (Updated)")
                .opinionType(this.opinionType)
                .matterDescription(this.matterDescription)
                .legalQuestions(new ArrayList<>(this.legalQuestions))
                .jurisdiction(this.jurisdiction)
                .applicableLaws(new ArrayList<>(this.applicableLaws))
                .caseLawReferences(new ArrayList<>(this.caseLawReferences))
                .statutoryReferences(new ArrayList<>(this.statutoryReferences))
                .legalAnalysis(this.legalAnalysis)
                .conclusions(this.conclusions)
                .recommendations(new ArrayList<>(this.recommendations))
                .riskAssessment(this.riskAssessment)
                .confidenceLevel(this.confidenceLevel)
                .confidentialityLevel(this.confidentialityLevel)
                .authorName(this.authorName)
                .authorCredentials(this.authorCredentials)
                .clientId(this.clientId)
                .relatedCaseId(this.relatedCaseId)
                .relatedContractId(this.relatedContractId)
                .createdBy(updatedBy)
                .build();
    }

    /**
     * Assess confidence level based on factors
     */
    public void assessConfidence(int caseLawSupport, int statutoryClarity,
                                  int jurisdictionalCertainty, int factualClarity) {
        // Each factor scored 0-100
        int averageScore = (caseLawSupport + statutoryClarity +
                jurisdictionalCertainty + factualClarity) / 4;

        if (averageScore >= 90) {
            this.confidenceLevel = ConfidenceLevel.VERY_HIGH;
        } else if (averageScore >= 75) {
            this.confidenceLevel = ConfidenceLevel.HIGH;
        } else if (averageScore >= 50) {
            this.confidenceLevel = ConfidenceLevel.MODERATE;
        } else if (averageScore >= 25) {
            this.confidenceLevel = ConfidenceLevel.LOW;
        } else {
            this.confidenceLevel = ConfidenceLevel.VERY_LOW;
        }
    }

    /**
     * Check if adequately researched
     */
    public boolean isAdequatelyResearched() {
        // Must have case law or statutory support
        if (caseLawReferences.isEmpty() && statutoryReferences.isEmpty()) {
            return false;
        }

        // Must have at least 3 total references for formal opinions
        if (opinionType == OpinionType.FORMAL_LEGAL_OPINION &&
            (caseLawReferences.size() + statutoryReferences.size()) < 3) {
            return false;
        }

        return true;
    }

    /**
     * Calculate research depth score
     */
    public int getResearchDepthScore() {
        int score = 0;

        // Case law references (up to 40 points)
        score += Math.min(40, caseLawReferences.size() * 10);

        // Statutory references (up to 30 points)
        score += Math.min(30, statutoryReferences.size() * 10);

        // Applicable laws identified (up to 20 points)
        score += Math.min(20, applicableLaws.size() * 5);

        // Document references (up to 10 points)
        score += Math.min(10, documentReferences.size() * 2);

        return Math.min(100, score);
    }

    /**
     * Check if attorney-client privileged
     */
    public boolean isPrivileged() {
        return confidentialityLevel == ConfidentialityLevel.ATTORNEY_CLIENT_PRIVILEGED ||
               confidentialityLevel == ConfidentialityLevel.ATTORNEY_WORK_PRODUCT;
    }

    /**
     * Check if requires peer review
     */
    public boolean requiresPeerReview() {
        // Formal opinions always require review
        if (opinionType == OpinionType.FORMAL_LEGAL_OPINION) {
            return reviewedBy == null;
        }

        // High-risk opinions require review
        if (confidenceLevel == ConfidenceLevel.LOW ||
            confidenceLevel == ConfidenceLevel.VERY_LOW) {
            return reviewedBy == null;
        }

        return false;
    }

    /**
     * Generate opinion summary
     */
    public Map<String, Object> generateSummary() {
        Map<String, Object> summary = new HashMap<>();
        summary.put("opinionId", opinionId);
        summary.put("opinionTitle", opinionTitle);
        summary.put("opinionType", opinionType);
        summary.put("jurisdiction", jurisdiction);
        summary.put("opinionDate", opinionDate);
        summary.put("authorName", authorName);
        summary.put("confidenceLevel", confidenceLevel);
        summary.put("isValid", isValid());
        summary.put("isExpired", isExpired());
        summary.put("isApproachingExpiration", isApproachingExpiration());
        summary.put("daysUntilExpiration", getDaysUntilExpiration());
        summary.put("updateRequired", updateRequired);
        summary.put("reviewed", reviewedBy != null);
        summary.put("approved", approvedBy != null);
        summary.put("isPrivileged", isPrivileged());
        summary.put("researchDepthScore", getResearchDepthScore());
        summary.put("caseLawReferencesCount", caseLawReferences.size());
        summary.put("statutoryReferencesCount", statutoryReferences.size());
        summary.put("recommendationsCount", recommendations.size());
        return summary;
    }

    /**
     * Validate opinion for approval
     */
    public List<String> validateForApproval() {
        List<String> errors = new ArrayList<>();

        if (legalQuestions.isEmpty()) {
            errors.add("At least one legal question must be addressed");
        }
        if (legalAnalysis == null || legalAnalysis.isBlank()) {
            errors.add("Legal analysis is required");
        }
        if (conclusions == null || conclusions.isBlank()) {
            errors.add("Conclusions are required");
        }
        if (!isAdequatelyResearched()) {
            errors.add("Insufficient legal research - case law or statutory support required");
        }
        if (confidenceLevel == null) {
            errors.add("Confidence level must be assessed");
        }
        if (reviewedBy == null && requiresPeerReview()) {
            errors.add("Peer review is required for this opinion type");
        }
        if (updateRequired) {
            errors.add("Opinion requires updates before approval");
        }

        return errors;
    }

    /**
     * Check if can be relied upon
     */
    public boolean canBeReliedUpon() {
        return isValid() &&
               approvedBy != null &&
               confidenceLevel != ConfidenceLevel.VERY_LOW &&
               confidenceLevel != ConfidenceLevel.INCONCLUSIVE;
    }

    /**
     * Get opinion age in days
     */
    public long getOpinionAgeDays() {
        return ChronoUnit.DAYS.between(opinionDate, LocalDate.now());
    }

    /**
     * Check if stale (older than 180 days without review)
     */
    public boolean isStale() {
        return getOpinionAgeDays() > 180 && !updateRequired;
    }

    /**
     * Associate with case
     */
    public void associateWithCase(String caseId) {
        this.relatedCaseId = caseId;
    }

    /**
     * Associate with contract
     */
    public void associateWithContract(String contractId) {
        this.relatedContractId = contractId;
    }

    /**
     * Get quality score (0-100)
     */
    public int getQualityScore() {
        int score = 0;

        // Research depth (40 points)
        score += (getResearchDepthScore() * 40) / 100;

        // Confidence level (20 points)
        if (confidenceLevel != null) {
            score += switch (confidenceLevel) {
                case VERY_HIGH -> 20;
                case HIGH -> 16;
                case MODERATE -> 12;
                case LOW -> 8;
                case VERY_LOW, INCONCLUSIVE -> 4;
            };
        }

        // Review and approval (20 points)
        if (reviewedBy != null) score += 10;
        if (approvedBy != null) score += 10;

        // Current and valid (20 points)
        if (!isExpired()) score += 10;
        if (!updateRequired) score += 10;

        return Math.min(100, score);
    }
}
