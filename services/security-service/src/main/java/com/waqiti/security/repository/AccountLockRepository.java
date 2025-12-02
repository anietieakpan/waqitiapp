package com.waqiti.security.repository;

import com.waqiti.security.domain.AccountLock;
import com.waqiti.security.domain.LockStatus;
import com.waqiti.security.domain.LockType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Account Lock Repository
 *
 * Provides data access for account lock operations.
 */
@Repository
public interface AccountLockRepository extends JpaRepository<AccountLock, String> {

    /**
     * Find active lock by account ID
     */
    @Query("SELECT al FROM AccountLock al WHERE al.accountId = :accountId " +
           "AND al.status = 'ACTIVE' " +
           "ORDER BY al.lockedAt DESC")
    Optional<AccountLock> findActiveByAccountId(@Param("accountId") String accountId);

    /**
     * Find all locks for an account
     */
    List<AccountLock> findByAccountIdOrderByLockedAtDesc(String accountId);

    /**
     * Find locks by user ID
     */
    List<AccountLock> findByUserIdOrderByLockedAtDesc(String userId);

    /**
     * Check if lock already processed for this event
     */
    boolean existsByAccountIdAndEventId(String accountId, String eventId);

    /**
     * Find locks by status
     */
    List<AccountLock> findByStatus(LockStatus status);

    /**
     * Find temporary locks that should be unlocked
     */
    @Query("SELECT al FROM AccountLock al WHERE al.lockType = :lockType " +
           "AND al.status = 'ACTIVE' " +
           "AND al.scheduledUnlockAt <= :now")
    List<AccountLock> findLocksToUnlock(
        @Param("lockType") LockType lockType,
        @Param("now") LocalDateTime now
    );

    /**
     * Count active locks by account ID
     */
    @Query("SELECT COUNT(al) FROM AccountLock al WHERE al.accountId = :accountId " +
           "AND al.status = 'ACTIVE'")
    long countActiveByAccountId(@Param("accountId") String accountId);

    /**
     * Find locks by correlation ID
     */
    List<AccountLock> findByCorrelationId(String correlationId);

    /**
     * Find high-severity active locks
     */
    @Query("SELECT al FROM AccountLock al WHERE al.status = 'ACTIVE' " +
           "AND al.lockReason IN ('FRAUD_DETECTED', 'CREDENTIAL_COMPROMISE', " +
           "'SANCTIONS_HIT', 'COMPLIANCE_REQUIRED', 'ACCOUNT_TAKEOVER_SUSPECTED')")
    List<AccountLock> findHighSeverityActiveLocks();

    /**
     * Delete old locks
     */
    @Query("DELETE FROM AccountLock al WHERE al.createdAt < :cutoffDate " +
           "AND al.status NOT IN ('ACTIVE', 'PENDING')")
    void deleteOldLocks(@Param("cutoffDate") LocalDateTime cutoffDate);
}
