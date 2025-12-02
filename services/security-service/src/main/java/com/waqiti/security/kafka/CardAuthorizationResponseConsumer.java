package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.CardSecurityService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.security.service.ThreatResponseService;
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
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class CardAuthorizationResponseConsumer {

    private final CardSecurityService cardSecurityService;
    private final SecurityNotificationService securityNotificationService;
    private final ThreatResponseService threatResponseService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("card_authorization_response_processed_total")
            .description("Total number of successfully processed card authorization response events")
            .register(meterRegistry);
        errorCounter = Counter.builder("card_authorization_response_errors_total")
            .description("Total number of card authorization response processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("card_authorization_response_processing_duration")
            .description("Time taken to process card authorization response events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"card-authorization-response", "card-auth-responses", "payment-authorization-responses"},
        groupId = "security-service-card-authorization-response-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "card-authorization-response", fallbackMethod = "handleCardAuthorizationResponseFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleCardAuthorizationResponse(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("card-auth-response-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String authorizationId = (String) event.get("authorizationId");
            String cardId = (String) event.get("cardId");
            String userId = (String) event.get("userId");
            String responseStatus = (String) event.get("responseStatus");
            String eventKey = String.format("%s-%s-%s", authorizationId, cardId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing card authorization response: authId={}, cardId={}, userId={}, status={}",
                authorizationId, cardId, userId, responseStatus);

            // Clean expired entries periodically
            cleanExpiredEntries();

            String transactionId = (String) event.get("transactionId");
            Double amount = ((Number) event.get("amount")).doubleValue();
            String currency = (String) event.get("currency");
            String merchantId = (String) event.get("merchantId");
            String responseCode = (String) event.get("responseCode");
            String responseMessage = (String) event.get("responseMessage");
            LocalDateTime processedAt = LocalDateTime.parse((String) event.get("processedAt"));
            @SuppressWarnings("unchecked")
            Map<String, Object> authenticationData = (Map<String, Object>) event.getOrDefault("authenticationData", Map.of());
            @SuppressWarnings("unchecked")
            List<String> declineReasons = (List<String>) event.getOrDefault("declineReasons", List.of());
            String deviceId = (String) event.get("deviceId");
            String ipAddress = (String) event.get("ipAddress");
            String location = (String) event.get("location");
            Double riskScore = ((Number) event.getOrDefault("riskScore", 0.0)).doubleValue();
            @SuppressWarnings("unchecked")
            Map<String, Object> securityFlags = (Map<String, Object>) event.getOrDefault("securityFlags", Map.of());

            // Process authorization response based on status
            switch (responseStatus) {
                case "APPROVED":
                    processApprovedAuthorization(authorizationId, cardId, userId, transactionId, amount,
                        currency, merchantId, processedAt, authenticationData, riskScore, correlationId);
                    break;

                case "DECLINED":
                    processDeclinedAuthorization(authorizationId, cardId, userId, transactionId, amount,
                        currency, merchantId, responseCode, declineReasons, riskScore, correlationId);
                    break;

                case "FRAUD_DECLINED":
                    processFraudDeclinedAuthorization(authorizationId, cardId, userId, transactionId,
                        amount, currency, merchantId, responseCode, securityFlags, riskScore, correlationId);
                    break;

                case "PENDING":
                    processPendingAuthorization(authorizationId, cardId, userId, transactionId, amount,
                        currency, merchantId, authenticationData, correlationId);
                    break;

                case "TIMEOUT":
                    processTimeoutAuthorization(authorizationId, cardId, userId, transactionId, amount,
                        currency, merchantId, processedAt, correlationId);
                    break;

                case "ERROR":
                    processErrorAuthorization(authorizationId, cardId, userId, transactionId,
                        responseCode, responseMessage, correlationId);
                    break;

                default:
                    processGenericAuthorizationResponse(authorizationId, cardId, userId, responseStatus,
                        transactionId, amount, currency, correlationId);
                    break;
            }

            // Monitor security patterns
            monitorAuthorizationSecurityPatterns(authorizationId, cardId, userId, responseStatus,
                amount, riskScore, securityFlags, deviceId, ipAddress, location, correlationId);

            // Handle high-risk responses
            if (riskScore > 0.8 || "FRAUD_DECLINED".equals(responseStatus)) {
                handleHighRiskAuthorizationResponse(authorizationId, cardId, userId, responseStatus,
                    amount, riskScore, securityFlags, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("CARD_AUTHORIZATION_RESPONSE_PROCESSED", userId,
                Map.of("authorizationId", authorizationId, "cardId", cardId, "responseStatus", responseStatus,
                    "amount", amount, "currency", currency, "merchantId", merchantId,
                    "riskScore", riskScore, "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process card authorization response event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("card-authorization-response-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleCardAuthorizationResponseFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("card-auth-response-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for card authorization response: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("card-authorization-response-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "Card Authorization Response Circuit Breaker Triggered",
                String.format("Card authorization response processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltCardAuthorizationResponse(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-card-auth-response-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - Card authorization response permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String authorizationId = (String) event.get("authorizationId");
            String cardId = (String) event.get("cardId");
            String userId = (String) event.get("userId");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("CARD_AUTHORIZATION_RESPONSE_DLT_EVENT", userId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "authorizationId", authorizationId, "cardId", cardId, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "Card Authorization Response Dead Letter Event",
                String.format("Card authorization response %s for card %s sent to DLT: %s", authorizationId, cardId, exceptionMessage),
                Map.of("authorizationId", authorizationId, "cardId", cardId, "userId", userId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse card authorization response DLT event: {}", eventJson, ex);
        }
    }

    private void processApprovedAuthorization(String authorizationId, String cardId, String userId,
                                            String transactionId, Double amount, String currency,
                                            String merchantId, LocalDateTime processedAt,
                                            Map<String, Object> authenticationData, Double riskScore,
                                            String correlationId) {
        try {
            cardSecurityService.processApprovedAuthorization(authorizationId, cardId, userId,
                transactionId, amount, currency, merchantId, processedAt, authenticationData, riskScore);

            log.info("Approved authorization processed: authId={}, cardId={}, amount={}{}",
                authorizationId, cardId, amount, currency);

        } catch (Exception e) {
            log.error("Failed to process approved authorization: authId={}, cardId={}",
                authorizationId, cardId, e);
            throw new RuntimeException("Approved authorization processing failed", e);
        }
    }

    private void processDeclinedAuthorization(String authorizationId, String cardId, String userId,
                                            String transactionId, Double amount, String currency,
                                            String merchantId, String responseCode, List<String> declineReasons,
                                            Double riskScore, String correlationId) {
        try {
            cardSecurityService.processDeclinedAuthorization(authorizationId, cardId, userId,
                transactionId, amount, currency, merchantId, responseCode, declineReasons, riskScore);

            log.info("Declined authorization processed: authId={}, cardId={}, reasons={}",
                authorizationId, cardId, declineReasons);

        } catch (Exception e) {
            log.error("Failed to process declined authorization: authId={}, cardId={}",
                authorizationId, cardId, e);
            throw new RuntimeException("Declined authorization processing failed", e);
        }
    }

    private void processFraudDeclinedAuthorization(String authorizationId, String cardId, String userId,
                                                 String transactionId, Double amount, String currency,
                                                 String merchantId, String responseCode,
                                                 Map<String, Object> securityFlags, Double riskScore,
                                                 String correlationId) {
        try {
            cardSecurityService.processFraudDeclinedAuthorization(authorizationId, cardId, userId,
                transactionId, amount, currency, merchantId, responseCode, securityFlags, riskScore);

            // Immediate fraud response
            threatResponseService.respondToCardFraud(cardId, userId, authorizationId, amount,
                merchantId, riskScore, securityFlags, correlationId);

            log.error("Fraud declined authorization processed: authId={}, cardId={}, riskScore={}",
                authorizationId, cardId, riskScore);

        } catch (Exception e) {
            log.error("Failed to process fraud declined authorization: authId={}, cardId={}",
                authorizationId, cardId, e);
            throw new RuntimeException("Fraud declined authorization processing failed", e);
        }
    }

    private void processPendingAuthorization(String authorizationId, String cardId, String userId,
                                           String transactionId, Double amount, String currency,
                                           String merchantId, Map<String, Object> authenticationData,
                                           String correlationId) {
        try {
            cardSecurityService.processPendingAuthorization(authorizationId, cardId, userId,
                transactionId, amount, currency, merchantId, authenticationData);

            log.info("Pending authorization processed: authId={}, cardId={}, amount={}{}",
                authorizationId, cardId, amount, currency);

        } catch (Exception e) {
            log.error("Failed to process pending authorization: authId={}, cardId={}",
                authorizationId, cardId, e);
            throw new RuntimeException("Pending authorization processing failed", e);
        }
    }

    private void processTimeoutAuthorization(String authorizationId, String cardId, String userId,
                                           String transactionId, Double amount, String currency,
                                           String merchantId, LocalDateTime processedAt, String correlationId) {
        try {
            cardSecurityService.processTimeoutAuthorization(authorizationId, cardId, userId,
                transactionId, amount, currency, merchantId, processedAt);

            log.warn("Timeout authorization processed: authId={}, cardId={}, amount={}{}",
                authorizationId, cardId, amount, currency);

        } catch (Exception e) {
            log.error("Failed to process timeout authorization: authId={}, cardId={}",
                authorizationId, cardId, e);
            throw new RuntimeException("Timeout authorization processing failed", e);
        }
    }

    private void processErrorAuthorization(String authorizationId, String cardId, String userId,
                                         String transactionId, String responseCode, String responseMessage,
                                         String correlationId) {
        try {
            cardSecurityService.processErrorAuthorization(authorizationId, cardId, userId,
                transactionId, responseCode, responseMessage);

            log.error("Error authorization processed: authId={}, cardId={}, error={}",
                authorizationId, cardId, responseMessage);

        } catch (Exception e) {
            log.error("Failed to process error authorization: authId={}, cardId={}",
                authorizationId, cardId, e);
            throw new RuntimeException("Error authorization processing failed", e);
        }
    }

    private void processGenericAuthorizationResponse(String authorizationId, String cardId, String userId,
                                                   String responseStatus, String transactionId, Double amount,
                                                   String currency, String correlationId) {
        try {
            cardSecurityService.processGenericAuthorizationResponse(authorizationId, cardId, userId,
                responseStatus, transactionId, amount, currency);

            log.info("Generic authorization response processed: authId={}, cardId={}, status={}",
                authorizationId, cardId, responseStatus);

        } catch (Exception e) {
            log.error("Failed to process generic authorization response: authId={}, cardId={}",
                authorizationId, cardId, e);
            throw new RuntimeException("Generic authorization response processing failed", e);
        }
    }

    private void monitorAuthorizationSecurityPatterns(String authorizationId, String cardId, String userId,
                                                    String responseStatus, Double amount, Double riskScore,
                                                    Map<String, Object> securityFlags, String deviceId,
                                                    String ipAddress, String location, String correlationId) {
        try {
            cardSecurityService.monitorAuthorizationSecurityPatterns(authorizationId, cardId, userId,
                responseStatus, amount, riskScore, securityFlags, deviceId, ipAddress, location);

            log.debug("Authorization security patterns monitored: authId={}, cardId={}, riskScore={}",
                authorizationId, cardId, riskScore);

        } catch (Exception e) {
            log.error("Failed to monitor authorization security patterns: authId={}, cardId={}",
                authorizationId, cardId, e);
            // Don't throw exception as pattern monitoring failure shouldn't block processing
        }
    }

    private void handleHighRiskAuthorizationResponse(String authorizationId, String cardId, String userId,
                                                   String responseStatus, Double amount, Double riskScore,
                                                   Map<String, Object> securityFlags, String correlationId) {
        try {
            cardSecurityService.handleHighRiskAuthorizationResponse(authorizationId, cardId, userId,
                responseStatus, amount, riskScore, securityFlags);

            // Send high risk alert
            securityNotificationService.sendCriticalAlert(
                "High Risk Card Authorization",
                String.format("High risk card authorization: %s for card %s with risk score %.2f",
                    responseStatus, cardId, riskScore),
                Map.of("authorizationId", authorizationId, "cardId", cardId, "userId", userId,
                    "riskScore", riskScore, "responseStatus", responseStatus, "correlationId", correlationId)
            );

            log.warn("High risk authorization response handled: authId={}, cardId={}, riskScore={}",
                authorizationId, cardId, riskScore);

        } catch (Exception e) {
            log.error("Failed to handle high risk authorization response: authId={}, cardId={}",
                authorizationId, cardId, e);
            // Don't throw exception as high risk handling failure shouldn't block processing
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
}