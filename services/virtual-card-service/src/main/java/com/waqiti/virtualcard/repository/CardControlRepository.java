package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.CardControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for CardControl entity
 */
@Repository
public interface CardControlRepository extends JpaRepository<CardControl, String> {
    
    /**
     * Find card control by card ID
     */
    Optional<CardControl> findByCardId(String cardId);
    
    /**
     * Find card controls by multiple card IDs
     */
    List<CardControl> findByCardIdIn(List<String> cardIds);
    
    /**
     * Find cards that allow online transactions
     */
    List<CardControl> findByAllowOnlineTransactionsTrue();
    
    /**
     * Find cards that allow international transactions
     */
    List<CardControl> findByAllowInternationalTransactionsTrue();
    
    /**
     * Find cards that allow ATM withdrawals
     */
    List<CardControl> findByAllowAtmWithdrawalsTrue();
    
    /**
     * Find cards that allow contactless payments
     */
    List<CardControl> findByAllowContactlessPaymentsTrue();
    
    /**
     * Find cards that block online transactions
     */
    List<CardControl> findByAllowOnlineTransactionsFalse();
    
    /**
     * Find cards that block international transactions
     */
    List<CardControl> findByAllowInternationalTransactionsFalse();
    
    /**
     * Find cards that block ATM withdrawals
     */
    List<CardControl> findByAllowAtmWithdrawalsFalse();
    
    /**
     * Find cards with gambling block enabled
     */
    List<CardControl> findByGamblingBlockTrue();
    
    /**
     * Find cards with adult content block enabled
     */
    List<CardControl> findByAdultContentBlockTrue();
    
    /**
     * Find cards with crypto purchase block enabled
     */
    List<CardControl> findByCryptoPurchaseBlockTrue();
    
    /**
     * Find cards with cash advance block enabled
     */
    List<CardControl> findByCashAdvanceBlockTrue();
    
    /**
     * Find cards with high risk merchant block enabled
     */
    List<CardControl> findByHighRiskMerchantBlockTrue();
    
    /**
     * Find cards that require MFA for online transactions
     */
    List<CardControl> findByRequireMfaForOnlineTrue();
    
    /**
     * Find cards that require MFA for international transactions
     */
    List<CardControl> findByRequireMfaForInternationalTrue();
    
    /**
     * Find cards with velocity check enabled
     */
    List<CardControl> findByVelocityCheckEnabledTrue();
    
    /**
     * Find cards with fraud check enabled
     */
    List<CardControl> findByFraudCheckEnabledTrue();
    
    /**
     * Find expired card controls
     */
    @Query("SELECT cc FROM CardControl cc WHERE cc.expiresAt IS NOT NULL AND cc.expiresAt <= CURRENT_TIMESTAMP")
    List<CardControl> findExpiredControls();
    
    /**
     * Find cards expiring soon
     */
    @Query("SELECT cc FROM CardControl cc WHERE cc.expiresAt IS NOT NULL AND " +
           "cc.expiresAt BETWEEN CURRENT_TIMESTAMP AND :expiryTime")
    List<CardControl> findControlsExpiringSoon(@Param("expiryTime") LocalDateTime expiryTime);
    
    /**
     * Find cards with notification enabled for all transactions
     */
    List<CardControl> findByNotificationOnAllTransactionsTrue();
    
    /**
     * Find cards with notification enabled for declined transactions
     */
    List<CardControl> findByNotificationOnDeclinedTrue();
    
    /**
     * Find cards with notification enabled for high amount transactions
     */
    List<CardControl> findByNotificationOnHighAmountTrue();
    
    /**
     * Update online transaction control
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.allowOnlineTransactions = :allow, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateOnlineTransactionControl(@Param("cardId") String cardId, @Param("allow") boolean allow);
    
    /**
     * Update international transaction control
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.allowInternationalTransactions = :allow, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateInternationalTransactionControl(@Param("cardId") String cardId, @Param("allow") boolean allow);
    
    /**
     * Update ATM withdrawal control
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.allowAtmWithdrawals = :allow, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateAtmWithdrawalControl(@Param("cardId") String cardId, @Param("allow") boolean allow);
    
    /**
     * Update contactless payment control
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.allowContactlessPayments = :allow, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateContactlessPaymentControl(@Param("cardId") String cardId, @Param("allow") boolean allow);
    
    /**
     * Update gambling block
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.gamblingBlock = :block, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateGamblingBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Update adult content block
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.adultContentBlock = :block, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateAdultContentBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Update crypto purchase block
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.cryptoPurchaseBlock = :block, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateCryptoPurchaseBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Update cash advance block
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.cashAdvanceBlock = :block, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateCashAdvanceBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Update MFA requirement for online transactions
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.requireMfaForOnline = :require, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateMfaForOnlineRequirement(@Param("cardId") String cardId, @Param("require") boolean require);
    
    /**
     * Update MFA requirement for international transactions
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.requireMfaForInternational = :require, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateMfaForInternationalRequirement(@Param("cardId") String cardId, @Param("require") boolean require);
    
    /**
     * Update transaction limits
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.maxTransactions = :maxTransactions, " +
           "cc.maxDailyTransactions = :maxDailyTransactions, " +
           "cc.maxWeeklyTransactions = :maxWeeklyTransactions, " +
           "cc.maxMonthlyTransactions = :maxMonthlyTransactions, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateTransactionLimits(@Param("cardId") String cardId,
                              @Param("maxTransactions") Integer maxTransactions,
                              @Param("maxDailyTransactions") Integer maxDailyTransactions,
                              @Param("maxWeeklyTransactions") Integer maxWeeklyTransactions,
                              @Param("maxMonthlyTransactions") Integer maxMonthlyTransactions);
    
    /**
     * Update notification settings
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.notificationOnAllTransactions = :allTransactions, " +
           "cc.notificationOnDeclined = :declined, " +
           "cc.notificationOnHighAmount = :highAmount, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateNotificationSettings(@Param("cardId") String cardId,
                                 @Param("allTransactions") boolean allTransactions,
                                 @Param("declined") boolean declined,
                                 @Param("highAmount") boolean highAmount);
    
    /**
     * Update expiry time
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.expiresAt = :expiresAt, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId = :cardId")
    int updateExpiryTime(@Param("cardId") String cardId, @Param("expiresAt") LocalDateTime expiresAt);
    
    /**
     * Bulk update controls for multiple cards
     */
    @Modifying
    @Query("UPDATE CardControl cc SET cc.allowOnlineTransactions = :allowOnline, " +
           "cc.allowInternationalTransactions = :allowInternational, " +
           "cc.updatedAt = CURRENT_TIMESTAMP WHERE cc.cardId IN :cardIds")
    int bulkUpdateTransactionControls(@Param("cardIds") List<String> cardIds,
                                    @Param("allowOnline") boolean allowOnline,
                                    @Param("allowInternational") boolean allowInternational);
    
    /**
     * Check if card allows specific transaction type
     */
    @Query("SELECT cc.allowOnlineTransactions FROM CardControl cc WHERE cc.cardId = :cardId")
    Boolean isOnlineTransactionAllowed(@Param("cardId") String cardId);
    
    /**
     * Check if card allows international transactions
     */
    @Query("SELECT cc.allowInternationalTransactions FROM CardControl cc WHERE cc.cardId = :cardId")
    Boolean isInternationalTransactionAllowed(@Param("cardId") String cardId);
    
    /**
     * Check if card allows ATM withdrawals
     */
    @Query("SELECT cc.allowAtmWithdrawals FROM CardControl cc WHERE cc.cardId = :cardId")
    Boolean isAtmWithdrawalAllowed(@Param("cardId") String cardId);
    
    /**
     * Check if card allows contactless payments
     */
    @Query("SELECT cc.allowContactlessPayments FROM CardControl cc WHERE cc.cardId = :cardId")
    Boolean isContactlessPaymentAllowed(@Param("cardId") String cardId);
    
    /**
     * Check if MFA is required for amount
     */
    @Query("SELECT CASE WHEN (cc.requireMfaAboveAmount IS NOT NULL AND :amount > cc.requireMfaAboveAmount) " +
           "THEN true ELSE false END FROM CardControl cc WHERE cc.cardId = :cardId")
    Boolean isMfaRequiredForAmount(@Param("cardId") String cardId, @Param("amount") java.math.BigDecimal amount);
    
    /**
     * Find cards with specific merchant category blocked
     */
    @Query("SELECT cc FROM CardControl cc WHERE :mcc MEMBER OF cc.blockedMerchantCategories")
    List<CardControl> findCardsWithMerchantCategoryBlocked(@Param("mcc") String mcc);
    
    /**
     * Find cards with specific country allowed
     */
    @Query("SELECT cc FROM CardControl cc WHERE :country MEMBER OF cc.allowedCountries")
    List<CardControl> findCardsWithCountryAllowed(@Param("country") String country);
    
    /**
     * Find cards with specific country blocked
     */
    @Query("SELECT cc FROM CardControl cc WHERE :country MEMBER OF cc.blockedCountries")
    List<CardControl> findCardsWithCountryBlocked(@Param("country") String country);
}