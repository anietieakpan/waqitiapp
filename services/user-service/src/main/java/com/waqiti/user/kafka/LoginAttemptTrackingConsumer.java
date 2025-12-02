package com.waqiti.user.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.user.model.User;
import com.waqiti.user.model.LoginAttempt;
import com.waqiti.user.model.LoginStatus;
import com.waqiti.user.model.LoginAttemptAuditLog;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.LoginAttemptService;
import com.waqiti.user.service.LoginSecurityService;
import com.waqiti.user.service.UserSecurityService;
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
public class LoginAttemptTrackingConsumer {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptTrackingConsumer.class);
    private static final String CONSUMER_NAME = "login-attempt-tracking-consumer";
    private static final String DLQ_TOPIC = "login-attempt-tracking-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 8;

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final LoginAttemptService loginAttemptService;
    private final LoginSecurityService loginSecurityService;
    private final UserSecurityService userSecurityService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final SecurityContextService securityContextService;
    private final ValidationService validationService;
    private final VaultSecretManager vaultSecretManager;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.consumer.login-attempt-tracking.enabled:true}")
    private boolean consumerEnabled;

    @Value("${login.security.max-failed-attempts:5}")
    private int maxFailedAttempts;

    @Value("${login.security.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;

    @Value("${login.security.tracking-window-minutes:60}")
    private int trackingWindowMinutes;

    @Value("${login.security.suspicious-threshold:3}")
    private int suspiciousThreshold;

    @Value("${login.security.rate-limit-attempts:10}")
    private int rateLimitAttempts;

    @Value("${login.security.rate-limit-window-minutes:15}")
    private int rateLimitWindowMinutes;

    @Value("${login.security.send-notifications:true}")
    private boolean sendNotifications;

    @Value("${login.security.monitor-geographic-anomalies:true}")
    private boolean monitorGeographicAnomalies;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter loginAttemptTypeCounters;

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("login_attempt_tracking_processed_total")
                .description("Total processed login attempt tracking events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("login_attempt_tracking_errors_total")
                .description("Total login attempt tracking processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("login_attempt_tracking_dlq_total")
                .description("Total login attempt tracking events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("login_attempt_tracking_processing_duration")
                .description("Login attempt tracking processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        logger.info("LoginAttemptTrackingConsumer metrics initialized");
    }

    @KafkaListener(
        topics = "${kafka.topics.login-attempt-tracking:login-attempt-tracking}",
        groupId = "${kafka.consumer.group-id:user-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "login-attempt-tracking-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "login-attempt-tracking-retry")
    public void processLoginAttemptTracking(
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
                logger.warn("Login attempt tracking consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.info("Processing login attempt tracking message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidLoginAttemptTrackingMessage(messageNode)) {
                logger.error("Invalid login attempt tracking message format: {}", message);
                sendToDlq(message, topic, "Invalid message format", null, correlationId, traceId);
                acknowledgment.acknowledge();
                return;
            }

            String userId = messageNode.has("userId") ? messageNode.get("userId").asText() : null;
            String username = messageNode.has("username") ? messageNode.get("username").asText() : null;
            String eventType = messageNode.get("eventType").asText();

            long startTime = System.currentTimeMillis();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    processLoginAttemptEvent(messageNode, userId, username, eventType, correlationId, traceId);
                } catch (Exception e) {
                    logger.error("Error in async processing: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Successfully processed login attempt tracking: messageId={}, userId={}, eventType={}, processingTime={}ms",
                    messageId, userId, eventType, processingTime);

            processedCounter.increment();
            metricsService.recordLoginAttemptTrackingProcessed(eventType, processingTime);
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse login attempt tracking message: messageId={}, error={}", messageId, e.getMessage());
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } catch (Exception e) {
            logger.error("Unexpected error processing login attempt tracking: messageId={}, error={}", messageId, e.getMessage(), e);
            handleProcessingError(message, topic, e, correlationId, traceId, acknowledgment);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private boolean isValidLoginAttemptTrackingMessage(JsonNode messageNode) {
        try {
            return messageNode != null &&
                   messageNode.has("eventType") && StringUtils.hasText(messageNode.get("eventType").asText()) &&
                   messageNode.has("timestamp") &&
                   (messageNode.has("userId") || messageNode.has("username")) &&
                   messageNode.has("ipAddress");
        } catch (Exception e) {
            logger.error("Error validating login attempt tracking message: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    private void processLoginAttemptEvent(JsonNode messageNode, String userId, String username, String eventType, 
                                        String correlationId, String traceId) {
        try {
            User user = null;
            if (userId != null) {
                user = userService.findById(userId);
            } else if (username != null) {
                user = userService.findByUsername(username);
            }

            LoginAttemptEvent attemptEvent = parseLoginAttemptEvent(messageNode);
            
            validateLoginAttemptEvent(attemptEvent, user);
            
            switch (attemptEvent.getEventType()) {
                case LOGIN_ATTEMPT:
                    processLoginAttempt(attemptEvent, user, correlationId, traceId);
                    break;
                case LOGIN_SUCCESS:
                    processLoginSuccess(attemptEvent, user, correlationId, traceId);
                    break;
                case LOGIN_FAILURE:
                    processLoginFailure(attemptEvent, user, correlationId, traceId);
                    break;
                case ACCOUNT_LOCKOUT:
                    processAccountLockout(attemptEvent, user, correlationId, traceId);
                    break;
                case SUSPICIOUS_LOGIN:
                    processSuspiciousLogin(attemptEvent, user, correlationId, traceId);
                    break;
                case BRUTE_FORCE_DETECTED:
                    processBruteForceDetected(attemptEvent, user, correlationId, traceId);
                    break;
                case CREDENTIAL_STUFFING:
                    processCredentialStuffing(attemptEvent, user, correlationId, traceId);
                    break;
                case GEOGRAPHIC_ANOMALY:
                    processGeographicAnomaly(attemptEvent, user, correlationId, traceId);
                    break;
                case IMPOSSIBLE_TRAVEL:
                    processImpossibleTravel(attemptEvent, user, correlationId, traceId);
                    break;
                case DEVICE_ANOMALY:
                    processDeviceAnomaly(attemptEvent, user, correlationId, traceId);
                    break;
                case TIME_ANOMALY:
                    processTimeAnomaly(attemptEvent, user, correlationId, traceId);
                    break;
                case RATE_LIMIT_EXCEEDED:
                    processRateLimitExceeded(attemptEvent, user, correlationId, traceId);
                    break;
                case PASSWORD_SPRAY:
                    processPasswordSpray(attemptEvent, user, correlationId, traceId);
                    break;
                case ACCOUNT_ENUMERATION:
                    processAccountEnumeration(attemptEvent, user, correlationId, traceId);
                    break;
                case SECURITY_BYPASS_ATTEMPT:
                    processSecurityBypassAttempt(attemptEvent, user, correlationId, traceId);
                    break;
                default:
                    logger.warn("Unknown login attempt event type: {}", attemptEvent.getEventType());
                    throw new ValidationException("Unknown login attempt event type: " + attemptEvent.getEventType());
            }

            recordLoginAttemptEventAudit(attemptEvent, user, correlationId, traceId);

        } catch (Exception e) {
            logger.error("Error processing login attempt event for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    private LoginAttemptEvent parseLoginAttemptEvent(JsonNode messageNode) {
        try {
            LoginAttemptEvent event = new LoginAttemptEvent();
            
            if (messageNode.has("userId")) {
                event.setUserId(messageNode.get("userId").asText());
            }
            
            if (messageNode.has("username")) {
                event.setUsername(messageNode.get("username").asText());
            }
            
            event.setEventType(LoginAttemptEventType.valueOf(messageNode.get("eventType").asText()));
            event.setTimestamp(LocalDateTime.parse(messageNode.get("timestamp").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            event.setIpAddress(messageNode.get("ipAddress").asText());
            
            if (messageNode.has("userAgent")) {
                event.setUserAgent(messageNode.get("userAgent").asText());
            }
            
            if (messageNode.has("deviceId")) {
                event.setDeviceId(messageNode.get("deviceId").asText());
            }
            
            if (messageNode.has("sessionId")) {
                event.setSessionId(messageNode.get("sessionId").asText());
            }
            
            if (messageNode.has("location")) {
                JsonNode locationNode = messageNode.get("location");
                Map<String, String> location = new HashMap<>();
                locationNode.fields().forEachRemaining(entry -> {
                    location.put(entry.getKey(), entry.getValue().asText());
                });
                event.setLocation(location);
            }
            
            if (messageNode.has("loginMethod")) {
                event.setLoginMethod(messageNode.get("loginMethod").asText());
            }
            
            if (messageNode.has("authenticationStep")) {
                event.setAuthenticationStep(messageNode.get("authenticationStep").asText());
            }
            
            if (messageNode.has("failureReason")) {
                event.setFailureReason(messageNode.get("failureReason").asText());
            }
            
            if (messageNode.has("riskScore")) {
                event.setRiskScore(messageNode.get("riskScore").asDouble());
            }
            
            if (messageNode.has("securityFlags")) {
                JsonNode flagsNode = messageNode.get("securityFlags");
                List<String> flags = new ArrayList<>();
                flagsNode.forEach(flag -> flags.add(flag.asText()));
                event.setSecurityFlags(flags);
            }
            
            if (messageNode.has("attemptCount")) {
                event.setAttemptCount(messageNode.get("attemptCount").asInt());
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
            logger.error("Error parsing login attempt event: {}", e.getMessage(), e);
            throw new ValidationException("Invalid login attempt event format: " + e.getMessage());
        }
    }

    private void validateLoginAttemptEvent(LoginAttemptEvent attemptEvent, User user) {
        if (attemptEvent.getIpAddress() == null || attemptEvent.getIpAddress().trim().isEmpty()) {
            throw new ValidationException("IP address is required for login attempt tracking");
        }
    }

    private void processLoginAttempt(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.info("Processing login attempt for user: {}, IP: {}", 
                   user != null ? user.getId() : attemptEvent.getUsername(), attemptEvent.getIpAddress());
        
        // Create login attempt record
        LoginAttempt loginAttempt = new LoginAttempt();
        loginAttempt.setUserId(user != null ? user.getId() : null);
        loginAttempt.setUsername(attemptEvent.getUsername());
        loginAttempt.setIpAddress(attemptEvent.getIpAddress());
        loginAttempt.setUserAgent(attemptEvent.getUserAgent());
        loginAttempt.setDeviceId(attemptEvent.getDeviceId());
        loginAttempt.setLocation(attemptEvent.getLocation());
        loginAttempt.setLoginMethod(attemptEvent.getLoginMethod());
        loginAttempt.setTimestamp(attemptEvent.getTimestamp());
        loginAttempt.setStatus(LoginStatus.ATTEMPTED);
        
        // Calculate risk score
        double riskScore = calculateLoginRiskScore(attemptEvent, user);
        loginAttempt.setRiskScore(riskScore);
        
        loginAttemptService.recordLoginAttempt(loginAttempt);
        
        // Check for rate limiting
        if (isRateLimited(attemptEvent.getIpAddress(), attemptEvent.getTimestamp())) {
            triggerRateLimitEvent(attemptEvent, correlationId, traceId);
        }
        
        // Check for suspicious patterns
        if (riskScore > 0.7) {
            triggerSuspiciousLoginEvent(attemptEvent, user, riskScore, correlationId, traceId);
        }
        
        auditService.recordLoginAttempt(user != null ? user.getId() : null, attemptEvent.getUsername(), 
                                      attemptEvent.getIpAddress(), attemptEvent.getUserAgent(), riskScore, correlationId);
        
        metricsService.recordLoginAttempt(user != null ? user.getId() : attemptEvent.getUsername(), 
                                        attemptEvent.getIpAddress(), riskScore);
        
        logger.info("Login attempt recorded for user: {}, riskScore: {}", 
                   user != null ? user.getId() : attemptEvent.getUsername(), riskScore);
    }

    private void processLoginSuccess(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.info("Processing successful login for user: {}", user != null ? user.getId() : attemptEvent.getUsername());
        
        if (user != null) {
            // Update user's successful login information
            user.setLastSuccessfulLogin(attemptEvent.getTimestamp());
            user.setLastLoginIp(attemptEvent.getIpAddress());
            
            // Clear failed attempts
            loginAttemptService.clearFailedAttempts(user.getId());
            
            // Update login attempt record
            LoginAttempt loginAttempt = loginAttemptService.getLatestLoginAttempt(user.getId(), attemptEvent.getIpAddress());
            if (loginAttempt != null) {
                loginAttempt.setStatus(LoginStatus.SUCCESS);
                loginAttempt.setSessionId(attemptEvent.getSessionId());
                loginAttemptService.updateLoginAttempt(loginAttempt);
            }
            
            // Check for geographic anomalies
            if (monitorGeographicAnomalies && attemptEvent.getLocation() != null) {
                checkGeographicAnomaly(user, attemptEvent, correlationId, traceId);
            }
            
            userService.updateUser(user);
        }
        
        auditService.recordSuccessfulLogin(user != null ? user.getId() : null, attemptEvent.getUsername(), 
                                         attemptEvent.getIpAddress(), attemptEvent.getSessionId(), correlationId);
        
        metricsService.recordSuccessfulLogin(user != null ? user.getId() : attemptEvent.getUsername(), 
                                           attemptEvent.getIpAddress());
        
        logger.info("Successful login processed for user: {}", user != null ? user.getId() : attemptEvent.getUsername());
    }

    private void processLoginFailure(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing failed login for user: {}, reason: {}", 
                   user != null ? user.getId() : attemptEvent.getUsername(), attemptEvent.getFailureReason());
        
        if (user != null) {
            // Increment failed attempts
            int failedAttempts = loginAttemptService.incrementFailedAttempts(user.getId(), attemptEvent.getIpAddress());
            
            // Update login attempt record
            LoginAttempt loginAttempt = loginAttemptService.getLatestLoginAttempt(user.getId(), attemptEvent.getIpAddress());
            if (loginAttempt != null) {
                loginAttempt.setStatus(LoginStatus.FAILED);
                loginAttempt.setFailureReason(attemptEvent.getFailureReason());
                loginAttemptService.updateLoginAttempt(loginAttempt);
            }
            
            // Check for account lockout
            if (failedAttempts >= maxFailedAttempts) {
                triggerAccountLockoutEvent(user, failedAttempts, correlationId, traceId);
            }
            
            // Check for brute force patterns
            if (failedAttempts >= suspiciousThreshold) {
                checkBruteForcePattern(user, attemptEvent, failedAttempts, correlationId, traceId);
            }
        } else {
            // Track failed attempts for unknown usernames (potential enumeration)
            int unknownUserAttempts = loginAttemptService.incrementUnknownUserAttempts(attemptEvent.getUsername(), 
                                                                                      attemptEvent.getIpAddress());
            
            if (unknownUserAttempts >= suspiciousThreshold) {
                triggerAccountEnumerationEvent(attemptEvent, unknownUserAttempts, correlationId, traceId);
            }
        }
        
        auditService.recordFailedLogin(user != null ? user.getId() : null, attemptEvent.getUsername(), 
                                     attemptEvent.getIpAddress(), attemptEvent.getFailureReason(), correlationId);
        
        metricsService.recordFailedLogin(user != null ? user.getId() : attemptEvent.getUsername(), 
                                       attemptEvent.getIpAddress(), attemptEvent.getFailureReason());
        
        logger.warn("Failed login processed for user: {}, reason: {}", 
                   user != null ? user.getId() : attemptEvent.getUsername(), attemptEvent.getFailureReason());
    }

    private void processAccountLockout(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing account lockout for user: {}", user != null ? user.getId() : attemptEvent.getUsername());
        
        if (user != null) {
            // Lock the user account
            user.setAccountLocked(true);
            user.setLockTimestamp(attemptEvent.getTimestamp());
            user.setLockReason("Too many failed login attempts");
            
            userService.updateUser(user);
            
            // Invalidate all active sessions
            userSecurityService.invalidateAllUserSessions(user.getId(), "Account locked due to failed login attempts");
            
            // Send lockout notification
            if (sendNotifications) {
                notificationService.sendAccountLockoutNotification(user.getId(), lockoutDurationMinutes, correlationId);
            }
            
            // Schedule automatic unlock
            scheduleAccountUnlock(user.getId(), lockoutDurationMinutes, correlationId);
        }
        
        auditService.recordAccountLockout(user != null ? user.getId() : null, attemptEvent.getUsername(), 
                                        attemptEvent.getIpAddress(), "Failed login attempts", correlationId);
        
        metricsService.recordAccountLockout(user != null ? user.getId() : attemptEvent.getUsername());
        
        logger.warn("Account lockout processed for user: {}", user != null ? user.getId() : attemptEvent.getUsername());
    }

    private void processSuspiciousLogin(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing suspicious login for user: {}, riskScore: {}", 
                   user != null ? user.getId() : attemptEvent.getUsername(), attemptEvent.getRiskScore());
        
        String suspiciousDetails = buildSuspiciousActivityDetails(attemptEvent);
        
        // Record security event
        if (user != null) {
            userSecurityService.recordSecurityEvent(user.getId(), "SUSPICIOUS_LOGIN", 
                                                   suspiciousDetails, attemptEvent.getRiskScore(), correlationId);
        }
        
        // Send security alert
        triggerSecurityAlert(user, attemptEvent, "Suspicious login activity detected", correlationId);
        
        // Send notification to user
        if (sendNotifications && user != null) {
            notificationService.sendSuspiciousLoginAlert(user.getId(), attemptEvent.getIpAddress(), 
                                                        attemptEvent.getLocation(), correlationId);
        }
        
        auditService.recordSuspiciousLogin(user != null ? user.getId() : null, attemptEvent.getUsername(), 
                                         attemptEvent.getIpAddress(), suspiciousDetails, 
                                         attemptEvent.getRiskScore(), correlationId);
        
        metricsService.recordSuspiciousLogin(user != null ? user.getId() : attemptEvent.getUsername(), 
                                           attemptEvent.getRiskScore());
        
        logger.warn("Suspicious login processed for user: {}, details: {}", 
                   user != null ? user.getId() : attemptEvent.getUsername(), suspiciousDetails);
    }

    private void processBruteForceDetected(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.error("Processing brute force attack detected for user: {}", 
                    user != null ? user.getId() : attemptEvent.getUsername());
        
        // Block IP address
        loginSecurityService.blockIpAddress(attemptEvent.getIpAddress(), "Brute force attack detected", 
                                           lockoutDurationMinutes * 2);
        
        // Create security incident
        createSecurityIncident(user, "BRUTE_FORCE_ATTACK", attemptEvent, correlationId);
        
        // Send immediate security alert
        triggerSecurityAlert(user, attemptEvent, "BRUTE FORCE ATTACK DETECTED", correlationId);
        
        auditService.recordBruteForceDetected(user != null ? user.getId() : null, attemptEvent.getUsername(), 
                                            attemptEvent.getIpAddress(), attemptEvent.getAttemptCount(), correlationId);
        
        metricsService.recordBruteForceDetected(user != null ? user.getId() : attemptEvent.getUsername(), 
                                              attemptEvent.getIpAddress());
        
        logger.error("Brute force attack detected and blocked for user: {}, IP: {}", 
                    user != null ? user.getId() : attemptEvent.getUsername(), attemptEvent.getIpAddress());
    }

    private void processCredentialStuffing(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.error("Processing credential stuffing attack for IP: {}", attemptEvent.getIpAddress());
        
        // Block IP address immediately
        loginSecurityService.blockIpAddress(attemptEvent.getIpAddress(), "Credential stuffing attack", 
                                           lockoutDurationMinutes * 4);
        
        // Create security incident
        createSecurityIncident(user, "CREDENTIAL_STUFFING", attemptEvent, correlationId);
        
        // Alert security team
        triggerSecurityAlert(null, attemptEvent, "CREDENTIAL STUFFING ATTACK DETECTED", correlationId);
        
        auditService.recordCredentialStuffing(attemptEvent.getIpAddress(), attemptEvent.getAttemptCount(), correlationId);
        
        metricsService.recordCredentialStuffing(attemptEvent.getIpAddress());
        
        logger.error("Credential stuffing attack detected and blocked for IP: {}", attemptEvent.getIpAddress());
    }

    private void processGeographicAnomaly(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing geographic anomaly for user: {}", user != null ? user.getId() : attemptEvent.getUsername());
        
        if (user != null) {
            String anomalyDetails = buildGeographicAnomalyDetails(user, attemptEvent);
            
            // Send geographic anomaly notification
            if (sendNotifications) {
                notificationService.sendGeographicAnomalyAlert(user.getId(), attemptEvent.getLocation(), 
                                                             user.getLastKnownLocation(), correlationId);
            }
            
            auditService.recordGeographicAnomaly(user.getId(), attemptEvent.getIpAddress(), 
                                               attemptEvent.getLocation(), anomalyDetails, correlationId);
            
            metricsService.recordGeographicAnomaly(user.getId());
            
            logger.warn("Geographic anomaly processed for user: {}, details: {}", user.getId(), anomalyDetails);
        }
    }

    private void processImpossibleTravel(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.error("Processing impossible travel for user: {}", user != null ? user.getId() : attemptEvent.getUsername());
        
        if (user != null) {
            // Immediately lock account for impossible travel
            user.setAccountLocked(true);
            user.setLockTimestamp(attemptEvent.getTimestamp());
            user.setLockReason("Impossible travel detected");
            userService.updateUser(user);
            
            // Invalidate all sessions
            userSecurityService.invalidateAllUserSessions(user.getId(), "Impossible travel detected");
            
            // Create security incident
            createSecurityIncident(user, "IMPOSSIBLE_TRAVEL", attemptEvent, correlationId);
            
            // Send immediate alert
            if (sendNotifications) {
                notificationService.sendImpossibleTravelAlert(user.getId(), attemptEvent.getLocation(), 
                                                            user.getLastKnownLocation(), correlationId);
            }
            
            auditService.recordImpossibleTravel(user.getId(), attemptEvent.getIpAddress(), 
                                              attemptEvent.getLocation(), user.getLastKnownLocation(), correlationId);
            
            metricsService.recordImpossibleTravel(user.getId());
            
            logger.error("Impossible travel detected - account locked for user: {}", user.getId());
        }
    }

    private void processDeviceAnomaly(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing device anomaly for user: {}", user != null ? user.getId() : attemptEvent.getUsername());
        
        if (user != null) {
            String deviceFingerprint = attemptEvent.getDeviceId();
            String anomalyDetails = buildDeviceAnomalyDetails(user, attemptEvent);
            
            // Send device anomaly notification
            if (sendNotifications) {
                notificationService.sendDeviceAnomalyAlert(user.getId(), deviceFingerprint, 
                                                         attemptEvent.getUserAgent(), correlationId);
            }
            
            auditService.recordDeviceAnomaly(user.getId(), deviceFingerprint, attemptEvent.getUserAgent(), 
                                           anomalyDetails, correlationId);
            
            metricsService.recordDeviceAnomaly(user.getId());
            
            logger.warn("Device anomaly processed for user: {}, details: {}", user.getId(), anomalyDetails);
        }
    }

    private void processTimeAnomaly(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing time anomaly for user: {}", user != null ? user.getId() : attemptEvent.getUsername());
        
        if (user != null) {
            String timeAnomalyDetails = buildTimeAnomalyDetails(user, attemptEvent);
            
            // Record time-based anomaly
            auditService.recordTimeAnomaly(user.getId(), attemptEvent.getTimestamp(), 
                                         user.getTypicalLoginTimes(), timeAnomalyDetails, correlationId);
            
            metricsService.recordTimeAnomaly(user.getId());
            
            logger.warn("Time anomaly processed for user: {}, details: {}", user.getId(), timeAnomalyDetails);
        }
    }

    private void processRateLimitExceeded(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing rate limit exceeded for IP: {}", attemptEvent.getIpAddress());
        
        // Temporarily block IP
        loginSecurityService.blockIpAddress(attemptEvent.getIpAddress(), "Rate limit exceeded", 
                                           rateLimitWindowMinutes);
        
        auditService.recordRateLimitExceeded(attemptEvent.getIpAddress(), rateLimitAttempts, 
                                           rateLimitWindowMinutes, correlationId);
        
        metricsService.recordRateLimitExceeded(attemptEvent.getIpAddress());
        
        logger.warn("Rate limit exceeded - IP blocked: {}", attemptEvent.getIpAddress());
    }

    private void processPasswordSpray(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.error("Processing password spray attack from IP: {}", attemptEvent.getIpAddress());
        
        // Block IP address for extended period
        loginSecurityService.blockIpAddress(attemptEvent.getIpAddress(), "Password spray attack", 
                                           lockoutDurationMinutes * 8);
        
        // Create security incident
        createSecurityIncident(user, "PASSWORD_SPRAY", attemptEvent, correlationId);
        
        auditService.recordPasswordSpray(attemptEvent.getIpAddress(), attemptEvent.getAttemptCount(), correlationId);
        
        metricsService.recordPasswordSpray(attemptEvent.getIpAddress());
        
        logger.error("Password spray attack detected and blocked for IP: {}", attemptEvent.getIpAddress());
    }

    private void processAccountEnumeration(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing account enumeration from IP: {}", attemptEvent.getIpAddress());
        
        // Block IP for enumeration attempts
        loginSecurityService.blockIpAddress(attemptEvent.getIpAddress(), "Account enumeration", 
                                           lockoutDurationMinutes);
        
        auditService.recordAccountEnumeration(attemptEvent.getIpAddress(), attemptEvent.getUsername(), 
                                            attemptEvent.getAttemptCount(), correlationId);
        
        metricsService.recordAccountEnumeration(attemptEvent.getIpAddress());
        
        logger.warn("Account enumeration detected and blocked for IP: {}", attemptEvent.getIpAddress());
    }

    private void processSecurityBypassAttempt(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        logger.error("Processing security bypass attempt for user: {}", 
                    user != null ? user.getId() : attemptEvent.getUsername());
        
        // Immediately block IP
        loginSecurityService.blockIpAddress(attemptEvent.getIpAddress(), "Security bypass attempt", 
                                           lockoutDurationMinutes * 12);
        
        // Lock account if user exists
        if (user != null) {
            user.setAccountLocked(true);
            user.setLockTimestamp(attemptEvent.getTimestamp());
            user.setLockReason("Security bypass attempt detected");
            userService.updateUser(user);
            
            userSecurityService.invalidateAllUserSessions(user.getId(), "Security incident");
        }
        
        // Create critical security incident
        createSecurityIncident(user, "SECURITY_BYPASS_ATTEMPT", attemptEvent, correlationId);
        
        auditService.recordSecurityBypassAttempt(user != null ? user.getId() : null, attemptEvent.getUsername(), 
                                                attemptEvent.getIpAddress(), attemptEvent.getEventMetadata(), correlationId);
        
        metricsService.recordSecurityBypassAttempt(user != null ? user.getId() : attemptEvent.getUsername());
        
        logger.error("Security bypass attempt detected - IP blocked and account locked");
    }

    private double calculateLoginRiskScore(LoginAttemptEvent attemptEvent, User user) {
        double riskScore = 0.0;
        
        // Base risk from IP reputation
        if (attemptEvent.getIpAddress() != null) {
            riskScore += loginSecurityService.getIpRiskScore(attemptEvent.getIpAddress()) * 0.3;
        }
        
        // Risk from geographic location
        if (user != null && attemptEvent.getLocation() != null) {
            double geoRisk = loginSecurityService.calculateGeographicRisk(user.getId(), attemptEvent.getLocation());
            riskScore += geoRisk * 0.2;
        }
        
        // Risk from device anomalies
        if (user != null && attemptEvent.getDeviceId() != null) {
            if (!userSecurityService.isKnownDevice(user.getId(), attemptEvent.getDeviceId())) {
                riskScore += 0.15;
            }
        }
        
        // Risk from timing patterns
        if (user != null) {
            double timeRisk = loginSecurityService.calculateTimeBasedRisk(user.getId(), attemptEvent.getTimestamp());
            riskScore += timeRisk * 0.1;
        }
        
        // Risk from recent failed attempts
        int recentFailures = loginAttemptService.getRecentFailedAttempts(attemptEvent.getIpAddress(), 
                                                                         attemptEvent.getTimestamp(), 60);
        if (recentFailures > 0) {
            riskScore += Math.min(recentFailures * 0.05, 0.25);
        }
        
        return Math.min(riskScore, 1.0);
    }

    private boolean isRateLimited(String ipAddress, LocalDateTime timestamp) {
        int recentAttempts = loginAttemptService.getRecentAttempts(ipAddress, timestamp, rateLimitWindowMinutes);
        return recentAttempts >= rateLimitAttempts;
    }

    private void checkGeographicAnomaly(User user, LoginAttemptEvent attemptEvent, String correlationId, String traceId) {
        if (user.getLastKnownLocation() != null && attemptEvent.getLocation() != null) {
            double distance = loginSecurityService.calculateDistance(user.getLastKnownLocation(), 
                                                                    attemptEvent.getLocation());
            
            if (distance > 1000) { // More than 1000km from last known location
                triggerGeographicAnomalyEvent(user, attemptEvent, distance, correlationId, traceId);
            }
        }
    }

    private void checkBruteForcePattern(User user, LoginAttemptEvent attemptEvent, int failedAttempts, 
                                      String correlationId, String traceId) {
        // Check for rapid successive attempts
        List<LoginAttempt> recentAttempts = loginAttemptService.getRecentLoginAttempts(user.getId(), 15);
        
        if (recentAttempts.size() >= 5) {
            // Calculate time between attempts
            long avgTimeBetweenAttempts = calculateAverageTimeBetweenAttempts(recentAttempts);
            
            if (avgTimeBetweenAttempts < 5000) { // Less than 5 seconds average
                triggerBruteForceEvent(user, attemptEvent, failedAttempts, correlationId, traceId);
            }
        }
    }

    private long calculateAverageTimeBetweenAttempts(List<LoginAttempt> attempts) {
        if (attempts.size() < 2) return Long.MAX_VALUE;
        
        long totalTime = 0;
        for (int i = 1; i < attempts.size(); i++) {
            totalTime += ChronoUnit.MILLIS.between(attempts.get(i-1).getTimestamp(), attempts.get(i).getTimestamp());
        }
        
        return totalTime / (attempts.size() - 1);
    }

    private String buildSuspiciousActivityDetails(LoginAttemptEvent attemptEvent) {
        StringBuilder details = new StringBuilder();
        
        if (attemptEvent.getSecurityFlags() != null && !attemptEvent.getSecurityFlags().isEmpty()) {
            details.append("Security flags: ").append(String.join(", ", attemptEvent.getSecurityFlags()));
        }
        
        if (attemptEvent.getRiskScore() != null) {
            details.append("; Risk score: ").append(attemptEvent.getRiskScore());
        }
        
        return details.toString();
    }

    private String buildGeographicAnomalyDetails(User user, LoginAttemptEvent attemptEvent) {
        Map<String, String> currentLocation = attemptEvent.getLocation();
        Map<String, String> lastLocation = user.getLastKnownLocation();
        
        return String.format("Login from %s (previous: %s)", 
                           formatLocation(currentLocation), formatLocation(lastLocation));
    }

    private String buildDeviceAnomalyDetails(User user, LoginAttemptEvent attemptEvent) {
        return String.format("New device detected: %s (User-Agent: %s)", 
                           attemptEvent.getDeviceId(), attemptEvent.getUserAgent());
    }

    private String buildTimeAnomalyDetails(User user, LoginAttemptEvent attemptEvent) {
        return String.format("Login at unusual time: %s (typical: %s)", 
                           attemptEvent.getTimestamp().toLocalTime(), user.getTypicalLoginTimes());
    }

    private String formatLocation(Map<String, String> location) {
        if (location == null) return "Unknown";
        
        return String.format("%s, %s", 
                           location.getOrDefault("city", "Unknown"), 
                           location.getOrDefault("country", "Unknown"));
    }

    private void scheduleAccountUnlock(String userId, int delayMinutes, String correlationId) {
        Map<String, Object> unlockEvent = new HashMap<>();
        unlockEvent.put("userId", userId);
        unlockEvent.put("eventType", "SCHEDULED_UNLOCK");
        unlockEvent.put("scheduledTime", LocalDateTime.now().plusMinutes(delayMinutes).toString());
        unlockEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("scheduled-events", unlockEvent);
    }

    private void triggerRateLimitEvent(LoginAttemptEvent attemptEvent, String correlationId, String traceId) {
        Map<String, Object> rateLimitEvent = new HashMap<>();
        rateLimitEvent.put("eventType", "RATE_LIMIT_EXCEEDED");
        rateLimitEvent.put("ipAddress", attemptEvent.getIpAddress());
        rateLimitEvent.put("attemptCount", rateLimitAttempts);
        rateLimitEvent.put("timestamp", LocalDateTime.now().toString());
        rateLimitEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("login-attempt-tracking", rateLimitEvent);
    }

    private void triggerSuspiciousLoginEvent(LoginAttemptEvent attemptEvent, User user, double riskScore, 
                                           String correlationId, String traceId) {
        Map<String, Object> suspiciousEvent = new HashMap<>();
        suspiciousEvent.put("userId", user != null ? user.getId() : null);
        suspiciousEvent.put("username", attemptEvent.getUsername());
        suspiciousEvent.put("eventType", "SUSPICIOUS_LOGIN");
        suspiciousEvent.put("riskScore", riskScore);
        suspiciousEvent.put("ipAddress", attemptEvent.getIpAddress());
        suspiciousEvent.put("timestamp", LocalDateTime.now().toString());
        suspiciousEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("login-attempt-tracking", suspiciousEvent);
    }

    private void triggerAccountLockoutEvent(User user, int failedAttempts, String correlationId, String traceId) {
        Map<String, Object> lockoutEvent = new HashMap<>();
        lockoutEvent.put("userId", user.getId());
        lockoutEvent.put("eventType", "ACCOUNT_LOCKOUT");
        lockoutEvent.put("failedAttempts", failedAttempts);
        lockoutEvent.put("timestamp", LocalDateTime.now().toString());
        lockoutEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("login-attempt-tracking", lockoutEvent);
    }

    private void triggerBruteForceEvent(User user, LoginAttemptEvent attemptEvent, int failedAttempts, 
                                      String correlationId, String traceId) {
        Map<String, Object> bruteForceEvent = new HashMap<>();
        bruteForceEvent.put("userId", user.getId());
        bruteForceEvent.put("eventType", "BRUTE_FORCE_DETECTED");
        bruteForceEvent.put("ipAddress", attemptEvent.getIpAddress());
        bruteForceEvent.put("attemptCount", failedAttempts);
        bruteForceEvent.put("timestamp", LocalDateTime.now().toString());
        bruteForceEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("login-attempt-tracking", bruteForceEvent);
    }

    private void triggerGeographicAnomalyEvent(User user, LoginAttemptEvent attemptEvent, double distance, 
                                             String correlationId, String traceId) {
        Map<String, Object> geoEvent = new HashMap<>();
        geoEvent.put("userId", user.getId());
        geoEvent.put("eventType", "GEOGRAPHIC_ANOMALY");
        geoEvent.put("ipAddress", attemptEvent.getIpAddress());
        geoEvent.put("location", attemptEvent.getLocation());
        geoEvent.put("distance", distance);
        geoEvent.put("timestamp", LocalDateTime.now().toString());
        geoEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("login-attempt-tracking", geoEvent);
    }

    private void triggerAccountEnumerationEvent(LoginAttemptEvent attemptEvent, int unknownUserAttempts, 
                                              String correlationId, String traceId) {
        Map<String, Object> enumEvent = new HashMap<>();
        enumEvent.put("eventType", "ACCOUNT_ENUMERATION");
        enumEvent.put("ipAddress", attemptEvent.getIpAddress());
        enumEvent.put("username", attemptEvent.getUsername());
        enumEvent.put("attemptCount", unknownUserAttempts);
        enumEvent.put("timestamp", LocalDateTime.now().toString());
        enumEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("login-attempt-tracking", enumEvent);
    }

    private void triggerSecurityAlert(User user, LoginAttemptEvent attemptEvent, String message, String correlationId) {
        Map<String, Object> securityAlert = new HashMap<>();
        securityAlert.put("userId", user != null ? user.getId() : null);
        securityAlert.put("alertType", "LOGIN_SECURITY");
        securityAlert.put("severity", "HIGH");
        securityAlert.put("message", message);
        securityAlert.put("ipAddress", attemptEvent.getIpAddress());
        securityAlert.put("location", attemptEvent.getLocation());
        securityAlert.put("timestamp", LocalDateTime.now().toString());
        securityAlert.put("correlationId", correlationId);
        
        kafkaTemplate.send("security-alerts", securityAlert);
    }

    private void createSecurityIncident(User user, String incidentType, LoginAttemptEvent attemptEvent, String correlationId) {
        Map<String, Object> incident = new HashMap<>();
        incident.put("userId", user != null ? user.getId() : null);
        incident.put("username", attemptEvent.getUsername());
        incident.put("incidentType", incidentType);
        incident.put("severity", "CRITICAL");
        incident.put("ipAddress", attemptEvent.getIpAddress());
        incident.put("userAgent", attemptEvent.getUserAgent());
        incident.put("location", attemptEvent.getLocation());
        incident.put("timestamp", attemptEvent.getTimestamp().toString());
        incident.put("eventMetadata", attemptEvent.getEventMetadata());
        incident.put("correlationId", correlationId);
        
        kafkaTemplate.send("security-incidents", incident);
    }

    private void recordLoginAttemptEventAudit(LoginAttemptEvent attemptEvent, User user, String correlationId, String traceId) {
        LoginAttemptAuditLog auditLog = new LoginAttemptAuditLog();
        auditLog.setUserId(user != null ? user.getId() : null);
        auditLog.setUsername(attemptEvent.getUsername());
        auditLog.setEventType(attemptEvent.getEventType());
        auditLog.setIpAddress(attemptEvent.getIpAddress());
        auditLog.setUserAgent(attemptEvent.getUserAgent());
        auditLog.setDeviceId(attemptEvent.getDeviceId());
        auditLog.setLocation(attemptEvent.getLocation());
        auditLog.setLoginMethod(attemptEvent.getLoginMethod());
        auditLog.setFailureReason(attemptEvent.getFailureReason());
        auditLog.setRiskScore(attemptEvent.getRiskScore());
        auditLog.setSecurityFlags(attemptEvent.getSecurityFlags());
        auditLog.setTimestamp(attemptEvent.getTimestamp());
        auditLog.setCorrelationId(correlationId);
        auditLog.setTraceId(traceId);
        
        auditService.recordLoginAttemptEventAudit(auditLog);
    }

    private void handleProcessingError(String message, String topic, Exception error, 
                                     String correlationId, String traceId, Acknowledgment acknowledgment) {
        try {
            errorCounter.increment();
            logger.error("Error processing login attempt tracking message: {}", error.getMessage(), error);
            
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
        logger.error("Circuit breaker fallback triggered for login attempt tracking consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        sendToDlq(message, topic, "Circuit breaker fallback: " + ex.getMessage(), ex, correlationId, traceId);
        acknowledgment.acknowledge();
    }

    public enum LoginAttemptEventType {
        LOGIN_ATTEMPT,
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        ACCOUNT_LOCKOUT,
        SUSPICIOUS_LOGIN,
        BRUTE_FORCE_DETECTED,
        CREDENTIAL_STUFFING,
        GEOGRAPHIC_ANOMALY,
        IMPOSSIBLE_TRAVEL,
        DEVICE_ANOMALY,
        TIME_ANOMALY,
        RATE_LIMIT_EXCEEDED,
        PASSWORD_SPRAY,
        ACCOUNT_ENUMERATION,
        SECURITY_BYPASS_ATTEMPT
    }

    public static class LoginAttemptEvent {
        private String userId;
        private String username;
        private LoginAttemptEventType eventType;
        private LocalDateTime timestamp;
        private String ipAddress;
        private String userAgent;
        private String deviceId;
        private String sessionId;
        private Map<String, String> location;
        private String loginMethod;
        private String authenticationStep;
        private String failureReason;
        private Double riskScore;
        private List<String> securityFlags;
        private Integer attemptCount;
        private Map<String, Object> eventMetadata;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public LoginAttemptEventType getEventType() { return eventType; }
        public void setEventType(LoginAttemptEventType eventType) { this.eventType = eventType; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public Map<String, String> getLocation() { return location; }
        public void setLocation(Map<String, String> location) { this.location = location; }

        public String getLoginMethod() { return loginMethod; }
        public void setLoginMethod(String loginMethod) { this.loginMethod = loginMethod; }

        public String getAuthenticationStep() { return authenticationStep; }
        public void setAuthenticationStep(String authenticationStep) { this.authenticationStep = authenticationStep; }

        public String getFailureReason() { return failureReason; }
        public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

        public Double getRiskScore() { return riskScore; }
        public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

        public List<String> getSecurityFlags() { return securityFlags; }
        public void setSecurityFlags(List<String> securityFlags) { this.securityFlags = securityFlags; }

        public Integer getAttemptCount() { return attemptCount; }
        public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }

        public Map<String, Object> getEventMetadata() { return eventMetadata; }
        public void setEventMetadata(Map<String, Object> eventMetadata) { this.eventMetadata = eventMetadata; }
    }
}