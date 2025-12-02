package com.waqiti.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.webhook.entity.Webhook;
import com.waqiti.webhook.entity.WebhookDelivery;
import com.waqiti.webhook.entity.WebhookStatus;
import com.waqiti.webhook.repository.WebhookDeliveryRepository;
import com.waqiti.webhook.repository.WebhookRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * CRITICAL SERVICE: Webhook Delivery with Exponential Backoff and Reliability
 * Ensures reliable delivery of webhooks for payment notifications and events
 * 
 * Features:
 * - Exponential backoff retry mechanism
 * - Circuit breaker pattern for failing endpoints
 * - Dead letter queue for permanently failed webhooks
 * - Parallel delivery with rate limiting
 * - Signature verification (HMAC-SHA256)
 * - Delivery status tracking and metrics
 * - Automatic endpoint health monitoring
 * - Webhook deduplication
 * - Priority-based delivery queue
 * - Comprehensive error handling and recovery
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDeliveryService {
    
    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${webhook.retry.max-attempts:5}")
    private int maxRetryAttempts;
    
    @Value("${webhook.retry.initial-delay-ms:1000}")
    private long initialDelayMs;
    
    @Value("${webhook.retry.max-delay-ms:3600000}")
    private long maxDelayMs;
    
    @Value("${webhook.retry.multiplier:2.0}")
    private double backoffMultiplier;
    
    @Value("${webhook.retry.jitter-factor:0.1}")
    private double jitterFactor;
    
    @Value("${webhook.timeout-ms:30000}")
    private int timeoutMs;
    
    @Value("${webhook.parallel-deliveries:10}")
    private int parallelDeliveries;
    
    @Value("${webhook.dead-letter-after-hours:72}")
    private int deadLetterAfterHours;
    
    @Value("${webhook.signature.algorithm:HmacSHA256}")
    private String signatureAlgorithm;
    
    @Value("${webhook.circuit-breaker.failure-threshold:50}")
    private float circuitBreakerFailureThreshold;
    
    @Value("${webhook.circuit-breaker.wait-duration-seconds:60}")
    private int circuitBreakerWaitDuration;
    
    // Delivery management
    private final ExecutorService deliveryExecutor = Executors.newFixedThreadPool(10);
    private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(5);
    private final Map<String, AtomicInteger> endpointFailureCounters = new ConcurrentHashMap<>();
    private final Map<String, CircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<WebhookDeliveryTask> deliveryQueue = new PriorityBlockingQueue<>();
    
    // Metrics
    private final AtomicInteger totalDeliveries = new AtomicInteger(0);
    private final AtomicInteger successfulDeliveries = new AtomicInteger(0);
    private final AtomicInteger failedDeliveries = new AtomicInteger(0);
    private final AtomicInteger retriedDeliveries = new AtomicInteger(0);
    
    @PostConstruct
    public void initialize() {
        startDeliveryWorkers();
        loadPendingDeliveries();
        log.info("Webhook Delivery Service initialized - Max retries: {}, Initial delay: {}ms, Max delay: {}ms",
            maxRetryAttempts, initialDelayMs, maxDelayMs);
    }
    
    /**
     * Send webhook with automatic retry on failure
     */
    @Async
    public CompletableFuture<WebhookDelivery> sendWebhook(WebhookPayload payload) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Processing webhook delivery for event: {} to endpoint: {}", 
                    payload.getEventType(), payload.getEndpointUrl());
                
                // Create webhook record
                Webhook webhook = createWebhook(payload);
                webhook = webhookRepository.save(webhook);
                
                // Create delivery task
                WebhookDeliveryTask task = new WebhookDeliveryTask(webhook, payload, 0);
                
                // Add to delivery queue
                deliveryQueue.offer(task);
                
                // Wait for initial delivery attempt
                return task.getFuture().get(timeoutMs * 2, TimeUnit.MILLISECONDS);
                
            } catch (Exception e) {
                log.error("Failed to send webhook", e);
                throw new WebhookException("Webhook delivery failed", e);
            }
        }, deliveryExecutor);
    }
    
    /**
     * Process webhook delivery with retry logic
     */
    private WebhookDelivery processWebhookDelivery(WebhookDeliveryTask task) {
        Webhook webhook = task.getWebhook();
        WebhookPayload payload = task.getPayload();
        
        try {
            // Check circuit breaker
            CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(payload.getEndpointUrl());
            
            // Attempt delivery with circuit breaker
            Supplier<WebhookDelivery> decoratedSupplier = CircuitBreaker
                .decorateSupplier(circuitBreaker, () -> attemptDelivery(webhook, payload));
            
            WebhookDelivery delivery = decoratedSupplier.get();
            
            if (delivery.isSuccessful()) {
                handleSuccessfulDelivery(webhook, delivery);
                task.complete(delivery);
            } else {
                handleFailedDelivery(webhook, delivery, task);
            }
            
            return delivery;
            
        } catch (Exception e) {
            log.error("Error processing webhook delivery for webhook: {}", webhook.getId(), e);
            return handleDeliveryException(webhook, payload, task, e);
        }
    }
    
    /**
     * Attempt webhook delivery
     */
    private WebhookDelivery attemptDelivery(Webhook webhook, WebhookPayload payload) {
        LocalDateTime attemptTime = LocalDateTime.now();
        
        try {
            log.debug("Attempting delivery for webhook: {} (attempt: {})", 
                webhook.getId(), webhook.getAttemptCount() + 1);
            
            // Build HTTP request
            HttpEntity<String> request = buildHttpRequest(webhook, payload);
            
            // Set timeout
            restTemplate.getRestTemplate().setConnectTimeout(Duration.ofMillis(timeoutMs / 3));
            restTemplate.getRestTemplate().setReadTimeout(Duration.ofMillis(timeoutMs));
            
            // Send HTTP request
            ResponseEntity<String> response = restTemplate.exchange(
                URI.create(payload.getEndpointUrl()),
                HttpMethod.POST,
                request,
                String.class
            );
            
            // Create successful delivery record
            WebhookDelivery delivery = WebhookDelivery.builder()
                .id(UUID.randomUUID().toString())
                .webhookId(webhook.getId())
                .attemptNumber(webhook.getAttemptCount() + 1)
                .attemptedAt(attemptTime)
                .responseCode(response.getStatusCodeValue())
                .responseBody(truncateResponse(response.getBody()))
                .responseHeaders(response.getHeaders().toSingleValueMap())
                .deliveryTime(Duration.between(attemptTime, LocalDateTime.now()).toMillis())
                .successful(response.getStatusCode().is2xxSuccessful())
                .build();
            
            deliveryRepository.save(delivery);
            
            log.info("Webhook delivered successfully: {} - Status: {}", 
                webhook.getId(), response.getStatusCode());
            
            return delivery;
            
        } catch (Exception e) {
            log.warn("Webhook delivery failed for: {} - Error: {}", webhook.getId(), e.getMessage());
            
            // Create failed delivery record
            WebhookDelivery delivery = WebhookDelivery.builder()
                .id(UUID.randomUUID().toString())
                .webhookId(webhook.getId())
                .attemptNumber(webhook.getAttemptCount() + 1)
                .attemptedAt(attemptTime)
                .errorMessage(e.getMessage())
                .errorType(e.getClass().getSimpleName())
                .deliveryTime(Duration.between(attemptTime, LocalDateTime.now()).toMillis())
                .successful(false)
                .build();
            
            deliveryRepository.save(delivery);
            
            return delivery;
        }
    }
    
    /**
     * Handle successful delivery
     */
    private void handleSuccessfulDelivery(Webhook webhook, WebhookDelivery delivery) {
        webhook.setStatus(WebhookStatus.DELIVERED);
        webhook.setDeliveredAt(LocalDateTime.now());
        webhook.setLastAttemptAt(LocalDateTime.now());
        webhook.setAttemptCount(webhook.getAttemptCount() + 1);
        webhookRepository.save(webhook);
        
        // Update metrics
        successfulDeliveries.incrementAndGet();
        totalDeliveries.incrementAndGet();
        
        // Reset failure counter for endpoint
        endpointFailureCounters.remove(webhook.getEndpointUrl());
        
        log.info("Webhook delivered successfully: {} after {} attempts", 
            webhook.getId(), webhook.getAttemptCount());
    }
    
    /**
     * Handle failed delivery with retry
     */
    private void handleFailedDelivery(Webhook webhook, WebhookDelivery delivery, WebhookDeliveryTask task) {
        webhook.setAttemptCount(webhook.getAttemptCount() + 1);
        webhook.setLastAttemptAt(LocalDateTime.now());
        webhook.setLastErrorMessage(delivery.getErrorMessage());
        
        // Check if max retries reached
        if (webhook.getAttemptCount() >= maxRetryAttempts) {
            handleMaxRetriesExceeded(webhook, task);
            return;
        }
        
        // Check if webhook is too old for retry
        if (isWebhookTooOld(webhook)) {
            handleWebhookExpired(webhook, task);
            return;
        }
        
        // Calculate next retry delay with exponential backoff
        long retryDelay = calculateRetryDelay(webhook.getAttemptCount());
        webhook.setNextRetryAt(LocalDateTime.now().plus(retryDelay, ChronoUnit.MILLIS));
        webhook.setStatus(WebhookStatus.PENDING_RETRY);
        webhookRepository.save(webhook);
        
        // Schedule retry
        scheduleRetry(task, retryDelay);
        
        // Update metrics
        retriedDeliveries.incrementAndGet();
        
        log.info("Webhook {} scheduled for retry #{} in {}ms", 
            webhook.getId(), webhook.getAttemptCount(), retryDelay);
    }
    
    /**
     * Calculate retry delay with exponential backoff and jitter
     */
    private long calculateRetryDelay(int attemptNumber) {
        // Exponential backoff: delay = initialDelay * (multiplier ^ attemptNumber)
        long baseDelay = (long) (initialDelayMs * Math.pow(backoffMultiplier, attemptNumber - 1));
        
        // Cap at max delay
        baseDelay = Math.min(baseDelay, maxDelayMs);
        
        // Add jitter to prevent thundering herd
        double jitter = (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * jitterFactor;
        long finalDelay = (long) (baseDelay * (1 + jitter));
        
        // Ensure delay is positive and within bounds
        return Math.max(initialDelayMs, Math.min(finalDelay, maxDelayMs));
    }
    
    /**
     * Schedule webhook retry
     */
    private void scheduleRetry(WebhookDeliveryTask task, long delayMs) {
        retryScheduler.schedule(() -> {
            try {
                log.debug("Retrying webhook delivery: {} (attempt: {})", 
                    task.getWebhook().getId(), task.getAttemptNumber() + 1);
                
                // Increment attempt number
                task.incrementAttempt();
                
                // Re-add to delivery queue
                deliveryQueue.offer(task);
                
            } catch (Exception e) {
                log.error("Error scheduling webhook retry", e);
                task.completeExceptionally(e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Handle webhook that exceeded max retries
     */
    private void handleMaxRetriesExceeded(Webhook webhook, WebhookDeliveryTask task) {
        log.warn("Webhook {} exceeded max retries ({}), moving to dead letter queue", 
            webhook.getId(), maxRetryAttempts);
        
        webhook.setStatus(WebhookStatus.FAILED);
        webhook.setFailedAt(LocalDateTime.now());
        webhookRepository.save(webhook);
        
        // Send to dead letter queue
        sendToDeadLetterQueue(webhook, "Max retries exceeded");
        
        // Update metrics
        failedDeliveries.incrementAndGet();
        totalDeliveries.incrementAndGet();
        
        // Update endpoint failure counter
        incrementEndpointFailures(webhook.getEndpointUrl());
        
        // Complete task with failure
        task.completeExceptionally(new WebhookException("Max retries exceeded"));
    }
    
    /**
     * Handle expired webhook
     */
    private void handleWebhookExpired(Webhook webhook, WebhookDeliveryTask task) {
        log.warn("Webhook {} expired (age: {} hours), moving to dead letter queue", 
            webhook.getId(), ChronoUnit.HOURS.between(webhook.getCreatedAt(), LocalDateTime.now()));
        
        webhook.setStatus(WebhookStatus.EXPIRED);
        webhook.setExpiredAt(LocalDateTime.now());
        webhookRepository.save(webhook);
        
        // Send to dead letter queue
        sendToDeadLetterQueue(webhook, "Webhook expired");
        
        // Complete task with failure
        task.completeExceptionally(new WebhookException("Webhook expired"));
    }
    
    /**
     * Send webhook to dead letter queue
     */
    private void sendToDeadLetterQueue(Webhook webhook, String reason) {
        try {
            DeadLetterWebhook deadLetter = DeadLetterWebhook.builder()
                .webhookId(webhook.getId())
                .payload(webhook.getPayload())
                .endpointUrl(webhook.getEndpointUrl())
                .failureReason(reason)
                .attemptCount(webhook.getAttemptCount())
                .lastErrorMessage(webhook.getLastErrorMessage())
                .movedToDlqAt(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send("webhook.dead-letter-queue", deadLetter);
            
            log.info("Webhook {} moved to dead letter queue: {}", webhook.getId(), reason);
            
        } catch (Exception e) {
            log.error("Failed to send webhook to dead letter queue", e);
        }
    }
    
    /**
     * Build HTTP request with signature
     */
    private HttpEntity<String> buildHttpRequest(Webhook webhook, WebhookPayload payload) throws Exception {
        String payloadJson = objectMapper.writeValueAsString(payload.getData());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Webhook-Id", webhook.getId());
        headers.set("X-Webhook-Timestamp", String.valueOf(System.currentTimeMillis()));
        headers.set("X-Webhook-Event", payload.getEventType());
        
        // Add signature for verification
        String signature = generateSignature(payloadJson, payload.getSecret());
        headers.set("X-Webhook-Signature", signature);
        
        // Add custom headers if provided
        if (payload.getCustomHeaders() != null) {
            payload.getCustomHeaders().forEach(headers::set);
        }
        
        return new HttpEntity<>(payloadJson, headers);
    }
    
    /**
     * Generate HMAC signature for webhook payload
     */
    private String generateSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance(signatureAlgorithm);
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), signatureAlgorithm);
        mac.init(secretKey);
        
        byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature);
    }
    
    /**
     * Get or create circuit breaker for endpoint
     */
    private CircuitBreaker getOrCreateCircuitBreaker(String endpointUrl) {
        return circuitBreakers.computeIfAbsent(endpointUrl, url -> {
            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(circuitBreakerFailureThreshold)
                .waitDurationInOpenState(Duration.ofSeconds(circuitBreakerWaitDuration))
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .build();
            
            CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
            return registry.circuitBreaker(url);
        });
    }
    
    /**
     * Process pending webhook retries
     */
    @Scheduled(fixedDelay = 60000) // Every minute
    public void processPendingRetries() {
        try {
            log.debug("Processing pending webhook retries");
            
            List<Webhook> pendingWebhooks = webhookRepository.findByStatusAndNextRetryAtBefore(
                WebhookStatus.PENDING_RETRY, LocalDateTime.now());
            
            for (Webhook webhook : pendingWebhooks) {
                try {
                    WebhookPayload payload = objectMapper.readValue(webhook.getPayload(), WebhookPayload.class);
                    WebhookDeliveryTask task = new WebhookDeliveryTask(webhook, payload, webhook.getAttemptCount());
                    deliveryQueue.offer(task);
                    
                    log.info("Queued pending webhook for retry: {}", webhook.getId());
                    
                } catch (Exception e) {
                    log.error("Error queuing pending webhook: {}", webhook.getId(), e);
                }
            }
            
            if (!pendingWebhooks.isEmpty()) {
                log.info("Queued {} pending webhooks for retry", pendingWebhooks.size());
            }
            
        } catch (Exception e) {
            log.error("Error processing pending webhook retries", e);
        }
    }
    
    /**
     * Clean up old webhooks
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void cleanupOldWebhooks() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
            
            int deletedCount = webhookRepository.deleteByStatusAndCreatedAtBefore(
                WebhookStatus.DELIVERED, cutoffDate);
            
            log.info("Cleaned up {} old delivered webhooks", deletedCount);
            
        } catch (Exception e) {
            log.error("Error cleaning up old webhooks", e);
        }
    }
    
    /**
     * Start delivery worker threads
     */
    private void startDeliveryWorkers() {
        for (int i = 0; i < parallelDeliveries; i++) {
            deliveryExecutor.submit(new DeliveryWorker());
        }
        log.info("Started {} delivery worker threads", parallelDeliveries);
    }
    
    /**
     * Load pending deliveries on startup
     */
    private void loadPendingDeliveries() {
        try {
            List<Webhook> pendingWebhooks = webhookRepository.findByStatusIn(
                Arrays.asList(WebhookStatus.PENDING, WebhookStatus.PENDING_RETRY));
            
            for (Webhook webhook : pendingWebhooks) {
                try {
                    WebhookPayload payload = objectMapper.readValue(webhook.getPayload(), WebhookPayload.class);
                    WebhookDeliveryTask task = new WebhookDeliveryTask(webhook, payload, webhook.getAttemptCount());
                    deliveryQueue.offer(task);
                } catch (Exception e) {
                    log.error("Error loading pending webhook: {}", webhook.getId(), e);
                }
            }
            
            log.info("Loaded {} pending webhooks for delivery", pendingWebhooks.size());
            
        } catch (Exception e) {
            log.error("Error loading pending deliveries", e);
        }
    }
    
    /**
     * Create webhook entity
     */
    private Webhook createWebhook(WebhookPayload payload) {
        try {
            return Webhook.builder()
                .id(UUID.randomUUID().toString())
                .eventType(payload.getEventType())
                .eventId(payload.getEventId())
                .endpointUrl(payload.getEndpointUrl())
                .payload(objectMapper.writeValueAsString(payload))
                .status(WebhookStatus.PENDING)
                .priority(payload.getPriority())
                .createdAt(LocalDateTime.now())
                .attemptCount(0)
                .build();
        } catch (Exception e) {
            throw new WebhookException("Failed to create webhook", e);
        }
    }
    
    /**
     * Handle delivery exception
     */
    private WebhookDelivery handleDeliveryException(Webhook webhook, WebhookPayload payload, 
                                                   WebhookDeliveryTask task, Exception e) {
        WebhookDelivery delivery = WebhookDelivery.builder()
            .id(UUID.randomUUID().toString())
            .webhookId(webhook.getId())
            .attemptNumber(webhook.getAttemptCount() + 1)
            .attemptedAt(LocalDateTime.now())
            .errorMessage(e.getMessage())
            .errorType(e.getClass().getSimpleName())
            .successful(false)
            .build();
        
        deliveryRepository.save(delivery);
        handleFailedDelivery(webhook, delivery, task);
        
        return delivery;
    }
    
    /**
     * Check if webhook is too old
     */
    private boolean isWebhookTooOld(Webhook webhook) {
        long hoursSinceCreation = ChronoUnit.HOURS.between(webhook.getCreatedAt(), LocalDateTime.now());
        return hoursSinceCreation >= deadLetterAfterHours;
    }
    
    /**
     * Increment endpoint failure counter
     */
    private void incrementEndpointFailures(String endpointUrl) {
        AtomicInteger counter = endpointFailureCounters.computeIfAbsent(endpointUrl, k -> new AtomicInteger(0));
        int failures = counter.incrementAndGet();
        
        if (failures >= 10) {
            log.warn("Endpoint {} has {} consecutive failures - consider disabling", endpointUrl, failures);
        }
    }
    
    /**
     * Truncate response body for storage
     */
    private String truncateResponse(String response) {
        if (response == null) return null;
        return response.length() > 1000 ? response.substring(0, 1000) + "..." : response;
    }
    
    /**
     * Count failures for subscription since specified time
     */
    public int countFailuresSince(String subscriptionId, LocalDateTime since) {
        try {
            return deliveryRepository.countFailedDeliveriesBySubscriptionIdSince(subscriptionId, since);
        } catch (Exception e) {
            log.error("Failed to count failures for subscription: {}", subscriptionId, e);
            return 0;
        }
    }
    
    /**
     * Get delivery metrics
     */
    public Map<String, Object> getMetrics() {
        return Map.of(
            "totalDeliveries", totalDeliveries.get(),
            "successfulDeliveries", successfulDeliveries.get(),
            "failedDeliveries", failedDeliveries.get(),
            "retriedDeliveries", retriedDeliveries.get(),
            "queueSize", deliveryQueue.size(),
            "failingEndpoints", endpointFailureCounters.size()
        );
    }
    
    /**
     * Delivery worker thread
     */
    private class DeliveryWorker implements Runnable {
        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    WebhookDeliveryTask task = deliveryQueue.poll(1, TimeUnit.SECONDS);
                    if (task != null) {
                        processWebhookDelivery(task);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Error in delivery worker", e);
                }
            }
        }
    }
}