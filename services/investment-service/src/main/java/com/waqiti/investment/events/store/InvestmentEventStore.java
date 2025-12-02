package com.waqiti.investment.events.store;

import com.waqiti.investment.events.model.InvestmentEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event store interface for investment events
 * Enables event sourcing and replay capability
 */
public interface InvestmentEventStore {
    
    /**
     * Stores event for replay capability
     */
    void storeEvent(InvestmentEvent event);
    
    /**
     * Marks event as successfully published
     */
    void markEventPublished(String eventId);
    
    /**
     * Retrieves events for replay
     */
    List<InvestmentEvent> getEvents(LocalDateTime from, LocalDateTime to);
    
    /**
     * Retrieves failed events for retry
     */
    List<InvestmentEvent> getFailedEvents();
}