package com.waqiti.audit.repository;

import com.waqiti.audit.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository for tracking processed events to ensure idempotency
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {
    
    boolean existsByEventId(String eventId);

    Optional<ProcessedEvent> findByEventId(String eventId);
}