package com.waqiti.apigateway.kafka;

import com.waqiti.common.events.ApiUsageEvent;
import com.waqiti.gateway.domain.ApiUsageRecord;
import com.waqiti.gateway.repository.ApiUsageRepository;
import com.waqiti.gateway.service.RateLimitService;
import com.waqiti.gateway.service.ApiQuotaService;
import com.waqiti.gateway.metrics.GatewayMetricsService;
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
public class ApiUsageTrackingEventsConsumer {
    
    private final ApiUsageRepository usageRepository;
    private final RateLimitService rateLimitService;
    private final ApiQuotaService quotaService;
    private final GatewayMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int RATE_LIMIT_THRESHOLD = 1000;
    private static final int QUOTA_WARNING_THRESHOLD = 80;
    
    @KafkaListener(
        topics = {"api-usage-events", "api-rate-limit-events", "api-quota-tracking"},
        groupId = "api-usage-tracking-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 15000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleApiUsageTrackingEvent(
            @Payload ApiUsageEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("api-usage-%s-%s-p%d-o%d", 
            event.getUserId(), event.getEndpoint(), partition, offset);
        
        log.info("Processing API usage event: userId={}, endpoint={}, method={}, status={}",
            event.getUserId(), event.getEndpoint(), event.getHttpMethod(), event.getStatusCode());
        
        try {
            ApiUsageRecord record = ApiUsageRecord.builder()
                .userId(event.getUserId())
                .clientId(event.getClientId())
                .endpoint(event.getEndpoint())
                .httpMethod(event.getHttpMethod())
                .statusCode(event.getStatusCode())
                .responseTimeMs(event.getResponseTimeMs())
                .requestSize(event.getRequestSize())
                .responseSize(event.getResponseSize())
                .ipAddress(event.getIpAddress())
                .userAgent(event.getUserAgent())
                .timestamp(LocalDateTime.now())
                .correlationId(correlationId)
                .build();
            usageRepository.save(record);
            
            rateLimitService.trackRequest(event.getUserId(), event.getClientId());
            
            int currentUsageCount = quotaService.getUsageCount(event.getUserId(), event.getClientId());
            int quotaLimit = quotaService.getQuotaLimit(event.getClientId());
            
            if (currentUsageCount >= quotaLimit) {
                rateLimitService.enforceRateLimit(event.getUserId(), event.getClientId());
                
                notificationService.sendNotification(event.getUserId(), "API Quota Exceeded",
                    String.format("Your API quota of %d requests has been exceeded. Access will be restricted.", quotaLimit),
                    correlationId);
                
                kafkaTemplate.send("api-quota-exceeded-alerts", Map.of(
                    "userId", event.getUserId(),
                    "clientId", event.getClientId(),
                    "currentUsage", currentUsageCount,
                    "quotaLimit", quotaLimit,
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
                
                metricsService.recordQuotaExceeded(event.getClientId());
            } else if (currentUsageCount >= (quotaLimit * QUOTA_WARNING_THRESHOLD / 100)) {
                notificationService.sendNotification(event.getUserId(), "API Quota Warning",
                    String.format("You have used %d%% of your API quota (%d/%d requests).", 
                        (currentUsageCount * 100 / quotaLimit), currentUsageCount, quotaLimit),
                    correlationId);
                
                metricsService.recordQuotaWarning(event.getClientId(), currentUsageCount, quotaLimit);
            }
            
            if (event.getStatusCode() >= 400) {
                metricsService.recordApiError(event.getEndpoint(), event.getStatusCode());
                
                if (event.getStatusCode() == 429) {
                    metricsService.recordRateLimitHit(event.getClientId());
                }
            }
            
            if (event.getResponseTimeMs() > 5000) {
                metricsService.recordSlowApiCall(event.getEndpoint(), event.getResponseTimeMs());
                
                kafkaTemplate.send("api-performance-alerts", Map.of(
                    "endpoint", event.getEndpoint(),
                    "responseTimeMs", event.getResponseTimeMs(),
                    "userId", event.getUserId(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
            
            metricsService.recordApiUsage(event.getEndpoint(), event.getHttpMethod(), 
                event.getStatusCode(), event.getResponseTimeMs());
            
            auditService.logApiUsageEvent("API_USAGE_TRACKED", event.getUserId(),
                Map.of("endpoint", event.getEndpoint(), "method", event.getHttpMethod(),
                    "statusCode", event.getStatusCode(), "responseTimeMs", event.getResponseTimeMs(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process API usage tracking event: {}", e.getMessage(), e);
            kafkaTemplate.send("api-usage-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
}
