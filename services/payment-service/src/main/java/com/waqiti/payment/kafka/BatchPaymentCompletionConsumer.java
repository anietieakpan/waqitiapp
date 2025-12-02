package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.payment.model.*;
import com.waqiti.payment.repository.BatchPaymentRepository;
import com.waqiti.payment.service.*;
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
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Production-grade Kafka consumer for batch payment completion events
 * Handles bulk payment processing, settlement batches, and mass payouts
 * 
 * Critical for: High-volume payment processing, settlement efficiency, cost optimization
 * SLA: Must complete batch processing within 10 minutes regardless of batch size
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BatchPaymentCompletionConsumer {

    private final BatchPaymentRepository batchRepository;
    private final PaymentService paymentService;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final SettlementService settlementService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final FraudService fraudService;
    private final MerchantService merchantService;
    private final ReportService reportService;
    private final ReconciliationService reconciliationService;
    private final WebhookService webhookService;
    private final AlertingService alertingService;
    private final FailedEventRepository failedEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final java.util.concurrent.ScheduledExecutorService scheduledExecutor =
        java.util.concurrent.Executors.newScheduledThreadPool(2);

    private static final int MAX_BATCH_SIZE = 10000;
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long BATCH_TIMEOUT_MS = 600000; // 10 minutes
    private static final BigDecimal MIN_BATCH_AMOUNT = new BigDecimal("0.01");
    private static final BigDecimal MAX_SINGLE_PAYMENT_AMOUNT = new BigDecimal("100000.00");
    
    @KafkaListener(
        topics = "batch-payment-completion",
        groupId = "batch-payment-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "batch-payment-processor", fallbackMethod = "handleBatchPaymentFailure")
    @Retry(name = "batch-payment-processor")
    public void processBatchPaymentCompletionEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing batch payment completion event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            BatchPaymentRequest batchRequest = extractBatchPaymentRequest(payload);
            
            // Validate batch request
            validateBatchRequest(batchRequest);
            
            // Check for duplicate batch
            if (isDuplicateBatch(batchRequest)) {
                log.warn("Duplicate batch detected: {}, skipping", batchRequest.getBatchId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Pre-process batch validation
            BatchValidationResult validationResult = validateBatchContents(batchRequest);
            
            // Apply fraud screening to batch
            FraudScreeningResult fraudResult = performBatchFraudScreening(batchRequest);
            
            // Process the batch
            BatchProcessingResult result = processBatch(batchRequest, validationResult, fraudResult);
            
            // Handle settlement if configured
            if (batchRequest.isAutoSettle()) {
                processSettlement(batchRequest, result);
            }
            
            // Generate batch reports
            generateBatchReports(batchRequest, result);
            
            // Send notifications
            sendBatchNotifications(batchRequest, result);
            
            // Audit batch processing
            auditBatchProcessing(batchRequest, result, event);
            
            // Record metrics
            recordBatchMetrics(batchRequest, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed batch: {} with {} payments in {}ms", 
                    batchRequest.getBatchId(), result.getProcessedCount(),
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for batch payment event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (FraudException e) {
            log.error("Fraud screening failed for batch payment event: {}", eventId, e);
            handleFraudError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process batch payment event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private BatchPaymentRequest extractBatchPaymentRequest(Map<String, Object> payload) {
        return BatchPaymentRequest.builder()
            .batchId(extractString(payload, "batchId", UUID.randomUUID().toString()))
            .batchType(BatchType.fromString(extractString(payload, "batchType", "STANDARD")))
            .merchantId(extractString(payload, "merchantId", null))
            .currency(extractString(payload, "currency", "USD"))
            .totalAmount(extractBigDecimal(payload, "totalAmount"))
            .paymentCount(extractInteger(payload, "paymentCount", 0))
            .payments(extractPaymentList(payload))
            .processingMode(ProcessingMode.fromString(extractString(payload, "processingMode", "PARALLEL")))
            .priority(extractString(payload, "priority", "NORMAL"))
            .autoSettle(extractBoolean(payload, "autoSettle", false))
            .settlementDate(extractInstant(payload, "settlementDate"))
            .notificationUrl(extractString(payload, "notificationUrl", null))
            .metadata(extractMap(payload, "metadata"))
            .requestedBy(extractString(payload, "requestedBy", "SYSTEM"))
            .createdAt(Instant.now())
            .build();
    }

    @SuppressWarnings("unchecked")
    private List<PaymentInstruction> extractPaymentList(Map<String, Object> payload) {
        Object paymentsObj = payload.get("payments");
        if (paymentsObj instanceof List) {
            List<Map<String, Object>> paymentMaps = (List<Map<String, Object>>) paymentsObj;
            return paymentMaps.stream()
                .map(this::extractPaymentInstruction)
                .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    private PaymentInstruction extractPaymentInstruction(Map<String, Object> paymentMap) {
        return PaymentInstruction.builder()
            .paymentId(extractString(paymentMap, "paymentId", UUID.randomUUID().toString()))
            .sourceAccountId(extractString(paymentMap, "sourceAccountId", null))
            .destinationAccountId(extractString(paymentMap, "destinationAccountId", null))
            .amount(extractBigDecimal(paymentMap, "amount"))
            .currency(extractString(paymentMap, "currency", "USD"))
            .paymentType(extractString(paymentMap, "paymentType", "TRANSFER"))
            .reference(extractString(paymentMap, "reference", null))
            .description(extractString(paymentMap, "description", null))
            .beneficiaryName(extractString(paymentMap, "beneficiaryName", null))
            .metadata(extractMap(paymentMap, "metadata"))
            .build();
    }

    private void validateBatchRequest(BatchPaymentRequest request) {
        if (request.getBatchId() == null || request.getBatchId().isEmpty()) {
            throw new ValidationException("Batch ID is required");
        }
        
        if (request.getPayments() == null || request.getPayments().isEmpty()) {
            throw new ValidationException("Batch must contain at least one payment");
        }
        
        if (request.getPayments().size() > MAX_BATCH_SIZE) {
            throw new ValidationException("Batch size exceeds maximum allowed: " + MAX_BATCH_SIZE);
        }
        
        if (request.getTotalAmount() == null || request.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Invalid total amount: " + request.getTotalAmount());
        }
        
        if (request.getPaymentCount() != request.getPayments().size()) {
            throw new ValidationException("Payment count mismatch. Expected: " + 
                request.getPaymentCount() + ", Actual: " + request.getPayments().size());
        }
        
        // Validate total amount matches sum of individual payments
        BigDecimal calculatedTotal = request.getPayments().stream()
            .map(PaymentInstruction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        if (calculatedTotal.compareTo(request.getTotalAmount()) != 0) {
            throw new ValidationException("Total amount mismatch. Calculated: " + 
                calculatedTotal + ", Declared: " + request.getTotalAmount());
        }
        
        // Check merchant authorization
        if (request.getMerchantId() != null && 
            !merchantService.isAuthorizedForBatchPayments(request.getMerchantId())) {
            throw new ValidationException("Merchant not authorized for batch payments: " + 
                request.getMerchantId());
        }
    }

    private boolean isDuplicateBatch(BatchPaymentRequest request) {
        return batchRepository.existsByBatchIdAndCreatedAtAfter(
            request.getBatchId(),
            Instant.now().minus(24, ChronoUnit.HOURS)
        );
    }

    private BatchValidationResult validateBatchContents(BatchPaymentRequest request) {
        BatchValidationResult result = new BatchValidationResult();
        result.setBatchId(request.getBatchId());
        result.setValidationStartTime(Instant.now());
        
        List<PaymentValidationError> errors = new ArrayList<>();
        AtomicInteger validCount = new AtomicInteger(0);
        AtomicInteger invalidCount = new AtomicInteger(0);
        
        // Validate each payment in parallel
        List<CompletableFuture<Void>> validationFutures = request.getPayments().stream()
            .map(payment -> CompletableFuture.runAsync(() -> {
                try {
                    validateIndividualPayment(payment);
                    validCount.incrementAndGet();
                } catch (Exception e) {
                    invalidCount.incrementAndGet();
                    synchronized (errors) {
                        errors.add(PaymentValidationError.builder()
                            .paymentId(payment.getPaymentId())
                            .errorCode("VALIDATION_FAILED")
                            .errorMessage(e.getMessage())
                            .build());
                    }
                }
            }))
            .collect(Collectors.toList());
        
        // Wait for all validations to complete
        try {
            CompletableFuture.allOf(validationFutures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Batch validation timed out after 30 seconds for batch: {}", request.getBatchId(), e);
            throw new ValidationException("Batch validation timed out - batch too large or validation service slow");
        } catch (java.util.concurrent.ExecutionException e) {
            log.error("Batch validation execution failed for batch: {}", request.getBatchId(), e.getCause());
            throw new ValidationException("Batch validation failed: " + e.getCause().getMessage());
        } catch (java.util.concurrent.InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Batch validation interrupted for batch: {}", request.getBatchId(), e);
            throw new ValidationException("Batch validation interrupted");
        }

        result.setValidPaymentCount(validCount.get());
        result.setInvalidPaymentCount(invalidCount.get());
        result.setValidationErrors(errors);
        result.setValidationEndTime(Instant.now());
        result.setIsValid(errors.isEmpty());

        if (!result.isValid()) {
            log.warn("Batch {} contains {} invalid payments", request.getBatchId(), invalidCount.get());
        }

        return result;
    }

    private void validateIndividualPayment(PaymentInstruction payment) {
        // Validate required fields
        if (payment.getSourceAccountId() == null || payment.getSourceAccountId().isEmpty()) {
            throw new ValidationException("Source account ID is required");
        }
        
        if (payment.getDestinationAccountId() == null || payment.getDestinationAccountId().isEmpty()) {
            throw new ValidationException("Destination account ID is required");
        }
        
        if (payment.getAmount() == null || payment.getAmount().compareTo(MIN_BATCH_AMOUNT) < 0) {
            throw new ValidationException("Invalid payment amount: " + payment.getAmount());
        }
        
        if (payment.getAmount().compareTo(MAX_SINGLE_PAYMENT_AMOUNT) > 0) {
            throw new ValidationException("Payment amount exceeds maximum: " + payment.getAmount());
        }
        
        // Validate accounts exist and are active
        if (!accountService.isAccountActive(payment.getSourceAccountId())) {
            throw new ValidationException("Source account is not active: " + payment.getSourceAccountId());
        }
        
        if (!accountService.isAccountActive(payment.getDestinationAccountId())) {
            throw new ValidationException("Destination account is not active: " + payment.getDestinationAccountId());
        }
        
        // Validate sufficient balance
        BigDecimal balance = accountService.getAvailableBalance(payment.getSourceAccountId());
        if (balance.compareTo(payment.getAmount()) < 0) {
            throw new ValidationException("Insufficient balance in source account");
        }
        
        // Validate currency consistency
        String accountCurrency = accountService.getAccountCurrency(payment.getSourceAccountId());
        if (!payment.getCurrency().equals(accountCurrency)) {
            throw new ValidationException("Currency mismatch for source account");
        }
    }

    private FraudScreeningResult performBatchFraudScreening(BatchPaymentRequest request) {
        FraudScreeningResult result = new FraudScreeningResult();
        result.setBatchId(request.getBatchId());
        result.setScreeningStartTime(Instant.now());
        
        // Perform batch-level fraud checks
        BatchFraudScore batchScore = fraudService.calculateBatchRisk(request);
        result.setBatchRiskScore(batchScore.getScore());
        result.setBatchRiskLevel(batchScore.getLevel());
        
        // Check for suspicious patterns
        List<String> suspiciousPatterns = detectSuspiciousPatterns(request);
        result.setSuspiciousPatterns(suspiciousPatterns);
        
        // Screen individual payments for high-risk indicators
        List<PaymentFraudAlert> fraudAlerts = new ArrayList<>();
        AtomicInteger highRiskCount = new AtomicInteger(0);
        
        request.getPayments().parallelStream().forEach(payment -> {
            PaymentFraudScore paymentScore = fraudService.calculatePaymentRisk(payment);
            
            if (paymentScore.getScore() > 70) { // High risk threshold
                highRiskCount.incrementAndGet();
                synchronized (fraudAlerts) {
                    fraudAlerts.add(PaymentFraudAlert.builder()
                        .paymentId(payment.getPaymentId())
                        .riskScore(paymentScore.getScore())
                        .riskFactors(paymentScore.getRiskFactors())
                        .build());
                }
            }
        });
        
        result.setHighRiskPaymentCount(highRiskCount.get());
        result.setFraudAlerts(fraudAlerts);
        result.setScreeningEndTime(Instant.now());
        
        // Determine if batch should be blocked
        boolean shouldBlock = batchScore.getScore() > 80 || // Very high batch risk
                             highRiskCount.get() > request.getPayments().size() * 0.1 || // >10% high risk
                             suspiciousPatterns.size() > 3; // Multiple suspicious patterns
        
        result.setBlocked(shouldBlock);
        
        if (shouldBlock) {
            throw new FraudException("Batch blocked due to high fraud risk. Score: " + 
                batchScore.getScore() + ", High risk payments: " + highRiskCount.get());
        }
        
        return result;
    }

    private List<String> detectSuspiciousPatterns(BatchPaymentRequest request) {
        List<String> patterns = new ArrayList<>();
        
        // Check for round amounts (potential structuring)
        long roundAmounts = request.getPayments().stream()
            .mapToLong(p -> p.getAmount().remainder(new BigDecimal("100")).compareTo(BigDecimal.ZERO) == 0 ? 1 : 0)
            .sum();
        
        if (roundAmounts > request.getPayments().size() * 0.5) {
            patterns.add("HIGH_ROUND_AMOUNTS");
        }
        
        // Check for identical amounts
        Map<BigDecimal, Long> amountCounts = request.getPayments().stream()
            .collect(Collectors.groupingBy(PaymentInstruction::getAmount, Collectors.counting()));
        
        if (amountCounts.values().stream().anyMatch(count -> count > request.getPayments().size() * 0.3)) {
            patterns.add("IDENTICAL_AMOUNTS");
        }
        
        // Check for sequential account numbers
        if (hasSequentialAccounts(request.getPayments())) {
            patterns.add("SEQUENTIAL_ACCOUNTS");
        }
        
        // Check for velocity (too many payments in short time)
        if (request.getPayments().size() > 1000 && 
            ChronoUnit.MINUTES.between(request.getCreatedAt(), Instant.now()) < 5) {
            patterns.add("HIGH_VELOCITY");
        }
        
        return patterns;
    }

    private boolean hasSequentialAccounts(List<PaymentInstruction> payments) {
        List<String> accounts = payments.stream()
            .map(PaymentInstruction::getDestinationAccountId)
            .filter(Objects::nonNull)
            .sorted()
            .collect(Collectors.toList());
        
        if (accounts.size() < 3) return false;
        
        int sequentialCount = 0;
        for (int i = 1; i < accounts.size(); i++) {
            try {
                long prev = Long.parseLong(accounts.get(i - 1));
                long curr = Long.parseLong(accounts.get(i));
                if (curr == prev + 1) {
                    sequentialCount++;
                }
            } catch (NumberFormatException e) {
                // Skip non-numeric accounts
            }
        }
        
        return sequentialCount > accounts.size() * 0.3;
    }

    private BatchProcessingResult processBatch(BatchPaymentRequest request, 
                                              BatchValidationResult validationResult,
                                              FraudScreeningResult fraudResult) {
        
        BatchProcessingResult result = new BatchProcessingResult();
        result.setBatchId(request.getBatchId());
        result.setProcessingStartTime(Instant.now());
        
        // Create batch record
        BatchPayment batchPayment = createBatchRecord(request, validationResult, fraudResult);
        
        // Process payments based on mode
        if (request.getProcessingMode() == ProcessingMode.PARALLEL) {
            result = processPaymentsInParallel(request, batchPayment);
        } else {
            result = processPaymentsSequentially(request, batchPayment);
        }
        
        // Update batch status
        updateBatchStatus(batchPayment, result);
        
        result.setProcessingEndTime(Instant.now());
        result.setTotalProcessingTime(
            ChronoUnit.MILLIS.between(result.getProcessingStartTime(), result.getProcessingEndTime())
        );
        
        return result;
    }

    private BatchPayment createBatchRecord(BatchPaymentRequest request, 
                                          BatchValidationResult validationResult,
                                          FraudScreeningResult fraudResult) {
        
        BatchPayment batchPayment = BatchPayment.builder()
            .batchId(request.getBatchId())
            .batchType(request.getBatchType())
            .merchantId(request.getMerchantId())
            .currency(request.getCurrency())
            .totalAmount(request.getTotalAmount())
            .paymentCount(request.getPaymentCount())
            .processingMode(request.getProcessingMode())
            .priority(request.getPriority())
            .autoSettle(request.isAutoSettle())
            .settlementDate(request.getSettlementDate())
            .status(BatchStatus.PROCESSING)
            .validationResult(validationResult.toJson())
            .fraudScreeningResult(fraudResult.toJson())
            .createdAt(request.getCreatedAt())
            .startedAt(Instant.now())
            .requestedBy(request.getRequestedBy())
            .metadata(request.getMetadata())
            .build();
        
        return batchRepository.save(batchPayment);
    }

    private BatchProcessingResult processPaymentsInParallel(BatchPaymentRequest request, 
                                                           BatchPayment batchPayment) {
        
        BatchProcessingResult result = new BatchProcessingResult();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<PaymentProcessingError> errors = Collections.synchronizedList(new ArrayList<>());
        
        // Process payments in parallel with limited concurrency
        List<CompletableFuture<Void>> processingFutures = request.getPayments().stream()
            .map(payment -> CompletableFuture.runAsync(() -> {
                try {
                    processIndividualPayment(payment, batchPayment);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    errors.add(PaymentProcessingError.builder()
                        .paymentId(payment.getPaymentId())
                        .errorCode("PROCESSING_FAILED")
                        .errorMessage(e.getMessage())
                        .timestamp(Instant.now())
                        .build());
                }
            }, ForkJoinPool.commonPool()))
            .collect(Collectors.toList());
        
        // Wait for all payments to complete with timeout
        try {
            CompletableFuture.allOf(processingFutures.toArray(new CompletableFuture[0]))
                .get(BATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Batch processing timeout or error for batch: {}", request.getBatchId(), e);
            
            // Cancel remaining futures
            processingFutures.forEach(future -> future.cancel(true));
            
            throw new BatchProcessingException("Batch processing timeout", e);
        }
        
        result.setProcessedCount(successCount.get());
        result.setFailedCount(failureCount.get());
        result.setProcessingErrors(errors);
        result.setSuccessRate(calculateSuccessRate(successCount.get(), request.getPaymentCount()));
        
        return result;
    }

    private BatchProcessingResult processPaymentsSequentially(BatchPaymentRequest request, 
                                                             BatchPayment batchPayment) {
        
        BatchProcessingResult result = new BatchProcessingResult();
        int successCount = 0;
        int failureCount = 0;
        List<PaymentProcessingError> errors = new ArrayList<>();
        
        for (PaymentInstruction payment : request.getPayments()) {
            try {
                processIndividualPayment(payment, batchPayment);
                successCount++;
            } catch (Exception e) {
                failureCount++;
                errors.add(PaymentProcessingError.builder()
                    .paymentId(payment.getPaymentId())
                    .errorCode("PROCESSING_FAILED")
                    .errorMessage(e.getMessage())
                    .timestamp(Instant.now())
                    .build());
                
                // Stop processing if failure rate is too high
                double failureRate = (double) failureCount / (successCount + failureCount);
                if (failureRate > 0.5 && (successCount + failureCount) > 10) {
                    log.error("High failure rate detected, stopping batch processing");
                    break;
                }
            }
        }
        
        result.setProcessedCount(successCount);
        result.setFailedCount(failureCount);
        result.setProcessingErrors(errors);
        result.setSuccessRate(calculateSuccessRate(successCount, successCount + failureCount));
        
        return result;
    }

    private void processIndividualPayment(PaymentInstruction payment, BatchPayment batchPayment) {
        // Execute the payment transfer
        String transactionId = ledgerService.executeTransfer(
            payment.getSourceAccountId(),
            payment.getDestinationAccountId(),
            payment.getAmount(),
            payment.getCurrency(),
            "BATCH_" + batchPayment.getBatchId() + "_" + payment.getPaymentId()
        );
        
        // Create payment record
        Payment paymentRecord = Payment.builder()
            .paymentId(payment.getPaymentId())
            .batchId(batchPayment.getBatchId())
            .sourceAccountId(payment.getSourceAccountId())
            .destinationAccountId(payment.getDestinationAccountId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentType(payment.getPaymentType())
            .reference(payment.getReference())
            .description(payment.getDescription())
            .beneficiaryName(payment.getBeneficiaryName())
            .transactionId(transactionId)
            .status(PaymentStatus.COMPLETED)
            .processedAt(Instant.now())
            .metadata(payment.getMetadata())
            .build();
        
        paymentService.savePayment(paymentRecord);
        
        // Update account balances
        accountService.updateBalanceAfterTransfer(
            payment.getSourceAccountId(),
            payment.getDestinationAccountId(),
            payment.getAmount()
        );
    }

    private double calculateSuccessRate(int successCount, int totalCount) {
        if (totalCount == 0) return 0.0;
        return (double) successCount / totalCount * 100.0;
    }

    private void updateBatchStatus(BatchPayment batchPayment, BatchProcessingResult result) {
        if (result.getFailedCount() == 0) {
            batchPayment.setStatus(BatchStatus.COMPLETED);
        } else if (result.getProcessedCount() == 0) {
            batchPayment.setStatus(BatchStatus.FAILED);
        } else {
            batchPayment.setStatus(BatchStatus.PARTIALLY_COMPLETED);
        }
        
        batchPayment.setProcessedCount(result.getProcessedCount());
        batchPayment.setFailedCount(result.getFailedCount());
        batchPayment.setSuccessRate(result.getSuccessRate());
        batchPayment.setCompletedAt(Instant.now());
        batchPayment.setProcessingResult(result.toJson());
        
        batchRepository.save(batchPayment);
    }

    private void processSettlement(BatchPaymentRequest request, BatchProcessingResult result) {
        if (result.getProcessedCount() == 0) {
            log.info("No successful payments to settle for batch: {}", request.getBatchId());
            return;
        }
        
        SettlementRequest settlementRequest = SettlementRequest.builder()
            .settlementId(UUID.randomUUID().toString())
            .batchId(request.getBatchId())
            .merchantId(request.getMerchantId())
            .currency(request.getCurrency())
            .amount(calculateSettlementAmount(request, result))
            .settlementDate(request.getSettlementDate() != null ? 
                request.getSettlementDate() : Instant.now().plus(1, ChronoUnit.DAYS))
            .paymentCount(result.getProcessedCount())
            .build();
        
        settlementService.createSettlement(settlementRequest);
    }

    private BigDecimal calculateSettlementAmount(BatchPaymentRequest request, BatchProcessingResult result) {
        // Calculate total amount of successful payments
        return request.getPayments().stream()
            .filter(payment -> !result.getProcessingErrors().stream()
                .anyMatch(error -> error.getPaymentId().equals(payment.getPaymentId())))
            .map(PaymentInstruction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private void generateBatchReports(BatchPaymentRequest request, BatchProcessingResult result) {
        // Generate processing report
        BatchReport report = BatchReport.builder()
            .batchId(request.getBatchId())
            .totalPayments(request.getPaymentCount())
            .successfulPayments(result.getProcessedCount())
            .failedPayments(result.getFailedCount())
            .successRate(result.getSuccessRate())
            .totalAmount(request.getTotalAmount())
            .processedAmount(calculateSettlementAmount(request, result))
            .processingTime(result.getTotalProcessingTime())
            .errors(result.getProcessingErrors())
            .generatedAt(Instant.now())
            .build();
        
        reportService.generateBatchReport(report);
        
        // Generate reconciliation file if requested
        if (request.getMetadata().containsKey("generateReconciliation")) {
            reconciliationService.generateBatchReconciliation(request.getBatchId());
        }
    }

    private void sendBatchNotifications(BatchPaymentRequest request, BatchProcessingResult result) {
        Map<String, Object> notificationData = Map.of(
            "batchId", request.getBatchId(),
            "totalPayments", request.getPaymentCount(),
            "successfulPayments", result.getProcessedCount(),
            "failedPayments", result.getFailedCount(),
            "successRate", result.getSuccessRate(),
            "processingTime", result.getTotalProcessingTime()
        );
        
        // Notify requestor
        notificationService.sendBatchCompletionNotification(
            request.getRequestedBy(),
            notificationData
        );
        
        // Notify merchant if applicable
        if (request.getMerchantId() != null) {
            notificationService.sendMerchantNotification(
                request.getMerchantId(),
                "BATCH_PROCESSING_COMPLETED",
                notificationData
            );
        }
        
        // Send webhook notification if URL provided
        if (request.getNotificationUrl() != null) {
            webhookService.sendBatchWebhook(
                request.getNotificationUrl(),
                notificationData
            );
        }
        
        // Alert on high failure rate
        if (result.getSuccessRate() < 95.0) {
            alertingService.createAlert(
                "BATCH_HIGH_FAILURE_RATE",
                String.format("Batch %s has low success rate: %.2f%%", 
                    request.getBatchId(), result.getSuccessRate()),
                "HIGH"
            );
        }
    }

    private void auditBatchProcessing(BatchPaymentRequest request, BatchProcessingResult result, 
                                     GenericKafkaEvent event) {
        auditService.auditBatchProcessing(
            request.getBatchId(),
            request.getPaymentCount(),
            result.getProcessedCount(),
            result.getFailedCount(),
            request.getTotalAmount(),
            event.getEventId()
        );
    }

    private void recordBatchMetrics(BatchPaymentRequest request, BatchProcessingResult result, long startTime) {
        long totalProcessingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordBatchMetrics(
            request.getBatchType().toString(),
            request.getPaymentCount(),
            result.getProcessedCount(),
            result.getFailedCount(),
            totalProcessingTime,
            result.getSuccessRate()
        );
        
        // Record SLA compliance
        boolean slaCompliant = totalProcessingTime <= BATCH_TIMEOUT_MS;
        metricsService.recordBatchSLA(request.getBatchId(), slaCompliant, totalProcessingTime);
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("batch-payment-validation-errors", event);
    }

    private void handleFraudError(GenericKafkaEvent event, FraudException e) {
        // Create fraud alert
        fraudService.createFraudAlert(
            "BATCH_FRAUD_DETECTION",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("batch-payment-fraud-alerts", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying batch payment event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("batch-payment-completion-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for batch payment event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "batch-payment-completion");
        
        kafkaTemplate.send("batch-payment-completion.DLQ", event);
        
        alertingService.createDLQAlert(
            "batch-payment-completion",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleBatchPaymentFailure(GenericKafkaEvent event, String topic, int partition,
                                         long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for batch payment processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Batch Payment Circuit Breaker Open",
            "Batch payment processing is failing. High-volume operations impacted."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private boolean extractBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
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

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class FraudException extends RuntimeException {
        public FraudException(String message) {
            super(message);
        }
    }

    public static class BatchProcessingException extends RuntimeException {
        public BatchProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}