package com.waqiti.legal.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Legal Notification Repository
 *
 * Complete data access layer for LegalNotification entities with custom query methods
 * Supports legal notices, alerts, and notification tracking
 *
 * Note: This repository is designed for a LegalNotification entity that should be created
 * to track legal notifications and alerts
 *
 * @author Waqiti Legal Team
 * @version 1.0.0
 * @since 2025-10-18
 */
@Repository
public interface LegalNotificationRepository extends JpaRepository<LegalNotification, UUID> {

    /**
     * Find notification by notification ID
     */
    Optional<LegalNotification> findByNotificationId(String notificationId);

    /**
     * Find notifications by notification type
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.notificationType = :notificationType")
    List<LegalNotification> findByNotificationType(@Param("notificationType") String notificationType);

    /**
     * Find notifications by recipient ID
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.recipientId = :recipientId " +
           "ORDER BY n.createdAt DESC")
    List<LegalNotification> findByRecipientId(@Param("recipientId") String recipientId);

    /**
     * Find notifications by recipient email
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.recipientEmail = :recipientEmail " +
           "ORDER BY n.createdAt DESC")
    List<LegalNotification> findByRecipientEmail(@Param("recipientEmail") String recipientEmail);

    /**
     * Find notifications by status
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.status = :status")
    List<LegalNotification> findByStatus(@Param("status") String status);

    /**
     * Find pending notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.status = 'PENDING' " +
           "ORDER BY n.priority DESC, n.createdAt ASC")
    List<LegalNotification> findPendingNotifications();

    /**
     * Find sent notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.status = 'SENT' " +
           "AND n.sentAt IS NOT NULL")
    List<LegalNotification> findSentNotifications();

    /**
     * Find failed notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.status = 'FAILED'")
    List<LegalNotification> findFailedNotifications();

    /**
     * Find notifications by priority
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.priority = :priority " +
           "ORDER BY n.createdAt DESC")
    List<LegalNotification> findByPriority(@Param("priority") String priority);

    /**
     * Find high priority notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.priority IN ('HIGH', 'URGENT') " +
           "AND n.status NOT IN ('SENT', 'DELIVERED') " +
           "ORDER BY n.priority DESC, n.createdAt ASC")
    List<LegalNotification> findHighPriorityNotifications();

    /**
     * Find unread notifications for recipient
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.recipientId = :recipientId " +
           "AND n.read = false " +
           "ORDER BY n.createdAt DESC")
    List<LegalNotification> findUnreadNotificationsByRecipient(@Param("recipientId") String recipientId);

    /**
     * Find read notifications for recipient
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.recipientId = :recipientId " +
           "AND n.read = true " +
           "ORDER BY n.readAt DESC")
    List<LegalNotification> findReadNotificationsByRecipient(@Param("recipientId") String recipientId);

    /**
     * Find notifications by channel
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.channel = :channel")
    List<LegalNotification> findByChannel(@Param("channel") String channel);

    /**
     * Find email notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.channel = 'EMAIL'")
    List<LegalNotification> findEmailNotifications();

    /**
     * Find SMS notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.channel = 'SMS'")
    List<LegalNotification> findSmsNotifications();

    /**
     * Find in-app notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.channel = 'IN_APP'")
    List<LegalNotification> findInAppNotifications();

    /**
     * Find notifications by related entity type
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.relatedEntityType = :entityType")
    List<LegalNotification> findByRelatedEntityType(@Param("entityType") String entityType);

    /**
     * Find notifications by related entity ID
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.relatedEntityId = :entityId " +
           "ORDER BY n.createdAt DESC")
    List<LegalNotification> findByRelatedEntityId(@Param("entityId") String entityId);

    /**
     * Find notifications by category
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.category = :category")
    List<LegalNotification> findByCategory(@Param("category") String category);

    /**
     * Find deadline notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.category = 'DEADLINE' " +
           "OR n.notificationType IN ('DEADLINE_REMINDER', 'DEADLINE_APPROACHING')")
    List<LegalNotification> findDeadlineNotifications();

    /**
     * Find compliance notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.category = 'COMPLIANCE'")
    List<LegalNotification> findComplianceNotifications();

    /**
     * Find alert notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.category = 'ALERT'")
    List<LegalNotification> findAlertNotifications();

    /**
     * Find notifications requiring acknowledgment
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.requiresAcknowledgment = true " +
           "AND n.acknowledged = false")
    List<LegalNotification> findRequiringAcknowledgment();

    /**
     * Find acknowledged notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.acknowledged = true " +
           "AND n.acknowledgedAt IS NOT NULL")
    List<LegalNotification> findAcknowledgedNotifications();

    /**
     * Find notifications scheduled for future delivery
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.scheduledFor IS NOT NULL " +
           "AND n.scheduledFor > :currentDateTime " +
           "AND n.status = 'SCHEDULED'")
    List<LegalNotification> findScheduledNotifications(@Param("currentDateTime") LocalDateTime currentDateTime);

    /**
     * Find notifications ready to send
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.scheduledFor IS NOT NULL " +
           "AND n.scheduledFor <= :currentDateTime " +
           "AND n.status = 'SCHEDULED'")
    List<LegalNotification> findReadyToSend(@Param("currentDateTime") LocalDateTime currentDateTime);

    /**
     * Find notifications sent within date range
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.sentAt BETWEEN :startDateTime AND :endDateTime " +
           "ORDER BY n.sentAt DESC")
    List<LegalNotification> findBySentAtBetween(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Find notifications created within date range
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.createdAt BETWEEN :startDateTime AND :endDateTime " +
           "ORDER BY n.createdAt DESC")
    List<LegalNotification> findByCreatedAtBetween(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Find notifications with delivery failure
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.deliveryFailed = true " +
           "AND n.retryCount < n.maxRetries")
    List<LegalNotification> findDeliveryFailuresForRetry();

    /**
     * Find notifications exceeding retry limit
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.deliveryFailed = true " +
           "AND n.retryCount >= n.maxRetries")
    List<LegalNotification> findExceededRetryLimit();

    /**
     * Find notifications by template ID
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.templateId = :templateId")
    List<LegalNotification> findByTemplateId(@Param("templateId") String templateId);

    /**
     * Find notifications by sender
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.senderId = :senderId")
    List<LegalNotification> findBySenderId(@Param("senderId") String senderId);

    /**
     * Find notifications with attachments
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.hasAttachments = true")
    List<LegalNotification> findNotificationsWithAttachments();

    /**
     * Find notifications by subject keyword
     */
    @Query("SELECT n FROM LegalNotification n WHERE LOWER(n.subject) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<LegalNotification> searchBySubject(@Param("keyword") String keyword);

    /**
     * Find expired notifications (not read within expiry period)
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.expiresAt IS NOT NULL " +
           "AND n.expiresAt < :currentDateTime " +
           "AND n.read = false")
    List<LegalNotification> findExpiredNotifications(@Param("currentDateTime") LocalDateTime currentDateTime);

    /**
     * Find notifications expiring soon
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.expiresAt BETWEEN :startDateTime AND :endDateTime " +
           "AND n.read = false")
    List<LegalNotification> findExpiringNotifications(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Find recurring notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.recurring = true")
    List<LegalNotification> findRecurringNotifications();

    /**
     * Find batch notifications
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.batchId IS NOT NULL " +
           "AND n.batchId = :batchId")
    List<LegalNotification> findByBatchId(@Param("batchId") String batchId);

    /**
     * Count unread notifications for recipient
     */
    @Query("SELECT COUNT(n) FROM LegalNotification n WHERE n.recipientId = :recipientId " +
           "AND n.read = false")
    long countUnreadNotificationsByRecipient(@Param("recipientId") String recipientId);

    /**
     * Count pending notifications
     */
    @Query("SELECT COUNT(n) FROM LegalNotification n WHERE n.status = 'PENDING'")
    long countPendingNotifications();

    /**
     * Count failed notifications
     */
    @Query("SELECT COUNT(n) FROM LegalNotification n WHERE n.status = 'FAILED'")
    long countFailedNotifications();

    /**
     * Count notifications by type
     */
    @Query("SELECT COUNT(n) FROM LegalNotification n WHERE n.notificationType = :notificationType")
    long countByNotificationType(@Param("notificationType") String notificationType);

    /**
     * Check if notification exists for entity and type
     */
    boolean existsByRelatedEntityIdAndNotificationType(String relatedEntityId, String notificationType);

    /**
     * Find notifications requiring immediate attention
     */
    @Query("SELECT n FROM LegalNotification n WHERE " +
           "(n.priority IN ('HIGH', 'URGENT') AND n.status = 'PENDING') " +
           "OR (n.requiresAcknowledgment = true AND n.acknowledged = false) " +
           "OR (n.deliveryFailed = true AND n.retryCount < n.maxRetries) " +
           "ORDER BY n.priority DESC, n.createdAt ASC")
    List<LegalNotification> findRequiringImmediateAttention();

    /**
     * Get notification statistics by type
     */
    @Query("SELECT n.notificationType, COUNT(*), " +
           "COUNT(CASE WHEN n.status = 'SENT' THEN 1 END), " +
           "COUNT(CASE WHEN n.read = true THEN 1 END) " +
           "FROM LegalNotification n " +
           "WHERE n.createdAt BETWEEN :startDateTime AND :endDateTime " +
           "GROUP BY n.notificationType")
    List<Object[]> getNotificationStatisticsByType(
        @Param("startDateTime") LocalDateTime startDateTime,
        @Param("endDateTime") LocalDateTime endDateTime
    );

    /**
     * Get notification statistics by recipient
     */
    @Query("SELECT n.recipientId, COUNT(*), " +
           "COUNT(CASE WHEN n.status = 'SENT' THEN 1 END), " +
           "COUNT(CASE WHEN n.read = false THEN 1 END) " +
           "FROM LegalNotification n " +
           "GROUP BY n.recipientId")
    List<Object[]> getNotificationStatisticsByRecipient();

    /**
     * Find notifications by created by user
     */
    @Query("SELECT n FROM LegalNotification n WHERE n.createdBy = :createdBy")
    List<LegalNotification> findByCreatedBy(@Param("createdBy") String createdBy);

    /**
     * Mark all as read for recipient
     */
    @Query("UPDATE LegalNotification n SET n.read = true, n.readAt = :readAt " +
           "WHERE n.recipientId = :recipientId AND n.read = false")
    void markAllAsReadForRecipient(@Param("recipientId") String recipientId, @Param("readAt") LocalDateTime readAt);

    /**
     * Delete old notifications (cleanup)
     */
    @Query("DELETE FROM LegalNotification n WHERE n.createdAt < :thresholdDate " +
           "AND n.requiresAcknowledgment = false " +
           "AND n.read = true")
    void deleteOldNotifications(@Param("thresholdDate") LocalDateTime thresholdDate);
}

/**
 * Placeholder class for LegalNotification entity
 * This should be created as a proper domain entity in com.waqiti.legal.domain package
 */
class LegalNotification {
    private UUID id;
    private String notificationId;
    private String notificationType;
    private String recipientId;
    private String recipientEmail;
    private String status;
    private String priority;
    private Boolean read;
    private LocalDateTime readAt;
    private String channel;
    private String relatedEntityType;
    private String relatedEntityId;
    private String category;
    private Boolean requiresAcknowledgment;
    private Boolean acknowledged;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime scheduledFor;
    private LocalDateTime sentAt;
    private LocalDateTime createdAt;
    private Boolean deliveryFailed;
    private Integer retryCount;
    private Integer maxRetries;
    private String templateId;
    private String senderId;
    private Boolean hasAttachments;
    private String subject;
    private LocalDateTime expiresAt;
    private Boolean recurring;
    private String batchId;
    private String createdBy;
}
