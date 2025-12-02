package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import com.waqiti.user.domain.*;
import com.waqiti.user.dto.SecurityAlertEvent;
import com.waqiti.user.repository.UserRepository;
import com.waqiti.user.security.JwtTokenProvider;
import com.waqiti.user.security.RedisTokenRevocationService;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.AuthService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Security alert consumer for user-service.
 * Handles security events that require immediate action on user accounts.
 * This consumer protects user accounts from various security threats.
 */
@Slf4j
@Component
public class SecurityAlertConsumer {
    
    private final UserRepository userRepository;
    private final UserService userService;
    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final RedisTokenRevocationService tokenRevocationService;
    private final UniversalDLQHandler dlqHandler;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    
    // Active incident tracking
    private final Map<String, SecurityIncident> activeIncidents = new ConcurrentHashMap<>();
    
    // Metrics
    private final Counter alertsProcessed;
    private final Counter accountsLocked;
    private final Counter passwordResetsForced;
    private final Counter sessionsInvalidated;
    private final Counter alertsFailed;
    private final Timer alertProcessingTimer;
    
    // Configuration
    @Value("${security.auto-lock.enabled:true}")
    private boolean autoLockEnabled;
    
    @Value("${security.auto-lock.threshold:0.8}")
    private double autoLockThreshold;
    
    @Value("${security.incident.retention.hours:72}")
    private int incidentRetentionHours;
    
    @Value("${security.notification.delay.seconds:5}")
    private int notificationDelaySeconds;
    
    @Value("${security.max.retry.attempts:3}")
    private int maxRetryAttempts;
    
    public SecurityAlertConsumer(
            UserRepository userRepository,
            UserService userService,
            AuthService authService,
            JwtTokenProvider jwtTokenProvider,
            RedisTokenRevocationService tokenRevocationService,
            KafkaTemplate<String, Object> kafkaTemplate,
            RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry) {
        
        this.userRepository = userRepository;
        this.userService = userService;
        this.authService = authService;
        this.jwtTokenProvider = jwtTokenProvider;
        this.tokenRevocationService = tokenRevocationService;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        
        // Initialize metrics
        this.alertsProcessed = Counter.builder("security.alerts.processed")
            .description("Total security alerts processed")
            .register(meterRegistry);
        
        this.accountsLocked = Counter.builder("security.accounts.locked")
            .description("Total accounts locked due to security threats")
            .register(meterRegistry);
        
        this.passwordResetsForced = Counter.builder("security.passwords.reset")
            .description("Total forced password resets")
            .register(meterRegistry);
        
        this.sessionsInvalidated = Counter.builder("security.sessions.invalidated")
            .description("Total sessions invalidated")
            .register(meterRegistry);
        
        this.alertsFailed = Counter.builder("security.alerts.failed")
            .description("Total alert processing failures")
            .register(meterRegistry);
        
        this.alertProcessingTimer = Timer.builder("security.alert.processing.time")
            .description("Security alert processing time")
            .register(meterRegistry);
    }
    
    @KafkaListener(
        topics = "security-alerts",
        groupId = "user-service-security-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 30)
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000, multiplier = 2))
    public void handleSecurityAlert(
            @Valid @Payload SecurityAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start(meterRegistry);
        String correlationId = event.getCorrelationId() != null ? 
            event.getCorrelationId() : UUID.randomUUID().toString();
        
        log.warn("Processing security alert: correlationId={}, userId={}, alertType={}, " +
                "severity={}, threatLevel={}, requiredAction={}, partition={}, offset={}", 
                correlationId, event.getUserId(), event.getAlertType(), 
                event.getSeverity(), event.getThreatLevel(), event.getRequiredAction(),
                partition, offset);
        
        LocalDateTime startTime = LocalDateTime.now();
        SecurityIncident incident = null;
        
        try {
            // Check for duplicate processing
            if (isDuplicateAlert(event.getEventId())) {
                log.warn("Duplicate security alert detected: eventId={}", event.getEventId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Fetch user
            User user = userRepository.findById(event.getUserId())
                .orElseThrow(() -> new UserNotFoundException("User not found: " + event.getUserId()));
            
            // Create or update security incident
            incident = createOrUpdateIncident(user, event, correlationId);
            
            // Execute parallel security checks
            CompletableFuture<SecurityContext> contextFuture = 
                CompletableFuture.supplyAsync(() -> buildSecurityContext(user, event));
            
            CompletableFuture<List<String>> activeSessionsFuture = 
                CompletableFuture.supplyAsync(() -> getActiveSessions(user.getId()));
            
            CompletableFuture<SecurityHistory> historyFuture = 
                CompletableFuture.supplyAsync(() -> getUserSecurityHistory(user.getId()));
            
            // Wait for all security data
            CompletableFuture.allOf(contextFuture, activeSessionsFuture, historyFuture)
                .get(10, TimeUnit.SECONDS);
            
            SecurityContext context = contextFuture.get();
            List<String> activeSessions = activeSessionsFuture.get();
            SecurityHistory history = historyFuture.get();
            
            // Determine response based on threat analysis
            SecurityResponse response = determineSecurityResponse(event, context, history);
            
            // Execute security actions
            List<SecurityAction> executedActions = executeSecurityActions(
                user, event, response, activeSessions, incident);
            
            // Update user security status
            updateUserSecurityStatus(user, event, response, executedActions);
            
            // Send notifications
            if (event.getNotifyUser() || event.getNotifySecurityTeam()) {
                sendSecurityNotifications(user, event, response, incident);
            }
            
            // Publish security event for audit
            publishSecurityAuditEvent(user, event, response, executedActions);
            
            // Update incident status
            incident.setStatus(SecurityIncident.Status.RESOLVED);
            incident.setResolvedAt(LocalDateTime.now());
            incident.setResolutionActions(executedActions);
            
            // Update metrics
            alertsProcessed.increment();
            updateActionMetrics(executedActions);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            long processingTime = Duration.between(startTime, LocalDateTime.now()).toMillis();
            log.info("Successfully processed security alert: correlationId={}, userId={}, " +
                    "response={}, actions={}, processingTime={}ms", 
                    correlationId, event.getUserId(), response.getDecision(), 
                    executedActions.size(), processingTime);
            
        } catch (Exception e) {
            log.error("Error processing security alert: correlationId={}, userId={}, error={}", 
                    correlationId, event.getUserId(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, event),
                e
            ).exceptionally(dlqError -> {
                log.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Processing failed", e);
        } finally {
            sample.stop(alertProcessingTimer);
            clearTemporaryCache(event.getEventId());
        }
    }
    
    /**
     * Determine appropriate security response based on threat analysis
     */
    private SecurityResponse determineSecurityResponse(
            SecurityAlertEvent event, 
            SecurityContext context,
            SecurityHistory history) {
        
        SecurityResponse.Builder response = SecurityResponse.builder();
        
        // Critical threats require immediate action
        if (event.isCritical()) {
            response.decision(SecurityResponse.Decision.IMMEDIATE_ACTION)
                .lockAccount(true)
                .invalidateSessions(true)
                .forcePasswordReset(true)
                .notifySecurityTeam(true)
                .priority(1)
                .reason("Critical security threat detected");
            
            if (event.getAlertType() == SecurityAlertEvent.AlertType.ACCOUNT_TAKEOVER) {
                response.suspendAllServices(true)
                    .freezeTransactions(true);
            }
            
            return response.build();
        }
        
        // Check for authentication threats
        if (event.isAuthenticationThreat()) {
            boolean shouldLock = context.getFailedAttempts() > 5 || 
                                event.getConfidenceScore() > autoLockThreshold;
            
            return response
                .decision(shouldLock ? 
                    SecurityResponse.Decision.LOCK_ACCOUNT : 
                    SecurityResponse.Decision.STEP_UP_AUTH)
                .lockAccount(shouldLock)
                .requireMfa(true)
                .invalidateSessions(event.getFailedAttempts() > 3)
                .monitoringLevel("HIGH")
                .priority(2)
                .reason("Authentication threat detected")
                .build();
        }
        
        // Check for credential compromise
        if (event.requiresPasswordReset()) {
            return response
                .decision(SecurityResponse.Decision.FORCE_PASSWORD_RESET)
                .forcePasswordReset(true)
                .invalidateSessions(true)
                .notifyUser(true)
                .requireMfa(true)
                .priority(2)
                .reason("Credential compromise detected")
                .build();
        }
        
        // Check for suspicious activity patterns
        if (history.hasRecentIncidents(24)) {
            return response
                .decision(SecurityResponse.Decision.ENHANCED_MONITORING)
                .enableEnhancedMonitoring(true)
                .requireMfa(true)
                .restrictFeatures(Arrays.asList("INTERNATIONAL_TRANSFERS", "API_ACCESS"))
                .monitoringLevel("ELEVATED")
                .priority(3)
                .reason("Multiple security incidents detected")
                .build();
        }
        
        // Default response for lower severity alerts
        return response
            .decision(SecurityResponse.Decision.MONITOR)
            .enableEnhancedMonitoring(true)
            .monitoringLevel("STANDARD")
            .priority(4)
            .reason("Security alert logged for monitoring")
            .build();
    }
    
    /**
     * Execute security actions based on response decision
     */
    private List<SecurityAction> executeSecurityActions(
            User user,
            SecurityAlertEvent event,
            SecurityResponse response,
            List<String> activeSessions,
            SecurityIncident incident) {
        
        List<SecurityAction> actions = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        // Lock account if required
        if (response.shouldLockAccount() && autoLockEnabled) {
            try {
                user.setStatus(UserStatus.LOCKED);
                user.setLockedAt(now);
                user.setLockReason("Security threat: " + event.getAlertType());
                userRepository.save(user);
                
                actions.add(SecurityAction.builder()
                    .action("ACCOUNT_LOCKED")
                    .status("SUCCESS")
                    .timestamp(now)
                    .details("Account locked due to " + event.getAlertType())
                    .build());
                
                accountsLocked.increment();
                log.warn("Locked account: userId={}, reason={}", user.getId(), event.getAlertType());
                
            } catch (Exception e) {
                log.error("Failed to lock account: userId={}", user.getId(), e);
                actions.add(SecurityAction.builder()
                    .action("ACCOUNT_LOCK_FAILED")
                    .status("ERROR")
                    .timestamp(now)
                    .errorMessage(e.getMessage())
                    .build());
            }
        }
        
        // Invalidate sessions if required
        if (response.shouldInvalidateSessions() && !activeSessions.isEmpty()) {
            try {
                for (String sessionId : activeSessions) {
                    tokenRevocationService.revokeToken(sessionId);
                }
                
                // Clear all user sessions from Redis
                String sessionKey = "user:sessions:" + user.getId();
                redisTemplate.delete(sessionKey);
                
                actions.add(SecurityAction.builder()
                    .action("SESSIONS_INVALIDATED")
                    .status("SUCCESS")
                    .timestamp(now)
                    .details("Invalidated " + activeSessions.size() + " sessions")
                    .build());
                
                sessionsInvalidated.increment();
                log.info("Invalidated {} sessions for user: {}", activeSessions.size(), user.getId());
                
            } catch (Exception e) {
                log.error("Failed to invalidate sessions: userId={}", user.getId(), e);
            }
        }
        
        // Force password reset if required
        if (response.shouldForcePasswordReset()) {
            try {
                user.setPasswordResetRequired(true);
                user.setPasswordResetRequestedAt(now);
                userRepository.save(user);
                
                // Generate password reset token
                String resetToken = UUID.randomUUID().toString();
                String resetKey = "password:reset:" + resetToken;
                redisTemplate.opsForValue().set(resetKey, user.getId(), Duration.ofHours(24));
                
                actions.add(SecurityAction.builder()
                    .action("PASSWORD_RESET_FORCED")
                    .status("SUCCESS")
                    .timestamp(now)
                    .details("Password reset required on next login")
                    .build());
                
                passwordResetsForced.increment();
                log.info("Forced password reset for user: {}", user.getId());
                
            } catch (Exception e) {
                log.error("Failed to force password reset: userId={}", user.getId(), e);
            }
        }
        
        // Enable MFA if required
        if (response.shouldRequireMfa() && !user.isMfaEnabled()) {
            try {
                user.setMfaRequired(true);
                userRepository.save(user);
                
                actions.add(SecurityAction.builder()
                    .action("MFA_REQUIRED")
                    .status("SUCCESS")
                    .timestamp(now)
                    .details("Multi-factor authentication now required")
                    .build());
                
                log.info("Enabled MFA requirement for user: {}", user.getId());
                
            } catch (Exception e) {
                log.error("Failed to enable MFA requirement: userId={}", user.getId(), e);
            }
        }
        
        // Enable enhanced monitoring
        if (response.shouldEnableMonitoring()) {
            try {
                String monitoringKey = "user:monitoring:" + user.getId();
                Map<String, Object> monitoringConfig = new HashMap<>();
                monitoringConfig.put("level", response.getMonitoringLevel());
                monitoringConfig.put("enabledAt", now);
                monitoringConfig.put("reason", event.getAlertType());
                monitoringConfig.put("expiresAt", now.plusDays(30));
                
                redisTemplate.opsForHash().putAll(monitoringKey, monitoringConfig);
                redisTemplate.expire(monitoringKey, Duration.ofDays(30));
                
                actions.add(SecurityAction.builder()
                    .action("MONITORING_ENABLED")
                    .status("SUCCESS")
                    .timestamp(now)
                    .details("Enhanced monitoring enabled: " + response.getMonitoringLevel())
                    .build());
                
                log.info("Enabled enhanced monitoring for user: {}", user.getId());
                
            } catch (Exception e) {
                log.error("Failed to enable monitoring: userId={}", user.getId(), e);
            }
        }
        
        // Restrict features if required
        if (response.hasRestrictedFeatures()) {
            try {
                List<String> currentRestrictions = user.getRestrictions() != null ? 
                    user.getRestrictions() : new ArrayList<>();
                currentRestrictions.addAll(response.getRestrictedFeatures());
                user.setRestrictions(currentRestrictions);
                user.setRestrictedUntil(now.plusDays(7));
                userRepository.save(user);
                
                actions.add(SecurityAction.builder()
                    .action("FEATURES_RESTRICTED")
                    .status("SUCCESS")
                    .timestamp(now)
                    .details("Restricted features: " + String.join(", ", response.getRestrictedFeatures()))
                    .build());
                
                log.info("Restricted features for user: {}", user.getId());
                
            } catch (Exception e) {
                log.error("Failed to restrict features: userId={}", user.getId(), e);
            }
        }
        
        // Freeze transactions if required
        if (response.shouldFreezeTransactions()) {
            try {
                Map<String, Object> freezeEvent = new HashMap<>();
                freezeEvent.put("userId", user.getId());
                freezeEvent.put("action", "FREEZE");
                freezeEvent.put("reason", event.getAlertType());
                freezeEvent.put("timestamp", now);
                
                kafkaTemplate.send("transaction-control", user.getId(), freezeEvent);
                
                actions.add(SecurityAction.builder()
                    .action("TRANSACTIONS_FROZEN")
                    .status("SUCCESS")
                    .timestamp(now)
                    .details("All transactions frozen")
                    .build());
                
                log.warn("Froze transactions for user: {}", user.getId());
                
            } catch (Exception e) {
                log.error("Failed to freeze transactions: userId={}", user.getId(), e);
            }
        }
        
        return actions;
    }
    
    /**
     * Update user security status in database
     */
    private void updateUserSecurityStatus(User user, SecurityAlertEvent event, 
                                         SecurityResponse response, 
                                         List<SecurityAction> actions) {
        try {
            // Update security metadata
            Map<String, Object> metadata = user.getMetadata() != null ? 
                user.getMetadata() : new HashMap<>();
            
            Map<String, Object> securityData = new HashMap<>();
            securityData.put("lastAlert", event.getAlertType());
            securityData.put("lastAlertTime", event.getTimestamp());
            securityData.put("threatLevel", event.getThreatLevel());
            securityData.put("response", response.getDecision());
            securityData.put("actionsExecuted", actions.stream()
                .map(SecurityAction::getAction)
                .collect(Collectors.toList()));
            
            metadata.put("security", securityData);
            user.setMetadata(metadata);
            
            // Update last security check
            user.setLastSecurityCheck(LocalDateTime.now());
            
            userRepository.save(user);
            
        } catch (Exception e) {
            log.error("Failed to update user security status: userId={}", user.getId(), e);
        }
    }
    
    /**
     * Send security notifications to user and security team
     */
    private void sendSecurityNotifications(User user, SecurityAlertEvent event,
                                          SecurityResponse response,
                                          SecurityIncident incident) {
        
        CompletableFuture.runAsync(() -> {
            try {
                // Add delay to ensure actions are completed
                Thread.sleep(notificationDelaySeconds * 1000);
                
                // Notify user if required
                if (event.getNotifyUser() && !response.shouldLockAccount()) {
                    Map<String, Object> userNotification = new HashMap<>();
                    userNotification.put("type", "SECURITY_ALERT");
                    userNotification.put("userId", user.getId());
                    userNotification.put("email", user.getEmail());
                    userNotification.put("alertType", event.getAlertType());
                    userNotification.put("severity", event.getSeverity());
                    userNotification.put("title", event.getAlertTitle());
                    userNotification.put("description", event.getAlertDescription());
                    userNotification.put("actionsTaken", response.getDecision());
                    userNotification.put("timestamp", LocalDateTime.now());
                    
                    kafkaTemplate.send("user-notifications", user.getId(), userNotification);
                    log.debug("Sent security notification to user: {}", user.getId());
                }
                
                // Notify security team if required
                if (event.getNotifySecurityTeam() || event.isCritical()) {
                    Map<String, Object> teamNotification = new HashMap<>();
                    teamNotification.put("type", "SECURITY_INCIDENT");
                    teamNotification.put("incidentId", incident.getIncidentId());
                    teamNotification.put("userId", user.getId());
                    teamNotification.put("alertType", event.getAlertType());
                    teamNotification.put("severity", event.getSeverity());
                    teamNotification.put("threatLevel", event.getThreatLevel());
                    teamNotification.put("response", response.getDecision());
                    teamNotification.put("priority", response.getPriority());
                    teamNotification.put("timestamp", LocalDateTime.now());
                    
                    kafkaTemplate.send("security-team-notifications", teamNotification);
                    log.info("Notified security team of incident: {}", incident.getIncidentId());
                }
                
            } catch (Exception e) {
                log.error("Failed to send security notifications", e);
            }
        });
    }
    
    /**
     * Publish security audit event for compliance and monitoring
     */
    private void publishSecurityAuditEvent(User user, SecurityAlertEvent event,
                                          SecurityResponse response,
                                          List<SecurityAction> actions) {
        try {
            Map<String, Object> auditEvent = new HashMap<>();
            auditEvent.put("eventType", "SECURITY_ALERT_PROCESSED");
            auditEvent.put("userId", user.getId());
            auditEvent.put("alertId", event.getEventId());
            auditEvent.put("alertType", event.getAlertType());
            auditEvent.put("severity", event.getSeverity());
            auditEvent.put("threatLevel", event.getThreatLevel());
            auditEvent.put("response", response.getDecision());
            auditEvent.put("actions", actions);
            auditEvent.put("sourceIp", event.getSourceIpAddress());
            auditEvent.put("deviceId", event.getDeviceId());
            auditEvent.put("timestamp", LocalDateTime.now());
            auditEvent.put("correlationId", event.getCorrelationId());
            
            kafkaTemplate.send("security-audit-events", user.getId(), auditEvent);
            
        } catch (Exception e) {
            log.error("Failed to publish security audit event", e);
        }
    }
    
    // Helper methods and inner classes
    
    private SecurityIncident createOrUpdateIncident(User user, SecurityAlertEvent event, 
                                                   String correlationId) {
        String incidentKey = user.getId() + ":" + event.getAlertType();
        SecurityIncident incident = activeIncidents.computeIfAbsent(incidentKey, 
            k -> SecurityIncident.builder()
                .incidentId(UUID.randomUUID().toString())
                .userId(user.getId())
                .alertType(event.getAlertType())
                .createdAt(LocalDateTime.now())
                .status(SecurityIncident.Status.ACTIVE)
                .events(new ArrayList<>())
                .build()
        );
        
        incident.getEvents().add(event);
        incident.setSeverity(event.getSeverity());
        incident.setThreatLevel(event.getThreatLevel());
        incident.setLastUpdated(LocalDateTime.now());
        
        return incident;
    }
    
    private SecurityContext buildSecurityContext(User user, SecurityAlertEvent event) {
        return SecurityContext.builder()
            .userId(user.getId())
            .accountStatus(user.getStatus())
            .mfaEnabled(user.isMfaEnabled())
            .lastLogin(user.getLastLoginAt())
            .failedAttempts(event.getFailedAttempts() != null ? event.getFailedAttempts() : 0)
            .knownDevice(event.getKnownDevice())
            .trustedLocation(event.getTrustedLocation())
            .riskScore(calculateUserRiskScore(user, event))
            .build();
    }
    
    private List<String> getActiveSessions(String userId) {
        try {
            String sessionKey = "user:sessions:" + userId;
            Set<Object> sessions = redisTemplate.opsForSet().members(sessionKey);
            return sessions != null ? 
                sessions.stream().map(Object::toString).collect(Collectors.toList()) :
                new ArrayList<>();
        } catch (Exception e) {
            log.error("Error fetching active sessions", e);
            return new ArrayList<>();
        }
    }
    
    private SecurityHistory getUserSecurityHistory(String userId) {
        try {
            String historyKey = "user:security:history:" + userId;
            List<Object> incidents = redisTemplate.opsForList().range(historyKey, 0, 100);
            
            return SecurityHistory.builder()
                .userId(userId)
                .recentIncidents(incidents != null ? incidents.size() : 0)
                .lastIncidentTime(LocalDateTime.now()) // Would fetch from actual data
                .build();
        } catch (Exception e) {
            log.error("Error fetching security history", e);
            return SecurityHistory.builder().userId(userId).recentIncidents(0).build();
        }
    }
    
    private double calculateUserRiskScore(User user, SecurityAlertEvent event) {
        double baseScore = event.getConfidenceScore() / 100.0;
        
        // Adjust based on user factors
        if (!user.isMfaEnabled()) baseScore += 0.1;
        if (user.getStatus() == UserStatus.SUSPENDED) baseScore += 0.2;
        if (Boolean.FALSE.equals(event.getKnownDevice())) baseScore += 0.15;
        if (Boolean.FALSE.equals(event.getTrustedLocation())) baseScore += 0.1;
        
        return Math.min(1.0, baseScore);
    }
    
    private void updateActionMetrics(List<SecurityAction> actions) {
        for (SecurityAction action : actions) {
            if ("SUCCESS".equals(action.getStatus())) {
                Counter.builder("security.action." + action.getAction().toLowerCase())
                    .register(meterRegistry)
                    .increment();
            }
        }
    }
    
    private boolean isDuplicateAlert(String eventId) {
        String key = "security:alert:processed:" + eventId;
        Boolean exists = redisTemplate.hasKey(key);
        if (Boolean.FALSE.equals(exists)) {
            redisTemplate.opsForValue().set(key, true, Duration.ofHours(24));
            return false;
        }
        return true;
    }
        
    private void clearTemporaryCache(String eventId) {
        try {
            redisTemplate.delete("security:alert:processing:" + eventId);
        } catch (Exception e) {
            log.debug("Error clearing temporary cache", e);
        }
    }
        
    // Inner classes
    
    @Data
    @Builder
    private static class SecurityResponse {
        private Decision decision;
        private boolean lockAccount;
        private boolean invalidateSessions;
        private boolean forcePasswordReset;
        private boolean requireMfa;
        private boolean enableEnhancedMonitoring;
        private boolean freezeTransactions;
        private boolean suspendAllServices;
        private boolean notifyUser;
        private boolean notifySecurityTeam;
        private String monitoringLevel;
        private List<String> restrictedFeatures;
        private int priority;
        private String reason;
        
        public enum Decision {
            IMMEDIATE_ACTION,
            LOCK_ACCOUNT,
            FORCE_PASSWORD_RESET,
            STEP_UP_AUTH,
            ENHANCED_MONITORING,
            MONITOR,
            NO_ACTION
        }
        
        public boolean shouldLockAccount() {
            return lockAccount;
        }
        
        public boolean shouldInvalidateSessions() {
            return invalidateSessions;
        }
        
        public boolean shouldForcePasswordReset() {
            return forcePasswordReset;
        }
        
        public boolean shouldRequireMfa() {
            return requireMfa;
        }
        
        public boolean shouldEnableMonitoring() {
            return enableEnhancedMonitoring;
        }
        
        public boolean shouldFreezeTransactions() {
            return freezeTransactions;
        }
        
        public boolean hasRestrictedFeatures() {
            return restrictedFeatures != null && !restrictedFeatures.isEmpty();
        }
    }
    
    @Data
    @Builder
    private static class SecurityContext {
        private String userId;
        private UserStatus accountStatus;
        private boolean mfaEnabled;
        private LocalDateTime lastLogin;
        private int failedAttempts;
        private Boolean knownDevice;
        private Boolean trustedLocation;
        private double riskScore;
    }
    
    @Data
    @Builder
    private static class SecurityHistory {
        private String userId;
        private int recentIncidents;
        private LocalDateTime lastIncidentTime;
        
        public boolean hasRecentIncidents(int hours) {
            return recentIncidents > 0 && lastIncidentTime != null &&
                   lastIncidentTime.isAfter(LocalDateTime.now().minusHours(hours));
        }
    }
    
    @Data
    @Builder
    private static class SecurityIncident {
        private String incidentId;
        private String userId;
        private SecurityAlertEvent.AlertType alertType;
        private SecurityAlertEvent.Severity severity;
        private SecurityAlertEvent.ThreatLevel threatLevel;
        private Status status;
        private LocalDateTime createdAt;
        private LocalDateTime lastUpdated;
        private LocalDateTime resolvedAt;
        private List<SecurityAlertEvent> events;
        private List<SecurityAction> resolutionActions;
        private String errorMessage;
        
        public enum Status {
            ACTIVE, INVESTIGATING, RESOLVED, ERROR
        }
    }
    
    @Data
    @Builder
    private static class SecurityAction {
        private String action;
        private String status;
        private LocalDateTime timestamp;
        private String details;
        private String errorMessage;
    }
}