package com.waqiti.gamification.repository;

import com.waqiti.gamification.domain.PointTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, Long> {
    
    List<PointTransaction> findByUserId(String userId);
    
    Page<PointTransaction> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    
    List<PointTransaction> findByUserIdAndTransactionType(String userId, PointTransaction.TransactionType transactionType);
    
    @Query("SELECT pt FROM PointTransaction pt WHERE pt.userId = :userId AND pt.createdAt BETWEEN :startDate AND :endDate ORDER BY pt.createdAt DESC")
    List<PointTransaction> findByUserIdAndDateRange(@Param("userId") String userId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COALESCE(SUM(pt.pointsAmount), 0) FROM PointTransaction pt WHERE pt.userId = :userId AND pt.transactionType = :transactionType")
    Long sumPointsByUserIdAndType(@Param("userId") String userId, @Param("transactionType") PointTransaction.TransactionType transactionType);
    
    @Query("SELECT COALESCE(SUM(pt.pointsAmount), 0) FROM PointTransaction pt WHERE pt.userId = :userId AND pt.createdAt BETWEEN :startDate AND :endDate")
    Long sumPointsByUserIdAndDateRange(@Param("userId") String userId, @Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT pt FROM PointTransaction pt WHERE pt.expiresAt IS NOT NULL AND pt.expiresAt <= :now AND pt.isExpired = false")
    List<PointTransaction> findExpiredTransactions(@Param("now") LocalDateTime now);
    
    @Query("SELECT pt FROM PointTransaction pt WHERE pt.isReversed = false AND pt.referenceId = :referenceId")
    List<PointTransaction> findActiveTransactionsByReferenceId(@Param("referenceId") String referenceId);
    
    @Query("SELECT pt.eventType, COUNT(pt), SUM(pt.pointsAmount) FROM PointTransaction pt WHERE pt.transactionType = 'EARNED' GROUP BY pt.eventType ORDER BY SUM(pt.pointsAmount) DESC")
    List<Object[]> findPointsStatsByEventType();
    
    @Query("SELECT pt.eventType, COUNT(pt), SUM(pt.pointsAmount) FROM PointTransaction pt WHERE pt.userId = :userId AND pt.transactionType = 'EARNED' GROUP BY pt.eventType ORDER BY SUM(pt.pointsAmount) DESC")
    List<Object[]> findPointsStatsByEventTypeForUser(@Param("userId") String userId);
    
    @Query("SELECT COUNT(pt) FROM PointTransaction pt WHERE pt.createdAt BETWEEN :startDate AND :endDate")
    Long countTransactionsBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COALESCE(SUM(pt.pointsAmount), 0) FROM PointTransaction pt WHERE pt.createdAt BETWEEN :startDate AND :endDate AND pt.transactionType = 'EARNED'")
    Long sumPointsEarnedBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT COALESCE(SUM(pt.pointsAmount), 0) FROM PointTransaction pt WHERE pt.createdAt BETWEEN :startDate AND :endDate AND pt.transactionType = 'REDEEMED'")
    Long sumPointsRedeemedBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);
}