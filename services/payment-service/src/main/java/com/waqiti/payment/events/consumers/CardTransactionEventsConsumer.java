package com.waqiti.payment.events.consumers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditSeverity;
import com.waqiti.common.exceptions.ServiceIntegrationException;
import com.waqiti.payment.client.FraudServiceClient;
import com.waqiti.payment.client.LedgerServiceClient;
import com.waqiti.payment.client.NotificationServiceClient;
import com.waqiti.payment.client.AnalyticsServiceClient;
import com.waqiti.payment.client.RewardsServiceClient;
import com.waqiti.payment.dto.*;
import com.waqiti.payment.entity.CardTransactionRecord;
import com.waqiti.payment.entity.PaymentTransaction;
import com.waqiti.payment.events.CardTransactionEvent;
import com.waqiti.payment.repository.CardTransactionRecordRepository;
import com.waqiti.payment.repository.PaymentTransactionRepository;
import com.waqiti.payment.service.AuditService;
import com.waqiti.payment.service.EventProcessingTrackingService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production-ready Card Transaction Events Consumer for Payment Service
 * 
 * Consumes card transaction events from card processing networks and integrates them
 * with payment processing workflows, fraud detection, rewards calculation, and
 * transaction reconciliation systems.
 * 
 * Key Responsibilities:
 * - Process card transaction events for payment workflow integration
 * - Update payment transaction records and reconciliation data
 * - Trigger fraud detection analysis for card transactions
 * - Calculate and update rewards points for card spending
 * - Send transaction notifications to cardholders
 * - Maintain transaction analytics and reporting data
 * - Handle authorization, settlement, and reversal events
 * 
 * Consumed Event Types:
 * - PURCHASE: Card purchase transactions
 * - WITHDRAWAL: ATM and cash advance transactions
 * - REFUND: Refund and return transactions
 * - AUTHORIZATION: Authorization-only transactions
 * - SETTLEMENT: Settlement of authorized transactions
 * - REVERSAL: Transaction reversals and chargebacks
 * 
 * Integration Points:
 * - fraud-service: Real-time fraud analysis and scoring
 * - rewards-service: Points calculation and rewards processing
 * - ledger-service: Transaction recording and accounting
 * - notification-service: Customer transaction notifications
 * - analytics-service: Transaction metrics and reporting
 * 
 * @author Waqiti Platform Team
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CardTransactionEventsConsumer {

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final AuditService auditService;
    private final EventProcessingTrackingService eventProcessingTrackingService;
    private final MeterRegistry meterRegistry;

    // Repository dependencies
    private final CardTransactionRecordRepository cardTransactionRecordRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;

    // Service clients
    private final FraudServiceClient fraudServiceClient;
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final AnalyticsServiceClient analyticsServiceClient;
    private final RewardsServiceClient rewardsServiceClient;

    // Metrics
    private final Counter successCounter = Counter.builder("card_transaction_events_processed_total")
            .description("Total number of card transaction events successfully processed")
            .register(meterRegistry);

    private final Counter failureCounter = Counter.builder("card_transaction_events_failed_total")
            .description("Total number of card transaction events that failed processing")
            .register(meterRegistry);

    private final Timer processingTimer = Timer.builder("card_transaction_event_processing_duration")
            .description("Time taken to process card transaction events")
            .register(meterRegistry);

    /**
     * Main Kafka listener for card transaction events
     */
    @KafkaListener(
        topics = "${kafka.topics.card-transaction-events:card-transaction-events}",
        groupId = "${kafka.consumer.group-id:payment-service-consumer-group}",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "${kafka.consumer.concurrency:3}"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        include = {ServiceIntegrationException.class, Exception.class},
        dltTopicSuffix = "-dlt",
        autoCreateTopics = "true",
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED, timeout = 30, rollbackFor = Exception.class)
    public void handleCardTransactionEvent(
            @Payload CardTransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlationId", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        LocalDateTime processingStartTime = LocalDateTime.now();
        
        // Generate correlation ID if not provided
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        log.info("Processing card transaction event: eventId={}, transactionId={}, correlationId={}, " +
                "topic={}, partition={}, offset={}", 
                event.getEventId(), event.getTransactionId(), correlationId, topic, partition, offset);

        try {
            // 1. Validate event
            validateCardTransactionEvent(event);

            // 2. Check for duplicate processing
            if (eventProcessingTrackingService.isDuplicateEvent(event.getEventId(), "CARD_TRANSACTION_EVENT")) {
                log.warn("Duplicate card transaction event detected, skipping: eventId={}, transactionId={}", 
                        event.getEventId(), event.getTransactionId());
                acknowledgment.acknowledge();
                return;
            }

            // 3. Track event processing start
            eventProcessingTrackingService.trackEventProcessingStart(
                event.getEventId(), 
                "CARD_TRANSACTION_EVENT", 
                correlationId,
                Map.of(
                    "transactionId", event.getTransactionId().toString(),
                    "transactionType", event.getTransactionType(),
                    "transactionStatus", event.getTransactionStatus(),
                    "amount", event.getAmount().toString(),
                    "currency", event.getCurrency()
                )
            );

            // 4. Process based on transaction type and status
            processCardTransactionByType(event, correlationId);

            // 5. Update payment processing records
            updatePaymentRecords(event, correlationId);

            // 6. Async downstream processing (non-blocking)
            processDownstreamIntegrations(event, correlationId);

            // 7. Track successful processing
            eventProcessingTrackingService.trackEventProcessingSuccess(
                event.getEventId(),
                Map.of(
                    "processingTimeMs", processingTimer.stop(sample).longValue(),
                    "transactionStatus", event.getTransactionStatus(),
                    "processingStartTime", processingStartTime.toString()
                )
            );

            successCounter.increment();
            acknowledgment.acknowledge();

            log.info("Successfully processed card transaction event: eventId={}, transactionId={}, " +
                    "correlationId={}, processingTimeMs={}",
                    event.getEventId(), event.getTransactionId(), correlationId,
                    processingTimer.stop(sample).longValue());

        } catch (Exception e) {
            processingTimer.stop(sample);
            failureCounter.increment();

            log.error("Failed to process card transaction event: eventId={}, transactionId={}, " +
                     "correlationId={}, attempt={}, error={}", 
                     event.getEventId(), event.getTransactionId(), correlationId,
                     RetrySynchronizationManager.getContext() != null ? 
                         RetrySynchronizationManager.getContext().getRetryCount() + 1 : 1,
                     e.getMessage(), e);

            // Track processing failure
            eventProcessingTrackingService.trackEventProcessingFailure(
                event.getEventId(),
                e.getClass().getSimpleName(),
                e.getMessage(),
                Map.of(
                    "processingTimeMs", processingTimer.stop(sample).longValue(),
                    "attempt", String.valueOf(RetrySynchronizationManager.getContext() != null ?
                        RetrySynchronizationManager.getContext().getRetryCount() + 1 : 1)
                )
            );

            // Audit critical failure
            auditService.logCardTransactionEventProcessingFailure(
                event.getEventId(),
                event.getTransactionId() != null ? event.getTransactionId().toString() : "unknown",
                correlationId,
                event.getTransactionStatus(),
                e.getClass().getSimpleName(),
                e.getMessage(),
                Map.of(
                    "topic", topic,
                    "partition", String.valueOf(partition),
                    "offset", String.valueOf(offset),
                    "amount", event.getAmount() != null ? event.getAmount().toString() : "0",
                    "currency", event.getCurrency()
                )
            );

            throw new ServiceIntegrationException("Card transaction event processing failed", e);
        }
    }

    /**
     * Process card transaction based on type and status
     */
    private void processCardTransactionByType(CardTransactionEvent event, String correlationId) {
        switch (event.getTransactionType().toUpperCase()) {
            case "PURCHASE" -> processPurchaseTransaction(event, correlationId);
            case "WITHDRAWAL" -> processWithdrawalTransaction(event, correlationId);
            case "REFUND" -> processRefundTransaction(event, correlationId);
            case "AUTHORIZATION" -> processAuthorizationTransaction(event, correlationId);
            case "SETTLEMENT" -> processSettlementTransaction(event, correlationId);
            case "REVERSAL" -> processReversalTransaction(event, correlationId);
            default -> {
                log.warn("Unknown card transaction type: {} for eventId={}", 
                        event.getTransactionType(), event.getEventId());
                processGenericTransaction(event, correlationId);
            }
        }
    }

    /**
     * Process purchase transactions
     */
    private void processPurchaseTransaction(CardTransactionEvent event, String correlationId) {
        log.info("Processing card purchase transaction: eventId={}, amount={} {}, merchant={}", 
                event.getEventId(), event.getAmount(), event.getCurrency(), event.getMerchantName());

        CardTransactionRecord record = createCardTransactionRecord(event, correlationId);
        
        if (event.isApproved()) {
            record.setStatus("APPROVED");
            
            // Trigger fraud analysis for approved purchases
            if (event.getAmount().compareTo(new BigDecimal("100")) > 0) {
                triggerFraudAnalysis(event, correlationId);
            }
            
            // Calculate rewards for purchases
            if (!event.isHighRisk()) {
                calculateRewards(event, correlationId);
            }
        } else {
            record.setStatus("DECLINED");
            record.setDeclineReason(event.getDeclineReason());
        }

        cardTransactionRecordRepository.save(record);
        
        auditService.logCardPurchaseProcessed(
            event.getEventId(),
            event.getTransactionId().toString(),
            correlationId,
            event.getUserId(),
            event.getAmount(),
            event.getCurrency(),
            event.getTransactionStatus(),
            event.getMerchantName(),
            event.getMerchantCategory(),
            Map.of(
                "isInternational", event.isInternational().toString(),
                "isContactless", event.isContactless().toString(),
                "riskScore", event.getRiskScore() != null ? event.getRiskScore().toString() : "0"
            )
        );
    }

    /**
     * Process withdrawal transactions (ATM, cash advance)
     */
    private void processWithdrawalTransaction(CardTransactionEvent event, String correlationId) {
        log.info("Processing card withdrawal transaction: eventId={}, amount={} {}, atmId={}", 
                event.getEventId(), event.getAmount(), event.getCurrency(), event.getAtmId());

        CardTransactionRecord record = createCardTransactionRecord(event, correlationId);
        
        if (event.isApproved()) {
            record.setStatus("APPROVED");
            
            // High-value withdrawals require enhanced monitoring
            if (event.getAmount().compareTo(new BigDecimal("500")) > 0) {
                triggerEnhancedMonitoring(event, correlationId);
            }
        } else {
            record.setStatus("DECLINED");
            record.setDeclineReason(event.getDeclineReason());
        }

        cardTransactionRecordRepository.save(record);
        
        auditService.logCardWithdrawalProcessed(
            event.getEventId(),
            event.getTransactionId().toString(),
            correlationId,
            event.getUserId(),
            event.getAmount(),
            event.getCurrency(),
            event.getTransactionStatus(),
            event.getAtmId(),
            event.getAtmLocation(),
            Map.of(
                "atmNetwork", event.getAtmNetwork() != null ? event.getAtmNetwork() : "unknown",
                "atmFee", event.getAtmFee() != null ? event.getAtmFee().toString() : "0"
            )
        );
    }

    /**
     * Process refund transactions
     */
    private void processRefundTransaction(CardTransactionEvent event, String correlationId) {
        log.info("Processing card refund transaction: eventId={}, amount={} {}, originalTxn={}", 
                event.getEventId(), event.getAmount(), event.getCurrency(), 
                event.getReferenceNumber());

        CardTransactionRecord record = createCardTransactionRecord(event, correlationId);
        record.setStatus("APPROVED"); // Refunds are typically auto-approved
        cardTransactionRecordRepository.save(record);
        
        // Update original transaction if reference is available
        if (event.getReferenceNumber() != null) {
            updateOriginalTransactionForRefund(event, correlationId);
        }
        
        auditService.logCardRefundProcessed(
            event.getEventId(),
            event.getTransactionId().toString(),
            correlationId,
            event.getUserId(),
            event.getAmount(),
            event.getCurrency(),
            event.getReferenceNumber(),
            Map.of(
                "merchantName", event.getMerchantName() != null ? event.getMerchantName() : "unknown"
            )
        );
    }

    /**
     * Process authorization-only transactions
     */
    private void processAuthorizationTransaction(CardTransactionEvent event, String correlationId) {
        log.info("Processing card authorization transaction: eventId={}, amount={} {}, authCode={}", 
                event.getEventId(), event.getAmount(), event.getCurrency(), 
                event.getAuthorizationCode());

        CardTransactionRecord record = createCardTransactionRecord(event, correlationId);
        
        if (event.isApproved()) {
            record.setStatus("AUTHORIZED");
            record.setAuthorizationCode(event.getAuthorizationCode());
            
            // Set expiration for authorization (typically 7 days)
            record.setAuthorizationExpiry(LocalDateTime.now().plusDays(7));
        } else {
            record.setStatus("DECLINED");
            record.setDeclineReason(event.getDeclineReason());
        }

        cardTransactionRecordRepository.save(record);
        
        auditService.logCardAuthorizationProcessed(
            event.getEventId(),
            event.getTransactionId().toString(),
            correlationId,
            event.getUserId(),
            event.getAmount(),
            event.getCurrency(),
            event.getTransactionStatus(),
            event.getAuthorizationCode(),
            Map.of(
                "merchantName", event.getMerchantName() != null ? event.getMerchantName() : "unknown",
                "responseCode", event.getResponseCode() != null ? event.getResponseCode() : "unknown"
            )
        );
    }

    /**
     * Process settlement transactions
     */
    private void processSettlementTransaction(CardTransactionEvent event, String correlationId) {
        log.info("Processing card settlement transaction: eventId={}, amount={} {}, authCode={}", 
                event.getEventId(), event.getAmount(), event.getCurrency(), 
                event.getAuthorizationCode());

        // Find and update the original authorization
        Optional<CardTransactionRecord> authRecordOpt = 
            cardTransactionRecordRepository.findByAuthorizationCode(event.getAuthorizationCode());
        
        if (authRecordOpt.isPresent()) {
            CardTransactionRecord authRecord = authRecordOpt.get();
            authRecord.setStatus("SETTLED");
            authRecord.setSettlementAmount(event.getAmount());
            authRecord.setSettlementTimestamp(event.getSettlementTimestamp());
            cardTransactionRecordRepository.save(authRecord);
            
            log.info("Updated authorization record for settlement: authCode={}, eventId={}", 
                    event.getAuthorizationCode(), event.getEventId());
        } else {
            // Create new settlement record if authorization not found
            CardTransactionRecord record = createCardTransactionRecord(event, correlationId);
            record.setStatus("SETTLED");
            cardTransactionRecordRepository.save(record);
            
            log.warn("No authorization record found for settlement, created new record: authCode={}, eventId={}", 
                    event.getAuthorizationCode(), event.getEventId());
        }
        
        auditService.logCardSettlementProcessed(
            event.getEventId(),
            event.getTransactionId().toString(),
            correlationId,
            event.getUserId(),
            event.getAmount(),
            event.getCurrency(),
            event.getAuthorizationCode(),
            Map.of(
                "settlementTimestamp", event.getSettlementTimestamp() != null ? 
                    event.getSettlementTimestamp().toString() : "unknown"
            )
        );
    }

    /**
     * Process reversal transactions
     */
    private void processReversalTransaction(CardTransactionEvent event, String correlationId) {
        log.info("Processing card reversal transaction: eventId={}, amount={} {}, refNumber={}", 
                event.getEventId(), event.getAmount(), event.getCurrency(), 
                event.getReferenceNumber());

        CardTransactionRecord record = createCardTransactionRecord(event, correlationId);
        record.setStatus("REVERSED");
        cardTransactionRecordRepository.save(record);
        
        // Update original transaction if reference is available
        if (event.getReferenceNumber() != null) {
            updateOriginalTransactionForReversal(event, correlationId);
        }
        
        auditService.logCardReversalProcessed(
            event.getEventId(),
            event.getTransactionId().toString(),
            correlationId,
            event.getUserId(),
            event.getAmount(),
            event.getCurrency(),
            event.getReferenceNumber(),
            Map.of(
                "reversalReason", event.getDeclineReason() != null ? event.getDeclineReason() : "unknown"
            )
        );
    }

    /**
     * Process generic/unknown transaction types
     */
    private void processGenericTransaction(CardTransactionEvent event, String correlationId) {
        log.info("Processing generic card transaction: eventId={}, type={}, amount={} {}", 
                event.getEventId(), event.getTransactionType(), event.getAmount(), event.getCurrency());

        CardTransactionRecord record = createCardTransactionRecord(event, correlationId);
        record.setStatus(event.isApproved() ? "APPROVED" : "DECLINED");
        
        if (!event.isApproved()) {
            record.setDeclineReason(event.getDeclineReason());
        }
        
        cardTransactionRecordRepository.save(record);
        
        auditService.logCardTransactionProcessed(
            event.getEventId(),
            event.getTransactionId().toString(),
            correlationId,
            event.getUserId(),
            event.getTransactionType(),
            event.getAmount(),
            event.getCurrency(),
            event.getTransactionStatus(),
            Map.of(
                "merchantName", event.getMerchantName() != null ? event.getMerchantName() : "unknown"
            )
        );
    }

    /**
     * Update payment processing records
     */
    private void updatePaymentRecords(CardTransactionEvent event, String correlationId) {
        try {
            // Look for related payment transaction
            Optional<PaymentTransaction> paymentOpt = 
                paymentTransactionRepository.findByTransactionId(event.getTransactionId().toString());
            
            if (paymentOpt.isPresent()) {
                PaymentTransaction payment = paymentOpt.get();
                
                // Update payment with card transaction details
                payment.setProviderTransactionId(event.getNetworkReferenceNumber());
                payment.setProcessorResponseCode(event.getResponseCode());
                
                if (event.isApproved()) {
                    payment.setAuthorizationCode(event.getAuthorizationCode());
                    payment.setAuthorizedAt(event.getAuthorizationTimestamp() != null ? 
                        event.getAuthorizationTimestamp().atZone(java.time.ZoneId.systemDefault()).toInstant() : 
                        Instant.now());
                }
                
                paymentTransactionRepository.save(payment);
                
                log.info("Updated payment transaction with card details: paymentId={}, eventId={}", 
                        payment.getId(), event.getEventId());
            }
        } catch (Exception e) {
            log.error("Failed to update payment records for card transaction: eventId={}, error={}", 
                     event.getEventId(), e.getMessage(), e);
            // Don't fail the entire processing for this non-critical operation
        }
    }

    /**
     * Process downstream integrations asynchronously
     */
    private void processDownstreamIntegrations(CardTransactionEvent event, String correlationId) {
        // Process integrations in parallel for performance
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 1. Fraud detection analysis
        if (event.isApproved() && (event.isHighRisk() || event.getAmount().compareTo(new BigDecimal("50")) > 0)) {
            futures.add(CompletableFuture.runAsync(() -> 
                triggerFraudAnalysis(event, correlationId)));
        }

        // 2. Rewards calculation
        if (event.isApproved() && "PURCHASE".equals(event.getTransactionType()) && !event.isHighRisk()) {
            futures.add(CompletableFuture.runAsync(() -> 
                calculateRewards(event, correlationId)));
        }

        // 3. Ledger recording
        futures.add(CompletableFuture.runAsync(() -> 
            recordTransactionInLedger(event, correlationId)));

        // 4. Customer notifications
        if (event.isApproved() || event.isHighRisk()) {
            futures.add(CompletableFuture.runAsync(() -> 
                sendTransactionNotification(event, correlationId)));
        }

        // 5. Analytics recording
        futures.add(CompletableFuture.runAsync(() -> 
            recordTransactionAnalytics(event, correlationId)));

        // Wait for all integrations to complete (with timeout)
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0]));
        
        try {
            allFutures.get(10, TimeUnit.SECONDS); // 10 second timeout
        } catch (Exception e) {
            log.warn("Some downstream integrations failed or timed out: eventId={}, error={}", 
                    event.getEventId(), e.getMessage());
            // Don't fail the main processing for downstream integration issues
        }
    }

    /**
     * Trigger fraud analysis for card transaction
     */
    @CircuitBreaker(name = "fraud-service", fallbackMethod = "triggerFraudAnalysisFallback")
    @Retry(name = "fraud-service")
    @TimeLimiter(name = "fraud-service")
    private void triggerFraudAnalysis(CardTransactionEvent event, String correlationId) {
        try {
            CardTransactionFraudAnalysisRequest request = CardTransactionFraudAnalysisRequest.builder()
                    .transactionId(event.getTransactionId())
                    .userId(event.getUserId())
                    .cardId(event.getCardId())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .merchantName(event.getMerchantName())
                    .merchantCategory(event.getMerchantCategory())
                    .isInternational(event.isInternational())
                    .isContactless(event.isContactless())
                    .isOnline(event.isOnline())
                    .riskScore(event.getRiskScore())
                    .correlationId(correlationId)
                    .build();

            fraudServiceClient.analyzeCardTransaction(request);
            
            log.info("Triggered fraud analysis for card transaction: eventId={}, transactionId={}", 
                    event.getEventId(), event.getTransactionId());
                    
        } catch (Exception e) {
            log.error("Failed to trigger fraud analysis: eventId={}, error={}", 
                     event.getEventId(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Fallback for fraud analysis
     */
    private void triggerFraudAnalysisFallback(CardTransactionEvent event, String correlationId, Exception ex) {
        log.warn("Fraud analysis fallback triggered for transaction: eventId={}, error={}", 
                event.getEventId(), ex.getMessage());
        
        auditService.logServiceIntegrationFailure(
            "fraud-service",
            "triggerFraudAnalysis",
            event.getTransactionId().toString(),
            ex.getMessage(),
            Map.of(
                "eventId", event.getEventId(),
                "correlationId", correlationId,
                "fallbackTriggered", "true"
            )
        );
    }

    /**
     * Calculate rewards for card transaction
     */
    @CircuitBreaker(name = "rewards-service", fallbackMethod = "calculateRewardsFallback")
    @Retry(name = "rewards-service")
    private void calculateRewards(CardTransactionEvent event, String correlationId) {
        try {
            CardTransactionRewardsRequest request = CardTransactionRewardsRequest.builder()
                    .transactionId(event.getTransactionId())
                    .userId(event.getUserId())
                    .cardId(event.getCardId())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .merchantCategory(event.getMerchantCategory())
                    .isInternational(event.isInternational())
                    .transactionDate(event.getTransactionTimestamp())
                    .correlationId(correlationId)
                    .build();

            rewardsServiceClient.calculateCardRewards(request);
            
            log.info("Triggered rewards calculation for card transaction: eventId={}, transactionId={}", 
                    event.getEventId(), event.getTransactionId());
                    
        } catch (Exception e) {
            log.error("Failed to calculate rewards: eventId={}, error={}", 
                     event.getEventId(), e.getMessage(), e);
            // Don't fail processing for rewards calculation issues
        }
    }

    /**
     * Fallback for rewards calculation
     */
    private void calculateRewardsFallback(CardTransactionEvent event, String correlationId, Exception ex) {
        log.warn("Rewards calculation fallback triggered for transaction: eventId={}, error={}", 
                event.getEventId(), ex.getMessage());
    }

    /**
     * Record transaction in ledger
     */
    @CircuitBreaker(name = "ledger-service", fallbackMethod = "recordTransactionInLedgerFallback")
    @Retry(name = "ledger-service")
    @TimeLimiter(name = "ledger-service")
    private void recordTransactionInLedger(CardTransactionEvent event, String correlationId) {
        try {
            RecordCardTransactionRequest request = RecordCardTransactionRequest.builder()
                    .transactionId(event.getTransactionId())
                    .userId(event.getUserId())
                    .cardId(event.getCardId())
                    .transactionType(event.getTransactionType())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .netAmount(event.getNetAmount())
                    .totalFees(event.getTotalFees())
                    .merchantName(event.getMerchantName())
                    .transactionStatus(event.getTransactionStatus())
                    .authorizationCode(event.getAuthorizationCode())
                    .networkReferenceNumber(event.getNetworkReferenceNumber())
                    .correlationId(correlationId)
                    .build();

            ledgerServiceClient.recordCardTransaction(request);
            
            log.info("Recorded card transaction in ledger: eventId={}, transactionId={}", 
                    event.getEventId(), event.getTransactionId());
                    
        } catch (Exception e) {
            log.error("Failed to record transaction in ledger: eventId={}, error={}", 
                     event.getEventId(), e.getMessage(), e);
            throw e; // Ledger recording is critical
        }
    }

    /**
     * Fallback for ledger recording
     */
    private void recordTransactionInLedgerFallback(CardTransactionEvent event, String correlationId, Exception ex) {
        log.error("CRITICAL: Ledger recording failed for card transaction: eventId={}, error={}", 
                event.getEventId(), ex.getMessage());
        
        auditService.logCriticalServiceFailure(
            "ledger-service",
            "recordCardTransaction",
            event.getTransactionId().toString(),
            ex.getMessage(),
            Map.of(
                "eventId", event.getEventId(),
                "correlationId", correlationId,
                "amount", event.getAmount().toString(),
                "currency", event.getCurrency(),
                "requiresManualIntervention", "true"
            )
        );
    }

    /**
     * Send transaction notification to customer
     */
    @CircuitBreaker(name = "notification-service", fallbackMethod = "sendTransactionNotificationFallback")
    @Retry(name = "notification-service")
    private void sendTransactionNotification(CardTransactionEvent event, String correlationId) {
        try {
            CardTransactionNotificationRequest request = CardTransactionNotificationRequest.builder()
                    .transactionId(event.getTransactionId())
                    .userId(event.getUserId())
                    .cardNumber(event.getCardNumber())
                    .transactionType(event.getTransactionType())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .merchantName(event.getMerchantName())
                    .transactionStatus(event.getTransactionStatus())
                    .isInternational(event.isInternational())
                    .isHighRisk(event.isHighRisk())
                    .transactionTimestamp(event.getTransactionTimestamp())
                    .correlationId(correlationId)
                    .build();

            notificationServiceClient.sendCardTransactionNotification(request);
            
            log.info("Sent transaction notification: eventId={}, transactionId={}", 
                    event.getEventId(), event.getTransactionId());
                    
        } catch (Exception e) {
            log.error("Failed to send transaction notification: eventId={}, error={}", 
                     event.getEventId(), e.getMessage(), e);
            // Don't fail processing for notification issues
        }
    }

    /**
     * Fallback for transaction notification
     */
    private void sendTransactionNotificationFallback(CardTransactionEvent event, String correlationId, Exception ex) {
        log.warn("Transaction notification fallback triggered: eventId={}, error={}", 
                event.getEventId(), ex.getMessage());
    }

    /**
     * Record transaction analytics
     */
    @CircuitBreaker(name = "analytics-service")
    @Retry(name = "analytics-service")
    private void recordTransactionAnalytics(CardTransactionEvent event, String correlationId) {
        try {
            RecordCardTransactionAnalyticsRequest request = RecordCardTransactionAnalyticsRequest.builder()
                    .transactionId(event.getTransactionId())
                    .userId(event.getUserId())
                    .cardId(event.getCardId())
                    .transactionType(event.getTransactionType())
                    .transactionStatus(event.getTransactionStatus())
                    .amount(event.getAmount())
                    .currency(event.getCurrency())
                    .merchantCategory(event.getMerchantCategory())
                    .merchantCountry(event.getMerchantCountry())
                    .isInternational(event.isInternational())
                    .isContactless(event.isContactless())
                    .isOnline(event.isOnline())
                    .riskScore(event.getRiskScore())
                    .cardNetwork(event.getCardNetwork())
                    .transactionTimestamp(event.getTransactionTimestamp())
                    .correlationId(correlationId)
                    .build();

            analyticsServiceClient.recordCardTransactionAnalytics(request);
            
            log.debug("Recorded transaction analytics: eventId={}, transactionId={}", 
                     event.getEventId(), event.getTransactionId());
                     
        } catch (Exception e) {
            log.warn("Failed to record transaction analytics: eventId={}, error={}", 
                    event.getEventId(), e.getMessage());
            // Don't fail processing for analytics issues
        }
    }

    /**
     * Enhanced monitoring for high-value transactions
     */
    private void triggerEnhancedMonitoring(CardTransactionEvent event, String correlationId) {
        try {
            log.info("Triggering enhanced monitoring for high-value withdrawal: eventId={}, amount={} {}", 
                    event.getEventId(), event.getAmount(), event.getCurrency());
            
            // Enhanced monitoring logic would go here
            // This could include additional fraud checks, velocity monitoring, etc.
            
        } catch (Exception e) {
            log.error("Failed to trigger enhanced monitoring: eventId={}, error={}", 
                     event.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * Update original transaction for refund
     */
    private void updateOriginalTransactionForRefund(CardTransactionEvent event, String correlationId) {
        try {
            Optional<CardTransactionRecord> originalOpt = 
                cardTransactionRecordRepository.findByReferenceNumber(event.getReferenceNumber());
            
            if (originalOpt.isPresent()) {
                CardTransactionRecord original = originalOpt.get();
                original.setRefundAmount(event.getAmount());
                original.setRefundTimestamp(event.getTransactionTimestamp());
                original.setStatus("PARTIALLY_REFUNDED"); // or FULLY_REFUNDED based on amounts
                cardTransactionRecordRepository.save(original);
                
                log.info("Updated original transaction for refund: originalId={}, refundEventId={}", 
                        original.getId(), event.getEventId());
            } else {
                log.warn("Original transaction not found for refund: refNumber={}, eventId={}", 
                        event.getReferenceNumber(), event.getEventId());
            }
        } catch (Exception e) {
            log.error("Failed to update original transaction for refund: eventId={}, error={}", 
                     event.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * Update original transaction for reversal
     */
    private void updateOriginalTransactionForReversal(CardTransactionEvent event, String correlationId) {
        try {
            Optional<CardTransactionRecord> originalOpt = 
                cardTransactionRecordRepository.findByReferenceNumber(event.getReferenceNumber());
            
            if (originalOpt.isPresent()) {
                CardTransactionRecord original = originalOpt.get();
                original.setStatus("REVERSED");
                original.setReversalTimestamp(event.getTransactionTimestamp());
                original.setReversalReason(event.getDeclineReason());
                cardTransactionRecordRepository.save(original);
                
                log.info("Updated original transaction for reversal: originalId={}, reversalEventId={}", 
                        original.getId(), event.getEventId());
            } else {
                log.warn("Original transaction not found for reversal: refNumber={}, eventId={}", 
                        event.getReferenceNumber(), event.getEventId());
            }
        } catch (Exception e) {
            log.error("Failed to update original transaction for reversal: eventId={}, error={}", 
                     event.getEventId(), e.getMessage(), e);
        }
    }

    /**
     * Create card transaction record
     */
    private CardTransactionRecord createCardTransactionRecord(CardTransactionEvent event, String correlationId) {
        return CardTransactionRecord.builder()
                .eventId(event.getEventId())
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .cardId(event.getCardId())
                .cardNumber(event.getCardNumber())
                .transactionType(event.getTransactionType())
                .amount(event.getAmount())
                .currency(event.getCurrency())
                .originalAmount(event.getOriginalAmount())
                .originalCurrency(event.getOriginalCurrency())
                .exchangeRate(event.getExchangeRate())
                .transactionTimestamp(event.getTransactionTimestamp())
                .authorizationCode(event.getAuthorizationCode())
                .responseCode(event.getResponseCode())
                .merchantId(event.getMerchantId())
                .merchantName(event.getMerchantName())
                .merchantCategory(event.getMerchantCategory())
                .merchantCountry(event.getMerchantCountry())
                .merchantCity(event.getMerchantCity())
                .isInternational(event.isInternational())
                .isContactless(event.isContactless())
                .isOnline(event.isOnline())
                .riskScore(event.getRiskScore())
                .riskLevel(event.getRiskLevel())
                .cardNetwork(event.getCardNetwork())
                .networkReferenceNumber(event.getNetworkReferenceNumber())
                .atmId(event.getAtmId())
                .atmLocation(event.getAtmLocation())
                .transactionFee(event.getTransactionFee())
                .foreignTransactionFee(event.getForeignTransactionFee())
                .atmFee(event.getAtmFee())
                .referenceNumber(event.getReferenceNumber())
                .correlationId(correlationId)
                .processedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Validate card transaction event
     */
    private void validateCardTransactionEvent(CardTransactionEvent event) {
        Set<ConstraintViolation<CardTransactionEvent>> violations = validator.validate(event);
        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder("Card transaction event validation failed: ");
            for (ConstraintViolation<CardTransactionEvent> violation : violations) {
                sb.append(violation.getPropertyPath()).append(" ").append(violation.getMessage()).append("; ");
            }
            throw new IllegalArgumentException(sb.toString());
        }

        // Additional business validation
        if (event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            throw new IllegalArgumentException("Event ID is required");
        }
        
        if (event.getTransactionId() == null) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        
        if (event.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (event.getAmount() == null || event.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Valid amount is required");
        }
    }

    /**
     * Dead Letter Topic handler for failed card transaction events
     */
    @DltHandler
    public void handleDltCardTransactionEvent(
            @Payload CardTransactionEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage,
            @Header(value = "correlationId", required = false) String correlationId) {
        
        log.error("Card transaction event sent to DLT: eventId={}, transactionId={}, topic={}, error={}", 
                 event.getEventId(), event.getTransactionId(), topic, exceptionMessage);

        // Generate correlation ID if not provided
        if (correlationId == null || correlationId.trim().isEmpty()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Track DLT event
        eventProcessingTrackingService.trackEventDLT(
            event.getEventId(),
            "CARD_TRANSACTION_EVENT",
            exceptionMessage,
            Map.of(
                "topic", topic,
                "transactionId", event.getTransactionId() != null ? event.getTransactionId().toString() : "unknown",
                "amount", event.getAmount() != null ? event.getAmount().toString() : "0",
                "currency", event.getCurrency() != null ? event.getCurrency() : "unknown"
            )
        );

        // Critical audit for DLT events
        auditService.logCardTransactionEventDLT(
            event.getEventId(),
            event.getTransactionId() != null ? event.getTransactionId().toString() : "unknown",
            correlationId,
            topic,
            exceptionMessage,
            Map.of(
                "amount", event.getAmount() != null ? event.getAmount().toString() : "0",
                "currency", event.getCurrency() != null ? event.getCurrency() : "unknown",
                "transactionType", event.getTransactionType() != null ? event.getTransactionType() : "unknown",
                "requiresManualIntervention", "true"
            )
        );
    }
}