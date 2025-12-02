package com.waqiti.common.tracing;

import io.micrometer.tracing.Span;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Provides utilities for customizing spans with additional context and metadata.
 */
@Component
@Slf4j
public class SpanCustomizer {
    
    /**
     * Adds user context to the current span.
     *
     * @param span The span to customize
     * @param userId The user ID
     * @param username The username
     * @param roles User roles
     */
    public void addUserContext(Span span, String userId, String username, String roles) {
        if (span == null) {
            log.warn("Cannot add user context to null span");
            return;
        }
        
        span.tag("user.id", userId != null ? userId : "anonymous");
        span.tag("user.name", username != null ? username : "unknown");
        span.tag("user.roles", roles != null ? roles : "none");
    }
    
    /**
     * Adds request context to the current span.
     *
     * @param span The span to customize
     * @param method HTTP method
     * @param path Request path
     * @param queryParams Query parameters
     */
    public void addRequestContext(Span span, String method, String path, Map<String, String> queryParams) {
        if (span == null) {
            log.warn("Cannot add request context to null span");
            return;
        }
        
        span.tag("http.method", method);
        span.tag("http.path", path);
        
        if (queryParams != null && !queryParams.isEmpty()) {
            span.tag("http.query", sanitizeQueryParams(queryParams));
        }
    }
    
    /**
     * Adds database context to the current span.
     *
     * @param span The span to customize
     * @param dbType Database type (e.g., postgresql, mysql)
     * @param operation Database operation (e.g., SELECT, INSERT)
     * @param table Table name
     */
    public void addDatabaseContext(Span span, String dbType, String operation, String table) {
        if (span == null) {
            log.warn("Cannot add database context to null span");
            return;
        }
        
        span.tag("db.type", dbType);
        span.tag("db.operation", operation);
        span.tag("db.table", table);
    }
    
    /**
     * Adds business context to the current span.
     *
     * @param span The span to customize
     * @param transactionId Business transaction ID
     * @param accountId Account ID
     * @param amount Transaction amount
     * @param currency Currency code
     */
    public void addBusinessContext(Span span, String transactionId, String accountId, 
                                  String amount, String currency) {
        if (span == null) {
            log.warn("Cannot add business context to null span");
            return;
        }
        
        if (transactionId != null) span.tag("business.transaction.id", transactionId);
        if (accountId != null) span.tag("business.account.id", accountId);
        if (amount != null) span.tag("business.amount", amount);
        if (currency != null) span.tag("business.currency", currency);
    }
    
    /**
     * Adds error context to the current span.
     *
     * @param span The span to customize
     * @param errorCode Application-specific error code
     * @param errorCategory Error category (e.g., validation, authorization)
     * @param recoverable Whether the error is recoverable
     */
    public void addErrorContext(Span span, String errorCode, String errorCategory, boolean recoverable) {
        if (span == null) {
            log.warn("Cannot add error context to null span");
            return;
        }
        
        span.tag("error.code", errorCode);
        span.tag("error.category", errorCategory);
        span.tag("error.recoverable", String.valueOf(recoverable));
    }
    
    /**
     * Adds performance context to the current span.
     *
     * @param span The span to customize
     * @param cacheHit Whether the request hit cache
     * @param dbQueries Number of database queries
     * @param externalCalls Number of external API calls
     */
    public void addPerformanceContext(Span span, boolean cacheHit, int dbQueries, int externalCalls) {
        if (span == null) {
            log.warn("Cannot add performance context to null span");
            return;
        }
        
        span.tag("performance.cache.hit", String.valueOf(cacheHit));
        span.tag("performance.db.queries", String.valueOf(dbQueries));
        span.tag("performance.external.calls", String.valueOf(externalCalls));
    }
    
    /**
     * Sanitizes query parameters to remove sensitive information.
     */
    private String sanitizeQueryParams(Map<String, String> queryParams) {
        StringBuilder sb = new StringBuilder();
        queryParams.forEach((key, value) -> {
            if (sb.length() > 0) sb.append("&");
            sb.append(key).append("=");
            
            // Mask sensitive parameters
            if (isSensitiveParam(key)) {
                sb.append("***");
            } else {
                sb.append(value);
            }
        });
        return sb.toString();
    }
    
    private boolean isSensitiveParam(String paramName) {
        String lower = paramName.toLowerCase();
        return lower.contains("password") || 
               lower.contains("secret") || 
               lower.contains("token") || 
               lower.contains("key") || 
               lower.contains("credential");
    }
}