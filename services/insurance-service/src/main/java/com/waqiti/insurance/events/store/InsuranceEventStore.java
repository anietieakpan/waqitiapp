package com.waqiti.insurance.events.store;

import com.waqiti.insurance.events.model.InsuranceEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event store interface for insurance events
 * Enables event sourcing and replay capability
 */
public interface InsuranceEventStore {
    
    /**
     * Stores event for replay capability
     */
    void storeEvent(InsuranceEvent event);
    
    /**
     * Marks event as successfully published
     */
    void markEventPublished(String eventId);
    
    /**
     * Retrieves events for replay
     */
    List<InsuranceEvent> getEvents(LocalDateTime from, LocalDateTime to);
    
    /**
     * Retrieves failed events for retry
     */
    List<InsuranceEvent> getFailedEvents();
}