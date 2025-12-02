package com.waqiti.common.eventsourcing;

/**
 * Base interface for financial aggregates in event sourcing
 */
public interface FinancialAggregate {
    
    /**
     * Get the aggregate ID
     */
    String getAggregateId();
    
    /**
     * Get the aggregate type
     */
    String getAggregateType();
    
    /**
     * Get the current version
     */
    Long getVersion();
    
    /**
     * Apply an event to the aggregate and return new state
     */
    FinancialAggregate applyEvent(FinancialEvent event);
    
    /**
     * Get uncommitted events
     */
    java.util.List<FinancialEvent> getUncommittedEvents();
    
    /**
     * Mark events as committed
     */
    void markEventsAsCommitted();
}