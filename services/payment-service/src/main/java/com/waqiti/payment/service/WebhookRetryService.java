package com.waqiti.payment.service;

import com.waqiti.payment.dto.WebhookEvent;
import com.waqiti.payment.dto.WebhookRetryPolicy;
import com.waqiti.payment.entity.WebhookDeliveryAttempt;
import com.waqiti.payment.entity.WebhookDeliveryStatus;
import com.waqiti.payment.repository.WebhookDeliveryAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import jakarta.crypto.Mac;
import jakarta.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Advanced webhook retry service with exponential backoff and circuit breaker
 * 
 * Features:
 * - Exponential backoff with jitter
 * - Circuit breaker pattern
 * - Dead letter queue for failed webhooks
 * - Webhook signature verification
 * - Delivery status tracking
 * - Automatic retry scheduling
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookRetryService {
    
    // Thread-safe SecureRandom for secure retry jitter calculation
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WebhookDeliveryAttemptRepository attemptRepository;
    private final RestTemplate restTemplate;
    private final WebhookDeliveryService webhookDeliveryService;
    
    @Value("${webhook.retry.max-attempts:5}")
    private int maxRetryAttempts;
    
    @Value("${webhook.retry.initial-delay-seconds:60}")
    private int initialDelaySeconds;
    
    @Value("${webhook.retry.max-delay-seconds:3600}")
    private int maxDelaySeconds;
    
    @Value("${webhook.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;
    
    @Value("${webhook.retry.jitter-factor:0.1}")
    private double jitterFactor;
    
    @Value("${webhook.signature.algorithm:HmacSHA256}")
    private String signatureAlgorithm;
    
    @Value("${webhook.timeout.connect-ms:5000}")
    private int connectTimeoutMs;
    
    @Value("${webhook.timeout.read-ms:10000}")
    private int readTimeoutMs;
    
    // Circuit breaker state per endpoint
    private final Map<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();
    
    /**
     * Send webhook with automatic retry on failure
     */
    @Async
    public CompletableFuture<WebhookDeliveryStatus> sendWebhookWithRetry(
            WebhookEvent event, 
            String endpointUrl, 
            String signingSecret,
            WebhookRetryPolicy retryPolicy) {
        
        log.info("Sending webhook event {} to {}", event.getEventId(), endpointUrl);
        
        // Check circuit breaker
        CircuitBreakerState circuitBreaker = circuitBreakers.computeIfAbsent(
            endpointUrl, k -> new CircuitBreakerState());
        
        if (circuitBreaker.isOpen()) {
            log.warn("Circuit breaker open for endpoint: {}", endpointUrl);
            return CompletableFuture.completedFuture(WebhookDeliveryStatus.CIRCUIT_BREAKER_OPEN);
        }
        
        // Create delivery attempt record
        int attemptCount = webhookDeliveryService.getAttemptCount(event.getEventId(), endpointUrl);
        WebhookDeliveryAttempt attempt = webhookDeliveryService.createDeliveryAttempt(
            event, endpointUrl, attemptCount);
        
        try {
            // Prepare webhook payload
            HttpEntity<WebhookEvent> request = prepareWebhookRequest(event, signingSecret);
            
            // Send webhook
            ResponseEntity<String> response = restTemplate.exchange(
                endpointUrl,
                HttpMethod.POST,
                request,
                String.class
            );
            
            // Handle response
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook delivered successfully to {}", endpointUrl);
                webhookDeliveryService.updateDeliveryAttempt(attempt, WebhookDeliveryStatus.SUCCESS, response);
                circuitBreaker.recordSuccess();
                return CompletableFuture.completedFuture(WebhookDeliveryStatus.SUCCESS);
            } else {
                log.warn("Webhook delivery failed with status {} to {}", 
                    response.getStatusCode(), endpointUrl);
                return handleFailedDelivery(event, endpointUrl, signingSecret, 
                    attempt, response, retryPolicy);
            }
            
        } catch (RestClientException e) {
            log.error("Error sending webhook to {}: {}", endpointUrl, e.getMessage());
            return handleFailedDelivery(event, endpointUrl, signingSecret, 
                attempt, null, retryPolicy);
        }
    }
    
    /**
     * Retry failed webhook delivery
     */
    private CompletableFuture<WebhookDeliveryStatus> handleFailedDelivery(
            WebhookEvent event,
            String endpointUrl,
            String signingSecret,
            WebhookDeliveryAttempt attempt,
            ResponseEntity<String> response,
            WebhookRetryPolicy retryPolicy) {
        
        // Update attempt record
        webhookDeliveryService.updateDeliveryAttempt(attempt, WebhookDeliveryStatus.FAILED, response);
        
        // Record circuit breaker failure
        CircuitBreakerState circuitBreaker = circuitBreakers.get(endpointUrl);
        if (circuitBreaker != null) {
            circuitBreaker.recordFailure();
        }
        
        // Check retry policy
        int attemptNumber = getAttemptCount(event.getEventId(), endpointUrl);
        int maxAttempts = retryPolicy != null ? retryPolicy.getMaxAttempts() : maxRetryAttempts;
        
        if (attemptNumber >= maxAttempts) {
            log.error("Max retry attempts ({}) reached for webhook {} to {}", 
                maxAttempts, event.getEventId(), endpointUrl);
            sendToDeadLetterQueue(event, endpointUrl, attempt);
            return CompletableFuture.completedFuture(WebhookDeliveryStatus.MAX_RETRIES_EXCEEDED);
        }
        
        // Schedule retry with exponential backoff
        long delaySeconds = calculateRetryDelay(attemptNumber, retryPolicy);
        scheduleRetry(event, endpointUrl, signingSecret, retryPolicy, delaySeconds);
        
        return CompletableFuture.completedFuture(WebhookDeliveryStatus.RETRY_SCHEDULED);
    }
    
    /**
     * Schedule webhook retry
     */
    private void scheduleRetry(
            WebhookEvent event,
            String endpointUrl,
            String signingSecret,
            WebhookRetryPolicy retryPolicy,
            long delaySeconds) {
        
        log.info("Scheduling webhook retry for {} to {} in {} seconds", 
            event.getEventId(), endpointUrl, delaySeconds);
        
        // Use Spring's TaskScheduler or similar
        CompletableFuture.delayedExecutor(delaySeconds, TimeUnit.SECONDS)
            .execute(() -> sendWebhookWithRetry(event, endpointUrl, signingSecret, retryPolicy));
    }
    
    /**
     * Calculate retry delay with exponential backoff and jitter
     */
    private long calculateRetryDelay(int attemptNumber, WebhookRetryPolicy retryPolicy) {
        double baseDelay = retryPolicy != null ? 
            retryPolicy.getInitialDelaySeconds() : initialDelaySeconds;
        double multiplier = retryPolicy != null ? 
            retryPolicy.getBackoffMultiplier() : backoffMultiplier;
        
        // Exponential backoff
        double delay = baseDelay * Math.pow(multiplier, attemptNumber - 1);
        
        // Apply max delay cap
        double maxDelay = retryPolicy != null ? 
            retryPolicy.getMaxDelaySeconds() : maxDelaySeconds;
        delay = Math.min(delay, maxDelay);
        
        // Add cryptographically secure jitter to prevent thundering herd
        double jitter = delay * jitterFactor * (2 * SECURE_RANDOM.nextDouble() - 1);
        delay = delay + jitter;
        
        return Math.max(1, (long) delay);
    }
    
    /**
     * Prepare webhook HTTP request with signature
     */
    private HttpEntity<WebhookEvent> prepareWebhookRequest(
            WebhookEvent event, String signingSecret) {
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Add webhook headers
        headers.add("X-Webhook-Id", event.getEventId());
        headers.add("X-Webhook-Timestamp", String.valueOf(Instant.now().getEpochSecond()));
        headers.add("X-Webhook-Event", event.getEventType());
        
        // Generate signature
        if (signingSecret != null && !signingSecret.isEmpty()) {
            String signature = generateWebhookSignature(event, signingSecret);
            headers.add("X-Webhook-Signature", signature);
        }
        
        // Add idempotency key
        headers.add("Idempotency-Key", event.getEventId());
        
        return new HttpEntity<>(event, headers);
    }
    
    /**
     * Generate webhook signature for verification
     */
    private String generateWebhookSignature(WebhookEvent event, String secret) {
        try {
            Mac mac = Mac.getInstance(signatureAlgorithm);
            SecretKeySpec secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), signatureAlgorithm);
            mac.init(secretKey);
            
            // Create signature payload
            String payload = event.getEventId() + "." + 
                           event.getTimestamp().getEpochSecond() + "." +
                           event.toJson();
            
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature);
            
        } catch (Exception e) {
            log.error("Error generating webhook signature", e);
            throw new RuntimeException("Failed to generate webhook signature", e);
        }
    }
    
    /**
     * Create webhook delivery attempt record
     */
    @Transactional
    private WebhookDeliveryAttempt createDeliveryAttempt(
            WebhookEvent event, String endpointUrl) {
        
        WebhookDeliveryAttempt attempt = WebhookDeliveryAttempt.builder()
            .eventId(event.getEventId())
            .eventType(event.getEventType())
            .endpointUrl(endpointUrl)
            .attemptNumber(getAttemptCount(event.getEventId(), endpointUrl) + 1)
            .status(WebhookDeliveryStatus.PENDING)
            .attemptedAt(Instant.now())
            .build();
        
        return attemptRepository.save(attempt);
    }
    
    /**
     * Update webhook delivery attempt with result
     */
    @Transactional
    private void updateDeliveryAttempt(
            WebhookDeliveryAttempt attempt,
            WebhookDeliveryStatus status,
            ResponseEntity<String> response) {
        
        attempt.setStatus(status);
        attempt.setCompletedAt(Instant.now());
        
        if (response != null) {
            attempt.setResponseStatus(response.getStatusCode().value());
            attempt.setResponseBody(response.getBody());
            attempt.setResponseHeaders(response.getHeaders().toString());
        }
        
        attemptRepository.save(attempt);
    }
    
    /**
     * Get attempt count for webhook
     */
    private int getAttemptCount(String eventId, String endpointUrl) {
        return attemptRepository.countByEventIdAndEndpointUrl(eventId, endpointUrl);
    }
    
    /**
     * Send failed webhook to dead letter queue
     */
    private void sendToDeadLetterQueue(
            WebhookEvent event, 
            String endpointUrl,
            WebhookDeliveryAttempt lastAttempt) {
        
        log.error("Sending webhook {} to dead letter queue after {} attempts", 
            event.getEventId(), lastAttempt.getAttemptNumber());
        
        // Store in dead letter queue for manual processing
        lastAttempt.setStatus(WebhookDeliveryStatus.DEAD_LETTER);
        lastAttempt.setNotes("Moved to dead letter queue after max retries");
        attemptRepository.save(lastAttempt);
        
        // Notify operations team
        notifyOperationsTeam(event, endpointUrl, lastAttempt);
    }
    
    /**
     * Notify operations team of webhook failure
     */
    private void notifyOperationsTeam(
            WebhookEvent event,
            String endpointUrl,
            WebhookDeliveryAttempt lastAttempt) {
        
        // Send alert to operations team
        log.error("ALERT: Webhook delivery failed permanently - Event: {}, Endpoint: {}, Attempts: {}",
            event.getEventId(), endpointUrl, lastAttempt.getAttemptNumber());
        
        // Could integrate with PagerDuty, Slack, etc.
    }
    
    /**
     * Process dead letter queue periodically
     */
    @Scheduled(cron = "0 0 * * * *") // Every hour
    public void processDeadLetterQueue() {
        log.info("Processing webhook dead letter queue");
        
        List<WebhookDeliveryAttempt> deadLetters = attemptRepository
            .findByStatusAndAttemptedAtBefore(
                WebhookDeliveryStatus.DEAD_LETTER,
                Instant.now().minus(24, ChronoUnit.HOURS)
            );
        
        log.info("Found {} webhooks in dead letter queue", deadLetters.size());
        
        // Manual retry or archive old entries
        for (WebhookDeliveryAttempt attempt : deadLetters) {
            // Implement retry logic or archival
            log.info("Processing dead letter webhook: {}", attempt.getEventId());
        }
    }
    
    /**
     * Circuit breaker state management
     */
    private static class CircuitBreakerState {
        private static final int FAILURE_THRESHOLD = 5;
        private static final long OPEN_DURATION_MS = 60000; // 1 minute
        
        private int failureCount = 0;
        private long lastFailureTime = 0;
        private boolean open = false;
        
        public synchronized void recordSuccess() {
            failureCount = 0;
            open = false;
        }
        
        public synchronized void recordFailure() {
            failureCount++;
            lastFailureTime = System.currentTimeMillis();
            
            if (failureCount >= FAILURE_THRESHOLD) {
                open = true;
                log.warn("Circuit breaker opened after {} failures", failureCount);
            }
        }
        
        public synchronized boolean isOpen() {
            if (open) {
                // Check if circuit should be half-open
                if (System.currentTimeMillis() - lastFailureTime > OPEN_DURATION_MS) {
                    open = false;
                    failureCount = 0;
                    log.info("Circuit breaker reset to closed state");
                }
            }
            return open;
        }
    }
}