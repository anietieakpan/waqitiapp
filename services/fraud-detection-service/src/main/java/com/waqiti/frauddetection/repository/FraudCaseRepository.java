package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.FraudCase;
import com.waqiti.frauddetection.entity.FraudCase.CaseStatus;
import com.waqiti.frauddetection.entity.FraudCase.FraudCaseDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Fraud Case Repository
 *
 * Data access layer for fraud cases with optimized queries for fraud investigation,
 * ML model training data retrieval, and fraud analytics.
 *
 * PRODUCTION-GRADE QUERIES
 * - Indexed lookups for performance
 * - ML training data queries
 * - Fraud pattern analysis
 * - Investigation workflow support
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0
 */
@Repository
public interface FraudCaseRepository extends JpaRepository<FraudCase, UUID> {

    /**
     * Find case by unique case ID
     * Uses unique index on case_id
     */
    Optional<FraudCase> findByCaseId(String caseId);

    /**
     * Find case by transaction ID
     * Uses index on transaction_id
     */
    Optional<FraudCase> findByTransactionId(String transactionId);

    /**
     * Find all cases for a user
     * Uses index on user_id
     */
    List<FraudCase> findByUserId(String userId);

    /**
     * Find open cases (for investigation queue)
     * Uses index on status
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.status IN :statuses ORDER BY fc.finalRiskScore DESC, fc.createdAt ASC")
    List<FraudCase> findOpenCases(@Param("statuses") List<CaseStatus> statuses);

    /**
     * Find cases by status
     */
    List<FraudCase> findByStatus(CaseStatus status);

    /**
     * Find high-risk open cases (priority queue)
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.status IN ('OPEN', 'UNDER_INVESTIGATION', 'PENDING_REVIEW') " +
           "AND fc.finalRiskScore >= :riskThreshold ORDER BY fc.finalRiskScore DESC, fc.createdAt ASC")
    List<FraudCase> findHighRiskOpenCases(@Param("riskThreshold") Double riskThreshold);

    /**
     * Find confirmed fraud cases (for training data)
     * Uses index on confirmed_fraud
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.confirmedFraud = true ORDER BY fc.createdAt DESC")
    List<FraudCase> findConfirmedFraudCases();

    /**
     * Find false positive cases (for model improvement)
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.falsePositive = true ORDER BY fc.createdAt DESC")
    List<FraudCase> findFalsePositiveCases();

    /**
     * Find labeled cases for ML training (confirmed fraud + false positives)
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.confirmedFraud IS NOT NULL ORDER BY fc.createdAt DESC")
    List<FraudCase> findLabeledCases();

    /**
     * Find cases created in date range (for reporting)
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.createdAt BETWEEN :start AND :end")
    List<FraudCase> findCasesInDateRange(
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end
    );

    /**
     * Find cases by decision
     */
    List<FraudCase> findByFinalDecision(FraudCaseDecision decision);

    /**
     * Find cases requiring investigation
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.status = 'OPEN' " +
           "AND fc.autoDecided = false ORDER BY fc.finalRiskScore DESC")
    List<FraudCase> findCasesRequiringInvestigation();

    /**
     * Find stale open cases (opened > N days ago, still open)
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.status IN ('OPEN', 'UNDER_INVESTIGATION') " +
           "AND fc.createdAt < :cutoffDate ORDER BY fc.createdAt ASC")
    List<FraudCase> findStaleOpenCases(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Find cases by device fingerprint
     */
    List<FraudCase> findByDeviceFingerprintHash(String deviceFingerprintHash);

    /**
     * Find cases by IP address
     */
    List<FraudCase> findByIpAddress(String ipAddress);

    /**
     * Find cases from specific country
     */
    List<FraudCase> findByCountryCode(String countryCode);

    /**
     * Find cases investigated by analyst
     */
    List<FraudCase> findByInvestigatedBy(String investigatedBy);

    /**
     * Find cases with chargeback filed
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.chargebackFiled = true ORDER BY fc.chargebackDate DESC")
    List<FraudCase> findCasesWithChargeback();

    /**
     * Count open cases
     */
    @Query("SELECT COUNT(fc) FROM FraudCase fc WHERE fc.status IN ('OPEN', 'UNDER_INVESTIGATION', 'PENDING_REVIEW')")
    Long countOpenCases();

    /**
     * Count confirmed fraud cases
     */
    @Query("SELECT COUNT(fc) FROM FraudCase fc WHERE fc.confirmedFraud = true")
    Long countConfirmedFraudCases();

    /**
     * Count false positives
     */
    @Query("SELECT COUNT(fc) FROM FraudCase fc WHERE fc.falsePositive = true")
    Long countFalsePositives();

    /**
     * Calculate total estimated losses
     */
    @Query("SELECT SUM(fc.estimatedLoss) FROM FraudCase fc WHERE fc.confirmedFraud = true")
    BigDecimal calculateTotalEstimatedLosses();

    /**
     * Calculate total recovered amount
     */
    @Query("SELECT SUM(fc.recoveredAmount) FROM FraudCase fc WHERE fc.recoveredAmount IS NOT NULL")
    BigDecimal calculateTotalRecovered();

    /**
     * Find cases by fraud type (for pattern analysis)
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.fraudType = :fraudType ORDER BY fc.createdAt DESC")
    List<FraudCase> findByFraudType(@Param("fraudType") String fraudType);

    /**
     * Find cases by model version (for model performance analysis)
     */
    List<FraudCase> findByModelVersion(String modelVersion);

    /**
     * Find recent cases for user (fraud history check)
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.userId = :userId " +
           "AND fc.createdAt >= :since ORDER BY fc.createdAt DESC")
    List<FraudCase> findRecentCasesForUser(
        @Param("userId") String userId,
        @Param("since") LocalDateTime since
    );

    /**
     * Find high-value fraud cases (> threshold)
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.confirmedFraud = true " +
           "AND fc.estimatedLoss >= :threshold ORDER BY fc.estimatedLoss DESC")
    List<FraudCase> findHighValueFraudCases(@Param("threshold") BigDecimal threshold);

    /**
     * Get fraud statistics by fraud type
     */
    @Query("SELECT fc.fraudType, COUNT(fc), SUM(fc.estimatedLoss) FROM FraudCase fc " +
           "WHERE fc.confirmedFraud = true AND fc.fraudType IS NOT NULL " +
           "GROUP BY fc.fraudType ORDER BY COUNT(fc) DESC")
    List<Object[]> getFraudStatisticsByType();

    /**
     * Get fraud statistics by country
     */
    @Query("SELECT fc.countryCode, COUNT(fc), SUM(fc.estimatedLoss) FROM FraudCase fc " +
           "WHERE fc.confirmedFraud = true AND fc.countryCode IS NOT NULL " +
           "GROUP BY fc.countryCode ORDER BY COUNT(fc) DESC")
    List<Object[]> getFraudStatisticsByCountry();

    /**
     * Calculate false positive rate for model version
     */
    @Query("SELECT " +
           "(SELECT COUNT(fc1) FROM FraudCase fc1 WHERE fc1.modelVersion = :modelVersion AND fc1.falsePositive = true) * 1.0 / " +
           "(SELECT COUNT(fc2) FROM FraudCase fc2 WHERE fc2.modelVersion = :modelVersion AND fc2.confirmedFraud IS NOT NULL)")
    Double calculateFalsePositiveRate(@Param("modelVersion") String modelVersion);

    /**
     * Find cases for retraining (recent labeled cases)
     */
    @Query("SELECT fc FROM FraudCase fc WHERE fc.confirmedFraud IS NOT NULL " +
           "AND fc.createdAt >= :since ORDER BY fc.createdAt DESC")
    List<FraudCase> findCasesForRetraining(@Param("since") LocalDateTime since);
}
