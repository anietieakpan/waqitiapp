package com.waqiti.discovery.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Event Publisher
 * Publishes events to Kafka (stub implementation)
 *
 * @author Discovery Service Team
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EventPublisher {

    /**
     * Publish an event
     *
     * @param event event to publish
     */
    public void publish(ServiceDiscoveryEvent event) {
        log.info("Publishing event: {}", event);
        // TODO: Implement Kafka publishing
    }
}
