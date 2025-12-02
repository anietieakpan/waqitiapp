package com.waqiti.atm.events.store;

import com.waqiti.atm.events.model.ATMEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event store interface for ATM events
 * Enables event sourcing and replay capability
 */
public interface ATMEventStore {
    
    /**
     * Stores event for replay capability
     */
    void storeEvent(ATMEvent event);
    
    /**
     * Marks event as successfully published
     */
    void markEventPublished(String eventId);
    
    /**
     * Retrieves events for replay
     */
    List<ATMEvent> getEvents(LocalDateTime from, LocalDateTime to);
    
    /**
     * Retrieves failed events for retry
     */
    List<ATMEvent> getFailedEvents();
    
    /**
     * Get events for a specific ATM
     */
    List<ATMEvent> getEventsForATM(String atmId);
    
    /**
     * Get events by correlation ID
     */
    List<ATMEvent> getEventsByCorrelationId(String correlationId);
    
    /**
     * Get events by event type
     */
    List<ATMEvent> getEventsByType(String eventType);
    
    /**
     * Mark event as failed
     */
    void markEventAsFailed(String eventId, String errorMessage);
    
    /**
     * Mark event as processed
     */
    void markEventAsProcessed(String eventId);
}