package com.waqiti.user.kafka;

import com.waqiti.user.event.UserSessionEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.SessionManagementService;
import com.waqiti.user.service.SecurityAuditService;
import com.waqiti.user.service.DeviceTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Production-grade Kafka consumer for user session events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserSessionEventConsumer {

    private final UserService userService;
    private final SessionManagementService sessionService;
    private final SecurityAuditService securityAuditService;
    private final DeviceTrackingService deviceTrackingService;

    @KafkaListener(topics = "user-session-events", groupId = "session-event-processor")
    public void processSessionEvent(@Payload UserSessionEvent event,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                  @Header(KafkaHeaders.OFFSET) long offset,
                                  Acknowledgment acknowledgment) {
        try {
            log.info("Processing session event for user: {} type: {} sessionId: {}", 
                    event.getUserId(), event.getEventType(), event.getSessionId());
            
            // Validate event
            validateSessionEvent(event);
            
            // Process based on event type
            switch (event.getEventType()) {
                case "SESSION_STARTED" -> handleSessionStart(event);
                case "SESSION_ENDED" -> handleSessionEnd(event);
                case "SESSION_EXPIRED" -> handleSessionExpiry(event);
                case "SESSION_REFRESHED" -> handleSessionRefresh(event);
                case "SESSION_INVALIDATED" -> handleSessionInvalidation(event);
                case "CONCURRENT_SESSION" -> handleConcurrentSession(event);
                case "SESSION_HIJACK_ATTEMPT" -> handleSessionHijackAttempt(event);
                default -> log.warn("Unknown session event type: {}", event.getEventType());
            }
            
            // Track device information
            deviceTrackingService.trackDevice(
                event.getUserId(),
                event.getDeviceId(),
                event.getDeviceType(),
                event.getDeviceFingerprint(),
                event.getUserAgent()
            );
            
            // Update session metrics
            sessionService.updateSessionMetrics(
                event.getUserId(),
                event.getSessionId(),
                event.getEventType(),
                event.getEventTime()
            );
            
            // Log session event
            securityAuditService.logSessionEvent(
                event.getUserId(),
                event.getSessionId(),
                event.getEventType(),
                event.getIpAddress(),
                event.getLocation(),
                event.getEventTime()
            );
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.debug("Successfully processed session event for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process session event for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Session event processing failed", e);
        }
    }

    private void validateSessionEvent(UserSessionEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for session event");
        }
        
        if (event.getSessionId() == null || event.getSessionId().trim().isEmpty()) {
            throw new IllegalArgumentException("Session ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
    }

    private void handleSessionStart(UserSessionEvent event) {
        // Create new session
        sessionService.createSession(
            event.getUserId(),
            event.getSessionId(),
            event.getIpAddress(),
            event.getUserAgent(),
            event.getDeviceId(),
            event.getStartTime()
        );
        
        // Check for concurrent sessions
        int activeSessions = sessionService.getActiveSessionCount(event.getUserId());
        if (activeSessions > event.getMaxConcurrentSessions()) {
            sessionService.handleExcessiveSessions(
                event.getUserId(),
                activeSessions,
                event.getMaxConcurrentSessions()
            );
        }
        
        // Validate login location
        if (!sessionService.isLocationValid(event.getUserId(), event.getLocation())) {
            securityAuditService.flagSuspiciousLocation(
                event.getUserId(),
                event.getLocation(),
                event.getIpAddress()
            );
        }
        
        // Set session timeout
        sessionService.setSessionTimeout(
            event.getSessionId(),
            event.getSessionTimeout()
        );
    }

    private void handleSessionEnd(UserSessionEvent event) {
        // Calculate session duration
        Duration sessionDuration = Duration.between(
            event.getStartTime(),
            event.getEndTime()
        );
        
        // End session
        sessionService.endSession(
            event.getSessionId(),
            event.getEndTime(),
            event.getEndReason(),
            sessionDuration
        );
        
        // Update user activity statistics
        userService.updateActivityStatistics(
            event.getUserId(),
            sessionDuration,
            event.getActivityCount()
        );
        
        // Clean up session data
        sessionService.cleanupSessionData(event.getSessionId());
    }

    private void handleSessionExpiry(UserSessionEvent event) {
        // Mark session as expired
        sessionService.markSessionExpired(
            event.getSessionId(),
            event.getExpiryTime()
        );
        
        // Revoke session tokens
        sessionService.revokeSessionTokens(event.getSessionId());
        
        // Send expiry notification if configured
        if (event.isNotifyOnExpiry()) {
            userService.sendSessionExpiryNotification(
                event.getUserId(),
                event.getSessionId()
            );
        }
    }

    private void handleSessionRefresh(UserSessionEvent event) {
        // Refresh session
        sessionService.refreshSession(
            event.getSessionId(),
            event.getRefreshTime(),
            event.getNewExpiryTime()
        );
        
        // Issue new tokens
        sessionService.issueNewTokens(
            event.getSessionId(),
            event.getUserId(),
            event.getTokenExpiry()
        );
        
        // Update last activity
        sessionService.updateLastActivity(
            event.getSessionId(),
            event.getRefreshTime()
        );
    }

    private void handleSessionInvalidation(UserSessionEvent event) {
        // Immediately invalidate session
        sessionService.invalidateSession(
            event.getSessionId(),
            event.getInvalidationReason()
        );
        
        // Force logout
        userService.forceLogout(
            event.getUserId(),
            event.getSessionId()
        );
        
        // Log security event if invalidation is security-related
        if ("SECURITY".equals(event.getInvalidationReason()) ||
            "SUSPICIOUS_ACTIVITY".equals(event.getInvalidationReason())) {
            securityAuditService.logSecurityInvalidation(
                event.getUserId(),
                event.getSessionId(),
                event.getInvalidationReason(),
                event.getSecurityDetails()
            );
        }
    }

    private void handleConcurrentSession(UserSessionEvent event) {
        // Get all active sessions
        var activeSessions = sessionService.getActiveSessions(event.getUserId());
        
        // Apply concurrent session policy
        String policy = event.getConcurrentSessionPolicy();
        switch (policy) {
            case "TERMINATE_OLDEST" -> {
                sessionService.terminateOldestSession(
                    event.getUserId(),
                    event.getSessionId()
                );
            }
            case "TERMINATE_ALL_OTHERS" -> {
                sessionService.terminateAllOtherSessions(
                    event.getUserId(),
                    event.getSessionId()
                );
            }
            case "BLOCK_NEW" -> {
                sessionService.blockNewSession(
                    event.getSessionId(),
                    "CONCURRENT_SESSION_LIMIT"
                );
            }
            case "ALLOW_ALL" -> {
                // Just log the concurrent session
                log.info("Allowing concurrent session for user: {}", event.getUserId());
            }
        }
        
        // Send notification about concurrent login
        userService.sendConcurrentLoginAlert(
            event.getUserId(),
            event.getSessionId(),
            event.getIpAddress(),
            event.getLocation()
        );
    }

    private void handleSessionHijackAttempt(UserSessionEvent event) {
        // Critical security event
        log.error("Session hijack attempt detected for user: {} session: {}", 
                event.getUserId(), event.getSessionId());
        
        // Immediately invalidate compromised session
        sessionService.invalidateCompromisedSession(
            event.getSessionId(),
            "HIJACK_ATTEMPT"
        );
        
        // Terminate all user sessions
        sessionService.terminateAllUserSessions(
            event.getUserId(),
            "SECURITY_BREACH"
        );
        
        // Lock user account
        userService.lockAccount(
            event.getUserId(),
            "SESSION_HIJACK_DETECTED"
        );
        
        // Create security incident
        securityAuditService.createSecurityIncident(
            event.getUserId(),
            "SESSION_HIJACK",
            event.getHijackDetails(),
            "CRITICAL"
        );
        
        // Force password reset
        userService.forcePasswordReset(
            event.getUserId(),
            "SESSION_HIJACK"
        );
    }
}