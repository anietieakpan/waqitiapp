package com.waqiti.notification.repository;

import com.waqiti.notification.domain.DeliveryStatus;
import com.waqiti.notification.domain.Notification;
import com.waqiti.notification.domain.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Optimized notification repository with performance improvements
 */
@Repository
public interface OptimizedNotificationRepository extends JpaRepository<Notification, UUID> {
    
    /**
     * Find notifications by user ID with optimized index usage
     */
    @Query(value = "SELECT * FROM notifications " +
           "WHERE user_id = ?1 " +
           "ORDER BY created_at DESC " +
           "LIMIT ?2 OFFSET ?3",
           nativeQuery = true)
    List<Notification> findByUserIdOptimized(UUID userId, int limit, int offset);

    /**
     * Find unread notifications by user ID with covering index
     */
    @Query(value = "SELECT * FROM notifications " +
           "WHERE user_id = ?1 AND read = false " +
           "ORDER BY created_at DESC " +
           "LIMIT ?2",
           nativeQuery = true)
    List<Notification> findUnreadByUserIdOptimized(UUID userId, int limit);

    /**
     * Count unread notifications efficiently using index-only scan
     */
    @Query(value = "SELECT COUNT(*) FROM notifications " +
           "WHERE user_id = ?1 AND read = false",
           nativeQuery = true)
    long countUnreadByUserId(UUID userId);

    /**
     * Batch mark notifications as read - optimized for performance
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt " +
           "WHERE n.userId = :userId AND n.id IN :ids AND n.read = false")
    int markAsReadBatch(@Param("userId") UUID userId, 
                       @Param("ids") List<UUID> ids, 
                       @Param("readAt") LocalDateTime readAt);

    /**
     * Batch mark all user notifications as read
     */
    @Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt " +
           "WHERE n.userId = :userId AND n.read = false")
    int markAllAsRead(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);

    /**
     * Find notifications by category with optimized filtering
     */
    @Query(value = "SELECT * FROM notifications " +
           "WHERE user_id = ?1 AND category = ?2 " +
           "ORDER BY created_at DESC " +
           "LIMIT ?3",
           nativeQuery = true)
    List<Notification> findByCategoryOptimized(UUID userId, String category, int limit);

    /**
     * Find notifications by delivery status - batch processing
     */
    @Query(value = "SELECT * FROM notifications " +
           "WHERE delivery_status = ?1 " +
           "ORDER BY created_at ASC " +
           "LIMIT ?2",
           nativeQuery = true)
    List<Notification> findByDeliveryStatusBatch(String status, int batchSize);

    /**
     * Find expired notifications for cleanup with efficient scanning
     */
    @Query(value = "SELECT id FROM notifications " +
           "WHERE read = false " +
           "AND expires_at < ?1 " +
           "AND delivery_status = ?2 " +
           "ORDER BY expires_at ASC " +
           "LIMIT ?3",
           nativeQuery = true)
    List<UUID> findExpiredNotificationIds(LocalDateTime now, String status, int batchSize);

    /**
     * Batch delete expired notifications
     */
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.id IN :ids")
    int deleteByIds(@Param("ids") List<UUID> ids);

    /**
     * Get notification summary statistics efficiently
     */
    @Query(value = "SELECT " +
           "COUNT(*) as total, " +
           "COUNT(*) FILTER (WHERE read = false) as unread, " +
           "COUNT(*) FILTER (WHERE delivery_status = 'DELIVERED') as delivered, " +
           "COUNT(*) FILTER (WHERE delivery_status = 'FAILED') as failed " +
           "FROM notifications " +
           "WHERE user_id = ?1 " +
           "AND created_at >= ?2",
           nativeQuery = true)
    Object[] getNotificationStatsByUser(UUID userId, LocalDateTime since);

    /**
     * Find latest notification by user and category using limit
     */
    @Query(value = "SELECT * FROM notifications " +
           "WHERE user_id = ?1 AND category = ?2 " +
           "ORDER BY created_at DESC " +
           "LIMIT 1",
           nativeQuery = true)
    Notification findLatestByUserIdAndCategoryOptimized(UUID userId, String category);

    /**
     * Bulk insert notifications for batch processing
     */
    @Modifying
    @Query(value = "INSERT INTO notifications " +
           "(id, user_id, title, content, type, category, delivery_status, read, created_at) " +
           "VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, false, ?8)",
           nativeQuery = true)
    void insertNotificationBatch(UUID id, UUID userId, String title, String content, 
                               String type, String category, String deliveryStatus, 
                               LocalDateTime createdAt);

    /**
     * Get notification delivery metrics for monitoring
     */
    @Query(value = "SELECT " +
           "delivery_status, " +
           "COUNT(*) as count, " +
           "AVG(EXTRACT(EPOCH FROM (delivered_at - created_at))) as avg_delivery_time " +
           "FROM notifications " +
           "WHERE created_at >= ?1 " +
           "AND delivery_status IN ('DELIVERED', 'FAILED') " +
           "GROUP BY delivery_status",
           nativeQuery = true)
    List<Object[]> getDeliveryMetrics(LocalDateTime since);

    /**
     * Find notifications requiring retry with backoff
     */
    @Query(value = "SELECT * FROM notifications " +
           "WHERE delivery_status = 'FAILED' " +
           "AND retry_count < ?1 " +
           "AND (last_retry_at IS NULL OR last_retry_at < ?2) " +
           "ORDER BY created_at ASC " +
           "LIMIT ?3",
           nativeQuery = true)
    List<Notification> findNotificationsForRetry(int maxRetries, LocalDateTime retryAfter, int batchSize);

    /**
     * Update retry information efficiently
     */
    @Modifying
    @Query("UPDATE Notification n SET n.retryCount = n.retryCount + 1, " +
           "n.lastRetryAt = :retryTime, n.deliveryStatus = :status " +
           "WHERE n.id = :id")
    void updateRetryInfo(@Param("id") UUID id, 
                        @Param("retryTime") LocalDateTime retryTime,
                        @Param("status") DeliveryStatus status);
}