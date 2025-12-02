package com.waqiti.payment.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Base exception for wallet service operations
 * Provides comprehensive error handling for wallet-related failures
 */
@Getter
public class WalletServiceException extends RuntimeException {
    
    private final String errorCode;
    private final HttpStatus httpStatus;
    private final LocalDateTime timestamp;
    private final Map<String, Object> details;
    private final String correlationId;
    private final String walletId;
    private final ErrorCategory category;
    
    public enum ErrorCategory {
        INSUFFICIENT_FUNDS,
        WALLET_NOT_FOUND,
        WALLET_LOCKED,
        WALLET_FROZEN,
        INVALID_OPERATION,
        TRANSACTION_FAILED,
        VALIDATION_ERROR,
        LIMIT_EXCEEDED,
        DUPLICATE_REQUEST,
        NETWORK_ERROR,
        SERVICE_UNAVAILABLE,
        AUTHENTICATION_FAILED,
        AUTHORIZATION_FAILED,
        CONFIGURATION_ERROR,
        INTERNAL_ERROR
    }
    
    public WalletServiceException(String message) {
        this(message, "WALLET_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, null, ErrorCategory.INTERNAL_ERROR, new HashMap<>());
    }
    
    public WalletServiceException(String message, Throwable cause) {
        this(message, "WALLET_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, null, ErrorCategory.INTERNAL_ERROR, new HashMap<>(), cause);
    }
    
    public WalletServiceException(String message, String errorCode, HttpStatus httpStatus) {
        this(message, errorCode, httpStatus, null, ErrorCategory.INTERNAL_ERROR, new HashMap<>());
    }
    
    public WalletServiceException(String message, String errorCode, HttpStatus httpStatus, String walletId, ErrorCategory category) {
        this(message, errorCode, httpStatus, walletId, category, new HashMap<>());
    }
    
    public WalletServiceException(String message, String errorCode, HttpStatus httpStatus, 
                                  String walletId, ErrorCategory category, Map<String, Object> details) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.walletId = walletId;
        this.category = category;
        this.details = details != null ? new HashMap<>(details) : new HashMap<>();
        this.timestamp = LocalDateTime.now();
        this.correlationId = generateCorrelationId();
    }
    
    public WalletServiceException(String message, String errorCode, HttpStatus httpStatus, 
                                  String walletId, ErrorCategory category, Map<String, Object> details, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.walletId = walletId;
        this.category = category;
        this.details = details != null ? new HashMap<>(details) : new HashMap<>();
        this.timestamp = LocalDateTime.now();
        this.correlationId = generateCorrelationId();
    }
    
    /**
     * Add additional detail to the exception
     */
    public WalletServiceException withDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }
    
    /**
     * Create exception for insufficient funds
     */
    public static WalletServiceException insufficientFunds(String walletId, double required, double available) {
        Map<String, Object> details = new HashMap<>();
        details.put("requiredAmount", required);
        details.put("availableAmount", available);
        details.put("shortfall", required - available);
        
        return new WalletServiceException(
            String.format("Insufficient funds in wallet %s. Required: %.2f, Available: %.2f", 
                         walletId, required, available),
            "INSUFFICIENT_FUNDS",
            HttpStatus.PAYMENT_REQUIRED,
            walletId,
            ErrorCategory.INSUFFICIENT_FUNDS,
            details
        );
    }
    
    /**
     * Create exception for wallet not found
     */
    public static WalletServiceException walletNotFound(String walletId) {
        return new WalletServiceException(
            String.format("Wallet not found: %s", walletId),
            "WALLET_NOT_FOUND",
            HttpStatus.NOT_FOUND,
            walletId,
            ErrorCategory.WALLET_NOT_FOUND,
            null
        );
    }
    
    /**
     * Create exception for locked wallet
     */
    public static WalletServiceException walletLocked(String walletId, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("lockReason", reason);
        
        return new WalletServiceException(
            String.format("Wallet %s is locked: %s", walletId, reason),
            "WALLET_LOCKED",
            HttpStatus.LOCKED,
            walletId,
            ErrorCategory.WALLET_LOCKED,
            details
        );
    }
    
    /**
     * Create exception for frozen wallet
     */
    public static WalletServiceException walletFrozen(String walletId, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("freezeReason", reason);
        
        return new WalletServiceException(
            String.format("Wallet %s is frozen: %s", walletId, reason),
            "WALLET_FROZEN",
            HttpStatus.FORBIDDEN,
            walletId,
            ErrorCategory.WALLET_FROZEN,
            details
        );
    }
    
    /**
     * Create exception for transaction limit exceeded
     */
    public static WalletServiceException limitExceeded(String walletId, String limitType, double limit, double requested) {
        Map<String, Object> details = new HashMap<>();
        details.put("limitType", limitType);
        details.put("limit", limit);
        details.put("requested", requested);
        details.put("excess", requested - limit);
        
        return new WalletServiceException(
            String.format("Transaction limit exceeded for wallet %s. Limit: %.2f, Requested: %.2f", 
                         walletId, limit, requested),
            "LIMIT_EXCEEDED",
            HttpStatus.UNPROCESSABLE_ENTITY,
            walletId,
            ErrorCategory.LIMIT_EXCEEDED,
            details
        );
    }
    
    /**
     * Create exception for duplicate transaction
     */
    public static WalletServiceException duplicateTransaction(String walletId, String transactionId) {
        Map<String, Object> details = new HashMap<>();
        details.put("duplicateTransactionId", transactionId);
        
        return new WalletServiceException(
            String.format("Duplicate transaction detected for wallet %s: %s", walletId, transactionId),
            "DUPLICATE_TRANSACTION",
            HttpStatus.CONFLICT,
            walletId,
            ErrorCategory.DUPLICATE_REQUEST,
            details
        );
    }
    
    /**
     * Create exception for invalid operation
     */
    public static WalletServiceException invalidOperation(String walletId, String operation, String reason) {
        Map<String, Object> details = new HashMap<>();
        details.put("operation", operation);
        details.put("reason", reason);
        
        return new WalletServiceException(
            String.format("Invalid operation '%s' for wallet %s: %s", operation, walletId, reason),
            "INVALID_OPERATION",
            HttpStatus.BAD_REQUEST,
            walletId,
            ErrorCategory.INVALID_OPERATION,
            details
        );
    }
    
    /**
     * Create exception for service unavailable
     */
    public static WalletServiceException serviceUnavailable(String reason) {
        return new WalletServiceException(
            String.format("Wallet service unavailable: %s", reason),
            "SERVICE_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE,
            null,
            ErrorCategory.SERVICE_UNAVAILABLE,
            null
        );
    }
    
    /**
     * Generate unique correlation ID for tracking
     */
    private String generateCorrelationId() {
        return String.format("WALLET-%d-%s", 
            System.currentTimeMillis(), 
            java.util.UUID.randomUUID().toString().substring(0, 8));
    }
    
    /**
     * Convert to error response map
     */
    public Map<String, Object> toErrorResponse() {
        Map<String, Object> response = new HashMap<>();
        response.put("error", errorCode);
        response.put("message", getMessage());
        response.put("timestamp", timestamp);
        response.put("correlationId", correlationId);
        response.put("category", category.name());
        
        if (walletId != null) {
            response.put("walletId", walletId);
        }
        
        if (!details.isEmpty()) {
            response.put("details", details);
        }
        
        if (getCause() != null) {
            response.put("cause", getCause().getMessage());
        }
        
        return response;
    }
    
    @Override
    public String toString() {
        return String.format("WalletServiceException{errorCode='%s', category=%s, walletId='%s', message='%s', correlationId='%s'}",
            errorCode, category, walletId, getMessage(), correlationId);
    }
}