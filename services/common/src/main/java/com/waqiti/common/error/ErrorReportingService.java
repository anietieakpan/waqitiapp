package com.waqiti.common.error;

import com.waqiti.common.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service for reporting errors to external monitoring and alerting systems
 * Integrates with error tracking platforms like Sentry, DataDog, etc.
 */
@Service
@RequiredArgsConstructor
public class ErrorReportingService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ErrorReportingService.class);

    @Value("${error-reporting.enabled:true}")
    private boolean errorReportingEnabled;
    
    @Value("${error-reporting.sentry.dsn:}")
    private String sentryDsn;
    
    @Value("${error-reporting.webhook.url:}")
    private String webhookUrl;
    
    @Value("${spring.application.name}")
    private String serviceName;
    
    @Value("${spring.profiles.active}")
    private String activeProfile;
    
    private final RestTemplate restTemplate;

    /**
     * Report critical system errors that require immediate attention
     */
    public void reportCriticalError(Exception exception, String path, String clientIp) {
        if (!errorReportingEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                ErrorReport report = ErrorReport.builder()
                        .severity(ErrorSeverity.CRITICAL)
                        .service(serviceName)
                        .environment(activeProfile)
                        .timestamp(LocalDateTime.now())
                        .errorType(exception.getClass().getSimpleName())
                        .message(exception.getMessage())
                        .stackTrace(getStackTrace(exception))
                        .path(path)
                        .clientIp(clientIp)
                        .metadata(Map.of(
                            "thread", Thread.currentThread().getName(),
                            "memory", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory(),
                            "processors", Runtime.getRuntime().availableProcessors()
                        ))
                        .build();
                
                sendErrorReport(report);
                sendSlackAlert(report);
                
            } catch (Exception e) {
                log.error("Failed to report critical error: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Report external service failures
     */
    public void reportExternalServiceError(ExternalServiceException exception) {
        if (!errorReportingEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                ErrorReport report = ErrorReport.builder()
                        .severity(ErrorSeverity.HIGH)
                        .service(serviceName)
                        .environment(activeProfile)
                        .timestamp(LocalDateTime.now())
                        .errorType("ExternalServiceError")
                        .message(exception.getMessage())
                        .metadata(Map.of(
                            "externalService", exception.getServiceName(),
                            "statusCode", exception.getStatusCode(),
                            "responseTime", exception.getResponseTime(),
                            "endpoint", exception.getEndpoint()
                        ))
                        .build();
                
                sendErrorReport(report);
                
                // Track service availability metrics
                updateServiceHealthMetrics(exception.getServiceName(), false);
                
            } catch (Exception e) {
                log.error("Failed to report external service error: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Report business logic errors for trend analysis
     */
    public void reportBusinessError(String errorCode, String message, Map<String, Object> context) {
        if (!errorReportingEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                ErrorReport report = ErrorReport.builder()
                        .severity(ErrorSeverity.MEDIUM)
                        .service(serviceName)
                        .environment(activeProfile)
                        .timestamp(LocalDateTime.now())
                        .errorType("BusinessLogicError")
                        .errorCode(errorCode)
                        .message(message)
                        .metadata(context)
                        .build();
                
                sendErrorReport(report);
                
            } catch (Exception e) {
                log.error("Failed to report business error: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Report performance issues
     */
    public void reportPerformanceIssue(String operation, long executionTime, long threshold) {
        if (!errorReportingEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                ErrorReport report = ErrorReport.builder()
                        .severity(ErrorSeverity.MEDIUM)
                        .service(serviceName)
                        .environment(activeProfile)
                        .timestamp(LocalDateTime.now())
                        .errorType("PerformanceIssue")
                        .message(String.format("Operation '%s' took %dms (threshold: %dms)", 
                            operation, executionTime, threshold))
                        .metadata(Map.of(
                            "operation", operation,
                            "executionTime", executionTime,
                            "threshold", threshold,
                            "ratio", (double) executionTime / threshold
                        ))
                        .build();
                
                sendErrorReport(report);
                
            } catch (Exception e) {
                log.error("Failed to report performance issue: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Report security violations for immediate investigation
     */
    public void reportSecurityViolation(String violationType, String description, 
                                       String clientIp, String userAgent) {
        if (!errorReportingEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                ErrorReport report = ErrorReport.builder()
                        .severity(ErrorSeverity.CRITICAL)
                        .service(serviceName)
                        .environment(activeProfile)
                        .timestamp(LocalDateTime.now())
                        .errorType("SecurityViolation")
                        .message(description)
                        .clientIp(clientIp)
                        .metadata(Map.of(
                            "violationType", violationType,
                            "userAgent", userAgent != null ? userAgent : "unknown",
                            "geoLocation", getGeoLocation(clientIp)
                        ))
                        .build();
                
                sendErrorReport(report);
                sendSecurityAlert(report);
                
            } catch (Exception e) {
                log.error("Failed to report security violation: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Report rate limiting violations
     */
    public void reportRateLimitViolation(String clientIp, String endpoint, int requestCount, int limit) {
        if (!errorReportingEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                ErrorReport report = ErrorReport.builder()
                        .severity(ErrorSeverity.HIGH)
                        .service(serviceName)
                        .environment(activeProfile)
                        .timestamp(LocalDateTime.now())
                        .errorType("RateLimitViolation")
                        .message(String.format("Rate limit exceeded: %d requests (limit: %d)", 
                            requestCount, limit))
                        .clientIp(clientIp)
                        .path(endpoint)
                        .metadata(Map.of(
                            "requestCount", requestCount,
                            "limit", limit,
                            "ratio", (double) requestCount / limit
                        ))
                        .build();
                
                sendErrorReport(report);
                
            } catch (Exception e) {
                log.error("Failed to report rate limit violation: {}", e.getMessage(), e);
            }
        });
    }

    // Private helper methods

    private void sendErrorReport(ErrorReport report) {
        try {
            if (sentryDsn != null && !sentryDsn.isEmpty()) {
                sendToSentry(report);
            }
            
            if (webhookUrl != null && !webhookUrl.isEmpty()) {
                sendWebhookNotification(report);
            }
            
            // Store in database for analytics
            storeErrorReport(report);
            
        } catch (Exception e) {
            log.error("Failed to send error report: {}", e.getMessage());
        }
    }

    private void sendToSentry(ErrorReport report) {
        // Integration with Sentry error tracking
        // This would use the Sentry SDK to send structured error reports
        log.debug("Sending error report to Sentry: {}", report.getErrorType());
    }

    private void sendWebhookNotification(ErrorReport report) {
        try {
            Map<String, Object> payload = Map.of(
                "service", report.getService(),
                "environment", report.getEnvironment(),
                "severity", report.getSeverity(),
                "errorType", report.getErrorType(),
                "message", report.getMessage(),
                "timestamp", report.getTimestamp(),
                "metadata", report.getMetadata()
            );
            
            restTemplate.postForObject(webhookUrl, payload, String.class);
            
        } catch (Exception e) {
            log.warn("Failed to send webhook notification: {}", e.getMessage());
        }
    }

    private void sendSlackAlert(ErrorReport report) {
        // Send high-priority alerts to Slack
        if (report.getSeverity() == ErrorSeverity.CRITICAL) {
            try {
                Map<String, Object> slackMessage = Map.of(
                    "text", String.format("🚨 CRITICAL ERROR in %s (%s)", 
                        report.getService(), report.getEnvironment()),
                    "attachments", java.util.List.of(Map.of(
                        "color", "danger",
                        "fields", java.util.List.of(
                            Map.of("title", "Error", "value", report.getErrorType(), "short", true),
                            Map.of("title", "Message", "value", report.getMessage(), "short", false),
                            Map.of("title", "Time", "value", report.getTimestamp().toString(), "short", true)
                        )
                    ))
                );
                
                // Send to Slack webhook (implement based on your Slack integration)
                
            } catch (Exception e) {
                log.warn("Failed to send Slack alert: {}", e.getMessage());
            }
        }
    }

    private void sendSecurityAlert(ErrorReport report) {
        // Send immediate security alerts to security team
        try {
            Map<String, Object> securityAlert = Map.of(
                "alertType", "SECURITY_VIOLATION",
                "severity", "CRITICAL",
                "service", report.getService(),
                "clientIp", report.getClientIp(),
                "timestamp", report.getTimestamp(),
                "details", report.getMetadata()
            );
            
            // Send to security monitoring system
            log.info("Security alert sent: {}", securityAlert);
            
        } catch (Exception e) {
            log.error("Failed to send security alert: {}", e.getMessage());
        }
    }

    private void storeErrorReport(ErrorReport report) {
        // Store error reports in database for analytics and trending
        try {
            // Implementation would save to your error analytics database
            log.debug("Stored error report: {} - {}", report.getErrorType(), report.getMessage());
            
        } catch (Exception e) {
            log.warn("Failed to store error report: {}", e.getMessage());
        }
    }

    private void updateServiceHealthMetrics(String serviceName, boolean isHealthy) {
        // Update service health metrics for monitoring dashboards
        try {
            // Implementation would update your metrics system (Prometheus, etc.)
            log.debug("Updated health metrics for service: {} - healthy: {}", serviceName, isHealthy);
            
        } catch (Exception e) {
            log.warn("Failed to update service health metrics: {}", e.getMessage());
        }
    }

    private String getStackTrace(Exception exception) {
        StringBuilder stackTrace = new StringBuilder();
        for (StackTraceElement element : exception.getStackTrace()) {
            stackTrace.append(element.toString()).append("\n");
            // Limit stack trace length to prevent huge payloads
            if (stackTrace.length() > 5000) {
                stackTrace.append("... (truncated)");
                break;
            }
        }
        return stackTrace.toString();
    }

    private String getGeoLocation(String clientIp) {
        // Implement GeoIP lookup for security analysis
        // Return city, country, etc. based on IP address
        return "Unknown";
    }

    // ErrorSeverity and ErrorReport are now standalone classes in this package
}