package com.waqiti.common.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.logging.LogContext.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Structured logging service for consistent log formatting across all services
 * 
 * Provides JSON-structured logging with:
 * - Consistent field naming and structure
 * - Automatic correlation ID generation
 * - Business context enrichment
 * - Performance metrics integration
 * - Security event logging
 * - Compliance audit trails
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StructuredLogger {

    private final ObjectMapper objectMapper;
    private final Logger auditLogger = LoggerFactory.getLogger("AUDIT");
    private final Logger securityLogger = LoggerFactory.getLogger("SECURITY");
    private final Logger performanceLogger = LoggerFactory.getLogger("PERFORMANCE");
    private final Logger businessLogger = LoggerFactory.getLogger("BUSINESS");

    // MDC Keys
    public static final String CORRELATION_ID = "correlationId";
    public static final String USER_ID = "userId";
    public static final String SESSION_ID = "sessionId";
    public static final String REQUEST_ID = "requestId";
    public static final String SERVICE_NAME = "serviceName";
    public static final String OPERATION = "operation";

    /**
     * Initialize logging context for a new request
     */
    public void initializeContext(String serviceName, String operation, String userId) {
        MDC.put(CORRELATION_ID, generateCorrelationId());
        MDC.put(SERVICE_NAME, serviceName);
        MDC.put(OPERATION, operation);
        if (userId != null) {
            MDC.put(USER_ID, userId);
        }
    }

    /**
     * Clear logging context
     */
    public void clearContext() {
        MDC.clear();
    }

    /**
     * Set correlation ID for distributed tracing
     */
    public void setCorrelationId(String correlationId) {
        MDC.put(CORRELATION_ID, correlationId);
    }

    /**
     * Set user context
     */
    public void setUserContext(String userId, String sessionId) {
        MDC.put(USER_ID, userId);
        if (sessionId != null) {
            MDC.put(SESSION_ID, sessionId);
        }
    }

    /**
     * Log payment transaction events
     */
    public void logPaymentTransaction(PaymentLogContext context) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", "payment_transaction");
        logData.put("transaction_id", context.getTransactionId());
        logData.put("user_id", context.getUserId());
        logData.put("amount", context.getAmount());
        logData.put("currency", context.getCurrency());
        logData.put("payment_method", context.getPaymentMethod());
        logData.put("status", context.getStatus());
        logData.put("processing_time_ms", context.getProcessingTimeMs());
        logData.put("timestamp", Instant.now().toString());
        
        if (context.getErrorCode() != null) {
            logData.put("error_code", context.getErrorCode());
            logData.put("error_message", context.getErrorMessage());
        }

        String message = formatLogMessage("Payment transaction processed", logData);
        
        if ("failed".equals(context.getStatus())) {
            log.error(message);
        } else {
            log.info(message);
        }

        // Also log to business logger for analytics
        businessLogger.info(message);
    }

    /**
     * Log fraud detection events
     */
    public void logFraudDetection(FraudLogContext context) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", "fraud_detection");
        logData.put("transaction_id", context.getTransactionId());
        logData.put("user_id", context.getUserId());
        logData.put("fraud_type", context.getFraudType());
        logData.put("risk_score", context.getRiskScore());
        logData.put("rules_triggered", context.getRulesTriggered());
        logData.put("action_taken", context.getActionTaken());
        logData.put("ml_model_version", context.getMlModelVersion());
        logData.put("timestamp", Instant.now().toString());

        String message = formatLogMessage("Fraud detection event", logData);
        log.warn(message);
        securityLogger.warn(message);
    }

    /**
     * Log authentication events
     */
    public void logAuthenticationEvent(AuthLogContext context) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", "authentication");
        logData.put("user_id", context.getUserId());
        logData.put("username", context.getUsername());
        logData.put("auth_method", context.getAuthMethod());
        logData.put("auth_result", context.getAuthResult());
        logData.put("client_ip", context.getClientIp());
        logData.put("user_agent", context.getUserAgent());
        logData.put("biometric_used", context.isBiometricUsed());
        logData.put("session_id", context.getSessionId());
        logData.put("timestamp", Instant.now().toString());

        if (context.getFailureReason() != null) {
            logData.put("failure_reason", context.getFailureReason());
        }

        String message = formatLogMessage("Authentication event", logData);
        
        if ("success".equals(context.getAuthResult())) {
            log.info(message);
        } else {
            log.warn(message);
        }

        securityLogger.info(message);
    }

    /**
     * Log API access events
     */
    public void logApiAccess(ApiLogContext context) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", "api_access");
        logData.put("endpoint", context.getEndpoint());
        logData.put("http_method", context.getHttpMethod());
        logData.put("response_code", context.getResponseCode());
        logData.put("response_time_ms", context.getResponseTimeMs());
        logData.put("request_size", context.getRequestSize());
        logData.put("response_size", context.getResponseSize());
        logData.put("client_ip", context.getClientIp());
        logData.put("user_agent", context.getUserAgent());
        logData.put("user_id", context.getUserId());
        logData.put("timestamp", Instant.now().toString());

        String message = formatLogMessage("API access", logData);
        
        if (context.getResponseCode() >= 500) {
            log.error(message);
        } else if (context.getResponseCode() >= 400) {
            log.warn(message);
        } else {
            log.info(message);
        }

        performanceLogger.info(message);
    }

    /**
     * Log security events
     */
    public void logSecurityEvent(SecurityLogContext context) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", "security_event");
        logData.put("security_event_type", context.getEventType());
        logData.put("severity", context.getSeverity());
        logData.put("user_id", context.getUserId());
        logData.put("client_ip", context.getClientIp());
        logData.put("description", context.getDescription());
        logData.put("action_taken", context.getActionTaken());
        logData.put("timestamp", Instant.now().toString());

        String message = formatLogMessage("Security event", logData);
        
        switch (context.getSeverity().toLowerCase()) {
            case "critical":
                log.error(message);
                break;
            case "high":
                log.warn(message);
                break;
            default:
                log.info(message);
        }

        securityLogger.warn(message);
    }

    /**
     * Log audit events for compliance
     */
    public void logAuditEvent(AuditLogContext context) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", "audit_event");
        logData.put("audit_action", context.getAction());
        logData.put("resource_type", context.getResourceType());
        logData.put("resource_id", context.getResourceId());
        logData.put("user_id", context.getUserId());
        logData.put("user_role", context.getUserRole());
        logData.put("old_values", context.getOldValues());
        logData.put("new_values", context.getNewValues());
        logData.put("result", context.getResult());
        logData.put("timestamp", Instant.now().toString());

        String message = formatLogMessage("Audit event", logData);
        auditLogger.info(message);
    }

    /**
     * Log performance metrics
     */
    public void logPerformanceMetric(PerformanceLogContext context) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", "performance_metric");
        logData.put("operation", context.getOperation());
        logData.put("duration_ms", context.getDurationMs());
        logData.put("cpu_usage", context.getCpuUsage());
        logData.put("memory_usage", context.getMemoryUsage());
        logData.put("database_queries", context.getDatabaseQueries());
        logData.put("external_api_calls", context.getExternalApiCalls());
        logData.put("cache_hits", context.getCacheHits());
        logData.put("cache_misses", context.getCacheMisses());
        logData.put("timestamp", Instant.now().toString());

        String message = formatLogMessage("Performance metric", logData);
        performanceLogger.info(message);
    }

    /**
     * Log business events
     */
    public void logBusinessEvent(BusinessLogContext context) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", "business_event");
        logData.put("business_event_type", context.getEventType());
        logData.put("user_id", context.getUserId());
        logData.put("revenue_impact", context.getRevenueImpact());
        logData.put("customer_segment", context.getCustomerSegment());
        logData.put("feature_used", context.getFeatureUsed());
        logData.put("conversion_step", context.getConversionStep());
        logData.put("metadata", context.getMetadata());
        logData.put("timestamp", Instant.now().toString());

        String message = formatLogMessage("Business event", logData);
        businessLogger.info(message);
    }

    /**
     * Log database operations
     */
    public void logDatabaseOperation(DatabaseLogContext context) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", "database_operation");
        logData.put("operation_type", context.getOperationType());
        logData.put("table_name", context.getTableName());
        logData.put("query_time_ms", context.getQueryTimeMs());
        logData.put("rows_affected", context.getRowsAffected());
        logData.put("connection_pool_size", context.getConnectionPoolSize());
        logData.put("is_slow_query", context.isSlowQuery());
        logData.put("timestamp", Instant.now().toString());

        if (context.getSqlQuery() != null && context.getSqlQuery().length() < 500) {
            logData.put("sql_query", context.getSqlQuery());
        }

        String message = formatLogMessage("Database operation", logData);
        
        if (context.isSlowQuery()) {
            log.warn(message);
        } else {
            log.debug(message);
        }

        performanceLogger.debug(message);
    }

    /**
     * Log errors with context
     */
    public void logError(String errorMessage, Throwable throwable, Map<String, Object> context) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("event_type", "error");
        logData.put("error_message", errorMessage);
        logData.put("exception_class", throwable.getClass().getSimpleName());
        logData.put("stack_trace", getStackTrace(throwable));
        logData.put("timestamp", Instant.now().toString());
        
        if (context != null) {
            logData.putAll(context);
        }

        String message = formatLogMessage("Error occurred", logData);
        log.error(message, throwable);
    }

    // Helper methods

    private String formatLogMessage(String message, Map<String, Object> data) {
        try {
            Map<String, Object> logEntry = new HashMap<>();
            logEntry.put("message", message);
            logEntry.put("data", data);
            logEntry.put("mdc", MDC.getCopyOfContextMap());
            
            return objectMapper.writeValueAsString(logEntry);
        } catch (Exception e) {
            log.error("Failed to format log message as JSON", e);
            return message + " " + data.toString();
        }
    }

    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }

    private String getStackTrace(Throwable throwable) {
        if (throwable == null) return null;
        
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.getClass().getSimpleName()).append(": ").append(throwable.getMessage());
        
        for (StackTraceElement element : throwable.getStackTrace()) {
            if (element.getClassName().startsWith("com.waqiti")) {
                sb.append("\n\tat ").append(element.toString());
            }
        }
        
        return sb.toString();
    }
}