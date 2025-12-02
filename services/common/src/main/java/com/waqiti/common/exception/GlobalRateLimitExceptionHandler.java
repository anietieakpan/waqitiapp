package com.waqiti.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler for rate limit exceptions
 */
@Slf4j
@RestControllerAdvice
public class GlobalRateLimitExceptionHandler {

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Object> handleRateLimitExceeded(
            RateLimitExceededException ex, WebRequest request) {
        
        log.warn("Rate limit exceeded: {}", ex.getMessage());
        
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("error", "Too Many Requests");
        body.put("message", ex.getMessage());
        body.put("path", request.getDescription(false).substring(4)); // Remove "uri=" prefix
        
        HttpHeaders headers = new HttpHeaders();
        
        // Add Retry-After header if wait time is available
        if (ex.getWaitTimeSeconds() > 0) {
            headers.add("Retry-After", String.valueOf(ex.getWaitTimeSeconds()));
            body.put("retryAfter", ex.getWaitTimeSeconds());
        }
        
        // Add rate limit headers
        headers.add("X-RateLimit-Limit", "See API documentation");
        headers.add("X-RateLimit-Remaining", "0");
        headers.add("X-RateLimit-Reset", String.valueOf(Instant.now().plusSeconds(ex.getWaitTimeSeconds()).getEpochSecond()));
        
        return new ResponseEntity<>(body, headers, HttpStatus.TOO_MANY_REQUESTS);
    }
}