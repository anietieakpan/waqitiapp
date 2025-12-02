package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.ComplianceTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Compliance Transaction Repository
 * 
 * CRITICAL: Manages compliance transaction data persistence with comprehensive search,
 * monitoring, and reporting capabilities.
 * 
 * COMPLIANCE IMPACT:
 * - Supports AML/BSA transaction monitoring
 * - Enables suspicious activity identification
 * - Facilitates regulatory reporting (SAR, CTR)
 * - Maintains audit trails for compliance reviews
 * 
 * BUSINESS IMPACT:
 * - Enables real-time compliance monitoring
 * - Reduces regulatory violation risks
 * - Supports fraud detection and prevention
 * - Maintains regulatory standing and licensing
 * 
 * PERFORMANCE CONSIDERATIONS:
 * - All queries optimized with appropriate indexes
 * - Pagination support for large result sets
 * - Read replicas recommended for reporting queries
 * - Query timeout set to 30s for complex analytics
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2025-09-27
 */
@Repository
public interface ComplianceTransactionRepository extends JpaRepository<ComplianceTransaction, UUID> {

    /**
     * Find transaction by transaction ID
     * Used for real-time compliance checks
     */
    Optional<ComplianceTransaction> findByTransactionId(UUID transactionId);

    /**
     * Find transactions by account ID
     * Used for account-level compliance monitoring
     */
    Page<ComplianceTransaction> findByAccountId(UUID accountId, Pageable pageable);

    /**
     * Find transactions by account ID within date range
     * Used for period-specific compliance analysis
     */
    @Query("SELECT ct FROM ComplianceTransaction ct WHERE " +
           "ct.accountId = :accountId AND " +
           "ct.createdAt >= :startDate AND ct.createdAt <= :endDate " +
           "ORDER BY ct.createdAt DESC")
    List<ComplianceTransaction> findByAccountIdAndDateRange(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Count compliance violations for a user after a specific date
     * CRITICAL: Used for wallet compliance validation
     */
    @Query("SELECT COUNT(ct) FROM ComplianceTransaction ct WHERE " +
           "CAST(ct.accountId AS string) = :userId AND " +
           "ct.createdAt > :createdAt AND " +
           "(ct.isSuspicious = true OR ct.complianceStatus = 'VIOLATION')")
    Long countViolationsByUserIdAndCreatedAtAfter(
        @Param("userId") String userId,
        @Param("createdAt") LocalDateTime createdAt
    );

    /**
     * Find suspicious transactions requiring review
     * Used for compliance officer workflow
     */
    @Query("SELECT ct FROM ComplianceTransaction ct WHERE " +
           "ct.isSuspicious = true AND ct.isReported = false " +
           "ORDER BY ct.riskScore DESC, ct.createdAt DESC")
    Page<ComplianceTransaction> findSuspiciousUnreportedTransactions(Pageable pageable);

    /**
     * Find high-risk transactions above threshold
     * Used for priority compliance review
     */
    @Query("SELECT ct FROM ComplianceTransaction ct WHERE " +
           "ct.riskScore >= :riskThreshold AND ct.isReported = false " +
           "ORDER BY ct.riskScore DESC, ct.createdAt DESC")
    Page<ComplianceTransaction> findHighRiskTransactions(
        @Param("riskThreshold") Double riskThreshold,
        Pageable pageable
    );

    /**
     * Find large transactions above reporting threshold
     * Used for CTR (Currency Transaction Report) filing
     */
    @Query("SELECT ct FROM ComplianceTransaction ct WHERE " +
           "ct.amount >= :threshold AND " +
           "ct.currency = :currency AND " +
           "ct.createdAt >= :startDate AND ct.createdAt <= :endDate " +
           "ORDER BY ct.amount DESC")
    List<ComplianceTransaction> findLargeTransactions(
        @Param("threshold") BigDecimal threshold,
        @Param("currency") String currency,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find cross-border transactions for enhanced monitoring
     * Used for international compliance screening
     */
    @Query("SELECT ct FROM ComplianceTransaction ct WHERE " +
           "ct.sourceCountry != ct.destinationCountry AND " +
           "ct.createdAt >= :startDate " +
           "ORDER BY ct.createdAt DESC")
    Page<ComplianceTransaction> findCrossBorderTransactions(
        @Param("startDate") LocalDateTime startDate,
        Pageable pageable
    );

    /**
     * Count transactions by account within time window
     * Used for velocity checks and structuring detection
     */
    @Query("SELECT COUNT(ct) FROM ComplianceTransaction ct WHERE " +
           "ct.accountId = :accountId AND " +
           "ct.createdAt >= :startDate")
    Long countByAccountIdSince(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate
    );

    /**
     * Sum transaction amounts by account within time window
     * Used for daily/monthly limit enforcement
     */
    @Query("SELECT COALESCE(SUM(ct.amount), 0) FROM ComplianceTransaction ct WHERE " +
           "ct.accountId = :accountId AND " +
           "ct.currency = :currency AND " +
           "ct.transactionType = :transactionType AND " +
           "ct.createdAt >= :startDate")
    BigDecimal sumAmountByAccountAndType(
        @Param("accountId") UUID accountId,
        @Param("currency") String currency,
        @Param("transactionType") String transactionType,
        @Param("startDate") LocalDateTime startDate
    );

    /**
     * Find transactions involving counterparty
     * Used for relationship analysis and network mapping
     */
    @Query("SELECT ct FROM ComplianceTransaction ct WHERE " +
           "(ct.accountId = :accountId AND ct.counterpartyId = :counterpartyId) OR " +
           "(ct.accountId = :counterpartyId AND ct.counterpartyId = :accountId) " +
           "ORDER BY ct.createdAt DESC")
    List<ComplianceTransaction> findTransactionsBetweenParties(
        @Param("accountId") UUID accountId,
        @Param("counterpartyId") UUID counterpartyId,
        Pageable pageable
    );

    /**
     * Find transactions by compliance status
     * Used for workflow management
     */
    List<ComplianceTransaction> findByComplianceStatus(String complianceStatus, Pageable pageable);

    /**
     * Find reported transactions within date range
     * Used for regulatory reporting audit
     */
    @Query("SELECT ct FROM ComplianceTransaction ct WHERE " +
           "ct.isReported = true AND " +
           "ct.reportedAt >= :startDate AND ct.reportedAt <= :endDate " +
           "ORDER BY ct.reportedAt DESC")
    List<ComplianceTransaction> findReportedTransactions(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find transactions requiring screening
     * Used for batch screening jobs
     */
    @Query("SELECT ct FROM ComplianceTransaction ct WHERE " +
           "ct.screenedAt IS NULL AND ct.createdAt >= :since " +
           "ORDER BY ct.createdAt ASC")
    Page<ComplianceTransaction> findUnscreenedTransactions(
        @Param("since") LocalDateTime since,
        Pageable pageable
    );

    /**
     * Count suspicious transactions by date range
     * Used for compliance metrics and dashboards
     */
    @Query("SELECT COUNT(ct) FROM ComplianceTransaction ct WHERE " +
           "ct.isSuspicious = true AND " +
           "ct.createdAt >= :startDate AND ct.createdAt <= :endDate")
    Long countSuspiciousTransactions(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    /**
     * Find structuring patterns (multiple transactions just below threshold)
     * Used for detecting structuring/smurfing money laundering
     */
    @Query("SELECT ct.accountId, COUNT(ct), SUM(ct.amount) " +
           "FROM ComplianceTransaction ct WHERE " +
           "ct.amount >= :minAmount AND ct.amount < :maxAmount AND " +
           "ct.createdAt >= :startDate " +
           "GROUP BY ct.accountId " +
           "HAVING COUNT(ct) >= :minCount " +
           "ORDER BY COUNT(ct) DESC")
    List<Object[]> findPotentialStructuringPatterns(
        @Param("minAmount") BigDecimal minAmount,
        @Param("maxAmount") BigDecimal maxAmount,
        @Param("minCount") Long minCount,
        @Param("startDate") LocalDateTime startDate
    );

    /**
     * Find transactions with specific flags
     * Used for targeted compliance investigation
     */
    @Query("SELECT ct FROM ComplianceTransaction ct " +
           "JOIN ct.flags f WHERE f IN :flags " +
           "ORDER BY ct.createdAt DESC")
    Page<ComplianceTransaction> findByFlags(
        @Param("flags") List<String> flags,
        Pageable pageable
    );

    /**
     * Get daily transaction statistics for account
     * Used for velocity monitoring and limit enforcement
     */
    @Query("SELECT " +
           "CAST(ct.createdAt AS date) as txDate, " +
           "COUNT(ct) as txCount, " +
           "SUM(ct.amount) as totalAmount, " +
           "AVG(ct.amount) as avgAmount, " +
           "MAX(ct.amount) as maxAmount " +
           "FROM ComplianceTransaction ct WHERE " +
           "ct.accountId = :accountId AND " +
           "ct.createdAt >= :startDate " +
           "GROUP BY CAST(ct.createdAt AS date) " +
           "ORDER BY txDate DESC")
    List<Object[]> getDailyStatistics(
        @Param("accountId") UUID accountId,
        @Param("startDate") LocalDateTime startDate
    );

    /**
     * Find transactions to specific high-risk countries
     * Used for sanctions screening and enhanced due diligence
     */
    @Query("SELECT ct FROM ComplianceTransaction ct WHERE " +
           "ct.destinationCountry IN :highRiskCountries AND " +
           "ct.createdAt >= :startDate " +
           "ORDER BY ct.amount DESC")
    Page<ComplianceTransaction> findTransactionsToHighRiskCountries(
        @Param("highRiskCountries") List<String> highRiskCountries,
        @Param("startDate") LocalDateTime startDate,
        Pageable pageable
    );

    /**
     * Delete old compliance transactions (data retention)
     * Used for GDPR compliance and storage optimization
     */
    @Query("DELETE FROM ComplianceTransaction ct WHERE " +
           "ct.createdAt < :retentionDate AND " +
           "ct.isSuspicious = false AND ct.isReported = false")
    void deleteOldNonSuspiciousTransactions(@Param("retentionDate") LocalDateTime retentionDate);
}