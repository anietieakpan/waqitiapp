package com.waqiti.common.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer Interceptor for monitoring and tracking
 * Intercepts all consumed messages for logging and metrics
 * PRODUCTION FIX: Updated to Spring Kafka 3.x RecordInterceptor API
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class KafkaConsumerInterceptor implements RecordInterceptor<String, Object> {

    private final KafkaEventTrackingService eventTrackingService;

    /**
     * PRODUCTION FIX: Updated signature for Spring Kafka 3.x
     * Added Consumer parameter as required by new API
     */
    @Override
    public ConsumerRecord<String, Object> intercept(ConsumerRecord<String, Object> record,
                                                     Consumer<String, Object> consumer) {
        try {
            // Log the received message
            log.debug("Intercepted message from topic: {}, partition: {}, offset: {}", 
                     record.topic(), record.partition(), record.offset());
            
            // Add tracking headers if not present
            enrichRecord(record);
            
            // Update real-time metrics
            updateMetrics(record);
            
            return record;
            
        } catch (Exception e) {
            log.error("Error in consumer interceptor for topic: {}", record.topic(), e);
            return record; // Return original record even if interceptor fails
        }
    }

    /**
     * Enrich record with tracking information
     */
    private void enrichRecord(ConsumerRecord<String, Object> record) {
        try {
            // Add interception timestamp
            record.headers().add("intercepted_at", 
                String.valueOf(System.currentTimeMillis()).getBytes());
            
            // Add consumer interceptor marker
            record.headers().add("consumer_interceptor", "waqiti-interceptor".getBytes());
            
        } catch (Exception e) {
            log.debug("Failed to enrich record headers", e);
        }
    }

    /**
     * Update real-time metrics
     */
    private void updateMetrics(ConsumerRecord<String, Object> record) {
        try {
            // This could be expanded to update real-time dashboards
            // For now, just log debug information
            
            if (log.isDebugEnabled()) {
                long messageAge = System.currentTimeMillis() - record.timestamp();
                log.debug("Message age: {}ms for topic: {}", messageAge, record.topic());
                
                // Warn about old messages
                if (messageAge > 300000) { // 5 minutes
                    log.warn("Processing old message ({}ms old) from topic: {}", 
                            messageAge, record.topic());
                }
            }
            
        } catch (Exception e) {
            log.debug("Failed to update metrics in interceptor", e);
        }
    }

    /**
     * PRODUCTION FIX: Updated signature for Spring Kafka 3.x
     */
    @Override
    public void success(ConsumerRecord<String, Object> record, Consumer<String, Object> consumer) {
        try {
            log.debug("Successfully processed message from topic: {}, partition: {}, offset: {}", 
                     record.topic(), record.partition(), record.offset());
            
        } catch (Exception e) {
            log.debug("Error in success callback", e);
        }
    }

    /**
     * PRODUCTION FIX: Updated signature for Spring Kafka 3.x
     */
    @Override
    public void failure(ConsumerRecord<String, Object> record, Exception exception,
                       Consumer<String, Object> consumer) {
        try {
            log.error("Failed to process message from topic: {}, partition: {}, offset: {} - Error: {}", 
                     record.topic(), record.partition(), record.offset(), exception.getMessage());
            
        } catch (Exception e) {
            log.debug("Error in failure callback", e);
        }
    }
}