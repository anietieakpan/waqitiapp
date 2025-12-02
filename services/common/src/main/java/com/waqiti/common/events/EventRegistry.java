package com.waqiti.common.events;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CRITICAL EVENT-DRIVEN ARCHITECTURE COMPONENT: Event Registry
 *
 * PURPOSE:
 * Central registry tracking all Kafka events, producers, and consumers.
 * Identifies orphaned events (no consumers) and missing event handlers.
 *
 * ISSUE ADDRESSED:
 * Analysis identified orphaned Kafka messages with no corresponding consumers,
 * leading to data inconsistencies and broken business flows.
 *
 * SOLUTION:
 * - Auto-discovery of event producers and consumers
 * - Validation that every event has at least one consumer
 * - Detection of orphaned events at application startup
 * - Runtime monitoring of event processing
 *
 * @author Waqiti Event Architecture Team
 * @since 2025-10-31
 * @version 1.0.0
 */
@Slf4j
@Component
public class EventRegistry {

    // Event type -> List of producer services
    private final Map<String, Set<String>> eventProducers = new ConcurrentHashMap<>();

    // Event type -> List of consumer services
    private final Map<String, Set<String>> eventConsumers = new ConcurrentHashMap<>();

    // Topic -> Event types published to this topic
    private final Map<String, Set<String>> topicToEvents = new ConcurrentHashMap<>();

    // Service -> Topics it consumes from
    private final Map<String, Set<String>> serviceToTopics = new ConcurrentHashMap<>();

    /**
     * Registers an event producer.
     *
     * @param eventType event type (e.g., "UserRegisteredEvent")
     * @param producerService producing service name
     * @param topicName Kafka topic name
     */
    public void registerProducer(String eventType, String producerService, String topicName) {
        eventProducers.computeIfAbsent(eventType, k -> ConcurrentHashMap.newKeySet())
            .add(producerService);

        topicToEvents.computeIfAbsent(topicName, k -> ConcurrentHashMap.newKeySet())
            .add(eventType);

        log.debug("Registered producer: {} produces {} to topic {}",
            producerService, eventType, topicName);
    }

    /**
     * Registers an event consumer.
     *
     * @param eventType event type being consumed
     * @param consumerService consuming service name
     * @param topicName Kafka topic name
     */
    public void registerConsumer(String eventType, String consumerService, String topicName) {
        eventConsumers.computeIfAbsent(eventType, k -> ConcurrentHashMap.newKeySet())
            .add(consumerService);

        serviceToTopics.computeIfAbsent(consumerService, k -> ConcurrentHashMap.newKeySet())
            .add(topicName);

        log.debug("Registered consumer: {} consumes {} from topic {}",
            consumerService, eventType, topicName);
    }

    /**
     * Validates event-driven architecture at startup.
     * Identifies orphaned events and missing consumers.
     *
     * @throws OrphanedEventException if critical events have no consumers
     */
    @PostConstruct
    public void validateEventArchitecture() {
        log.info("Validating event-driven architecture...");

        List<OrphanedEvent> orphanedEvents = findOrphanedEvents();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (OrphanedEvent orphaned : orphanedEvents) {
            String message = String.format(
                "ORPHANED EVENT: %s produced by %s has NO CONSUMERS. " +
                "Business flow may be broken. Topic: %s",
                orphaned.eventType,
                String.join(", ", orphaned.producers),
                orphaned.topicName
            );

            if (orphaned.isCritical()) {
                errors.add(message);
                log.error(message);
            } else {
                warnings.add(message);
                log.warn(message);
            }
        }

        // Log summary
        log.info("Event Architecture Validation Summary:");
        log.info("- Total event types: {}", eventProducers.size());
        log.info("- Events with consumers: {}", eventConsumers.size());
        log.info("- Orphaned events: {}", orphanedEvents.size());
        log.info("- Critical orphaned events: {}",
            orphanedEvents.stream().filter(OrphanedEvent::isCritical).count());

        // Fail fast if critical events are orphaned
        if (!errors.isEmpty()) {
            throw new OrphanedEventException(
                "Critical event-driven architecture violations detected:\n" +
                String.join("\n", errors)
            );
        }

        if (!warnings.isEmpty()) {
            log.warn("Event-driven architecture warnings:\n{}", String.join("\n", warnings));
        }
    }

    /**
     * Finds all orphaned events (events with producers but no consumers).
     *
     * @return list of orphaned events
     */
    public List<OrphanedEvent> findOrphanedEvents() {
        List<OrphanedEvent> orphaned = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : eventProducers.entrySet()) {
            String eventType = entry.getKey();
            Set<String> producers = entry.getValue();
            Set<String> consumers = eventConsumers.getOrDefault(eventType, Collections.emptySet());

            if (consumers.isEmpty()) {
                // Find topic for this event
                String topicName = findTopicForEvent(eventType);

                orphaned.add(new OrphanedEvent(
                    eventType,
                    producers,
                    topicName,
                    isCriticalEvent(eventType)
                ));
            }
        }

        return orphaned;
    }

    /**
     * Checks if event has consumers.
     *
     * @param eventType event type to check
     * @return true if event has at least one consumer
     */
    public boolean hasConsumers(String eventType) {
        Set<String> consumers = eventConsumers.get(eventType);
        return consumers != null && !consumers.isEmpty();
    }

    /**
     * Gets all consumers for an event type.
     *
     * @param eventType event type
     * @return set of consumer service names
     */
    public Set<String> getConsumers(String eventType) {
        return eventConsumers.getOrDefault(eventType, Collections.emptySet());
    }

    /**
     * Gets all producers for an event type.
     *
     * @param eventType event type
     * @return set of producer service names
     */
    public Set<String> getProducers(String eventType) {
        return eventProducers.getOrDefault(eventType, Collections.emptySet());
    }

    /**
     * Generates event flow documentation.
     *
     * @return markdown documentation of event flows
     */
    public String generateEventFlowDocumentation() {
        StringBuilder doc = new StringBuilder();
        doc.append("# Event-Driven Architecture Documentation\n\n");
        doc.append("Generated: ").append(new Date()).append("\n\n");

        doc.append("## Event Flows\n\n");

        List<String> sortedEvents = new ArrayList<>(eventProducers.keySet());
        Collections.sort(sortedEvents);

        for (String eventType : sortedEvents) {
            Set<String> producers = eventProducers.get(eventType);
            Set<String> consumers = eventConsumers.getOrDefault(eventType, Collections.emptySet());
            String topicName = findTopicForEvent(eventType);

            doc.append("### ").append(eventType).append("\n\n");
            doc.append("**Topic:** `").append(topicName).append("`\n\n");

            doc.append("**Producers:**\n");
            for (String producer : producers) {
                doc.append("- ").append(producer).append("\n");
            }
            doc.append("\n");

            doc.append("**Consumers:**\n");
            if (consumers.isEmpty()) {
                doc.append("- ⚠️ **NO CONSUMERS** (ORPHANED EVENT)\n");
            } else {
                for (String consumer : consumers) {
                    doc.append("- ").append(consumer).append("\n");
                }
            }
            doc.append("\n---\n\n");
        }

        return doc.toString();
    }

    /**
     * Finds topic name for given event type.
     *
     * @param eventType event type
     * @return topic name or "unknown"
     */
    private String findTopicForEvent(String eventType) {
        return topicToEvents.entrySet().stream()
            .filter(entry -> entry.getValue().contains(eventType))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("unknown");
    }

    /**
     * Determines if event is critical (failure would break core business flows).
     *
     * @param eventType event type
     * @return true if critical
     */
    private boolean isCriticalEvent(String eventType) {
        // Critical financial events
        if (eventType.contains("Transaction") || eventType.contains("Payment") ||
            eventType.contains("Transfer") || eventType.contains("Refund") ||
            eventType.contains("Settlement")) {
            return true;
        }

        // Critical user events
        if (eventType.contains("UserRegistered") || eventType.contains("UserDeleted")) {
            return true;
        }

        // Critical wallet events
        if (eventType.contains("WalletCreated") || eventType.contains("BalanceUpdated")) {
            return true;
        }

        return false;
    }

    /**
     * Orphaned event details.
     */
    public static class OrphanedEvent {
        public final String eventType;
        public final Set<String> producers;
        public final String topicName;
        public final boolean critical;

        public OrphanedEvent(String eventType, Set<String> producers,
                            String topicName, boolean critical) {
            this.eventType = eventType;
            this.producers = producers;
            this.topicName = topicName;
            this.critical = critical;
        }

        public boolean isCritical() {
            return critical;
        }
    }

    /**
     * Exception thrown when critical events are orphaned.
     */
    public static class OrphanedEventException extends RuntimeException {
        public OrphanedEventException(String message) {
            super(message);
        }
    }
}
