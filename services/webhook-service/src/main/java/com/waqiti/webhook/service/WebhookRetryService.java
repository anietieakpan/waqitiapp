package com.waqiti.webhook.service;

import com.waqiti.webhook.model.WebhookStatus;
import com.waqiti.webhook.repository.WebhookDeliveryRepository;
import com.waqiti.webhook.repository.WebhookSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for handling webhook retry logic and failed deliveries
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class WebhookRetryService {
    
    @Lazy
    private final WebhookRetryService self;

    private final WebhookDeliveryRepository deliveryRepository;
    private final WebhookSubscriptionRepository subscriptionRepository;
    private final RestTemplate restTemplate;
    
    @Value("${webhook.service.delivery.max-retry-attempts:5}")
    private int maxRetryAttempts;
    
    @Value("${webhook.service.delivery.initial-retry-delay:1000}")
    private long initialRetryDelay;
    
    @Value("${webhook.service.delivery.max-retry-delay:300000}")
    private long maxRetryDelay;
    
    @Value("${webhook.service.delivery.retry-multiplier:2.0}")
    private double retryMultiplier;
    
    @Value("${webhook.service.delivery.timeout:30000}")
    private int deliveryTimeout;
    
    @Value("${webhook.service.delivery.batch-size:100}")
    private int batchSize;

    private final AtomicInteger healthStatus = new AtomicInteger(1); // 1 = healthy, 0 = unhealthy

    /**
     * Retry a failed webhook delivery
     */
    public WebhookRetryResult retryDelivery(String deliveryId, String userId, boolean forceRetry) {
        log.info("Retrying webhook delivery: {} by user: {}, force: {}", deliveryId, userId, forceRetry);
        
        try {
            WebhookDelivery delivery = deliveryRepository.findById(deliveryId)
                    .orElseThrow(() -> new IllegalArgumentException("Delivery not found"));
            
            // Verify user ownership
            WebhookSubscription subscription = subscriptionRepository
                    .findByIdAndUserId(delivery.getSubscriptionId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Subscription not found or access denied"));
            
            // Check if retry is possible
            if (!forceRetry && delivery.getAttempts() >= maxRetryAttempts) {
                return WebhookRetryResult.builder()
                        .success(false)
                        .deliveryId(deliveryId)
                        .errorMessage("Maximum retry attempts exceeded")
                        .attemptsRemaining(0)
                        .build();
            }
            
            if (!forceRetry && delivery.getStatus() == WebhookStatus.COMPLETED) {
                return WebhookRetryResult.builder()
                        .success(false)
                        .deliveryId(deliveryId)
                        .errorMessage("Delivery already completed successfully")
                        .attemptsRemaining(maxRetryAttempts - delivery.getAttempts())
                        .build();
            }
            
            // Perform retry
            CompletableFuture<Boolean> retryFuture = performRetryAsync(delivery, subscription);
            boolean retrySuccess = retryFuture.get();
            
            return WebhookRetryResult.builder()
                    .success(retrySuccess)
                    .deliveryId(deliveryId)
                    .timestamp(LocalDateTime.now())
                    .attemptsRemaining(Math.max(0, maxRetryAttempts - delivery.getAttempts()))
                    .errorMessage(retrySuccess ? null : "Retry attempt failed")
                    .build();
                    
        } catch (Exception e) {
            log.error("Error retrying webhook delivery: {}", deliveryId, e);
            return WebhookRetryResult.builder()
                    .success(false)
                    .deliveryId(deliveryId)
                    .errorMessage("Retry failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * Get failed deliveries that need retry
     */
    @Transactional(readOnly = true)
    public Page<WebhookDeliveryDTO> getFailedDeliveries(FailedDeliveryFilter filter, Pageable pageable) {
        Page<WebhookDelivery> failedDeliveries = deliveryRepository
                .findFailedDeliveries(filter, pageable);
        
        return failedDeliveries.map(this::convertToDTO);
    }

    /**
     * Process webhook retries in batch
     */
    public WebhookRetryProcessResult processRetries(int batchSize) {
        log.info("Processing webhook retries with batch size: {}", batchSize);
        
        List<WebhookDelivery> failedDeliveries = deliveryRepository
                .findFailedDeliveriesForRetry(batchSize);
        
        AtomicInteger processedCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failedCount = new AtomicInteger(0);
        
        List<String> processedIds = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();
        
        for (WebhookDelivery delivery : failedDeliveries) {
            try {
                WebhookSubscription subscription = subscriptionRepository
                        .findById(delivery.getSubscriptionId())
                        .orElse(null);
                
                if (subscription == null || !subscription.isActive()) {
                    log.warn("Skipping retry for delivery {} - subscription not found or inactive", 
                            delivery.getId());
                    continue;
                }
                
                boolean retryResult = performRetry(delivery, subscription);
                processedCount.incrementAndGet();
                processedIds.add(delivery.getId());
                
                if (retryResult) {
                    successCount.incrementAndGet();
                } else {
                    failedCount.incrementAndGet();
                    failedIds.add(delivery.getId());
                }
                
            } catch (Exception e) {
                log.error("Error processing retry for delivery: {}", delivery.getId(), e);
                processedCount.incrementAndGet();
                failedCount.incrementAndGet();
                failedIds.add(delivery.getId());
            }
        }
        
        log.info("Webhook retry processing completed. Processed: {}, Success: {}, Failed: {}", 
                processedCount.get(), successCount.get(), failedCount.get());
        
        return WebhookRetryProcessResult.builder()
                .processedCount(processedCount.get())
                .successCount(successCount.get())
                .failedCount(failedCount.get())
                .processedIds(processedIds)
                .failedIds(failedIds)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Scheduled task to process failed webhook retries
     */
    @Scheduled(fixedDelayString = "${webhook.service.retry.interval:300000}") // 5 minutes
    public void processScheduledRetries() {
        log.debug("Starting scheduled webhook retry processing");
        
        try {
            WebhookRetryProcessResult result = self.processRetries(batchSize);
            
            if (result.getProcessedCount() > 0) {
                log.info("Scheduled retry processing: {} processed, {} succeeded, {} failed", 
                        result.getProcessedCount(), result.getSuccessCount(), result.getFailedCount());
            }
            
            // Update health status based on failure rate
            double failureRate = result.getProcessedCount() > 0 ? 
                    (double) result.getFailedCount() / result.getProcessedCount() : 0.0;
            
            if (failureRate > 0.8) { // More than 80% failure rate
                healthStatus.set(0);
                log.warn("Webhook retry service health degraded - failure rate: {}%", failureRate * 100);
            } else {
                healthStatus.set(1);
            }
            
        } catch (Exception e) {
            log.error("Error in scheduled webhook retry processing", e);
            healthStatus.set(0);
        }
    }

    /**
     * Check if retry service is healthy
     */
    public boolean isHealthy() {
        return healthStatus.get() == 1;
    }

    /**
     * Get retry service statistics
     */
    @Transactional(readOnly = true)
    public WebhookRetryStatistics getRetryStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        List<WebhookDelivery> deliveries = deliveryRepository
                .findDeliveriesInDateRange(startDate, endDate);
        
        long totalRetries = deliveries.stream()
                .mapToLong(d -> Math.max(0, d.getAttempts() - 1))
                .sum();
        
        long successfulRetries = deliveries.stream()
                .filter(d -> d.getAttempts() > 1 && d.getStatus() == WebhookStatus.COMPLETED)
                .count();
        
        long failedRetries = deliveries.stream()
                .filter(d -> d.getAttempts() > 1 && d.getStatus() == WebhookStatus.FAILED)
                .count();
        
        long maxRetriesExceeded = deliveries.stream()
                .filter(d -> d.getAttempts() >= maxRetryAttempts && d.getStatus() == WebhookStatus.FAILED)
                .count();
        
        double retrySuccessRate = totalRetries > 0 ? 
                (double) successfulRetries / totalRetries * 100 : 0.0;
        
        return WebhookRetryStatistics.builder()
                .totalRetries(totalRetries)
                .successfulRetries(successfulRetries)
                .failedRetries(failedRetries)
                .maxRetriesExceeded(maxRetriesExceeded)
                .retrySuccessRate(retrySuccessRate)
                .averageAttempts(calculateAverageAttempts(deliveries))
                .periodStart(startDate)
                .periodEnd(endDate)
                .build();
    }

    // Private helper methods

    @Async
    private CompletableFuture<Boolean> performRetryAsync(WebhookDelivery delivery, 
                                                       WebhookSubscription subscription) {
        boolean result = performRetry(delivery, subscription);
        return CompletableFuture.completedFuture(result);
    }

    private boolean performRetry(WebhookDelivery delivery, WebhookSubscription subscription) {
        try {
            // Calculate delay based on attempt number
            long delay = calculateRetryDelay(delivery.getAttempts());
            
            if (delay > 0) {
                TimeUnit.MILLISECONDS.sleep(delay);
            }
            
            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("User-Agent", "Waqiti-Webhook/1.0");
            headers.set("X-Retry-Attempt", String.valueOf(delivery.getAttempts() + 1));
            
            // Add custom headers from subscription
            if (subscription.getHeaders() != null) {
                subscription.getHeaders().forEach(headers::set);
            }
            
            // Add signature if secret is configured
            if (subscription.getSecret() != null && !subscription.getSecret().isEmpty()) {
                String signature = generateSignature(delivery.getPayload().toString(), 
                                                   subscription.getSecret());
                headers.set("X-Webhook-Signature", signature);
            }
            
            headers.set("X-Webhook-Timestamp", String.valueOf(System.currentTimeMillis()));
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(delivery.getPayload(), headers);
            
            // Perform HTTP request
            ResponseEntity<String> response = restTemplate.exchange(
                subscription.getUrl(), HttpMethod.POST, entity, String.class
            );
            
            // Update delivery record
            delivery.setAttempts(delivery.getAttempts() + 1);
            delivery.setResponseCode(response.getStatusCode().value());
            delivery.setResponseBody(response.getBody());
            delivery.setLastAttemptAt(LocalDateTime.now());
            
            boolean success = response.getStatusCode().is2xxSuccessful();
            
            if (success) {
                delivery.setStatus(WebhookStatus.COMPLETED);
                delivery.setDeliveredAt(LocalDateTime.now());
                log.info("Webhook retry successful for delivery: {}", delivery.getId());
                
                // Update subscription statistics
                WebhookSubscription sub = subscriptionRepository.findById(subscription.getId()).orElse(null);
                if (sub != null) {
                    sub.setSuccessfulDeliveries(sub.getSuccessfulDeliveries() + 1);
                    sub.setLastDeliveryAt(LocalDateTime.now());
                    subscriptionRepository.save(sub);
                }
            } else {
                delivery.setStatus(WebhookStatus.FAILED);
                log.warn("Webhook retry failed for delivery: {} - HTTP {}", 
                        delivery.getId(), response.getStatusCode().value());
            }
            
            deliveryRepository.save(delivery);
            return success;
            
        } catch (Exception e) {
            log.error("Error performing webhook retry for delivery: {}", delivery.getId(), e);
            
            // Update delivery record with error
            delivery.setAttempts(delivery.getAttempts() + 1);
            delivery.setStatus(WebhookStatus.FAILED);
            delivery.setResponseCode(0);
            delivery.setResponseBody("Error: " + e.getMessage());
            delivery.setLastAttemptAt(LocalDateTime.now());
            deliveryRepository.save(delivery);
            
            return false;
        }
    }

    private long calculateRetryDelay(int attemptNumber) {
        if (attemptNumber <= 1) {
            return 0; // No delay for first retry
        }
        
        // Exponential backoff with jitter
        long delay = (long) (initialRetryDelay * Math.pow(retryMultiplier, attemptNumber - 2));
        delay = Math.min(delay, maxRetryDelay);
        
        // Add jitter (±20%)
        double jitter = 0.8 + (ThreadLocalRandom.current().nextDouble() * 0.4);
        delay = (long) (delay * jitter);
        
        return delay;
    }

    private String generateSignature(String payload, String secret) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKeySpec = 
                new javax.crypto.spec.SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), 
                                                  "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Error generating webhook signature: {}", e.getMessage());
            return "";
        }
    }

    private double calculateAverageAttempts(List<WebhookDelivery> deliveries) {
        if (deliveries.isEmpty()) {
            return 0.0;
        }
        
        return deliveries.stream()
                .mapToInt(WebhookDelivery::getAttempts)
                .average()
                .orElse(0.0);
    }

    private WebhookDeliveryDTO convertToDTO(WebhookDelivery delivery) {
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
                .lastAttemptAt(delivery.getLastAttemptAt())
                .build();
    }

    /**
     * Clean up old webhook deliveries
     */
    @Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
    public void cleanupOldDeliveries() {
        log.info("Starting cleanup of old webhook deliveries");
        
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minus(30, ChronoUnit.DAYS);
            int deletedCount = deliveryRepository.deleteOldDeliveries(cutoffDate);
            
            log.info("Cleaned up {} old webhook deliveries", deletedCount);
            
        } catch (Exception e) {
            log.error("Error cleaning up old webhook deliveries", e);
        }
    }

    /**
     * Update webhook subscription failure statistics
     */
    private void updateSubscriptionFailureStats(String subscriptionId) {
        try {
            WebhookSubscription subscription = subscriptionRepository.findById(subscriptionId).orElse(null);
            if (subscription != null) {
                subscription.setFailedDeliveries(subscription.getFailedDeliveries() + 1);
                subscription.setLastDeliveryAt(LocalDateTime.now());
                subscriptionRepository.save(subscription);
            }
        } catch (Exception e) {
            log.error("Error updating subscription failure stats: {}", subscriptionId, e);
        }
    }
}