package com.waqiti.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for ensuring idempotent event processing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

    private final Map<String, ProcessingRecord> processedEvents = new ConcurrentHashMap<>();

    public boolean isAlreadyProcessed(String eventId) {
        boolean processed = processedEvents.containsKey(eventId);
        if (processed) {
            log.info("Event {} already processed, skipping", eventId);
        }
        return processed;
    }

    public void markAsProcessed(String eventId, String eventType) {
        processedEvents.put(eventId, new ProcessingRecord(eventType, System.currentTimeMillis()));
        log.debug("Marked event {} as processed", eventId);
    }

    public void markAsProcessed(String eventId, String eventType, Duration ttl) {
        long expiryTime = System.currentTimeMillis() + ttl.toMillis();
        processedEvents.put(eventId, new ProcessingRecord(eventType, System.currentTimeMillis(), expiryTime));
        log.debug("Marked event {} as processed with TTL {}ms", eventId, ttl.toMillis());
    }

    public boolean checkAndMarkProcessed(String eventId, String eventType) {
        if (isAlreadyProcessed(eventId)) {
            return false;
        }
        markAsProcessed(eventId, eventType);
        return true;
    }

    public void cleanupExpiredRecords() {
        long currentTime = System.currentTimeMillis();
        int removed = 0;
        for (Map.Entry<String, ProcessingRecord> entry : processedEvents.entrySet()) {
            ProcessingRecord record = entry.getValue();
            if (record.expiryTime != null && record.expiryTime < currentTime) {
                processedEvents.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.info("Cleaned up {} expired idempotency records", removed);
        }
    }

    public void removeProcessingRecord(String eventId) {
        processedEvents.remove(eventId);
        log.debug("Removed processing record for event {}", eventId);
    }

    private static class ProcessingRecord {
        private final String eventType;
        private final long processedAt;
        private final Long expiryTime;

        public ProcessingRecord(String eventType, long processedAt) {
            this(eventType, processedAt, null);
        }

        public ProcessingRecord(String eventType, long processedAt, Long expiryTime) {
            this.eventType = eventType;
            this.processedAt = processedAt;
            this.expiryTime = expiryTime;
        }
    }
}
