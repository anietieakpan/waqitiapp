package com.waqiti.lending.events.store;

import com.waqiti.lending.events.model.LendingEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lending event store for durability and event sourcing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LendingEventStore {
    
    private final ConcurrentHashMap<String, List<LendingEvent>> eventStore = new ConcurrentHashMap<>();
    
    /**
     * Store lending event for durability
     */
    public void storeEvent(LendingEvent event) {
        try {
            String key = event.getLoanId() != null ? event.getLoanId() : "global";
            
            eventStore.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(event);
            
            log.debug("Stored lending event: type={}, loanId={}, eventId={}", 
                event.getEventType(), event.getLoanId(), event.getEventId());
                
        } catch (Exception e) {
            log.error("Failed to store lending event: {}", event.getEventId(), e);
            throw new RuntimeException("Event storage failed", e);
        }
    }
    
    /**
     * Retrieve events for a loan ID
     */
    public List<LendingEvent> getEvents(String loanId) {
        return eventStore.getOrDefault(loanId, new CopyOnWriteArrayList<>());
    }
    
    /**
     * Retrieve events by type
     */
    public List<LendingEvent> getEventsByType(String loanId, String eventType) {
        return getEvents(loanId).stream()
            .filter(event -> eventType.equals(event.getEventType()))
            .toList();
    }
    
    /**
     * Retrieve events after timestamp
     */
    public List<LendingEvent> getEventsAfter(String loanId, Instant timestamp) {
        return getEvents(loanId).stream()
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