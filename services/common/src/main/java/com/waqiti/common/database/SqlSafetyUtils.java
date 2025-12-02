package com.waqiti.common.database;

import org.springframework.jdbc.core.JdbcTemplate;
import java.util.regex.Pattern;

/**
 * Utility class for SQL safety and injection prevention
 */
public class SqlSafetyUtils {
    
    private static final Pattern SAFE_TABLE_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");
    private static final Pattern SAFE_COLUMN_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");
    private static final Pattern SAFE_SCHEMA_NAME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}$");
    
    /**
     * Validates and quotes a table name for safe SQL usage
     * @param tableName the table name to validate
     * @return the quoted table name
     * @throws IllegalArgumentException if the table name is invalid
     */
    public static String quoteTableName(String tableName) {
        if (tableName == null || tableName.isEmpty()) {
            throw new IllegalArgumentException("Table name cannot be null or empty");
        }
        
        if (!SAFE_TABLE_NAME_PATTERN.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Invalid table name format: " + tableName);
        }
        
        // PostgreSQL style quoting
        return "\"" + tableName + "\"";
    }
    
    /**
     * Validates and quotes a column name for safe SQL usage
     * @param columnName the column name to validate
     * @return the quoted column name
     * @throws IllegalArgumentException if the column name is invalid
     */
    public static String quoteColumnName(String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            throw new IllegalArgumentException("Column name cannot be null or empty");
        }
        
        if (!SAFE_COLUMN_NAME_PATTERN.matcher(columnName).matches()) {
            throw new IllegalArgumentException("Invalid column name format: " + columnName);
        }
        
        return "\"" + columnName + "\"";
    }
    
    /**
     * Validates and quotes a schema name for safe SQL usage
     * @param schemaName the schema name to validate
     * @return the quoted schema name
     * @throws IllegalArgumentException if the schema name is invalid
     */
    public static String quoteSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isEmpty()) {
            throw new IllegalArgumentException("Schema name cannot be null or empty");
        }
        
        if (!SAFE_SCHEMA_NAME_PATTERN.matcher(schemaName).matches()) {
            throw new IllegalArgumentException("Invalid schema name format: " + schemaName);
        }
        
        return "\"" + schemaName + "\"";
    }
    
    /**
     * Creates a fully qualified table name with schema
     * @param schemaName the schema name
     * @param tableName the table name
     * @return the fully qualified table name
     */
    public static String qualifiedTableName(String schemaName, String tableName) {
        return quoteSchemaName(schemaName) + "." + quoteTableName(tableName);
    }
    
    /**
     * Validates that a string contains only safe characters for use in SQL
     * @param value the value to validate
     * @return true if the value is safe
     */
    public static boolean isSafeSqlIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return SAFE_TABLE_NAME_PATTERN.matcher(value).matches();
    }
    
    /**
     * Escapes single quotes in a string value for SQL
     * @param value the value to escape
     * @return the escaped value
     */
    public static String escapeSqlString(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("'", "''");
    }
    
    /**
     * Creates a safe LIKE pattern
     * @param pattern the pattern to make safe
     * @return the safe LIKE pattern
     */
    public static String safeLikePattern(String pattern) {
        if (pattern == null) {
            return null;
        }
        // Escape special LIKE characters
        return pattern
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}