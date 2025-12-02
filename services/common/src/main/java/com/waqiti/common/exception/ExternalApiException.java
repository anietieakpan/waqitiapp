package com.waqiti.common.exception;

/**
 * Exception thrown when external API calls fail.
 * This is a recoverable exception that should trigger circuit breaker and retry logic.
 */
public class ExternalApiException extends RuntimeException {

    private final String apiName;
    private final int statusCode;
    private final String errorCode;

    public ExternalApiException(String message) {
        super(message);
        this.apiName = null;
        this.statusCode = 0;
        this.errorCode = null;
    }

    public ExternalApiException(String message, Throwable cause) {
        super(message, cause);
        this.apiName = null;
        this.statusCode = 0;
        this.errorCode = null;
    }

    public ExternalApiException(String message, String apiName, int statusCode) {
        super(message);
        this.apiName = apiName;
        this.statusCode = statusCode;
        this.errorCode = null;
    }

    public ExternalApiException(String message, String apiName, int statusCode, String errorCode) {
        super(message);
        this.apiName = apiName;
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    public ExternalApiException(String message, String apiName, int statusCode, Throwable cause) {
        super(message, cause);
        this.apiName = apiName;
        this.statusCode = statusCode;
        this.errorCode = null;
    }

    public String getApiName() {
        return apiName;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
