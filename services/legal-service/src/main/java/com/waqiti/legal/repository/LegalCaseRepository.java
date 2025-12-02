package com.waqiti.legal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legal Case Repository
 *
 * Complete data access layer for LegalCase entities with custom query methods
 * Supports litigation, disputes, and legal case management
 *
 * Note: This repository is designed for a LegalCase entity that should be created
 * to track litigation and legal disputes
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface LegalCaseRepository extends JpaRepository<LegalCase, UUID> {

    /**
     * Find case by case ID
     */
    Optional<LegalCase> findByCaseId(String caseId);

    /**
     * Find case by case number
     */
    Optional<LegalCase> findByCaseNumber(String caseNumber);

    /**
     * Find cases by case type
     */
    @Query("SELECT c FROM LegalCase c WHERE c.caseType = :caseType")
    List<LegalCase> findByCaseType(@Param("caseType") String caseType);

    /**
     * Find cases by status
     */
    @Query("SELECT c FROM LegalCase c WHERE c.caseStatus = :status")
    List<LegalCase> findByCaseStatus(@Param("status") String status);

    /**
     * Find active cases
     */
    @Query("SELECT c FROM LegalCase c WHERE c.caseStatus IN ('FILED', 'DISCOVERY', 'PRE_TRIAL', 'TRIAL') " +
           "ORDER BY c.filingDate DESC")
    List<LegalCase> findActiveCases();

    /**
     * Find closed cases
     */
    @Query("SELECT c FROM LegalCase c WHERE c.caseStatus IN ('SETTLED', 'DISMISSED', 'JUDGMENT_ENTERED', 'CLOSED')")
    List<LegalCase> findClosedCases();

    /**
     * Find cases by plaintiff
     */
    @Query("SELECT c FROM LegalCase c WHERE c.plaintiffId = :plaintiffId")
    List<LegalCase> findByPlaintiffId(@Param("plaintiffId") String plaintiffId);

    /**
     * Find cases by defendant
     */
    @Query("SELECT c FROM LegalCase c WHERE c.defendantId = :defendantId")
    List<LegalCase> findByDefendantId(@Param("defendantId") String defendantId);

    /**
     * Find cases where Waqiti is plaintiff
     */
    @Query("SELECT c FROM LegalCase c WHERE c.waqitiRole = 'PLAINTIFF'")
    List<LegalCase> findCasesWhereWaqitiIsPlaintiff();

    /**
     * Find cases where Waqiti is defendant
     */
    @Query("SELECT c FROM LegalCase c WHERE c.waqitiRole = 'DEFENDANT'")
    List<LegalCase> findCasesWhereWaqitiIsDefendant();

    /**
     * Find cases by court
     */
    @Query("SELECT c FROM LegalCase c WHERE c.court = :court")
    List<LegalCase> findByCourt(@Param("court") String court);

    /**
     * Find cases by jurisdiction
     */
    @Query("SELECT c FROM LegalCase c WHERE c.jurisdiction = :jurisdiction")
    List<LegalCase> findByJurisdiction(@Param("jurisdiction") String jurisdiction);

    /**
     * Find cases by judge
     */
    @Query("SELECT c FROM LegalCase c WHERE c.judgeName = :judgeName")
    List<LegalCase> findByJudgeName(@Param("judgeName") String judgeName);

    /**
     * Find cases by assigned attorney
     */
    @Query("SELECT c FROM LegalCase c WHERE c.assignedAttorneyId = :attorneyId")
    List<LegalCase> findByAssignedAttorneyId(@Param("attorneyId") String attorneyId);

    /**
     * Find cases by law firm
     */
    @Query("SELECT c FROM LegalCase c WHERE c.lawFirm = :lawFirm")
    List<LegalCase> findByLawFirm(@Param("lawFirm") String lawFirm);

    /**
     * Find cases filed within date range
     */
    @Query("SELECT c FROM LegalCase c WHERE c.filingDate BETWEEN :startDate AND :endDate " +
           "ORDER BY c.filingDate DESC")
    List<LegalCase> findByFilingDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find cases with upcoming hearings
     */
    @Query("SELECT c FROM LegalCase c WHERE c.nextHearingDate IS NOT NULL " +
           "AND c.nextHearingDate >= :startDate " +
           "AND c.nextHearingDate <= :endDate " +
           "ORDER BY c.nextHearingDate ASC")
    List<LegalCase> findCasesWithUpcomingHearings(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find cases with overdue deadlines
     */
    @Query("SELECT c FROM LegalCase c WHERE c.nextDeadline IS NOT NULL " +
           "AND c.nextDeadline < :currentDate " +
           "AND c.caseStatus NOT IN ('SETTLED', 'DISMISSED', 'JUDGMENT_ENTERED', 'CLOSED')")
    List<LegalCase> findCasesWithOverdueDeadlines(@Param("currentDate") LocalDate currentDate);

    /**
     * Find cases approaching deadline
     */
    @Query("SELECT c FROM LegalCase c WHERE c.nextDeadline BETWEEN :startDate AND :endDate " +
           "AND c.caseStatus NOT IN ('SETTLED', 'DISMISSED', 'JUDGMENT_ENTERED', 'CLOSED') " +
           "ORDER BY c.nextDeadline ASC")
    List<LegalCase> findCasesApproachingDeadline(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find cases by priority level
     */
    @Query("SELECT c FROM LegalCase c WHERE c.priorityLevel = :priority " +
           "ORDER BY c.filingDate DESC")
    List<LegalCase> findByPriorityLevel(@Param("priority") String priority);

    /**
     * Find high priority cases
     */
    @Query("SELECT c FROM LegalCase c WHERE c.priorityLevel IN ('HIGH', 'CRITICAL') " +
           "AND c.caseStatus NOT IN ('SETTLED', 'DISMISSED', 'JUDGMENT_ENTERED', 'CLOSED') " +
           "ORDER BY c.priorityLevel DESC, c.nextDeadline ASC")
    List<LegalCase> findHighPriorityCases();

    /**
     * Find cases with amount in controversy greater than specified
     */
    @Query("SELECT c FROM LegalCase c WHERE c.amountInControversy >= :minAmount " +
           "ORDER BY c.amountInControversy DESC")
    List<LegalCase> findByAmountInControversyGreaterThan(@Param("minAmount") BigDecimal minAmount);

    /**
     * Find settled cases
     */
    @Query("SELECT c FROM LegalCase c WHERE c.caseStatus = 'SETTLED' " +
           "AND c.settlementDate IS NOT NULL")
    List<LegalCase> findSettledCases();

    /**
     * Find cases settled within date range
     */
    @Query("SELECT c FROM LegalCase c WHERE c.settlementDate BETWEEN :startDate AND :endDate " +
           "ORDER BY c.settlementDate DESC")
    List<LegalCase> findBySettlementDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find cases with judgment entered
     */
    @Query("SELECT c FROM LegalCase c WHERE c.judgmentEntered = true " +
           "AND c.judgmentDate IS NOT NULL")
    List<LegalCase> findCasesWithJudgment();

    /**
     * Find favorable judgments
     */
    @Query("SELECT c FROM LegalCase c WHERE c.judgmentEntered = true " +
           "AND c.judgmentFavorable = true")
    List<LegalCase> findFavorableJudgments();

    /**
     * Find unfavorable judgments
     */
    @Query("SELECT c FROM LegalCase c WHERE c.judgmentEntered = true " +
           "AND c.judgmentFavorable = false")
    List<LegalCase> findUnfavorableJudgments();

    /**
     * Find cases under appeal
     */
    @Query("SELECT c FROM LegalCase c WHERE c.underAppeal = true")
    List<LegalCase> findCasesUnderAppeal();

    /**
     * Find cases by discovery status
     */
    @Query("SELECT c FROM LegalCase c WHERE c.discoveryStatus = :status")
    List<LegalCase> findByDiscoveryStatus(@Param("status") String status);

    /**
     * Find cases with pending motions
     */
    @Query("SELECT c FROM LegalCase c WHERE c.pendingMotionsCount > 0 " +
           "ORDER BY c.pendingMotionsCount DESC")
    List<LegalCase> findCasesWithPendingMotions();

    /**
     * Find class action cases
     */
    @Query("SELECT c FROM LegalCase c WHERE c.classAction = true")
    List<LegalCase> findClassActionCases();

    /**
     * Find arbitration cases
     */
    @Query("SELECT c FROM LegalCase c WHERE c.arbitration = true")
    List<LegalCase> findArbitrationCases();

    /**
     * Find mediation cases
     */
    @Query("SELECT c FROM LegalCase c WHERE c.mediation = true")
    List<LegalCase> findMediationCases();

    /**
     * Count active cases
     */
    @Query("SELECT COUNT(c) FROM LegalCase c WHERE c.caseStatus IN ('FILED', 'DISCOVERY', 'PRE_TRIAL', 'TRIAL')")
    long countActiveCases();

    /**
     * Count cases by status
     */
    @Query("SELECT COUNT(c) FROM LegalCase c WHERE c.caseStatus = :status")
    long countByCaseStatus(@Param("status") String status);

    /**
     * Count cases by type
     */
    @Query("SELECT COUNT(c) FROM LegalCase c WHERE c.caseType = :caseType")
    long countByCaseType(@Param("caseType") String caseType);

    /**
     * Calculate total amount in controversy for active cases
     */
    @Query("SELECT COALESCE(SUM(c.amountInControversy), 0) FROM LegalCase c " +
           "WHERE c.caseStatus NOT IN ('SETTLED', 'DISMISSED', 'JUDGMENT_ENTERED', 'CLOSED')")
    BigDecimal calculateTotalAmountInControversy();

    /**
     * Calculate total settlement amounts
     */
    @Query("SELECT COALESCE(SUM(c.settlementAmount), 0) FROM LegalCase c " +
           "WHERE c.caseStatus = 'SETTLED' AND c.settlementAmount IS NOT NULL")
    BigDecimal calculateTotalSettlementAmounts();

    /**
     * Check if case number exists
     */
    boolean existsByCaseNumber(String caseNumber);

    /**
     * Find cases requiring immediate attention
     */
    @Query("SELECT c FROM LegalCase c WHERE " +
           "(c.nextDeadline IS NOT NULL AND c.nextDeadline <= :thresholdDate) " +
           "OR (c.priorityLevel IN ('HIGH', 'CRITICAL')) " +
           "OR (c.nextHearingDate IS NOT NULL AND c.nextHearingDate BETWEEN :now AND :hearingThreshold) " +
           "AND c.caseStatus NOT IN ('SETTLED', 'DISMISSED', 'JUDGMENT_ENTERED', 'CLOSED') " +
           "ORDER BY c.priorityLevel DESC, c.nextDeadline ASC")
    List<LegalCase> findCasesRequiringAttention(
        @Param("thresholdDate") LocalDate thresholdDate,
        @Param("now") LocalDateTime now,
        @Param("hearingThreshold") LocalDateTime hearingThreshold
    );

    /**
     * Search cases by case name
     */
    @Query("SELECT c FROM LegalCase c WHERE LOWER(c.caseName) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<LegalCase> searchByCaseName(@Param("searchTerm") String searchTerm);

    /**
     * Find cases by created by user
     */
    @Query("SELECT c FROM LegalCase c WHERE c.createdBy = :createdBy")
    List<LegalCase> findByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Get case statistics by type
     */
    @Query("SELECT c.caseType, COUNT(*), " +
           "COUNT(CASE WHEN c.caseStatus IN ('SETTLED', 'DISMISSED', 'JUDGMENT_ENTERED', 'CLOSED') THEN 1 END), " +
           "COUNT(CASE WHEN c.judgmentFavorable = true THEN 1 END) " +
           "FROM LegalCase c " +
           "GROUP BY c.caseType")
    List<Object[]> getCaseStatisticsByType();
}

/**
 * Placeholder class for LegalCase entity
 * This should be created as a proper domain entity in com.waqiti.legal.domain package
 */
class LegalCase {
    private UUID id;
    private String caseId;
    private String caseNumber;
    private String caseName;
    private String caseType;
    private String caseStatus;
    private String plaintiffId;
    private String defendantId;
    private String waqitiRole;
    private String court;
    private String jurisdiction;
    private String judgeName;
    private String assignedAttorneyId;
    private String lawFirm;
    private LocalDate filingDate;
    private LocalDateTime nextHearingDate;
    private LocalDate nextDeadline;
    private String priorityLevel;
    private BigDecimal amountInControversy;
    private LocalDate settlementDate;
    private BigDecimal settlementAmount;
    private Boolean judgmentEntered;
    private LocalDate judgmentDate;
    private Boolean judgmentFavorable;
    private Boolean underAppeal;
    private String discoveryStatus;
    private Integer pendingMotionsCount;
    private Boolean classAction;
    private Boolean arbitration;
    private Boolean mediation;
    private String createdBy;
}
