package com.waqiti.compliance.repository;

import com.waqiti.compliance.entity.FinancialCrimeCase;
import com.waqiti.compliance.enums.CrimeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Financial Crime Case Repository
 *
 * Data access layer for financial crime cases with custom queries
 * for reporting, investigation, and regulatory compliance.
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Repository
public interface FinancialCrimeCaseRepository extends JpaRepository<FinancialCrimeCase, String> {

    /**
     * Find all cases by user ID
     *
     * @param userId user ID
     * @return list of cases
     */
    List<FinancialCrimeCase> findByUserId(String userId);

    /**
     * Find all cases by crime type
     *
     * @param crimeType crime type
     * @return list of cases
     */
    List<FinancialCrimeCase> findByCrimeType(CrimeType crimeType);

    /**
     * Find all cases by status
     *
     * @param status status string
     * @return list of cases
     */
    List<FinancialCrimeCase> findByStatus(String status);

    /**
     * Find all cases requiring SAR filing
     *
     * @return list of cases requiring SAR
     */
    @Query("SELECT f FROM FinancialCrimeCase f WHERE f.sarRequired = true AND f.sarFiled = false")
    List<FinancialCrimeCase> findCasesRequiringSAR();

    /**
     * Find all cases requiring law enforcement notification
     *
     * @return list of cases requiring notification
     */
    @Query("SELECT f FROM FinancialCrimeCase f WHERE f.lawEnforcementNotified = false AND f.lawEnforcementNotificationRequired = true")
    List<FinancialCrimeCase> findCasesRequiringLawEnforcementNotification();

    /**
     * Find all active cases (not closed)
     *
     * @return list of active cases
     */
    @Query("SELECT f FROM FinancialCrimeCase f WHERE f.status != 'CLOSED'")
    List<FinancialCrimeCase> findActiveCases();

    /**
     * Find all cases created within date range
     *
     * @param startDate start date
     * @param endDate end date
     * @return list of cases
     */
    @Query("SELECT f FROM FinancialCrimeCase f WHERE f.createdAt BETWEEN :startDate AND :endDate ORDER BY f.createdAt DESC")
    List<FinancialCrimeCase> findCasesByDateRange(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find all critical cases (high severity crimes)
     *
     * @return list of critical cases
     */
    @Query("SELECT f FROM FinancialCrimeCase f WHERE f.crimeType IN ('TERRORISM_FINANCING', 'MONEY_LAUNDERING', 'SECURITIES_FRAUD', 'SANCTIONS_VIOLATION') AND f.status != 'CLOSED'")
    List<FinancialCrimeCase> findCriticalCases();

    /**
     * Find cases by assigned investigator
     *
     * @param assignedTo investigator ID
     * @return list of cases
     */
    List<FinancialCrimeCase> findByAssignedToOrderByCreatedAtDesc(String assignedTo);

    /**
     * Count cases by crime type
     *
     * @param crimeType crime type
     * @return count of cases
     */
    long countByCrimeType(CrimeType crimeType);

    /**
     * Count active cases by user
     *
     * @param userId user ID
     * @return count of active cases
     */
    @Query("SELECT COUNT(f) FROM FinancialCrimeCase f WHERE f.userId = :userId AND f.status != 'CLOSED'")
    long countActiveCasesByUser(@Param("userId") String userId);

    /**
     * Find cases with evidence preserved
     *
     * @return list of cases with evidence
     */
    @Query("SELECT f FROM FinancialCrimeCase f WHERE f.evidencePreserved = true")
    List<FinancialCrimeCase> findCasesWithEvidencePreserved();

    /**
     * Find cases by SAR reference number
     *
     * @param sarReferenceNumber SAR reference number
     * @return optional case
     */
    Optional<FinancialCrimeCase> findBySarReferenceNumber(String sarReferenceNumber);

    /**
     * Find all cases requiring follow-up
     *
     * @return list of cases requiring follow-up
     */
    @Query("SELECT f FROM FinancialCrimeCase f WHERE f.followUpRequired = true AND f.status != 'CLOSED'")
    List<FinancialCrimeCase> findCasesRequiringFollowUp();
}
