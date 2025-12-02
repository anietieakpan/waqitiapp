package com.waqiti.crypto.events.store;

import com.waqiti.crypto.events.model.CryptoEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event store interface for crypto events
 * Enables event sourcing and replay capability
 */
public interface CryptoEventStore {
    
    /**
     * Stores event for replay capability
     */
    void storeEvent(CryptoEvent event);
    
    /**
     * Marks event as successfully published
     */
    void markEventPublished(String eventId);
    
    /**
     * Retrieves events for replay
     */
    List<CryptoEvent> getEvents(LocalDateTime from, LocalDateTime to);
    
    /**
     * Retrieves failed events for retry
     */
    List<CryptoEvent> getFailedEvents();
    
    /**
     * Get events for a specific crypto wallet
     */
    List<CryptoEvent> getEventsForCryptoWallet(String cryptoWalletId);
    
    /**
     * Get events by correlation ID
     */
    List<CryptoEvent> getEventsByCorrelationId(String correlationId);
    
    /**
     * Get events by event type
     */
    List<CryptoEvent> getEventsByType(String eventType);
    
    /**
     * Mark event as failed
     */
    void markEventAsFailed(String eventId, String errorMessage);
    
    /**
     * Mark event as processed
     */
    void markEventAsProcessed(String eventId);
}