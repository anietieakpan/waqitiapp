package com.waqiti.common.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.*;
import java.util.regex.Pattern;

/**
 * CRITICAL SECURITY COMPONENT: SQL Injection Prevention Framework
 *
 * PURPOSE:
 * Provides safe query building API that prevents SQL injection vulnerabilities
 * by enforcing parameterized queries and validating all inputs.
 *
 * SECURITY ISSUE ADDRESSED:
 * Analysis identified SQL injection vulnerabilities from string concatenation:
 * - String building with user inputs
 * - Dynamic table/column names from user data
 * - LIKE clauses with unescaped wildcards
 *
 * USAGE:
 * Replace string concatenation:
 *   BAD:  "SELECT * FROM users WHERE email = '" + userEmail + "'"
 *   GOOD: SafeQueryBuilder.select("users").where("email", userEmail).build()
 *
 * @author Waqiti Security Team
 * @since 2025-10-31
 * @version 1.0.0
 */
@Slf4j
public class SafeQueryBuilder {

    private final StringBuilder queryBuilder;
    private final MapSqlParameterSource parameters;
    private int paramCounter = 0;

    // Whitelist of allowed table names (prevents SQL injection via table names)
    private static final Set<String> ALLOWED_TABLES = Set.of(
        "users", "wallets", "transactions_partitioned", "payments",
        "ledger_entries_partitioned", "audit_events_partitioned",
        "merchants", "payment_methods", "bank_accounts",
        "kyc_verifications", "compliance_checks", "fraud_alerts"
    );

    // Whitelist of allowed column names
    private static final Set<String> ALLOWED_COLUMNS = Set.of(
        "user_id", "wallet_id", "transaction_id", "payment_id",
        "email", "phone_number", "status", "amount", "currency",
        "created_at", "updated_at", "transaction_date", "merchant_id"
    );

    // Pattern for validating identifiers (table/column names)
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    // Pattern for detecting SQL injection attempts
    private static final Pattern SQL_INJECTION_PATTERN = Pattern.compile(
        "('|(--)|(/\\*)(\\*/)|;|\\b(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|EXEC|EXECUTE)\\b)",
        Pattern.CASE_INSENSITIVE
    );

    private SafeQueryBuilder() {
        this.queryBuilder = new StringBuilder();
        this.parameters = new MapSqlParameterSource();
    }

    /**
     * Starts a SELECT query builder.
     *
     * @param tableName table name (validated against whitelist)
     * @return query builder
     * @throws SecurityException if table name invalid
     */
    public static SafeQueryBuilder select(String tableName) {
        SafeQueryBuilder builder = new SafeQueryBuilder();
        builder.validateTableName(tableName);
        builder.queryBuilder.append("SELECT * FROM ").append(tableName);
        return builder;
    }

    /**
     * Starts a SELECT query with specific columns.
     *
     * @param tableName table name
     * @param columns column names (validated)
     * @return query builder
     */
    public static SafeQueryBuilder selectColumns(String tableName, String... columns) {
        SafeQueryBuilder builder = new SafeQueryBuilder();
        builder.validateTableName(tableName);

        for (String column : columns) {
            builder.validateColumnName(column);
        }

        builder.queryBuilder.append("SELECT ")
            .append(String.join(", ", columns))
            .append(" FROM ")
            .append(tableName);
        return builder;
    }

    /**
     * Adds WHERE clause with equality condition.
     * Uses parameterized query to prevent SQL injection.
     *
     * @param column column name
     * @param value parameter value
     * @return query builder
     */
    public SafeQueryBuilder where(String column, Object value) {
        validateColumnName(column);
        String paramName = nextParamName();

        if (queryBuilder.toString().contains("WHERE")) {
            queryBuilder.append(" AND ");
        } else {
            queryBuilder.append(" WHERE ");
        }

        queryBuilder.append(column).append(" = :").append(paramName);
        parameters.addValue(paramName, value);
        return this;
    }

    /**
     * Adds WHERE IN clause with multiple values.
     *
     * @param column column name
     * @param values collection of values
     * @return query builder
     */
    public SafeQueryBuilder whereIn(String column, Collection<?> values) {
        validateColumnName(column);

        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("WHERE IN clause requires at least one value");
        }

        String paramName = nextParamName();

        if (queryBuilder.toString().contains("WHERE")) {
            queryBuilder.append(" AND ");
        } else {
            queryBuilder.append(" WHERE ");
        }

        queryBuilder.append(column).append(" IN (:").append(paramName).append(")");
        parameters.addValue(paramName, values);
        return this;
    }

    /**
     * Adds LIKE clause for pattern matching.
     * Automatically escapes wildcards in user input to prevent injection.
     *
     * @param column column name
     * @param pattern user-provided pattern
     * @return query builder
     */
    public SafeQueryBuilder whereLike(String column, String pattern) {
        validateColumnName(column);

        // Escape special LIKE characters from user input
        String escapedPattern = escapeLikePattern(pattern);
        String paramName = nextParamName();

        if (queryBuilder.toString().contains("WHERE")) {
            queryBuilder.append(" AND ");
        } else {
            queryBuilder.append(" WHERE ");
        }

        queryBuilder.append(column).append(" LIKE :").append(paramName).append(" ESCAPE '\\'");
        parameters.addValue(paramName, "%" + escapedPattern + "%");
        return this;
    }

    /**
     * Adds comparison condition (>, <, >=, <=).
     *
     * @param column column name
     * @param operator comparison operator
     * @param value comparison value
     * @return query builder
     */
    public SafeQueryBuilder whereComparison(String column, String operator, Object value) {
        validateColumnName(column);
        validateOperator(operator);

        String paramName = nextParamName();

        if (queryBuilder.toString().contains("WHERE")) {
            queryBuilder.append(" AND ");
        } else {
            queryBuilder.append(" WHERE ");
        }

        queryBuilder.append(column).append(" ").append(operator).append(" :").append(paramName);
        parameters.addValue(paramName, value);
        return this;
    }

    /**
     * Adds ORDER BY clause.
     *
     * @param column column name
     * @param direction ASC or DESC
     * @return query builder
     */
    public SafeQueryBuilder orderBy(String column, String direction) {
        validateColumnName(column);

        if (!"ASC".equalsIgnoreCase(direction) && !"DESC".equalsIgnoreCase(direction)) {
            throw new SecurityException("Invalid sort direction: " + direction);
        }

        queryBuilder.append(" ORDER BY ").append(column).append(" ").append(direction.toUpperCase());
        return this;
    }

    /**
     * Adds LIMIT clause for pagination.
     *
     * @param limit maximum rows to return
     * @return query builder
     */
    public SafeQueryBuilder limit(int limit) {
        if (limit < 0 || limit > 10000) {
            throw new IllegalArgumentException("Limit must be between 0 and 10000");
        }

        String paramName = nextParamName();
        queryBuilder.append(" LIMIT :").append(paramName);
        parameters.addValue(paramName, limit);
        return this;
    }

    /**
     * Adds OFFSET clause for pagination.
     *
     * @param offset number of rows to skip
     * @return query builder
     */
    public SafeQueryBuilder offset(int offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("Offset must be non-negative");
        }

        String paramName = nextParamName();
        queryBuilder.append(" OFFSET :").append(paramName);
        parameters.addValue(paramName, offset);
        return this;
    }

    /**
     * Builds the final SQL query string.
     *
     * @return SQL query
     */
    public String buildQuery() {
        String query = queryBuilder.toString();
        log.debug("Built safe SQL query: {}", query);
        return query;
    }

    /**
     * Gets the parameter map for NamedParameterJdbcTemplate.
     *
     * @return parameter source
     */
    public MapSqlParameterSource getParameters() {
        return parameters;
    }

    /**
     * Executes query and returns results.
     *
     * @param jdbcTemplate JDBC template
     * @param rowMapper row mapper
     * @return query results
     */
    public <T> List<T> execute(NamedParameterJdbcTemplate jdbcTemplate,
                                org.springframework.jdbc.core.RowMapper<T> rowMapper) {
        String query = buildQuery();
        log.debug("Executing safe query with {} parameters", parameters.getValues().size());
        return jdbcTemplate.query(query, parameters, rowMapper);
    }

    // ========================================================================
    // VALIDATION METHODS
    // ========================================================================

    /**
     * Validates table name against whitelist and SQL injection patterns.
     *
     * @param tableName table name to validate
     * @throws SecurityException if validation fails
     */
    private void validateTableName(String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            throw new SecurityException("Table name cannot be null or empty");
        }

        // Check whitelist
        if (!ALLOWED_TABLES.contains(tableName.toLowerCase())) {
            log.error("SECURITY: Attempted to query non-whitelisted table: {}", tableName);
            throw new SecurityException("Table name not in whitelist: " + tableName);
        }

        // Check for SQL injection patterns
        if (SQL_INJECTION_PATTERN.matcher(tableName).find()) {
            log.error("SECURITY: SQL injection attempt detected in table name: {}", tableName);
            throw new SecurityException("Invalid characters in table name");
        }

        // Check identifier format
        if (!IDENTIFIER_PATTERN.matcher(tableName).matches()) {
            throw new SecurityException("Invalid table name format: " + tableName);
        }
    }

    /**
     * Validates column name.
     *
     * @param columnName column name to validate
     * @throws SecurityException if validation fails
     */
    private void validateColumnName(String columnName) {
        if (columnName == null || columnName.trim().isEmpty()) {
            throw new SecurityException("Column name cannot be null or empty");
        }

        // Check whitelist
        if (!ALLOWED_COLUMNS.contains(columnName.toLowerCase())) {
            log.warn("Column name not in whitelist (may be valid): {}", columnName);
            // Don't throw exception, just warn - whitelist may be incomplete
        }

        // Check for SQL injection patterns
        if (SQL_INJECTION_PATTERN.matcher(columnName).find()) {
            log.error("SECURITY: SQL injection attempt detected in column name: {}", columnName);
            throw new SecurityException("Invalid characters in column name");
        }

        // Check identifier format
        if (!IDENTIFIER_PATTERN.matcher(columnName).matches()) {
            throw new SecurityException("Invalid column name format: " + columnName);
        }
    }

    /**
     * Validates comparison operator.
     *
     * @param operator operator to validate
     * @throws SecurityException if invalid
     */
    private void validateOperator(String operator) {
        Set<String> allowedOperators = Set.of("=", ">", "<", ">=", "<=", "!=", "<>");
        if (!allowedOperators.contains(operator)) {
            throw new SecurityException("Invalid comparison operator: " + operator);
        }
    }

    /**
     * Escapes LIKE pattern wildcards from user input.
     *
     * Prevents user from injecting % or _ wildcards to expand search.
     *
     * @param pattern user input pattern
     * @return escaped pattern
     */
    private String escapeLikePattern(String pattern) {
        if (pattern == null) {
            return "";
        }

        return pattern
            .replace("\\", "\\\\")  // Escape backslash first
            .replace("%", "\\%")     // Escape % wildcard
            .replace("_", "\\_");    // Escape _ wildcard
    }

    /**
     * Generates next parameter name.
     *
     * @return parameter name like "param0", "param1", etc.
     */
    private String nextParamName() {
        return "param" + (paramCounter++);
    }
}
