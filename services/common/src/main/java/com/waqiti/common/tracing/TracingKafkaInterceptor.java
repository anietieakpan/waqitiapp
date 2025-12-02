package com.waqiti.common.tracing;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Kafka interceptors for distributed tracing across async messaging
 * Propagates trace context through Kafka headers
 */
@Slf4j
@Component
public class TracingKafkaInterceptor {

    private static final String TRACE_ID_HEADER = "trace_id";
    private static final String SPAN_ID_HEADER = "span_id";
    private static final String PARENT_SPAN_ID_HEADER = "parent_span_id";
    private static final String CORRELATION_ID_HEADER = "correlation_id";
    private static final String TRACE_FLAGS_HEADER = "trace_flags";
    private static final String BAGGAGE_PREFIX = "baggage_";
    
    /**
     * Producer interceptor for outgoing messages
     */
    public static class TracingProducerInterceptor implements ProducerInterceptor<Object, Object> {
        
        private DistributedTracingService tracingService;
        private OpenTelemetryTracingService openTelemetryService;
        
        @Override
        public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
            try {
                String topic = record.topic();
                String operationName = "kafka_send_" + topic;
                
                log.debug("Intercepting Kafka producer for topic: {}", topic);
                
                // Start child span for Kafka send
                if (tracingService != null) {
                    DistributedTracingService.TraceContext childTrace = 
                        tracingService.startChildTrace(operationName);
                    
                    // Add Kafka-specific tags
                    Map<String, String> tags = new HashMap<>();
                    tags.put("messaging.system", "kafka");
                    tags.put("messaging.destination", topic);
                    tags.put("messaging.destination_kind", "topic");
                    tags.put("messaging.operation", "send");
                    
                    if (record.partition() != null) {
                        tags.put("messaging.kafka.partition", String.valueOf(record.partition()));
                    }
                    
                    if (record.key() != null) {
                        tags.put("messaging.kafka.message_key", record.key().toString());
                    }
                    
                    tracingService.addTags(tags);
                }
                
                // Inject trace context into Kafka headers
                injectTraceHeaders(record.headers());
                
                log.debug("Injected trace context into Kafka message for topic: {}", topic);
                
            } catch (Exception e) {
                log.error("Error injecting trace context into Kafka message", e);
                // Don't fail the message send due to tracing issues
            }
            
            return record;
        }
        
        @Override
        public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
            try {
                if (tracingService != null) {
                    if (exception != null) {
                        tracingService.recordError(exception);
                        log.error("Kafka send failed", exception);
                    } else if (metadata != null) {
                        // Add metadata to trace
                        Map<String, String> tags = new HashMap<>();
                        tags.put("messaging.kafka.offset", String.valueOf(metadata.offset()));
                        tags.put("messaging.kafka.timestamp", String.valueOf(metadata.timestamp()));
                        tracingService.addTags(tags);
                        
                        log.debug("Kafka message sent successfully to topic: {} partition: {} offset: {}", 
                            metadata.topic(), metadata.partition(), metadata.offset());
                    }
                }
            } catch (Exception e) {
                log.error("Error recording Kafka acknowledgement in trace", e);
            }
        }
        
        @Override
        public void close() {
            // Cleanup if needed
        }
        
        @Override
        public void configure(Map<String, ?> configs) {
            // Initialize services from Spring context if available
            initializeServices();
        }
        
        private void injectTraceHeaders(Headers headers) {
            if (tracingService != null) {
                String traceId = tracingService.getCurrentTraceId();
                String spanId = tracingService.getCurrentSpanId();
                String correlationId = CorrelationContext.getCorrelationId();
                
                if (traceId != null) {
                    headers.add(TRACE_ID_HEADER, traceId.getBytes(StandardCharsets.UTF_8));
                }
                
                if (spanId != null) {
                    headers.add(SPAN_ID_HEADER, spanId.getBytes(StandardCharsets.UTF_8));
                    headers.add(PARENT_SPAN_ID_HEADER, spanId.getBytes(StandardCharsets.UTF_8));
                }
                
                if (correlationId != null) {
                    headers.add(CORRELATION_ID_HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
                }
                
                // Add trace flags
                headers.add(TRACE_FLAGS_HEADER, "01".getBytes(StandardCharsets.UTF_8)); // Sampled
            }
            
            // Inject OpenTelemetry context
            if (openTelemetryService != null) {
                Map<String, String> carrier = new HashMap<>();
                openTelemetryService.injectContext(carrier);
                
                carrier.forEach((key, value) -> 
                    headers.add(key, value.getBytes(StandardCharsets.UTF_8)));
            }
        }
        
        private void initializeServices() {
            // This would be injected via Spring in a real application
            // For now, we'll use a static reference or service locator pattern
        }
    }
    
    /**
     * Consumer interceptor for incoming messages
     */
    public static class TracingConsumerInterceptor implements ConsumerInterceptor<Object, Object> {
        
        private DistributedTracingService tracingService;
        private OpenTelemetryTracingService openTelemetryService;
        
        @Override
        public ConsumerRecords<Object, Object> onConsume(ConsumerRecords<Object, Object> records) {
            for (ConsumerRecord<Object, Object> record : records) {
                try {
                    String topic = record.topic();
                    String operationName = "kafka_receive_" + topic;
                    
                    log.debug("Intercepting Kafka consumer for topic: {}", topic);
                    
                    // Extract trace context from headers
                    Map<String, String> headers = extractTraceHeaders(record.headers());
                    
                    // Start or continue trace
                    String correlationId = headers.get(CORRELATION_ID_HEADER);
                    
                    if (tracingService != null) {
                        DistributedTracingService.TraceContext traceContext = 
                            tracingService.startTrace(operationName, correlationId);
                        
                        // Add Kafka-specific tags
                        Map<String, String> tags = new HashMap<>();
                        tags.put("messaging.system", "kafka");
                        tags.put("messaging.source", topic);
                        tags.put("messaging.source_kind", "topic");
                        tags.put("messaging.operation", "receive");
                        tags.put("messaging.kafka.partition", String.valueOf(record.partition()));
                        tags.put("messaging.kafka.offset", String.valueOf(record.offset()));
                        tags.put("messaging.kafka.timestamp", String.valueOf(record.timestamp()));
                        
                        if (record.key() != null) {
                            tags.put("messaging.kafka.message_key", record.key().toString());
                        }
                        
                        tracingService.addTags(tags);
                        
                        // Store trace context in record headers for processing
                        record.headers().add("_trace_context", 
                            serialize(traceContext).getBytes(StandardCharsets.UTF_8));
                    }
                    
                    // Extract OpenTelemetry context
                    if (openTelemetryService != null) {
                        openTelemetryService.extractContext(headers);
                        
                        // Extract baggage
                        extractBaggage(headers);
                    }
                    
                    log.debug("Extracted trace context from Kafka message for topic: {}", topic);
                    
                } catch (Exception e) {
                    log.error("Error extracting trace context from Kafka message", e);
                    // Don't fail message processing due to tracing issues
                }
            }
            
            return records;
        }
        
        @Override
        public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
            try {
                if (tracingService != null) {
                    // Record commit event in trace
                    Map<String, String> eventAttributes = new HashMap<>();
                    eventAttributes.put("event.type", "kafka_commit");
                    eventAttributes.put("commit.offsets", offsets.toString());
                    
                    tracingService.recordEvent("kafka_commit", eventAttributes);
                    
                    log.debug("Recorded Kafka commit in trace: {}", offsets);
                }
            } catch (Exception e) {
                log.error("Error recording Kafka commit in trace", e);
            }
        }
        
        @Override
        public void close() {
            // Cleanup if needed
        }
        
        @Override
        public void configure(Map<String, ?> configs) {
            // Initialize services from Spring context if available
            initializeServices();
        }
        
        private Map<String, String> extractTraceHeaders(Headers headers) {
            Map<String, String> traceHeaders = new HashMap<>();
            
            for (Header header : headers) {
                String key = header.key();
                String value = new String(header.value(), StandardCharsets.UTF_8);
                
                if (key.equals(TRACE_ID_HEADER) || 
                    key.equals(SPAN_ID_HEADER) || 
                    key.equals(PARENT_SPAN_ID_HEADER) || 
                    key.equals(CORRELATION_ID_HEADER) || 
                    key.equals(TRACE_FLAGS_HEADER) ||
                    key.startsWith(BAGGAGE_PREFIX)) {
                    
                    traceHeaders.put(key, value);
                }
            }
            
            return traceHeaders;
        }
        
        private void extractBaggage(Map<String, String> headers) {
            headers.forEach((key, value) -> {
                if (key.startsWith(BAGGAGE_PREFIX)) {
                    String baggageKey = key.substring(BAGGAGE_PREFIX.length());
                    openTelemetryService.setBaggage(baggageKey, value);
                }
            });
        }
        
        private String serialize(Object obj) {
            // Simple serialization for trace context
            // In production, use proper serialization
            return obj.toString();
        }
        
        private void initializeServices() {
            // This would be injected via Spring in a real application
            // For now, we'll use a static reference or service locator pattern
        }
    }
    
    /**
     * Configuration helper for Kafka interceptors
     */
    public static class InterceptorConfig {
        
        public static Map<String, Object> producerConfig() {
            Map<String, Object> config = new HashMap<>();
            config.put("interceptor.classes", 
                TracingProducerInterceptor.class.getName());
            return config;
        }
        
        public static Map<String, Object> consumerConfig() {
            Map<String, Object> config = new HashMap<>();
            config.put("interceptor.classes", 
                TracingConsumerInterceptor.class.getName());
            return config;
        }
    }
}