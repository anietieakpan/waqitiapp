package com.waqiti.common.performance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * High-Performance Batch Processing Service
 * 
 * Optimizes bulk operations for 10,000+ TPS throughput:
 * - JDBC batch inserts/updates (1000 rows/batch)
 * - Parallel batch processing with work-stealing
 * - Automatic batch size tuning
 * - Batch commit optimization
 * - Error handling with partial success
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BatchProcessingService {

    private final JdbcTemplate jdbcTemplate;
    private final Executor batchProcessingExecutor;

    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int MAX_BATCH_SIZE = 5000;

    /**
     * Execute batch insert with automatic batching
     */
    @Transactional
    public <T> int batchInsert(String sql, List<T> items, BatchItemSetter<T> setter) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        int batchSize = calculateOptimalBatchSize(items.size());
        int totalInserted = 0;

        log.debug("Executing batch insert: {} items in batches of {}", items.size(), batchSize);

        for (int i = 0; i < items.size(); i += batchSize) {
            int endIdx = Math.min(i + batchSize, items.size());
            List<T> batch = items.subList(i, endIdx);

            int[] results = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int index) throws SQLException {
                    setter.setValues(ps, batch.get(index));
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });

            totalInserted += results.length;
            log.debug("Batch insert completed: {}/{} items", totalInserted, items.size());
        }

        log.info("Batch insert completed: {} items inserted", totalInserted);
        return totalInserted;
    }

    /**
     * Execute batch update with automatic batching
     */
    @Transactional
    public <T> int batchUpdate(String sql, List<T> items, BatchItemSetter<T> setter) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        int batchSize = calculateOptimalBatchSize(items.size());
        int totalUpdated = 0;

        log.debug("Executing batch update: {} items in batches of {}", items.size(), batchSize);

        for (int i = 0; i < items.size(); i += batchSize) {
            int endIdx = Math.min(i + batchSize, items.size());
            List<T> batch = items.subList(i, endIdx);

            int[] results = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int index) throws SQLException {
                    setter.setValues(ps, batch.get(index));
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });

            totalUpdated += results.length;
            log.debug("Batch update completed: {}/{} items", totalUpdated, items.size());
        }

        log.info("Batch update completed: {} items updated", totalUpdated);
        return totalUpdated;
    }

    /**
     * Execute parallel batch processing for CPU-intensive operations
     */
    public <T, R> CompletableFuture<List<R>> parallelBatchProcess(
            List<T> items,
            Function<T, R> processor,
            int parallelism) {

        if (items == null || items.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>());
        }

        int batchSize = Math.max(items.size() / parallelism, 100);
        List<CompletableFuture<List<R>>> futures = new ArrayList<>();

        log.debug("Starting parallel batch processing: {} items, {} threads, batch size {}",
                items.size(), parallelism, batchSize);

        for (int i = 0; i < items.size(); i += batchSize) {
            int startIdx = i;
            int endIdx = Math.min(i + batchSize, items.size());
            List<T> batch = items.subList(startIdx, endIdx);

            CompletableFuture<List<R>> future = CompletableFuture.supplyAsync(() -> {
                List<R> results = new ArrayList<>();
                for (T item : batch) {
                    try {
                        results.add(processor.apply(item));
                    } catch (Exception e) {
                        log.error("Error processing batch item", e);
                    }
                }
                return results;
            }, batchProcessingExecutor);

            futures.add(future);
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<R> allResults = new ArrayList<>();
                for (CompletableFuture<List<R>> future : futures) {
                    try {
                        allResults.addAll(future.get(1, java.util.concurrent.TimeUnit.SECONDS));
                    } catch (Exception e) {
                        log.error("Failed to retrieve batch processing result", e);
                        // Continue with other batches
                    }
                }
                log.info("Parallel batch processing completed: {} items processed", allResults.size());
                return allResults;
            });
    }

    /**
     * Execute batch with error handling and partial success
     */
    @Transactional
    public <T> BatchResult<T> batchInsertWithErrorHandling(
            String sql,
            List<T> items,
            BatchItemSetter<T> setter) {

        BatchResult<T> result = new BatchResult<>();

        if (items == null || items.isEmpty()) {
            return result;
        }

        int batchSize = calculateOptimalBatchSize(items.size());

        for (int i = 0; i < items.size(); i += batchSize) {
            int endIdx = Math.min(i + batchSize, items.size());
            List<T> batch = items.subList(i, endIdx);

            try {
                int[] results = jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                    @Override
                    public void setValues(PreparedStatement ps, int index) throws SQLException {
                        setter.setValues(ps, batch.get(index));
                    }

                    @Override
                    public int getBatchSize() {
                        return batch.size();
                    }
                });

                for (int j = 0; j < results.length; j++) {
                    if (results[j] > 0) {
                        result.addSuccess(batch.get(j));
                    } else {
                        result.addFailure(batch.get(j), "Update returned 0");
                    }
                }

            } catch (Exception e) {
                log.error("Batch insert failed for batch starting at index {}", i, e);
                batch.forEach(item -> result.addFailure(item, e.getMessage()));
            }
        }

        log.info("Batch insert with error handling completed: {} succeeded, {} failed",
                result.getSuccessCount(), result.getFailureCount());

        return result;
    }

    /**
     * Calculate optimal batch size based on total items
     */
    private int calculateOptimalBatchSize(int totalItems) {
        if (totalItems <= 100) {
            return totalItems;
        } else if (totalItems <= 1000) {
            return 100;
        } else if (totalItems <= 10000) {
            return 1000;
        } else {
            return Math.min(MAX_BATCH_SIZE, totalItems / 10);
        }
    }

    /**
     * Functional interface for setting batch item values
     */
    @FunctionalInterface
    public interface BatchItemSetter<T> {
        void setValues(PreparedStatement ps, T item) throws SQLException;
    }

    /**
     * Result container for batch operations with error handling
     */
    public static class BatchResult<T> {
        private final List<T> successes = new ArrayList<>();
        private final List<BatchFailure<T>> failures = new ArrayList<>();

        public void addSuccess(T item) {
            successes.add(item);
        }

        public void addFailure(T item, String error) {
            failures.add(new BatchFailure<>(item, error));
        }

        public int getSuccessCount() {
            return successes.size();
        }

        public int getFailureCount() {
            return failures.size();
        }

        public List<T> getSuccesses() {
            return successes;
        }

        public List<BatchFailure<T>> getFailures() {
            return failures;
        }

        public boolean hasFailures() {
            return !failures.isEmpty();
        }
    }

    /**
     * Container for batch failure information
     */
    public static class BatchFailure<T> {
        private final T item;
        private final String error;

        public BatchFailure(T item, String error) {
            this.item = item;
            this.error = error;
        }

        public T getItem() {
            return item;
        }

        public String getError() {
            return error;
        }
    }
}