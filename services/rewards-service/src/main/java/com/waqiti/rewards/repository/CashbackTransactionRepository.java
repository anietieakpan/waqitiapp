package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.CashbackTransaction;
import com.waqiti.rewards.enums.CashbackStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CashbackTransactionRepository extends JpaRepository<CashbackTransaction, UUID> {
    
    Optional<CashbackTransaction> findByTransactionId(String transactionId);
    
    Page<CashbackTransaction> findByUserIdOrderByEarnedAtDesc(String userId, Pageable pageable);
    
    List<CashbackTransaction> findByUserIdAndStatus(String userId, CashbackStatus status);
    
    @Query("SELECT ct FROM CashbackTransaction ct WHERE ct.status = :status AND ct.earnedAt < :date")
    List<CashbackTransaction> findByStatusAndEarnedAtBefore(
        @Param("status") CashbackStatus status, 
        @Param("date") Instant date
    );
    
    @Query("SELECT SUM(ct.cashbackAmount) FROM CashbackTransaction ct " +
           "WHERE ct.userId = :userId AND ct.earnedAt >= :startDate")
    BigDecimal getDailyCashback(@Param("userId") String userId, @Param("startDate") Instant startDate);
    
    @Query("SELECT SUM(ct.cashbackAmount) FROM CashbackTransaction ct " +
           "WHERE ct.userId = :userId AND ct.earnedAt >= :startDate AND ct.status = 'EARNED'")
    BigDecimal getEarningsInPeriod(@Param("userId") String userId, @Param("startDate") Instant startDate);
    
    @Query("SELECT ct FROM CashbackTransaction ct WHERE ct.merchantId = :merchantId " +
           "AND ct.userId = :userId ORDER BY ct.earnedAt DESC")
    Page<CashbackTransaction> findByMerchantAndUser(
        @Param("merchantId") String merchantId, 
        @Param("userId") String userId, 
        Pageable pageable
    );
    
    @Query("SELECT ct FROM CashbackTransaction ct WHERE ct.campaignId = :campaignId")
    List<CashbackTransaction> findByCampaignId(@Param("campaignId") String campaignId);
    
    @Query("SELECT COUNT(ct) FROM CashbackTransaction ct WHERE ct.userId = :userId " +
           "AND ct.status = 'EARNED' AND ct.earnedAt >= :startDate")
    long countEarnedTransactions(@Param("userId") String userId, @Param("startDate") Instant startDate);
    
    @Query("SELECT ct.merchantCategory, SUM(ct.cashbackAmount) FROM CashbackTransaction ct " +
           "WHERE ct.userId = :userId AND ct.status = 'EARNED' " +
           "GROUP BY ct.merchantCategory ORDER BY SUM(ct.cashbackAmount) DESC")
    List<Object[]> getCashbackByCategory(@Param("userId") String userId);
    
    @Query("SELECT ct FROM CashbackTransaction ct WHERE ct.expiresAt IS NOT NULL " +
           "AND ct.expiresAt <= :now AND ct.status = 'PENDING'")
    List<CashbackTransaction> findExpiredCashback(@Param("now") Instant now);
    
    @Query(value = "SELECT DATE_TRUNC('day', earned_at) as date, SUM(cashback_amount) as total " +
           "FROM cashback_transactions WHERE user_id = :userId AND status = 'EARNED' " +
           "AND earned_at >= :startDate GROUP BY DATE_TRUNC('day', earned_at) " +
           "ORDER BY date ASC", nativeQuery = true)
    List<Object[]> getDailyCashbackTrend(
        @Param("userId") String userId, 
        @Param("startDate") Instant startDate
    );
    
    /**
     * Check if user has received welcome bonus
     */
    @Query("SELECT COUNT(ct) > 0 FROM CashbackTransaction ct WHERE ct.userId = :userId AND ct.source = :source")
    boolean existsByUserIdAndSource(@Param("userId") String userId, @Param("source") com.waqiti.rewards.enums.CashbackSource source);
    
    /**
     * Get total spending in period for tier calculation
     */
    @Query("SELECT COALESCE(SUM(ct.transactionAmount), 0) FROM CashbackTransaction ct " +
           "WHERE ct.userId = :userId AND ct.earnedAt >= :startDate AND ct.status = 'EARNED'")
    BigDecimal getTotalSpendingInPeriod(@Param("userId") String userId, @Param("startDate") Instant startDate);
    
    /**
     * Get transaction count in period
     */
    @Query("SELECT COUNT(ct) FROM CashbackTransaction ct " +
           "WHERE ct.userId = :userId AND ct.earnedAt >= :startDate AND ct.status = 'EARNED'")
    long getTransactionCountInPeriod(@Param("userId") String userId, @Param("startDate") Instant startDate);
    
    /**
     * Get category breakdown for analytics
     */
    @Query("SELECT ct.merchantCategory, SUM(ct.transactionAmount), SUM(ct.cashbackAmount), COUNT(ct) " +
           "FROM CashbackTransaction ct WHERE ct.userId = :userId AND ct.earnedAt >= :startDate " +
           "AND ct.status = 'EARNED' GROUP BY ct.merchantCategory")
    List<Object[]> getCategoryBreakdownInPeriod(@Param("userId") String userId, @Param("startDate") Instant startDate);
    
    /**
     * Get merchant breakdown for analytics
     */
    @Query("SELECT ct.merchantId, ct.merchantName, SUM(ct.transactionAmount), SUM(ct.cashbackAmount), COUNT(ct) " +
           "FROM CashbackTransaction ct WHERE ct.userId = :userId AND ct.earnedAt >= :startDate " +
           "AND ct.status = 'EARNED' GROUP BY ct.merchantId, ct.merchantName ORDER BY SUM(ct.transactionAmount) DESC")
    List<Object[]> getMerchantBreakdownInPeriod(@Param("userId") String userId, @Param("startDate") Instant startDate);
    
    /**
     * Get average daily cashback for system metrics
     */
    @Query("SELECT AVG(daily_total) FROM (" +
           "SELECT DATE_TRUNC('day', earned_at) as day, SUM(cashback_amount) as daily_total " +
           "FROM cashback_transactions WHERE earned_at >= :startDate AND status = 'EARNED' " +
           "GROUP BY DATE_TRUNC('day', earned_at)) daily_totals")
    BigDecimal getAverageDailyCashback(@Param("startDate") Instant startDate);
    
    /**
     * Get average daily transaction count for system metrics
     */
    @Query("SELECT AVG(daily_count) FROM (" +
           "SELECT DATE_TRUNC('day', earned_at) as day, COUNT(*) as daily_count " +
           "FROM cashback_transactions WHERE earned_at >= :startDate AND status = 'EARNED' " +
           "GROUP BY DATE_TRUNC('day', earned_at)) daily_counts")
    long getAverageDailyTransactions(@Param("startDate") Instant startDate);
}