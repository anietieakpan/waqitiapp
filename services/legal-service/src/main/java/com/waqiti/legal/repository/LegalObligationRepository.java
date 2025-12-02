package com.waqiti.legal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legal Obligation Repository
 *
 * Complete data access layer for LegalObligation entities with custom query methods
 * Supports contractual obligations, regulatory obligations, and compliance tracking
 *
 * Note: This repository is designed for a LegalObligation entity that should be created
 * to track legal and contractual obligations
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface LegalObligationRepository extends JpaRepository<LegalObligation, UUID> {

    /**
     * Find obligation by obligation ID
     */
    Optional<LegalObligation> findByObligationId(String obligationId);

    /**
     * Find obligations by contract ID
     */
    List<LegalObligation> findByContractId(String contractId);

    /**
     * Find obligations by document ID
     */
    List<LegalObligation> findByDocumentId(String documentId);

    /**
     * Find obligations by obligation type
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.obligationType = :obligationType")
    List<LegalObligation> findByObligationType(@Param("obligationType") String obligationType);

    /**
     * Find obligations by status
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.status = :status")
    List<LegalObligation> findByStatus(@Param("status") String status);

    /**
     * Find active obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.status IN ('ACTIVE', 'PENDING', 'IN_PROGRESS') " +
           "ORDER BY o.dueDate ASC")
    List<LegalObligation> findActiveObligations();

    /**
     * Find completed obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.status = 'COMPLETED' " +
           "AND o.completedDate IS NOT NULL")
    List<LegalObligation> findCompletedObligations();

    /**
     * Find obligations by responsible party
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.responsiblePartyId = :partyId")
    List<LegalObligation> findByResponsiblePartyId(@Param("partyId") String partyId);

    /**
     * Find obligations by category
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.category = :category")
    List<LegalObligation> findByCategory(@Param("category") String category);

    /**
     * Find obligations by priority level
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.priorityLevel = :priority " +
           "ORDER BY o.dueDate ASC")
    List<LegalObligation> findByPriorityLevel(@Param("priority") String priority);

    /**
     * Find high priority obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.priorityLevel IN ('HIGH', 'CRITICAL') " +
           "AND o.status NOT IN ('COMPLETED', 'CANCELLED', 'WAIVED') " +
           "ORDER BY o.priorityLevel DESC, o.dueDate ASC")
    List<LegalObligation> findHighPriorityObligations();

    /**
     * Find overdue obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.dueDate < :currentDate " +
           "AND o.status NOT IN ('COMPLETED', 'CANCELLED', 'WAIVED') " +
           "ORDER BY o.dueDate ASC")
    List<LegalObligation> findOverdueObligations(@Param("currentDate") LocalDate currentDate);

    /**
     * Find obligations approaching due date
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.dueDate BETWEEN :startDate AND :endDate " +
           "AND o.status NOT IN ('COMPLETED', 'CANCELLED', 'WAIVED') " +
           "ORDER BY o.dueDate ASC")
    List<LegalObligation> findObligationsApproachingDueDate(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find recurring obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.recurring = true " +
           "AND o.status IN ('ACTIVE', 'PENDING')")
    List<LegalObligation> findRecurringObligations();

    /**
     * Find obligations by recurrence frequency
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.recurrenceFrequency = :frequency " +
           "AND o.recurring = true")
    List<LegalObligation> findByRecurrenceFrequency(@Param("frequency") String frequency);

    /**
     * Find monetary obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.monetaryObligation = true " +
           "AND o.obligationAmount IS NOT NULL " +
           "ORDER BY o.obligationAmount DESC")
    List<LegalObligation> findMonetaryObligations();

    /**
     * Find monetary obligations greater than amount
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.monetaryObligation = true " +
           "AND o.obligationAmount >= :minAmount " +
           "ORDER BY o.obligationAmount DESC")
    List<LegalObligation> findMonetaryObligationsGreaterThan(@Param("minAmount") BigDecimal minAmount);

    /**
     * Find performance obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.obligationType = 'PERFORMANCE' " +
           "AND o.status IN ('ACTIVE', 'PENDING', 'IN_PROGRESS')")
    List<LegalObligation> findPerformanceObligations();

    /**
     * Find reporting obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.obligationType = 'REPORTING' " +
           "AND o.status IN ('ACTIVE', 'PENDING')")
    List<LegalObligation> findReportingObligations();

    /**
     * Find compliance obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.obligationType = 'COMPLIANCE' " +
           "AND o.status IN ('ACTIVE', 'PENDING')")
    List<LegalObligation> findComplianceObligations();

    /**
     * Find obligations by assigned to user
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.assignedToUserId = :userId")
    List<LegalObligation> findByAssignedToUserId(@Param("userId") String userId);

    /**
     * Find obligations requiring approval
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.requiresApproval = true " +
           "AND o.approved = false")
    List<LegalObligation> findRequiringApproval();

    /**
     * Find approved obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.approved = true " +
           "AND o.approvedBy IS NOT NULL")
    List<LegalObligation> findApprovedObligations();

    /**
     * Find obligations with dependencies
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.hasDependencies = true")
    List<LegalObligation> findObligationsWithDependencies();

    /**
     * Find obligations dependent on another obligation
     */
    @Query("SELECT o FROM LegalObligation o WHERE :dependencyId MEMBER OF o.dependsOnObligationIds")
    List<LegalObligation> findDependentObligations(@Param("dependencyId") String dependencyId);

    /**
     * Find obligations with penalties for non-compliance
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.penaltyForNonCompliance IS NOT NULL " +
           "AND o.status NOT IN ('COMPLETED', 'CANCELLED', 'WAIVED')")
    List<LegalObligation> findObligationsWithPenalties();

    /**
     * Find obligations by effective date range
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.effectiveDate BETWEEN :startDate AND :endDate " +
           "ORDER BY o.effectiveDate ASC")
    List<LegalObligation> findByEffectiveDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find obligations due within date range
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.dueDate BETWEEN :startDate AND :endDate " +
           "ORDER BY o.dueDate ASC")
    List<LegalObligation> findByDueDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find obligations completed within date range
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.completedDate BETWEEN :startDate AND :endDate " +
           "ORDER BY o.completedDate DESC")
    List<LegalObligation> findByCompletedDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find waived obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.status = 'WAIVED' " +
           "AND o.waivedBy IS NOT NULL")
    List<LegalObligation> findWaivedObligations();

    /**
     * Find cancelled obligations
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.status = 'CANCELLED'")
    List<LegalObligation> findCancelledObligations();

    /**
     * Find obligations by jurisdiction
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.jurisdiction = :jurisdiction")
    List<LegalObligation> findByJurisdiction(@Param("jurisdiction") String jurisdiction);

    /**
     * Count obligations by status
     */
    @Query("SELECT COUNT(o) FROM LegalObligation o WHERE o.status = :status")
    long countByStatus(@Param("status") String status);

    /**
     * Count active obligations
     */
    @Query("SELECT COUNT(o) FROM LegalObligation o WHERE o.status IN ('ACTIVE', 'PENDING', 'IN_PROGRESS')")
    long countActiveObligations();

    /**
     * Count overdue obligations
     */
    @Query("SELECT COUNT(o) FROM LegalObligation o WHERE o.dueDate < :currentDate " +
           "AND o.status NOT IN ('COMPLETED', 'CANCELLED', 'WAIVED')")
    long countOverdueObligations(@Param("currentDate") LocalDate currentDate);

    /**
     * Count obligations by contract
     */
    @Query("SELECT COUNT(o) FROM LegalObligation o WHERE o.contractId = :contractId")
    long countByContractId(@Param("contractId") String contractId);

    /**
     * Calculate total monetary obligation amount
     */
    @Query("SELECT COALESCE(SUM(o.obligationAmount), 0) FROM LegalObligation o " +
           "WHERE o.monetaryObligation = true " +
           "AND o.status NOT IN ('COMPLETED', 'CANCELLED', 'WAIVED')")
    BigDecimal calculateTotalOutstandingMonetaryObligations();

    /**
     * Calculate total monetary obligations by contract
     */
    @Query("SELECT COALESCE(SUM(o.obligationAmount), 0) FROM LegalObligation o " +
           "WHERE o.contractId = :contractId " +
           "AND o.monetaryObligation = true " +
           "AND o.status NOT IN ('COMPLETED', 'CANCELLED', 'WAIVED')")
    BigDecimal calculateTotalMonetaryObligationsByContract(@Param("contractId") String contractId);

    /**
     * Check if obligation ID exists
     */
    boolean existsByObligationId(String obligationId);

    /**
     * Find obligations requiring immediate attention
     */
    @Query("SELECT o FROM LegalObligation o WHERE " +
           "(o.status NOT IN ('COMPLETED', 'CANCELLED', 'WAIVED')) " +
           "AND ((o.dueDate < :currentDate) " +
           "OR (o.priorityLevel IN ('HIGH', 'CRITICAL')) " +
           "OR (o.dueDate BETWEEN :currentDate AND :thresholdDate AND o.requiresApproval = true)) " +
           "ORDER BY o.priorityLevel DESC, o.dueDate ASC")
    List<LegalObligation> findRequiringImmediateAttention(
        @Param("currentDate") LocalDate currentDate,
        @Param("thresholdDate") LocalDate thresholdDate
    );

    /**
     * Search obligations by description
     */
    @Query("SELECT o FROM LegalObligation o WHERE LOWER(o.obligationDescription) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<LegalObligation> searchByDescription(@Param("searchTerm") String searchTerm);

    /**
     * Find obligations by created by user
     */
    @Query("SELECT o FROM LegalObligation o WHERE o.createdBy = :createdBy")
    List<LegalObligation> findByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Get obligation completion statistics
     */
    @Query("SELECT o.obligationType, COUNT(*), " +
           "COUNT(CASE WHEN o.status = 'COMPLETED' THEN 1 END), " +
           "COUNT(CASE WHEN o.dueDate < :currentDate AND o.status NOT IN ('COMPLETED', 'CANCELLED', 'WAIVED') THEN 1 END) " +
           "FROM LegalObligation o " +
           "GROUP BY o.obligationType")
    List<Object[]> getObligationStatisticsByType(@Param("currentDate") LocalDate currentDate);
}

/**
 * Placeholder class for LegalObligation entity
 * This should be created as a proper domain entity in com.waqiti.legal.domain package
 */
class LegalObligation {
    private UUID id;
    private String obligationId;
    private String contractId;
    private String documentId;
    private String obligationType;
    private String status;
    private String responsiblePartyId;
    private String category;
    private String priorityLevel;
    private LocalDate dueDate;
    private Boolean recurring;
    private String recurrenceFrequency;
    private Boolean monetaryObligation;
    private BigDecimal obligationAmount;
    private String assignedToUserId;
    private Boolean requiresApproval;
    private Boolean approved;
    private String approvedBy;
    private Boolean hasDependencies;
    private List<String> dependsOnObligationIds;
    private String penaltyForNonCompliance;
    private LocalDate effectiveDate;
    private LocalDate completedDate;
    private String waivedBy;
    private String jurisdiction;
    private String obligationDescription;
    private String createdBy;
}
