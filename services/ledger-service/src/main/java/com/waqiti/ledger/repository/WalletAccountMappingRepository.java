package com.waqiti.ledger.repository;

import com.waqiti.ledger.entity.WalletAccountMappingEntity;
import com.waqiti.ledger.entity.WalletAccountMappingEntity.MappingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRITICAL P0 FIX: Wallet Account Mapping Repository
 *
 * Provides database access for wallet-to-account mappings,
 * replacing hardcoded UUID generation with proper chart of accounts integration.
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-05
 */
@Repository
public interface WalletAccountMappingRepository extends JpaRepository<WalletAccountMappingEntity, UUID> {

    /**
     * Find active mapping for a specific wallet, currency, and mapping type
     * This is the primary lookup method for account resolution
     */
    Optional<WalletAccountMappingEntity> findByWalletIdAndCurrencyAndMappingTypeAndIsActiveTrue(
        UUID walletId,
        String currency,
        MappingType mappingType
    );

    /**
     * Find all active mappings for a wallet
     */
    List<WalletAccountMappingEntity> findByWalletIdAndIsActiveTrue(UUID walletId);

    /**
     * Find all active mappings for a specific mapping type and currency
     * Useful for getting all customer liability accounts for a currency
     */
    List<WalletAccountMappingEntity> findByMappingTypeAndCurrencyAndIsActiveTrue(
        MappingType mappingType,
        String currency
    );

    /**
     * Find mapping by account ID (reverse lookup)
     */
    Optional<WalletAccountMappingEntity> findByAccountIdAndIsActiveTrue(UUID accountId);

    /**
     * Check if a mapping exists for wallet/currency/type combination
     */
    boolean existsByWalletIdAndCurrencyAndMappingTypeAndIsActiveTrue(
        UUID walletId,
        String currency,
        MappingType mappingType
    );

    /**
     * Find all active mappings for a user
     */
    List<WalletAccountMappingEntity> findByUserIdAndIsActiveTrue(UUID userId);

    /**
     * Count active mappings for a wallet
     */
    @Query("SELECT COUNT(m) FROM WalletAccountMappingEntity m WHERE m.walletId = :walletId AND m.isActive = true")
    long countActiveWalletMappings(@Param("walletId") UUID walletId);

    /**
     * Find all mappings (active and inactive) for audit purposes
     */
    List<WalletAccountMappingEntity> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    /**
     * Find cash clearing account for a currency
     * Note: Cash clearing accounts typically don't have a specific walletId
     */
    @Query("SELECT m FROM WalletAccountMappingEntity m WHERE m.mappingType = 'CASH_CLEARING' AND m.currency = :currency AND m.isActive = true")
    Optional<WalletAccountMappingEntity> findCashClearingAccountByCurrency(@Param("currency") String currency);

    /**
     * Find fee revenue account for a currency
     */
    @Query("SELECT m FROM WalletAccountMappingEntity m WHERE m.mappingType = 'FEE_REVENUE' AND m.currency = :currency AND m.isActive = true")
    Optional<WalletAccountMappingEntity> findFeeRevenueAccountByCurrency(@Param("currency") String currency);

    /**
     * P2 QUICK WIN: Find any active mapping for a wallet by mapping type
     * Used for currency resolution when we don't know the currency upfront
     */
    Optional<WalletAccountMappingEntity> findFirstByWalletIdAndMappingTypeAndIsActiveTrue(
        UUID walletId,
        MappingType mappingType
    );
}
