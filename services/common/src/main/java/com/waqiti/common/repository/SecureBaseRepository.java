package com.waqiti.common.repository;

import com.waqiti.common.security.validation.SecureInputValidator;
import com.waqiti.common.security.audit.SecurityAuditLogger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Secure base repository that prevents SQL injection attacks.
 * All database queries are parameterized and validated.
 * 
 * Features:
 * - Mandatory parameterized queries
 * - SQL injection prevention
 * - Input validation integration
 * - Query audit logging
 * - Safe dynamic query building
 * - Transaction management
 * - Performance monitoring
 */
@Slf4j
public class SecureBaseRepository<T, ID extends Serializable> extends SimpleJpaRepository<T, ID> {
    
    private final SecureInputValidator inputValidator;
    private final SecurityAuditLogger auditLogger;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;
    private final Class<T> entityClass;
    
    // Cache for validated queries to improve performance
    private static final Map<String, Boolean> validatedQueries = new ConcurrentHashMap<>();
    
    // Whitelist of allowed column names and table names
    private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of(
        "id", "createdAt", "updatedAt", "name", "status", "amount", "currency",
        "timestamp", "type", "userId", "accountId", "transactionId"
    );
    
    // Patterns for detecting unsafe queries
    private static final Pattern UNSAFE_QUERY_PATTERN = Pattern.compile(
        "(?i).*(union|drop|delete|update|insert|alter|create|grant|revoke|exec|execute|truncate).*"
    );
    
    public SecureBaseRepository(
            JpaEntityInformation<T, ID> entityInformation, 
            EntityManager entityManager,
            SecureInputValidator inputValidator,
            SecurityAuditLogger auditLogger,
            JdbcTemplate jdbcTemplate) {
        super(entityInformation, entityManager);
        this.entityManager = entityManager;
        this.entityClass = entityInformation.getJavaType();
        this.inputValidator = inputValidator;
        this.auditLogger = auditLogger;
        this.jdbcTemplate = jdbcTemplate;
    }
    
    /**
     * Execute a parameterized query safely
     */
    @Transactional(readOnly = true)
    public List<T> executeSecureQuery(String queryName, Map<String, Object> parameters) {
        validateQuerySafety(queryName, parameters);
        
        try {
            TypedQuery<T> query = entityManager.createNamedQuery(queryName, entityClass);
            
            // Set parameters safely
            if (parameters != null) {
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    String paramName = entry.getKey();
                    Object paramValue = entry.getValue();
                    
                    // Validate parameter name
                    validateParameterName(paramName);
                    
                    // Validate parameter value
                    validateParameterValue(paramValue);
                    
                    query.setParameter(paramName, paramValue);
                }
            }
            
            List<T> results = query.getResultList();
            
            auditLogger.logDatabaseQuery(queryName, parameters != null ? parameters.size() : 0, results.size());
            
            return results;
            
        } catch (Exception e) {
            auditLogger.logDatabaseError(queryName, e.getMessage());
            log.error("Secure query execution failed: {}", queryName, e);
            throw new SecureRepositoryException("Query execution failed", e);
        }
    }
    
    /**
     * Execute a single result parameterized query safely
     */
    @Transactional(readOnly = true)
    public Optional<T> executeSecureQuerySingle(String queryName, Map<String, Object> parameters) {
        List<T> results = executeSecureQuery(queryName, parameters);
        
        if (results.isEmpty()) {
            return Optional.empty();
        } else if (results.size() == 1) {
            return Optional.of(results.get(0));
        } else {
            throw new SecureRepositoryException("Query returned multiple results when single expected");
        }
    }
    
    /**
     * Execute a count query safely
     */
    @Transactional(readOnly = true)
    public long executeSecureCountQuery(String queryName, Map<String, Object> parameters) {
        validateQuerySafety(queryName, parameters);
        
        try {
            TypedQuery<Long> query = entityManager.createNamedQuery(queryName, Long.class);
            
            if (parameters != null) {
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    validateParameterName(entry.getKey());
                    validateParameterValue(entry.getValue());
                    query.setParameter(entry.getKey(), entry.getValue());
                }
            }
            
            Long result = query.getSingleResult();
            auditLogger.logDatabaseQuery(queryName, parameters != null ? parameters.size() : 0, 1);
            
            return result != null ? result : 0L;
            
        } catch (Exception e) {
            auditLogger.logDatabaseError(queryName, e.getMessage());
            log.error("Secure count query execution failed: {}", queryName, e);
            throw new SecureRepositoryException("Count query execution failed", e);
        }
    }
    
    /**
     * Execute a criteria-based query safely
     */
    @Transactional(readOnly = true)
    public List<T> executeSecureCriteriaQuery(SecureCriteriaQueryBuilder<T> queryBuilder) {
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<T> cq = cb.createQuery(entityClass);
            Root<T> root = cq.from(entityClass);
            
            // Build query using secure criteria builder
            Predicate predicate = queryBuilder.buildPredicate(cb, root);
            if (predicate != null) {
                cq.where(predicate);
            }
            
            // Apply sorting if specified
            if (queryBuilder.getSortBy() != null) {
                validateSortColumn(queryBuilder.getSortBy());
                if (queryBuilder.isAscending()) {
                    cq.orderBy(cb.asc(root.get(queryBuilder.getSortBy())));
                } else {
                    cq.orderBy(cb.desc(root.get(queryBuilder.getSortBy())));
                }
            }
            
            TypedQuery<T> query = entityManager.createQuery(cq);
            
            // Apply pagination if specified
            if (queryBuilder.getOffset() != null) {
                query.setFirstResult(queryBuilder.getOffset());
            }
            if (queryBuilder.getLimit() != null) {
                query.setMaxResults(queryBuilder.getLimit());
            }
            
            List<T> results = query.getResultList();
            
            auditLogger.logDatabaseQuery("CRITERIA_QUERY", 0, results.size());
            
            return results;
            
        } catch (Exception e) {
            auditLogger.logDatabaseError("CRITERIA_QUERY", e.getMessage());
            log.error("Secure criteria query execution failed", e);
            throw new SecureRepositoryException("Criteria query execution failed", e);
        }
    }
    
    /**
     * Execute an update operation safely
     */
    @Transactional
    public int executeSecureUpdate(String queryName, Map<String, Object> parameters) {
        validateQuerySafety(queryName, parameters);
        validateUpdateOperation(queryName);
        
        try {
            Query query = entityManager.createNamedQuery(queryName);
            
            if (parameters != null) {
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    validateParameterName(entry.getKey());
                    validateParameterValue(entry.getValue());
                    query.setParameter(entry.getKey(), entry.getValue());
                }
            }
            
            int rowsAffected = query.executeUpdate();
            
            auditLogger.logDatabaseUpdate(queryName, parameters != null ? parameters.size() : 0, rowsAffected);
            
            return rowsAffected;
            
        } catch (Exception e) {
            auditLogger.logDatabaseError(queryName, e.getMessage());
            log.error("Secure update execution failed: {}", queryName, e);
            throw new SecureRepositoryException("Update execution failed", e);
        }
    }
    
    /**
     * Execute a native query safely (only for read operations)
     */
    @Transactional(readOnly = true)
    public List<Object[]> executeSecureNativeQuery(String sql, Map<String, Object> parameters) {
        validateNativeQuery(sql);
        
        try {
            Query query = entityManager.createNativeQuery(sql);
            
            if (parameters != null) {
                for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                    validateParameterName(entry.getKey());
                    validateParameterValue(entry.getValue());
                    query.setParameter(entry.getKey(), entry.getValue());
                }
            }
            
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            
            auditLogger.logNativeQuery(sanitizeQueryForLogging(sql), parameters != null ? parameters.size() : 0, results.size());
            
            return results;
            
        } catch (Exception e) {
            auditLogger.logDatabaseError("NATIVE_QUERY", e.getMessage());
            log.error("Secure native query execution failed", e);
            throw new SecureRepositoryException("Native query execution failed", e);
        }
    }
    
    /**
     * Execute a JDBC query with prepared statement
     */
    @Transactional(readOnly = true)
    public <R> List<R> executeSecureJdbcQuery(String sql, Object[] parameters, RowMapper<R> rowMapper) {
        validateNativeQuery(sql);
        
        try {
            // Validate parameters
            if (parameters != null) {
                for (Object param : parameters) {
                    validateParameterValue(param);
                }
            }
            
            List<R> results = jdbcTemplate.query(sql, rowMapper, parameters);
            
            auditLogger.logJdbcQuery(sanitizeQueryForLogging(sql), parameters != null ? parameters.length : 0, results.size());
            
            return results;
            
        } catch (Exception e) {
            auditLogger.logDatabaseError("JDBC_QUERY", e.getMessage());
            log.error("Secure JDBC query execution failed", e);
            throw new SecureRepositoryException("JDBC query execution failed", e);
        }
    }
    
    /**
     * Batch insert operation with validation
     */
    @Transactional
    public void executeSecureBatchInsert(String sql, List<Object[]> batchParameters) {
        validateNativeQuery(sql);
        validateBatchInsertQuery(sql);
        
        if (batchParameters == null || batchParameters.isEmpty()) {
            return;
        }
        
        // Validate all parameters
        for (Object[] parameters : batchParameters) {
            if (parameters != null) {
                for (Object param : parameters) {
                    validateParameterValue(param);
                }
            }
        }
        
        try {
            jdbcTemplate.batchUpdate(sql, batchParameters);
            
            auditLogger.logBatchOperation("BATCH_INSERT", batchParameters.size());
            
        } catch (Exception e) {
            auditLogger.logDatabaseError("BATCH_INSERT", e.getMessage());
            log.error("Secure batch insert failed", e);
            throw new SecureRepositoryException("Batch insert failed", e);
        }
    }
    
    /**
     * Safe pagination with validation
     */
    @Override
    public Page<T> findAll(Pageable pageable) {
        validatePageable(pageable);
        
        try {
            Page<T> results = super.findAll(pageable);
            auditLogger.logDatabaseQuery("FIND_ALL_PAGEABLE", 0, results.getNumberOfElements());
            return results;
            
        } catch (Exception e) {
            auditLogger.logDatabaseError("FIND_ALL_PAGEABLE", e.getMessage());
            log.error("Secure pagination failed", e);
            throw new SecureRepositoryException("Pagination failed", e);
        }
    }
    
    // Validation methods
    
    private void validateQuerySafety(String queryName, Map<String, Object> parameters) {
        if (queryName == null || queryName.trim().isEmpty()) {
            throw new IllegalArgumentException("Query name cannot be null or empty");
        }
        
        // Check if query has been validated before (cache for performance)
        String cacheKey = queryName + "_" + (parameters != null ? parameters.keySet().toString() : "");
        if (validatedQueries.containsKey(cacheKey)) {
            return;
        }
        
        // Validate query name format
        if (!queryName.matches("^[a-zA-Z][a-zA-Z0-9_.]*$")) {
            auditLogger.logSecurityThreat("INVALID_QUERY_NAME", "repository", "system", queryName);
            throw new SecurityException("Invalid query name format");
        }
        
        // Cache validation result
        validatedQueries.put(cacheKey, true);
    }
    
    private void validateParameterName(String paramName) {
        if (paramName == null || paramName.trim().isEmpty()) {
            throw new IllegalArgumentException("Parameter name cannot be null or empty");
        }
        
        if (!paramName.matches("^[a-zA-Z][a-zA-Z0-9_]*$")) {
            auditLogger.logSecurityThreat("INVALID_PARAMETER_NAME", "repository", "system", paramName);
            throw new SecurityException("Invalid parameter name format");
        }
    }
    
    private void validateParameterValue(Object paramValue) {
        if (paramValue == null) {
            return; // Null values are allowed
        }
        
        // Validate string parameters for injection attempts
        if (paramValue instanceof String) {
            String stringValue = (String) paramValue;
            
            SecureInputValidator.ValidationResult result = inputValidator.validateInput(
                stringValue,
                new SecureInputValidator.ValidationContext("db_parameter", "system", SecureInputValidator.InputType.FREE_TEXT)
            );
            
            if (!result.isValid()) {
                auditLogger.logSecurityThreat("SQL_INJECTION_PARAMETER", "repository", "system", stringValue);
                throw new SecurityException("Parameter contains potential SQL injection: " + result.getErrorMessage());
            }
        }
        
        // Additional validation for specific types
        if (paramValue instanceof Number) {
            validateNumericParameter((Number) paramValue);
        }
    }
    
    private void validateNumericParameter(Number value) {
        if (value instanceof Double || value instanceof Float) {
            if (value.doubleValue() == Double.POSITIVE_INFINITY || 
                value.doubleValue() == Double.NEGATIVE_INFINITY ||
                Double.isNaN(value.doubleValue())) {
                throw new IllegalArgumentException("Invalid numeric parameter value");
            }
        }
    }
    
    private void validateNativeQuery(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL query cannot be null or empty");
        }
        
        String normalizedSql = sql.toLowerCase().trim();
        
        // Only allow SELECT statements for native queries
        if (!normalizedSql.startsWith("select")) {
            auditLogger.logSecurityThreat("UNSAFE_NATIVE_QUERY", "repository", "system", sql);
            throw new SecurityException("Only SELECT statements allowed in native queries");
        }
        
        // Check for dangerous patterns
        if (UNSAFE_QUERY_PATTERN.matcher(normalizedSql).find()) {
            auditLogger.logSecurityThreat("DANGEROUS_SQL_PATTERN", "repository", "system", sql);
            throw new SecurityException("Query contains dangerous SQL patterns");
        }
        
        // Ensure query uses parameters, not string concatenation
        if (sql.contains("'") && !sql.contains("?") && !sql.contains(":")) {
            auditLogger.logSecurityThreat("UNPARAMETERIZED_QUERY", "repository", "system", sql);
            throw new SecurityException("Query must use parameterized values");
        }
    }
    
    private void validateBatchInsertQuery(String sql) {
        String normalizedSql = sql.toLowerCase().trim();
        
        if (!normalizedSql.startsWith("insert")) {
            throw new SecurityException("Only INSERT statements allowed for batch operations");
        }
    }
    
    private void validateUpdateOperation(String queryName) {
        // Additional validation for update operations
        // Could check permissions, audit requirements, etc.
    }
    
    private void validateSortColumn(String sortBy) {
        if (sortBy == null || sortBy.trim().isEmpty()) {
            throw new IllegalArgumentException("Sort column cannot be null or empty");
        }
        
        // Only allow whitelisted column names
        if (!ALLOWED_SORT_COLUMNS.contains(sortBy)) {
            auditLogger.logSecurityThreat("INVALID_SORT_COLUMN", "repository", "system", sortBy);
            throw new SecurityException("Sort column not in whitelist: " + sortBy);
        }
    }
    
    private void validatePageable(Pageable pageable) {
        if (pageable == null) {
            return;
        }
        
        // Validate page size limits
        if (pageable.getPageSize() > 1000) {
            throw new IllegalArgumentException("Page size too large (max 1000)");
        }
        
        if (pageable.getPageSize() <= 0) {
            throw new IllegalArgumentException("Page size must be positive");
        }
        
        // Validate sort parameters
        if (pageable.getSort() != null && pageable.getSort().isSorted()) {
            pageable.getSort().forEach(order -> {
                validateSortColumn(order.getProperty());
            });
        }
    }
    
    private String sanitizeQueryForLogging(String sql) {
        if (sql == null) {
            return "null";
        }
        
        // Remove potentially sensitive data from SQL for logging
        String sanitized = sql.replaceAll("'[^']*'", "'***'")
                             .replaceAll("\\b\\d{4,}\\b", "****");
        
        return sanitized.length() > 200 ? sanitized.substring(0, 200) + "..." : sanitized;
    }
    
    // Builder interface for secure criteria queries
    
    public interface SecureCriteriaQueryBuilder<T> {
        Predicate buildPredicate(CriteriaBuilder cb, Root<T> root);
        
        default String getSortBy() { return "id"; }
        default boolean isAscending() { return true; }
        default Integer getOffset() { return 0; }
        default Integer getLimit() { return 100; }
    }
    
    // Exception class
    
    public static class SecureRepositoryException extends RuntimeException {
        public SecureRepositoryException(String message) {
            super(message);
        }
        
        public SecureRepositoryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    // Utility methods for common secure queries
    
    /**
     * Safe find by ID with validation
     */
    @Override
    public Optional<T> findById(ID id) {
        if (id == null) {
            return Optional.empty();
        }
        
        validateParameterValue(id);
        
        try {
            Optional<T> result = super.findById(id);
            auditLogger.logDatabaseQuery("FIND_BY_ID", 1, result.isPresent() ? 1 : 0);
            return result;
            
        } catch (Exception e) {
            auditLogger.logDatabaseError("FIND_BY_ID", e.getMessage());
            log.error("Secure find by ID failed", e);
            throw new SecureRepositoryException("Find by ID failed", e);
        }
    }
    
    /**
     * Safe save operation with validation
     */
    @Override
    @Transactional
    public <S extends T> S save(S entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        
        try {
            S result = super.save(entity);
            auditLogger.logDatabaseUpdate("SAVE_ENTITY", 0, 1);
            return result;
            
        } catch (Exception e) {
            auditLogger.logDatabaseError("SAVE_ENTITY", e.getMessage());
            log.error("Secure save failed", e);
            throw new SecureRepositoryException("Save operation failed", e);
        }
    }
    
    /**
     * Safe delete operation with validation
     */
    @Override
    @Transactional
    public void delete(T entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity cannot be null");
        }
        
        try {
            super.delete(entity);
            auditLogger.logDatabaseUpdate("DELETE_ENTITY", 0, 1);
            
        } catch (Exception e) {
            auditLogger.logDatabaseError("DELETE_ENTITY", e.getMessage());
            log.error("Secure delete failed", e);
            throw new SecureRepositoryException("Delete operation failed", e);
        }
    }
}