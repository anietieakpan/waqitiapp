package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.customer.service.CustomerCommunicationService;
import com.waqiti.customer.service.CommunicationChannelService;
import com.waqiti.customer.service.CustomerEngagementService;
import com.waqiti.customer.service.PersonalizationService;
import com.waqiti.customer.service.CommunicationMetricsService;
import com.waqiti.customer.entity.Customer;
import com.waqiti.customer.entity.CustomerCommunication;
import com.waqiti.customer.domain.CommunicationType;
import com.waqiti.customer.domain.DeliveryStatus;
import com.waqiti.customer.domain.CommunicationPriority;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerCommunicationsConsumer {

    private final CustomerCommunicationService communicationService;
    private final CommunicationChannelService channelService;
    private final CustomerEngagementService engagementService;
    private final PersonalizationService personalizationService;
    private final CommunicationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("customer_communications_processed_total")
            .description("Total number of successfully processed customer communication events")
            .register(meterRegistry);
        errorCounter = Counter.builder("customer_communications_errors_total")
            .description("Total number of customer communication processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("customer_communications_processing_duration")
            .description("Time taken to process customer communication events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"customer-communications", "customer-communication-delivery", "customer-communication-status"},
        groupId = "customer-communications-service-group",
        containerFactory = "customerCommunicationKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "customer-communications", fallbackMethod = "handleCustomerCommunicationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCustomerCommunicationEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String customerId = (String) eventData.get("customerId");
        String eventType = (String) eventData.get("eventType");
        String correlationId = String.format("comm-%s-p%d-o%d", customerId, partition, offset);
        String eventKey = String.format("%s-%s-%s", customerId, eventType, eventData.get("timestamp"));

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Communication event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing customer communication event: customerId={}, type={}, channel={}",
                customerId, eventType, eventData.get("channel"));

            // Clean expired entries periodically
            cleanExpiredEntries();

            switch (eventType) {
                case "COMMUNICATION_REQUESTED":
                    processCommunicationRequest(eventData, correlationId);
                    break;

                case "COMMUNICATION_SCHEDULED":
                    scheduleCommunication(eventData, correlationId);
                    break;

                case "COMMUNICATION_SENT":
                    processCommunicationSent(eventData, correlationId);
                    break;

                case "COMMUNICATION_DELIVERED":
                    processCommunicationDelivered(eventData, correlationId);
                    break;

                case "COMMUNICATION_FAILED":
                    processCommunicationFailed(eventData, correlationId);
                    break;

                case "COMMUNICATION_OPENED":
                    processCommunicationOpened(eventData, correlationId);
                    break;

                case "COMMUNICATION_CLICKED":
                    processCommunicationClicked(eventData, correlationId);
                    break;

                case "UNSUBSCRIBE_REQUESTED":
                    processUnsubscribeRequest(eventData, correlationId);
                    break;

                default:
                    log.warn("Unknown customer communication event type: {}", eventType);
                    break;
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logCustomerEvent("CUSTOMER_COMMUNICATION_EVENT_PROCESSED", customerId,
                Map.of("eventType", eventType, "channel", eventData.get("channel"),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process customer communication event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("customer-communications-fallback-events", Map.of(
                "originalEvent", eventData, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCustomerCommunicationEventFallback(
            Map<String, Object> eventData,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String customerId = (String) eventData.get("customerId");
        String correlationId = String.format("comm-fallback-%s-p%d-o%d", customerId, partition, offset);

        log.error("Circuit breaker fallback triggered for customer communication: customerId={}, error={}",
            customerId, ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("customer-communications-dlq", Map.of(
            "originalEvent", eventData,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Customer Communication Circuit Breaker Triggered",
                String.format("Customer %s communication failed: %s", customerId, ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCustomerCommunicationEvent(
            @Payload Map<String, Object> eventData,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String customerId = (String) eventData.get("customerId");
        String correlationId = String.format("dlt-comm-%s-%d", customerId, System.currentTimeMillis());

        log.error("Dead letter topic handler - Customer communication permanently failed: customerId={}, topic={}, error={}",
            customerId, topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logCustomerEvent("CUSTOMER_COMMUNICATION_DLT_EVENT", customerId,
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", eventData.get("eventType"), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send critical alert
        try {
            notificationService.sendCriticalAlert(
                "Customer Communication Dead Letter Event",
                String.format("Customer %s communication sent to DLT: %s", customerId, exceptionMessage),
                Map.of("customerId", customerId, "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send critical DLT alert: {}", ex.getMessage());
        }
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) {
            return false;
        }

        // Check if the entry has expired
        if (System.currentTimeMillis() - timestamp > TTL_24_HOURS) {
            processedEvents.remove(eventKey);
            return false;
        }

        return true;
    }

    private void markEventAsProcessed(String eventKey) {
        processedEvents.put(eventKey, System.currentTimeMillis());
    }

    private void cleanExpiredEntries() {
        if (processedEvents.size() > 1000) { // Only clean when we have many entries
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processCommunicationRequest(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String messageType = (String) eventData.get("messageType");
        String channel = (String) eventData.get("channel");
        String content = (String) eventData.get("content");

        // Validate customer preferences and channel eligibility
        Customer customer = communicationService.getCustomer(customerId);
        if (!channelService.isChannelAvailable(customerId, channel)) {
            log.warn("Communication channel not available for customer: customerId={}, channel={}", customerId, channel);
            return;
        }

        // Personalize content based on customer profile
        String personalizedContent = personalizationService.personalizeContent(customerId, content, messageType);

        // Create communication record
        CustomerCommunication communication = CustomerCommunication.builder()
            .customerId(customerId)
            .communicationType(CommunicationType.valueOf(messageType))
            .channel(channel)
            .content(personalizedContent)
            .originalContent(content)
            .status(DeliveryStatus.REQUESTED)
            .priority(CommunicationPriority.valueOf((String) eventData.getOrDefault("priority", "MEDIUM")))
            .scheduledFor(eventData.containsKey("scheduledFor") ?
                LocalDateTime.parse((String) eventData.get("scheduledFor")) : LocalDateTime.now())
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .build();

        communicationService.saveCommunication(communication);

        // Trigger engagement scoring
        engagementService.updateEngagementScore(customerId, "COMMUNICATION_REQUESTED");

        // Send to communication scheduler
        kafkaTemplate.send("customer-communication-delivery", Map.of(
            "customerId", customerId,
            "communicationId", communication.getId(),
            "eventType", "COMMUNICATION_SCHEDULED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        metricsService.recordCommunicationRequested(messageType, channel);

        log.info("Communication request processed: customerId={}, type={}, channel={}",
            customerId, messageType, channel);
    }

    private void scheduleCommunication(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String communicationId = (String) eventData.get("communicationId");

        CustomerCommunication communication = communicationService.getCommunication(communicationId);
        if (communication == null) {
            log.error("Communication not found: communicationId={}", communicationId);
            return;
        }

        communication.setStatus(DeliveryStatus.SCHEDULED);
        communication.setScheduledAt(LocalDateTime.now());
        communicationService.saveCommunication(communication);

        // Schedule delivery based on customer preferences and optimal timing
        LocalDateTime optimalTime = engagementService.getOptimalDeliveryTime(customerId, communication.getChannel());

        if (optimalTime.isAfter(LocalDateTime.now())) {
            // Schedule for later delivery
            communicationService.scheduleForDelivery(communicationId, optimalTime);
        } else {
            // Send immediately
            kafkaTemplate.send("customer-communication-delivery", Map.of(
                "customerId", customerId,
                "communicationId", communicationId,
                "eventType", "COMMUNICATION_SENT",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        metricsService.recordCommunicationScheduled(communication.getCommunicationType().toString());

        log.info("Communication scheduled: customerId={}, communicationId={}, deliveryTime={}",
            customerId, communicationId, optimalTime);
    }

    private void processCommunicationSent(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String communicationId = (String) eventData.get("communicationId");

        CustomerCommunication communication = communicationService.getCommunication(communicationId);
        if (communication == null) {
            log.error("Communication not found: communicationId={}", communicationId);
            return;
        }

        communication.setStatus(DeliveryStatus.SENT);
        communication.setSentAt(LocalDateTime.now());
        communicationService.saveCommunication(communication);

        // Update delivery metrics
        metricsService.recordCommunicationSent(
            communication.getCommunicationType().toString(),
            communication.getChannel());

        // Track engagement expectations
        engagementService.trackCommunicationSent(customerId, communicationId);

        log.info("Communication sent: customerId={}, communicationId={}", customerId, communicationId);
    }

    private void processCommunicationDelivered(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String communicationId = (String) eventData.get("communicationId");

        CustomerCommunication communication = communicationService.getCommunication(communicationId);
        if (communication == null) {
            log.error("Communication not found: communicationId={}", communicationId);
            return;
        }

        communication.setStatus(DeliveryStatus.DELIVERED);
        communication.setDeliveredAt(LocalDateTime.now());
        communicationService.saveCommunication(communication);

        metricsService.recordCommunicationDelivered(
            communication.getCommunicationType().toString(),
            communication.getChannel());

        engagementService.updateEngagementScore(customerId, "COMMUNICATION_DELIVERED");

        log.info("Communication delivered: customerId={}, communicationId={}", customerId, communicationId);
    }

    private void processCommunicationFailed(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String communicationId = (String) eventData.get("communicationId");
        String errorReason = (String) eventData.get("errorReason");

        CustomerCommunication communication = communicationService.getCommunication(communicationId);
        if (communication == null) {
            log.error("Communication not found: communicationId={}", communicationId);
            return;
        }

        communication.setStatus(DeliveryStatus.FAILED);
        communication.setFailureReason(errorReason);
        communication.setFailedAt(LocalDateTime.now());
        communicationService.saveCommunication(communication);

        // Handle retry logic based on failure reason
        if (channelService.shouldRetryDelivery(errorReason)) {
            communicationService.scheduleRetryDelivery(communicationId);
        } else {
            // Try alternative channel if available
            String alternativeChannel = channelService.getAlternativeChannel(customerId, communication.getChannel());
            if (alternativeChannel != null) {
                communicationService.scheduleAlternativeChannelDelivery(communicationId, alternativeChannel);
            }
        }

        metricsService.recordCommunicationFailed(
            communication.getCommunicationType().toString(),
            communication.getChannel(),
            errorReason);

        log.warn("Communication failed: customerId={}, communicationId={}, reason={}",
            customerId, communicationId, errorReason);
    }

    private void processCommunicationOpened(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String communicationId = (String) eventData.get("communicationId");

        engagementService.recordCommunicationOpened(customerId, communicationId);
        metricsService.recordCommunicationOpened(customerId, communicationId);

        // Update customer engagement profile
        engagementService.updateEngagementScore(customerId, "COMMUNICATION_OPENED");

        log.info("Communication opened: customerId={}, communicationId={}", customerId, communicationId);
    }

    private void processCommunicationClicked(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String communicationId = (String) eventData.get("communicationId");
        String clickedElement = (String) eventData.get("clickedElement");

        engagementService.recordCommunicationClicked(customerId, communicationId, clickedElement);
        metricsService.recordCommunicationClicked(customerId, communicationId, clickedElement);

        // High-value engagement event
        engagementService.updateEngagementScore(customerId, "COMMUNICATION_CLICKED");

        log.info("Communication clicked: customerId={}, communicationId={}, element={}",
            customerId, communicationId, clickedElement);
    }

    private void processUnsubscribeRequest(Map<String, Object> eventData, String correlationId) {
        String customerId = (String) eventData.get("customerId");
        String communicationType = (String) eventData.get("communicationType");
        String channel = (String) eventData.get("channel");

        // Process unsubscribe request
        communicationService.processUnsubscribeRequest(customerId, communicationType, channel);

        // Update customer preferences
        channelService.updateChannelPreferences(customerId, channel, false);

        // Record unsubscribe event
        auditService.logCustomerEvent("COMMUNICATION_UNSUBSCRIBE", customerId,
            Map.of("communicationType", communicationType, "channel", channel,
                "correlationId", correlationId, "timestamp", Instant.now()));

        metricsService.recordUnsubscribeRequest(communicationType, channel);

        log.info("Unsubscribe request processed: customerId={}, type={}, channel={}",
            customerId, communicationType, channel);
    }
}