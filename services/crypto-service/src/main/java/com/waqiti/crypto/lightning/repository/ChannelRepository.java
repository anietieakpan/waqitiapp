package com.waqiti.crypto.lightning.repository;

import com.waqiti.crypto.lightning.entity.ChannelEntity;
import com.waqiti.crypto.lightning.entity.ChannelStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Lightning channel entities
 */
@Repository
public interface ChannelRepository extends JpaRepository<ChannelEntity, String>, JpaSpecificationExecutor<ChannelEntity> {

    /**
     * Find channels by user ID
     */
    List<ChannelEntity> findByUserId(String userId);

    /**
     * Find channels by user ID with pagination
     */
    Page<ChannelEntity> findByUserId(String userId, Pageable pageable);

    /**
     * Find channels by user ID and status
     */
    Page<ChannelEntity> findByUserIdAndStatus(String userId, ChannelStatus status, Pageable pageable);

    /**
     * Find channels by remote public key
     */
    List<ChannelEntity> findByRemotePubkey(String remotePubkey);

    /**
     * Find active channels
     */
    List<ChannelEntity> findByStatus(ChannelStatus status);

    /**
     * Count channels by status
     */
    long countByStatus(ChannelStatus status);

    /**
     * Count user's channels by status
     */
    long countByUserIdAndStatus(String userId, ChannelStatus status);

    /**
     * Get total capacity for user's channels
     */
    @Query("SELECT SUM(c.capacity) FROM ChannelEntity c WHERE c.userId = :userId AND c.status = 'ACTIVE'")
    Long getTotalCapacity(@Param("userId") String userId);

    /**
     * Get total local balance for user
     */
    @Query("SELECT SUM(c.localBalance) FROM ChannelEntity c WHERE c.userId = :userId AND c.status = 'ACTIVE'")
    Long getTotalLocalBalance(@Param("userId") String userId);

    /**
     * Get total remote balance for user
     */
    @Query("SELECT SUM(c.remoteBalance) FROM ChannelEntity c WHERE c.userId = :userId AND c.status = 'ACTIVE'")
    Long getTotalRemoteBalance(@Param("userId") String userId);

    /**
     * Find channels needing rebalancing
     */
    @Query("SELECT c FROM ChannelEntity c WHERE c.status = 'ACTIVE' " +
           "AND (c.localBalance < c.capacity * :minRatio OR c.localBalance > c.capacity * :maxRatio)")
    List<ChannelEntity> findChannelsNeedingRebalance(
        @Param("minRatio") double minRatio,
        @Param("maxRatio") double maxRatio
    );

    /**
     * Find inactive channels
     */
    @Query("SELECT c FROM ChannelEntity c WHERE c.status = 'ACTIVE' " +
           "AND (c.lastActivityAt IS NULL OR c.lastActivityAt < :threshold)")
    List<ChannelEntity> findInactiveChannels(@Param("threshold") Instant threshold);

    /**
     * Get channel statistics for user
     */
    @Query("SELECT c.status, COUNT(c), SUM(c.capacity), SUM(c.localBalance), SUM(c.remoteBalance) " +
           "FROM ChannelEntity c WHERE c.userId = :userId GROUP BY c.status")
    List<Object[]> getUserChannelStatistics(@Param("userId") String userId);

    /**
     * Find channels by funding transaction
     */
    Optional<ChannelEntity> findByFundingTxId(String fundingTxId);

    /**
     * Find channels pending open
     */
    @Query("SELECT c FROM ChannelEntity c WHERE c.status = 'PENDING_OPEN' " +
           "AND c.openedAt < :timeout")
    List<ChannelEntity> findStalePendingChannels(@Param("timeout") Instant timeout);

    /**
     * Update channel balances
     */
    @Modifying
    @Query("UPDATE ChannelEntity c SET c.localBalance = :localBalance, " +
           "c.remoteBalance = :remoteBalance, c.updatedAt = :now " +
           "WHERE c.id = :channelId")
    int updateChannelBalances(
        @Param("channelId") String channelId,
        @Param("localBalance") Long localBalance,
        @Param("remoteBalance") Long remoteBalance,
        @Param("now") Instant now
    );

    /**
     * Find channels with auto-rebalance enabled
     */
    List<ChannelEntity> findByAutoRebalanceEnabledTrue();

    /**
     * Get top earning channels
     */
    @Query("SELECT c FROM ChannelEntity c WHERE c.userId = :userId " +
           "ORDER BY c.totalFeesEarned DESC")
    List<ChannelEntity> findTopEarningChannels(@Param("userId") String userId, Pageable pageable);

    /**
     * Find channels needing backup
     */
    @Query("SELECT c FROM ChannelEntity c WHERE c.status = 'ACTIVE' " +
           "AND (c.lastBackupAt IS NULL OR c.lastBackupAt < :threshold)")
    List<ChannelEntity> findChannelsNeedingBackup(@Param("threshold") Instant threshold);

    /**
     * Get total fees earned by user
     */
    @Query("SELECT SUM(c.totalFeesEarned) FROM ChannelEntity c WHERE c.userId = :userId")
    Long getTotalFeesEarned(@Param("userId") String userId);

    /**
     * Find channels by closing transaction
     */
    Optional<ChannelEntity> findByClosingTxId(String closingTxId);

    /**
     * Delete closed channels older than date
     */
    @Modifying
    @Query("DELETE FROM ChannelEntity c WHERE c.status IN ('CLOSED', 'FORCE_CLOSED') " +
           "AND c.closedAt < :cutoffDate")
    int deleteOldClosedChannels(@Param("cutoffDate") Instant cutoffDate);

    /**
     * Check if channel exists for user
     */
    boolean existsByIdAndUserId(String channelId, String userId);

    /**
     * Find channels with pending HTLCs
     */
    @Query("SELECT c FROM ChannelEntity c WHERE c.pendingHtlcs > 0")
    List<ChannelEntity> findChannelsWithPendingHtlcs();
}