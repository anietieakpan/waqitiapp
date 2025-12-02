package com.waqiti.common.exception;

import lombok.Getter;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base exception class for all Waqiti business exceptions
 */
@Getter
public class WaqitiException extends Exception {
    
    private final String errorId;
    private final ErrorCode errorCode;
    private final String userMessage;
    private final Map<String, Object> metadata;
    private final LocalDateTime timestamp;
    
    /**
     * Create exception with error code
     */
    public WaqitiException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage(), null, null);
    }
    
    /**
     * Create exception with error code and custom message
     */
    public WaqitiException(ErrorCode errorCode, String userMessage) {
        this(errorCode, userMessage, null, null);
    }
    
    /**
     * Create exception with error code and cause
     */
    public WaqitiException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode.getDefaultMessage(), cause, null);
    }
    
    /**
     * Create exception with error code, custom message and cause
     */
    public WaqitiException(ErrorCode errorCode, String userMessage, Throwable cause) {
        this(errorCode, userMessage, cause, null);
    }
    
    /**
     * Create exception with all parameters
     */
    public WaqitiException(ErrorCode errorCode, String userMessage, Throwable cause, Map<String, Object> metadata) {
        super(buildMessage(errorCode, userMessage), cause);
        this.errorId = UUID.randomUUID().toString();
        this.errorCode = errorCode;
        this.userMessage = userMessage != null ? userMessage : errorCode.getDefaultMessage();
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        this.timestamp = LocalDateTime.now();
    }
    
    /**
     * Add metadata to the exception
     */
    public WaqitiException withMetadata(String key, Object value) {
        this.metadata.put(key, value);
        return this;
    }
    
    /**
     * Add multiple metadata entries
     */
    public WaqitiException withMetadata(Map<String, Object> additionalMetadata) {
        if (additionalMetadata != null) {
            this.metadata.putAll(additionalMetadata);
        }
        return this;
    }
    
    /**
     * Get error response for API
     */
    public ErrorResponse toErrorResponse() {
        return ErrorResponse.builder()
            .errorId(errorId)
            .error(errorCode.getCode())
            .message(userMessage)
            .timestamp(timestamp)
            .details(metadata)
            .build();
    }
    
    /**
     * Check if this is a client error (4xx)
     */
    public boolean isClientError() {
        String code = errorCode.getCode();
        return code.startsWith("AUTH_") || 
               code.startsWith("VAL_") || 
               code.startsWith("USER_") ||
               code.startsWith("RATE_") ||
               code.equals("WALLET_INSUFFICIENT_BALANCE") ||
               code.equals("PAYMENT_INVALID_AMOUNT");
    }
    
    /**
     * Check if this is a server error (5xx)
     */
    public boolean isServerError() {
        return !isClientError();
    }
    
    /**
     * Get HTTP status code for this error
     */
    public int getHttpStatusCode() {
        switch (errorCode) {
            // 400 Bad Request
            case VAL_REQUIRED_FIELD:
            case VAL_INVALID_FORMAT:
            case VAL_OUT_OF_RANGE:
            case VAL_INVALID_LENGTH:
            case VAL_PATTERN_MISMATCH:
            case PAYMENT_INVALID_AMOUNT:
            case CRYPTO_ADDRESS_INVALID:
                return 400;
                
            // 401 Unauthorized
            case AUTH_INVALID_CREDENTIALS:
            case AUTH_TOKEN_EXPIRED:
            case AUTH_TOKEN_INVALID:
                return 401;
                
            // 403 Forbidden
            case AUTH_INSUFFICIENT_PERMISSIONS:
            case AUTH_ACCOUNT_LOCKED:
            case WALLET_FROZEN:
            case CRYPTO_ADDRESS_SANCTIONED:
            case PAYMENT_COMPLIANCE_BLOCK:
                return 403;
                
            // 404 Not Found
            case USER_NOT_FOUND:
            case WALLET_NOT_FOUND:
            case PAYMENT_RECIPIENT_NOT_FOUND:
            case TXN_NOT_FOUND:
                return 404;
                
            // 409 Conflict
            case USER_ALREADY_EXISTS:
            case TXN_DUPLICATE:
            case PAYMENT_ALREADY_PROCESSED:
                return 409;
                
            // 422 Unprocessable Entity
            case USER_KYC_REQUIRED:
            case USER_KYC_PENDING:
            case USER_KYC_REJECTED:
            case BIZ_RULE_VIOLATION:
                return 422;
                
            // 429 Too Many Requests
            case RATE_LIMIT_EXCEEDED:
            case RATE_BURST_LIMIT_EXCEEDED:
            case RATE_DAILY_LIMIT_EXCEEDED:
                return 429;
                
            // 503 Service Unavailable
            case INT_SERVICE_UNAVAILABLE:
            case SYS_MAINTENANCE_MODE:
                return 503;
                
            // 504 Gateway Timeout
            case INT_TIMEOUT:
                return 504;
                
            // 500 Internal Server Error (default)
            default:
                return 500;
        }
    }
    
    private static String buildMessage(ErrorCode errorCode, String userMessage) {
        return String.format("[%s] %s", errorCode.getCode(), 
            userMessage != null ? userMessage : errorCode.getDefaultMessage());
    }
}