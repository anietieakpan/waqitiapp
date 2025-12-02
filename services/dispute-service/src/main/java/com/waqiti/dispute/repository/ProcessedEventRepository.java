package com.waqiti.dispute.repository;

import com.waqiti.dispute.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ProcessedEvent entities
 * Tracks processed Kafka events for idempotency
 * Enhanced with additional query methods for distributed idempotency
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    Optional<ProcessedEvent> findByEventId(String eventId);

    Optional<ProcessedEvent> findByEventKey(String eventKey);

    boolean existsByEventId(String eventId);

    boolean existsByEventKey(String eventKey);

    @Query("SELECT p FROM ProcessedEvent p WHERE p.expiresAt < :cutoffTime")
    List<ProcessedEvent> findExpiredEvents(@Param("cutoffTime") LocalDateTime cutoffTime);

    @Query("SELECT COUNT(p) FROM ProcessedEvent p WHERE p.success = true")
    long countSuccessfulEvents();

    @Query("SELECT COUNT(p) FROM ProcessedEvent p WHERE p.success = false")
    long countFailedEvents();
}
