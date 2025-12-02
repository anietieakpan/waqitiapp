package com.waqiti.compliance.repository;

import com.waqiti.compliance.domain.LegalOrder;
import com.waqiti.compliance.domain.LegalOrder.OrderStatus;
import com.waqiti.compliance.domain.LegalOrder.OrderType;
import com.waqiti.compliance.domain.LegalOrder.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * CRITICAL P0 FIX: Legal Order Repository
 *
 * Provides database access for legal orders, court orders, garnishments,
 * and other legal compliance requirements.
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-05
 */
@Repository
public interface LegalOrderRepository extends JpaRepository<LegalOrder, UUID> {

    /**
     * Find legal order by order number (court case number)
     */
    Optional<LegalOrder> findByOrderNumber(String orderNumber);

    /**
     * Find all legal orders for a user
     */
    List<LegalOrder> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * Find active legal orders for a user
     */
    @Query("SELECT lo FROM LegalOrder lo WHERE lo.userId = :userId AND lo.orderStatus IN ('VERIFIED', 'EXECUTED', 'PARTIALLY_EXECUTED')")
    List<LegalOrder> findActiveLegalOrdersByUserId(@Param("userId") UUID userId);

    /**
     * Find legal orders by status
     */
    List<LegalOrder> findByOrderStatusOrderByCreatedAtDesc(OrderStatus orderStatus);

    /**
     * Find legal orders by type
     */
    List<LegalOrder> findByOrderTypeOrderByCreatedAtDesc(OrderType orderType);

    /**
     * Find legal orders by verification status
     */
    List<LegalOrder> findByVerificationStatusOrderByCreatedAtDesc(VerificationStatus verificationStatus);

    /**
     * Find legal orders pending review
     */
    @Query("SELECT lo FROM LegalOrder lo WHERE lo.orderStatus = 'PENDING_REVIEW' ORDER BY lo.priority DESC, lo.receivedDate ASC")
    List<LegalOrder> findPendingReviewOrders();

    /**
     * Find legal orders requiring urgent attention
     */
    @Query("SELECT lo FROM LegalOrder lo WHERE lo.priority IN ('URGENT', 'CRITICAL') AND lo.orderStatus NOT IN ('EXECUTED', 'RELEASED', 'REJECTED', 'EXPIRED') ORDER BY lo.priority DESC, lo.receivedDate ASC")
    List<LegalOrder> findUrgentOrders();

    /**
     * Find legal orders assigned to legal counsel
     */
    List<LegalOrder> findByAssignedCounselOrderByCreatedAtDesc(String assignedCounsel);

    /**
     * Find legal orders by wallet ID
     */
    List<LegalOrder> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    /**
     * Find legal orders by freeze ID
     */
    Optional<LegalOrder> findByFreezeId(UUID freezeId);

    /**
     * Find expiring legal orders (within next N days)
     */
    @Query("SELECT lo FROM LegalOrder lo WHERE lo.expirationDate IS NOT NULL AND lo.expirationDate BETWEEN :startDate AND :endDate AND lo.orderStatus IN ('VERIFIED', 'EXECUTED', 'PARTIALLY_EXECUTED') ORDER BY lo.expirationDate ASC")
    List<LegalOrder> findExpiringOrders(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    /**
     * Find expired legal orders that haven't been processed
     */
    @Query("SELECT lo FROM LegalOrder lo WHERE lo.expirationDate IS NOT NULL AND lo.expirationDate < :currentDate AND lo.orderStatus NOT IN ('EXPIRED', 'RELEASED', 'REJECTED') ORDER BY lo.expirationDate ASC")
    List<LegalOrder> findExpiredUnprocessedOrders(@Param("currentDate") LocalDate currentDate);

    /**
     * Find legal orders by issuing authority
     */
    List<LegalOrder> findByIssuingAuthorityOrderByCreatedAtDesc(String issuingAuthority);

    /**
     * Find legal orders by jurisdiction
     */
    List<LegalOrder> findByJurisdictionOrderByCreatedAtDesc(String jurisdiction);

    /**
     * Count active legal orders for a user
     */
    @Query("SELECT COUNT(lo) FROM LegalOrder lo WHERE lo.userId = :userId AND lo.orderStatus IN ('VERIFIED', 'EXECUTED', 'PARTIALLY_EXECUTED')")
    long countActiveLegalOrdersByUserId(@Param("userId") UUID userId);

    /**
     * Count pending review orders
     */
    @Query("SELECT COUNT(lo) FROM LegalOrder lo WHERE lo.orderStatus = 'PENDING_REVIEW'")
    long countPendingReviewOrders();

    /**
     * Find legal orders created within date range
     */
    @Query("SELECT lo FROM LegalOrder lo WHERE lo.createdAt BETWEEN :startDate AND :endDate ORDER BY lo.createdAt DESC")
    List<LegalOrder> findOrdersCreatedBetween(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    /**
     * Check if order number already exists
     */
    boolean existsByOrderNumber(String orderNumber);

    /**
     * Find legal orders by case number
     */
    List<LegalOrder> findByCaseNumberOrderByCreatedAtDesc(String caseNumber);

    /**
     * Find legal orders requiring verification
     */
    @Query("SELECT lo FROM LegalOrder lo WHERE lo.verificationStatus IN ('UNVERIFIED', 'PENDING_VERIFICATION') ORDER BY lo.priority DESC, lo.receivedDate ASC")
    List<LegalOrder> findOrdersRequiringVerification();

    /**
     * Find legal orders with recent updates (last N hours)
     */
    @Query("SELECT lo FROM LegalOrder lo WHERE lo.lastUpdated >= :sinceDate ORDER BY lo.lastUpdated DESC")
    List<LegalOrder> findRecentlyUpdatedOrders(@Param("sinceDate") LocalDateTime sinceDate);
}
