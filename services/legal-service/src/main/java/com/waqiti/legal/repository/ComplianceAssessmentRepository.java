package com.waqiti.legal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Compliance Assessment Repository
 *
 * Complete data access layer for ComplianceAssessment entities with custom query methods
 * Supports compliance audits, assessments, and findings tracking
 *
 * Note: This repository is designed for a ComplianceAssessment entity that should be created
 * to track compliance assessments and audits
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface ComplianceAssessmentRepository extends JpaRepository<ComplianceAssessment, UUID> {

    /**
     * Find assessment by assessment ID
     */
    Optional<ComplianceAssessment> findByAssessmentId(String assessmentId);

    /**
     * Find assessments by requirement ID
     */
    List<ComplianceAssessment> findByRequirementId(String requirementId);

    /**
     * Find assessments by assessment type
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.assessmentType = :assessmentType")
    List<ComplianceAssessment> findByAssessmentType(@Param("assessmentType") String assessmentType);

    /**
     * Find assessments by status
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.status = :status")
    List<ComplianceAssessment> findByStatus(@Param("status") String status);

    /**
     * Find assessments by result
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.assessmentResult = :result")
    List<ComplianceAssessment> findByAssessmentResult(@Param("result") String result);

    /**
     * Find pending assessments
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.status = 'PENDING' OR a.status = 'IN_PROGRESS'")
    List<ComplianceAssessment> findPendingAssessments();

    /**
     * Find completed assessments
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.status = 'COMPLETED' AND a.completedAt IS NOT NULL")
    List<ComplianceAssessment> findCompletedAssessments();

    /**
     * Find assessments by assessor
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.assessorId = :assessorId")
    List<ComplianceAssessment> findByAssessorId(@Param("assessorId") String assessorId);

    /**
     * Find assessments by reviewer
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.reviewerId = :reviewerId")
    List<ComplianceAssessment> findByReviewerId(@Param("reviewerId") String reviewerId);

    /**
     * Find non-compliant assessments
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.assessmentResult IN ('NON_COMPLIANT', 'PARTIALLY_COMPLIANT') " +
           "ORDER BY a.severityLevel DESC, a.completedAt DESC")
    List<ComplianceAssessment> findNonCompliantAssessments();

    /**
     * Find assessments with findings
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.findingsCount > 0")
    List<ComplianceAssessment> findAssessmentsWithFindings();

    /**
     * Find assessments by severity level
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.severityLevel = :severityLevel")
    List<ComplianceAssessment> findBySeverityLevel(@Param("severityLevel") String severityLevel);

    /**
     * Find critical assessments
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.severityLevel = 'CRITICAL' " +
           "AND a.assessmentResult IN ('NON_COMPLIANT', 'PARTIALLY_COMPLIANT')")
    List<ComplianceAssessment> findCriticalAssessments();

    /**
     * Find assessments scheduled within date range
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.scheduledDate BETWEEN :startDate AND :endDate " +
           "ORDER BY a.scheduledDate ASC")
    List<ComplianceAssessment> findByScheduledDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find overdue assessments
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.dueDate < :currentDate " +
           "AND a.status NOT IN ('COMPLETED', 'CANCELLED') " +
           "ORDER BY a.dueDate ASC")
    List<ComplianceAssessment> findOverdueAssessments(@Param("currentDate") LocalDate currentDate);

    /**
     * Find assessments approaching due date
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.dueDate BETWEEN :startDate AND :endDate " +
           "AND a.status NOT IN ('COMPLETED', 'CANCELLED')")
    List<ComplianceAssessment> findAssessmentsApproachingDueDate(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find assessments completed within date range
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.completedAt BETWEEN :startDateTime AND :endDateTime " +
           "ORDER BY a.completedAt DESC")
    List<ComplianceAssessment> findByCompletedAtBetween(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Find assessments by regulatory framework
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.regulatoryFramework = :framework")
    List<ComplianceAssessment> findByRegulatoryFramework(@Param("framework") String framework);

    /**
     * Find assessments by jurisdiction
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.jurisdiction = :jurisdiction")
    List<ComplianceAssessment> findByJurisdiction(@Param("jurisdiction") String jurisdiction);

    /**
     * Find external audits
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.externalAudit = true")
    List<ComplianceAssessment> findExternalAudits();

    /**
     * Find internal audits
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.externalAudit = false OR a.externalAudit IS NULL")
    List<ComplianceAssessment> findInternalAudits();

    /**
     * Find assessments requiring remediation
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.remediationRequired = true " +
           "AND (a.remediationCompleted IS NULL OR a.remediationCompleted = false)")
    List<ComplianceAssessment> findRequiringRemediation();

    /**
     * Find assessments with completed remediation
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.remediationRequired = true " +
           "AND a.remediationCompleted = true")
    List<ComplianceAssessment> findWithCompletedRemediation();

    /**
     * Find assessments by scope
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.assessmentScope = :scope")
    List<ComplianceAssessment> findByAssessmentScope(@Param("scope") String scope);

    /**
     * Find assessments requiring follow-up
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.followUpRequired = true " +
           "AND a.followUpCompleted = false")
    List<ComplianceAssessment> findRequiringFollowUp();

    /**
     * Find assessments by compliance score range
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.complianceScore BETWEEN :minScore AND :maxScore " +
           "ORDER BY a.complianceScore ASC")
    List<ComplianceAssessment> findByComplianceScoreBetween(
        @Param("minScore") Integer minScore,
        @Param("maxScore") Integer maxScore
    );

    /**
     * Find assessments with low compliance scores (below threshold)
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.complianceScore < :threshold " +
           "ORDER BY a.complianceScore ASC")
    List<ComplianceAssessment> findLowComplianceScores(@Param("threshold") Integer threshold);

    /**
     * Find assessments by created by user
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.createdBy = :createdBy")
    List<ComplianceAssessment> findByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Count assessments by status
     */
    @Query("SELECT COUNT(a) FROM ComplianceAssessment a WHERE a.status = :status")
    long countByStatus(@Param("status") String status);

    /**
     * Count assessments by result
     */
    @Query("SELECT COUNT(a) FROM ComplianceAssessment a WHERE a.assessmentResult = :result")
    long countByAssessmentResult(@Param("result") String result);

    /**
     * Count pending assessments
     */
    @Query("SELECT COUNT(a) FROM ComplianceAssessment a WHERE a.status IN ('PENDING', 'IN_PROGRESS')")
    long countPendingAssessments();

    /**
     * Count non-compliant assessments
     */
    @Query("SELECT COUNT(a) FROM ComplianceAssessment a WHERE a.assessmentResult IN ('NON_COMPLIANT', 'PARTIALLY_COMPLIANT')")
    long countNonCompliantAssessments();

    /**
     * Check if assessment exists for requirement
     */
    boolean existsByRequirementId(String requirementId);

    /**
     * Check if recent assessment exists for requirement (within days)
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM ComplianceAssessment a " +
           "WHERE a.requirementId = :requirementId " +
           "AND a.completedAt >= :thresholdDate")
    boolean hasRecentAssessment(
        @Param("requirementId") String requirementId,
        @Param("thresholdDate") LocalDateTime thresholdDate
    );

    /**
     * Find latest assessment for requirement
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE a.requirementId = :requirementId " +
           "ORDER BY a.completedAt DESC, a.createdAt DESC")
    Optional<ComplianceAssessment> findLatestAssessmentForRequirement(@Param("requirementId") String requirementId);

    /**
     * Calculate average compliance score for framework
     */
    @Query("SELECT AVG(a.complianceScore) FROM ComplianceAssessment a " +
           "WHERE a.regulatoryFramework = :framework " +
           "AND a.status = 'COMPLETED' " +
           "AND a.complianceScore IS NOT NULL")
    Double calculateAverageComplianceScoreForFramework(@Param("framework") String framework);

    /**
     * Calculate average compliance score by assessor
     */
    @Query("SELECT AVG(a.complianceScore) FROM ComplianceAssessment a " +
           "WHERE a.assessorId = :assessorId " +
           "AND a.status = 'COMPLETED' " +
           "AND a.complianceScore IS NOT NULL")
    Double calculateAverageComplianceScoreByAssessor(@Param("assessorId") String assessorId);

    /**
     * Find assessments requiring immediate attention
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE " +
           "(a.status IN ('PENDING', 'IN_PROGRESS') AND a.dueDate < :currentDate) " +
           "OR (a.severityLevel = 'CRITICAL' AND a.assessmentResult IN ('NON_COMPLIANT', 'PARTIALLY_COMPLIANT')) " +
           "OR (a.remediationRequired = true AND a.remediationCompleted = false AND a.remediationDueDate < :currentDate) " +
           "ORDER BY a.severityLevel DESC, a.dueDate ASC")
    List<ComplianceAssessment> findRequiringImmediateAttention(@Param("currentDate") LocalDate currentDate);

    /**
     * Search assessments by title or description
     */
    @Query("SELECT a FROM ComplianceAssessment a WHERE LOWER(a.assessmentTitle) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<ComplianceAssessment> searchAssessments(@Param("searchTerm") String searchTerm);

    /**
     * Get assessment statistics by framework
     */
    @Query("SELECT a.regulatoryFramework, COUNT(*), " +
           "COUNT(CASE WHEN a.assessmentResult = 'COMPLIANT' THEN 1 END), " +
           "AVG(a.complianceScore) " +
           "FROM ComplianceAssessment a WHERE a.status = 'COMPLETED' " +
           "GROUP BY a.regulatoryFramework")
    List<Object[]> getAssessmentStatisticsByFramework();
}

/**
 * Placeholder class for ComplianceAssessment entity
 * This should be created as a proper domain entity in com.waqiti.legal.domain package
 */
class ComplianceAssessment {
    private UUID id;
    private String assessmentId;
    private String requirementId;
    private String assessmentType;
    private String status;
    private String assessmentResult;
    private String assessorId;
    private String reviewerId;
    private Integer findingsCount;
    private String severityLevel;
    private LocalDate scheduledDate;
    private LocalDate dueDate;
    private LocalDateTime completedAt;
    private String regulatoryFramework;
    private String jurisdiction;
    private Boolean externalAudit;
    private Boolean remediationRequired;
    private Boolean remediationCompleted;
    private LocalDate remediationDueDate;
    private String assessmentScope;
    private Boolean followUpRequired;
    private Boolean followUpCompleted;
    private Integer complianceScore;
    private String createdBy;
    private String assessmentTitle;
    private String description;
}
