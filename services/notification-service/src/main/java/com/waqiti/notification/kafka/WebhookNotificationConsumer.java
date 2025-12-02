package com.waqiti.notification.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.monitoring.AlertingService;
import com.waqiti.notification.event.WebhookNotificationEvent;
import com.waqiti.notification.service.WebhookDeliveryService;
import com.waqiti.notification.service.WebhookSecurityService;
import com.waqiti.notification.service.NotificationTrackingService;
import com.waqiti.notification.model.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.micrometer.core.instrument.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;
import java.util.function.Supplier;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Production-grade Kafka consumer for webhook notifications
 * Enhanced with circuit breaker, retry mechanisms, comprehensive security, and delivery guarantees
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebhookNotificationConsumer {

    private static final String TOPIC = "webhook-notifications";
    private static final String DLQ_TOPIC = "webhook-notifications-dlq";
    private static final String CONSUMER_GROUP = "notification-webhook-processor-group";
    
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_RETRY_DELAY_MS = 1000;
    private static final double RETRY_MULTIPLIER = 2.0;
    private static final long MAX_RETRY_DELAY_MS = 60000;
    
    private static final int MAX_BATCH_SIZE = 50;
    private static final int RATE_LIMIT_PER_MINUTE = 500;
    private static final int MAX_PAYLOAD_SIZE_KB = 1024;
    private static final double DELIVERY_RATE_THRESHOLD = 0.95;
    private static final int WEBHOOK_TIMEOUT_SECONDS = 30;
    private static final int MAX_REDIRECTS = 3;
    private static final int ENDPOINT_FAILURE_THRESHOLD = 10;
    
    // Services
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final WebhookDeliveryService webhookService;
    private final WebhookSecurityService securityService;
    private final NotificationTrackingService trackingService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler universalDLQHandler;
    
    // Resilience components
    private CircuitBreaker circuitBreaker;
    private Retry retryMechanism;
    private ScheduledExecutorService scheduledExecutor;
    
    // State management
    private final Map<String, WebhookProcessingState> webhookStates = new ConcurrentHashMap<>();
    private final Map<String, WebhookBatch> activeBatches = new ConcurrentHashMap<>();
    private final Map<String, EndpointHealth> endpointHealth = new ConcurrentHashMap<>();
    private final Map<String, WebhookSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, RateLimitState> rateLimits = new ConcurrentHashMap<>();
    private final Map<String, ScheduledWebhook> scheduledWebhooks = new ConcurrentHashMap<>();
    private final Map<String, WebhookAnalytics> analyticsData = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> failedEndpoints = new ConcurrentHashMap<>();
    private final Map<String, DeliveryAttempt> deliveryHistory = new ConcurrentHashMap<>();
    
    // Metrics
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong deliveredCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong retriedCount = new AtomicLong(0);
    private final AtomicLong timeoutCount = new AtomicLong(0);
    private final AtomicLong securityFailureCount = new AtomicLong(0);
    private final AtomicLong duplicateCount = new AtomicLong(0);
    
    private Counter processedCounter;
    private Counter errorCounter;
    private Counter deliveredCounter;
    private Counter failedCounter;
    private Counter retriedCounter;
    private Counter timeoutCounter;
    private Counter securityFailureCounter;
    private Counter duplicateCounter;
    private Gauge activeWebhooksGauge;
    private Gauge deliveryRateGauge;
    private Timer processingTimer;
    private Timer deliveryTimer;
    
    @PostConstruct
    public void init() {
        initializeMetrics();
        initializeCircuitBreaker();
        initializeRetryMechanism();
        initializeScheduledTasks();
        initializeRateLimits();
        loadWebhookSubscriptions();
        log.info("WebhookNotificationConsumer initialized successfully");
    }
    
    private void initializeMetrics() {
        processedCounter = meterRegistry.counter("webhook.notifications.processed.total");
        errorCounter = meterRegistry.counter("webhook.notifications.errors.total");
        deliveredCounter = meterRegistry.counter("webhook.notifications.delivered.total");
        failedCounter = meterRegistry.counter("webhook.notifications.failed.total");
        retriedCounter = meterRegistry.counter("webhook.notifications.retried.total");
        timeoutCounter = meterRegistry.counter("webhook.notifications.timeout.total");
        securityFailureCounter = meterRegistry.counter("webhook.notifications.security.failed.total");
        duplicateCounter = meterRegistry.counter("webhook.notifications.duplicate.total");
        
        activeWebhooksGauge = Gauge.builder("webhook.notifications.active", webhookStates, Map::size)
            .description("Number of active webhook notifications")
            .register(meterRegistry);
            
        deliveryRateGauge = Gauge.builder("webhook.notifications.delivery.rate", this, 
            consumer -> calculateDeliveryRate())
            .description("Current webhook delivery rate")
            .register(meterRegistry);
            
        processingTimer = Timer.builder("webhook.notifications.processing.duration")
            .description("Webhook notification processing duration")
            .register(meterRegistry);
            
        deliveryTimer = Timer.builder("webhook.notifications.delivery.duration")
            .description("Webhook notification delivery duration")
            .register(meterRegistry);
    }
    
    private void initializeCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(100)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(WEBHOOK_TIMEOUT_SECONDS))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
            
        circuitBreaker = CircuitBreaker.of("webhook-notification-processor", config);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                log.warn("Circuit breaker state transition: {}", event);
                alertingService.sendAlert(
                    "Circuit Breaker State Change",
                    String.format("Webhook notification circuit breaker transitioned from %s to %s",
                        event.getStateTransition().getFromState(),
                        event.getStateTransition().getToState()),
                    AlertingService.AlertSeverity.HIGH
                );
            });
    }
    
    private void initializeRetryMechanism() {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(MAX_RETRY_ATTEMPTS)
            .waitDuration(Duration.ofMillis(INITIAL_RETRY_DELAY_MS))
            .retryOnException(this::isRetryableException)
            .failAfterMaxAttempts(true)
            .build();
            
        retryMechanism = Retry.of("webhook-notification-retry", config);
        
        retryMechanism.getEventPublisher()
            .onRetry(event -> {
                log.debug("Retry attempt {} for webhook notification", event.getNumberOfRetryAttempts());
                retriedCount.incrementAndGet();
                retriedCounter.increment();
            });
    }
    
    private void initializeScheduledTasks() {
        scheduledExecutor = Executors.newScheduledThreadPool(6);
        
        scheduledExecutor.scheduleAtFixedRate(
            this::processBatches,
            0, 10, TimeUnit.SECONDS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::processScheduledWebhooks,
            0, 1, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::updateEndpointHealth,
            0, 5, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::cleanupExpiredData,
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeWebhookPerformance,
            0, 30, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::verifyEndpointSecurity,
            0, 6, TimeUnit.HOURS
        );
    }
    
    private void initializeRateLimits() {
        rateLimits.put("global", new RateLimitState(RATE_LIMIT_PER_MINUTE));
        rateLimits.put("payment", new RateLimitState(1000));
        rateLimits.put("user", new RateLimitState(300));
        rateLimits.put("transaction", new RateLimitState(800));
        rateLimits.put("system", new RateLimitState(200));
    }
    
    private void loadWebhookSubscriptions() {
        try {
            // Load active webhook subscriptions
            List<WebhookSubscription> subs = webhookService.getActiveSubscriptions();
            subs.forEach(sub -> subscriptions.put(sub.getId(), sub));
            log.info("Loaded {} webhook subscriptions", subscriptions.size());
        } catch (Exception e) {
            log.error("Failed to load webhook subscriptions", e);
        }
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    public void processWebhookNotification(@Payload String message,
                                         @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                         @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                         @Header(KafkaHeaders.OFFSET) long offset,
                                         @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                         Acknowledgment acknowledgment) {
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        
        try {
            log.debug("Processing webhook notification from partition {} offset {}", partition, offset);
            
            WebhookNotificationEvent event = parseEvent(message);
            MDC.put("webhookId", event.getWebhookId());
            MDC.put("endpoint", maskEndpoint(event.getEndpoint()));
            MDC.put("eventType", event.getEventType());
            
            if (!validateEvent(event)) {
                log.error("Invalid webhook notification event: {}", event);
                handleInvalidEvent(event, message);
                acknowledgment.acknowledge();
                return;
            }
            
            Supplier<Boolean> processor = () -> processEvent(event);
            
            Boolean success = circuitBreaker.executeSupplier(
                () -> retryMechanism.executeSupplier(processor)
            );
            
            if (Boolean.TRUE.equals(success)) {
                acknowledgment.acknowledge();
                processedCount.incrementAndGet();
                processedCounter.increment();
            } else {
                handleProcessingFailure(event, message);
                sendToDLQ(message, "Processing failed");
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            log.error("Error processing webhook notification", e);
            errorCount.incrementAndGet();
            errorCounter.increment();
            handleProcessingError(e, message);

            // Use UniversalDLQHandler for enhanced DLQ routing
            universalDLQHandler.sendToDLQ(
                message,
                topic,
                partition,
                offset,
                e,
                Map.of(
                    "consumerGroup", CONSUMER_GROUP,
                    "errorType", e.getClass().getSimpleName()
                )
            );

            acknowledgment.acknowledge();
            throw e;
        } finally {
            MDC.clear();
        }
    }
    
    private WebhookNotificationEvent parseEvent(String message) throws JsonProcessingException {
        return objectMapper.readValue(message, WebhookNotificationEvent.class);
    }
    
    private boolean validateEvent(WebhookNotificationEvent event) {
        if (event == null || event.getWebhookId() == null || event.getWebhookId().trim().isEmpty()) {
            return false;
        }
        
        if (event.getEndpoint() == null || !isValidUrl(event.getEndpoint())) {
            return false;
        }
        
        if (event.getPayload() == null) {
            return false;
        }
        
        // Check payload size
        try {
            String payloadStr = objectMapper.writeValueAsString(event.getPayload());
            if (payloadStr.getBytes().length > MAX_PAYLOAD_SIZE_KB * 1024) {
                log.warn("Webhook payload too large: {} bytes", payloadStr.getBytes().length);
                return false;
            }
        } catch (Exception e) {
            log.error("Error validating payload size", e);
            return false;
        }
        
        return true;
    }
    
    private boolean processEvent(WebhookNotificationEvent event) {
        return processingTimer.recordCallable(() -> {
            switch (event.getType()) {
                case "DELIVER_WEBHOOK":
                    return processDeliverWebhook(event);
                case "BATCH_WEBHOOK":
                    return processBatchWebhook(event);
                case "SIGNED_WEBHOOK":
                    return processSignedWebhook(event);
                case "RETRY_WEBHOOK":
                    return processRetryWebhook(event);
                case "VERIFY_ENDPOINT":
                    return processVerifyEndpoint(event);
                case "SUBSCRIBE_WEBHOOK":
                    return processSubscribeWebhook(event);
                case "UNSUBSCRIBE_WEBHOOK":
                    return processUnsubscribeWebhook(event);
                case "UPDATE_SUBSCRIPTION":
                    return processUpdateSubscription(event);
                case "WEBHOOK_RESPONSE":
                    return processWebhookResponse(event);
                case "ENDPOINT_HEALTH_CHECK":
                    return processEndpointHealthCheck(event);
                case "BULK_DELIVERY":
                    return processBulkDelivery(event);
                case "SCHEDULED_WEBHOOK":
                    return processScheduledWebhook(event);
                case "WEBHOOK_ANALYTICS":
                    return processWebhookAnalytics(event);
                case "SECURITY_SCAN":
                    return processSecurityScan(event);
                case "DELIVERY_CONFIRMATION":
                    return processDeliveryConfirmation(event);
                default:
                    log.warn("Unknown webhook notification type: {}", event.getType());
                    return false;
            }
        });
    }
    
    private boolean processDeliverWebhook(WebhookNotificationEvent event) {
        try {
            String webhookId = event.getWebhookId();
            WebhookProcessingState state = new WebhookProcessingState(webhookId);
            webhookStates.put(webhookId, state);
            
            if (!checkRateLimit(event.getEventType())) {
                queueForLaterDelivery(event);
                return true;
            }
            
            // Check if endpoint is healthy
            EndpointHealth health = getEndpointHealth(event.getEndpoint());
            if (!health.isHealthy()) {
                log.warn("Endpoint {} is unhealthy, queuing for later", maskEndpoint(event.getEndpoint()));
                queueForLaterDelivery(event);
                return true;
            }
            
            // Check for duplicate delivery
            if (isDuplicateDelivery(event)) {
                log.warn("Duplicate webhook delivery detected: {}", webhookId);
                duplicateCount.incrementAndGet();
                duplicateCounter.increment();
                return true;
            }
            
            state.setStatus("PROCESSING");
            state.setEndpoint(event.getEndpoint());
            
            // Prepare webhook payload
            WebhookPayload payload = prepareWebhookPayload(event);
            
            // Add security headers if required
            Map<String, String> headers = prepareHeaders(event);
            
            // Deliver webhook
            WebhookDeliveryResult result = deliveryTimer.recordCallable(() -> 
                webhookService.deliverWebhook(
                    event.getEndpoint(),
                    payload,
                    headers,
                    WEBHOOK_TIMEOUT_SECONDS,
                    MAX_REDIRECTS
                )
            );
            
            // Process delivery result
            return handleDeliveryResult(event, state, result);
            
        } catch (Exception e) {
            log.error("Error processing deliver webhook for {}", event.getWebhookId(), e);
            return false;
        }
    }
    
    private boolean processBatchWebhook(WebhookNotificationEvent event) {
        try {
            String batchId = event.getBatchId();
            WebhookBatch batch = activeBatches.computeIfAbsent(batchId, 
                k -> new WebhookBatch(batchId, MAX_BATCH_SIZE));
            
            batch.addWebhook(event);
            
            if (batch.shouldProcess()) {
                return processBatch(batch);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing batch webhook", e);
            return false;
        }
    }
    
    private boolean processSignedWebhook(WebhookNotificationEvent event) {
        try {
            // Generate signature
            String signature = generateWebhookSignature(event.getPayload(), event.getSecret());
            
            // Add signature to headers
            Map<String, String> headers = new HashMap<>();
            if (event.getHeaders() != null) {
                headers.putAll(event.getHeaders());
            }
            headers.put("X-Webhook-Signature", signature);
            headers.put("X-Webhook-Signature-256", "sha256=" + signature);
            
            event.setHeaders(headers);
            event.setType("DELIVER_WEBHOOK");
            
            return processDeliverWebhook(event);
            
        } catch (Exception e) {
            log.error("Error processing signed webhook", e);
            return false;
        }
    }
    
    private boolean processRetryWebhook(WebhookNotificationEvent event) {
        try {
            String webhookId = event.getWebhookId();
            
            // Check retry count
            DeliveryAttempt lastAttempt = deliveryHistory.get(webhookId);
            if (lastAttempt != null && lastAttempt.getAttemptCount() >= MAX_RETRY_ATTEMPTS) {
                log.warn("Max retry attempts exceeded for webhook {}", webhookId);
                handleMaxRetriesExceeded(event);
                return false;
            }
            
            // Calculate retry delay
            long delay = calculateRetryDelay(lastAttempt != null ? lastAttempt.getAttemptCount() : 0);
            
            // Schedule retry
            scheduleWebhookDelivery(event, Instant.now().plusMillis(delay));
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing retry webhook", e);
            return false;
        }
    }
    
    private boolean processVerifyEndpoint(WebhookNotificationEvent event) {
        try {
            String endpoint = event.getEndpoint();
            
            boolean verified = webhookService.verifyEndpoint(endpoint, event.getVerificationChallenge());
            
            EndpointHealth health = getEndpointHealth(endpoint);
            health.setVerified(verified);
            health.setLastVerification(Instant.now());
            
            if (!verified) {
                log.warn("Endpoint verification failed for {}", maskEndpoint(endpoint));
                securityFailureCount.incrementAndGet();
                securityFailureCounter.increment();
            }
            
            return verified;
            
        } catch (Exception e) {
            log.error("Error processing verify endpoint", e);
            return false;
        }
    }
    
    private boolean processSubscribeWebhook(WebhookNotificationEvent event) {
        try {
            WebhookSubscription subscription = new WebhookSubscription(
                event.getSubscriptionId(),
                event.getEndpoint(),
                event.getEventTypes(),
                event.getSecret(),
                true
            );
            
            subscriptions.put(subscription.getId(), subscription);
            
            // Initialize endpoint health
            endpointHealth.put(event.getEndpoint(), new EndpointHealth(event.getEndpoint()));
            
            log.info("Added webhook subscription {} for endpoint {}", 
                subscription.getId(), maskEndpoint(event.getEndpoint()));
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing subscribe webhook", e);
            return false;
        }
    }
    
    private boolean processUnsubscribeWebhook(WebhookNotificationEvent event) {
        try {
            String subscriptionId = event.getSubscriptionId();
            
            WebhookSubscription subscription = subscriptions.remove(subscriptionId);
            if (subscription != null) {
                log.info("Removed webhook subscription {} for endpoint {}", 
                    subscriptionId, maskEndpoint(subscription.getEndpoint()));
            }
            
            return subscription != null;
            
        } catch (Exception e) {
            log.error("Error processing unsubscribe webhook", e);
            return false;
        }
    }
    
    private boolean processUpdateSubscription(WebhookNotificationEvent event) {
        try {
            String subscriptionId = event.getSubscriptionId();
            WebhookSubscription subscription = subscriptions.get(subscriptionId);
            
            if (subscription != null) {
                if (event.getEventTypes() != null) {
                    subscription.setEventTypes(event.getEventTypes());
                }
                if (event.getSecret() != null) {
                    subscription.setSecret(event.getSecret());
                }
                subscription.setActive(event.isActive());
                
                log.info("Updated webhook subscription {}", subscriptionId);
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error processing update subscription", e);
            return false;
        }
    }
    
    private boolean processWebhookResponse(WebhookNotificationEvent event) {
        try {
            String webhookId = event.getWebhookId();
            WebhookProcessingState state = webhookStates.get(webhookId);
            
            if (state != null) {
                state.setResponseCode(event.getResponseCode());
                state.setResponseTime(event.getResponseTime());
                state.setResponseHeaders(event.getResponseHeaders());
                
                // Update endpoint health based on response
                updateEndpointHealthFromResponse(event.getEndpoint(), event.getResponseCode());
            }
            
            // Track analytics
            trackWebhookAnalytics(event);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing webhook response", e);
            return false;
        }
    }
    
    private boolean processEndpointHealthCheck(WebhookNotificationEvent event) {
        try {
            String endpoint = event.getEndpoint();
            
            boolean healthy = webhookService.checkEndpointHealth(endpoint);
            
            EndpointHealth health = getEndpointHealth(endpoint);
            health.setHealthy(healthy);
            health.setLastHealthCheck(Instant.now());
            
            if (!healthy) {
                health.incrementFailureCount();
                if (health.getFailureCount() >= ENDPOINT_FAILURE_THRESHOLD) {
                    disableEndpoint(endpoint);
                }
            } else {
                health.resetFailureCount();
            }
            
            return healthy;
            
        } catch (Exception e) {
            log.error("Error processing endpoint health check", e);
            return false;
        }
    }
    
    private boolean processBulkDelivery(WebhookNotificationEvent event) {
        try {
            List<WebhookNotificationEvent> webhooks = event.getBulkWebhooks();
            
            if (webhooks.size() > 100) {
                log.warn("Bulk delivery too large: {} webhooks, limiting to 100", webhooks.size());
                webhooks = webhooks.subList(0, 100);
            }
            
            boolean anySuccess = false;
            
            for (WebhookNotificationEvent webhook : webhooks) {
                try {
                    boolean delivered = processDeliverWebhook(webhook);
                    if (delivered) {
                        anySuccess = true;
                    }
                    
                    // Small delay between deliveries
                    Thread.sleep(50);
                    
                } catch (Exception e) {
                    log.error("Error in bulk delivery for webhook {}", webhook.getWebhookId(), e);
                }
            }
            
            return anySuccess;
            
        } catch (Exception e) {
            log.error("Error processing bulk delivery", e);
            return false;
        }
    }
    
    private boolean processScheduledWebhook(WebhookNotificationEvent event) {
        try {
            String scheduleId = event.getScheduleId();
            Instant scheduledTime = event.getScheduledTime();
            
            ScheduledWebhook scheduled = new ScheduledWebhook(scheduleId, event, scheduledTime);
            scheduledWebhooks.put(scheduleId, scheduled);
            
            log.info("Scheduled webhook {} for {}", scheduleId, scheduledTime);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing scheduled webhook", e);
            return false;
        }
    }
    
    private boolean processWebhookAnalytics(WebhookNotificationEvent event) {
        try {
            WebhookAnalytics analytics = getAnalytics(event.getEventType());
            
            analytics.recordDelivery(
                event.getResponseCode() != null && event.getResponseCode() >= 200 && event.getResponseCode() < 300,
                event.getResponseTime() != null ? event.getResponseTime() : 0L,
                event.getEndpoint()
            );
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing webhook analytics", e);
            return false;
        }
    }
    
    private boolean processSecurityScan(WebhookNotificationEvent event) {
        try {
            String endpoint = event.getEndpoint();
            
            SecurityScanResult result = securityService.scanEndpoint(endpoint);
            
            EndpointHealth health = getEndpointHealth(endpoint);
            health.setSecurityScore(result.getSecurityScore());
            health.setLastSecurityScan(Instant.now());
            
            if (result.getSecurityScore() < 0.7) {
                log.warn("Low security score for endpoint {}: {}", 
                    maskEndpoint(endpoint), result.getSecurityScore());
                securityFailureCount.incrementAndGet();
                securityFailureCounter.increment();
            }
            
            return result.getSecurityScore() >= 0.5; // Minimum acceptable score
            
        } catch (Exception e) {
            log.error("Error processing security scan", e);
            return false;
        }
    }
    
    private boolean processDeliveryConfirmation(WebhookNotificationEvent event) {
        try {
            String webhookId = event.getWebhookId();
            
            // Mark as confirmed
            WebhookProcessingState state = webhookStates.get(webhookId);
            if (state != null) {
                state.setStatus("CONFIRMED");
                state.setConfirmationTime(Instant.now());
            }
            
            // Remove from retry queue if present
            scheduledWebhooks.remove(webhookId);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing delivery confirmation", e);
            return false;
        }
    }
    
    @Scheduled(fixedDelay = 10000)
    public void processBatches() {
        activeBatches.values().stream()
            .filter(WebhookBatch::shouldProcess)
            .forEach(this::processBatch);
    }
    
    private boolean processBatch(WebhookBatch batch) {
        try {
            log.info("Processing webhook batch {} with {} webhooks", batch.getId(), batch.getSize());
            
            List<WebhookNotificationEvent> webhooks = batch.getWebhooks();
            
            for (WebhookNotificationEvent webhook : webhooks) {
                try {
                    processDeliverWebhook(webhook);
                } catch (Exception e) {
                    log.error("Error processing webhook in batch {}: {}", batch.getId(), e.getMessage());
                }
            }
            
            activeBatches.remove(batch.getId());
            return true;
            
        } catch (Exception e) {
            log.error("Error processing webhook batch {}", batch.getId(), e);
            return false;
        }
    }
    
    @Scheduled(fixedDelay = 60000)
    public void processScheduledWebhooks() {
        Instant now = Instant.now();
        
        scheduledWebhooks.values().stream()
            .filter(scheduled -> scheduled.getScheduledTime().isBefore(now))
            .forEach(scheduled -> {
                try {
                    processDeliverWebhook(scheduled.getEvent());
                    scheduledWebhooks.remove(scheduled.getId());
                } catch (Exception e) {
                    log.error("Error processing scheduled webhook {}", scheduled.getId(), e);
                }
            });
    }
    
    @Scheduled(fixedDelay = 300000)
    public void updateEndpointHealth() {
        try {
            double deliveryRate = calculateDeliveryRate();
            
            if (deliveryRate < DELIVERY_RATE_THRESHOLD) {
                alertingService.sendAlert(
                    "Low Webhook Delivery Rate",
                    String.format("Current delivery rate: %.2f%%", deliveryRate * 100),
                    AlertingService.AlertSeverity.HIGH
                );
            }
            
            // Check endpoint health
            endpointHealth.values().forEach(this::analyzeEndpointHealth);
            
        } catch (Exception e) {
            log.error("Error updating endpoint health", e);
        }
    }
    
    @Scheduled(fixedDelay = 3600000)
    public void cleanupExpiredData() {
        try {
            Instant expiryTime = Instant.now().minus(Duration.ofDays(7));
            
            webhookStates.entrySet().removeIf(entry -> 
                entry.getValue().getCreatedAt().isBefore(expiryTime));
            
            scheduledWebhooks.entrySet().removeIf(entry ->
                entry.getValue().getScheduledTime().isBefore(expiryTime.minus(Duration.ofDays(1))));
            
            deliveryHistory.entrySet().removeIf(entry ->
                entry.getValue().getLastAttempt().isBefore(expiryTime));
            
            log.info("Cleaned up expired webhook data");
            
        } catch (Exception e) {
            log.error("Error cleaning up expired webhook data", e);
        }
    }
    
    @Scheduled(fixedDelay = 1800000)
    public void analyzeWebhookPerformance() {
        try {
            Map<String, WebhookPerformanceAnalysis> analyses = new HashMap<>();
            
            analyticsData.values().forEach(analytics -> {
                WebhookPerformanceAnalysis analysis = analyzePerformance(analytics);
                analyses.put(analytics.getEventType(), analysis);
            });
            
            analyses.values().stream()
                .filter(analysis -> analysis.hasPerformanceIssues())
                .forEach(analysis -> {
                    log.warn("Performance issues detected for event type {}: {}", 
                        analysis.getEventType(), analysis.getIssues());
                });
                
            generatePerformanceReport(analyses);
            
        } catch (Exception e) {
            log.error("Error analyzing webhook performance", e);
        }
    }
    
    @Scheduled(fixedDelay = 21600000)
    public void verifyEndpointSecurity() {
        try {
            subscriptions.values().stream()
                .filter(WebhookSubscription::isActive)
                .forEach(subscription -> {
                    try {
                        WebhookNotificationEvent securityEvent = new WebhookNotificationEvent();
                        securityEvent.setType("SECURITY_SCAN");
                        securityEvent.setEndpoint(subscription.getEndpoint());
                        processSecurityScan(securityEvent);
                    } catch (Exception e) {
                        log.error("Error in security verification for {}", 
                            maskEndpoint(subscription.getEndpoint()), e);
                    }
                });
            
        } catch (Exception e) {
            log.error("Error verifying endpoint security", e);
        }
    }
    
    private boolean checkRateLimit(String eventType) {
        RateLimitState globalLimit = rateLimits.get("global");
        RateLimitState typeLimit = rateLimits.get(eventType);
        
        return globalLimit.tryAcquire() && 
               (typeLimit == null || typeLimit.tryAcquire());
    }
    
    private void queueForLaterDelivery(WebhookNotificationEvent event) {
        log.info("Queueing webhook {} for later delivery", event.getWebhookId());
        scheduleWebhookDelivery(event, Instant.now().plus(Duration.ofMinutes(5)));
    }
    
    private void scheduleWebhookDelivery(WebhookNotificationEvent event, Instant scheduledTime) {
        ScheduledWebhook scheduled = new ScheduledWebhook(event.getWebhookId(), event, scheduledTime);
        scheduledWebhooks.put(event.getWebhookId(), scheduled);
    }
    
    private boolean isDuplicateDelivery(WebhookNotificationEvent event) {
        DeliveryAttempt lastAttempt = deliveryHistory.get(event.getWebhookId());
        if (lastAttempt == null) return false;
        
        // Consider duplicate if same payload delivered within last 5 minutes
        return lastAttempt.getLastAttempt().isAfter(Instant.now().minus(Duration.ofMinutes(5))) &&
               lastAttempt.isSuccessful();
    }
    
    private WebhookPayload prepareWebhookPayload(WebhookNotificationEvent event) {
        return new WebhookPayload(
            event.getWebhookId(),
            event.getEventType(),
            event.getPayload(),
            Instant.now(),
            event.getVersion()
        );
    }
    
    private Map<String, String> prepareHeaders(WebhookNotificationEvent event) {
        Map<String, String> headers = new HashMap<>();
        
        // Standard headers
        headers.put("Content-Type", "application/json");
        headers.put("User-Agent", "Waqiti-Webhook/1.0");
        headers.put("X-Webhook-ID", event.getWebhookId());
        headers.put("X-Webhook-Event", event.getEventType());
        headers.put("X-Webhook-Timestamp", String.valueOf(Instant.now().getEpochSecond()));
        
        // Custom headers
        if (event.getHeaders() != null) {
            headers.putAll(event.getHeaders());
        }
        
        return headers;
    }
    
    private boolean handleDeliveryResult(WebhookNotificationEvent event, WebhookProcessingState state, WebhookDeliveryResult result) {
        try {
            state.setResponseCode(result.getStatusCode());
            state.setResponseTime(result.getResponseTime());
            state.setDeliveryTime(Instant.now());
            
            // Record delivery attempt
            recordDeliveryAttempt(event, result);
            
            if (result.isSuccess()) {
                deliveredCount.incrementAndGet();
                deliveredCounter.increment();
                state.setStatus("DELIVERED");
                
                // Update endpoint health
                updateEndpointHealthFromResponse(event.getEndpoint(), result.getStatusCode());
                
                // Track analytics
                trackWebhookAnalytics(event, result);
                
                return true;
            } else {
                failedCount.incrementAndGet();
                failedCounter.increment();
                state.setStatus("FAILED");
                state.setFailureReason(result.getErrorMessage());
                
                // Handle specific failure types
                if (result.isTimeout()) {
                    timeoutCount.incrementAndGet();
                    timeoutCounter.increment();
                }
                
                // Update endpoint health
                updateEndpointHealthFromFailure(event.getEndpoint(), result);
                
                // Schedule retry if appropriate
                if (shouldRetryDelivery(event, result)) {
                    scheduleRetryDelivery(event);
                }
                
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error handling delivery result for {}", event.getWebhookId(), e);
            return false;
        }
    }
    
    private void recordDeliveryAttempt(WebhookNotificationEvent event, WebhookDeliveryResult result) {
        DeliveryAttempt attempt = deliveryHistory.computeIfAbsent(event.getWebhookId(),
            k -> new DeliveryAttempt(event.getWebhookId()));
        
        attempt.addAttempt(result.isSuccess(), result.getStatusCode(), result.getErrorMessage());
    }
    
    private boolean shouldRetryDelivery(WebhookNotificationEvent event, WebhookDeliveryResult result) {
        DeliveryAttempt attempt = deliveryHistory.get(event.getWebhookId());
        if (attempt == null || attempt.getAttemptCount() >= MAX_RETRY_ATTEMPTS) {
            return false;
        }
        
        // Retry on temporary failures
        return result.getStatusCode() >= 500 || result.isTimeout() || result.isNetworkError();
    }
    
    private void scheduleRetryDelivery(WebhookNotificationEvent event) {
        DeliveryAttempt attempt = deliveryHistory.get(event.getWebhookId());
        long delay = calculateRetryDelay(attempt != null ? attempt.getAttemptCount() : 0);
        
        scheduleWebhookDelivery(event, Instant.now().plusMillis(delay));
        
        log.info("Scheduled retry for webhook {} in {}ms", event.getWebhookId(), delay);
    }
    
    private long calculateRetryDelay(int attemptCount) {
        long delay = (long) (INITIAL_RETRY_DELAY_MS * Math.pow(RETRY_MULTIPLIER, attemptCount));
        return Math.min(delay, MAX_RETRY_DELAY_MS);
    }
    
    private void handleMaxRetriesExceeded(WebhookNotificationEvent event) {
        log.error("Max retries exceeded for webhook {}", event.getWebhookId());
        
        alertingService.sendAlert(
            "Webhook Max Retries Exceeded",
            String.format("Webhook %s to %s exceeded max retry attempts", 
                event.getWebhookId(), maskEndpoint(event.getEndpoint())),
            AlertingService.AlertSeverity.HIGH
        );
        
        // Mark endpoint as potentially problematic
        EndpointHealth health = getEndpointHealth(event.getEndpoint());
        health.incrementFailureCount();
    }
    
    private String generateWebhookSignature(Object payload, String secret) {
        try {
            String payloadStr = objectMapper.writeValueAsString(payload);
            
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            
            byte[] signature = mac.doFinal(payloadStr.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : signature) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            log.error("Error generating webhook signature", e);
            return "";
        }
    }
    
    private EndpointHealth getEndpointHealth(String endpoint) {
        return endpointHealth.computeIfAbsent(endpoint, k -> new EndpointHealth(endpoint));
    }
    
    private void updateEndpointHealthFromResponse(String endpoint, int statusCode) {
        EndpointHealth health = getEndpointHealth(endpoint);
        
        if (statusCode >= 200 && statusCode < 300) {
            health.setHealthy(true);
            health.resetFailureCount();
        } else if (statusCode >= 400) {
            health.incrementFailureCount();
        }
        
        health.setLastResponse(Instant.now());
    }
    
    private void updateEndpointHealthFromFailure(String endpoint, WebhookDeliveryResult result) {
        EndpointHealth health = getEndpointHealth(endpoint);
        health.incrementFailureCount();
        
        if (result.isTimeout()) {
            health.incrementTimeoutCount();
        }
        
        health.setLastResponse(Instant.now());
    }
    
    private void disableEndpoint(String endpoint) {
        log.warn("Disabling endpoint {} due to excessive failures", maskEndpoint(endpoint));
        
        // Find and disable subscription
        subscriptions.values().stream()
            .filter(sub -> sub.getEndpoint().equals(endpoint))
            .forEach(sub -> sub.setActive(false));
        
        alertingService.sendAlert(
            "Webhook Endpoint Disabled",
            String.format("Endpoint %s disabled due to excessive failures", maskEndpoint(endpoint)),
            AlertingService.AlertSeverity.HIGH
        );
    }
    
    private void analyzeEndpointHealth(EndpointHealth health) {
        if (health.getFailureCount() > ENDPOINT_FAILURE_THRESHOLD / 2) {
            log.warn("Endpoint {} showing degraded performance: {} failures", 
                maskEndpoint(health.getEndpoint()), health.getFailureCount());
        }
        
        if (health.getTimeoutCount() > 5) {
            log.warn("Endpoint {} experiencing timeouts: {} timeouts", 
                maskEndpoint(health.getEndpoint()), health.getTimeoutCount());
        }
    }
    
    private void trackWebhookAnalytics(WebhookNotificationEvent event) {
        trackWebhookAnalytics(event, null);
    }
    
    private void trackWebhookAnalytics(WebhookNotificationEvent event, WebhookDeliveryResult result) {
        WebhookAnalytics analytics = getAnalytics(event.getEventType());
        
        if (result != null) {
            analytics.recordDelivery(result.isSuccess(), result.getResponseTime(), event.getEndpoint());
        }
        
        metricsService.recordMetric(
            "webhook.delivery.success",
            result != null && result.isSuccess() ? 1.0 : 0.0,
            Map.of(
                "eventType", event.getEventType() != null ? event.getEventType() : "unknown",
                "endpoint", maskEndpoint(event.getEndpoint())
            )
        );
    }
    
    private WebhookAnalytics getAnalytics(String eventType) {
        return analyticsData.computeIfAbsent(eventType != null ? eventType : "unknown",
            k -> new WebhookAnalytics(eventType));
    }
    
    private WebhookPerformanceAnalysis analyzePerformance(WebhookAnalytics analytics) {
        return new WebhookPerformanceAnalysis(
            analytics.getEventType(),
            analytics.getSuccessRate(),
            analytics.getAverageResponseTime(),
            analytics.getTimeoutRate(),
            analytics.getPerformanceIssues()
        );
    }
    
    private void generatePerformanceReport(Map<String, WebhookPerformanceAnalysis> analyses) {
        double avgSuccessRate = analyses.values().stream()
            .mapToDouble(WebhookPerformanceAnalysis::getSuccessRate)
            .average()
            .orElse(0.0);
            
        double avgResponseTime = analyses.values().stream()
            .mapToDouble(WebhookPerformanceAnalysis::getAverageResponseTime)
            .average()
            .orElse(0.0);
            
        log.info("Webhook Performance Report - Avg Success Rate: {:.2f}%, Avg Response Time: {:.0f}ms",
            avgSuccessRate * 100, avgResponseTime);
    }
    
    private double calculateDeliveryRate() {
        if (deliveredCount.get() == 0 && failedCount.get() == 0) return 1.0;
        return deliveredCount.get() / (double) (deliveredCount.get() + failedCount.get());
    }
    
    private boolean isValidUrl(String url) {
        try {
            new java.net.URI(url);
            return url.startsWith("http://") || url.startsWith("https://");
        } catch (Exception e) {
            return false;
        }
    }
    
    private String maskEndpoint(String endpoint) {
        if (endpoint == null) return "null";
        
        try {
            java.net.URI uri = new java.net.URI(endpoint);
            return uri.getScheme() + "://" + uri.getHost() + "/***";
        } catch (Exception e) {
            return "***";
        }
    }
    
    // Enhanced error handling methods
    private void handleInvalidEvent(WebhookNotificationEvent event, String rawMessage) {
        log.error("Invalid webhook notification event received: {}", rawMessage);
        
        metricsService.recordMetric(
            "webhook.notifications.invalid",
            1.0,
            Map.of("type", event != null ? event.getType() : "unknown")
        );
    }
    
    private void handleProcessingFailure(WebhookNotificationEvent event, String rawMessage) {
        errorCount.incrementAndGet();
        errorCounter.increment();
        
        log.error("Failed to process webhook notification: {}", event.getWebhookId());
        
        alertingService.sendAlert(
            "Webhook Notification Processing Failed",
            String.format("Failed to process webhook %s", event.getWebhookId()),
            AlertingService.AlertSeverity.MEDIUM
        );
    }
    
    private void handleProcessingError(Exception e, String rawMessage) {
        log.error("Processing error for webhook notification message: {}", rawMessage, e);
        
        if (errorCount.get() > 100) {
            alertingService.sendAlert(
                "High Webhook Processing Error Rate",
                "More than 100 webhook processing errors detected",
                AlertingService.AlertSeverity.HIGH
            );
        }
    }
    
    private void sendToDLQ(String message, String reason) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalMessage", message);
            dlqMessage.put("reason", reason);
            dlqMessage.put("timestamp", Instant.now().toString());
            dlqMessage.put("topic", TOPIC);
            
            kafkaTemplate.send(DLQ_TOPIC, objectMapper.writeValueAsString(dlqMessage));
            log.info("Sent webhook notification message to DLQ with reason: {}", reason);
            
        } catch (Exception e) {
            log.error("Failed to send webhook notification message to DLQ", e);
        }
    }
    
    private boolean isRetryableException(Throwable throwable) {
        return throwable instanceof java.net.SocketTimeoutException ||
               throwable instanceof java.io.IOException ||
               throwable instanceof java.net.ConnectException;
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down WebhookNotificationConsumer");
        
        try {
            scheduledExecutor.shutdown();
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            // Process remaining batches
            activeBatches.values().forEach(this::processBatch);
            
            log.info("WebhookNotificationConsumer shutdown completed. Processed: {}, Errors: {}, Delivered: {}, Failed: {}",
                processedCount.get(), errorCount.get(), deliveredCount.get(), failedCount.get());
                
        } catch (Exception e) {
            log.error("Error during webhook consumer shutdown", e);
        }
    }
}