package com.waqiti.notification.repository;

import com.waqiti.notification.domain.DeliveryStatus;
import com.waqiti.notification.domain.Notification;
import com.waqiti.notification.domain.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    /**
     * Find notifications by user ID, sorted by creation date
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find unread notifications by user ID
     */
    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /**
     * Find notifications by user ID and category
     */
    Page<Notification> findByUserIdAndCategoryOrderByCreatedAtDesc(UUID userId, String category, Pageable pageable);

    /**
     * Find notifications by delivery status
     */
    List<Notification> findByDeliveryStatus(DeliveryStatus status);

    /**
     * Find notifications by type and delivery status
     */
    List<Notification> findByTypeAndDeliveryStatus(NotificationType type, DeliveryStatus status);

    /**
     * Count unread notifications by user ID
     */
    long countByUserIdAndReadFalse(UUID userId);

    /**
     * CRITICAL FIX: Bulk update to mark all notifications as read
     * Replaces N+1 query pattern (fetch all + loop + save each)
     * Single UPDATE query handles all records at once
     *
     * Performance: O(1) vs O(n) - 100x faster for 100 notifications
     * Memory: Constant vs O(n) - prevents OOM for users with 1000s of notifications
     *
     * @param userId The user ID
     * @param readAt The timestamp when notifications were marked as read
     * @return Number of notifications updated
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Notification n SET n.read = true, n.readAt = :readAt WHERE n.userId = :userId AND n.read = false")
    int markAllAsReadBulk(@Param("userId") UUID userId, @Param("readAt") LocalDateTime readAt);

    /**
     * OPTIMIZED: Get IDs of unread notifications for a user
     * Used for metrics/logging without loading full entities
     *
     * @param userId The user ID
     * @return List of notification IDs
     */
    @Query("SELECT n.notificationId FROM Notification n WHERE n.userId = :userId AND n.read = false")
    List<UUID> findUnreadNotificationIds(@Param("userId") UUID userId);

    /**
     * Find expired unread notifications to clean up
     */
    List<Notification> findByReadFalseAndExpiresAtBeforeAndDeliveryStatus(
            LocalDateTime now, DeliveryStatus status);

    /**
     * Find notifications by reference ID
     */
    List<Notification> findByReferenceId(String referenceId);

    /**
     * Find latest notification by user ID and category
     */
    @Query("SELECT n FROM Notification n WHERE n.userId = :userId AND n.category = :category " +
            "ORDER BY n.createdAt DESC LIMIT 1")
    Notification findLatestByUserIdAndCategory(@Param("userId") UUID userId, @Param("category") String category);
}