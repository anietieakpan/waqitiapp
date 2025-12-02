package com.waqiti.transaction.repository;

import com.waqiti.transaction.domain.TransactionBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionBlockRepository extends JpaRepository<TransactionBlock, String> {
    
    boolean existsByTransactionIdAndEventId(String transactionId, String eventId);
    
    List<TransactionBlock> findByTransactionId(String transactionId);
    
    Optional<TransactionBlock> findByTransactionIdAndEventId(String transactionId, String eventId);
    
    @Query("SELECT tb FROM TransactionBlock tb WHERE tb.status = 'ACTIVE' AND tb.expirationTime < :now")
    List<TransactionBlock> findExpiredActiveBlocks(@Param("now") LocalDateTime now);
    
    @Query("SELECT tb FROM TransactionBlock tb WHERE tb.requiresManualReview = true AND tb.reviewedAt IS NULL")
    List<TransactionBlock> findBlocksRequiringManualReview();
    
    @Query("SELECT tb FROM TransactionBlock tb WHERE tb.blockReason = :reason AND tb.blockTimestamp BETWEEN :startTime AND :endTime")
    List<TransactionBlock> findByReasonAndTimePeriod(
        @Param("reason") String reason, 
        @Param("startTime") LocalDateTime startTime, 
        @Param("endTime") LocalDateTime endTime
    );
}