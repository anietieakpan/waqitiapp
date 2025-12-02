package com.waqiti.apigateway.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.apigateway.service.ApiUsageService;
import com.waqiti.apigateway.service.RateLimitingService;
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
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiUsageEventsDlqConsumer extends BaseDlqConsumer {

    private final ApiUsageService apiUsageService;
    private final RateLimitingService rateLimitingService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public ApiUsageEventsDlqConsumer(ApiUsageService apiUsageService,
                                     RateLimitingService rateLimitingService,
                                     MeterRegistry meterRegistry) {
        super("api-usage-events-dlq");
        this.apiUsageService = apiUsageService;
        this.rateLimitingService = rateLimitingService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("api_usage_events_dlq_processed_total")
                .description("Total API usage events DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("api_usage_events_dlq_errors_total")
                .description("Total API usage events DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("api_usage_events_dlq_duration")
                .description("API usage events DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "api-usage-events-dlq",
        groupId = "api-gateway-api-usage-events-dlq-group",
        containerFactory = "kafkaListenerContainerFactory",
        properties = {
            "spring.kafka.consumer.isolation-level=read_committed",
            "spring.kafka.consumer.auto-offset-reset=earliest",
            "spring.kafka.consumer.max-poll-interval-ms=300000",
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
    @CircuitBreaker(name = "api-usage-dlq", fallbackMethod = "handleApiUsageDlqFallback")
    public void handleApiUsageEventsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-API-Key", required = false) String apiKey,
            @Header(value = "X-Client-Id", required = false) String clientId,
            @Header(value = "X-Request-Path", required = false) String requestPath,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("API usage event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing API usage DLQ event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, apiKey={}, clientId={}, requestPath={}",
                     topic, partition, offset, record.key(), correlationId, apiKey, clientId, requestPath);

            String usageData = record.value();
            validateApiUsageData(usageData, eventId);

            // Process API usage DLQ with recovery and reconciliation
            ApiUsageRecoveryResult result = apiUsageService.processApiUsageDlq(
                usageData,
                record.key(),
                correlationId,
                apiKey,
                clientId,
                requestPath,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result
            if (result.isReconciled()) {
                handleSuccessfulReconciliation(result, correlationId);
            } else if (result.isQuotaExceeded()) {
                handleQuotaExceeded(result, correlationId);
            } else {
                handleFailedReconciliation(result, eventId, correlationId);
            }

            // Update rate limiting if needed
            updateRateLimiting(result, correlationId);

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed API usage DLQ: eventId={}, clientId={}, " +
                    "correlationId={}, reconciliationStatus={}",
                    eventId, result.getClientId(), correlationId, result.getReconciliationStatus());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in API usage DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();
        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in API usage DLQ: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in API usage DLQ: eventId={}, correlationId={}",
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
        log.error("API usage event sent to DLT: topic={}, originalPartition={}, " +
                 "originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        // Store for billing reconciliation
        storeForgottenApiUsage(record, topic, exceptionMessage, correlationId);

        // Send billing alert
        sendBillingAlert(record, topic, exceptionMessage, correlationId);

        // Update DLT metrics
        Counter.builder("api_usage_dlt_events_total")
                .description("Total API usage events sent to DLT")
                .tag("topic", topic)
                .register(meterRegistry)
                .increment();
    }

    public void handleApiUsageDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String apiKey, String clientId, String requestPath,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for API usage DLQ: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store for later processing
        storeForLaterProcessing(record, correlationId);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("api_usage_dlq_circuit_breaker_activations_total")
                .register(meterRegistry)
                .increment();
    }

    private void validateApiUsageData(String usageData, String eventId) {
        if (usageData == null || usageData.trim().isEmpty()) {
            throw new ValidationException("API usage data is null or empty for eventId: " + eventId);
        }

        if (!usageData.contains("clientId")) {
            throw new ValidationException("API usage data missing clientId for eventId: " + eventId);
        }

        if (!usageData.contains("endpoint")) {
            throw new ValidationException("API usage data missing endpoint for eventId: " + eventId);
        }

        if (!usageData.contains("requestCount")) {
            throw new ValidationException("API usage data missing requestCount for eventId: " + eventId);
        }
    }

    private void handleSuccessfulReconciliation(ApiUsageRecoveryResult result, String correlationId) {
        log.info("API usage successfully reconciled: clientId={}, totalRequests={}, correlationId={}",
                result.getClientId(), result.getTotalRequests(), correlationId);

        // Update billing records
        billingService.updateApiUsage(
            result.getClientId(),
            result.getTotalRequests(),
            result.getBillableAmount(),
            correlationId
        );

        // Update usage metrics
        usageMetricsService.recordReconciliation(
            result.getClientId(),
            result.getReconciliationPeriod(),
            result.getTotalRequests(),
            correlationId
        );
    }

    private void handleQuotaExceeded(ApiUsageRecoveryResult result, String correlationId) {
        log.warn("API quota exceeded: clientId={}, quotaLimit={}, actualUsage={}, correlationId={}",
                result.getClientId(), result.getQuotaLimit(), result.getActualUsage(), correlationId);

        // Apply quota enforcement
        quotaEnforcementService.enforceQuota(
            result.getClientId(),
            result.getQuotaLimit(),
            result.getActualUsage(),
            correlationId
        );

        // Send quota alert
        notificationService.sendQuotaExceededAlert(
            result.getClientId(),
            result.getQuotaLimit(),
            result.getActualUsage(),
            correlationId
        );
    }

    private void handleFailedReconciliation(ApiUsageRecoveryResult result, String eventId, String correlationId) {
        log.error("API usage reconciliation failed: clientId={}, reason={}, correlationId={}",
                result.getClientId(), result.getFailureReason(), correlationId);

        // Queue for manual billing review
        manualBillingReviewQueue.add(
            ManualBillingReviewItem.builder()
                .clientId(result.getClientId())
                .period(result.getReconciliationPeriod())
                .failureReason(result.getFailureReason())
                .eventId(eventId)
                .correlationId(correlationId)
                .estimatedRevenueLoss(result.getEstimatedRevenueLoss())
                .priority(Priority.HIGH)
                .build()
        );
    }

    private void updateRateLimiting(ApiUsageRecoveryResult result, String correlationId) {
        if (result.requiresRateLimitAdjustment()) {
            rateLimitingService.adjustRateLimit(
                result.getClientId(),
                result.getNewRateLimit(),
                result.getRateLimitReason(),
                correlationId
            );

            log.info("Rate limit adjusted for clientId={}, newLimit={}, correlationId={}",
                    result.getClientId(), result.getNewRateLimit(), correlationId);
        }
    }

    private void storeForgottenApiUsage(ConsumerRecord<String, String> record, String topic,
                                        String exceptionMessage, String correlationId) {
        forgottenApiUsageRepository.save(
            ForgottenApiUsage.builder()
                .sourceTopic(topic)
                .messageKey(record.key())
                .messageValue(record.value())
                .failureReason(exceptionMessage)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .status(ReconciliationStatus.PENDING_MANUAL_REVIEW)
                .estimatedRevenueLoss(calculateEstimatedRevenueLoss(record.value()))
                .build()
        );
    }

    private void sendBillingAlert(ConsumerRecord<String, String> record, String topic,
                                  String exceptionMessage, String correlationId) {
        billingAlertService.sendCriticalAlert(
            AlertType.API_USAGE_RECONCILIATION_FAILURE,
            "API usage permanently failed - potential revenue loss",
            Map.of(
                "topic", topic,
                "clientId", extractClientId(record.value()),
                "error", exceptionMessage,
                "correlationId", correlationId,
                "estimatedLoss", calculateEstimatedRevenueLoss(record.value()).toString(),
                "requiredAction", "Manual billing reconciliation required"
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
}