package com.waqiti.common.eventsourcing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Jackson-based implementation of financial event serializer
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JacksonFinancialEventSerializer implements FinancialEventSerializer {
    
    private final ObjectMapper objectMapper;
    
    @Override
    public String serialize(FinancialEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new EventStoreException("Failed to serialize event: " + e.getMessage(), e);
        }
    }
    
    @Override
    public FinancialEvent deserialize(String eventData, String eventType) {
        try {
            return objectMapper.readValue(eventData, FinancialEvent.class);
        } catch (Exception e) {
            throw new EventStoreException("Failed to deserialize event of type " + eventType + ": " + e.getMessage(), e);
        }
    }
    
    @Override
    public String serializeEventList(List<FinancialEvent> events) {
        try {
            return objectMapper.writeValueAsString(events);
        } catch (Exception e) {
            throw new EventStoreException("Failed to serialize event list: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<FinancialEvent> deserializeEventList(String eventsData) {
        try {
            return objectMapper.readValue(eventsData, new TypeReference<List<FinancialEvent>>() {});
        } catch (Exception e) {
            throw new EventStoreException("Failed to deserialize event list: " + e.getMessage(), e);
        }
    }
    
    @Override
    public String serializeAggregate(FinancialAggregate aggregate) {
        try {
            return objectMapper.writeValueAsString(aggregate);
        } catch (Exception e) {
            throw new EventStoreException("Failed to serialize aggregate: " + e.getMessage(), e);
        }
    }
    
    @Override
    public FinancialAggregate deserializeAggregate(String aggregateData) {
        try {
            return objectMapper.readValue(aggregateData, FinancialTransactionAggregate.class);
        } catch (Exception e) {
            throw new EventStoreException("Failed to deserialize aggregate: " + e.getMessage(), e);
        }
    }
}