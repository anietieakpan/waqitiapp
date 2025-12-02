package com.waqiti.wallet.repository;

import com.waqiti.wallet.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    
    boolean existsByEventId(String eventId);
    
    Optional<ProcessedEvent> findByEventId(String eventId);
    
    List<ProcessedEvent> findByEventType(String eventType);
    
    List<ProcessedEvent> findByEntityId(String entityId);
    
    List<ProcessedEvent> findByProcessedAtBetween(LocalDateTime start, LocalDateTime end);
    
    void deleteByProcessedAtBefore(LocalDateTime cutoffDate);
}