package com.waqiti.common.bulkhead;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * Bulkhead pattern implementation for resource isolation
 * Prevents cascade failures by isolating different types of operations
 */
@Service
@Slf4j
public class BulkheadService {
    
    private final BulkheadConfiguration.BulkheadProperties properties;
    
    private final int paymentProcessingPoolSize;
    private final int kycVerificationPoolSize;
    private final int fraudDetectionPoolSize;
    private final int notificationPoolSize;
    private final int analyticsPoolSize;
    private final int coreBankingPoolSize;
    
    private final int paymentProcessingTimeoutSeconds;
    private final int kycVerificationTimeoutSeconds;
    private final int fraudDetectionTimeoutSeconds;
    private final int notificationTimeoutSeconds;
    private final int analyticsTimeoutSeconds;
    private final int coreBankingTimeoutSeconds;
    
    // Isolated thread pools for different resource types
    private final ExecutorService paymentProcessingExecutor;
    private final ExecutorService kycVerificationExecutor;
    private final ExecutorService fraudDetectionExecutor;
    private final ExecutorService notificationExecutor;
    private final ExecutorService analyticsExecutor;
    private final ExecutorService coreBankingExecutor;
    
    // Semaphores for additional resource control
    private final Semaphore paymentProcessingSemaphore;
    private final Semaphore kycVerificationSemaphore;
    private final Semaphore fraudDetectionSemaphore;
    private final Semaphore notificationSemaphore;
    private final Semaphore analyticsSemaphore;
    private final Semaphore coreBankingSemaphore;
    
    public BulkheadService(BulkheadConfiguration.BulkheadProperties properties) {
        this.properties = properties;
        
        this.paymentProcessingPoolSize = properties.getPaymentProcessing().getPoolSize();
        this.kycVerificationPoolSize = properties.getKycVerification().getPoolSize();
        this.fraudDetectionPoolSize = properties.getFraudDetection().getPoolSize();
        this.notificationPoolSize = properties.getNotification().getPoolSize();
        this.analyticsPoolSize = properties.getAnalytics().getPoolSize();
        this.coreBankingPoolSize = properties.getCoreBanking().getPoolSize();
        
        this.paymentProcessingTimeoutSeconds = properties.getPaymentProcessing().getTimeoutSeconds();
        this.kycVerificationTimeoutSeconds = properties.getKycVerification().getTimeoutSeconds();
        this.fraudDetectionTimeoutSeconds = properties.getFraudDetection().getTimeoutSeconds();
        this.notificationTimeoutSeconds = properties.getNotification().getTimeoutSeconds();
        this.analyticsTimeoutSeconds = properties.getAnalytics().getTimeoutSeconds();
        this.coreBankingTimeoutSeconds = properties.getCoreBanking().getTimeoutSeconds();
        
        this.paymentProcessingExecutor = createExecutorService("payment-processing", paymentProcessingPoolSize);
        this.kycVerificationExecutor = createExecutorService("kyc-verification", kycVerificationPoolSize);
        this.fraudDetectionExecutor = createExecutorService("fraud-detection", fraudDetectionPoolSize);
        this.notificationExecutor = createExecutorService("notification", notificationPoolSize);
        this.analyticsExecutor = createExecutorService("analytics", analyticsPoolSize);
        this.coreBankingExecutor = createExecutorService("core-banking", coreBankingPoolSize);
        
        this.paymentProcessingSemaphore = new Semaphore(properties.getPaymentProcessing().getPermits());
        this.kycVerificationSemaphore = new Semaphore(properties.getKycVerification().getPermits());
        this.fraudDetectionSemaphore = new Semaphore(properties.getFraudDetection().getPermits());
        this.notificationSemaphore = new Semaphore(properties.getNotification().getPermits());
        this.analyticsSemaphore = new Semaphore(properties.getAnalytics().getPermits());
        this.coreBankingSemaphore = new Semaphore(properties.getCoreBanking().getPermits());
    }
    
    /**
     * Execute payment processing operation with bulkhead isolation
     */
    public <T> CompletableFuture<BulkheadResult<T>> executePaymentProcessing(
            Supplier<T> operation, String operationId) {
        
        return executeWithBulkhead(
            operation,
            operationId,
            ResourceType.PAYMENT_PROCESSING,
            paymentProcessingExecutor,
            paymentProcessingSemaphore,
            paymentProcessingTimeoutSeconds
        );
    }
    
    /**
     * Execute KYC verification with bulkhead isolation
     */
    public <T> CompletableFuture<BulkheadResult<T>> executeKycVerification(
            Supplier<T> operation, String operationId) {
        
        return executeWithBulkhead(
            operation,
            operationId,
            ResourceType.KYC_VERIFICATION,
            kycVerificationExecutor,
            kycVerificationSemaphore,
            kycVerificationTimeoutSeconds
        );
    }
    
    /**
     * Execute fraud detection with bulkhead isolation
     */
    public <T> CompletableFuture<BulkheadResult<T>> executeFraudDetection(
            Supplier<T> operation, String operationId) {
        
        return executeWithBulkhead(
            operation,
            operationId,
            ResourceType.FRAUD_DETECTION,
            fraudDetectionExecutor,
            fraudDetectionSemaphore,
            fraudDetectionTimeoutSeconds
        );
    }
    
    /**
     * Execute notification with bulkhead isolation
     */
    public <T> CompletableFuture<BulkheadResult<T>> executeNotification(
            Supplier<T> operation, String operationId) {
        
        return executeWithBulkhead(
            operation,
            operationId,
            ResourceType.NOTIFICATION,
            notificationExecutor,
            notificationSemaphore,
            notificationTimeoutSeconds
        );
    }
    
    /**
     * Execute analytics operation with bulkhead isolation
     */
    public <T> CompletableFuture<BulkheadResult<T>> executeAnalytics(
            Supplier<T> operation, String operationId) {
        
        return executeWithBulkhead(
            operation,
            operationId,
            ResourceType.ANALYTICS,
            analyticsExecutor,
            analyticsSemaphore,
            analyticsTimeoutSeconds
        );
    }
    
    /**
     * Execute core banking operation with bulkhead isolation
     */
    public <T> CompletableFuture<BulkheadResult<T>> executeCoreBanking(
            Supplier<T> operation, String operationId) {
        
        return executeWithBulkhead(
            operation,
            operationId,
            ResourceType.CORE_BANKING,
            coreBankingExecutor,
            coreBankingSemaphore,
            coreBankingTimeoutSeconds
        );
    }
    
    /**
     * Get bulkhead statistics for monitoring
     */
    public BulkheadStatistics getStatistics() {
        return BulkheadStatistics.builder()
            .paymentProcessingStats(getResourceStats(ResourceType.PAYMENT_PROCESSING, 
                paymentProcessingSemaphore, paymentProcessingPoolSize))
            .kycVerificationStats(getResourceStats(ResourceType.KYC_VERIFICATION, 
                kycVerificationSemaphore, kycVerificationPoolSize))
            .fraudDetectionStats(getResourceStats(ResourceType.FRAUD_DETECTION, 
                fraudDetectionSemaphore, fraudDetectionPoolSize))
            .notificationStats(getResourceStats(ResourceType.NOTIFICATION, 
                notificationSemaphore, notificationPoolSize))
            .analyticsStats(getResourceStats(ResourceType.ANALYTICS, 
                analyticsSemaphore, analyticsPoolSize))
            .coreBankingStats(getResourceStats(ResourceType.CORE_BANKING, 
                coreBankingSemaphore, coreBankingPoolSize))
            .build();
    }
    
    /**
     * Check if resource pool has capacity
     */
    public boolean hasCapacity(ResourceType resourceType) {
        return switch (resourceType) {
            case PAYMENT_PROCESSING -> paymentProcessingSemaphore.availablePermits() > 0;
            case KYC_VERIFICATION -> kycVerificationSemaphore.availablePermits() > 0;
            case FRAUD_DETECTION -> fraudDetectionSemaphore.availablePermits() > 0;
            case NOTIFICATION -> notificationSemaphore.availablePermits() > 0;
            case ANALYTICS -> analyticsSemaphore.availablePermits() > 0;
            case CORE_BANKING -> coreBankingSemaphore.availablePermits() > 0;
        };
    }
    
    /**
     * Get resource utilization percentage
     */
    public double getResourceUtilization(ResourceType resourceType) {
        return switch (resourceType) {
            case PAYMENT_PROCESSING -> calculateUtilization(paymentProcessingSemaphore, paymentProcessingPoolSize);
            case KYC_VERIFICATION -> calculateUtilization(kycVerificationSemaphore, kycVerificationPoolSize);
            case FRAUD_DETECTION -> calculateUtilization(fraudDetectionSemaphore, fraudDetectionPoolSize);
            case NOTIFICATION -> calculateUtilization(notificationSemaphore, notificationPoolSize);
            case ANALYTICS -> calculateUtilization(analyticsSemaphore, analyticsPoolSize);
            case CORE_BANKING -> calculateUtilization(coreBankingSemaphore, coreBankingPoolSize);
        };
    }
    
    // Private helper methods
    
    private <T> CompletableFuture<BulkheadResult<T>> executeWithBulkhead(
            Supplier<T> operation,
            String operationId,
            ResourceType resourceType,
            ExecutorService executor,
            Semaphore semaphore,
            int timeoutSeconds) {
        
        long startTime = System.currentTimeMillis();
        
        return CompletableFuture.supplyAsync(() -> {
            boolean acquired = false;
            try {
                // Try to acquire permit within timeout
                acquired = semaphore.tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
                
                if (!acquired) {
                    log.warn("Resource exhausted for {}: operation {}", resourceType, operationId);
                    return BulkheadResult.<T>builder()
                        .success(false)
                        .resourceType(resourceType)
                        .operationId(operationId)
                        .error("Resource pool exhausted")
                        .executionTime(System.currentTimeMillis() - startTime)
                        .build();
                }
                
                log.debug("Executing {} operation: {}", resourceType, operationId);
                
                // Execute the actual operation
                T result = operation.get();
                
                long executionTime = System.currentTimeMillis() - startTime;
                
                log.debug("Completed {} operation: {} in {}ms", resourceType, operationId, executionTime);
                
                return BulkheadResult.<T>builder()
                    .success(true)
                    .result(result)
                    .resourceType(resourceType)
                    .operationId(operationId)
                    .executionTime(executionTime)
                    .build();
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Operation interrupted: {} for {}", operationId, resourceType, e);
                
                return BulkheadResult.<T>builder()
                    .success(false)
                    .resourceType(resourceType)
                    .operationId(operationId)
                    .error("Operation interrupted: " + e.getMessage())
                    .executionTime(System.currentTimeMillis() - startTime)
                    .build();
                
            } catch (Exception e) {
                log.error("Operation failed: {} for {}", operationId, resourceType, e);
                
                return BulkheadResult.<T>builder()
                    .success(false)
                    .resourceType(resourceType)
                    .operationId(operationId)
                    .error("Operation failed: " + e.getMessage())
                    .executionTime(System.currentTimeMillis() - startTime)
                    .build();
                
            } finally {
                if (acquired) {
                    semaphore.release();
                }
            }
        }, executor).orTimeout(timeoutSeconds, TimeUnit.SECONDS)
        .exceptionally(throwable -> {
            log.error("Timeout or error for {} operation: {}", resourceType, operationId, throwable);
            
            return BulkheadResult.<T>builder()
                .success(false)
                .resourceType(resourceType)
                .operationId(operationId)
                .error("Timeout or error: " + throwable.getMessage())
                .executionTime(System.currentTimeMillis() - startTime)
                .timeout(throwable instanceof TimeoutException)
                .build();
        });
    }
    
    private ExecutorService createExecutorService(String threadNamePrefix, int poolSize) {
        return Executors.newFixedThreadPool(poolSize, r -> {
            Thread thread = new Thread(r);
            thread.setName("bulkhead-" + threadNamePrefix + "-" + thread.getId());
            thread.setDaemon(true);
            return thread;
        });
    }
    
    private ResourceStats getResourceStats(ResourceType resourceType, Semaphore semaphore, int poolSize) {
        int available = semaphore.availablePermits();
        int inUse = poolSize - available;
        double utilization = calculateUtilization(semaphore, poolSize);
        
        return ResourceStats.builder()
            .resourceType(resourceType)
            .poolSize(poolSize)
            .available(available)
            .inUse(inUse)
            .utilization(utilization)
            .queueLength(semaphore.getQueueLength())
            .build();
    }
    
    private double calculateUtilization(Semaphore semaphore, int poolSize) {
        int inUse = poolSize - semaphore.availablePermits();
        return (double) inUse / poolSize * 100.0;
    }
    
    /**
     * Shutdown all executor services gracefully
     */
    public void shutdown() {
        log.info("Shutting down bulkhead executor services");
        
        shutdownExecutor(paymentProcessingExecutor, "payment-processing");
        shutdownExecutor(kycVerificationExecutor, "kyc-verification");
        shutdownExecutor(fraudDetectionExecutor, "fraud-detection");
        shutdownExecutor(notificationExecutor, "notification");
        shutdownExecutor(analyticsExecutor, "analytics");
        shutdownExecutor(coreBankingExecutor, "core-banking");
    }
    
    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor {} did not terminate within 30 seconds, forcing shutdown", name);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}