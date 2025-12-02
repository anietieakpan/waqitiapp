package com.waqiti.webhook.kafka;

import com.waqiti.common.events.WebhookRetryEvent;
import com.waqiti.webhookservice.domain.WebhookDeliveryAttempt;
import com.waqiti.webhookservice.repository.WebhookDeliveryAttemptRepository;
import com.waqiti.webhookservice.service.WebhookDeliveryService;
import com.waqiti.webhook.service.WebhookRetryService;
import com.waqiti.webhookservice.metrics.WebhookMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class WebhookRetryEventsConsumer {
    
    private final WebhookDeliveryAttemptRepository deliveryAttemptRepository;
    private final WebhookDeliveryService deliveryService;
    private final WebhookRetryService retryService;
    private final WebhookMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long[] RETRY_DELAYS_SECONDS = {5, 30, 300, 1800, 7200};
    
    @KafkaListener(
        topics = {"webhook-retry-events", "webhook-failure-events", "webhook-delivery-attempts"},
        groupId = "webhook-retry-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleWebhookRetryEvent(
            @Payload WebhookRetryEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("webhook-%s-att%d-p%d-o%d", 
            event.getWebhookId(), event.getAttemptNumber(), partition, offset);
        
        log.info("Processing webhook retry event: webhookId={}, status={}, attempt={}/{}",
            event.getWebhookId(), event.getStatus(), event.getAttemptNumber(), event.getMaxRetries());
        
        try {
            switch (event.getStatus()) {
                case "RETRY_SCHEDULED":
                    scheduleRetry(event, correlationId);
                    break;
                    
                case "RETRY_ATTEMPTED":
                    attemptDelivery(event, correlationId);
                    break;
                    
                case "DELIVERY_SUCCESSFUL":
                    markDeliverySuccessful(event, correlationId);
                    break;
                    
                case "DELIVERY_FAILED":
                    handleDeliveryFailure(event, correlationId);
                    break;
                    
                case "MAX_RETRIES_EXCEEDED":
                    handleMaxRetriesExceeded(event, correlationId);
                    break;
                    
                case "PERMANENT_FAILURE":
                    handlePermanentFailure(event, correlationId);
                    break;
                    
                case "RETRY_CANCELLED":
                    cancelRetry(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown webhook retry status: {}", event.getStatus());
                    break;
            }
            
            auditService.logWebhookEvent("WEBHOOK_RETRY_EVENT_PROCESSED", event.getWebhookId(),
                Map.of("status", event.getStatus(), "attemptNumber", event.getAttemptNumber(),
                    "responseCode", event.getResponseCode(), "correlationId", correlationId, 
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process webhook retry event: {}", e.getMessage(), e);
            kafkaTemplate.send("webhook-retry-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void scheduleRetry(WebhookRetryEvent event, String correlationId) {
        WebhookDeliveryAttempt attempt = WebhookDeliveryAttempt.builder()
            .webhookId(event.getWebhookId())
            .attemptNumber(event.getAttemptNumber())
            .status("RETRY_SCHEDULED")
            .targetUrl(event.getTargetUrl())
            .payload(event.getPayload())
            .scheduledAt(LocalDateTime.now())
            .nextRetryAt(calculateNextRetryTime(event.getAttemptNumber()))
            .correlationId(correlationId)
            .build();
        deliveryAttemptRepository.save(attempt);
        
        long delaySeconds = RETRY_DELAYS_SECONDS[Math.min(event.getAttemptNumber() - 1, RETRY_DELAYS_SECONDS.length - 1)];
        retryService.scheduleRetry(event.getWebhookId(), delaySeconds);
        
        metricsService.recordRetryScheduled(event.getAttemptNumber());
        
        log.info("Webhook retry scheduled: webhookId={}, attempt={}, delaySeconds={}", 
            event.getWebhookId(), event.getAttemptNumber(), delaySeconds);
    }
    
    private void attemptDelivery(WebhookRetryEvent event, String correlationId) {
        WebhookDeliveryAttempt attempt = deliveryAttemptRepository
            .findByWebhookIdAndAttemptNumber(event.getWebhookId(), event.getAttemptNumber())
            .orElseThrow(() -> new RuntimeException("Webhook delivery attempt not found"));
        
        attempt.setStatus("RETRY_ATTEMPTED");
        attempt.setAttemptedAt(LocalDateTime.now());
        deliveryAttemptRepository.save(attempt);
        
        try {
            Map<String, Object> deliveryResult = deliveryService.deliverWebhook(
                event.getWebhookId(),
                event.getTargetUrl(),
                event.getPayload(),
                event.getHeaders()
            );
            
            int responseCode = (int) deliveryResult.get("statusCode");
            String responseBody = (String) deliveryResult.get("responseBody");
            
            attempt.setResponseCode(responseCode);
            attempt.setResponseBody(responseBody);
            deliveryAttemptRepository.save(attempt);
            
            if (responseCode >= 200 && responseCode < 300) {
                kafkaTemplate.send("webhook-retry-events", Map.of(
                    "webhookId", event.getWebhookId(),
                    "status", "DELIVERY_SUCCESSFUL",
                    "attemptNumber", event.getAttemptNumber(),
                    "responseCode", responseCode,
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            } else {
                kafkaTemplate.send("webhook-retry-events", Map.of(
                    "webhookId", event.getWebhookId(),
                    "status", "DELIVERY_FAILED",
                    "attemptNumber", event.getAttemptNumber(),
                    "responseCode", responseCode,
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
            
        } catch (Exception e) {
            attempt.setErrorMessage(e.getMessage());
            deliveryAttemptRepository.save(attempt);
            
            kafkaTemplate.send("webhook-retry-events", Map.of(
                "webhookId", event.getWebhookId(),
                "status", "DELIVERY_FAILED",
                "attemptNumber", event.getAttemptNumber(),
                "errorMessage", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        metricsService.recordDeliveryAttempted(event.getAttemptNumber());
        
        log.info("Webhook delivery attempted: webhookId={}, attempt={}", 
            event.getWebhookId(), event.getAttemptNumber());
    }
    
    private void markDeliverySuccessful(WebhookRetryEvent event, String correlationId) {
        WebhookDeliveryAttempt attempt = deliveryAttemptRepository
            .findByWebhookIdAndAttemptNumber(event.getWebhookId(), event.getAttemptNumber())
            .orElseThrow(() -> new RuntimeException("Webhook delivery attempt not found"));
        
        attempt.setStatus("DELIVERY_SUCCESSFUL");
        attempt.setSuccessfulAt(LocalDateTime.now());
        attempt.setResponseCode(event.getResponseCode());
        deliveryAttemptRepository.save(attempt);
        
        deliveryService.markWebhookDelivered(event.getWebhookId());
        
        metricsService.recordDeliverySuccessful(event.getAttemptNumber());
        
        log.info("Webhook delivered successfully: webhookId={}, attempt={}, responseCode={}", 
            event.getWebhookId(), event.getAttemptNumber(), event.getResponseCode());
    }
    
    private void handleDeliveryFailure(WebhookRetryEvent event, String correlationId) {
        WebhookDeliveryAttempt attempt = deliveryAttemptRepository
            .findByWebhookIdAndAttemptNumber(event.getWebhookId(), event.getAttemptNumber())
            .orElseThrow(() -> new RuntimeException("Webhook delivery attempt not found"));
        
        attempt.setStatus("DELIVERY_FAILED");
        attempt.setFailedAt(LocalDateTime.now());
        attempt.setResponseCode(event.getResponseCode());
        attempt.setErrorMessage(event.getErrorMessage());
        deliveryAttemptRepository.save(attempt);
        
        if (isPermanentFailure(event.getResponseCode())) {
            kafkaTemplate.send("webhook-retry-events", Map.of(
                "webhookId", event.getWebhookId(),
                "status", "PERMANENT_FAILURE",
                "attemptNumber", event.getAttemptNumber(),
                "responseCode", event.getResponseCode(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else if (event.getAttemptNumber() >= MAX_RETRY_ATTEMPTS) {
            kafkaTemplate.send("webhook-retry-events", Map.of(
                "webhookId", event.getWebhookId(),
                "status", "MAX_RETRIES_EXCEEDED",
                "attemptNumber", event.getAttemptNumber(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("webhook-retry-events", Map.of(
                "webhookId", event.getWebhookId(),
                "status", "RETRY_SCHEDULED",
                "attemptNumber", event.getAttemptNumber() + 1,
                "maxRetries", MAX_RETRY_ATTEMPTS,
                "targetUrl", event.getTargetUrl(),
                "payload", event.getPayload(),
                "headers", event.getHeaders(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        metricsService.recordDeliveryFailed(event.getAttemptNumber(), event.getResponseCode());
        
        log.warn("Webhook delivery failed: webhookId={}, attempt={}, responseCode={}", 
            event.getWebhookId(), event.getAttemptNumber(), event.getResponseCode());
    }
    
    private void handleMaxRetriesExceeded(WebhookRetryEvent event, String correlationId) {
        deliveryService.markWebhookFailed(event.getWebhookId(), "MAX_RETRIES_EXCEEDED");
        
        notificationService.sendNotification("WEBHOOK_OPS_TEAM", "Webhook Max Retries Exceeded",
            String.format("Webhook %s failed after %d delivery attempts to %s", 
                event.getWebhookId(), MAX_RETRY_ATTEMPTS, event.getTargetUrl()),
            correlationId);
        
        metricsService.recordMaxRetriesExceeded();
        
        log.error("Webhook max retries exceeded: webhookId={}, attempts={}, targetUrl={}", 
            event.getWebhookId(), MAX_RETRY_ATTEMPTS, event.getTargetUrl());
    }
    
    private void handlePermanentFailure(WebhookRetryEvent event, String correlationId) {
        deliveryService.markWebhookFailed(event.getWebhookId(), "PERMANENT_FAILURE");
        
        notificationService.sendNotification("WEBHOOK_OPS_TEAM", "Webhook Permanent Failure",
            String.format("Webhook %s encountered permanent failure (HTTP %d) to %s", 
                event.getWebhookId(), event.getResponseCode(), event.getTargetUrl()),
            correlationId);
        
        metricsService.recordPermanentFailure(event.getResponseCode());
        
        log.error("Webhook permanent failure: webhookId={}, responseCode={}, targetUrl={}", 
            event.getWebhookId(), event.getResponseCode(), event.getTargetUrl());
    }
    
    private void cancelRetry(WebhookRetryEvent event, String correlationId) {
        deliveryService.cancelWebhookRetries(event.getWebhookId());
        
        metricsService.recordRetryCancelled();
        
        log.warn("Webhook retry cancelled: webhookId={}", event.getWebhookId());
    }
    
    private LocalDateTime calculateNextRetryTime(int attemptNumber) {
        long delaySeconds = RETRY_DELAYS_SECONDS[Math.min(attemptNumber - 1, RETRY_DELAYS_SECONDS.length - 1)];
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }
    
    private boolean isPermanentFailure(int responseCode) {
        return responseCode == 400 || responseCode == 401 || responseCode == 403 || 
               responseCode == 404 || responseCode == 405 || responseCode == 410;
    }
}