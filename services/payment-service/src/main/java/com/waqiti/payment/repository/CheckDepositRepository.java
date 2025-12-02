package com.waqiti.payment.repository;

import com.waqiti.payment.entity.CheckDeposit;
import com.waqiti.payment.entity.CheckDepositStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 * Repository for CheckDeposit entities
 */
@Repository
public interface CheckDepositRepository extends JpaRepository<CheckDeposit, UUID> {
    
    /**
     * Find check deposits by user ID
     */
    List<CheckDeposit> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find check deposits by user ID and status
     */
    List<CheckDeposit> findByUserIdAndStatus(UUID userId, CheckDepositStatus status);
    
    /**
     * Find check deposits by user ID created after a specific date
     */
    List<CheckDeposit> findByUserIdAndCreatedAtAfter(UUID userId, LocalDateTime date);
    
    /**
     * Check for duplicate deposits by MICR data and amount
     */
    @Query("SELECT c FROM CheckDeposit c WHERE c.micrRoutingNumber = :routing " +
           "AND c.micrAccountNumber = :account AND c.checkNumber = :checkNumber " +
           "AND c.amount = :amount AND c.status NOT IN ('REJECTED', 'CANCELLED') " +
           "AND c.createdAt > :cutoffDate")
    List<CheckDeposit> findPotentialDuplicates(
        @Param("routing") String micrRoutingNumber,
        @Param("account") String micrAccountNumber,
        @Param("checkNumber") String checkNumber,
        @Param("amount") BigDecimal amount,
        @Param("cutoffDate") LocalDateTime cutoffDate
    );
    
    /**
     * Check for duplicate by image hash
     */
    @Query("SELECT c FROM CheckDeposit c WHERE (c.frontImageHash = :frontHash OR c.backImageHash = :backHash) " +
           "AND c.status NOT IN ('REJECTED', 'CANCELLED') AND c.createdAt > :cutoffDate")
    List<CheckDeposit> findByImageHash(
        @Param("frontHash") String frontImageHash,
        @Param("backHash") String backImageHash,
        @Param("cutoffDate") LocalDateTime cutoffDate
    );
    
    /**
     * Find by idempotency key
     */
    Optional<CheckDeposit> findByIdempotencyKey(String idempotencyKey);
    
    /**
     * Calculate daily total for user
     */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CheckDeposit c " +
           "WHERE c.userId = :userId AND c.createdAt >= :startDate " +
           "AND c.status NOT IN ('REJECTED', 'CANCELLED', 'RETURNED')")
    BigDecimal calculateDailyTotal(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate
    );
    
    /**
     * Calculate monthly total for user
     */
    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM CheckDeposit c " +
           "WHERE c.userId = :userId AND c.createdAt >= :startDate " +
           "AND c.status NOT IN ('REJECTED', 'CANCELLED', 'RETURNED')")
    BigDecimal calculateMonthlyTotal(
        @Param("userId") UUID userId,
        @Param("startDate") LocalDateTime startDate
    );
    
    /**
     * Find deposits requiring manual review
     */
    @Query("SELECT c FROM CheckDeposit c WHERE c.manualReviewRequired = true " +
           "AND c.status = 'MANUAL_REVIEW' ORDER BY c.createdAt ASC")
    Page<CheckDeposit> findDepositsForManualReview(Pageable pageable);
    
    /**
     * Find deposits with holds ready for release
     */
    @Query("SELECT c FROM CheckDeposit c WHERE c.holdReleaseDate <= CURRENT_DATE " +
           "AND c.status IN ('PARTIAL_HOLD', 'FULL_HOLD')")
    List<CheckDeposit> findDepositsWithExpiredHolds();
    
    /**
     * Find deposits by external reference
     */
    Optional<CheckDeposit> findByExternalReferenceId(String externalReferenceId);
    
    /**
     * Count deposits by status for monitoring
     */
    @Query("SELECT c.status, COUNT(c) FROM CheckDeposit c " +
           "WHERE c.createdAt >= :startDate GROUP BY c.status")
    List<Object[]> countByStatusSince(@Param("startDate") LocalDateTime startDate);
    
    /**
     * Find high-risk deposits
     */
    @Query("SELECT c FROM CheckDeposit c WHERE c.riskScore >= :threshold " +
           "AND c.status IN ('PENDING', 'IMAGE_PROCESSING', 'FRAUD_CHECK') " +
           "ORDER BY c.riskScore DESC")
    List<CheckDeposit> findHighRiskDeposits(@Param("threshold") BigDecimal threshold);
    
    /**
     * Check if user has successful deposits
     */
    @Query("SELECT COUNT(c) > 0 FROM CheckDeposit c WHERE c.userId = :userId " +
           "AND c.status = 'DEPOSITED'")
    boolean hasSuccessfulDeposits(@Param("userId") UUID userId);
    
    /**
     * Get user's deposit history summary
     */
    @Query("SELECT COUNT(c), COALESCE(SUM(c.amount), 0), " +
           "COALESCE(AVG(c.amount), 0), COALESCE(MAX(c.amount), 0) " +
           "FROM CheckDeposit c WHERE c.userId = :userId " +
           "AND c.status = 'DEPOSITED'")
    Object[] getUserDepositSummary(@Param("userId") UUID userId);
}