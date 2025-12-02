package com.waqiti.webhook.service;

import com.waqiti.webhook.dto.CreateWebhookSubscriptionRequest;
import com.waqiti.webhook.dto.WebhookSubscriptionDTO;
import com.waqiti.webhook.model.WebhookEventType;
import com.waqiti.webhook.model.WebhookStatus;
import com.waqiti.webhook.repository.WebhookSubscriptionRepository;
import com.waqiti.webhook.repository.WebhookDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service for managing webhook subscriptions and deliveries
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WebhookManagementService {

    private final WebhookSubscriptionRepository subscriptionRepository;
    private final WebhookDeliveryRepository deliveryRepository;
    private final RestTemplate restTemplate;
    
    @Value("${webhook.service.validation.url-validation-timeout:10000}")
    private int urlValidationTimeout;
    
    @Value("${webhook.service.security.signature-algorithm:HmacSHA256}")
    private String signatureAlgorithm;
    
    @Value("${webhook.service.security.signature-header:X-Webhook-Signature}")
    private String signatureHeader;
    
    @Value("${webhook.service.security.timestamp-header:X-Webhook-Timestamp}")
    private String timestampHeader;

    /**
     * Create a new webhook subscription
     */
    public WebhookSubscriptionDTO createWebhookSubscription(CreateWebhookSubscriptionRequest request) {
        log.info("Creating webhook subscription for user: {} to URL: {}", 
                request.getUserId(), request.getUrl());
        
        // Check if subscription already exists
        Optional<WebhookSubscription> existing = subscriptionRepository
                .findByUserIdAndUrlAndEventTypes(request.getUserId(), request.getUrl(), 
                        new HashSet<>(request.getEventTypes()));
        
        if (existing.isPresent() && existing.get().isActive()) {
            throw new IllegalArgumentException("Active webhook subscription already exists for this URL and events");
        }
        
        // Create new subscription
        WebhookSubscription subscription = WebhookSubscription.builder()
                .id(UUID.randomUUID().toString())
                .userId(request.getUserId())
                .clientId(request.getClientId())
                .url(request.getUrl())
                .secret(request.getSecret())
                .eventTypes(new HashSet<>(request.getEventTypes()))
                .isActive(true)
                .status(WebhookStatus.ACTIVE)
                .description(request.getDescription())
                .headers(request.getHeaders() != null ? request.getHeaders() : new HashMap<>())
                .timeout(request.getTimeout() != null ? request.getTimeout() : 30000)
                .maxRetries(request.getMaxRetries() != null ? request.getMaxRetries() : 3)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .lastDeliveryAt(null)
                .successfulDeliveries(0L)
                .failedDeliveries(0L)
                .build();
        
        WebhookSubscription saved = subscriptionRepository.save(subscription);
        log.info("Webhook subscription created successfully with ID: {}", saved.getId());
        
        return convertToDTO(saved);
    }

    /**
     * Validate webhook URL by sending a test request
     */
    @Async
    public CompletableFuture<Boolean> validateWebhookUrl(String url, String secret) {
        log.debug("Validating webhook URL: {}", url);
        
        try {
            // Create test payload
            Map<String, Object> testPayload = Map.of(
                "event", "webhook.test",
                "timestamp", LocalDateTime.now().toString(),
                "test", true
            );
            
            // Create headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("User-Agent", "Waqiti-Webhook/1.0");
            
            if (secret != null && !secret.isEmpty()) {
                String signature = generateSignature(testPayload.toString(), secret);
                headers.set(signatureHeader, signature);
            }
            
            headers.set(timestampHeader, String.valueOf(System.currentTimeMillis()));
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(testPayload, headers);
            
            // Send test request
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, String.class
            );
            
            boolean isValid = response.getStatusCode().is2xxSuccessful();
            log.debug("Webhook URL validation result for {}: {}", url, isValid);
            return CompletableFuture.completedFuture(isValid);
            
        } catch (Exception e) {
            log.warn("Webhook URL validation failed for {}: {}", url, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Get webhook subscription by ID
     */
    @Transactional(readOnly = true)
    public WebhookSubscriptionDTO getWebhookSubscription(String subscriptionId, String userId) {
        WebhookSubscription subscription = subscriptionRepository
                .findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found"));
        
        return convertToDTO(subscription);
    }

    /**
     * Get user's webhook subscriptions with filtering
     */
    @Transactional(readOnly = true)
    public Page<WebhookSubscriptionDTO> getUserWebhookSubscriptions(
            WebhookSubscriptionFilter filter, Pageable pageable) {
        
        Page<WebhookSubscription> subscriptions = subscriptionRepository
                .findByUserIdWithFilters(filter, pageable);
        
        return subscriptions.map(this::convertToDTO);
    }

    /**
     * Update webhook subscription
     */
    public WebhookSubscriptionDTO updateWebhookSubscription(UpdateWebhookSubscriptionRequest request) {
        WebhookSubscription subscription = subscriptionRepository
                .findById(request.getSubscriptionId())
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found"));
        
        // Update fields if provided
        if (request.getUrl() != null) {
            subscription.setUrl(request.getUrl());
        }
        if (request.getSecret() != null) {
            subscription.setSecret(request.getSecret());
        }
        if (request.getEventTypes() != null) {
            subscription.setEventTypes(new HashSet<>(request.getEventTypes()));
        }
        if (request.getDescription() != null) {
            subscription.setDescription(request.getDescription());
        }
        if (request.getHeaders() != null) {
            subscription.setHeaders(request.getHeaders());
        }
        if (request.getTimeout() != null) {
            subscription.setTimeout(request.getTimeout());
        }
        if (request.getMaxRetries() != null) {
            subscription.setMaxRetries(request.getMaxRetries());
        }
        if (request.getIsActive() != null) {
            subscription.setActive(request.getIsActive());
            subscription.setStatus(request.getIsActive() ? WebhookStatus.ACTIVE : WebhookStatus.DISABLED);
        }
        
        subscription.setUpdatedAt(LocalDateTime.now());
        WebhookSubscription updated = subscriptionRepository.save(subscription);
        
        log.info("Webhook subscription updated: {}", updated.getId());
        return convertToDTO(updated);
    }

    /**
     * Delete webhook subscription
     */
    public void deleteWebhookSubscription(String subscriptionId, String userId) {
        WebhookSubscription subscription = subscriptionRepository
                .findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found"));
        
        subscriptionRepository.delete(subscription);
        log.info("Webhook subscription deleted: {}", subscriptionId);
    }

    /**
     * Test webhook by sending a test payload
     */
    public WebhookTestResult testWebhook(String subscriptionId, String userId, 
                                       Map<String, Object> testPayload) {
        WebhookSubscription subscription = subscriptionRepository
                .findByIdAndUserId(subscriptionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found"));
        
        try {
            // Use provided payload or create default
            Map<String, Object> payload = testPayload != null ? testPayload : Map.of(
                "event", "webhook.test",
                "timestamp", LocalDateTime.now().toString(),
                "subscription_id", subscriptionId,
                "test", true
            );
            
            // Send webhook
            boolean success = deliverWebhook(subscription, payload, "webhook.test");
            
            return WebhookTestResult.builder()
                    .success(success)
                    .subscriptionId(subscriptionId)
                    .timestamp(LocalDateTime.now())
                    .payload(payload)
                    .errorMessage(success ? null : "Test webhook delivery failed")
                    .build();
                    
        } catch (Exception e) {
            log.error("Test webhook failed for subscription: {}", subscriptionId, e);
            return WebhookTestResult.builder()
                    .success(false)
                    .subscriptionId(subscriptionId)
                    .timestamp(LocalDateTime.now())
                    .errorMessage("Test failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Get webhook deliveries with filtering
     */
    @Transactional(readOnly = true)
    public Page<WebhookDeliveryDTO> getWebhookDeliveries(WebhookDeliveryFilter filter, 
                                                        String userId, Pageable pageable) {
        // Verify user owns the subscription
        WebhookSubscription subscription = subscriptionRepository
                .findByIdAndUserId(filter.getSubscriptionId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook subscription not found"));
        
        Page<WebhookDelivery> deliveries = deliveryRepository
                .findBySubscriptionIdWithFilters(filter, pageable);
        
        return deliveries.map(this::convertDeliveryToDTO);
    }

    /**
     * Get webhook statistics
     */
    @Transactional(readOnly = true)
    public WebhookStatistics getWebhookStatistics(WebhookStatisticsRequest request) {
        List<WebhookSubscription> subscriptions;
        
        if (request.getSubscriptionId() != null) {
            WebhookSubscription subscription = subscriptionRepository
                    .findByIdAndUserId(request.getSubscriptionId(), request.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
            subscriptions = List.of(subscription);
        } else {
            subscriptions = subscriptionRepository.findByUserId(request.getUserId());
        }
        
        // Calculate statistics
        long totalSubscriptions = subscriptions.size();
        long activeSubscriptions = subscriptions.stream()
                .mapToLong(s -> s.isActive() ? 1 : 0)
                .sum();
        
        long totalDeliveries = subscriptions.stream()
                .mapToLong(s -> s.getSuccessfulDeliveries() + s.getFailedDeliveries())
                .sum();
        
        long successfulDeliveries = subscriptions.stream()
                .mapToLong(WebhookSubscription::getSuccessfulDeliveries)
                .sum();
        
        long failedDeliveries = subscriptions.stream()
                .mapToLong(WebhookSubscription::getFailedDeliveries)
                .sum();
        
        double successRate = totalDeliveries > 0 ? 
                (double) successfulDeliveries / totalDeliveries * 100 : 0.0;
        
        return WebhookStatistics.builder()
                .totalSubscriptions(totalSubscriptions)
                .activeSubscriptions(activeSubscriptions)
                .totalDeliveries(totalDeliveries)
                .successfulDeliveries(successfulDeliveries)
                .failedDeliveries(failedDeliveries)
                .successRate(successRate)
                .periodStart(request.getStartDate())
                .periodEnd(request.getEndDate())
                .build();
    }

    /**
     * Bulk update webhook subscriptions
     */
    public BulkWebhookUpdateResult bulkUpdateWebhooks(BulkWebhookUpdateRequest request) {
        List<String> successIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        
        for (String subscriptionId : request.getSubscriptionIds()) {
            try {
                WebhookSubscription subscription = subscriptionRepository
                        .findById(subscriptionId)
                        .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
                
                // Apply updates
                if (request.getIsActive() != null) {
                    subscription.setActive(request.getIsActive());
                    subscription.setStatus(request.getIsActive() ? 
                        WebhookStatus.ACTIVE : WebhookStatus.DISABLED);
                }
                if (request.getMaxRetries() != null) {
                    subscription.setMaxRetries(request.getMaxRetries());
                }
                if (request.getTimeout() != null) {
                    subscription.setTimeout(request.getTimeout());
                }
                
                subscription.setUpdatedAt(LocalDateTime.now());
                subscriptionRepository.save(subscription);
                successIds.add(subscriptionId);
                
            } catch (Exception e) {
                log.error("Failed to update webhook subscription: {}", subscriptionId, e);
                failedIds.add(subscriptionId);
            }
        }
        
        return BulkWebhookUpdateResult.builder()
                .successCount(successIds.size())
                .failureCount(failedIds.size())
                .successIds(successIds)
                .failedIds(failedIds)
                .build();
    }

    /**
     * Get available webhook event types
     */
    public List<WebhookEventTypeInfo> getWebhookEventTypes() {
        return Arrays.stream(WebhookEventType.values())
                .map(eventType -> WebhookEventTypeInfo.builder()
                        .type(eventType.name())
                        .description(getEventTypeDescription(eventType))
                        .category(getEventTypeCategory(eventType))
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Verify webhook signature
     */
    public boolean verifyWebhookSignature(String subscriptionId, Map<String, Object> payload, 
                                        String signature) {
        try {
            WebhookSubscription subscription = subscriptionRepository
                    .findById(subscriptionId)
                    .orElseThrow(() -> new IllegalArgumentException("Subscription not found"));
            
            if (subscription.getSecret() == null || subscription.getSecret().isEmpty()) {
                return true; // No signature verification required
            }
            
            String expectedSignature = generateSignature(payload.toString(), subscription.getSecret());
            return signature.equals(expectedSignature);
            
        } catch (Exception e) {
            log.error("Error verifying webhook signature: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Process incoming webhook
     */
    public void processIncomingWebhook(String subscriptionId, Map<String, Object> payload, 
                                     HttpServletRequest request) {
        log.info("Processing incoming webhook for subscription: {}", subscriptionId);
        
        // Log the webhook for monitoring/debugging
        WebhookDelivery delivery = WebhookDelivery.builder()
                .id(UUID.randomUUID().toString())
                .subscriptionId(subscriptionId)
                .eventType("incoming_webhook")
                .payload(payload)
                .status(WebhookStatus.COMPLETED)
                .deliveredAt(LocalDateTime.now())
                .responseCode(200)
                .responseBody("Processed successfully")
                .attempts(1)
                .build();
        
        deliveryRepository.save(delivery);
    }

    // Private helper methods

    private boolean deliverWebhook(WebhookSubscription subscription, Map<String, Object> payload, 
                                 String eventType) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("User-Agent", "Waqiti-Webhook/1.0");
            
            // Add custom headers
            if (subscription.getHeaders() != null) {
                subscription.getHeaders().forEach(headers::set);
            }
            
            // Add signature if secret is configured
            if (subscription.getSecret() != null && !subscription.getSecret().isEmpty()) {
                String signature = generateSignature(payload.toString(), subscription.getSecret());
                headers.set(signatureHeader, signature);
            }
            
            headers.set(timestampHeader, String.valueOf(System.currentTimeMillis()));
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                subscription.getUrl(), HttpMethod.POST, entity, String.class
            );
            
            // Record delivery
            recordDelivery(subscription, payload, eventType, response, true);
            return response.getStatusCode().is2xxSuccessful();
            
        } catch (Exception e) {
            log.error("Failed to deliver webhook to {}: {}", subscription.getUrl(), e.getMessage());
            recordDelivery(subscription, payload, eventType, null, false);
            return false;
        }
    }

    private void recordDelivery(WebhookSubscription subscription, Map<String, Object> payload, 
                              String eventType, ResponseEntity<String> response, boolean success) {
        WebhookDelivery delivery = WebhookDelivery.builder()
                .id(UUID.randomUUID().toString())
                .subscriptionId(subscription.getId())
                .eventType(eventType)
                .payload(payload)
                .status(success ? WebhookStatus.COMPLETED : WebhookStatus.FAILED)
                .deliveredAt(LocalDateTime.now())
                .responseCode(response != null ? response.getStatusCode().value() : 0)
                .responseBody(response != null ? response.getBody() : "")
                .attempts(1)
                .build();
        
        deliveryRepository.save(delivery);
        
        // Update subscription statistics
        if (success) {
            subscription.setSuccessfulDeliveries(subscription.getSuccessfulDeliveries() + 1);
        } else {
            subscription.setFailedDeliveries(subscription.getFailedDeliveries() + 1);
        }
        subscription.setLastDeliveryAt(LocalDateTime.now());
        subscriptionRepository.save(subscription);
    }

    private String generateSignature(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance(signatureAlgorithm);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), 
                                                          signatureAlgorithm);
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Error generating webhook signature: {}", e.getMessage());
            return "";
        }
    }

    private WebhookSubscriptionDTO convertToDTO(WebhookSubscription subscription) {
        return WebhookSubscriptionDTO.builder()
                .id(subscription.getId())
                .userId(subscription.getUserId())
                .clientId(subscription.getClientId())
                .url(subscription.getUrl())
                .eventTypes(new ArrayList<>(subscription.getEventTypes()))
                .isActive(subscription.isActive())
                .status(subscription.getStatus())
                .description(subscription.getDescription())
                .headers(subscription.getHeaders())
                .timeout(subscription.getTimeout())
                .maxRetries(subscription.getMaxRetries())
                .createdAt(subscription.getCreatedAt())
                .updatedAt(subscription.getUpdatedAt())
                .lastDeliveryAt(subscription.getLastDeliveryAt())
                .successfulDeliveries(subscription.getSuccessfulDeliveries())
                .failedDeliveries(subscription.getFailedDeliveries())
                .build();
    }

    private WebhookDeliveryDTO convertDeliveryToDTO(WebhookDelivery delivery) {
        return WebhookDeliveryDTO.builder()
                .id(delivery.getId())
                .subscriptionId(delivery.getSubscriptionId())
                .eventType(delivery.getEventType())
                .payload(delivery.getPayload())
                .status(delivery.getStatus())
                .deliveredAt(delivery.getDeliveredAt())
                .responseCode(delivery.getResponseCode())
                .responseBody(delivery.getResponseBody())
                .attempts(delivery.getAttempts())
                .build();
    }

    private String getEventTypeDescription(WebhookEventType eventType) {
        switch (eventType) {
            case PAYMENT_CREATED: return "Triggered when a new payment is created";
            case PAYMENT_COMPLETED: return "Triggered when a payment is successfully completed";
            case PAYMENT_FAILED: return "Triggered when a payment fails";
            case USER_REGISTERED: return "Triggered when a new user registers";
            case TRANSACTION_COMPLETED: return "Triggered when a transaction is completed";
            case DISPUTE_CREATED: return "Triggered when a dispute is created";
            default: return "Event type: " + eventType.name();
        }
    }

    private String getEventTypeCategory(WebhookEventType eventType) {
        String name = eventType.name();
        if (name.startsWith("PAYMENT")) return "Payment";
        if (name.startsWith("USER")) return "User";
        if (name.startsWith("TRANSACTION")) return "Transaction";
        if (name.startsWith("DISPUTE")) return "Dispute";
        if (name.startsWith("SECURITY")) return "Security";
        return "General";
    }
}