package com.waqiti.auth.kafka;

import com.waqiti.common.events.SessionEvent;
import com.waqiti.auth.domain.UserSession;
import com.waqiti.auth.repository.SessionRepository;
import com.waqiti.auth.service.SessionService;
import com.waqiti.auth.service.SessionSecurityService;
import com.waqiti.auth.metrics.AuthMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class SessionManagementEventsConsumer {
    
    private final SessionRepository sessionRepository;
    private final SessionService sessionService;
    private final SessionSecurityService securityService;
    private final AuthMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final long MAX_SESSION_DURATION_HOURS = 24;
    private static final int MAX_CONCURRENT_SESSIONS = 5;
    private static final long SESSION_INACTIVITY_MINUTES = 30;
    
    @KafkaListener(
        topics = {"session-events", "user-session-events", "session-lifecycle-events"},
        groupId = "session-management-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 15000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleSessionManagementEvent(
            @Payload SessionEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("session-%s-%s-p%d-o%d", 
            event.getUserId(), event.getSessionId(), partition, offset);
        
        log.info("Processing session event: userId={}, sessionId={}, action={}, device={}",
            event.getUserId(), event.getSessionId(), event.getAction(), event.getDeviceType());
        
        try {
            switch (event.getAction()) {
                case "SESSION_CREATED":
                    createSession(event, correlationId);
                    break;
                    
                case "SESSION_AUTHENTICATED":
                    authenticateSession(event, correlationId);
                    break;
                    
                case "SESSION_REFRESHED":
                    refreshSession(event, correlationId);
                    break;
                    
                case "SESSION_ACTIVITY":
                    recordSessionActivity(event, correlationId);
                    break;
                    
                case "SESSION_EXPIRED":
                    expireSession(event, correlationId);
                    break;
                    
                case "SESSION_TERMINATED":
                    terminateSession(event, correlationId);
                    break;
                    
                case "SESSION_LOGOUT":
                    logoutSession(event, correlationId);
                    break;
                    
                case "CONCURRENT_SESSION_LIMIT_EXCEEDED":
                    handleConcurrentSessionLimit(event, correlationId);
                    break;
                    
                case "SUSPICIOUS_SESSION_DETECTED":
                    handleSuspiciousSession(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown session action: {}", event.getAction());
                    break;
            }
            
            auditService.logAuthEvent("SESSION_EVENT_PROCESSED", event.getUserId(),
                Map.of("sessionId", event.getSessionId(), "action", event.getAction(),
                    "deviceType", event.getDeviceType(), "ipAddress", event.getIpAddress(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process session management event: {}", e.getMessage(), e);
            kafkaTemplate.send("session-events-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void createSession(SessionEvent event, String correlationId) {
        int activeSessions = sessionRepository.countActiveSessionsByUserId(event.getUserId());
        
        if (activeSessions >= MAX_CONCURRENT_SESSIONS) {
            sessionService.terminateOldestSession(event.getUserId());
            
            notificationService.sendNotification(event.getUserId(), "Session Limit Reached",
                "Your oldest session has been terminated due to concurrent session limit.",
                correlationId);
        }
        
        UserSession session = UserSession.builder()
            .sessionId(event.getSessionId())
            .userId(event.getUserId())
            .deviceType(event.getDeviceType())
            .deviceId(event.getDeviceId())
            .ipAddress(event.getIpAddress())
            .userAgent(event.getUserAgent())
            .status("CREATED")
            .createdAt(LocalDateTime.now())
            .lastActivityAt(LocalDateTime.now())
            .expiresAt(LocalDateTime.now().plusHours(MAX_SESSION_DURATION_HOURS))
            .correlationId(correlationId)
            .build();
        sessionRepository.save(session);
        
        metricsService.recordSessionCreated(event.getDeviceType());
        
        log.info("Session created: userId={}, sessionId={}, device={}", 
            event.getUserId(), event.getSessionId(), event.getDeviceType());
    }
    
    private void authenticateSession(SessionEvent event, String correlationId) {
        UserSession session = sessionRepository.findBySessionId(event.getSessionId())
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        securityService.validateSessionSecurity(event.getSessionId(), event.getIpAddress());
        
        session.setStatus("AUTHENTICATED");
        session.setAuthenticatedAt(LocalDateTime.now());
        sessionRepository.save(session);
        
        metricsService.recordSessionAuthenticated();
        
        log.info("Session authenticated: userId={}, sessionId={}", 
            event.getUserId(), event.getSessionId());
    }
    
    private void refreshSession(SessionEvent event, String correlationId) {
        UserSession session = sessionRepository.findBySessionId(event.getSessionId())
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        session.setExpiresAt(LocalDateTime.now().plusHours(MAX_SESSION_DURATION_HOURS));
        session.setLastActivityAt(LocalDateTime.now());
        sessionRepository.save(session);
        
        metricsService.recordSessionRefreshed();
        
        log.info("Session refreshed: userId={}, sessionId={}", 
            event.getUserId(), event.getSessionId());
    }
    
    private void recordSessionActivity(SessionEvent event, String correlationId) {
        UserSession session = sessionRepository.findBySessionId(event.getSessionId())
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        LocalDateTime lastActivity = session.getLastActivityAt();
        Duration inactiveDuration = Duration.between(lastActivity, LocalDateTime.now());
        
        if (inactiveDuration.toMinutes() > SESSION_INACTIVITY_MINUTES) {
            kafkaTemplate.send("session-events", Map.of(
                "userId", event.getUserId(),
                "sessionId", event.getSessionId(),
                "action", "SESSION_EXPIRED",
                "reason", "INACTIVITY_TIMEOUT",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            return;
        }
        
        session.setLastActivityAt(LocalDateTime.now());
        sessionRepository.save(session);
        
        metricsService.recordSessionActivity(event.getSessionId());
    }
    
    private void expireSession(SessionEvent event, String correlationId) {
        UserSession session = sessionRepository.findBySessionId(event.getSessionId())
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        session.setStatus("EXPIRED");
        session.setExpiredAt(LocalDateTime.now());
        session.setExpirationReason(event.getReason());
        sessionRepository.save(session);
        
        notificationService.sendNotification(event.getUserId(), "Session Expired",
            "Your session has expired due to inactivity. Please log in again.",
            correlationId);
        
        metricsService.recordSessionExpired(event.getReason());
        
        log.info("Session expired: userId={}, sessionId={}, reason={}", 
            event.getUserId(), event.getSessionId(), event.getReason());
    }
    
    private void terminateSession(SessionEvent event, String correlationId) {
        UserSession session = sessionRepository.findBySessionId(event.getSessionId())
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        session.setStatus("TERMINATED");
        session.setTerminatedAt(LocalDateTime.now());
        session.setTerminationReason(event.getReason());
        sessionRepository.save(session);
        
        sessionService.revokeSessionTokens(event.getSessionId());
        
        metricsService.recordSessionTerminated(event.getReason());
        
        log.warn("Session terminated: userId={}, sessionId={}, reason={}", 
            event.getUserId(), event.getSessionId(), event.getReason());
    }
    
    private void logoutSession(SessionEvent event, String correlationId) {
        UserSession session = sessionRepository.findBySessionId(event.getSessionId())
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        session.setStatus("LOGGED_OUT");
        session.setLoggedOutAt(LocalDateTime.now());
        sessionRepository.save(session);
        
        sessionService.revokeSessionTokens(event.getSessionId());
        
        metricsService.recordSessionLogout();
        
        log.info("User logged out: userId={}, sessionId={}", 
            event.getUserId(), event.getSessionId());
    }
    
    private void handleConcurrentSessionLimit(SessionEvent event, String correlationId) {
        List<UserSession> activeSessions = sessionRepository.findActiveSessionsByUserId(event.getUserId());
        
        if (activeSessions.size() > MAX_CONCURRENT_SESSIONS) {
            activeSessions.stream()
                .sorted(Comparator.comparing(UserSession::getLastActivityAt))
                .limit(activeSessions.size() - MAX_CONCURRENT_SESSIONS)
                .forEach(session -> {
                    kafkaTemplate.send("session-events", Map.of(
                        "userId", event.getUserId(),
                        "sessionId", session.getSessionId(),
                        "action", "SESSION_TERMINATED",
                        "reason", "CONCURRENT_SESSION_LIMIT",
                        "correlationId", correlationId,
                        "timestamp", Instant.now()
                    ));
                });
        }
        
        metricsService.recordConcurrentSessionLimitExceeded(event.getUserId());
        
        log.warn("Concurrent session limit exceeded: userId={}, activeCount={}", 
            event.getUserId(), activeSessions.size());
    }
    
    private void handleSuspiciousSession(SessionEvent event, String correlationId) {
        UserSession session = sessionRepository.findBySessionId(event.getSessionId())
            .orElseThrow(() -> new RuntimeException("Session not found"));
        
        session.setStatus("SUSPICIOUS");
        session.setFlaggedAt(LocalDateTime.now());
        session.setSuspiciousReason(event.getReason());
        sessionRepository.save(session);
        
        kafkaTemplate.send("security-incidents", Map.of(
            "userId", event.getUserId(),
            "sessionId", event.getSessionId(),
            "incidentType", "SUSPICIOUS_SESSION",
            "reason", event.getReason(),
            "ipAddress", event.getIpAddress(),
            "deviceType", event.getDeviceType(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        notificationService.sendNotification(event.getUserId(), "Suspicious Activity Detected",
            String.format("We detected suspicious activity on your account from %s. If this wasn't you, please secure your account immediately.", 
                event.getDeviceType()),
            correlationId);
        
        metricsService.recordSuspiciousSessionDetected(event.getReason());
        
        log.error("Suspicious session detected: userId={}, sessionId={}, reason={}, ip={}", 
            event.getUserId(), event.getSessionId(), event.getReason(), event.getIpAddress());
    }
}