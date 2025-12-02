package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.CardLimit;
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
 * Repository interface for CardLimit entity
 */
@Repository
public interface CardLimitRepository extends JpaRepository<CardLimit, String> {
    
    /**
     * Find card limit by card ID
     */
    Optional<CardLimit> findByCardId(String cardId);
    
    /**
     * Find card limits by multiple card IDs
     */
    List<CardLimit> findByCardIdIn(List<String> cardIds);
    
    /**
     * Find cards that need daily limit reset
     */
    @Query("SELECT cl FROM CardLimit cl WHERE cl.resetDailyAt <= CURRENT_TIMESTAMP")
    List<CardLimit> findCardsNeedingDailyReset();
    
    /**
     * Find cards that need weekly limit reset
     */
    @Query("SELECT cl FROM CardLimit cl WHERE cl.resetWeeklyAt <= CURRENT_TIMESTAMP")
    List<CardLimit> findCardsNeedingWeeklyReset();
    
    /**
     * Find cards that need monthly limit reset
     */
    @Query("SELECT cl FROM CardLimit cl WHERE cl.resetMonthlyAt <= CURRENT_TIMESTAMP")
    List<CardLimit> findCardsNeedingMonthlyReset();
    
    /**
     * Find cards that need yearly limit reset
     */
    @Query("SELECT cl FROM CardLimit cl WHERE cl.resetYearlyAt <= CURRENT_TIMESTAMP")
    List<CardLimit> findCardsNeedingYearlyReset();
    
    /**
     * Update daily spent amount
     */
    @Modifying
    @Query("UPDATE CardLimit cl SET cl.dailySpent = cl.dailySpent + :amount, " +
           "cl.dailyTransactionCount = cl.dailyTransactionCount + 1, " +
           "cl.updatedAt = CURRENT_TIMESTAMP WHERE cl.cardId = :cardId")
    int updateDailySpent(@Param("cardId") String cardId, @Param("amount") BigDecimal amount);
    
    /**
     * Update weekly spent amount
     */
    @Modifying
    @Query("UPDATE CardLimit cl SET cl.weeklySpent = cl.weeklySpent + :amount, " +
           "cl.weeklyTransactionCount = cl.weeklyTransactionCount + 1, " +
           "cl.updatedAt = CURRENT_TIMESTAMP WHERE cl.cardId = :cardId")
    int updateWeeklySpent(@Param("cardId") String cardId, @Param("amount") BigDecimal amount);
    
    /**
     * Update monthly spent amount
     */
    @Modifying
    @Query("UPDATE CardLimit cl SET cl.monthlySpent = cl.monthlySpent + :amount, " +
           "cl.monthlyTransactionCount = cl.monthlyTransactionCount + 1, " +
           "cl.updatedAt = CURRENT_TIMESTAMP WHERE cl.cardId = :cardId")
    int updateMonthlySpent(@Param("cardId") String cardId, @Param("amount") BigDecimal amount);
    
    /**
     * Update yearly spent amount
     */
    @Modifying
    @Query("UPDATE CardLimit cl SET cl.yearlySpent = cl.yearlySpent + :amount, " +
           "cl.updatedAt = CURRENT_TIMESTAMP WHERE cl.cardId = :cardId")
    int updateYearlySpent(@Param("cardId") String cardId, @Param("amount") BigDecimal amount);
    
    /**
     * Reset daily limits
     */
    @Modifying
    @Query("UPDATE CardLimit cl SET cl.dailySpent = 0, cl.dailyTransactionCount = 0, " +
           "cl.resetDailyAt = :nextResetTime, cl.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cl.cardId = :cardId")
    int resetDailyLimits(@Param("cardId") String cardId, @Param("nextResetTime") LocalDateTime nextResetTime);
    
    /**
     * Reset weekly limits
     */
    @Modifying
    @Query("UPDATE CardLimit cl SET cl.weeklySpent = 0, cl.weeklyTransactionCount = 0, " +
           "cl.resetWeeklyAt = :nextResetTime, cl.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cl.cardId = :cardId")
    int resetWeeklyLimits(@Param("cardId") String cardId, @Param("nextResetTime") LocalDateTime nextResetTime);
    
    /**
     * Reset monthly limits
     */
    @Modifying
    @Query("UPDATE CardLimit cl SET cl.monthlySpent = 0, cl.monthlyTransactionCount = 0, " +
           "cl.resetMonthlyAt = :nextResetTime, cl.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cl.cardId = :cardId")
    int resetMonthlyLimits(@Param("cardId") String cardId, @Param("nextResetTime") LocalDateTime nextResetTime);
    
    /**
     * Reset yearly limits
     */
    @Modifying
    @Query("UPDATE CardLimit cl SET cl.yearlySpent = 0, " +
           "cl.resetYearlyAt = :nextResetTime, cl.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cl.cardId = :cardId")
    int resetYearlyLimits(@Param("cardId") String cardId, @Param("nextResetTime") LocalDateTime nextResetTime);
    
    /**
     * Find cards with daily limit exceeded
     */
    @Query("SELECT cl FROM CardLimit cl WHERE cl.dailyLimit IS NOT NULL AND " +
           "cl.dailySpent >= cl.dailyLimit")
    List<CardLimit> findCardsWithDailyLimitExceeded();
    
    /**
     * Find cards with weekly limit exceeded
     */
    @Query("SELECT cl FROM CardLimit cl WHERE cl.weeklyLimit IS NOT NULL AND " +
           "cl.weeklySpent >= cl.weeklyLimit")
    List<CardLimit> findCardsWithWeeklyLimitExceeded();
    
    /**
     * Find cards with monthly limit exceeded
     */
    @Query("SELECT cl FROM CardLimit cl WHERE cl.monthlyLimit IS NOT NULL AND " +
           "cl.monthlySpent >= cl.monthlyLimit")
    List<CardLimit> findCardsWithMonthlyLimitExceeded();
    
    /**
     * Find cards with yearly limit exceeded
     */
    @Query("SELECT cl FROM CardLimit cl WHERE cl.yearlyLimit IS NOT NULL AND " +
           "cl.yearlySpent >= cl.yearlyLimit")
    List<CardLimit> findCardsWithYearlyLimitExceeded();
    
    /**
     * Find cards approaching daily limit
     */
    @Query("SELECT cl FROM CardLimit cl WHERE cl.dailyLimit IS NOT NULL AND " +
           "cl.dailySpent >= (cl.dailyLimit * :percentage / 100)")
    List<CardLimit> findCardsApproachingDailyLimit(@Param("percentage") double percentage);
    
    /**
     * Find cards approaching monthly limit
     */
    @Query("SELECT cl FROM CardLimit cl WHERE cl.monthlyLimit IS NOT NULL AND " +
           "cl.monthlySpent >= (cl.monthlyLimit * :percentage / 100)")
    List<CardLimit> findCardsApproachingMonthlyLimit(@Param("percentage") double percentage);
    
    /**
     * Check if amount would exceed daily limit
     */
    @Query("SELECT CASE WHEN (cl.dailyLimit IS NULL OR cl.dailySpent + :amount <= cl.dailyLimit) " +
           "THEN true ELSE false END FROM CardLimit cl WHERE cl.cardId = :cardId")
    Boolean checkDailyLimitAvailability(@Param("cardId") String cardId, @Param("amount") BigDecimal amount);
    
    /**
     * Check if amount would exceed weekly limit
     */
    @Query("SELECT CASE WHEN (cl.weeklyLimit IS NULL OR cl.weeklySpent + :amount <= cl.weeklyLimit) " +
           "THEN true ELSE false END FROM CardLimit cl WHERE cl.cardId = :cardId")
    Boolean checkWeeklyLimitAvailability(@Param("cardId") String cardId, @Param("amount") BigDecimal amount);
    
    /**
     * Check if amount would exceed monthly limit
     */
    @Query("SELECT CASE WHEN (cl.monthlyLimit IS NULL OR cl.monthlySpent + :amount <= cl.monthlyLimit) " +
           "THEN true ELSE false END FROM CardLimit cl WHERE cl.cardId = :cardId")
    Boolean checkMonthlyLimitAvailability(@Param("cardId") String cardId, @Param("amount") BigDecimal amount);
    
    /**
     * Check if transaction limit allows amount
     */
    @Query("SELECT CASE WHEN (cl.transactionLimit IS NULL OR :amount <= cl.transactionLimit) " +
           "THEN true ELSE false END FROM CardLimit cl WHERE cl.cardId = :cardId")
    Boolean checkTransactionLimitAvailability(@Param("cardId") String cardId, @Param("amount") BigDecimal amount);
    
    /**
     * Get remaining daily limit
     */
    @Query("SELECT CASE WHEN cl.dailyLimit IS NULL THEN NULL " +
           "ELSE GREATEST(cl.dailyLimit - cl.dailySpent, 0) END " +
           "FROM CardLimit cl WHERE cl.cardId = :cardId")
    BigDecimal getRemainingDailyLimit(@Param("cardId") String cardId);
    
    /**
     * Get remaining monthly limit
     */
    @Query("SELECT CASE WHEN cl.monthlyLimit IS NULL THEN NULL " +
           "ELSE GREATEST(cl.monthlyLimit - cl.monthlySpent, 0) END " +
           "FROM CardLimit cl WHERE cl.cardId = :cardId")
    BigDecimal getRemainingMonthlyLimit(@Param("cardId") String cardId);
    
    /**
     * Bulk reset daily limits for multiple cards
     */
    @Modifying
    @Query("UPDATE CardLimit cl SET cl.dailySpent = 0, cl.dailyTransactionCount = 0, " +
           "cl.resetDailyAt = :nextResetTime, cl.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cl.cardId IN :cardIds")
    int bulkResetDailyLimits(@Param("cardIds") List<String> cardIds, 
                           @Param("nextResetTime") LocalDateTime nextResetTime);
    
    /**
     * Find cards with transaction count limits exceeded
     */
    @Query("SELECT cl FROM CardLimit cl WHERE " +
           "(cl.maxDailyTransactions IS NOT NULL AND cl.dailyTransactionCount >= cl.maxDailyTransactions) OR " +
           "(cl.maxWeeklyTransactions IS NOT NULL AND cl.weeklyTransactionCount >= cl.maxWeeklyTransactions) OR " +
           "(cl.maxMonthlyTransactions IS NOT NULL AND cl.monthlyTransactionCount >= cl.maxMonthlyTransactions)")
    List<CardLimit> findCardsWithTransactionLimitExceeded();
    
    /**
     * Update spending amounts and transaction counts in one operation
     */
    @Modifying
    @Query("UPDATE CardLimit cl SET " +
           "cl.dailySpent = cl.dailySpent + :amount, " +
           "cl.weeklySpent = cl.weeklySpent + :amount, " +
           "cl.monthlySpent = cl.monthlySpent + :amount, " +
           "cl.yearlySpent = cl.yearlySpent + :amount, " +
           "cl.dailyTransactionCount = cl.dailyTransactionCount + 1, " +
           "cl.weeklyTransactionCount = cl.weeklyTransactionCount + 1, " +
           "cl.monthlyTransactionCount = cl.monthlyTransactionCount + 1, " +
           "cl.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE cl.cardId = :cardId")
    int updateAllSpendingCounters(@Param("cardId") String cardId, @Param("amount") BigDecimal amount);
}