package com.waqiti.gdpr.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.GenericGenerator;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Data Privacy Impact Assessment (DPIA) - GDPR Article 35
 *
 * Required when processing is likely to result in high risk to rights
 * and freedoms of natural persons, particularly when using new technologies.
 *
 * Mandatory for:
 * - Systematic and extensive evaluation/profiling with legal effects
 * - Large-scale processing of special category data
 * - Systematic monitoring of publicly accessible areas on large scale
 */
@Entity
@Table(name = "data_privacy_impact_assessments",
       indexes = {
           @Index(name = "idx_dpia_status", columnList = "status"),
           @Index(name = "idx_dpia_risk_level", columnList = "overall_risk_level"),
           @Index(name = "idx_dpia_processing_activity", columnList = "processing_activity_id"),
           @Index(name = "idx_dpia_review_date", columnList = "next_review_date")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataPrivacyImpactAssessment {

    @Id
    @GeneratedValue(generator = "uuid")
    @GenericGenerator(name = "uuid", strategy = "uuid2")
    @Column(name = "id", length = 36)
    private String id;

    @NotBlank
    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "processing_activity_id", length = 36)
    private String processingActivityId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private DpiaStatus status;

    // Processing Description
    @Column(name = "processing_purpose", columnDefinition = "TEXT")
    private String processingPurpose;

    @Column(name = "processing_description", columnDefinition = "TEXT")
    private String processingDescription;

    @ElementCollection
    @CollectionTable(name = "dpia_data_categories",
                     joinColumns = @JoinColumn(name = "dpia_id"))
    @Column(name = "data_category", length = 100)
    @Builder.Default
    private Set<String> dataCategories = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "dpia_data_subjects",
                     joinColumns = @JoinColumn(name = "dpia_id"))
    @Column(name = "subject_type", length = 100)
    @Builder.Default
    private Set<String> dataSubjects = new HashSet<>();

    @Column(name = "estimated_subjects_count")
    private Long estimatedSubjectsCount;

    @Column(name = "involves_special_category_data")
    private Boolean involvesSpecialCategoryData;

    @Column(name = "involves_automated_decisions")
    private Boolean involvesAutomatedDecisions;

    @Column(name = "involves_profiling")
    private Boolean involvesProfiling;

    @Column(name = "large_scale_processing")
    private Boolean largeScaleProcessing;

    @Column(name = "systematic_monitoring")
    private Boolean systematicMonitoring;

    // Necessity and Proportionality
    @Column(name = "necessity_assessment", columnDefinition = "TEXT")
    private String necessityAssessment;

    @Column(name = "proportionality_assessment", columnDefinition = "TEXT")
    private String proportionalityAssessment;

    @Column(name = "legal_basis", length = 100)
    private String legalBasis;

    @Column(name = "legitimate_interests", columnDefinition = "TEXT")
    private String legitimateInterests;

    // Risk Assessment
    @Embedded
    private RiskAssessment riskAssessment;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_risk_level", length = 20)
    private RiskLevel overallRiskLevel;

    @Column(name = "identified_risks", columnDefinition = "TEXT")
    private String identifiedRisks; // JSON array

    @Column(name = "risk_mitigation_measures", columnDefinition = "TEXT")
    private String riskMitigationMeasures;

    @Column(name = "residual_risks", columnDefinition = "TEXT")
    private String residualRisks;

    // Security Measures
    @Column(name = "technical_measures", columnDefinition = "TEXT")
    private String technicalMeasures;

    @Column(name = "organizational_measures", columnDefinition = "TEXT")
    private String organizationalMeasures;

    @Column(name = "data_minimization_applied")
    private Boolean dataMinimizationApplied;

    @Column(name = "pseudonymization_applied")
    private Boolean pseudonymizationApplied;

    @Column(name = "encryption_applied")
    private Boolean encryptionApplied;

    // Consultation
    @Column(name = "dpo_consulted")
    private Boolean dpoConsulted;

    @Column(name = "dpo_consultation_date")
    private LocalDateTime dpoConsultationDate;

    @Column(name = "dpo_opinion", columnDefinition = "TEXT")
    private String dpoOpinion;

    @Column(name = "subjects_consulted")
    private Boolean subjectsConsulted;

    @Column(name = "subject_consultation_summary", columnDefinition = "TEXT")
    private String subjectConsultationSummary;

    @Column(name = "external_consultation_required")
    private Boolean externalConsultationRequired;

    @Column(name = "supervisory_authority_consulted")
    private Boolean supervisoryAuthorityConsulted;

    @Column(name = "supervisory_authority_consultation_date")
    private LocalDateTime supervisoryAuthorityConsultationDate;

    @Column(name = "supervisory_authority_reference", length = 255)
    private String supervisoryAuthorityReference;

    // Recommendations and Actions
    @Column(name = "recommendations", columnDefinition = "TEXT")
    private String recommendations;

    @Column(name = "required_actions", columnDefinition = "TEXT")
    private String requiredActions;

    @Column(name = "actions_implemented")
    private Boolean actionsImplemented;

    @Column(name = "actions_implementation_date")
    private LocalDateTime actionsImplementationDate;

    // Approval and Review
    @Column(name = "prepared_by", length = 255)
    private String preparedBy;

    @Column(name = "preparation_date")
    private LocalDateTime preparationDate;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "review_date")
    private LocalDateTime reviewDate;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approval_date")
    private LocalDateTime approvalDate;

    @Column(name = "next_review_date")
    private LocalDateTime nextReviewDate;

    @Column(name = "review_frequency_months")
    private Integer reviewFrequencyMonths;

    // Documentation
    @Column(name = "methodology_used", length = 255)
    private String methodologyUsed;

    @Column(name = "standards_applied", columnDefinition = "TEXT")
    private String standardsApplied;

    @Column(name = "document_url", length = 500)
    private String documentUrl;

    @Column(name = "attachment_references", columnDefinition = "TEXT")
    private String attachmentReferences; // JSON array

    // Conclusion
    @Enumerated(EnumType.STRING)
    @Column(name = "conclusion", length = 50)
    private DpiaConclusion conclusion;

    @Column(name = "conclusion_notes", columnDefinition = "TEXT")
    private String conclusionNotes;

    @Column(name = "processing_may_proceed")
    private Boolean processingMayProceed;

    @Column(name = "conditions_for_processing", columnDefinition = "TEXT")
    private String conditionsForProcessing;

    // Metadata
    @NotNull
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();

        // Set default review frequency to 12 months
        if (reviewFrequencyMonths == null) {
            reviewFrequencyMonths = 12;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Mark DPIA as completed
     */
    public void markCompleted() {
        this.status = DpiaStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();

        // Schedule next review
        if (reviewFrequencyMonths != null) {
            this.nextReviewDate = completedAt.plusMonths(reviewFrequencyMonths);
        }
    }

    /**
     * Approve the DPIA
     */
    public void approve(String approver) {
        this.approvedBy = approver;
        this.approvalDate = LocalDateTime.now();
        this.status = DpiaStatus.APPROVED;
    }

    /**
     * Reject the DPIA
     */
    public void reject(String reviewer, String reason) {
        this.reviewedBy = reviewer;
        this.reviewDate = LocalDateTime.now();
        this.status = DpiaStatus.REJECTED;
        this.conclusionNotes = reason;
        this.processingMayProceed = false;
    }

    /**
     * Check if DPIA review is overdue
     */
    public boolean isReviewOverdue() {
        return nextReviewDate != null && LocalDateTime.now().isAfter(nextReviewDate);
    }

    /**
     * Determine if supervisory authority consultation is required
     * Article 36: Required if DPIA indicates high risk and controller cannot mitigate
     */
    public boolean requiresSupervisoryConsultation() {
        return overallRiskLevel == RiskLevel.CRITICAL ||
               (overallRiskLevel == RiskLevel.HIGH && !actionsImplemented);
    }
}
