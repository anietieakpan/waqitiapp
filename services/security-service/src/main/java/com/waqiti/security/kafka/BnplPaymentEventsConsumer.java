package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.service.BnplSecurityService;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class BnplPaymentEventsConsumer {

    private final BnplSecurityService bnplSecurityService;
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
        successCounter = Counter.builder("bnpl_payment_events_processed_total")
            .description("Total number of successfully processed BNPL payment events")
            .register(meterRegistry);
        errorCounter = Counter.builder("bnpl_payment_events_errors_total")
            .description("Total number of BNPL payment event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("bnpl_payment_events_processing_duration")
            .description("Time taken to process BNPL payment events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"bnpl-payment-events", "bnpl-transactions", "bnpl-security-events"},
        groupId = "security-service-bnpl-payment-events-group",
        containerFactory = "criticalSecurityKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "bnpl-payment-events", fallbackMethod = "handleBnplPaymentEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBnplPaymentEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("bnpl-payment-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String paymentId = (String) event.get("paymentId");
            String userId = (String) event.get("userId");
            String eventType = (String) event.get("eventType");
            String eventKey = String.format("%s-%s-%s", paymentId, userId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing BNPL payment event: paymentId={}, userId={}, type={}",
                paymentId, userId, eventType);

            // Clean expired entries periodically
            cleanExpiredEntries();

            String bnplLoanId = (String) event.get("bnplLoanId");
            Double amount = ((Number) event.get("amount")).doubleValue();
            String currency = (String) event.get("currency");
            String merchantId = (String) event.get("merchantId");
            String paymentMethodId = (String) event.get("paymentMethodId");
            LocalDateTime eventTime = LocalDateTime.parse((String) event.get("eventTime"));
            String status = (String) event.get("status");
            @SuppressWarnings("unchecked")
            Map<String, Object> securityFlags = (Map<String, Object>) event.getOrDefault("securityFlags", Map.of());
            String deviceId = (String) event.get("deviceId");
            String ipAddress = (String) event.get("ipAddress");
            String location = (String) event.get("location");
            Double riskScore = ((Number) event.getOrDefault("riskScore", 0.0)).doubleValue();

            // Process BNPL payment event based on type
            switch (eventType) {
                case "BNPL_LOAN_CREATED":
                    processBnplLoanCreated(paymentId, userId, bnplLoanId, amount, currency,
                        merchantId, deviceId, ipAddress, location, riskScore, correlationId);
                    break;

                case "BNPL_PAYMENT_AUTHORIZED":
                    processBnplPaymentAuthorized(paymentId, userId, bnplLoanId, amount, currency,
                        merchantId, paymentMethodId, eventTime, correlationId);
                    break;

                case "BNPL_PAYMENT_DECLINED":
                    processBnplPaymentDeclined(paymentId, userId, bnplLoanId, amount, currency,
                        merchantId, eventTime, correlationId);
                    break;

                case "BNPL_PAYMENT_COMPLETED":
                    processBnplPaymentCompleted(paymentId, userId, bnplLoanId, amount, currency,
                        merchantId, eventTime, correlationId);
                    break;

                case "BNPL_LOAN_DEFAULTED":
                    processBnplLoanDefaulted(paymentId, userId, bnplLoanId, amount, currency,
                        eventTime, correlationId);
                    break;

                case "BNPL_FRAUD_DETECTED":
                    processBnplFraudDetected(paymentId, userId, bnplLoanId, amount, currency,
                        merchantId, securityFlags, riskScore, correlationId);
                    break;

                case "BNPL_CHARGEBACK":
                    processBnplChargeback(paymentId, userId, bnplLoanId, amount, currency,
                        merchantId, eventTime, correlationId);
                    break;

                default:
                    processGenericBnplPaymentEvent(paymentId, userId, eventType, bnplLoanId,
                        amount, currency, correlationId);
                    break;
            }

            // Monitor security patterns
            monitorBnplSecurityPatterns(userId, bnplLoanId, amount, eventType, riskScore,
                securityFlags, deviceId, ipAddress, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("BNPL_PAYMENT_EVENT_PROCESSED", userId,
                Map.of("paymentId", paymentId, "eventType", eventType, "bnplLoanId", bnplLoanId,
                    "amount", amount, "status", status, "riskScore", riskScore,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process BNPL payment event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("bnpl-payment-events-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBnplPaymentEventFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("bnpl-payment-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for BNPL payment event: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("bnpl-payment-events-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "BNPL Payment Event Circuit Breaker Triggered",
                String.format("BNPL payment event processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBnplPaymentEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-bnpl-payment-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - BNPL payment event permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String paymentId = (String) event.get("paymentId");
            String userId = (String) event.get("userId");
            String eventType = (String) event.get("eventType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("BNPL_PAYMENT_EVENT_DLT_EVENT", userId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "paymentId", paymentId, "eventType", eventType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "BNPL Payment Event Dead Letter Event",
                String.format("BNPL payment event %s for user %s sent to DLT: %s", paymentId, userId, exceptionMessage),
                Map.of("paymentId", paymentId, "userId", userId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse BNPL payment event DLT event: {}", eventJson, ex);
        }
    }

    private void processBnplLoanCreated(String paymentId, String userId, String bnplLoanId,
                                      Double amount, String currency, String merchantId,
                                      String deviceId, String ipAddress, String location,
                                      Double riskScore, String correlationId) {
        try {
            bnplSecurityService.processBnplLoanCreated(paymentId, userId, bnplLoanId, amount,
                currency, merchantId, deviceId, ipAddress, location, riskScore);

            log.info("BNPL loan created processed: paymentId={}, userId={}, amount={}",
                paymentId, userId, amount);

        } catch (Exception e) {
            log.error("Failed to process BNPL loan created: paymentId={}, userId={}",
                paymentId, userId, e);
            throw new RuntimeException("BNPL loan created processing failed", e);
        }
    }

    private void processBnplPaymentAuthorized(String paymentId, String userId, String bnplLoanId,
                                            Double amount, String currency, String merchantId,
                                            String paymentMethodId, LocalDateTime eventTime,
                                            String correlationId) {
        try {
            bnplSecurityService.processBnplPaymentAuthorized(paymentId, userId, bnplLoanId,
                amount, currency, merchantId, paymentMethodId, eventTime);

            log.info("BNPL payment authorized processed: paymentId={}, userId={}, amount={}",
                paymentId, userId, amount);

        } catch (Exception e) {
            log.error("Failed to process BNPL payment authorized: paymentId={}, userId={}",
                paymentId, userId, e);
            throw new RuntimeException("BNPL payment authorized processing failed", e);
        }
    }

    private void processBnplPaymentDeclined(String paymentId, String userId, String bnplLoanId,
                                          Double amount, String currency, String merchantId,
                                          LocalDateTime eventTime, String correlationId) {
        try {
            bnplSecurityService.processBnplPaymentDeclined(paymentId, userId, bnplLoanId,
                amount, currency, merchantId, eventTime);

            log.info("BNPL payment declined processed: paymentId={}, userId={}, amount={}",
                paymentId, userId, amount);

        } catch (Exception e) {
            log.error("Failed to process BNPL payment declined: paymentId={}, userId={}",
                paymentId, userId, e);
            throw new RuntimeException("BNPL payment declined processing failed", e);
        }
    }

    private void processBnplPaymentCompleted(String paymentId, String userId, String bnplLoanId,
                                           Double amount, String currency, String merchantId,
                                           LocalDateTime eventTime, String correlationId) {
        try {
            bnplSecurityService.processBnplPaymentCompleted(paymentId, userId, bnplLoanId,
                amount, currency, merchantId, eventTime);

            log.info("BNPL payment completed processed: paymentId={}, userId={}, amount={}",
                paymentId, userId, amount);

        } catch (Exception e) {
            log.error("Failed to process BNPL payment completed: paymentId={}, userId={}",
                paymentId, userId, e);
            throw new RuntimeException("BNPL payment completed processing failed", e);
        }
    }

    private void processBnplLoanDefaulted(String paymentId, String userId, String bnplLoanId,
                                        Double amount, String currency, LocalDateTime eventTime,
                                        String correlationId) {
        try {
            bnplSecurityService.processBnplLoanDefaulted(paymentId, userId, bnplLoanId,
                amount, currency, eventTime);

            // Alert security team about default
            threatResponseService.handleBnplLoanDefault(userId, bnplLoanId, amount, correlationId);

            log.warn("BNPL loan defaulted processed: paymentId={}, userId={}, amount={}",
                paymentId, userId, amount);

        } catch (Exception e) {
            log.error("Failed to process BNPL loan defaulted: paymentId={}, userId={}",
                paymentId, userId, e);
            throw new RuntimeException("BNPL loan defaulted processing failed", e);
        }
    }

    private void processBnplFraudDetected(String paymentId, String userId, String bnplLoanId,
                                        Double amount, String currency, String merchantId,
                                        Map<String, Object> securityFlags, Double riskScore,
                                        String correlationId) {
        try {
            bnplSecurityService.processBnplFraudDetected(paymentId, userId, bnplLoanId,
                amount, currency, merchantId, securityFlags, riskScore);

            // Immediate fraud response
            threatResponseService.respondToBnplFraud(userId, bnplLoanId, paymentId, amount,
                riskScore, securityFlags, correlationId);

            log.error("BNPL fraud detected processed: paymentId={}, userId={}, riskScore={}",
                paymentId, userId, riskScore);

        } catch (Exception e) {
            log.error("Failed to process BNPL fraud detected: paymentId={}, userId={}",
                paymentId, userId, e);
            throw new RuntimeException("BNPL fraud detected processing failed", e);
        }
    }

    private void processBnplChargeback(String paymentId, String userId, String bnplLoanId,
                                     Double amount, String currency, String merchantId,
                                     LocalDateTime eventTime, String correlationId) {
        try {
            bnplSecurityService.processBnplChargeback(paymentId, userId, bnplLoanId,
                amount, currency, merchantId, eventTime);

            log.warn("BNPL chargeback processed: paymentId={}, userId={}, amount={}",
                paymentId, userId, amount);

        } catch (Exception e) {
            log.error("Failed to process BNPL chargeback: paymentId={}, userId={}",
                paymentId, userId, e);
            throw new RuntimeException("BNPL chargeback processing failed", e);
        }
    }

    private void processGenericBnplPaymentEvent(String paymentId, String userId, String eventType,
                                              String bnplLoanId, Double amount, String currency,
                                              String correlationId) {
        try {
            bnplSecurityService.processGenericBnplPaymentEvent(paymentId, userId, eventType,
                bnplLoanId, amount, currency);

            log.info("Generic BNPL payment event processed: paymentId={}, userId={}, type={}",
                paymentId, userId, eventType);

        } catch (Exception e) {
            log.error("Failed to process generic BNPL payment event: paymentId={}, userId={}",
                paymentId, userId, e);
            throw new RuntimeException("Generic BNPL payment event processing failed", e);
        }
    }

    private void monitorBnplSecurityPatterns(String userId, String bnplLoanId, Double amount,
                                           String eventType, Double riskScore,
                                           Map<String, Object> securityFlags, String deviceId,
                                           String ipAddress, String correlationId) {
        try {
            bnplSecurityService.monitorBnplSecurityPatterns(userId, bnplLoanId, amount,
                eventType, riskScore, securityFlags, deviceId, ipAddress);

            log.debug("BNPL security patterns monitored: userId={}, bnplLoanId={}, riskScore={}",
                userId, bnplLoanId, riskScore);

        } catch (Exception e) {
            log.error("Failed to monitor BNPL security patterns: userId={}, bnplLoanId={}",
                userId, bnplLoanId, e);
            // Don't throw exception as pattern monitoring failure shouldn't block processing
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