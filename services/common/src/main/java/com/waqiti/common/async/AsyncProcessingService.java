package com.waqiti.common.async;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Comprehensive async processing service for heavy operations
 * Handles background processing, parallel execution, and async workflows
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncProcessingService {
    
    private final MeterRegistry meterRegistry;
    
    // Dedicated executor services for different operation types
    private final ExecutorService heavyOperationExecutor = createExecutor("heavy-ops", 10, 50);
    private final ExecutorService ioOperationExecutor = createExecutor("io-ops", 20, 100);
    private final ExecutorService cpuIntensiveExecutor = createExecutor("cpu-ops", 
        Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors() * 2);
    private final ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(5);
    
    // Task tracking
    private final Map<String, AsyncTaskStatus> taskStatuses = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<?>> runningTasks = new ConcurrentHashMap<>();
    private final AtomicLong taskCounter = new AtomicLong(0);

    // SECURITY FIX: Use SecureRandom instead of Math.random()
    private static final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Execute heavy operation asynchronously with progress tracking
     */
    @Async("heavyTaskExecutor")
    public <T> CompletableFuture<AsyncResult<T>> executeHeavyOperation(
            String operationName, 
            Supplier<T> operation,
            ProgressCallback progressCallback) {
        
        String taskId = generateTaskId(operationName);
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Initialize task status
            AsyncTaskStatus status = AsyncTaskStatus.builder()
                .taskId(taskId)
                .operationName(operationName)
                .status(TaskStatus.RUNNING)
                .startTime(System.currentTimeMillis())
                .progress(0)
                .build();
            taskStatuses.put(taskId, status);
            
            // Execute operation with progress tracking
            CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                try {
                    log.info("Starting heavy operation: {} (taskId: {})", operationName, taskId);
                    
                    // Wrap operation with progress updates
                    T result = executeWithProgress(operation, taskId, progressCallback);
                    
                    // Update status on completion
                    updateTaskStatus(taskId, TaskStatus.COMPLETED, 100, null);
                    log.info("Completed heavy operation: {} (taskId: {})", operationName, taskId);
                    
                    return result;
                    
                } catch (Exception e) {
                    log.error("Failed heavy operation: {} (taskId: {})", operationName, taskId, e);
                    updateTaskStatus(taskId, TaskStatus.FAILED, -1, e.getMessage());
                    throw new AsyncOperationException("Heavy operation failed", e);
                }
            }, heavyOperationExecutor);
            
            runningTasks.put(taskId, future);
            
            return future.thenApply(result -> {
                sample.stop(Timer.builder("async.heavy.operation")
                    .tag("operation", operationName)
                    .tag("status", "success")
                    .register(meterRegistry));
                runningTasks.remove(taskId);
                return new AsyncResult<>(result);
            }).exceptionally(ex -> {
                sample.stop(Timer.builder("async.heavy.operation")
                    .tag("operation", operationName)
                    .tag("status", "failure")
                    .register(meterRegistry));
                runningTasks.remove(taskId);
                throw new CompletionException(ex);
            });
            
        } catch (Exception e) {
            log.error("Error initiating heavy operation: {}", operationName, e);
            updateTaskStatus(taskId, TaskStatus.FAILED, -1, e.getMessage());
            throw new AsyncOperationException("Failed to initiate heavy operation", e);
        }
    }
    
    /**
     * Execute multiple operations in parallel
     */
    public <T> CompletableFuture<List<T>> executeParallel(
            List<Supplier<T>> operations,
            ExecutionStrategy strategy) {
        
        log.info("Executing {} operations in parallel with strategy: {}", operations.size(), strategy);
        
        ExecutorService executor = selectExecutor(strategy);
        List<CompletableFuture<T>> futures = new ArrayList<>();
        
        for (int i = 0; i < operations.size(); i++) {
            final int index = i;
            CompletableFuture<T> future = CompletableFuture.supplyAsync(() -> {
                try {
                    log.debug("Executing parallel operation {}", index);
                    return operations.get(index).get();
                } catch (Exception e) {
                    log.error("Parallel operation {} failed", index, e);
                    throw new AsyncOperationException("Parallel operation failed", e);
                }
            }, executor);
            futures.add(future);
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }
    
    /**
     * Execute operations with circuit breaker pattern
     */
    public <T> CompletableFuture<T> executeWithCircuitBreaker(
            String operationName,
            Supplier<T> operation,
            CircuitBreakerConfig config) {
        
        String circuitBreakerId = "cb:" + operationName;
        CircuitBreakerState state = getCircuitBreakerState(circuitBreakerId);
        
        if (state.isOpen()) {
            log.warn("Circuit breaker is open for operation: {}", operationName);
            return CompletableFuture.failedFuture(
                new CircuitBreakerOpenException("Circuit breaker is open"));
        }
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                T result = operation.get();
                state.recordSuccess();
                return result;
            } catch (Exception e) {
                state.recordFailure();
                if (state.shouldOpen(config)) {
                    log.error("Opening circuit breaker for operation: {}", operationName);
                    state.open();
                }
                throw new AsyncOperationException("Operation failed", e);
            }
        }, heavyOperationExecutor);
    }
    
    /**
     * Execute with retry and exponential backoff
     */
    public <T> CompletableFuture<T> executeWithRetry(
            String operationName,
            Supplier<T> operation,
            RetryConfig config) {
        
        return executeWithRetryInternal(operationName, operation, config, 0);
    }
    
    private <T> CompletableFuture<T> executeWithRetryInternal(
            String operationName,
            Supplier<T> operation,
            RetryConfig config,
            int attemptNumber) {
        
        if (attemptNumber >= config.getMaxAttempts()) {
            return CompletableFuture.failedFuture(
                new MaxRetriesExceededException("Max retries exceeded for " + operationName));
        }
        
        return CompletableFuture.supplyAsync(operation, heavyOperationExecutor)
            .exceptionally(ex -> {
                log.warn("Operation {} failed on attempt {}, retrying...", 
                    operationName, attemptNumber + 1);
                    
                long delay = calculateBackoffDelay(attemptNumber, config);
                
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AsyncOperationException("Retry interrupted", e);
                }
                
                return executeWithRetryInternal(operationName, operation, config, attemptNumber + 1)
                    .join();
            });
    }
    
    /**
     * Execute batch operations with controlled concurrency
     */
    public <T, R> CompletableFuture<List<R>> executeBatch(
            List<T> items,
            Function<T, R> processor,
            int batchSize,
            int maxConcurrency) {
        
        log.info("Processing batch of {} items with batch size {} and max concurrency {}", 
            items.size(), batchSize, maxConcurrency);
            
        Semaphore semaphore = new Semaphore(maxConcurrency);
        List<CompletableFuture<List<R>>> batchFutures = new ArrayList<>();
        
        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            List<T> batch = items.subList(i, endIndex);
            
            CompletableFuture<List<R>> batchFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    semaphore.acquire();
                    log.debug("Processing batch of {} items", batch.size());
                    
                    return batch.stream()
                        .map(processor)
                        .toList();
                        
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AsyncOperationException("Batch processing interrupted", e);
                } finally {
                    semaphore.release();
                }
            }, ioOperationExecutor);
            
            batchFutures.add(batchFuture);
        }
        
        return CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0]))
            .thenApply(v -> batchFutures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList());
    }
    
    /**
     * Schedule delayed execution
     */
    public <T> ScheduledFuture<T> scheduleDelayed(
            String operationName,
            Supplier<T> operation,
            Duration delay) {
        
        log.info("Scheduling operation {} with delay of {}", operationName, delay);
        
        return (ScheduledFuture<T>) scheduledExecutor.schedule(() -> {
            try {
                log.info("Executing scheduled operation: {}", operationName);
                T result = operation.get();
                meterRegistry.counter("async.scheduled.success", "operation", operationName).increment();
                return result;
            } catch (Exception e) {
                log.error("Scheduled operation failed: {}", operationName, e);
                meterRegistry.counter("async.scheduled.failure", "operation", operationName).increment();
                throw new AsyncOperationException("Scheduled operation failed", e);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
    
    /**
     * Execute with timeout
     */
    public <T> CompletableFuture<T> executeWithTimeout(
            String operationName,
            Supplier<T> operation,
            Duration timeout) {
        
        log.info("Executing operation {} with timeout of {}", operationName, timeout);
        
        CompletableFuture<T> future = CompletableFuture.supplyAsync(operation, heavyOperationExecutor);
        
        return future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                if (ex instanceof TimeoutException) {
                    log.error("Operation {} timed out after {}", operationName, timeout);
                    meterRegistry.counter("async.timeout", "operation", operationName).increment();
                    throw new AsyncTimeoutException("Operation timed out", ex);
                }
                throw new AsyncOperationException("Operation failed", ex);
            });
    }
    
    /**
     * Fire and forget execution
     */
    public void executeFireAndForget(String operationName, Runnable operation) {
        log.debug("Executing fire-and-forget operation: {}", operationName);
        
        CompletableFuture.runAsync(() -> {
            try {
                operation.run();
                log.debug("Fire-and-forget operation completed: {}", operationName);
                meterRegistry.counter("async.fireandforget.success", "operation", operationName).increment();
            } catch (Exception e) {
                log.error("Fire-and-forget operation failed: {}", operationName, e);
                meterRegistry.counter("async.fireandforget.failure", "operation", operationName).increment();
            }
        }, heavyOperationExecutor);
    }
    
    /**
     * Get task status
     */
    public AsyncTaskStatus getTaskStatus(String taskId) {
        return taskStatuses.get(taskId);
    }
    
    /**
     * Cancel running task
     */
    public boolean cancelTask(String taskId) {
        CompletableFuture<?> task = runningTasks.get(taskId);
        if (task != null) {
            boolean cancelled = task.cancel(true);
            if (cancelled) {
                updateTaskStatus(taskId, TaskStatus.CANCELLED, -1, "Task cancelled by user");
                runningTasks.remove(taskId);
            }
            return cancelled;
        }
        return false;
    }
    
    /**
     * Get async processing statistics
     */
    public AsyncProcessingStatistics getStatistics() {
        long totalTasks = taskCounter.get();
        long runningTaskCount = runningTasks.size();
        long completedTasks = taskStatuses.values().stream()
            .filter(s -> s.getStatus() == TaskStatus.COMPLETED)
            .count();
        long failedTasks = taskStatuses.values().stream()
            .filter(s -> s.getStatus() == TaskStatus.FAILED)
            .count();
            
        return AsyncProcessingStatistics.builder()
            .totalTasksProcessed(totalTasks)
            .currentlyRunning(runningTaskCount)
            .completedTasks(completedTasks)
            .failedTasks(failedTasks)
            .successRate(totalTasks > 0 ? (double) completedTasks / totalTasks * 100 : 0)
            .heavyOpsQueueSize(((ThreadPoolExecutor) heavyOperationExecutor).getQueue().size())
            .ioOpsQueueSize(((ThreadPoolExecutor) ioOperationExecutor).getQueue().size())
            .cpuOpsQueueSize(((ThreadPoolExecutor) cpuIntensiveExecutor).getQueue().size())
            .build();
    }
    
    // Helper methods
    
    private ExecutorService createExecutor(String name, int coreSize, int maxSize) {
        return new ThreadPoolExecutor(
            coreSize,
            maxSize,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            r -> {
                Thread thread = new Thread(r);
                thread.setName("async-" + name + "-" + thread.getId());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    private ExecutorService selectExecutor(ExecutionStrategy strategy) {
        return switch (strategy) {
            case IO_INTENSIVE -> ioOperationExecutor;
            case CPU_INTENSIVE -> cpuIntensiveExecutor;
            case HEAVY_OPERATION -> heavyOperationExecutor;
        };
    }
    
    private String generateTaskId(String operationName) {
        return String.format("%s-%d-%s", 
            operationName, 
            taskCounter.incrementAndGet(), 
            UUID.randomUUID().toString().substring(0, 8));
    }
    
    private <T> T executeWithProgress(
            Supplier<T> operation,
            String taskId,
            ProgressCallback callback) {
        
        // This is a simplified version - real implementation would need
        // operation to support progress reporting
        if (callback != null) {
            callback.onProgress(0, "Starting operation");
            updateTaskStatus(taskId, TaskStatus.RUNNING, 0, null);
            
            callback.onProgress(50, "Processing");
            updateTaskStatus(taskId, TaskStatus.RUNNING, 50, null);
        }
        
        T result = operation.get();
        
        if (callback != null) {
            callback.onProgress(100, "Completed");
            updateTaskStatus(taskId, TaskStatus.COMPLETED, 100, null);
        }
        
        return result;
    }
    
    private void updateTaskStatus(String taskId, TaskStatus status, int progress, String error) {
        AsyncTaskStatus taskStatus = taskStatuses.get(taskId);
        if (taskStatus != null) {
            taskStatus.setStatus(status);
            taskStatus.setProgress(progress);
            taskStatus.setError(error);
            if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED) {
                taskStatus.setEndTime(System.currentTimeMillis());
            }
        }
    }
    
    private long calculateBackoffDelay(int attemptNumber, RetryConfig config) {
        long baseDelay = config.getInitialDelay().toMillis();
        long maxDelay = config.getMaxDelay().toMillis();
        long delay = Math.min(baseDelay * (long) Math.pow(2, attemptNumber), maxDelay);
        
        // Add jitter
        if (config.isJitterEnabled()) {
            // SECURITY FIX: Use SecureRandom instead of Math.random()
            delay = (long) (delay * (0.5 + secureRandom.nextDouble() * 0.5));
        }
        
        return delay;
    }
    
    private final Map<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();
    
    private CircuitBreakerState getCircuitBreakerState(String id) {
        return circuitBreakers.computeIfAbsent(id, k -> new CircuitBreakerState());
    }
    
    // Internal classes
    
    private static class CircuitBreakerState {
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private volatile boolean open = false;
        private volatile long openTime = 0;
        
        public boolean isOpen() {
            if (open && System.currentTimeMillis() - openTime > 60000) { // 1 minute timeout
                close();
            }
            return open;
        }
        
        public void open() {
            open = true;
            openTime = System.currentTimeMillis();
        }
        
        public void close() {
            open = false;
            failureCount.set(0);
            successCount.set(0);
        }
        
        public void recordSuccess() {
            successCount.incrementAndGet();
            if (failureCount.get() > 0) {
                failureCount.decrementAndGet();
            }
        }
        
        public void recordFailure() {
            failureCount.incrementAndGet();
        }
        
        public boolean shouldOpen(CircuitBreakerConfig config) {
            return failureCount.get() >= config.getFailureThreshold();
        }
    }
}