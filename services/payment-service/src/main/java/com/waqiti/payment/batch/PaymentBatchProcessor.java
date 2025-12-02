package com.waqiti.payment.batch;

import com.waqiti.payment.service.PaymentService;
import com.waqiti.payment.dto.*;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.locking.DistributedLockService;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * PRODUCTION-GRADE Batch Payment Processor with comprehensive safeguards
 * Implements circuit breakers, rate limiting, dead letter queues, and comprehensive monitoring
 * Critical for high-volume payment processing with financial integrity
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentBatchProcessor {

    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService distributedLockService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final BatchAuditService batchAuditService;
    private final BatchValidationService batchValidationService;
    private final DeadLetterQueueService deadLetterQueueService;

    // Configuration
    @Value("${batch.payment.max-batch-size:1000}")
    private int maxBatchSize;

    @Value("${batch.payment.max-concurrent-batches:5}")
    private int maxConcurrentBatches;

    @Value("${batch.payment.processing-timeout-minutes:30}")
    private int processingTimeoutMinutes;

    @Value("${batch.payment.retry-attempts:3}")
    private int maxRetryAttempts;

    @Value("${batch.payment.circuit-breaker-threshold:50}")
    private int circuitBreakerThreshold;

    @Value("${batch.payment.rate-limit-per-minute:100}")
    private int rateLimitPerMinute;

    // Executors
    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(10);
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(2);

    // Circuit breaker state
    private volatile CircuitBreakerState circuitBreakerState = CircuitBreakerState.CLOSED;
    private volatile int consecutiveFailures = 0;
    private volatile LocalDateTime circuitBreakerLastFailure = LocalDateTime.now();

    // Rate limiting
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    /**
     * CRITICAL: Process payment batch with comprehensive safeguards
     */
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public BatchProcessingResult processBatch(BatchPaymentRequest batchRequest) {
        Timer.Sample timer = Timer.start(meterRegistry);
        
        log.info("SECURITY: Starting batch payment processing for batch: {}, size: {}, initiated by: {}", 
                batchRequest.getBatchId(), batchRequest.getPayments().size(), batchRequest.getInitiatedBy());

        try {
            // 1. Pre-processing validation
            BatchValidationResult validationResult = validateBatch(batchRequest);
            if (!validationResult.isValid()) {
                return createFailedResult(batchRequest, "Batch validation failed: " + 
                                        validationResult.getErrorMessage());
            }

            // 2. Check circuit breaker
            if (circuitBreakerState == CircuitBreakerState.OPEN) {
                log.warn("SECURITY: Circuit breaker is OPEN, rejecting batch: {}", batchRequest.getBatchId());
                return createFailedResult(batchRequest, "Circuit breaker is open - service unavailable");
            }

            // 3. Rate limiting check
            if (!checkRateLimit(batchRequest.getInitiatedBy(), batchRequest.getPayments().size())) {
                log.warn("SECURITY: Rate limit exceeded for user: {}, batch: {}", 
                        batchRequest.getInitiatedBy(), batchRequest.getBatchId());
                return createFailedResult(batchRequest, "Rate limit exceeded");
            }

            // 4. Acquire distributed lock for batch processing
            String lockKey = "batch-processing:" + batchRequest.getBatchId();
            var lock = distributedLockService.acquireLock(lockKey, 
                    Duration.ofMinutes(processingTimeoutMinutes), 
                    Duration.ofMinutes(processingTimeoutMinutes + 5));

            if (lock == null) {
                log.warn("SECURITY: Failed to acquire lock for batch: {}", batchRequest.getBatchId());
                return createFailedResult(batchRequest, "Failed to acquire processing lock");
            }

            try {
                // 5. Create audit record
                BatchAuditRecord auditRecord = batchAuditService.createBatchAuditRecord(batchRequest);

                // 6. Execute idempotent batch processing
                String idempotencyKey = "batch-payment:" + batchRequest.getBatchId();
                
                BatchProcessingResult result = idempotencyService.executeIdempotentWithPersistence(
                    "payment-batch-service",
                    "process-batch",
                    idempotencyKey,
                    () -> executeBatchProcessing(batchRequest, auditRecord),
                    Duration.ofHours(4)
                );

                // 7. Update metrics and circuit breaker
                updateMetrics(result);
                updateCircuitBreakerState(result.getStatus() == BatchProcessingStatus.COMPLETED);

                return result;

            } finally {
                lock.release();
            }

        } catch (Exception e) {
            log.error("CRITICAL: Batch processing failed for batch: {}", batchRequest.getBatchId(), e);
            
            updateCircuitBreakerState(false);
            incrementCounter("batch_processing_errors_total", "error_type", e.getClass().getSimpleName());
            
            return createFailedResult(batchRequest, "Batch processing failed: " + e.getMessage());
            
        } finally {
            timer.stop(Timer.builder("batch_processing_duration")
                    .description("Time taken to process payment batch")
                    .register(meterRegistry));
        }
    }

    /**
     * Execute batch processing with chunking and parallel processing
     */
    private BatchProcessingResult executeBatchProcessing(BatchPaymentRequest batchRequest, 
                                                       BatchAuditRecord auditRecord) {
        log.info("SECURITY: Executing batch processing for batch: {}", batchRequest.getBatchId());

        BatchProcessingResult.BatchProcessingResultBuilder resultBuilder = 
                BatchProcessingResult.builder()
                        .batchId(batchRequest.getBatchId())
                        .startedAt(LocalDateTime.now());

        try {
            List<PaymentRequest> payments = batchRequest.getPayments();
            
            // Split into chunks for parallel processing
            List<List<PaymentRequest>> chunks = chunkPayments(payments, 
                    Math.min(maxBatchSize, payments.size() / maxConcurrentBatches + 1));

            List<CompletableFuture<ChunkProcessingResult>> futures = new ArrayList<>();
            
            // Process chunks in parallel
            for (int i = 0; i < chunks.size(); i++) {
                List<PaymentRequest> chunk = chunks.get(i);
                String chunkId = batchRequest.getBatchId() + "-chunk-" + i;
                
                CompletableFuture<ChunkProcessingResult> future = CompletableFuture
                        .supplyAsync(() -> processChunk(chunkId, chunk, batchRequest), batchExecutor)
                        .orTimeout(processingTimeoutMinutes, TimeUnit.MINUTES);
                
                futures.add(future);
            }

            // Wait for all chunks to complete
            CompletableFuture<Void> allChunks = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            
            allChunks.get(processingTimeoutMinutes + 5, TimeUnit.MINUTES);

            // Collect results
            List<PaymentProcessingResult> successfulPayments = new ArrayList<>();
            List<PaymentProcessingResult> failedPayments = new ArrayList<>();
            
            for (CompletableFuture<ChunkProcessingResult> future : futures) {
                ChunkProcessingResult chunkResult = future.get();
                successfulPayments.addAll(chunkResult.getSuccessfulPayments());
                failedPayments.addAll(chunkResult.getFailedPayments());
            }

            // Determine overall batch status
            BatchProcessingStatus batchStatus;
            if (failedPayments.isEmpty()) {
                batchStatus = BatchProcessingStatus.COMPLETED;
            } else if (successfulPayments.isEmpty()) {
                batchStatus = BatchProcessingStatus.FAILED;
            } else {
                batchStatus = BatchProcessingStatus.PARTIAL_SUCCESS;
            }

            // Update audit record
            batchAuditService.completeBatchAuditRecord(auditRecord, batchStatus, 
                    successfulPayments.size(), failedPayments.size());

            // Publish batch completion event
            publishBatchCompletionEvent(batchRequest.getBatchId(), batchStatus, 
                    successfulPayments.size(), failedPayments.size());

            log.info("SECURITY: Batch processing completed for batch: {}, status: {}, successful: {}, failed: {}",
                    batchRequest.getBatchId(), batchStatus, successfulPayments.size(), failedPayments.size());

            return resultBuilder
                    .status(batchStatus)
                    .successfulPayments(successfulPayments)
                    .failedPayments(failedPayments)
                    .completedAt(LocalDateTime.now())
                    .auditRecordId(auditRecord.getId())
                    .build();

        } catch (TimeoutException e) {
            log.error("CRITICAL: Batch processing timeout for batch: {}", batchRequest.getBatchId(), e);
            
            batchAuditService.markBatchAuditRecordFailed(auditRecord, "Processing timeout");
            
            return resultBuilder
                    .status(BatchProcessingStatus.TIMEOUT)
                    .errorMessage("Batch processing timeout")
                    .failedAt(LocalDateTime.now())
                    .build();
            
        } catch (Exception e) {
            log.error("CRITICAL: Batch processing execution failed for batch: {}", 
                    batchRequest.getBatchId(), e);
            
            batchAuditService.markBatchAuditRecordFailed(auditRecord, e.getMessage());
            
            return resultBuilder
                    .status(BatchProcessingStatus.FAILED)
                    .errorMessage(e.getMessage())
                    .failedAt(LocalDateTime.now())
                    .build();
        }
    }

    /**
     * Process individual chunk with retry logic
     */
    private ChunkProcessingResult processChunk(String chunkId, List<PaymentRequest> payments, 
                                             BatchPaymentRequest batchRequest) {
        log.debug("SECURITY: Processing chunk: {} with {} payments", chunkId, payments.size());

        List<PaymentProcessingResult> successfulPayments = new ArrayList<>();
        List<PaymentProcessingResult> failedPayments = new ArrayList<>();

        for (PaymentRequest payment : payments) {
            PaymentProcessingResult result = processPaymentWithRetry(payment, batchRequest);
            
            if (result.getStatus() == PaymentProcessingStatus.COMPLETED) {
                successfulPayments.add(result);
            } else {
                failedPayments.add(result);
                
                // Send failed payment to dead letter queue
                deadLetterQueueService.sendToDeadLetterQueue(payment, result.getErrorMessage());
            }
        }

        log.debug("SECURITY: Chunk processing completed: {}, successful: {}, failed: {}",
                chunkId, successfulPayments.size(), failedPayments.size());

        return ChunkProcessingResult.builder()
                .chunkId(chunkId)
                .successfulPayments(successfulPayments)
                .failedPayments(failedPayments)
                .build();
    }

    /**
     * Process individual payment with retry logic
     */
    private PaymentProcessingResult processPaymentWithRetry(PaymentRequest payment, 
                                                          BatchPaymentRequest batchRequest) {
        String paymentIdempotencyKey = generatePaymentIdempotencyKey(payment, batchRequest.getBatchId());
        
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                log.debug("SECURITY: Processing payment: {} (attempt {}/{})", 
                        payment.getPaymentId(), attempt, maxRetryAttempts);

                // Process payment with idempotency
                var response = idempotencyService.executeIdempotentWithPersistence(
                    "payment-batch-service",
                    "process-individual-payment",
                    paymentIdempotencyKey,
                    () -> paymentService.processPayment(payment),
                    Duration.ofHours(1)
                );

                return PaymentProcessingResult.builder()
                        .paymentId(payment.getPaymentId())
                        .status(PaymentProcessingStatus.COMPLETED)
                        .transactionId(response.getTransactionId())
                        .processedAt(LocalDateTime.now())
                        .build();

            } catch (Exception e) {
                log.warn("SECURITY: Payment processing attempt {} failed for payment: {}", 
                        attempt, payment.getPaymentId(), e);

                if (attempt == maxRetryAttempts) {
                    log.error("SECURITY: Payment processing failed after {} attempts for payment: {}", 
                            maxRetryAttempts, payment.getPaymentId(), e);

                    return PaymentProcessingResult.builder()
                            .paymentId(payment.getPaymentId())
                            .status(PaymentProcessingStatus.FAILED)
                            .errorMessage(e.getMessage())
                            .failedAt(LocalDateTime.now())
                            .retryAttempts(attempt)
                            .build();
                }

                // Exponential backoff between retries
                try {
                    Thread.sleep(Math.min(1000 * (1L << (attempt - 1)), 30000));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return PaymentProcessingResult.builder()
                .paymentId(payment.getPaymentId())
                .status(PaymentProcessingStatus.FAILED)
                .errorMessage("Max retry attempts exceeded")
                .failedAt(LocalDateTime.now())
                .retryAttempts(maxRetryAttempts)
                .build();
    }

    /**
     * Validate batch request before processing
     */
    private BatchValidationResult validateBatch(BatchPaymentRequest batchRequest) {
        return batchValidationService.validateBatch(batchRequest);
    }

    /**
     * Check rate limiting for user
     */
    private boolean checkRateLimit(String userId, int requestCount) {
        RateLimiter rateLimiter = rateLimiters.computeIfAbsent(userId, 
                k -> new RateLimiter(rateLimitPerMinute, Duration.ofMinutes(1)));
        
        return rateLimiter.tryAcquire(requestCount);
    }

    /**
     * Update circuit breaker state based on success/failure
     */
    private synchronized void updateCircuitBreakerState(boolean success) {
        if (success) {
            consecutiveFailures = 0;
            if (circuitBreakerState == CircuitBreakerState.HALF_OPEN) {
                circuitBreakerState = CircuitBreakerState.CLOSED;
                log.info("SECURITY: Circuit breaker closed");
            }
        } else {
            consecutiveFailures++;
            circuitBreakerLastFailure = LocalDateTime.now();
            
            if (consecutiveFailures >= circuitBreakerThreshold && 
                circuitBreakerState == CircuitBreakerState.CLOSED) {
                circuitBreakerState = CircuitBreakerState.OPEN;
                log.error("SECURITY: Circuit breaker opened after {} consecutive failures", 
                        consecutiveFailures);
                
                // Schedule half-open attempt
                scheduledExecutor.schedule(() -> {
                    if (circuitBreakerState == CircuitBreakerState.OPEN) {
                        circuitBreakerState = CircuitBreakerState.HALF_OPEN;
                        log.info("SECURITY: Circuit breaker moved to half-open state");
                    }
                }, 5, TimeUnit.MINUTES);
            }
        }
    }

    /**
     * Update metrics after batch processing
     */
    private void updateMetrics(BatchProcessingResult result) {
        incrementCounter("batch_processing_total", "status", result.getStatus().toString().toLowerCase());
        
        if (result.getSuccessfulPayments() != null) {
            meterRegistry.counter("batch_payments_successful_total")
                    .increment(result.getSuccessfulPayments().size());
        }
        
        if (result.getFailedPayments() != null) {
            meterRegistry.counter("batch_payments_failed_total")
                    .increment(result.getFailedPayments().size());
        }
    }

    /**
     * Publish batch completion event
     */
    private void publishBatchCompletionEvent(String batchId, BatchProcessingStatus status, 
                                           int successCount, int failCount) {
        try {
            BatchCompletionEvent event = BatchCompletionEvent.builder()
                    .batchId(batchId)
                    .status(status.toString())
                    .successfulPayments(successCount)
                    .failedPayments(failCount)
                    .completedAt(LocalDateTime.now())
                    .build();

            kafkaTemplate.send("batch-payment-completion", event.toJson());
        } catch (Exception e) {
            log.warn("Failed to publish batch completion event for batch: {}", batchId, e);
        }
    }

    /**
     * Utility methods
     */
    private List<List<PaymentRequest>> chunkPayments(List<PaymentRequest> payments, int chunkSize) {
        List<List<PaymentRequest>> chunks = new ArrayList<>();
        for (int i = 0; i < payments.size(); i += chunkSize) {
            chunks.add(payments.subList(i, Math.min(payments.size(), i + chunkSize)));
        }
        return chunks;
    }

    private String generatePaymentIdempotencyKey(PaymentRequest payment, String batchId) {
        return String.format("batch-payment:%s:%s", batchId, payment.getPaymentId());
    }

    private BatchProcessingResult createFailedResult(BatchPaymentRequest batchRequest, String errorMessage) {
        return BatchProcessingResult.builder()
                .batchId(batchRequest.getBatchId())
                .status(BatchProcessingStatus.FAILED)
                .errorMessage(errorMessage)
                .failedAt(LocalDateTime.now())
                .build();
    }

    private void incrementCounter(String counterName, String... tags) {
        Counter.builder(counterName).tags(tags).register(meterRegistry).increment();
    }

    // Enums and DTOs
    public enum BatchProcessingStatus {
        PENDING, IN_PROGRESS, COMPLETED, PARTIAL_SUCCESS, FAILED, TIMEOUT
    }

    public enum PaymentProcessingStatus {
        PENDING, IN_PROGRESS, COMPLETED, FAILED
    }

    public enum CircuitBreakerState {
        CLOSED, OPEN, HALF_OPEN
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchProcessingResult {
        private String batchId;
        private BatchProcessingStatus status;
        private List<PaymentProcessingResult> successfulPayments;
        private List<PaymentProcessingResult> failedPayments;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private LocalDateTime failedAt;
        private UUID auditRecordId;
    }

    @lombok.Builder
    @lombok.Data
    public static class PaymentProcessingResult {
        private String paymentId;
        private PaymentProcessingStatus status;
        private UUID transactionId;
        private String errorMessage;
        private LocalDateTime processedAt;
        private LocalDateTime failedAt;
        private Integer retryAttempts;
    }

    @lombok.Builder
    @lombok.Data
    public static class ChunkProcessingResult {
        private String chunkId;
        private List<PaymentProcessingResult> successfulPayments;
        private List<PaymentProcessingResult> failedPayments;
    }

    @lombok.Builder
    @lombok.Data
    public static class BatchCompletionEvent {
        private String batchId;
        private String status;
        private Integer successfulPayments;
        private Integer failedPayments;
        private LocalDateTime completedAt;

        public String toJson() {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(this);
            } catch (Exception e) {
                return "{}";
            }
        }
    }

    /**
     * Simple rate limiter implementation
     */
    private static class RateLimiter {
        private final int maxRequests;
        private final Duration window;
        private final Queue<LocalDateTime> requests = new ConcurrentLinkedQueue<>();

        public RateLimiter(int maxRequests, Duration window) {
            this.maxRequests = maxRequests;
            this.window = window;
        }

        public boolean tryAcquire(int requestCount) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime cutoff = now.minus(window);

            // Remove expired requests
            requests.removeIf(time -> time.isBefore(cutoff));

            // Check if we can accommodate the new requests
            if (requests.size() + requestCount <= maxRequests) {
                for (int i = 0; i < requestCount; i++) {
                    requests.offer(now);
                }
                return true;
            }

            return false;
        }
    }
}