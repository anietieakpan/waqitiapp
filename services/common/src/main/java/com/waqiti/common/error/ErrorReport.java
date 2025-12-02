package com.waqiti.common.error;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;

/**
 * Comprehensive error report for tracking, analysis, and alerting
 * in the Waqiti platform with enterprise-grade error intelligence
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorReport {
    
    /**
     * Report identification and metadata
     */
    private String reportId;
    private LocalDateTime timestamp;
    private String service;
    private String environment;
    private String version;
    private String hostname;
    
    /**
     * Error classification and details
     */
    private ErrorSeverity severity;
    private String errorType;
    private String errorCode;
    private String message;
    private String stackTrace;
    private Throwable rootCause;
    
    /**
     * Context information
     */
    private String path;
    private String httpMethod;
    private String clientIp;
    private String userAgent;
    private String userId;
    private String sessionId;
    private String correlationId;
    
    /**
     * Request and response details
     */
    private Map<String, String> requestHeaders;
    private Map<String, String> requestParams;
    private Object requestBody;
    private Integer responseStatus;
    private Long responseTime;
    
    /**
     * System state and metrics
     */
    private Map<String, Object> metadata;
    private SystemMetrics systemMetrics;
    private Map<String, String> environmentVariables;
    private List<String> activeProfiles;
    
    /**
     * Impact and business context
     */
    private String businessImpact;
    private List<String> affectedFeatures;
    private Integer affectedUsers;
    private Boolean customerFacing;
    private String remediation;
    
    /**
     * Tracking and analytics
     */
    private Integer occurrenceCount;
    private LocalDateTime firstOccurrence;
    private LocalDateTime lastOccurrence;
    private List<String> similarErrors;
    private String errorFingerprint;
    
    /**
     * Alerting and notification
     */
    private Boolean alertSent;
    private List<String> notifiedChannels;
    private String escalationLevel;
    private String assignedTo;
    private String ticketId;
    
    /**
     * Generate a unique error fingerprint for deduplication
     */
    public String generateFingerprint() {
        if (errorFingerprint != null) {
            return errorFingerprint;
        }
        
        StringBuilder fingerprint = new StringBuilder();
        fingerprint.append(errorType != null ? errorType : "UNKNOWN");
        fingerprint.append("|");
        fingerprint.append(errorCode != null ? errorCode : "");
        fingerprint.append("|");
        
        // Extract key parts from stack trace
        if (stackTrace != null && !stackTrace.isEmpty()) {
            String[] lines = stackTrace.split("\n");
            if (lines.length > 0) {
                // Get first meaningful line from stack trace
                for (String line : lines) {
                    if (line.contains("com.waqiti")) {
                        fingerprint.append(line.trim().replaceAll("\\s+", ""));
                        break;
                    }
                }
            }
        }
        
        this.errorFingerprint = Integer.toHexString(fingerprint.toString().hashCode());
        return errorFingerprint;
    }
    
    /**
     * Determine if this error requires immediate attention
     */
    public boolean requiresImmediateAttention() {
        return severity == ErrorSeverity.CRITICAL ||
               severity == ErrorSeverity.EMERGENCY ||
               (customerFacing != null && customerFacing) ||
               (affectedUsers != null && affectedUsers > 100);
    }
    
    /**
     * Get recommended escalation path
     */
    public String getEscalationPath() {
        if (severity == ErrorSeverity.EMERGENCY) {
            return "PAGE_ONCALL_IMMEDIATELY";
        } else if (severity == ErrorSeverity.CRITICAL) {
            return "NOTIFY_SENIOR_ENGINEERS";
        } else if (severity == ErrorSeverity.HIGH) {
            return "NOTIFY_TEAM_LEAD";
        } else if (severity == ErrorSeverity.MEDIUM) {
            return "CREATE_TICKET";
        } else {
            return "LOG_FOR_REVIEW";
        }
    }
    
    /**
     * Generate a concise summary for notifications
     */
    public String getNotificationSummary() {
        StringBuilder summary = new StringBuilder();
        
        summary.append(String.format("[%s] %s Error in %s", 
            severity, errorType, service));
        
        if (environment != null && !"production".equals(environment)) {
            summary.append(String.format(" (%s)", environment));
        }
        
        summary.append(String.format(": %s", message));
        
        if (affectedUsers != null && affectedUsers > 0) {
            summary.append(String.format(" | %d users affected", affectedUsers));
        }
        
        if (occurrenceCount != null && occurrenceCount > 1) {
            summary.append(String.format(" | Occurred %d times", occurrenceCount));
        }
        
        return summary.toString();
    }
    
    /**
     * Get formatted error details for logging
     */
    public String getFormattedDetails() {
        StringBuilder details = new StringBuilder();
        
        details.append("=== ERROR REPORT ===\n");
        details.append(String.format("Timestamp: %s\n", timestamp));
        details.append(String.format("Service: %s | Environment: %s\n", service, environment));
        details.append(String.format("Severity: %s | Type: %s | Code: %s\n", 
            severity, errorType, errorCode));
        details.append(String.format("Message: %s\n", message));
        
        if (path != null) {
            details.append(String.format("Path: %s %s\n", httpMethod, path));
        }
        
        if (clientIp != null) {
            details.append(String.format("Client: %s | User: %s\n", clientIp, userId));
        }
        
        if (stackTrace != null && !stackTrace.isEmpty()) {
            details.append("Stack Trace:\n");
            details.append(stackTrace).append("\n");
        }
        
        if (metadata != null && !metadata.isEmpty()) {
            details.append("Metadata:\n");
            metadata.forEach((key, value) -> 
                details.append(String.format("  %s: %s\n", key, value)));
        }
        
        return details.toString();
    }
    
    // Explicit getters as fallback for Lombok processing issues
    public ErrorSeverity getSeverity() { return severity; }
    public String getService() { return service; }
    public String getEnvironment() { return environment; }
    public String getErrorType() { return errorType; }
    public String getMessage() { return message; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getClientIp() { return clientIp; }
    public Map<String, Object> getMetadata() { return metadata; }
    
    /**
     * Create a minimal error report for quick logging
     */
    public static ErrorReport minimal(String service, String message, Exception exception) {
        return ErrorReport.builder()
            .timestamp(LocalDateTime.now())
            .service(service)
            .message(message)
            .errorType(exception.getClass().getSimpleName())
            .severity(ErrorSeverity.MEDIUM)
            .build();
    }
    
    /**
     * Create a critical error report
     */
    public static ErrorReport critical(String service, String message, Exception exception, 
                                      String clientIp, String path) {
        return ErrorReport.builder()
            .timestamp(LocalDateTime.now())
            .service(service)
            .message(message)
            .errorType(exception.getClass().getSimpleName())
            .severity(ErrorSeverity.CRITICAL)
            .stackTrace(getStackTraceString(exception))
            .clientIp(clientIp)
            .path(path)
            .customerFacing(true)
            .build();
    }
    
    private static String getStackTraceString(Exception exception) {
        if (exception == null) return null;
        
        StringBuilder sb = new StringBuilder();
        sb.append(exception.toString()).append("\n");
        
        StackTraceElement[] trace = exception.getStackTrace();
        for (int i = 0; i < Math.min(trace.length, 50); i++) {
            sb.append("\tat ").append(trace[i]).append("\n");
        }
        
        Throwable cause = exception.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(cause.toString()).append("\n");
            StackTraceElement[] causeTrace = cause.getStackTrace();
            for (int i = 0; i < Math.min(causeTrace.length, 20); i++) {
                sb.append("\tat ").append(causeTrace[i]).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    // Removed custom builder - using Lombok @Builder instead
    /* Commenting out custom builder to avoid conflicts with Lombok
    public static class ErrorReportBuilder {
        private String reportId;
        private LocalDateTime timestamp = LocalDateTime.now();
        private String service;
        private String environment;
        private String version;
        private String hostname;
        private ErrorSeverity severity;
        private String errorType;
        private String errorCode;
        private String message;
        private String stackTrace;
        private Throwable rootCause;
        private String path;
        private String httpMethod;
        private String clientIp;
        private String userAgent;
        private String userId;
        private String sessionId;
        private String correlationId;
        private Map<String, String> requestHeaders;
        private Map<String, String> requestParams;
        private Object requestBody;
        private Integer responseStatus;
        private Long responseTime;
        private Map<String, Object> metadata;
        private SystemMetrics systemMetrics;
        private Map<String, String> environmentVariables;
        private List<String> activeProfiles;
        private String businessImpact;
        private List<String> affectedFeatures;
        private Integer affectedUsers;
        private Boolean customerFacing;
        private String remediation;
        private Integer occurrenceCount;
        private LocalDateTime firstOccurrence;
        private LocalDateTime lastOccurrence;
        private List<String> similarErrors;
        private String errorFingerprint;
        private Boolean alertSent;
        private List<String> notifiedChannels;
        private String escalationLevel;
        private String assignedTo;
        private String ticketId;
        
        public ErrorReportBuilder reportId(String reportId) { this.reportId = reportId; return this; }
        public ErrorReportBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        public ErrorReportBuilder service(String service) { this.service = service; return this; }
        public ErrorReportBuilder environment(String environment) { this.environment = environment; return this; }
        public ErrorReportBuilder version(String version) { this.version = version; return this; }
        public ErrorReportBuilder hostname(String hostname) { this.hostname = hostname; return this; }
        public ErrorReportBuilder severity(ErrorSeverity severity) { this.severity = severity; return this; }
        public ErrorReportBuilder errorType(String errorType) { this.errorType = errorType; return this; }
        public ErrorReportBuilder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public ErrorReportBuilder message(String message) { this.message = message; return this; }
        public ErrorReportBuilder stackTrace(String stackTrace) { this.stackTrace = stackTrace; return this; }
        public ErrorReportBuilder rootCause(Throwable rootCause) { this.rootCause = rootCause; return this; }
        public ErrorReportBuilder path(String path) { this.path = path; return this; }
        public ErrorReportBuilder httpMethod(String httpMethod) { this.httpMethod = httpMethod; return this; }
        public ErrorReportBuilder clientIp(String clientIp) { this.clientIp = clientIp; return this; }
        public ErrorReportBuilder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public ErrorReportBuilder userId(String userId) { this.userId = userId; return this; }
        public ErrorReportBuilder sessionId(String sessionId) { this.sessionId = sessionId; return this; }
        public ErrorReportBuilder correlationId(String correlationId) { this.correlationId = correlationId; return this; }
        public ErrorReportBuilder requestHeaders(Map<String, String> requestHeaders) { this.requestHeaders = requestHeaders; return this; }
        public ErrorReportBuilder requestParams(Map<String, String> requestParams) { this.requestParams = requestParams; return this; }
        public ErrorReportBuilder requestBody(Object requestBody) { this.requestBody = requestBody; return this; }
        public ErrorReportBuilder responseStatus(Integer responseStatus) { this.responseStatus = responseStatus; return this; }
        public ErrorReportBuilder responseTime(Long responseTime) { this.responseTime = responseTime; return this; }
        public ErrorReportBuilder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public ErrorReportBuilder systemMetrics(SystemMetrics systemMetrics) { this.systemMetrics = systemMetrics; return this; }
        public ErrorReportBuilder environmentVariables(Map<String, String> environmentVariables) { this.environmentVariables = environmentVariables; return this; }
        public ErrorReportBuilder activeProfiles(List<String> activeProfiles) { this.activeProfiles = activeProfiles; return this; }
        public ErrorReportBuilder businessImpact(String businessImpact) { this.businessImpact = businessImpact; return this; }
        public ErrorReportBuilder affectedFeatures(List<String> affectedFeatures) { this.affectedFeatures = affectedFeatures; return this; }
        public ErrorReportBuilder affectedUsers(Integer affectedUsers) { this.affectedUsers = affectedUsers; return this; }
        public ErrorReportBuilder customerFacing(Boolean customerFacing) { this.customerFacing = customerFacing; return this; }
        public ErrorReportBuilder remediation(String remediation) { this.remediation = remediation; return this; }
        public ErrorReportBuilder occurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; return this; }
        public ErrorReportBuilder firstOccurrence(LocalDateTime firstOccurrence) { this.firstOccurrence = firstOccurrence; return this; }
        public ErrorReportBuilder lastOccurrence(LocalDateTime lastOccurrence) { this.lastOccurrence = lastOccurrence; return this; }
        public ErrorReportBuilder similarErrors(List<String> similarErrors) { this.similarErrors = similarErrors; return this; }
        public ErrorReportBuilder errorFingerprint(String errorFingerprint) { this.errorFingerprint = errorFingerprint; return this; }
        public ErrorReportBuilder alertSent(Boolean alertSent) { this.alertSent = alertSent; return this; }
        public ErrorReportBuilder notifiedChannels(List<String> notifiedChannels) { this.notifiedChannels = notifiedChannels; return this; }
        public ErrorReportBuilder escalationLevel(String escalationLevel) { this.escalationLevel = escalationLevel; return this; }
        public ErrorReportBuilder assignedTo(String assignedTo) { this.assignedTo = assignedTo; return this; }
        public ErrorReportBuilder ticketId(String ticketId) { this.ticketId = ticketId; return this; }
        
        public ErrorReport build() {
            ErrorReport report = new ErrorReport();
            report.setReportId(reportId);
            report.setTimestamp(timestamp);
            report.setService(service);
            report.setEnvironment(environment);
            report.setVersion(version);
            report.setHostname(hostname);
            report.setSeverity(severity);
            report.setErrorType(errorType);
            report.setErrorCode(errorCode);
            report.setMessage(message);
            report.setStackTrace(stackTrace);
            report.setRootCause(rootCause);
            report.setPath(path);
            report.setHttpMethod(httpMethod);
            report.setClientIp(clientIp);
            report.setUserAgent(userAgent);
            report.setUserId(userId);
            report.setSessionId(sessionId);
            report.setCorrelationId(correlationId);
            report.setRequestHeaders(requestHeaders);
            report.setRequestParams(requestParams);
            report.setRequestBody(requestBody);
            report.setResponseStatus(responseStatus);
            report.setResponseTime(responseTime);
            report.setMetadata(metadata);
            report.setSystemMetrics(systemMetrics);
            report.setEnvironmentVariables(environmentVariables);
            report.setActiveProfiles(activeProfiles);
            report.setBusinessImpact(businessImpact);
            report.setAffectedFeatures(affectedFeatures);
            report.setAffectedUsers(affectedUsers);
            report.setCustomerFacing(customerFacing);
            report.setRemediation(remediation);
            report.setOccurrenceCount(occurrenceCount);
            report.setFirstOccurrence(firstOccurrence);
            report.setLastOccurrence(lastOccurrence);
            report.setSimilarErrors(similarErrors);
            report.setErrorFingerprint(errorFingerprint);
            report.setAlertSent(alertSent);
            report.setNotifiedChannels(notifiedChannels);
            report.setEscalationLevel(escalationLevel);
            report.setAssignedTo(assignedTo);
            report.setTicketId(ticketId);
            return report;
        }
    }
    */
}

/**
 * System metrics captured at the time of error
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class SystemMetrics {
    private double cpuUsage;
    private long memoryUsed;
    private long memoryTotal;
    private long diskFree;
    private int activeThreads;
    private int activeConnections;
    private double loadAverage;
    private long gcCount;
    private long gcTime;
}