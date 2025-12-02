package com.waqiti.customer.events.store;

import com.waqiti.customer.events.model.CustomerEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event store interface for customer events
 * Enables event sourcing and replay capability
 */
public interface CustomerEventStore {
    
    /**
     * Stores event for replay capability
     */
    void storeEvent(CustomerEvent event);
    
    /**
     * Marks event as successfully published
     */
    void markEventPublished(String eventId);
    
    /**
     * Retrieves events for replay
     */
    List<CustomerEvent> getEvents(LocalDateTime from, LocalDateTime to);
    
    /**
     * Retrieves failed events for retry
     */
    List<CustomerEvent> getFailedEvents();
    
    /**
     * Get events for a specific customer
     */
    List<CustomerEvent> getEventsForCustomer(String customerId);
    
    /**
     * Get events by correlation ID
     */
    List<CustomerEvent> getEventsByCorrelationId(String correlationId);
    
    /**
     * Get events by event type
     */
    List<CustomerEvent> getEventsByType(String eventType);
    
    /**
     * Mark event as failed
     */
    void markEventAsFailed(String eventId, String errorMessage);
    
    /**
     * Mark event as processed
     */
    void markEventAsProcessed(String eventId);
}