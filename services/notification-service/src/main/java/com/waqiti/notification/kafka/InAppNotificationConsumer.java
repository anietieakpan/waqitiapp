package com.waqiti.notification.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.monitoring.AlertingService;
import com.waqiti.notification.event.InAppNotificationEvent;
import com.waqiti.notification.service.InAppNotificationService;
import com.waqiti.notification.service.UserSessionService;
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
 * Production-grade Kafka consumer for in-app notifications
 * Enhanced with circuit breaker, retry mechanisms, comprehensive metrics, and real-time delivery
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InAppNotificationConsumer {

    private static final String TOPIC = "in-app-notifications";
    private static final String DLQ_TOPIC = "in-app-notifications-dlq";
    private static final String CONSUMER_GROUP = "notification-inapp-processor-group";
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 500;
    private static final double RETRY_MULTIPLIER = 2.0;
    
    private static final int MAX_BATCH_SIZE = 100;
    private static final int RATE_LIMIT_PER_MINUTE = 2000;
    private static final int MAX_NOTIFICATIONS_PER_USER = 50;
    private static final double DELIVERY_RATE_THRESHOLD = 0.98;
    private static final int NOTIFICATION_TTL_HOURS = 72;
    private static final int MAX_NOTIFICATION_LENGTH = 5000;
    
    // Services
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MetricsService metricsService;
    private final AlertingService alertingService;
    private final InAppNotificationService inAppService;
    private final UserSessionService sessionService;
    private final NotificationTrackingService trackingService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler universalDLQHandler;
    
    // Resilience components
    private CircuitBreaker circuitBreaker;
    private Retry retryMechanism;
    private ScheduledExecutorService scheduledExecutor;
    
    // State management
    private final Map<String, InAppProcessingState> notificationStates = new ConcurrentHashMap<>();
    private final Map<String, NotificationBatch> activeBatches = new ConcurrentHashMap<>();
    private final Map<String, UserNotificationQueue> userQueues = new ConcurrentHashMap<>();
    private final Map<String, SessionMetrics> sessionMetrics = new ConcurrentHashMap<>();
    private final Map<String, RateLimitState> rateLimits = new ConcurrentHashMap<>();
    private final Map<String, ScheduledNotification> scheduledNotifications = new ConcurrentHashMap<>();
    private final Map<String, NotificationAnalytics> analyticsData = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> activeUserSessions = new ConcurrentHashMap<>();
    private final Map<String, NotificationTemplate> templateCache = new ConcurrentHashMap<>();
    
    // Metrics
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong deliveredCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong readCount = new AtomicLong(0);
    private final AtomicLong dismissedCount = new AtomicLong(0);
    private final AtomicLong expiredCount = new AtomicLong(0);
    private final AtomicLong offlineCount = new AtomicLong(0);
    
    private Counter processedCounter;
    private Counter errorCounter;
    private Counter deliveredCounter;
    private Counter failedCounter;
    private Counter readCounter;
    private Counter dismissedCounter;
    private Counter expiredCounter;
    private Counter offlineCounter;
    private Gauge activeNotificationsGauge;
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
        initializeTemplateCache();
        log.info("InAppNotificationConsumer initialized successfully");
    }
    
    private void initializeMetrics() {
        processedCounter = meterRegistry.counter("inapp.notifications.processed.total");
        errorCounter = meterRegistry.counter("inapp.notifications.errors.total");
        deliveredCounter = meterRegistry.counter("inapp.notifications.delivered.total");
        failedCounter = meterRegistry.counter("inapp.notifications.failed.total");
        readCounter = meterRegistry.counter("inapp.notifications.read.total");
        dismissedCounter = meterRegistry.counter("inapp.notifications.dismissed.total");
        expiredCounter = meterRegistry.counter("inapp.notifications.expired.total");
        offlineCounter = meterRegistry.counter("inapp.notifications.offline.total");
        
        activeNotificationsGauge = Gauge.builder("inapp.notifications.active", notificationStates, Map::size)
            .description("Number of active in-app notifications")
            .register(meterRegistry);
            
        deliveryRateGauge = Gauge.builder("inapp.notifications.delivery.rate", this, 
            consumer -> calculateDeliveryRate())
            .description("Current in-app delivery rate")
            .register(meterRegistry);
            
        processingTimer = Timer.builder("inapp.notifications.processing.duration")
            .description("In-app notification processing duration")
            .register(meterRegistry);
            
        deliveryTimer = Timer.builder("inapp.notifications.delivery.duration")
            .description("In-app notification delivery duration")
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
            
        circuitBreaker = CircuitBreaker.of("inapp-notification-processor", config);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                log.warn("Circuit breaker state transition: {}", event);
                alertingService.sendAlert(
                    "Circuit Breaker State Change",
                    String.format("In-app notification circuit breaker transitioned from %s to %s",
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
            
        retryMechanism = Retry.of("inapp-notification-retry", config);
        
        retryMechanism.getEventPublisher()
            .onRetry(event -> log.debug("Retry attempt {} for in-app notification", 
                event.getNumberOfRetryAttempts()));
    }
    
    private void initializeScheduledTasks() {
        scheduledExecutor = Executors.newScheduledThreadPool(6);
        
        scheduledExecutor.scheduleAtFixedRate(
            this::processBatches,
            0, 5, TimeUnit.SECONDS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::processScheduledNotifications,
            0, 1, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::updateSessionMetrics,
            0, 2, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::cleanupExpiredNotifications,
            0, 30, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeUserEngagement,
            0, 15, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::optimizeNotificationQueues,
            0, 1, TimeUnit.HOURS
        );
    }
    
    private void initializeRateLimits() {
        rateLimits.put("global", new RateLimitState(RATE_LIMIT_PER_MINUTE));
        rateLimits.put("transactional", new RateLimitState(3000));
        rateLimits.put("promotional", new RateLimitState(1000));
        rateLimits.put("system", new RateLimitState(5000));
        rateLimits.put("alert", new RateLimitState(4000));
    }
    
    private void initializeTemplateCache() {
        try {
            // Load notification templates into cache
            templateCache.put("welcome", new NotificationTemplate("welcome", "Welcome to Waqiti!", "Welcome {{userName}}! Your account is ready."));
            templateCache.put("transaction", new NotificationTemplate("transaction", "Transaction Alert", "{{transactionType}} of {{amount}} {{currency}} completed."));
            templateCache.put("security", new NotificationTemplate("security", "Security Alert", "New {{deviceType}} login detected from {{location}}."));
            templateCache.put("system", new NotificationTemplate("system", "System Update", "{{message}}"));
            log.info("Loaded {} notification templates into cache", templateCache.size());
        } catch (Exception e) {
            log.error("Failed to initialize template cache", e);
        }
    }
    
    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    public void processInAppNotification(@Payload String message,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                       @Header(KafkaHeaders.OFFSET) long offset,
                                       @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                       Acknowledgment acknowledgment) {
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        
        try {
            log.debug("Processing in-app notification from partition {} offset {}", partition, offset);
            
            InAppNotificationEvent event = parseEvent(message);
            MDC.put("notificationId", event.getNotificationId());
            MDC.put("userId", event.getUserId());
            MDC.put("type", event.getType());
            
            if (!validateEvent(event)) {
                log.error("Invalid in-app notification event: {}", event);
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
            log.error("Error processing in-app notification", e);
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
    
    private InAppNotificationEvent parseEvent(String message) throws JsonProcessingException {
        return objectMapper.readValue(message, InAppNotificationEvent.class);
    }
    
    private boolean validateEvent(InAppNotificationEvent event) {
        if (event == null || event.getNotificationId() == null || event.getNotificationId().trim().isEmpty()) {
            return false;
        }
        
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            return false;
        }
        
        if (event.getTitle() == null || event.getTitle().trim().isEmpty()) {
            return false;
        }
        
        if (event.getMessage() != null && event.getMessage().length() > MAX_NOTIFICATION_LENGTH) {
            log.warn("In-app notification message too long: {} characters", event.getMessage().length());
            return false;
        }
        
        return true;
    }
    
    private boolean processEvent(InAppNotificationEvent event) {
        return processingTimer.recordCallable(() -> {
            switch (event.getType()) {
                case "SHOW_NOTIFICATION":
                    return processShowNotification(event);
                case "BATCH_NOTIFICATION":
                    return processBatchNotification(event);
                case "TEMPLATED_NOTIFICATION":
                    return processTemplatedNotification(event);
                case "TRANSACTIONAL_NOTIFICATION":
                    return processTransactionalNotification(event);
                case "PROMOTIONAL_NOTIFICATION":
                    return processPromotionalNotification(event);
                case "SYSTEM_NOTIFICATION":
                    return processSystemNotification(event);
                case "ALERT_NOTIFICATION":
                    return processAlertNotification(event);
                case "NOTIFICATION_READ":
                    return processNotificationRead(event);
                case "NOTIFICATION_DISMISSED":
                    return processNotificationDismissed(event);
                case "NOTIFICATION_CLICKED":
                    return processNotificationClicked(event);
                case "BULK_MARK_READ":
                    return processBulkMarkRead(event);
                case "CLEAR_ALL_NOTIFICATIONS":
                    return processClearAllNotifications(event);
                case "SCHEDULED_NOTIFICATION":
                    return processScheduledNotification(event);
                case "UPDATE_NOTIFICATION_SETTINGS":
                    return processUpdateNotificationSettings(event);
                case "GET_NOTIFICATION_COUNT":
                    return processGetNotificationCount(event);
                default:
                    log.warn("Unknown in-app notification type: {}", event.getType());
                    return false;
            }
        });
    }
    
    private boolean processShowNotification(InAppNotificationEvent event) {
        try {
            String notificationId = event.getNotificationId();
            InAppProcessingState state = new InAppProcessingState(notificationId);
            notificationStates.put(notificationId, state);
            
            if (!checkRateLimit(event.getCategory())) {
                queueForLaterDelivery(event);
                return true;
            }
            
            // Check user's notification queue capacity
            UserNotificationQueue userQueue = getUserQueue(event.getUserId());
            if (userQueue.getSize() >= MAX_NOTIFICATIONS_PER_USER) {
                log.warn("User {} notification queue full, removing oldest", event.getUserId());
                userQueue.removeOldest();
            }
            
            state.setStatus("PROCESSING");
            
            // Check if user is online
            boolean isOnline = isUserOnline(event.getUserId());
            
            if (isOnline) {
                // Deliver immediately via WebSocket/SSE
                boolean delivered = deliveryTimer.recordCallable(() -> 
                    inAppService.deliverNotification(
                        event.getUserId(),
                        event.getNotificationId(),
                        event.getTitle(),
                        event.getMessage(),
                        event.getIcon(),
                        event.getActions(),
                        event.getPriority(),
                        event.getExpiry()
                    )
                );
                
                if (delivered) {
                    deliveredCount.incrementAndGet();
                    deliveredCounter.increment();
                    state.setStatus("DELIVERED");
                    state.setDeliveryTime(Instant.now());
                    trackDelivery(event, true);
                } else {
                    failedCount.incrementAndGet();
                    failedCounter.increment();
                    state.setStatus("FAILED");
                    trackDelivery(event, false);
                }
                
                return delivered;
            } else {
                // Store for later delivery when user comes online
                userQueue.addNotification(event);
                offlineCount.incrementAndGet();
                offlineCounter.increment();
                state.setStatus("QUEUED");
                trackDelivery(event, true); // Counts as successful queuing
                return true;
            }
            
        } catch (Exception e) {
            log.error("Error processing show notification for {}", event.getNotificationId(), e);
            return false;
        }
    }
    
    private boolean processBatchNotification(InAppNotificationEvent event) {
        try {
            String batchId = event.getBatchId();
            NotificationBatch batch = activeBatches.computeIfAbsent(batchId, 
                k -> new NotificationBatch(batchId, MAX_BATCH_SIZE));
            
            batch.addNotification(event);
            
            if (batch.shouldProcess()) {
                return processBatch(batch);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing batch notification", e);
            return false;
        }
    }
    
    private boolean processTemplatedNotification(InAppNotificationEvent event) {
        try {
            String templateId = event.getTemplateId();
            NotificationTemplate template = getTemplate(templateId);
            
            if (template == null) {
                log.error("Notification template {} not found", templateId);
                return false;
            }
            
            String renderedTitle = renderTemplate(template.getTitle(), event.getTemplateVariables());
            String renderedMessage = renderTemplate(template.getMessage(), event.getTemplateVariables());
            
            event.setTitle(renderedTitle);
            event.setMessage(renderedMessage);
            event.setType("SHOW_NOTIFICATION");
            
            return processShowNotification(event);
            
        } catch (Exception e) {
            log.error("Error processing templated notification", e);
            return false;
        }
    }
    
    private boolean processTransactionalNotification(InAppNotificationEvent event) {
        try {
            event.setPriority("high");
            event.setCategory("transactional");
            event.setExpiry(Instant.now().plus(Duration.ofHours(NOTIFICATION_TTL_HOURS)));
            
            return processShowNotification(event);
            
        } catch (Exception e) {
            log.error("Error processing transactional notification", e);
            return false;
        }
    }
    
    private boolean processPromotionalNotification(InAppNotificationEvent event) {
        try {
            // Check user preferences for promotional notifications
            if (!hasPromotionalConsent(event.getUserId())) {
                log.info("User {} has no promotional consent", event.getUserId());
                return true;
            }
            
            event.setPriority("low");
            event.setCategory("promotional");
            event.setExpiry(Instant.now().plus(Duration.ofHours(24)));
            
            return processShowNotification(event);
            
        } catch (Exception e) {
            log.error("Error processing promotional notification", e);
            return false;
        }
    }
    
    private boolean processSystemNotification(InAppNotificationEvent event) {
        try {
            event.setPriority("high");
            event.setCategory("system");
            event.setExpiry(Instant.now().plus(Duration.ofHours(NOTIFICATION_TTL_HOURS)));
            
            return processShowNotification(event);
            
        } catch (Exception e) {
            log.error("Error processing system notification", e);
            return false;
        }
    }
    
    private boolean processAlertNotification(InAppNotificationEvent event) {
        try {
            event.setPriority("urgent");
            event.setCategory("alert");
            event.setExpiry(Instant.now().plus(Duration.ofHours(NOTIFICATION_TTL_HOURS)));
            event.setSticky(true); // Alert notifications are sticky by default
            
            return processShowNotification(event);
            
        } catch (Exception e) {
            log.error("Error processing alert notification", e);
            return false;
        }
    }
    
    private boolean processNotificationRead(InAppNotificationEvent event) {
        try {
            String notificationId = event.getNotificationId();
            String userId = event.getUserId();
            
            boolean marked = inAppService.markAsRead(userId, notificationId);
            
            if (marked) {
                readCount.incrementAndGet();
                readCounter.increment();
                
                // Update analytics
                NotificationAnalytics analytics = getAnalytics(event.getCategory());
                analytics.incrementReadCount();
                
                // Track user engagement
                updateUserEngagement(userId, "READ", notificationId);
            }
            
            return marked;
            
        } catch (Exception e) {
            log.error("Error processing notification read", e);
            return false;
        }
    }
    
    private boolean processNotificationDismissed(InAppNotificationEvent event) {
        try {
            String notificationId = event.getNotificationId();
            String userId = event.getUserId();
            
            boolean dismissed = inAppService.dismissNotification(userId, notificationId);
            
            if (dismissed) {
                dismissedCount.incrementAndGet();
                dismissedCounter.increment();
                
                // Update analytics
                NotificationAnalytics analytics = getAnalytics(event.getCategory());
                analytics.incrementDismissedCount();
                
                // Track user engagement
                updateUserEngagement(userId, "DISMISSED", notificationId);
            }
            
            return dismissed;
            
        } catch (Exception e) {
            log.error("Error processing notification dismissed", e);
            return false;
        }
    }
    
    private boolean processNotificationClicked(InAppNotificationEvent event) {
        try {
            String notificationId = event.getNotificationId();
            String userId = event.getUserId();
            String actionId = event.getActionId();
            
            boolean clicked = inAppService.handleNotificationClick(userId, notificationId, actionId);
            
            if (clicked) {
                // Update analytics
                NotificationAnalytics analytics = getAnalytics(event.getCategory());
                analytics.incrementClickedCount();
                
                // Track user engagement
                updateUserEngagement(userId, "CLICKED", notificationId);
                
                // Execute action if specified
                if (actionId != null) {
                    executeNotificationAction(userId, notificationId, actionId, event.getActionData());
                }
            }
            
            return clicked;
            
        } catch (Exception e) {
            log.error("Error processing notification clicked", e);
            return false;
        }
    }
    
    private boolean processBulkMarkRead(InAppNotificationEvent event) {
        try {
            String userId = event.getUserId();
            List<String> notificationIds = event.getNotificationIds();
            
            int markedCount = inAppService.bulkMarkAsRead(userId, notificationIds);
            
            readCount.addAndGet(markedCount);
            
            log.info("Bulk marked {} notifications as read for user {}", markedCount, userId);
            
            return markedCount > 0;
            
        } catch (Exception e) {
            log.error("Error processing bulk mark read", e);
            return false;
        }
    }
    
    private boolean processClearAllNotifications(InAppNotificationEvent event) {
        try {
            String userId = event.getUserId();
            
            int clearedCount = inAppService.clearAllNotifications(userId);
            
            // Remove from user queue
            UserNotificationQueue userQueue = userQueues.get(userId);
            if (userQueue != null) {
                userQueue.clear();
            }
            
            log.info("Cleared {} notifications for user {}", clearedCount, userId);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing clear all notifications", e);
            return false;
        }
    }
    
    private boolean processScheduledNotification(InAppNotificationEvent event) {
        try {
            String scheduleId = event.getScheduleId();
            Instant scheduledTime = event.getScheduledTime();
            
            ScheduledNotification scheduled = new ScheduledNotification(scheduleId, event, scheduledTime);
            scheduledNotifications.put(scheduleId, scheduled);
            
            log.info("Scheduled in-app notification {} for {}", scheduleId, scheduledTime);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing scheduled notification", e);
            return false;
        }
    }
    
    private boolean processUpdateNotificationSettings(InAppNotificationEvent event) {
        try {
            String userId = event.getUserId();
            NotificationSettings settings = event.getNotificationSettings();
            
            boolean updated = inAppService.updateNotificationSettings(userId, settings);
            
            log.info("Updated notification settings for user {}", userId);
            
            return updated;
            
        } catch (Exception e) {
            log.error("Error processing update notification settings", e);
            return false;
        }
    }
    
    private boolean processGetNotificationCount(InAppNotificationEvent event) {
        try {
            String userId = event.getUserId();
            
            NotificationCounts counts = inAppService.getNotificationCounts(userId);
            
            // Send response back via WebSocket if user is online
            if (isUserOnline(userId)) {
                inAppService.sendCountsUpdate(userId, counts);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error processing get notification count", e);
            return false;
        }
    }
    
    @Scheduled(fixedDelay = 5000)
    public void processBatches() {
        activeBatches.values().stream()
            .filter(NotificationBatch::shouldProcess)
            .forEach(this::processBatch);
    }
    
    private boolean processBatch(NotificationBatch batch) {
        try {
            log.info("Processing notification batch {} with {} notifications", batch.getId(), batch.getSize());
            
            List<InAppNotificationEvent> notifications = batch.getNotifications();
            
            for (InAppNotificationEvent notification : notifications) {
                try {
                    processShowNotification(notification);
                } catch (Exception e) {
                    log.error("Error processing notification in batch {}: {}", batch.getId(), e.getMessage());
                }
            }
            
            activeBatches.remove(batch.getId());
            return true;
            
        } catch (Exception e) {
            log.error("Error processing notification batch {}", batch.getId(), e);
            return false;
        }
    }
    
    @Scheduled(fixedDelay = 60000)
    public void processScheduledNotifications() {
        Instant now = Instant.now();
        
        scheduledNotifications.values().stream()
            .filter(scheduled -> scheduled.getScheduledTime().isBefore(now))
            .forEach(scheduled -> {
                try {
                    processShowNotification(scheduled.getEvent());
                    scheduledNotifications.remove(scheduled.getId());
                } catch (Exception e) {
                    log.error("Error processing scheduled notification {}", scheduled.getId(), e);
                }
            });
    }
    
    @Scheduled(fixedDelay = 120000)
    public void updateSessionMetrics() {
        try {
            // Update active user sessions
            Set<String> currentlyOnline = sessionService.getActiveUserSessions();
            
            for (String userId : currentlyOnline) {
                activeUserSessions.computeIfAbsent(userId, k -> new HashSet<>()).add("ACTIVE");
                
                // Check if user has queued notifications
                UserNotificationQueue queue = userQueues.get(userId);
                if (queue != null && !queue.isEmpty()) {
                    deliverQueuedNotifications(userId, queue);
                }
            }
            
            // Remove offline users
            activeUserSessions.entrySet().removeIf(entry -> 
                !currentlyOnline.contains(entry.getKey()));
            
        } catch (Exception e) {
            log.error("Error updating session metrics", e);
        }
    }
    
    @Scheduled(fixedDelay = 1800000)
    public void cleanupExpiredNotifications() {
        try {
            Instant now = Instant.now();
            int expiredCount = inAppService.cleanupExpiredNotifications(now);
            
            if (expiredCount > 0) {
                expiredCounter.increment(expiredCount);
                this.expiredCount.addAndGet(expiredCount);
                log.info("Cleaned up {} expired notifications", expiredCount);
            }
            
            // Clean up processing states
            notificationStates.entrySet().removeIf(entry -> 
                entry.getValue().getCreatedAt().isBefore(now.minus(Duration.ofHours(24))));
            
        } catch (Exception e) {
            log.error("Error cleaning up expired notifications", e);
        }
    }
    
    @Scheduled(fixedDelay = 900000)
    public void analyzeUserEngagement() {
        try {
            Map<String, UserEngagementSummary> engagementSummaries = new HashMap<>();
            
            analyticsData.values().forEach(analytics -> {
                UserEngagementSummary summary = calculateEngagementSummary(analytics);
                engagementSummaries.put(analytics.getCategory(), summary);
            });
            
            engagementSummaries.values().stream()
                .filter(summary -> summary.getEngagementRate() < 0.20)
                .forEach(summary -> {
                    log.warn("Low engagement for category {}: {}%", 
                        summary.getCategory(), summary.getEngagementRate() * 100);
                });
                
            generateEngagementReport(engagementSummaries);
            
        } catch (Exception e) {
            log.error("Error analyzing user engagement", e);
        }
    }
    
    @Scheduled(fixedDelay = 3600000)
    public void optimizeNotificationQueues() {
        try {
            userQueues.values().forEach(queue -> {
                if (queue.getSize() > MAX_NOTIFICATIONS_PER_USER * 0.8) {
                    queue.optimizeQueue(); // Remove low-priority old notifications
                }
            });
            
            log.info("Optimized {} user notification queues", userQueues.size());
            
        } catch (Exception e) {
            log.error("Error optimizing notification queues", e);
        }
    }
    
    private boolean checkRateLimit(String category) {
        RateLimitState globalLimit = rateLimits.get("global");
        RateLimitState categoryLimit = rateLimits.get(category);
        
        return globalLimit.tryAcquire() && 
               (categoryLimit == null || categoryLimit.tryAcquire());
    }
    
    private void queueForLaterDelivery(InAppNotificationEvent event) {
        log.info("Rate limit exceeded, queueing notification {} for later delivery", event.getNotificationId());
        scheduledNotifications.put(event.getNotificationId(), 
            new ScheduledNotification(event.getNotificationId(), event, Instant.now().plus(Duration.ofMinutes(1))));
    }
    
    private UserNotificationQueue getUserQueue(String userId) {
        return userQueues.computeIfAbsent(userId, k -> new UserNotificationQueue(userId));
    }
    
    private boolean isUserOnline(String userId) {
        return activeUserSessions.containsKey(userId) || sessionService.isUserOnline(userId);
    }
    
    private void deliverQueuedNotifications(String userId, UserNotificationQueue queue) {
        try {
            List<InAppNotificationEvent> notifications = queue.getNotifications(10); // Deliver up to 10 at once
            
            for (InAppNotificationEvent notification : notifications) {
                boolean delivered = inAppService.deliverNotification(
                    userId,
                    notification.getNotificationId(),
                    notification.getTitle(),
                    notification.getMessage(),
                    notification.getIcon(),
                    notification.getActions(),
                    notification.getPriority(),
                    notification.getExpiry()
                );
                
                if (delivered) {
                    deliveredCount.incrementAndGet();
                    deliveredCounter.increment();
                    queue.removeNotification(notification.getNotificationId());
                    trackDelivery(notification, true);
                }
            }
            
        } catch (Exception e) {
            log.error("Error delivering queued notifications for user {}", userId, e);
        }
    }
    
    private NotificationTemplate getTemplate(String templateId) {
        return templateCache.get(templateId);
    }
    
    private String renderTemplate(String template, Map<String, Object> variables) {
        if (template == null || variables == null) return template;
        
        String rendered = template;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            rendered = rendered.replace(placeholder, String.valueOf(entry.getValue()));
        }
        
        return rendered;
    }
    
    private boolean hasPromotionalConsent(String userId) {
        try {
            // Check user preferences for promotional consent
            return checkUserPromotionalConsent(userId);
        } catch (Exception e) {
            log.error("Error checking promotional consent for user {}: {}", userId, e.getMessage());
            // Default to false to respect user privacy
            return false;
        }
    }
    
    /**
     * Check if user has given consent for promotional notifications
     */
    private boolean checkUserPromotionalConsent(String userId) {
        try {
            // In production, this would query user preferences:
            // return preferencesService.hasPromotionalConsent(userId);
            
            log.debug("Checking promotional consent for user: {}", userId);
            
            // For now, default to true for transactional notifications
            // In production, this would be a proper preference check
            return true;
            
        } catch (Exception e) {
            log.error("Error in promotional consent check: {}", e.getMessage());
            return false;
        }
    }
    
    private void trackDelivery(InAppNotificationEvent event, boolean success) {
        trackingService.trackInAppNotification(
            event.getNotificationId(),
            event.getUserId(),
            success ? "DELIVERED" : "FAILED",
            Instant.now()
        );
        
        NotificationAnalytics analytics = getAnalytics(event.getCategory());
        analytics.addDelivery(success);
        
        metricsService.recordMetric(
            "inapp.delivery.success",
            success ? 1.0 : 0.0,
            Map.of(
                "category", event.getCategory() != null ? event.getCategory() : "unknown",
                "priority", event.getPriority() != null ? event.getPriority() : "normal"
            )
        );
    }
    
    private NotificationAnalytics getAnalytics(String category) {
        return analyticsData.computeIfAbsent(category != null ? category : "unknown",
            k -> new NotificationAnalytics(category));
    }
    
    private void updateUserEngagement(String userId, String action, String notificationId) {
        SessionMetrics metrics = sessionMetrics.computeIfAbsent(userId,
            k -> new SessionMetrics(userId));
        
        metrics.addInteraction(action, notificationId, Instant.now());
    }
    
    private void executeNotificationAction(String userId, String notificationId, String actionId, Map<String, Object> actionData) {
        try {
            inAppService.executeAction(userId, notificationId, actionId, actionData);
            log.debug("Executed action {} for notification {}", actionId, notificationId);
        } catch (Exception e) {
            log.error("Error executing notification action", e);
        }
    }
    
    private UserEngagementSummary calculateEngagementSummary(NotificationAnalytics analytics) {
        return new UserEngagementSummary(
            analytics.getCategory(),
            analytics.getDeliveredCount(),
            analytics.getReadCount(),
            analytics.getClickedCount(),
            analytics.getDismissedCount(),
            analytics.calculateEngagementRate()
        );
    }
    
    private void generateEngagementReport(Map<String, UserEngagementSummary> summaries) {
        double avgEngagementRate = summaries.values().stream()
            .mapToDouble(UserEngagementSummary::getEngagementRate)
            .average()
            .orElse(0.0);
            
        long totalDelivered = summaries.values().stream()
            .mapToLong(UserEngagementSummary::getDeliveredCount)
            .sum();
            
        log.info("In-App Engagement Report - Avg Engagement Rate: {:.2f}%, Total Delivered: {}",
            avgEngagementRate * 100, totalDelivered);
    }
    
    private double calculateDeliveryRate() {
        if (deliveredCount.get() == 0 && failedCount.get() == 0) return 1.0;
        return deliveredCount.get() / (double) (deliveredCount.get() + failedCount.get());
    }
    
    // Enhanced error handling methods
    private void handleInvalidEvent(InAppNotificationEvent event, String rawMessage) {
        log.error("Invalid in-app notification event received: {}", rawMessage);
        
        metricsService.recordMetric(
            "inapp.notifications.invalid",
            1.0,
            Map.of("type", event != null ? event.getType() : "unknown")
        );
    }
    
    private void handleProcessingFailure(InAppNotificationEvent event, String rawMessage) {
        errorCount.incrementAndGet();
        errorCounter.increment();
        
        log.error("Failed to process in-app notification: {}", event.getNotificationId());
        
        alertingService.sendAlert(
            "In-App Notification Processing Failed",
            String.format("Failed to process in-app notification %s", event.getNotificationId()),
            AlertingService.AlertSeverity.MEDIUM
        );
    }
    
    private void handleProcessingError(Exception e, String rawMessage) {
        log.error("Processing error for in-app notification message: {}", rawMessage, e);
        
        if (errorCount.get() > 100) {
            alertingService.sendAlert(
                "High In-App Processing Error Rate",
                "More than 100 in-app processing errors detected",
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
            log.info("Sent in-app notification message to DLQ with reason: {}", reason);
            
        } catch (Exception e) {
            log.error("Failed to send in-app notification message to DLQ", e);
        }
    }
    
    private boolean isRetryableException(Throwable throwable) {
        return throwable instanceof java.net.SocketTimeoutException ||
               throwable instanceof java.io.IOException ||
               throwable instanceof java.net.ConnectException;
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down InAppNotificationConsumer");
        
        try {
            scheduledExecutor.shutdown();
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            // Process remaining batches
            activeBatches.values().forEach(this::processBatch);
            
            // Deliver any remaining queued notifications
            userQueues.values().forEach(queue -> {
                if (!queue.isEmpty()) {
                    deliverQueuedNotifications(queue.getUserId(), queue);
                }
            });
            
            log.info("InAppNotificationConsumer shutdown completed. Processed: {}, Errors: {}, Delivered: {}, Failed: {}",
                processedCount.get(), errorCount.get(), deliveredCount.get(), failedCount.get());
                
        } catch (Exception e) {
            log.error("Error during in-app consumer shutdown", e);
        }
    }
}