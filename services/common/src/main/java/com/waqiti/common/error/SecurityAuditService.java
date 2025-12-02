package com.waqiti.common.error;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service for auditing and reporting security-related events
 * Tracks authentication failures, security violations, and suspicious activities
 */
@Service
@RequiredArgsConstructor
public class SecurityAuditService {
    
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SecurityAuditService.class);

    @Value("${security.audit.enabled:true}")
    private boolean auditEnabled;
    
    @Value("${security.failed-auth.max-attempts:5}")
    private int maxFailedAuthAttempts;
    
    @Value("${security.failed-auth.lockout-duration:3600}") // 1 hour in seconds
    private int lockoutDuration;
    
    private final ErrorReportingService errorReportingService;
    
    // Track failed authentication attempts by IP
    private final Map<String, AtomicInteger> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastFailedAttempt = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lockedOutIps = new ConcurrentHashMap<>();

    /**
     * Report a security violation
     */
    public void reportSecurityViolation(String clientIp, String violationType, 
                                       String description, String userAgent) {
        if (!auditEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                SecurityEvent event = SecurityEvent.builder()
                        .eventType("SECURITY_VIOLATION")
                        .severity("CRITICAL")
                        .clientIp(clientIp)
                        .userAgent(userAgent)
                        .timestamp(LocalDateTime.now())
                        .description(description)
                        .metadata(Map.of(
                            "violationType", violationType,
                            "threatLevel", calculateThreatLevel(violationType),
                            "blockedByFirewall", isBlockedByFirewall(clientIp)
                        ))
                        .build();
                
                logSecurityEvent(event);
                
                // Report to external systems
                errorReportingService.reportSecurityViolation(violationType, description, clientIp, userAgent);
                
                // Auto-block if threat level is high
                if ("HIGH".equals(event.getMetadata().get("threatLevel"))) {
                    blockIpAddress(clientIp, "Automatic block due to security violation");
                }
                
            } catch (Exception e) {
                log.error("Failed to report security violation: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Record a failed authentication attempt
     */
    public void recordFailedAuthentication(String clientIp, String userAgent) {
        if (!auditEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                // Track failed attempts
                AtomicInteger attempts = failedAttempts.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
                int currentAttempts = attempts.incrementAndGet();
                lastFailedAttempt.put(clientIp, LocalDateTime.now());
                
                SecurityEvent event = SecurityEvent.builder()
                        .eventType("FAILED_AUTHENTICATION")
                        .severity(currentAttempts >= maxFailedAuthAttempts ? "HIGH" : "MEDIUM")
                        .clientIp(clientIp)
                        .userAgent(userAgent)
                        .timestamp(LocalDateTime.now())
                        .description("Failed authentication attempt")
                        .metadata(Map.of(
                            "attemptCount", currentAttempts,
                            "maxAttempts", maxFailedAuthAttempts,
                            "isRepeatedOffender", isRepeatedOffender(clientIp)
                        ))
                        .build();
                
                logSecurityEvent(event);
                
                // Lock out IP if threshold exceeded
                if (currentAttempts >= maxFailedAuthAttempts) {
                    lockoutIpAddress(clientIp);
                    
                    // Report as security violation
                    reportSecurityViolation(clientIp, "BRUTE_FORCE", 
                        String.format("IP exceeded %d failed authentication attempts", maxFailedAuthAttempts),
                        userAgent);
                }
                
            } catch (Exception e) {
                log.error("Failed to record failed authentication: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Record successful authentication (reset failed attempts)
     */
    public void recordSuccessfulAuthentication(String clientIp, String userId) {
        if (!auditEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                // Reset failed attempts for this IP
                failedAttempts.remove(clientIp);
                lastFailedAttempt.remove(clientIp);
                lockedOutIps.remove(clientIp);
                
                SecurityEvent event = SecurityEvent.builder()
                        .eventType("SUCCESSFUL_AUTHENTICATION")
                        .severity("LOW")
                        .clientIp(clientIp)
                        .userId(userId)
                        .timestamp(LocalDateTime.now())
                        .description("Successful authentication")
                        .build();
                
                logSecurityEvent(event);
                
            } catch (Exception e) {
                log.error("Failed to record successful authentication: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Report cryptographic errors that might indicate tampering
     */
    public void reportCryptographicError(String clientIp, String errorType, String userAgent) {
        if (!auditEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                SecurityEvent event = SecurityEvent.builder()
                        .eventType("CRYPTOGRAPHIC_ERROR")
                        .severity("HIGH")
                        .clientIp(clientIp)
                        .userAgent(userAgent)
                        .timestamp(LocalDateTime.now())
                        .description("Cryptographic error detected - possible tampering attempt")
                        .metadata(Map.of(
                            "errorType", errorType,
                            "possibleAttack", determinePossibleAttack(errorType),
                            "riskScore", calculateRiskScore(clientIp, errorType)
                        ))
                        .build();
                
                logSecurityEvent(event);
                
                // Report as security violation if risk is high
                int riskScore = (Integer) event.getMetadata().get("riskScore");
                if (riskScore >= 8) {
                    reportSecurityViolation(clientIp, "CRYPTO_TAMPERING", 
                        "Cryptographic tampering attempt detected", userAgent);
                }
                
            } catch (Exception e) {
                log.error("Failed to report cryptographic error: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Record suspicious API usage patterns
     */
    public void recordSuspiciousApiUsage(String clientIp, String endpoint, String pattern) {
        if (!auditEnabled) {
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                SecurityEvent event = SecurityEvent.builder()
                        .eventType("SUSPICIOUS_API_USAGE")
                        .severity("MEDIUM")
                        .clientIp(clientIp)
                        .timestamp(LocalDateTime.now())
                        .description("Suspicious API usage pattern detected")
                        .metadata(Map.of(
                            "endpoint", endpoint,
                            "pattern", pattern,
                            "automationLikelihood", calculateAutomationLikelihood(pattern),
                            "botScore", calculateBotScore(clientIp, endpoint)
                        ))
                        .build();
                
                logSecurityEvent(event);
                
                // Auto-block if bot score is very high
                int botScore = (Integer) event.getMetadata().get("botScore");
                if (botScore >= 9) {
                    blockIpAddress(clientIp, "Automated bot activity detected");
                }
                
            } catch (Exception e) {
                log.error("Failed to record suspicious API usage: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * Check if an IP address is currently locked out
     */
    public boolean isIpLockedOut(String clientIp) {
        LocalDateTime lockedAt = lockedOutIps.get(clientIp);
        if (lockedAt == null) {
            return false;
        }
        
        // Check if lockout has expired
        if (LocalDateTime.now().isAfter(lockedAt.plusSeconds(lockoutDuration))) {
            lockedOutIps.remove(clientIp);
            failedAttempts.remove(clientIp);
            return false;
        }
        
        return true;
    }

    /**
     * Get failed authentication attempts for an IP
     */
    public int getFailedAttempts(String clientIp) {
        AtomicInteger attempts = failedAttempts.get(clientIp);
        return attempts != null ? attempts.get() : 0;
    }

    // Private helper methods

    private void lockoutIpAddress(String clientIp) {
        lockedOutIps.put(clientIp, LocalDateTime.now());
        log.warn("IP address locked out due to excessive failed authentication attempts: {}", clientIp);
        
        SecurityEvent event = SecurityEvent.builder()
                .eventType("IP_LOCKOUT")
                .severity("HIGH")
                .clientIp(clientIp)
                .timestamp(LocalDateTime.now())
                .description("IP address locked out")
                .metadata(Map.of(
                    "lockoutDuration", lockoutDuration,
                    "reason", "Excessive failed authentication attempts"
                ))
                .build();
        
        logSecurityEvent(event);
    }

    private void blockIpAddress(String clientIp, String reason) {
        // Implement IP blocking in firewall/load balancer
        log.error("IP address blocked: {} - Reason: {}", clientIp, reason);
        
        SecurityEvent event = SecurityEvent.builder()
                .eventType("IP_BLOCKED")
                .severity("CRITICAL")
                .clientIp(clientIp)
                .timestamp(LocalDateTime.now())
                .description("IP address blocked")
                .metadata(Map.of("reason", reason))
                .build();
        
        logSecurityEvent(event);
    }

    private void logSecurityEvent(SecurityEvent event) {
        // Log to security log file
        log.info("SECURITY_EVENT: {} | IP: {} | Severity: {} | Description: {}", 
            event.getEventType(), event.getClientIp(), event.getSeverity(), event.getDescription());
        
        // Store in database for analytics
        storeSecurityEvent(event);
    }

    private void storeSecurityEvent(SecurityEvent event) {
        try {
            // Implementation would store in security events database
            log.debug("Stored security event: {} - {}", event.getEventType(), event.getClientIp());
            
        } catch (Exception e) {
            log.error("Failed to store security event: {}", e.getMessage());
        }
    }

    private String calculateThreatLevel(String violationType) {
        return switch (violationType.toUpperCase()) {
            case "SQL_INJECTION", "XSS", "CRYPTO_TAMPERING" -> "CRITICAL";
            case "BRUTE_FORCE", "UNAUTHORIZED_ACCESS" -> "HIGH";
            case "RATE_LIMIT_EXCEEDED", "INVALID_INPUT" -> "MEDIUM";
            default -> "LOW";
        };
    }

    private boolean isBlockedByFirewall(String clientIp) {
        // Check if IP is already blocked by firewall
        // Implementation would query your firewall/security system
        return false;
    }

    private boolean isRepeatedOffender(String clientIp) {
        // Check if this IP has been flagged before
        // Implementation would check historical data
        return getFailedAttempts(clientIp) > maxFailedAuthAttempts * 2;
    }

    private String determinePossibleAttack(String errorType) {
        return switch (errorType.toUpperCase()) {
            case "BAD_PADDING" -> "PADDING_ORACLE_ATTACK";
            case "INVALID_SIGNATURE" -> "SIGNATURE_FORGERY";
            case "DECRYPTION_FAILED" -> "CIPHERTEXT_MANIPULATION";
            default -> "UNKNOWN";
        };
    }

    private int calculateRiskScore(String clientIp, String errorType) {
        int score = 5; // Base score
        
        // Increase score based on error type severity
        switch (errorType.toUpperCase()) {
            case "BAD_PADDING" -> score += 4;
            case "INVALID_SIGNATURE" -> score += 3;
            case "DECRYPTION_FAILED" -> score += 2;
        }
        
        // Increase score if repeated from same IP
        if (getFailedAttempts(clientIp) > 0) {
            score += 2;
        }
        
        return Math.min(score, 10);
    }

    private int calculateAutomationLikelihood(String pattern) {
        // Analyze request patterns to detect automation
        int likelihood = 0;
        
        if (pattern.contains("rapid_requests")) likelihood += 3;
        if (pattern.contains("identical_user_agent")) likelihood += 2;
        if (pattern.contains("sequential_endpoints")) likelihood += 2;
        if (pattern.contains("no_cookies")) likelihood += 1;
        
        return Math.min(likelihood, 10);
    }

    private int calculateBotScore(String clientIp, String endpoint) {
        // Calculate likelihood that requests are from a bot
        int score = 0;
        
        // Check request frequency
        if (getFailedAttempts(clientIp) > 10) score += 3;
        
        // Check if targeting sensitive endpoints
        if (endpoint.contains("/admin") || endpoint.contains("/api/auth")) score += 2;
        
        // Additional bot detection logic would go here
        
        return Math.min(score, 10);
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SecurityEvent {
        private String eventType;
        private String severity;
        private String clientIp;
        private String userId;
        private String userAgent;
        @lombok.Builder.Default
        private LocalDateTime timestamp = LocalDateTime.now();
        private String description;
        private Map<String, Object> metadata;
    }
}