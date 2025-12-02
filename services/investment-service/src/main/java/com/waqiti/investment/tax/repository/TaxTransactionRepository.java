package com.waqiti.investment.tax.repository;

import com.waqiti.common.repository.BaseRepository;
import com.waqiti.investment.tax.entity.TaxTransaction;
import com.waqiti.investment.tax.entity.TaxTransaction.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Repository for Tax Transactions.
 *
 * Provides queries for:
 * - Transaction tracking for tax reporting
 * - Wash sale identification
 * - Cost basis calculation
 * - Holding period determination
 * - 1099 form data aggregation
 *
 * @author Waqiti Platform
 * @version 1.0
 * @since 2025-10-01
 */
@Repository
public interface TaxTransactionRepository extends BaseRepository<TaxTransaction, UUID> {

    /**
     * Find all tax transactions for a user in a tax year.
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.deleted = false ORDER BY t.saleDate, t.dividendPaymentDate")
    List<TaxTransaction> findByUserIdAndTaxYear(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find all stock sales for a user in a tax year (for 1099-B).
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.transactionType = 'STOCK_SALE' AND t.deleted = false ORDER BY t.saleDate")
    List<TaxTransaction> findStockSalesByUserAndYear(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find all dividend transactions for a user in a tax year (for 1099-DIV).
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.transactionType IN ('DIVIDEND_ORDINARY', 'DIVIDEND_QUALIFIED', 'DIVIDEND_CAPITAL_GAIN') AND t.deleted = false ORDER BY t.dividendPaymentDate")
    List<TaxTransaction> findDividendsByUserAndYear(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find short-term sales (1 year or less).
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.transactionType = 'STOCK_SALE' AND t.holdingPeriodType = 'SHORT_TERM' AND t.deleted = false ORDER BY t.saleDate")
    List<TaxTransaction> findShortTermSales(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find long-term sales (more than 1 year).
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.transactionType = 'STOCK_SALE' AND t.holdingPeriodType = 'LONG_TERM' AND t.deleted = false ORDER BY t.saleDate")
    List<TaxTransaction> findLongTermSales(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find covered securities (broker reports cost basis).
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.isCoveredSecurity = true AND t.deleted = false ORDER BY t.saleDate")
    List<TaxTransaction> findCoveredSecurities(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find noncovered securities (pre-2011 purchases, broker doesn't report cost basis).
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.isNoncoveredSecurity = true AND t.deleted = false ORDER BY t.saleDate")
    List<TaxTransaction> findNoncoveredSecurities(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find wash sales for a user in a tax year.
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.isWashSale = true AND t.deleted = false ORDER BY t.saleDate")
    List<TaxTransaction> findWashSales(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find potential wash sale transactions for a symbol within 30 days.
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.symbol = :symbol AND t.transactionType IN ('STOCK_PURCHASE', 'STOCK_SALE') AND t.saleDate BETWEEN :startDate AND :endDate AND t.deleted = false ORDER BY t.saleDate")
    List<TaxTransaction> findPotentialWashSaleTransactions(@Param("userId") UUID userId,
                                                             @Param("symbol") String symbol,
                                                             @Param("startDate") LocalDate startDate,
                                                             @Param("endDate") LocalDate endDate);

    /**
     * Find transactions not yet reported on 1099.
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.reportedOn1099 = false AND t.deleted = false ORDER BY t.saleDate, t.dividendPaymentDate")
    List<TaxTransaction> findUnreportedTransactions(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find transactions by symbol and year.
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.symbol = :symbol AND t.taxYear = :taxYear AND t.deleted = false ORDER BY t.saleDate, t.dividendPaymentDate")
    List<TaxTransaction> findBySymbolAndYear(@Param("userId") UUID userId,
                                               @Param("symbol") String symbol,
                                               @Param("taxYear") Integer taxYear);

    /**
     * Calculate total proceeds for 1099-B.
     */
    @Query("SELECT COALESCE(SUM(t.proceeds), 0) FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.transactionType = 'STOCK_SALE' AND t.deleted = false")
    BigDecimal calculateTotalProceeds(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Calculate total cost basis for 1099-B.
     */
    @Query("SELECT COALESCE(SUM(t.costBasis), 0) FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.transactionType = 'STOCK_SALE' AND t.deleted = false")
    BigDecimal calculateTotalCostBasis(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Calculate total wash sale loss disallowed.
     */
    @Query("SELECT COALESCE(SUM(t.washSaleLossDisallowed), 0) FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.isWashSale = true AND t.deleted = false")
    BigDecimal calculateTotalWashSaleLoss(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Calculate total ordinary dividends for 1099-DIV.
     */
    @Query("SELECT COALESCE(SUM(t.dividendAmount), 0) FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.transactionType IN ('DIVIDEND_ORDINARY', 'DIVIDEND_QUALIFIED') AND t.deleted = false")
    BigDecimal calculateTotalOrdinaryDividends(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Calculate qualified dividends for 1099-DIV.
     */
    @Query("SELECT COALESCE(SUM(t.dividendAmount), 0) FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.isQualifiedDividend = true AND t.deleted = false")
    BigDecimal calculateQualifiedDividends(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Calculate total capital gain distributions for 1099-DIV.
     */
    @Query("SELECT COALESCE(SUM(t.dividendAmount), 0) FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.transactionType = 'DIVIDEND_CAPITAL_GAIN' AND t.deleted = false")
    BigDecimal calculateCapitalGainDistributions(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Calculate total return of capital.
     */
    @Query("SELECT COALESCE(SUM(t.returnOfCapital), 0) FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.returnOfCapital IS NOT NULL AND t.deleted = false")
    BigDecimal calculateTotalReturnOfCapital(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Calculate total foreign tax paid.
     */
    @Query("SELECT COALESCE(SUM(t.foreignTaxPaid), 0) FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.foreignTaxPaid IS NOT NULL AND t.deleted = false")
    BigDecimal calculateTotalForeignTaxPaid(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find transactions for specific investment account.
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.investmentAccountId = :accountId AND t.taxYear = :taxYear AND t.deleted = false ORDER BY t.saleDate, t.dividendPaymentDate")
    List<TaxTransaction> findByAccountIdAndYear(@Param("accountId") String accountId, @Param("taxYear") Integer taxYear);

    /**
     * Count transactions by type and year.
     */
    @Query("SELECT COUNT(t) FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.transactionType = :transactionType AND t.deleted = false")
    Long countByTypeAndYear(@Param("userId") UUID userId,
                             @Param("taxYear") Integer taxYear,
                             @Param("transactionType") TransactionType transactionType);

    /**
     * Get summary statistics for tax year.
     */
    @Query("""
        SELECT NEW map(
            t.holdingPeriodType as holdingPeriod,
            t.isCoveredSecurity as covered,
            COUNT(t) as count,
            SUM(t.proceeds) as totalProceeds,
            SUM(t.costBasis) as totalCostBasis,
            SUM(t.gainLoss) as totalGainLoss
        )
        FROM TaxTransaction t
        WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.transactionType = 'STOCK_SALE' AND t.deleted = false
        GROUP BY t.holdingPeriodType, t.isCoveredSecurity
    """)
    List<Object> getSalesStatisticsByUserAndYear(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Get dividend statistics for tax year.
     */
    @Query("""
        SELECT NEW map(
            t.isQualifiedDividend as qualified,
            COUNT(t) as count,
            SUM(t.dividendAmount) as totalAmount
        )
        FROM TaxTransaction t
        WHERE t.userId = :userId AND t.taxYear = :taxYear AND t.transactionType IN ('DIVIDEND_ORDINARY', 'DIVIDEND_QUALIFIED') AND t.deleted = false
        GROUP BY t.isQualifiedDividend
    """)
    List<Object> getDividendStatisticsByUserAndYear(@Param("userId") UUID userId, @Param("taxYear") Integer taxYear);

    /**
     * Find transactions with gains/losses exceeding threshold (for reporting requirements).
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.userId = :userId AND t.taxYear = :taxYear AND ABS(t.gainLoss) >= :threshold AND t.deleted = false ORDER BY ABS(t.gainLoss) DESC")
    List<TaxTransaction> findLargeGainsOrLosses(@Param("userId") UUID userId,
                                                  @Param("taxYear") Integer taxYear,
                                                  @Param("threshold") BigDecimal threshold);

    /**
     * Find all transactions for an order ID (for tracking complete order lifecycle).
     */
    @Query("SELECT t FROM TaxTransaction t WHERE t.orderId = :orderId AND t.deleted = false ORDER BY t.saleDate")
    List<TaxTransaction> findByOrderId(@Param("orderId") String orderId);
}
