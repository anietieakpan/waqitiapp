package com.waqiti.webhook.repository;

import com.waqiti.webhook.entity.WebhookSubscription;
import com.waqiti.webhook.model.WebhookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Repository for WebhookSubscription entity
 *
 * Manages webhook subscriptions for users/clients, enabling them to
 * subscribe to specific event types and receive HTTP callbacks.
 *
 * PRODUCTION-GRADE FEATURES:
 * - User subscription management
 * - Event type filtering
 * - Active/inactive subscription tracking
 * - Subscription analytics
 * - Performance monitoring
 *
 * @author Waqiti Platform Team
 * @version 1.0.0
 * @since 2025-10-20
 */
@Repository
public interface WebhookSubscriptionRepository extends JpaRepository<WebhookSubscription, String> {

    // ========== CORE QUERIES ==========

    /**
     * Find subscription by ID and user ID (ownership validation)
     */
    Optional<WebhookSubscription> findByIdAndUserId(String id, String userId);

    /**
     * Find all subscriptions for a user
     */
    List<WebhookSubscription> findByUserId(String userId);

    /**
     * Find all active subscriptions for a user
     */
    List<WebhookSubscription> findByUserIdAndIsActiveTrue(String userId);

    /**
     * Find subscriptions by user ID with pagination
     */
    Page<WebhookSubscription> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    /**
     * Find subscription by URL (exact match)
     */
    Optional<WebhookSubscription> findByUrl(String url);

    /**
     * Find subscriptions by user and URL
     */
    List<WebhookSubscription> findByUserIdAndUrl(String userId, String url);

    // ========== EVENT TYPE QUERIES ==========

    /**
     * Find subscriptions for specific event type
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE :eventType MEMBER OF s.eventTypes " +
           "AND s.isActive = true " +
           "AND s.status = 'ACTIVE'")
    List<WebhookSubscription> findActiveSubscriptionsByEventType(@Param("eventType") String eventType);

    /**
     * Find user's subscriptions for specific event type
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.userId = :userId " +
           "AND :eventType MEMBER OF s.eventTypes")
    List<WebhookSubscription> findByUserIdAndEventType(@Param("userId") String userId,
                                                         @Param("eventType") String eventType);

    /**
     * Check if subscription already exists for user, URL, and event types
     * Used for duplicate prevention
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.userId = :userId " +
           "AND s.url = :url " +
           "AND s.eventTypes = :eventTypes")
    Optional<WebhookSubscription> findByUserIdAndUrlAndEventTypes(
            @Param("userId") String userId,
            @Param("url") String url,
            @Param("eventTypes") Set<String> eventTypes);

    // ========== STATUS-BASED QUERIES ==========

    /**
     * Find subscriptions by status
     */
    Page<WebhookSubscription> findByStatusOrderByCreatedAtDesc(WebhookStatus status, Pageable pageable);

    /**
     * Find user's subscriptions by status
     */
    Page<WebhookSubscription> findByUserIdAndStatusOrderByCreatedAtDesc(
            String userId, WebhookStatus status, Pageable pageable);

    /**
     * Find active subscriptions
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.isActive = true " +
           "AND s.status = 'ACTIVE' " +
           "ORDER BY s.createdAt DESC")
    List<WebhookSubscription> findAllActiveSubscriptions();

    /**
     * Find disabled subscriptions (for cleanup/notification)
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.isActive = false " +
           "OR s.status = 'DISABLED' " +
           "ORDER BY s.updatedAt DESC")
    List<WebhookSubscription> findAllDisabledSubscriptions(Pageable pageable);

    // ========== CLIENT-BASED QUERIES ==========

    /**
     * Find subscriptions by client ID
     */
    List<WebhookSubscription> findByClientId(String clientId);

    /**
     * Find subscriptions by client ID and status
     */
    List<WebhookSubscription> findByClientIdAndStatus(String clientId, WebhookStatus status);

    // ========== STATISTICS QUERIES ==========

    /**
     * Count subscriptions for user
     */
    long countByUserId(String userId);

    /**
     * Count active subscriptions for user
     */
    long countByUserIdAndIsActiveTrue(String userId);

    /**
     * Count subscriptions by status
     */
    long countByStatus(WebhookStatus status);

    /**
     * Get total success/failure counts for subscription
     */
    @Query("SELECT s.successfulDeliveries, s.failedDeliveries " +
           "FROM WebhookSubscription s WHERE s.id = :subscriptionId")
    Object[] getDeliveryCountsById(@Param("subscriptionId") String subscriptionId);

    /**
     * Get subscriptions with high failure rates
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.failedDeliveries > 0 " +
           "AND CAST(s.failedDeliveries AS double) / " +
           "    CAST((s.successfulDeliveries + s.failedDeliveries) AS double) > :threshold " +
           "ORDER BY s.failedDeliveries DESC")
    List<WebhookSubscription> findSubscriptionsWithHighFailureRate(
            @Param("threshold") double threshold, Pageable pageable);

    /**
     * Get subscriptions with no recent deliveries (potentially stale)
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.isActive = true " +
           "AND (s.lastDeliveryAt IS NULL OR s.lastDeliveryAt < :cutoffTime)")
    List<WebhookSubscription> findStaleSubscriptions(@Param("cutoffTime") LocalDateTime cutoffTime,
                                                       Pageable pageable);

    // ========== SEARCH QUERIES ==========

    /**
     * Search subscriptions by description
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.userId = :userId " +
           "AND LOWER(s.description) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<WebhookSubscription> searchByDescription(@Param("userId") String userId,
                                                    @Param("searchTerm") String searchTerm,
                                                    Pageable pageable);

    /**
     * Search subscriptions by URL pattern
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.userId = :userId " +
           "AND LOWER(s.url) LIKE LOWER(CONCAT('%', :urlPattern, '%'))")
    List<WebhookSubscription> searchByUrlPattern(@Param("userId") String userId,
                                                   @Param("urlPattern") String urlPattern,
                                                   Pageable pageable);

    // ========== FILTERING QUERIES ==========

    /**
     * Find subscriptions with custom filters
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.userId = :#{#filter.userId} " +
           "AND (:#{#filter.eventType} IS NULL OR :#{#filter.eventType} MEMBER OF s.eventTypes) " +
           "AND (:#{#filter.status} IS NULL OR s.status = :#{#filter.status}) " +
           "AND (:#{#filter.isActive} IS NULL OR s.isActive = :#{#filter.isActive}) " +
           "ORDER BY s.createdAt DESC")
    Page<WebhookSubscription> findByUserIdWithFilters(
            @Param("filter") com.waqiti.webhook.dto.WebhookSubscriptionFilter filter,
            Pageable pageable);

    // ========== BULK OPERATIONS ==========

    /**
     * Update subscription status in bulk
     */
    @Modifying
    @Query("UPDATE WebhookSubscription s " +
           "SET s.status = :status, s.isActive = :isActive, s.updatedAt = :now " +
           "WHERE s.id IN :subscriptionIds")
    int bulkUpdateStatus(@Param("subscriptionIds") List<String> subscriptionIds,
                          @Param("status") WebhookStatus status,
                          @Param("isActive") boolean isActive,
                          @Param("now") LocalDateTime now);

    /**
     * Disable subscriptions with excessive failures
     */
    @Modifying
    @Query("UPDATE WebhookSubscription s " +
           "SET s.isActive = false, s.status = 'DISABLED', s.updatedAt = :now " +
           "WHERE s.failedDeliveries >= :threshold " +
           "AND s.isActive = true")
    int disableSubscriptionsWithExcessiveFailures(@Param("threshold") long threshold,
                                                    @Param("now") LocalDateTime now);

    /**
     * Increment successful delivery counter
     */
    @Modifying
    @Query("UPDATE WebhookSubscription s " +
           "SET s.successfulDeliveries = s.successfulDeliveries + 1, " +
           "s.lastDeliveryAt = :deliveryTime " +
           "WHERE s.id = :subscriptionId")
    int incrementSuccessfulDeliveries(@Param("subscriptionId") String subscriptionId,
                                       @Param("deliveryTime") LocalDateTime deliveryTime);

    /**
     * Increment failed delivery counter
     */
    @Modifying
    @Query("UPDATE WebhookSubscription s " +
           "SET s.failedDeliveries = s.failedDeliveries + 1, " +
           "s.lastDeliveryAt = :deliveryTime " +
           "WHERE s.id = :subscriptionId")
    int incrementFailedDeliveries(@Param("subscriptionId") String subscriptionId,
                                    @Param("deliveryTime") LocalDateTime deliveryTime);

    // ========== MONITORING QUERIES ==========

    /**
     * Get subscription statistics grouped by event type
     */
    @Query("SELECT " +
           "COUNT(DISTINCT s.id) as subscriptionCount " +
           "FROM WebhookSubscription s " +
           "WHERE s.isActive = true")
    long countActiveSubscriptions();

    /**
     * Get subscriptions that need health check
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.isActive = true " +
           "AND s.lastDeliveryAt IS NOT NULL " +
           "AND s.failedDeliveries > :failureThreshold")
    List<WebhookSubscription> findSubscriptionsNeedingHealthCheck(
            @Param("failureThreshold") long failureThreshold);

    /**
     * Find subscriptions approaching retry limit
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.failedDeliveries >= :threshold " +
           "AND s.failedDeliveries < s.maxRetries " +
           "AND s.isActive = true")
    List<WebhookSubscription> findSubscriptionsApproachingRetryLimit(
            @Param("threshold") long threshold);

    // ========== CLEANUP QUERIES ==========

    /**
     * Find old inactive subscriptions for cleanup
     */
    @Query("SELECT s FROM WebhookSubscription s " +
           "WHERE s.isActive = false " +
           "AND s.updatedAt < :cutoffDate " +
           "ORDER BY s.updatedAt ASC")
    List<WebhookSubscription> findOldInactiveSubscriptions(
            @Param("cutoffDate") LocalDateTime cutoffDate,
            Pageable pageable);

    /**
     * Delete old inactive subscriptions
     */
    @Modifying
    @Query("DELETE FROM WebhookSubscription s " +
           "WHERE s.isActive = false " +
           "AND s.updatedAt < :cutoffDate")
    int deleteOldInactiveSubscriptions(@Param("cutoffDate") LocalDateTime cutoffDate);

    // ========== DEDUPLICATION ==========

    /**
     * Check if active subscription exists for URL
     */
    boolean existsByUrlAndIsActiveTrue(String url);

    /**
     * Check if subscription exists for user and URL
     */
    boolean existsByUserIdAndUrl(String userId, String url);

    // ========== TENANT QUERIES (for multi-tenancy if needed) ==========

    /**
     * Find subscriptions by tenant ID
     */
    Page<WebhookSubscription> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);

    /**
     * Count subscriptions for tenant
     */
    long countByTenantId(String tenantId);

    /**
     * Find active subscriptions for tenant
     */
    List<WebhookSubscription> findByTenantIdAndIsActiveTrueAndStatus(
            String tenantId, WebhookStatus status);
}
