package com.waqiti.transaction.kafka;

import com.waqiti.common.events.TransactionAuthorizedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.transaction.service.TransactionService;
import com.waqiti.transaction.service.TransactionLimitService;
import com.waqiti.transaction.service.TransactionMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.PostConstruct;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionAuthorizedConsumer {

    private final TransactionService transactionService;
    private final TransactionLimitService limitService;
    private final TransactionMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final UniversalDLQHandler dlqHandler;

    // Idempotency cache with 24-hour TTL
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private static final long TTL_24_HOURS = 24 * 60 * 60 * 1000; // 24 hours in milliseconds

    // Metrics
    private Counter successCounter;
    private Counter errorCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        successCounter = Counter.builder("transaction_authorized_processed_total")
            .description("Total number of successfully processed transaction authorized events")
            .register(meterRegistry);
        errorCounter = Counter.builder("transaction_authorized_errors_total")
            .description("Total number of transaction authorized processing errors")
            .register(meterRegistry);
        processingTimer = Timer.builder("transaction_authorized_processing_duration")
            .description("Time taken to process transaction authorized events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = {"transaction-authorized", "transaction-approved", "authorization-granted"},
        groupId = "transaction-authorized-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "10"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "transaction-authorized", fallbackMethod = "handleTransactionAuthorizedEventFallback")
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000))
    public void handleTransactionAuthorizedEvent(
            ConsumerRecord<String, TransactionAuthorizedEvent> record,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        TransactionAuthorizedEvent event = record.value();

        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = String.format("auth-%s-p%d-o%d", event.getTransactionId(), partition, offset);
        String eventKey = String.format("%s-%s-%s", event.getTransactionId(), event.getAuthorizationCode(), event.getTimestamp());

        try {
            // Idempotency check
            if (isEventProcessed(eventKey)) {
                log.info("Event already processed, skipping: {}", eventKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing transaction authorized: transactionId={}, amount={}, authCode={}, customerId={}",
                event.getTransactionId(), event.getAmount(), event.getAuthorizationCode(), event.getCustomerId());

            // Clean expired entries periodically
            cleanExpiredEntries();

            // Update transaction status
            updateTransactionStatus(event, correlationId);

            // Process authorization hold
            processAuthorizationHold(event, correlationId);

            // Update customer spending limits
            updateSpendingLimits(event, correlationId);

            // Send authorization confirmation
            sendAuthorizationConfirmation(event, correlationId);

            // Process post-authorization activities
            processPostAuthorizationActivities(event, correlationId);

            // Mark event as processed
            markEventAsProcessed(eventKey);

            auditService.logTransactionEvent("TRANSACTION_AUTHORIZED_PROCESSED", event.getTransactionId(),
                Map.of("authorizationCode", event.getAuthorizationCode(), "amount", event.getAmount(),
                    "customerId", event.getCustomerId(), "correlationId", correlationId,
                    "timestamp", Instant.now()));

            successCounter.increment();
            acknowledgment.acknowledge();

        } catch (Exception e) {
            errorCounter.increment();
            log.error("Failed to process transaction authorized event: {}", e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Transaction authorized event sent to DLQ: transactionId={}, destination={}, attemptNumber={}",
                        event.getTransactionId(), result.getDestinationTopic(), result.getAttemptNumber()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for transaction authorized event - MESSAGE MAY BE LOST! " +
                            "transactionId={}, partition={}, offset={}, error={}",
                            event.getTransactionId(), partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            acknowledgment.acknowledge();
            throw e; // Re-throw for retry mechanism
        } finally {
            sample.stop(processingTimer);
        }
    }

    public void handleTransactionAuthorizedEventFallback(
            ConsumerRecord<String, TransactionAuthorizedEvent> record,
            int partition,
            long offset,
            Acknowledgment acknowledgment,
            Exception ex) {

        TransactionAuthorizedEvent event = record.value();

        String correlationId = String.format("auth-fallback-%s-p%d-o%d", event.getTransactionId(), partition, offset);

        log.error("Circuit breaker fallback triggered for transaction authorized: transactionId={}, error={}",
            event.getTransactionId(), ex.getMessage());

        // Send to dead letter queue
        kafkaTemplate.send("transaction-authorized-dlq", Map.of(
            "originalEvent", event,
            "error", ex.getMessage(),
            "errorType", "CIRCUIT_BREAKER_FALLBACK",
            "correlationId", correlationId,
            "timestamp", Instant.now()));

        // Send critical notification for high-value transactions
        if (event.getAmount().compareTo(transactionService.getHighValueThreshold()) > 0) {
            try {
                notificationService.sendCriticalAlert(
                    "High Value Transaction Authorization Failed",
                    String.format("Transaction %s authorization processing failed: %s", event.getTransactionId(), ex.getMessage()),
                    "CRITICAL"
                );
            } catch (Exception notificationEx) {
                log.error("Failed to send critical alert: {}", notificationEx.getMessage());
            }
        }

        acknowledgment.acknowledge();
    }

    @DltHandler
    public void handleDltTransactionAuthorizedEvent(
            ConsumerRecord<String, TransactionAuthorizedEvent> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {

        TransactionAuthorizedEvent event = record.value();

        String correlationId = String.format("dlt-auth-%s-%d", event.getTransactionId(), System.currentTimeMillis());

        log.error("Dead letter topic handler - Transaction authorized permanently failed: transactionId={}, topic={}, error={}",
            event.getTransactionId(), topic, exceptionMessage);

        // Save to dead letter store for manual investigation
        auditService.logTransactionEvent("TRANSACTION_AUTHORIZED_DLT_EVENT", event.getTransactionId(),
            Map.of("originalTopic", topic, "errorMessage", exceptionMessage,
                "authorizationCode", event.getAuthorizationCode(), "correlationId", correlationId,
                "requiresManualIntervention", true, "timestamp", Instant.now()));

        // Send emergency alert
        try {
            notificationService.sendEmergencyAlert(
                "Transaction Authorized Dead Letter Event",
                String.format("Transaction %s authorization sent to DLT: %s", event.getTransactionId(), exceptionMessage),
                Map.of("transactionId", event.getTransactionId(), "topic", topic, "correlationId", correlationId)
            );
        } catch (Exception ex) {
            log.error("Failed to send emergency DLT alert: {}", ex.getMessage());
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

    private void updateTransactionStatus(TransactionAuthorizedEvent event, String correlationId) {
        // Update transaction status to authorized
        transactionService.updateTransactionStatus(
            event.getTransactionId(),
            "AUTHORIZED",
            event.getAuthorizationCode(),
            event.getAuthorizingEntity(),
            event.getAuthorizedAt(),
            correlationId
        );

        // Update metrics
        metricsService.incrementTransactionsAuthorized(event.getTransactionType());
        metricsService.recordAuthorizationTime(
            event.getTransactionType(),
            event.getAuthorizedAt().toEpochMilli() - event.getRequestedAt().toEpochMilli()
        );

        // Record authorization source metrics
        metricsService.recordAuthorizationBySource(event.getAuthorizingEntity());

        log.info("Transaction status updated to authorized: transactionId={}, authCode={}",
            event.getTransactionId(), event.getAuthorizationCode());
    }

    private void processAuthorizationHold(TransactionAuthorizedEvent event, String correlationId) {
        // Place authorization hold on customer account
        transactionService.placeAuthorizationHold(
            event.getCustomerId(),
            event.getAccountId(),
            event.getAmount(),
            event.getCurrency(),
            event.getTransactionId(),
            event.getAuthorizationCode(),
            correlationId
        );

        // Send hold placement event
        kafkaTemplate.send("authorization-holds", Map.of(
            "transactionId", event.getTransactionId(),
            "customerId", event.getCustomerId(),
            "accountId", event.getAccountId(),
            "amount", event.getAmount(),
            "currency", event.getCurrency(),
            "authorizationCode", event.getAuthorizationCode(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Update account balance (reduce available balance)
        kafkaTemplate.send("account-balance-updates", Map.of(
            "accountId", event.getAccountId(),
            "transactionId", event.getTransactionId(),
            "type", "AUTHORIZATION_HOLD",
            "amount", event.getAmount(),
            "currency", event.getCurrency(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Authorization hold placed: transactionId={}, amount={}, accountId={}",
            event.getTransactionId(), event.getAmount(), event.getAccountId());
    }

    private void updateSpendingLimits(TransactionAuthorizedEvent event, String correlationId) {
        // Update customer spending limits and velocity checks
        limitService.updateSpendingLimits(
            event.getCustomerId(),
            event.getAmount(),
            event.getCurrency(),
            event.getTransactionType(),
            event.getAuthorizedAt(),
            correlationId
        );

        // Check if customer is approaching limits
        var limitStatus = limitService.checkLimitStatus(event.getCustomerId(), event.getTransactionType());
        if (limitStatus.isApproachingLimit()) {
            kafkaTemplate.send("customer-notifications", Map.of(
                "type", "SPENDING_LIMIT_WARNING",
                "customerId", event.getCustomerId(),
                "remainingLimit", limitStatus.getRemainingLimit(),
                "limitType", event.getTransactionType(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Update velocity tracking
        limitService.updateVelocityTracking(
            event.getCustomerId(),
            event.getAmount(),
            event.getTransactionType(),
            event.getMerchantCategory(),
            event.getAuthorizedAt()
        );

        log.info("Spending limits updated: customerId={}, amount={}, type={}",
            event.getCustomerId(), event.getAmount(), event.getTransactionType());
    }

    private void sendAuthorizationConfirmation(TransactionAuthorizedEvent event, String correlationId) {
        // Send confirmation to payment processor/gateway
        kafkaTemplate.send("payment-gateway-confirmations", Map.of(
            "transactionId", event.getTransactionId(),
            "authorizationCode", event.getAuthorizationCode(),
            "status", "AUTHORIZED",
            "amount", event.getAmount(),
            "currency", event.getCurrency(),
            "gatewayId", event.getGatewayId(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Send real-time notification to customer if enabled
        if (event.isNotifyCustomer()) {
            kafkaTemplate.send("customer-notifications", Map.of(
                "type", "TRANSACTION_AUTHORIZED",
                "customerId", event.getCustomerId(),
                "transactionId", event.getTransactionId(),
                "amount", event.getAmount(),
                "currency", event.getCurrency(),
                "merchantName", event.getMerchantName(),
                "authorizationCode", event.getAuthorizationCode(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        log.info("Authorization confirmation sent: transactionId={}, authCode={}",
            event.getTransactionId(), event.getAuthorizationCode());
    }

    private void processPostAuthorizationActivities(TransactionAuthorizedEvent event, String correlationId) {
        // Send for fraud post-authorization checks if flagged
        if (event.getFraudScore() != null && event.getFraudScore() > 50) {
            kafkaTemplate.send("fraud-post-authorization-checks", Map.of(
                "transactionId", event.getTransactionId(),
                "customerId", event.getCustomerId(),
                "fraudScore", event.getFraudScore(),
                "authorizationCode", event.getAuthorizationCode(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Send to loyalty processing if applicable
        if (event.getLoyaltyEligible() != null && event.getLoyaltyEligible()) {
            kafkaTemplate.send("loyalty-processing", Map.of(
                "transactionId", event.getTransactionId(),
                "customerId", event.getCustomerId(),
                "amount", event.getAmount(),
                "merchantCategory", event.getMerchantCategory(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Send to analytics for pattern analysis
        kafkaTemplate.send("transaction-analytics", Map.of(
            "type", "TRANSACTION_AUTHORIZED",
            "transactionId", event.getTransactionId(),
            "customerId", event.getCustomerId(),
            "amount", event.getAmount(),
            "currency", event.getCurrency(),
            "transactionType", event.getTransactionType(),
            "merchantCategory", event.getMerchantCategory(),
            "authorizationTime", event.getAuthorizedAt().toEpochMilli() - event.getRequestedAt().toEpochMilli(),
            "fraudScore", event.getFraudScore(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        // Check for promotion eligibility
        if (event.getAmount().compareTo(transactionService.getPromotionThreshold()) > 0) {
            kafkaTemplate.send("promotion-eligibility-checks", Map.of(
                "customerId", event.getCustomerId(),
                "transactionId", event.getTransactionId(),
                "amount", event.getAmount(),
                "merchantCategory", event.getMerchantCategory(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }

        // Update transaction risk scoring
        kafkaTemplate.send("transaction-risk-updates", Map.of(
            "transactionId", event.getTransactionId(),
            "customerId", event.getCustomerId(),
            "riskScore", event.getFraudScore(),
            "authorizationResult", "APPROVED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));

        log.info("Post-authorization activities processed: transactionId={}", event.getTransactionId());
    }
}