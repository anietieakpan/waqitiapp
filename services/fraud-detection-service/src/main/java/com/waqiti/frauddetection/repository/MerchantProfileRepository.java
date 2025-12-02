package com.waqiti.frauddetection.repository;

import com.waqiti.frauddetection.entity.MerchantProfile;
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
 * Merchant Profile Repository
 *
 * Data access layer for merchant profiles with optimized queries
 * for fraud detection and merchant risk assessment.
 *
 * @author Waqiti Fraud Detection Team
 * @version 1.0 - Production Implementation
 */
@Repository
public interface MerchantProfileRepository extends JpaRepository<MerchantProfile, UUID> {

    /**
     * Find merchant profile by merchant ID
     */
    Optional<MerchantProfile> findByMerchantId(UUID merchantId);

    /**
     * Find high-risk merchants
     */
    @Query("SELECT mp FROM MerchantProfile mp WHERE mp.currentRiskLevel = 'HIGH' AND mp.lastTransactionDate >= :since")
    List<MerchantProfile> findHighRiskMerchantsSince(@Param("since") LocalDateTime since);

    /**
     * Find merchants with high chargeback rates
     */
    @Query("SELECT mp FROM MerchantProfile mp WHERE mp.chargebackRate > :threshold ORDER BY mp.chargebackRate DESC")
    List<MerchantProfile> findMerchantsWithHighChargebackRate(@Param("threshold") BigDecimal threshold);

    /**
     * Find merchants with recent fraud activity
     */
    @Query("SELECT mp FROM MerchantProfile mp WHERE mp.lastFraudDate >= :since ORDER BY mp.lastFraudDate DESC")
    List<MerchantProfile> findMerchantsWithRecentFraud(@Param("since") LocalDateTime since);

    /**
     * Find merchants requiring enhanced monitoring
     */
    @Query("SELECT mp FROM MerchantProfile mp WHERE mp.enhancedMonitoringRequired = true")
    List<MerchantProfile> findMerchantsRequiringEnhancedMonitoring();

    /**
     * Count merchants by risk level
     */
    @Query("SELECT mp.currentRiskLevel, COUNT(mp) FROM MerchantProfile mp GROUP BY mp.currentRiskLevel")
    List<Object[]> countMerchantsByRiskLevel();

    /**
     * Find merchants by category code with high fraud rates
     */
    @Query("SELECT mp FROM MerchantProfile mp WHERE mp.merchantCategoryCode = :mcc AND mp.fraudRate > :threshold")
    List<MerchantProfile> findHighFraudMerchantsByMCC(@Param("mcc") String mcc, @Param("threshold") BigDecimal threshold);

    /**
     * Check if merchant exists
     */
    boolean existsByMerchantId(UUID merchantId);
}
