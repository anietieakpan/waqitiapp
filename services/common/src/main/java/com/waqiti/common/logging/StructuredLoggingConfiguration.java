package com.waqiti.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import lombok.extern.slf4j.Slf4j;
import net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder;
import net.logstash.logback.fieldnames.LogstashFieldNames;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Structured logging configuration with correlation IDs and audit trails
 */
@Configuration
@Slf4j
public class StructuredLoggingConfiguration {
    
    @Value("${logging.file.path:./logs}")
    private String logPath;
    
    @Value("${spring.application.name}")
    private String applicationName;
    
    @Value("${logging.level.audit:INFO}")
    private String auditLogLevel;
    
    /**
     * Configure structured JSON logging
     */
    @Bean
    public LoggerContext loggerContext() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        
        // Console appender with JSON format
        ConsoleAppender<ILoggingEvent> consoleAppender = new ConsoleAppender<>();
        consoleAppender.setContext(context);
        consoleAppender.setName("CONSOLE");
        
        LoggingEventCompositeJsonEncoder jsonEncoder = new LoggingEventCompositeJsonEncoder();
        jsonEncoder.setContext(context);
        
        // Configure JSON field names
        // Configure JSON output
        // Use the default configuration provided by LoggingEventCompositeJsonEncoder
        
        jsonEncoder.start();
        consoleAppender.setEncoder(jsonEncoder);
        consoleAppender.start();
        
        // File appender with rotation
        RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(context);
        fileAppender.setName("FILE");
        fileAppender.setFile(logPath + "/" + applicationName + ".log");
        
        TimeBasedRollingPolicy<ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(context);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(logPath + "/" + applicationName + ".%d{yyyy-MM-dd}.%i.log.gz");
        rollingPolicy.setMaxHistory(30);
        rollingPolicy.start();
        
        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.setEncoder(jsonEncoder);
        fileAppender.start();
        
        // Root logger configuration
        Logger rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(consoleAppender);
        rootLogger.addAppender(fileAppender);
        
        return context;
    }
    
    /**
     * Correlation ID filter for request tracing
     */
    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
    
    /**
     * Request logging filter
     */
    @Bean
    public RequestLoggingFilter requestLoggingFilter() {
        return new RequestLoggingFilter();
    }
    
    /**
     * Audit logger for financial operations
     */
    @Bean
    public AuditLogger auditLogger() {
        return new AuditLogger();
    }
    
    /**
     * Performance monitoring interceptor
     */
    @Bean
    public PerformanceLoggingInterceptor performanceLoggingInterceptor() {
        return new PerformanceLoggingInterceptor();
    }
    
    /**
     * Correlation ID filter implementation
     */
    public static class CorrelationIdFilter extends OncePerRequestFilter {
        
        private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
        private static final String CORRELATION_ID_MDC_KEY = "correlationId";
        
        @Override
        protected void doFilterInternal(HttpServletRequest request, 
                                      HttpServletResponse response, 
                                      FilterChain filterChain) throws ServletException, IOException {
            
            String correlationId = extractCorrelationId(request);
            
            // Set in MDC for structured logging
            org.slf4j.MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
            org.slf4j.MDC.put("requestId", UUID.randomUUID().toString());
            org.slf4j.MDC.put("userId", extractUserId(request));
            org.slf4j.MDC.put("sessionId", extractSessionId(request));
            org.slf4j.MDC.put("userAgent", request.getHeader("User-Agent"));
            org.slf4j.MDC.put("clientIp", getClientIpAddress(request));
            
            // Add to response header
            response.setHeader(CORRELATION_ID_HEADER, correlationId);
            
            try {
                filterChain.doFilter(request, response);
            } finally {
                // Clean up MDC
                org.slf4j.MDC.clear();
            }
        }
        
        private String extractCorrelationId(HttpServletRequest request) {
            String correlationId = request.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isEmpty()) {
                correlationId = UUID.randomUUID().toString();
            }
            return correlationId;
        }
        
        private String extractUserId(HttpServletRequest request) {
            // Try to extract from JWT token or session
            String authorization = request.getHeader("Authorization");
            if (authorization != null && authorization.startsWith("Bearer ")) {
                // Extract user ID from JWT token (simplified)
                return "user-from-jwt";
            }
            return "anonymous";
        }
        
        private String extractSessionId(HttpServletRequest request) {
            return request.getSession(false) != null ? 
                request.getSession().getId() : "no-session";
        }
        
        private String getClientIpAddress(HttpServletRequest request) {
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                return xForwardedFor.split(",")[0].trim();
            }
            
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isEmpty()) {
                return xRealIp;
            }
            
            return request.getRemoteAddr();
        }
    }
    
    /**
     * Request logging filter implementation
     */
    public static class RequestLoggingFilter extends OncePerRequestFilter {
        
        @Override
        protected void doFilterInternal(HttpServletRequest request, 
                                      HttpServletResponse response, 
                                      FilterChain filterChain) throws ServletException, IOException {
            
            long startTime = System.currentTimeMillis();
            
            // Log request start
            log.info("HTTP Request: {} {} - User-Agent: {}", 
                request.getMethod(), 
                request.getRequestURI(),
                request.getHeader("User-Agent"));
            
            try {
                filterChain.doFilter(request, response);
            } finally {
                long duration = System.currentTimeMillis() - startTime;
                
                // Log request completion
                log.info("HTTP Response: {} {} - Status: {} - Duration: {}ms", 
                    request.getMethod(), 
                    request.getRequestURI(),
                    response.getStatus(),
                    duration);
                
                // Log slow requests
                if (duration > 2000) {
                    log.warn("Slow Request: {} {} took {}ms", 
                        request.getMethod(), 
                        request.getRequestURI(), 
                        duration);
                }
            }
        }
    }
    
    /**
     * Audit logger for financial operations
     */
    public static class AuditLogger {
        
        private static final org.slf4j.Logger auditLog = 
            LoggerFactory.getLogger("AUDIT");
        
        public void logPaymentCreated(UUID paymentId, UUID requestorId, UUID recipientId, 
                                    java.math.BigDecimal amount, String currency) {
            AuditEvent event = AuditEvent.builder()
                .eventType("PAYMENT_CREATED")
                .entityType("PAYMENT")
                .entityId(paymentId.toString())
                .userId(requestorId.toString())
                .timestamp(LocalDateTime.now())
                .details(Map.of(
                    "recipientId", recipientId.toString(),
                    "amount", amount.toString(),
                    "currency", currency
                ))
                .build();
            
            auditLog.info("Audit Event: {}", event.toJson());
        }
        
        public void logBalanceUpdate(UUID walletId, UUID userId, 
                                   java.math.BigDecimal oldBalance, java.math.BigDecimal newBalance) {
            AuditEvent event = AuditEvent.builder()
                .eventType("BALANCE_UPDATED")
                .entityType("WALLET")
                .entityId(walletId.toString())
                .userId(userId.toString())
                .timestamp(LocalDateTime.now())
                .details(Map.of(
                    "oldBalance", oldBalance.toString(),
                    "newBalance", newBalance.toString(),
                    "change", newBalance.subtract(oldBalance).toString()
                ))
                .build();
            
            auditLog.info("Audit Event: {}", event.toJson());
        }
        
        public void logUserLogin(UUID userId, String ipAddress, boolean successful) {
            AuditEvent event = AuditEvent.builder()
                .eventType(successful ? "LOGIN_SUCCESS" : "LOGIN_FAILURE")
                .entityType("USER")
                .entityId(userId.toString())
                .userId(userId.toString())
                .timestamp(LocalDateTime.now())
                .details(Map.of(
                    "ipAddress", ipAddress,
                    "successful", String.valueOf(successful)
                ))
                .build();
            
            auditLog.info("Audit Event: {}", event.toJson());
        }
        
        public void logSecurityEvent(String eventType, String description, 
                                   String ipAddress, String userId) {
            AuditEvent event = AuditEvent.builder()
                .eventType("SECURITY_" + eventType)
                .entityType("SECURITY")
                .entityId(UUID.randomUUID().toString())
                .userId(userId != null ? userId : "system")
                .timestamp(LocalDateTime.now())
                .details(Map.of(
                    "description", description,
                    "ipAddress", ipAddress
                ))
                .build();
            
            auditLog.warn("Security Event: {}", event.toJson());
        }
        
        public void logDataAccess(String operation, String entityType, String entityId, 
                                String userId, boolean authorized) {
            AuditEvent event = AuditEvent.builder()
                .eventType("DATA_ACCESS")
                .entityType(entityType)
                .entityId(entityId)
                .userId(userId)
                .timestamp(LocalDateTime.now())
                .details(Map.of(
                    "operation", operation,
                    "authorized", String.valueOf(authorized)
                ))
                .build();
            
            if (authorized) {
                auditLog.info("Data Access: {}", event.toJson());
            } else {
                auditLog.warn("Unauthorized Data Access: {}", event.toJson());
            }
        }
    }
    
    /**
     * Performance logging interceptor
     */
    public static class PerformanceLoggingInterceptor {
        
        private static final org.slf4j.Logger perfLog = 
            LoggerFactory.getLogger("PERFORMANCE");
        
        public void logDatabaseQuery(String query, long executionTime, boolean successful) {
            if (executionTime > 1000) { // Log slow queries
                perfLog.warn("Slow Database Query: {} - Duration: {}ms - Success: {}", 
                    query, executionTime, successful);
            } else {
                perfLog.debug("Database Query: {} - Duration: {}ms", query, executionTime);
            }
        }
        
        public void logCacheOperation(String operation, String key, boolean hit, long duration) {
            perfLog.debug("Cache {}: {} - Hit: {} - Duration: {}ms", 
                operation, key, hit, duration);
        }
        
        public void logExternalServiceCall(String service, String endpoint, 
                                         long duration, boolean successful) {
            if (duration > 5000) { // Log slow external calls
                perfLog.warn("Slow External Service Call: {} {} - Duration: {}ms - Success: {}", 
                    service, endpoint, duration, successful);
            } else {
                perfLog.info("External Service Call: {} {} - Duration: {}ms - Success: {}", 
                    service, endpoint, duration, successful);
            }
        }
        
        public void logMethodExecution(String className, String methodName, 
                                     long duration, Object result) {
            if (duration > 2000) { // Log slow methods
                perfLog.warn("Slow Method Execution: {}.{} - Duration: {}ms", 
                    className, methodName, duration);
            }
        }
    }
    
    /**
     * Audit event data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class AuditEvent {
        private String eventType;
        private String entityType;
        private String entityId;
        private String userId;
        private LocalDateTime timestamp;
        private Map<String, Object> details;
        private String correlationId;
        
        public String toJson() {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = 
                    new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                return mapper.writeValueAsString(this);
            } catch (Exception e) {
                return "Failed to serialize audit event: " + e.getMessage();
            }
        }
    }
    
    /**
     * Security event logger
     */
    @lombok.RequiredArgsConstructor
    public static class SecurityEventLogger {
        
        private final AuditLogger auditLogger;
        private static final org.slf4j.Logger securityLog = 
            LoggerFactory.getLogger("SECURITY");
        
        public void logFailedAuthentication(String username, String ipAddress) {
            securityLog.warn("Failed authentication attempt - Username: {} - IP: {}", 
                username, ipAddress);
            auditLogger.logSecurityEvent("FAILED_AUTH", 
                "Failed authentication for user: " + username, ipAddress, username);
        }
        
        public void logSuspiciousActivity(String activity, String userId, String ipAddress) {
            securityLog.error("Suspicious activity detected - Activity: {} - User: {} - IP: {}", 
                activity, userId, ipAddress);
            auditLogger.logSecurityEvent("SUSPICIOUS_ACTIVITY", activity, ipAddress, userId);
        }
        
        public void logPrivilegeEscalation(String userId, String fromRole, String toRole) {
            securityLog.warn("Privilege escalation - User: {} - From: {} - To: {}", 
                userId, fromRole, toRole);
            auditLogger.logSecurityEvent("PRIVILEGE_ESCALATION", 
                String.format("User %s escalated from %s to %s", userId, fromRole, toRole), 
                "system", userId);
        }
        
        public void logDataBreach(String dataType, String affectedEntities, String userId) {
            securityLog.error("Potential data breach - Data Type: {} - Affected: {} - User: {}", 
                dataType, affectedEntities, userId);
            auditLogger.logSecurityEvent("DATA_BREACH", 
                String.format("Potential breach of %s affecting %s", dataType, affectedEntities), 
                "system", userId);
        }
    }
}