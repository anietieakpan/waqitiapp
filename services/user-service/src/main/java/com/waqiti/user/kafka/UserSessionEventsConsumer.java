package com.waqiti.user.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.user.model.User;
import com.waqiti.user.model.UserSession;
import com.waqiti.user.model.SessionStatus;
import com.waqiti.user.model.SessionType;
import com.waqiti.user.model.SessionAuditLog;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.UserSessionService;
import com.waqiti.user.service.SessionSecurityService;
import com.waqiti.user.service.DeviceService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.security.SecurityContextService;
import com.waqiti.common.exception.ValidationException;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.SystemException;
import com.waqiti.common.kafka.KafkaMessage;
import com.waqiti.common.kafka.KafkaHeaders;
import com.waqiti.common.monitoring.MetricsService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.validation.ValidationService;
import com.waqiti.common.vault.VaultSecretManager;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserSessionEventsConsumer {

    private static final Logger logger = LoggerFactory.getLogger(UserSessionEventsConsumer.class);
    private static final String CONSUMER_NAME = "user-session-events-consumer";
    private static final String DLQ_TOPIC = "user-session-events-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 12;

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final UserSessionService userSessionService;
    private final SessionSecurityService sessionSecurityService;
    private final DeviceService deviceService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final SecurityContextService securityContextService;
    private final ValidationService validationService;
    private final VaultSecretManager vaultSecretManager;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.consumer.user-session-events.enabled:true}")
    private boolean consumerEnabled;

    @Value("${user.session.max-concurrent-sessions:5}")
    private int maxConcurrentSessions;

    @Value("${user.session.idle-timeout-minutes:30}")
    private int idleTimeoutMinutes;

    @Value("${user.session.max-session-duration-hours:12}")
    private int maxSessionDurationHours;

    @Value("${user.session.security-monitoring-enabled:true}")
    private boolean securityMonitoringEnabled;

    @Value("${user.session.concurrent-session-alerts:true}")
    private boolean concurrentSessionAlertsEnabled;

    @Value("${user.session.suspicious-activity-detection:true}")
    private boolean suspiciousActivityDetectionEnabled;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter sessionEventTypeCounters;

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("user_session_events_processed_total")
                .description("Total processed user session events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("user_session_events_errors_total")
                .description("Total user session events processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("user_session_events_dlq_total")
                .description("Total user session events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("user_session_events_processing_duration")
                .description("User session events processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        logger.info("UserSessionEventsConsumer metrics initialized");
    }

    @KafkaListener(
        topics = "${kafka.topics.user-session-events:user-session-events}",
        groupId = "${kafka.consumer.group-id:user-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "user-session-events-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "user-session-events-retry")
    public void processUserSessionEvents(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.CORRELATION_ID, required = false) String correlationId,
            @Header(name = KafkaHeaders.TRACE_ID, required = false) String traceId,
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String messageId = UUID.randomUUID().toString();

        try {
            MDC.put("messageId", messageId);
            MDC.put("correlationId", correlationId != null ? correlationId : messageId);
            MDC.put("traceId", traceId != null ? traceId : messageId);
            MDC.put("topic", topic);
            MDC.put("partition", String.valueOf(partition));
            MDC.put("offset", String.valueOf(offset));

            if (!consumerEnabled) {
                logger.warn("User session events consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.info("Processing user session event message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidUserSessionEventMessage(messageNode)) {
                logger.error("Invalid user session event message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String userId = messageNode.get("userId").asText();
            String eventType = messageNode.get("eventType").asText();

            long startTime = System.currentTimeMillis();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    processSessionEvent(messageNode, userId, eventType, correlationId, traceId);
                } catch (Exception e) {
                    logger.error("Error in async processing: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Successfully processed user session event: messageId={}, userId={}, eventType={}, processingTime={}ms",
                    messageId, userId, eventType, processingTime);

            processedCounter.increment();
            metricsService.recordUserSessionEventProcessed(eventType, processingTime);
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse user session event message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing user session event: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private boolean isValidUserSessionEventMessage(JsonNode messageNode) {
        try {
            return messageNode != null &&
                   messageNode.has("userId") && StringUtils.hasText(messageNode.get("userId").asText()) &&
                   messageNode.has("eventType") && StringUtils.hasText(messageNode.get("eventType").asText()) &&
                   messageNode.has("timestamp") &&
                   messageNode.has("sessionId");
        } catch (Exception e) {
            logger.error("Error validating user session event message: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    private void processSessionEvent(JsonNode messageNode, String userId, String eventType, 
                                   String correlationId, String traceId) {
        try {
            User user = userService.findById(userId);
            if (user == null) {
                throw new ValidationException("User not found: " + userId);
            }

            UserSessionEvent sessionEvent = parseUserSessionEvent(messageNode);
            
            validateSessionEvent(sessionEvent, user);
            
            switch (sessionEvent.getEventType()) {
                case SESSION_STARTED:
                    processSessionStarted(sessionEvent, user, correlationId, traceId);
                    break;
                case SESSION_ENDED:
                    processSessionEnded(sessionEvent, user, correlationId, traceId);
                    break;
                case SESSION_RENEWED:
                    processSessionRenewed(sessionEvent, user, correlationId, traceId);
                    break;
                case SESSION_EXPIRED:
                    processSessionExpired(sessionEvent, user, correlationId, traceId);
                    break;
                case SESSION_TIMEOUT:
                    processSessionTimeout(sessionEvent, user, correlationId, traceId);
                    break;
                case SESSION_INVALIDATED:
                    processSessionInvalidated(sessionEvent, user, correlationId, traceId);
                    break;
                case CONCURRENT_SESSION_DETECTED:
                    processConcurrentSessionDetected(sessionEvent, user, correlationId, traceId);
                    break;
                case SUSPICIOUS_SESSION_ACTIVITY:
                    processSuspiciousSessionActivity(sessionEvent, user, correlationId, traceId);
                    break;
                case SESSION_HIJACKING_DETECTED:
                    processSessionHijackingDetected(sessionEvent, user, correlationId, traceId);
                    break;
                case DEVICE_CHANGE_DETECTED:
                    processDeviceChangeDetected(sessionEvent, user, correlationId, traceId);
                    break;
                case LOCATION_CHANGE_DETECTED:
                    processLocationChangeDetected(sessionEvent, user, correlationId, traceId);
                    break;
                case SESSION_ACTIVITY_UPDATE:
                    processSessionActivityUpdate(sessionEvent, user, correlationId, traceId);
                    break;
                case SESSION_LOCKED:
                    processSessionLocked(sessionEvent, user, correlationId, traceId);
                    break;
                case SESSION_UNLOCKED:
                    processSessionUnlocked(sessionEvent, user, correlationId, traceId);
                    break;
                case IDLE_SESSION_WARNING:
                    processIdleSessionWarning(sessionEvent, user, correlationId, traceId);
                    break;
                default:
                    logger.warn("Unknown session event type: {}", sessionEvent.getEventType());
                    throw new ValidationException("Unknown session event type: " + sessionEvent.getEventType());
            }

            recordSessionEventAudit(sessionEvent, user, correlationId, traceId);

        } catch (Exception e) {
            logger.error("Error processing session event for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    private UserSessionEvent parseUserSessionEvent(JsonNode messageNode) {
        try {
            UserSessionEvent event = new UserSessionEvent();
            event.setUserId(messageNode.get("userId").asText());
            event.setSessionId(messageNode.get("sessionId").asText());
            event.setEventType(SessionEventType.valueOf(messageNode.get("eventType").asText()));
            event.setTimestamp(LocalDateTime.parse(messageNode.get("timestamp").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            if (messageNode.has("deviceId")) {
                event.setDeviceId(messageNode.get("deviceId").asText());
            }
            
            if (messageNode.has("ipAddress")) {
                event.setIpAddress(messageNode.get("ipAddress").asText());
            }
            
            if (messageNode.has("userAgent")) {
                event.setUserAgent(messageNode.get("userAgent").asText());
            }
            
            if (messageNode.has("location")) {
                JsonNode locationNode = messageNode.get("location");
                Map<String, String> location = new HashMap<>();
                locationNode.fields().forEachRemaining(entry -> {
                    location.put(entry.getKey(), entry.getValue().asText());
                });
                event.setLocation(location);
            }
            
            if (messageNode.has("sessionType")) {
                event.setSessionType(SessionType.valueOf(messageNode.get("sessionType").asText()));
            }
            
            if (messageNode.has("sessionDuration")) {
                event.setSessionDuration(messageNode.get("sessionDuration").asLong());
            }
            
            if (messageNode.has("lastActivity")) {
                event.setLastActivity(LocalDateTime.parse(messageNode.get("lastActivity").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            }
            
            if (messageNode.has("securityFlags")) {
                JsonNode flagsNode = messageNode.get("securityFlags");
                List<String> flags = new ArrayList<>();
                flagsNode.forEach(flag -> flags.add(flag.asText()));
                event.setSecurityFlags(flags);
            }
            
            if (messageNode.has("riskScore")) {
                event.setRiskScore(messageNode.get("riskScore").asDouble());
            }
            
            if (messageNode.has("eventMetadata")) {
                JsonNode metadataNode = messageNode.get("eventMetadata");
                Map<String, Object> metadata = new HashMap<>();
                metadataNode.fields().forEachRemaining(entry -> {
                    metadata.put(entry.getKey(), entry.getValue().asText());
                });
                event.setEventMetadata(metadata);
            }

            return event;
        } catch (Exception e) {
            logger.error("Error parsing user session event: {}", e.getMessage(), e);
            throw new ValidationException("Invalid user session event format: " + e.getMessage());
        }
    }

    private void validateSessionEvent(UserSessionEvent sessionEvent, User user) {
        if (!user.isActive()) {
            throw new ValidationException("User is not active: " + user.getId());
        }
        
        if (sessionEvent.getSessionId() == null || sessionEvent.getSessionId().trim().isEmpty()) {
            throw new ValidationException("Session ID is required");
        }
    }

    private void processSessionStarted(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing session started for user: {}", user.getId());
        
        // Check for concurrent session limits
        int activeSessions = userSessionService.getActiveSessionCount(user.getId());
        if (activeSessions >= maxConcurrentSessions) {
            logger.warn("User {} exceeded concurrent session limit: {} >= {}", 
                       user.getId(), activeSessions, maxConcurrentSessions);
            
            if (concurrentSessionAlertsEnabled) {
                handleConcurrentSessionLimit(user, activeSessions, correlationId, traceId);
            }
        }
        
        // Create new session record
        UserSession session = new UserSession();
        session.setSessionId(sessionEvent.getSessionId());
        session.setUserId(user.getId());
        session.setDeviceId(sessionEvent.getDeviceId());
        session.setIpAddress(sessionEvent.getIpAddress());
        session.setUserAgent(sessionEvent.getUserAgent());
        session.setLocation(sessionEvent.getLocation());
        session.setSessionType(sessionEvent.getSessionType() != null ? sessionEvent.getSessionType() : SessionType.WEB);
        session.setStatus(SessionStatus.ACTIVE);
        session.setStartTime(sessionEvent.getTimestamp());
        session.setLastActivity(sessionEvent.getTimestamp());
        session.setExpiryTime(calculateSessionExpiry(sessionEvent.getTimestamp()));
        
        if (securityMonitoringEnabled) {
            double riskScore = sessionSecurityService.calculateSessionRiskScore(sessionEvent);
            session.setRiskScore(riskScore);
            
            if (riskScore > 0.7) {
                session.setSecurityFlags(List.of("HIGH_RISK_SESSION"));
                triggerSecurityAlert(user, session, "High risk session detected", correlationId);
            }
        }
        
        userSessionService.createSession(session);
        
        // Update user's last login
        user.setLastLoginDate(sessionEvent.getTimestamp());
        user.setLastLoginIp(sessionEvent.getIpAddress());
        userService.updateUser(user);
        
        auditService.recordSessionStarted(user.getId(), sessionEvent.getSessionId(), 
                                        sessionEvent.getDeviceId(), sessionEvent.getIpAddress(), correlationId);
        
        metricsService.recordSessionStarted(user.getId(), sessionEvent.getSessionType());
        
        logger.info("Session started successfully for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
    }

    private LocalDateTime calculateSessionExpiry(LocalDateTime startTime) {
        return startTime.plusHours(maxSessionDurationHours);
    }

    private void handleConcurrentSessionLimit(User user, int activeSessions, String correlationId, String traceId) {
        logger.info("Handling concurrent session limit for user: {}, activeSessions: {}", user.getId(), activeSessions);
        
        // Terminate oldest session
        Optional<UserSession> oldestSession = userSessionService.getOldestActiveSession(user.getId());
        if (oldestSession.isPresent()) {
            UserSession sessionToTerminate = oldestSession.get();
            userSessionService.terminateSession(sessionToTerminate.getSessionId(), "CONCURRENT_LIMIT_EXCEEDED");
            
            logger.info("Terminated oldest session {} for user {} due to concurrent limit", 
                       sessionToTerminate.getSessionId(), user.getId());
        }
        
        // Send notification
        notificationService.sendConcurrentSessionAlert(user.getId(), activeSessions, maxConcurrentSessions, correlationId);
        
        auditService.recordConcurrentSessionLimitExceeded(user.getId(), activeSessions, maxConcurrentSessions, correlationId);
    }

    private void processSessionEnded(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing session ended for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            session.setStatus(SessionStatus.ENDED);
            session.setEndTime(sessionEvent.getTimestamp());
            session.setSessionDuration(ChronoUnit.SECONDS.between(session.getStartTime(), sessionEvent.getTimestamp()));
            
            userSessionService.updateSession(session);
            
            auditService.recordSessionEnded(user.getId(), sessionEvent.getSessionId(), 
                                          session.getSessionDuration(), "USER_LOGOUT", correlationId);
            
            metricsService.recordSessionEnded(user.getId(), session.getSessionDuration());
            
            logger.info("Session ended successfully for user: {}, sessionId: {}, duration: {}s", 
                       user.getId(), sessionEvent.getSessionId(), session.getSessionDuration());
        } else {
            logger.warn("Session not found for termination: {}", sessionEvent.getSessionId());
        }
    }

    private void processSessionRenewed(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing session renewed for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            session.setLastActivity(sessionEvent.getTimestamp());
            session.setExpiryTime(calculateSessionExpiry(sessionEvent.getTimestamp()));
            
            userSessionService.updateSession(session);
            
            auditService.recordSessionRenewed(user.getId(), sessionEvent.getSessionId(), correlationId);
            
            logger.info("Session renewed successfully for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        } else {
            logger.warn("Session not found for renewal: {}", sessionEvent.getSessionId());
        }
    }

    private void processSessionExpired(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing session expired for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            session.setStatus(SessionStatus.EXPIRED);
            session.setEndTime(sessionEvent.getTimestamp());
            session.setSessionDuration(ChronoUnit.SECONDS.between(session.getStartTime(), sessionEvent.getTimestamp()));
            
            userSessionService.updateSession(session);
            
            auditService.recordSessionExpired(user.getId(), sessionEvent.getSessionId(), 
                                            session.getSessionDuration(), correlationId);
            
            metricsService.recordSessionExpired(user.getId());
            
            logger.info("Session expired for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        }
    }

    private void processSessionTimeout(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing session timeout for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            session.setStatus(SessionStatus.TIMEOUT);
            session.setEndTime(sessionEvent.getTimestamp());
            session.setSessionDuration(ChronoUnit.SECONDS.between(session.getStartTime(), sessionEvent.getTimestamp()));
            
            userSessionService.updateSession(session);
            
            auditService.recordSessionTimeout(user.getId(), sessionEvent.getSessionId(), 
                                            session.getSessionDuration(), correlationId);
            
            metricsService.recordSessionTimeout(user.getId());
            
            logger.info("Session timed out for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        }
    }

    private void processSessionInvalidated(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing session invalidated for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            session.setStatus(SessionStatus.INVALIDATED);
            session.setEndTime(sessionEvent.getTimestamp());
            session.setSessionDuration(ChronoUnit.SECONDS.between(session.getStartTime(), sessionEvent.getTimestamp()));
            
            String invalidationReason = sessionEvent.getEventMetadata() != null ? 
                                      (String) sessionEvent.getEventMetadata().get("reason") : "SECURITY_VIOLATION";
            session.setInvalidationReason(invalidationReason);
            
            userSessionService.updateSession(session);
            
            auditService.recordSessionInvalidated(user.getId(), sessionEvent.getSessionId(), 
                                                 invalidationReason, correlationId);
            
            metricsService.recordSessionInvalidated(user.getId(), invalidationReason);
            
            logger.info("Session invalidated for user: {}, sessionId: {}, reason: {}", 
                       user.getId(), sessionEvent.getSessionId(), invalidationReason);
        }
    }

    private void processConcurrentSessionDetected(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing concurrent session detected for user: {}", user.getId());
        
        int activeSessions = userSessionService.getActiveSessionCount(user.getId());
        
        if (concurrentSessionAlertsEnabled) {
            notificationService.sendConcurrentSessionAlert(user.getId(), activeSessions, 
                                                          maxConcurrentSessions, correlationId);
            
            triggerSecurityAlert(user, null, "Concurrent session detected: " + activeSessions + " active sessions", correlationId);
        }
        
        auditService.recordConcurrentSessionDetected(user.getId(), activeSessions, correlationId);
        
        metricsService.recordConcurrentSessionDetected(user.getId(), activeSessions);
        
        logger.info("Concurrent session detected for user: {}, activeSessions: {}", user.getId(), activeSessions);
    }

    private void processSuspiciousSessionActivity(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing suspicious session activity for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            List<String> securityFlags = new ArrayList<>(session.getSecurityFlags() != null ? session.getSecurityFlags() : new ArrayList<>());
            securityFlags.add("SUSPICIOUS_ACTIVITY");
            session.setSecurityFlags(securityFlags);
            
            if (sessionEvent.getRiskScore() != null) {
                session.setRiskScore(sessionEvent.getRiskScore());
            }
            
            userSessionService.updateSession(session);
            
            String activityDetails = sessionEvent.getEventMetadata() != null ? 
                                   sessionEvent.getEventMetadata().toString() : "Unknown suspicious activity";
            
            triggerSecurityAlert(user, session, "Suspicious session activity detected: " + activityDetails, correlationId);
            
            auditService.recordSuspiciousSessionActivity(user.getId(), sessionEvent.getSessionId(), 
                                                       activityDetails, sessionEvent.getRiskScore(), correlationId);
            
            metricsService.recordSuspiciousSessionActivity(user.getId());
            
            logger.warn("Suspicious activity detected for user: {}, sessionId: {}, details: {}", 
                       user.getId(), sessionEvent.getSessionId(), activityDetails);
        }
    }

    private void processSessionHijackingDetected(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.error("Processing session hijacking detected for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        // Immediately invalidate the session
        userSessionService.terminateSession(sessionEvent.getSessionId(), "SESSION_HIJACKING_DETECTED");
        
        // Invalidate all sessions for the user
        userSessionService.terminateAllUserSessions(user.getId(), "SECURITY_INCIDENT");
        
        // Lock the user account temporarily
        user.setAccountLocked(true);
        user.setLockReason("Session hijacking detected");
        user.setLockTimestamp(LocalDateTime.now());
        userService.updateUser(user);
        
        // Send immediate security alert
        notificationService.sendSecurityAlert(user.getId(), "Session hijacking detected. All sessions terminated and account temporarily locked.", correlationId);
        
        triggerSecurityAlert(user, null, "SESSION HIJACKING DETECTED - Account locked", correlationId);
        
        // Create security incident
        createSecurityIncident(user.getId(), "SESSION_HIJACKING", sessionEvent, correlationId);
        
        auditService.recordSessionHijackingDetected(user.getId(), sessionEvent.getSessionId(), correlationId);
        
        metricsService.recordSessionHijackingDetected(user.getId());
        
        logger.error("Session hijacking detected - terminated all sessions and locked account for user: {}", user.getId());
    }

    private void processDeviceChangeDetected(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing device change detected for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            String previousDeviceId = session.getDeviceId();
            String newDeviceId = sessionEvent.getDeviceId();
            
            session.setDeviceId(newDeviceId);
            session.setDeviceChangeDetected(true);
            session.setLastDeviceChange(sessionEvent.getTimestamp());
            
            List<String> securityFlags = new ArrayList<>(session.getSecurityFlags() != null ? session.getSecurityFlags() : new ArrayList<>());
            securityFlags.add("DEVICE_CHANGE");
            session.setSecurityFlags(securityFlags);
            
            userSessionService.updateSession(session);
            
            // Register new device if not already known
            if (!deviceService.isKnownDevice(user.getId(), newDeviceId)) {
                deviceService.registerDevice(user.getId(), newDeviceId, sessionEvent.getUserAgent(), 
                                           sessionEvent.getIpAddress(), sessionEvent.getTimestamp());
                
                // Send new device notification
                notificationService.sendNewDeviceAlert(user.getId(), newDeviceId, 
                                                      sessionEvent.getIpAddress(), correlationId);
            }
            
            auditService.recordDeviceChange(user.getId(), sessionEvent.getSessionId(), 
                                          previousDeviceId, newDeviceId, correlationId);
            
            metricsService.recordDeviceChange(user.getId());
            
            logger.info("Device change detected for user: {}, sessionId: {}, {} -> {}", 
                       user.getId(), sessionEvent.getSessionId(), previousDeviceId, newDeviceId);
        }
    }

    private void processLocationChangeDetected(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing location change detected for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            Map<String, String> previousLocation = session.getLocation();
            Map<String, String> newLocation = sessionEvent.getLocation();
            
            session.setLocation(newLocation);
            session.setLocationChangeDetected(true);
            session.setLastLocationChange(sessionEvent.getTimestamp());
            
            // Calculate location distance for risk assessment
            double distance = sessionSecurityService.calculateLocationDistance(previousLocation, newLocation);
            session.setLocationChangeDistance(distance);
            
            // Check for impossible travel
            if (sessionSecurityService.isImpossibleTravel(previousLocation, newLocation, 
                                                         session.getLastLocationChange(), sessionEvent.getTimestamp())) {
                List<String> securityFlags = new ArrayList<>(session.getSecurityFlags() != null ? session.getSecurityFlags() : new ArrayList<>());
                securityFlags.add("IMPOSSIBLE_TRAVEL");
                session.setSecurityFlags(securityFlags);
                
                triggerSecurityAlert(user, session, "Impossible travel detected", correlationId);
                
                logger.warn("Impossible travel detected for user: {}, distance: {}km", user.getId(), distance);
            }
            
            userSessionService.updateSession(session);
            
            auditService.recordLocationChange(user.getId(), sessionEvent.getSessionId(), 
                                            previousLocation, newLocation, distance, correlationId);
            
            metricsService.recordLocationChange(user.getId(), distance);
            
            logger.info("Location change detected for user: {}, sessionId: {}, distance: {}km", 
                       user.getId(), sessionEvent.getSessionId(), distance);
        }
    }

    private void processSessionActivityUpdate(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.debug("Processing session activity update for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            session.setLastActivity(sessionEvent.getTimestamp());
            
            // Update activity metrics
            if (sessionEvent.getEventMetadata() != null) {
                String activityType = (String) sessionEvent.getEventMetadata().get("activityType");
                if (activityType != null) {
                    session.setLastActivityType(activityType);
                }
            }
            
            userSessionService.updateSession(session);
            
            // Check for idle timeout warning
            long idleMinutes = ChronoUnit.MINUTES.between(session.getLastActivity(), LocalDateTime.now());
            if (idleMinutes >= (idleTimeoutMinutes - 5)) { // 5 minutes before timeout
                scheduleIdleTimeoutWarning(session, correlationId);
            }
            
            logger.debug("Session activity updated for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        }
    }

    private void processSessionLocked(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing session locked for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            session.setStatus(SessionStatus.LOCKED);
            session.setLockTimestamp(sessionEvent.getTimestamp());
            
            String lockReason = sessionEvent.getEventMetadata() != null ? 
                              (String) sessionEvent.getEventMetadata().get("reason") : "Manual lock";
            session.setLockReason(lockReason);
            
            userSessionService.updateSession(session);
            
            auditService.recordSessionLocked(user.getId(), sessionEvent.getSessionId(), lockReason, correlationId);
            
            metricsService.recordSessionLocked(user.getId(), lockReason);
            
            logger.info("Session locked for user: {}, sessionId: {}, reason: {}", 
                       user.getId(), sessionEvent.getSessionId(), lockReason);
        }
    }

    private void processSessionUnlocked(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing session unlocked for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            session.setStatus(SessionStatus.ACTIVE);
            session.setUnlockTimestamp(sessionEvent.getTimestamp());
            session.setLockReason(null);
            
            userSessionService.updateSession(session);
            
            auditService.recordSessionUnlocked(user.getId(), sessionEvent.getSessionId(), correlationId);
            
            metricsService.recordSessionUnlocked(user.getId());
            
            logger.info("Session unlocked for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        }
    }

    private void processIdleSessionWarning(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        logger.info("Processing idle session warning for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        
        UserSession session = userSessionService.getSession(sessionEvent.getSessionId());
        if (session != null) {
            session.setIdleWarningIssued(true);
            session.setIdleWarningTime(sessionEvent.getTimestamp());
            
            userSessionService.updateSession(session);
            
            // Send idle warning notification
            notificationService.sendIdleSessionWarning(user.getId(), sessionEvent.getSessionId(), 
                                                      idleTimeoutMinutes, correlationId);
            
            auditService.recordIdleSessionWarning(user.getId(), sessionEvent.getSessionId(), correlationId);
            
            metricsService.recordIdleSessionWarning(user.getId());
            
            logger.info("Idle session warning issued for user: {}, sessionId: {}", user.getId(), sessionEvent.getSessionId());
        }
    }

    private void scheduleIdleTimeoutWarning(UserSession session, String correlationId) {
        logger.debug("Scheduling idle timeout warning for session: {}", session.getSessionId());
        
        Map<String, Object> warningEvent = new HashMap<>();
        warningEvent.put("userId", session.getUserId());
        warningEvent.put("sessionId", session.getSessionId());
        warningEvent.put("eventType", "IDLE_SESSION_WARNING");
        warningEvent.put("scheduledTime", LocalDateTime.now().plusMinutes(5).toString());
        warningEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("scheduled-events", warningEvent);
    }

    private void triggerSecurityAlert(User user, UserSession session, String alertMessage, String correlationId) {
        logger.warn("Triggering security alert for user: {}, message: {}", user.getId(), alertMessage);
        
        Map<String, Object> securityAlert = new HashMap<>();
        securityAlert.put("userId", user.getId());
        securityAlert.put("alertType", "SESSION_SECURITY");
        securityAlert.put("severity", "HIGH");
        securityAlert.put("message", alertMessage);
        securityAlert.put("timestamp", LocalDateTime.now().toString());
        securityAlert.put("correlationId", correlationId);
        
        if (session != null) {
            securityAlert.put("sessionId", session.getSessionId());
            securityAlert.put("deviceId", session.getDeviceId());
            securityAlert.put("ipAddress", session.getIpAddress());
        }
        
        kafkaTemplate.send("security-alerts", securityAlert);
    }

    private void createSecurityIncident(String userId, String incidentType, UserSessionEvent sessionEvent, String correlationId) {
        logger.error("Creating security incident for user: {}, type: {}", userId, incidentType);
        
        Map<String, Object> incident = new HashMap<>();
        incident.put("userId", userId);
        incident.put("incidentType", incidentType);
        incident.put("severity", "CRITICAL");
        incident.put("sessionId", sessionEvent.getSessionId());
        incident.put("deviceId", sessionEvent.getDeviceId());
        incident.put("ipAddress", sessionEvent.getIpAddress());
        incident.put("timestamp", sessionEvent.getTimestamp().toString());
        incident.put("eventMetadata", sessionEvent.getEventMetadata());
        incident.put("correlationId", correlationId);
        
        kafkaTemplate.send("security-incidents", incident);
    }

    private void recordSessionEventAudit(UserSessionEvent sessionEvent, User user, String correlationId, String traceId) {
        SessionAuditLog auditLog = new SessionAuditLog();
        auditLog.setUserId(user.getId());
        auditLog.setSessionId(sessionEvent.getSessionId());
        auditLog.setEventType(sessionEvent.getEventType());
        auditLog.setDeviceId(sessionEvent.getDeviceId());
        auditLog.setIpAddress(sessionEvent.getIpAddress());
        auditLog.setUserAgent(sessionEvent.getUserAgent());
        auditLog.setLocation(sessionEvent.getLocation());
        auditLog.setTimestamp(sessionEvent.getTimestamp());
        auditLog.setCorrelationId(correlationId);
        auditLog.setTraceId(traceId);
        
        auditService.recordSessionEventAudit(auditLog);
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing user session event message: {}", error.getMessage(), error);
            
            sendToDlq(message, topic, error.getMessage(), error, correlationId, traceId);
            acknowledgment.acknowledge();
            
        } catch (Exception dlqError) {
            logger.error("Failed to send message to DLQ: {}", dlqError.getMessage(), dlqError);
            acknowledgment.nack();
        }
    }

    private void sendToDlq(String originalMessage, String originalTopic, String errorReason, 
                          Exception error, String correlationId, String traceId) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalMessage", originalMessage);
            dlqMessage.put("originalTopic", originalTopic);
            dlqMessage.put("errorReason", errorReason);
            dlqMessage.put("errorTimestamp", LocalDateTime.now().toString());
            dlqMessage.put("correlationId", correlationId);
            dlqMessage.put("traceId", traceId);
            dlqMessage.put("consumerName", CONSUMER_NAME);
            
            if (error != null) {
                dlqMessage.put("errorClass", error.getClass().getSimpleName());
                dlqMessage.put("errorMessage", error.getMessage());
            }
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            dlqCounter.increment();
            
            logger.info("Sent message to DLQ: topic={}, reason={}", DLQ_TOPIC, errorReason);
            
        } catch (Exception e) {
            logger.error("Failed to send message to DLQ: {}", e.getMessage(), e);
        }
    }

    public void handleCircuitBreakerFallback(String message, String topic, int partition, long offset,
                                           String correlationId, String traceId, ConsumerRecord<String, String> record,
                                           Acknowledgment acknowledgment, Exception ex) {
        logger.error("Circuit breaker fallback triggered for user session events consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    public enum SessionEventType {
        SESSION_STARTED,
        SESSION_ENDED,
        SESSION_RENEWED,
        SESSION_EXPIRED,
        SESSION_TIMEOUT,
        SESSION_INVALIDATED,
        CONCURRENT_SESSION_DETECTED,
        SUSPICIOUS_SESSION_ACTIVITY,
        SESSION_HIJACKING_DETECTED,
        DEVICE_CHANGE_DETECTED,
        LOCATION_CHANGE_DETECTED,
        SESSION_ACTIVITY_UPDATE,
        SESSION_LOCKED,
        SESSION_UNLOCKED,
        IDLE_SESSION_WARNING
    }

    public static class UserSessionEvent {
        private String userId;
        private String sessionId;
        private SessionEventType eventType;
        private LocalDateTime timestamp;
        private String deviceId;
        private String ipAddress;
        private String userAgent;
        private Map<String, String> location;
        private SessionType sessionType;
        private Long sessionDuration;
        private LocalDateTime lastActivity;
        private List<String> securityFlags;
        private Double riskScore;
        private Map<String, Object> eventMetadata;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public SessionEventType getEventType() { return eventType; }
        public void setEventType(SessionEventType eventType) { this.eventType = eventType; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

        public Map<String, String> getLocation() { return location; }
        public void setLocation(Map<String, String> location) { this.location = location; }

        public SessionType getSessionType() { return sessionType; }
        public void setSessionType(SessionType sessionType) { this.sessionType = sessionType; }

        public Long getSessionDuration() { return sessionDuration; }
        public void setSessionDuration(Long sessionDuration) { this.sessionDuration = sessionDuration; }

        public LocalDateTime getLastActivity() { return lastActivity; }
        public void setLastActivity(LocalDateTime lastActivity) { this.lastActivity = lastActivity; }

        public List<String> getSecurityFlags() { return securityFlags; }
        public void setSecurityFlags(List<String> securityFlags) { this.securityFlags = securityFlags; }

        public Double getRiskScore() { return riskScore; }
        public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

        public Map<String, Object> getEventMetadata() { return eventMetadata; }
        public void setEventMetadata(Map<String, Object> eventMetadata) { this.eventMetadata = eventMetadata; }
    }
}