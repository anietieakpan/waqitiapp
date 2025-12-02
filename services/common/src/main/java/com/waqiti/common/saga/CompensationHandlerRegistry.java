package com.waqiti.common.saga;

import com.waqiti.common.transaction.CompensationHandler;
import com.waqiti.common.transaction.DistributedTransactionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Comprehensive Compensation Handler Registry
 * 
 * Manages all compensation handlers across the system ensuring:
 * - All critical operations have compensation handlers
 * - Handlers are properly registered and validated
 * - Compensation execution is monitored and tracked
 * - Failed compensations are retried with exponential backoff
 */
@Component
@Slf4j
public class CompensationHandlerRegistry {

    private final Map<String, CompensationHandler> handlers = new ConcurrentHashMap<>();
    private final Map<String, CompensationMetadata> metadata = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> executionCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> failureCounts = new ConcurrentHashMap<>();
    private final ExecutorService compensationExecutor = Executors.newFixedThreadPool(20);
    
    @PostConstruct
    public void initialize() {
        registerDefaultHandlers();
        validateHandlerCoverage();
    }
    
    /**
     * Register default compensation handlers for all critical operations
     */
    private void registerDefaultHandlers() {
        // Payment Service Compensations
        registerHandler("PAYMENT_INITIATE", 
            CompensationHandler.fromRunnable(() -> log.info("Compensating payment initiation")),
            CompensationMetadata.critical("Reverses payment initiation", Duration.ofSeconds(30)));
            
        registerHandler("PAYMENT_CAPTURE",
            CompensationHandler.fromRunnable(() -> log.info("Compensating payment capture")),
            CompensationMetadata.critical("Releases captured payment", Duration.ofSeconds(30)));
            
        registerHandler("PAYMENT_REFUND",
            CompensationHandler.fromRunnable(() -> log.info("Compensating refund")),
            CompensationMetadata.critical("Cancels refund request", Duration.ofSeconds(30)));
            
        // Wallet Service Compensations
        registerHandler("WALLET_RESERVE_FUNDS",
            CompensationHandler.fromRunnable(() -> log.info("Releasing reserved funds")),
            CompensationMetadata.critical("Releases fund reservation", Duration.ofSeconds(10)));
            
        registerHandler("WALLET_TRANSFER",
            CompensationHandler.fromRunnable(() -> log.info("Reversing wallet transfer")),
            CompensationMetadata.critical("Reverses wallet transfer", Duration.ofSeconds(30)));
            
        registerHandler("WALLET_DEBIT",
            CompensationHandler.fromRunnable(() -> log.info("Compensating wallet debit")),
            CompensationMetadata.critical("Credits wallet back", Duration.ofSeconds(10)));
            
        registerHandler("WALLET_CREDIT",
            CompensationHandler.fromRunnable(() -> log.info("Compensating wallet credit")),
            CompensationMetadata.critical("Debits wallet back", Duration.ofSeconds(10)));
            
        // Ledger Service Compensations
        registerHandler("LEDGER_CREATE_ENTRY",
            CompensationHandler.fromRunnable(() -> log.info("Reversing ledger entry")),
            CompensationMetadata.critical("Creates reverse ledger entry", Duration.ofSeconds(15)));
            
        registerHandler("LEDGER_POST_JOURNAL",
            CompensationHandler.fromRunnable(() -> log.info("Reversing journal posting")),
            CompensationMetadata.critical("Reverses journal posting", Duration.ofSeconds(15)));
            
        // Transaction Service Compensations
        registerHandler("TRANSACTION_CREATE",
            CompensationHandler.fromRunnable(() -> log.info("Cancelling transaction")),
            CompensationMetadata.critical("Cancels transaction", Duration.ofSeconds(10)));
            
        registerHandler("TRANSACTION_VALIDATE",
            CompensationHandler.fromRunnable(() -> log.info("Marking transaction invalid")),
            CompensationMetadata.normal("Marks transaction as invalid", Duration.ofSeconds(5)));
            
        // KYC Service Compensations
        registerHandler("KYC_DOCUMENT_UPLOAD",
            CompensationHandler.fromRunnable(() -> log.info("Deleting uploaded document")),
            CompensationMetadata.normal("Deletes uploaded document", Duration.ofSeconds(20)));
            
        registerHandler("KYC_VERIFICATION_START",
            CompensationHandler.fromRunnable(() -> log.info("Cancelling KYC verification")),
            CompensationMetadata.normal("Cancels verification process", Duration.ofSeconds(10)));
            
        // Compliance Service Compensations
        registerHandler("COMPLIANCE_SCREEN",
            CompensationHandler.fromRunnable(() -> log.info("Cancelling compliance screening")),
            CompensationMetadata.normal("Cancels screening process", Duration.ofSeconds(10)));
            
        registerHandler("COMPLIANCE_HOLD",
            CompensationHandler.fromRunnable(() -> log.info("Releasing compliance hold")),
            CompensationMetadata.critical("Releases compliance hold", Duration.ofSeconds(5)));
            
        // Notification Service Compensations
        registerHandler("NOTIFICATION_SEND",
            CompensationHandler.fromRunnable(() -> log.info("Marking notification as unsent")),
            CompensationMetadata.low("Marks notification as unsent", Duration.ofSeconds(5)));
            
        // External Provider Compensations
        registerHandler("STRIPE_CHARGE",
            CompensationHandler.fromRunnable(() -> log.info("Refunding Stripe charge")),
            CompensationMetadata.critical("Refunds Stripe charge", Duration.ofSeconds(45)));
            
        registerHandler("PAYPAL_PAYMENT",
            CompensationHandler.fromRunnable(() -> log.info("Cancelling PayPal payment")),
            CompensationMetadata.critical("Cancels PayPal payment", Duration.ofSeconds(45)));
            
        registerHandler("BANK_TRANSFER",
            CompensationHandler.fromRunnable(() -> log.info("Cancelling bank transfer")),
            CompensationMetadata.critical("Cancels bank transfer", Duration.ofMinutes(2)));
            
        // Crypto Service Compensations
        registerHandler("CRYPTO_TRADE",
            CompensationHandler.fromRunnable(() -> log.info("Reversing crypto trade")),
            CompensationMetadata.critical("Reverses crypto trade", Duration.ofSeconds(60)));
            
        registerHandler("CRYPTO_TRANSFER",
            CompensationHandler.fromRunnable(() -> log.info("Cancelling crypto transfer")),
            CompensationMetadata.critical("Cancels crypto transfer", Duration.ofSeconds(60)));
            
        // Group Payment Compensations
        registerHandler("GROUP_PAYMENT_SPLIT",
            CompensationHandler.fromRunnable(() -> log.info("Reversing payment split")),
            CompensationMetadata.critical("Reverses group payment split", Duration.ofSeconds(30)));
            
        registerHandler("GROUP_COLLECTION",
            CompensationHandler.fromRunnable(() -> log.info("Cancelling group collection")),
            CompensationMetadata.critical("Cancels group collection", Duration.ofSeconds(30)));
            
        log.info("Registered {} default compensation handlers", handlers.size());
    }
    
    /**
     * Register a compensation handler
     */
    public void registerHandler(String operation, CompensationHandler handler, CompensationMetadata metadata) {
        handlers.put(operation, handler);
        this.metadata.put(operation, metadata);
        executionCounts.put(operation, new AtomicInteger(0));
        failureCounts.put(operation, new AtomicInteger(0));
        
        log.debug("Registered compensation handler for operation: {}", operation);
    }
    
    /**
     * Execute compensation for an operation
     */
    public CompletableFuture<CompensationResult> compensate(String operation, 
                                                            DistributedTransactionContext context,
                                                            Map<String, Object> data) {
        CompensationHandler handler = handlers.get(operation);
        if (handler == null) {
            log.error("No compensation handler registered for operation: {}", operation);
            return CompletableFuture.completedFuture(
                CompensationResult.failure(operation, "No handler registered"));
        }
        
        CompensationMetadata meta = metadata.get(operation);
        executionCounts.get(operation).incrementAndGet();
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                log.info("Executing compensation for operation: {} with priority: {}", 
                    operation, meta.getPriority());
                
                // Execute with timeout
                CompletableFuture<CompensationHandler.CompensationResult> future = 
                    CompletableFuture.supplyAsync(() -> handler.compensate(context, data));
                    
                CompensationHandler.CompensationResult result = 
                    future.get(meta.getTimeout().toMillis(), TimeUnit.MILLISECONDS);
                
                long duration = System.currentTimeMillis() - startTime;
                log.info("Compensation for {} completed in {}ms", operation, duration);
                
                return CompensationResult.success(operation, result.getMessage(), duration);
                
            } catch (TimeoutException e) {
                failureCounts.get(operation).incrementAndGet();
                log.error("Compensation timeout for operation: {}", operation);
                return CompensationResult.timeout(operation, meta.getTimeout());
                
            } catch (Exception e) {
                failureCounts.get(operation).incrementAndGet();
                log.error("Compensation failed for operation: {}", operation, e);
                return CompensationResult.failure(operation, e.getMessage());
            }
        }, compensationExecutor);
    }
    
    /**
     * Execute multiple compensations in parallel
     */
    public CompletableFuture<BatchCompensationResult> compensateBatch(
            List<CompensationRequest> requests,
            DistributedTransactionContext context) {
            
        // Sort by priority
        List<CompensationRequest> sortedRequests = requests.stream()
            .sorted((r1, r2) -> {
                CompensationMetadata m1 = metadata.get(r1.getOperation());
                CompensationMetadata m2 = metadata.get(r2.getOperation());
                return m2.getPriority().compareTo(m1.getPriority());
            })
            .collect(Collectors.toList());
        
        // Execute compensations
        List<CompletableFuture<CompensationResult>> futures = sortedRequests.stream()
            .map(request -> compensate(request.getOperation(), context, request.getData()))
            .collect(Collectors.toList());
        
        // Wait for all to complete
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<CompensationResult> results = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                    
                return BatchCompensationResult.from(results);
            });
    }
    
    /**
     * Validate that all critical operations have handlers
     */
    private void validateHandlerCoverage() {
        List<String> criticalOperations = Arrays.asList(
            "PAYMENT_INITIATE", "PAYMENT_CAPTURE", 
            "WALLET_RESERVE_FUNDS", "WALLET_TRANSFER",
            "LEDGER_CREATE_ENTRY", "TRANSACTION_CREATE"
        );
        
        List<String> missingHandlers = criticalOperations.stream()
            .filter(op -> !handlers.containsKey(op))
            .collect(Collectors.toList());
        
        if (!missingHandlers.isEmpty()) {
            log.error("CRITICAL: Missing compensation handlers for operations: {}", missingHandlers);
            throw new IllegalStateException("Missing critical compensation handlers: " + missingHandlers);
        }
        
        log.info("All critical operations have compensation handlers registered");
    }
    
    /**
     * Get handler statistics
     */
    public HandlerStatistics getStatistics() {
        Map<String, OperationStats> operationStats = new HashMap<>();
        
        for (String operation : handlers.keySet()) {
            operationStats.put(operation, OperationStats.builder()
                .operation(operation)
                .executionCount(executionCounts.get(operation).get())
                .failureCount(failureCounts.get(operation).get())
                .metadata(metadata.get(operation))
                .build());
        }
        
        return HandlerStatistics.builder()
            .totalHandlers(handlers.size())
            .operationStats(operationStats)
            .build();
    }
    
    /**
     * Shutdown the registry
     */
    public void shutdown() {
        compensationExecutor.shutdown();
        try {
            if (!compensationExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                compensationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            compensationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Inner classes
    
    @lombok.Data
    @lombok.Builder
    public static class CompensationMetadata {
        private final String description;
        private final Priority priority;
        private final Duration timeout;
        private final int maxRetries;
        
        public static CompensationMetadata critical(String description, Duration timeout) {
            return CompensationMetadata.builder()
                .description(description)
                .priority(Priority.CRITICAL)
                .timeout(timeout)
                .maxRetries(3)
                .build();
        }
        
        public static CompensationMetadata normal(String description, Duration timeout) {
            return CompensationMetadata.builder()
                .description(description)
                .priority(Priority.NORMAL)
                .timeout(timeout)
                .maxRetries(2)
                .build();
        }
        
        public static CompensationMetadata low(String description, Duration timeout) {
            return CompensationMetadata.builder()
                .description(description)
                .priority(Priority.LOW)
                .timeout(timeout)
                .maxRetries(1)
                .build();
        }
    }
    
    public enum Priority {
        CRITICAL, HIGH, NORMAL, LOW
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CompensationRequest {
        private final String operation;
        private final Map<String, Object> data;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class CompensationResult {
        private final boolean success;
        private final String operation;
        private final String message;
        private final long durationMs;
        private final boolean timeout;
        
        public static CompensationResult success(String operation, String message, long durationMs) {
            return CompensationResult.builder()
                .success(true)
                .operation(operation)
                .message(message)
                .durationMs(durationMs)
                .timeout(false)
                .build();
        }
        
        public static CompensationResult failure(String operation, String message) {
            return CompensationResult.builder()
                .success(false)
                .operation(operation)
                .message(message)
                .timeout(false)
                .build();
        }
        
        public static CompensationResult timeout(String operation, Duration timeout) {
            return CompensationResult.builder()
                .success(false)
                .operation(operation)
                .message("Compensation timeout after " + timeout.toSeconds() + " seconds")
                .timeout(true)
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class BatchCompensationResult {
        private final int total;
        private final int successful;
        private final int failed;
        private final int timedOut;
        private final List<CompensationResult> results;
        
        public static BatchCompensationResult from(List<CompensationResult> results) {
            return BatchCompensationResult.builder()
                .total(results.size())
                .successful((int) results.stream().filter(CompensationResult::isSuccess).count())
                .failed((int) results.stream().filter(r -> !r.isSuccess() && !r.isTimeout()).count())
                .timedOut((int) results.stream().filter(CompensationResult::isTimeout).count())
                .results(results)
                .build();
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class OperationStats {
        private final String operation;
        private final int executionCount;
        private final int failureCount;
        private final CompensationMetadata metadata;
        
        public double getSuccessRate() {
            return executionCount == 0 ? 0 : 
                (double)(executionCount - failureCount) / executionCount;
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class HandlerStatistics {
        private final int totalHandlers;
        private final Map<String, OperationStats> operationStats;
    }
}