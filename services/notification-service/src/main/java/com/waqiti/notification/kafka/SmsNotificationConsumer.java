package com.waqiti.notification.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.monitoring.AlertingService;
import com.waqiti.notification.event.SmsNotificationEvent;
import com.waqiti.notification.service.SmsService;
import com.waqiti.notification.service.SmsProviderService;
import com.waqiti.notification.service.NotificationTrackingService;
import com.waqiti.notification.service.PhoneValidationService;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.Collectors;
import java.util.function.Supplier;

/**
 * Production-grade Kafka consumer for SMS notifications
 * Enhanced with circuit breaker, retry mechanisms, comprehensive metrics, and batch processing
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsNotificationConsumer {

    private static final String TOPIC = "sms-notifications";
    private static final String DLQ_TOPIC = "sms-notifications-dlq";
    private static final String CONSUMER_GROUP = "notification-sms-processor-group";
    
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 500;
    private static final double RETRY_MULTIPLIER = 2.0;
    
    private static final int MAX_BATCH_SIZE = 100;
    private static final int RATE_LIMIT_PER_MINUTE = 500;
    private static final int MAX_SMS_LENGTH = 1600;
    private static final int STANDARD_SMS_LENGTH = 160;
    private static final int UNICODE_SMS_LENGTH = 70;
    private static final double DELIVERY_RATE_THRESHOLD = 0.95;
    private static final int MAX_DELIVERY_ATTEMPTS = 5;

    // Existing services
    private final SmsService smsService;
    private final SmsProviderService providerService;
    private final NotificationTrackingService trackingService;
    private final PhoneValidationService phoneValidationService;
    
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
    private final Map<String, SmsProcessingState> smsStates = new ConcurrentHashMap<>();
    private final Map<String, SmsBatch> activeBatches = new ConcurrentHashMap<>();
    private final Map<String, RecipientProfile> recipientProfiles = new ConcurrentHashMap<>();
    private final Map<String, DeliveryStats> deliveryStats = new ConcurrentHashMap<>();
    private final Map<String, CarrierPerformance> carrierStats = new ConcurrentHashMap<>();
    private final Map<String, RateLimitState> rateLimits = new ConcurrentHashMap<>();
    private final Map<String, ScheduledSms> scheduledSms = new ConcurrentHashMap<>();
    private final Map<String, SmsAnalytics> analyticsData = new ConcurrentHashMap<>();
    
    // Metrics
    private final AtomicLong processedCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final AtomicLong deliveredCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong optOutCount = new AtomicLong(0);
    private final AtomicLong multipartCount = new AtomicLong(0);
    private final AtomicLong internationalCount = new AtomicLong(0);
    
    private Counter processedCounter;
    private Counter errorCounter;
    private Counter deliveredCounter;
    private Counter failedCounter;
    private Counter optOutCounter;
    private Counter multipartCounter;
    private Counter internationalCounter;
    private Gauge activeSmsGauge;
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
        log.info("Enhanced SmsNotificationConsumer initialized successfully");
    }
    
    private void initializeMetrics() {
        processedCounter = meterRegistry.counter("sms.notifications.processed.total");
        errorCounter = meterRegistry.counter("sms.notifications.errors.total");
        deliveredCounter = meterRegistry.counter("sms.notifications.delivered.total");
        failedCounter = meterRegistry.counter("sms.notifications.failed.total");
        optOutCounter = meterRegistry.counter("sms.notifications.optout.total");
        multipartCounter = meterRegistry.counter("sms.notifications.multipart.total");
        internationalCounter = meterRegistry.counter("sms.notifications.international.total");
        
        activeSmsGauge = Gauge.builder("sms.notifications.active", smsStates, Map::size)
            .description("Number of active SMS notifications")
            .register(meterRegistry);
            
        deliveryRateGauge = Gauge.builder("sms.notifications.delivery.rate", this, 
            consumer -> calculateDeliveryRate())
            .description("Current SMS delivery rate")
            .register(meterRegistry);
            
        processingTimer = Timer.builder("sms.notifications.processing.duration")
            .description("SMS processing duration")
            .register(meterRegistry);
            
        deliveryTimer = Timer.builder("sms.notifications.delivery.duration")
            .description("SMS delivery duration")
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
            
        circuitBreaker = CircuitBreaker.of("sms-notification-processor", config);
        
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> {
                log.warn("Circuit breaker state transition: {}", event);
                alertingService.sendAlert(
                    "Circuit Breaker State Change",
                    String.format("SMS notification circuit breaker transitioned from %s to %s",
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
            
        retryMechanism = Retry.of("sms-notification-retry", config);
        
        retryMechanism.getEventPublisher()
            .onRetry(event -> log.debug("Retry attempt {} for SMS notification", 
                event.getNumberOfRetryAttempts()));
    }
    
    private void initializeScheduledTasks() {
        scheduledExecutor = Executors.newScheduledThreadPool(5);
        
        scheduledExecutor.scheduleAtFixedRate(
            this::processBatches,
            0, 10, TimeUnit.SECONDS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::processScheduledSms,
            0, 1, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::updateCarrierStats,
            0, 5, TimeUnit.MINUTES
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::cleanupExpiredData,
            0, 1, TimeUnit.HOURS
        );
        
        scheduledExecutor.scheduleAtFixedRate(
            this::analyzeDeliveryPatterns,
            0, 30, TimeUnit.MINUTES
        );
    }
    
    private void initializeRateLimits() {
        rateLimits.put("global", new RateLimitState(RATE_LIMIT_PER_MINUTE));
        rateLimits.put("transactional", new RateLimitState(1000));
        rateLimits.put("marketing", new RateLimitState(200));
        rateLimits.put("alert", new RateLimitState(2000));
        rateLimits.put("verification", new RateLimitState(1500));
    }

    @KafkaListener(topics = TOPIC, groupId = CONSUMER_GROUP)
    public void processSmsNotification(@Payload String message,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                     @Header(KafkaHeaders.OFFSET) long offset,
                                     @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
                                     Acknowledgment acknowledgment) {
        MDC.put("topic", topic);
        MDC.put("partition", String.valueOf(partition));
        MDC.put("offset", String.valueOf(offset));
        
        try {
            log.debug("Processing SMS notification from partition {} offset {}", partition, offset);
            
            SmsNotificationEvent event = parseEvent(message);
            MDC.put("smsId", event.getMessageId());
            MDC.put("eventType", event.getMessageType());
            
            if (!validateSmsEvent(event)) {
                log.error("Invalid SMS notification event: {}", event);
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
            log.error("Error processing SMS notification", e);
            errorCount.incrementAndGet();
            errorCounter.increment();
            handleProcessingError(e, message);
            sendToDLQ(message, e.getMessage());
            acknowledgment.acknowledge();
        } finally {
            MDC.clear();
        }
    }
    
    private SmsNotificationEvent parseEvent(String message) throws JsonProcessingException {
        return objectMapper.readValue(message, SmsNotificationEvent.class);
    }
    
    private boolean processEventWithEnhancements(SmsNotificationEvent event) {
        return processingTimer.recordCallable(() -> {
            log.info("Processing SMS notification: {} to: {} type: {} provider: {}", 
                    event.getMessageId(), maskPhoneNumber(event.getPhoneNumber()), 
                    event.getMessageType(), event.getProvider());
            
            String smsId = event.getMessageId();
            SmsProcessingState state = new SmsProcessingState(smsId);
            smsStates.put(smsId, state);
            
            // Enhanced validation with rate limiting
            if (!checkRateLimit(event.getMessageType())) {
                queueForLaterDelivery(event);
                return true;
            }
            
            // Validate phone number
            if (!validatePhoneNumber(event)) {
                handleInvalidPhoneNumber(event);
                return false;
            }
            
            // Check SMS quotas and limits
            if (!checkSmsLimits(event)) {
                handleQuotaExceeded(event);
                return false;
            }
            
            // Enhanced phone validation and enrichment
            PhoneNumberInfo phoneInfo = phoneValidationService.validateAndEnrich(event.getPhoneNumber());
            if (!phoneInfo.isValid()) {
                log.error("Invalid phone number: {}", event.getPhoneNumber());
                state.setStatus("INVALID_PHONE");
                return false;
            }
            
            // Set carrier and country info
            event.setCountryCode(phoneInfo.getCountryCode());
            event.setCarrier(phoneInfo.getCarrier());
            
            if (phoneInfo.isInternational()) {
                internationalCount.incrementAndGet();
                internationalCounter.increment();
            }
            
            // Analyze message segments
            SmsSegmentInfo segmentInfo = analyzeSmsSegments(event.getMessage());
            if (segmentInfo.getSegmentCount() > 1) {
                multipartCount.incrementAndGet();
                multipartCounter.increment();
                event.setMultipart(true);
                event.setSegmentCount(segmentInfo.getSegmentCount());
            }
            
            state.setStatus("PROCESSING");
            state.setCarrier(phoneInfo.getCarrier());
            state.setSegmentCount(segmentInfo.getSegmentCount());
            
            // Select provider if not specified
            if (event.getProvider() == null) {
                event.setProvider(selectOptimalProvider(event));
            }
            
            // Send SMS with enhanced delivery tracking
            boolean sent = deliveryTimer.recordCallable(() -> sendSmsEnhanced(event));
            
            // Track delivery
            trackSmsDelivery(event, sent);
            
            // Handle delivery status
            if (sent) {
                deliveredCount.incrementAndGet();
                deliveredCounter.increment();
                handleSuccessfulSms(event);
                updateDeliveryStats(phoneInfo.getCarrier(), true);
                trackAnalytics(event, true);
            } else {
                failedCount.incrementAndGet();
                failedCounter.increment();
                handleFailedSms(event);
                updateDeliveryStats(phoneInfo.getCarrier(), false);
                trackAnalytics(event, false);
            }
            
            // Update metrics
            updateSmsMetrics(event, sent);
            
            state.setStatus(sent ? "SENT" : "FAILED");
            state.setDeliveryTime(Instant.now());
            
            log.debug("Successfully processed SMS notification: {}", event.getMessageId());
            return sent;
        });
    }

    private boolean validateSmsEvent(SmsNotificationEvent event) {
        if (event == null || event.getMessageId() == null || event.getMessageId().trim().isEmpty()) {
            return false;
        }
        
        if (event.getPhoneNumber() == null || event.getPhoneNumber().trim().isEmpty()) {
            return false;
        }
        
        if (event.getMessage() == null || event.getMessage().trim().isEmpty()) {
            return false;
        }
        
        if (event.getMessage().length() > MAX_SMS_LENGTH) {
            log.warn("SMS message too long: {} characters", event.getMessage().length());
            return false;
        }
        
        return true;
    }
    
    private boolean checkRateLimit(String messageType) {
        RateLimitState globalLimit = rateLimits.get("global");
        RateLimitState typeLimit = rateLimits.get(messageType);
        
        return globalLimit.tryAcquire() && 
               (typeLimit == null || typeLimit.tryAcquire());
    }
    
    private void queueForLaterDelivery(SmsNotificationEvent event) {
        log.info("Rate limit exceeded, queueing SMS {} for later delivery", event.getMessageId());
        scheduledSms.put(event.getMessageId(), 
            new ScheduledSms(event.getMessageId(), event, Instant.now().plus(Duration.ofMinutes(5))));
    }
    
    private SmsSegmentInfo analyzeSmsSegments(String message) {
        if (message == null) return new SmsSegmentInfo(0, 0, false);
        
        boolean hasUnicode = containsUnicodeCharacters(message);
        int maxLength = hasUnicode ? UNICODE_SMS_LENGTH : STANDARD_SMS_LENGTH;
        int segmentCount = (int) Math.ceil((double) message.length() / maxLength);
        
        return new SmsSegmentInfo(message.length(), segmentCount, hasUnicode);
    }
    
    private boolean containsUnicodeCharacters(String message) {
        return message.chars().anyMatch(c -> c > 127);
    }
    
    private boolean sendSmsEnhanced(SmsNotificationEvent event) {
        try {
            return sendSms(event);
        } catch (Exception e) {
            log.error("Enhanced SMS send failed for {}: {}", event.getMessageId(), e.getMessage());
            return false;
        }
    }
    
    private void updateDeliveryStats(String carrier, boolean success) {
        DeliveryStats stats = deliveryStats.computeIfAbsent(carrier,
            k -> new DeliveryStats(carrier));
        stats.addDelivery(Instant.now(), success);
    }
    
    private void trackAnalytics(SmsNotificationEvent event, boolean success) {
        SmsAnalytics analytics = analyticsData.computeIfAbsent(event.getMessageType(),
            k -> new SmsAnalytics(event.getMessageType()));
        
        analytics.addDelivery(
            success,
            calculateDeliveryLatency(event),
            event.getCarrier(),
            event.getCountryCode(),
            event.getSegmentCount()
        );
        
        metricsService.recordMetric(
            "sms.delivery.success",
            success ? 1.0 : 0.0,
            Map.of(
                "messageType", event.getMessageType(),
                "carrier", event.getCarrier() != null ? event.getCarrier() : "unknown",
                "country", event.getCountryCode() != null ? event.getCountryCode() : "unknown"
            )
        );
    }

    private boolean validatePhoneNumber(SmsNotificationEvent event) {
        // Validate format
        if (!phoneValidationService.isValidFormat(event.getPhoneNumber())) {
            log.warn("Invalid phone number format: {}", maskPhoneNumber(event.getPhoneNumber()));
            return false;
        }
        
        // Check if number is blacklisted
        if (phoneValidationService.isBlacklisted(event.getPhoneNumber())) {
            log.warn("Phone number is blacklisted: {}", maskPhoneNumber(event.getPhoneNumber()));
            return false;
        }
        
        // Check if number is reachable
        if (event.isValidateReachability() && 
            !phoneValidationService.isReachable(event.getPhoneNumber())) {
            log.warn("Phone number is not reachable: {}", maskPhoneNumber(event.getPhoneNumber()));
            return false;
        }
        
        // Check carrier if required
        if (event.isValidateCarrier()) {
            String carrier = phoneValidationService.getCarrier(event.getPhoneNumber());
            if (carrier == null || phoneValidationService.isUnsupportedCarrier(carrier)) {
                log.warn("Unsupported carrier for phone: {}", maskPhoneNumber(event.getPhoneNumber()));
                return false;
            }
        }
        
        return true;
    }

    private boolean checkSmsLimits(SmsNotificationEvent event) {
        // Check daily limit for user
        int dailySent = smsService.getDailySmsCount(event.getUserId());
        if (dailySent >= event.getDailyLimit()) {
            log.warn("Daily SMS limit exceeded for user: {} sent: {} limit: {}", 
                    event.getUserId(), dailySent, event.getDailyLimit());
            return false;
        }
        
        // Check hourly rate limit
        int hourlySent = smsService.getHourlySmsCount(event.getUserId());
        if (hourlySent >= event.getHourlyLimit()) {
            log.warn("Hourly SMS limit exceeded for user: {} sent: {} limit: {}", 
                    event.getUserId(), hourlySent, event.getHourlyLimit());
            return false;
        }
        
        // Check duplicate message
        if (smsService.isDuplicateMessage(
                event.getPhoneNumber(),
                event.getMessage(),
                event.getDuplicateWindow())) {
            log.warn("Duplicate SMS detected for phone: {} within window", 
                    maskPhoneNumber(event.getPhoneNumber()));
            return false;
        }
        
        // Check cost limits
        if (event.getCostLimit() != null) {
            double estimatedCost = providerService.estimateCost(
                event.getProvider(),
                event.getPhoneNumber(),
                event.getMessage().length()
            );
            
            double monthlySpent = smsService.getMonthlySpend(event.getUserId());
            if (monthlySpent + estimatedCost > event.getCostLimit()) {
                log.warn("SMS cost limit would be exceeded for user: {}", event.getUserId());
                return false;
            }
        }
        
        return true;
    }

    private String selectOptimalProvider(SmsNotificationEvent event) {
        // Get available providers for destination
        var providers = providerService.getAvailableProviders(
            event.getPhoneNumber(),
            event.getMessageType()
        );
        
        if (providers.isEmpty()) {
            throw new IllegalStateException("No SMS providers available for " + 
                    maskPhoneNumber(event.getPhoneNumber()));
        }
        
        // Select based on criteria
        String selected = providerService.selectProvider(
            providers,
            event.getPriority(),
            event.getDeliverySpeed(),
            event.getCostOptimization()
        );
        
        log.info("Selected SMS provider: {} for message: {}", selected, event.getMessageId());
        return selected;
    }

    private boolean sendSms(SmsNotificationEvent event) {
        try {
            // Format message
            String formattedMessage = formatMessage(event);
            
            // Check message length and split if needed
            if (formattedMessage.length() > 160) {
                return sendLongSms(event, formattedMessage);
            }
            
            // Send SMS via provider
            Map<String, Object> result = providerService.sendSms(
                event.getProvider(),
                event.getPhoneNumber(),
                formattedMessage,
                event.getSenderId(),
                event.getMessageType(),
                event.getCallbackUrl()
            );
            
            // Store provider message ID
            if (result.containsKey("providerMessageId")) {
                smsService.storeProviderMessageId(
                    event.getMessageId(),
                    (String) result.get("providerMessageId"),
                    event.getProvider()
                );
            }
            
            // Check immediate delivery status
            String status = (String) result.get("status");
            return "SENT".equals(status) || "QUEUED".equals(status);
            
        } catch (Exception e) {
            log.error("Failed to send SMS via provider {}: {}", 
                    event.getProvider(), e.getMessage());
            
            // Try fallback provider if available
            if (event.getFallbackProvider() != null) {
                log.info("Attempting fallback provider: {}", event.getFallbackProvider());
                event.setProvider(event.getFallbackProvider());
                event.setFallbackProvider(null);
                return sendSms(event);
            }
            
            return false;
        }
    }

    private boolean sendLongSms(SmsNotificationEvent event, String message) {
        // Split message into parts
        var parts = smsService.splitMessage(message, 153); // 153 chars for multipart SMS
        
        log.info("Sending multipart SMS: {} parts for message: {}", 
                parts.size(), event.getMessageId());
        
        boolean allSent = true;
        for (int i = 0; i < parts.size(); i++) {
            String part = parts.get(i);
            String partId = event.getMessageId() + "_part_" + (i + 1);
            
            Map<String, Object> result = providerService.sendSms(
                event.getProvider(),
                event.getPhoneNumber(),
                part,
                event.getSenderId(),
                event.getMessageType(),
                null
            );
            
            String status = (String) result.get("status");
            if (!"SENT".equals(status) && !"QUEUED".equals(status)) {
                allSent = false;
                log.error("Failed to send SMS part {} of {}", i + 1, parts.size());
            }
        }
        
        return allSent;
    }

    private String formatMessage(SmsNotificationEvent event) {
        String message = event.getMessage();
        
        // Apply template if specified
        if (event.getTemplateId() != null) {
            message = smsService.renderTemplate(
                event.getTemplateId(),
                event.getTemplateData()
            );
        }
        
        // Add prefix/suffix if configured
        if (event.getMessagePrefix() != null) {
            message = event.getMessagePrefix() + " " + message;
        }
        if (event.getMessageSuffix() != null) {
            message = message + " " + event.getMessageSuffix();
        }
        
        // Apply URL shortening if needed
        if (event.isShortenUrls()) {
            message = smsService.shortenUrls(message);
        }
        
        // Validate characters
        message = smsService.sanitizeMessage(message);
        
        return message;
    }

    private void trackSmsDelivery(SmsNotificationEvent event, boolean sent) {
        trackingService.trackSms(
            event.getMessageId(),
            event.getUserId(),
            event.getPhoneNumber(),
            event.getProvider(),
            sent ? "SENT" : "FAILED",
            LocalDateTime.now()
        );
        
        // Schedule delivery status check
        if (sent && event.isTrackDelivery()) {
            smsService.scheduleDeliveryStatusCheck(
                event.getMessageId(),
                event.getProvider(),
                LocalDateTime.now().plusMinutes(5)
            );
        }
    }

    private void handleSuccessfulSms(SmsNotificationEvent event) {
        // Update status
        smsService.updateMessageStatus(
            event.getMessageId(),
            "SENT",
            LocalDateTime.now()
        );
        
        // Clear retry attempts
        smsService.clearRetryAttempts(event.getMessageId());
        
        // Trigger success callback
        if (event.getSuccessCallbackUrl() != null) {
            smsService.triggerCallback(
                event.getSuccessCallbackUrl(),
                event.getMessageId(),
                "SUCCESS",
                Map.of(
                    "phoneNumber", event.getPhoneNumber(),
                    "provider", event.getProvider(),
                    "sentAt", LocalDateTime.now()
                )
            );
        }
        
        // Update user engagement
        trackingService.updateUserSmsEngagement(
            event.getUserId(),
            "SENT",
            event.getMessageType()
        );
    }

    private void handleFailedSms(SmsNotificationEvent event) {
        // Increment failure count
        int failureCount = smsService.incrementFailureCount(event.getMessageId());
        
        // Check if should retry
        if (failureCount < event.getMaxRetries()) {
            // Schedule retry with exponential backoff
            long delaySeconds = (long) Math.pow(2, failureCount) * 30;
            smsService.scheduleRetry(
                event,
                LocalDateTime.now().plusSeconds(delaySeconds)
            );
            
            log.info("Scheduled SMS retry {} for message: {} in {} seconds", 
                    failureCount + 1, event.getMessageId(), delaySeconds);
        } else {
            // Mark as permanently failed
            smsService.markAsFailed(
                event.getMessageId(),
                "MAX_RETRIES_EXCEEDED",
                LocalDateTime.now()
            );
            
            // Send to DLQ
            smsService.sendToDeadLetterQueue(event);
            
            // Trigger failure callback
            if (event.getFailureCallbackUrl() != null) {
                smsService.triggerCallback(
                    event.getFailureCallbackUrl(),
                    event.getMessageId(),
                    "FAILED",
                    Map.of(
                        "phoneNumber", event.getPhoneNumber(),
                        "failureReason", "MAX_RETRIES_EXCEEDED",
                        "attempts", failureCount
                    )
                );
            }
        }
    }

    private void handleInvalidPhoneNumber(SmsNotificationEvent event) {
        // Log invalid number
        smsService.logInvalidNumber(
            event.getMessageId(),
            event.getPhoneNumber(),
            "VALIDATION_FAILED"
        );
        
        // Update status
        smsService.updateMessageStatus(
            event.getMessageId(),
            "INVALID_NUMBER",
            LocalDateTime.now()
        );
        
        // Notify sender if configured
        if (event.isNotifyOnFailure()) {
            smsService.notifySender(
                event.getUserId(),
                event.getMessageId(),
                "INVALID_PHONE_NUMBER",
                event.getPhoneNumber()
            );
        }
    }

    private void handleQuotaExceeded(SmsNotificationEvent event) {
        // Log quota exceeded
        smsService.logQuotaExceeded(
            event.getUserId(),
            event.getMessageId(),
            event.getQuotaType()
        );
        
        // Queue for later if configured
        if (event.isQueueOnQuotaExceeded()) {
            smsService.queueForLater(
                event,
                LocalDateTime.now().plusHours(1)
            );
            
            log.info("SMS queued due to quota exceeded: {}", event.getMessageId());
        } else {
            // Mark as failed
            smsService.updateMessageStatus(
                event.getMessageId(),
                "QUOTA_EXCEEDED",
                LocalDateTime.now()
            );
        }
    }

    private void updateSmsMetrics(SmsNotificationEvent event, boolean sent) {
        // Update provider metrics
        providerService.updateMetrics(
            event.getProvider(),
            sent,
            event.getMessageType()
        );
        
        // Update cost tracking
        if (sent) {
            double cost = providerService.calculateCost(
                event.getProvider(),
                event.getPhoneNumber(),
                event.getMessage().length()
            );
            
            smsService.recordCost(
                event.getMessageId(),
                event.getUserId(),
                cost,
                event.getProvider()
            );
        }
        
        // Update delivery metrics
        trackingService.updateSmsMetrics(
            event.getProvider(),
            event.getMessageType(),
            sent ? "SUCCESS" : "FAILED",
            event.getPriority()
        );
    }

    private String maskPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 4) {
            return "****";
        }
        int visibleDigits = 4;
        return "*".repeat(Math.max(0, phoneNumber.length() - visibleDigits)) + 
               phoneNumber.substring(phoneNumber.length() - visibleDigits);
    }
    
    // Enhanced scheduled methods
    @Scheduled(fixedDelay = 10000)
    public void processBatches() {
        activeBatches.values().stream()
            .filter(SmsBatch::shouldProcess)
            .forEach(this::processBatch);
    }
    
    private boolean processBatch(SmsBatch batch) {
        try {
            log.info("Processing SMS batch {} with {} messages", batch.getId(), batch.getSize());
            
            List<SmsNotificationEvent> smsMessages = batch.getSmsMessages();
            
            for (SmsNotificationEvent sms : smsMessages) {
                try {
                    processEventWithEnhancements(sms);
                } catch (Exception e) {
                    log.error("Error processing SMS in batch {}: {}", batch.getId(), e.getMessage());
                }
            }
            
            activeBatches.remove(batch.getId());
            return true;
            
        } catch (Exception e) {
            log.error("Error processing SMS batch {}", batch.getId(), e);
            return false;
        }
    }
    
    @Scheduled(fixedDelay = 60000)
    public void processScheduledSms() {
        Instant now = Instant.now();
        
        scheduledSms.values().stream()
            .filter(scheduled -> scheduled.getScheduledTime().isBefore(now))
            .forEach(scheduled -> {
                try {
                    processEventWithEnhancements(scheduled.getEvent());
                    scheduledSms.remove(scheduled.getId());
                } catch (Exception e) {
                    log.error("Error processing scheduled SMS {}", scheduled.getId(), e);
                }
            });
    }
    
    @Scheduled(fixedDelay = 300000)
    public void updateCarrierStats() {
        try {
            double deliveryRate = calculateDeliveryRate();
            
            if (deliveryRate < DELIVERY_RATE_THRESHOLD) {
                alertingService.sendAlert(
                    "Low SMS Delivery Rate",
                    String.format("Current delivery rate: %.2f%%", deliveryRate * 100),
                    AlertingService.AlertSeverity.HIGH
                );
            }
            
            carrierStats.values().forEach(this::analyzeCarrierPerformance);
            
        } catch (Exception e) {
            log.error("Error updating carrier statistics", e);
        }
    }
    
    @Scheduled(fixedDelay = 3600000)
    public void cleanupExpiredData() {
        try {
            Instant expiryTime = Instant.now().minus(Duration.ofDays(7));
            
            smsStates.entrySet().removeIf(entry -> 
                entry.getValue().getCreatedAt().isBefore(expiryTime));
            
            scheduledSms.entrySet().removeIf(entry ->
                entry.getValue().getScheduledTime().isBefore(expiryTime.minus(Duration.ofDays(1))));
            
            analyticsData.entrySet().removeIf(entry ->
                entry.getValue().getLastUpdate().isBefore(expiryTime));
            
            log.info("Cleaned up expired SMS data");
            
        } catch (Exception e) {
            log.error("Error cleaning up expired SMS data", e);
        }
    }
    
    @Scheduled(fixedDelay = 1800000)
    public void analyzeDeliveryPatterns() {
        try {
            Map<String, DeliveryPatternAnalysis> patterns = new HashMap<>();
            
            deliveryStats.values().forEach(stats -> {
                DeliveryPatternAnalysis analysis = analyzeDeliveryPattern(stats);
                patterns.put(stats.getPhoneNumber(), analysis);
            });
            
            patterns.values().stream()
                .filter(pattern -> pattern.hasAnomalies())
                .forEach(pattern -> {
                    log.warn("Delivery anomaly detected for {}: {}", 
                        pattern.getPhoneNumber(), pattern.getAnomalies());
                });
                
            generateDeliveryReport(patterns);
            
        } catch (Exception e) {
            log.error("Error analyzing delivery patterns", e);
        }
    }
    
    private void analyzeCarrierPerformance(CarrierPerformance performance) {
        if (performance.getDeliveryRate() < DELIVERY_RATE_THRESHOLD) {
            log.warn("Poor performance for carrier {}: {:.2f}% delivery rate", 
                performance.getProviderId(), performance.getDeliveryRate() * 100);
        }
    }
    
    private DeliveryPatternAnalysis analyzeDeliveryPattern(DeliveryStats stats) {
        return new DeliveryPatternAnalysis(
            stats.getPhoneNumber(),
            stats.getDeliveryRate(),
            stats.getFailurePatterns(),
            stats.getAnomalies()
        );
    }
    
    private void generateDeliveryReport(Map<String, DeliveryPatternAnalysis> patterns) {
        double avgDeliveryRate = patterns.values().stream()
            .mapToDouble(DeliveryPatternAnalysis::getDeliveryRate)
            .average()
            .orElse(0.0);
            
        long anomalyCount = patterns.values().stream()
            .mapToLong(pattern -> pattern.getAnomalies().size())
            .sum();
            
        log.info("SMS Delivery Report - Avg Delivery Rate: {:.2f}%, Anomalies: {}",
            avgDeliveryRate * 100, anomalyCount);
    }
    
    private double calculateDeliveryRate() {
        if (deliveredCount.get() == 0 && failedCount.get() == 0) return 1.0;
        return deliveredCount.get() / (double) (deliveredCount.get() + failedCount.get());
    }
    
    // Enhanced error handling methods
    private void handleInvalidEvent(SmsNotificationEvent event, String rawMessage) {
        log.error("Invalid SMS event received: {}", rawMessage);
        
        metricsService.recordMetric(
            "sms.notifications.invalid",
            1.0,
            Map.of("type", event != null ? event.getMessageType() : "unknown")
        );
    }
    
    private void handleProcessingFailure(SmsNotificationEvent event, String rawMessage) {
        errorCount.incrementAndGet();
        errorCounter.increment();
        
        log.error("Failed to process SMS notification: {}", event.getMessageId());
        
        alertingService.sendAlert(
            "SMS Notification Processing Failed",
            String.format("Failed to process SMS %s", event.getMessageId()),
            AlertingService.AlertSeverity.MEDIUM
        );
    }
    
    private void handleProcessingError(Exception e, String rawMessage) {
        log.error("Processing error for SMS message: {}", rawMessage, e);
        
        if (errorCount.get() > 100) {
            alertingService.sendAlert(
                "High SMS Processing Error Rate",
                "More than 100 SMS processing errors detected",
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
            log.info("Sent SMS message to DLQ with reason: {}", reason);
            
        } catch (Exception e) {
            log.error("Failed to send SMS message to DLQ", e);
        }
    }
    
    private boolean isRetryableException(Throwable throwable) {
        return throwable instanceof java.net.SocketTimeoutException ||
               throwable instanceof java.io.IOException ||
               throwable instanceof java.net.ConnectException;
    }
    
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Enhanced SmsNotificationConsumer");
        
        try {
            scheduledExecutor.shutdown();
            if (!scheduledExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow();
            }
            
            activeBatches.values().forEach(this::processBatch);
            
            log.info("Enhanced SmsNotificationConsumer shutdown completed. Processed: {}, Errors: {}, Delivered: {}, Failed: {}",
                processedCount.get(), errorCount.get(), deliveredCount.get(), failedCount.get());
                
        } catch (Exception e) {
            log.error("Error during enhanced SMS consumer shutdown", e);
        }
    }
    
    /**
     * Calculate delivery latency for analytics
     */
    private long calculateDeliveryLatency(SmsNotificationEvent event) {
        try {
            if (event.getCreatedAt() != null) {
                return java.time.Duration.between(event.getCreatedAt(), Instant.now()).toMillis();
            }
            
            // If no creation timestamp, return 0
            return 0L;
            
        } catch (Exception e) {
            log.debug("Unable to calculate delivery latency: {}", e.getMessage());
            return 0L;
        }
    }
}