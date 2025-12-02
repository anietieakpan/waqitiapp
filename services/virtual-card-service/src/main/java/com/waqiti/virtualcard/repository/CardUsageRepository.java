package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.CardUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for CardUsage entity
 * Tracks card usage patterns and statistics
 */
@Repository
public interface CardUsageRepository extends JpaRepository<CardUsage, String> {
    
    /**
     * Find card usage by card ID and date
     */
    Optional<CardUsage> findByCardIdAndUsageDate(String cardId, LocalDate usageDate);
    
    /**
     * Find card usage by card ID within date range
     */
    List<CardUsage> findByCardIdAndUsageDateBetween(String cardId, LocalDate startDate, LocalDate endDate);
    
    /**
     * Find card usage by card ID for current month
     */
    @Query("SELECT cu FROM CardUsage cu WHERE cu.cardId = :cardId AND " +
           "YEAR(cu.usageDate) = YEAR(CURRENT_DATE) AND MONTH(cu.usageDate) = MONTH(CURRENT_DATE)")
    List<CardUsage> findCurrentMonthUsage(@Param("cardId") String cardId);
    
    /**
     * Find card usage by card ID for current week
     */
    @Query("SELECT cu FROM CardUsage cu WHERE cu.cardId = :cardId AND " +
           "cu.usageDate >= :weekStart AND cu.usageDate <= :weekEnd")
    List<CardUsage> findCurrentWeekUsage(@Param("cardId") String cardId, 
                                        @Param("weekStart") LocalDate weekStart,
                                        @Param("weekEnd") LocalDate weekEnd);
    
    /**
     * Find today's usage for card
     */
    Optional<CardUsage> findByCardIdAndUsageDate(@Param("cardId") String cardId, 
                                                @Param("usageDate") LocalDate usageDate);
    
    /**
     * Get total spending for card on specific date
     */
    @Query("SELECT COALESCE(cu.totalSpent, 0) FROM CardUsage cu WHERE cu.cardId = :cardId AND cu.usageDate = :date")
    BigDecimal getTotalSpentOnDate(@Param("cardId") String cardId, @Param("date") LocalDate date);
    
    /**
     * Get transaction count for card on specific date
     */
    @Query("SELECT COALESCE(cu.transactionCount, 0) FROM CardUsage cu WHERE cu.cardId = :cardId AND cu.usageDate = :date")
    Integer getTransactionCountOnDate(@Param("cardId") String cardId, @Param("date") LocalDate date);
    
    /**
     * Sum total spending for card within date range
     */
    @Query("SELECT COALESCE(SUM(cu.totalSpent), 0) FROM CardUsage cu WHERE cu.cardId = :cardId AND " +
           "cu.usageDate BETWEEN :startDate AND :endDate")
    BigDecimal sumTotalSpentInRange(@Param("cardId") String cardId, 
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);
    
    /**
     * Sum transaction count for card within date range
     */
    @Query("SELECT COALESCE(SUM(cu.transactionCount), 0) FROM CardUsage cu WHERE cu.cardId = :cardId AND " +
           "cu.usageDate BETWEEN :startDate AND :endDate")
    Long sumTransactionCountInRange(@Param("cardId") String cardId, 
                                   @Param("startDate") LocalDate startDate,
                                   @Param("endDate") LocalDate endDate);
    
    /**
     * Find cards with high usage on specific date
     */
    @Query("SELECT cu FROM CardUsage cu WHERE cu.usageDate = :date AND cu.totalSpent >= :minAmount " +
           "ORDER BY cu.totalSpent DESC")
    List<CardUsage> findHighUsageOnDate(@Param("date") LocalDate date, @Param("minAmount") BigDecimal minAmount);
    
    /**
     * Find cards with high transaction count on specific date
     */
    @Query("SELECT cu FROM CardUsage cu WHERE cu.usageDate = :date AND cu.transactionCount >= :minCount " +
           "ORDER BY cu.transactionCount DESC")
    List<CardUsage> findHighTransactionCountOnDate(@Param("date") LocalDate date, @Param("minCount") Integer minCount);
    
    /**
     * Update or increment usage statistics
     */
    @Modifying
    @Query("UPDATE CardUsage cu SET " +
           "cu.transactionCount = cu.transactionCount + 1, " +
           "cu.totalSpent = cu.totalSpent + :amount, " +
           "cu.lastTransactionTime = :transactionTime, " +
           "cu.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cu.cardId = :cardId AND cu.usageDate = :usageDate")
    int updateUsageStats(@Param("cardId") String cardId, 
                        @Param("usageDate") LocalDate usageDate,
                        @Param("amount") BigDecimal amount,
                        @Param("transactionTime") LocalDateTime transactionTime);
    
    /**
     * Increment online transaction count
     */
    @Modifying
    @Query("UPDATE CardUsage cu SET " +
           "cu.onlineTransactionCount = cu.onlineTransactionCount + 1, " +
           "cu.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cu.cardId = :cardId AND cu.usageDate = :usageDate")
    int incrementOnlineTransactionCount(@Param("cardId") String cardId, @Param("usageDate") LocalDate usageDate);
    
    /**
     * Increment international transaction count
     */
    @Modifying
    @Query("UPDATE CardUsage cu SET " +
           "cu.internationalTransactionCount = cu.internationalTransactionCount + 1, " +
           "cu.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cu.cardId = :cardId AND cu.usageDate = :usageDate")
    int incrementInternationalTransactionCount(@Param("cardId") String cardId, @Param("usageDate") LocalDate usageDate);
    
    /**
     * Increment contactless transaction count
     */
    @Modifying
    @Query("UPDATE CardUsage cu SET " +
           "cu.contactlessTransactionCount = cu.contactlessTransactionCount + 1, " +
           "cu.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cu.cardId = :cardId AND cu.usageDate = :usageDate")
    int incrementContactlessTransactionCount(@Param("cardId") String cardId, @Param("usageDate") LocalDate usageDate);
    
    /**
     * Increment ATM transaction count
     */
    @Modifying
    @Query("UPDATE CardUsage cu SET " +
           "cu.atmTransactionCount = cu.atmTransactionCount + 1, " +
           "cu.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cu.cardId = :cardId AND cu.usageDate = :usageDate")
    int incrementAtmTransactionCount(@Param("cardId") String cardId, @Param("usageDate") LocalDate usageDate);
    
    /**
     * Update merchant usage statistics
     */
    @Modifying
    @Query("UPDATE CardUsage cu SET " +
           "cu.uniqueMerchantCount = " +
           "(SELECT COUNT(DISTINCT ct.merchantName) FROM CardTransaction ct " +
           "WHERE ct.cardId = :cardId AND DATE(ct.transactionDate) = :usageDate), " +
           "cu.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cu.cardId = :cardId AND cu.usageDate = :usageDate")
    int updateMerchantStats(@Param("cardId") String cardId, @Param("usageDate") LocalDate usageDate);
    
    /**
     * Find usage patterns for anomaly detection
     */
    @Query("SELECT cu FROM CardUsage cu WHERE cu.cardId = :cardId AND " +
           "cu.usageDate >= :startDate ORDER BY cu.usageDate DESC")
    List<CardUsage> findRecentUsagePattern(@Param("cardId") String cardId, @Param("startDate") LocalDate startDate);
    
    /**
     * Find cards with unusual spending patterns
     */
    @Query("SELECT cu FROM CardUsage cu WHERE cu.usageDate = :date AND " +
           "(cu.totalSpent > (SELECT AVG(cu2.totalSpent) * :multiplier FROM CardUsage cu2 " +
           "WHERE cu2.cardId = cu.cardId AND cu2.usageDate > :compareDate) OR " +
           "cu.transactionCount > (SELECT AVG(cu3.transactionCount) * :multiplier FROM CardUsage cu3 " +
           "WHERE cu3.cardId = cu.cardId AND cu3.usageDate > :compareDate))")
    List<CardUsage> findUnusualSpendingPatterns(@Param("date") LocalDate date, 
                                              @Param("multiplier") Double multiplier,
                                              @Param("compareDate") LocalDate compareDate);
    
    /**
     * Get average daily spending for card
     */
    @Query("SELECT AVG(cu.totalSpent) FROM CardUsage cu WHERE cu.cardId = :cardId AND " +
           "cu.usageDate BETWEEN :startDate AND :endDate")
    BigDecimal getAverageDailySpending(@Param("cardId") String cardId, 
                                      @Param("startDate") LocalDate startDate,
                                      @Param("endDate") LocalDate endDate);
    
    /**
     * Get peak usage times for card
     */
    @Query("SELECT HOUR(cu.lastTransactionTime), COUNT(*) FROM CardUsage cu WHERE cu.cardId = :cardId AND " +
           "cu.usageDate BETWEEN :startDate AND :endDate GROUP BY HOUR(cu.lastTransactionTime) " +
           "ORDER BY COUNT(*) DESC")
    List<Object[]> getPeakUsageTimes(@Param("cardId") String cardId, 
                                    @Param("startDate") LocalDate startDate,
                                    @Param("endDate") LocalDate endDate);
    
    /**
     * Delete old usage records
     */
    @Modifying
    @Query("DELETE FROM CardUsage cu WHERE cu.usageDate < :cutoffDate")
    int deleteOldUsageRecords(@Param("cutoffDate") LocalDate cutoffDate);
    
    /**
     * Find cards not used for specified days
     */
    @Query("SELECT DISTINCT cu.cardId FROM CardUsage cu WHERE cu.cardId NOT IN " +
           "(SELECT cu2.cardId FROM CardUsage cu2 WHERE cu2.usageDate >= :cutoffDate)")
    List<String> findUnusedCards(@Param("cutoffDate") LocalDate cutoffDate);
    
    /**
     * Get monthly usage summary
     */
    @Query("SELECT YEAR(cu.usageDate), MONTH(cu.usageDate), " +
           "SUM(cu.transactionCount), SUM(cu.totalSpent), COUNT(DISTINCT cu.cardId) " +
           "FROM CardUsage cu WHERE cu.usageDate BETWEEN :startDate AND :endDate " +
           "GROUP BY YEAR(cu.usageDate), MONTH(cu.usageDate) " +
           "ORDER BY YEAR(cu.usageDate), MONTH(cu.usageDate)")
    List<Object[]> getMonthlyUsageSummary(@Param("startDate") LocalDate startDate, 
                                         @Param("endDate") LocalDate endDate);
}