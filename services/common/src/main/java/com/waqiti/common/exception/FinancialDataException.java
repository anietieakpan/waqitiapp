package com.waqiti.common.exception;

import java.util.Map;

/**
 * Exception thrown when financial data operations fail or encounter invalid states.
 * This exception should be used for cases where financial data is missing, invalid,
 * or cannot be processed safely.
 *
 * USAGE EXAMPLES:
 * - Simple: throw new FinancialDataException("Invalid financial data")
 * - With entity context: throw new FinancialDataException("Account not found", "accountId123")
 * - With full context: throw new FinancialDataException("Invalid account", "Account", "accountId123")
 * - With cause: throw new FinancialDataException("Database error", cause)
 */
public class FinancialDataException extends BusinessException {

    /**
     * Create financial data exception with message only
     */
    public FinancialDataException(String message) {
        super(ErrorCode.FINANCIAL_DATA_ERROR, message);
    }

    /**
     * Create financial data exception with message and root cause
     */
    public FinancialDataException(String message, Throwable cause) {
        super(ErrorCode.FINANCIAL_DATA_ERROR, message, cause);
    }

    /**
     * Create financial data exception with entity context
     * Automatically adds entityId to metadata for tracking
     */
    public FinancialDataException(String message, String entityId) {
        super(ErrorCode.FINANCIAL_DATA_ERROR, message, Map.of("entityId", entityId));
    }

    /**
     * Create financial data exception with full entity context
     * Automatically adds entityType and entityId to metadata
     */
    public FinancialDataException(String message, String entityType, String entityId) {
        super(ErrorCode.FINANCIAL_DATA_ERROR, message,
            Map.of("entityType", entityType, "entityId", entityId));
    }

    /**
     * Create financial data exception with cause and entity context
     */
    public FinancialDataException(String message, Throwable cause, String entityId) {
        super(ErrorCode.FINANCIAL_DATA_ERROR, message, cause, Map.of("entityId", entityId));
    }

    /**
     * Create financial data exception with cause and full entity context
     */
    public FinancialDataException(String message, Throwable cause, String entityType, String entityId) {
        super(ErrorCode.FINANCIAL_DATA_ERROR, message, cause,
            Map.of("entityType", entityType, "entityId", entityId));
    }
}