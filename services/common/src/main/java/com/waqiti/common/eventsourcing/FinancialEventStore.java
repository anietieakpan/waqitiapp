package com.waqiti.common.eventsourcing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Event store for financial transaction event sourcing
 * Provides ACID guarantees and immutable event storage
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinancialEventStore {
    
    private final FinancialEventRepository eventRepository;
    private final EventSnapshotRepository snapshotRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final FinancialEventSerializer eventSerializer;
    
    @Value("${eventsourcing.snapshot.frequency:50}")
    private int snapshotFrequency;
    
    @Value("${eventsourcing.cache.ttl:3600}")
    private int cacheTtlSeconds;
    
    @Value("${eventsourcing.batch.size:1000}")
    private int batchSize;
    
    /**
     * Append new event to the event stream
     */
    @Transactional
    public EventStoreResult appendEvent(FinancialEvent event) {
        try {
            // Validate event
            validateEvent(event);
            
            // Check for concurrency conflicts
            checkConcurrency(event);
            
            // Persist event
            FinancialEventEntity eventEntity = FinancialEventEntity.builder()
                .aggregateId(event.getAggregateId())
                .aggregateType(event.getAggregateType())
                .eventType(event.getEventType())
                .eventData(eventSerializer.serialize(event))
                .eventVersion(event.getVersion())
                .sequenceNumber(getNextSequenceNumber(event.getAggregateId()))
                .timestamp(event.getTimestamp())
                .userId(event.getUserId())
                .correlationId(event.getCorrelationId())
                .causationId(event.getCausationId())
                .build();
            
            eventEntity = eventRepository.save(eventEntity);
            
            // Update cache
            invalidateCache(event.getAggregateId());
            
            // Check if snapshot needed
            if (shouldCreateSnapshot(event.getAggregateId())) {
                createSnapshot(event.getAggregateId());
            }
            
            log.debug("Event appended successfully: {}", eventEntity.getId());
            
            return EventStoreResult.success(eventEntity.getId(), eventEntity.getSequenceNumber());
            
        } catch (Exception e) {
            log.error("Failed to append event for aggregate {}", event.getAggregateId(), e);
            return EventStoreResult.failure(e.getMessage());
        }
    }
    
    /**
     * Append multiple events atomically
     */
    @Transactional
    public BatchEventStoreResult appendEvents(List<FinancialEvent> events) {
        if (events.isEmpty()) {
            return BatchEventStoreResult.success(Collections.emptyList());
        }
        
        try {
            // Group events by aggregate to ensure ordering
            Map<String, List<FinancialEvent>> eventsByAggregate = events.stream()
                .collect(Collectors.groupingBy(FinancialEvent::getAggregateId));
            
            List<EventStoreResult> results = new ArrayList<>();
            Set<String> affectedAggregates = new HashSet<>();
            
            // Process events by aggregate to maintain ordering
            for (Map.Entry<String, List<FinancialEvent>> entry : eventsByAggregate.entrySet()) {
                String aggregateId = entry.getKey();
                List<FinancialEvent> aggregateEvents = entry.getValue();
                
                // Sort events by expected sequence
                aggregateEvents.sort(Comparator.comparing(FinancialEvent::getVersion));
                
                for (FinancialEvent event : aggregateEvents) {
                    EventStoreResult result = appendEvent(event);
                    results.add(result);
                    
                    if (!result.isSuccess()) {
                        throw new EventStoreException("Failed to append event in batch: " + result.getErrorMessage());
                    }
                }
                
                affectedAggregates.add(aggregateId);
            }
            
            // Invalidate caches for all affected aggregates
            affectedAggregates.forEach(this::invalidateCache);
            
            return BatchEventStoreResult.success(results);
            
        } catch (Exception e) {
            log.error("Failed to append event batch", e);
            return BatchEventStoreResult.failure(e.getMessage());
        }
    }
    
    /**
     * Get events for an aggregate from a specific version
     */
    public List<FinancialEvent> getEventsFromVersion(String aggregateId, long fromVersion) {
        try {
            // Try cache first for recent events
            String cacheKey = buildCacheKey(aggregateId, fromVersion);
            List<FinancialEvent> cachedEvents = getCachedEvents(cacheKey);
            if (cachedEvents != null) {
                log.debug("Retrieved {} events from cache for aggregate {}", cachedEvents.size(), aggregateId);
                return cachedEvents;
            }
            
            // Load from database
            List<FinancialEventEntity> eventEntities = eventRepository
                .findByAggregateIdAndEventVersionGreaterThanEqualOrderBySequenceNumber(aggregateId, fromVersion);
            
            List<FinancialEvent> events = eventEntities.stream()
                .map(this::deserializeEvent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
            // Cache for future use
            cacheEvents(cacheKey, events);
            
            log.debug("Retrieved {} events from store for aggregate {}", events.size(), aggregateId);
            return events;
            
        } catch (Exception e) {
            log.error("Failed to get events for aggregate {} from version {}", aggregateId, fromVersion, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Get all events for an aggregate
     */
    public List<FinancialEvent> getAllEvents(String aggregateId) {
        return getEventsFromVersion(aggregateId, 0);
    }
    
    /**
     * Get events within a time range
     */
    public List<FinancialEvent> getEventsByTimeRange(Instant fromTime, Instant toTime, int limit) {
        try {
            List<FinancialEventEntity> eventEntities = eventRepository
                .findByTimestampBetweenOrderByTimestampAsc(fromTime, toTime, 
                    org.springframework.data.domain.PageRequest.of(0, limit));
            
            return eventEntities.stream()
                .map(this::deserializeEvent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to get events by time range {} to {}", fromTime, toTime, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Get events by event type
     */
    public List<FinancialEvent> getEventsByType(String eventType, int limit) {
        try {
            List<FinancialEventEntity> eventEntities = eventRepository
                .findByEventTypeOrderByTimestampDesc(eventType, 
                    org.springframework.data.domain.PageRequest.of(0, limit));
            
            return eventEntities.stream()
                .map(this::deserializeEvent)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Failed to get events by type {}", eventType, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Get latest snapshot for aggregate
     */
    public Optional<AggregateSnapshot> getLatestSnapshot(String aggregateId) {
        try {
            return snapshotRepository.findTopByAggregateIdOrderByVersionDesc(aggregateId)
                .map(this::deserializeSnapshot);
                
        } catch (Exception e) {
            log.error("Failed to get snapshot for aggregate {}", aggregateId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Create snapshot of current aggregate state
     */
    @Transactional
    public void createSnapshot(String aggregateId) {
        try {
            // Get all events for aggregate
            List<FinancialEvent> events = getAllEvents(aggregateId);
            
            if (events.isEmpty()) {
                log.debug("No events found for aggregate {}, skipping snapshot", aggregateId);
                return;
            }
            
            // Rebuild aggregate state from events
            FinancialAggregate aggregate = rebuildAggregateFromEvents(events);
            
            // Create snapshot
            EventSnapshotEntity snapshotEntity = EventSnapshotEntity.builder()
                .aggregateId(aggregateId)
                .aggregateType(aggregate.getAggregateType())
                .version(aggregate.getVersion())
                .snapshotData(eventSerializer.serializeAggregate(aggregate))
                .timestamp(Instant.now())
                .eventCount((long) events.size())
                .build();
            
            snapshotRepository.save(snapshotEntity);
            
            log.debug("Created snapshot for aggregate {} at version {}", aggregateId, aggregate.getVersion());
            
        } catch (Exception e) {
            log.error("Failed to create snapshot for aggregate {}", aggregateId, e);
        }
    }
    
    /**
     * Rebuild aggregate from events and optional snapshot
     */
    public FinancialAggregate rebuildAggregate(String aggregateId) {
        try {
            // Try to get latest snapshot
            Optional<AggregateSnapshot> snapshot = getLatestSnapshot(aggregateId);
            
            FinancialAggregate aggregate;
            long fromVersion = 0;
            
            if (snapshot.isPresent()) {
                // Start from snapshot
                aggregate = eventSerializer.deserializeAggregate(snapshot.get().getSnapshotData());
                fromVersion = snapshot.get().getVersion() + 1;
                log.debug("Starting rebuild from snapshot at version {} for aggregate {}", 
                    snapshot.get().getVersion(), aggregateId);
            } else {
                // Start from empty aggregate
                aggregate = createEmptyAggregate(aggregateId);
            }
            
            // Apply events after snapshot
            List<FinancialEvent> events = getEventsFromVersion(aggregateId, fromVersion);
            
            for (FinancialEvent event : events) {
                aggregate = aggregate.applyEvent(event);
            }
            
            log.debug("Rebuilt aggregate {} with {} events, final version {}", 
                aggregateId, events.size(), aggregate.getVersion());
            
            return aggregate;
            
        } catch (Exception e) {
            log.error("Failed to rebuild aggregate {}", aggregateId, e);
            throw new EventStoreException("Failed to rebuild aggregate: " + e.getMessage());
        }
    }
    
    /**
     * Get event store statistics
     */
    public EventStoreStatistics getStatistics() {
        try {
            long totalEvents = eventRepository.count();
            long totalSnapshots = snapshotRepository.count();
            long totalAggregates = eventRepository.countDistinctAggregateIds();
            
            return EventStoreStatistics.builder()
                .totalEvents(totalEvents)
                .totalSnapshots(totalSnapshots)
                .totalAggregates(totalAggregates)
                .snapshotFrequency(snapshotFrequency)
                .cacheHitRatio(calculateCacheHitRatio())
                .lastUpdated(Instant.now())
                .build();
                
        } catch (Exception e) {
            log.error("Failed to get event store statistics", e);
            return EventStoreStatistics.error("Failed to get statistics: " + e.getMessage());
        }
    }
    
    // Private helper methods
    
    private void validateEvent(FinancialEvent event) {
        if (event.getAggregateId() == null || event.getAggregateId().isEmpty()) {
            throw new IllegalArgumentException("Aggregate ID is required");
        }
        if (event.getEventType() == null || event.getEventType().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Event timestamp is required");
        }
    }
    
    private void checkConcurrency(FinancialEvent event) {
        // Get current version from database
        Long currentVersion = eventRepository.findMaxVersionByAggregateId(event.getAggregateId());
        
        if (currentVersion != null && event.getVersion() <= currentVersion) {
            throw new ConcurrencyException(
                String.format("Concurrency conflict for aggregate %s: expected version > %d, got %d", 
                    event.getAggregateId(), currentVersion, event.getVersion()));
        }
    }
    
    private long getNextSequenceNumber(String aggregateId) {
        Long maxSequence = eventRepository.findMaxSequenceNumberByAggregateId(aggregateId);
        return maxSequence != null ? maxSequence + 1 : 1;
    }
    
    private boolean shouldCreateSnapshot(String aggregateId) {
        long eventCount = eventRepository.countByAggregateId(aggregateId);
        Optional<AggregateSnapshot> lastSnapshot = getLatestSnapshot(aggregateId);
        
        if (lastSnapshot.isEmpty()) {
            return eventCount >= snapshotFrequency;
        }
        
        long eventsSinceSnapshot = eventCount - lastSnapshot.get().getEventCount();
        return eventsSinceSnapshot >= snapshotFrequency;
    }
    
    private void invalidateCache(String aggregateId) {
        try {
            String pattern = buildCacheKeyPattern(aggregateId);
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("Failed to invalidate cache for aggregate {}", aggregateId, e);
        }
    }
    
    private String buildCacheKey(String aggregateId, long fromVersion) {
        return String.format("events:%s:%d", aggregateId, fromVersion);
    }
    
    private String buildCacheKeyPattern(String aggregateId) {
        return String.format("events:%s:*", aggregateId);
    }
    
    private List<FinancialEvent> getCachedEvents(String cacheKey) {
        try {
            String cachedData = redisTemplate.opsForValue().get(cacheKey);
            if (cachedData != null) {
                return eventSerializer.deserializeEventList(cachedData);
            }
        } catch (Exception e) {
            log.debug("Failed to get cached events for key {}", cacheKey, e);
        }
        return null;
    }
    
    private void cacheEvents(String cacheKey, List<FinancialEvent> events) {
        try {
            String serializedEvents = eventSerializer.serializeEventList(events);
            redisTemplate.opsForValue().set(cacheKey, serializedEvents, 
                java.time.Duration.ofSeconds(cacheTtlSeconds));
        } catch (Exception e) {
            log.debug("Failed to cache events for key {}", cacheKey, e);
        }
    }
    
    private FinancialEvent deserializeEvent(FinancialEventEntity entity) {
        try {
            return eventSerializer.deserialize(entity.getEventData(), entity.getEventType());
        } catch (Exception e) {
            log.error("CRITICAL: Failed to deserialize financial event {} - Event sourcing integrity compromised", entity.getId(), e);
            throw new RuntimeException("Failed to deserialize financial event: " + entity.getId(), e);
        }
    }
    
    private AggregateSnapshot deserializeSnapshot(EventSnapshotEntity entity) {
        try {
            return AggregateSnapshot.builder()
                .aggregateId(entity.getAggregateId())
                .version(entity.getVersion())
                .snapshotData(entity.getSnapshotData())
                .timestamp(entity.getTimestamp())
                .eventCount(entity.getEventCount())
                .build();
        } catch (Exception e) {
            log.error("CRITICAL: Failed to deserialize financial snapshot {} - Event sourcing integrity compromised", entity.getId(), e);
            throw new RuntimeException("Failed to deserialize financial snapshot: " + entity.getId(), e);
        }
    }
    
    private FinancialAggregate rebuildAggregateFromEvents(List<FinancialEvent> events) {
        if (events.isEmpty()) {
            throw new IllegalArgumentException("Cannot rebuild aggregate from empty event list");
        }
        
        String aggregateId = events.get(0).getAggregateId();
        FinancialAggregate aggregate = createEmptyAggregate(aggregateId);
        
        for (FinancialEvent event : events) {
            aggregate = aggregate.applyEvent(event);
        }
        
        return aggregate;
    }
    
    private FinancialAggregate createEmptyAggregate(String aggregateId) {
        // This would be implemented based on the specific aggregate type
        return new FinancialTransactionAggregate(aggregateId);
    }
    
    private double calculateCacheHitRatio() {
        // This would track cache hits/misses and calculate ratio
        return 0.0; // Placeholder
    }
}