package com.waqiti.common.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * Exception thrown when investment data operations fail or are invalid.
 * This exception should be used for cases where investment data is missing,
 * invalid, or cannot be retrieved safely.
 *
 * DOMAIN-SPECIFIC FIELDS:
 * - symbol: Stock/crypto symbol (e.g., "AAPL", "BTC")
 * - investmentId: Unique investment identifier
 *
 * USAGE EXAMPLES:
 * - Simple: throw new InvestmentDataException("Invalid investment data")
 * - With symbol: throw new InvestmentDataException("Price not found", "AAPL")
 * - With full context: throw new InvestmentDataException("Position error", "AAPL", "INV-123")
 * - With cause: throw new InvestmentDataException("API error", cause, "BTC")
 */
public class InvestmentDataException extends BusinessException {

    private final String symbol;
    private final String investmentId;

    /**
     * Create investment data exception with message only
     */
    public InvestmentDataException(String message) {
        super(ErrorCode.INVESTMENT_DATA_ERROR, message);
        this.symbol = null;
        this.investmentId = null;
    }

    /**
     * Create investment data exception with message and root cause
     */
    public InvestmentDataException(String message, Throwable cause) {
        super(ErrorCode.INVESTMENT_DATA_ERROR, message, cause);
        this.symbol = null;
        this.investmentId = null;
    }

    /**
     * Create investment data exception with stock/crypto symbol
     * Automatically adds symbol to metadata for tracking
     */
    public InvestmentDataException(String message, String symbol) {
        super(ErrorCode.INVESTMENT_DATA_ERROR, message, Map.of("symbol", symbol));
        this.symbol = symbol;
        this.investmentId = null;
    }

    /**
     * Create investment data exception with symbol and investment ID
     * Automatically adds both to metadata
     */
    public InvestmentDataException(String message, String symbol, String investmentId) {
        super(ErrorCode.INVESTMENT_DATA_ERROR, message,
            buildMetadata(symbol, investmentId));
        this.symbol = symbol;
        this.investmentId = investmentId;
    }

    /**
     * Create investment data exception with cause and symbol
     */
    public InvestmentDataException(String message, Throwable cause, String symbol) {
        super(ErrorCode.INVESTMENT_DATA_ERROR, message, cause, Map.of("symbol", symbol));
        this.symbol = symbol;
        this.investmentId = null;
    }

    /**
     * Create investment data exception with cause, symbol, and investment ID
     */
    public InvestmentDataException(String message, Throwable cause, String symbol, String investmentId) {
        super(ErrorCode.INVESTMENT_DATA_ERROR, message, cause,
            buildMetadata(symbol, investmentId));
        this.symbol = symbol;
        this.investmentId = investmentId;
    }

    /**
     * Get stock/crypto symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Get investment ID
     */
    public String getInvestmentId() {
        return investmentId;
    }

    /**
     * Helper method to build metadata map
     */
    private static Map<String, Object> buildMetadata(String symbol, String investmentId) {
        Map<String, Object> metadata = new HashMap<>();
        if (symbol != null) {
            metadata.put("symbol", symbol);
        }
        if (investmentId != null) {
            metadata.put("investmentId", investmentId);
        }
        return metadata;
    }
}