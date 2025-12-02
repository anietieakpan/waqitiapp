package com.waqiti.common.performance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Database index optimizer for performance optimization
 * Creates and manages indexes for optimal query performance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseIndexOptimizer {
    
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    
    private static final Map<String, List<IndexDefinition>> REQUIRED_INDEXES = new HashMap<>();
    
    static {
        // Transaction table indexes
        REQUIRED_INDEXES.put("transactions", Arrays.asList(
            IndexDefinition.create("idx_transactions_user_id", "user_id"),
            IndexDefinition.create("idx_transactions_recipient_id", "recipient_id"),
            IndexDefinition.create("idx_transactions_status", "status"),
            IndexDefinition.create("idx_transactions_created_at", "created_at DESC"),
            IndexDefinition.createComposite("idx_transactions_user_status", "user_id", "status"),
            IndexDefinition.createComposite("idx_transactions_user_date", "user_id", "created_at DESC"),
            IndexDefinition.createUnique("idx_transactions_reference", "transaction_reference")
        ));
        
        // User table indexes
        REQUIRED_INDEXES.put("users", Arrays.asList(
            IndexDefinition.createUnique("idx_users_email", "email"),
            IndexDefinition.createUnique("idx_users_phone", "phone_number"),
            IndexDefinition.create("idx_users_kyc_status", "kyc_status"),
            IndexDefinition.create("idx_users_created_at", "created_at"),
            IndexDefinition.createComposite("idx_users_status_kyc", "status", "kyc_status")
        ));
        
        // KYC verification table indexes
        REQUIRED_INDEXES.put("kyc_verifications", Arrays.asList(
            IndexDefinition.create("idx_kyc_user_id", "user_id"),
            IndexDefinition.create("idx_kyc_status", "verification_status"),
            IndexDefinition.create("idx_kyc_expiry", "expiry_date"),
            IndexDefinition.createComposite("idx_kyc_user_status", "user_id", "verification_status")
        ));
        
        // Fraud assessments table indexes
        REQUIRED_INDEXES.put("fraud_assessments", Arrays.asList(
            IndexDefinition.create("idx_fraud_transaction_id", "transaction_id"),
            IndexDefinition.create("idx_fraud_risk_score", "risk_score"),
            IndexDefinition.create("idx_fraud_risk_level", "risk_level"),
            IndexDefinition.create("idx_fraud_created_at", "created_at DESC"),
            IndexDefinition.createComposite("idx_fraud_score_level", "risk_score", "risk_level")
        ));
        
        // Event store table indexes
        REQUIRED_INDEXES.put("event_store", Arrays.asList(
            IndexDefinition.create("idx_events_aggregate_id", "aggregate_id"),
            IndexDefinition.create("idx_events_event_type", "event_type"),
            IndexDefinition.create("idx_events_timestamp", "event_timestamp DESC"),
            IndexDefinition.createComposite("idx_events_aggregate_version", "aggregate_id", "version"),
            IndexDefinition.createComposite("idx_events_type_timestamp", "event_type", "event_timestamp DESC")
        ));
        
        // Audit trail table indexes
        REQUIRED_INDEXES.put("audit_trail", Arrays.asList(
            IndexDefinition.create("idx_audit_user_id", "user_id"),
            IndexDefinition.create("idx_audit_action", "action"),
            IndexDefinition.create("idx_audit_timestamp", "timestamp DESC"),
            IndexDefinition.create("idx_audit_severity", "severity"),
            IndexDefinition.createComposite("idx_audit_user_action", "user_id", "action", "timestamp DESC")
        ));
        
        // Payment methods table indexes
        REQUIRED_INDEXES.put("payment_methods", Arrays.asList(
            IndexDefinition.create("idx_payment_user_id", "user_id"),
            IndexDefinition.create("idx_payment_type", "payment_type"),
            IndexDefinition.create("idx_payment_status", "status"),
            IndexDefinition.createComposite("idx_payment_user_type", "user_id", "payment_type")
        ));
        
        // Notifications table indexes
        REQUIRED_INDEXES.put("notifications", Arrays.asList(
            IndexDefinition.create("idx_notif_user_id", "user_id"),
            IndexDefinition.create("idx_notif_type", "notification_type"),
            IndexDefinition.create("idx_notif_status", "status"),
            IndexDefinition.create("idx_notif_created_at", "created_at DESC"),
            IndexDefinition.createComposite("idx_notif_user_status", "user_id", "status", "created_at DESC")
        ));
        
        // Currency exchange rates table indexes
        REQUIRED_INDEXES.put("exchange_rates", Arrays.asList(
            IndexDefinition.create("idx_exchange_from_currency", "from_currency"),
            IndexDefinition.create("idx_exchange_to_currency", "to_currency"),
            IndexDefinition.create("idx_exchange_timestamp", "rate_timestamp DESC"),
            IndexDefinition.createComposite("idx_exchange_currencies", "from_currency", "to_currency", "rate_timestamp DESC")
        ));
        
        // Session table indexes
        REQUIRED_INDEXES.put("user_sessions", Arrays.asList(
            IndexDefinition.create("idx_session_user_id", "user_id"),
            IndexDefinition.create("idx_session_token", "session_token"),
            IndexDefinition.create("idx_session_expiry", "expires_at"),
            IndexDefinition.createComposite("idx_session_user_active", "user_id", "is_active", "expires_at")
        ));
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void optimizeIndexes() {
        log.info("Starting database index optimization...");
        
        try {
            IndexOptimizationResult result = analyzeAndOptimize();
            
            log.info("Index optimization completed: {} indexes created, {} already existed, {} failed",
                result.getCreatedCount(), result.getExistingCount(), result.getFailedCount());
                
            if (result.hasFailures()) {
                log.warn("Some indexes failed to create: {}", result.getFailedIndexes());
            }
            
        } catch (Exception e) {
            log.error("Failed to optimize database indexes", e);
        }
    }
    
    /**
     * Analyze and optimize database indexes
     */
    public IndexOptimizationResult analyzeAndOptimize() {
        IndexOptimizationResult result = new IndexOptimizationResult();
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            for (Map.Entry<String, List<IndexDefinition>> entry : REQUIRED_INDEXES.entrySet()) {
                String tableName = entry.getKey();
                List<IndexDefinition> indexes = entry.getValue();
                
                if (!tableExists(metaData, tableName)) {
                    log.debug("Table {} does not exist, skipping index creation", tableName);
                    continue;
                }
                
                Set<String> existingIndexes = getExistingIndexes(metaData, tableName);
                
                for (IndexDefinition index : indexes) {
                    if (existingIndexes.contains(index.getName())) {
                        result.addExisting(index.getName());
                        log.debug("Index {} already exists on table {}", index.getName(), tableName);
                    } else {
                        if (createIndex(tableName, index)) {
                            result.addCreated(index.getName());
                            log.info("Created index {} on table {}", index.getName(), tableName);
                        } else {
                            result.addFailed(index.getName());
                            log.error("Failed to create index {} on table {}", index.getName(), tableName);
                        }
                    }
                }
            }
            
            // Analyze query performance
            analyzeQueryPerformance();
            
        } catch (SQLException e) {
            log.error("Error during index optimization", e);
        }
        
        return result;
    }
    
    /**
     * Check if table exists
     */
    private boolean tableExists(DatabaseMetaData metaData, String tableName) throws SQLException {
        try (ResultSet rs = metaData.getTables(null, null, tableName, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
    
    /**
     * Get existing indexes for a table
     */
    private Set<String> getExistingIndexes(DatabaseMetaData metaData, String tableName) throws SQLException {
        Set<String> indexes = new HashSet<>();
        
        try (ResultSet rs = metaData.getIndexInfo(null, null, tableName, false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName != null && !indexName.equalsIgnoreCase("PRIMARY")) {
                    indexes.add(indexName);
                }
            }
        }
        
        return indexes;
    }
    
    /**
     * Create an index
     */
    private boolean createIndex(String tableName, IndexDefinition index) {
        try {
            String sql = buildCreateIndexSQL(tableName, index);
            jdbcTemplate.execute(sql);
            return true;
        } catch (Exception e) {
            log.debug("Error creating index {} on table {}: {}", 
                index.getName(), tableName, e.getMessage());
            return false;
        }
    }
    
    /**
     * Build CREATE INDEX SQL statement
     */
    private String buildCreateIndexSQL(String tableName, IndexDefinition index) {
        StringBuilder sql = new StringBuilder("CREATE ");
        
        if (index.isUnique()) {
            sql.append("UNIQUE ");
        }
        
        sql.append("INDEX IF NOT EXISTS ")
           .append(index.getName())
           .append(" ON ")
           .append(tableName)
           .append(" (")
           .append(String.join(", ", index.getColumns()))
           .append(")");
           
        return sql.toString();
    }
    
    /**
     * Analyze query performance
     */
    private void analyzeQueryPerformance() {
        log.info("Analyzing query performance...");
        
        // Analyze slow queries (this would connect to database slow query log in production)
        List<SlowQuery> slowQueries = identifySlowQueries();
        
        for (SlowQuery query : slowQueries) {
            log.warn("Slow query detected: {} (execution time: {}ms)", 
                query.getQuery(), query.getExecutionTime());
                
            // Suggest optimizations
            List<String> suggestions = suggestOptimizations(query);
            for (String suggestion : suggestions) {
                log.info("  Suggestion: {}", suggestion);
            }
        }
    }
    
    /**
     * Identify slow queries (mock implementation)
     */
    private List<SlowQuery> identifySlowQueries() {
        // In production, this would query the database slow query log
        // or performance schema tables
        return new ArrayList<>();
    }
    
    /**
     * Suggest query optimizations
     */
    private List<String> suggestOptimizations(SlowQuery query) {
        List<String> suggestions = new ArrayList<>();
        
        String queryLower = query.getQuery().toLowerCase();
        
        if (queryLower.contains("select *")) {
            suggestions.add("Avoid SELECT * - specify only needed columns");
        }
        
        if (!queryLower.contains("limit") && queryLower.contains("select")) {
            suggestions.add("Consider adding LIMIT clause for large result sets");
        }
        
        if (queryLower.contains("like '%")) {
            suggestions.add("Leading wildcard in LIKE prevents index usage");
        }
        
        if (queryLower.contains("or")) {
            suggestions.add("OR conditions may prevent index usage - consider UNION");
        }
        
        if (queryLower.contains("in") && queryLower.contains("select")) {
            suggestions.add("Subquery with IN may be slow - consider JOIN");
        }
        
        return suggestions;
    }
    
    /**
     * Get index statistics
     */
    public IndexStatistics getStatistics() {
        IndexStatistics stats = new IndexStatistics();
        
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            
            for (String tableName : REQUIRED_INDEXES.keySet()) {
                if (!tableExists(metaData, tableName)) {
                    continue;
                }
                
                Set<String> existingIndexes = getExistingIndexes(metaData, tableName);
                stats.addTableIndexCount(tableName, existingIndexes.size());
                
                // Get table statistics
                String countQuery = "SELECT COUNT(*) FROM " + tableName;
                Integer rowCount = jdbcTemplate.queryForObject(countQuery, Integer.class);
                stats.addTableRowCount(tableName, rowCount != null ? rowCount : 0);
            }
            
        } catch (SQLException e) {
            log.error("Error getting index statistics", e);
        }
        
        return stats;
    }
}