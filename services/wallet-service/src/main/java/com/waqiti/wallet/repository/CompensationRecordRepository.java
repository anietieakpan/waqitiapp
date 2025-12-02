package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.CompensationRecord;
import com.waqiti.wallet.domain.CompensationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Compensation Record Repository
 * 
 * MongoDB repository for managing compensation records
 */
@Repository
public interface CompensationRecordRepository extends MongoRepository<CompensationRecord, UUID> {
    
    /**
     * Find compensation records by payment ID
     */
    Optional<CompensationRecord> findByPaymentId(String paymentId);
    
    /**
     * Find compensation records by user ID
     */
    List<CompensationRecord> findByUserIdOrderByCreatedAtDesc(String userId);
    
    /**
     * Find compensation records by wallet ID
     */
    List<CompensationRecord> findByWalletIdOrderByCreatedAtDesc(String walletId);
    
    /**
     * Find compensation records by status
     */
    Page<CompensationRecord> findByStatus(CompensationStatus status, Pageable pageable);
    
    /**
     * Find compensation records by multiple statuses
     */
    @Query("{ 'status': { $in: ?0 } }")
    Page<CompensationRecord> findByStatusIn(List<CompensationStatus> statuses, Pageable pageable);
    
    /**
     * Find compensation records pending retry
     */
    @Query("{ 'status': 'RETRY', 'nextRetryAt': { $lte: ?0 } }")
    List<CompensationRecord> findRecordsForRetry(LocalDateTime cutoff);
    
    /**
     * Find compensation records requiring manual review
     */
    Page<CompensationRecord> findByStatusAndReviewedByIsNull(
            CompensationStatus status, Pageable pageable);
    
    /**
     * Find compensation records by date range
     */
    @Query("{ 'createdAt': { $gte: ?0, $lte: ?1 } }")
    Page<CompensationRecord> findByDateRange(
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find failed compensation records
     */
    @Query("{ 'status': { $in: ['FAILED', 'FAILED_PERMANENTLY'] }, 'attemptCount': { $gte: ?0 } }")
    List<CompensationRecord> findFailedRecordsWithMinAttempts(Integer minAttempts);
    
    /**
     * Count compensation records by status
     */
    long countByStatus(CompensationStatus status);
    
    /**
     * Count compensation records by status and date range
     */
    @Query(value = "{ 'status': ?0, 'createdAt': { $gte: ?1, $lte: ?2 } }", count = true)
    long countByStatusAndDateRange(
            CompensationStatus status, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find compensation records for dashboard
     */
    @Query("{ 'status': { $in: ['PENDING', 'IN_PROGRESS', 'RETRY', 'MANUAL_REVIEW'] } }")
    Page<CompensationRecord> findActiveCompensations(Pageable pageable);
    
    /**
     * Find compensation records by compensation type
     */
    Page<CompensationRecord> findByCompensationType(String compensationType, Pageable pageable);
    
    /**
     * Find high-value compensation records
     */
    @Query("{ 'amount': { $gte: ?0 }, 'currency': ?1 }")
    List<CompensationRecord> findHighValueCompensations(
            java.math.BigDecimal minAmount, String currency);
    
    /**
     * Find stale compensation records
     */
    @Query("{ 'status': 'IN_PROGRESS', 'lastAttemptAt': { $lte: ?0 } }")
    List<CompensationRecord> findStaleCompensations(LocalDateTime cutoff);
}