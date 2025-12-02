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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentVerificationConsumer {

    private final ObjectMapper objectMapper;
    private final EventValidator eventValidator;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final SecurityContext securityContext;
    
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVerificationRepository documentVerificationRepository;
    private final DocumentHistoryRepository documentHistoryRepository;
    private final UserComplianceRepository userComplianceRepository;
    private final KycStatusRepository kycStatusRepository;
    
    private final DocumentVerificationService documentVerificationService;
    private final OcrService ocrService;
    private final DocumentAuthenticityService documentAuthenticityService;
    private final FaceMatchingService faceMatchingService;
    private final UserNotificationService userNotificationService;
    private final ComplianceService complianceService;
    private final UniversalDLQHandler dlqHandler;
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private final Map<String, Long> processingMetrics = new ConcurrentHashMap<>();
    private final Map<String, Integer> documentTypeCounts = new ConcurrentHashMap<>();

    @KafkaListener(topics = "document-verification", groupId = "user-service-group")
    @CircuitBreaker(name = "document-verification-consumer", fallbackMethod = "fallbackProcessDocumentVerification")
    @Retry(name = "document-verification-consumer")
    @Transactional
    public void processDocumentVerification(
            @Payload String eventPayload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        long startTime = System.currentTimeMillis();
        String eventId = null;
        String documentType = null;
        String userId = null;

        try {
            log.info("Processing document verification from topic: {}, partition: {}, offset: {}", topic, partition, offset);

            JsonNode eventNode = objectMapper.readTree(eventPayload);
            eventId = eventNode.has("eventId") ? eventNode.get("eventId").asText() : UUID.randomUUID().toString();
            documentType = eventNode.has("documentType") ? eventNode.get("documentType").asText() : "UNKNOWN";
            userId = eventNode.has("userId") ? eventNode.get("userId").asText() : null;

            if (!eventValidator.validateEvent(eventNode, "DOCUMENT_VERIFICATION_SCHEMA")) {
                throw new IllegalArgumentException("Invalid document verification event structure");
            }

            DocumentVerificationContext context = buildVerificationContext(eventNode, eventId, documentType, userId);
            
            validateDocumentVerification(context);
            enrichVerificationContext(context);
            
            DocumentVerificationResult result = processDocumentByType(context);
            
            executeAutomatedActions(context, result);
            updateVerificationMetrics(context, result);
            
            auditService.logUserEvent(eventId, "DOCUMENT_VERIFICATION", userId, "SUCCESS", result.getProcessingDetails());
            
            long processingTime = System.currentTimeMillis() - startTime;
            metricsService.recordProcessingTime("document_verification_consumer", processingTime);
            metricsService.incrementCounter("documents_verified", "type", documentType);
            
            processingMetrics.put(documentType, processingTime);
            documentTypeCounts.merge(documentType, 1, Integer::sum);

            acknowledgment.acknowledge();
            log.info("Successfully processed document verification: {} of type: {} in {}ms", eventId, documentType, processingTime);

        } catch (Exception e) {
            log.error("Error executing automated actions for document verification: {}", context.getEventId(), e);

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

    public void fallbackProcessDocumentVerification(String eventPayload, String topic, int partition, long offset, 
                                                   Long timestamp, Acknowledgment acknowledgment, Exception ex) {
        log.error("Circuit breaker fallback triggered for document verification processing", ex);
        
        metricsService.incrementCounter("document_verification_circuit_breaker_fallback");
        
        sendToDlq(eventPayload, "document-verification-dlq", "CIRCUIT_BREAKER_OPEN", ex.getMessage());
        acknowledgment.acknowledge();
    }
}