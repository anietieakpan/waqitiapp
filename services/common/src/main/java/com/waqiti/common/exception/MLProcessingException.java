package com.waqiti.common.exception;

import lombok.Getter;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * Production-ready exception for ML processing errors.
 * Provides detailed error information for debugging and monitoring.
 */
@Getter
public class MLProcessingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Error code for categorizing ML errors
     */
    private final String errorCode;

    /**
     * The ML model that caused the error
     */
    private final String modelName;

    /**
     * The model version when error occurred
     */
    private final String modelVersion;

    /**
     * Error severity level
     */
    private final ErrorSeverity severity;

    /**
     * Whether the error is recoverable
     */
    private final boolean recoverable;

    /**
     * Timestamp when error occurred
     */
    private final LocalDateTime timestamp;

    /**
     * Additional context about the error
     */
    private final Map<String, Object> context;

    /**
     * Transaction or request ID for tracing
     */
    private final String traceId;

    /**
     * User ID if applicable
     */
    private final String userId;

    /**
     * Error severity levels
     */
    public enum ErrorSeverity {
        LOW,      // Minor issue, can continue
        MEDIUM,   // Some functionality affected
        HIGH,     // Significant impact
        CRITICAL  // System failure
    }

    /**
     * Common ML error codes
     */
    public static class ErrorCodes {
        public static final String MODEL_NOT_FOUND = "ML001";
        public static final String MODEL_LOADING_FAILED = "ML002";
        public static final String INFERENCE_FAILED = "ML003";
        public static final String FEATURE_EXTRACTION_FAILED = "ML004";
        public static final String INVALID_INPUT_DATA = "ML005";
        public static final String MODEL_VERSION_MISMATCH = "ML006";
        public static final String TIMEOUT_ERROR = "ML007";
        public static final String RESOURCE_EXHAUSTED = "ML008";
        public static final String DATA_PREPROCESSING_FAILED = "ML009";
        public static final String MODEL_VALIDATION_FAILED = "ML010";
        public static final String CONFIGURATION_ERROR = "ML011";
        public static final String SERIALIZATION_ERROR = "ML012";
        public static final String NETWORK_ERROR = "ML013";
        public static final String PERMISSION_DENIED = "ML014";
        public static final String QUOTA_EXCEEDED = "ML015";
    }

    /**
     * Constructor with basic parameters
     */
    public MLProcessingException(String message) {
        this(message, ErrorCodes.INFERENCE_FAILED, null);
    }

    /**
     * Constructor with message and cause
     */
    public MLProcessingException(String message, Throwable cause) {
        this(message, ErrorCodes.INFERENCE_FAILED, cause);
    }

    /**
     * Constructor with message and error code
     */
    public MLProcessingException(String message, String errorCode) {
        this(message, errorCode, null);
    }

    /**
     * Constructor with message, error code, and cause
     */
    public MLProcessingException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.modelName = null;
        this.modelVersion = null;
        this.severity = determineSeverity(errorCode);
        this.recoverable = isRecoverable(errorCode);
        this.timestamp = LocalDateTime.now();
        this.context = new HashMap<>();
        this.traceId = null;
        this.userId = null;
    }

    /**
     * Full constructor with all parameters
     */
    public MLProcessingException(String message, 
                                String errorCode,
                                String modelName,
                                String modelVersion,
                                ErrorSeverity severity,
                                boolean recoverable,
                                Map<String, Object> context,
                                String traceId,
                                String userId,
                                Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.severity = severity != null ? severity : determineSeverity(errorCode);
        this.recoverable = recoverable;
        this.timestamp = LocalDateTime.now();
        this.context = context != null ? new HashMap<>(context) : new HashMap<>();
        this.traceId = traceId;
        this.userId = userId;
    }

    /**
     * Builder for creating MLProcessingException
     */
    public static class Builder {
        private String message;
        private String errorCode = ErrorCodes.INFERENCE_FAILED;
        private String modelName;
        private String modelVersion;
        private ErrorSeverity severity;
        private boolean recoverable = false;
        private Map<String, Object> context = new HashMap<>();
        private String traceId;
        private String userId;
        private Throwable cause;

        public Builder(String message) {
            this.message = message;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder modelName(String modelName) {
            this.modelName = modelName;
            return this;
        }

        public Builder modelVersion(String modelVersion) {
            this.modelVersion = modelVersion;
            return this;
        }

        public Builder severity(ErrorSeverity severity) {
            this.severity = severity;
            return this;
        }

        public Builder recoverable(boolean recoverable) {
            this.recoverable = recoverable;
            return this;
        }

        public Builder context(Map<String, Object> context) {
            this.context = context;
            return this;
        }

        public Builder addContext(String key, Object value) {
            this.context.put(key, value);
            return this;
        }

        public Builder traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public MLProcessingException build() {
            return new MLProcessingException(
                message, errorCode, modelName, modelVersion,
                severity, recoverable, context, traceId, userId, cause
            );
        }
    }

    /**
     * Create a builder for this exception
     */
    public static Builder builder(String message) {
        return new Builder(message);
    }

    /**
     * Determine severity based on error code
     */
    private static ErrorSeverity determineSeverity(String errorCode) {
        if (errorCode == null) return ErrorSeverity.MEDIUM;
        
        switch (errorCode) {
            case ErrorCodes.MODEL_NOT_FOUND:
            case ErrorCodes.MODEL_LOADING_FAILED:
            case ErrorCodes.RESOURCE_EXHAUSTED:
                return ErrorSeverity.CRITICAL;
            
            case ErrorCodes.INFERENCE_FAILED:
            case ErrorCodes.TIMEOUT_ERROR:
            case ErrorCodes.MODEL_VERSION_MISMATCH:
                return ErrorSeverity.HIGH;
            
            case ErrorCodes.FEATURE_EXTRACTION_FAILED:
            case ErrorCodes.DATA_PREPROCESSING_FAILED:
            case ErrorCodes.INVALID_INPUT_DATA:
                return ErrorSeverity.MEDIUM;
            
            default:
                return ErrorSeverity.LOW;
        }
    }

    /**
     * Determine if error is recoverable based on error code
     */
    private static boolean isRecoverable(String errorCode) {
        if (errorCode == null) return false;
        
        switch (errorCode) {
            case ErrorCodes.TIMEOUT_ERROR:
            case ErrorCodes.NETWORK_ERROR:
            case ErrorCodes.RESOURCE_EXHAUSTED:
            case ErrorCodes.QUOTA_EXCEEDED:
                return true;
            
            case ErrorCodes.MODEL_NOT_FOUND:
            case ErrorCodes.MODEL_LOADING_FAILED:
            case ErrorCodes.CONFIGURATION_ERROR:
            case ErrorCodes.PERMISSION_DENIED:
                return false;
            
            default:
                return true;
        }
    }

    /**
     * Add context to the exception
     */
    public void addContext(String key, Object value) {
        if (context != null) {
            context.put(key, value);
        }
    }

    /**
     * Get formatted error message for logging
     */
    public String getFormattedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("MLProcessingException: ").append(getMessage());
        sb.append(" [Code: ").append(errorCode);
        
        if (modelName != null) {
            sb.append(", Model: ").append(modelName);
        }
        
        if (modelVersion != null) {
            sb.append(", Version: ").append(modelVersion);
        }
        
        sb.append(", Severity: ").append(severity);
        sb.append(", Recoverable: ").append(recoverable);
        
        if (traceId != null) {
            sb.append(", TraceId: ").append(traceId);
        }
        
        sb.append(", Timestamp: ").append(timestamp);
        sb.append("]");
        
        if (!context.isEmpty()) {
            sb.append(" Context: ").append(context);
        }
        
        return sb.toString();
    }

    /**
     * Get error details as a map for API responses
     */
    public Map<String, Object> toErrorDetails() {
        Map<String, Object> details = new HashMap<>();
        details.put("error_code", errorCode);
        details.put("message", getMessage());
        details.put("severity", severity.toString());
        details.put("recoverable", recoverable);
        details.put("timestamp", timestamp.toString());
        
        if (modelName != null) {
            details.put("model_name", modelName);
        }
        
        if (modelVersion != null) {
            details.put("model_version", modelVersion);
        }
        
        if (traceId != null) {
            details.put("trace_id", traceId);
        }
        
        if (!context.isEmpty()) {
            details.put("context", context);
        }
        
        return details;
    }

    /**
     * Check if this is a critical error
     */
    public boolean isCritical() {
        return severity == ErrorSeverity.CRITICAL;
    }

    /**
     * Check if retry should be attempted
     */
    public boolean shouldRetry() {
        return recoverable && severity != ErrorSeverity.CRITICAL;
    }

    /**
     * Get recommended action based on error
     */
    public String getRecommendedAction() {
        if (!recoverable) {
            return "Manual intervention required. Please contact support.";
        }
        
        switch (errorCode) {
            case ErrorCodes.TIMEOUT_ERROR:
                return "Retry with increased timeout or smaller batch size.";
            
            case ErrorCodes.RESOURCE_EXHAUSTED:
                return "Wait and retry after some time or scale up resources.";
            
            case ErrorCodes.NETWORK_ERROR:
                return "Check network connectivity and retry.";
            
            case ErrorCodes.INVALID_INPUT_DATA:
                return "Validate and correct input data format.";
            
            case ErrorCodes.MODEL_VERSION_MISMATCH:
                return "Update to compatible model version.";
            
            default:
                return "Retry operation or contact support if issue persists.";
        }
    }

    /**
     * Create exception for model not found
     */
    public static MLProcessingException modelNotFound(String modelName) {
        return builder("Model not found: " + modelName)
            .errorCode(ErrorCodes.MODEL_NOT_FOUND)
            .modelName(modelName)
            .severity(ErrorSeverity.CRITICAL)
            .recoverable(false)
            .build();
    }

    /**
     * Create exception for inference failure
     */
    public static MLProcessingException inferenceFailed(String modelName, String reason, Throwable cause) {
        return builder("Inference failed for model " + modelName + ": " + reason)
            .errorCode(ErrorCodes.INFERENCE_FAILED)
            .modelName(modelName)
            .severity(ErrorSeverity.HIGH)
            .recoverable(true)
            .cause(cause)
            .build();
    }

    /**
     * Create exception for invalid input
     */
    public static MLProcessingException invalidInput(String reason) {
        return builder("Invalid input data: " + reason)
            .errorCode(ErrorCodes.INVALID_INPUT_DATA)
            .severity(ErrorSeverity.MEDIUM)
            .recoverable(false)
            .build();
    }

    /**
     * Create exception for timeout
     */
    public static MLProcessingException timeout(String operation, long timeoutMs) {
        return builder("Operation timed out: " + operation)
            .errorCode(ErrorCodes.TIMEOUT_ERROR)
            .severity(ErrorSeverity.HIGH)
            .recoverable(true)
            .addContext("operation", operation)
            .addContext("timeout_ms", timeoutMs)
            .build();
    }
}