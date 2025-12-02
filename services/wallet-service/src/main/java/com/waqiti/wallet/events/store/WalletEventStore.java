package com.waqiti.wallet.events.store;

import com.waqiti.wallet.events.model.WalletEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Event store interface for wallet events
 * Enables event sourcing and replay capability
 */
public interface WalletEventStore {
    
    /**
     * Stores event for replay capability
     */
    void storeEvent(WalletEvent event);
    
    /**
     * Marks event as successfully published
     */
    void markEventPublished(String eventId);
    
    /**
     * Retrieves events for replay
     */
    List<WalletEvent> getEvents(LocalDateTime from, LocalDateTime to);
    
    /**
     * Retrieves failed events for retry
     */
    List<WalletEvent> getFailedEvents();
    
    /**
     * Get events for a specific wallet
     */
    List<WalletEvent> getEventsForWallet(String walletId);
    
    /**
     * Get events by correlation ID
     */
    List<WalletEvent> getEventsByCorrelationId(String correlationId);
    
    /**
     * Get events by event type
     */
    List<WalletEvent> getEventsByType(String eventType);
    
    /**
     * Mark event as failed
     */
    void markEventAsFailed(String eventId, String errorMessage);
    
    /**
     * Mark event as processed
     */
    void markEventAsProcessed(String eventId);
}