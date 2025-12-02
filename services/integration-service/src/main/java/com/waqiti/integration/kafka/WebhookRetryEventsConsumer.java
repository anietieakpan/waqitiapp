package com.waqiti.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.integration.service.WebhookRetryService;
import com.waqiti.integration.service.WebhookDeliveryService;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class WebhookRetryEventsConsumer {
    
    private final WebhookRetryService retryService;
    private final WebhookDeliveryService deliveryService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"webhook-retry-events", "webhook-delivery-failed", "webhook-retry-scheduled", "webhook-max-retries-exceeded"},
        groupId = "integration-service-webhook-retry-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 15000)
    )
    @Transactional
    public void handleWebhookRetryEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID webhookId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            webhookId = UUID.fromString((String) event.get("webhookId"));
            eventType = (String) event.get("eventType");
            String webhookUrl = (String) event.get("webhookUrl");
            Integer attemptNumber = (Integer) event.get("attemptNumber");
            String errorMessage = (String) event.get("errorMessage");
            LocalDateTime nextRetryTime = event.containsKey("nextRetryTime") ?
                    LocalDateTime.parse((String) event.get("nextRetryTime")) : null;
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            log.info("Processing webhook retry event - WebhookId: {}, Type: {}, Attempt: {}", 
                    webhookId, eventType, attemptNumber);
            
            switch (eventType) {
                case "WEBHOOK_DELIVERY_FAILED":
                    retryService.handleDeliveryFailure(webhookId, webhookUrl, attemptNumber,
                            errorMessage, timestamp);
                    break;
                case "WEBHOOK_RETRY_SCHEDULED":
                    retryService.scheduleRetry(webhookId, webhookUrl, nextRetryTime, timestamp);
                    break;
                case "WEBHOOK_MAX_RETRIES_EXCEEDED":
                    retryService.handleMaxRetriesExceeded(webhookId, webhookUrl, attemptNumber, timestamp);
                    break;
                default:
                    retryService.processGenericRetryEvent(webhookId, eventType, event, timestamp);
            }
            
            deliveryService.updateWebhookMetrics(webhookId, eventType, attemptNumber, timestamp);
            
            auditService.auditFinancialEvent(
                    "WEBHOOK_RETRY_EVENT_PROCESSED",
                    webhookUrl,
                    String.format("Webhook retry event processed - Type: %s, Attempt: %d", eventType, attemptNumber),
                    Map.of(
                            "webhookId", webhookId.toString(),
                            "eventType", eventType,
                            "webhookUrl", webhookUrl,
                            "attemptNumber", attemptNumber.toString(),
                            "errorMessage", errorMessage != null ? errorMessage : "N/A"
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed webhook retry event - WebhookId: {}, EventType: {}", 
                    webhookId, eventType);
            
        } catch (Exception e) {
            log.error("Webhook retry event processing failed - WebhookId: {}, Error: {}", 
                    webhookId, e.getMessage(), e);
            throw new IntegrationProcessingException("Webhook retry event processing failed", e);
        }
    }
}