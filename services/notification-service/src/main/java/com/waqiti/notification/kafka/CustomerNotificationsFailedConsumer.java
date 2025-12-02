package com.waqiti.notification.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.notification.service.NotificationRetryService;
import com.waqiti.notification.service.NotificationFailureAnalysisService;
import com.waqiti.notification.service.AlternativeChannelService;
import com.waqiti.notification.service.CustomerCommunicationService;
import com.waqiti.notification.domain.NotificationFailure;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.notification.domain.FailureReason;
import com.waqiti.common.audit.AuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class CustomerNotificationsFailedConsumer {

    private final NotificationRetryService retryService;
    private final NotificationFailureAnalysisService failureAnalysisService;
    private final AlternativeChannelService alternativeChannelService;
    private final CustomerCommunicationService customerCommService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter notificationFailuresProcessedCounter;
    private final Counter notificationFailuresRetryCounter;
    private final Counter alternativeChannelUsedCounter;
    private final Counter permanentFailuresCounter;
    private final Timer notificationFailureProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public CustomerNotificationsFailedConsumer(
            NotificationRetryService retryService,
            NotificationFailureAnalysisService failureAnalysisService,
            AlternativeChannelService alternativeChannelService,
            CustomerCommunicationService customerCommService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.retryService = retryService;
        this.failureAnalysisService = failureAnalysisService;
        this.alternativeChannelService = alternativeChannelService;
        this.customerCommService = customerCommService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.notificationFailuresProcessedCounter = Counter.builder("notification.failures.processed")
            .description("Count of notification failures processed")
            .register(meterRegistry);
        
        this.notificationFailuresRetryCounter = Counter.builder("notification.failures.retried")
            .description("Count of notification failures that were retried")
            .register(meterRegistry);
        
        this.alternativeChannelUsedCounter = Counter.builder("notification.failures.alternative_channel_used")
            .description("Count of times alternative channels were used")
            .register(meterRegistry);
        
        this.permanentFailuresCounter = Counter.builder("notification.failures.permanent")
            .description("Count of permanent notification failures")
            .register(meterRegistry);
        
        this.notificationFailureProcessingTimer = Timer.builder("notification.failures.processing.duration")
            .description("Time taken to process notification failures")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "customer-notifications-failed",
        groupId = "customer-notifications-failed-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "customer-notifications-failed-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleCustomerNotificationFailedEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.warn("Customer notification failure received - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Customer notification failure event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String notificationId = (String) eventData.get("notificationId");
            String customerId = (String) eventData.get("customerId");
            String notificationType = (String) eventData.get("notificationType");
            String failedChannel = (String) eventData.get("failedChannel");
            String failureReason = (String) eventData.get("failureReason");
            String errorMessage = (String) eventData.get("errorMessage");
            Integer attemptNumber = (Integer) eventData.get("attemptNumber");
            String originalMessage = (String) eventData.get("originalMessage");
            Boolean isCritical = (Boolean) eventData.getOrDefault("isCritical", false);
            Boolean allowRetry = (Boolean) eventData.getOrDefault("allowRetry", true);
            
            String correlationId = String.format("notification-failure-%s-%d", 
                notificationId, System.currentTimeMillis());
            
            log.warn("Processing customer notification failure - notificationId: {}, customerId: {}, channel: {}, reason: {}, correlationId: {}", 
                notificationId, customerId, failedChannel, failureReason, correlationId);
            
            notificationFailuresProcessedCounter.increment();
            
            processNotificationFailure(notificationId, customerId, notificationType, failedChannel,
                failureReason, errorMessage, attemptNumber, originalMessage, isCritical,
                allowRetry, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(notificationFailureProcessingTimer);
            
            log.info("Customer notification failure processed - eventId: {}, notificationId: {}", eventId, notificationId);
            
        } catch (Exception e) {
            log.error("Failed to process customer notification failure event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Customer notification failure processing failed", e);
        }
    }

    @CircuitBreaker(name = "notification", fallbackMethod = "processNotificationFailureFallback")
    @Retry(name = "notification")
    private void processNotificationFailure(
            String notificationId,
            String customerId,
            String notificationType,
            String failedChannel,
            String failureReason,
            String errorMessage,
            Integer attemptNumber,
            String originalMessage,
            Boolean isCritical,
            Boolean allowRetry,
            Map<String, Object> eventData,
            String correlationId) {
        
        NotificationFailure failure = NotificationFailure.builder()
            .notificationId(notificationId)
            .customerId(customerId)
            .notificationType(notificationType)
            .failedChannel(NotificationChannel.valueOf(failedChannel))
            .failureReason(FailureReason.valueOf(failureReason))
            .errorMessage(errorMessage)
            .attemptNumber(attemptNumber)
            .originalMessage(originalMessage)
            .isCritical(isCritical)
            .failureTimestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        // Analyze failure to determine if it's temporary or permanent
        boolean isTemporaryFailure = failureAnalysisService.isTemporaryFailure(failure);
        boolean shouldRetry = allowRetry && isTemporaryFailure && attemptNumber < getMaxRetryAttempts(notificationType, isCritical);
        
        if (shouldRetry) {
            // Schedule retry with exponential backoff
            retryService.scheduleRetry(failure, correlationId);
            notificationFailuresRetryCounter.increment();
            
            log.info("Scheduled retry for notification failure - notificationId: {}, attempt: {}, correlationId: {}", 
                notificationId, attemptNumber + 1, correlationId);
        } else {
            // Try alternative channels if available
            var alternativeChannels = alternativeChannelService.getAlternativeChannels(
                customerId, NotificationChannel.valueOf(failedChannel), isCritical);
            
            boolean alternativeUsed = false;
            for (NotificationChannel altChannel : alternativeChannels) {
                try {
                    customerCommService.sendViaAlternativeChannel(
                        failure, altChannel, correlationId);
                    alternativeChannelUsedCounter.increment();
                    alternativeUsed = true;
                    
                    log.info("Sent notification via alternative channel - notificationId: {}, channel: {}, correlationId: {}", 
                        notificationId, altChannel, correlationId);
                    break;
                } catch (Exception e) {
                    log.warn("Alternative channel {} also failed for notification {}: {}", 
                        altChannel, notificationId, e.getMessage());
                }
            }
            
            if (!alternativeUsed) {
                // Permanent failure - log and alert
                permanentFailuresCounter.increment();
                handlePermanentFailure(failure, correlationId);
            }
        }
        
        // Update customer communication preferences if needed
        failureAnalysisService.updateCustomerPreferencesBasedOnFailure(failure, correlationId);
        
        // Publish notification failure status update
        kafkaTemplate.send("notification-failure-status-updates", Map.of(
            "notificationId", notificationId,
            "customerId", customerId,
            "failedChannel", failedChannel,
            "failureReason", failureReason,
            "isTemporaryFailure", isTemporaryFailure,
            "willRetry", shouldRetry,
            "alternativeChannelUsed", alternativeChannels.size() > 0,
            "eventType", "NOTIFICATION_FAILURE_PROCESSED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Send to customer service if critical notification failed permanently
        if (isCritical && !shouldRetry && alternativeChannels.isEmpty()) {
            kafkaTemplate.send("customer-service-notification-failures", Map.of(
                "notificationId", notificationId,
                "customerId", customerId,
                "notificationType", notificationType,
                "failureReason", failureReason,
                "requiresManualFollowup", true,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            ));
        }
        
        // Update notification delivery metrics
        kafkaTemplate.send("notification-metrics-updates", Map.of(
            "customerId", customerId,
            "channel", failedChannel,
            "notificationType", notificationType,
            "eventType", "DELIVERY_FAILED",
            "failureReason", failureReason,
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logNotificationEvent(
            "CUSTOMER_NOTIFICATION_FAILURE_PROCESSED",
            notificationId,
            Map.of(
                "customerId", customerId,
                "notificationType", notificationType,
                "failedChannel", failedChannel,
                "failureReason", failureReason,
                "errorMessage", errorMessage,
                "attemptNumber", attemptNumber,
                "isTemporaryFailure", isTemporaryFailure,
                "willRetry", shouldRetry,
                "isCritical", isCritical,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        log.info("Customer notification failure fully processed - notificationId: {}, customerId: {}, willRetry: {}, correlationId: {}", 
            notificationId, customerId, shouldRetry, correlationId);
    }

    private void handlePermanentFailure(NotificationFailure failure, String correlationId) {
        log.error("PERMANENT NOTIFICATION FAILURE - notificationId: {}, customerId: {}, channel: {}, correlationId: {}", 
            failure.getNotificationId(), failure.getCustomerId(), failure.getFailedChannel(), correlationId);
        
        // Store permanent failure for analysis
        failureAnalysisService.recordPermanentFailure(failure, correlationId);
        
        // Alert operations team about delivery issues
        kafkaTemplate.send("operations-notification-alerts", Map.of(
            "alertType", "PERMANENT_NOTIFICATION_FAILURE",
            "notificationId", failure.getNotificationId(),
            "customerId", failure.getCustomerId(),
            "channel", failure.getFailedChannel().toString(),
            "failureReason", failure.getFailureReason().toString(),
            "isCritical", failure.getIsCritical(),
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        // Update customer unreachability status if multiple channels failed
        customerCommService.checkAndUpdateUnreachabilityStatus(failure.getCustomerId(), correlationId);
    }

    private int getMaxRetryAttempts(String notificationType, Boolean isCritical) {
        if (isCritical) {
            return 5; // More retries for critical notifications
        }
        
        switch (notificationType.toUpperCase()) {
            case "SECURITY_ALERT":
            case "FRAUD_ALERT":
            case "PAYMENT_FAILED":
                return 4;
            case "MARKETING":
            case "PROMOTIONAL":
                return 1;
            default:
                return 3;
        }
    }

    private void processNotificationFailureFallback(
            String notificationId,
            String customerId,
            String notificationType,
            String failedChannel,
            String failureReason,
            String errorMessage,
            Integer attemptNumber,
            String originalMessage,
            Boolean isCritical,
            Boolean allowRetry,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for notification failure processing - notificationId: {}, customerId: {}, correlationId: {}, error: {}", 
            notificationId, customerId, correlationId, e.getMessage());
        
        // Try emergency notification to operations team
        try {
            customerCommService.sendEmergencyOperationsAlert(
                notificationId, customerId, failedChannel, failureReason, correlationId);
        } catch (Exception fallbackException) {
            log.error("Emergency operations alert failed - notificationId: {}, error: {}", 
                notificationId, fallbackException.getMessage());
        }
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("notificationId", notificationId);
        fallbackEvent.put("customerId", customerId);
        fallbackEvent.put("notificationType", notificationType);
        fallbackEvent.put("failedChannel", failedChannel);
        fallbackEvent.put("failureReason", failureReason);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("notification-failure-processing-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Customer notification failure message sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
            topic, partition, offset, exceptionMessage);
        
        try {
            Map<String, Object> dltEvent = Map.of(
                "originalTopic", topic,
                "partition", partition,
                "offset", offset,
                "message", message,
                "error", exceptionMessage,
                "timestamp", Instant.now().toString(),
                "dltReason", "MAX_RETRIES_EXCEEDED"
            );
            
            kafkaTemplate.send("notification-failure-processing-failures", dltEvent);
            
            customerCommService.sendCriticalOperationalAlert(
                "Notification Failure Processing Failed",
                String.format("CRITICAL: Failed to process customer notification failure after max retries. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process notification failure DLT message: {}", e.getMessage(), e);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, String.valueOf(System.currentTimeMillis()));
        
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}