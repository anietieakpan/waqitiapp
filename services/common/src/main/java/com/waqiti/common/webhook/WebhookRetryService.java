package com.waqiti.common.webhook;

import com.waqiti.common.audit.Audited;
import com.waqiti.common.encryption.FieldEncryption;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.core.IntervalFunction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Predicate;

/**
 * Comprehensive webhook retry service with exponential backoff
 * Handles webhook delivery with reliability, security, and monitoring
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookRetryService {

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final WebhookRepository webhookRepository;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final MeterRegistry meterRegistry;
    private final FieldEncryption fieldEncryption;
    private final ScheduledExecutorService scheduledExecutor;
    
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
    
    @Value("${webhook.signature.algorithm:HmacSHA256}")
    private String signatureAlgorithm;
    
    @Value("${webhook.dead-letter.enabled:true}")
    private boolean deadLetterEnabled;

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();

    @Value("${webhook.circuit-breaker.enabled:true}")
    private boolean circuitBreakerEnabled;
    
    private final Map<String, WebhookDeliveryStatus> deliveryStatusCache = new ConcurrentHashMap<>();
    private final BlockingQueue<WebhookEvent> retryQueue = new LinkedBlockingQueue<>(10000);
    private final Set<String> processingWebhooks = ConcurrentHashMap.newKeySet();
    
    // Metrics
    private final Counter webhooksSentCounter;
    private final Counter webhooksFailedCounter;
    private final Counter webhooksRetriedCounter;
    private final Counter webhooksDeadLetteredCounter;
    private final Timer webhookDeliveryTimer;
    
    public WebhookRetryService(
            RestTemplate restTemplate,
            RedisTemplate<String, Object> redisTemplate,
            WebhookRepository webhookRepository,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry,
            MeterRegistry meterRegistry,
            FieldEncryption fieldEncryption) {
        
        this.restTemplate = restTemplate;
        this.redisTemplate = redisTemplate;
        this.webhookRepository = webhookRepository;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.meterRegistry = meterRegistry;
        this.fieldEncryption = fieldEncryption;
        this.scheduledExecutor = Executors.newScheduledThreadPool(10);
        
        // Initialize metrics
        this.webhooksSentCounter = Counter.builder("webhooks.sent.total")
                .description("Total webhooks sent successfully")
                .register(meterRegistry);
        
        this.webhooksFailedCounter = Counter.builder("webhooks.failed.total")
                .description("Total webhooks failed")
                .register(meterRegistry);
        
        this.webhooksRetriedCounter = Counter.builder("webhooks.retried.total")
                .description("Total webhook retries")
                .register(meterRegistry);
        
        this.webhooksDeadLetteredCounter = Counter.builder("webhooks.deadlettered.total")
                .description("Total webhooks sent to dead letter queue")
                .register(meterRegistry);
        
        this.webhookDeliveryTimer = Timer.builder("webhook.delivery.duration")
                .description("Webhook delivery duration")
                .register(meterRegistry);
        
        // Initialize retry configuration
        configureRetryStrategies();
    }
    
    /**
     * Send webhook with automatic retry on failure
     */
    @Async
    @Audited(action = "WEBHOOK_SEND")
    public CompletableFuture<WebhookDeliveryResult> sendWebhook(WebhookEvent event) {
        String webhookId = event.getId().toString();
        
        // Check if already processing
        if (!processingWebhooks.add(webhookId)) {
            log.warn("Webhook {} is already being processed", webhookId);
            return CompletableFuture.completedFuture(
                WebhookDeliveryResult.duplicate(webhookId));
        }
        
        try {
            // Validate webhook event
            validateWebhookEvent(event);
            
            // Store initial webhook event
            webhookRepository.save(event);
            
            // Attempt delivery
            WebhookDeliveryResult result = attemptDelivery(event, 0);
            
            if (!result.isSuccessful()) {
                // Schedule retry
                scheduleRetry(event, 1);
            }
            
            return CompletableFuture.completedFuture(result);
            
        } catch (Exception e) {
            log.error("Error sending webhook {}", webhookId, e);
            webhooksFailedCounter.increment();
            return CompletableFuture.completedFuture(
                WebhookDeliveryResult.failed(webhookId, e.getMessage()));
        } finally {
            processingWebhooks.remove(webhookId);
        }
    }
    
    /**
     * Attempt webhook delivery with circuit breaker
     */
    private WebhookDeliveryResult attemptDelivery(WebhookEvent event, int attemptNumber) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String webhookId = event.getId().toString();
        
        try {
            log.info("Attempting webhook delivery {} (attempt {})", webhookId, attemptNumber + 1);
            
            // Get or create circuit breaker for this endpoint
            CircuitBreaker circuitBreaker = getCircuitBreaker(event.getEndpointUrl());
            
            // Prepare request
            HttpEntity<Object> request = prepareWebhookRequest(event);
            
            // Execute with circuit breaker
            ResponseEntity<String> response;
            if (circuitBreakerEnabled) {
                response = circuitBreaker.executeSupplier(() -> 
                    restTemplate.exchange(
                        event.getEndpointUrl(),
                        HttpMethod.POST,
                        request,
                        String.class
                    )
                );
            } else {
                response = restTemplate.exchange(
                    event.getEndpointUrl(),
                    HttpMethod.POST,
                    request,
                    String.class
                );
            }
            
            // Check response
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook {} delivered successfully", webhookId);
                webhooksSentCounter.increment();
                updateWebhookStatus(webhookId, WebhookEvent.WebhookStatus.DELIVERED, attemptNumber);
                return WebhookDeliveryResult.success(webhookId, response.getStatusCode().value(), response.getBody());
            } else {
                log.warn("Webhook {} received non-2xx response: {}", 
                        webhookId, response.getStatusCode());
                return WebhookDeliveryResult.failed(webhookId, 
                        "Non-2xx response: " + response.getStatusCode());
            }
            
        } catch (HttpStatusCodeException e) {
            log.error("HTTP error delivering webhook {}: {}", webhookId, e.getStatusCode());
            
            // Determine if retryable
            if (isRetryableHttpStatus(e.getStatusCode())) {
                return WebhookDeliveryResult.retryable(webhookId, e.getMessage());
            } else {
                return WebhookDeliveryResult.failed(webhookId, e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Error delivering webhook {}", webhookId, e);
            return WebhookDeliveryResult.retryable(webhookId, e.getMessage());
            
        } finally {
            sample.stop(webhookDeliveryTimer);
        }
    }
    
    /**
     * Schedule webhook retry with exponential backoff
     */
    private void scheduleRetry(WebhookEvent event, int attemptNumber) {
        if (attemptNumber >= maxRetryAttempts) {
            log.warn("Webhook {} exceeded max retry attempts ({})", 
                    event.getId().toString(), maxRetryAttempts);
            handleDeadLetter(event);
            return;
        }
        
        long delayMs = calculateBackoffDelay(attemptNumber);
        
        log.info("Scheduling webhook {} retry {} in {} ms", 
                event.getId().toString(), attemptNumber, delayMs);
        
        webhooksRetriedCounter.increment();
        
        scheduledExecutor.schedule(() -> {
            WebhookDeliveryResult result = attemptDelivery(event, attemptNumber);
            
            if (!result.isSuccessful() && result.isRetryable()) {
                scheduleRetry(event, attemptNumber + 1);
            } else if (!result.isSuccessful()) {
                handleDeadLetter(event);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Calculate exponential backoff delay with jitter
     */
    private long calculateBackoffDelay(int attemptNumber) {
        // Exponential backoff: delay = initialDelay * (multiplier ^ attemptNumber)
        double exponentialDelay = initialDelayMs * Math.pow(backoffMultiplier, attemptNumber - 1);
        
        // Cap at maximum delay
        long delay = Math.min((long) exponentialDelay, maxDelayMs);

        // Add jitter to prevent thundering herd
        // SECURITY FIX: Use SecureRandom instead of Math.random()
        double jitter = delay * jitterFactor * (secureRandom.nextDouble() - 0.5) * 2;
        delay = (long) (delay + jitter);

        // Ensure minimum delay
        return Math.max(delay, initialDelayMs);
    }
    
    /**
     * Prepare webhook HTTP request with headers and signature
     */
    private HttpEntity<Object> prepareWebhookRequest(WebhookEvent event) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        // Add standard webhook headers
        headers.add("X-Webhook-Id", event.getId().toString());
        headers.add("X-Webhook-Event", event.getEventType());
        headers.add("X-Webhook-Timestamp", String.valueOf(Instant.now().toEpochMilli()));
        headers.add("X-Webhook-Version", "1.0");
        
        // Add signature for webhook verification
        String signature = generateWebhookSignature(event);
        headers.add("X-Webhook-Signature", signature);
        
        // Add custom headers
        if (event.getHeaders() != null) {
            event.getHeaders().forEach(headers::add);
        }
        
        // Add correlation ID
        headers.add("X-Correlation-Id", event.getCorrelationId());
        
        // Add idempotency key
        headers.add("X-Idempotency-Key", event.getIdempotencyKey());
        
        return new HttpEntity<>(event.getPayload(), headers);
    }
    
    /**
     * Generate HMAC signature for webhook payload
     */
    private String generateWebhookSignature(WebhookEvent event) {
        try {
            String secret = getWebhookSecret(event.getId().toString());
            String payload = event.getPayload().toString();
            
            Mac mac = Mac.getInstance(signatureAlgorithm);
            SecretKeySpec secretKey = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), signatureAlgorithm);
            mac.init(secretKey);
            
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error generating webhook signature", e);
            return "";
        }
    }
    
    /**
     * Handle dead letter webhook
     */
    private void handleDeadLetter(WebhookEvent event) {
        if (!deadLetterEnabled) {
            log.error("Webhook {} failed permanently, dead letter disabled", 
                    event.getId().toString());
            updateWebhookStatus(event.getId().toString(), WebhookEvent.WebhookStatus.FAILED, maxRetryAttempts);
            return;
        }
        
        log.warn("Moving webhook {} to dead letter queue", event.getId().toString());
        webhooksDeadLetteredCounter.increment();
        
        // Store in dead letter queue
        DeadLetterWebhook deadLetter = DeadLetterWebhook.builder()
                .webhookId(event.getId().toString())
                .event(event)
                .failedAt(Instant.now())
                .reason("Max retries exceeded")
                .totalAttempts(maxRetryAttempts)
                .build();
        
        webhookRepository.saveDeadLetter(deadLetter);
        updateWebhookStatus(event.getId().toString(), WebhookEvent.WebhookStatus.DEAD_LETTER, maxRetryAttempts);
        
        // Send alert
        sendDeadLetterAlert(event);
    }
    
    /**
     * Process dead letter queue periodically
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void processDeadLetterQueue() {
        log.info("Processing dead letter queue");
        
        List<DeadLetterWebhook> deadLetters = webhookRepository.findDeadLettersToRetry();
        
        for (DeadLetterWebhook deadLetter : deadLetters) {
            if (shouldRetryDeadLetter(deadLetter)) {
                log.info("Retrying dead letter webhook {}", deadLetter.getId().toString());
                sendWebhook(deadLetter.getEvent());
                // Remove from dead letter queue after retry
                webhookRepository.deleteById(deadLetter.getId());
            }
        }
    }
    
    /**
     * Get circuit breaker for endpoint
     */
    private CircuitBreaker getCircuitBreaker(String url) {
        String host = extractHost(url);

        // Get or create circuit breaker for this host
        return circuitBreakerRegistry.circuitBreaker(host);
    }
    
    /**
     * Configure retry strategies
     */
    private void configureRetryStrategies() {
        // Default retry configuration
        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(maxRetryAttempts)
                .waitDuration(Duration.ofMillis(initialDelayMs))
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        initialDelayMs, backoffMultiplier))
                .retryOnException(this::isRetryableException)
                .retryOnResult(this::isRetryableResult)
                .build();
        
        retryRegistry.addConfiguration("default", defaultConfig);
        
        // Idempotent operations retry config (more aggressive)
        RetryConfig idempotentConfig = RetryConfig.custom()
                .maxAttempts(maxRetryAttempts * 2)
                .waitDuration(Duration.ofMillis(initialDelayMs / 2))
                .intervalFunction(IntervalFunction.ofExponentialBackoff(
                        initialDelayMs / 2, backoffMultiplier))
                .retryExceptions(Exception.class)
                .build();
        
        retryRegistry.addConfiguration("idempotent", idempotentConfig);
    }
    
    /**
     * Check if exception is retryable
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof HttpStatusCodeException) {
            return isRetryableHttpStatus(((HttpStatusCodeException) throwable).getStatusCode());
        }
        
        // Retry on network/timeout errors
        return throwable instanceof java.net.SocketTimeoutException ||
               throwable instanceof java.net.ConnectException ||
               throwable instanceof java.io.IOException;
    }
    
    /**
     * Check if HTTP status is retryable
     */
    private boolean isRetryableHttpStatus(HttpStatusCode status) {
        // Retry on 5xx errors and specific 4xx errors
        return status.is5xxServerError() ||
               status == HttpStatus.TOO_MANY_REQUESTS ||
               status == HttpStatus.REQUEST_TIMEOUT ||
               status == HttpStatus.LOCKED;
    }
    
    /**
     * Check if result is retryable
     */
    private boolean isRetryableResult(Object result) {
        if (result instanceof WebhookDeliveryResult) {
            return ((WebhookDeliveryResult) result).isRetryable();
        }
        return false;
    }
    
    /**
     * Validate webhook event
     */
    private void validateWebhookEvent(WebhookEvent event) {
        if (event.getId().toString() == null || event.getId().toString().isEmpty()) {
            throw new IllegalArgumentException("Webhook ID is required");
        }
        
        if (event.getEndpointUrl() == null || event.getEndpointUrl().isEmpty()) {
            throw new IllegalArgumentException("Webhook URL is required");
        }
        
        if (!isValidUrl(event.getEndpointUrl())) {
            throw new IllegalArgumentException("Invalid webhook URL: " + event.getEndpointUrl());
        }
        
        if (event.getPayload() == null) {
            throw new IllegalArgumentException("Webhook payload is required");
        }
    }
    
    /**
     * Update webhook status
     */
    @Transactional
    private void updateWebhookStatus(String webhookId, WebhookEvent.WebhookStatus status, int attempts) {
        WebhookEvent event = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook not found: " + webhookId));

        event.setStatus(status);
        event.setRetryCount(attempts);
        event.setUpdatedAt(LocalDateTime.now());

        if (status == WebhookEvent.WebhookStatus.DELIVERED) {
            event.setDeliveredAt(Instant.now());
        } else if (status == WebhookEvent.WebhookStatus.FAILED || status == WebhookEvent.WebhookStatus.DEAD_LETTER) {
            event.setLastAttemptAt(Instant.now());
        }

        webhookRepository.save(event);

        // Update cache - using builder pattern for WebhookDeliveryStatus
        deliveryStatusCache.put(webhookId,
                WebhookDeliveryStatus.builder()
                        .webhookId(webhookId)
                        .endpointUrl(event.getEndpointUrl())
                        .status(status)
                        .attemptCount(attempts)
                        .maxAttempts(event.getMaxRetryAttempts())
                        .lastAttemptAt(Instant.now())
                        .nextAttemptAt(event.getNextAttemptAt())
                        .isSuccess(status == WebhookEvent.WebhookStatus.DELIVERED)
                        .canRetry(attempts < event.getMaxRetryAttempts())
                        .build());
    }
    
    /**
     * Create webhook record
     */
    private WebhookRecord createWebhookRecord(WebhookEvent event) {
        return WebhookRecord.builder()
                .webhookId(event.getId().toString())
                .endpointUrl(event.getEndpointUrl())
                .eventType(event.getEventType())
                .status(WebhookStatus.PENDING)
                .retryCount(0)
                .lastAttemptAt(Instant.now())
                .idempotencyKey(event.getIdempotencyKey())
                .build();
    }
    
    // Helper methods
    
    private String getWebhookSecret(String webhookId) {
        // Retrieve webhook secret from secure storage
        return (String) redisTemplate.opsForValue().get("webhook:secret:" + webhookId);
    }
    
    private String extractHost(String url) {
        try {
            java.net.URL u = new java.net.URL(url);
            return u.getHost();
        } catch (Exception e) {
            return "unknown";
        }
    }
    
    private boolean isValidUrl(String url) {
        try {
            new java.net.URL(url);
            return url.startsWith("https://"); // Require HTTPS
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean shouldRetryDeadLetter(DeadLetterWebhook deadLetter) {
        // Retry dead letters older than 24 hours but not older than 7 days
        Instant now = Instant.now();
        Instant failedAt = deadLetter.getFailedAt();
        
        return failedAt.isBefore(now.minus(24, ChronoUnit.HOURS)) &&
               failedAt.isAfter(now.minus(7, ChronoUnit.DAYS));
    }
    
    private void sendDeadLetterAlert(WebhookEvent event) {
        // Send alert to monitoring system
        log.error("ALERT: Webhook {} moved to dead letter queue after {} attempts",
                event.getId().toString(), maxRetryAttempts);
    }
}