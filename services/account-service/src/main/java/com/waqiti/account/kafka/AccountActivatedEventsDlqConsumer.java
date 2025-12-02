package com.waqiti.account.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.account.service.AccountService;
import com.waqiti.common.error.ValidationException;
import com.waqiti.common.exception.RecoverableException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountActivatedEventsDlqConsumer extends BaseDlqConsumer {

    private final AccountService accountService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public AccountActivatedEventsDlqConsumer(AccountService accountService, MeterRegistry meterRegistry) {
        super("account-activated-events-dlq");
        this.accountService = accountService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("account_activated_events_dlq_processed_total")
                .description("Total account activated events DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("account_activated_events_dlq_errors_total")
                .description("Total account activated events DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("account_activated_events_dlq_duration")
                .description("Account activated events DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "account-activated-events-dlq",
        groupId = "account-service-account-activated-events-dlq-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.max-poll-interval-ms=600000",
            "spring.kafka.consumer.session-timeout-ms=30000"
        }
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "account-activated-dlq", fallbackMethod = "handleAccountActivatedDlqFallback")
    public void handleAccountActivatedEventsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Account activated events DLQ event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing account activated events DLQ event: topic={}, partition={}, offset={}, key={}, correlationId={}",
                     topic, partition, offset, record.key(), correlationId);

            String accountData = record.value();
            validateAccountData(accountData, eventId);

            // Process account activation DLQ with recovery strategy
            AccountActivationRecoveryResult result = accountService.processAccountActivatedEventsDlq(
                accountData,
                record.key(),
                correlationId,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result
            if (result.isRecovered()) {
                log.info("Successfully recovered account activation for accountId={}, correlationId={}",
                        result.getAccountId(), correlationId);
                sendAccountActivationRecoveryNotification(result, correlationId);
            } else {
                log.warn("Failed to recover account activation for accountId={}, reason={}, correlationId={}",
                        result.getAccountId(), result.getFailureReason(), correlationId);
                escalateActivationFailure(result, eventId, correlationId);
            }

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed account activated events DLQ event: eventId={}, accountId={}, correlationId={}",
                    eventId, result.getAccountId(), correlationId);

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error processing account activated events DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge(); // Acknowledge invalid messages to prevent reprocessing
        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error processing account activated events DLQ: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());
            throw e; // Let retry mechanism handle it
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Error processing account activated events DLQ event: eventId={}, correlationId={}",
                     eventId, correlationId, e);
            handleCriticalFailure(record, e, correlationId);
            throw e;
        } finally {
            sample.stop(processingTimer);
        }
    }

    @DltHandler
    public void handleDlt(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(KafkaHeaders.ORIGINAL_OFFSET) long originalOffset,
            @Header(KafkaHeaders.ORIGINAL_PARTITION) int originalPartition) {

        String correlationId = generateCorrelationId();
        log.error("Account activated DLQ event sent to DLT after all retries exhausted: " +
                 "topic={}, originalPartition={}, originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        // Store in permanent failure storage for manual investigation
        storeInPermanentFailureStorage(record, topic, exceptionMessage, correlationId);

        // Send critical alert for DLT events
        sendCriticalAlert(record, topic, exceptionMessage, correlationId);

        // Update metrics
        Counter.builder("account_activated_dlt_events_total")
                .description("Total events sent to DLT")
                .tag("topic", topic)
                .register(meterRegistry)
                .increment();
    }

    public void handleAccountActivatedDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for account activated DLQ processing: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store for later processing when circuit breaker recovers
        storeForLaterProcessing(record, correlationId);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("account_activated_dlq_circuit_breaker_activations_total")
                .register(meterRegistry)
                .increment();
    }

    private void validateAccountData(String accountData, String eventId) {
        if (accountData == null || accountData.trim().isEmpty()) {
            throw new ValidationException("Account data is null or empty for eventId: " + eventId);
        }

        // Additional validation logic
        if (!accountData.contains("accountId")) {
            throw new ValidationException("Account data missing accountId field for eventId: " + eventId);
        }

        if (!accountData.contains("activationTimestamp")) {
            throw new ValidationException("Account data missing activationTimestamp for eventId: " + eventId);
        }
    }

    private void sendAccountActivationRecoveryNotification(AccountActivationRecoveryResult result, String correlationId) {
        try {
            notificationService.sendActivationRecoveryNotification(
                result.getAccountId(),
                result.getCustomerId(),
                result.getRecoveryMethod(),
                correlationId
            );
        } catch (Exception e) {
            log.error("Failed to send activation recovery notification: accountId={}, correlationId={}",
                     result.getAccountId(), correlationId, e);
        }
    }

    private void escalateActivationFailure(AccountActivationRecoveryResult result, String eventId, String correlationId) {
        try {
            escalationService.escalateActivationFailure(
                result.getAccountId(),
                result.getFailureReason(),
                eventId,
                correlationId,
                EscalationPriority.HIGH
            );
        } catch (Exception e) {
            log.error("Failed to escalate activation failure: accountId={}, correlationId={}",
                     result.getAccountId(), correlationId, e);
        }
    }

    private void handleValidationFailure(ConsumerRecord<String, String> record, ValidationException e, String correlationId) {
        // Store invalid records for analysis
        invalidRecordRepository.save(
            InvalidRecord.builder()
                .topic("account-activated-events-dlq")
                .key(record.key())
                .value(record.value())
                .errorMessage(e.getMessage())
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .build()
        );
    }

    private void handleCriticalFailure(ConsumerRecord<String, String> record, Exception e, String correlationId) {
        // Send immediate alert for critical failures
        alertService.sendCriticalAlert(
            AlertType.ACCOUNT_ACTIVATION_DLQ_FAILURE,
            "Critical failure in account activation DLQ processing",
            Map.of(
                "key", record.key(),
                "correlationId", correlationId,
                "error", e.getMessage()
            )
        );
    }

    private void storeInPermanentFailureStorage(ConsumerRecord<String, String> record, String topic,
                                                String exceptionMessage, String correlationId) {
        permanentFailureRepository.save(
            PermanentFailure.builder()
                .sourceTopic(topic)
                .messageKey(record.key())
                .messageValue(record.value())
                .failureReason(exceptionMessage)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .status(FailureStatus.PENDING_INVESTIGATION)
                .build()
        );
    }

    private void sendCriticalAlert(ConsumerRecord<String, String> record, String topic,
                                  String exceptionMessage, String correlationId) {
        alertService.sendCriticalAlert(
            AlertType.DLT_EVENT,
            "Account activation event sent to DLT",
            Map.of(
                "topic", topic,
                "key", record.key(),
                "error", exceptionMessage,
                "correlationId", correlationId,
                "action", "Manual investigation required"
            )
        );
    }

    private void storeForLaterProcessing(ConsumerRecord<String, String> record, String correlationId) {
        circuitBreakerRecoveryQueue.add(
            CircuitBreakerRecoveryItem.builder()
                .topic("account-activated-events-dlq")
                .key(record.key())
                .value(record.value())
                .correlationId(correlationId)
                .retryCount(0)
                .nextRetryTime(Instant.now().plus(Duration.ofMinutes(5)))
                .build()
        );
    }

    @Override
    protected void processDomainSpecificLogic(Object originalMessage, String topic, String exceptionMessage, String messageId) {
//        TODO - properly implement with business logic, production-ready code, etc. added by aniix october, 28th 2025
    }

    @Override
    protected String getConsumerName() {
        return "";
//        TODO - properly implement with business logic, production-ready code, etc. added by aniix october, 28th 2025
    }

    @Override
    protected String getBusinessDomain() {
        return "";
//        TODO - properly implement with business logic, production-ready code, etc. added by aniix october, 28th 2025
    }

    @Override
    protected boolean isCriticalBusinessImpact(Object originalMessage, String topic) {
        return false;
//        TODO - properly implement with business logic, production-ready code, etc. added by aniix october, 28th 2025
    }

    @Override
    protected void generateDomainSpecificAlerts(Object originalMessage, String topic, String exceptionMessage, String messageId) {
//        TODO - properly implement with business logic, production-ready code, etc. added by aniix october, 28th 2025

    }

    private boolean isAlreadyProcessed(String eventId) {
        Long processTime = processedEvents.get(eventId);
        if (processTime != null) {
            // Check if event was processed within the last 24 hours
            return System.currentTimeMillis() - processTime < Duration.ofHours(24).toMillis();
        }
        return false;
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
        // Clean up old entries periodically
        if (processedEvents.size() > 10000) {
            cleanupOldProcessedEvents();
        }
    }

    private void cleanupOldProcessedEvents() {
        long cutoffTime = System.currentTimeMillis() - Duration.ofHours(24).toMillis();
        processedEvents.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }
}