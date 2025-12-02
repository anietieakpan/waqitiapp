package com.waqiti.common.events;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event Gateway - Unified entry point for all events
 * Provides standardization, validation, routing, and monitoring
 * for events across the Waqiti platform
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventGateway {

    private final EventRegistryService eventRegistry;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    
    private final Map<String, EventTransformer> transformers = new ConcurrentHashMap<>();
    private final Map<String, EventEnricher> enrichers = new ConcurrentHashMap<>();
    private final AtomicLong eventCounter = new AtomicLong(0);

    /**
     * Publish event through the gateway with full processing pipeline
     */
    public CompletableFuture<EventPublishResult> publishEvent(EventPublishRequest request) {
        long startTime = System.currentTimeMillis();
        String eventId = generateEventId();
        
        log.debug("Publishing event through gateway: type={}, id={}", 
                request.getEventType(), eventId);
        
        return CompletableFuture
                .supplyAsync(() -> validateEvent(request))
                .thenCompose(validationResult -> {
                    if (!validationResult.isValid()) {
                        return CompletableFuture.completedFuture(
                                EventPublishResult.failure(eventId, 
                                        "Validation failed: " + String.join(", ", validationResult.getErrors())));
                    }
                    
                    return processEvent(request, eventId, startTime);
                })
                .exceptionally(throwable -> {
                    log.error("Event publishing failed: eventId={}", eventId, throwable);
                    recordMetrics(request.getEventType(), false, System.currentTimeMillis() - startTime);
                    return EventPublishResult.failure(eventId, throwable.getMessage());
                });
    }

    /**
     * Publish event with simple interface (backward compatibility)
     */
    public CompletableFuture<EventPublishResult> publishEvent(String eventType, Object eventData) {
        return publishEvent(EventPublishRequest.builder()
                .eventType(eventType)
                .eventData(eventData)
                .build());
    }

    /**
     * Publish event with routing key
     */
    public CompletableFuture<EventPublishResult> publishEvent(String eventType, Object eventData, String routingKey) {
        return publishEvent(EventPublishRequest.builder()
                .eventType(eventType)
                .eventData(eventData)
                .routingKey(routingKey)
                .build());
    }

    /**
     * Batch publish events
     */
    public CompletableFuture<List<EventPublishResult>> publishEvents(List<EventPublishRequest> requests) {
        log.info("Batch publishing {} events", requests.size());
        
        List<CompletableFuture<EventPublishResult>> futures = requests.stream()
                .map(this::publishEvent)
                .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
    }

    /**
     * Register event transformer
     */
    public void registerTransformer(String eventType, EventTransformer transformer) {
        log.info("Registering transformer for event type: {}", eventType);
        transformers.put(eventType, transformer);
    }

    /**
     * Register event enricher
     */
    public void registerEnricher(String eventType, EventEnricher enricher) {
        log.info("Registering enricher for event type: {}", eventType);
        enrichers.put(eventType, enricher);
    }

    /**
     * Get gateway statistics
     */
    public GatewayStatistics getStatistics() {
        return GatewayStatistics.builder()
                .totalEventsProcessed(eventCounter.get())
                .registeredTransformers(transformers.size())
                .registeredEnrichers(enrichers.size())
                .generatedAt(Instant.now())
                .build();
    }

    // Private helper methods

    private CompletableFuture<EventPublishResult> processEvent(EventPublishRequest request, 
                                                               String eventId, 
                                                               long startTime) {
        try {
            // Transform event if transformer is registered
            Object transformedData = transformEvent(request);
            
            // Enrich event with additional context
            Object enrichedData = enrichEvent(request.getEventType(), transformedData);
            
            // Create standardized event
            StandardEvent standardEvent = createStandardEvent(request, eventId, enrichedData);
            
            // Determine routing strategy
            String topic = determineKafkaTopic(request.getEventType());
            String routingKey = determineRoutingKey(request, standardEvent);
            
            // Publish to Kafka
            return publishToKafka(topic, routingKey, standardEvent)
                    .thenApply(sendResult -> {
                        long processingTime = System.currentTimeMillis() - startTime;
                        recordMetrics(request.getEventType(), true, processingTime);
                        eventRegistry.recordEventMetrics(request.getEventType(), true, processingTime);
                        
                        log.debug("Event published successfully: eventId={}, topic={}, partition={}", 
                                eventId, topic, sendResult.getRecordMetadata().partition());
                        
                        return EventPublishResult.success(eventId, topic, 
                                sendResult.getRecordMetadata().partition());
                    })
                    .exceptionally(throwable -> {
                        long processingTime = System.currentTimeMillis() - startTime;
                        recordMetrics(request.getEventType(), false, processingTime);
                        eventRegistry.recordEventMetrics(request.getEventType(), false, processingTime);
                        
                        log.error("Failed to publish to Kafka: eventId={}", eventId, throwable);
                        return EventPublishResult.failure(eventId, throwable.getMessage());
                    });
                    
        } catch (Exception e) {
            log.error("Error processing event: eventId={}", eventId, e);
            return CompletableFuture.completedFuture(EventPublishResult.failure(eventId, e.getMessage()));
        }
    }

    private EventRegistryService.ValidationResult validateEvent(EventPublishRequest request) {
        // Validate event type exists
        Optional<EventRegistryService.EventDefinition> definition = 
                eventRegistry.getEventDefinition(request.getEventType());
        
        if (definition.isEmpty()) {
            log.warn("Event type not registered: {}", request.getEventType());
            return EventRegistryService.ValidationResult.builder()
                    .valid(false)
                    .errors(List.of("Event type not registered: " + request.getEventType()))
                    .build();
        }
        
        // Validate against schema if available
        return eventRegistry.validateEvent(request.getEventType(), request.getEventData());
    }

    private Object transformEvent(EventPublishRequest request) {
        EventTransformer transformer = transformers.get(request.getEventType());
        
        if (transformer != null) {
            log.debug("Transforming event: {}", request.getEventType());
            return transformer.transform(request.getEventData());
        }
        
        return request.getEventData();
    }

    private Object enrichEvent(String eventType, Object eventData) {
        EventEnricher enricher = enrichers.get(eventType);
        
        if (enricher != null) {
            log.debug("Enriching event: {}", eventType);
            return enricher.enrich(eventData);
        }
        
        return eventData;
    }

    private StandardEvent createStandardEvent(EventPublishRequest request, String eventId, Object eventData) {
        return StandardEvent.builder()
                .eventId(eventId)
                .eventType(request.getEventType())
                .eventData(eventData)
                .correlationId(request.getCorrelationId())
                .causationId(request.getCausationId())
                .aggregateId(request.getAggregateId())
                .aggregateType(request.getAggregateType())
                .version(request.getVersion() != null ? request.getVersion() : 1L)
                .timestamp(Instant.now())
                .source(request.getSource())
                .metadata(request.getMetadata() != null ? request.getMetadata() : new HashMap<>())
                .build();
    }

    private String determineKafkaTopic(String eventType) {
        // Map event type to Kafka topic
        // Use event domain as topic prefix
        String domain = extractDomain(eventType);
        
        return switch (domain) {
            case "payment" -> "payment-events";
            case "user" -> "user-events";
            case "security" -> "security-events";
            case "system" -> "system-events";
            case "notification" -> "notification-events";
            case "fraud" -> "fraud-events";
            default -> "general-events";
        };
    }

    private String determineRoutingKey(EventPublishRequest request, StandardEvent standardEvent) {
        // Use provided routing key or generate based on event
        if (request.getRoutingKey() != null) {
            return request.getRoutingKey();
        }
        
        // Generate routing key based on aggregate ID or user ID
        if (standardEvent.getAggregateId() != null) {
            return standardEvent.getAggregateId();
        }
        
        // Default to event type
        return standardEvent.getEventType();
    }

    private CompletableFuture<SendResult<String, Object>> publishToKafka(String topic, String key, StandardEvent event) {
        return kafkaTemplate.send(topic, key, event)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.error("Kafka publish failed: topic={}, key={}", topic, key, throwable);
                    }
                });
    }

    private void recordMetrics(String eventType, boolean success, long processingTime) {
        eventCounter.incrementAndGet();
        
        // Record success/failure metrics
        meterRegistry.counter("events.gateway.published",
                Tags.of("event_type", eventType,
                        "status", success ? "success" : "failure"))
                .increment();
        
        // Record processing time
        meterRegistry.timer("events.gateway.processing.duration",
                Tags.of("event_type", eventType))
                .record(processingTime, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        // Record total events
        meterRegistry.gauge("events.gateway.total", eventCounter.get());
    }

    private String generateEventId() {
        return "evt_" + UUID.randomUUID().toString().replace("-", "");
    }

    private String extractDomain(String eventType) {
        if (eventType.contains(".")) {
            return eventType.split("\\.")[0];
        }
        return "general";
    }

    // Inner classes and interfaces

    @Data
    @Builder
    public static class EventPublishRequest {
        private String eventType;
        private Object eventData;
        private String correlationId;
        private String causationId;
        private String aggregateId;
        private String aggregateType;
        private Long version;
        private String source;
        private String routingKey;
        private Map<String, String> metadata;
    }

    @Data
    @Builder
    public static class EventPublishResult {
        private String eventId;
        private boolean success;
        private String message;
        private String topic;
        private Integer partition;
        private Instant publishedAt;
        
        public static EventPublishResult success(String eventId, String topic, Integer partition) {
            return EventPublishResult.builder()
                    .eventId(eventId)
                    .success(true)
                    .topic(topic)
                    .partition(partition)
                    .publishedAt(Instant.now())
                    .build();
        }
        
        public static EventPublishResult failure(String eventId, String message) {
            return EventPublishResult.builder()
                    .eventId(eventId)
                    .success(false)
                    .message(message)
                    .publishedAt(Instant.now())
                    .build();
        }
    }

    @Data
    @Builder
    public static class StandardEvent {
        private String eventId;
        private String eventType;
        private Object eventData;
        private String correlationId;
        private String causationId;
        private String aggregateId;
        private String aggregateType;
        private Long version;
        private Instant timestamp;
        private String source;
        private Map<String, String> metadata;
    }

    @Data
    @Builder
    public static class GatewayStatistics {
        private long totalEventsProcessed;
        private int registeredTransformers;
        private int registeredEnrichers;
        private Instant generatedAt;
    }

    /**
     * Interface for event transformation
     */
    public interface EventTransformer {
        Object transform(Object eventData);
    }

    /**
     * Interface for event enrichment
     */
    public interface EventEnricher {
        Object enrich(Object eventData);
    }

    /**
     * Default event enricher that adds common metadata
     */
    @Component
    public static class DefaultEventEnricher implements EventEnricher {
        public Object enrich(Object eventData) {
            if (eventData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> eventMap = (Map<String, Object>) eventData;
                
                // Add common metadata
                eventMap.putIfAbsent("processedAt", Instant.now());
                eventMap.putIfAbsent("platform", "waqiti");
                eventMap.putIfAbsent("environment", System.getProperty("spring.profiles.active", "development"));
                
                return eventMap;
            }
            
            return eventData;
        }
    }

    /**
     * Payment event transformer example
     */
    @Component
    public static class PaymentEventTransformer implements EventTransformer {
        public Object transform(Object eventData) {
            if (eventData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> paymentData = (Map<String, Object>) eventData;
                
                // Normalize currency codes
                if (paymentData.containsKey("currency")) {
                    String currency = (String) paymentData.get("currency");
                    paymentData.put("currency", currency.toUpperCase());
                }
                
                // Add computed fields
                if (paymentData.containsKey("amount")) {
                    double amount = ((Number) paymentData.get("amount")).doubleValue();
                    paymentData.put("isHighValue", amount > 10000);
                }
                
                return paymentData;
            }
            
            return eventData;
        }
    }
}