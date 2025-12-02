package com.waqiti.risk.events.store;

import com.waqiti.risk.events.model.RiskEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Risk event store for durability and event sourcing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RiskEventStore {
    
    private final ConcurrentHashMap<String, List<RiskEvent>> eventStore = new ConcurrentHashMap<>();
    
    /**
     * Store risk event for durability
     */
    public void storeEvent(RiskEvent event) {
        try {
            String key = event.getRiskId() != null ? event.getRiskId() : "global";
            
            eventStore.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(event);
            
            log.debug("Stored risk event: type={}, riskId={}, eventId={}", 
                event.getEventType(), event.getRiskId(), event.getEventId());
                
        } catch (Exception e) {
            log.error("Failed to store risk event: {}", event.getEventId(), e);
            throw new RuntimeException("Event storage failed", e);
        }
    }
    
    /**
     * Retrieve events for a risk ID
     */
    public List<RiskEvent> getEvents(String riskId) {
        return eventStore.getOrDefault(riskId, new CopyOnWriteArrayList<>());
    }
    
    /**
     * Retrieve events by type
     */
    public List<RiskEvent> getEventsByType(String riskId, String eventType) {
        return getEvents(riskId).stream()
            .filter(event -> eventType.equals(event.getEventType()))
            .toList();
    }
    
    /**
     * Retrieve events after timestamp
     */
    public List<RiskEvent> getEventsAfter(String riskId, Instant timestamp) {
        return getEvents(riskId).stream()
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