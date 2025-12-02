package com.waqiti.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Security audit service for tracking authentication and authorization events.
 * Provides comprehensive audit logging for compliance and security monitoring.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditService {
    
    private final SecurityEventRepository securityEventRepository;
    private final ThreatIntelligenceService threatIntelligenceService;
    
    /**
     * Log successful authentication event.
     * 
     * @param userId User ID
     * @param clientIp Client IP address
     */
    @Transactional
    public void logSuccessfulAuthentication(UUID userId, String clientIp) {
        SecurityEvent event = SecurityEvent.builder()
                .userId(userId)
                .eventType(SecurityEventType.AUTHENTICATION_SUCCESS)
                .clientIp(clientIp)
                .timestamp(Instant.now())
                .details("User successfully authenticated")
                .riskScore(calculateRiskScore(clientIp))
                .build();
        
        securityEventRepository.save(event);
        
        log.info("SECURITY_AUDIT: Authentication success - User: {}, IP: {}", userId, clientIp);
    }
    
    /**
     * Log failed authentication attempt.
     * 
     * @param username Username attempt
     * @param reason Failure reason
     * @param clientIp Client IP address
     */
    @Transactional
    public void logFailedAuthentication(String username, String reason, String clientIp) {
        SecurityEvent event = SecurityEvent.builder()
                .username(username)
                .eventType(SecurityEventType.AUTHENTICATION_FAILURE)
                .clientIp(clientIp)
                .timestamp(Instant.now())
                .details("Authentication failed: " + reason)
                .riskScore(calculateRiskScore(clientIp))
                .build();
        
        securityEventRepository.save(event);
        
        // Check for brute force patterns
        checkBruteForcePattern(username, clientIp);
        
        log.warn("SECURITY_AUDIT: Authentication failure - Username: {}, IP: {}, Reason: {}", 
                username, clientIp, reason);
    }
    
    /**
     * Log MFA challenge issued.
     * 
     * @param userId User ID
     * @param clientIp Client IP address
     */
    @Transactional
    public void logMfaChallenge(UUID userId, String clientIp) {
        SecurityEvent event = SecurityEvent.builder()
                .userId(userId)
                .eventType(SecurityEventType.MFA_CHALLENGE)
                .clientIp(clientIp)
                .timestamp(Instant.now())
                .details("MFA challenge issued")
                .riskScore(calculateRiskScore(clientIp))
                .build();
        
        securityEventRepository.save(event);
        
        log.info("SECURITY_AUDIT: MFA challenge - User: {}, IP: {}", userId, clientIp);
    }
    
    /**
     * Log token refresh event.
     * 
     * @param userId User ID
     * @param clientIp Client IP address
     */
    @Transactional
    public void logTokenRefresh(UUID userId, String clientIp) {
        SecurityEvent event = SecurityEvent.builder()
                .userId(userId)
                .eventType(SecurityEventType.TOKEN_REFRESH)
                .clientIp(clientIp)
                .timestamp(Instant.now())
                .details("Access token refreshed")
                .riskScore(calculateRiskScore(clientIp))
                .build();
        
        securityEventRepository.save(event);
        
        log.debug("SECURITY_AUDIT: Token refresh - User: {}, IP: {}", userId, clientIp);
    }
    
    /**
     * Log user logout event.
     * 
     * @param userId User ID
     * @param clientIp Client IP address
     */
    @Transactional
    public void logLogout(UUID userId, String clientIp) {
        SecurityEvent event = SecurityEvent.builder()
                .userId(userId)
                .eventType(SecurityEventType.LOGOUT)
                .clientIp(clientIp)
                .timestamp(Instant.now())
                .details("User logged out")
                .riskScore(calculateRiskScore(clientIp))
                .build();
        
        securityEventRepository.save(event);
        
        log.info("SECURITY_AUDIT: Logout - User: {}, IP: {}", userId, clientIp);
    }
    
    /**
     * Log privilege escalation attempt.
     * 
     * @param userId User ID
     * @param attemptedAction Attempted privileged action
     * @param clientIp Client IP address
     */
    @Transactional
    public void logPrivilegeEscalation(UUID userId, String attemptedAction, String clientIp) {
        SecurityEvent event = SecurityEvent.builder()
                .userId(userId)
                .eventType(SecurityEventType.PRIVILEGE_ESCALATION)
                .clientIp(clientIp)
                .timestamp(Instant.now())
                .details("Privilege escalation attempt: " + attemptedAction)
                .severity(SecuritySeverity.HIGH)
                .riskScore(0.8)
                .build();
        
        securityEventRepository.save(event);
        
        // Alert security team
        alertSecurityTeam(event);
        
        log.error("SECURITY_AUDIT: Privilege escalation - User: {}, Action: {}, IP: {}", 
                userId, attemptedAction, clientIp);
    }
    
    /**
     * Log suspicious activity.
     * 
     * @param userId User ID (optional)
     * @param activity Description of suspicious activity
     * @param clientIp Client IP address
     * @param severity Severity level
     */
    @Transactional
    public void logSuspiciousActivity(UUID userId, String activity, String clientIp, SecuritySeverity severity) {
        SecurityEvent event = SecurityEvent.builder()
                .userId(userId)
                .eventType(SecurityEventType.SUSPICIOUS_ACTIVITY)
                .clientIp(clientIp)
                .timestamp(Instant.now())
                .details("Suspicious activity: " + activity)
                .severity(severity)
                .riskScore(severity == SecuritySeverity.HIGH ? 0.9 : 0.6)
                .build();
        
        securityEventRepository.save(event);
        
        if (severity == SecuritySeverity.HIGH) {
            alertSecurityTeam(event);
        }
        
        log.warn("SECURITY_AUDIT: Suspicious activity - User: {}, Activity: {}, IP: {}, Severity: {}", 
                userId, activity, clientIp, severity);
    }
    
    // Private helper methods
    
    private double calculateRiskScore(String clientIp) {
        try {
            // Check threat intelligence for IP reputation
            ThreatIntelligence threat = threatIntelligenceService.checkIpReputation(clientIp);
            
            if (threat.isMalicious()) {
                return 0.9;
            } else if (threat.isSuspicious()) {
                return 0.6;
            } else if (threat.isFromKnownBotnet()) {
                return 0.8;
            } else if (threat.isFromTorNetwork()) {
                return 0.7;
            }
            
            return 0.1; // Low risk for clean IPs
            
        } catch (Exception e) {
            log.warn("Failed to calculate risk score for IP: {}", clientIp, e);
            return 0.5; // Medium risk if unable to determine
        }
    }
    
    private void checkBruteForcePattern(String username, String clientIp) {
        try {
            // Count recent failed attempts
            Instant since = Instant.now().minusSeconds(300); // Last 5 minutes
            
            long failedAttempts = securityEventRepository.countFailedAuthenticationAttempts(
                    username, clientIp, since);
            
            if (failedAttempts >= 5) {
                // Potential brute force attack detected
                logSuspiciousActivity(null, 
                        String.format("Brute force pattern detected: %d failed attempts for user %s", 
                                failedAttempts, username),
                        clientIp, 
                        SecuritySeverity.HIGH);
                
                // Consider rate limiting or IP blocking here
                threatIntelligenceService.reportSuspiciousIp(clientIp, "Brute force attack");
            }
            
        } catch (Exception e) {
            log.error("Failed to check brute force pattern", e);
        }
    }
    
    private void alertSecurityTeam(SecurityEvent event) {
        try {
            // Send alert to security team (implement based on your alerting system)
            log.error("SECURITY_ALERT: High-severity event detected: {}", event.getDetails());

            // In production, this would integrate with:
            // - Slack/Teams notifications
            // - PagerDuty/OpsGenie alerts
            // - SIEM integration
            // - Email notifications

        } catch (Exception e) {
            log.error("Failed to send security alert", e);
        }
    }

    /**
     * Log account status change for audit trail
     */
    @Transactional
    public void logAccountStatusChange(String userId, String accountId, String previousStatus,
                                      String newStatus, String changeReason, String changedBy,
                                      java.time.LocalDateTime changedAt, java.util.Map<String, Object> metadata) {
        log.info("SECURITY_AUDIT: Account status change - User: {}, Account: {}, From: {} To: {}, Reason: {}, By: {}",
                userId, accountId, previousStatus, newStatus, changeReason, changedBy);

        // In production, this would:
        // - Save to audit log database
        // - Send to compliance system
        // - Trigger workflows based on status change
        // - Update risk assessments if needed
    }
}

// Supporting enums and classes

