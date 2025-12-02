package com.waqiti.payment.kafka;

import com.waqiti.common.events.BankIntegrationEvent;
import com.waqiti.payment.domain.BankIntegrationLog;
import com.waqiti.payment.repository.BankIntegrationLogRepository;
import com.waqiti.payment.service.BankIntegrationService;
import com.waqiti.payment.service.ConnectivityService;
import com.waqiti.payment.metrics.PaymentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.DltHandler;
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
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class BankIntegrationEventsConsumer {

    private final BankIntegrationLogRepository bankIntegrationLogRepository;
    private final BankIntegrationService bankIntegrationService;
    private final ConnectivityService connectivityService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000;

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("bank_integration_events_processed_total")
            .description("Total number of successfully processed bank integration events")
            .register(meterRegistry);
        errorCounter = Counter.builder("bank_integration_events_errors_total")
            .description("Total number of bank integration event processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("bank_integration_events_processing_duration")
            .description("Time taken to process bank integration events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"bank-integration-events", "bank-connectivity-status", "external-bank-notifications"},
        groupId = "bank-integration-events-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    @CircuitBreaker(name = "bank-integration-events", fallbackMethod = "handleBankIntegrationEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleBankIntegrationEvent(
            @Payload BankIntegrationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("bank-integration-%s-p%d-o%d", event.getBankCode(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getBankCode(), event.getEventType(), event.getTimestamp());

        try {
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing bank integration event: bank={}, type={}, status={}",
                event.getBankCode(), event.getEventType(), event.getStatus());

            cleanExpiredEntries();

            switch (event.getEventType()) {
                case CONNECTION_ESTABLISHED:
                    processConnectionEstablished(event, correlationId);
                    break;

                case CONNECTION_LOST:
                    processConnectionLost(event, correlationId);
                    break;

                case CONNECTION_RESTORED:
                    processConnectionRestored(event, correlationId);
                    break;

                case API_RATE_LIMIT_REACHED:
                    processApiRateLimitReached(event, correlationId);
                    break;

                case AUTHENTICATION_FAILED:
                    processAuthenticationFailed(event, correlationId);
                    break;

                case AUTHENTICATION_RENEWED:
                    processAuthenticationRenewed(event, correlationId);
                    break;

                case TRANSACTION_REJECTED:
                    processTransactionRejected(event, correlationId);
                    break;

                case BANK_MAINTENANCE_SCHEDULED:
                    processBankMaintenanceScheduled(event, correlationId);
                    break;

                case BANK_MAINTENANCE_COMPLETED:
                    processBankMaintenanceCompleted(event, correlationId);
                    break;

                case ERROR_THRESHOLD_EXCEEDED:
                    processErrorThresholdExceeded(event, correlationId);
                    break;

                default:
                    log.warn("Unknown bank integration event type: {}", event.getEventType());
                    break;
            }

            markEventAsProcessed(eventKey);

            auditService.logPaymentEvent("BANK_INTEGRATION_EVENT_PROCESSED", event.getBankCode(),
                Map.of("eventType", event.getEventType(), "status", event.getStatus(),
                    "responseTime", event.getResponseTime(), "errorCode", event.getErrorCode(),
                    "correlationId", correlationId, "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process bank integration event: {}", e.getMessage(), e);

            kafkaTemplate.send("bank-integration-events-fallback-events", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now(),
                "retryCount", 0, "maxRetries", 3));

            acknowledgment.acknowledge();
            throw e;
        } finally {
            processingTimer.stop(sample);
        }
    }

    public void handleBankIntegrationEventFallback(
            BankIntegrationEvent event,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        String correlationId = String.format("bank-integration-fallback-%s-p%d-o%d", event.getBankCode(), partition, offset);

        log.error("Circuit breaker fallback triggered for bank integration: bank={}, error={}",
            event.getBankCode(), ex.getMessage());

        kafkaTemplate.send("bank-integration-events-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltBankIntegrationEvent(
            @Payload BankIntegrationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        String correlationId = String.format("dlt-bank-integration-%s-%d", event.getBankCode(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Bank integration event permanently failed: bank={}, error={}",
            event.getBankCode(), exceptionMessage);

        auditService.logPaymentEvent("BANK_INTEGRATION_DLT_EVENT", event.getBankCode(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "eventType", event.getEventType(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));
    }

    private boolean isEventProcessed(String eventKey) {
        Long timestamp = processedEvents.get(eventKey);
        if (timestamp == null) return false;
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
        if (processedEvents.size() > 1000) {
            long currentTime = System.currentTimeMillis();
            processedEvents.entrySet().removeIf(entry ->
                currentTime - entry.getValue() > TTL_24_HOURS);
        }
    }

    private void processConnectionEstablished(BankIntegrationEvent event, String correlationId) {
        // Log successful connection
        BankIntegrationLog log = BankIntegrationLog.builder()
            .bankCode(event.getBankCode())
            .eventType("CONNECTION_ESTABLISHED")
            .status("SUCCESS")
            .responseTime(event.getResponseTime())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        bankIntegrationLogRepository.save(log);

        // Update connection status
        connectivityService.updateBankConnectionStatus(event.getBankCode(), "CONNECTED", correlationId);

        // Resume any pending transactions for this bank
        bankIntegrationService.resumePendingTransactions(event.getBankCode(), correlationId);

        // Record metrics
        metricsService.recordBankConnectionEstablished(event.getBankCode(), event.getResponseTime());

        // Send operational notification
        notificationService.sendOperationalAlert(
            "Bank Connection Established",
            String.format("Connection to %s established successfully", event.getBankCode()),
            "INFO"
        );

        log.info("Bank connection established: bank={}, responseTime={}ms",
            event.getBankCode(), event.getResponseTime());
    }

    private void processConnectionLost(BankIntegrationEvent event, String correlationId) {
        // Log connection loss
        BankIntegrationLog log = BankIntegrationLog.builder()
            .bankCode(event.getBankCode())
            .eventType("CONNECTION_LOST")
            .status("FAILED")
            .errorCode(event.getErrorCode())
            .errorMessage(event.getErrorMessage())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        bankIntegrationLogRepository.save(log);

        // Update connection status
        connectivityService.updateBankConnectionStatus(event.getBankCode(), "DISCONNECTED", correlationId);

        // Activate failover mechanisms
        bankIntegrationService.activateFailover(event.getBankCode(), correlationId);

        // Queue pending transactions
        bankIntegrationService.queuePendingTransactions(event.getBankCode(), correlationId);

        // Record metrics
        metricsService.recordBankConnectionLost(event.getBankCode());

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Bank Connection Lost",
            String.format("Connection to %s has been lost: %s", event.getBankCode(), event.getErrorMessage()),
            Map.of("bankCode", event.getBankCode(), "errorCode", event.getErrorCode(), "correlationId", correlationId)
        );

        log.error("Bank connection lost: bank={}, error={}", event.getBankCode(), event.getErrorMessage());
    }

    private void processConnectionRestored(BankIntegrationEvent event, String correlationId) {
        // Log connection restoration
        BankIntegrationLog log = BankIntegrationLog.builder()
            .bankCode(event.getBankCode())
            .eventType("CONNECTION_RESTORED")
            .status("SUCCESS")
            .responseTime(event.getResponseTime())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        bankIntegrationLogRepository.save(log);

        // Update connection status
        connectivityService.updateBankConnectionStatus(event.getBankCode(), "CONNECTED", correlationId);

        // Deactivate failover mechanisms
        bankIntegrationService.deactivateFailover(event.getBankCode(), correlationId);

        // Process queued transactions
        bankIntegrationService.processQueuedTransactions(event.getBankCode(), correlationId);

        // Record metrics
        metricsService.recordBankConnectionRestored(event.getBankCode(), event.getDowntimeDuration());

        // Send notification
        notificationService.sendOperationalAlert(
            "Bank Connection Restored",
            String.format("Connection to %s has been restored after %d minutes downtime",
                event.getBankCode(), event.getDowntimeDuration()),
            "INFO"
        );

        log.info("Bank connection restored: bank={}, downtime={}min",
            event.getBankCode(), event.getDowntimeDuration());
    }

    private void processApiRateLimitReached(BankIntegrationEvent event, String correlationId) {
        // Log rate limit event
        BankIntegrationLog log = BankIntegrationLog.builder()
            .bankCode(event.getBankCode())
            .eventType("API_RATE_LIMIT_REACHED")
            .status("THROTTLED")
            .rateLimitRemaining(event.getRateLimitRemaining())
            .rateLimitResetTime(event.getRateLimitResetTime())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        bankIntegrationLogRepository.save(log);

        // Implement rate limiting strategy
        bankIntegrationService.handleRateLimit(
            event.getBankCode(),
            event.getRateLimitRemaining(),
            event.getRateLimitResetTime(),
            correlationId
        );

        // Record metrics
        metricsService.recordApiRateLimitReached(event.getBankCode());

        // Send operational alert
        notificationService.sendOperationalAlert(
            "Bank API Rate Limit Reached",
            String.format("Rate limit reached for %s. Reset at: %s",
                event.getBankCode(), event.getRateLimitResetTime()),
            "MEDIUM"
        );

        log.warn("API rate limit reached: bank={}, remaining={}, resetTime={}",
            event.getBankCode(), event.getRateLimitRemaining(), event.getRateLimitResetTime());
    }

    private void processAuthenticationFailed(BankIntegrationEvent event, String correlationId) {
        // Log authentication failure
        BankIntegrationLog log = BankIntegrationLog.builder()
            .bankCode(event.getBankCode())
            .eventType("AUTHENTICATION_FAILED")
            .status("FAILED")
            .errorCode(event.getErrorCode())
            .errorMessage(event.getErrorMessage())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        bankIntegrationLogRepository.save(log);

        // Handle authentication failure
        bankIntegrationService.handleAuthenticationFailure(event.getBankCode(), correlationId);

        // Attempt to renew authentication
        bankIntegrationService.renewAuthentication(event.getBankCode(), correlationId);

        // Record metrics
        metricsService.recordAuthenticationFailure(event.getBankCode());

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Bank Authentication Failed",
            String.format("Authentication failed for %s: %s", event.getBankCode(), event.getErrorMessage()),
            Map.of("bankCode", event.getBankCode(), "errorCode", event.getErrorCode(), "correlationId", correlationId)
        );

        log.error("Bank authentication failed: bank={}, error={}", event.getBankCode(), event.getErrorMessage());
    }

    private void processAuthenticationRenewed(BankIntegrationEvent event, String correlationId) {
        // Log authentication renewal
        BankIntegrationLog log = BankIntegrationLog.builder()
            .bankCode(event.getBankCode())
            .eventType("AUTHENTICATION_RENEWED")
            .status("SUCCESS")
            .tokenExpiryTime(event.getTokenExpiryTime())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        bankIntegrationLogRepository.save(log);

        // Update authentication status
        bankIntegrationService.updateAuthenticationStatus(event.getBankCode(), "AUTHENTICATED", correlationId);

        // Resume operations
        bankIntegrationService.resumeOperations(event.getBankCode(), correlationId);

        // Record metrics
        metricsService.recordAuthenticationRenewed(event.getBankCode());

        log.info("Bank authentication renewed: bank={}, expiryTime={}",
            event.getBankCode(), event.getTokenExpiryTime());
    }

    private void processTransactionRejected(BankIntegrationEvent event, String correlationId) {
        // Log transaction rejection
        BankIntegrationLog log = BankIntegrationLog.builder()
            .bankCode(event.getBankCode())
            .eventType("TRANSACTION_REJECTED")
            .status("REJECTED")
            .transactionId(event.getTransactionId())
            .errorCode(event.getErrorCode())
            .errorMessage(event.getErrorMessage())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        bankIntegrationLogRepository.save(log);

        // Handle transaction rejection
        bankIntegrationService.handleTransactionRejection(
            event.getBankCode(),
            event.getTransactionId(),
            event.getErrorCode(),
            event.getErrorMessage(),
            correlationId
        );

        // Record metrics
        metricsService.recordTransactionRejected(event.getBankCode(), event.getErrorCode());

        log.warn("Transaction rejected: bank={}, transactionId={}, error={}",
            event.getBankCode(), event.getTransactionId(), event.getErrorMessage());
    }

    private void processBankMaintenanceScheduled(BankIntegrationEvent event, String correlationId) {
        // Log maintenance schedule
        BankIntegrationLog log = BankIntegrationLog.builder()
            .bankCode(event.getBankCode())
            .eventType("BANK_MAINTENANCE_SCHEDULED")
            .status("SCHEDULED")
            .maintenanceStartTime(event.getMaintenanceStartTime())
            .maintenanceEndTime(event.getMaintenanceEndTime())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        bankIntegrationLogRepository.save(log);

        // Prepare for maintenance
        bankIntegrationService.prepareForMaintenance(
            event.getBankCode(),
            event.getMaintenanceStartTime(),
            event.getMaintenanceEndTime(),
            correlationId
        );

        // Send notification
        notificationService.sendOperationalAlert(
            "Bank Maintenance Scheduled",
            String.format("Maintenance scheduled for %s from %s to %s",
                event.getBankCode(), event.getMaintenanceStartTime(), event.getMaintenanceEndTime()),
            "INFO"
        );

        log.info("Bank maintenance scheduled: bank={}, start={}, end={}",
            event.getBankCode(), event.getMaintenanceStartTime(), event.getMaintenanceEndTime());
    }

    private void processBankMaintenanceCompleted(BankIntegrationEvent event, String correlationId) {
        // Log maintenance completion
        BankIntegrationLog log = BankIntegrationLog.builder()
            .bankCode(event.getBankCode())
            .eventType("BANK_MAINTENANCE_COMPLETED")
            .status("COMPLETED")
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        bankIntegrationLogRepository.save(log);

        // Resume normal operations
        bankIntegrationService.resumeAfterMaintenance(event.getBankCode(), correlationId);

        // Test connectivity
        connectivityService.testBankConnectivity(event.getBankCode(), correlationId);

        // Process any queued transactions
        bankIntegrationService.processQueuedTransactions(event.getBankCode(), correlationId);

        log.info("Bank maintenance completed: bank={}", event.getBankCode());
    }

    private void processErrorThresholdExceeded(BankIntegrationEvent event, String correlationId) {
        // Log error threshold event
        BankIntegrationLog log = BankIntegrationLog.builder()
            .bankCode(event.getBankCode())
            .eventType("ERROR_THRESHOLD_EXCEEDED")
            .status("THRESHOLD_EXCEEDED")
            .errorCount(event.getErrorCount())
            .errorThreshold(event.getErrorThreshold())
            .timestamp(LocalDateTime.now())
            .correlationId(correlationId)
            .build();

        bankIntegrationLogRepository.save(log);

        // Handle error threshold exceeded
        bankIntegrationService.handleErrorThresholdExceeded(
            event.getBankCode(),
            event.getErrorCount(),
            event.getErrorThreshold(),
            correlationId
        );

        // Send critical alert
        notificationService.sendCriticalAlert(
            "Bank Integration Error Threshold Exceeded",
            String.format("Error threshold exceeded for %s: %d errors (threshold: %d)",
                event.getBankCode(), event.getErrorCount(), event.getErrorThreshold()),
            Map.of("bankCode", event.getBankCode(), "errorCount", event.getErrorCount(), "correlationId", correlationId)
        );

        log.error("Error threshold exceeded: bank={}, errors={}, threshold={}",
            event.getBankCode(), event.getErrorCount(), event.getErrorThreshold());
    }
}