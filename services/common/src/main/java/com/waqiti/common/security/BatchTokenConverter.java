package com.waqiti.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Batch Token Converter for converting legacy JWT tokens to Keycloak tokens
 * Handles large-scale batch conversions with minimal system impact
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchTokenConverter {
    
    private final TokenMigrationUtility tokenMigrationUtility;
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    
    @Value("${batch.converter.threads:20}")
    private int threadPoolSize;
    
    @Value("${batch.converter.batch-size:500}")
    private int batchSize;
    
    @Value("${batch.converter.delay-between-batches-ms:2000}")
    private long delayBetweenBatches;
    
    @Value("${batch.converter.max-retries:3}")
    private int maxRetries;
    
    @Value("${batch.converter.enabled:true}")
    private boolean enabled;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(20);
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    private final AtomicInteger totalProcessed = new AtomicInteger(0);
    private final AtomicInteger successfulConversions = new AtomicInteger(0);
    private final AtomicInteger failedConversions = new AtomicInteger(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    
    private volatile ConversionStatus currentStatus = ConversionStatus.IDLE;
    private volatile Instant conversionStartTime;
    private volatile Instant conversionEndTime;
    
    public enum ConversionStatus {
        IDLE,
        PREPARING,
        IN_PROGRESS,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }
    
    /**
     * Start batch conversion process
     */
    @Async
    public CompletableFuture<ConversionReport> startBatchConversion(ConversionRequest request) {
        if (!enabled) {
            log.warn("Batch converter is disabled");
            return CompletableFuture.completedFuture(
                ConversionReport.failed("Batch converter is disabled")
            );
        }
        
        if (currentStatus == ConversionStatus.IN_PROGRESS) {
            log.warn("Conversion already in progress");
            return CompletableFuture.completedFuture(
                ConversionReport.failed("Conversion already in progress")
            );
        }
        
        log.info("Starting batch conversion with request: {}", request);
        currentStatus = ConversionStatus.PREPARING;
        conversionStartTime = Instant.now();
        resetCounters();
        
        try {
            // Prepare conversion
            List<TokenRecord> tokens = prepareTokensForConversion(request);
            log.info("Found {} tokens for conversion", tokens.size());
            
            if (tokens.isEmpty()) {
                currentStatus = ConversionStatus.COMPLETED;
                return CompletableFuture.completedFuture(
                    ConversionReport.success(0, 0, 0)
                );
            }
            
            currentStatus = ConversionStatus.IN_PROGRESS;
            
            // Process in batches
            List<CompletableFuture<BatchResult>> batchFutures = new ArrayList<>();
            for (int i = 0; i < tokens.size(); i += batchSize) {
                if (currentStatus == ConversionStatus.CANCELLED) {
                    log.info("Conversion cancelled");
                    break;
                }
                
                int endIndex = Math.min(i + batchSize, tokens.size());
                List<TokenRecord> batch = tokens.subList(i, endIndex);
                
                CompletableFuture<BatchResult> batchFuture = processBatch(batch, i / batchSize);
                batchFutures.add(batchFuture);
                
                // Add delay between batches to prevent overwhelming the system
                if (i + batchSize < tokens.size()) {
                    Thread.sleep(delayBetweenBatches);
                }
            }
            
            // Wait for all batches to complete
            CompletableFuture<Void> allBatches = CompletableFuture.allOf(
                batchFutures.toArray(new CompletableFuture[0])
            );

            try {
                allBatches.get(request.getTimeoutMinutes(), TimeUnit.MINUTES);
            } catch (TimeoutException e) {
                log.error("Batch conversion timed out after {} minutes", request.getTimeoutMinutes(), e);
                batchFutures.forEach(f -> f.cancel(true));
                throw new RuntimeException("Batch conversion timed out", e);
            } catch (ExecutionException e) {
                log.error("Batch conversion execution failed", e.getCause());
                throw new RuntimeException("Batch conversion failed: " + e.getCause().getMessage(), e.getCause());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Batch conversion interrupted", e);
                throw new RuntimeException("Batch conversion interrupted", e);
            }

            // Aggregate results (safe to get with short timeout since allOf completed)
            List<BatchResult> batchResults = batchFutures.stream()
                .map(f -> {
                    try {
                        return f.get(1, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.error("Failed to retrieve batch result", e);
                        throw new RuntimeException("Failed to retrieve batch result", e);
                    }
                })
                .toList();
            
            ConversionReport report = aggregateResults(batchResults);
            
            // Update database with conversion results
            updateConversionRecords(batchResults);
            
            // Clean up old tokens if requested
            if (request.isCleanupOldTokens()) {
                cleanupOldTokens(tokens);
            }
            
            currentStatus = ConversionStatus.COMPLETED;
            conversionEndTime = Instant.now();
            
            log.info("Batch conversion completed: {}", report);
            return CompletableFuture.completedFuture(report);
            
        } catch (Exception e) {
            log.error("Batch conversion failed", e);
            currentStatus = ConversionStatus.FAILED;
            conversionEndTime = Instant.now();
            return CompletableFuture.completedFuture(
                ConversionReport.failed(e.getMessage())
            );
        }
    }
    
    /**
     * Process a single batch of tokens
     */
    private CompletableFuture<BatchResult> processBatch(List<TokenRecord> batch, int batchNumber) {
        return CompletableFuture.supplyAsync(() -> {
            log.debug("Processing batch {} with {} tokens", batchNumber, batch.size());
            long batchStartTime = System.currentTimeMillis();
            
            BatchResult result = new BatchResult();
            result.setBatchNumber(batchNumber);
            result.setTotalTokens(batch.size());
            
            List<CompletableFuture<ConversionResult>> futures = batch.stream()
                .map(token -> convertTokenWithRetry(token))
                .toList();

            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(5, java.util.concurrent.TimeUnit.MINUTES);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Batch {} token conversion timed out after 5 minutes", batchNumber, e);
                futures.forEach(f -> f.cancel(true));
                throw new RuntimeException("Batch token conversion timed out", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Batch {} token conversion execution failed", batchNumber, e.getCause());
                throw new RuntimeException("Batch token conversion failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Batch {} token conversion interrupted", batchNumber, e);
                throw new RuntimeException("Batch token conversion interrupted", e);
            }

            List<ConversionResult> conversionResults = futures.stream()
                .map(f -> {
                    try {
                        return f.get(1, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.error("Failed to retrieve conversion result", e);
                        // Return failure result instead of throwing
                        ConversionResult failureResult = new ConversionResult();
                        failureResult.setSuccess(false);
                        failureResult.setError("Failed to retrieve result: " + e.getMessage());
                        return failureResult;
                    }
                })
                .toList();
            
            // Count successes and failures
            long successCount = conversionResults.stream()
                .filter(ConversionResult::isSuccess)
                .count();
            
            result.setSuccessCount((int) successCount);
            result.setFailureCount((int) (batch.size() - successCount));
            result.setConversionResults(conversionResults);
            result.setProcessingTimeMs(System.currentTimeMillis() - batchStartTime);
            
            // Update counters
            totalProcessed.addAndGet(batch.size());
            successfulConversions.addAndGet(result.getSuccessCount());
            failedConversions.addAndGet(result.getFailureCount());
            totalProcessingTime.addAndGet(result.getProcessingTimeMs());
            
            log.info("Batch {} completed: {} success, {} failures in {}ms",
                batchNumber, result.getSuccessCount(), result.getFailureCount(), 
                result.getProcessingTimeMs());
            
            return result;
        }, executorService);
    }
    
    /**
     * Convert a single token with retry logic
     */
    private CompletableFuture<ConversionResult> convertTokenWithRetry(TokenRecord token) {
        return CompletableFuture.supplyAsync(() -> {
            int attempts = 0;
            Exception lastException = null;
            
            while (attempts < maxRetries) {
                try {
                    TokenMigrationUtility.MigrationResult migrationResult = 
                        tokenMigrationUtility.migrateSingleToken(token.getToken());
                    
                    if (migrationResult.isSuccess()) {
                        return ConversionResult.success(
                            token.getUserId(),
                            token.getToken(),
                            migrationResult.getAccessToken(),
                            migrationResult.getRefreshToken(),
                            migrationResult.getKeycloakUserId()
                        );
                    } else {
                        lastException = new RuntimeException(migrationResult.getErrorMessage());
                    }
                } catch (Exception e) {
                    lastException = e;
                    log.debug("Attempt {} failed for token {}: {}", 
                        attempts + 1, token.getTokenId(), e.getMessage());
                }
                
                attempts++;
                if (attempts < maxRetries) {
                    try {
                        Thread.sleep(1000 * attempts); // Exponential backoff
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            
            log.warn("Failed to convert token {} after {} attempts", 
                token.getTokenId(), attempts);
            
            return ConversionResult.failed(
                token.getUserId(),
                token.getToken(),
                lastException != null ? lastException.getMessage() : "Unknown error"
            );
        }, executorService);
    }
    
    /**
     * Prepare tokens for conversion from database
     */
    private List<TokenRecord> prepareTokensForConversion(ConversionRequest request) throws SQLException {
        List<TokenRecord> tokens = new ArrayList<>();
        
        String query = buildConversionQuery(request);
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            if (request.getUserIds() != null && !request.getUserIds().isEmpty()) {
                // Set user ID parameters
                int paramIndex = 1;
                for (String userId : request.getUserIds()) {
                    stmt.setString(paramIndex++, userId);
                }
            }
            
            if (request.getStartDate() != null) {
                stmt.setTimestamp(stmt.getParameterMetaData().getParameterCount() - 1, 
                    Timestamp.valueOf(request.getStartDate()));
            }
            
            if (request.getEndDate() != null) {
                stmt.setTimestamp(stmt.getParameterMetaData().getParameterCount(), 
                    Timestamp.valueOf(request.getEndDate()));
            }
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TokenRecord record = new TokenRecord();
                    record.setTokenId(rs.getString("token_id"));
                    record.setUserId(rs.getString("user_id"));
                    record.setToken(rs.getString("token"));
                    record.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                    record.setExpiresAt(rs.getTimestamp("expires_at").toLocalDateTime());
                    tokens.add(record);
                }
            }
        }
        
        return tokens;
    }
    
    /**
     * Build SQL query based on conversion request
     */
    private String buildConversionQuery(ConversionRequest request) {
        StringBuilder query = new StringBuilder(
            "SELECT token_id, user_id, token, created_at, expires_at FROM user_tokens WHERE 1=1"
        );
        
        if (request.getUserIds() != null && !request.getUserIds().isEmpty()) {
            query.append(" AND user_id IN (");
            query.append(String.join(",", Collections.nCopies(request.getUserIds().size(), "?")));
            query.append(")");
        }
        
        if (request.getStartDate() != null) {
            query.append(" AND created_at >= ?");
        }
        
        if (request.getEndDate() != null) {
            query.append(" AND created_at <= ?");
        }
        
        if (request.isOnlyActiveTokens()) {
            query.append(" AND expires_at > NOW()");
        }
        
        query.append(" ORDER BY created_at DESC");
        
        if (request.getMaxTokens() > 0) {
            query.append(" LIMIT ").append(request.getMaxTokens());
        }
        
        return query.toString();
    }
    
    /**
     * Update database with conversion results
     */
    private void updateConversionRecords(List<BatchResult> batchResults) throws SQLException {
        String updateQuery = "UPDATE user_tokens SET keycloak_token = ?, keycloak_refresh_token = ?, " +
            "migration_status = ?, migrated_at = ? WHERE token_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
            
            for (BatchResult batch : batchResults) {
                for (ConversionResult result : batch.getConversionResults()) {
                    if (result.isSuccess()) {
                        stmt.setString(1, result.getKeycloakToken());
                        stmt.setString(2, result.getKeycloakRefreshToken());
                        stmt.setString(3, "MIGRATED");
                        stmt.setTimestamp(4, Timestamp.from(Instant.now()));
                        stmt.setString(5, result.getOriginalToken());
                        stmt.addBatch();
                    }
                }
            }
            
            stmt.executeBatch();
            conn.commit();
        }
    }
    
    /**
     * Clean up old tokens after successful conversion
     */
    private void cleanupOldTokens(List<TokenRecord> tokens) {
        log.info("Cleaning up {} old tokens", tokens.size());
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                 "UPDATE user_tokens SET token = NULL WHERE token_id = ?")) {
            
            for (TokenRecord token : tokens) {
                stmt.setString(1, token.getTokenId());
                stmt.addBatch();
            }
            
            stmt.executeBatch();
            conn.commit();
            
            log.info("Successfully cleaned up old tokens");
        } catch (SQLException e) {
            log.error("Failed to cleanup old tokens", e);
        }
    }
    
    /**
     * Aggregate batch results into final report
     */
    private ConversionReport aggregateResults(List<BatchResult> batchResults) {
        ConversionReport report = new ConversionReport();
        report.setStartTime(conversionStartTime);
        report.setEndTime(Instant.now());
        report.setTotalProcessed(totalProcessed.get());
        report.setSuccessCount(successfulConversions.get());
        report.setFailureCount(failedConversions.get());
        
        if (totalProcessed.get() > 0) {
            report.setSuccessRate(
                (double) successfulConversions.get() / totalProcessed.get() * 100
            );
            report.setAverageProcessingTimeMs(
                (double) totalProcessingTime.get() / totalProcessed.get()
            );
        }
        
        // Collect failed tokens for reporting
        List<String> failedTokens = batchResults.stream()
            .flatMap(batch -> batch.getConversionResults().stream())
            .filter(result -> !result.isSuccess())
            .map(ConversionResult::getUserId)
            .toList();
        
        report.setFailedUserIds(failedTokens);
        report.setBatchResults(batchResults);
        
        return report;
    }
    
    /**
     * Pause the conversion process
     */
    public void pauseConversion() {
        if (currentStatus == ConversionStatus.IN_PROGRESS) {
            log.info("Pausing conversion process");
            currentStatus = ConversionStatus.PAUSED;
        }
    }
    
    /**
     * Resume the conversion process
     */
    public void resumeConversion() {
        if (currentStatus == ConversionStatus.PAUSED) {
            log.info("Resuming conversion process");
            currentStatus = ConversionStatus.IN_PROGRESS;
        }
    }
    
    /**
     * Cancel the conversion process
     */
    public void cancelConversion() {
        log.warn("Cancelling conversion process");
        currentStatus = ConversionStatus.CANCELLED;
    }
    
    /**
     * Get current conversion status
     */
    public ConversionStatusReport getCurrentStatus() {
        ConversionStatusReport statusReport = new ConversionStatusReport();
        statusReport.setStatus(currentStatus);
        statusReport.setTotalProcessed(totalProcessed.get());
        statusReport.setSuccessCount(successfulConversions.get());
        statusReport.setFailureCount(failedConversions.get());
        
        if (currentStatus == ConversionStatus.IN_PROGRESS && totalProcessed.get() > 0) {
            long elapsedMs = System.currentTimeMillis() - conversionStartTime.toEpochMilli();
            double tokensPerSecond = (double) totalProcessed.get() / (elapsedMs / 1000.0);
            statusReport.setTokensPerSecond(tokensPerSecond);
            
            if (totalProcessed.get() > 0) {
                statusReport.setSuccessRate(
                    (double) successfulConversions.get() / totalProcessed.get() * 100
                );
            }
        }
        
        return statusReport;
    }
    
    /**
     * Scheduled health check for conversion process
     */
    @Scheduled(fixedDelay = 30000) // Every 30 seconds
    public void healthCheck() {
        if (currentStatus == ConversionStatus.IN_PROGRESS) {
            log.info("Conversion health check - Processed: {}, Success: {}, Failed: {}",
                totalProcessed.get(), successfulConversions.get(), failedConversions.get());
        }
    }
    
    private void resetCounters() {
        totalProcessed.set(0);
        successfulConversions.set(0);
        failedConversions.set(0);
        totalProcessingTime.set(0);
    }
    
    // Data classes
    
    @Data
    public static class ConversionRequest {
        private List<String> userIds;
        private LocalDateTime startDate;
        private LocalDateTime endDate;
        private boolean onlyActiveTokens = true;
        private boolean cleanupOldTokens = false;
        private int maxTokens = 0; // 0 means no limit
        private int timeoutMinutes = 60;
    }
    
    @Data
    public static class TokenRecord {
        private String tokenId;
        private String userId;
        private String token;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }
    
    @Data
    public static class ConversionResult {
        private boolean success;
        private String userId;
        private String originalToken;
        private String keycloakToken;
        private String keycloakRefreshToken;
        private String keycloakUserId;
        private String errorMessage;
        private Instant timestamp;
        
        public static ConversionResult success(String userId, String originalToken,
                                              String keycloakToken, String keycloakRefreshToken,
                                              String keycloakUserId) {
            ConversionResult result = new ConversionResult();
            result.success = true;
            result.userId = userId;
            result.originalToken = originalToken;
            result.keycloakToken = keycloakToken;
            result.keycloakRefreshToken = keycloakRefreshToken;
            result.keycloakUserId = keycloakUserId;
            result.timestamp = Instant.now();
            return result;
        }
        
        public static ConversionResult failed(String userId, String originalToken, String errorMessage) {
            ConversionResult result = new ConversionResult();
            result.success = false;
            result.userId = userId;
            result.originalToken = originalToken;
            result.errorMessage = errorMessage;
            result.timestamp = Instant.now();
            return result;
        }
    }
    
    @Data
    public static class BatchResult {
        private int batchNumber;
        private int totalTokens;
        private int successCount;
        private int failureCount;
        private long processingTimeMs;
        private List<ConversionResult> conversionResults;
    }
    
    @Data
    public static class ConversionReport {
        private Instant startTime;
        private Instant endTime;
        private int totalProcessed;
        private int successCount;
        private int failureCount;
        private double successRate;
        private double averageProcessingTimeMs;
        private List<String> failedUserIds;
        private List<BatchResult> batchResults;
        private String errorMessage;
        
        public static ConversionReport success(int total, int success, int failure) {
            ConversionReport report = new ConversionReport();
            report.totalProcessed = total;
            report.successCount = success;
            report.failureCount = failure;
            report.successRate = total > 0 ? (double) success / total * 100 : 0;
            return report;
        }
        
        public static ConversionReport failed(String errorMessage) {
            ConversionReport report = new ConversionReport();
            report.errorMessage = errorMessage;
            return report;
        }
    }
    
    @Data
    public static class ConversionStatusReport {
        private ConversionStatus status;
        private int totalProcessed;
        private int successCount;
        private int failureCount;
        private double successRate;
        private double tokensPerSecond;
    }
}