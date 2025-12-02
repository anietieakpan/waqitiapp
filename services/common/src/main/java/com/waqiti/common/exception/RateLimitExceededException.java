package com.waqiti.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when rate limit is exceeded
 */
@Getter
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class RateLimitExceededException extends RuntimeException {
    
    private final long waitTimeSeconds;
    private final long limit;
    private final long remainingLimit;
    private final long resetTime;
    
    public RateLimitExceededException(String message) {
        super(message);
        this.waitTimeSeconds = 0;
        this.limit = 0;
        this.remainingLimit = 0;
        this.resetTime = System.currentTimeMillis() / 1000;
    }
    
    public RateLimitExceededException(String message, long waitTimeSeconds) {
        super(message);
        this.waitTimeSeconds = waitTimeSeconds;
        this.limit = 0;
        this.remainingLimit = 0;
        this.resetTime = System.currentTimeMillis() / 1000;
    }
    
    public RateLimitExceededException(String message, long waitTimeSeconds, long limit, long remainingLimit, long resetTime) {
        super(message);
        this.waitTimeSeconds = waitTimeSeconds;
        this.limit = limit;
        this.remainingLimit = remainingLimit;
        this.resetTime = resetTime;
    }
    
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
        this.waitTimeSeconds = 0;
        this.limit = 0;
        this.remainingLimit = 0;
        this.resetTime = System.currentTimeMillis() / 1000;
    }
    
    public RateLimitExceededException(String message, long waitTimeSeconds, Throwable cause) {
        super(message, cause);
        this.waitTimeSeconds = waitTimeSeconds;
        this.limit = 0;
        this.remainingLimit = 0;
        this.resetTime = System.currentTimeMillis() / 1000;
    }
    
    public long getRetryAfter() {
        return waitTimeSeconds;
    }
}