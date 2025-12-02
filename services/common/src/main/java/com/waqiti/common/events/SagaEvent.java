package com.waqiti.common.events;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Event published during saga execution lifecycle
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class SagaEvent extends FinancialEvent {
    
    private String sagaId;
    private SagaEventType sagaEventType;
    private String message;
    private Map<String, Object> eventData;
    private Map<String, Object> previousData;
    private Map<String, Object> newData;
    
    @Override
    public String getEventType() {
        return sagaEventType != null ? sagaEventType.name() : super.getEventType();
    }
    
    public SagaEventType getSagaEventType() {
        return sagaEventType;
    }
    
    public void setEventType(SagaEventType eventType) {
        this.sagaEventType = eventType;
        super.setEventType(eventType != null ? eventType.name() : null);
    }
    
    public SagaEvent(String sagaId, SagaEventType eventType, String message, 
                     Map<String, Object> eventData, Instant timestamp) {
        super();
        this.sagaId = sagaId;
        this.sagaEventType = eventType;
        this.message = message;
        this.eventData = eventData;
        setTimestamp(timestamp);
        setEventId(java.util.UUID.randomUUID());
        setVersion(1);
        super.setEventType(eventType != null ? eventType.name() : null);
    }
    
    /**
     * Constructor that accepts string event type for backward compatibility
     */
    public SagaEvent(String sagaId, String eventTypeStr, String message, 
                     Map<String, Object> eventData, Instant timestamp) {
        super();
        this.sagaId = sagaId;
        // Convert string eventType to enum if possible, otherwise set to a default
        try {
            this.sagaEventType = SagaEventType.valueOf(eventTypeStr);
        } catch (IllegalArgumentException e) {
            this.sagaEventType = SagaEventType.SAGA_STEP_UPDATED; // Default fallback
        }
        this.message = message;
        this.eventData = eventData;
        setTimestamp(timestamp);
        setEventId(java.util.UUID.randomUUID());
        setVersion(1);
        super.setEventType(this.sagaEventType != null ? this.sagaEventType.name() : null);
    }
}