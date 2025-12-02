package com.waqiti.account.repository;

import com.waqiti.account.model.AccountClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Account Closure Repository - Production Implementation
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Repository
public interface AccountClosureRepository extends JpaRepository<AccountClosure, UUID> {

    /**
     * Find closure by account ID
     */
    Optional<AccountClosure> findByAccountId(String accountId);

    /**
     * Check if account has closure record
     */
    boolean existsByAccountId(String accountId);

    /**
     * Find closures by status
     */
    List<AccountClosure> findByStatus(String status);

    /**
     * Find closures by type
     */
    List<AccountClosure> findByClosureType(String closureType);

    /**
     * Find closures by customer ID
     */
    List<AccountClosure> findByCustomerId(String customerId);

    /**
     * Find closures in date range
     */
    @Query("SELECT ac FROM AccountClosure ac WHERE ac.closureDate BETWEEN :startDate AND :endDate")
    List<AccountClosure> findByClosureDateBetween(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Find pending closures older than date
     */
    @Query("SELECT ac FROM AccountClosure ac WHERE ac.status = 'PENDING' AND ac.createdAt < :cutoffDate")
    List<AccountClosure> findStalePendingClosures(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count closures by type
     */
    long countByClosureType(String closureType);

    /**
     * Count closures by status
     */
    long countByStatus(String status);
}
