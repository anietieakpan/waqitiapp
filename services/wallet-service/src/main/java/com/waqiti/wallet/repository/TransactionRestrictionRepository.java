package com.waqiti.wallet.repository;

import com.waqiti.wallet.entity.TransactionRestriction;
import com.waqiti.wallet.entity.TransactionRestriction.RestrictionType;
import com.waqiti.wallet.entity.TransactionRestriction.RestrictionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Transaction Restriction management
 * 
 * PERFORMANCE: Optimized queries with proper indexing
 * COMPLIANCE: All queries support audit trail tracking
 */
@Repository
public interface TransactionRestrictionRepository extends JpaRepository<TransactionRestriction, UUID> {
    
    @Query("SELECT tr FROM TransactionRestriction tr WHERE tr.walletId = :walletId " +
           "AND tr.status = 'ACTIVE' ORDER BY tr.appliedAt DESC")
    List<TransactionRestriction> findActiveByWalletId(@Param("walletId") String walletId);
    
    @Query("SELECT tr FROM TransactionRestriction tr WHERE tr.walletId = :walletId " +
           "AND tr.restrictionType = :type AND tr.status = 'ACTIVE'")
    Optional<TransactionRestriction> findActiveByWalletIdAndType(
        @Param("walletId") String walletId,
        @Param("type") RestrictionType type
    );
    
    @Query("SELECT tr FROM TransactionRestriction tr WHERE tr.walletId = :walletId " +
           "ORDER BY tr.appliedAt DESC")
    List<TransactionRestriction> findByWalletId(@Param("walletId") String walletId);
    
    @Query("SELECT tr FROM TransactionRestriction tr WHERE tr.restrictionType = :type " +
           "AND tr.status = 'ACTIVE' ORDER BY tr.appliedAt DESC")
    List<TransactionRestriction> findActiveByType(@Param("type") RestrictionType type);
    
    @Query("SELECT tr FROM TransactionRestriction tr WHERE tr.status = :status " +
           "ORDER BY tr.appliedAt DESC")
    List<TransactionRestriction> findByStatus(@Param("status") RestrictionStatus status);
    
    @Query("SELECT tr FROM TransactionRestriction tr WHERE tr.expiresAt < :now " +
           "AND tr.status = 'ACTIVE'")
    List<TransactionRestriction> findExpiredRestrictions(@Param("now") LocalDateTime now);
    
    @Query("SELECT tr FROM TransactionRestriction tr WHERE tr.expiresAt BETWEEN :now AND :threshold " +
           "AND tr.status = 'ACTIVE' ORDER BY tr.expiresAt ASC")
    List<TransactionRestriction> findExpiringSoon(
        @Param("now") LocalDateTime now,
        @Param("threshold") LocalDateTime threshold
    );
    
    @Query("SELECT tr FROM TransactionRestriction tr WHERE tr.appliedBy = :appliedBy " +
           "ORDER BY tr.appliedAt DESC")
    List<TransactionRestriction> findByAppliedBy(@Param("appliedBy") String appliedBy);
    
    @Query("SELECT tr FROM TransactionRestriction tr WHERE tr.removedBy = :removedBy " +
           "ORDER BY tr.removedAt DESC")
    List<TransactionRestriction> findByRemovedBy(@Param("removedBy") String removedBy);
    
    @Query("SELECT tr FROM TransactionRestriction tr WHERE tr.appliedAt BETWEEN :startDate AND :endDate " +
           "ORDER BY tr.appliedAt DESC")
    List<TransactionRestriction> findByAppliedAtBetween(
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Query("SELECT COUNT(tr) FROM TransactionRestriction tr WHERE tr.walletId = :walletId " +
           "AND tr.status = 'ACTIVE'")
    long countActiveByWalletId(@Param("walletId") String walletId);
    
    @Query("SELECT tr.restrictionType, COUNT(tr) FROM TransactionRestriction tr " +
           "WHERE tr.status = 'ACTIVE' GROUP BY tr.restrictionType")
    List<Object[]> countActiveByType();
    
    @Query("SELECT tr.walletId, COUNT(tr) FROM TransactionRestriction tr " +
           "WHERE tr.status = 'ACTIVE' GROUP BY tr.walletId HAVING COUNT(tr) > :threshold")
    List<Object[]> findWalletsWithMultipleRestrictions(@Param("threshold") long threshold);
    
    boolean existsByWalletIdAndRestrictionType(String walletId, RestrictionType restrictionType);
    
    @Query("SELECT tr FROM TransactionRestriction tr WHERE tr.walletId IN :walletIds " +
           "AND tr.status = 'ACTIVE'")
    List<TransactionRestriction> findActiveByWalletIds(@Param("walletIds") List<String> walletIds);
}