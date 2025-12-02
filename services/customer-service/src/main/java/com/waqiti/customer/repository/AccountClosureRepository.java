package com.waqiti.customer.repository;

import com.waqiti.customer.entity.AccountClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Account Closure Repository
 *
 * Manages account closure records with 7-year retention for regulatory compliance
 */
@Repository
public interface AccountClosureRepository extends JpaRepository<AccountClosure, String> {

    /**
     * Find closure record by account ID
     */
    Optional<AccountClosure> findByAccountId(String accountId);

    /**
     * Find closure record by customer ID
     */
    List<AccountClosure> findByCustomerId(String customerId);

    /**
     * Find closures by status
     */
    List<AccountClosure> findByStatus(String status);

    /**
     * Find closures by type and date range
     */
    @Query("SELECT ac FROM AccountClosure ac WHERE ac.closureType = :closureType " +
           "AND ac.closureDate BETWEEN :startDate AND :endDate")
    List<AccountClosure> findByTypeAndDateRange(
            @Param("closureType") String closureType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    /**
     * Check if account has existing closure record
     */
    boolean existsByAccountId(String accountId);

    /**
     * Find pending closures older than specified date
     */
    @Query("SELECT ac FROM AccountClosure ac WHERE ac.status = 'PENDING' " +
           "AND ac.createdAt < :cutoffDate")
    List<AccountClosure> findStalePendingClosures(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Count closures by type
     */
    @Query("SELECT COUNT(ac) FROM AccountClosure ac WHERE ac.closureType = :closureType " +
           "AND ac.closureDate >= :startDate")
    long countByTypeAfterDate(
            @Param("closureType") String closureType,
            @Param("startDate") LocalDateTime startDate);
}
