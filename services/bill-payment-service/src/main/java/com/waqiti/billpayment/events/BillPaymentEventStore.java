package com.waqiti.billpayment.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Bill Payment Event Store - Provides durability and replay capabilities
 *
 * Features:
 * - Event persistence for durability
 * - Event replay for failed publishes
 * - Event history for audit/debugging
 * - TTL-based automatic cleanup
 * - Query by event ID, correlation ID, user ID
 * - Support for event reconstruction
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillPaymentEventStore {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String EVENT_KEY_PREFIX = "bill-payment:event:";
    private static final String EVENT_BY_USER_PREFIX = "bill-payment:events:user:";
    private static final String EVENT_BY_CORRELATION_PREFIX = "bill-payment:events:correlation:";
    private static final long DEFAULT_TTL_DAYS = 30; // Store events for 30 days

    /**
     * Store a bill payment event
     */
    public void storeEvent(BillPaymentEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            String eventKey = EVENT_KEY_PREFIX + event.getEventId();

            // Store the event
            redisTemplate.opsForValue().set(eventKey, eventJson,
                Duration.ofDays(DEFAULT_TTL_DAYS));

            // Index by user ID
            if (event.getUserId() != null) {
                String userKey = EVENT_BY_USER_PREFIX + event.getUserId();
                redisTemplate.opsForList().rightPush(userKey, event.getEventId());
                redisTemplate.expire(userKey, DEFAULT_TTL_DAYS, TimeUnit.DAYS);
            }

            // Index by correlation ID
            if (event.getCorrelationId() != null) {
                String correlationKey = EVENT_BY_CORRELATION_PREFIX + event.getCorrelationId();
                redisTemplate.opsForList().rightPush(correlationKey, event.getEventId());
                redisTemplate.expire(correlationKey, DEFAULT_TTL_DAYS, TimeUnit.DAYS);
            }

            log.debug("Stored bill payment event: eventId={}, type={}",
                event.getEventId(), event.getEventType());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize bill payment event: eventId={}",
                event.getEventId(), e);
            throw new EventStoreException("Failed to store event", e);
        }
    }

    /**
     * Retrieve an event by ID
     */
    public Optional<BillPaymentEvent> getEvent(String eventId) {
        try {
            String eventKey = EVENT_KEY_PREFIX + eventId;
            String eventJson = redisTemplate.opsForValue().get(eventKey);

            if (eventJson == null) {
                return Optional.empty();
            }

            BillPaymentEvent event = objectMapper.readValue(eventJson, BillPaymentEvent.class);
            return Optional.of(event);

        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize bill payment event: eventId={}", eventId, e);
            return Optional.empty();
        }
    }

    /**
     * Get all events for a user
     */
    public List<BillPaymentEvent> getEventsByUserId(String userId) {
        String userKey = EVENT_BY_USER_PREFIX + userId;
        List<String> eventIds = redisTemplate.opsForList().range(userKey, 0, -1);

        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyList();
        }

        return eventIds.stream()
            .map(this::getEvent)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    /**
     * Get all events for a correlation ID (saga)
     */
    public List<BillPaymentEvent> getEventsByCorrelationId(String correlationId) {
        String correlationKey = EVENT_BY_CORRELATION_PREFIX + correlationId;
        List<String> eventIds = redisTemplate.opsForList().range(correlationKey, 0, -1);

        if (eventIds == null || eventIds.isEmpty()) {
            return Collections.emptyList();
        }

        return eventIds.stream()
            .map(this::getEvent)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    /**
     * Get recent events (last N events)
     */
    public List<BillPaymentEvent> getRecentEvents(int limit) {
        Set<String> keys = redisTemplate.keys(EVENT_KEY_PREFIX + "*");

        if (keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }

        return keys.stream()
            .limit(limit)
            .map(key -> key.replace(EVENT_KEY_PREFIX, ""))
            .map(this::getEvent)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .sorted(Comparator.comparing(BillPaymentEvent::getTimestamp).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Check if event exists
     */
    public boolean eventExists(String eventId) {
        String eventKey = EVENT_KEY_PREFIX + eventId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(eventKey));
    }

    /**
     * Delete an event
     */
    public void deleteEvent(String eventId) {
        String eventKey = EVENT_KEY_PREFIX + eventId;
        redisTemplate.delete(eventKey);
        log.debug("Deleted bill payment event: eventId={}", eventId);
    }

    /**
     * Get event store statistics
     */
    public EventStoreStatistics getStatistics() {
        Set<String> allEventKeys = redisTemplate.keys(EVENT_KEY_PREFIX + "*");
        long totalEvents = allEventKeys != null ? allEventKeys.size() : 0;

        Set<String> userKeys = redisTemplate.keys(EVENT_BY_USER_PREFIX + "*");
        long uniqueUsers = userKeys != null ? userKeys.size() : 0;

        Set<String> correlationKeys = redisTemplate.keys(EVENT_BY_CORRELATION_PREFIX + "*");
        long activeSagas = correlationKeys != null ? correlationKeys.size() : 0;

        return EventStoreStatistics.builder()
            .totalEvents(totalEvents)
            .uniqueUsers(uniqueUsers)
            .activeSagas(activeSagas)
            .timestamp(Instant.now())
            .build();
    }

    /**
     * Event Store Statistics
     */
    @lombok.Data
    @lombok.Builder
    public static class EventStoreStatistics {
        private long totalEvents;
        private long uniqueUsers;
        private long activeSagas;
        private Instant timestamp;
    }

    /**
     * Event Store Exception
     */
    public static class EventStoreException extends RuntimeException {
        public EventStoreException(String message) {
            super(message);
        }

        public EventStoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
