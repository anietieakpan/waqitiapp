package com.waqiti.gdpr.repository;

import com.waqiti.gdpr.domain.DataPrivacyImpactAssessment;
import com.waqiti.gdpr.domain.DpiaConclusion;
import com.waqiti.gdpr.domain.DpiaStatus;
import com.waqiti.gdpr.domain.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for DataPrivacyImpactAssessment entities
 * Supports GDPR Article 35 DPIA requirements
 */
@Repository
public interface DataPrivacyImpactAssessmentRepository extends JpaRepository<DataPrivacyImpactAssessment, String> {

    /**
     * Find DPIAs by status
     */
    List<DataPrivacyImpactAssessment> findByStatus(DpiaStatus status);

    /**
     * Find DPIAs for specific processing activity
     */
    List<DataPrivacyImpactAssessment> findByProcessingActivityId(String processingActivityId);

    /**
     * Find latest DPIA for processing activity
     */
    @Query("SELECT d FROM DataPrivacyImpactAssessment d " +
           "WHERE d.processingActivityId = :activityId " +
           "ORDER BY d.createdAt DESC LIMIT 1")
    Optional<DataPrivacyImpactAssessment> findLatestByProcessingActivityId(
        @Param("activityId") String activityId);

    /**
     * Find DPIAs by risk level
     */
    @Query("SELECT d FROM DataPrivacyImpactAssessment d WHERE d.overallRiskLevel = :riskLevel")
    List<DataPrivacyImpactAssessment> findByRiskLevel(@Param("riskLevel") RiskLevel riskLevel);

    /**
     * Find DPIAs with review due
     */
    @Query("SELECT d FROM DataPrivacyImpactAssessment d " +
           "WHERE d.nextReviewDate IS NOT NULL " +
           "AND d.nextReviewDate <= :date " +
           "AND d.status NOT IN ('UNDER_PERIODIC_REVIEW', 'SUPERSEDED', 'ARCHIVED')")
    List<DataPrivacyImpactAssessment> findDpiasWithReviewDue(@Param("date") LocalDateTime date);

    /**
     * Find DPIAs requiring supervisory consultation
     */
    @Query("SELECT d FROM DataPrivacyImpactAssessment d " +
           "WHERE d.externalConsultationRequired = true " +
           "AND (d.supervisoryAuthorityConsulted = false OR d.supervisoryAuthorityConsulted IS NULL)")
    List<DataPrivacyImpactAssessment> findRequiringAuthorityConsultation();

    /**
     * Find DPIAs without DPO consultation
     */
    @Query("SELECT d FROM DataPrivacyImpactAssessment d " +
           "WHERE (d.dpoConsulted = false OR d.dpoConsulted IS NULL) " +
           "AND d.status NOT IN ('INITIATED', 'DRAFT')")
    List<DataPrivacyImpactAssessment> findWithoutDpoConsultation();

    /**
     * Find DPIAs by conclusion
     */
    List<DataPrivacyImpactAssessment> findByConclusion(DpiaConclusion conclusion);

    /**
     * Find DPIAs where processing may not proceed
     */
    @Query("SELECT d FROM DataPrivacyImpactAssessment d " +
           "WHERE d.processingMayProceed = false")
    List<DataPrivacyImpactAssessment> findBlockedProcessing();

    /**
     * Find DPIAs with high/critical risk
     */
    @Query("SELECT d FROM DataPrivacyImpactAssessment d " +
           "WHERE d.overallRiskLevel IN ('HIGH', 'CRITICAL')")
    List<DataPrivacyImpactAssessment> findHighRiskDpias();

    /**
     * Find DPIAs involving special category data
     */
    @Query("SELECT d FROM DataPrivacyImpactAssessment d " +
           "WHERE d.involvesSpecialCategoryData = true")
    List<DataPrivacyImpactAssessment> findInvolvingSpecialCategoryData();

    /**
     * Find DPIAs involving automated decisions
     */
    @Query("SELECT d FROM DataPrivacyImpactAssessment d " +
           "WHERE d.involvesAutomatedDecisions = true")
    List<DataPrivacyImpactAssessment> findInvolvingAutomatedDecisions();

    /**
     * Find DPIAs involving large-scale processing
     */
    @Query("SELECT d FROM DataPrivacyImpactAssessment d " +
           "WHERE d.largeScaleProcessing = true")
    List<DataPrivacyImpactAssessment> findLargeScaleProcessing();

    /**
     * Find DPIAs prepared by specific person
     */
    List<DataPrivacyImpactAssessment> findByPreparedByOrderByPreparationDateDesc(String preparedBy);

    /**
     * Find DPIAs approved by specific person
     */
    List<DataPrivacyImpactAssessment> findByApprovedByOrderByApprovalDateDesc(String approvedBy);

    /**
     * Find DPIAs created in date range
     */
    List<DataPrivacyImpactAssessment> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Find recent DPIAs
     */
    @Query("SELECT d FROM DataPrivacyImpactAssessment d " +
           "WHERE d.createdAt >= :since " +
           "ORDER BY d.createdAt DESC")
    List<DataPrivacyImpactAssessment> findRecentDpias(@Param("since") LocalDateTime since);

    /**
     * Count DPIAs by status
     */
    long countByStatus(DpiaStatus status);

    /**
     * Count DPIAs by risk level
     */
    @Query("SELECT COUNT(d) FROM DataPrivacyImpactAssessment d " +
           "WHERE d.overallRiskLevel = :riskLevel")
    long countByRiskLevel(@Param("riskLevel") RiskLevel riskLevel);

    /**
     * Find DPIAs by methodology
     */
    List<DataPrivacyImpactAssessment> findByMethodologyUsed(String methodology);
}
