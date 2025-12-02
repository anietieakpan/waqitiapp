package com.waqiti.common.security.database;

import com.waqiti.common.security.SecurityContext;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Database Access Control Service
 * Implements Row-Level Security (RLS), query validation, and access auditing
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseAccessControlService {

    private final JdbcTemplate jdbcTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${database.security.rls.enabled:true}")
    private boolean rlsEnabled;

    @Value("${database.security.query.max-execution-time:30000}") // 30 seconds
    private long maxQueryExecutionTime;

    @Value("${database.security.query.max-rows:10000}")
    private int maxQueryRows;

    @Value("${database.security.audit.query-logging:true}")
    private boolean queryLoggingEnabled;

    @Value("${database.security.sensitive-data.masking:true}")
    private boolean dataMaskingEnabled;

    // Cache for user permissions
    private final Map<String, UserDatabasePermissions> permissionCache = new ConcurrentHashMap<>();

    // Patterns for dangerous SQL operations
    private static final Pattern[] DANGEROUS_PATTERNS = {
        Pattern.compile("(?i)\\b(DROP|TRUNCATE|DELETE\\s+FROM)\\b"),
        Pattern.compile("(?i)\\b(CREATE|ALTER|GRANT|REVOKE)\\b"),
        Pattern.compile("(?i)\\b(EXEC|EXECUTE|CALL)\\b"),
        Pattern.compile("(?i)\\b(xp_cmdshell|sp_configure)\\b"),
        Pattern.compile("(?i)/\\*.*\\*/"), // Comments that might hide injection
        Pattern.compile("(?i)--.*$", Pattern.MULTILINE), // SQL comments
        Pattern.compile("(?i);\\s*(DROP|DELETE|UPDATE|INSERT)"), // Stacked queries
    };

    // Sensitive data patterns for masking
    private static final Map<String, Pattern> SENSITIVE_PATTERNS = Map.of(
        "SSN", Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
        "CREDIT_CARD", Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"),
        "EMAIL", Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
        "PHONE", Pattern.compile("\\b\\d{3}[\\s.-]?\\d{3}[\\s.-]?\\d{4}\\b"),
        "ACCOUNT", Pattern.compile("\\b\\d{10,16}\\b")
    );

    /**
     * Execute query with security controls
     */
    @Transactional(readOnly = true)
    public <T> List<T> executeSecureQuery(String sql, Class<T> resultClass, Object... params) {
        QueryExecutionContext context = createQueryContext(sql);

        try {
            // Validate query security
            validateQuerySecurity(sql, context);

            // Apply Row-Level Security
            String securedQuery = applyRowLevelSecurity(sql, context);

            // Set query timeout
            jdbcTemplate.setQueryTimeout((int) (maxQueryExecutionTime / 1000));

            // Execute query with monitoring
            long startTime = System.currentTimeMillis();
            List<T> results = jdbcTemplate.queryForList(securedQuery, resultClass, params);
            long executionTime = System.currentTimeMillis() - startTime;

            // Check result set size
            if (results.size() > maxQueryRows) {
                log.warn("Query returned too many rows: {} (max: {})", results.size(), maxQueryRows);
                results = results.subList(0, maxQueryRows);
            }

            // Apply data masking if needed
            if (dataMaskingEnabled && needsDataMasking(context)) {
                results = applyDataMasking(results, context);
            }

            // Audit query execution
            auditQueryExecution(context, sql, executionTime, results.size());

            return results;

        } catch (Exception e) {
            auditQueryFailure(context, sql, e.getMessage());
            throw new DatabaseSecurityException("Query execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute update with security controls
     */
    @Transactional
    public int executeSecureUpdate(String sql, Object... params) {
        QueryExecutionContext context = createQueryContext(sql);

        try {
            // Validate update security
            validateUpdateSecurity(sql, context);

            // Check user permissions for update
            if (!hasUpdatePermission(context)) {
                throw new DatabaseSecurityException("User lacks permission for update operation");
            }

            // Apply Row-Level Security
            String securedQuery = applyRowLevelSecurity(sql, context);

            // Execute update with monitoring
            long startTime = System.currentTimeMillis();
            int rowsAffected = jdbcTemplate.update(securedQuery, params);
            long executionTime = System.currentTimeMillis() - startTime;

            // Audit update execution
            auditUpdateExecution(context, sql, executionTime, rowsAffected);

            return rowsAffected;

        } catch (Exception e) {
            auditUpdateFailure(context, sql, e.getMessage());
            throw new DatabaseSecurityException("Update execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Validate query security
     */
    private void validateQuerySecurity(String sql, QueryExecutionContext context) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new DatabaseSecurityException("Empty query not allowed");
        }

        // Check for dangerous patterns
        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(sql).find()) {
                log.error("Dangerous SQL pattern detected: {} by user: {}", 
                    pattern.pattern(), context.getUsername());
                throw new DatabaseSecurityException("Query contains forbidden SQL patterns");
            }
        }

        // Validate query complexity
        if (getQueryComplexity(sql) > 100) {
            throw new DatabaseSecurityException("Query too complex");
        }

        // Check query permissions
        if (!hasQueryPermission(sql, context)) {
            throw new DatabaseSecurityException("Insufficient permissions for query");
        }
    }

    /**
     * Validate update security
     */
    private void validateUpdateSecurity(String sql, QueryExecutionContext context) {
        validateQuerySecurity(sql, context);

        // Additional checks for updates
        if (!sql.trim().toUpperCase().startsWith("UPDATE") && 
            !sql.trim().toUpperCase().startsWith("INSERT") &&
            !sql.trim().toUpperCase().startsWith("DELETE")) {
            throw new DatabaseSecurityException("Only UPDATE, INSERT, DELETE operations allowed");
        }

        // Prevent updates without WHERE clause (except INSERT)
        if (sql.toUpperCase().contains("UPDATE") || sql.toUpperCase().contains("DELETE")) {
            if (!sql.toUpperCase().contains("WHERE")) {
                throw new DatabaseSecurityException("UPDATE/DELETE without WHERE clause not allowed");
            }
        }
    }

    /**
     * Apply Row-Level Security (RLS) to query
     */
    private String applyRowLevelSecurity(String sql, QueryExecutionContext context) {
        if (!rlsEnabled) {
            return sql;
        }

        UserDatabasePermissions permissions = getUserPermissions(context.getUsername());
        
        // Apply tenant isolation for multi-tenant systems
        if (permissions.getTenantId() != null) {
            sql = applyTenantIsolation(sql, permissions.getTenantId());
        }

        // Apply user-specific row filters
        if (!permissions.getRowFilters().isEmpty()) {
            sql = applyRowFilters(sql, permissions.getRowFilters());
        }

        // Apply column-level security
        if (!permissions.getRestrictedColumns().isEmpty()) {
            sql = applyColumnRestrictions(sql, permissions.getRestrictedColumns());
        }

        log.debug("Applied RLS to query for user: {}", context.getUsername());
        return sql;
    }

    /**
     * Apply tenant isolation to queries
     */
    private String applyTenantIsolation(String sql, String tenantId) {
        // Simple implementation - in production, use proper SQL parser
        String upperSql = sql.toUpperCase();
        
        if (upperSql.contains("WHERE")) {
            // Add tenant filter to existing WHERE clause
            int whereIndex = upperSql.indexOf("WHERE");
            return sql.substring(0, whereIndex + 5) + 
                   " tenant_id = '" + tenantId + "' AND " + 
                   sql.substring(whereIndex + 5);
        } else if (upperSql.contains("FROM")) {
            // Add WHERE clause with tenant filter
            int fromEnd = findFromClauseEnd(sql);
            return sql.substring(0, fromEnd) + 
                   " WHERE tenant_id = '" + tenantId + "'" + 
                   sql.substring(fromEnd);
        }
        
        return sql;
    }

    /**
     * Apply row-level filters
     */
    private String applyRowFilters(String sql, Map<String, String> filters) {
        for (Map.Entry<String, String> filter : filters.entrySet()) {
            sql = addFilterCondition(sql, filter.getKey(), filter.getValue());
        }
        return sql;
    }

    /**
     * Apply column-level restrictions
     */
    private String applyColumnRestrictions(String sql, Set<String> restrictedColumns) {
        // Replace restricted columns with masked values in SELECT
        for (String column : restrictedColumns) {
            sql = sql.replaceAll("\\b" + column + "\\b", 
                "'***RESTRICTED***' AS " + column);
        }
        return sql;
    }

    /**
     * Apply data masking to results
     */
    private <T> List<T> applyDataMasking(List<T> results, QueryExecutionContext context) {
        UserDatabasePermissions permissions = getUserPermissions(context.getUsername());
        
        if (permissions.getDataMaskingLevel() == DataMaskingLevel.NONE) {
            return results;
        }

        // Apply masking based on level
        for (T result : results) {
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> row = (Map<String, Object>) result;
                maskSensitiveData(row, permissions.getDataMaskingLevel());
            }
        }

        return results;
    }

    /**
     * Mask sensitive data in a row
     */
    private void maskSensitiveData(Map<String, Object> row, DataMaskingLevel level) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getValue() instanceof String) {
                String value = (String) entry.getValue();
                String masked = maskSensitiveString(value, level);
                if (!masked.equals(value)) {
                    entry.setValue(masked);
                }
            }
        }
    }

    /**
     * Mask sensitive patterns in string
     */
    private String maskSensitiveString(String value, DataMaskingLevel level) {
        if (level == DataMaskingLevel.NONE) {
            return value;
        }

        String masked = value;
        
        for (Map.Entry<String, Pattern> entry : SENSITIVE_PATTERNS.entrySet()) {
            Pattern pattern = entry.getValue();
            
            if (pattern.matcher(masked).find()) {
                switch (level) {
                    case PARTIAL:
                        // Mask all but last 4 characters
                        masked = pattern.matcher(masked).replaceAll(m -> {
                            String match = m.group();
                            if (match.length() > 4) {
                                return "*".repeat(match.length() - 4) + 
                                       match.substring(match.length() - 4);
                            }
                            return "*".repeat(match.length());
                        });
                        break;
                    
                    case FULL:
                        // Mask entire value
                        masked = pattern.matcher(masked).replaceAll(m -> 
                            "*".repeat(m.group().length()));
                        break;
                    
                    case HASH:
                        // Replace with hash
                        masked = pattern.matcher(masked).replaceAll("[REDACTED-" + 
                            entry.getKey() + "]");
                        break;
                }
            }
        }
        
        return masked;
    }

    /**
     * Get user database permissions
     */
    private UserDatabasePermissions getUserPermissions(String username) {
        return permissionCache.computeIfAbsent(username, u -> {
            // Load permissions from database or configuration
            UserDatabasePermissions permissions = loadUserPermissions(u);
            
            // Cache for 5 minutes
            String cacheKey = "db:permissions:" + u;
            redisTemplate.opsForValue().set(cacheKey, permissions, Duration.ofMinutes(5));
            
            return permissions;
        });
    }

    /**
     * Load user permissions from database
     */
    private UserDatabasePermissions loadUserPermissions(String username) {
        try {
            // Query user permissions from database
            String query = "SELECT * FROM user_database_permissions WHERE username = ?";
            
            return jdbcTemplate.queryForObject(query, (rs, rowNum) -> 
                UserDatabasePermissions.builder()
                    .username(username)
                    .allowedTables(parseSet(rs.getString("allowed_tables")))
                    .restrictedColumns(parseSet(rs.getString("restricted_columns")))
                    .maxQueryRows(rs.getInt("max_query_rows"))
                    .canUpdate(rs.getBoolean("can_update"))
                    .canDelete(rs.getBoolean("can_delete"))
                    .tenantId(rs.getString("tenant_id"))
                    .dataMaskingLevel(DataMaskingLevel.valueOf(rs.getString("masking_level")))
                    .rowFilters(parseFilters(rs.getString("row_filters")))
                    .build(),
                username);
                
        } catch (Exception e) {
            // Return default restrictive permissions
            log.debug("No specific permissions found for user: {}, using defaults", username);
            return UserDatabasePermissions.builder()
                .username(username)
                .allowedTables(Set.of())
                .restrictedColumns(Set.of("password", "ssn", "credit_card"))
                .maxQueryRows(1000)
                .canUpdate(false)
                .canDelete(false)
                .dataMaskingLevel(DataMaskingLevel.PARTIAL)
                .rowFilters(Map.of())
                .build();
        }
    }

    /**
     * Check if user has query permission
     */
    private boolean hasQueryPermission(String sql, QueryExecutionContext context) {
        UserDatabasePermissions permissions = getUserPermissions(context.getUsername());
        
        // Extract tables from query
        Set<String> queryTables = extractTablesFromQuery(sql);
        
        // Check if all tables are allowed
        if (!permissions.getAllowedTables().isEmpty()) {
            for (String table : queryTables) {
                if (!permissions.getAllowedTables().contains(table)) {
                    log.warn("User {} lacks permission for table: {}", 
                        context.getUsername(), table);
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Check if user has update permission
     */
    private boolean hasUpdatePermission(QueryExecutionContext context) {
        UserDatabasePermissions permissions = getUserPermissions(context.getUsername());
        return permissions.isCanUpdate();
    }

    /**
     * Create query execution context
     */
    private QueryExecutionContext createQueryContext(String sql) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        return QueryExecutionContext.builder()
            .username(auth != null ? auth.getName() : "anonymous")
            .timestamp(Instant.now())
            .queryHash(sql.hashCode())
            .clientIp(getClientIp())
            .sessionId(getSessionId())
            .build();
    }

    /**
     * Audit query execution
     */
    private void auditQueryExecution(QueryExecutionContext context, String sql, 
                                    long executionTime, int rowCount) {
        if (!queryLoggingEnabled) {
            return;
        }

        try {
            QueryAuditLog auditLog = QueryAuditLog.builder()
                .username(context.getUsername())
                .queryType("SELECT")
                .query(sanitizeQueryForLogging(sql))
                .executionTime(executionTime)
                .rowCount(rowCount)
                .timestamp(context.getTimestamp())
                .clientIp(context.getClientIp())
                .sessionId(context.getSessionId())
                .success(true)
                .build();

            // Store audit log
            String auditKey = "db:audit:query:" + context.getTimestamp().toEpochMilli();
            redisTemplate.opsForValue().set(auditKey, auditLog, Duration.ofDays(90));

            // Log for real-time monitoring
            if (executionTime > maxQueryExecutionTime / 2) {
                log.warn("Slow query detected - User: {}, Time: {}ms, Rows: {}", 
                    context.getUsername(), executionTime, rowCount);
            }

        } catch (Exception e) {
            log.error("Failed to audit query execution", e);
        }
    }

    /**
     * Audit query failure
     */
    private void auditQueryFailure(QueryExecutionContext context, String sql, String error) {
        try {
            QueryAuditLog auditLog = QueryAuditLog.builder()
                .username(context.getUsername())
                .queryType("SELECT")
                .query(sanitizeQueryForLogging(sql))
                .timestamp(context.getTimestamp())
                .clientIp(context.getClientIp())
                .sessionId(context.getSessionId())
                .success(false)
                .errorMessage(error)
                .build();

            // Store audit log with security flag
            String auditKey = "db:audit:failed:" + context.getTimestamp().toEpochMilli();
            redisTemplate.opsForValue().set(auditKey, auditLog, Duration.ofDays(90));

            log.error("Query execution failed - User: {}, Error: {}", 
                context.getUsername(), error);

        } catch (Exception e) {
            log.error("Failed to audit query failure", e);
        }
    }

    /**
     * Audit update execution
     */
    private void auditUpdateExecution(QueryExecutionContext context, String sql, 
                                     long executionTime, int rowsAffected) {
        if (!queryLoggingEnabled) {
            return;
        }

        try {
            QueryAuditLog auditLog = QueryAuditLog.builder()
                .username(context.getUsername())
                .queryType(extractQueryType(sql))
                .query(sanitizeQueryForLogging(sql))
                .executionTime(executionTime)
                .rowCount(rowsAffected)
                .timestamp(context.getTimestamp())
                .clientIp(context.getClientIp())
                .sessionId(context.getSessionId())
                .success(true)
                .build();

            // Store audit log with higher retention for updates
            String auditKey = "db:audit:update:" + context.getTimestamp().toEpochMilli();
            redisTemplate.opsForValue().set(auditKey, auditLog, Duration.ofDays(365));

            log.info("Database update - User: {}, Type: {}, Rows: {}", 
                context.getUsername(), auditLog.getQueryType(), rowsAffected);

        } catch (Exception e) {
            log.error("Failed to audit update execution", e);
        }
    }

    /**
     * Audit update failure
     */
    private void auditUpdateFailure(QueryExecutionContext context, String sql, String error) {
        try {
            QueryAuditLog auditLog = QueryAuditLog.builder()
                .username(context.getUsername())
                .queryType(extractQueryType(sql))
                .query(sanitizeQueryForLogging(sql))
                .timestamp(context.getTimestamp())
                .clientIp(context.getClientIp())
                .sessionId(context.getSessionId())
                .success(false)
                .errorMessage(error)
                .build();

            // Store audit log with security alert
            String auditKey = "db:audit:update:failed:" + context.getTimestamp().toEpochMilli();
            redisTemplate.opsForValue().set(auditKey, auditLog, Duration.ofDays(365));

            // Alert on suspicious update failures
            log.error("UPDATE FAILURE - User: {}, Error: {}, Query: {}", 
                context.getUsername(), error, sanitizeQueryForLogging(sql));

        } catch (Exception e) {
            log.error("Failed to audit update failure", e);
        }
    }

    // Helper methods

    private int getQueryComplexity(String sql) {
        // Simple complexity calculation based on query structure
        int complexity = 0;
        complexity += countOccurrences(sql, "JOIN") * 10;
        complexity += countOccurrences(sql, "SUBQUERY") * 20;
        complexity += countOccurrences(sql, "UNION") * 15;
        complexity += countOccurrences(sql, "GROUP BY") * 5;
        complexity += countOccurrences(sql, "HAVING") * 5;
        return complexity;
    }

    private int countOccurrences(String str, String pattern) {
        return (str.length() - str.replace(pattern, "").length()) / pattern.length();
    }

    private boolean needsDataMasking(QueryExecutionContext context) {
        UserDatabasePermissions permissions = getUserPermissions(context.getUsername());
        return permissions.getDataMaskingLevel() != DataMaskingLevel.NONE;
    }

    private Set<String> extractTablesFromQuery(String sql) {
        // Simple implementation - in production use proper SQL parser
        Set<String> tables = new HashSet<>();
        String upperSql = sql.toUpperCase();
        
        // Extract table names after FROM and JOIN
        String[] keywords = {"FROM", "JOIN", "INTO", "UPDATE"};
        for (String keyword : keywords) {
            int index = upperSql.indexOf(keyword);
            if (index != -1) {
                // Extract next word as table name
                String afterKeyword = sql.substring(index + keyword.length()).trim();
                String tableName = afterKeyword.split("\\s+")[0];
                tables.add(tableName.toLowerCase());
            }
        }
        
        return tables;
    }

    private String extractQueryType(String sql) {
        String trimmed = sql.trim().toUpperCase();
        if (trimmed.startsWith("SELECT")) return "SELECT";
        if (trimmed.startsWith("INSERT")) return "INSERT";
        if (trimmed.startsWith("UPDATE")) return "UPDATE";
        if (trimmed.startsWith("DELETE")) return "DELETE";
        return "OTHER";
    }

    private String sanitizeQueryForLogging(String sql) {
        // Remove sensitive values from query for logging
        return sql.replaceAll("'[^']*'", "'***'")
                 .replaceAll("\\b\\d{4,}\\b", "****");
    }

    private int findFromClauseEnd(String sql) {
        String upperSql = sql.toUpperCase();
        int fromIndex = upperSql.indexOf("FROM");
        if (fromIndex == -1) return sql.length();
        
        // Find end of FROM clause
        String[] endKeywords = {"WHERE", "GROUP", "ORDER", "LIMIT", ";"};
        int endIndex = sql.length();
        
        for (String keyword : endKeywords) {
            int index = upperSql.indexOf(keyword, fromIndex);
            if (index != -1 && index < endIndex) {
                endIndex = index;
            }
        }
        
        return endIndex;
    }

    private String addFilterCondition(String sql, String column, String value) {
        // Add filter condition to WHERE clause
        String condition = column + " = '" + value + "'";
        
        if (sql.toUpperCase().contains("WHERE")) {
            return sql.replaceFirst("WHERE", "WHERE " + condition + " AND ");
        } else {
            return sql + " WHERE " + condition;
        }
    }

    private Set<String> parseSet(String value) {
        if (value == null || value.isEmpty()) return Set.of();
        return Set.of(value.split(","));
    }

    private Map<String, String> parseFilters(String value) {
        if (value == null || value.isEmpty()) return Map.of();
        
        Map<String, String> filters = new HashMap<>();
        for (String filter : value.split(";")) {
            String[] parts = filter.split("=");
            if (parts.length == 2) {
                filters.put(parts[0], parts[1]);
            }
        }
        return filters;
    }

    private String getClientIp() {
        // Get from request context or security context
        return "unknown";
    }

    private String getSessionId() {
        // Get from security context
        return UUID.randomUUID().toString();
    }

    // Data classes

    @Data
    @Builder
    public static class QueryExecutionContext {
        private String username;
        private Instant timestamp;
        private int queryHash;
        private String clientIp;
        private String sessionId;
    }

    @Data
    @Builder
    public static class UserDatabasePermissions {
        private String username;
        private Set<String> allowedTables;
        private Set<String> restrictedColumns;
        private int maxQueryRows;
        private boolean canUpdate;
        private boolean canDelete;
        private String tenantId;
        private DataMaskingLevel dataMaskingLevel;
        private Map<String, String> rowFilters;
    }

    @Data
    @Builder
    public static class QueryAuditLog {
        private String username;
        private String queryType;
        private String query;
        private long executionTime;
        private int rowCount;
        private Instant timestamp;
        private String clientIp;
        private String sessionId;
        private boolean success;
        private String errorMessage;
    }

    public enum DataMaskingLevel {
        NONE, PARTIAL, FULL, HASH
    }

    public static class DatabaseSecurityException extends RuntimeException {
        public DatabaseSecurityException(String message) {
            super(message);
        }

        public DatabaseSecurityException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}