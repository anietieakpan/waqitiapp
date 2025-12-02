package com.waqiti.card.repository;

import com.waqiti.card.entity.CardAuditLog;
import com.waqiti.card.enums.CardAuditEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CardAuditLogRepository - Data access for card audit logs
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Repository
public interface CardAuditLogRepository extends JpaRepository<CardAuditLog, UUID> {

    /**
     * Find all audit logs for a specific card, ordered by timestamp descending
     *
     * @param cardId Card ID
     * @return List of audit logs
     */
    List<CardAuditLog> findByCardIdOrderByTimestampDesc(UUID cardId);

    /**
     * Find all audit logs for a specific user, ordered by timestamp descending
     *
     * @param userId User ID
     * @return List of audit logs
     */
    List<CardAuditLog> findByUserIdOrderByTimestampDesc(UUID userId);

    /**
     * Find audit logs by card ID and event type
     *
     * @param cardId Card ID
     * @param eventType Event type
     * @return List of audit logs
     */
    List<CardAuditLog> findByCardIdAndEventType(UUID cardId, CardAuditEventType eventType);

    /**
     * Count audit logs by card ID, event type, and timestamp after
     *
     * @param cardId Card ID
     * @param eventType Event type
     * @param timestamp Timestamp threshold
     * @return Count of matching audit logs
     */
    long countByCardIdAndEventTypeAndTimestampAfter(
            UUID cardId,
            CardAuditEventType eventType,
            LocalDateTime timestamp
    );

    /**
     * Find audit logs by card ID within a time range
     *
     * @param cardId Card ID
     * @param startTime Start time
     * @param endTime End time
     * @return List of audit logs
     */
    @Query("SELECT a FROM CardAuditLog a WHERE a.cardId = :cardId " +
           "AND a.timestamp BETWEEN :startTime AND :endTime " +
           "ORDER BY a.timestamp DESC")
    List<CardAuditLog> findByCardIdAndTimestampBetween(
            @Param("cardId") UUID cardId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );

    /**
     * Find failed events for a card
     *
     * @param cardId Card ID
     * @param since Since timestamp
     * @return List of failed audit logs
     */
    @Query("SELECT a FROM CardAuditLog a WHERE a.cardId = :cardId " +
           "AND a.success = false " +
           "AND a.timestamp >= :since " +
           "ORDER BY a.timestamp DESC")
    List<CardAuditLog> findFailedEventsByCardIdSince(
            @Param("cardId") UUID cardId,
            @Param("since") LocalDateTime since
    );

    /**
     * Find all audit logs by event type within time range
     *
     * @param eventType Event type
     * @param startTime Start time
     * @param endTime End time
     * @return List of audit logs
     */
    List<CardAuditLog> findByEventTypeAndTimestampBetween(
            CardAuditEventType eventType,
            LocalDateTime startTime,
            LocalDateTime endTime
    );

    /**
     * Count failed PIN attempts for a card since a timestamp
     *
     * @param cardId Card ID
     * @param since Since timestamp
     * @return Count of failed attempts
     */
    @Query("SELECT COUNT(a) FROM CardAuditLog a WHERE a.cardId = :cardId " +
           "AND a.eventType = 'PIN_VERIFICATION_FAILED' " +
           "AND a.timestamp >= :since")
    long countFailedPinAttemptsSince(
            @Param("cardId") UUID cardId,
            @Param("since") LocalDateTime since
    );

    /**
     * Find recent audit logs for fraud analysis
     *
     * @param userId User ID
     * @param limit Number of recent logs to retrieve
     * @return List of recent audit logs
     */
    @Query(value = "SELECT * FROM card_audit_log WHERE user_id = :userId " +
           "ORDER BY timestamp DESC LIMIT :limit", nativeQuery = true)
    List<CardAuditLog> findRecentAuditLogsByUserId(
            @Param("userId") UUID userId,
            @Param("limit") int limit
    );
}
