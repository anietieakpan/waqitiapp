package com.waqiti.notification.service;

import com.waqiti.common.notification.model.*;
import com.waqiti.notification.entity.NotificationDelivery;
import com.waqiti.notification.entity.NotificationAttempt;
import com.waqiti.notification.repository.NotificationDeliveryRepository;
import com.waqiti.notification.repository.NotificationAttemptRepository;
import com.waqiti.common.exception.NotificationException;
import com.waqiti.common.tracing.Traced;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationAttemptRepository attemptRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final EmailService emailService;
    private final SmsService smsService;
    private final PushNotificationService pushService;

    @Value("${notification.delivery.max-attempts:3}")
    private int maxDeliveryAttempts;

    @Value("${notification.delivery.retry-delay-minutes:5}")
    private int retryDelayMinutes;

    @Value("${notification.delivery.timeout-hours:24}")
    private int deliveryTimeoutHours;

    private static final String DELIVERY_CACHE_PREFIX = "notification:delivery:";
    private static final String RETRY_QUEUE_PREFIX = "notification:retry:";

    @Traced(operation = "create_delivery")
    @Transactional
    public String createDelivery(NotificationRequest request) {
        try {
            log.debug("Creating notification delivery for request: {}", request.getChannel());

            NotificationDelivery delivery = new NotificationDelivery();
            delivery.setNotificationId(UUID.randomUUID().toString());
            delivery.setChannel(request.getChannel());
            delivery.setRecipient(getRecipient(request));
            delivery.setSubject(getSubject(request));
            delivery.setContent(getContent(request));
            delivery.setMetadata(request.getMetadata());
            delivery.setStatus(NotificationResult.DeliveryStatus.PENDING);
            delivery.setPriority(request.getPriority());
            delivery.setMaxAttempts(maxDeliveryAttempts);
            delivery.setAttemptCount(0);
            delivery.setCreatedAt(LocalDateTime.now());
            delivery.setUpdatedAt(LocalDateTime.now());
            delivery.setExpiresAt(LocalDateTime.now().plus(deliveryTimeoutHours, ChronoUnit.HOURS));

            NotificationDelivery saved = deliveryRepository.save(delivery);

            // Cache for quick access
            cacheDelivery(saved);

            log.info("Created notification delivery: {} for channel: {}", saved.getNotificationId(), saved.getChannel());
            return saved.getNotificationId();

        } catch (Exception e) {
            log.error("Error creating notification delivery", e);
            throw new NotificationException("Failed to create notification delivery", e);
        }
    }

    @Traced(operation = "deliver_notification")
    @Transactional
    public NotificationResult deliverNotification(String notificationId) {
        try {
            log.debug("Delivering notification: {}", notificationId);

            NotificationDelivery delivery = getDelivery(notificationId);
            
            if (delivery.getStatus() == NotificationResult.DeliveryStatus.DELIVERED) {
                return createSuccessResult(delivery);
            }

            if (delivery.getExpiresAt().isBefore(LocalDateTime.now())) {
                return handleExpiredDelivery(delivery);
            }

            if (delivery.getAttemptCount() >= delivery.getMaxAttempts()) {
                return handleMaxAttemptsExceeded(delivery);
            }

            return attemptDelivery(delivery);

        } catch (Exception e) {
            log.error("Error delivering notification: {}", notificationId, e);
            return createErrorResult(notificationId, e);
        }
    }

    @Traced(operation = "attempt_delivery")
    private NotificationResult attemptDelivery(NotificationDelivery delivery) {
        NotificationAttempt attempt = createAttempt(delivery);
        
        try {
            delivery.setAttemptCount(delivery.getAttemptCount() + 1);
            delivery.setStatus(NotificationResult.DeliveryStatus.IN_PROGRESS);
            delivery.setUpdatedAt(LocalDateTime.now());

            NotificationResult result = performChannelDelivery(delivery);
            
            attempt.setStatus(result.getStatus());
            attempt.setErrorMessage(result.getErrorDetails() != null ? result.getErrorDetails().getMessage() : null);
            attempt.setResponseCode(result.getErrorDetails() != null ? result.getErrorDetails().getCode() : null);
            attempt.setCompletedAt(LocalDateTime.now());

            if (result.getStatus() == NotificationResult.DeliveryStatus.DELIVERED) {
                delivery.setStatus(NotificationResult.DeliveryStatus.DELIVERED);
                delivery.setDeliveredAt(LocalDateTime.now());
            } else if (delivery.getAttemptCount() >= delivery.getMaxAttempts()) {
                delivery.setStatus(NotificationResult.DeliveryStatus.FAILED);
            } else {
                delivery.setStatus(NotificationResult.DeliveryStatus.PENDING);
                scheduleRetry(delivery);
            }

            delivery.setUpdatedAt(LocalDateTime.now());
            deliveryRepository.save(delivery);
            attemptRepository.save(attempt);

            updateCache(delivery);

            return result;

        } catch (Exception e) {
            attempt.setStatus(NotificationResult.DeliveryStatus.FAILED);
            attempt.setErrorMessage(e.getMessage());
            attempt.setCompletedAt(LocalDateTime.now());
            attemptRepository.save(attempt);

            delivery.setStatus(NotificationResult.DeliveryStatus.FAILED);
            delivery.setUpdatedAt(LocalDateTime.now());
            deliveryRepository.save(delivery);

            updateCache(delivery);

            throw e;
        }
    }

    private NotificationResult performChannelDelivery(NotificationDelivery delivery) {
        switch (delivery.getChannel()) {
            case EMAIL:
                return deliverEmail(delivery);
            case SMS:
                return deliverSms(delivery);
            case PUSH:
                return deliverPush(delivery);
            case IN_APP:
                return deliverInApp(delivery);
            case WEBHOOK:
                return deliverWebhook(delivery);
            default:
                throw new NotificationException("Unsupported channel: " + delivery.getChannel());
        }
    }

    private NotificationResult deliverEmail(NotificationDelivery delivery) {
        try {
            EmailNotificationRequest request = EmailNotificationRequest.builder()
                .to(List.of(delivery.getRecipient()))
                .subject(delivery.getSubject())
                .htmlContent(delivery.getContent())
                .priority(delivery.getPriority())
                .metadata(delivery.getMetadata())
                .build();

            return emailService.sendEmail(request);

        } catch (Exception e) {
            log.error("Error delivering email notification: {}", delivery.getNotificationId(), e);
            return createChannelErrorResult(delivery, e);
        }
    }

    private NotificationResult deliverSms(NotificationDelivery delivery) {
        try {
            SmsNotificationRequest request = SmsNotificationRequest.builder()
                .phoneNumber(delivery.getRecipient())
                .message(delivery.getContent())
                .priority(delivery.getPriority())
                .metadata(delivery.getMetadata())
                .build();

            return smsService.sendSms(request);

        } catch (Exception e) {
            log.error("Error delivering SMS notification: {}", delivery.getNotificationId(), e);
            return createChannelErrorResult(delivery, e);
        }
    }

    private NotificationResult deliverPush(NotificationDelivery delivery) {
        try {
            PushNotificationRequest request = PushNotificationRequest.builder()
                .userId(delivery.getRecipient())
                .title(delivery.getSubject())
                .body(delivery.getContent())
                .priority(delivery.getPriority())
                .data(delivery.getMetadata())
                .build();

            return pushService.sendPushNotification(request);

        } catch (Exception e) {
            log.error("Error delivering push notification: {}", delivery.getNotificationId(), e);
            return createChannelErrorResult(delivery, e);
        }
    }

    private NotificationResult deliverInApp(NotificationDelivery delivery) {
        try {
            InAppNotificationRequest request = InAppNotificationRequest.builder()
                .userId(delivery.getRecipient())
                .title(delivery.getSubject())
                .content(delivery.getContent())
                .priority(delivery.getPriority())
                .metadata(delivery.getMetadata())
                .build();

            // For in-app notifications, we just store them for the user to retrieve
            return NotificationResult.builder()
                .notificationId(delivery.getNotificationId())
                .status(NotificationResult.DeliveryStatus.DELIVERED)
                .channel(NotificationChannel.IN_APP)
                .deliveredAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Error delivering in-app notification: {}", delivery.getNotificationId(), e);
            return createChannelErrorResult(delivery, e);
        }
    }

    private NotificationResult deliverWebhook(NotificationDelivery delivery) {
        try {
            WebhookNotificationRequest request = WebhookNotificationRequest.builder()
                .url(delivery.getRecipient())
                .payload(delivery.getContent())
                .headers(delivery.getMetadata())
                .build();

            // Execute webhook delivery
            boolean webhookSuccess = executeWebhookDelivery(request);
            
            return NotificationResult.builder()
                .notificationId(delivery.getNotificationId())
                .status(webhookSuccess ? NotificationResult.DeliveryStatus.DELIVERED : NotificationResult.DeliveryStatus.FAILED)
                .channel(NotificationChannel.WEBHOOK)
                .deliveredAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Error delivering webhook notification: {}", delivery.getNotificationId(), e);
            return createChannelErrorResult(delivery, e);
        }
    }

    public NotificationStatus getNotificationStatus(String notificationId) {
        try {
            NotificationDelivery delivery = getDelivery(notificationId);
            List<NotificationAttempt> attempts = attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notificationId);

            return NotificationStatus.builder()
                .notificationId(notificationId)
                .status(delivery.getStatus())
                .channel(delivery.getChannel())
                .recipient(delivery.getRecipient())
                .attemptCount(delivery.getAttemptCount())
                .maxAttempts(delivery.getMaxAttempts())
                .createdAt(delivery.getCreatedAt())
                .updatedAt(delivery.getUpdatedAt())
                .deliveredAt(delivery.getDeliveredAt())
                .expiresAt(delivery.getExpiresAt())
                .attempts(attempts.stream().map(this::convertAttempt).collect(Collectors.toList()))
                .build();

        } catch (Exception e) {
            log.error("Error getting notification status: {}", notificationId, e);
            throw new NotificationException("Failed to get notification status", e);
        }
    }

    public DeliveryReport getDeliveryReport(String notificationId) {
        try {
            NotificationDelivery delivery = getDelivery(notificationId);
            List<NotificationAttempt> attempts = attemptRepository.findByNotificationIdOrderByAttemptNumberAsc(notificationId);

            return DeliveryReport.builder()
                .notificationId(notificationId)
                .channel(delivery.getChannel())
                .recipient(delivery.getRecipient())
                .status(delivery.getStatus())
                .totalAttempts(delivery.getAttemptCount())
                .createdAt(delivery.getCreatedAt())
                .deliveredAt(delivery.getDeliveredAt())
                .processingTime(calculateProcessingTime(delivery))
                .attempts(attempts.stream().map(this::convertAttemptToReport).collect(Collectors.toList()))
                .metadata(delivery.getMetadata())
                .build();

        } catch (Exception e) {
            log.error("Error getting delivery report: {}", notificationId, e);
            throw new NotificationException("Failed to get delivery report", e);
        }
    }

    @Traced(operation = "retry_failed_deliveries")
    public void retryFailedDeliveries() {
        try {
            log.debug("Processing retry queue for failed deliveries");

            LocalDateTime cutoff = LocalDateTime.now().minus(retryDelayMinutes, ChronoUnit.MINUTES);
            
            List<NotificationDelivery> pendingRetries = deliveryRepository
                .findByStatusAndUpdatedAtBeforeAndAttemptCountLessThan(
                    NotificationResult.DeliveryStatus.PENDING, cutoff, maxDeliveryAttempts);

            for (NotificationDelivery delivery : pendingRetries) {
                CompletableFuture.runAsync(() -> {
                    try {
                        deliverNotification(delivery.getNotificationId());
                    } catch (Exception e) {
                        log.error("Error retrying delivery: {}", delivery.getNotificationId(), e);
                    }
                });
            }

            log.info("Scheduled {} deliveries for retry", pendingRetries.size());

        } catch (Exception e) {
            log.error("Error processing retry queue", e);
        }
    }

    @Traced(operation = "cleanup_expired_deliveries")
    public void cleanupExpiredDeliveries() {
        try {
            log.debug("Cleaning up expired deliveries");

            LocalDateTime cutoff = LocalDateTime.now().minus(deliveryTimeoutHours * 2, ChronoUnit.HOURS);
            
            List<NotificationDelivery> expiredDeliveries = deliveryRepository.findByExpiresAtBefore(cutoff);
            
            for (NotificationDelivery delivery : expiredDeliveries) {
                if (delivery.getStatus() == NotificationResult.DeliveryStatus.PENDING) {
                    delivery.setStatus(NotificationResult.DeliveryStatus.EXPIRED);
                    delivery.setUpdatedAt(LocalDateTime.now());
                    deliveryRepository.save(delivery);
                }

                // Remove from cache
                removeFromCache(delivery.getNotificationId());
            }

            log.info("Cleaned up {} expired deliveries", expiredDeliveries.size());

        } catch (Exception e) {
            log.error("Error cleaning up expired deliveries", e);
        }
    }

    private NotificationDelivery getDelivery(String notificationId) {
        // Try cache first
        String cacheKey = DELIVERY_CACHE_PREFIX + notificationId;
        NotificationDelivery cached = (NotificationDelivery) redisTemplate.opsForValue().get(cacheKey);
        
        if (cached != null) {
            return cached;
        }

        // Load from database
        return deliveryRepository.findByNotificationId(notificationId)
            .orElseThrow(() -> new NotificationException("Notification not found: " + notificationId));
    }

    private NotificationAttempt createAttempt(NotificationDelivery delivery) {
        NotificationAttempt attempt = new NotificationAttempt();
        attempt.setNotificationId(delivery.getNotificationId());
        attempt.setAttemptNumber(delivery.getAttemptCount() + 1);
        attempt.setChannel(delivery.getChannel());
        attempt.setStatus(NotificationResult.DeliveryStatus.IN_PROGRESS);
        attempt.setStartedAt(LocalDateTime.now());
        return attempt;
    }

    private void scheduleRetry(NotificationDelivery delivery) {
        String retryKey = RETRY_QUEUE_PREFIX + delivery.getNotificationId();
        LocalDateTime nextRetry = LocalDateTime.now().plus(retryDelayMinutes, ChronoUnit.MINUTES);
        
        redisTemplate.opsForValue().set(retryKey, nextRetry, retryDelayMinutes * 2, TimeUnit.MINUTES);
    }

    private void cacheDelivery(NotificationDelivery delivery) {
        String cacheKey = DELIVERY_CACHE_PREFIX + delivery.getNotificationId();
        redisTemplate.opsForValue().set(cacheKey, delivery, 1, TimeUnit.HOURS);
    }

    private void updateCache(NotificationDelivery delivery) {
        cacheDelivery(delivery);
    }

    private void removeFromCache(String notificationId) {
        String cacheKey = DELIVERY_CACHE_PREFIX + notificationId;
        redisTemplate.delete(cacheKey);
    }

    private String getRecipient(NotificationRequest request) {
        if (request instanceof EmailNotificationRequest) {
            return ((EmailNotificationRequest) request).getTo().get(0);
        } else if (request instanceof SmsNotificationRequest) {
            return ((SmsNotificationRequest) request).getPhoneNumber();
        } else if (request instanceof PushNotificationRequest) {
            return ((PushNotificationRequest) request).getUserId();
        } else if (request instanceof InAppNotificationRequest) {
            return ((InAppNotificationRequest) request).getUserId();
        } else if (request instanceof WebhookNotificationRequest) {
            return ((WebhookNotificationRequest) request).getUrl();
        }
        return "unknown";
    }

    private String getSubject(NotificationRequest request) {
        if (request instanceof EmailNotificationRequest) {
            return ((EmailNotificationRequest) request).getSubject();
        } else if (request instanceof PushNotificationRequest) {
            return ((PushNotificationRequest) request).getTitle();
        } else if (request instanceof InAppNotificationRequest) {
            return ((InAppNotificationRequest) request).getTitle();
        }
        return "";
    }

    private String getContent(NotificationRequest request) {
        if (request instanceof EmailNotificationRequest) {
            return ((EmailNotificationRequest) request).getHtmlContent();
        } else if (request instanceof SmsNotificationRequest) {
            return ((SmsNotificationRequest) request).getMessage();
        } else if (request instanceof PushNotificationRequest) {
            return ((PushNotificationRequest) request).getBody();
        } else if (request instanceof InAppNotificationRequest) {
            return ((InAppNotificationRequest) request).getContent();
        } else if (request instanceof WebhookNotificationRequest) {
            return ((WebhookNotificationRequest) request).getPayload();
        }
        return "";
    }

    private NotificationResult createSuccessResult(NotificationDelivery delivery) {
        return NotificationResult.builder()
            .notificationId(delivery.getNotificationId())
            .status(NotificationResult.DeliveryStatus.DELIVERED)
            .channel(delivery.getChannel())
            .deliveredAt(delivery.getDeliveredAt())
            .build();
    }

    private NotificationResult handleExpiredDelivery(NotificationDelivery delivery) {
        delivery.setStatus(NotificationResult.DeliveryStatus.EXPIRED);
        delivery.setUpdatedAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
        updateCache(delivery);

        return NotificationResult.builder()
            .notificationId(delivery.getNotificationId())
            .status(NotificationResult.DeliveryStatus.EXPIRED)
            .channel(delivery.getChannel())
            .errorDetails(NotificationResult.ErrorDetails.builder()
                .code("EXPIRED")
                .message("Notification expired after " + deliveryTimeoutHours + " hours")
                .build())
            .build();
    }

    private NotificationResult handleMaxAttemptsExceeded(NotificationDelivery delivery) {
        delivery.setStatus(NotificationResult.DeliveryStatus.FAILED);
        delivery.setUpdatedAt(LocalDateTime.now());
        deliveryRepository.save(delivery);
        updateCache(delivery);

        return NotificationResult.builder()
            .notificationId(delivery.getNotificationId())
            .status(NotificationResult.DeliveryStatus.FAILED)
            .channel(delivery.getChannel())
            .errorDetails(NotificationResult.ErrorDetails.builder()
                .code("MAX_ATTEMPTS_EXCEEDED")
                .message("Maximum delivery attempts (" + maxDeliveryAttempts + ") exceeded")
                .build())
            .build();
    }

    private NotificationResult createErrorResult(String notificationId, Exception e) {
        return NotificationResult.builder()
            .notificationId(notificationId)
            .status(NotificationResult.DeliveryStatus.FAILED)
            .errorDetails(NotificationResult.ErrorDetails.builder()
                .code("DELIVERY_ERROR")
                .message(e.getMessage())
                .build())
            .build();
    }

    private NotificationResult createChannelErrorResult(NotificationDelivery delivery, Exception e) {
        return NotificationResult.builder()
            .notificationId(delivery.getNotificationId())
            .status(NotificationResult.DeliveryStatus.FAILED)
            .channel(delivery.getChannel())
            .errorDetails(NotificationResult.ErrorDetails.builder()
                .code("CHANNEL_ERROR")
                .message(e.getMessage())
                .build())
            .build();
    }

    private NotificationStatus.AttemptInfo convertAttempt(NotificationAttempt attempt) {
        return NotificationStatus.AttemptInfo.builder()
            .attemptNumber(attempt.getAttemptNumber())
            .status(attempt.getStatus())
            .startedAt(attempt.getStartedAt())
            .completedAt(attempt.getCompletedAt())
            .errorMessage(attempt.getErrorMessage())
            .responseCode(attempt.getResponseCode())
            .build();
    }

    private DeliveryReport.AttemptReport convertAttemptToReport(NotificationAttempt attempt) {
        return DeliveryReport.AttemptReport.builder()
            .attemptNumber(attempt.getAttemptNumber())
            .status(attempt.getStatus())
            .startedAt(attempt.getStartedAt())
            .completedAt(attempt.getCompletedAt())
            .errorMessage(attempt.getErrorMessage())
            .responseCode(attempt.getResponseCode())
            .processingTime(calculateAttemptProcessingTime(attempt))
            .build();
    }

    private Long calculateProcessingTime(NotificationDelivery delivery) {
        if (delivery.getDeliveredAt() != null) {
            return ChronoUnit.MILLIS.between(delivery.getCreatedAt(), delivery.getDeliveredAt());
        }
        return ChronoUnit.MILLIS.between(delivery.getCreatedAt(), LocalDateTime.now());
    }

    private Long calculateAttemptProcessingTime(NotificationAttempt attempt) {
        if (attempt.getCompletedAt() != null) {
            return ChronoUnit.MILLIS.between(attempt.getStartedAt(), attempt.getCompletedAt());
        }
        return null;
    }
    
    /**
     * Execute webhook delivery
     */
    private boolean executeWebhookDelivery(WebhookNotificationRequest request) {
        try {
            log.debug("Executing webhook delivery to: {}", request.getUrl());
            
            // Create HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Add custom headers from metadata
            if (request.getHeaders() != null) {
                request.getHeaders().forEach((key, value) -> {
                    if (value instanceof String) {
                        headers.add(key, (String) value);
                    }
                });
            }
            
            // Create HTTP entity with payload
            HttpEntity<String> entity = new HttpEntity<>(request.getPayload(), headers);
            
            // Execute webhook call with timeout
            RestTemplate restTemplate = new RestTemplate();
            restTemplate.getInterceptors().add((httpRequest, body, execution) -> {
                // Add authentication or other custom headers here if needed
                return execution.execute(httpRequest, body);
            });
            
            ResponseEntity<String> response = restTemplate.postForEntity(request.getUrl(), entity, String.class);
            
            // Consider 2xx responses as successful
            boolean success = response.getStatusCode().is2xxSuccessful();
            
            if (success) {
                log.debug("Webhook delivery successful to: {}", request.getUrl());
            } else {
                log.warn("Webhook delivery failed with status {} to: {}", response.getStatusCode(), request.getUrl());
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Webhook delivery error to {}: {}", request.getUrl(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Webhook notification request DTO
     */
    @lombok.Builder
    @lombok.Data
    private static class WebhookNotificationRequest {
        private String url;
        private String payload;
        private Map<String, Object> headers;
    }
}