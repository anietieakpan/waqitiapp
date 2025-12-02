package com.waqiti.tax.repository;

import com.waqiti.tax.entity.TaxTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for TaxTransaction entities.
 * Provides comprehensive data access methods for tax transaction management and reporting.
 * 
 * @author Waqiti Tax Team
 * @since 2.0.0
 */
@Repository
public interface TaxTransactionRepository extends JpaRepository<TaxTransaction, Long> {

    /**
     * Find tax transaction by original transaction ID
     */
    Optional<TaxTransaction> findByTransactionId(String transactionId);

    /**
     * Find all tax transactions within date range
     */
    List<TaxTransaction> findByCalculationDateBetween(LocalDateTime startDate, 
                                                      LocalDateTime endDate);

    /**
     * Find tax transactions by user ID within date range
     */
    List<TaxTransaction> findByUserIdAndCalculationDateBetween(String userId, 
                                                              LocalDateTime startDate, 
                                                              LocalDateTime endDate);

    /**
     * Find tax transactions by user ID and tax year
     */
    List<TaxTransaction> findByUserIdAndTaxYear(String userId, Integer taxYear);

    /**
     * Find tax transactions by jurisdiction
     */
    List<TaxTransaction> findByJurisdictionAndCalculationDateBetween(String jurisdiction,
                                                                    LocalDateTime startDate,
                                                                    LocalDateTime endDate);

    /**
     * Find tax transactions by transaction type
     */
    List<TaxTransaction> findByTransactionTypeAndCalculationDateBetween(String transactionType,
                                                                       LocalDateTime startDate,
                                                                       LocalDateTime endDate);

    /**
     * Find high-value tax transactions above threshold
     */
    @Query("SELECT tt FROM TaxTransaction tt WHERE tt.taxAmount >= :threshold " +
           "AND tt.calculationDate BETWEEN :startDate AND :endDate")
    List<TaxTransaction> findHighValueTransactions(@Param("threshold") BigDecimal threshold,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);

    /**
     * Find cross-border tax transactions
     */
    List<TaxTransaction> findByCrossBorderTrueAndCalculationDateBetween(LocalDateTime startDate,
                                                                       LocalDateTime endDate);

    /**
     * Find transactions requiring manual review
     */
    List<TaxTransaction> findByManualReviewRequiredTrueAndStatus(String status);

    /**
     * Find transactions not included in filing
     */
    @Query("SELECT tt FROM TaxTransaction tt WHERE tt.includedInFiling = false " +
           "AND tt.taxYear = :taxYear AND tt.status = 'CALCULATED'")
    List<TaxTransaction> findUnfiledTransactions(@Param("taxYear") Integer taxYear);

    /**
     * Find transactions by filing reference
     */
    List<TaxTransaction> findByFilingReference(String filingReference);

    /**
     * Calculate total tax amount by user and date range
     */
    @Query("SELECT SUM(tt.taxAmount) FROM TaxTransaction tt WHERE tt.userId = :userId " +
           "AND tt.calculationDate BETWEEN :startDate AND :endDate")
    BigDecimal calculateTotalTaxByUserAndDateRange(@Param("userId") String userId,
                                                  @Param("startDate") LocalDateTime startDate,
                                                  @Param("endDate") LocalDateTime endDate);

    /**
     * Calculate total transaction amount by user and date range
     */
    @Query("SELECT SUM(tt.transactionAmount) FROM TaxTransaction tt WHERE tt.userId = :userId " +
           "AND tt.calculationDate BETWEEN :startDate AND :endDate")
    BigDecimal calculateTotalTransactionAmountByUserAndDateRange(@Param("userId") String userId,
                                                                @Param("startDate") LocalDateTime startDate,
                                                                @Param("endDate") LocalDateTime endDate);

    /**
     * Get tax summary by jurisdiction and date range
     */
    @Query("SELECT tt.jurisdiction, SUM(tt.taxAmount), COUNT(tt) FROM TaxTransaction tt " +
           "WHERE tt.calculationDate BETWEEN :startDate AND :endDate " +
           "GROUP BY tt.jurisdiction")
    List<Object[]> getTaxSummaryByJurisdiction(@Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    /**
     * Get tax summary by transaction type and date range
     */
    @Query("SELECT tt.transactionType, SUM(tt.taxAmount), COUNT(tt) FROM TaxTransaction tt " +
           "WHERE tt.calculationDate BETWEEN :startDate AND :endDate " +
           "GROUP BY tt.transactionType")
    List<Object[]> getTaxSummaryByTransactionType(@Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    /**
     * Get monthly tax summary for a user
     */
    @Query("SELECT YEAR(tt.calculationDate), MONTH(tt.calculationDate), SUM(tt.taxAmount) " +
           "FROM TaxTransaction tt WHERE tt.userId = :userId " +
           "AND tt.calculationDate BETWEEN :startDate AND :endDate " +
           "GROUP BY YEAR(tt.calculationDate), MONTH(tt.calculationDate) " +
           "ORDER BY YEAR(tt.calculationDate), MONTH(tt.calculationDate)")
    List<Object[]> getMonthlyTaxSummary(@Param("userId") String userId,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Find transactions overdue for review
     */
    @Query("SELECT tt FROM TaxTransaction tt WHERE tt.nextReviewDate IS NOT NULL " +
           "AND tt.nextReviewDate < :currentTime AND tt.status = 'CALCULATED'")
    List<TaxTransaction> findTransactionsOverdueForReview(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Find transactions with low confidence scores
     */
    @Query("SELECT tt FROM TaxTransaction tt WHERE tt.confidenceScore < :threshold " +
           "AND tt.status = 'CALCULATED'")
    List<TaxTransaction> findLowConfidenceTransactions(@Param("threshold") BigDecimal threshold);

    /**
     * Count transactions by status
     */
    @Query("SELECT tt.status, COUNT(tt) FROM TaxTransaction tt " +
           "WHERE tt.calculationDate BETWEEN :startDate AND :endDate " +
           "GROUP BY tt.status")
    List<Object[]> countTransactionsByStatus(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    /**
     * Find refund transactions (those with original transaction references)
     */
    List<TaxTransaction> findByOriginalTransactionIdIsNotNull();

    /**
     * Find transactions by customer type
     */
    List<TaxTransaction> findByCustomerTypeAndCalculationDateBetween(String customerType,
                                                                    LocalDateTime startDate,
                                                                    LocalDateTime endDate);

    /**
     * Find transactions by business category
     */
    List<TaxTransaction> findByBusinessCategoryAndCalculationDateBetween(String businessCategory,
                                                                        LocalDateTime startDate,
                                                                        LocalDateTime endDate);

    /**
     * Get average tax amount by transaction type
     */
    @Query("SELECT tt.transactionType, AVG(tt.taxAmount) FROM TaxTransaction tt " +
           "WHERE tt.calculationDate BETWEEN :startDate AND :endDate " +
           "GROUP BY tt.transactionType")
    List<Object[]> getAverageTaxByTransactionType(@Param("startDate") LocalDateTime startDate,
                                                 @Param("endDate") LocalDateTime endDate);

    /**
     * Find transactions with exemptions applied
     */
    @Query("SELECT tt FROM TaxTransaction tt WHERE tt.exemptionAmount > 0 " +
           "AND tt.calculationDate BETWEEN :startDate AND :endDate")
    List<TaxTransaction> findTransactionsWithExemptions(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Find transactions with deductions applied
     */
    @Query("SELECT tt FROM TaxTransaction tt WHERE tt.deductionAmount > 0 " +
           "AND tt.calculationDate BETWEEN :startDate AND :endDate")
    List<TaxTransaction> findTransactionsWithDeductions(@Param("startDate") LocalDateTime startDate,
                                                       @Param("endDate") LocalDateTime endDate);

    /**
     * Get tax statistics for a date range
     */
    @Query("SELECT COUNT(tt), SUM(tt.taxAmount), AVG(tt.taxAmount), " +
           "MIN(tt.taxAmount), MAX(tt.taxAmount) FROM TaxTransaction tt " +
           "WHERE tt.calculationDate BETWEEN :startDate AND :endDate")
    List<Object[]> getTaxStatistics(@Param("startDate") LocalDateTime startDate,
                                   @Param("endDate") LocalDateTime endDate);

    /**
     * Find transactions by payment method
     */
    List<TaxTransaction> findByPaymentMethodAndCalculationDateBetween(String paymentMethod,
                                                                     LocalDateTime startDate,
                                                                     LocalDateTime endDate);

    /**
     * Find duplicate transactions (same transaction ID)
     */
    @Query("SELECT tt FROM TaxTransaction tt WHERE tt.transactionId IN " +
           "(SELECT tt2.transactionId FROM TaxTransaction tt2 " +
           "GROUP BY tt2.transactionId HAVING COUNT(tt2) > 1)")
    List<TaxTransaction> findDuplicateTransactions();

    /**
     * Find transactions requiring compliance filing
     */
    @Query("SELECT tt FROM TaxTransaction tt WHERE " +
           "(tt.crossBorder = true OR tt.transactionAmount >= :threshold OR tt.manualReviewRequired = true) " +
           "AND tt.calculationDate BETWEEN :startDate AND :endDate")
    List<TaxTransaction> findTransactionsRequiringCompliance(@Param("threshold") BigDecimal threshold,
                                                            @Param("startDate") LocalDateTime startDate,
                                                            @Param("endDate") LocalDateTime endDate);

    /**
     * Get user tax burden analysis
     */
    @Query("SELECT tt.userId, SUM(tt.transactionAmount), SUM(tt.taxAmount), " +
           "(SUM(tt.taxAmount) / SUM(tt.transactionAmount) * 100) as taxBurden " +
           "FROM TaxTransaction tt WHERE tt.calculationDate BETWEEN :startDate AND :endDate " +
           "GROUP BY tt.userId ORDER BY taxBurden DESC")
    List<Object[]> getUserTaxBurdenAnalysis(@Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    /**
     * Find transactions by effective tax rate range
     */
    @Query("SELECT tt FROM TaxTransaction tt WHERE tt.effectiveTaxRate BETWEEN :minRate AND :maxRate " +
           "AND tt.calculationDate BETWEEN :startDate AND :endDate")
    List<TaxTransaction> findByEffectiveTaxRateRange(@Param("minRate") BigDecimal minRate,
                                                    @Param("maxRate") BigDecimal maxRate,
                                                    @Param("startDate") LocalDateTime startDate,
                                                    @Param("endDate") LocalDateTime endDate);

    /**
     * Get jurisdiction tax collection summary
     */
    @Query("SELECT tt.jurisdiction, COUNT(tt), SUM(tt.taxAmount), AVG(tt.effectiveTaxRate) " +
           "FROM TaxTransaction tt WHERE tt.calculationDate BETWEEN :startDate AND :endDate " +
           "GROUP BY tt.jurisdiction ORDER BY SUM(tt.taxAmount) DESC")
    List<Object[]> getJurisdictionTaxSummary(@Param("startDate") LocalDateTime startDate,
                                            @Param("endDate") LocalDateTime endDate);

    /**
     * Find transactions updated after a specific time (for sync purposes)
     */
    List<TaxTransaction> findByUpdatedAtAfter(LocalDateTime updatedAfter);

    /**
     * Get paginated transactions with filters
     */
    @Query("SELECT tt FROM TaxTransaction tt WHERE " +
           "(:userId IS NULL OR tt.userId = :userId) " +
           "AND (:jurisdiction IS NULL OR tt.jurisdiction = :jurisdiction) " +
           "AND (:transactionType IS NULL OR tt.transactionType = :transactionType) " +
           "AND (:status IS NULL OR tt.status = :status) " +
           "AND tt.calculationDate BETWEEN :startDate AND :endDate")
    Page<TaxTransaction> findWithFilters(@Param("userId") String userId,
                                        @Param("jurisdiction") String jurisdiction,
                                        @Param("transactionType") String transactionType,
                                        @Param("status") String status,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate,
                                        Pageable pageable);

    /**
     * Mark transactions as included in filing
     */
    @Modifying
    @Query("UPDATE TaxTransaction tt SET tt.includedInFiling = true, " +
           "tt.filingReference = :filingReference, tt.filingDate = :filingDate " +
           "WHERE tt.id IN :transactionIds")
    int markAsIncludedInFiling(@Param("transactionIds") List<Long> transactionIds,
                              @Param("filingReference") String filingReference,
                              @Param("filingDate") LocalDateTime filingDate);

    /**
     * Update next review dates for transactions
     */
    @Modifying
    @Query("UPDATE TaxTransaction tt SET tt.nextReviewDate = :nextReviewDate " +
           "WHERE tt.id IN :transactionIds")
    int updateNextReviewDates(@Param("transactionIds") List<Long> transactionIds,
                             @Param("nextReviewDate") LocalDateTime nextReviewDate);

    /**
     * Get tax collection trends by month
     */
    @Query("SELECT YEAR(tt.calculationDate), MONTH(tt.calculationDate), " +
           "SUM(tt.taxAmount), COUNT(tt) FROM TaxTransaction tt " +
           "WHERE tt.calculationDate BETWEEN :startDate AND :endDate " +
           "GROUP BY YEAR(tt.calculationDate), MONTH(tt.calculationDate) " +
           "ORDER BY YEAR(tt.calculationDate), MONTH(tt.calculationDate)")
    List<Object[]> getTaxCollectionTrends(@Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);
}