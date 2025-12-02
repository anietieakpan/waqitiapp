package com.waqiti.dispute.exception;

/**
 * Exception thrown when Kafka event processing fails
 *
 * This exception is used in Kafka consumers to indicate event processing failures.
 * It wraps the underlying cause and provides context about the event processing.
 *
 * @author Waqiti Dispute Team
 */
public class KafkaEventProcessingException extends DisputeServiceException {

    private final String eventType;
    private final String eventId;

    public KafkaEventProcessingException(String eventType, String eventId, String message) {
        super(String.format("Failed to process %s event (ID: %s): %s", eventType, eventId, message),
                "KAFKA_EVENT_PROCESSING_ERROR", 500);
        this.eventType = eventType;
        this.eventId = eventId;
    }

    public KafkaEventProcessingException(String eventType, String eventId, String message, Throwable cause) {
        super(String.format("Failed to process %s event (ID: %s): %s", eventType, eventId, message),
                cause, "KAFKA_EVENT_PROCESSING_ERROR", 500);
        this.eventType = eventType;
        this.eventId = eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventId() {
        return eventId;
    }
}
