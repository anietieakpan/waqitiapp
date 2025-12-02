package com.waqiti.common.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * High-performance batch processing service for database operations and bulk processing
 */
@Service
@Slf4j
public class BatchProcessingService {

    private final BatchConfiguration batchConfiguration;
    private final ExecutorService executorService;
    private final Map<String, Queue<BatchOperation>> batchQueues = new ConcurrentHashMap<>();
    private final Map<String, InternalBatchConfig> batchConfigs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BatchJobStatus> activeJobs = new ConcurrentHashMap<>();
    
    // Statistics tracking
    private final AtomicLong totalBatchJobs = new AtomicLong(0);
    private final AtomicLong completedJobs = new AtomicLong(0);
    private final AtomicLong failedJobs = new AtomicLong(0);
    private final AtomicLong totalItemsProcessed = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);

    public BatchProcessingService(BatchConfiguration batchConfiguration) {
        this.batchConfiguration = batchConfiguration;
        this.executorService = Executors.newFixedThreadPool(
            batchConfiguration.getConcurrencyLevel(),
            r -> {
                Thread t = new Thread(r, "batch-processor-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            }
        );
        
        log.info("BatchProcessingService initialized with batch size: {}, concurrency: {}, timeout: {}ms",
            batchConfiguration.getBatchSize(), batchConfiguration.getConcurrencyLevel(), 
            batchConfiguration.getTimeoutMs());
    }

    /**
     * Register a batch processor for a specific operation type
     */
    public void registerBatchProcessor(String operationType, InternalBatchConfig config) {
        batchConfigs.put(operationType, config);
        batchQueues.put(operationType, new ConcurrentLinkedQueue<>());
        log.info("Registered batch processor for operation type: {}", operationType);
    }

    /**
     * Process a list of items in batches with functional processor
     */
    @Async
    public <T> CompletableFuture<BatchResult<T>> processBatch(
            List<T> items, 
            BulkOperationProcessor<T> processor) {
        
        return processBatch(items, processor, null);
    }

    /**
     * Process a list of items in batches with custom batch ID
     */
    @Async
    public <T> CompletableFuture<BatchResult<T>> processBatch(
            List<T> items, 
            BulkOperationProcessor<T> processor,
            String customBatchId) {
        
        String batchId = customBatchId != null ? customBatchId : generateBatchId();
        Instant startTime = Instant.now();

        totalBatchJobs.incrementAndGet();

        // Create job status
        BatchJobStatus jobStatus = BatchJobStatus.builder()
            .batchId(batchId)
            .status(BatchStatus.RUNNING)
            .totalItems(items.size())
            .processedItems(0)
            .startTime(LocalDateTime.now())
            .build();

        activeJobs.put(batchId, jobStatus);

        log.info("Starting batch processing: {} with {} items", batchId, items.size());

        return CompletableFuture.supplyAsync(() -> {
            try {
                return processBatchInternal(items, processor, batchId, jobStatus, startTime);
            } catch (Exception e) {
                failedJobs.incrementAndGet();
                jobStatus.setStatus(BatchStatus.FAILED);
                jobStatus.setEndTime(LocalDateTime.now());
                jobStatus.setErrorMessage(e.getMessage());
                
                log.error("Batch processing failed for batch: {}", batchId, e);
                
                return BatchResult.<T>builder()
                    .batchId(batchId)
                    .totalCount(items.size())
                    .successfulCount(0)
                    .failedCount(items.size())
                    .results(new ArrayList<>())
                    .errors(List.of(BatchError.builder()
                        .error("Batch processing failed: " + e.getMessage())
                        .build()))
                    .duration(Duration.between(startTime, Instant.now()))
                    .build();
            } finally {
                activeJobs.remove(batchId);
            }
        }, executorService);
    }

    /**
     * Add operation to batch queue
     */
    public <T> CompletableFuture<Void> addToBatch(String operationType, T data) {
        Queue<BatchOperation> queue = batchQueues.get(operationType);
        if (queue == null) {
            throw new IllegalArgumentException("No batch processor registered for: " + operationType);
        }

        BatchOperation operation = new BatchOperation(data, LocalDateTime.now());
        queue.offer(operation);

        // Check if batch is ready for immediate processing
        InternalBatchConfig config = batchConfigs.get(operationType);
        if (queue.size() >= config.getBatchSize()) {
            return processBatchAsync(operationType);
        }

        return CompletableFuture.completedFuture(null);
    }

    private <T> BatchResult<T> processBatchInternal(
            List<T> items,
            BulkOperationProcessor<T> processor,
            String batchId,
            BatchJobStatus jobStatus,
            Instant startTime) {
        
        List<T> results = new ArrayList<>();
        List<BatchError> errors = new ArrayList<>();
        AtomicInteger processedCount = new AtomicInteger(0);
        int batchSize = batchConfiguration.getBatchSize();
        
        // Process items in batches
        List<List<T>> batches = createBatches(items, batchSize);
        List<CompletableFuture<Void>> batchFutures = new ArrayList<>();
        
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            List<T> batch = batches.get(batchIndex);
            final int currentBatchIndex = batchIndex;
            
            CompletableFuture<Void> batchFuture = CompletableFuture.runAsync(() -> {
                processSingleBatch(batch, processor, results, errors, processedCount, 
                    currentBatchIndex * batchSize, batchId, jobStatus);
            }, executorService);
            
            batchFutures.add(batchFuture);
        }
        
        // Wait for all batches to complete with timeout
        try {
            CompletableFuture<Void> allBatches = CompletableFuture.allOf(
                batchFutures.toArray(new CompletableFuture[0])
            );
            
            allBatches.get(batchConfiguration.getTimeoutMs(), TimeUnit.MILLISECONDS);
            
        } catch (TimeoutException e) {
            log.warn("Batch processing timeout for batch: {}", batchId);
            batchFutures.forEach(future -> future.cancel(true));
            
            errors.add(BatchError.builder()
                .error("Batch processing timed out after " + batchConfiguration.getTimeoutMs() + "ms")
                .build());
                
        } catch (InterruptedException | ExecutionException e) {
            log.error("Batch processing interrupted for batch: {}", batchId, e);
            
            errors.add(BatchError.builder()
                .error("Batch processing interrupted: " + e.getMessage())
                .build());
        }
        
        LocalDateTime endTime = LocalDateTime.now();
        Instant endTimeInstant = Instant.now();
        Duration duration = Duration.between(startTime, endTimeInstant);
        
        // Update job status
        jobStatus.setProcessedItems(processedCount.get());
        jobStatus.setEndTime(endTime);
        jobStatus.setStatus(errors.isEmpty() ? BatchStatus.COMPLETED : BatchStatus.FAILED);
        
        // Update statistics
        if (errors.isEmpty()) {
            completedJobs.incrementAndGet();
        } else {
            failedJobs.incrementAndGet();
        }
        
        totalItemsProcessed.addAndGet(processedCount.get());
        totalProcessingTime.addAndGet(duration.toMillis());
        
        int successfulCount = processedCount.get() - errors.size();
        
        BatchResult<T> result = BatchResult.<T>builder()
            .batchId(batchId)
            .totalCount(items.size())
            .successfulCount(successfulCount)
            .failedCount(errors.size())
            .results(results)
            .errors(errors)
            .duration(duration)
            .build();
        
        log.info("Batch processing completed: {} - {}", batchId, result.getSummary());
        
        return result;
    }

    private <T> void processSingleBatch(
            List<T> batch,
            BulkOperationProcessor<T> processor,
            List<T> results,
            List<BatchError> errors,
            AtomicInteger processedCount,
            int baseIndex,
            String batchId,
            BatchJobStatus jobStatus) {
        
        for (int i = 0; i < batch.size(); i++) {
            T item = batch.get(i);
            int itemIndex = baseIndex + i;
            
            try {
                processor.process(item);
                
                synchronized (results) {
                    results.add(item);
                }
                
                processedCount.incrementAndGet();
                
                // Update progress
                jobStatus.setProcessedItems(processedCount.get());
                
                if (processedCount.get() % 100 == 0) {
                    log.debug("Batch {} progress: {}/{} items processed", 
                        batchId, processedCount.get(), jobStatus.getTotalItems());
                }
                
            } catch (Exception e) {
                log.warn("Failed to process item at index {} in batch {}: {}", 
                    itemIndex, batchId, e.getMessage());
                
                BatchError error = BatchError.builder()
                    .item(item)
                    .error(e.getMessage())
                    .index(itemIndex)
                    .itemId(getItemId(item))
                    .build();
                
                synchronized (errors) {
                    errors.add(error);
                }
            }
        }
    }

    private <T> List<List<T>> createBatches(List<T> items, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        
        for (int i = 0; i < items.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, items.size());
            batches.add(new ArrayList<>(items.subList(i, endIndex)));
        }
        
        return batches;
    }

    private <T> String getItemId(T item) {
        if (item == null) return "NULL_ITEM";
        
        // Try to get ID using reflection for common ID field names
        try {
            Class<?> clazz = item.getClass();
            
            // Try common ID field names
            String[] idFields = {"id", "getId", "uuid", "getUuid", "identifier", "getIdentifier"};
            
            for (String fieldName : idFields) {
                try {
                    if (fieldName.startsWith("get")) {
                        // Try method
                        return String.valueOf(clazz.getMethod(fieldName).invoke(item));
                    } else {
                        // Try field
                        return String.valueOf(clazz.getField(fieldName).get(item));
                    }
                } catch (Exception ignored) {
                    // Continue to next field name
                }
            }
            
        } catch (Exception e) {
            log.debug("Could not extract ID from item: {}", e.getMessage());
        }
        
        return item.toString();
    }

    /**
     * Process batches on schedule
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void processScheduledBatches() {
        batchQueues.forEach((operationType, queue) -> {
            if (!queue.isEmpty()) {
                InternalBatchConfig config = batchConfigs.get(operationType);
                LocalDateTime oldestAllowed = LocalDateTime.now().minus(config.getMaxWaitTime());
                
                // Check if oldest operation exceeds wait time
                BatchOperation oldest = queue.peek();
                if (oldest != null && oldest.getTimestamp().isBefore(oldestAllowed)) {
                    processBatchAsync(operationType);
                }
            }
        });
    }

    /**
     * Process batch asynchronously
     */
    @Async("batchExecutor")
    public CompletableFuture<Void> processBatchAsync(String operationType) {
        return CompletableFuture.runAsync(() -> processBatch(operationType));
    }

    /**
     * Process batch operations
     */
    @Transactional
    public void processBatch(String operationType) {
        Queue<BatchOperation> queue = batchQueues.get(operationType);
        InternalBatchConfig config = batchConfigs.get(operationType);
        
        if (queue == null || config == null) {
            return;
        }

        List<BatchOperation> operations = new ArrayList<>();
        BatchOperation operation;
        
        // Collect batch operations up to batch size
        while (operations.size() < config.getBatchSize() && (operation = queue.poll()) != null) {
            operations.add(operation);
        }

        if (operations.isEmpty()) {
            return;
        }

        try {
            log.debug("Processing batch of {} operations for type: {}", operations.size(), operationType);
            
            // Extract data from operations
            List<Object> data = operations.stream()
                .map(BatchOperation::getData)
                .toList();

            // Execute batch processor
            config.getProcessor().accept(data);
            
            log.info("Successfully processed batch of {} {} operations", 
                    operations.size(), operationType);
                    
        } catch (Exception e) {
            log.error("Failed to process batch for operation type: {}", operationType, e);
            
            // Re-queue failed operations for retry (with limit)
            operations.forEach(op -> {
                if (op.getRetryCount() < config.getMaxRetries()) {
                    op.incrementRetryCount();
                    queue.offer(op);
                }
            });
        }
    }

    /**
     * Get batch statistics (legacy method)
     */
    public Map<String, BatchStats> getBatchStatistics() {
        Map<String, BatchStats> stats = new HashMap<>();
        
        batchQueues.forEach((operationType, queue) -> {
            InternalBatchConfig config = batchConfigs.get(operationType);
            stats.put(operationType, BatchStats.builder()
                .operationType(operationType)
                .queueSize(queue.size())
                .batchSize(config.getBatchSize())
                .maxWaitTime(config.getMaxWaitTime())
                .build());
        });
        
        return stats;
    }

    /**
     * Get status of a batch job
     */
    public BatchJobStatus getJobStatus(String batchId) {
        return activeJobs.get(batchId);
    }

    /**
     * Get all active batch jobs
     */
    public List<BatchJobStatus> getActiveJobs() {
        return new ArrayList<>(activeJobs.values());
    }

    /**
     * Get comprehensive processing statistics
     */
    public BatchProcessingStatistics getStatistics() {
        long running = activeJobs.size();
        long completed = completedJobs.get();
        long failed = failedJobs.get();
        long total = totalBatchJobs.get();
        
        double successRate = total > 0 ? ((double) completed / total) * 100 : 0;
        double avgProcessingTime = completed > 0 ? 
            (double) totalProcessingTime.get() / completed : 0;
        
        return BatchProcessingStatistics.builder()
            .totalBatchJobs(total)
            .runningJobs(running)
            .completedJobs(completed)
            .failedJobs(failed)
            .successRate(successRate)
            .totalItemsProcessed(totalItemsProcessed.get())
            .averageProcessingTime(avgProcessingTime)
            .queueSize((int) running)
            .build();
    }

    /**
     * Cancel a batch job
     */
    public boolean cancelJob(String batchId) {
        BatchJobStatus jobStatus = activeJobs.get(batchId);
        if (jobStatus != null && jobStatus.getStatus() == BatchStatus.RUNNING) {
            jobStatus.setStatus(BatchStatus.FAILED);
            jobStatus.setEndTime(LocalDateTime.now());
            jobStatus.setErrorMessage("Job cancelled by user");
            
            activeJobs.remove(batchId);
            failedJobs.incrementAndGet();
            
            log.info("Batch job cancelled: {}", batchId);
            return true;
        }
        return false;
    }

    /**
     * Check if batch processing is healthy
     */
    public boolean isHealthy() {
        BatchProcessingStatistics stats = getStatistics();
        return stats.isHealthy();
    }

    private String generateBatchId() {
        return "batch-" + UUID.randomUUID().toString().substring(0, 8) + "-" + 
               System.currentTimeMillis();
    }

    /**
     * Shutdown the service gracefully
     */
    public void shutdown() {
        log.info("Shutting down BatchProcessingService...");
        
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate gracefully, forcing shutdown");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.error("Shutdown interrupted", e);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("BatchProcessingService shutdown completed");
    }

    /**
     * Clear all queues (for testing)
     */
    public void clearAllQueues() {
        batchQueues.values().forEach(Queue::clear);
    }

    /**
     * Batch operation wrapper
     */
    private static class BatchOperation {
        private final Object data;
        private final LocalDateTime timestamp;
        private int retryCount = 0;

        public BatchOperation(Object data, LocalDateTime timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }

        public Object getData() { return data; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public int getRetryCount() { return retryCount; }
        public void incrementRetryCount() { retryCount++; }
    }
    
    /**
     * Internal batch configuration for queued operations
     */
    @lombok.Data
    @lombok.Builder
    public static class InternalBatchConfig {
        private int batchSize;
        private java.time.Duration maxWaitTime;
        private int maxRetries;
        private Consumer<List<Object>> processor;
    }
}

/**
 * Batch statistics for queued operations
 */
@lombok.Data
@lombok.Builder
class BatchStats {
    private String operationType;
    private int queueSize;
    private int batchSize;
    private java.time.Duration maxWaitTime;
}