package com.waqiti.compliance.events.store;

import com.waqiti.compliance.events.model.ComplianceDomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Compliance event store for durability and event sourcing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ComplianceEventStore {
    
    private final ConcurrentHashMap<String, List<ComplianceDomainEvent>> eventStore = new ConcurrentHashMap<>();
    
    /**
     * Store compliance event for durability
     */
    public void storeEvent(ComplianceDomainEvent event) {
        try {
            String key = event.getComplianceId() != null ? event.getComplianceId() : "global";
            
            eventStore.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(event);
            
            log.debug("Stored compliance event: type={}, complianceId={}, eventId={}", 
                event.getEventType(), event.getComplianceId(), event.getEventId());
                
        } catch (Exception e) {
            log.error("Failed to store compliance event: {}", event.getEventId(), e);
            throw new RuntimeException("Event storage failed", e);
        }
    }
    
    /**
     * Retrieve events for a compliance ID
     */
    public List<ComplianceDomainEvent> getEvents(String complianceId) {
        return eventStore.getOrDefault(complianceId, new CopyOnWriteArrayList<>());
    }
    
    /**
     * Retrieve events by type
     */
    public List<ComplianceDomainEvent> getEventsByType(String complianceId, String eventType) {
        return getEvents(complianceId).stream()
            .filter(event -> eventType.equals(event.getEventType()))
            .toList();
    }
    
    /**
     * Retrieve events after timestamp
     */
    public List<ComplianceDomainEvent> getEventsAfter(String complianceId, Instant timestamp) {
        return getEvents(complianceId).stream()
            .filter(event -> event.getTimestamp() != null && event.getTimestamp().isAfter(timestamp))
            .toList();
    }
    
    /**
     * Get total event count
     */
    public long getTotalEventCount() {
        return eventStore.values().stream()
            .mapToLong(List::size)
            .sum();
    }
    
    /**
     * Clear old events (for cleanup)
     */
    public void clearEventsOlderThan(Instant cutoff) {
        eventStore.values().forEach(events -> 
            events.removeIf(event -> event.getTimestamp() != null && event.getTimestamp().isBefore(cutoff)));
    }
}