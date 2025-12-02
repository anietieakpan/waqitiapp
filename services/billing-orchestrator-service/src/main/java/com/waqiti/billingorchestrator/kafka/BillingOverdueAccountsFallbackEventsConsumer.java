package com.waqiti.billingorchestrator.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.billingorchestrator.service.BillingService;
import com.waqiti.billingorchestrator.service.CollectionsService;
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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class BillingOverdueAccountsFallbackEventsConsumer extends BaseDlqConsumer {

    private final BillingService billingService;
    private final CollectionsService collectionsService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public BillingOverdueAccountsFallbackEventsConsumer(BillingService billingService,
                                                        CollectionsService collectionsService,
                                                        MeterRegistry meterRegistry) {
        super("billing-overdue-accounts-fallback-events");
        this.billingService = billingService;
        this.collectionsService = collectionsService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("billing_overdue_accounts_fallback_processed_total")
                .description("Total billing overdue accounts fallback events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("billing_overdue_accounts_fallback_errors_total")
                .description("Total billing overdue accounts fallback errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("billing_overdue_accounts_fallback_duration")
                .description("Billing overdue accounts fallback processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "billing-overdue-accounts-fallback-events",
        groupId = "billing-service-overdue-accounts-fallback-group",
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
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "billing-overdue-fallback", fallbackMethod = "handleBillingOverdueFallbackCircuitBreaker")
    public void handleBillingOverdueAccountsFallbackEvents(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Account-Id", required = false) String accountId,
            @Header(value = "X-Overdue-Amount", required = false) String overdueAmount,
            @Header(value = "X-Days-Overdue", required = false) String daysOverdue,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Billing overdue fallback event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing billing overdue fallback event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, accountId={}, overdueAmount={}, daysOverdue={}",
                     topic, partition, offset, record.key(), correlationId, accountId, overdueAmount, daysOverdue);

            String overdueData = record.value();
            validateOverdueData(overdueData, eventId);

            // Process billing overdue fallback with collections strategy
            BillingOverdueRecoveryResult result = billingService.processBillingOverdueFallback(
                overdueData,
                record.key(),
                correlationId,
                accountId,
                overdueAmount,
                daysOverdue,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result based on overdue severity
            if (result.isRecovered()) {
                handleSuccessfulRecovery(result, correlationId);
            } else if (result.requiresCollections()) {
                handleCollectionsRequired(result, correlationId);
            } else {
                handleFailedRecovery(result, eventId, correlationId);
            }

            // Update collections metrics
            updateCollectionsMetrics(result, correlationId);

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed billing overdue fallback: eventId={}, accountId={}, " +
                    "correlationId={}, recoveryStatus={}, recoveredAmount={}",
                    eventId, result.getAccountId(), correlationId, result.getRecoveryStatus(),
                    result.getRecoveredAmount());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in billing overdue fallback: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();
        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in billing overdue fallback: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in billing overdue fallback: eventId={}, correlationId={}",
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
        log.error("Billing overdue fallback sent to DLT - REVENUE LOSS RISK: " +
                 "topic={}, originalPartition={}, originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        // Execute revenue protection protocol
        executeRevenueProtectionProtocol(record, topic, exceptionMessage, correlationId);

        // Store for manual collections review
        storeForManualCollectionsReview(record, topic, exceptionMessage, correlationId);

        // Send CFO alert for revenue impact
        sendCfoRevenueAlert(record, topic, exceptionMessage, correlationId);

        // Update DLT metrics
        Counter.builder("billing_overdue_fallback_dlt_events_total")
                .description("Total billing overdue fallback events sent to DLT")
                .tag("topic", topic)
                .tag("revenue_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    public void handleBillingOverdueFallbackCircuitBreaker(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String accountId, String overdueAmount, String daysOverdue,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for billing overdue fallback: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store in high-priority collections queue
        storeInHighPriorityCollectionsQueue(record, correlationId);

        // Send collections team alert
        sendCollectionsTeamAlert(correlationId, ex);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("billing_overdue_fallback_circuit_breaker_activations_total")
                .tag("revenue_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    private void validateOverdueData(String overdueData, String eventId) {
        if (overdueData == null || overdueData.trim().isEmpty()) {
            throw new ValidationException("Overdue data is null or empty for eventId: " + eventId);
        }

        if (!overdueData.contains("accountId")) {
            throw new ValidationException("Overdue data missing accountId for eventId: " + eventId);
        }

        if (!overdueData.contains("overdueAmount")) {
            throw new ValidationException("Overdue data missing overdueAmount for eventId: " + eventId);
        }

        if (!overdueData.contains("originalDueDate")) {
            throw new ValidationException("Overdue data missing originalDueDate for eventId: " + eventId);
        }

        // Validate financial integrity
        validateFinancialIntegrity(overdueData, eventId);
    }

    private void validateFinancialIntegrity(String overdueData, String eventId) {
        try {
            JsonNode data = objectMapper.readTree(overdueData);
            BigDecimal overdueAmount = new BigDecimal(data.get("overdueAmount").asText());

            if (overdueAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Invalid overdue amount (must be positive) for eventId: " + eventId);
            }

            // Check for unusually large amounts that may indicate data corruption
            if (overdueAmount.compareTo(new BigDecimal("1000000")) > 0) {
                log.warn("Unusually large overdue amount detected: {} for eventId: {}", overdueAmount, eventId);
            }

        } catch (Exception e) {
            throw new ValidationException("Failed to validate financial integrity: " + e.getMessage());
        }
    }

    private void handleSuccessfulRecovery(BillingOverdueRecoveryResult result, String correlationId) {
        log.info("Billing overdue successfully recovered: accountId={}, recoveredAmount={}, correlationId={}",
                result.getAccountId(), result.getRecoveredAmount(), correlationId);

        // Update account status
        billingService.updateAccountStatus(
            result.getAccountId(),
            AccountStatus.CURRENT,
            correlationId
        );

        // Record payment recovery
        paymentRecoveryService.recordRecovery(
            result.getAccountId(),
            result.getRecoveredAmount(),
            RecoveryMethod.AUTOMATED,
            correlationId
        );

        // Send recovery notification
        notificationService.sendRecoveryNotification(
            result.getAccountId(),
            result.getCustomerId(),
            result.getRecoveredAmount(),
            correlationId
        );

        // Update revenue metrics
        revenueMetricsService.recordRecovery(
            result.getRecoveredAmount(),
            result.getRecoveryMethod(),
            correlationId
        );

        // Clear collections flags
        collectionsService.clearCollectionsFlags(
            result.getAccountId(),
            "Automated recovery successful",
            correlationId
        );
    }

    private void handleCollectionsRequired(BillingOverdueRecoveryResult result, String correlationId) {
        log.warn("Collections required for overdue account: accountId={}, overdueAmount={}, daysOverdue={}, correlationId={}",
                result.getAccountId(), result.getOverdueAmount(), result.getDaysOverdue(), correlationId);

        // Determine collections strategy based on overdue severity
        CollectionsStrategy strategy = determineCollectionsStrategy(result);

        // Queue for collections
        collectionsService.queueForCollections(
            result.getAccountId(),
            result.getOverdueAmount(),
            result.getDaysOverdue(),
            strategy,
            correlationId
        );

        // Send collections notification
        notificationService.sendCollectionsNotification(
            result.getAccountId(),
            result.getCustomerId(),
            result.getOverdueAmount(),
            strategy,
            correlationId
        );

        // Update collections metrics
        collectionsMetricsService.recordCollectionsCase(
            result.getAccountId(),
            result.getOverdueAmount(),
            result.getDaysOverdue(),
            strategy,
            correlationId
        );

        // Apply account restrictions if severe
        if (strategy == CollectionsStrategy.AGGRESSIVE || result.getDaysOverdue() > 90) {
            accountRestrictionsService.applyCollectionsRestrictions(
                result.getAccountId(),
                RestrictionLevel.SEVERE,
                correlationId
            );
        }
    }

    private void handleFailedRecovery(BillingOverdueRecoveryResult result, String eventId, String correlationId) {
        log.error("Billing overdue recovery failed: accountId={}, reason={}, potentialLoss={}, correlationId={}",
                result.getAccountId(), result.getFailureReason(), result.getPotentialLoss(), correlationId);

        // Escalate to collections manager
        collectionsEscalationService.escalateToManager(
            result.getAccountId(),
            result.getOverdueAmount(),
            result.getFailureReason(),
            eventId,
            correlationId,
            EscalationPriority.HIGH
        );

        // Create bad debt provision if severely overdue
        if (result.getDaysOverdue() > 120) {
            badDebtProvisionService.createProvision(
                result.getAccountId(),
                result.getOverdueAmount(),
                "Recovery failed - potential bad debt",
                correlationId
            );
        }

        // Send revenue impact alert
        revenueAlertService.sendRevenueImpactAlert(
            AlertType.POTENTIAL_REVENUE_LOSS,
            result.getAccountId(),
            result.getPotentialLoss(),
            correlationId
        );
    }

    private CollectionsStrategy determineCollectionsStrategy(BillingOverdueRecoveryResult result) {
        int daysOverdue = result.getDaysOverdue();
        BigDecimal overdueAmount = result.getOverdueAmount();

        if (daysOverdue <= 30 && overdueAmount.compareTo(new BigDecimal("1000")) <= 0) {
            return CollectionsStrategy.GENTLE_REMINDER;
        } else if (daysOverdue <= 60 && overdueAmount.compareTo(new BigDecimal("5000")) <= 0) {
            return CollectionsStrategy.STANDARD;
        } else if (daysOverdue <= 90) {
            return CollectionsStrategy.FIRM;
        } else {
            return CollectionsStrategy.AGGRESSIVE;
        }
    }

    private void updateCollectionsMetrics(BillingOverdueRecoveryResult result, String correlationId) {
        // Record collections metrics
        collectionsMetricsService.recordRecoveryAttempt(
            result.getRecoveryStatus(),
            result.getOverdueAmount(),
            result.getDaysOverdue(),
            correlationId
        );

        // Update recovery rate metrics
        if (result.isRecovered()) {
            recoveryRateMetricsService.recordSuccessfulRecovery(
                result.getDaysOverdue(),
                result.getRecoveredAmount(),
                correlationId
            );
        }

        // Update aging bucket metrics
        agingBucketMetricsService.updateBucket(
            result.getAccountId(),
            result.getDaysOverdue(),
            result.getOverdueAmount(),
            correlationId
        );
    }

    private void executeRevenueProtectionProtocol(ConsumerRecord<String, String> record,
                                                  String topic, String exceptionMessage,
                                                  String correlationId) {
        try {
            // Execute comprehensive revenue protection
            RevenueProtectionResult protection = revenueProtectionService.executeProtocol(
                record.key(),
                record.value(),
                topic,
                exceptionMessage,
                correlationId
            );

            if (protection.isPartiallyProtected()) {
                log.info("Partial revenue protection achieved: correlationId={}, protectedAmount={}",
                        correlationId, protection.getProtectedAmount());

                // Apply protective measures
                protectiveMeasuresService.applyMeasures(
                    protection.getAccountId(),
                    protection.getProtectiveMeasures(),
                    correlationId
                );
            }
        } catch (Exception e) {
            log.error("Revenue protection protocol failed: correlationId={}", correlationId, e);
        }
    }

    private void storeForManualCollectionsReview(ConsumerRecord<String, String> record, String topic,
                                                 String exceptionMessage, String correlationId) {
        manualCollectionsReviewRepository.save(
            ManualCollectionsReview.builder()
                .sourceTopic(topic)
                .accountId(extractAccountId(record.value()))
                .messageKey(record.key())
                .messageValue(record.value())
                .failureReason(exceptionMessage)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .status(ReviewStatus.PENDING_COLLECTIONS_MANAGER)
                .overdueAmount(extractOverdueAmount(record.value()))
                .daysOverdue(extractDaysOverdue(record.value()))
                .priority(Priority.HIGH)
                .assignedTo("COLLECTIONS_MANAGER")
                .build()
        );
    }

    private void sendCfoRevenueAlert(ConsumerRecord<String, String> record, String topic,
                                     String exceptionMessage, String correlationId) {
        cfoAlertService.sendRevenueImpactAlert(
            RevenueAlertLevel.CRITICAL,
            "Billing overdue permanently failed - potential revenue loss",
            Map.of(
                "topic", topic,
                "accountId", extractAccountId(record.value()),
                "overdueAmount", extractOverdueAmount(record.value()).toString(),
                "daysOverdue", extractDaysOverdue(record.value()).toString(),
                "error", exceptionMessage,
                "correlationId", correlationId,
                "revenueImpact", "HIGH",
                "requiredAction", "Immediate CFO review for revenue protection",
                "potentialLoss", calculatePotentialLoss(record.value()).toString()
            )
        );
    }

    private void storeInHighPriorityCollectionsQueue(ConsumerRecord<String, String> record, String correlationId) {
        highPriorityCollectionsQueue.add(
            HighPriorityCollectionsItem.builder()
                .topic("billing-overdue-accounts-fallback-events")
                .accountId(extractAccountId(record.value()))
                .key(record.key())
                .value(record.value())
                .correlationId(correlationId)
                .priority(Priority.CRITICAL)
                .overdueAmount(extractOverdueAmount(record.value()))
                .daysOverdue(extractDaysOverdue(record.value()))
                .retryCount(0)
                .maxRetries(3)
                .nextRetryTime(Instant.now().plus(Duration.ofMinutes(15)))
                .assignedTo("COLLECTIONS_TEAM")
                .build()
        );
    }

    private void sendCollectionsTeamAlert(String correlationId, Exception ex) {
        collectionsTeamAlertService.sendAlert(
            AlertType.CIRCUIT_BREAKER_ACTIVATED,
            "Billing overdue fallback circuit breaker activated",
            Map.of(
                "service", "billing-overdue-fallback",
                "correlationId", correlationId,
                "error", ex.getMessage(),
                "impact", "Collections processing may be delayed",
                "revenueRisk", "MEDIUM"
            )
        );
    }

    private boolean isAlreadyProcessed(String eventId) {
        Long processTime = processedEvents.get(eventId);
        if (processTime != null) {
            return System.currentTimeMillis() - processTime < Duration.ofHours(24).toMillis();
        }
        return false;
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, System.currentTimeMillis());
        if (processedEvents.size() > 10000) {
            cleanupOldProcessedEvents();
        }
    }

    private void cleanupOldProcessedEvents() {
        long cutoffTime = System.currentTimeMillis() - Duration.ofHours(24).toMillis();
        processedEvents.entrySet().removeIf(entry -> entry.getValue() < cutoffTime);
    }

    private String extractAccountId(String value) {
        try {
            return objectMapper.readTree(value).get("accountId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private BigDecimal extractOverdueAmount(String value) {
        try {
            return new BigDecimal(objectMapper.readTree(value).get("overdueAmount").asText());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer extractDaysOverdue(String value) {
        try {
            return objectMapper.readTree(value).get("daysOverdue").asInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private BigDecimal calculatePotentialLoss(String value) {
        // Calculate potential loss based on overdue amount and historical recovery rates
        BigDecimal overdueAmount = extractOverdueAmount(value);
        Integer daysOverdue = extractDaysOverdue(value);

        // Apply loss probability based on aging
        double lossProbability = Math.min(0.8, daysOverdue / 365.0);
        return overdueAmount.multiply(BigDecimal.valueOf(lossProbability));
    }
}