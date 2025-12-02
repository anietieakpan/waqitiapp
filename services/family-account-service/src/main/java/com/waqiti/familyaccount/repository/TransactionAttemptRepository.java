package com.waqiti.familyaccount.repository;

import com.waqiti.familyaccount.domain.FamilyMember;
import com.waqiti.familyaccount.domain.TransactionAttempt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Transaction Attempt Repository
 *
 * Data access layer for TransactionAttempt entities
 * Records all transaction authorization attempts for audit and analysis
 *
 * @author Waqiti Family Account Team
 * @version 2.0.0
 * @since 2025-10-17
 */
@Repository
public interface TransactionAttemptRepository extends JpaRepository<TransactionAttempt, Long> {

    /**
     * Find transaction attempts by member
     */
    Page<TransactionAttempt> findByFamilyMember(FamilyMember familyMember, Pageable pageable);

    /**
     * Find transaction attempts by member and date range
     */
    List<TransactionAttempt> findByFamilyMemberAndAttemptTimeBetween(
        FamilyMember familyMember,
        LocalDateTime startTime,
        LocalDateTime endTime);

    /**
     * Find declined transaction attempts
     */
    List<TransactionAttempt> findByFamilyMemberAndAuthorizedFalse(FamilyMember familyMember);

    /**
     * Find transaction attempts requiring approval
     */
    @Query("SELECT ta FROM TransactionAttempt ta WHERE ta.familyMember = :member AND " +
           "ta.requiresParentApproval = true AND ta.approvalStatus = 'PENDING'")
    List<TransactionAttempt> findPendingApprovals(@Param("member") FamilyMember member);

    /**
     * Find recent declined attempts (for fraud detection)
     */
    @Query("SELECT ta FROM TransactionAttempt ta WHERE ta.familyMember = :member AND " +
           "ta.authorized = false AND ta.attemptTime > :since")
    List<TransactionAttempt> findRecentDeclinedAttempts(
        @Param("member") FamilyMember member,
        @Param("since") LocalDateTime since);

    /**
     * Count transaction attempts in time period
     */
    long countByFamilyMemberAndAttemptTimeBetween(
        FamilyMember familyMember,
        LocalDateTime startTime,
        LocalDateTime endTime);

    /**
     * Find transaction attempt by idempotency key
     *
     * Critical for preventing duplicate transaction processing.
     * When a client retries a transaction request (due to network issues, timeouts, etc.),
     * the same idempotency key is sent. This method checks if we've already processed
     * this transaction and returns the cached result instead of processing it again.
     *
     * @param idempotencyKey Unique key provided by client (UUID recommended)
     * @return TransactionAttempt if already processed, empty otherwise
     */
    java.util.Optional<TransactionAttempt> findByIdempotencyKey(String idempotencyKey);
}
