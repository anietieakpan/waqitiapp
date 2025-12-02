/**
 * SQL Sensitive Data Filter
 * Specifically filters SQL queries and parameters that may contain sensitive data
 * Prevents database credentials, PII, and financial data from appearing in SQL logs
 */
package com.waqiti.common.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.util.regex.Pattern;

/**
 * Specialized filter for SQL logging that identifies and filters sensitive database operations
 * Focuses on preventing exposure of sensitive data in SQL query logs
 */
public class SqlSensitiveDataFilter extends Filter<ILoggingEvent> {

    private boolean enabled = true;

    // SQL patterns that often contain sensitive data
    private static final Pattern[] SENSITIVE_SQL_PATTERNS = {
        // INSERT statements with values that might contain sensitive data
        Pattern.compile("(?i)INSERT\\s+INTO\\s+(?:users?|customers?|accounts?|payments?|transactions?).*VALUES", Pattern.DOTALL),
        
        // UPDATE statements on sensitive tables
        Pattern.compile("(?i)UPDATE\\s+(?:users?|customers?|accounts?|payments?|transactions?)\\s+SET.*password", Pattern.DOTALL),
        Pattern.compile("(?i)UPDATE\\s+(?:users?|customers?|accounts?|payments?|transactions?)\\s+SET.*email", Pattern.DOTALL),
        Pattern.compile("(?i)UPDATE\\s+(?:users?|customers?|accounts?|payments?|transactions?)\\s+SET.*phone", Pattern.DOTALL),
        Pattern.compile("(?i)UPDATE\\s+(?:users?|customers?|accounts?|payments?|transactions?)\\s+SET.*ssn", Pattern.DOTALL),
        Pattern.compile("(?i)UPDATE\\s+(?:users?|customers?|accounts?|payments?|transactions?)\\s+SET.*account_number", Pattern.DOTALL),
        
        // SELECT statements that might expose sensitive data
        Pattern.compile("(?i)SELECT.*password.*FROM", Pattern.DOTALL),
        Pattern.compile("(?i)SELECT.*credit_card.*FROM", Pattern.DOTALL),
        Pattern.compile("(?i)SELECT.*ssn.*FROM", Pattern.DOTALL),
        Pattern.compile("(?i)SELECT.*account_number.*FROM", Pattern.DOTALL),
        Pattern.compile("(?i)SELECT.*api_key.*FROM", Pattern.DOTALL),
        Pattern.compile("(?i)SELECT.*secret.*FROM", Pattern.DOTALL),
        
        // WHERE clauses with potentially sensitive conditions
        Pattern.compile("(?i)WHERE.*password\\s*=", Pattern.DOTALL),
        Pattern.compile("(?i)WHERE.*email\\s*=\\s*['\"][^'\"]*@", Pattern.DOTALL),
        Pattern.compile("(?i)WHERE.*phone\\s*=\\s*['\"][0-9+\\-\\s\\(\\)]+", Pattern.DOTALL),
        Pattern.compile("(?i)WHERE.*ssn\\s*=", Pattern.DOTALL),
        Pattern.compile("(?i)WHERE.*account_number\\s*=", Pattern.DOTALL),
        
        // Database connection strings
        Pattern.compile("(?i)(?:jdbc|mongodb|redis)://.*:[^@]*@", Pattern.DOTALL),
        
        // Prepared statement parameters that might be sensitive
        Pattern.compile("(?i)binding parameter.*password", Pattern.DOTALL),
        Pattern.compile("(?i)binding parameter.*secret", Pattern.DOTALL),
        Pattern.compile("(?i)binding parameter.*token", Pattern.DOTALL),
        Pattern.compile("(?i)binding parameter.*key", Pattern.DOTALL),
        
        // Common sensitive column names in any context
        Pattern.compile("(?i)\\b(?:password|passwd|pwd|secret|token|api_key|private_key|credit_card|ccn|ssn|social_security|account_number|routing_number|pin|cvv|security_code)\\b", Pattern.DOTALL),
    };

    // Patterns for SQL that should be completely blocked
    private static final Pattern[] BLOCK_SQL_PATTERNS = {
        // Queries that dump entire sensitive tables
        Pattern.compile("(?i)SELECT\\s+\\*\\s+FROM\\s+(?:users?|customers?|accounts?|payments?|transactions?|credentials?)", Pattern.DOTALL),
        
        // Administrative queries that might expose system information
        Pattern.compile("(?i)SHOW\\s+(?:DATABASES|TABLES|COLUMNS|GRANTS|PRIVILEGES)", Pattern.DOTALL),
        Pattern.compile("(?i)DESCRIBE\\s+(?:users?|customers?|accounts?|payments?)", Pattern.DOTALL),
        Pattern.compile("(?i)INFORMATION_SCHEMA", Pattern.DOTALL),
        
        // Backup and export operations
        Pattern.compile("(?i)(?:BACKUP|EXPORT|DUMP).*(?:users?|customers?|accounts?|payments?)", Pattern.DOTALL),
    };

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (!enabled) {
            return FilterReply.NEUTRAL;
        }

        String message = event.getFormattedMessage();
        if (message == null) {
            return FilterReply.NEUTRAL;
        }

        // Check if this is even an SQL-related log message
        if (!isLikelySqlLog(message)) {
            return FilterReply.NEUTRAL;
        }

        // Check for SQL patterns that should be completely blocked
        for (Pattern pattern : BLOCK_SQL_PATTERNS) {
            if (pattern.matcher(message).find()) {
                return FilterReply.DENY;
            }
        }

        // Check for SQL patterns that contain sensitive data
        for (Pattern pattern : SENSITIVE_SQL_PATTERNS) {
            if (pattern.matcher(message).find()) {
                return FilterReply.DENY;
            }
        }

        return FilterReply.NEUTRAL;
    }

    /**
     * Quick check to determine if a log message is likely SQL-related
     */
    private boolean isLikelySqlLog(String message) {
        String lowerMessage = message.toLowerCase();
        return lowerMessage.contains("select") ||
               lowerMessage.contains("insert") ||
               lowerMessage.contains("update") ||
               lowerMessage.contains("delete") ||
               lowerMessage.contains("create") ||
               lowerMessage.contains("alter") ||
               lowerMessage.contains("drop") ||
               lowerMessage.contains("grant") ||
               lowerMessage.contains("revoke") ||
               lowerMessage.contains("jdbc") ||
               lowerMessage.contains("sql") ||
               lowerMessage.contains("query") ||
               lowerMessage.contains("binding parameter") ||
               lowerMessage.contains("prepared statement") ||
               lowerMessage.contains("database");
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}