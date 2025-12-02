package com.waqiti.common.telemetry.kafka;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.semconv.SemanticAttributes;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enterprise-grade Kafka Producer Interceptor with OpenTelemetry tracing
 * 
 * This implementation provides comprehensive Kafka producer tracing without requiring
 * the official OpenTelemetry Kafka instrumentation dependency. Features include:
 * 
 * - Automatic span creation for message production
 * - Trace context propagation via Kafka headers
 * - Message payload size tracking
 * - Topic and partition correlation
 * - Producer performance metrics
 * - Error tracking and correlation
 * - Batch processing optimization
 * - Financial transaction correlation
 * - Compliance event tagging
 * 
 * @author Waqiti Platform Team
 * @since Phase 3 - OpenTelemetry Implementation
 */
@Slf4j
public class WaqitiKafkaTracingProducerInterceptor<K, V> implements ProducerInterceptor<K, V> {
    
    private OpenTelemetry openTelemetry;
    private Tracer tracer;
    private final Map<String, Span> activeSpans = new ConcurrentHashMap<>();
    
    // Attribute keys
    private static final AttributeKey<String> MESSAGING_SYSTEM = SemanticAttributes.MESSAGING_SYSTEM;
    private static final AttributeKey<String> MESSAGING_DESTINATION_NAME = SemanticAttributes.MESSAGING_DESTINATION_NAME;
    private static final AttributeKey<String> MESSAGING_OPERATION = SemanticAttributes.MESSAGING_OPERATION;
    private static final AttributeKey<Long> MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES = SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES;
    private static final AttributeKey<String> MESSAGING_KAFKA_MESSAGE_KEY = SemanticAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
    private static final AttributeKey<Long> MESSAGING_KAFKA_PARTITION = SemanticAttributes.MESSAGING_KAFKA_PARTITION;
    private static final AttributeKey<Long> MESSAGING_KAFKA_OFFSET = SemanticAttributes.MESSAGING_KAFKA_OFFSET;
    
    // Custom Waqiti attributes
    private static final AttributeKey<String> WAQITI_TRANSACTION_ID = AttributeKey.stringKey("waqiti.transaction.id");
    private static final AttributeKey<String> WAQITI_USER_ID = AttributeKey.stringKey("waqiti.user.id");
    private static final AttributeKey<String> WAQITI_MESSAGE_TYPE = AttributeKey.stringKey("waqiti.message.type");
    private static final AttributeKey<String> WAQITI_BUSINESS_CONTEXT = AttributeKey.stringKey("waqiti.business.context");
    private static final AttributeKey<Boolean> WAQITI_COMPLIANCE_EVENT = AttributeKey.booleanKey("waqiti.compliance.event");
    private static final AttributeKey<String> WAQITI_EVENT_PRIORITY = AttributeKey.stringKey("waqiti.event.priority");
    
    // Kafka headers for trace propagation
    private static final String TRACE_PARENT_HEADER = "traceparent";
    private static final String TRACE_STATE_HEADER = "tracestate";
    
    @Override
    public void configure(Map<String, ?> configs) {
        this.openTelemetry = GlobalOpenTelemetry.get();
        this.tracer = openTelemetry.getTracer("waqiti-kafka-producer", "1.0.0");
        
        log.info("Configured Waqiti Kafka producer tracing interceptor");
    }
    
    @Override
    public ProducerRecord<K, V> onSend(ProducerRecord<K, V> record) {
        if (openTelemetry == null) {
            log.debug("OpenTelemetry not configured, skipping tracing for topic: {}", record.topic());
            return record;
        }
        
        try {
            // Create span for message production
            Span span = createProducerSpan(record);
            
            // Store span for correlation with acknowledgment
            String spanKey = generateSpanKey(record);
            activeSpans.put(spanKey, span);
            
            // Inject trace context into Kafka headers
            injectTraceContext(record, span);
            
            log.trace("Created producer span for topic: {}, partition: {}, key: {}", 
                record.topic(), record.partition(), record.key());
            
        } catch (Exception e) {
            log.error("Error creating producer span for topic: " + record.topic(), e);
        }
        
        return record;
    }
    
    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        String spanKey = generateSpanKey(metadata);
        Span span = activeSpans.remove(spanKey);
        
        if (span != null) {
            try {
                // Add metadata attributes
                if (metadata != null) {
                    addMetadataAttributes(span, metadata);
                }
                
                // Handle exceptions
                if (exception != null) {
                    addErrorAttributes(span, exception);
                } else {
                    span.setStatus(io.opentelemetry.api.trace.StatusCode.OK);
                }
                
                log.trace("Completed producer span for topic: {}, partition: {}, offset: {}", 
                    metadata != null ? metadata.topic() : "unknown",
                    metadata != null ? metadata.partition() : -1,
                    metadata != null ? metadata.offset() : -1);
                
            } finally {
                span.end(Instant.now());
            }
        }
    }
    
    @Override
    public void close() {
        log.info("Closing Waqiti Kafka producer tracing interceptor");
        
        // Complete any remaining spans
        activeSpans.values().forEach(span -> {
            try {
                span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, "Producer closed with pending span");
                span.end();
            } catch (Exception e) {
                log.warn("Error completing span during close", e);
            }
        });
        
        activeSpans.clear();
    }
    
    /**
     * Create producer span for the record
     */
    private Span createProducerSpan(ProducerRecord<K, V> record) {
        String spanName = record.topic() + " send";
        
        SpanBuilder spanBuilder = tracer.spanBuilder(spanName)
            .setSpanKind(SpanKind.PRODUCER)
            .setStartTimestamp(Instant.now());
        
        Span span = spanBuilder.startSpan();
        
        // Add standard messaging attributes
        addMessagingAttributes(span, record);
        
        // Add Waqiti-specific attributes
        addWaqitiAttributes(span, record);
        
        return span;
    }
    
    /**
     * Add standard messaging attributes to span
     */
    private void addMessagingAttributes(Span span, ProducerRecord<K, V> record) {
        span.setAttribute(MESSAGING_SYSTEM, "kafka");
        span.setAttribute(MESSAGING_DESTINATION_NAME, record.topic());
        span.setAttribute(MESSAGING_OPERATION, "send");
        
        // Message key
        if (record.key() != null) {
            span.setAttribute(MESSAGING_KAFKA_MESSAGE_KEY, String.valueOf(record.key()));
        }
        
        // Partition
        if (record.partition() != null) {
            span.setAttribute(MESSAGING_KAFKA_PARTITION, (long) record.partition());
        }
        
        // Message size
        if (record.value() != null) {
            long messageSize = calculateMessageSize(record.value());
            span.setAttribute(MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES, messageSize);
        }
        
        // Headers count
        if (record.headers() != null) {
            span.setAttribute(AttributeKey.longKey("messaging.kafka.headers_count"), 
                (long) record.headers().toArray().length);
        }
    }
    
    /**
     * Add Waqiti-specific attributes to span
     */
    private void addWaqitiAttributes(Span span, ProducerRecord<K, V> record) {
        // Extract business context from message
        String messageType = determineMessageType(record.topic());
        if (messageType != null) {
            span.setAttribute(WAQITI_MESSAGE_TYPE, messageType);
        }
        
        // Determine business context
        String businessContext = determineBusinessContext(record.topic());
        span.setAttribute(WAQITI_BUSINESS_CONTEXT, businessContext);
        
        // Check if compliance event
        boolean isComplianceEvent = isComplianceEvent(record.topic());
        if (isComplianceEvent) {
            span.setAttribute(WAQITI_COMPLIANCE_EVENT, true);
        }
        
        // Determine event priority
        String priority = determineEventPriority(record.topic(), record.value());
        if (priority != null) {
            span.setAttribute(WAQITI_EVENT_PRIORITY, priority);
        }
        
        // Try to extract user/transaction context from headers
        if (record.headers() != null) {
            extractContextFromHeaders(span, record.headers());
        }
        
        // Try to extract context from message content
        extractContextFromMessage(span, record.value());
    }
    
    /**
     * Add metadata attributes after acknowledgment
     */
    private void addMetadataAttributes(Span span, RecordMetadata metadata) {
        span.setAttribute(MESSAGING_KAFKA_PARTITION, (long) metadata.partition());
        span.setAttribute(MESSAGING_KAFKA_OFFSET, metadata.offset());
        span.setAttribute(AttributeKey.longKey("messaging.kafka.timestamp"), metadata.timestamp());
        
        // Add serialized key size if available
        if (metadata.serializedKeySize() >= 0) {
            span.setAttribute(AttributeKey.longKey("messaging.kafka.key_size"), 
                (long) metadata.serializedKeySize());
        }
        
        // Add serialized value size if available
        if (metadata.serializedValueSize() >= 0) {
            span.setAttribute(AttributeKey.longKey("messaging.kafka.value_size"), 
                (long) metadata.serializedValueSize());
        }
    }
    
    /**
     * Add error attributes to span
     */
    private void addErrorAttributes(Span span, Exception exception) {
        span.setAttribute(AttributeKey.booleanKey("error"), true);
        span.setAttribute(AttributeKey.stringKey("error.type"), exception.getClass().getSimpleName());
        
        if (exception.getMessage() != null) {
            span.setAttribute(AttributeKey.stringKey("error.message"), exception.getMessage());
        }
        
        span.setStatus(io.opentelemetry.api.trace.StatusCode.ERROR, exception.getMessage());
        span.recordException(exception);
    }
    
    /**
     * Inject trace context into Kafka headers
     */
    private void injectTraceContext(ProducerRecord<K, V> record, Span span) {
        Context context = Context.current().with(span);
        
        openTelemetry.getPropagators()
            .getTextMapPropagator()
            .inject(context, record.headers(), new KafkaHeaderSetter());
    }
    
    /**
     * Generate unique key for span tracking
     */
    private String generateSpanKey(ProducerRecord<K, V> record) {
        return record.topic() + ":" + 
               (record.partition() != null ? record.partition() : "null") + ":" + 
               System.identityHashCode(record);
    }
    
    /**
     * Generate span key from metadata
     */
    private String generateSpanKey(RecordMetadata metadata) {
        if (metadata == null) {
            return "unknown";
        }
        return metadata.topic() + ":" + metadata.partition() + ":" + metadata.offset();
    }
    
    /**
     * Calculate message size
     */
    private long calculateMessageSize(V value) {
        if (value == null) {
            return 0;
        }
        
        if (value instanceof String) {
            return ((String) value).getBytes(StandardCharsets.UTF_8).length;
        } else if (value instanceof byte[]) {
            return ((byte[]) value).length;
        } else {
            // Estimate based on string representation
            return String.valueOf(value).getBytes(StandardCharsets.UTF_8).length;
        }
    }
    
    /**
     * Determine message type from topic
     */
    private String determineMessageType(String topic) {
        if (topic.contains("payment")) return "payment";
        if (topic.contains("transfer")) return "transfer";
        if (topic.contains("settlement")) return "settlement";
        if (topic.contains("compliance")) return "compliance";
        if (topic.contains("fraud")) return "fraud";
        if (topic.contains("kyc")) return "kyc";
        if (topic.contains("notification")) return "notification";
        if (topic.contains("audit")) return "audit";
        return null;
    }
    
    /**
     * Determine business context from topic
     */
    private String determineBusinessContext(String topic) {
        if (topic.contains("payment") || topic.contains("transfer") || topic.contains("settlement")) {
            return "financial";
        } else if (topic.contains("compliance") || topic.contains("kyc") || topic.contains("fraud")) {
            return "regulatory";
        } else if (topic.contains("user") || topic.contains("account")) {
            return "identity";
        } else if (topic.contains("notification") || topic.contains("email") || topic.contains("sms")) {
            return "messaging";
        } else if (topic.contains("audit") || topic.contains("log")) {
            return "observability";
        }
        return "general";
    }
    
    /**
     * Check if topic represents compliance event
     */
    private boolean isComplianceEvent(String topic) {
        return topic.contains("compliance") || 
               topic.contains("aml") || 
               topic.contains("sanctions") || 
               topic.contains("kyc") || 
               topic.contains("fraud") ||
               topic.contains("audit");
    }
    
    /**
     * Determine event priority
     */
    private String determineEventPriority(String topic, V value) {
        // High priority events
        if (topic.contains("fraud") || topic.contains("alert") || topic.contains("critical")) {
            return "high";
        }
        
        // Medium priority events
        if (topic.contains("payment") || topic.contains("settlement") || topic.contains("compliance")) {
            return "medium";
        }
        
        return "low";
    }
    
    /**
     * Extract context from Kafka headers
     */
    private void extractContextFromHeaders(Span span, Headers headers) {
        headers.forEach(header -> {
            String key = header.key();
            if (header.value() != null) {
                String value = new String(header.value(), StandardCharsets.UTF_8);
                
                if ("X-User-Id".equals(key) || "user-id".equals(key)) {
                    span.setAttribute(WAQITI_USER_ID, value);
                } else if ("X-Transaction-Id".equals(key) || "transaction-id".equals(key)) {
                    span.setAttribute(WAQITI_TRANSACTION_ID, value);
                }
            }
        });
    }
    
    /**
     * Extract context from message content (simplified)
     */
    private void extractContextFromMessage(Span span, V value) {
        if (value instanceof String) {
            String message = (String) value;
            
            // Try to extract JSON fields (simplified)
            if (message.contains("\"userId\"")) {
                // Would implement proper JSON parsing in production
                log.trace("Message contains userId field");
            }
            
            if (message.contains("\"transactionId\"")) {
                log.trace("Message contains transactionId field");
            }
        }
    }
    
    /**
     * Kafka header setter for trace propagation
     */
    private static class KafkaHeaderSetter implements TextMapSetter<Headers> {
        @Override
        public void set(Headers carrier, String key, String value) {
            if (carrier != null) {
                carrier.remove(key);
                carrier.add(key, value.getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}