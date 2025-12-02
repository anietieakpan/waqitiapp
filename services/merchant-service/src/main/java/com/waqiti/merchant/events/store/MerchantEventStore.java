package com.waqiti.merchant.events.store;

import com.waqiti.merchant.events.model.MerchantEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event store interface for merchant events
 * Enables event sourcing and replay capability
 */
public interface MerchantEventStore {
    
    /**
     * Stores event for replay capability
     */
    void storeEvent(MerchantEvent event);
    
    /**
     * Marks event as successfully published
     */
    void markEventPublished(String eventId);
    
    /**
     * Retrieves events for replay
     */
    List<MerchantEvent> getEvents(LocalDateTime from, LocalDateTime to);
    
    /**
     * Retrieves failed events for retry
     */
    List<MerchantEvent> getFailedEvents();
}