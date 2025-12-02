package com.waqiti.rewards.repository;

import com.waqiti.rewards.domain.RedemptionTransaction;
import com.waqiti.rewards.enums.RedemptionStatus;
import com.waqiti.rewards.enums.RedemptionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RedemptionTransactionRepository extends JpaRepository<RedemptionTransaction, UUID> {
    
    Optional<RedemptionTransaction> findByReferenceNumber(String referenceNumber);
    
    Page<RedemptionTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    List<RedemptionTransaction> findByUserIdAndStatus(String userId, RedemptionStatus status);
    
    @Query("SELECT rt FROM RedemptionTransaction rt WHERE rt.userId = :userId " +
           "AND rt.type = :type ORDER BY rt.createdAt DESC")
    Page<RedemptionTransaction> findByUserIdAndType(
        @Param("userId") String userId, 
        @Param("type") RedemptionType type, 
        Pageable pageable
    );
    
    @Query("SELECT rt FROM RedemptionTransaction rt WHERE rt.status = 'PENDING' " +
           "AND rt.createdAt < :timeout")
    List<RedemptionTransaction> findTimedOutRedemptions(@Param("timeout") Instant timeout);
    
    @Query("SELECT SUM(rt.amount) FROM RedemptionTransaction rt WHERE rt.userId = :userId " +
           "AND rt.type = 'CASHBACK' AND rt.status = 'COMPLETED' " +
           "AND rt.completedAt >= :startDate")
    BigDecimal getTotalCashbackRedeemed(
        @Param("userId") String userId, 
        @Param("startDate") Instant startDate
    );
    
    @Query("SELECT SUM(rt.points) FROM RedemptionTransaction rt WHERE rt.userId = :userId " +
           "AND rt.type = 'POINTS' AND rt.status = 'COMPLETED' " +
           "AND rt.completedAt >= :startDate")
    Long getTotalPointsRedeemed(
        @Param("userId") String userId, 
        @Param("startDate") Instant startDate
    );
    
    @Query("SELECT COUNT(rt) FROM RedemptionTransaction rt WHERE rt.userId = :userId " +
           "AND rt.status = :status AND rt.createdAt >= :startDate")
    long countByUserAndStatus(
        @Param("userId") String userId, 
        @Param("status") RedemptionStatus status,
        @Param("startDate") Instant startDate
    );
    
    @Query("SELECT rt.method, COUNT(rt) FROM RedemptionTransaction rt " +
           "WHERE rt.status = 'COMPLETED' GROUP BY rt.method")
    List<Object[]> getRedemptionsByMethod();
    
    /**
     * Find by user ID and order by processed at desc (for history)
     */
    Page<RedemptionTransaction> findByUserIdOrderByProcessedAtDesc(String userId, Pageable pageable);
    
    /**
     * Get total cashback redeemed system-wide
     */
    @Query("SELECT COALESCE(SUM(rt.amount), 0) FROM RedemptionTransaction rt " +
           "WHERE rt.type = 'CASHBACK' AND rt.status = 'COMPLETED'")
    BigDecimal getTotalCashbackRedeemed();
    
    /**
     * Get total points redeemed system-wide
     */
    @Query("SELECT COALESCE(SUM(rt.points), 0L) FROM RedemptionTransaction rt " +
           "WHERE rt.points IS NOT NULL AND rt.status = 'COMPLETED'")
    Long getTotalPointsRedeemed();
}