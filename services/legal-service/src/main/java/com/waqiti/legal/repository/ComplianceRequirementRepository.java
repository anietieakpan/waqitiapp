package com.waqiti.legal.repository;

import com.waqiti.legal.domain.ComplianceRequirement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Compliance Requirement Repository
 *
 * Complete data access layer for ComplianceRequirement entities with custom query methods
 * Supports multi-jurisdiction regulatory compliance tracking and assessment
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface ComplianceRequirementRepository extends JpaRepository<ComplianceRequirement, UUID> {

    /**
     * Find requirement by requirement ID
     */
    Optional<ComplianceRequirement> findByRequirementId(String requirementId);

    /**
     * Find requirements by type
     */
    List<ComplianceRequirement> findByRequirementType(ComplianceRequirement.RequirementType requirementType);

    /**
     * Find requirements by regulatory framework
     */
    List<ComplianceRequirement> findByRegulatoryFramework(String regulatoryFramework);

    /**
     * Find requirements by jurisdiction
     */
    List<ComplianceRequirement> findByJurisdiction(String jurisdiction);

    /**
     * Find all active requirements
     */
    List<ComplianceRequirement> findByIsActiveTrue();

    /**
     * Find all inactive requirements
     */
    List<ComplianceRequirement> findByIsActiveFalse();

    /**
     * Find mandatory requirements
     */
    List<ComplianceRequirement> findByMandatoryTrue();

    /**
     * Find optional requirements
     */
    List<ComplianceRequirement> findByMandatoryFalse();

    /**
     * Find requirements by severity level
     */
    List<ComplianceRequirement> findBySeverityLevel(ComplianceRequirement.SeverityLevel severityLevel);

    /**
     * Find critical and high severity requirements
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.severityLevel IN ('CRITICAL', 'HIGH') " +
           "AND r.isActive = true " +
           "ORDER BY r.severityLevel DESC, r.effectiveDate DESC")
    List<ComplianceRequirement> findCriticalAndHighSeverityRequirements();

    /**
     * Find implemented requirements
     */
    List<ComplianceRequirement> findByImplementedTrue();

    /**
     * Find not implemented requirements
     */
    List<ComplianceRequirement> findByImplementedFalse();

    /**
     * Find requirements with overdue implementation
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.implemented = false " +
           "AND r.implementationDeadline IS NOT NULL " +
           "AND r.implementationDeadline < :currentDate " +
           "AND r.isActive = true")
    List<ComplianceRequirement> findOverdueImplementation(@Param("currentDate") LocalDate currentDate);

    /**
     * Find requirements approaching implementation deadline
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.implemented = false " +
           "AND r.implementationDeadline BETWEEN :startDate AND :endDate " +
           "AND r.isActive = true")
    List<ComplianceRequirement> findApproachingImplementationDeadline(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find requirements requiring review
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.nextReviewDate IS NOT NULL " +
           "AND r.nextReviewDate <= :thresholdDate " +
           "AND r.isActive = true")
    List<ComplianceRequirement> findRequiringReview(@Param("thresholdDate") LocalDate thresholdDate);

    /**
     * Find requirements by monitoring frequency
     */
    List<ComplianceRequirement> findByMonitoringFrequency(ComplianceRequirement.MonitoringFrequency frequency);

    /**
     * Find requirements by last assessment result
     */
    List<ComplianceRequirement> findByLastAssessmentResult(ComplianceRequirement.AssessmentResult result);

    /**
     * Find non-compliant requirements
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.lastAssessmentResult IN ('NON_COMPLIANT', 'PARTIALLY_COMPLIANT') " +
           "AND r.isActive = true " +
           "ORDER BY r.severityLevel DESC")
    List<ComplianceRequirement> findNonCompliantRequirements();

    /**
     * Find compliant requirements
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.lastAssessmentResult = 'COMPLIANT' " +
           "AND r.isActive = true")
    List<ComplianceRequirement> findCompliantRequirements();

    /**
     * Find requirements never assessed
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.lastAssessmentDate IS NULL " +
           "OR r.lastAssessmentResult = 'NOT_ASSESSED' " +
           "AND r.isActive = true")
    List<ComplianceRequirement> findNeverAssessed();

    /**
     * Find requirements with overdue assessment
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.isActive = true " +
           "AND ((r.lastAssessmentDate IS NULL AND r.effectiveDate < :thresholdDate) " +
           "OR (r.lastAssessmentDate IS NOT NULL AND r.lastAssessmentDate < :thresholdDate))")
    List<ComplianceRequirement> findOverdueAssessment(@Param("thresholdDate") LocalDate thresholdDate);

    /**
     * Find requirements by responsible party
     */
    List<ComplianceRequirement> findByResponsibleParty(String responsibleParty);

    /**
     * Find requirements requiring external auditor
     */
    List<ComplianceRequirement> findByExternalAuditorRequiredTrue();

    /**
     * Find requirements requiring certification
     */
    List<ComplianceRequirement> findByCertificationRequiredTrue();

    /**
     * Find requirements by certification type
     */
    List<ComplianceRequirement> findByCertificationType(String certificationType);

    /**
     * Find requirements with escalation required
     */
    List<ComplianceRequirement> findByEscalationRequiredTrue();

    /**
     * Find superseded requirements
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.supersededBy IS NOT NULL")
    List<ComplianceRequirement> findSupersededRequirements();

    /**
     * Find requirements by control reference
     */
    List<ComplianceRequirement> findByControlReference(String controlReference);

    /**
     * Find requirements effective within date range
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.effectiveDate BETWEEN :startDate AND :endDate " +
           "ORDER BY r.effectiveDate DESC")
    List<ComplianceRequirement> findByEffectiveDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find requirements revised within date range
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.revisionDate BETWEEN :startDate AND :endDate " +
           "ORDER BY r.revisionDate DESC")
    List<ComplianceRequirement> findByRevisionDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Count requirements by type
     */
    long countByRequirementType(ComplianceRequirement.RequirementType requirementType);

    /**
     * Count active requirements
     */
    long countByIsActiveTrue();

    /**
     * Count implemented requirements
     */
    long countByImplementedTrue();

    /**
     * Count requirements by severity level
     */
    long countBySeverityLevel(ComplianceRequirement.SeverityLevel severityLevel);

    /**
     * Count non-compliant requirements
     */
    @Query("SELECT COUNT(r) FROM ComplianceRequirement r WHERE r.lastAssessmentResult IN ('NON_COMPLIANT', 'PARTIALLY_COMPLIANT') " +
           "AND r.isActive = true")
    long countNonCompliantRequirements();

    /**
     * Check if requirement ID exists
     */
    boolean existsByRequirementId(String requirementId);

    /**
     * Check if requirement exists for framework and jurisdiction
     */
    boolean existsByRegulatoryFrameworkAndJurisdiction(String regulatoryFramework, String jurisdiction);

    /**
     * Find requirements requiring immediate attention
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.isActive = true " +
           "AND ((r.severityLevel IN ('CRITICAL', 'HIGH') AND r.implemented = false) " +
           "OR (r.implementationDeadline IS NOT NULL AND r.implementationDeadline < :currentDate AND r.implemented = false) " +
           "OR (r.lastAssessmentResult IN ('NON_COMPLIANT', 'PARTIALLY_COMPLIANT')) " +
           "OR r.escalationRequired = true) " +
           "ORDER BY r.severityLevel DESC, r.implementationDeadline ASC")
    List<ComplianceRequirement> findRequiringImmediateAttention(@Param("currentDate") LocalDate currentDate);

    /**
     * Search requirements by name
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE LOWER(r.requirementName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<ComplianceRequirement> searchByRequirementName(@Param("searchTerm") String searchTerm);

    /**
     * Find requirements by multiple jurisdictions
     */
    @Query("SELECT r FROM ComplianceRequirement r WHERE r.jurisdiction IN :jurisdictions " +
           "AND r.isActive = true")
    List<ComplianceRequirement> findByJurisdictionIn(@Param("jurisdictions") List<String> jurisdictions);

    /**
     * Find requirements by created by user
     */
    List<ComplianceRequirement> findByCreatedBy(String createdBy);

    /**
     * Calculate compliance score for active requirements
     */
    @Query("SELECT CAST(COUNT(CASE WHEN r.lastAssessmentResult = 'COMPLIANT' THEN 1 END) AS DOUBLE) * 100.0 / COUNT(*) " +
           "FROM ComplianceRequirement r WHERE r.isActive = true AND r.implemented = true")
    Double calculateComplianceScore();

    /**
     * Get compliance summary by framework
     */
    @Query("SELECT r.regulatoryFramework, COUNT(*), " +
           "COUNT(CASE WHEN r.implemented = true THEN 1 END), " +
           "COUNT(CASE WHEN r.lastAssessmentResult = 'COMPLIANT' THEN 1 END) " +
           "FROM ComplianceRequirement r WHERE r.isActive = true " +
           "GROUP BY r.regulatoryFramework")
    List<Object[]> getComplianceSummaryByFramework();
}
