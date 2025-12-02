package com.waqiti.transaction.repository;

import com.waqiti.common.sharding.ShardingConfiguration.ShardManager;
import com.waqiti.transaction.domain.Transaction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Sharded repository for high-volume transaction operations
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class ShardedTransactionRepository {
    
    private final ShardManager shardManager;
    
    private static final String INSERT_TRANSACTION = """
        INSERT INTO transactions (
            id, transaction_id, source_wallet_id, target_wallet_id,
            amount, currency, type, status, reference_number,
            description, metadata, created_at, updated_at, version
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
        """;
    
    private static final String UPDATE_TRANSACTION_STATUS = """
        UPDATE transactions 
        SET status = ?, updated_at = ?, version = version + 1
        WHERE id = ? AND version = ?
        """;
    
    private static final String SELECT_BY_ID = """
        SELECT * FROM transactions WHERE id = ?
        """;
    
    private static final String SELECT_BY_WALLET = """
        SELECT * FROM transactions 
        WHERE (source_wallet_id = ? OR target_wallet_id = ?)
        AND created_at >= ? AND created_at < ?
        ORDER BY created_at DESC
        LIMIT ?
        """;
    
    /**
     * Save transaction to appropriate shard
     */
    @Transactional
    public Transaction save(Transaction transaction) {
        String shardKey = transaction.getId().toString();
        
        return shardManager.executeOnShard(shardKey, jdbcTemplate -> {
            jdbcTemplate.update(INSERT_TRANSACTION,
                transaction.getId(),
                transaction.getTransactionId(),
                transaction.getSourceWalletId(),
                transaction.getTargetWalletId(),
                transaction.getAmount(),
                transaction.getCurrency(),
                transaction.getType().name(),
                transaction.getStatus().name(),
                transaction.getReferenceNumber(),
                transaction.getDescription(),
                transaction.getMetadata(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt(),
                transaction.getVersion()
            );
            
            log.debug("Saved transaction {} to shard", transaction.getId());
            return transaction;
        });
    }
    
    /**
     * Batch save transactions across shards
     */
    @Transactional
    public void batchSave(List<Transaction> transactions) {
        // Group transactions by shard
        Map<Integer, List<Transaction>> transactionsByShard = transactions.stream()
            .collect(Collectors.groupingBy(tx -> {
                String shardKey = tx.getId().toString();
                return Math.abs(shardKey.hashCode()) % shardManager.getShardCount();
            }));
        
        // Execute batch insert on each shard
        transactionsByShard.forEach((shardId, shardTransactions) -> {
            JdbcTemplate jdbcTemplate = new JdbcTemplate(shardManager.getShardById(shardId));
            
            jdbcTemplate.batchUpdate(INSERT_TRANSACTION, shardTransactions, 1000,
                (ps, transaction) -> {
                    ps.setObject(1, transaction.getId());
                    ps.setString(2, transaction.getTransactionId());
                    ps.setObject(3, transaction.getSourceWalletId());
                    ps.setObject(4, transaction.getTargetWalletId());
                    ps.setBigDecimal(5, transaction.getAmount());
                    ps.setString(6, transaction.getCurrency());
                    ps.setString(7, transaction.getType().name());
                    ps.setString(8, transaction.getStatus().name());
                    ps.setString(9, transaction.getReferenceNumber());
                    ps.setString(10, transaction.getDescription());
                    ps.setString(11, transaction.getMetadata());
                    ps.setObject(12, transaction.getCreatedAt());
                    ps.setObject(13, transaction.getUpdatedAt());
                    ps.setLong(14, transaction.getVersion());
                });
            
            log.info("Batch saved {} transactions to shard {}", shardTransactions.size(), shardId);
        });
    }
    
    /**
     * Find transaction by ID
     */
    public Optional<Transaction> findById(UUID id) {
        String shardKey = id.toString();
        
        return shardManager.executeOnShard(shardKey, jdbcTemplate -> {
            List<Transaction> results = jdbcTemplate.query(SELECT_BY_ID,
                new Object[]{id}, new TransactionRowMapper());
            
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        });
    }
    
    /**
     * Update transaction status with optimistic locking
     */
    @Transactional
    public boolean updateStatus(UUID id, String newStatus, Long expectedVersion) {
        String shardKey = id.toString();
        
        return shardManager.executeOnShard(shardKey, jdbcTemplate -> {
            int updated = jdbcTemplate.update(UPDATE_TRANSACTION_STATUS,
                newStatus, LocalDateTime.now(), id, expectedVersion);
            
            return updated > 0;
        });
    }
    
    /**
     * Find transactions by wallet ID across all shards
     */
    public List<Transaction> findByWalletId(UUID walletId, LocalDateTime startDate, 
                                          LocalDateTime endDate, int limit) {
        // Query all shards in parallel
        Map<Integer, CompletableFuture<List<Transaction>>> futures = new ConcurrentHashMap<>();
        
        for (int i = 0; i < shardManager.getShardCount(); i++) {
            final int shardId = i;
            CompletableFuture<List<Transaction>> future = CompletableFuture.supplyAsync(() -> {
                JdbcTemplate jdbcTemplate = new JdbcTemplate(shardManager.getShardById(shardId));
                return jdbcTemplate.query(SELECT_BY_WALLET,
                    new Object[]{walletId, walletId, startDate, endDate, limit},
                    new TransactionRowMapper());
            });
            futures.put(shardId, future);
        }
        
        // Collect results from all shards
        List<Transaction> allTransactions = new ArrayList<>();
        futures.values().forEach(future -> {
            try {
                allTransactions.addAll(future.get());
            } catch (Exception e) {
                log.error("Failed to fetch transactions from shard", e);
            }
        });
        
        // Sort and limit results
        return allTransactions.stream()
            .sorted(Comparator.comparing(Transaction::getCreatedAt).reversed())
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Get transaction statistics across all shards
     */
    public TransactionStatistics getStatistics(LocalDateTime startDate, LocalDateTime endDate) {
        String sql = """
            SELECT 
                COUNT(*) as total_count,
                SUM(amount) as total_amount,
                AVG(amount) as avg_amount,
                MIN(amount) as min_amount,
                MAX(amount) as max_amount,
                COUNT(DISTINCT source_wallet_id) as unique_senders,
                COUNT(DISTINCT target_wallet_id) as unique_receivers
            FROM transactions
            WHERE created_at >= ? AND created_at < ?
            AND status = 'COMPLETED'
            """;
        
        Map<Integer, TransactionStatistics> shardStats = shardManager.executeOnAllShardsWithResult(
            jdbcTemplate -> jdbcTemplate.queryForObject(sql,
                new Object[]{startDate, endDate},
                (rs, rowNum) -> new TransactionStatistics(
                    rs.getLong("total_count"),
                    rs.getBigDecimal("total_amount"),
                    rs.getBigDecimal("avg_amount"),
                    rs.getBigDecimal("min_amount"),
                    rs.getBigDecimal("max_amount"),
                    rs.getLong("unique_senders"),
                    rs.getLong("unique_receivers")
                )
            )
        );
        
        // Aggregate statistics from all shards
        return aggregateStatistics(shardStats.values());
    }
    
    /**
     * Archive old transactions
     */
    @Transactional
    public int archiveOldTransactions(LocalDateTime cutoffDate) {
        String archiveSql = """
            INSERT INTO transactions_archive
            SELECT * FROM transactions
            WHERE created_at < ?
            """;
        
        String deleteSql = """
            DELETE FROM transactions
            WHERE created_at < ?
            """;
        
        int totalArchived = 0;
        
        // Execute on all shards
        Map<Integer, Integer> results = shardManager.executeOnAllShardsWithResult(
            jdbcTemplate -> {
                // Archive
                jdbcTemplate.update(archiveSql, cutoffDate);
                
                // Delete
                return jdbcTemplate.update(deleteSql, cutoffDate);
            }
        );
        
        for (Map.Entry<Integer, Integer> entry : results.entrySet()) {
            log.info("Archived {} transactions from shard {}", entry.getValue(), entry.getKey());
            totalArchived += entry.getValue();
        }
        
        return totalArchived;
    }
    
    /**
     * Row mapper for transactions
     */
    private static class TransactionRowMapper implements RowMapper<Transaction> {
        @Override
        public Transaction mapRow(ResultSet rs, int rowNum) throws SQLException {
            Transaction transaction = new Transaction();
            transaction.setId(UUID.fromString(rs.getString("id")));
            transaction.setTransactionId(rs.getString("transaction_id"));
            transaction.setSourceWalletId(UUID.fromString(rs.getString("source_wallet_id")));
            transaction.setTargetWalletId(UUID.fromString(rs.getString("target_wallet_id")));
            transaction.setAmount(rs.getBigDecimal("amount"));
            transaction.setCurrency(rs.getString("currency"));
            transaction.setType(Transaction.TransactionType.valueOf(rs.getString("type")));
            transaction.setStatus(Transaction.TransactionStatus.valueOf(rs.getString("status")));
            transaction.setReferenceNumber(rs.getString("reference_number"));
            transaction.setDescription(rs.getString("description"));
            transaction.setMetadata(rs.getString("metadata"));
            transaction.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
            transaction.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());
            transaction.setVersion(rs.getLong("version"));
            return transaction;
        }
    }
    
    /**
     * Transaction statistics
     */
    public static class TransactionStatistics {
        private final long totalCount;
        private final BigDecimal totalAmount;
        private final BigDecimal avgAmount;
        private final BigDecimal minAmount;
        private final BigDecimal maxAmount;
        private final long uniqueSenders;
        private final long uniqueReceivers;
        
        public TransactionStatistics(long totalCount, BigDecimal totalAmount, 
                                   BigDecimal avgAmount, BigDecimal minAmount,
                                   BigDecimal maxAmount, long uniqueSenders,
                                   long uniqueReceivers) {
            this.totalCount = totalCount;
            this.totalAmount = totalAmount;
            this.avgAmount = avgAmount;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
            this.uniqueSenders = uniqueSenders;
            this.uniqueReceivers = uniqueReceivers;
        }
        
        // Getters
        public long getTotalCount() { return totalCount; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public BigDecimal getAvgAmount() { return avgAmount; }
        public BigDecimal getMinAmount() { return minAmount; }
        public BigDecimal getMaxAmount() { return maxAmount; }
        public long getUniqueSenders() { return uniqueSenders; }
        public long getUniqueReceivers() { return uniqueReceivers; }
    }
    
    /**
     * Aggregate statistics from multiple shards
     */
    private TransactionStatistics aggregateStatistics(Collection<TransactionStatistics> shardStats) {
        long totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal minAmount = null;
        BigDecimal maxAmount = null;
        Set<Long> uniqueSenders = new HashSet<>();
        Set<Long> uniqueReceivers = new HashSet<>();
        
        for (TransactionStatistics stats : shardStats) {
            totalCount += stats.getTotalCount();
            totalAmount = totalAmount.add(stats.getTotalAmount() != null ? 
                stats.getTotalAmount() : BigDecimal.ZERO);
            
            if (stats.getMinAmount() != null) {
                minAmount = minAmount == null ? stats.getMinAmount() : 
                    minAmount.min(stats.getMinAmount());
            }
            
            if (stats.getMaxAmount() != null) {
                maxAmount = maxAmount == null ? stats.getMaxAmount() : 
                    maxAmount.max(stats.getMaxAmount());
            }
            
            // Note: unique counts are approximations when aggregated
            uniqueSenders.add(stats.getUniqueSenders());
            uniqueReceivers.add(stats.getUniqueReceivers());
        }
        
        BigDecimal avgAmount = totalCount > 0 ? 
            totalAmount.divide(BigDecimal.valueOf(totalCount), 2, RoundingMode.HALF_UP) : 
            BigDecimal.ZERO;
        
        return new TransactionStatistics(
            totalCount,
            totalAmount,
            avgAmount,
            minAmount,
            maxAmount,
            uniqueSenders.stream().mapToLong(Long::longValue).sum(),
            uniqueReceivers.stream().mapToLong(Long::longValue).sum()
        );
    }
}