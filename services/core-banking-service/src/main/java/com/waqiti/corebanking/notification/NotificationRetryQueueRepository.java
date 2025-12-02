package com.waqiti.corebanking.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for NotificationRetryQueue
 *
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Repository
public interface NotificationRetryQueueRepository extends JpaRepository<NotificationRetryQueue, UUID> {

    /**
     * Find notifications ready for retry
     * Returns notifications where next_retry_at <= now and status = PENDING or RETRYING
     */
    @Query("SELECT n FROM NotificationRetryQueue n WHERE " +
           "n.status IN ('PENDING', 'RETRYING') AND " +
           "n.nextRetryAt <= :now ORDER BY n.nextRetryAt ASC")
    List<NotificationRetryQueue> findReadyForRetry(@Param("now") LocalDateTime now);

    /**
     * Find failed notifications (exceeded max retries)
     */
    List<NotificationRetryQueue> findByStatus(NotificationRetryQueue.RetryStatus status);

    /**
     * Count pending notifications by type
     */
    @Query("SELECT COUNT(n) FROM NotificationRetryQueue n WHERE " +
           "n.notificationType = :type AND n.status = 'PENDING'")
    long countPendingByType(@Param("type") NotificationRetryQueue.NotificationType type);

    /**
     * Delete old completed/failed notifications (cleanup after 7 days)
     */
    @Query("DELETE FROM NotificationRetryQueue n WHERE " +
           "(n.status = 'COMPLETED' OR n.status = 'FAILED') AND " +
           "n.createdAt < :cutoffDate")
    int deleteOldNotifications(@Param("cutoffDate") LocalDateTime cutoffDate);
}
