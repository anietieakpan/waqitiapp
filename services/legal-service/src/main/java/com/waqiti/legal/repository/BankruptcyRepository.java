package com.waqiti.legal.repository;

import com.waqiti.legal.domain.BankruptcyCase;
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
 * Bankruptcy Case Repository
 *
 * Complete data access layer for Bankruptcy entities with custom query methods
 * Supports Chapter 7, 11, 12, 13, and 15 bankruptcy proceedings
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface BankruptcyRepository extends JpaRepository<BankruptcyCase, UUID> {

    /**
     * Find bankruptcy case by bankruptcy ID
     */
    Optional<BankruptcyCase> findByBankruptcyId(String bankruptcyId);

    /**
     * Find bankruptcy case by case number
     */
    Optional<BankruptcyCase> findByCaseNumber(String caseNumber);

    /**
     * Find all bankruptcy cases for a customer
     */
    List<BankruptcyCase> findByCustomerId(String customerId);

    /**
     * Find all bankruptcy cases by chapter
     */
    List<BankruptcyCase> findByBankruptcyChapter(BankruptcyCase.BankruptcyChapter chapter);

    /**
     * Find all bankruptcy cases by status
     */
    List<BankruptcyCase> findByCaseStatus(BankruptcyCase.BankruptcyStatus status);

    /**
     * Find all active bankruptcy cases (not dismissed, discharged, or closed)
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.dismissed = false AND b.dischargeGranted = false " +
           "AND b.caseStatus NOT IN ('CLOSED', 'DISMISSED', 'DISCHARGED')")
    List<BankruptcyCase> findActiveCases();

    /**
     * Find cases with automatic stay currently active
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.automaticStayActive = true " +
           "AND b.automaticStayLiftedDate IS NULL " +
           "AND b.dismissed = false AND b.dischargeGranted = false")
    List<BankruptcyCase> findCasesWithActiveAutomaticStay();

    /**
     * Find cases by court district
     */
    List<BankruptcyCase> findByCourtDistrict(String courtDistrict);

    /**
     * Find cases by trustee name
     */
    List<BankruptcyCase> findByTrusteeName(String trusteeName);

    /**
     * Find cases where proof of claim has not been filed
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.proofOfClaimFiled = false " +
           "AND b.dismissed = false AND b.dischargeGranted = false")
    List<BankruptcyCase> findCasesWithoutProofOfClaim();

    /**
     * Find cases with overdue proof of claim deadlines
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.proofOfClaimFiled = false " +
           "AND b.proofOfClaimBarDate < :currentDate " +
           "AND b.dismissed = false")
    List<BankruptcyCase> findCasesWithOverdueProofOfClaim(@Param("currentDate") LocalDate currentDate);

    /**
     * Find cases approaching proof of claim bar date (within specified days)
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.proofOfClaimFiled = false " +
           "AND b.proofOfClaimBarDate BETWEEN :startDate AND :endDate " +
           "AND b.dismissed = false")
    List<BankruptcyCase> findCasesApproachingProofOfClaimDeadline(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find cases with 341 meeting scheduled but not attended
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.meeting341Scheduled = true " +
           "AND b.meeting341Attended = false " +
           "AND b.dismissed = false")
    List<BankruptcyCase> findCasesWithPending341Meeting();

    /**
     * Find cases where 341 meeting is overdue
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.meeting341Scheduled = true " +
           "AND b.meeting341Attended = false " +
           "AND b.meeting341Date < :currentDateTime " +
           "AND b.dismissed = false")
    List<BankruptcyCase> findCasesWithOverdue341Meeting(@Param("currentDateTime") LocalDateTime currentDateTime);

    /**
     * Find Chapter 13 cases with confirmed repayment plans
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.bankruptcyChapter = 'CHAPTER_13' " +
           "AND b.planConfirmed = true " +
           "AND b.dismissed = false AND b.dischargeGranted = false")
    List<BankruptcyCase> findChapter13CasesWithConfirmedPlans();

    /**
     * Find cases with stay relief motion filed
     */
    List<BankruptcyCase> findByStayReliefMotionFiledTrue();

    /**
     * Find cases with frozen accounts
     */
    List<BankruptcyCase> findByAccountsFrozenTrue();

    /**
     * Find discharged cases
     */
    List<BankruptcyCase> findByDischargeGrantedTrue();

    /**
     * Find dismissed cases
     */
    List<BankruptcyCase> findByDismissedTrue();

    /**
     * Find cases by assigned case manager
     */
    List<BankruptcyCase> findByAssignedTo(String userId);

    /**
     * Find cases filed within a date range
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.filingDate BETWEEN :startDate AND :endDate " +
           "ORDER BY b.filingDate DESC")
    List<BankruptcyCase> findByFilingDateBetween(
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate
    );

    /**
     * Find cases with Waqiti claim amount greater than specified amount
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.waqitiClaimAmount >= :minAmount " +
           "AND b.dismissed = false " +
           "ORDER BY b.waqitiClaimAmount DESC")
    List<BankruptcyCase> findCasesWithClaimAmountGreaterThan(@Param("minAmount") BigDecimal minAmount);

    /**
     * Find cases requiring credit reporting flag
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.creditReportingFlagged = false " +
           "AND b.dismissed = false " +
           "AND b.caseStatus NOT IN ('FILED', 'PENDING_REVIEW')")
    List<BankruptcyCase> findCasesRequiringCreditReportingFlag();

    /**
     * Find cases where departments have not been notified
     */
    List<BankruptcyCase> findByAllDepartmentsNotifiedFalse();

    /**
     * Find converted cases (converted from one chapter to another)
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.convertedToChapter IS NOT NULL")
    List<BankruptcyCase> findConvertedCases();

    /**
     * Find cases with reaffirmation agreement requested
     */
    List<BankruptcyCase> findByReaffirmationAgreementRequestedTrue();

    /**
     * Count active cases by chapter
     */
    @Query("SELECT COUNT(b) FROM BankruptcyCase b WHERE b.bankruptcyChapter = :chapter " +
           "AND b.dismissed = false AND b.dischargeGranted = false")
    long countActiveCasesByChapter(@Param("chapter") BankruptcyCase.BankruptcyChapter chapter);

    /**
     * Count cases with active automatic stay
     */
    @Query("SELECT COUNT(b) FROM BankruptcyCase b WHERE b.automaticStayActive = true " +
           "AND b.automaticStayLiftedDate IS NULL")
    long countCasesWithActiveAutomaticStay();

    /**
     * Check if case number exists
     */
    boolean existsByCaseNumber(String caseNumber);

    /**
     * Check if customer has any active bankruptcy cases
     */
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM BankruptcyCase b " +
           "WHERE b.customerId = :customerId " +
           "AND b.dismissed = false AND b.dischargeGranted = false")
    boolean hasActiveBankruptcy(@Param("customerId") String customerId);

    /**
     * Find cases requiring action (overdue deadlines or pending items)
     */
    @Query("SELECT b FROM BankruptcyCase b WHERE b.dismissed = false AND b.dischargeGranted = false " +
           "AND (b.proofOfClaimFiled = false AND b.proofOfClaimBarDate < :currentDate " +
           "OR b.meeting341Scheduled = true AND b.meeting341Attended = false AND b.meeting341Date < :currentDateTime " +
           "OR b.allDepartmentsNotified = false) " +
           "ORDER BY b.filingDate ASC")
    List<BankruptcyCase> findCasesRequiringAction(
        @Param("currentDate") LocalDate currentDate,
        @Param("currentDateTime") LocalDateTime currentDateTime
    );

    /**
     * Calculate total Waqiti claims across all active cases
     */
    @Query("SELECT COALESCE(SUM(b.waqitiClaimAmount), 0) FROM BankruptcyCase b " +
           "WHERE b.dismissed = false AND b.dischargeGranted = false")
    BigDecimal calculateTotalActiveClaims();

    /**
     * Calculate total recovery received across all cases
     */
    @Query("SELECT COALESCE(SUM(b.actualRecoveryAmount), 0) FROM BankruptcyCase b " +
           "WHERE b.actualRecoveryAmount IS NOT NULL")
    BigDecimal calculateTotalRecoveryReceived();

    /**
     * Find cases by judge name
     */
    List<BankruptcyCase> findByJudgeName(String judgeName);

    /**
     * Find cases by claim classification
     */
    List<BankruptcyCase> findByClaimClassification(String claimClassification);
}
