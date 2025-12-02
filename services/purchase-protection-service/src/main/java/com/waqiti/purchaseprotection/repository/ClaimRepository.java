package com.waqiti.purchaseprotection.repository;

import com.waqiti.purchaseprotection.domain.ProtectionClaim;
import com.waqiti.purchaseprotection.domain.ClaimStatus;
import com.waqiti.purchaseprotection.domain.ClaimType;
import com.waqiti.purchaseprotection.domain.FraudResult;
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
import java.util.Set;

/**
 * Repository for protection claim operations.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Repository
public interface ClaimRepository extends JpaRepository<ProtectionClaim, String> {
    
    /**
     * Find claims by policy ID.
     *
     * @param policyId policy ID
     * @return list of claims
     */
    List<ProtectionClaim> findByPolicyId(String policyId);
    
    /**
     * Find claims by buyer ID.
     *
     * @param buyerId buyer ID
     * @return list of claims
     */
    @Query("SELECT c FROM ProtectionClaim c JOIN c.policy p WHERE p.buyerId = :buyerId")
    List<ProtectionClaim> findByBuyerId(@Param("buyerId") String buyerId);
    
    /**
     * Find claims by status.
     *
     * @param status claim status
     * @return list of claims
     */
    List<ProtectionClaim> findByStatus(ClaimStatus status);
    
    /**
     * Find claims by multiple statuses.
     *
     * @param statuses set of statuses
     * @param pageable pagination
     * @return page of claims
     */
    Page<ProtectionClaim> findByStatusIn(Set<ClaimStatus> statuses, Pageable pageable);
    
    /**
     * Find claims pending review.
     *
     * @return list of pending claims
     */
    @Query("SELECT c FROM ProtectionClaim c WHERE c.status IN ('SUBMITTED', 'UNDER_REVIEW') " +
           "ORDER BY c.filedAt ASC")
    List<ProtectionClaim> findPendingReview();
    
    /**
     * Find claims under investigation.
     *
     * @param pageable pagination
     * @return page of claims
     */
    Page<ProtectionClaim> findByStatus(ClaimStatus status, Pageable pageable);
    
    /**
     * Find high-risk claims.
     *
     * @param minFraudScore minimum fraud score
     * @return list of high-risk claims
     */
    @Query("SELECT c FROM ProtectionClaim c WHERE c.fraudScore >= :minScore " +
           "OR c.fraudCheckResult = 'HIGH_RISK' OR c.fraudCheckResult = 'FRAUDULENT'")
    List<ProtectionClaim> findHighRiskClaims(@Param("minScore") Double minFraudScore);
    
    /**
     * Find auto-approved claims.
     *
     * @param startDate start date
     * @param endDate end date
     * @return list of auto-approved claims
     */
    @Query("SELECT c FROM ProtectionClaim c WHERE c.autoApproved = true " +
           "AND c.approvedAt BETWEEN :startDate AND :endDate")
    List<ProtectionClaim> findAutoApprovedClaims(@Param("startDate") Instant startDate,
                                                @Param("endDate") Instant endDate);
    
    /**
     * Calculate total claims amount by status.
     *
     * @param status claim status
     * @return total amount
     */
    @Query("SELECT COALESCE(SUM(c.claimAmount), 0) FROM ProtectionClaim c " +
           "WHERE c.status = :status")
    BigDecimal calculateTotalClaimsAmount(@Param("status") ClaimStatus status);
    
    /**
     * Calculate total paid claims amount.
     *
     * @param startDate start date
     * @param endDate end date
     * @return total paid amount
     */
    @Query("SELECT COALESCE(SUM(c.approvedAmount), 0) FROM ProtectionClaim c " +
           "WHERE c.status = 'PAID' AND c.paidAt BETWEEN :startDate AND :endDate")
    BigDecimal calculateTotalPaidAmount(@Param("startDate") Instant startDate,
                                       @Param("endDate") Instant endDate);
    
    /**
     * Get claim statistics.
     *
     * @param buyerId buyer ID
     * @return claim statistics
     */
    @Query("SELECT NEW com.waqiti.protection.dto.ClaimStatistics(" +
           "COUNT(c), " +
           "SUM(CASE WHEN c.status = 'APPROVED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN c.status = 'REJECTED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN c.status = 'PAID' THEN 1 ELSE 0 END), " +
           "SUM(c.claimAmount), " +
           "SUM(c.approvedAmount)) " +
           "FROM ProtectionClaim c JOIN c.policy p WHERE p.buyerId = :buyerId")
    ClaimStatistics getClaimStatistics(@Param("buyerId") String buyerId);
    
    /**
     * Find claims by type.
     *
     * @param claimType claim type
     * @param pageable pagination
     * @return page of claims
     */
    Page<ProtectionClaim> findByClaimType(ClaimType claimType, Pageable pageable);
    
    /**
     * Update claim status.
     *
     * @param claimId claim ID
     * @param status new status
     */
    @Modifying
    @Query("UPDATE ProtectionClaim c SET c.status = :status " +
           "WHERE c.id = :claimId")
    void updateClaimStatus(@Param("claimId") String claimId,
                         @Param("status") ClaimStatus status);
    
    /**
     * Find claims filed in date range.
     *
     * @param startDate start date
     * @param endDate end date
     * @return list of claims
     */
    @Query("SELECT c FROM ProtectionClaim c WHERE c.filedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY c.filedAt DESC")
    List<ProtectionClaim> findClaimsFiledInDateRange(@Param("startDate") Instant startDate,
                                                    @Param("endDate") Instant endDate);
    
    /**
     * Count claims by fraud result.
     *
     * @param fraudResult fraud result
     * @return count of claims
     */
    long countByFraudCheckResult(FraudResult fraudResult);
    
    /**
     * Find claims requiring manual review.
     *
     * @return list of claims
     */
    @Query("SELECT c FROM ProtectionClaim c WHERE c.status = 'UNDER_REVIEW' " +
           "AND c.autoApproved = false " +
           "AND (c.fraudScore > 50 OR c.claimAmount > 1000) " +
           "ORDER BY c.filedAt ASC")
    List<ProtectionClaim> findClaimsRequiringManualReview();
    
    /**
     * Get fraud detection accuracy metrics.
     *
     * @param startDate start date
     * @param endDate end date
     * @return fraud metrics
     */
    @Query("SELECT NEW com.waqiti.protection.dto.FraudMetrics(" +
           "COUNT(c), " +
           "SUM(CASE WHEN c.fraudCheckResult = 'FRAUDULENT' AND c.status = 'REJECTED' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN c.fraudCheckResult = 'LOW_RISK' AND c.status = 'APPROVED' THEN 1 ELSE 0 END), " +
           "AVG(c.fraudScore)) " +
           "FROM ProtectionClaim c WHERE c.filedAt BETWEEN :startDate AND :endDate")
    FraudMetrics getFraudMetrics(@Param("startDate") Instant startDate,
                                @Param("endDate") Instant endDate);
}