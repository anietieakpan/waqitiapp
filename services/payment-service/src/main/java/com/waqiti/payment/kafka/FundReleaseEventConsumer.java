package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.idempotency.IdempotentPaymentProcessor;
import com.waqiti.payment.model.FundRelease;
import com.waqiti.payment.model.FundReleaseStatus;
import com.waqiti.payment.repository.FundReleaseRepository;
import com.waqiti.payment.service.MerchantService;
import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.service.AuditService;
import com.waqiti.payment.service.NotificationService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for fund release events
 * Handles merchant payouts, escrow releases, and settlement distributions
 *
 * CRITICAL SECURITY FIX (2025-11-08):
 * - Added 3-layer idempotency to prevent duplicate fund releases
 * - Replaces basic isDuplicateRelease() with database-backed processor
 * - Prevents double payouts to merchants (critical financial integrity issue)
 *
 * Critical for: Merchant cash flow, platform liquidity management
 * SLA: Must process within 5 seconds for amounts < $10,000
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FundReleaseEventConsumer {

    private final FundReleaseRepository fundReleaseRepository;
    private final MerchantService merchantService;
    private final PaymentService paymentService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final UniversalDLQHandler dlqHandler;
    private final IdempotentPaymentProcessor idempotentProcessor;

    private static final BigDecimal INSTANT_RELEASE_THRESHOLD = new BigDecimal("10000.00");
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("100000.00");
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long PROCESSING_TIMEOUT_MS = 30000; // 30 seconds

    @KafkaListener(
        topics = "fund-release-events",
        groupId = "fund-release-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "fund-release-processor", fallbackMethod = "handleFundReleaseFailure")
    @Retry(name = "fund-release-processor")
    public void processFundReleaseEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing fund release event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            // Extract fund release details from event
            Map<String, Object> payload = event.getPayload();
            FundRelease fundRelease = extractFundRelease(payload);

            // ═══════════════════════════════════════════════════════════════════════
            // IDEMPOTENT PROCESSING (3-Layer Defense)
            // ═══════════════════════════════════════════════════════════════════════
            ProcessingResult result = idempotentProcessor.process(
                eventId,                        // Unique event ID
                fundRelease.getReleaseId(),     // Business entity ID
                "FUND_RELEASE",                 // Entity type
                "fund-release-consumer",        // Consumer name
                () -> processFundRelease(fundRelease),  // Business logic
                ProcessingResult.class          // Result class
            );
            
            // Update tracking and audit
            updateReleaseTracking(fundRelease, result);
            auditFundRelease(fundRelease, result, event);
            
            // Send notifications
            sendReleaseNotifications(fundRelease, result);

            // Handle post-processing based on result
            handlePostProcessing(fundRelease, result);

            // Record metrics
            recordMetrics(fundRelease, startTime);

            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed fund release: {} in {}ms with status: {}", 
                    fundRelease.getReleaseId(), 
                    System.currentTimeMillis() - startTime,
                    result.getStatus());
            
        } catch (ValidationException e) {
            log.error("Validation failed for fund release event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge(); // Acknowledge to avoid reprocessing invalid events
            
        } catch (ComplianceException e) {
            log.error("Compliance check failed for fund release event: {}", eventId, e);
            handleComplianceError(event, e);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing fund release event: topic={}, partition={}, offset={}, error={}",
                topic, partition, offset, e.getMessage(), e);

            handleProcessingError(event, e, acknowledgment);

            dlqHandler.handleFailedMessage(org.apache.kafka.clients.consumer.ConsumerRecord.class.cast(event), e)
                .thenAccept(result -> log.info("Message sent to DLQ: topic={}, offset={}, destination={}, category={}",
                        topic, offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed - MESSAGE MAY BE LOST! " +
                            "topic={}, partition={}, offset={}, error={}",
                            topic, partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Fund release event processing failed", e);
        }
    }

    /**
     * Process fund release (business logic - called within idempotency wrapper)
     */
    private ProcessingResult processFundRelease(FundRelease fundRelease) {
        // Business validation
        validateFundRelease(fundRelease);

        // Check merchant status and compliance
        performComplianceChecks(fundRelease);

        // Determine release strategy based on amount and risk
        FundReleaseStrategy strategy = determineReleaseStrategy(fundRelease);

        // Process the fund release based on strategy
        ProcessingResult result = processRelease(fundRelease, strategy);

        return result;
    }

    private FundRelease extractFundRelease(Map<String, Object> payload) {
        return FundRelease.builder()
            .releaseId(extractString(payload, "releaseId", UUID.randomUUID().toString()))
            .merchantId(extractString(payload, "merchantId", null))
            .orderId(extractString(payload, "orderId", null))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", "USD"))
            .releaseType(FundReleaseType.fromString(extractString(payload, "releaseType", "STANDARD")))
            .sourceAccount(extractString(payload, "sourceAccount", null))
            .destinationAccount(extractString(payload, "destinationAccount", null))
            .scheduledReleaseTime(extractInstant(payload, "scheduledReleaseTime"))
            .metadata(extractMap(payload, "metadata"))
            .status(FundReleaseStatus.PENDING)
            .createdAt(Instant.now())
            .build();
    }

    private boolean isDuplicateRelease(FundRelease fundRelease) {
        return fundReleaseRepository.existsByReleaseIdAndStatus(
            fundRelease.getReleaseId(), 
            FundReleaseStatus.COMPLETED
        );
    }

    private void validateFundRelease(FundRelease fundRelease) {
        // Validate required fields
        if (fundRelease.getMerchantId() == null || fundRelease.getMerchantId().isEmpty()) {
            throw new ValidationException("Merchant ID is required for fund release");
        }
        
        if (fundRelease.getAmount() == null || fundRelease.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Invalid release amount: " + fundRelease.getAmount());
        }
        
        if (fundRelease.getDestinationAccount() == null || fundRelease.getDestinationAccount().isEmpty()) {
            throw new ValidationException("Destination account is required");
        }
        
        // Validate merchant exists and is active
        if (!merchantService.isActive(fundRelease.getMerchantId())) {
            throw new ValidationException("Merchant is not active: " + fundRelease.getMerchantId());
        }
        
        // Check for sufficient funds in source account
        BigDecimal availableBalance = paymentService.getAvailableBalance(fundRelease.getSourceAccount());
        if (availableBalance.compareTo(fundRelease.getAmount()) < 0) {
            throw new ValidationException(String.format(
                "Insufficient funds. Required: %s, Available: %s", 
                fundRelease.getAmount(), 
                availableBalance
            ));
        }
    }

    private void performComplianceChecks(FundRelease fundRelease) {
        // Check if merchant is under investigation or blocked
        if (merchantService.isUnderInvestigation(fundRelease.getMerchantId())) {
            throw new ComplianceException("Merchant under investigation, fund release blocked");
        }
        
        // Check for AML limits
        if (fundRelease.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            // High value transfers require additional verification
            if (!merchantService.hasEnhancedVerification(fundRelease.getMerchantId())) {
                throw new ComplianceException("Enhanced verification required for high value release");
            }
        }
        
        // Check daily/monthly limits
        BigDecimal dailyReleased = fundReleaseRepository.getTotalReleasedToday(fundRelease.getMerchantId());
        BigDecimal dailyLimit = merchantService.getDailyReleaseLimit(fundRelease.getMerchantId());
        
        if (dailyReleased.add(fundRelease.getAmount()).compareTo(dailyLimit) > 0) {
            throw new ComplianceException(String.format(
                "Daily release limit exceeded. Limit: %s, Already released: %s, Requested: %s",
                dailyLimit, dailyReleased, fundRelease.getAmount()
            ));
        }
    }

    private FundReleaseStrategy determineReleaseStrategy(FundRelease fundRelease) {
        // Instant release for small amounts from trusted merchants
        if (fundRelease.getAmount().compareTo(INSTANT_RELEASE_THRESHOLD) <= 0 
            && merchantService.isTrusted(fundRelease.getMerchantId())) {
            return FundReleaseStrategy.INSTANT;
        }
        
        // Scheduled release for standard merchants
        if (fundRelease.getScheduledReleaseTime() != null 
            && fundRelease.getScheduledReleaseTime().isAfter(Instant.now())) {
            return FundReleaseStrategy.SCHEDULED;
        }
        
        // Batch release for high volume merchants
        if (merchantService.isHighVolume(fundRelease.getMerchantId())) {
            return FundReleaseStrategy.BATCH;
        }
        
        // Manual review for high risk or high value
        if (fundRelease.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0 
            || merchantService.isHighRisk(fundRelease.getMerchantId())) {
            return FundReleaseStrategy.MANUAL_REVIEW;
        }
        
        return FundReleaseStrategy.STANDARD;
    }

    private ProcessingResult processRelease(FundRelease fundRelease, FundReleaseStrategy strategy) {
        log.info("Processing fund release {} with strategy: {}", fundRelease.getReleaseId(), strategy);
        
        ProcessingResult result = new ProcessingResult();
        result.setStartTime(Instant.now());
        
        try {
            switch (strategy) {
                case INSTANT:
                    result = processInstantRelease(fundRelease);
                    break;
                    
                case SCHEDULED:
                    result = scheduleRelease(fundRelease);
                    break;
                    
                case BATCH:
                    result = addToBatchRelease(fundRelease);
                    break;
                    
                case MANUAL_REVIEW:
                    result = submitForManualReview(fundRelease);
                    break;
                    
                case STANDARD:
                default:
                    result = processStandardRelease(fundRelease);
                    break;
            }
            
            result.setEndTime(Instant.now());
            result.setProcessingTimeMs(
                ChronoUnit.MILLIS.between(result.getStartTime(), result.getEndTime())
            );
            
        } catch (Exception e) {
            log.error("Failed to process release with strategy {}: {}", strategy, e.getMessage());
            result.setStatus(ProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new ProcessingException("Release processing failed", e);
        }
        
        return result;
    }

    private ProcessingResult processInstantRelease(FundRelease fundRelease) {
        // Execute immediate transfer
        String transactionId = paymentService.executeTransfer(
            fundRelease.getSourceAccount(),
            fundRelease.getDestinationAccount(),
            fundRelease.getAmount(),
            fundRelease.getCurrency(),
            "INSTANT_RELEASE_" + fundRelease.getReleaseId()
        );
        
        fundRelease.setStatus(FundReleaseStatus.COMPLETED);
        fundRelease.setTransactionId(transactionId);
        fundRelease.setReleasedAt(Instant.now());
        fundReleaseRepository.save(fundRelease);
        
        return ProcessingResult.success(transactionId);
    }

    private ProcessingResult processStandardRelease(FundRelease fundRelease) {
        // Standard T+1 or T+2 settlement
        Instant releaseTime = calculateStandardReleaseTime(fundRelease);
        
        // Create pending transfer
        String pendingTransferId = paymentService.createPendingTransfer(
            fundRelease.getSourceAccount(),
            fundRelease.getDestinationAccount(),
            fundRelease.getAmount(),
            fundRelease.getCurrency(),
            releaseTime,
            "STANDARD_RELEASE_" + fundRelease.getReleaseId()
        );
        
        fundRelease.setStatus(FundReleaseStatus.PENDING);
        fundRelease.setTransactionId(pendingTransferId);
        fundRelease.setScheduledReleaseTime(releaseTime);
        fundReleaseRepository.save(fundRelease);
        
        return ProcessingResult.pending(pendingTransferId, releaseTime);
    }

    private ProcessingResult scheduleRelease(FundRelease fundRelease) {
        // Schedule for specified time
        String scheduledId = paymentService.scheduleTransfer(
            fundRelease.getSourceAccount(),
            fundRelease.getDestinationAccount(),
            fundRelease.getAmount(),
            fundRelease.getCurrency(),
            fundRelease.getScheduledReleaseTime(),
            "SCHEDULED_RELEASE_" + fundRelease.getReleaseId()
        );
        
        fundRelease.setStatus(FundReleaseStatus.SCHEDULED);
        fundRelease.setTransactionId(scheduledId);
        fundReleaseRepository.save(fundRelease);
        
        return ProcessingResult.scheduled(scheduledId, fundRelease.getScheduledReleaseTime());
    }

    private ProcessingResult addToBatchRelease(FundRelease fundRelease) {
        // Add to next batch processing cycle
        String batchId = paymentService.addToBatch(
            fundRelease.getMerchantId(),
            fundRelease.getSourceAccount(),
            fundRelease.getDestinationAccount(),
            fundRelease.getAmount(),
            fundRelease.getCurrency()
        );
        
        fundRelease.setStatus(FundReleaseStatus.BATCHED);
        fundRelease.setBatchId(batchId);
        fundReleaseRepository.save(fundRelease);
        
        return ProcessingResult.batched(batchId);
    }

    private ProcessingResult submitForManualReview(FundRelease fundRelease) {
        // Create manual review case
        String reviewId = paymentService.createManualReviewCase(
            fundRelease.getReleaseId(),
            fundRelease.getMerchantId(),
            fundRelease.getAmount(),
            "HIGH_VALUE_RELEASE"
        );
        
        fundRelease.setStatus(FundReleaseStatus.MANUAL_REVIEW);
        fundRelease.setReviewId(reviewId);
        fundReleaseRepository.save(fundRelease);
        
        // Notify compliance team
        notificationService.notifyComplianceTeam(reviewId, fundRelease);
        
        return ProcessingResult.manualReview(reviewId);
    }

    private Instant calculateStandardReleaseTime(FundRelease fundRelease) {
        // T+1 for trusted merchants, T+2 for standard
        int daysToAdd = merchantService.isTrusted(fundRelease.getMerchantId()) ? 1 : 2;
        return Instant.now().plus(daysToAdd, ChronoUnit.DAYS);
    }

    private void updateReleaseTracking(FundRelease fundRelease, ProcessingResult result) {
        fundRelease.setLastUpdated(Instant.now());
        fundRelease.setProcessingResult(result.toJson());
        fundReleaseRepository.save(fundRelease);
    }

    private void auditFundRelease(FundRelease fundRelease, ProcessingResult result, GenericKafkaEvent event) {
        auditService.auditFundRelease(
            fundRelease.getReleaseId(),
            fundRelease.getMerchantId(),
            fundRelease.getAmount(),
            result.getStatus().toString(),
            event.getEventId(),
            event.getUserId()
        );
    }

    private void sendReleaseNotifications(FundRelease fundRelease, ProcessingResult result) {
        // Notify merchant
        CompletableFuture.runAsync(() -> {
            notificationService.notifyMerchant(
                fundRelease.getMerchantId(),
                "FUND_RELEASE",
                Map.of(
                    "releaseId", fundRelease.getReleaseId(),
                    "amount", fundRelease.getAmount(),
                    "status", result.getStatus().toString(),
                    "expectedTime", result.getExpectedCompletionTime()
                )
            );
        });
        
        // Send webhook if configured
        if (merchantService.hasWebhookEnabled(fundRelease.getMerchantId())) {
            CompletableFuture.runAsync(() -> {
                merchantService.sendWebhook(
                    fundRelease.getMerchantId(),
                    "fund.release." + result.getStatus().toString().toLowerCase(),
                    fundRelease
                );
            });
        }
    }

    private void handlePostProcessing(FundRelease fundRelease, ProcessingResult result) {
        if (result.getStatus() == ProcessingStatus.COMPLETED) {
            // Update merchant balance
            merchantService.updateAvailableBalance(
                fundRelease.getMerchantId(),
                fundRelease.getAmount().negate()
            );
            
            // Update settlement records
            paymentService.updateSettlementRecord(
                fundRelease.getMerchantId(),
                fundRelease.getReleaseId(),
                fundRelease.getAmount()
            );
        }
    }

    private void recordMetrics(FundRelease fundRelease, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        // Record processing metrics
        metricsService.recordFundReleaseMetrics(
            fundRelease.getReleaseType().toString(),
            fundRelease.getAmount(),
            processingTime,
            fundRelease.getStatus().toString()
        );
    }

    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        // Log validation error for analysis
        auditService.logValidationError(event.getEventId(), e.getMessage());
        
        // Send to validation error topic for manual review
        kafkaTemplate.send("fund-release-validation-errors", event);
    }

    private void handleComplianceError(GenericKafkaEvent event, ComplianceException e) {
        // Create compliance alert
        complianceService.createAlert(
            "FUND_RELEASE_BLOCKED",
            event.getPayloadValue("merchantId", String.class),
            e.getMessage(),
            event
        );
        
        // Notify compliance team
        notificationService.notifyComplianceTeam("Fund release blocked", e.getMessage());
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        
        // Check retry count
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class);
        if (retryCount == null) retryCount = 0;
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            // Retry with exponential backoff
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying fund release event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            // Schedule retry
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("fund-release-events-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            // Max retries exceeded, send to DLQ
            log.error("Max retries exceeded for fund release event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "fund-release-events");
        
        kafkaTemplate.send("fund-release-events.DLQ", event);
        
        // Create alert for DLQ message
        alertingService.createDLQAlert(
            "fund-release-events",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleFundReleaseFailure(GenericKafkaEvent event, String topic, int partition, 
                                         long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for fund release processing: {}", e.getMessage());
        
        // Store for later processing
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        // Alert operations team
        alertingService.sendCriticalAlert(
            "Fund Release Circuit Breaker Open",
            "Fund release processing is failing. Manual intervention required."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper methods for extraction
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Inner classes and enums
    public enum FundReleaseType {
        INSTANT, STANDARD, SCHEDULED, BATCH, MANUAL;
        
        public static FundReleaseType fromString(String value) {
            try {
                return FundReleaseType.valueOf(value.toUpperCase());
            } catch (Exception e) {
                return STANDARD;
            }
        }
    }

    public enum FundReleaseStrategy {
        INSTANT, STANDARD, SCHEDULED, BATCH, MANUAL_REVIEW
    }

    public enum ProcessingStatus {
        COMPLETED, PENDING, SCHEDULED, BATCHED, MANUAL_REVIEW, FAILED
    }

    @Data
    @Builder
    public static class ProcessingResult {
        private ProcessingStatus status;
        private String transactionId;
        private String batchId;
        private String reviewId;
        private Instant startTime;
        private Instant endTime;
        private long processingTimeMs;
        private Instant expectedCompletionTime;
        private String errorMessage;
        
        public static ProcessingResult success(String transactionId) {
            return ProcessingResult.builder()
                .status(ProcessingStatus.COMPLETED)
                .transactionId(transactionId)
                .build();
        }
        
        public static ProcessingResult pending(String transactionId, Instant expectedTime) {
            return ProcessingResult.builder()
                .status(ProcessingStatus.PENDING)
                .transactionId(transactionId)
                .expectedCompletionTime(expectedTime)
                .build();
        }
        
        public static ProcessingResult scheduled(String transactionId, Instant scheduledTime) {
            return ProcessingResult.builder()
                .status(ProcessingStatus.SCHEDULED)
                .transactionId(transactionId)
                .expectedCompletionTime(scheduledTime)
                .build();
        }
        
        public static ProcessingResult batched(String batchId) {
            return ProcessingResult.builder()
                .status(ProcessingStatus.BATCHED)
                .batchId(batchId)
                .build();
        }
        
        public static ProcessingResult manualReview(String reviewId) {
            return ProcessingResult.builder()
                .status(ProcessingStatus.MANUAL_REVIEW)
                .reviewId(reviewId)
                .build();
        }
        
        public String toJson() {
            try {
                return new ObjectMapper().writeValueAsString(this);
            } catch (Exception e) {
                return "{}";
            }
        }
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class ComplianceException extends RuntimeException {
        public ComplianceException(String message) {
            super(message);
        }
    }

    public static class ProcessingException extends RuntimeException {
        public ProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}