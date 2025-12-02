package com.waqiti.purchaseprotection.repository;

import com.waqiti.purchaseprotection.domain.ProtectionPolicy;
import com.waqiti.purchaseprotection.domain.PolicyStatus;
import com.waqiti.purchaseprotection.domain.RiskLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for protection policy operations.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Repository
public interface ProtectionPolicyRepository extends JpaRepository<ProtectionPolicy, String> {
    
    /**
     * Find policy by transaction ID.
     *
     * @param transactionId transaction ID
     * @return optional policy
     */
    Optional<ProtectionPolicy> findByTransactionId(String transactionId);
    
    /**
     * Find policies by buyer ID.
     *
     * @param buyerId buyer ID
     * @return list of policies
     */
    List<ProtectionPolicy> findByBuyerId(String buyerId);
    
    /**
     * Find policies by seller ID.
     *
     * @param sellerId seller ID
     * @return list of policies
     */
    List<ProtectionPolicy> findBySellerId(String sellerId);
    
    /**
     * Find active policies by buyer ID.
     *
     * @param buyerId buyer ID
     * @param status policy status
     * @param pageable pagination
     * @return page of policies
     */
    Page<ProtectionPolicy> findByBuyerIdAndStatus(String buyerId, PolicyStatus status, Pageable pageable);
    
    /**
     * Find policies expiring soon.
     *
     * @param startDate start of time window
     * @param endDate end of time window
     * @return list of expiring policies
     */
    @Query("SELECT p FROM ProtectionPolicy p WHERE p.status = 'ACTIVE' " +
           "AND p.endDate BETWEEN :startDate AND :endDate")
    List<ProtectionPolicy> findExpiringSoon(@Param("startDate") Instant startDate, 
                                           @Param("endDate") Instant endDate);
    
    /**
     * Find policies with active claims.
     *
     * @param buyerId buyer ID
     * @return list of policies with claims
     */
    @Query("SELECT p FROM ProtectionPolicy p WHERE p.buyerId = :buyerId " +
           "AND p.hasActiveClaim = true")
    List<ProtectionPolicy> findPoliciesWithActiveClaims(@Param("buyerId") String buyerId);
    
    /**
     * Calculate total protected amount for buyer.
     *
     * @param buyerId buyer ID
     * @param status policy status
     * @return total protected amount
     */
    @Query("SELECT COALESCE(SUM(p.coverageAmount), 0) FROM ProtectionPolicy p " +
           "WHERE p.buyerId = :buyerId AND p.status = :status")
    BigDecimal calculateTotalProtectedAmount(@Param("buyerId") String buyerId, 
                                            @Param("status") PolicyStatus status);
    
    /**
     * Get protection statistics for user.
     *
     * @param userId user ID
     * @return protection statistics
     */
    @Query("SELECT NEW com.waqiti.protection.dto.PolicyStatistics(" +
           "COUNT(p), " +
           "SUM(CASE WHEN p.status = 'ACTIVE' THEN 1 ELSE 0 END), " +
           "SUM(p.protectionFee), " +
           "SUM(p.coverageAmount), " +
           "AVG(p.riskScore)) " +
           "FROM ProtectionPolicy p WHERE p.buyerId = :userId OR p.sellerId = :userId")
    PolicyStatistics getPolicyStatistics(@Param("userId") String userId);
    
    /**
     * Find high-risk policies.
     *
     * @param riskLevel minimum risk level
     * @param pageable pagination
     * @return page of high-risk policies
     */
    Page<ProtectionPolicy> findByRiskLevelGreaterThanEqual(RiskLevel riskLevel, Pageable pageable);
    
    /**
     * Find policies requiring escrow.
     *
     * @return list of escrow policies
     */
    @Query("SELECT p FROM ProtectionPolicy p WHERE p.requiresEscrow = true " +
           "AND p.escrowStatus = 'HOLDING'")
    List<ProtectionPolicy> findPoliciesWithActiveEscrow();
    
    /**
     * Update policy status.
     *
     * @param policyId policy ID
     * @param status new status
     */
    @Modifying
    @Query("UPDATE ProtectionPolicy p SET p.status = :status " +
           "WHERE p.id = :policyId")
    void updatePolicyStatus(@Param("policyId") String policyId, 
                          @Param("status") PolicyStatus status);
    
    /**
     * Mark policies as expired.
     *
     * @param currentTime current time
     * @return number of expired policies
     */
    @Modifying
    @Query("UPDATE ProtectionPolicy p SET p.status = 'EXPIRED' " +
           "WHERE p.status = 'ACTIVE' AND p.endDate < :currentTime")
    int markExpiredPolicies(@Param("currentTime") Instant currentTime);
    
    /**
     * Find policies by seller and date range.
     *
     * @param sellerId seller ID
     * @param startDate start date
     * @param endDate end date
     * @return list of policies
     */
    @Query("SELECT p FROM ProtectionPolicy p WHERE p.sellerId = :sellerId " +
           "AND p.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY p.createdAt DESC")
    List<ProtectionPolicy> findSellerPoliciesInDateRange(@Param("sellerId") String sellerId,
                                                        @Param("startDate") Instant startDate,
                                                        @Param("endDate") Instant endDate);
    
    /**
     * Count policies by status for seller.
     *
     * @param sellerId seller ID
     * @param status policy status
     * @return count of policies
     */
    long countBySellerIdAndStatus(String sellerId, PolicyStatus status);
    
    /**
     * Check if buyer has active protection for seller.
     *
     * @param buyerId buyer ID
     * @param sellerId seller ID
     * @param status policy status
     * @return true if protection exists
     */
    boolean existsByBuyerIdAndSellerIdAndStatus(String buyerId, String sellerId, PolicyStatus status);
    
    /**
     * Get revenue from protection fees in date range.
     *
     * @param startDate start date
     * @param endDate end date
     * @return total revenue
     */
    @Query("SELECT COALESCE(SUM(p.totalFees), 0) FROM ProtectionPolicy p " +
           "WHERE p.feeCollected = true " +
           "AND p.createdAt BETWEEN :startDate AND :endDate")
    BigDecimal calculateRevenueInDateRange(@Param("startDate") Instant startDate,
                                          @Param("endDate") Instant endDate);
}