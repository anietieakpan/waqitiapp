package com.waqiti.common.eventsourcing;

import java.util.List;

/**
 * Interface for serializing/deserializing financial events
 */
public interface FinancialEventSerializer {
    
    /**
     * Serialize event to JSON string
     */
    String serialize(FinancialEvent event);
    
    /**
     * Deserialize event from JSON string
     */
    FinancialEvent deserialize(String eventData, String eventType);
    
    /**
     * Serialize list of events
     */
    String serializeEventList(List<FinancialEvent> events);
    
    /**
     * Deserialize list of events
     */
    List<FinancialEvent> deserializeEventList(String eventsData);
    
    /**
     * Serialize aggregate to JSON string
     */
    String serializeAggregate(FinancialAggregate aggregate);
    
    /**
     * Deserialize aggregate from JSON string
     */
    FinancialAggregate deserializeAggregate(String aggregateData);
}