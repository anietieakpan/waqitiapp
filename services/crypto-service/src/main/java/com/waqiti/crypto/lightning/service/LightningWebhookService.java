package com.waqiti.crypto.lightning.service;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ErrorCode;
import com.waqiti.crypto.lightning.entity.*;
import com.waqiti.crypto.lightning.repository.WebhookRepository;
import com.waqiti.crypto.lightning.repository.WebhookDeliveryRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Production-grade Lightning webhook service for real-time event notifications
 * Handles webhook registration, event delivery, retries, and monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class LightningWebhookService {

    private final WebhookRepository webhookRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final MeterRegistry meterRegistry;
    
    @Value("${waqiti.lightning.webhook.max-retries:3}")
    private int maxRetries;
    
    @Value("${waqiti.lightning.webhook.timeout-seconds:30}")
    private int timeoutSeconds;
    
    @Value("${waqiti.lightning.webhook.batch-size:50}")
    private int batchSize;
    
    @Value("${waqiti.lightning.webhook.enable-signature:true}")
    private boolean enableSignature;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();
    
    private final ExecutorService webhookExecutor = Executors.newFixedThreadPool(10);
    private final ConcurrentHashMap<String, WebhookRateLimit> rateLimits = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    private Counter webhookDeliveredCounter;
    private Counter webhookFailedCounter;
    private Timer webhookDeliveryTimer;

    @jakarta.annotation.PostConstruct
    public void init() {
        webhookDeliveredCounter = Counter.builder("lightning.webhook.delivered")
            .description("Number of webhooks delivered successfully")
            .register(meterRegistry);
            
        webhookFailedCounter = Counter.builder("lightning.webhook.failed")
            .description("Number of webhook deliveries that failed")
            .register(meterRegistry);
            
        webhookDeliveryTimer = Timer.builder("lightning.webhook.delivery.duration")
            .description("Webhook delivery duration")
            .register(meterRegistry);
    }

    /**
     * Register a new webhook for Lightning events
     */
    public String registerWebhook(String userId, String url, Set<WebhookEventType> events, String secret) {
        log.info("Registering webhook for user: {}, events: {}", userId, events);
        
        // Validate webhook URL
        validateWebhookUrl(url);
        
        // Generate webhook ID and secret if not provided
        String webhookId = UUID.randomUUID().toString();
        String webhookSecret = secret != null ? secret : generateWebhookSecret();
        
        // Create webhook entity
        WebhookEntity webhook = WebhookEntity.builder()
            .id(webhookId)
            .userId(userId)
            .url(url)
            .events(events)
            .secret(webhookSecret)
            .status(WebhookStatus.ACTIVE)
            .createdAt(Instant.now())
            .deliveryCount(0)
            .failureCount(0)
            .build();
        
        webhook = webhookRepository.save(webhook);
        
        // Test webhook delivery
        if (events.contains(WebhookEventType.WEBHOOK_TEST)) {
            testWebhookDelivery(webhook);
        }
        
        log.info("Webhook registered successfully: {}", webhookId);
        return webhookId;
    }

    /**
     * Register webhook for specific payment hash
     */
    public void registerWebhook(String paymentHash, String webhookUrl) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            return;
        }
        
        // Create temporary webhook for this specific payment
        String webhookId = "payment_" + paymentHash.substring(0, 8);
        
        WebhookEntity webhook = WebhookEntity.builder()
            .id(webhookId)
            .userId("system")
            .url(webhookUrl.trim())
            .events(Set.of(WebhookEventType.PAYMENT_RECEIVED, WebhookEventType.PAYMENT_FAILED))
            .status(WebhookStatus.ACTIVE)
            .paymentHash(paymentHash)
            .createdAt(Instant.now())
            .expiresAt(Instant.now().plus(Duration.ofHours(24)))
            .build();
        
        webhookRepository.save(webhook);
        log.info("Temporary webhook registered for payment: {}", paymentHash);
    }

    /**
     * Delete a webhook
     */
    public void deleteWebhook(String webhookId, String userId) {
        WebhookEntity webhook = webhookRepository.findByIdAndUserId(webhookId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RES_NOT_FOUND, "Webhook not found"));
        
        webhook.setStatus(WebhookStatus.INACTIVE);
        webhook.setUpdatedAt(Instant.now());
        webhookRepository.save(webhook);
        
        log.info("Webhook {} deleted for user: {}", webhookId, userId);
    }

    /**
     * Update webhook configuration
     */
    public WebhookEntity updateWebhook(String webhookId, String userId, String url, 
                                       Set<WebhookEventType> events) {
        WebhookEntity webhook = webhookRepository.findByIdAndUserId(webhookId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.RES_NOT_FOUND, "Webhook not found"));
        
        if (url != null) {
            validateWebhookUrl(url);
            webhook.setUrl(url);
        }
        
        if (events != null && !events.isEmpty()) {
            webhook.setEvents(events);
        }
        
        webhook.setUpdatedAt(Instant.now());
        return webhookRepository.save(webhook);
    }

    /**
     * Notify webhook about payment success
     */
    @Async
    public void notifyPaymentSuccess(PaymentEntity payment) {
        log.debug("Notifying payment success: {}", payment.getId());
        
        WebhookPayload payload = WebhookPayload.builder()
            .eventType(WebhookEventType.PAYMENT_RECEIVED)
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .data(Map.of(
                "paymentId", payment.getId(),
                "paymentHash", payment.getPaymentHash(),
                "amountSat", payment.getAmountSat(),
                "status", "completed",
                "completedAt", payment.getCompletedAt()
            ))
            .build();
        
        deliverWebhooks(payment.getUserId(), payload, payment.getPaymentHash());
    }

    /**
     * Notify webhook about payment failure
     */
    @Async
    public void notifyPaymentFailure(String userId, String paymentHash, String error) {
        log.debug("Notifying payment failure: {}", paymentHash);
        
        WebhookPayload payload = WebhookPayload.builder()
            .eventType(WebhookEventType.PAYMENT_FAILED)
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .data(Map.of(
                "paymentHash", paymentHash,
                "status", "failed",
                "error", error,
                "failedAt", Instant.now()
            ))
            .build();
        
        deliverWebhooks(userId, payload, paymentHash);
    }

    /**
     * Notify webhook about invoice settlement
     */
    @Async
    public void notifyInvoiceSettled(InvoiceEntity invoice) {
        log.debug("Notifying invoice settled: {}", invoice.getId());
        
        WebhookPayload payload = WebhookPayload.builder()
            .eventType(WebhookEventType.INVOICE_SETTLED)
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .data(Map.of(
                "invoiceId", invoice.getId(),
                "paymentHash", invoice.getPaymentHash(),
                "amountSat", invoice.getAmountSat(),
                "amountPaidSat", invoice.getAmountPaidSat(),
                "settledAt", invoice.getSettledAt()
            ))
            .build();
        
        deliverWebhooks(invoice.getUserId(), payload, null);
    }

    /**
     * Notify webhook about channel events
     */
    @Async
    public void notifyChannelEvent(ChannelEntity channel, WebhookEventType eventType) {
        log.debug("Notifying channel event: {} for channel: {}", eventType, channel.getId());
        
        WebhookPayload payload = WebhookPayload.builder()
            .eventType(eventType)
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .data(Map.of(
                "channelId", channel.getId(),
                "remotePubkey", channel.getRemotePubkey(),
                "capacity", channel.getCapacity(),
                "localBalance", channel.getLocalBalance(),
                "remoteBalance", channel.getRemoteBalance(),
                "status", channel.getStatus().toString()
            ))
            .build();
        
        deliverWebhooks(channel.getUserId(), payload, null);
    }

    /**
     * Process pending webhook deliveries
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void processPendingDeliveries() {
        log.debug("Processing pending webhook deliveries");
        
        List<WebhookDeliveryEntity> pendingDeliveries = deliveryRepository
            .findPendingDeliveries(Instant.now().minus(Duration.ofMinutes(1)), batchSize);
        
        for (WebhookDeliveryEntity delivery : pendingDeliveries) {
            try {
                retryWebhookDelivery(delivery);
            } catch (Exception e) {
                log.error("Error processing webhook delivery: {}", delivery.getId(), e);
            }
        }
    }

    /**
     * Clean up old webhook deliveries
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    @Transactional
    public void cleanupOldDeliveries() {
        log.info("Cleaning up old webhook deliveries");
        
        Instant cutoffDate = Instant.now().minus(Duration.ofDays(30));
        int deletedCount = deliveryRepository.deleteOldDeliveries(cutoffDate);
        
        log.info("Deleted {} old webhook deliveries", deletedCount);
    }

    /**
     * Clean up expired webhooks
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    @Transactional
    public void cleanupExpiredWebhooks() {
        log.debug("Cleaning up expired webhooks");
        
        List<WebhookEntity> expiredWebhooks = webhookRepository.findExpiredWebhooks(Instant.now());
        
        for (WebhookEntity webhook : expiredWebhooks) {
            webhook.setStatus(WebhookStatus.INACTIVE);
            webhookRepository.save(webhook);
            log.info("Expired webhook: {}", webhook.getId());
        }
    }

    // Event listeners for automatic webhook notifications

    @EventListener
    @Async
    public void handlePaymentCompletedEvent(PaymentCompletedEvent event) {
        notifyPaymentSuccess(event.getPayment());
    }

    @EventListener
    @Async
    public void handleInvoiceSettledEvent(InvoiceSettledEvent event) {
        notifyInvoiceSettled(event.getInvoice());
    }

    @EventListener
    @Async
    public void handleChannelOpenedEvent(ChannelOpenedEvent event) {
        notifyChannelEvent(event.getChannel(), WebhookEventType.CHANNEL_OPENED);
    }

    @EventListener
    @Async
    public void handleChannelClosedEvent(ChannelClosedEvent event) {
        notifyChannelEvent(event.getChannel(), WebhookEventType.CHANNEL_CLOSED);
    }

    // Private helper methods

    private void deliverWebhooks(String userId, WebhookPayload payload, String paymentHash) {
        List<WebhookEntity> webhooks = findApplicableWebhooks(userId, payload.getEventType(), paymentHash);
        
        for (WebhookEntity webhook : webhooks) {
            if (shouldDeliverWebhook(webhook, payload)) {
                CompletableFuture.runAsync(() -> deliverWebhook(webhook, payload), webhookExecutor);
            }
        }
    }

    private List<WebhookEntity> findApplicableWebhooks(String userId, WebhookEventType eventType, String paymentHash) {
        List<WebhookEntity> webhooks = new ArrayList<>();
        
        // User-specific webhooks
        webhooks.addAll(webhookRepository.findByUserIdAndEventType(userId, eventType));
        
        // Payment-specific webhooks
        if (paymentHash != null) {
            webhookRepository.findByPaymentHash(paymentHash).ifPresent(webhooks::add);
        }
        
        return webhooks.stream()
            .filter(w -> w.getStatus() == WebhookStatus.ACTIVE)
            .filter(w -> w.getExpiresAt() == null || w.getExpiresAt().isAfter(Instant.now()))
            .toList();
    }

    private boolean shouldDeliverWebhook(WebhookEntity webhook, WebhookPayload payload) {
        // Check rate limiting
        WebhookRateLimit rateLimit = rateLimits.computeIfAbsent(webhook.getId(), 
            k -> new WebhookRateLimit());
        
        if (!rateLimit.allowRequest()) {
            log.warn("Rate limit exceeded for webhook: {}", webhook.getId());
            return false;
        }
        
        // Check if webhook supports this event type
        return webhook.getEvents().contains(payload.getEventType());
    }

    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    private void deliverWebhook(WebhookEntity webhook, WebhookPayload payload) {
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            log.debug("Delivering webhook: {} for event: {}", webhook.getId(), payload.getEventType());
            
            // Create delivery record
            WebhookDeliveryEntity delivery = WebhookDeliveryEntity.builder()
                .id(UUID.randomUUID().toString())
                .webhookId(webhook.getId())
                .eventType(payload.getEventType())
                .payload(serializePayload(payload))
                .status(WebhookDeliveryStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build();
            
            delivery = deliveryRepository.save(delivery);
            
            // Send HTTP request
            boolean success = sendWebhookRequest(webhook, payload, delivery);
            
            // Update delivery status
            if (success) {
                delivery.setStatus(WebhookDeliveryStatus.DELIVERED);
                delivery.setDeliveredAt(Instant.now());
                webhook.setDeliveryCount(webhook.getDeliveryCount() + 1);
                webhook.setLastSuccessAt(Instant.now());
                webhookDeliveredCounter.increment();
            } else {
                delivery.setStatus(WebhookDeliveryStatus.FAILED);
                webhook.setFailureCount(webhook.getFailureCount() + 1);
                webhook.setLastFailureAt(Instant.now());
                webhookFailedCounter.increment();
                
                // Disable webhook after too many failures
                if (webhook.getFailureCount() > 50) {
                    webhook.setStatus(WebhookStatus.SUSPENDED);
                    log.warn("Webhook {} suspended due to too many failures", webhook.getId());
                }
            }
            
            deliveryRepository.save(delivery);
            webhookRepository.save(webhook);
            
        } catch (Exception e) {
            log.error("Error delivering webhook: {}", webhook.getId(), e);
            webhookFailedCounter.increment();
            throw e;
        } finally {
            sample.stop(webhookDeliveryTimer);
        }
    }

    private boolean sendWebhookRequest(WebhookEntity webhook, WebhookPayload payload, 
                                       WebhookDeliveryEntity delivery) {
        try {
            String jsonPayload = serializePayload(payload);
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(webhook.getUrl()))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Waqiti-Lightning-Webhook/1.0")
                .header("X-Webhook-Id", webhook.getId())
                .header("X-Event-Type", payload.getEventType().toString())
                .header("X-Delivery-Id", delivery.getId())
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload));
            
            // Add signature header if enabled
            if (enableSignature && webhook.getSecret() != null) {
                String signature = generateSignature(jsonPayload, webhook.getSecret());
                requestBuilder.header("X-Waqiti-Signature", signature);
            }
            
            HttpRequest request = requestBuilder.build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Update delivery with response
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setResponseCode(response.statusCode());
            delivery.setResponseBody(response.body().length() > 1000 ? 
                response.body().substring(0, 1000) : response.body());
            delivery.setAttemptedAt(Instant.now());
            
            // Consider 2xx status codes as successful
            boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
            
            log.debug("Webhook delivery response: {} - {}", response.statusCode(), 
                success ? "SUCCESS" : "FAILED");
            
            return success;
            
        } catch (Exception e) {
            log.warn("Webhook request failed: {}", e.getMessage());
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setResponseCode(0);
            delivery.setResponseBody("Request failed: " + e.getMessage());
            delivery.setAttemptedAt(Instant.now());
            return false;
        }
    }

    private void retryWebhookDelivery(WebhookDeliveryEntity delivery) {
        if (delivery.getAttemptCount() >= maxRetries) {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            deliveryRepository.save(delivery);
            log.warn("Webhook delivery failed after {} attempts: {}", maxRetries, delivery.getId());
            return;
        }
        
        WebhookEntity webhook = webhookRepository.findById(delivery.getWebhookId())
            .orElse(null);
            
        if (webhook == null || webhook.getStatus() != WebhookStatus.ACTIVE) {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            deliveryRepository.save(delivery);
            return;
        }
        
        // Recreate payload from stored data
        WebhookPayload payload = deserializePayload(delivery.getPayload(), delivery.getEventType());
        
        boolean success = sendWebhookRequest(webhook, payload, delivery);
        
        if (success) {
            delivery.setStatus(WebhookDeliveryStatus.DELIVERED);
            delivery.setDeliveredAt(Instant.now());
        } else if (delivery.getAttemptCount() >= maxRetries) {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
        }
        
        deliveryRepository.save(delivery);
    }

    private void testWebhookDelivery(WebhookEntity webhook) {
        WebhookPayload testPayload = WebhookPayload.builder()
            .eventType(WebhookEventType.WEBHOOK_TEST)
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .data(Map.of(
                "message", "This is a test webhook from Waqiti Lightning service",
                "webhookId", webhook.getId(),
                "timestamp", Instant.now().toString()
            ))
            .build();
        
        CompletableFuture.runAsync(() -> deliverWebhook(webhook, testPayload), webhookExecutor);
    }

    private void validateWebhookUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!uri.getScheme().matches("https?")) {
                throw new BusinessException(ErrorCode.VAL_INVALID_FORMAT, 
                    "Webhook URL must use HTTP or HTTPS");
            }
            if (uri.getHost() == null) {
                throw new BusinessException(ErrorCode.VAL_INVALID_FORMAT, 
                    "Invalid webhook URL format");
            }
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VAL_INVALID_FORMAT, 
                "Invalid webhook URL: " + e.getMessage());
        }
    }

    private String generateWebhookSecret() {
        byte[] secretBytes = new byte[32];
        secureRandom.nextBytes(secretBytes);
        return Base64.getEncoder().encodeToString(secretBytes);
    }

    private String generateSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to generate Lightning webhook signature - Security compromised", e);
            throw new RuntimeException("Failed to generate webhook signature for Lightning payment", e);
        }
    }

    private String serializePayload(WebhookPayload payload) {
        // Simple JSON serialization for webhook payload
        try {
            Map<String, Object> json = new HashMap<>();
            json.put("eventType", payload.getEventType().toString());
            json.put("eventId", payload.getEventId());
            json.put("timestamp", payload.getTimestamp().toString());
            json.put("data", payload.getData());
            
            // Convert to JSON string (in production, use Jackson)
            StringBuilder jsonBuilder = new StringBuilder();
            jsonBuilder.append("{");
            jsonBuilder.append("\"eventType\":\"").append(payload.getEventType()).append("\",");
            jsonBuilder.append("\"eventId\":\"").append(payload.getEventId()).append("\",");
            jsonBuilder.append("\"timestamp\":\"").append(payload.getTimestamp()).append("\",");
            jsonBuilder.append("\"data\":").append(mapToJsonString(payload.getData()));
            jsonBuilder.append("}");
            
            return jsonBuilder.toString();
        } catch (Exception e) {
            log.error("Error serializing webhook payload", e);
            return "{}";
        }
    }

    private WebhookPayload deserializePayload(String json, WebhookEventType eventType) {
        // Simple JSON deserialization (in production, use Jackson)
        return WebhookPayload.builder()
            .eventType(eventType)
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .data(Map.of("raw", json))
            .build();
    }

    private String mapToJsonString(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        
        StringBuilder json = new StringBuilder();
        json.append("{");
        
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value.toString()).append("\"");
            }
            first = false;
        }
        
        json.append("}");
        return json.toString();
    }

    /**
     * Rate limiting for webhooks
     */
    private static class WebhookRateLimit {
        private final int maxRequests = 100;
        private final Duration window = Duration.ofMinutes(1);
        private final Queue<Instant> requests = new LinkedList<>();
        
        public boolean allowRequest() {
            Instant now = Instant.now();
            Instant windowStart = now.minus(window);
            
            // Remove old requests
            while (!requests.isEmpty() && requests.peek().isBefore(windowStart)) {
                requests.poll();
            }
            
            if (requests.size() >= maxRequests) {
                return false;
            }
            
            requests.offer(now);
            return true;
        }
    }

    /**
     * Webhook payload for delivery
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class WebhookPayload {
        private WebhookEventType eventType;
        private String eventId;
        private Instant timestamp;
        private Map<String, Object> data;
    }

    // Event classes for Spring event publishing

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class PaymentCompletedEvent {
        private PaymentEntity payment;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class InvoiceSettledEvent {
        private InvoiceEntity invoice;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ChannelOpenedEvent {
        private ChannelEntity channel;
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ChannelClosedEvent {
        private ChannelEntity channel;
    }
}