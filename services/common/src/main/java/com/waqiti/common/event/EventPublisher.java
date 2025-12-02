package com.waqiti.common.event;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    /**
     * Publishes an event to Kafka
     *
     * @param topic The Kafka topic
     * @param key The message key (usually entity ID)
     * @param eventType The type of event
     * @param data The event data
     */
    public void publishEvent(String topic, String key, String eventType, Map<String, Object> data) {
        try {
            Map<String, Object> event = new HashMap<>(data);
            event.put("eventId", UUID.randomUUID().toString());
            event.put("eventType", eventType);
            event.put("timestamp", LocalDateTime.now().toString());
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, key, eventJson);
            log.info("Published event: topic={}, type={}, key={}", topic, eventType, key);
        } catch (Exception e) {
            log.error("Failed to publish event: topic={}, type={}", topic, eventType, e);
        }
    }
    /**
     * Publishes a domain event to Kafka
     */
    public void publishDomainEvent(String topic, DomainEvent event) {
        try {
            String eventJson = objectMapper.writeValueAsString(event);
            kafkaTemplate.send(topic, event.getEventId(), eventJson);

            log.info("Published domain event: topic={}, type={}, eventId={}",
                    topic, event.getEventType(), event.getEventId());
        } catch (Exception e) {
            log.error("Failed to publish domain event: topic={}, type={}",
                    topic, event.getEventType(), e);
        }
    }
    
    /**
     * Publishes a simple event with event type and data
     * @param eventType The type of event
     * @param data The event data
     */
    public void publish(String eventType, Map<String, Object> data) {
        String topic = eventType.toLowerCase().replace("_", "-");
        String key = data.containsKey("transactionId") ? 
            data.get("transactionId").toString() : UUID.randomUUID().toString();
        publishEvent(topic, key, eventType, data);
    }
}