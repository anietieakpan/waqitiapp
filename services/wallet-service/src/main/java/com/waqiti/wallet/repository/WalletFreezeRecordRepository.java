package com.waqiti.wallet.repository;

import com.waqiti.wallet.domain.WalletFreezeRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for wallet freeze records with optimized queries for fraud and compliance operations.
 *
 * <p>Supports high-performance lookups for:
 * <ul>
 *   <li>Active freeze checks (sub-10ms response time)</li>
 *   <li>Fraud event correlation</li>
 *   <li>Compliance audit trails</li>
 *   <li>Scheduled review management</li>
 * </ul>
 *
 * @author Waqiti Security Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface WalletFreezeRecordRepository extends MongoRepository<WalletFreezeRecord, UUID> {

    /**
     * Find all active freeze records for a wallet.
     *
     * @param walletId wallet ID
     * @return list of active freezes
     */
    @Query("{ 'walletId': ?0, 'active': true }")
    List<WalletFreezeRecord> findActiveByWalletId(UUID walletId);

    /**
     * Find all active freeze records for a user.
     *
     * @param userId user ID
     * @return list of active freezes
     */
    @Query("{ 'userId': ?0, 'active': true }")
    List<WalletFreezeRecord> findActiveByUserId(UUID userId);

    /**
     * Find freeze record by fraud event ID.
     *
     * @param fraudEventId fraud event ID
     * @return freeze record if exists
     */
    Optional<WalletFreezeRecord> findByFraudEventId(UUID fraudEventId);

    /**
     * Find freeze records by compliance case ID.
     *
     * @param complianceCaseId compliance case ID
     * @return list of related freeze records
     */
    List<WalletFreezeRecord> findByComplianceCaseId(String complianceCaseId);

    /**
     * Find freeze records by legal order ID.
     *
     * @param legalOrderId legal order ID
     * @return list of related freeze records
     */
    List<WalletFreezeRecord> findByLegalOrderId(String legalOrderId);

    /**
     * Find all freeze records requiring review by date.
     *
     * @param reviewDate review date cutoff
     * @return list of freeze records pending review
     */
    @Query("{ 'reviewDate': { $lte: ?0 }, 'active': true }")
    List<WalletFreezeRecord> findPendingReview(LocalDateTime reviewDate);

    /**
     * Find freeze records by severity and active status.
     *
     * @param severity severity level
     * @param active active status
     * @return list of matching freeze records
     */
    List<WalletFreezeRecord> findBySeverityAndActive(String severity, boolean active);

    /**
     * Count active freezes for a user.
     *
     * @param userId user ID
     * @return count of active freezes
     */
    @Query(value = "{ 'userId': ?0, 'active': true }", count = true)
    long countActiveByUserId(UUID userId);

    /**
     * Count active freezes for a wallet.
     *
     * @param walletId wallet ID
     * @return count of active freezes
     */
    @Query(value = "{ 'walletId': ?0, 'active': true }", count = true)
    long countActiveByWalletId(UUID walletId);

    /**
     * Find all freeze records within date range (audit queries).
     *
     * @param startDate start of range
     * @param endDate end of range
     * @return list of freeze records in range
     */
    @Query("{ 'frozenAt': { $gte: ?0, $lte: ?1 } }")
    List<WalletFreezeRecord> findByDateRange(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * Find high-severity active freezes for monitoring dashboard.
     *
     * @return list of critical and high-severity active freezes
     */
    @Query("{ 'severity': { $in: ['CRITICAL', 'HIGH'] }, 'active': true }")
    List<WalletFreezeRecord> findHighSeverityActive();

    /**
     * Check if wallet has any active freezes (optimized for fast check).
     *
     * @param walletId wallet ID
     * @return true if wallet has active freezes
     */
    @Query(value = "{ 'walletId': ?0, 'active': true }", count = true)
    default boolean hasActiveFreezes(UUID walletId) {
        return countActiveByWalletId(walletId) > 0;
    }

    /**
     * Delete all inactive freeze records older than specified date (data retention).
     *
     * @param cutoffDate cutoff date for deletion
     * @return number of records deleted
     */
    @Query(value = "{ 'active': false, 'unfrozenAt': { $lt: ?0 } }", delete = true)
    long deleteInactiveOlderThan(LocalDateTime cutoffDate);
}
