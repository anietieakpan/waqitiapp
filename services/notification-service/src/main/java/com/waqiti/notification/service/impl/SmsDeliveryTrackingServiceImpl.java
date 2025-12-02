package com.waqiti.notification.service.impl;

import com.waqiti.notification.domain.SmsDeliveryStatus;
import com.waqiti.notification.domain.SmsMessage;
import com.waqiti.notification.service.SmsDeliveryTrackingService;
import com.waqiti.notification.service.provider.SmsProvider;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class SmsDeliveryTrackingServiceImpl implements SmsDeliveryTrackingService {

    private final SmsProvider smsProvider;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    public SmsDeliveryTrackingServiceImpl(
            SmsProvider smsProvider,
            RedisTemplate<String, Object> redisTemplate,
            KafkaTemplate<String, Object> kafkaTemplate,
            MeterRegistry meterRegistry) {
        this.smsProvider = smsProvider;
        this.redisTemplate = redisTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }
    
    @Value("${notification.sms.from}")
    private String fromNumber;
    
    @Value("${twilio.account.sid}")
    private String accountSid;
    
    @Value("${twilio.auth.token}")
    private String authToken;
    
    @Value("${twilio.enabled:false}")
    private boolean twilioEnabled;
    
    @Value("${notification.sms.max-retries:3}")
    private int maxRetries;
    
    @Value("${notification.sms.retry-delay-ms:60000}")
    private long retryDelayMs;
    
    @Value("${notification.sms.delivery-check-interval-ms:30000}")
    private long deliveryCheckIntervalMs;
    
    // Metrics
    private Counter smsSentCounter;
    private Counter smsDeliveredCounter;
    private Counter smsFailedCounter;
    private Timer smsDeliveryTimer;
    
    // Thread pool for async operations
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    // In-memory storage for pending deliveries (in production, use database)
    private final Map<String, SmsMessage> pendingMessages = new ConcurrentHashMap<>();
    private final Map<String, SmsDeliveryStatus> deliveryStatuses = new ConcurrentHashMap<>();
    
    // Cache keys
    private static final String SMS_STATUS_CACHE_PREFIX = "sms:status:";
    private static final String SMS_MESSAGE_CACHE_PREFIX = "sms:message:";
    private static final String SMS_STATS_CACHE_KEY = "sms:statistics";
    private static final Duration CACHE_TTL = Duration.ofHours(24);
    
    @PostConstruct
    public void initialize() {
        // Initialize Twilio if enabled
        if (twilioEnabled) {
            try {
                Twilio.init(accountSid, authToken);
                log.info("Twilio SMS delivery tracking initialized");
            } catch (Exception e) {
                log.error("Failed to initialize Twilio", e);
            }
        }
        
        // Initialize metrics
        smsSentCounter = Counter.builder("sms.sent")
            .description("Number of SMS messages sent")
            .register(meterRegistry);
            
        smsDeliveredCounter = Counter.builder("sms.delivered")
            .description("Number of SMS messages delivered")
            .register(meterRegistry);
            
        smsFailedCounter = Counter.builder("sms.failed")
            .description("Number of SMS messages failed")
            .register(meterRegistry);
            
        smsDeliveryTimer = Timer.builder("sms.delivery.time")
            .description("SMS delivery time")
            .register(meterRegistry);
            
        log.info("SMS delivery tracking service initialized");
    }

    @Override
    @Async
    @CircuitBreaker(name = "sms-delivery", fallbackMethod = "sendAndTrackSmsFallback")
    @Retry(name = "sms-delivery")
    public CompletableFuture<SmsDeliveryStatus> sendAndTrackSms(SmsMessage message) {
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            log.info("Sending SMS to {} with tracking", message.getRecipientNumber());
            
            try {
                // Check if message is expired
                if (message.isExpired()) {
                    log.warn("SMS message {} has expired", message.getMessageId());
                    return createFailedStatus(message, "Message expired");
                }
                
                // Check if scheduled for later
                if (message.isScheduled()) {
                    scheduleMessage(message);
                    return createQueuedStatus(message);
                }
                
                // Send SMS
                SmsDeliveryStatus status = sendSmsWithProvider(message);
                
                // Store message and status
                storeMessage(message);
                storeDeliveryStatus(status);
                
                // Update metrics
                smsSentCounter.increment();
                
                // Start delivery tracking
                trackDeliveryAsync(status.getMessageId());
                
                // Publish event
                publishSmsEvent("SMS_SENT", message, status);
                
                sample.stop(smsDeliveryTimer);
                log.info("SMS sent successfully with message ID: {}", status.getMessageId());
                
                return status;
                
            } catch (Exception e) {
                sample.stop(smsDeliveryTimer);
                smsFailedCounter.increment();
                log.error("Failed to send SMS to {}", message.getRecipientNumber(), e);
                
                SmsDeliveryStatus failedStatus = createFailedStatus(message, e.getMessage());
                storeDeliveryStatus(failedStatus);
                
                // Schedule retry if applicable
                if (shouldRetry(message, 1)) {
                    scheduleRetry(message, 1);
                }
                
                return failedStatus;
            }
        }, executorService);
    }

    @Override
    public SmsDeliveryStatus getDeliveryStatus(String messageId) {
        log.debug("Getting delivery status for message: {}", messageId);
        
        // Check cache first
        String cacheKey = SMS_STATUS_CACHE_PREFIX + messageId;
        SmsDeliveryStatus cached = (SmsDeliveryStatus) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        // Check in-memory storage
        SmsDeliveryStatus status = deliveryStatuses.get(messageId);
        if (status != null) {
            // Update from provider if pending
            if (status.isPending() && twilioEnabled) {
                status = updateStatusFromProvider(messageId, status);
            }
            
            // Cache the status
            redisTemplate.opsForValue().set(cacheKey, status, CACHE_TTL);
            return status;
        }
        
        // Try to fetch from provider
        if (twilioEnabled) {
            return fetchStatusFromProvider(messageId);
        }
        
        log.debug("SMS delivery status not found for messageId: {}", messageId);
        return SmsDeliveryStatus.builder()
            .messageId(messageId)
            .status("UNKNOWN")
            .build();
    }

    @Override
    public void updateDeliveryStatus(String messageId, SmsDeliveryStatus status) {
        log.info("Updating delivery status for message {}: {}", messageId, status.getState());
        
        // Update in-memory storage
        deliveryStatuses.put(messageId, status);
        
        // Update cache
        String cacheKey = SMS_STATUS_CACHE_PREFIX + messageId;
        redisTemplate.opsForValue().set(cacheKey, status, CACHE_TTL);
        
        // Update metrics
        if (status.isSuccessful()) {
            smsDeliveredCounter.increment();
        } else if (status.isFailed()) {
            smsFailedCounter.increment();
        }
        
        // Publish event
        publishDeliveryStatusEvent(status);
        
        // Handle failed delivery
        if (status.isFailed()) {
            handleFailedDelivery(messageId, status);
        }
    }

    @Override
    public Map<String, Object> getDeliveryStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        log.debug("Getting delivery statistics from {} to {}", startDate, endDate);
        
        // Check cache
        Map<String, Object> cached = (Map<String, Object>) redisTemplate.opsForValue().get(SMS_STATS_CACHE_KEY);
        if (cached != null) {
            return cached;
        }
        
        // Calculate statistics
        Map<String, Object> stats = new HashMap<>();
        
        List<SmsDeliveryStatus> statuses = deliveryStatuses.values().stream()
            .filter(s -> isWithinDateRange(s, startDate, endDate))
            .collect(Collectors.toList());
        
        long total = statuses.size();
        long delivered = statuses.stream().filter(SmsDeliveryStatus::isSuccessful).count();
        long failed = statuses.stream().filter(SmsDeliveryStatus::isFailed).count();
        long pending = statuses.stream().filter(SmsDeliveryStatus::isPending).count();
        
        stats.put("total", total);
        stats.put("delivered", delivered);
        stats.put("failed", failed);
        stats.put("pending", pending);
        stats.put("deliveryRate", total > 0 ? (double) delivered / total * 100 : 0.0);
        stats.put("failureRate", total > 0 ? (double) failed / total * 100 : 0.0);
        
        // Calculate average delivery time
        Double avgDeliveryTime = statuses.stream()
            .map(SmsDeliveryStatus::getDeliveryTimeMs)
            .filter(Objects::nonNull)
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);
        stats.put("averageDeliveryTimeMs", avgDeliveryTime);
        
        // Calculate costs
        BigDecimal totalCost = statuses.stream()
            .map(SmsDeliveryStatus::getCost)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("totalCost", totalCost);
        
        // Group by carrier
        Map<String, Long> byCarrier = statuses.stream()
            .filter(s -> s.getCarrier() != null)
            .collect(Collectors.groupingBy(SmsDeliveryStatus::getCarrier, Collectors.counting()));
        stats.put("byCarrier", byCarrier);
        
        // Group by country
        Map<String, Long> byCountry = statuses.stream()
            .filter(s -> s.getCountryCode() != null)
            .collect(Collectors.groupingBy(SmsDeliveryStatus::getCountryCode, Collectors.counting()));
        stats.put("byCountry", byCountry);
        
        // Cache for 5 minutes
        redisTemplate.opsForValue().set(SMS_STATS_CACHE_KEY, stats, Duration.ofMinutes(5));
        
        return stats;
    }

    @Override
    public List<SmsMessage> getFailedDeliveries(LocalDateTime since) {
        log.debug("Getting failed deliveries since {}", since);
        
        return pendingMessages.values().stream()
            .filter(msg -> {
                SmsDeliveryStatus status = deliveryStatuses.get(msg.getMessageId());
                return status != null && 
                       status.isFailed() && 
                       status.getFailedAt() != null &&
                       status.getFailedAt().isAfter(since);
            })
            .collect(Collectors.toList());
    }

    @Override
    @Async
    public CompletableFuture<SmsDeliveryStatus> retryDelivery(String messageId) {
        log.info("Retrying SMS delivery for message: {}", messageId);
        
        SmsMessage message = pendingMessages.get(messageId);
        if (message == null) {
            log.warn("Message {} not found for retry", messageId);
            return CompletableFuture.completedFuture(null);
        }
        
        SmsDeliveryStatus currentStatus = deliveryStatuses.get(messageId);
        if (currentStatus == null || !currentStatus.isFailed()) {
            log.warn("Message {} is not in failed state", messageId);
            return CompletableFuture.completedFuture(currentStatus);
        }
        
        int attemptCount = currentStatus.getAttemptCount() != null ? currentStatus.getAttemptCount() + 1 : 1;
        
        if (attemptCount > maxRetries) {
            log.warn("Message {} has exceeded max retries", messageId);
            return CompletableFuture.completedFuture(currentStatus);
        }
        
        // Retry sending
        return sendAndTrackSms(message);
    }

    @Override
    public List<SmsDeliveryStatus> getUserDeliveryReport(String userId, LocalDateTime startDate, 
                                                        LocalDateTime endDate) {
        log.debug("Getting delivery report for user {} from {} to {}", userId, startDate, endDate);
        
        return deliveryStatuses.values().stream()
            .filter(status -> {
                SmsMessage message = pendingMessages.get(status.getMessageId());
                return message != null && 
                       userId.equals(message.getUserId()) &&
                       isWithinDateRange(status, startDate, endDate);
            })
            .sorted(Comparator.comparing(SmsDeliveryStatus::getSentAt).reversed())
            .collect(Collectors.toList());
    }

    @Override
    public boolean cancelScheduledSms(String messageId) {
        log.info("Cancelling scheduled SMS: {}", messageId);
        
        SmsMessage message = pendingMessages.get(messageId);
        if (message == null || !message.isScheduled()) {
            log.warn("Message {} is not scheduled or not found", messageId);
            return false;
        }
        
        // Remove from pending
        pendingMessages.remove(messageId);
        
        // Update status
        SmsDeliveryStatus cancelledStatus = SmsDeliveryStatus.builder()
            .messageId(messageId)
            .recipientNumber(message.getRecipientNumber())
            .state(SmsDeliveryStatus.DeliveryState.FAILED)
            .statusMessage("Cancelled by user")
            .failedAt(LocalDateTime.now())
            .build();
        
        updateDeliveryStatus(messageId, cancelledStatus);
        
        return true;
    }

    @Override
    public List<SmsMessage> getPendingDeliveries() {
        return pendingMessages.values().stream()
            .filter(msg -> {
                SmsDeliveryStatus status = deliveryStatuses.get(msg.getMessageId());
                return status != null && status.isPending();
            })
            .collect(Collectors.toList());
    }

    // Scheduled tasks
    
    @Scheduled(fixedDelayString = "${notification.sms.delivery-check-interval-ms:30000}")
    public void checkPendingDeliveries() {
        log.debug("Checking pending SMS deliveries");
        
        List<SmsDeliveryStatus> pendingStatuses = deliveryStatuses.values().stream()
            .filter(SmsDeliveryStatus::isPending)
            .collect(Collectors.toList());
        
        for (SmsDeliveryStatus status : pendingStatuses) {
            updateStatusFromProvider(status.getMessageId(), status);
        }
    }
    
    @Scheduled(fixedDelayString = "${notification.sms.retry-interval-ms:300000}")
    public void retryFailedDeliveries() {
        log.debug("Processing failed SMS deliveries for retry");
        
        LocalDateTime retryThreshold = LocalDateTime.now().minusMinutes(5);
        List<SmsMessage> failedMessages = getFailedDeliveries(retryThreshold);
        
        for (SmsMessage message : failedMessages) {
            SmsDeliveryStatus status = deliveryStatuses.get(message.getMessageId());
            if (shouldRetry(message, status.getAttemptCount())) {
                retryDelivery(message.getMessageId());
            }
        }
    }
    
    // Private helper methods
    
    private SmsDeliveryStatus sendSmsWithProvider(SmsMessage message) {
        if (!twilioEnabled) {
            log.info("Twilio disabled, simulating SMS send to {}", message.getRecipientNumber());
            return createMockDeliveryStatus(message);
        }
        
        try {
            Message twilioMessage = Message.creator(
                new PhoneNumber(message.getRecipientNumber()),
                new PhoneNumber(fromNumber),
                message.getMessageText()
            ).create();
            
            return SmsDeliveryStatus.builder()
                .messageId(twilioMessage.getSid())
                .recipientNumber(message.getRecipientNumber())
                .state(mapTwilioStatus(twilioMessage.getStatus()))
                .statusMessage(twilioMessage.getStatus().toString())
                .sentAt(LocalDateTime.now())
                .attemptCount(1)
                .cost(twilioMessage.getPrice() != null ? 
                    new BigDecimal(twilioMessage.getPrice()) : BigDecimal.ZERO)
                .build();
                
        } catch (Exception e) {
            throw new SmsDeliveryException("Failed to send SMS via Twilio", e);
        }
    }
    
    private SmsDeliveryStatus.DeliveryState mapTwilioStatus(Message.Status twilioStatus) {
        return switch (twilioStatus) {
            case QUEUED -> SmsDeliveryStatus.DeliveryState.QUEUED;
            case SENDING -> SmsDeliveryStatus.DeliveryState.SENDING;
            case SENT -> SmsDeliveryStatus.DeliveryState.SENT;
            case DELIVERED -> SmsDeliveryStatus.DeliveryState.DELIVERED;
            case FAILED -> SmsDeliveryStatus.DeliveryState.FAILED;
            case UNDELIVERED -> SmsDeliveryStatus.DeliveryState.UNDELIVERED;
            default -> SmsDeliveryStatus.DeliveryState.QUEUED;
        };
    }
    
    private SmsDeliveryStatus updateStatusFromProvider(String messageId, SmsDeliveryStatus currentStatus) {
        if (!twilioEnabled) {
            return currentStatus;
        }
        
        try {
            Message twilioMessage = Message.fetcher(messageId).fetch();
            
            SmsDeliveryStatus updatedStatus = SmsDeliveryStatus.builder()
                .messageId(messageId)
                .recipientNumber(currentStatus.getRecipientNumber())
                .state(mapTwilioStatus(twilioMessage.getStatus()))
                .statusMessage(twilioMessage.getStatus().toString())
                .sentAt(currentStatus.getSentAt())
                .deliveredAt(twilioMessage.getStatus() == Message.Status.DELIVERED ? 
                    LocalDateTime.now() : null)
                .failedAt(twilioMessage.getStatus() == Message.Status.FAILED ? 
                    LocalDateTime.now() : null)
                .attemptCount(currentStatus.getAttemptCount())
                .errorCode(twilioMessage.getErrorCode() != null ? 
                    String.valueOf(twilioMessage.getErrorCode()) : null)
                .errorMessage(twilioMessage.getErrorMessage())
                .cost(twilioMessage.getPrice() != null ? 
                    new BigDecimal(twilioMessage.getPrice()) : currentStatus.getCost())
                .build();
            
            updateDeliveryStatus(messageId, updatedStatus);
            return updatedStatus;
            
        } catch (Exception e) {
            log.error("Failed to update status from provider for message {}", messageId, e);
            return currentStatus;
        }
    }
    
    private SmsDeliveryStatus fetchStatusFromProvider(String messageId) {
        if (!twilioEnabled) {
            return null;
        }
        
        try {
            Message twilioMessage = Message.fetcher(messageId).fetch();
            
            return SmsDeliveryStatus.builder()
                .messageId(messageId)
                .recipientNumber(twilioMessage.getTo())
                .state(mapTwilioStatus(twilioMessage.getStatus()))
                .statusMessage(twilioMessage.getStatus().toString())
                .sentAt(LocalDateTime.now())
                .cost(twilioMessage.getPrice() != null ? 
                    new BigDecimal(twilioMessage.getPrice()) : BigDecimal.ZERO)
                .build();
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to fetch SMS status from provider for message {} - Status tracking unavailable", messageId, e);
            return SmsDeliveryStatus.builder()
                .messageId(messageId)
                .status("ERROR")
                .errorMessage("Provider fetch failed: " + e.getMessage())
                .build();
        }
    }
    
    private void trackDeliveryAsync(String messageId) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(deliveryCheckIntervalMs);
                
                for (int i = 0; i < 10; i++) { // Check up to 10 times
                    SmsDeliveryStatus status = getDeliveryStatus(messageId);
                    if (status != null && !status.isPending()) {
                        break;
                    }
                    Thread.sleep(deliveryCheckIntervalMs);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Delivery tracking interrupted for message {}", messageId);
            }
        }, executorService);
    }
    
    private void storeMessage(SmsMessage message) {
        pendingMessages.put(message.getMessageId(), message);
        
        String cacheKey = SMS_MESSAGE_CACHE_PREFIX + message.getMessageId();
        redisTemplate.opsForValue().set(cacheKey, message, CACHE_TTL);
    }
    
    private void storeDeliveryStatus(SmsDeliveryStatus status) {
        deliveryStatuses.put(status.getMessageId(), status);
        
        String cacheKey = SMS_STATUS_CACHE_PREFIX + status.getMessageId();
        redisTemplate.opsForValue().set(cacheKey, status, CACHE_TTL);
    }
    
    private void scheduleMessage(SmsMessage message) {
        log.info("Scheduling SMS for delivery at {}", message.getScheduledAt());
        
        // Store message in pending state
        storeMessage(message);
        storeDeliveryStatus(createScheduledStatus(message));
        
        // Calculate delay and schedule delivery
        long delayMs = calculateDeliveryDelay(message);
        if (delayMs > 0) {
            CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                .execute(() -> processScheduledMessage(message));
        } else {
            // Deliver immediately if scheduled time has passed
            processScheduledMessage(message);
        }
    }
    
    /**
     * Calculate delay until message should be delivered
     */
    private long calculateDeliveryDelay(SmsMessage message) {
        if (message.getScheduledAt() == null) {
            return 0;
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledTime = message.getScheduledAt();
        
        return Duration.between(now, scheduledTime).toMillis();
    }
    
    /**
     * Process a scheduled message for delivery
     */
    private void processScheduledMessage(SmsMessage message) {
        try {
            log.info("Processing scheduled SMS message {}", message.getMessageId());
            
            // Update status to queued and attempt delivery
            storeDeliveryStatus(createQueuedStatus(message));
            
            // This would integrate with actual SMS service
            boolean deliveryResult = attemptSmsDelivery(message);
            
            if (deliveryResult) {
                storeDeliveryStatus(createSentStatus(message));
            } else {
                storeDeliveryStatus(createFailedStatus(message, "Delivery attempt failed"));
            }
            
        } catch (Exception e) {
            log.error("Error processing scheduled message {}: {}", message.getMessageId(), e.getMessage());
            storeDeliveryStatus(createFailedStatus(message, "Processing error: " + e.getMessage()));
        }
    }
    
    /**
     * Create a scheduled status for a message
     */
    private SmsDeliveryStatus createScheduledStatus(SmsMessage message) {
        return SmsDeliveryStatus.builder()
            .messageId(message.getMessageId())
            .status("SCHEDULED")
            .timestamp(LocalDateTime.now())
            .scheduledAt(message.getScheduledAt())
            .phoneNumber(message.getTo())
            .build();
    }
    
    /**
     * Attempt actual SMS delivery (integration point)
     */
    private boolean attemptSmsDelivery(SmsMessage message) {
        try {
            // This would integrate with SMS provider (Twilio, AWS SNS, etc.)
            log.debug("Attempting SMS delivery for message {}", message.getMessageId());
            
            // For now, simulate successful delivery
            // In production: return smsProvider.send(message);
            return true;
            
        } catch (Exception e) {
            log.error("SMS delivery attempt failed: {}", e.getMessage());
            return false;
        }
    }
    
    private void scheduleRetry(SmsMessage message, int attemptCount) {
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(retryDelayMs * attemptCount); // Exponential backoff
                retryDelivery(message.getMessageId());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Retry scheduling interrupted for message {}", message.getMessageId());
            }
        }, executorService);
    }
    
    private boolean shouldRetry(SmsMessage message, Integer attemptCount) {
        int attempts = attemptCount != null ? attemptCount : 0;
        return attempts < maxRetries && 
               (message.getMaxRetries() == null || attempts < message.getMaxRetries());
    }
    
    private void handleFailedDelivery(String messageId, SmsDeliveryStatus status) {
        SmsMessage message = pendingMessages.get(messageId);
        if (message != null && shouldRetry(message, status.getAttemptCount())) {
            scheduleRetry(message, status.getAttemptCount());
        } else {
            // Final failure, clean up
            pendingMessages.remove(messageId);
            publishSmsEvent("SMS_FAILED_PERMANENTLY", message, status);
        }
    }
    
    private void publishSmsEvent(String eventType, SmsMessage message, SmsDeliveryStatus status) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("message", message);
        event.put("status", status);
        event.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("sms-events", event);
    }
    
    private void publishDeliveryStatusEvent(SmsDeliveryStatus status) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "SMS_DELIVERY_STATUS_UPDATED");
        event.put("status", status);
        event.put("timestamp", LocalDateTime.now());
        
        kafkaTemplate.send("sms-delivery-status", event);
    }
    
    private boolean isWithinDateRange(SmsDeliveryStatus status, LocalDateTime startDate, 
                                     LocalDateTime endDate) {
        LocalDateTime sentAt = status.getSentAt();
        return sentAt != null && 
               !sentAt.isBefore(startDate) && 
               !sentAt.isAfter(endDate);
    }
    
    private SmsDeliveryStatus createQueuedStatus(SmsMessage message) {
        return SmsDeliveryStatus.builder()
            .messageId(message.getMessageId())
            .recipientNumber(message.getRecipientNumber())
            .state(SmsDeliveryStatus.DeliveryState.QUEUED)
            .statusMessage("Message queued for delivery")
            .sentAt(LocalDateTime.now())
            .attemptCount(0)
            .build();
    }
    
    private SmsDeliveryStatus createFailedStatus(SmsMessage message, String errorMessage) {
        return SmsDeliveryStatus.builder()
            .messageId(message.getMessageId())
            .recipientNumber(message.getRecipientNumber())
            .state(SmsDeliveryStatus.DeliveryState.FAILED)
            .statusMessage("Delivery failed")
            .errorMessage(errorMessage)
            .failedAt(LocalDateTime.now())
            .attemptCount(1)
            .build();
    }
    
    private SmsDeliveryStatus createMockDeliveryStatus(SmsMessage message) {
        // For testing when Twilio is disabled
        return SmsDeliveryStatus.builder()
            .messageId(UUID.randomUUID().toString())
            .recipientNumber(message.getRecipientNumber())
            .state(SmsDeliveryStatus.DeliveryState.DELIVERED)
            .statusMessage("Mock delivery successful")
            .sentAt(LocalDateTime.now())
            .deliveredAt(LocalDateTime.now().plusSeconds(2))
            .attemptCount(1)
            .cost(BigDecimal.valueOf(0.01))
            .build();
    }
    
    // Fallback method
    
    public CompletableFuture<SmsDeliveryStatus> sendAndTrackSmsFallback(SmsMessage message, Exception ex) {
        log.warn("SMS delivery fallback triggered for {}: {}", message.getRecipientNumber(), ex.getMessage());
        
        return CompletableFuture.completedFuture(
            createFailedStatus(message, "Service temporarily unavailable")
        );
    }
    
    // Exception class
    
    public static class SmsDeliveryException extends RuntimeException {
        public SmsDeliveryException(String message) {
            super(message);
        }
        
        public SmsDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}