/**
 * File: ./payment-service/src/main/java/com/waqiti/payment/event/EventPublisher.java
 */
package com.waqiti.payment.event;

/**
 * Interface for publishing events to Kafka or other messaging systems.
 * This allows for easier mocking in tests.
 */
public interface EventPublisher {
    /**
     * Publishes an event to the appropriate topic
     */
    void publishEvent(Object event, String topic, String key);
}