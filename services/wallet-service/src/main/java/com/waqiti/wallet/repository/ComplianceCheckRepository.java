package com.waqiti.wallet.repository;

import com.waqiti.wallet.entity.ComplianceCheck;
import com.waqiti.wallet.entity.ComplianceCheck.CheckType;
import com.waqiti.wallet.entity.ComplianceCheck.CheckStatus;
import com.waqiti.wallet.entity.ComplianceCheck.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Compliance Check management
 * 
 * PERFORMANCE: Optimized queries with proper indexing
 * COMPLIANCE: All queries support regulatory audit requirements
 */
@Repository
public interface ComplianceCheckRepository extends JpaRepository<ComplianceCheck, UUID> {
    
    List<ComplianceCheck> findByWalletIdOrderByInitiatedAtDesc(String walletId);
    
    List<ComplianceCheck> findByUserIdOrderByInitiatedAtDesc(String userId);
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.walletId = :walletId " +
           "AND cc.checkType = :checkType ORDER BY cc.initiatedAt DESC")
    List<ComplianceCheck> findByWalletIdAndCheckType(
        @Param("walletId") String walletId,
        @Param("checkType") CheckType checkType
    );
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.userId = :userId " +
           "AND cc.checkType = :checkType ORDER BY cc.initiatedAt DESC")
    List<ComplianceCheck> findByUserIdAndCheckType(
        @Param("userId") String userId,
        @Param("checkType") CheckType checkType
    );
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.status = :status " +
           "ORDER BY cc.initiatedAt DESC")
    List<ComplianceCheck> findByStatus(@Param("status") CheckStatus status);
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.riskLevel = :riskLevel " +
           "ORDER BY cc.initiatedAt DESC")
    List<ComplianceCheck> findByRiskLevel(@Param("riskLevel") RiskLevel riskLevel);
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.riskLevel IN ('HIGH', 'CRITICAL') " +
           "ORDER BY cc.initiatedAt DESC")
    List<ComplianceCheck> findHighRiskChecks();
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.status IN ('FLAGGED', 'REQUIRES_REVIEW') " +
           "ORDER BY cc.initiatedAt ASC")
    List<ComplianceCheck> findChecksRequiringReview();
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.status = 'BLOCKED' " +
           "ORDER BY cc.initiatedAt DESC")
    List<ComplianceCheck> findBlockedChecks();
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.externalCheckId = :externalCheckId")
    Optional<ComplianceCheck> findByExternalCheckId(@Param("externalCheckId") String externalCheckId);
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.initiatedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY cc.initiatedAt DESC")
    List<ComplianceCheck> findByInitiatedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.checkType = :checkType " +
           "AND cc.initiatedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY cc.initiatedAt DESC")
    List<ComplianceCheck> findByCheckTypeAndInitiatedAtBetween(
        @Param("checkType") CheckType checkType,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT COUNT(cc) FROM ComplianceCheck cc WHERE cc.walletId = :walletId " +
           "AND cc.status = :status")
    long countByWalletIdAndStatus(
        @Param("walletId") String walletId,
        @Param("status") CheckStatus status
    );
    
    @Query("SELECT COUNT(cc) FROM ComplianceCheck cc WHERE cc.userId = :userId " +
           "AND cc.riskLevel IN ('HIGH', 'CRITICAL')")
    long countHighRiskChecksByUserId(@Param("userId") String userId);
    
    @Query("SELECT cc.checkType, COUNT(cc) FROM ComplianceCheck cc " +
           "WHERE cc.initiatedAt >= :since GROUP BY cc.checkType")
    List<Object[]> countByCheckTypeSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT cc.status, COUNT(cc) FROM ComplianceCheck cc " +
           "WHERE cc.initiatedAt >= :since GROUP BY cc.status")
    List<Object[]> countByStatusSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.status = 'PENDING' " +
           "AND cc.initiatedAt < :threshold ORDER BY cc.initiatedAt ASC")
    List<ComplianceCheck> findStuckChecks(@Param("threshold") LocalDateTime threshold);
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.walletId = :walletId " +
           "AND cc.status = 'PASSED' AND cc.checkType = 'KYC' " +
           "ORDER BY cc.completedAt DESC")
    Optional<ComplianceCheck> findLatestPassedKYCCheck(@Param("walletId") String walletId);
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.userId = :userId " +
           "AND cc.checkType = 'SANCTIONS' AND cc.status = 'BLOCKED' " +
           "ORDER BY cc.initiatedAt DESC")
    List<ComplianceCheck> findSanctionsBlocksByUserId(@Param("userId") String userId);
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.userId = :userId " +
           "AND cc.checkType = 'PEP' AND cc.status IN ('PASSED', 'FLAGGED') " +
           "ORDER BY cc.initiatedAt DESC")
    Optional<ComplianceCheck> findLatestPEPCheckByUserId(@Param("userId") String userId);
    
    @Query("SELECT cc FROM ComplianceCheck cc WHERE cc.reviewedBy = :reviewedBy " +
           "ORDER BY cc.reviewedAt DESC")
    List<ComplianceCheck> findByReviewedBy(@Param("reviewedBy") String reviewedBy);
}