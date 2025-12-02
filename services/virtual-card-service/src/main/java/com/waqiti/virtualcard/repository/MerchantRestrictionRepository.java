package com.waqiti.virtualcard.repository;

import com.waqiti.virtualcard.domain.MerchantRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for MerchantRestriction entity
 */
@Repository
public interface MerchantRestrictionRepository extends JpaRepository<MerchantRestriction, String> {
    
    /**
     * Find merchant restriction by card ID
     */
    Optional<MerchantRestriction> findByCardId(String cardId);
    
    /**
     * Find merchant restrictions by multiple card IDs
     */
    List<MerchantRestriction> findByCardIdIn(List<String> cardIds);
    
    /**
     * Find cards in whitelist mode
     */
    List<MerchantRestriction> findByWhitelistModeTrue();
    
    /**
     * Find cards in blacklist mode
     */
    List<MerchantRestriction> findByBlacklistModeTrue();
    
    /**
     * Find cards that allow unknown merchants
     */
    List<MerchantRestriction> findByAllowUnknownMerchantsTrue();
    
    /**
     * Find cards that block unknown merchants
     */
    List<MerchantRestriction> findByAllowUnknownMerchantsFalse();
    
    /**
     * Find cards that require approval for new merchants
     */
    List<MerchantRestriction> findByRequireApprovalForNewMerchantsTrue();
    
    /**
     * Find cards that auto-approve trusted merchants
     */
    List<MerchantRestriction> findByAutoApproveTrustedMerchantsTrue();
    
    /**
     * Find cards that block high-risk merchants
     */
    List<MerchantRestriction> findByBlockHighRiskMerchantsTrue();
    
    /**
     * Find cards with gambling block enabled
     */
    List<MerchantRestriction> findByBlockGamblingTrue();
    
    /**
     * Find cards with adult content block enabled
     */
    List<MerchantRestriction> findByBlockAdultContentTrue();
    
    /**
     * Find cards with alcohol block enabled
     */
    List<MerchantRestriction> findByBlockAlcoholTrue();
    
    /**
     * Find cards with tobacco block enabled
     */
    List<MerchantRestriction> findByBlockTobaccoTrue();
    
    /**
     * Find cards with cryptocurrency block enabled
     */
    List<MerchantRestriction> findByBlockCryptocurrencyTrue();
    
    /**
     * Find cards with cash advance block enabled
     */
    List<MerchantRestriction> findByBlockCashAdvanceTrue();
    
    /**
     * Find cards with money transfer block enabled
     */
    List<MerchantRestriction> findByBlockMoneyTransferTrue();
    
    /**
     * Find cards with subscription services block enabled
     */
    List<MerchantRestriction> findByBlockSubscriptionServicesTrue();
    
    /**
     * Check if merchant is in allowed list
     */
    @Query("SELECT CASE WHEN :merchantName MEMBER OF mr.allowedMerchants " +
           "THEN true ELSE false END FROM MerchantRestriction mr WHERE mr.cardId = :cardId")
    Boolean isMerchantAllowed(@Param("cardId") String cardId, @Param("merchantName") String merchantName);
    
    /**
     * Check if merchant is in blocked list
     */
    @Query("SELECT CASE WHEN :merchantName MEMBER OF mr.blockedMerchants " +
           "THEN true ELSE false END FROM MerchantRestriction mr WHERE mr.cardId = :cardId")
    Boolean isMerchantBlocked(@Param("cardId") String cardId, @Param("merchantName") String merchantName);
    
    /**
     * Check if merchant ID is in allowed list
     */
    @Query("SELECT CASE WHEN :merchantId MEMBER OF mr.allowedMerchantIds " +
           "THEN true ELSE false END FROM MerchantRestriction mr WHERE mr.cardId = :cardId")
    Boolean isMerchantIdAllowed(@Param("cardId") String cardId, @Param("merchantId") String merchantId);
    
    /**
     * Check if merchant ID is in blocked list
     */
    @Query("SELECT CASE WHEN :merchantId MEMBER OF mr.blockedMerchantIds " +
           "THEN true ELSE false END FROM MerchantRestriction mr WHERE mr.cardId = :cardId")
    Boolean isMerchantIdBlocked(@Param("cardId") String cardId, @Param("merchantId") String merchantId);
    
    /**
     * Check if MCC is in allowed list
     */
    @Query("SELECT CASE WHEN :mcc MEMBER OF mr.allowedMccs " +
           "THEN true ELSE false END FROM MerchantRestriction mr WHERE mr.cardId = :cardId")
    Boolean isMccAllowed(@Param("cardId") String cardId, @Param("mcc") String mcc);
    
    /**
     * Check if MCC is in blocked list
     */
    @Query("SELECT CASE WHEN :mcc MEMBER OF mr.blockedMccs " +
           "THEN true ELSE false END FROM MerchantRestriction mr WHERE mr.cardId = :cardId")
    Boolean isMccBlocked(@Param("cardId") String cardId, @Param("mcc") String mcc);
    
    /**
     * Check if category is in allowed list
     */
    @Query("SELECT CASE WHEN :category MEMBER OF mr.allowedCategories " +
           "THEN true ELSE false END FROM MerchantRestriction mr WHERE mr.cardId = :cardId")
    Boolean isCategoryAllowed(@Param("cardId") String cardId, @Param("category") String category);
    
    /**
     * Check if category is in blocked list
     */
    @Query("SELECT CASE WHEN :category MEMBER OF mr.blockedCategories " +
           "THEN true ELSE false END FROM MerchantRestriction mr WHERE mr.cardId = :cardId")
    Boolean isCategoryBlocked(@Param("cardId") String cardId, @Param("category") String category);
    
    /**
     * Get merchant spending limit
     */
    @Query("SELECT mr.merchantLimits[:merchantId] FROM MerchantRestriction mr WHERE mr.cardId = :cardId")
    java.math.BigDecimal getMerchantLimit(@Param("cardId") String cardId, @Param("merchantId") String merchantId);
    
    /**
     * Get category spending limit
     */
    @Query("SELECT mr.categoryLimits[:category] FROM MerchantRestriction mr WHERE mr.cardId = :cardId")
    java.math.BigDecimal getCategoryLimit(@Param("cardId") String cardId, @Param("category") String category);
    
    /**
     * Find cards with specific merchant allowed
     */
    @Query("SELECT mr FROM MerchantRestriction mr WHERE :merchantName MEMBER OF mr.allowedMerchants")
    List<MerchantRestriction> findCardsWithMerchantAllowed(@Param("merchantName") String merchantName);
    
    /**
     * Find cards with specific merchant blocked
     */
    @Query("SELECT mr FROM MerchantRestriction mr WHERE :merchantName MEMBER OF mr.blockedMerchants")
    List<MerchantRestriction> findCardsWithMerchantBlocked(@Param("merchantName") String merchantName);
    
    /**
     * Find cards with specific MCC allowed
     */
    @Query("SELECT mr FROM MerchantRestriction mr WHERE :mcc MEMBER OF mr.allowedMccs")
    List<MerchantRestriction> findCardsWithMccAllowed(@Param("mcc") String mcc);
    
    /**
     * Find cards with specific MCC blocked
     */
    @Query("SELECT mr FROM MerchantRestriction mr WHERE :mcc MEMBER OF mr.blockedMccs")
    List<MerchantRestriction> findCardsWithMccBlocked(@Param("mcc") String mcc);
    
    /**
     * Find cards with specific category allowed
     */
    @Query("SELECT mr FROM MerchantRestriction mr WHERE :category MEMBER OF mr.allowedCategories")
    List<MerchantRestriction> findCardsWithCategoryAllowed(@Param("category") String category);
    
    /**
     * Find cards with specific category blocked
     */
    @Query("SELECT mr FROM MerchantRestriction mr WHERE :category MEMBER OF mr.blockedCategories")
    List<MerchantRestriction> findCardsWithCategoryBlocked(@Param("category") String category);
    
    /**
     * Update whitelist mode
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET mr.whitelistMode = :whitelistMode, " +
           "mr.updatedAt = CURRENT_TIMESTAMP WHERE mr.cardId = :cardId")
    int updateWhitelistMode(@Param("cardId") String cardId, @Param("whitelistMode") boolean whitelistMode);
    
    /**
     * Update blacklist mode
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET mr.blacklistMode = :blacklistMode, " +
           "mr.updatedAt = CURRENT_TIMESTAMP WHERE mr.cardId = :cardId")
    int updateBlacklistMode(@Param("cardId") String cardId, @Param("blacklistMode") boolean blacklistMode);
    
    /**
     * Update allow unknown merchants setting
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET mr.allowUnknownMerchants = :allow, " +
           "mr.updatedAt = CURRENT_TIMESTAMP WHERE mr.cardId = :cardId")
    int updateAllowUnknownMerchants(@Param("cardId") String cardId, @Param("allow") boolean allow);
    
    /**
     * Update gambling block
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET mr.blockGambling = :block, " +
           "mr.updatedAt = CURRENT_TIMESTAMP WHERE mr.cardId = :cardId")
    int updateGamblingBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Update adult content block
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET mr.blockAdultContent = :block, " +
           "mr.updatedAt = CURRENT_TIMESTAMP WHERE mr.cardId = :cardId")
    int updateAdultContentBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Update alcohol block
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET mr.blockAlcohol = :block, " +
           "mr.updatedAt = CURRENT_TIMESTAMP WHERE mr.cardId = :cardId")
    int updateAlcoholBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Update tobacco block
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET mr.blockTobacco = :block, " +
           "mr.updatedAt = CURRENT_TIMESTAMP WHERE mr.cardId = :cardId")
    int updateTobaccoBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Update cryptocurrency block
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET mr.blockCryptocurrency = :block, " +
           "mr.updatedAt = CURRENT_TIMESTAMP WHERE mr.cardId = :cardId")
    int updateCryptocurrencyBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Update cash advance block
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET mr.blockCashAdvance = :block, " +
           "mr.updatedAt = CURRENT_TIMESTAMP WHERE mr.cardId = :cardId")
    int updateCashAdvanceBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Update money transfer block
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET mr.blockMoneyTransfer = :block, " +
           "mr.updatedAt = CURRENT_TIMESTAMP WHERE mr.cardId = :cardId")
    int updateMoneyTransferBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Update subscription services block
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET mr.blockSubscriptionServices = :block, " +
           "mr.updatedAt = CURRENT_TIMESTAMP WHERE mr.cardId = :cardId")
    int updateSubscriptionServicesBlock(@Param("cardId") String cardId, @Param("block") boolean block);
    
    /**
     * Bulk update category blocks
     */
    @Modifying
    @Query("UPDATE MerchantRestriction mr SET " +
           "mr.blockGambling = :gambling, " +
           "mr.blockAdultContent = :adultContent, " +
           "mr.blockAlcohol = :alcohol, " +
           "mr.blockTobacco = :tobacco, " +
           "mr.blockCryptocurrency = :cryptocurrency, " +
           "mr.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE mr.cardId = :cardId")
    int bulkUpdateCategoryBlocks(@Param("cardId") String cardId,
                               @Param("gambling") boolean gambling,
                               @Param("adultContent") boolean adultContent,
                               @Param("alcohol") boolean alcohol,
                               @Param("tobacco") boolean tobacco,
                               @Param("cryptocurrency") boolean cryptocurrency);
}