package com.waqiti.currency.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.exception.RecoverableException;
import com.waqiti.currency.service.CurrencyConversionService;
import com.waqiti.currency.service.ExchangeRateService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.transaction.annotation.Isolation;
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
public class CurrencyConversionEventsDlqConsumer extends BaseDlqConsumer {

    private final CurrencyConversionService currencyConversionService;
    private final ExchangeRateService exchangeRateService;
    private final CurrencyNotificationService notificationService;
    private final CurrencyMetricsService currencyMetricsService;
    private final AccountBalanceService accountBalanceService;
    private final ExchangeRateAvailabilityQueue exchangeRateAvailabilityQueue;
    private final ExchangeRateMetricsService exchangeRateMetricsService;
    private final ProductReviewQueue productReviewQueue;
    private final CurrencyPairMetricsService currencyPairMetricsService;
    private final TreasuryOperationsEscalationService treasuryOperationsEscalationService;
    private final SupportTicketService supportTicketService;
    private final RefundService refundService;
    private final CurrencyConversionMetricsService currencyConversionMetricsService;
    private final CurrencyPairVolumeMetricsService currencyPairVolumeMetricsService;
    private final ExchangeRateSpreadMetricsService exchangeRateSpreadMetricsService;
    private final FinancialTransactionEmergencyService financialTransactionEmergencyService;
    private final FinancialMitigationService financialMitigationService;
    private final TreasuryOperationsReviewRepository treasuryOperationsReviewRepository;
    private final TreasuryTeamAlertService treasuryTeamAlertService;
    private final FinancialImpactAssessmentService financialImpactAssessmentService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public CurrencyConversionEventsDlqConsumer(CurrencyConversionService currencyConversionService,
                                               ExchangeRateService exchangeRateService,
                                               CurrencyNotificationService notificationService,
                                               CurrencyMetricsService currencyMetricsService,
                                               AccountBalanceService accountBalanceService,
                                               ExchangeRateAvailabilityQueue exchangeRateAvailabilityQueue,
                                               ExchangeRateMetricsService exchangeRateMetricsService,
                                               ProductReviewQueue productReviewQueue,
                                               CurrencyPairMetricsService currencyPairMetricsService,
                                               TreasuryOperationsEscalationService treasuryOperationsEscalationService,
                                               SupportTicketService supportTicketService,
                                               RefundService refundService,
                                               CurrencyConversionMetricsService currencyConversionMetricsService,
                                               CurrencyPairVolumeMetricsService currencyPairVolumeMetricsService,
                                               ExchangeRateSpreadMetricsService exchangeRateSpreadMetricsService,
                                               FinancialTransactionEmergencyService financialTransactionEmergencyService,
                                               FinancialMitigationService financialMitigationService,
                                               TreasuryOperationsReviewRepository treasuryOperationsReviewRepository,
                                               TreasuryTeamAlertService treasuryTeamAlertService,
                                               FinancialImpactAssessmentService financialImpactAssessmentService,
                                               MeterRegistry meterRegistry,
                                               ObjectMapper objectMapper) {
        super("currency-conversion-events-dlq");
        this.currencyConversionService = currencyConversionService;
        this.exchangeRateService = exchangeRateService;
        this.notificationService = notificationService;
        this.currencyMetricsService = currencyMetricsService;
        this.accountBalanceService = accountBalanceService;
        this.exchangeRateAvailabilityQueue = exchangeRateAvailabilityQueue;
        this.exchangeRateMetricsService = exchangeRateMetricsService;
        this.productReviewQueue = productReviewQueue;
        this.currencyPairMetricsService = currencyPairMetricsService;
        this.treasuryOperationsEscalationService = treasuryOperationsEscalationService;
        this.supportTicketService = supportTicketService;
        this.refundService = refundService;
        this.currencyConversionMetricsService = currencyConversionMetricsService;
        this.currencyPairVolumeMetricsService = currencyPairVolumeMetricsService;
        this.exchangeRateSpreadMetricsService = exchangeRateSpreadMetricsService;
        this.financialTransactionEmergencyService = financialTransactionEmergencyService;
        this.financialMitigationService = financialMitigationService;
        this.treasuryOperationsReviewRepository = treasuryOperationsReviewRepository;
        this.treasuryTeamAlertService = treasuryTeamAlertService;
        this.financialImpactAssessmentService = financialImpactAssessmentService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
        this.processedCounter = Counter.builder("currency_conversion_events_dlq_processed_total")
                .description("Total currency conversion events DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("currency_conversion_events_dlq_errors_total")
                .description("Total currency conversion events DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("currency_conversion_events_dlq_duration")
                .description("Currency conversion events DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "currency-conversion-events-dlq",
        groupId = "currency-service-currency-conversion-events-dlq-group",
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
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 12000),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class},
        traversingCauses = "true",
        retryTopicSuffix = "-retry",
        dltStrategy = DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @CircuitBreaker(name = "currency-conversion-events-dlq", fallbackMethod = "handleCurrencyConversionEventsDlqFallback")
    public void handleCurrencyConversionEventsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Conversion-Id", required = false) String conversionId,
            @Header(value = "X-From-Currency", required = false) String fromCurrency,
            @Header(value = "X-To-Currency", required = false) String toCurrency,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Currency conversion event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing currency conversion DLQ event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, conversionId={}, fromCurrency={}, toCurrency={}",
                     topic, partition, offset, record.key(), correlationId, conversionId, fromCurrency, toCurrency);

            String conversionData = record.value();
            validateCurrencyConversionData(conversionData, eventId);

            // Process currency conversion DLQ with exchange rate recovery
            CurrencyConversionRecoveryResult result = currencyConversionService.processCurrencyConversionEventsDlq(
                conversionData,
                record.key(),
                correlationId,
                conversionId,
                fromCurrency,
                toCurrency,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result based on conversion criticality
            if (result.isConverted()) {
                handleSuccessfulConversion(result, correlationId);
            } else if (result.isExchangeRateUnavailable()) {
                handleExchangeRateUnavailable(result, correlationId);
            } else if (result.isCurrencyPairUnsupported()) {
                handleUnsupportedCurrencyPair(result, correlationId);
            } else {
                handleFailedConversion(result, eventId, correlationId);
            }

            // Update currency conversion metrics
            updateCurrencyConversionMetrics(result, correlationId);

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed currency conversion DLQ: eventId={}, conversionId={}, " +
                    "correlationId={}, conversionStatus={}",
                    eventId, result.getConversionId(), correlationId, result.getConversionStatus());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in currency conversion DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();
        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in currency conversion DLQ: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in currency conversion DLQ: eventId={}, correlationId={}",
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
        log.error("Currency conversion event sent to DLT - FINANCIAL TRANSACTION IMPACT: " +
                 "topic={}, originalPartition={}, originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        // Execute financial transaction emergency protocol
        executeFinancialTransactionEmergencyProtocol(record, topic, exceptionMessage, correlationId);

        // Store for treasury operations review
        storeForTreasuryOperationsReview(record, topic, exceptionMessage, correlationId);

        // Send treasury team alert
        sendTreasuryTeamAlert(record, topic, exceptionMessage, correlationId);

        // Create financial impact assessment
        createFinancialImpactAssessment(record, correlationId);

        // Update DLT metrics
        Counter.builder("currency_conversion_events_dlt_events_total")
                .description("Total currency conversion events sent to DLT")
                .tag("topic", topic)
                .tag("financial_impact", "medium")
                .register(meterRegistry)
                .increment();
    }

    public void handleCurrencyConversionEventsDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String conversionId, String fromCurrency, String toCurrency,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for currency conversion DLQ: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store in treasury operations queue
        storeInTreasuryOperationsQueue(record, correlationId);

        // Send financial operations alert
        sendFinancialOperationsAlert(correlationId, ex);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("currency_conversion_events_dlq_circuit_breaker_activations_total")
                .tag("financial_impact", "low")
                .register(meterRegistry)
                .increment();
    }

    private void validateCurrencyConversionData(String conversionData, String eventId) {
        if (conversionData == null || conversionData.trim().isEmpty()) {
            throw new ValidationException("Currency conversion data is null or empty for eventId: " + eventId);
        }

        if (!conversionData.contains("conversionId")) {
            throw new ValidationException("Currency conversion data missing conversionId for eventId: " + eventId);
        }

        if (!conversionData.contains("fromCurrency")) {
            throw new ValidationException("Currency conversion data missing fromCurrency for eventId: " + eventId);
        }

        if (!conversionData.contains("toCurrency")) {
            throw new ValidationException("Currency conversion data missing toCurrency for eventId: " + eventId);
        }

        if (!conversionData.contains("amount")) {
            throw new ValidationException("Currency conversion data missing amount for eventId: " + eventId);
        }

        // Validate financial integrity
        validateFinancialIntegrity(conversionData, eventId);
    }

    private void validateFinancialIntegrity(String conversionData, String eventId) {
        try {
            JsonNode data = objectMapper.readTree(conversionData);
            String fromCurrency = data.get("fromCurrency").asText();
            String toCurrency = data.get("toCurrency").asText();
            BigDecimal amount = new BigDecimal(data.get("amount").asText());

            // Validate amount is positive
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new ValidationException("Invalid conversion amount (must be positive) for eventId: " + eventId);
            }

            // Validate currency codes
            if (!isValidCurrencyCode(fromCurrency)) {
                throw new ValidationException("Invalid from currency code: " + fromCurrency + " for eventId: " + eventId);
            }

            if (!isValidCurrencyCode(toCurrency)) {
                throw new ValidationException("Invalid to currency code: " + toCurrency + " for eventId: " + eventId);
            }

            // Check for suspicious large amounts
            if (amount.compareTo(new BigDecimal("1000000")) > 0) {
                log.warn("Large currency conversion amount detected: {} for eventId: {}", amount, eventId);
            }

            // Validate exchange rate timestamp
            if (!data.has("exchangeRateTimestamp")) {
                log.warn("Currency conversion missing exchange rate timestamp: eventId={}", eventId);
            }

        } catch (Exception e) {
            throw new ValidationException("Failed to validate financial integrity: " + e.getMessage());
        }
    }

    private boolean isValidCurrencyCode(String currencyCode) {
        // ISO 4217 currency code validation
        return currencyCode != null &&
               currencyCode.length() == 3 &&
               currencyCode.matches("^[A-Z]{3}$");
    }

    private void handleSuccessfulConversion(CurrencyConversionRecoveryResult result, String correlationId) {
        log.info("Currency conversion successfully recovered: conversionId={}, fromCurrency={}, toCurrency={}, " +
                "convertedAmount={}, correlationId={}",
                result.getConversionId(), result.getFromCurrency(), result.getToCurrency(),
                result.getConvertedAmount(), correlationId);

        // Update conversion status
        currencyConversionService.updateConversionStatus(
            result.getConversionId(),
            ConversionStatus.COMPLETED,
            result.getConversionDetails(),
            correlationId
        );

        // Record final exchange rate used
        exchangeRateService.recordExchangeRateUsage(
            result.getFromCurrency(),
            result.getToCurrency(),
            result.getExchangeRate(),
            result.getOriginalAmount(),
            result.getConvertedAmount(),
            correlationId
        );

        // Send conversion confirmation
        notificationService.sendCurrencyConversionConfirmation(
            result.getCustomerId(),
            result.getConversionId(),
            result.getFromCurrency(),
            result.getToCurrency(),
            result.getOriginalAmount(),
            result.getConvertedAmount(),
            correlationId
        );

        // Update conversion metrics
        currencyMetricsService.recordSuccessfulConversion(
            result.getFromCurrency(),
            result.getToCurrency(),
            result.getOriginalAmount(),
            result.getConvertedAmount(),
            result.getConversionTime(),
            correlationId
        );

        // Update account balances if applicable
        if (result.hasAccountUpdate()) {
            accountBalanceService.updateBalanceForConversion(
                result.getAccountId(),
                result.getFromCurrency(),
                result.getToCurrency(),
                result.getOriginalAmount(),
                result.getConvertedAmount(),
                correlationId
            );
        }
    }

    private void handleExchangeRateUnavailable(CurrencyConversionRecoveryResult result, String correlationId) {
        log.warn("Exchange rate unavailable for currency conversion: conversionId={}, fromCurrency={}, toCurrency={}, correlationId={}",
                result.getConversionId(), result.getFromCurrency(), result.getToCurrency(), correlationId);

        // Update conversion status to pending rate
        currencyConversionService.updateConversionStatus(
            result.getConversionId(),
            ConversionStatus.PENDING_EXCHANGE_RATE,
            "Exchange rate temporarily unavailable",
            correlationId
        );

        // Queue for rate availability notification
        exchangeRateAvailabilityQueue.add(
            ExchangeRateAvailabilityRequest.builder()
                .conversionId(result.getConversionId())
                .fromCurrency(result.getFromCurrency())
                .toCurrency(result.getToCurrency())
                .amount(result.getOriginalAmount())
                .correlationId(correlationId)
                .priority(Priority.MEDIUM)
                .retryCount(0)
                .maxRetries(5)
                .nextRetryTime(Instant.now().plus(Duration.ofMinutes(5)))
                .build()
        );

        // Send rate unavailable notification
        notificationService.sendExchangeRateUnavailableNotification(
            result.getCustomerId(),
            result.getConversionId(),
            result.getFromCurrency(),
            result.getToCurrency(),
            correlationId
        );

        // Update rate availability metrics
        exchangeRateMetricsService.recordRateUnavailability(
            result.getFromCurrency(),
            result.getToCurrency(),
            correlationId
        );
    }

    private void handleUnsupportedCurrencyPair(CurrencyConversionRecoveryResult result, String correlationId) {
        log.warn("Unsupported currency pair: conversionId={}, fromCurrency={}, toCurrency={}, correlationId={}",
                result.getConversionId(), result.getFromCurrency(), result.getToCurrency(), correlationId);

        // Update conversion status to unsupported
        currencyConversionService.updateConversionStatus(
            result.getConversionId(),
            ConversionStatus.UNSUPPORTED_CURRENCY_PAIR,
            String.format("Currency pair %s/%s not supported", result.getFromCurrency(), result.getToCurrency()),
            correlationId
        );

        // Send unsupported pair notification
        notificationService.sendUnsupportedCurrencyPairNotification(
            result.getCustomerId(),
            result.getConversionId(),
            result.getFromCurrency(),
            result.getToCurrency(),
            correlationId
        );

        // Queue for product team review
        productReviewQueue.add(
            ProductReviewRequest.builder()
                .conversionId(result.getConversionId())
                .fromCurrency(result.getFromCurrency())
                .toCurrency(result.getToCurrency())
                .amount(result.getOriginalAmount())
                .correlationId(correlationId)
                .reviewType(ReviewType.CURRENCY_PAIR_SUPPORT)
                .priority(Priority.LOW)
                .assignedTo("PRODUCT_TEAM")
                .requiresEngineering(false)
                .build()
        );

        // Update currency pair metrics
        currencyPairMetricsService.recordUnsupportedPair(
            result.getFromCurrency(),
            result.getToCurrency(),
            correlationId
        );
    }

    private void handleFailedConversion(CurrencyConversionRecoveryResult result, String eventId, String correlationId) {
        log.error("Currency conversion recovery failed: conversionId={}, fromCurrency={}, toCurrency={}, reason={}, correlationId={}",
                result.getConversionId(), result.getFromCurrency(), result.getToCurrency(),
                result.getFailureReason(), correlationId);

        // Update conversion status to failed
        currencyConversionService.updateConversionStatus(
            result.getConversionId(),
            ConversionStatus.FAILED,
            result.getFailureReason(),
            correlationId
        );

        // Escalate to treasury operations
        treasuryOperationsEscalationService.escalateConversionFailure(
            result.getConversionId(),
            result.getFromCurrency(),
            result.getToCurrency(),
            result.getOriginalAmount(),
            result.getFailureReason(),
            eventId,
            correlationId,
            EscalationPriority.MEDIUM
        );

        // Send failure notification
        notificationService.sendCurrencyConversionFailureNotification(
            result.getCustomerId(),
            result.getConversionId(),
            result.getFailureReason(),
            correlationId
        );

        // Create support ticket for customer
        supportTicketService.createTicket(
            TicketType.CURRENCY_CONVERSION_FAILURE,
            result.getCustomerId(),
            String.format("Currency conversion failed: %s", result.getFailureReason()),
            Priority.MEDIUM,
            correlationId
        );

        // Refund original amount if applicable
        if (result.hasDebitedAmount()) {
            refundService.processConversionRefund(
                result.getAccountId(),
                result.getFromCurrency(),
                result.getOriginalAmount(),
                "Currency conversion failure refund",
                correlationId
            );
        }
    }

    private void updateCurrencyConversionMetrics(CurrencyConversionRecoveryResult result, String correlationId) {
        // Record conversion processing metrics
        currencyConversionMetricsService.recordProcessing(
            result.getFromCurrency(),
            result.getToCurrency(),
            result.getConversionStatus(),
            result.getOriginalAmount(),
            result.getConvertedAmount(),
            result.getConversionTime(),
            correlationId
        );

        // Update currency pair volume metrics
        currencyPairVolumeMetricsService.recordVolume(
            result.getFromCurrency(),
            result.getToCurrency(),
            result.getOriginalAmount(),
            correlationId
        );

        // Update exchange rate spread metrics
        if (result.getExchangeRate() != null) {
            exchangeRateSpreadMetricsService.recordSpread(
                result.getFromCurrency(),
                result.getToCurrency(),
                result.getExchangeRate(),
                correlationId
            );
        }
    }

    private void executeFinancialTransactionEmergencyProtocol(ConsumerRecord<String, String> record,
                                                              String topic, String exceptionMessage,
                                                              String correlationId) {
        try {
            // Execute comprehensive financial transaction emergency protocol
            FinancialTransactionEmergencyResult emergency = financialTransactionEmergencyService.execute(
                record.key(),
                record.value(),
                topic,
                exceptionMessage,
                correlationId
            );

            if (emergency.isFinancialImpactMitigated()) {
                log.info("Financial impact mitigated: correlationId={}, mitigatedConversions={}",
                        correlationId, emergency.getMitigatedConversions());

                // Apply financial mitigation measures
                financialMitigationService.applyMeasures(
                    emergency.getAffectedConversions(),
                    emergency.getMitigationMeasures(),
                    correlationId
                );
            }
        } catch (Exception e) {
            log.error("Financial transaction emergency protocol failed: correlationId={}", correlationId, e);
        }
    }

    private void storeForTreasuryOperationsReview(ConsumerRecord<String, String> record, String topic,
                                                  String exceptionMessage, String correlationId) {
        treasuryOperationsReviewRepository.save(
            TreasuryOperationsReview.builder()
                .sourceTopic(topic)
                .conversionId(extractConversionId(record.value()))
                .fromCurrency(extractFromCurrency(record.value()))
                .toCurrency(extractToCurrency(record.value()))
                .amount(extractAmount(record.value()))
                .messageKey(record.key())
                .messageValue(record.value())
                .failureReason(exceptionMessage)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .status(ReviewStatus.PENDING_TREASURY_TEAM)
                .priority(Priority.MEDIUM)
                .assignedTo("TREASURY_OPERATIONS")
                .requiresFinancialAnalysis(true)
                .build()
        );
    }

    private void sendTreasuryTeamAlert(ConsumerRecord<String, String> record, String topic,
                                       String exceptionMessage, String correlationId) {
        treasuryTeamAlertService.sendAlert(
            AlertType.CURRENCY_CONVERSION_PERMANENT_FAILURE,
            "Currency conversion permanently failed - financial transaction impact",
            Map.of(
                "topic", topic,
                "conversionId", extractConversionId(record.value()),
                "fromCurrency", extractFromCurrency(record.value()),
                "toCurrency", extractToCurrency(record.value()),
                "amount", extractAmount(record.value()).toString(),
                "error", exceptionMessage,
                "correlationId", correlationId,
                "financialImpact", "MEDIUM",
                "requiredAction", "Treasury team review required"
            )
        );
    }

    private void createFinancialImpactAssessment(ConsumerRecord<String, String> record, String correlationId) {
        financialImpactAssessmentService.create(
            FinancialImpactAssessment.builder()
                .conversionId(extractConversionId(record.value()))
                .fromCurrency(extractFromCurrency(record.value()))
                .toCurrency(extractToCurrency(record.value()))
                .amount(extractAmount(record.value()))
                .impactType(ImpactType.CURRENCY_CONVERSION_FAILURE)
                .correlationId(correlationId)
                .assessmentDate(Instant.now())
                .status(AssessmentStatus.PENDING_REVIEW)
                .priority(Priority.MEDIUM)
                .assignedTo("TREASURY_ANALYST")
                .build()
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

    private String extractConversionId(String value) {
        try {
            return objectMapper.readTree(value).get("conversionId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractFromCurrency(String value) {
        try {
            return objectMapper.readTree(value).get("fromCurrency").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractToCurrency(String value) {
        try {
            return objectMapper.readTree(value).get("toCurrency").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private BigDecimal extractAmount(String value) {
        try {
            return new BigDecimal(objectMapper.readTree(value).get("amount").asText());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}