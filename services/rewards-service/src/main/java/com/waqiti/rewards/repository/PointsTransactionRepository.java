package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.PointsTransaction;
import com.waqiti.rewards.enums.PointsStatus;
import com.waqiti.rewards.enums.PointsTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, UUID> {
    
    Page<PointsTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    Page<PointsTransaction> findByUserIdAndTypeOrderByProcessedAtDesc(
        String userId, 
        PointsTransactionType type, 
        Pageable pageable
    );
    
    List<PointsTransaction> findByUserIdAndStatus(String userId, PointsStatus status);
    
    @Query("SELECT pt FROM PointsTransaction pt WHERE pt.expiresAt <= :now " +
           "AND pt.status IN ('PENDING', 'COMPLETED')")
    List<PointsTransaction> findExpiringPoints(@Param("now") Instant now);
    
    @Query("SELECT SUM(pt.points) FROM PointsTransaction pt WHERE pt.userId = :userId " +
           "AND pt.type = 'EARNED' AND pt.processedAt >= :startDate")
    Long getPointsEarnedInPeriod(@Param("userId") String userId, @Param("startDate") Instant startDate);
    
    @Query("SELECT SUM(pt.points) FROM PointsTransaction pt WHERE pt.userId = :userId " +
           "AND pt.type = 'REDEEMED' AND pt.processedAt >= :startDate")
    Long getPointsRedeemedInPeriod(@Param("userId") String userId, @Param("startDate") Instant startDate);
    
    @Query("SELECT pt FROM PointsTransaction pt WHERE pt.campaignId = :campaignId")
    List<PointsTransaction> findByCampaignId(@Param("campaignId") String campaignId);
    
    @Query("SELECT COUNT(pt) FROM PointsTransaction pt WHERE pt.userId = :userId " +
           "AND pt.type = :type AND pt.processedAt >= :startDate")
    long countTransactionsByType(
        @Param("userId") String userId, 
        @Param("type") PointsTransactionType type,
        @Param("startDate") Instant startDate
    );
    
    @Query("SELECT pt.source, SUM(pt.points) FROM PointsTransaction pt " +
           "WHERE pt.userId = :userId AND pt.type = 'EARNED' " +
           "GROUP BY pt.source ORDER BY SUM(pt.points) DESC")
    List<Object[]> getPointsBySource(@Param("userId") String userId);
    
    @Query("SELECT SUM(pt.points) FROM PointsTransaction pt WHERE pt.userId = :userId " +
           "AND pt.expiresAt > :now AND pt.status = 'COMPLETED'")
    Long getAvailablePoints(@Param("userId") String userId, @Param("now") Instant now);
    
    @Query(value = "SELECT DATE_TRUNC('month', processed_at) as month, " +
           "SUM(CASE WHEN type = 'EARNED' THEN points ELSE 0 END) as earned, " +
           "SUM(CASE WHEN type = 'REDEEMED' THEN points ELSE 0 END) as redeemed " +
           "FROM points_transactions WHERE user_id = :userId " +
           "AND processed_at >= :startDate GROUP BY DATE_TRUNC('month', processed_at) " +
           "ORDER BY month ASC", nativeQuery = true)
    List<Object[]> getMonthlyPointsActivity(
        @Param("userId") String userId, 
        @Param("startDate") Instant startDate
    );
    
    /**
     * Get points earned in period (returns 0 if null)
     */
    @Query("SELECT COALESCE(SUM(pt.points), 0L) FROM PointsTransaction pt WHERE pt.userId = :userId " +
           "AND pt.type = 'EARNED' AND pt.processedAt >= :startDate AND pt.status = 'COMPLETED'")
    Long getPointsEarnedInPeriodSafe(@Param("userId") String userId, @Param("startDate") Instant startDate);
    
    /**
     * Find by user ID and order by processed at desc (for history)
     */
    Page<PointsTransaction> findByUserIdOrderByProcessedAtDesc(String userId, Pageable pageable);
}