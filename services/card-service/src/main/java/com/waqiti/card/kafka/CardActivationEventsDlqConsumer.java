package com.waqiti.card.kafka;

import com.waqiti.common.dlq.BaseDlqConsumer;
import com.waqiti.card.service.CardService;
import com.waqiti.card.service.CardSecurityService;
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
public class CardActivationEventsDlqConsumer extends BaseDlqConsumer {

    private final CardService cardService;
    private final CardSecurityService cardSecurityService;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Long> processedEvents = new ConcurrentHashMap<>();
    private final Counter processedCounter;
    private final Counter errorCounter;
    private final Timer processingTimer;

    public CardActivationEventsDlqConsumer(CardService cardService,
                                           CardSecurityService cardSecurityService,
                                           MeterRegistry meterRegistry) {
        super("card-activation-events-dlq");
        this.cardService = cardService;
        this.cardSecurityService = cardSecurityService;
        this.meterRegistry = meterRegistry;
        this.processedCounter = Counter.builder("card_activation_events_dlq_processed_total")
                .description("Total card activation events DLQ events processed")
                .register(meterRegistry);
        this.errorCounter = Counter.builder("card_activation_events_dlq_errors_total")
                .description("Total card activation events DLQ errors")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("card_activation_events_dlq_duration")
                .description("Card activation events DLQ processing duration")
                .register(meterRegistry);
    }

    @KafkaListener(
        topics = "card-activation-events-dlq",
        groupId = "card-service-card-activation-events-dlq-group",
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
    @CircuitBreaker(name = "card-activation-dlq", fallbackMethod = "handleCardActivationDlqFallback")
    public void handleCardActivationEventsDlq(
            ConsumerRecord<String, String> record,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) Long timestamp,
            @Header(value = "X-Card-Id", required = false) String cardId,
            @Header(value = "X-Customer-Id", required = false) String customerId,
            @Header(value = "X-Activation-Method", required = false) String activationMethod,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String eventId = generateEventId(record, topic, partition, offset);
        String correlationId = generateCorrelationId();

        try {
            if (isAlreadyProcessed(eventId)) {
                log.debug("Card activation event already processed: {}", eventId);
                acknowledgment.acknowledge();
                return;
            }

            log.info("Processing card activation DLQ event: topic={}, partition={}, offset={}, key={}, " +
                    "correlationId={}, cardId={}, customerId={}, activationMethod={}",
                     topic, partition, offset, record.key(), correlationId, cardId, customerId, activationMethod);

            String activationData = record.value();
            validateCardActivationData(activationData, eventId);

            // Perform security validation before processing
            performSecurityValidation(activationData, correlationId);

            // Process card activation DLQ with security checks
            CardActivationRecoveryResult result = cardService.processCardActivationEventsDlq(
                activationData,
                record.key(),
                correlationId,
                cardId,
                customerId,
                activationMethod,
                Instant.ofEpochMilli(timestamp != null ? timestamp : System.currentTimeMillis())
            );

            // Handle recovery result based on activation status
            if (result.isActivated()) {
                handleSuccessfulActivation(result, correlationId);
            } else if (result.isSecurityBlocked()) {
                handleSecurityBlocked(result, correlationId);
            } else {
                handleFailedActivation(result, eventId, correlationId);
            }

            markAsProcessed(eventId);
            processedCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed card activation DLQ: eventId={}, cardId={}, " +
                    "correlationId={}, activationStatus={}",
                    eventId, result.getCardId(), correlationId, result.getActivationStatus());

        } catch (ValidationException e) {
            errorCounter.increment();
            log.error("Validation error in card activation DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleValidationFailure(record, e, correlationId);
            acknowledgment.acknowledge();
        } catch (SecurityException e) {
            errorCounter.increment();
            log.error("Security violation in card activation DLQ: eventId={}, correlationId={}, error={}",
                     eventId, correlationId, e.getMessage());
            handleSecurityViolation(record, e, correlationId);
            throw e; // Security violations must be retried
        } catch (RecoverableException e) {
            errorCounter.increment();
            log.warn("Recoverable error in card activation DLQ: eventId={}, correlationId={}, error={}",
                    eventId, correlationId, e.getMessage());
            throw e;
        } catch (Exception e) {
            errorCounter.increment();
            log.error("Critical error in card activation DLQ: eventId={}, correlationId={}",
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
        log.error("Card activation event sent to DLT - CUSTOMER IMPACT: " +
                 "topic={}, originalPartition={}, originalOffset={}, correlationId={}, error={}",
                 topic, originalPartition, originalOffset, correlationId, exceptionMessage);

        // Execute customer impact mitigation protocol
        executeCustomerImpactMitigationProtocol(record, topic, exceptionMessage, correlationId);

        // Store for manual card operations review
        storeForManualCardOperationsReview(record, topic, exceptionMessage, correlationId);

        // Send customer service alert
        sendCustomerServiceAlert(record, topic, exceptionMessage, correlationId);

        // Update DLT metrics
        Counter.builder("card_activation_events_dlt_events_total")
                .description("Total card activation events sent to DLT")
                .tag("topic", topic)
                .tag("customer_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    public void handleCardActivationDlqFallback(
            ConsumerRecord<String, String> record,
            String topic, int partition, long offset, Long timestamp,
            String cardId, String customerId, String activationMethod,
            Acknowledgment acknowledgment, Exception ex) {

        String correlationId = generateCorrelationId();
        log.error("Circuit breaker activated for card activation DLQ: correlationId={}, error={}",
                 correlationId, ex.getMessage());

        // Store in customer service priority queue
        storeInCustomerServicePriorityQueue(record, correlationId);

        // Send customer service manager alert
        sendCustomerServiceManagerAlert(correlationId, ex);

        // Acknowledge to prevent blocking
        acknowledgment.acknowledge();

        // Update circuit breaker metrics
        Counter.builder("card_activation_dlq_circuit_breaker_activations_total")
                .tag("customer_impact", "high")
                .register(meterRegistry)
                .increment();
    }

    private void validateCardActivationData(String activationData, String eventId) {
        if (activationData == null || activationData.trim().isEmpty()) {
            throw new ValidationException("Card activation data is null or empty for eventId: " + eventId);
        }

        if (!activationData.contains("cardId")) {
            throw new ValidationException("Card activation data missing cardId for eventId: " + eventId);
        }

        if (!activationData.contains("customerId")) {
            throw new ValidationException("Card activation data missing customerId for eventId: " + eventId);
        }

        if (!activationData.contains("activationMethod")) {
            throw new ValidationException("Card activation data missing activationMethod for eventId: " + eventId);
        }

        // Validate PCI compliance requirements
        validatePciCompliance(activationData, eventId);
    }

    private void validatePciCompliance(String activationData, String eventId) {
        // Ensure no sensitive card data is present in activation data
        if (activationData.contains("pan") || activationData.contains("cardNumber")) {
            throw new SecurityException("PCI violation: Card number detected in activation data for eventId: " + eventId);
        }

        if (activationData.contains("cvv") || activationData.contains("securityCode")) {
            throw new SecurityException("PCI violation: CVV detected in activation data for eventId: " + eventId);
        }

        if (!activationData.contains("tokenizedCardRef")) {
            log.warn("Card activation data should use tokenized references for PCI compliance: eventId={}", eventId);
        }
    }

    private void performSecurityValidation(String activationData, String correlationId) {
        try {
            SecurityValidationResult securityCheck = cardSecurityService.validateActivation(
                activationData,
                correlationId
            );

            if (!securityCheck.isValid()) {
                throw new SecurityException("Card security validation failed: " + securityCheck.getFailureReason());
            }

            if (securityCheck.requiresFraudCheck()) {
                log.warn("Card activation requires additional fraud verification: correlationId={}", correlationId);
                // Flag for additional verification without failing
            }

        } catch (Exception e) {
            log.error("Card security validation error: correlationId={}", correlationId, e);
            throw new SecurityException("Card security validation failed: " + e.getMessage(), e);
        }
    }

    private void handleSuccessfulActivation(CardActivationRecoveryResult result, String correlationId) {
        log.info("Card successfully activated: cardId={}, customerId={}, correlationId={}",
                result.getCardId(), result.getCustomerId(), correlationId);

        // Update card status
        cardService.updateCardStatus(
            result.getCardId(),
            CardStatus.ACTIVE,
            correlationId
        );

        // Send activation confirmation
        notificationService.sendCardActivationConfirmation(
            result.getCustomerId(),
            result.getCardId(),
            result.getActivationMethod(),
            correlationId
        );

        // Update customer card metrics
        customerCardMetricsService.recordActivation(
            result.getCustomerId(),
            result.getCardType(),
            result.getActivationMethod(),
            correlationId
        );

        // Enable card for transactions
        cardTransactionService.enableCard(
            result.getCardId(),
            result.getDefaultLimits(),
            correlationId
        );

        // Clear any pending activation alerts
        alertService.clearPendingAlerts(result.getCardId(), AlertType.CARD_ACTIVATION_PENDING);
    }

    private void handleSecurityBlocked(CardActivationRecoveryResult result, String correlationId) {
        log.warn("Card activation blocked for security: cardId={}, customerId={}, reason={}, correlationId={}",
                result.getCardId(), result.getCustomerId(), result.getSecurityBlockReason(), correlationId);

        // Update card status to security hold
        cardService.updateCardStatus(
            result.getCardId(),
            CardStatus.SECURITY_HOLD,
            correlationId
        );

        // Create security incident
        cardSecurityIncidentService.createIncident(
            IncidentType.CARD_ACTIVATION_SECURITY_BLOCK,
            result.getCardId(),
            result.getCustomerId(),
            result.getSecurityBlockReason(),
            correlationId,
            Severity.MEDIUM
        );

        // Send security hold notification
        notificationService.sendCardSecurityHoldNotification(
            result.getCustomerId(),
            result.getCardId(),
            result.getSecurityBlockReason(),
            correlationId
        );

        // Queue for manual security review
        securityReviewQueue.add(
            SecurityReviewItem.builder()
                .cardId(result.getCardId())
                .customerId(result.getCustomerId())
                .blockReason(result.getSecurityBlockReason())
                .correlationId(correlationId)
                .priority(Priority.MEDIUM)
                .reviewType(ReviewType.CARD_ACTIVATION_SECURITY)
                .assignedTo("CARD_SECURITY_TEAM")
                .build()
        );
    }

    private void handleFailedActivation(CardActivationRecoveryResult result, String eventId, String correlationId) {
        log.error("Card activation recovery failed: cardId={}, customerId={}, reason={}, correlationId={}",
                result.getCardId(), result.getCustomerId(), result.getFailureReason(), correlationId);

        // Update card status to activation failed
        cardService.updateCardStatus(
            result.getCardId(),
            CardStatus.ACTIVATION_FAILED,
            correlationId
        );

        // Escalate to customer service
        customerServiceEscalationService.escalateCardActivationFailure(
            result.getCardId(),
            result.getCustomerId(),
            result.getFailureReason(),
            eventId,
            correlationId,
            EscalationPriority.HIGH
        );

        // Send failure notification to customer
        notificationService.sendCardActivationFailureNotification(
            result.getCustomerId(),
            result.getCardId(),
            result.getFailureReason(),
            correlationId
        );

        // Create customer service ticket
        customerServiceTicketService.createTicket(
            TicketType.CARD_ACTIVATION_FAILURE,
            result.getCustomerId(),
            String.format("Card activation failed: %s", result.getFailureReason()),
            Priority.HIGH,
            correlationId
        );

        // Queue for manual activation
        manualActivationQueue.add(
            ManualActivationRequest.builder()
                .cardId(result.getCardId())
                .customerId(result.getCustomerId())
                .failureReason(result.getFailureReason())
                .eventId(eventId)
                .correlationId(correlationId)
                .priority(Priority.HIGH)
                .assignedTo("CARD_OPERATIONS_TEAM")
                .requiresCustomerContact(true)
                .build()
        );
    }

    private void handleSecurityViolation(ConsumerRecord<String, String> record,
                                        SecurityException e, String correlationId) {
        // Create card security violation record
        cardSecurityViolationRepository.save(
            CardSecurityViolation.builder()
                .cardId(extractCardId(record.value()))
                .customerId(extractCustomerId(record.value()))
                .violationType(ViolationType.CARD_ACTIVATION_SECURITY)
                .description(e.getMessage())
                .correlationId(correlationId)
                .severity(Severity.HIGH)
                .timestamp(Instant.now())
                .requiresInvestigation(true)
                .source("card-activation-events-dlq")
                .pciRelevant(true)
                .build()
        );

        // Send immediate security team alert
        cardSecurityAlertService.sendCriticalAlert(
            CardSecurityAlertType.ACTIVATION_SECURITY_VIOLATION,
            e.getMessage(),
            correlationId
        );

        // Temporarily block card if it exists
        String cardId = extractCardId(record.value());
        if (cardId != null && !cardId.equals("unknown")) {
            cardService.temporaryBlock(
                cardId,
                BlockReason.SECURITY_VIOLATION,
                Duration.ofHours(24),
                correlationId
            );
        }
    }

    private void executeCustomerImpactMitigationProtocol(ConsumerRecord<String, String> record,
                                                          String topic, String exceptionMessage,
                                                          String correlationId) {
        try {
            // Execute customer impact mitigation
            CustomerImpactMitigationResult mitigation = customerImpactMitigationService.execute(
                record.key(),
                record.value(),
                topic,
                exceptionMessage,
                correlationId
            );

            if (mitigation.isMitigated()) {
                log.info("Customer impact mitigated: correlationId={}, mitigationActions={}",
                        correlationId, mitigation.getMitigationActions());

                // Apply mitigation actions
                mitigationActionsService.applyActions(
                    mitigation.getCustomerId(),
                    mitigation.getMitigationActions(),
                    correlationId
                );
            }
        } catch (Exception e) {
            log.error("Customer impact mitigation failed: correlationId={}", correlationId, e);
        }
    }

    private void storeForManualCardOperationsReview(ConsumerRecord<String, String> record, String topic,
                                                    String exceptionMessage, String correlationId) {
        manualCardOperationsReviewRepository.save(
            ManualCardOperationsReview.builder()
                .sourceTopic(topic)
                .cardId(extractCardId(record.value()))
                .customerId(extractCustomerId(record.value()))
                .messageKey(record.key())
                .messageValue(record.value())
                .failureReason(exceptionMessage)
                .correlationId(correlationId)
                .timestamp(Instant.now())
                .status(ReviewStatus.PENDING_CARD_OPERATIONS)
                .priority(Priority.HIGH)
                .customerImpact(CustomerImpact.HIGH)
                .assignedTo("CARD_OPERATIONS_MANAGER")
                .requiresCustomerContact(true)
                .build()
        );
    }

    private void sendCustomerServiceAlert(ConsumerRecord<String, String> record, String topic,
                                          String exceptionMessage, String correlationId) {
        customerServiceAlertService.sendCriticalAlert(
            AlertType.CARD_ACTIVATION_PERMANENT_FAILURE,
            "Card activation permanently failed - customer impact",
            Map.of(
                "topic", topic,
                "cardId", extractCardId(record.value()),
                "customerId", extractCustomerId(record.value()),
                "error", exceptionMessage,
                "correlationId", correlationId,
                "customerImpact", "HIGH",
                "requiredAction", "Immediate customer service intervention required",
                "slaRisk", "Customer satisfaction at risk"
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

    private String extractCardId(String value) {
        try {
            return objectMapper.readTree(value).get("cardId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String extractCustomerId(String value) {
        try {
            return objectMapper.readTree(value).get("customerId").asText();
        } catch (Exception e) {
            return "unknown";
        }
    }
}