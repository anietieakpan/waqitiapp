package com.waqiti.card.events.store;

import com.waqiti.card.events.model.CardEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Card event store for durability and event sourcing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardEventStore {
    
    private final ConcurrentHashMap<String, List<CardEvent>> eventStore = new ConcurrentHashMap<>();
    
    /**
     * Store card event for durability
     */
    public void storeEvent(CardEvent event) {
        try {
            String key = event.getCardId() != null ? event.getCardId() : "global";
            
            eventStore.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(event);
            
            log.debug("Stored card event: type={}, cardId={}, eventId={}", 
                event.getEventType(), event.getCardId(), event.getEventId());
                
        } catch (Exception e) {
            log.error("Failed to store card event: {}", event.getEventId(), e);
            throw new RuntimeException("Event storage failed", e);
        }
    }
    
    /**
     * Retrieve events for a card ID
     */
    public List<CardEvent> getEvents(String cardId) {
        return eventStore.getOrDefault(cardId, new CopyOnWriteArrayList<>());
    }
    
    /**
     * Retrieve events by type
     */
    public List<CardEvent> getEventsByType(String cardId, String eventType) {
        return getEvents(cardId).stream()
            .filter(event -> eventType.equals(event.getEventType()))
            .toList();
    }
    
    /**
     * Retrieve events after timestamp
     */
    public List<CardEvent> getEventsAfter(String cardId, Instant timestamp) {
        return getEvents(cardId).stream()
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