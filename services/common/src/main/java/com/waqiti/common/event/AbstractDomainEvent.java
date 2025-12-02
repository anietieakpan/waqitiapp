package com.waqiti.common.event;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base implementation of a domain event
 */
public abstract class AbstractDomainEvent implements DomainEvent {
    private final String eventId;
    private final LocalDateTime timestamp;

    protected AbstractDomainEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    @Override
    public String getEventId() {
        return eventId;
    }

    @Override
    public LocalDateTime getTimestamp() {
        return timestamp;
    }


    @Override
    public String getEventType() {
        return this.getClass().getSimpleName();
    }

    @Override
    public abstract String getTopic();


}