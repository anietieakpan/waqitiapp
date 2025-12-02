package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.security.SecurityContext;
import com.waqiti.common.validation.EventValidator;
import com.waqiti.user.model.*;
import com.waqiti.user.repository.*;
import com.waqiti.user.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileUpdatesConsumer {

    private final ObjectMapper objectMapper;
    private final EventValidator eventValidator;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final SecurityContext securityContext;
    
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ProfileUpdateHistoryRepository profileUpdateHistoryRepository;
    private final UserPreferencesRepository userPreferencesRepository;
    private final UserComplianceRepository userComplianceRepository;
    private final ProfileValidationRepository profileValidationRepository;
    
    private final UserProfileService userProfileService;
    private final ProfileValidationService profileValidationService;
    private final ProfileEnrichmentService profileEnrichmentService;
    private final UserNotificationService userNotificationService;
    private final ComplianceVerificationService complianceVerificationService;
    private final UserAnalyticsService userAnalyticsService;
    private final UniversalDLQHandler dlqHandler;
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private final Map<String, Long> processingMetrics = new ConcurrentHashMap<>();
    private final Map<String, Integer> updateTypeCounts = new ConcurrentHashMap<>();

    @KafkaListener(topics = "profile-updates", groupId = "user-service-group")
    @CircuitBreaker(name = "profile-updates-consumer", fallbackMethod = "fallbackProcessProfileUpdate")
    @Retry(name = "profile-updates-consumer")
    @Transactional
    public void processProfileUpdate(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        long startTime = System.currentTimeMillis();
        String eventId = null;
        String updateType = null;
        String userId = null;

        try {
            log.info("Processing profile update from topic: {}, partition: {}, offset: {}", topic, partition, offset);

            JsonNode eventNode = objectMapper.readTree(eventPayload);
            eventId = eventNode.has("eventId") ? eventNode.get("eventId").asText() : UUID.randomUUID().toString();
            updateType = eventNode.has("updateType") ? eventNode.get("updateType").asText() : "UNKNOWN";
            userId = eventNode.has("userId") ? eventNode.get("userId").asText() : null;

            if (!eventValidator.validateEvent(eventNode, "PROFILE_UPDATE_SCHEMA")) {
                throw new IllegalArgumentException("Invalid profile update event structure");
            }

            ProfileUpdateContext context = buildUpdateContext(eventNode, eventId, updateType, userId);
            
            validateProfileUpdate(context);
            enrichProfileUpdate(context);
            
            ProfileUpdateResult result = processUpdateByType(context);
            
            executeAutomatedActions(context, result);
            updateProfileMetrics(context, result);
            
            auditService.logUserEvent(eventId, updateType, userId, "SUCCESS", result.getProcessingDetails());
            
            long processingTime = System.currentTimeMillis() - startTime;
            metricsService.recordProcessingTime("profile_updates_consumer", processingTime);
            metricsService.incrementCounter("profile_updates_processed", "type", updateType);
            
            processingMetrics.put(updateType, processingTime);
            updateTypeCounts.merge(updateType, 1, Integer::sum);

            acknowledgment.acknowledge();
            log.info("Successfully processed profile update: {} of type: {} in {}ms", eventId, updateType, processingTime);

        } catch (Exception e) {
            log.error("Error executing automated actions for profile update: {}", context.getEventId(), e);

            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, event),
                e
            ).exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Processing failed", e);
        }

    private boolean isRetryableError(Exception e) {
        return e instanceof org.springframework.dao.TransientDataAccessException ||
               e instanceof java.net.SocketTimeoutException ||
               e instanceof org.springframework.web.client.ResourceAccessException;
    }

    private void sendToDlq(String originalMessage, String dlqTopic, String errorType, String errorMessage) {
        Map<String, Object> dlqMessage = Map.of(
            "originalMessage", originalMessage,
            "errorType", errorType,
            "errorMessage", errorMessage,
            "timestamp", Instant.now().toString(),
            "service", "user-service"
        );
        
        kafkaTemplate.send(dlqTopic, dlqMessage);
    }

    public void fallbackProcessProfileUpdate(String eventPayload, String topic, int partition, long offset, 
                                           Long timestamp, Acknowledgment acknowledgment, Exception ex) {
        log.error("Circuit breaker fallback triggered for profile update processing", ex);
        
        metricsService.incrementCounter("profile_update_circuit_breaker_fallback");
        
        sendToDlq(eventPayload, "profile-updates-dlq", "CIRCUIT_BREAKER_OPEN", ex.getMessage());
        acknowledgment.acknowledge();
    }
}