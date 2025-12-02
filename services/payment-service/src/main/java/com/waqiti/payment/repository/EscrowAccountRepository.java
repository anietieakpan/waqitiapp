package com.waqiti.payment.repository;

import com.waqiti.payment.entity.EscrowAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Escrow Account management
 * 
 * CRITICAL: Uses pessimistic locking for balance updates to prevent race conditions
 * COMPLIANCE: All queries include audit trail tracking
 */
@Repository
public interface EscrowAccountRepository extends JpaRepository<EscrowAccount, UUID> {
    
    /**
     * Find escrow account by account ID with pessimistic write lock
     * CRITICAL: Prevents concurrent modifications
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EscrowAccount e WHERE e.accountId = :accountId")
    Optional<EscrowAccount> findByAccountIdForUpdate(@Param("accountId") String accountId);
    
    /**
     * Find escrow account by account ID
     */
    Optional<EscrowAccount> findByAccountId(String accountId);
    
    /**
     * Find escrow accounts by buyer ID
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.buyerId = :buyerId ORDER BY e.createdAt DESC")
    List<EscrowAccount> findByBuyerId(@Param("buyerId") String buyerId);
    
    /**
     * Find escrow accounts by seller ID
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.sellerId = :sellerId ORDER BY e.createdAt DESC")
    List<EscrowAccount> findBySellerId(@Param("sellerId") String sellerId);
    
    /**
     * Find escrow accounts by escrow agent ID
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.escrowAgentId = :agentId ORDER BY e.createdAt DESC")
    List<EscrowAccount> findByEscrowAgentId(@Param("agentId") String agentId);
    
    /**
     * Find escrow account by contract ID
     */
    Optional<EscrowAccount> findByContractId(String contractId);
    
    /**
     * Find escrow accounts by status
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.status = :status ORDER BY e.createdAt DESC")
    List<EscrowAccount> findByStatus(@Param("status") EscrowAccount.EscrowStatus status);
    
    /**
     * Find active (funded) escrow accounts
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.status = 'FUNDED' AND e.frozen = false AND e.isDisputed = false")
    List<EscrowAccount> findActiveAccounts();
    
    /**
     * Find disputed escrow accounts
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.isDisputed = true ORDER BY e.disputeOpenedAt DESC")
    List<EscrowAccount> findDisputedAccounts();
    
    /**
     * Find frozen escrow accounts
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.frozen = true ORDER BY e.frozenAt DESC")
    List<EscrowAccount> findFrozenAccounts();
    
    /**
     * Find escrow accounts expiring soon
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.expiryDate BETWEEN :now AND :expiryThreshold " +
           "AND e.status IN ('FUNDED', 'HELD') ORDER BY e.expiryDate ASC")
    List<EscrowAccount> findAccountsExpiringSoon(@Param("now") LocalDateTime now,
                                                  @Param("expiryThreshold") LocalDateTime expiryThreshold);
    
    /**
     * Find expired escrow accounts
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.expiryDate < :now " +
           "AND e.status IN ('FUNDED', 'HELD', 'PENDING')")
    List<EscrowAccount> findExpiredAccounts(@Param("now") LocalDateTime now);
    
    /**
     * Find escrow accounts requiring release
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.releaseDate <= :now " +
           "AND e.status = 'APPROVED_FOR_RELEASE' AND e.frozen = false")
    List<EscrowAccount> findAccountsRequiringRelease(@Param("now") LocalDateTime now);
    
    /**
     * Find escrow accounts by buyer and status
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.buyerId = :buyerId AND e.status = :status")
    List<EscrowAccount> findByBuyerIdAndStatus(@Param("buyerId") String buyerId,
                                               @Param("status") EscrowAccount.EscrowStatus status);
    
    /**
     * Find escrow accounts by seller and status
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.sellerId = :sellerId AND e.status = :status")
    List<EscrowAccount> findBySellerIdAndStatus(@Param("sellerId") String sellerId,
                                                @Param("status") EscrowAccount.EscrowStatus status);
    
    /**
     * Find escrow accounts created within date range
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY e.createdAt DESC")
    List<EscrowAccount> findByCreatedAtBetween(@Param("startDate") LocalDateTime startDate,
                                               @Param("endDate") LocalDateTime endDate);
    
    /**
     * Calculate total balance held in escrow by currency
     */
    @Query("SELECT e.currency, SUM(e.balance) FROM EscrowAccount e " +
           "WHERE e.status IN ('FUNDED', 'HELD', 'APPROVED_FOR_RELEASE') " +
           "GROUP BY e.currency")
    List<Object[]> calculateTotalBalancesByCurrency();
    
    /**
     * Count escrow accounts by status
     */
    @Query("SELECT e.status, COUNT(e) FROM EscrowAccount e GROUP BY e.status")
    List<Object[]> countByStatus();
    
    /**
     * Find high-value escrow accounts
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.balance >= :threshold " +
           "AND e.status IN ('FUNDED', 'HELD') ORDER BY e.balance DESC")
    List<EscrowAccount> findHighValueAccounts(@Param("threshold") BigDecimal threshold);
    
    /**
     * Find accounts with pending approvals
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.receivedApprovals < e.requiredApprovals " +
           "AND e.status IN ('HELD', 'FUNDED')")
    List<EscrowAccount> findAccountsWithPendingApprovals();
    
    /**
     * Check if escrow account exists for contract
     */
    boolean existsByContractId(String contractId);
    
    /**
     * Check if account ID already exists
     */
    boolean existsByAccountId(String accountId);
    
    /**
     * Find accounts involving a specific party (buyer, seller, or agent)
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.buyerId = :partyId OR e.sellerId = :partyId " +
           "OR e.escrowAgentId = :partyId ORDER BY e.createdAt DESC")
    List<EscrowAccount> findAccountsByPartyId(@Param("partyId") String partyId);
    
    /**
     * Find stale accounts (funded but inactive for a period)
     */
    @Query("SELECT e FROM EscrowAccount e WHERE e.status = 'FUNDED' " +
           "AND e.updatedAt < :staleThreshold ORDER BY e.updatedAt ASC")
    List<EscrowAccount> findStaleAccounts(@Param("staleThreshold") LocalDateTime staleThreshold);
}