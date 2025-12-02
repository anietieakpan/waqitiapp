package com.waqiti.gdpr.service;

import com.waqiti.gdpr.domain.*;
import com.waqiti.gdpr.repository.DataPrivacyImpactAssessmentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Data Privacy Impact Assessment Service
 *
 * Implements GDPR Article 35 DPIA requirements for high-risk processing operations.
 * Required when processing is likely to result in high risk to rights and freedoms,
 * particularly when using new technologies.
 *
 * Mandatory for:
 * - Systematic and extensive evaluation/profiling with legal effects
 * - Large-scale processing of special category data (Article 9)
 * - Systematic monitoring of publicly accessible areas on large scale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DpiaService {

    private final DataPrivacyImpactAssessmentRepository dpiaRepository;
    private final PrivacyAuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${gdpr.dpia.review-frequency-months:12}")
    private int defaultReviewFrequencyMonths;

    @Value("${gdpr.dpia.dpo-consultation-required:true}")
    private boolean dpoConsultationRequired;

    /**
     * Initiate a new DPIA
     */
    @Transactional
    public DataPrivacyImpactAssessment initiateDpia(DpiaInitiationRequest request) {
        log.info("Initiating DPIA: title={}, processingActivity={}",
                request.getTitle(), request.getProcessingActivityId());

        DataPrivacyImpactAssessment dpia = DataPrivacyImpactAssessment.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .processingActivityId(request.getProcessingActivityId())
                .status(DpiaStatus.INITIATED)
                .processingPurpose(request.getProcessingPurpose())
                .processingDescription(request.getProcessingDescription())
                .dataCategories(request.getDataCategories())
                .dataSubjects(request.getDataSubjects())
                .estimatedSubjectsCount(request.getEstimatedSubjectsCount())
                .involvesSpecialCategoryData(request.getInvolvesSpecialCategoryData())
                .involvesAutomatedDecisions(request.getInvolvesAutomatedDecisions())
                .involvesProfiling(request.getInvolvesProfiling())
                .largeScaleProcessing(request.getLargeScaleProcessing())
                .systematicMonitoring(request.getSystematicMonitoring())
                .legalBasis(request.getLegalBasis())
                .preparedBy(request.getPreparedBy())
                .preparationDate(LocalDateTime.now())
                .reviewFrequencyMonths(defaultReviewFrequencyMonths)
                .createdAt(LocalDateTime.now())
                .build();

        // Determine if DPIA is mandatory based on criteria
        boolean mandatory = isDpiaMandatory(dpia);
        if (!mandatory) {
            log.warn("DPIA initiated but may not be mandatory: {}", dpia.getTitle());
        }

        dpia = dpiaRepository.save(dpia);

        // Record audit
        auditService.recordDpia(dpia.getId(), AuditAction.DPIA_INITIATED, request.getPreparedBy());

        // Publish event
        publishDpiaEvent("dpia.initiated", dpia);

        // Metrics
        meterRegistry.counter("gdpr.dpia.initiated").increment();

        log.info("DPIA initiated: id={}, mandatory={}", dpia.getId(), mandatory);

        return dpia;
    }

    /**
     * Conduct risk assessment for DPIA
     */
    @Transactional
    public DataPrivacyImpactAssessment conductRiskAssessment(String dpiaId, RiskAssessmentInput input) {
        DataPrivacyImpactAssessment dpia = getDpia(dpiaId);

        log.info("Conducting risk assessment for DPIA: {}", dpiaId);

        // Create risk assessment
        RiskAssessment assessment = RiskAssessment.builder()
                .likelihoodScore(input.getLikelihoodScore())
                .impactScore(input.getImpactScore())
                .assessedBy(input.getAssessedBy())
                .assessedAt(LocalDateTime.now())
                .assessmentMethodology(input.getMethodology())
                .build();

        assessment.calculateRiskScore();
        assessment.setRequiresDpia(assessment.shouldRequireDpia());

        dpia.setRiskAssessment(assessment);
        dpia.setOverallRiskLevel(assessment.getRiskLevel());
        dpia.setIdentifiedRisks(input.getIdentifiedRisks());
        dpia.setRiskMitigationMeasures(input.getMitigationMeasures());
        dpia.setResidualRisks(input.getResidualRisks());

        // Determine if supervisory authority consultation required (Article 36)
        if (dpia.requiresSupervisoryConsultation()) {
            dpia.setExternalConsultationRequired(true);
            log.warn("DPIA {} requires supervisory authority consultation due to high residual risk", dpiaId);
        }

        dpia.setStatus(DpiaStatus.UNDER_REVIEW);
        dpia = dpiaRepository.save(dpia);

        // Record audit
        auditService.recordDpia(dpiaId, AuditAction.DPIA_REVIEWED, input.getAssessedBy());

        // Publish event
        publishDpiaEvent("dpia.risk.assessed", dpia);

        meterRegistry.counter("gdpr.dpia.risk.assessed",
                "riskLevel", assessment.getRiskLevel().toString()
        ).increment();

        return dpia;
    }

    /**
     * Record DPO consultation
     */
    @Transactional
    public DataPrivacyImpactAssessment consultDpo(String dpiaId, String dpoName, String opinion) {
        DataPrivacyImpactAssessment dpia = getDpia(dpiaId);

        dpia.setDpoConsulted(true);
        dpia.setDpoConsultationDate(LocalDateTime.now());
        dpia.setDpoOpinion(opinion);
        dpia.setStatus(DpiaStatus.DPO_CONSULTATION);

        dpia = dpiaRepository.save(dpia);

        auditService.recordDpia(dpiaId, AuditAction.DPIA_REVIEWED,
                "DPO consultation: " + dpoName);

        publishDpiaEvent("dpia.dpo.consulted", dpia);

        log.info("DPO consulted for DPIA: {}", dpiaId);

        return dpia;
    }

    /**
     * Record data subject consultation
     */
    @Transactional
    public DataPrivacyImpactAssessment consultDataSubjects(String dpiaId, String consultationSummary) {
        DataPrivacyImpactAssessment dpia = getDpia(dpiaId);

        dpia.setSubjectsConsulted(true);
        dpia.setSubjectConsultationSummary(consultationSummary);
        dpia.setStatus(DpiaStatus.SUBJECT_CONSULTATION);

        dpia = dpiaRepository.save(dpia);

        auditService.recordDpia(dpiaId, AuditAction.DPIA_REVIEWED, "Data subjects consulted");

        publishDpiaEvent("dpia.subjects.consulted", dpia);

        log.info("Data subjects consulted for DPIA: {}", dpiaId);

        return dpia;
    }

    /**
     * Record supervisory authority consultation (Article 36)
     */
    @Transactional
    public DataPrivacyImpactAssessment consultSupervisoryAuthority(String dpiaId, String reference) {
        DataPrivacyImpactAssessment dpia = getDpia(dpiaId);

        if (!dpia.requiresSupervisoryConsultation()) {
            throw new IllegalStateException("DPIA does not require supervisory authority consultation");
        }

        dpia.setSupervisoryAuthorityConsulted(true);
        dpia.setSupervisoryAuthorityConsultationDate(LocalDateTime.now());
        dpia.setSupervisoryAuthorityReference(reference);
        dpia.setStatus(DpiaStatus.AUTHORITY_CONSULTATION);

        dpia = dpiaRepository.save(dpia);

        auditService.recordDpia(dpiaId, AuditAction.DPIA_REVIEWED,
                "Supervisory authority consulted: " + reference);

        publishDpiaEvent("dpia.authority.consulted", dpia);

        log.info("Supervisory authority consulted for DPIA: {}, reference={}", dpiaId, reference);

        return dpia;
    }

    /**
     * Complete DPIA with conclusion
     */
    @Transactional
    public DataPrivacyImpactAssessment completeDpia(String dpiaId, DpiaCompletionInput input) {
        DataPrivacyImpactAssessment dpia = getDpia(dpiaId);

        // Validate required consultations
        if (dpoConsultationRequired && !Boolean.TRUE.equals(dpia.getDpoConsulted())) {
            throw new IllegalStateException("DPO consultation required before completing DPIA");
        }

        if (dpia.requiresSupervisoryConsultation() &&
            !Boolean.TRUE.equals(dpia.getSupervisoryAuthorityConsulted())) {
            throw new IllegalStateException("Supervisory authority consultation required before completing DPIA");
        }

        dpia.setConclusion(input.getConclusion());
        dpia.setConclusionNotes(input.getConclusionNotes());
        dpia.setProcessingMayProceed(input.getProcessingMayProceed());
        dpia.setConditionsForProcessing(input.getConditionsForProcessing());
        dpia.setRecommendations(input.getRecommendations());
        dpia.setRequiredActions(input.getRequiredActions());

        dpia.markCompleted();

        dpia = dpiaRepository.save(dpia);

        auditService.recordDpia(dpiaId, AuditAction.DPIA_COMPLETED, input.getCompletedBy());

        publishDpiaEvent("dpia.completed", dpia);

        meterRegistry.counter("gdpr.dpia.completed",
                "conclusion", dpia.getConclusion().toString(),
                "processingAllowed", String.valueOf(dpia.getProcessingMayProceed())
        ).increment();

        log.info("DPIA completed: id={}, conclusion={}, processingMayProceed={}",
                dpiaId, dpia.getConclusion(), dpia.getProcessingMayProceed());

        return dpia;
    }

    /**
     * Approve DPIA
     */
    @Transactional
    public DataPrivacyImpactAssessment approveDpia(String dpiaId, String approver) {
        DataPrivacyImpactAssessment dpia = getDpia(dpiaId);

        if (dpia.getStatus() != DpiaStatus.COMPLETED) {
            throw new IllegalStateException("DPIA must be completed before approval");
        }

        dpia.approve(approver);
        dpia = dpiaRepository.save(dpia);

        auditService.recordDpia(dpiaId, AuditAction.DPIA_REVIEWED, "Approved by: " + approver);

        publishDpiaEvent("dpia.approved", dpia);

        meterRegistry.counter("gdpr.dpia.approved").increment();

        log.info("DPIA approved: id={}, approver={}", dpiaId, approver);

        return dpia;
    }

    /**
     * Reject DPIA (requires revision)
     */
    @Transactional
    public DataPrivacyImpactAssessment rejectDpia(String dpiaId, String reviewer, String reason) {
        DataPrivacyImpactAssessment dpia = getDpia(dpiaId);

        dpia.reject(reviewer, reason);
        dpia = dpiaRepository.save(dpia);

        auditService.recordDpia(dpiaId, AuditAction.DPIA_REVIEWED,
                "Rejected by " + reviewer + ": " + reason);

        publishDpiaEvent("dpia.rejected", dpia);

        meterRegistry.counter("gdpr.dpia.rejected").increment();

        log.info("DPIA rejected: id={}, reviewer={}", dpiaId, reviewer);

        return dpia;
    }

    /**
     * Scheduled review monitoring
     * Runs daily to check for DPIAs requiring review
     */
    @Scheduled(cron = "${gdpr.dpia.review-check.cron:0 0 9 * * *}")
    public void monitorDpiaReviews() {
        LocalDateTime now = LocalDateTime.now();
        List<DataPrivacyImpactAssessment> reviewDue = dpiaRepository.findDpiasWithReviewDue(now);

        if (!reviewDue.isEmpty()) {
            log.warn("ALERT: {} DPIAs require periodic review", reviewDue.size());

            for (DataPrivacyImpactAssessment dpia : reviewDue) {
                publishDpiaEvent("dpia.review.due", dpia);

                log.warn("DPIA review overdue: id={}, title={}, lastReview={}",
                        dpia.getId(), dpia.getTitle(), dpia.getReviewDate());
            }

            meterRegistry.counter("gdpr.dpia.reviews.due").increment(reviewDue.size());
        }
    }

    /**
     * Get all DPIAs requiring supervisory consultation
     */
    @Transactional(readOnly = true)
    public List<DataPrivacyImpactAssessment> getDpiasRequiringAuthority() {
        return dpiaRepository.findRequiringAuthorityConsultation();
    }

    /**
     * Get high-risk DPIAs
     */
    @Transactional(readOnly = true)
    public List<DataPrivacyImpactAssessment> getHighRiskDpias() {
        return dpiaRepository.findHighRiskDpias();
    }

    /**
     * Check if DPIA is mandatory based on processing characteristics
     */
    private boolean isDpiaMandatory(DataPrivacyImpactAssessment dpia) {
        // Mandatory if special category data on large scale
        if (Boolean.TRUE.equals(dpia.getInvolvesSpecialCategoryData()) &&
            Boolean.TRUE.equals(dpia.getLargeScaleProcessing())) {
            return true;
        }

        // Mandatory if systematic monitoring on large scale
        if (Boolean.TRUE.equals(dpia.getSystematicMonitoring()) &&
            Boolean.TRUE.equals(dpia.getLargeScaleProcessing())) {
            return true;
        }

        // Mandatory if extensive profiling with legal effects
        if (Boolean.TRUE.equals(dpia.getInvolvesProfiling()) &&
            Boolean.TRUE.equals(dpia.getInvolvesAutomatedDecisions())) {
            return true;
        }

        return false;
    }

    private DataPrivacyImpactAssessment getDpia(String dpiaId) {
        return dpiaRepository.findById(dpiaId)
                .orElseThrow(() -> new IllegalArgumentException("DPIA not found: " + dpiaId));
    }

    private void publishDpiaEvent(String topic, DataPrivacyImpactAssessment dpia) {
        try {
            kafkaTemplate.send("gdpr." + topic, dpia);
        } catch (Exception e) {
            log.error("Failed to publish DPIA event to topic {}: {}", topic, e.getMessage());
        }
    }

    // DTOs

    @lombok.Data
    @lombok.Builder
    public static class DpiaInitiationRequest {
        private String title;
        private String description;
        private String processingActivityId;
        private String processingPurpose;
        private String processingDescription;
        private Set<String> dataCategories;
        private Set<String> dataSubjects;
        private Long estimatedSubjectsCount;
        private Boolean involvesSpecialCategoryData;
        private Boolean involvesAutomatedDecisions;
        private Boolean involvesProfiling;
        private Boolean largeScaleProcessing;
        private Boolean systematicMonitoring;
        private String legalBasis;
        private String preparedBy;
    }

    @lombok.Data
    @lombok.Builder
    public static class RiskAssessmentInput {
        private Integer likelihoodScore;
        private Integer impactScore;
        private String identifiedRisks;
        private String mitigationMeasures;
        private String residualRisks;
        private String assessedBy;
        private String methodology;
    }

    @lombok.Data
    @lombok.Builder
    public static class DpiaCompletionInput {
        private DpiaConclusion conclusion;
        private String conclusionNotes;
        private Boolean processingMayProceed;
        private String conditionsForProcessing;
        private String recommendations;
        private String requiredActions;
        private String completedBy;
    }
}
