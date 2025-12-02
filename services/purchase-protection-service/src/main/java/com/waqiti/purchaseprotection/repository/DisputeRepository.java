package com.waqiti.purchaseprotection.repository;

import com.waqiti.purchaseprotection.domain.Dispute;
import com.waqiti.purchaseprotection.domain.DisputeStatus;
import com.waqiti.purchaseprotection.domain.DisputeDecision;
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
 * Repository for dispute operations.
 *
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Repository
public interface DisputeRepository extends JpaRepository<Dispute, String> {
    
    /**
     * Find disputes by transaction ID.
     *
     * @param transactionId transaction ID
     * @return list of disputes
     */
    List<Dispute> findByTransactionId(String transactionId);
    
    /**
     * Find disputes by buyer or seller ID.
     *
     * @param buyerId buyer ID
     * @param sellerId seller ID
     * @return list of disputes
     */
    List<Dispute> findByBuyerIdOrSellerId(String buyerId, String sellerId);
    
    /**
     * Find disputes by status.
     *
     * @param status dispute status
     * @param pageable pagination
     * @return page of disputes
     */
    Page<Dispute> findByStatus(DisputeStatus status, Pageable pageable);
    
    /**
     * Find open disputes for user.
     *
     * @param userId user ID
     * @return list of open disputes
     */
    @Query("SELECT d FROM Dispute d WHERE (d.buyerId = :userId OR d.sellerId = :userId) " +
           "AND d.status IN ('OPEN', 'SELLER_RESPONDED', 'UNDER_MEDIATION')")
    List<Dispute> findOpenDisputesForUser(@Param("userId") String userId);
    
    /**
     * Find expired disputes.
     *
     * @param currentTime current time
     * @return list of expired disputes
     */
    @Query("SELECT d FROM Dispute d WHERE d.status = 'OPEN' " +
           "AND d.deadlineAt < :currentTime " +
           "AND d.sellerResponse IS NULL")
    List<Dispute> findExpiredDisputes(@Param("currentTime") Instant currentTime);
    
    /**
     * Find disputes awaiting seller response.
     *
     * @param reminderTime time for reminder
     * @return list of disputes
     */
    @Query("SELECT d FROM Dispute d WHERE d.status = 'OPEN' " +
           "AND d.sellerResponse IS NULL " +
           "AND d.deadlineAt > :reminderTime")
    List<Dispute> findDisputesAwaitingResponse(@Param("reminderTime") Instant reminderTime);
    
    /**
     * Find disputes under mediation.
     *
     * @param mediatorId mediator ID
     * @param pageable pagination
     * @return page of disputes
     */
    Page<Dispute> findByMediatorIdAndStatus(String mediatorId, DisputeStatus status, Pageable pageable);
    
    /**
     * Get dispute statistics for user.
     *
     * @param userId user ID
     * @return dispute statistics
     */
    @Query("SELECT NEW com.waqiti.protection.dto.DisputeStatistics(" +
           "COUNT(d), " +
           "SUM(CASE WHEN d.status = 'RESOLVED' AND d.resolution.decision = 'FAVOR_BUYER' AND d.buyerId = :userId THEN 1 " +
           "         WHEN d.status = 'RESOLVED' AND d.resolution.decision = 'FAVOR_SELLER' AND d.sellerId = :userId THEN 1 " +
           "         ELSE 0 END), " +
           "SUM(CASE WHEN d.status IN ('OPEN', 'SELLER_RESPONDED', 'UNDER_MEDIATION') THEN 1 ELSE 0 END), " +
           "AVG(CASE WHEN d.status = 'RESOLVED' THEN " +
           "    TIMESTAMPDIFF(HOUR, d.initiatedAt, d.resolvedAt) ELSE NULL END)) " +
           "FROM Dispute d WHERE d.buyerId = :userId OR d.sellerId = :userId")
    DisputeStatistics getDisputeStatistics(@Param("userId") String userId);
    
    /**
     * Calculate total disputed amount.
     *
     * @param status dispute status
     * @return total amount
     */
    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM Dispute d WHERE d.status = :status")
    BigDecimal calculateTotalDisputedAmount(@Param("status") DisputeStatus status);
    
    /**
     * Find disputes with protection.
     *
     * @param hasProtection protection flag
     * @param pageable pagination
     * @return page of disputes
     */
    Page<Dispute> findByHasProtection(boolean hasProtection, Pageable pageable);
    
    /**
     * Update dispute status.
     *
     * @param disputeId dispute ID
     * @param status new status
     */
    @Modifying
    @Query("UPDATE Dispute d SET d.status = :status WHERE d.id = :disputeId")
    void updateDisputeStatus(@Param("disputeId") String disputeId,
                           @Param("status") DisputeStatus status);
    
    /**
     * Find disputes resolved in date range.
     *
     * @param startDate start date
     * @param endDate end date
     * @return list of disputes
     */
    @Query("SELECT d FROM Dispute d WHERE d.status = 'RESOLVED' " +
           "AND d.resolvedAt BETWEEN :startDate AND :endDate")
    List<Dispute> findResolvedDisputesInDateRange(@Param("startDate") Instant startDate,
                                                 @Param("endDate") Instant endDate);
    
    /**
     * Get resolution statistics.
     *
     * @param startDate start date
     * @param endDate end date
     * @return resolution statistics
     */
    @Query("SELECT NEW com.waqiti.protection.dto.ResolutionStatistics(" +
           "COUNT(d), " +
           "SUM(CASE WHEN d.resolution.decision = 'FAVOR_BUYER' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN d.resolution.decision = 'FAVOR_SELLER' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN d.resolution.decision = 'PARTIAL_REFUND' THEN 1 ELSE 0 END), " +
           "SUM(CASE WHEN d.resolution.autoResolved = true THEN 1 ELSE 0 END)) " +
           "FROM Dispute d WHERE d.status = 'RESOLVED' " +
           "AND d.resolvedAt BETWEEN :startDate AND :endDate")
    ResolutionStatistics getResolutionStatistics(@Param("startDate") Instant startDate,
                                                @Param("endDate") Instant endDate);
    
    /**
     * Find disputes requiring mediator.
     *
     * @return list of disputes
     */
    @Query("SELECT d FROM Dispute d WHERE d.status = 'UNDER_MEDIATION' " +
           "AND d.mediatorId IS NULL")
    List<Dispute> findDisputesRequiringMediator();
    
    /**
     * Count disputes by decision.
     *
     * @param decision dispute decision
     * @return count of disputes
     */
    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.resolution.decision = :decision")
    long countByDecision(@Param("decision") DisputeDecision decision);
    
    /**
     * Find disputes with held funds.
     *
     * @return list of disputes
     */
    @Query("SELECT d FROM Dispute d WHERE d.fundsHeld = true " +
           "AND d.status NOT IN ('RESOLVED', 'CANCELLED')")
    List<Dispute> findDisputesWithHeldFunds();
    
    /**
     * Get mediator workload.
     *
     * @param mediatorId mediator ID
     * @return count of active disputes
     */
    @Query("SELECT COUNT(d) FROM Dispute d WHERE d.mediatorId = :mediatorId " +
           "AND d.status = 'UNDER_MEDIATION'")
    long getMediatorWorkload(@Param("mediatorId") String mediatorId);
}