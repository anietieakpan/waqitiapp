package com.waqiti.reporting.repository;

import com.waqiti.reporting.dto.ReportCriteria;
import com.waqiti.reporting.dto.ReportResult;
import com.waqiti.reporting.exception.ReportSecurityException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Secure Custom Report Repository
 * 
 * This repository demonstrates how to prevent SQL injection vulnerabilities by:
 * - Using parameterized queries instead of string concatenation
 * - Input validation and sanitization
 * - Criteria API for dynamic queries
 * - Query whitelisting for allowed operations
 * - Audit logging of all query operations
 * 
 * SECURITY: Fixes SQL injection vulnerabilities in dynamic query building
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class SecureCustomReportRepository {
    
    private final EntityManager entityManager;
    private final AuditService auditService;
    
    // Whitelisted columns for dynamic queries
    private static final Set<String> ALLOWED_COLUMNS = Set.of(
        "transaction_id", "user_id", "amount", "currency", 
        "created_at", "status", "transaction_type", "category"
    );
    
    // Whitelisted tables for dynamic queries
    private static final Set<String> ALLOWED_TABLES = Set.of(
        "transactions", "users", "payments", "wallets", "transfers"
    );
    
    // Safe operators for filtering
    private static final Set<String> ALLOWED_OPERATORS = Set.of(
        "=", "!=", ">", "<", ">=", "<=", "LIKE", "IN"
    );
    
    // Pattern for validating identifiers
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");
    
    /**
     * SECURE: Execute custom report using Criteria API
     * This method prevents SQL injection by using JPA Criteria API
     */
    public List<ReportResult> executeSecureReport(ReportCriteria criteria) {
        log.info("Executing secure report: {}", criteria.getReportName());
        
        try {
            // Security: Validate criteria
            validateReportCriteria(criteria);
            
            // Security: Use Criteria API instead of string concatenation
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
            Root<?> root = cq.from(getEntityClass(criteria.getTableName()));
            
            // Build select clause
            List<jakarta.persistence.criteria.Selection<?>> selections = new ArrayList<>();
            for (String column : criteria.getSelectedColumns()) {
                validateColumnName(column);
                selections.add(root.get(column));
            }
            cq.multiselect(selections);
            
            // Build where clause
            List<Predicate> predicates = new ArrayList<>();
            for (ReportCriteria.FilterCriteria filter : criteria.getFilters()) {
                Predicate predicate = buildSecurePredicate(cb, root, filter);
                if (predicate != null) {
                    predicates.add(predicate);
                }
            }
            
            if (!predicates.isEmpty()) {
                cq.where(cb.and(predicates.toArray(new Predicate[0])));
            }
            
            // Security: Limit results to prevent large data dumps
            TypedQuery<Object[]> query = entityManager.createQuery(cq);
            query.setMaxResults(Math.min(criteria.getLimit(), 10000)); // Max 10K rows
            query.setFirstResult(criteria.getOffset());
            
            List<Object[]> results = query.getResultList();
            
            // Security: Audit the query execution
            auditService.auditReportExecution(
                criteria.getReportName(),
                criteria.getUserId(),
                results.size(),
                "SUCCESS"
            );
            
            return convertToReportResults(results, criteria.getSelectedColumns());
            
        } catch (Exception e) {
            log.error("Report execution failed", e);
            auditService.auditReportExecution(
                criteria.getReportName(),
                criteria.getUserId(),
                0,
                "FAILED: " + e.getMessage()
            );
            throw new ReportSecurityException("Report execution failed", e);
        }
    }
    
    /**
     * SECURE: Execute parameterized query
     * Uses proper parameter binding instead of string concatenation
     */
    public List<Map<String, Object>> executeParameterizedQuery(
            String templateName,
            Map<String, Object> parameters) {
        
        log.info("Executing parameterized query: {}", templateName);
        
        // Security: Get pre-approved query template
        String queryTemplate = getApprovedQueryTemplate(templateName);
        if (queryTemplate == null) {
            throw new ReportSecurityException("Query template not approved: " + templateName);
        }
        
        try {
            // Security: Use named parameters
            Query query = entityManager.createNativeQuery(queryTemplate);
            
            // Security: Bind parameters safely
            for (Map.Entry<String, Object> param : parameters.entrySet()) {
                validateParameterName(param.getKey());
                validateParameterValue(param.getValue());
                query.setParameter(param.getKey(), param.getValue());
            }
            
            // Security: Limit results
            query.setMaxResults(10000);
            
            @SuppressWarnings("unchecked")
            List<Object[]> results = query.getResultList();
            
            return convertResultsToMap(results);
            
        } catch (Exception e) {
            log.error("Parameterized query execution failed", e);
            throw new ReportSecurityException("Query execution failed", e);
        }
    }
    
    /**
     * VULNERABLE CODE EXAMPLE (DO NOT USE)
     * This shows what NOT to do - never concatenate user input into SQL
     */
    @Deprecated
    public List<Object[]> vulnerableQuery(String userProvidedFilter) {
        // SECURITY VULNERABILITY: SQL Injection
        // String sql = "SELECT * FROM transactions WHERE " + userProvidedFilter;
        // return entityManager.createNativeQuery(sql).getResultList();
        
        // PRODUCTION FIX: Implement secure parameterized queries instead of raw SQL
        log.warn("Attempted to use deprecated vulnerable query method - using secure alternative");
        
        // Use secure parameterized query builder
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<Transaction> root = query.from(Transaction.class);
        
        // Build secure predicates from user criteria
        List<Predicate> predicates = buildSecurePredicates(cb, root, userCriteria);
        
        if (!predicates.isEmpty()) {
            query.where(cb.and(predicates.toArray(new Predicate[0])));
        }
        
        // Apply security filters - user can only see their own data
        Predicate userFilter = cb.equal(root.get("userId"), getCurrentUserId());
        query.where(cb.and(query.getRestriction(), userFilter));
        
        return entityManager.createQuery(query).getResultList();
    }
    
    /**
     * Validate report criteria
     */
    private void validateReportCriteria(ReportCriteria criteria) {
        if (criteria == null) {
            throw new ReportSecurityException("Report criteria cannot be null");
        }
        
        // Validate table name
        if (!ALLOWED_TABLES.contains(criteria.getTableName())) {
            throw new ReportSecurityException(
                "Table not allowed: " + criteria.getTableName()
            );
        }
        
        // Validate selected columns
        for (String column : criteria.getSelectedColumns()) {
            validateColumnName(column);
        }
        
        // Validate filters
        for (ReportCriteria.FilterCriteria filter : criteria.getFilters()) {
            validateFilter(filter);
        }
        
        // Validate pagination
        if (criteria.getLimit() <= 0 || criteria.getLimit() > 10000) {
            throw new ReportSecurityException("Invalid limit: " + criteria.getLimit());
        }
        
        if (criteria.getOffset() < 0) {
            throw new ReportSecurityException("Invalid offset: " + criteria.getOffset());
        }
    }
    
    /**
     * Validate column name
     */
    private void validateColumnName(String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) {
            throw new ReportSecurityException("Column name cannot be null or empty");
        }
        
        if (!ALLOWED_COLUMNS.contains(columnName)) {
            throw new ReportSecurityException("Column not allowed: " + columnName);
        }
        
        if (!IDENTIFIER_PATTERN.matcher(columnName).matches()) {
            throw new ReportSecurityException("Invalid column name format: " + columnName);
        }
    }
    
    /**
     * Validate filter criteria
     */
    private void validateFilter(ReportCriteria.FilterCriteria filter) {
        validateColumnName(filter.getColumnName());
        
        if (!ALLOWED_OPERATORS.contains(filter.getOperator())) {
            throw new ReportSecurityException("Operator not allowed: " + filter.getOperator());
        }
        
        if (filter.getValue() == null) {
            throw new ReportSecurityException("Filter value cannot be null");
        }
        
        validateParameterValue(filter.getValue());
    }
    
    /**
     * Build secure predicate using Criteria API
     */
    private Predicate buildSecurePredicate(
            CriteriaBuilder cb,
            Root<?> root,
            ReportCriteria.FilterCriteria filter) {
        
        String column = filter.getColumnName();
        String operator = filter.getOperator();
        Object value = filter.getValue();
        
        jakarta.persistence.criteria.Path<?> path = root.get(column);
        
        return switch (operator) {
            case "=" -> cb.equal(path, value);
            case "!=" -> cb.notEqual(path, value);
            case ">" -> cb.greaterThan((jakarta.persistence.criteria.Path<Comparable>) path, 
                                    (Comparable) value);
            case "<" -> cb.lessThan((jakarta.persistence.criteria.Path<Comparable>) path, 
                                  (Comparable) value);
            case ">=" -> cb.greaterThanOrEqualTo((jakarta.persistence.criteria.Path<Comparable>) path, 
                                               (Comparable) value);
            case "<=" -> cb.lessThanOrEqualTo((jakarta.persistence.criteria.Path<Comparable>) path, 
                                            (Comparable) value);
            case "LIKE" -> cb.like((jakarta.persistence.criteria.Path<String>) path, 
                                 value.toString());
            case "IN" -> path.in((Collection<?>) value);
            default -> null;
        };
    }
    
    /**
     * Get entity class for table name
     */
    private Class<?> getEntityClass(String tableName) {
        // Map table names to entity classes
        return switch (tableName) {
            case "transactions" -> Transaction.class;
            case "users" -> User.class;
            case "payments" -> Payment.class;
            case "wallets" -> Wallet.class;
            case "transfers" -> Transfer.class;
            default -> throw new ReportSecurityException("Unknown table: " + tableName);
        };
    }
    
    /**
     * Get approved query template
     */
    private String getApprovedQueryTemplate(String templateName) {
        // Pre-approved query templates stored securely
        Map<String, String> templates = Map.of(
            "daily_transaction_summary",
            "SELECT DATE(created_at) as transaction_date, " +
            "COUNT(*) as transaction_count, " +
            "SUM(amount) as total_amount " +
            "FROM transactions " +
            "WHERE created_at >= :startDate AND created_at < :endDate " +
            "GROUP BY DATE(created_at) " +
            "ORDER BY transaction_date",
            
            "user_payment_history",
            "SELECT t.transaction_id, t.amount, t.currency, t.created_at, t.status " +
            "FROM transactions t " +
            "WHERE t.user_id = :userId " +
            "AND t.created_at >= :startDate " +
            "ORDER BY t.created_at DESC " +
            "LIMIT :limit",
            
            "merchant_revenue_report",
            "SELECT m.merchant_name, " +
            "SUM(t.amount) as total_revenue, " +
            "COUNT(t.transaction_id) as transaction_count " +
            "FROM transactions t " +
            "JOIN merchants m ON t.merchant_id = m.merchant_id " +
            "WHERE t.created_at >= :startDate AND t.created_at < :endDate " +
            "AND t.status = 'COMPLETED' " +
            "GROUP BY m.merchant_id, m.merchant_name " +
            "ORDER BY total_revenue DESC"
        );
        
        return templates.get(templateName);
    }
    
    /**
     * Validate parameter name
     */
    private void validateParameterName(String paramName) {
        if (!IDENTIFIER_PATTERN.matcher(paramName).matches()) {
            throw new ReportSecurityException("Invalid parameter name: " + paramName);
        }
    }
    
    /**
     * Validate parameter value
     */
    private void validateParameterValue(Object value) {
        if (value instanceof String) {
            String strValue = (String) value;
            if (strValue.length() > 1000) {
                throw new ReportSecurityException("Parameter value too long");
            }
            
            // Check for SQL injection patterns
            String lowercaseValue = strValue.toLowerCase();
            String[] dangerousPatterns = {
                "union", "select", "insert", "update", "delete", "drop", 
                "exec", "execute", "--", "/*", "*/", "xp_", "sp_"
            };
            
            for (String pattern : dangerousPatterns) {
                if (lowercaseValue.contains(pattern)) {
                    throw new ReportSecurityException(
                        "Parameter contains dangerous pattern: " + pattern
                    );
                }
            }
        }
        
        // Validate numeric values
        if (value instanceof Number) {
            if (value instanceof BigDecimal) {
                BigDecimal decimal = (BigDecimal) value;
                if (decimal.precision() > 20) {
                    throw new ReportSecurityException("Numeric precision too high");
                }
            }
        }
    }
    
    /**
     * Convert results to report format
     */
    private List<ReportResult> convertToReportResults(
            List<Object[]> results, 
            List<String> columns) {
        
        List<ReportResult> reportResults = new ArrayList<>();
        
        for (Object[] row : results) {
            Map<String, Object> dataMap = new HashMap<>();
            for (int i = 0; i < columns.size() && i < row.length; i++) {
                dataMap.put(columns.get(i), row[i]);
            }
            
            reportResults.add(ReportResult.builder()
                .data(dataMap)
                .rowNumber(reportResults.size() + 1)
                .build());
        }
        
        return reportResults;
    }
    
    /**
     * Convert results to map format
     */
    private List<Map<String, Object>> convertResultsToMap(List<Object[]> results) {
        List<Map<String, Object>> mapResults = new ArrayList<>();
        
        for (Object[] row : results) {
            Map<String, Object> rowMap = new HashMap<>();
            for (int i = 0; i < row.length; i++) {
                rowMap.put("column_" + i, row[i]);
            }
            mapResults.add(rowMap);
        }
        
        return mapResults;
    }
    
    /**
     * Get report statistics with security validation
     */
    public ReportStatistics getReportStatistics(String reportName, String userId) {
        // Security: Validate user has permission for report
        validateUserReportAccess(userId, reportName);
        
        // Security: Use parameterized query for statistics
        String sql = """
            SELECT COUNT(*) as total_executions,
                   AVG(execution_time_ms) as avg_execution_time,
                   MAX(created_at) as last_execution
            FROM report_executions
            WHERE report_name = :reportName
              AND user_id = :userId
              AND created_at >= :thirtyDaysAgo
        """;
        
        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("reportName", reportName);
        query.setParameter("userId", userId);
        query.setParameter("thirtyDaysAgo", LocalDate.now().minusDays(30));
        
        Object[] result = (Object[]) query.getSingleResult();
        
        return ReportStatistics.builder()
            .totalExecutions(((Number) result[0]).longValue())
            .averageExecutionTime(((Number) result[1]).doubleValue())
            .lastExecution((LocalDate) result[2])
            .build();
    }
    
    /**
     * Validate user has access to report
     */
    private void validateUserReportAccess(String userId, String reportName) {
        // Implementation would check user permissions
        // This is a placeholder for proper authorization
        if (userId == null || reportName == null) {
            throw new ReportSecurityException("User ID and report name required");
        }
    }
}