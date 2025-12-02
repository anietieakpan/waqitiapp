package com.waqiti.notification.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.monitoring.AlertingService;
import com.waqiti.notification.event.PushNotificationEvent;
import com.waqiti.notification.service.PushNotificationService;
import com.waqiti.notification.service.DeviceRegistryService;
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

/**
 * Production-grade Kafka consumer for push notifications
 * Enhanced with circuit breaker, retry mechanisms, comprehensive metrics, and batch processing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PushNotificationConsumer {

    private static final String TOPIC = "push-notifications";
    private static final String DLQ_TOPIC = "push-notifications-dlq";
    private static final String CONSUMER_GROUP = "notification-push-processor-group";
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 500;
    private static final double RETRY_MULTIPLIER = 2.0;
    
    private static final int MAX_BATCH_SIZE = 200;
    private static final int RATE_LIMIT_PER_MINUTE = 1000;
    private static final int MAX_DEVICES_PER_NOTIFICATION = 1000;
    private static final double DELIVERY_RATE_THRESHOLD = 0.95;
    private static final int MAX_PAYLOAD_SIZE_KB = 256;
    private static final int TOKEN_CLEANUP_DAYS = 30;

    // Existing services
    private final PushNotificationService pushService;
    private final DeviceRegistryService deviceRegistry;
    private final NotificationTrackingService trackingService;
    
    // Enhanced services
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler universalDLQHandler;
    
    // Resilience components
    private CircuitBreaker circuitBreaker;
    private Retry retryMechanism;
    private ScheduledExecutorService scheduledExecutor;
    
    // State management
    private final Map<String, PushProcessingState> pushStates = new ConcurrentHashMap<>();
    private final Map<String, PushBatch> activeBatches = new ConcurrentHashMap<>();
    private final Map<String, DeviceMetrics> deviceMetrics = new ConcurrentHashMap<>();
    private final Map<String, PlatformPerformance> platformStats = new ConcurrentHashMap<>();
    private final Map<String, RateLimitState> rateLimits = new ConcurrentHashMap<>();
    private final Map<String, ScheduledPush> scheduledPushes = new ConcurrentHashMap<>();
    private final Map<String, PushAnalytics> analyticsData = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> invalidTokens = new ConcurrentHashMap<>();
    
    // Metrics
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong deliveredCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong noDevicesCount = new AtomicLong(0);
    private final AtomicLong batchCount = new AtomicLong(0);
    private final AtomicLong multiPlatformCount = new AtomicLong(0);
    private final AtomicLong invalidTokenCount = new AtomicLong(0);
    
    private Counter processedCounter;
    private Counter errorCounter;
    private Counter deliveredCounter;
    private Counter failedCounter;
    private Counter noDevicesCounter;
    private Counter batchCounter;
    private Counter multiPlatformCounter;
    private Counter invalidTokenCounter;
    private Gauge activePushGauge;
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
        initializePlatformStats();
        log.info("Enhanced PushNotificationConsumer initialized successfully");
    }
    
    private void initializeMetrics() {
        processedCounter = meterRegistry.counter("push.notifications.processed.total");
        errorCounter = meterRegistry.counter("push.notifications.errors.total");
        deliveredCounter = meterRegistry.counter("push.notifications.delivered.total");
        failedCounter = meterRegistry.counter("push.notifications.failed.total");
        noDevicesCounter = meterRegistry.counter("push.notifications.nodevices.total");
        batchCounter = meterRegistry.counter("push.notifications.batch.total");
        multiPlatformCounter = meterRegistry.counter("push.notifications.multiplatform.total");
        invalidTokenCounter = meterRegistry.counter("push.notifications.invalidtoken.total");
        
        activePushGauge = Gauge.builder("push.notifications.active", pushStates, Map::size)
            .description("Number of active push notifications")
            .register(meterRegistry);
            
        deliveryRateGauge = Gauge.builder("push.notifications.delivery.rate", this, 
            consumer -> calculateDeliveryRate())
            .description("Current push delivery rate")
            .register(meterRegistry);
            
        processingTimer = Timer.builder("push.notifications.processing.duration")
            .description("Push notification processing duration")
            .register(meterRegistry);
            
        deliveryTimer = Timer.builder("push.notifications.delivery.duration")
            .description("Push notification delivery duration")
            .register(meterRegistry);
    }
    
    private void initializeCircuitBreaker() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .slidingWindowSize(100)
            .failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .slowCallRateThreshold(80)
            .slowCallDurationThreshold(Duration.ofSeconds(5))
            .permittedNumberOfCallsInHalfOpenState(3)
            .automaticTransitionFromOpenToHalfOpenEnabled(true)
            .build();
            
        circuitBreaker = CircuitBreaker.of("push-notification-processor", config);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                log.warn("Circuit breaker state transition: {}", event);
                alertingService.sendAlert(
                    "Circuit Breaker State Change",
                    String.format("Push notification circuit breaker transitioned from %s to %s",
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
            
        retryMechanism = Retry.of("push-notification-retry", config);
        
        retryMechanism.getEventPublisher()
            .onRetry(event -> log.debug("Retry attempt {} for push notification", 
                event.getNumberOfRetryAttempts()));
    }
    
    private void initializeScheduledTasks() {
        scheduledExecutor = Executors.newScheduledThreadPool(6);
        
        scheduledExecutor.scheduleAtFixedRate(
            this::processBatches,
            0, 10, TimeUnit.SECONDS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::processScheduledPushes,
            0, 1, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::updatePlatformStats,
            0, 5, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::cleanupExpiredData,
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeDeviceMetrics,
            0, 30, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::cleanupInvalidTokens,
            0, 4, TimeUnit.HOURS
        );
    }
    
    private void initializeRateLimits() {
        rateLimits.put("global", new RateLimitState(RATE_LIMIT_PER_MINUTE));
        rateLimits.put("ios", new RateLimitState(800));
        rateLimits.put("android", new RateLimitState(900));
        rateLimits.put("web", new RateLimitState(500));
        rateLimits.put("critical", new RateLimitState(2000));
    }
    
    private void initializePlatformStats() {
        platformStats.put("ios", new PlatformPerformance("ios"));
        platformStats.put("android", new PlatformPerformance("android"));
        platformStats.put("web", new PlatformPerformance("web"));
    }

    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    public void processPushNotification(@Payload String message,
                                      @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                      @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                      @Header(KafkaHeaders.OFFSET) long offset,
                                      @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                      Acknowledgment acknowledgment) {
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        
        try {
            log.debug("Processing push notification from partition {} offset {}", partition, offset);
            
            PushNotificationEvent event = parseEvent(message);
            MDC.put("pushId", event.getNotificationId());
            MDC.put("platform", event.getPlatform());
            MDC.put("priority", event.getPriority());
            
            if (!validatePushEvent(event)) {
                log.error("Invalid push notification event: {}", event);
                handleInvalidEvent(event, message);
                acknowledgment.acknowledge();
                return;
            }
            
            Supplier<Boolean> processor = () -> processEventWithEnhancements(event);
            
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
            log.error("Error processing push notification", e);
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
    
    private PushNotificationEvent parseEvent(String message) throws JsonProcessingException {
        return objectMapper.readValue(message, PushNotificationEvent.class);
    }
    
    private boolean processEventWithEnhancements(PushNotificationEvent event) {
        return processingTimer.recordCallable(() -> {
            log.info("Processing push notification: {} for user: {} platform: {} priority: {}", 
                    event.getNotificationId(), event.getUserId(), 
                    event.getPlatform(), event.getPriority());
            
            String pushId = event.getNotificationId();
            PushProcessingState state = new PushProcessingState(pushId);
            pushStates.put(pushId, state);
            
            // Enhanced validation with rate limiting
            if (!checkRateLimit(event.getPlatform(), event.getPriority())) {
                queueForLaterDelivery(event);
                return true;
            }
            
            // Validate payload size
            if (!validatePayloadSize(event)) {
                log.error("Payload too large for push notification: {}", pushId);
                state.setStatus("PAYLOAD_TOO_LARGE");
                return false;
            }
            
            // Get active device tokens with enhanced filtering
            List<String> deviceTokens = getActiveDeviceTokensEnhanced(event);
            if (deviceTokens.isEmpty()) {
                log.warn("No active devices found for user: {}", event.getUserId());
                noDevicesCount.incrementAndGet();
                noDevicesCounter.increment();
                handleNoDevices(event);
                return true;
            }
            
            state.setStatus("PROCESSING");
            state.setDeviceCount(deviceTokens.size());
            state.setPlatform(event.getPlatform());
            
            // Check for batch processing
            if (deviceTokens.size() > MAX_BATCH_SIZE) {
                return processBatchDelivery(event, deviceTokens, state);
            }
            
            // Send push notification with enhanced delivery tracking
            boolean sent = deliveryTimer.recordCallable(() -> sendPushNotificationEnhanced(event, deviceTokens));
            
            // Track delivery
            trackPushDelivery(event, deviceTokens, sent);
            
            // Handle delivery status
            if (sent) {
                deliveredCount.incrementAndGet();
                deliveredCounter.increment();
                handleSuccessfulPush(event, deviceTokens);
                updatePlatformStats(event.getPlatform(), true, deviceTokens.size());
                trackAnalytics(event, deviceTokens, true);
            } else {
                failedCount.incrementAndGet();
                failedCounter.increment();
                handleFailedPush(event);
                updatePlatformStats(event.getPlatform(), false, deviceTokens.size());
                trackAnalytics(event, deviceTokens, false);
            }
            
            state.setStatus(sent ? "DELIVERED" : "FAILED");
            state.setDeliveryTime(Instant.now());
            
            log.debug("Successfully processed push notification: {}", event.getNotificationId());
            return sent;
        });
    }

    private boolean validatePushEvent(PushNotificationEvent event) {
        if (event == null || event.getNotificationId() == null || event.getNotificationId().trim().isEmpty()) {
            return false;
        }
        
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            return false;
        }
        
        if (event.getTitle() == null || event.getTitle().trim().isEmpty()) {
            return false;
        }
        
        if (event.getBody() == null || event.getBody().trim().isEmpty()) {
            return false;
        }
        
        return true;
    }
    
    private boolean checkRateLimit(String platform, String priority) {
        RateLimitState globalLimit = rateLimits.get("global");
        RateLimitState platformLimit = rateLimits.get(platform != null ? platform.toLowerCase() : "unknown");
        RateLimitState priorityLimit = "CRITICAL".equals(priority) ? rateLimits.get("critical") : null;
        
        // Critical priority bypasses platform limits
        if ("CRITICAL".equals(priority)) {
            return globalLimit.tryAcquire() && 
                   (priorityLimit == null || priorityLimit.tryAcquire());
        }
        
        return globalLimit.tryAcquire() && 
               (platformLimit == null || platformLimit.tryAcquire());
    }
    
    private void queueForLaterDelivery(PushNotificationEvent event) {
        log.info("Rate limit exceeded, queueing push notification {} for later delivery", event.getNotificationId());
        scheduledPushes.put(event.getNotificationId(), 
            new ScheduledPush(event.getNotificationId(), event, Instant.now().plus(Duration.ofMinutes(2))));
    }
    
    private boolean validatePayloadSize(PushNotificationEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(preparePushPayload(event));
            int sizeKB = payload.getBytes().length / 1024;
            return sizeKB <= MAX_PAYLOAD_SIZE_KB;
        } catch (Exception e) {
            log.error("Error validating payload size", e);
            return false;
        }
    }
    
    private List<String> getActiveDeviceTokensEnhanced(PushNotificationEvent event) {
        List<String> tokens = getActiveDeviceTokens(event);
        
        // Filter out known invalid tokens
        Set<String> invalidTokenSet = invalidTokens.getOrDefault(event.getPlatform(), new HashSet<>());
        tokens = tokens.stream()
            .filter(token -> !invalidTokenSet.contains(token))
            .collect(Collectors.toList());
        
        // Limit devices per notification
        if (tokens.size() > MAX_DEVICES_PER_NOTIFICATION) {
            log.warn("Too many devices for notification {}: {}, limiting to {}", 
                event.getNotificationId(), tokens.size(), MAX_DEVICES_PER_NOTIFICATION);
            tokens = tokens.subList(0, MAX_DEVICES_PER_NOTIFICATION);
        }
        
        return tokens;
    }
    
    private boolean processBatchDelivery(PushNotificationEvent event, List<String> deviceTokens, PushProcessingState state) {
        log.info("Processing batch delivery for {} with {} devices", event.getNotificationId(), deviceTokens.size());
        
        batchCount.incrementAndGet();
        batchCounter.increment();
        
        // Split into batches
        List<List<String>> batches = partitionTokens(deviceTokens, MAX_BATCH_SIZE);
        boolean anySuccess = false;
        
        for (int i = 0; i < batches.size(); i++) {
            List<String> batchTokens = batches.get(i);
            String batchId = event.getNotificationId() + "_batch_" + i;
            
            try {
                boolean sent = sendPushNotificationEnhanced(event, batchTokens);
                if (sent) {
                    anySuccess = true;
                    deliveredCount.addAndGet(batchTokens.size());
                    deliveredCounter.increment();
                } else {
                    failedCount.addAndGet(batchTokens.size());
                    failedCounter.increment();
                }
                
                trackPushDelivery(event, batchTokens, sent);
                
                // Small delay between batches to avoid overwhelming providers
                TimeUnit.MILLISECONDS.sleep(100);
                
            } catch (Exception e) {
                log.error("Error processing batch {} for notification {}", i, event.getNotificationId(), e);
                failedCount.addAndGet(batchTokens.size());
                failedCounter.increment();
            }
        }
        
        state.setBatchCount(batches.size());
        return anySuccess;
    }
    
    private List<List<String>> partitionTokens(List<String> tokens, int batchSize) {
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i += batchSize) {
            batches.add(tokens.subList(i, Math.min(i + batchSize, tokens.size())));
        }
        return batches;
    }
    
    private boolean sendPushNotificationEnhanced(PushNotificationEvent event, List<String> deviceTokens) {
        try {
            boolean sent = sendPushNotification(event, deviceTokens);
            
            // Track invalid tokens if delivery failed
            if (!sent) {
                trackInvalidTokens(event.getPlatform(), deviceTokens);
            }
            
            return sent;
        } catch (Exception e) {
            log.error("Enhanced push send failed for {}: {}", event.getNotificationId(), e.getMessage());
            trackInvalidTokens(event.getPlatform(), deviceTokens);
            return false;
        }
    }
    
    private void trackInvalidTokens(String platform, List<String> tokens) {
        Set<String> invalidSet = invalidTokens.computeIfAbsent(platform, k -> new HashSet<>());
        invalidSet.addAll(tokens);
        invalidTokenCount.addAndGet(tokens.size());
        invalidTokenCounter.increment();
    }
    
    private void updatePlatformStats(String platform, boolean success, int deviceCount) {
        if (platform == null) return;
        
        PlatformPerformance stats = platformStats.get(platform.toLowerCase());
        if (stats != null) {
            stats.addDeliveryAttempt(success, deviceCount);
        }
    }
    
    private void trackAnalytics(PushNotificationEvent event, List<String> tokens, boolean success) {
        PushAnalytics analytics = analyticsData.computeIfAbsent(event.getPlatform(),
            k -> new PushAnalytics(event.getPlatform()));
        
        analytics.addDelivery(
            success,
            tokens.size(),
            event.getPriority(),
            event.getNotificationType()
        );
        
        metricsService.recordMetric(
            "push.delivery.success",
            success ? 1.0 : 0.0,
            Map.of(
                "platform", event.getPlatform() != null ? event.getPlatform() : "unknown",
                "priority", event.getPriority() != null ? event.getPriority() : "normal",
                "deviceCount", String.valueOf(tokens.size())
            )
        );
    }

    private List<String> getActiveDeviceTokens(PushNotificationEvent event) {
        // Get all registered devices for user
        List<String> tokens = deviceRegistry.getDeviceTokens(event.getUserId());
        
        // Filter by platform if specified
        if (event.getPlatform() != null) {
            tokens = deviceRegistry.filterByPlatform(tokens, event.getPlatform());
        }
        
        // Filter by device IDs if specified
        if (event.getTargetDevices() != null && !event.getTargetDevices().isEmpty()) {
            tokens = deviceRegistry.filterByDeviceIds(tokens, event.getTargetDevices());
        }
        
        // Remove invalid tokens
        tokens = deviceRegistry.removeInvalidTokens(tokens);
        
        // Check device preferences
        tokens = filterByDevicePreferences(event, tokens);
        
        return tokens;
    }

    private List<String> filterByDevicePreferences(PushNotificationEvent event, List<String> tokens) {
        return tokens.stream()
            .filter(token -> {
                // Check if notifications enabled for device
                if (!deviceRegistry.isNotificationEnabled(token)) {
                    return false;
                }
                
                // Check quiet hours for device
                if (deviceRegistry.isInQuietHours(token) && 
                    !"CRITICAL".equals(event.getPriority())) {
                    return false;
                }
                
                // Check notification type preference
                if (!deviceRegistry.isTypeAllowed(token, event.getNotificationType())) {
                    return false;
                }
                
                return true;
            })
            .toList();
    }

    private boolean sendPushNotification(PushNotificationEvent event, List<String> deviceTokens) {
        try {
            // Prepare notification payload
            Map<String, Object> payload = preparePushPayload(event);
            
            // Send based on platform
            return switch (event.getPlatform() != null ? event.getPlatform() : "MULTI") {
                case "IOS" -> sendIosPush(event, deviceTokens, payload);
                case "ANDROID" -> sendAndroidPush(event, deviceTokens, payload);
                case "WEB" -> sendWebPush(event, deviceTokens, payload);
                case "MULTI" -> sendMultiPlatformPush(event, deviceTokens, payload);
                default -> {
                    log.warn("Unknown platform: {}", event.getPlatform());
                    yield false;
                }
            };
        } catch (Exception e) {
            log.error("Failed to send push notification: {}", e.getMessage());
            return false;
        }
    }

    private Map<String, Object> preparePushPayload(PushNotificationEvent event) {
        Map<String, Object> payload = new java.util.HashMap<>();
        
        // Basic notification data
        payload.put("title", event.getTitle());
        payload.put("body", event.getBody());
        payload.put("sound", event.getSound() != null ? event.getSound() : "default");
        payload.put("badge", event.getBadgeCount());
        
        // Rich media
        if (event.getImageUrl() != null) {
            payload.put("image", event.getImageUrl());
        }
        if (event.getVideoUrl() != null) {
            payload.put("video", event.getVideoUrl());
        }
        
        // Actions
        if (event.getActions() != null && !event.getActions().isEmpty()) {
            payload.put("actions", event.getActions());
        }
        
        // Deep linking
        if (event.getDeepLink() != null) {
            payload.put("deepLink", event.getDeepLink());
        }
        
        // Custom data
        if (event.getCustomData() != null) {
            payload.put("data", event.getCustomData());
        }
        
        // Notification options
        payload.put("priority", event.getPriority());
        payload.put("collapseKey", event.getCollapseKey());
        payload.put("ttl", event.getTimeToLive());
        
        return payload;
    }

    private boolean sendIosPush(PushNotificationEvent event, List<String> tokens, Map<String, Object> payload) {
        // Prepare iOS-specific payload
        Map<String, Object> iosPayload = new java.util.HashMap<>(payload);
        
        // Add iOS-specific fields
        iosPayload.put("category", event.getCategory());
        iosPayload.put("threadId", event.getThreadId());
        iosPayload.put("targetContentId", event.getTargetContentId());
        
        // Critical alerts for iOS
        if ("CRITICAL".equals(event.getPriority())) {
            iosPayload.put("criticalAlert", true);
            iosPayload.put("volume", 1.0);
        }
        
        // Send via APNS
        return pushService.sendApns(
            tokens,
            iosPayload,
            event.isProduction()
        );
    }

    private boolean sendAndroidPush(PushNotificationEvent event, List<String> tokens, Map<String, Object> payload) {
        // Prepare Android-specific payload
        Map<String, Object> androidPayload = new java.util.HashMap<>(payload);
        
        // Add Android-specific fields
        androidPayload.put("channelId", event.getChannelId());
        androidPayload.put("color", event.getColor());
        androidPayload.put("icon", event.getIcon());
        androidPayload.put("tag", event.getTag());
        
        // Notification priority for Android
        androidPayload.put("androidPriority", mapPriorityToAndroid(event.getPriority()));
        
        // Send via FCM
        return pushService.sendFcm(
            tokens,
            androidPayload
        );
    }

    private boolean sendWebPush(PushNotificationEvent event, List<String> tokens, Map<String, Object> payload) {
        // Prepare Web Push payload
        Map<String, Object> webPayload = new java.util.HashMap<>(payload);
        
        // Add Web Push specific fields
        webPayload.put("icon", event.getWebIcon());
        webPayload.put("requireInteraction", event.isRequireInteraction());
        webPayload.put("vibrate", event.getVibrationPattern());
        webPayload.put("timestamp", LocalDateTime.now().toString());
        
        // Send via Web Push API
        return pushService.sendWebPush(
            tokens,
            webPayload,
            event.getVapidKeys()
        );
    }

    private boolean sendMultiPlatformPush(PushNotificationEvent event, List<String> tokens, Map<String, Object> payload) {
        boolean anySuccess = false;
        
        // Group tokens by platform
        Map<String, List<String>> tokensByPlatform = deviceRegistry.groupByPlatform(tokens);
        
        // Send to each platform
        for (Map.Entry<String, List<String>> entry : tokensByPlatform.entrySet()) {
            String platform = entry.getKey();
            List<String> platformTokens = entry.getValue();
            
            event.setPlatform(platform);
            boolean sent = sendPushNotification(event, platformTokens);
            
            if (sent) {
                anySuccess = true;
            }
        }
        
        return anySuccess;
    }

    private String mapPriorityToAndroid(String priority) {
        return switch (priority) {
            case "CRITICAL", "HIGH" -> "high";
            case "NORMAL" -> "normal";
            case "LOW" -> "low";
            default -> "default";
        };
    }

    private void trackPushDelivery(PushNotificationEvent event, List<String> tokens, boolean sent) {
        for (String token : tokens) {
            trackingService.trackPushNotification(
                event.getNotificationId(),
                event.getUserId(),
                token,
                sent ? "SENT" : "FAILED",
                LocalDateTime.now()
            );
        }
        
        // Update platform metrics
        trackingService.updatePushMetrics(
            event.getPlatform(),
            tokens.size(),
            sent ? tokens.size() : 0,
            event.getPriority()
        );
    }

    private void handleSuccessfulPush(PushNotificationEvent event, List<String> tokens) {
        // Update delivery status
        pushService.markAsDelivered(
            event.getNotificationId(),
            tokens,
            LocalDateTime.now()
        );
        
        // Schedule interaction tracking
        pushService.scheduleInteractionTracking(
            event.getNotificationId(),
            event.getUserId(),
            event.getInteractionTrackingDuration()
        );
        
        // Update badge count if specified
        if (event.getBadgeCount() != null) {
            deviceRegistry.updateBadgeCount(
                event.getUserId(),
                event.getBadgeCount()
            );
        }
        
        // Trigger success callback
        if (event.getSuccessCallback() != null) {
            pushService.triggerCallback(
                event.getSuccessCallback(),
                event.getNotificationId(),
                "SUCCESS",
                Map.of(
                    "deliveredTo", tokens.size(),
                    "timestamp", LocalDateTime.now()
                )
            );
        }
    }

    private void handleFailedPush(PushNotificationEvent event) {
        // Increment failure count
        int failures = pushService.incrementFailureCount(event.getNotificationId());
        
        // Retry if below threshold
        if (failures < event.getMaxRetries()) {
            pushService.scheduleRetry(
                event,
                calculateRetryDelay(failures)
            );
            
            log.info("Scheduled push retry {} for notification: {}", 
                    failures + 1, event.getNotificationId());
        } else {
            // Mark as permanently failed
            pushService.markAsFailed(
                event.getNotificationId(),
                "MAX_RETRIES_EXCEEDED"
            );
            
            // Send to DLQ
            pushService.sendToDeadLetterQueue(event);
            
            // Trigger failure callback
            if (event.getFailureCallback() != null) {
                pushService.triggerCallback(
                    event.getFailureCallback(),
                    event.getNotificationId(),
                    "FAILED",
                    Map.of(
                        "reason", "MAX_RETRIES_EXCEEDED",
                        "attempts", failures
                    )
                );
            }
        }
    }

    private void handleNoDevices(PushNotificationEvent event) {
        // Log no devices found
        pushService.logNoDevices(
            event.getNotificationId(),
            event.getUserId()
        );
        
        // Try alternative delivery method if configured
        if (event.getFallbackChannel() != null) {
            pushService.fallbackToAlternativeChannel(
                event.getUserId(),
                event.getFallbackChannel(),
                event.getTitle(),
                event.getBody()
            );
        }
        
        // Update status
        pushService.updateStatus(
            event.getNotificationId(),
            "NO_DEVICES"
        );
    }

    private LocalDateTime calculateRetryDelay(int attemptNumber) {
        // Exponential backoff: 30s, 1m, 2m, 4m, etc.
        long delaySeconds = (long) Math.pow(2, attemptNumber - 1) * 30;
        return LocalDateTime.now().plusSeconds(delaySeconds);
    }
    
    // Enhanced scheduled methods
    @Scheduled(fixedDelay = 10000)
    public void processBatches() {
        activeBatches.values().stream()
            .filter(PushBatch::shouldProcess)
            .forEach(this::processBatch);
    }
    
    private boolean processBatch(PushBatch batch) {
        try {
            log.info("Processing push batch {} with {} notifications", batch.getId(), batch.getSize());
            
            List<PushNotificationEvent> notifications = batch.getNotifications();
            
            for (PushNotificationEvent notification : notifications) {
                try {
                    processEventWithEnhancements(notification);
                } catch (Exception e) {
                    log.error("Error processing push notification in batch {}: {}", batch.getId(), e.getMessage());
                }
            }
            
            activeBatches.remove(batch.getId());
            return true;
            
        } catch (Exception e) {
            log.error("Error processing push batch {}", batch.getId(), e);
            return false;
        }
    }
    
    @Scheduled(fixedDelay = 60000)
    public void processScheduledPushes() {
        Instant now = Instant.now();
        
        scheduledPushes.values().stream()
            .filter(scheduled -> scheduled.getScheduledTime().isBefore(now))
            .forEach(scheduled -> {
                try {
                    processEventWithEnhancements(scheduled.getEvent());
                    scheduledPushes.remove(scheduled.getId());
                } catch (Exception e) {
                    log.error("Error processing scheduled push notification {}", scheduled.getId(), e);
                }
            });
    }
    
    @Scheduled(fixedDelay = 300000)
    public void updatePlatformStats() {
        try {
            double deliveryRate = calculateDeliveryRate();
            
            if (deliveryRate < DELIVERY_RATE_THRESHOLD) {
                alertingService.sendAlert(
                    "Low Push Delivery Rate",
                    String.format("Current delivery rate: %.2f%%", deliveryRate * 100),
                    AlertingService.AlertSeverity.HIGH
                );
            }
            
            platformStats.values().forEach(this::analyzePlatformPerformance);
            
        } catch (Exception e) {
            log.error("Error updating platform statistics", e);
        }
    }
    
    @Scheduled(fixedDelay = 3600000)
    public void cleanupExpiredData() {
        try {
            Instant expiryTime = Instant.now().minus(Duration.ofDays(7));
            
            pushStates.entrySet().removeIf(entry -> 
                entry.getValue().getCreatedAt().isBefore(expiryTime));
            
            scheduledPushes.entrySet().removeIf(entry ->
                entry.getValue().getScheduledTime().isBefore(expiryTime.minus(Duration.ofDays(1))));
            
            analyticsData.entrySet().removeIf(entry ->
                entry.getValue().getLastUpdate().isBefore(expiryTime));
            
            log.info("Cleaned up expired push notification data");
            
        } catch (Exception e) {
            log.error("Error cleaning up expired push data", e);
        }
    }
    
    @Scheduled(fixedDelay = 1800000)
    public void analyzeDeviceMetrics() {
        try {
            Map<String, DeviceMetricsAnalysis> analyses = new HashMap<>();
            
            deviceMetrics.values().forEach(metrics -> {
                DeviceMetricsAnalysis analysis = analyzeDeviceMetrics(metrics);
                analyses.put(metrics.getDeviceToken(), analysis);
            });
            
            analyses.values().stream()
                .filter(analysis -> analysis.hasAnomalies())
                .forEach(analysis -> {
                    log.warn("Device anomaly detected for {}: {}", 
                        maskToken(analysis.getDeviceToken()), analysis.getAnomalies());
                });
                
            generateDeviceReport(analyses);
            
        } catch (Exception e) {
            log.error("Error analyzing device metrics", e);
        }
    }
    
    @Scheduled(fixedDelay = 14400000)
    public void cleanupInvalidTokens() {
        try {
            Instant cutoff = Instant.now().minus(Duration.ofDays(TOKEN_CLEANUP_DAYS));
            
            invalidTokens.values().forEach(tokens -> {
                tokens.removeIf(token -> shouldCleanupToken(token, cutoff));
            });
            
            // Remove empty platform sets
            invalidTokens.entrySet().removeIf(entry -> entry.getValue().isEmpty());
            
            log.info("Cleaned up invalid tokens older than {} days", TOKEN_CLEANUP_DAYS);
            
        } catch (Exception e) {
            log.error("Error cleaning up invalid tokens", e);
        }
    }
    
    private void analyzePlatformPerformance(PlatformPerformance performance) {
        if (performance.getDeliveryRate() < DELIVERY_RATE_THRESHOLD) {
            log.warn("Poor performance for platform {}: {:.2f}% delivery rate", 
                performance.getPlatform(), performance.getDeliveryRate() * 100);
        }
        
        if (performance.getAverageLatency() > 5000) {
            log.warn("High latency for platform {}: {}ms", 
                performance.getPlatform(), performance.getAverageLatency());
        }
    }
    
    private DeviceMetricsAnalysis analyzeDeviceMetrics(DeviceMetrics metrics) {
        return new DeviceMetricsAnalysis(
            metrics.getDeviceToken(),
            metrics.getDeliveryRate(),
            metrics.getFailurePatterns(),
            metrics.getAnomalies()
        );
    }
    
    private void generateDeviceReport(Map<String, DeviceMetricsAnalysis> analyses) {
        double avgDeliveryRate = analyses.values().stream()
            .mapToDouble(DeviceMetricsAnalysis::getDeliveryRate)
            .average()
            .orElse(0.0);
            
        long anomalyCount = analyses.values().stream()
            .mapToLong(analysis -> analysis.getAnomalies().size())
            .sum();
            
        log.info("Push Device Report - Avg Delivery Rate: {:.2f}%, Anomalies: {}",
            avgDeliveryRate * 100, anomalyCount);
    }
    
    private boolean shouldCleanupToken(String token, Instant cutoff) {
        try {
            // Check if token should be cleaned up based on age and invalidity
            return checkTokenForCleanup(token, cutoff);
        } catch (Exception e) {
            log.error("Error checking token cleanup status: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if token should be cleaned up
     */
    private boolean checkTokenForCleanup(String token, Instant cutoff) {
        try {
            // In production, this would check token metadata from database:
            // - When was it last seen as invalid
            // - How many consecutive failures
            // - When was it last successfully used
            
            log.debug("Checking cleanup status for token: {}", maskToken(token));
            
            // For now, return false to preserve all tokens
            // In production: return tokenRepository.shouldCleanup(token, cutoff);
            return false;
            
        } catch (Exception e) {
            log.error("Error in token cleanup check: {}", e.getMessage());
            return false;
        }
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() < 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }
    
    private double calculateDeliveryRate() {
        if (deliveredCount.get() == 0 && failedCount.get() == 0) return 1.0;
        return deliveredCount.get() / (double) (deliveredCount.get() + failedCount.get());
    }
    
    // Enhanced error handling methods
    private void handleInvalidEvent(PushNotificationEvent event, String rawMessage) {
        log.error("Invalid push notification event received: {}", rawMessage);
        
        metricsService.recordMetric(
            "push.notifications.invalid",
            1.0,
            Map.of("platform", event != null && event.getPlatform() != null ? event.getPlatform() : "unknown")
        );
    }
    
    private void handleProcessingFailure(PushNotificationEvent event, String rawMessage) {
        errorCount.incrementAndGet();
        errorCounter.increment();
        
        log.error("Failed to process push notification: {}", event.getNotificationId());
        
        alertingService.sendAlert(
            "Push Notification Processing Failed",
            String.format("Failed to process push notification %s", event.getNotificationId()),
            AlertingService.AlertSeverity.MEDIUM
        );
    }
    
    private void handleProcessingError(Exception e, String rawMessage) {
        log.error("Processing error for push notification message: {}", rawMessage, e);
        
        if (errorCount.get() > 100) {
            alertingService.sendAlert(
                "High Push Processing Error Rate",
                "More than 100 push processing errors detected",
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
            log.info("Sent push notification message to DLQ with reason: {}", reason);
            
        } catch (Exception e) {
            log.error("Failed to send push notification message to DLQ", e);
        }
    }
    
    private boolean isRetryableException(Throwable throwable) {
        return throwable instanceof java.net.SocketTimeoutException ||
               throwable instanceof java.io.IOException ||
               throwable instanceof java.net.ConnectException;
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Enhanced PushNotificationConsumer");
        
        try {
            scheduledExecutor.shutdown();
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            activeBatches.values().forEach(this::processBatch);
            
            log.info("Enhanced PushNotificationConsumer shutdown completed. Processed: {}, Errors: {}, Delivered: {}, Failed: {}",
                processedCount.get(), errorCount.get(), deliveredCount.get(), failedCount.get());
                
        } catch (Exception e) {
            log.error("Error during enhanced push consumer shutdown", e);
        }
    }
}