package com.waqiti.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.integration.service.APIRateLimitService;
import com.waqiti.integration.service.IntegrationMonitoringService;
import com.waqiti.common.exception.IntegrationProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for API Rate Limit Events
 * Handles rate limiting, throttling, and API usage monitoring
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class APIRateLimitEventsConsumer {
    
    private final APIRateLimitService rateLimitService;
    private final IntegrationMonitoringService monitoringService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"api-rate-limit-events", "rate-limit-exceeded", "throttling-applied", "api-quota-reset"},
        groupId = "integration-service-rate-limit-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 15000)
    )
    @Transactional
    public void handleAPIRateLimitEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID rateLimitEventId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            rateLimitEventId = UUID.fromString((String) event.get("rateLimitEventId"));
            eventType = (String) event.get("eventType");
            String apiKey = (String) event.get("apiKey");
            String endpoint = (String) event.get("endpoint");
            Integer requestCount = (Integer) event.get("requestCount");
            Integer rateLimit = (Integer) event.get("rateLimit");
            String timeWindow = (String) event.get("timeWindow");
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            log.info("Processing API rate limit event - EventId: {}, Type: {}, Endpoint: {}, Requests: {}/{}", 
                    rateLimitEventId, eventType, endpoint, requestCount, rateLimit);
            
            switch (eventType) {
                case "RATE_LIMIT_EXCEEDED":
                    rateLimitService.handleRateLimitExceeded(rateLimitEventId, apiKey, endpoint,
                            requestCount, rateLimit, timeWindow, timestamp);
                    break;
                case "THROTTLING_APPLIED":
                    rateLimitService.applyThrottling(rateLimitEventId, apiKey, endpoint,
                            (Integer) event.get("throttleDelay"), timestamp);
                    break;
                case "API_QUOTA_RESET":
                    rateLimitService.resetAPIQuota(rateLimitEventId, apiKey, endpoint, timestamp);
                    break;
                default:
                    rateLimitService.processGenericRateLimitEvent(rateLimitEventId, eventType, event, timestamp);
            }
            
            monitoringService.updateAPIMetrics(apiKey, endpoint, requestCount, eventType, timestamp);
            
            auditService.auditFinancialEvent(
                    "API_RATE_LIMIT_EVENT_PROCESSED",
                    apiKey,
                    String.format("API rate limit event processed - Type: %s, Endpoint: %s", eventType, endpoint),
                    Map.of(
                            "rateLimitEventId", rateLimitEventId.toString(),
                            "eventType", eventType,
                            "apiKey", apiKey.substring(0, 8) + "***", // Masked for security
                            "endpoint", endpoint,
                            "requestCount", requestCount.toString(),
                            "rateLimit", rateLimit.toString(),
                            "timeWindow", timeWindow
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed API rate limit event - EventId: {}, EventType: {}", 
                    rateLimitEventId, eventType);
            
        } catch (Exception e) {
            log.error("API rate limit event processing failed - EventId: {}, Error: {}", 
                    rateLimitEventId, e.getMessage(), e);
            throw new IntegrationProcessingException("API rate limit event processing failed", e);
        }
    }
}