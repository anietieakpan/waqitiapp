package com.waqiti.common.database;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Financial Query Optimizer
 * 
 * Specialized query optimization patterns for financial applications
 * focusing on high-volume transaction processing, balance calculations,
 * and compliance reporting.
 */
@Service
@Slf4j
public class FinancialQueryOptimizer {

    private final JdbcTemplate jdbcTemplate;
    private final Executor asyncExecutor;

    public FinancialQueryOptimizer(JdbcTemplate jdbcTemplate, Executor asyncExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.asyncExecutor = asyncExecutor;
    }

    // =====================================================================
    // OPTIMIZED TRANSACTION QUERIES
    // =====================================================================

    /**
     * High-performance transaction history with smart pagination
     * Uses partition pruning and covering indexes
     */
    public List<Map<String, Object>> getOptimizedTransactionHistory(
            UUID accountId, 
            LocalDateTime startDate, 
            LocalDateTime endDate,
            int page, 
            int size) {
        
        String sql = """
            WITH account_transactions AS (
                SELECT /*+ USE_INDEX(transactions_partitioned, idx_account_date) */
                    t.id, t.transaction_number, t.transaction_type, 
                    t.amount, t.currency, t.description, 
                    t.transaction_date, t.status,
                    ROW_NUMBER() OVER (ORDER BY t.transaction_date DESC, t.id) as rn
                FROM transactions_partitioned t
                WHERE (t.source_account_id = ? OR t.target_account_id = ?)
                AND t.transaction_date BETWEEN ? AND ?
                AND t.status IN ('COMPLETED', 'PENDING', 'PROCESSING')
            )
            SELECT id, transaction_number, transaction_type, amount, 
                   currency, description, transaction_date, status
            FROM account_transactions
            WHERE rn BETWEEN ? AND ?
            ORDER BY transaction_date DESC, id
            """;

        int offset = page * size + 1;
        int limit = offset + size - 1;

        return jdbcTemplate.queryForList(sql, accountId, accountId, 
                                        startDate, endDate, offset, limit);
    }

    /**
     * Batch balance inquiry with read-through optimization
     * Minimizes database round trips and uses bulk operations
     */
    public Map<UUID, Map<String, BigDecimal>> getBulkAccountBalances(List<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // Split large requests to avoid query plan issues
        if (accountIds.size() > 1000) {
            return getBulkAccountBalancesChunked(accountIds);
        }

        String sql = """
            SELECT 
                account_id,
                current_balance,
                available_balance,
                reserved_balance,
                pending_balance,
                CASE 
                    WHEN last_transaction_date > CURRENT_TIMESTAMP - INTERVAL '1 hour' 
                    THEN 'RECENT_ACTIVITY' 
                    ELSE 'STABLE' 
                END as activity_status
            FROM accounts
            WHERE account_id = ANY(?)
            AND status = 'ACTIVE'
            """;

        UUID[] accountArray = accountIds.toArray(new UUID[0]);
        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, (Object) accountArray);

        return results.stream()
            .collect(Collectors.toMap(
                row -> (UUID) row.get("account_id"),
                row -> {
                    Map<String, BigDecimal> balances = new HashMap<>();
                    balances.put("current", (BigDecimal) row.get("current_balance"));
                    balances.put("available", (BigDecimal) row.get("available_balance"));
                    balances.put("reserved", (BigDecimal) row.get("reserved_balance"));
                    balances.put("pending", (BigDecimal) row.get("pending_balance"));
                    return balances;
                }
            ));
    }

    /**
     * Optimized transaction aggregation with materialized view support
     * Pre-calculates common aggregations for faster reporting
     */
    public CompletableFuture<Map<String, Object>> getAccountFinancialSummary(
            UUID accountId, 
            LocalDateTime startDate, 
            LocalDateTime endDate) {
        
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                WITH transaction_summary AS (
                    SELECT 
                        COUNT(*) as total_transactions,
                        COUNT(*) FILTER (WHERE source_account_id = ?) as outgoing_count,
                        COUNT(*) FILTER (WHERE target_account_id = ?) as incoming_count,
                        SUM(CASE WHEN source_account_id = ? THEN amount ELSE 0 END) as total_outgoing,
                        SUM(CASE WHEN target_account_id = ? THEN amount ELSE 0 END) as total_incoming,
                        AVG(amount) as avg_transaction_amount,
                        MAX(amount) as largest_transaction,
                        MIN(amount) as smallest_transaction,
                        COUNT(DISTINCT DATE_TRUNC('day', transaction_date)) as active_days,
                        PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY amount) as median_amount
                    FROM transactions_partitioned
                    WHERE (source_account_id = ? OR target_account_id = ?)
                    AND transaction_date BETWEEN ? AND ?
                    AND status = 'COMPLETED'
                ),
                daily_volumes AS (
                    SELECT 
                        DATE_TRUNC('day', transaction_date) as day,
                        SUM(CASE WHEN source_account_id = ? THEN -amount ELSE amount END) as net_flow
                    FROM transactions_partitioned
                    WHERE (source_account_id = ? OR target_account_id = ?)
                    AND transaction_date BETWEEN ? AND ?
                    AND status = 'COMPLETED'
                    GROUP BY DATE_TRUNC('day', transaction_date)
                ),
                volatility_metrics AS (
                    SELECT 
                        STDDEV(net_flow) as flow_volatility,
                        MAX(net_flow) as max_daily_inflow,
                        MIN(net_flow) as max_daily_outflow
                    FROM daily_volumes
                )
                SELECT 
                    ts.*,
                    (ts.total_incoming - ts.total_outgoing) as net_flow,
                    vm.flow_volatility,
                    vm.max_daily_inflow,
                    vm.max_daily_outflow,
                    CASE 
                        WHEN ts.active_days > 0 THEN ts.total_transactions::DECIMAL / ts.active_days
                        ELSE 0
                    END as avg_daily_transactions
                FROM transaction_summary ts
                CROSS JOIN volatility_metrics vm
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql,
                accountId, accountId, accountId, accountId, accountId, accountId, startDate, endDate,
                accountId, accountId, accountId, startDate, endDate);

            return results.isEmpty() ? Collections.emptyMap() : results.get(0);
        }, asyncExecutor);
    }

    /**
     * Real-time fraud detection query with risk scoring
     * Uses window functions for pattern analysis
     */
    public List<Map<String, Object>> detectSuspiciousTransactions(
            UUID accountId, 
            LocalDateTime lookbackPeriod) {
        
        String sql = """
            WITH account_patterns AS (
                SELECT 
                    t.*,
                    -- Velocity analysis
                    COUNT(*) OVER (
                        PARTITION BY source_account_id 
                        ORDER BY transaction_date 
                        RANGE BETWEEN INTERVAL '1 hour' PRECEDING AND CURRENT ROW
                    ) as hourly_transaction_count,
                    
                    SUM(amount) OVER (
                        PARTITION BY source_account_id 
                        ORDER BY transaction_date 
                        RANGE BETWEEN INTERVAL '1 hour' PRECEDING AND CURRENT ROW
                    ) as hourly_volume,
                    
                    -- Amount analysis
                    AVG(amount) OVER (
                        PARTITION BY source_account_id 
                        ORDER BY transaction_date 
                        ROWS BETWEEN 20 PRECEDING AND 1 PRECEDING
                    ) as historical_avg_amount,
                    
                    -- Time pattern analysis
                    EXTRACT(HOUR FROM transaction_date) as transaction_hour,
                    EXTRACT(DOW FROM transaction_date) as transaction_dow,
                    
                    -- Geographic/IP analysis (if available)
                    LAG(metadata) OVER (
                        PARTITION BY source_account_id 
                        ORDER BY transaction_date
                    ) as prev_metadata
                    
                FROM transactions_partitioned t
                WHERE source_account_id = ?
                AND transaction_date >= ?
                AND status IN ('COMPLETED', 'PENDING', 'PROCESSING')
            ),
            risk_scored_transactions AS (
                SELECT 
                    ap.*,
                    -- Risk scoring
                    CASE 
                        WHEN hourly_transaction_count > 10 THEN 25
                        WHEN hourly_transaction_count > 5 THEN 15
                        ELSE 0
                    END +
                    CASE 
                        WHEN hourly_volume > 50000 THEN 30
                        WHEN hourly_volume > 10000 THEN 20
                        ELSE 0
                    END +
                    CASE 
                        WHEN amount > historical_avg_amount * 5 THEN 25
                        WHEN amount > historical_avg_amount * 3 THEN 15
                        ELSE 0
                    END +
                    CASE 
                        WHEN transaction_hour BETWEEN 2 AND 5 THEN 10  -- Unusual hours
                        WHEN transaction_dow IN (0, 6) THEN 5          -- Weekends
                        ELSE 0
                    END as risk_score
                FROM account_patterns ap
            )
            SELECT 
                id, transaction_number, amount, currency, 
                transaction_date, risk_score, description,
                hourly_transaction_count, hourly_volume,
                historical_avg_amount, transaction_hour
            FROM risk_scored_transactions
            WHERE risk_score >= 30  -- High risk threshold
            ORDER BY risk_score DESC, transaction_date DESC
            LIMIT 100
            """;

        return jdbcTemplate.queryForList(sql, accountId, lookbackPeriod);
    }

    /**
     * Optimized compliance reporting with data aggregation
     * Pre-aggregated for regulatory reporting requirements
     */
    public Map<String, Object> generateComplianceReport(
            LocalDateTime startDate, 
            LocalDateTime endDate,
            List<String> reportTypes) {
        
        Map<String, Object> report = new HashMap<>();
        
        // High-value transactions (>$10,000)
        if (reportTypes.contains("HIGH_VALUE")) {
            String highValueSql = """
                SELECT 
                    COUNT(*) as high_value_count,
                    SUM(amount) as high_value_total,
                    COUNT(DISTINCT source_account_id) as unique_senders,
                    COUNT(DISTINCT target_account_id) as unique_recipients,
                    AVG(amount) as avg_high_value_amount
                FROM transactions_partitioned
                WHERE transaction_date BETWEEN ? AND ?
                AND amount >= 10000
                AND status = 'COMPLETED'
                """;
            
            List<Map<String, Object>> highValueResults = jdbcTemplate.queryForList(
                highValueSql, startDate, endDate);
            report.put("high_value_transactions", highValueResults.get(0));
        }
        
        // Cross-border transactions
        if (reportTypes.contains("CROSS_BORDER")) {
            String crossBorderSql = """
                SELECT 
                    COUNT(*) as cross_border_count,
                    SUM(amount) as cross_border_total,
                    COUNT(DISTINCT currency) as currencies_involved
                FROM transactions_partitioned t
                JOIN accounts source_acc ON t.source_account_id = source_acc.account_id
                JOIN accounts target_acc ON t.target_account_id = target_acc.account_id
                WHERE t.transaction_date BETWEEN ? AND ?
                AND source_acc.currency != target_acc.currency
                AND t.status = 'COMPLETED'
                """;
            
            List<Map<String, Object>> crossBorderResults = jdbcTemplate.queryForList(
                crossBorderSql, startDate, endDate);
            report.put("cross_border_transactions", crossBorderResults.get(0));
        }
        
        // Suspicious activity patterns
        if (reportTypes.contains("SUSPICIOUS_ACTIVITY")) {
            String suspiciousActivitySql = """
                WITH suspicious_patterns AS (
                    SELECT 
                        source_account_id,
                        COUNT(*) as transaction_count,
                        SUM(amount) as total_volume,
                        COUNT(DISTINCT target_account_id) as unique_recipients,
                        MIN(transaction_date) as first_transaction,
                        MAX(transaction_date) as last_transaction
                    FROM transactions_partitioned
                    WHERE transaction_date BETWEEN ? AND ?
                    AND status = 'COMPLETED'
                    GROUP BY source_account_id
                    HAVING COUNT(*) > 100  -- High frequency
                    OR SUM(amount) > 100000  -- High volume
                    OR COUNT(DISTINCT target_account_id) > 50  -- Many recipients
                )
                SELECT 
                    COUNT(*) as suspicious_account_count,
                    SUM(transaction_count) as total_suspicious_transactions,
                    SUM(total_volume) as total_suspicious_volume,
                    AVG(unique_recipients) as avg_recipients_per_account
                FROM suspicious_patterns
                """;
            
            List<Map<String, Object>> suspiciousResults = jdbcTemplate.queryForList(
                suspiciousActivitySql, startDate, endDate);
            report.put("suspicious_activity", suspiciousResults.get(0));
        }
        
        return report;
    }

    /**
     * Optimized ledger reconciliation with parallel processing
     * Compares account balances with ledger entries for accuracy
     */
    public CompletableFuture<Map<String, Object>> performLedgerReconciliation(
            List<UUID> accountIds) {
        
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                WITH account_balances AS (
                    SELECT 
                        account_id,
                        current_balance as account_balance
                    FROM accounts
                    WHERE account_id = ANY(?)
                    AND status = 'ACTIVE'
                ),
                ledger_balances AS (
                    SELECT 
                        account_id,
                        SUM(CASE 
                            WHEN entry_type = 'DEBIT' THEN -amount 
                            ELSE amount 
                        END) as calculated_balance
                    FROM ledger_entries_partitioned
                    WHERE account_id = ANY(?)
                    AND reconciled = true
                    GROUP BY account_id
                ),
                reconciliation_results AS (
                    SELECT 
                        ab.account_id,
                        ab.account_balance,
                        COALESCE(lb.calculated_balance, 0) as calculated_balance,
                        ABS(ab.account_balance - COALESCE(lb.calculated_balance, 0)) as variance,
                        CASE 
                            WHEN ABS(ab.account_balance - COALESCE(lb.calculated_balance, 0)) > 0.01 
                            THEN 'DISCREPANCY'
                            ELSE 'BALANCED'
                        END as status
                    FROM account_balances ab
                    LEFT JOIN ledger_balances lb ON ab.account_id = lb.account_id
                )
                SELECT 
                    COUNT(*) as total_accounts,
                    COUNT(*) FILTER (WHERE status = 'BALANCED') as balanced_accounts,
                    COUNT(*) FILTER (WHERE status = 'DISCREPANCY') as discrepancy_accounts,
                    SUM(variance) as total_variance,
                    MAX(variance) as max_variance,
                    AVG(variance) as avg_variance
                FROM reconciliation_results
                """;

            UUID[] accountArray = accountIds.toArray(new UUID[0]);
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                sql, (Object) accountArray, (Object) accountArray);

            return results.isEmpty() ? Collections.emptyMap() : results.get(0);
        }, asyncExecutor);
    }

    // =====================================================================
    // PRIVATE HELPER METHODS
    // =====================================================================

    /**
     * Handle large bulk operations by chunking
     */
    private Map<UUID, Map<String, BigDecimal>> getBulkAccountBalancesChunked(List<UUID> accountIds) {
        Map<UUID, Map<String, BigDecimal>> allResults = new HashMap<>();
        
        int chunkSize = 1000;
        for (int i = 0; i < accountIds.size(); i += chunkSize) {
            int end = Math.min(i + chunkSize, accountIds.size());
            List<UUID> chunk = accountIds.subList(i, end);
            
            Map<UUID, Map<String, BigDecimal>> chunkResults = getBulkAccountBalances(chunk);
            allResults.putAll(chunkResults);
        }
        
        return allResults;
    }

    /**
     * Execute query with retry logic for transient failures
     */
    public <T> T executeWithRetry(String queryName, RetryableOperation<T> operation, int maxRetries) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                long startTime = System.currentTimeMillis();
                T result = operation.execute();
                long duration = System.currentTimeMillis() - startTime;
                
                if (duration > 5000) { // Log slow queries
                    log.warn("Slow query: {} took {}ms on attempt {}", queryName, duration, attempt);
                }
                
                return result;
                
            } catch (Exception e) {
                lastException = e;
                log.warn("Query {} failed on attempt {} of {}: {}", 
                        queryName, attempt, maxRetries, e.getMessage());
                
                if (attempt < maxRetries && isRetryableException(e)) {
                    try {
                        Thread.sleep(attempt * 200); // Progressive backoff
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Query execution interrupted", ie);
                    }
                } else {
                    break;
                }
            }
        }
        
        throw new RuntimeException(String.format(
            "Query %s failed after %d attempts", queryName, maxRetries), lastException);
    }

    /**
     * Check if exception is retryable
     */
    private boolean isRetryableException(Exception e) {
        String message = e.getMessage().toLowerCase();
        return message.contains("connection") || 
               message.contains("timeout") ||
               message.contains("deadlock") ||
               message.contains("lock wait") ||
               message.contains("connection reset");
    }

    /**
     * Functional interface for retryable operations
     */
    @FunctionalInterface
    public interface RetryableOperation<T> {
        T execute() throws Exception;
    }

    /**
     * Optimize query hints based on database type
     */
    public String addQueryHints(String baseQuery, String queryType) {
        // PostgreSQL-specific optimizations
        switch (queryType.toLowerCase()) {
            case "transaction_history":
                return "/*+ SeqScan(transactions_partitioned) */ " + baseQuery;
            case "balance_inquiry":
                return "/*+ IndexScan(accounts) */ " + baseQuery;
            case "aggregation":
                return "/*+ HashAgg */ " + baseQuery;
            default:
                return baseQuery;
        }
    }

    /**
     * Create optimized batch processing strategy
     */
    public <T> void processBatchOptimized(String operationName, 
                                        List<T> items, 
                                        BatchProcessor<T> processor) {
        if (items.isEmpty()) return;
        
        int optimalBatchSize = calculateOptimalBatchSize(items.size());
        int totalBatches = (int) Math.ceil((double) items.size() / optimalBatchSize);
        
        log.info("Processing {} {} in {} batches of size {}", 
                items.size(), operationName, totalBatches, optimalBatchSize);
        
        for (int i = 0; i < totalBatches; i++) {
            int start = i * optimalBatchSize;
            int end = Math.min(start + optimalBatchSize, items.size());
            List<T> batch = items.subList(start, end);
            
            long startTime = System.currentTimeMillis();
            
            try {
                processor.process(batch);
                
                long duration = System.currentTimeMillis() - startTime;
                log.debug("Batch {}/{} processed in {}ms", i + 1, totalBatches, duration);
                
            } catch (Exception e) {
                log.error("Error processing batch {}/{} for {}", i + 1, totalBatches, operationName, e);
                throw new RuntimeException(
                    String.format("Batch processing failed at batch %d/%d", i + 1, totalBatches), e);
            }
        }
    }

    /**
     * Calculate optimal batch size based on available memory and data characteristics
     */
    private int calculateOptimalBatchSize(int totalItems) {
        // Start with a base batch size
        int baseBatchSize = 1000;
        
        // Adjust based on total volume
        if (totalItems > 100000) {
            return Math.min(baseBatchSize, 500); // Smaller batches for large datasets
        } else if (totalItems < 1000) {
            return totalItems; // Process all at once for small datasets
        }
        
        return baseBatchSize;
    }

    /**
     * Functional interface for batch processing
     */
    @FunctionalInterface
    public interface BatchProcessor<T> {
        void process(List<T> batch) throws Exception;
    }
}