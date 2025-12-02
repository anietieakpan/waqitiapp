package com.waqiti.savings.events.store;

import com.waqiti.savings.events.model.SavingsEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event store interface for savings events
 * Enables event sourcing and replay capability
 */
public interface SavingsEventStore {
    
    /**
     * Stores event for replay capability
     */
    void storeEvent(SavingsEvent event);
    
    /**
     * Marks event as successfully published
     */
    void markEventPublished(String eventId);
    
    /**
     * Retrieves events for replay
     */
    List<SavingsEvent> getEvents(LocalDateTime from, LocalDateTime to);
    
    /**
     * Retrieves failed events for retry
     */
    List<SavingsEvent> getFailedEvents();
}