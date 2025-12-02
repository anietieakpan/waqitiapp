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
public class BnplInstallmentEventsConsumer {

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
        successCounter = Counter.builder("bnpl_installment_events_processed_total")
            .description("Total number of successfully processed BNPL installment events")
            .register(meterRegistry);
        errorCounter = Counter.builder("bnpl_installment_events_errors_total")
            .description("Total number of BNPL installment event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("bnpl_installment_events_processing_duration")
            .description("Time taken to process BNPL installment events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"bnpl-installment-events", "bnpl-payment-installments", "bnpl-schedule-events"},
        groupId = "security-service-bnpl-installment-events-group",
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
    @CircuitBreaker(name = "bnpl-installment-events", fallbackMethod = "handleBnplInstallmentEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBnplInstallmentEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("bnpl-installment-p%d-o%d", partition, offset);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            String installmentId = (String) event.get("installmentId");
            String userId = (String) event.get("userId");
            String eventType = (String) event.get("eventType");
            String eventKey = String.format("%s-%s-%s", installmentId, userId, event.get("timestamp"));

            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing BNPL installment event: installmentId={}, userId={}, type={}",
                installmentId, userId, eventType);

            // Clean expired entries periodically
            cleanExpiredEntries();

            String bnplLoanId = (String) event.get("bnplLoanId");
            Double installmentAmount = ((Number) event.get("installmentAmount")).doubleValue();
            String currency = (String) event.get("currency");
            LocalDateTime dueDate = LocalDateTime.parse((String) event.get("dueDate"));
            String status = (String) event.get("status");
            LocalDateTime eventTime = LocalDateTime.parse((String) event.get("eventTime"));
            String paymentMethodId = (String) event.get("paymentMethodId");
            @SuppressWarnings("unchecked")
            Map<String, Object> riskMetrics = (Map<String, Object>) event.getOrDefault("riskMetrics", Map.of());
            String merchantId = (String) event.get("merchantId");
            Integer installmentNumber = (Integer) event.get("installmentNumber");
            Integer totalInstallments = (Integer) event.get("totalInstallments");
            String failureReason = (String) event.get("failureReason");
            Boolean isOverdue = (Boolean) event.getOrDefault("isOverdue", false);
            Integer daysPastDue = (Integer) event.getOrDefault("daysPastDue", 0);

            // Process BNPL installment event based on type
            switch (eventType) {
                case "INSTALLMENT_SCHEDULED":
                    processInstallmentScheduled(installmentId, userId, bnplLoanId, installmentAmount,
                        currency, dueDate, installmentNumber, totalInstallments, correlationId);
                    break;

                case "INSTALLMENT_PAYMENT_ATTEMPT":
                    processInstallmentPaymentAttempt(installmentId, userId, bnplLoanId,
                        installmentAmount, currency, paymentMethodId, eventTime, correlationId);
                    break;

                case "INSTALLMENT_PAID":
                    processInstallmentPaid(installmentId, userId, bnplLoanId, installmentAmount,
                        currency, eventTime, installmentNumber, totalInstallments, correlationId);
                    break;

                case "INSTALLMENT_FAILED":
                    processInstallmentFailed(installmentId, userId, bnplLoanId, installmentAmount,
                        currency, failureReason, eventTime, correlationId);
                    break;

                case "INSTALLMENT_OVERDUE":
                    processInstallmentOverdue(installmentId, userId, bnplLoanId, installmentAmount,
                        currency, dueDate, daysPastDue, correlationId);
                    break;

                case "INSTALLMENT_RESCHEDULED":
                    processInstallmentRescheduled(installmentId, userId, bnplLoanId,
                        installmentAmount, currency, dueDate, eventTime, correlationId);
                    break;

                case "INSTALLMENT_CANCELLED":
                    processInstallmentCancelled(installmentId, userId, bnplLoanId,
                        installmentAmount, currency, eventTime, correlationId);
                    break;

                default:
                    processGenericInstallmentEvent(installmentId, userId, eventType, bnplLoanId,
                        installmentAmount, currency, correlationId);
                    break;
            }

            // Security monitoring for BNPL patterns
            monitorSecurityPatterns(userId, bnplLoanId, installmentAmount, eventType, status,
                riskMetrics, correlationId);

            // Handle overdue installments
            if (isOverdue && daysPastDue > 0) {
                handleOverdueInstallment(installmentId, userId, bnplLoanId, daysPastDue,
                    installmentAmount, correlationId);
            }

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logSecurityEvent("BNPL_INSTALLMENT_EVENT_PROCESSED", userId,
                Map.of("installmentId", installmentId, "eventType", eventType, "bnplLoanId", bnplLoanId,
                    "installmentAmount", installmentAmount, "status", status, "isOverdue", isOverdue,
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process BNPL installment event: {}", e.getMessage(), e);

            // Send fallback event
            kafkaTemplate.send("bnpl-installment-events-fallback-events", Map.of(
                "originalEvent", eventJson, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleBnplInstallmentEventFallback(
            String eventJson,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("bnpl-installment-fallback-p%d-o%d", partition, offset);

        log.error("Circuit breaker fallback triggered for BNPL installment event: error={}", ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("bnpl-installment-events-dlq", Map.of(
            "originalEvent", eventJson,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send notification to operations team
        try {
            notificationService.sendOperationalAlert(
                "BNPL Installment Event Circuit Breaker Triggered",
                String.format("BNPL installment event processing failed: %s", ex.getMessage()),
                "HIGH"
            );
        } catch (Exception notificationEx) {
            log.error("Failed to send operational alert: {}", notificationEx.getMessage());
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBnplInstallmentEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-bnpl-installment-%d", System.currentTimeMillis());

        log.error("Dead letter topic handler - BNPL installment event permanently failed: topic={}, error={}",
            topic, exceptionMessage);

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            String installmentId = (String) event.get("installmentId");
            String userId = (String) event.get("userId");
            String eventType = (String) event.get("eventType");

            // Save to dead letter store for manual investigation
            auditService.logSecurityEvent("BNPL_INSTALLMENT_EVENT_DLT_EVENT", userId,
                Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                    "installmentId", installmentId, "eventType", eventType, "correlationId", correlationId,
                    "requiresManualIntervention", true, "timestamp", Instant.now()));

            // Send critical alert
            securityNotificationService.sendCriticalAlert(
                "BNPL Installment Event Dead Letter Event",
                String.format("BNPL installment event %s for user %s sent to DLT: %s", installmentId, userId, exceptionMessage),
                Map.of("installmentId", installmentId, "userId", userId, "topic", topic, "correlationId", correlationId)
            );

        } catch (Exception ex) {
            log.error("Failed to parse BNPL installment event DLT event: {}", eventJson, ex);
        }
    }

    private void processInstallmentScheduled(String installmentId, String userId, String bnplLoanId,
                                           Double installmentAmount, String currency, LocalDateTime dueDate,
                                           Integer installmentNumber, Integer totalInstallments,
                                           String correlationId) {
        try {
            bnplSecurityService.processInstallmentScheduled(installmentId, userId, bnplLoanId,
                installmentAmount, currency, dueDate, installmentNumber, totalInstallments);

            log.info("Installment scheduled processed: installmentId={}, userId={}, amount={}",
                installmentId, userId, installmentAmount);

        } catch (Exception e) {
            log.error("Failed to process installment scheduled: installmentId={}, userId={}",
                installmentId, userId, e);
            throw new RuntimeException("Installment scheduled processing failed", e);
        }
    }

    private void processInstallmentPaymentAttempt(String installmentId, String userId,
                                                String bnplLoanId, Double installmentAmount,
                                                String currency, String paymentMethodId,
                                                LocalDateTime eventTime, String correlationId) {
        try {
            bnplSecurityService.processInstallmentPaymentAttempt(installmentId, userId,
                bnplLoanId, installmentAmount, currency, paymentMethodId, eventTime);

            log.info("Installment payment attempt processed: installmentId={}, userId={}, amount={}",
                installmentId, userId, installmentAmount);

        } catch (Exception e) {
            log.error("Failed to process installment payment attempt: installmentId={}, userId={}",
                installmentId, userId, e);
            throw new RuntimeException("Installment payment attempt processing failed", e);
        }
    }

    private void processInstallmentPaid(String installmentId, String userId, String bnplLoanId,
                                      Double installmentAmount, String currency, LocalDateTime eventTime,
                                      Integer installmentNumber, Integer totalInstallments,
                                      String correlationId) {
        try {
            bnplSecurityService.processInstallmentPaid(installmentId, userId, bnplLoanId,
                installmentAmount, currency, eventTime, installmentNumber, totalInstallments);

            log.info("Installment paid processed: installmentId={}, userId={}, amount={}",
                installmentId, userId, installmentAmount);

        } catch (Exception e) {
            log.error("Failed to process installment paid: installmentId={}, userId={}",
                installmentId, userId, e);
            throw new RuntimeException("Installment paid processing failed", e);
        }
    }

    private void processInstallmentFailed(String installmentId, String userId, String bnplLoanId,
                                        Double installmentAmount, String currency, String failureReason,
                                        LocalDateTime eventTime, String correlationId) {
        try {
            bnplSecurityService.processInstallmentFailed(installmentId, userId, bnplLoanId,
                installmentAmount, currency, failureReason, eventTime);

            // Alert for potential fraud patterns
            threatResponseService.analyzeInstallmentFailurePattern(userId, bnplLoanId,
                failureReason, correlationId);

            log.info("Installment failed processed: installmentId={}, userId={}, reason={}",
                installmentId, userId, failureReason);

        } catch (Exception e) {
            log.error("Failed to process installment failed: installmentId={}, userId={}",
                installmentId, userId, e);
            throw new RuntimeException("Installment failed processing failed", e);
        }
    }

    private void processInstallmentOverdue(String installmentId, String userId, String bnplLoanId,
                                         Double installmentAmount, String currency, LocalDateTime dueDate,
                                         Integer daysPastDue, String correlationId) {
        try {
            bnplSecurityService.processInstallmentOverdue(installmentId, userId, bnplLoanId,
                installmentAmount, currency, dueDate, daysPastDue);

            log.info("Installment overdue processed: installmentId={}, userId={}, daysPastDue={}",
                installmentId, userId, daysPastDue);

        } catch (Exception e) {
            log.error("Failed to process installment overdue: installmentId={}, userId={}",
                installmentId, userId, e);
            throw new RuntimeException("Installment overdue processing failed", e);
        }
    }

    private void processInstallmentRescheduled(String installmentId, String userId, String bnplLoanId,
                                             Double installmentAmount, String currency,
                                             LocalDateTime newDueDate, LocalDateTime eventTime,
                                             String correlationId) {
        try {
            bnplSecurityService.processInstallmentRescheduled(installmentId, userId, bnplLoanId,
                installmentAmount, currency, newDueDate, eventTime);

            log.info("Installment rescheduled processed: installmentId={}, userId={}, newDueDate={}",
                installmentId, userId, newDueDate);

        } catch (Exception e) {
            log.error("Failed to process installment rescheduled: installmentId={}, userId={}",
                installmentId, userId, e);
            throw new RuntimeException("Installment rescheduled processing failed", e);
        }
    }

    private void processInstallmentCancelled(String installmentId, String userId, String bnplLoanId,
                                           Double installmentAmount, String currency,
                                           LocalDateTime eventTime, String correlationId) {
        try {
            bnplSecurityService.processInstallmentCancelled(installmentId, userId, bnplLoanId,
                installmentAmount, currency, eventTime);

            log.info("Installment cancelled processed: installmentId={}, userId={}, amount={}",
                installmentId, userId, installmentAmount);

        } catch (Exception e) {
            log.error("Failed to process installment cancelled: installmentId={}, userId={}",
                installmentId, userId, e);
            throw new RuntimeException("Installment cancelled processing failed", e);
        }
    }

    private void processGenericInstallmentEvent(String installmentId, String userId, String eventType,
                                              String bnplLoanId, Double installmentAmount,
                                              String currency, String correlationId) {
        try {
            bnplSecurityService.processGenericInstallmentEvent(installmentId, userId, eventType,
                bnplLoanId, installmentAmount, currency);

            log.info("Generic installment event processed: installmentId={}, userId={}, type={}",
                installmentId, userId, eventType);

        } catch (Exception e) {
            log.error("Failed to process generic installment event: installmentId={}, userId={}",
                installmentId, userId, e);
            throw new RuntimeException("Generic installment event processing failed", e);
        }
    }

    private void monitorSecurityPatterns(String userId, String bnplLoanId, Double installmentAmount,
                                       String eventType, String status, Map<String, Object> riskMetrics,
                                       String correlationId) {
        try {
            bnplSecurityService.monitorSecurityPatterns(userId, bnplLoanId, installmentAmount,
                eventType, status, riskMetrics);

            log.debug("Security patterns monitored: userId={}, bnplLoanId={}, eventType={}",
                userId, bnplLoanId, eventType);

        } catch (Exception e) {
            log.error("Failed to monitor security patterns: userId={}, bnplLoanId={}",
                userId, bnplLoanId, e);
            // Don't throw exception as pattern monitoring failure shouldn't block processing
        }
    }

    private void handleOverdueInstallment(String installmentId, String userId, String bnplLoanId,
                                        Integer daysPastDue, Double installmentAmount,
                                        String correlationId) {
        try {
            bnplSecurityService.handleOverdueInstallment(installmentId, userId, bnplLoanId,
                daysPastDue, installmentAmount);

            // Send overdue notification
            securityNotificationService.notifyUserOverdueInstallment(userId, installmentId,
                installmentAmount, daysPastDue);

            log.warn("Overdue installment handled: installmentId={}, userId={}, daysPastDue={}",
                installmentId, userId, daysPastDue);

        } catch (Exception e) {
            log.error("Failed to handle overdue installment: installmentId={}, userId={}",
                installmentId, userId, e);
            // Don't throw exception as overdue handling failure shouldn't block processing
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