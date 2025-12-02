package com.waqiti.transaction.repository;

import com.waqiti.transaction.domain.FeeConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Fee Configuration Management
 *
 * Manages fee structures for transactions including:
 * - Fixed fees
 * - Percentage-based fees
 * - Tiered fees
 * - Regional fees
 * - Merchant fees
 *
 * CRITICAL: This repository was missing and causing runtime NullPointerException.
 *
 * @author Waqiti Platform Team
 * @since 2025-10-31 - CRITICAL FIX
 */
@Repository
public interface FeeConfigurationRepository extends JpaRepository<FeeConfiguration, UUID> {

    /**
     * Find fee configuration by transaction type
     */
    @Query("SELECT fc FROM FeeConfiguration fc WHERE fc.transactionType = :transactionType " +
           "AND fc.active = true " +
           "ORDER BY fc.priority DESC")
    List<FeeConfiguration> findByTransactionType(@Param("transactionType") String transactionType);

    /**
     * Find fee configuration by transaction type and amount range
     * Used for tiered fee structures
     */
    @Query("SELECT fc FROM FeeConfiguration fc WHERE fc.transactionType = :transactionType " +
           "AND fc.active = true " +
           "AND (fc.minAmount IS NULL OR fc.minAmount <= :amount) " +
           "AND (fc.maxAmount IS NULL OR fc.maxAmount >= :amount) " +
           "ORDER BY fc.priority DESC")
    List<FeeConfiguration> findByTransactionTypeAndAmountRange(
        @Param("transactionType") String transactionType,
        @Param("amount") BigDecimal amount
    );

    /**
     * Find fee configuration by transaction type, amount, and currency
     */
    @Query("SELECT fc FROM FeeConfiguration fc WHERE fc.transactionType = :transactionType " +
           "AND fc.currency = :currency " +
           "AND fc.active = true " +
           "AND (fc.minAmount IS NULL OR fc.minAmount <= :amount) " +
           "AND (fc.maxAmount IS NULL OR fc.maxAmount >= :amount) " +
           "ORDER BY fc.priority DESC")
    Optional<FeeConfiguration> findApplicableFeeConfiguration(
        @Param("transactionType") String transactionType,
        @Param("currency") String currency,
        @Param("amount") BigDecimal amount
    );

    /**
     * Find merchant-specific fee configuration
     */
    @Query("SELECT fc FROM FeeConfiguration fc WHERE fc.merchantId = :merchantId " +
           "AND fc.transactionType = :transactionType " +
           "AND fc.active = true " +
           "ORDER BY fc.priority DESC")
    Optional<FeeConfiguration> findByMerchantIdAndTransactionType(
        @Param("merchantId") String merchantId,
        @Param("transactionType") String transactionType
    );

    /**
     * Find regional fee configuration
     */
    @Query("SELECT fc FROM FeeConfiguration fc WHERE fc.region = :region " +
           "AND fc.transactionType = :transactionType " +
           "AND fc.active = true " +
           "ORDER BY fc.priority DESC")
    Optional<FeeConfiguration> findByRegionAndTransactionType(
        @Param("region") String region,
        @Param("transactionType") String transactionType
    );

    /**
     * Find all active fee configurations
     */
    @Query("SELECT fc FROM FeeConfiguration fc WHERE fc.active = true " +
           "ORDER BY fc.transactionType, fc.priority DESC")
    List<FeeConfiguration> findAllActive();

    /**
     * Find fee configurations by currency
     */
    @Query("SELECT fc FROM FeeConfiguration fc WHERE fc.currency = :currency " +
           "AND fc.active = true")
    List<FeeConfiguration> findByCurrency(@Param("currency") String currency);

    /**
     * Find fee configurations requiring review
     * (Not updated in X days)
     */
    @Query("SELECT fc FROM FeeConfiguration fc WHERE fc.lastReviewedAt < :cutoffDate")
    List<FeeConfiguration> findRequiringReview(@Param("cutoffDate") java.time.LocalDateTime cutoffDate);

    /**
     * Find default fee configuration (fallback when no specific configuration found)
     */
    @Query("SELECT fc FROM FeeConfiguration fc WHERE fc.transactionType = :transactionType " +
           "AND fc.isDefault = true " +
           "AND fc.active = true")
    Optional<FeeConfiguration> findDefaultConfiguration(@Param("transactionType") String transactionType);

    /**
     * Find fee configurations by payment method
     */
    @Query("SELECT fc FROM FeeConfiguration fc WHERE fc.paymentMethod = :paymentMethod " +
           "AND fc.active = true")
    List<FeeConfiguration> findByPaymentMethod(@Param("paymentMethod") String paymentMethod);

    /**
     * Find volume-based fee configurations
     * Used for users with high transaction volumes
     */
    @Query("SELECT fc FROM FeeConfiguration fc WHERE fc.volumeTier IS NOT NULL " +
           "AND fc.transactionType = :transactionType " +
           "AND fc.active = true " +
           "ORDER BY fc.volumeTier ASC")
    List<FeeConfiguration> findVolumeBasedConfigurations(@Param("transactionType") String transactionType);

    /**
     * Check if fee configuration exists for combination
     */
    @Query("SELECT CASE WHEN COUNT(fc) > 0 THEN true ELSE false END " +
           "FROM FeeConfiguration fc " +
           "WHERE fc.transactionType = :transactionType " +
           "AND fc.currency = :currency " +
           "AND fc.active = true")
    boolean existsByTransactionTypeAndCurrency(
        @Param("transactionType") String transactionType,
        @Param("currency") String currency
    );
}
