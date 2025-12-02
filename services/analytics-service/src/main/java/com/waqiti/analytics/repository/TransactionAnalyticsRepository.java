package com.waqiti.analytics.repository;

import com.waqiti.analytics.domain.TransactionAnalytics;
import com.waqiti.analytics.dto.response.*;
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
 * Transaction analytics repository
 */
@Repository
public interface TransactionAnalyticsRepository extends JpaRepository<TransactionAnalytics, UUID> {

    List<TransactionAnalytics> findByUserIdAndTimestampBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate);

    Optional<TransactionAnalytics> findByTransactionId(UUID transactionId);

    Long countByUserIdAndTimestampAfter(UUID userId, LocalDateTime timestamp);

    @Query(value = "SELECT COUNT(*) FROM transaction_analytics WHERE user_id = :userId AND created_at BETWEEN :startDate AND :endDate", nativeQuery = true)
    long countTransactionsByUserAndDateRange(@Param("userId") UUID userId,
                                           @Param("startDate") LocalDateTime startDate,
                                           @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT COALESCE(SUM(amount), 0) FROM transaction_analytics WHERE user_id = :userId AND created_at BETWEEN :startDate AND :endDate", nativeQuery = true)
    BigDecimal getTotalAmountByUserAndDateRange(@Param("userId") UUID userId,
                                              @Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT COUNT(*) as totalTransactions, " +
            "COALESCE(SUM(CASE WHEN transaction_type IN ('DEBIT', 'PAYMENT', 'WITHDRAWAL', 'PURCHASE') THEN amount ELSE 0 END), 0) as totalSpent, " +
            "COALESCE(SUM(CASE WHEN transaction_type IN ('CREDIT', 'DEPOSIT', 'SALARY', 'REFUND') THEN amount ELSE 0 END), 0) as totalReceived, " +
            "COALESCE(AVG(amount), 0) as averageAmount, " +
            "COALESCE(MAX(amount), 0) as largestTransaction, " +
            "COALESCE(MIN(amount), 0) as smallestTransaction, " +
            "COUNT(DISTINCT merchant_id) as uniqueMerchants, " +
            "COUNT(DISTINCT category) as uniqueCategories " +
            "FROM transaction_analytics " +
            "WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate", nativeQuery = true)
    List<Object[]> getTransactionSummary(@Param("userId") UUID userId,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT category, SUM(amount) as total, COUNT(*) as count " +
            "FROM transaction_analytics " +
            "WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate " +
            "AND transaction_type IN ('DEBIT', 'PAYMENT', 'WITHDRAWAL', 'PURCHASE') " +
            "GROUP BY category " +
            "ORDER BY total DESC", nativeQuery = true)
    List<Object[]> getCategorySpending(@Param("userId") UUID userId,
                                              @Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT DATE(timestamp) as date, SUM(amount) as total, COUNT(*) as count " +
            "FROM transaction_analytics " +
            "WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY DATE(timestamp) " +
            "ORDER BY date", nativeQuery = true)
    List<Object[]> getDailySpending(@Param("userId") UUID userId,
                                        @Param("startDate") LocalDateTime startDate,
                                        @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT EXTRACT(HOUR FROM timestamp) as hour, SUM(amount) as total, COUNT(*) as count " +
            "FROM transaction_analytics " +
            "WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY EXTRACT(HOUR FROM timestamp) " +
            "ORDER BY hour", nativeQuery = true)
    List<Object[]> getHourlySpending(@Param("userId") UUID userId,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT EXTRACT(HOUR FROM timestamp) as hour, SUM(amount) as total, COUNT(*) as count " +
            "FROM transaction_analytics " +
            "WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY EXTRACT(HOUR FROM timestamp) " +
            "ORDER BY total DESC", nativeQuery = true)
    List<Object[]> getHourlySpendingData(@Param("userId") UUID userId,
                                         @Param("startDate") LocalDateTime startDate,
                                         @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT TO_CHAR(timestamp, 'Day') as day " +
            "FROM transaction_analytics " +
            "WHERE user_id = :userId AND EXTRACT(HOUR FROM timestamp) = :hour " +
            "AND timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY TO_CHAR(timestamp, 'Day') " +
            "ORDER BY SUM(amount) DESC LIMIT 1", nativeQuery = true)
    String getPeakDayForHour(@Param("userId") UUID userId,
                            @Param("hour") Integer hour,
                            @Param("startDate") LocalDateTime startDate,
                            @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT merchant_name as source, SUM(amount) as total " +
            "FROM transaction_analytics " +
            "WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate " +
            "AND transaction_type IN ('CREDIT', 'DEPOSIT', 'SALARY', 'REFUND') " +
            "GROUP BY merchant_name " +
            "ORDER BY total DESC", nativeQuery = true)
    List<Object[]> getIncomeSources(@Param("userId") UUID userId,
                                       @Param("startDate") LocalDateTime startDate,
                                       @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT DATE(timestamp) as date, SUM(amount) as total, COUNT(*) as count " +
            "FROM transaction_analytics " +
            "WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate " +
            "AND transaction_type IN ('CREDIT', 'DEPOSIT', 'SALARY', 'REFUND') " +
            "GROUP BY DATE(timestamp) " +
            "ORDER BY date", nativeQuery = true)
    List<Object[]> getDailyIncome(@Param("userId") UUID userId,
                                    @Param("startDate") LocalDateTime startDate,
                                    @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT DATE(timestamp) as date, " +
            "SUM(CASE WHEN transaction_type IN ('CREDIT', 'DEPOSIT', 'SALARY', 'REFUND') THEN amount ELSE -amount END) as netFlow " +
            "FROM transaction_analytics " +
            "WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY DATE(timestamp) " +
            "ORDER BY date", nativeQuery = true)
    List<Object[]> getCashFlowData(@Param("userId") UUID userId,
                                      @Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT merchant_name, SUM(amount) as total, COUNT(*) as count " +
            "FROM transaction_analytics " +
            "WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate " +
            "AND transaction_type IN ('DEBIT', 'PAYMENT', 'WITHDRAWAL', 'PURCHASE') " +
            "GROUP BY merchant_name " +
            "ORDER BY total DESC LIMIT :limit", nativeQuery = true)
    List<Object[]> getTopMerchants(@Param("userId") UUID userId,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate,
                                          @Param("limit") int limit);

    @Query(value = "SELECT merchant_name, COUNT(*) as frequency " +
            "FROM transaction_analytics " +
            "WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate " +
            "GROUP BY merchant_name " +
            "ORDER BY frequency DESC", nativeQuery = true)
    List<Object[]> getMerchantFrequency(@Param("userId") UUID userId,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);

    @Query(value = "SELECT COUNT(*) FROM transaction_analytics " +
            "WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate " +
            "AND (status = 'FAILED' OR status = 'DECLINED' OR status = 'ERROR')", nativeQuery = true)
    Long countFailedTransactions(@Param("userId") UUID userId,
                                 @Param("startDate") LocalDateTime startDate,
                                 @Param("endDate") LocalDateTime endDate);
}