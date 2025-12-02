package com.waqiti.monitoring.events.store;

import com.waqiti.monitoring.events.model.MonitoringEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Monitoring event store for durability and event sourcing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MonitoringEventStore {
    
    private final ConcurrentHashMap<String, List<MonitoringEvent>> eventStore = new ConcurrentHashMap<>();
    
    /**
     * Store monitoring event for durability
     */
    public void storeEvent(MonitoringEvent event) {
        try {
            String key = event.getMonitoringId() != null ? event.getMonitoringId() : "global";
            
            eventStore.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(event);
            
            log.debug("Stored monitoring event: type={}, monitoringId={}, eventId={}", 
                event.getEventType(), event.getMonitoringId(), event.getEventId());
                
        } catch (Exception e) {
            log.error("Failed to store monitoring event: {}", event.getEventId(), e);
            throw new RuntimeException("Event storage failed", e);
        }
    }
    
    /**
     * Retrieve events for a monitoring ID
     */
    public List<MonitoringEvent> getEvents(String monitoringId) {
        return eventStore.getOrDefault(monitoringId, new CopyOnWriteArrayList<>());
    }
    
    /**
     * Retrieve events by type
     */
    public List<MonitoringEvent> getEventsByType(String monitoringId, String eventType) {
        return getEvents(monitoringId).stream()
            .filter(event -> eventType.equals(event.getEventType()))
            .toList();
    }
    
    /**
     * Retrieve events after timestamp
     */
    public List<MonitoringEvent> getEventsAfter(String monitoringId, Instant timestamp) {
        return getEvents(monitoringId).stream()
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