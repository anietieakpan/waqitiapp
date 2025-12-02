package com.waqiti.payment.service;

import com.waqiti.payment.webhook.*;
import com.waqiti.payment.webhook.repository.WebhookDeliveryAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Service for handling webhook delivery transactional operations.
 * Separated from WebhookRetryService to ensure proper Spring AOP proxy behavior.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookDeliveryService {
    
    private final WebhookDeliveryAttemptRepository attemptRepository;
    
    /**
     * Create webhook delivery attempt record
     */
    @Transactional
    public WebhookDeliveryAttempt createDeliveryAttempt(
            WebhookEvent event, String endpointUrl, int attemptCount) {
        
        WebhookDeliveryAttempt attempt = WebhookDeliveryAttempt.builder()
            .eventId(event.getEventId())
            .eventType(event.getEventType())
            .endpointUrl(endpointUrl)
            .attemptNumber(attemptCount + 1)
            .status(WebhookDeliveryStatus.PENDING)
            .attemptedAt(Instant.now())
            .build();
        
        return attemptRepository.save(attempt);
    }
    
    /**
     * Update webhook delivery attempt with result
     */
    @Transactional
    public void updateDeliveryAttempt(
            WebhookDeliveryAttempt attempt,
            WebhookDeliveryStatus status,
            ResponseEntity<String> response) {
        
        attempt.setStatus(status);
        attempt.setCompletedAt(Instant.now());
        
        if (response != null) {
            attempt.setResponseStatus(response.getStatusCode().value());
            attempt.setResponseBody(response.getBody());
            attempt.setResponseHeaders(response.getHeaders().toSingleValueMap().toString());
        }
        
        // Calculate response time
        long responseTime = Instant.now().toEpochMilli() - 
                           attempt.getAttemptedAt().toEpochMilli();
        attempt.setResponseTimeMs(responseTime);
        
        attemptRepository.save(attempt);
        
        log.info("Updated webhook delivery attempt {} with status: {}", 
                attempt.getId(), status);
    }
    
    /**
     * Get attempt count for an event and endpoint
     */
    public int getAttemptCount(String eventId, String endpointUrl) {
        return attemptRepository.countByEventIdAndEndpointUrl(eventId, endpointUrl);
    }
}