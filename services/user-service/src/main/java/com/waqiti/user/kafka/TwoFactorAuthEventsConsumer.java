package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.user.model.User;
import com.waqiti.user.model.TwoFactorAuthToken;
import com.waqiti.user.model.TwoFactorAuthMethod;
import com.waqiti.user.model.TwoFactorAuthStatus;
import com.waqiti.user.model.TwoFactorAuthAuditLog;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.TwoFactorAuthService;
import com.waqiti.user.service.AuthenticationService;
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
public class TwoFactorAuthEventsConsumer {

    private static final Logger logger = LoggerFactory.getLogger(TwoFactorAuthEventsConsumer.class);
    private static final String CONSUMER_NAME = "two-factor-auth-events-consumer";
    private static final String DLQ_TOPIC = "two-factor-auth-events-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final TwoFactorAuthService twoFactorAuthService;
    private final AuthenticationService authenticationService;
    private final UserSecurityService userSecurityService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final NotificationService notificationService;
    private final SecurityContextService securityContextService;
    private final ValidationService validationService;
    private final UniversalDLQHandler dlqHandler;
    private final VaultSecretManager vaultSecretManager;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${kafka.consumer.two-factor-auth-events.enabled:true}")
    private boolean consumerEnabled;

    @Value("${two-factor-auth.token-expiry-minutes:10}")
    private int tokenExpiryMinutes;

    @Value("${two-factor-auth.max-attempts:3}")
    private int maxVerificationAttempts;

    @Value("${two-factor-auth.lockout-duration-minutes:30}")
    private int lockoutDurationMinutes;

    @Value("${two-factor-auth.backup-codes-count:10}")
    private int backupCodesCount;

    @Value("${two-factor-auth.security-monitoring-enabled:true}")
    private boolean securityMonitoringEnabled;

    @Value("${two-factor-auth.send-notifications:true}")
    private boolean sendNotifications;

    @Value("${two-factor-auth.require-for-sensitive-operations:true}")
    private boolean requireForSensitiveOperations;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter twoFactorEventTypeCounters;

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("two_factor_auth_events_processed_total")
                .description("Total processed two-factor auth events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("two_factor_auth_events_errors_total")
                .description("Total two-factor auth events processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("two_factor_auth_events_dlq_total")
                .description("Total two-factor auth events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("two_factor_auth_events_processing_duration")
                .description("Two-factor auth events processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        logger.info("TwoFactorAuthEventsConsumer metrics initialized");
    }

    @KafkaListener(
        topics = "${kafka.topics.two-factor-auth-events:two-factor-auth-events}",
        groupId = "${kafka.consumer.group-id:user-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "two-factor-auth-events-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "two-factor-auth-events-retry")
    public void processTwoFactorAuthEvents(
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
                logger.warn("Two-factor auth events consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.info("Processing two-factor auth event message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidTwoFactorAuthEventMessage(messageNode)) {
                logger.error("Invalid two-factor auth event message format: {}", message);
                // TODO: Use dlqHandler for fallback
                acknowledgment.acknowledge();
                return;
            }

            String userId = messageNode.get("userId").asText();
            String eventType = messageNode.get("eventType").asText();

            long startTime = System.currentTimeMillis();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    processTwoFactorAuthEvent(messageNode, userId, eventType, correlationId, traceId);
                } catch (Exception e) {
                    logger.error("Error in async processing: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Successfully processed two-factor auth event: messageId={}, userId={}, eventType={}, processingTime={}ms",
                    messageId, userId, eventType, processingTime);

            processedCounter.increment();
            metricsService.recordTwoFactorAuthEventProcessed(eventType, processingTime);
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse two-factor auth event message: messageId={}, error={}", messageId, e.getMessage());
            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, null),
                e
            ).exceptionally(dlqError -> {
                logger.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Processing failed", e);
        } catch (Exception e) {
            
            logger.error("Unexpected error processing two-factor auth event: messageId={}, error={}", messageId, e.getMessage(), e);

            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, null),
                e
            ).exceptionally(dlqError -> {
                logger.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Processing failed", e);
        } finally {
            sample.stop(processingTimer);
            MDC.clear();
        }
    }

    private boolean isValidTwoFactorAuthEventMessage(JsonNode messageNode) {
        try {
            return messageNode != null &&
                   messageNode.has("userId") && StringUtils.hasText(messageNode.get("userId").asText()) &&
                   messageNode.has("eventType") && StringUtils.hasText(messageNode.get("eventType").asText()) &&
                   messageNode.has("timestamp");
        } catch (Exception e) {
            logger.error("Error validating two-factor auth event message: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    private void processTwoFactorAuthEvent(JsonNode messageNode, String userId, String eventType, 
                                         String correlationId, String traceId) {
        try {
            User user = userService.findById(userId);
            if (user == null) {
                throw new ValidationException("User not found: " + userId);
            }

            TwoFactorAuthEvent authEvent = parseTwoFactorAuthEvent(messageNode);
            
            validateTwoFactorAuthEvent(authEvent, user);
            
            switch (authEvent.getEventType()) {
                case SETUP_INITIATED:
                    processSetupInitiated(authEvent, user, correlationId, traceId);
                    break;
                case METHOD_REGISTERED:
                    processMethodRegistered(authEvent, user, correlationId, traceId);
                    break;
                case SETUP_COMPLETED:
                    processSetupCompleted(authEvent, user, correlationId, traceId);
                    break;
                case CHALLENGE_REQUESTED:
                    processChallengeRequested(authEvent, user, correlationId, traceId);
                    break;
                case TOKEN_GENERATED:
                    processTokenGenerated(authEvent, user, correlationId, traceId);
                    break;
                case TOKEN_SENT:
                    processTokenSent(authEvent, user, correlationId, traceId);
                    break;
                case VERIFICATION_ATTEMPTED:
                    processVerificationAttempted(authEvent, user, correlationId, traceId);
                    break;
                case VERIFICATION_SUCCESSFUL:
                    processVerificationSuccessful(authEvent, user, correlationId, traceId);
                    break;
                case VERIFICATION_FAILED:
                    processVerificationFailed(authEvent, user, correlationId, traceId);
                    break;
                case BACKUP_CODE_USED:
                    processBackupCodeUsed(authEvent, user, correlationId, traceId);
                    break;
                case BACKUP_CODES_REGENERATED:
                    processBackupCodesRegenerated(authEvent, user, correlationId, traceId);
                    break;
                case METHOD_DISABLED:
                    processMethodDisabled(authEvent, user, correlationId, traceId);
                    break;
                case ACCOUNT_LOCKED:
                    processAccountLocked(authEvent, user, correlationId, traceId);
                    break;
                case SUSPICIOUS_ACTIVITY:
                    processSuspiciousActivity(authEvent, user, correlationId, traceId);
                    break;
                case BYPASS_ATTEMPT:
                    processBypassAttempt(authEvent, user, correlationId, traceId);
                    break;
                default:
                    logger.warn("Unknown two-factor auth event type: {}", authEvent.getEventType());
                    throw new ValidationException("Unknown two-factor auth event type: " + authEvent.getEventType());
            }

            recordTwoFactorAuthEventAudit(authEvent, user, correlationId, traceId);

        } catch (Exception e) {
            logger.error("Error processing two-factor auth event for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    private TwoFactorAuthEvent parseTwoFactorAuthEvent(JsonNode messageNode) {
        try {
            TwoFactorAuthEvent event = new TwoFactorAuthEvent();
            event.setUserId(messageNode.get("userId").asText());
            event.setEventType(TwoFactorAuthEventType.valueOf(messageNode.get("eventType").asText()));
            event.setTimestamp(LocalDateTime.parse(messageNode.get("timestamp").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            if (messageNode.has("tokenId")) {
                event.setTokenId(messageNode.get("tokenId").asText());
            }
            
            if (messageNode.has("authMethod")) {
                event.setAuthMethod(TwoFactorAuthMethod.valueOf(messageNode.get("authMethod").asText()));
            }
            
            if (messageNode.has("ipAddress")) {
                event.setIpAddress(messageNode.get("ipAddress").asText());
            }
            
            if (messageNode.has("userAgent")) {
                event.setUserAgent(messageNode.get("userAgent").asText());
            }
            
            if (messageNode.has("deviceId")) {
                event.setDeviceId(messageNode.get("deviceId").asText());
            }
            
            if (messageNode.has("attemptNumber")) {
                event.setAttemptNumber(messageNode.get("attemptNumber").asInt());
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
            
            if (messageNode.has("reason")) {
                event.setReason(messageNode.get("reason").asText());
            }
            
            if (messageNode.has("backupCodeUsed")) {
                event.setBackupCodeUsed(messageNode.get("backupCodeUsed").asBoolean());
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
            logger.error("Error parsing two-factor auth event: {}", e.getMessage(), e);
            throw new ValidationException("Invalid two-factor auth event format: " + e.getMessage());
        }
    }

    private void validateTwoFactorAuthEvent(TwoFactorAuthEvent authEvent, User user) {
        if (!user.isActive()) {
            throw new ValidationException("User is not active: " + user.getId());
        }
    }

    private void processSetupInitiated(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.info("Processing 2FA setup initiated for user: {}", user.getId());
        
        // Create setup session
        String setupSessionId = twoFactorAuthService.createSetupSession(user.getId(), authEvent.getAuthMethod());
        
        // Generate QR code or send setup instructions based on method
        switch (authEvent.getAuthMethod()) {
            case TOTP:
                String totpSecret = twoFactorAuthService.generateTOTPSecret(user.getId());
                String qrCodeUrl = twoFactorAuthService.generateQRCodeUrl(user.getId(), totpSecret);
                
                if (sendNotifications) {
                    notificationService.sendTOTPSetupInstructions(user.getId(), qrCodeUrl, correlationId);
                }
                break;
                
            case SMS:
                if (sendNotifications) {
                    notificationService.sendSMSSetupInstructions(user.getId(), correlationId);
                }
                break;
                
            case EMAIL:
                if (sendNotifications) {
                    notificationService.sendEmailSetupInstructions(user.getId(), correlationId);
                }
                break;
                
            case HARDWARE_TOKEN:
                if (sendNotifications) {
                    notificationService.sendHardwareTokenSetupInstructions(user.getId(), correlationId);
                }
                break;
                
            default:
                logger.warn("Unknown 2FA method for setup: {}", authEvent.getAuthMethod());
        }
        
        auditService.record2FASetupInitiated(user.getId(), authEvent.getAuthMethod(), setupSessionId, correlationId);
        
        metricsService.record2FASetupInitiated(user.getId(), authEvent.getAuthMethod());
        
        logger.info("2FA setup initiated for user: {}, method: {}, sessionId: {}", 
                   user.getId(), authEvent.getAuthMethod(), setupSessionId);
    }

    private void processMethodRegistered(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.info("Processing 2FA method registered for user: {}, method: {}", user.getId(), authEvent.getAuthMethod());
        
        // Register the 2FA method
        twoFactorAuthService.registerAuthMethod(user.getId(), authEvent.getAuthMethod(), 
                                               authEvent.getDeviceId(), authEvent.getEventMetadata());
        
        // Generate backup codes
        List<String> backupCodes = twoFactorAuthService.generateBackupCodes(user.getId(), backupCodesCount);
        
        // Send backup codes to user
        if (sendNotifications) {
            notificationService.sendBackupCodes(user.getId(), backupCodes, correlationId);
        }
        
        auditService.record2FAMethodRegistered(user.getId(), authEvent.getAuthMethod(), 
                                             authEvent.getDeviceId(), correlationId);
        
        metricsService.record2FAMethodRegistered(user.getId(), authEvent.getAuthMethod());
        
        logger.info("2FA method registered for user: {}, method: {}", user.getId(), authEvent.getAuthMethod());
    }

    private void processSetupCompleted(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.info("Processing 2FA setup completed for user: {}", user.getId());
        
        // Enable 2FA for the user
        user.setTwoFactorEnabled(true);
        user.setTwoFactorMethod(authEvent.getAuthMethod());
        user.setTwoFactorSetupDate(authEvent.getTimestamp());
        userService.updateUser(user);
        
        // Complete setup session
        twoFactorAuthService.completeSetupSession(user.getId());
        
        // Send confirmation notification
        if (sendNotifications) {
            notificationService.send2FASetupCompleted(user.getId(), authEvent.getAuthMethod(), correlationId);
        }
        
        auditService.record2FASetupCompleted(user.getId(), authEvent.getAuthMethod(), correlationId);
        
        metricsService.record2FASetupCompleted(user.getId(), authEvent.getAuthMethod());
        
        logger.info("2FA setup completed for user: {}, method: {}", user.getId(), authEvent.getAuthMethod());
    }

    private void processChallengeRequested(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.info("Processing 2FA challenge requested for user: {}", user.getId());
        
        if (!user.isTwoFactorEnabled()) {
            logger.warn("2FA challenge requested for user without 2FA enabled: {}", user.getId());
            return;
        }
        
        // Check if user is locked out
        if (twoFactorAuthService.isUserLockedOut(user.getId())) {
            logger.warn("2FA challenge requested for locked out user: {}", user.getId());
            triggerAccountLockedEvent(user, "2FA lockout", correlationId, traceId);
            return;
        }
        
        // Generate and send challenge based on method
        TwoFactorAuthMethod method = authEvent.getAuthMethod() != null ? 
                                   authEvent.getAuthMethod() : user.getTwoFactorMethod();
        
        String challengeId = generateAndSendChallenge(user, method, correlationId);
        
        auditService.record2FAChallengeRequested(user.getId(), method, challengeId, 
                                               authEvent.getIpAddress(), correlationId);
        
        metricsService.record2FAChallengeRequested(user.getId(), method);
        
        logger.info("2FA challenge requested for user: {}, method: {}, challengeId: {}", 
                   user.getId(), method, challengeId);
    }

    private String generateAndSendChallenge(User user, TwoFactorAuthMethod method, String correlationId) {
        String challengeId = UUID.randomUUID().toString();
        
        switch (method) {
            case TOTP:
                // TOTP doesn't require sending - user generates from app
                twoFactorAuthService.createTOTPChallenge(user.getId(), challengeId, tokenExpiryMinutes);
                break;
                
            case SMS:
                String smsToken = twoFactorAuthService.generateSMSToken(user.getId(), challengeId, tokenExpiryMinutes);
                if (sendNotifications) {
                    notificationService.sendSMSToken(user.getId(), smsToken, correlationId);
                }
                break;
                
            case EMAIL:
                String emailToken = twoFactorAuthService.generateEmailToken(user.getId(), challengeId, tokenExpiryMinutes);
                if (sendNotifications) {
                    notificationService.sendEmailToken(user.getId(), emailToken, correlationId);
                }
                break;
                
            case HARDWARE_TOKEN:
                // Hardware token challenge
                twoFactorAuthService.createHardwareTokenChallenge(user.getId(), challengeId, tokenExpiryMinutes);
                break;
                
            default:
                throw new ValidationException("Unsupported 2FA method: " + method);
        }
        
        return challengeId;
    }

    private void processTokenGenerated(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.info("Processing 2FA token generated for user: {}", user.getId());
        
        String tokenId = authEvent.getTokenId();
        TwoFactorAuthToken authToken = twoFactorAuthService.getAuthToken(tokenId);
        
        if (authToken != null) {
            authToken.setStatus(TwoFactorAuthStatus.GENERATED);
            authToken.setGeneratedAt(authEvent.getTimestamp());
            twoFactorAuthService.updateAuthToken(authToken);
            
            auditService.record2FATokenGenerated(user.getId(), tokenId, authEvent.getAuthMethod(), correlationId);
            
            logger.info("2FA token generated for user: {}, tokenId: {}, method: {}", 
                       user.getId(), tokenId, authEvent.getAuthMethod());
        } else {
            logger.warn("2FA token not found for token generated event: {}", tokenId);
        }
    }

    private void processTokenSent(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.info("Processing 2FA token sent for user: {}", user.getId());
        
        String tokenId = authEvent.getTokenId();
        TwoFactorAuthToken authToken = twoFactorAuthService.getAuthToken(tokenId);
        
        if (authToken != null) {
            authToken.setStatus(TwoFactorAuthStatus.SENT);
            authToken.setSentAt(authEvent.getTimestamp());
            twoFactorAuthService.updateAuthToken(authToken);
            
            auditService.record2FATokenSent(user.getId(), tokenId, authEvent.getAuthMethod(), correlationId);
            
            logger.info("2FA token sent for user: {}, tokenId: {}, method: {}", 
                       user.getId(), tokenId, authEvent.getAuthMethod());
        } else {
            logger.warn("2FA token not found for token sent event: {}", tokenId);
        }
    }

    private void processVerificationAttempted(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.info("Processing 2FA verification attempted for user: {}, attempt: {}", 
                   user.getId(), authEvent.getAttemptNumber());
        
        // Track failed attempts
        int failedAttempts = twoFactorAuthService.getFailedAttempts(user.getId());
        
        // Security monitoring
        if (securityMonitoringEnabled && failedAttempts > 1) {
            double riskScore = calculateVerificationRiskScore(authEvent, user, failedAttempts);
            if (riskScore > 0.7) {
                triggerSuspiciousActivityEvent(user, riskScore, "Multiple 2FA verification attempts", correlationId, traceId);
            }
        }
        
        auditService.record2FAVerificationAttempted(user.getId(), authEvent.getTokenId(), 
                                                   authEvent.getAuthMethod(), authEvent.getAttemptNumber(), 
                                                   authEvent.getIpAddress(), correlationId);
        
        metricsService.record2FAVerificationAttempted(user.getId(), authEvent.getAuthMethod());
        
        logger.info("2FA verification attempted for user: {}, attempt: {}, failedAttempts: {}", 
                   user.getId(), authEvent.getAttemptNumber(), failedAttempts);
    }

    private void processVerificationSuccessful(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.info("Processing 2FA verification successful for user: {}", user.getId());
        
        String tokenId = authEvent.getTokenId();
        TwoFactorAuthToken authToken = twoFactorAuthService.getAuthToken(tokenId);
        
        if (authToken != null) {
            authToken.setStatus(TwoFactorAuthStatus.VERIFIED);
            authToken.setVerifiedAt(authEvent.getTimestamp());
            twoFactorAuthService.updateAuthToken(authToken);
        }
        
        // Clear failed attempts
        twoFactorAuthService.clearFailedAttempts(user.getId());
        
        // Update user's last successful 2FA
        user.setLastTwoFactorAuth(authEvent.getTimestamp());
        userService.updateUser(user);
        
        // Complete authentication flow
        authenticationService.complete2FAAuthentication(user.getId(), authEvent.getAuthMethod());
        
        auditService.record2FAVerificationSuccessful(user.getId(), tokenId, authEvent.getAuthMethod(), 
                                                    authEvent.getIpAddress(), correlationId);
        
        metricsService.record2FAVerificationSuccessful(user.getId(), authEvent.getAuthMethod());
        
        logger.info("2FA verification successful for user: {}, method: {}", user.getId(), authEvent.getAuthMethod());
    }

    private void processVerificationFailed(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing 2FA verification failed for user: {}, attempt: {}", 
                   user.getId(), authEvent.getAttemptNumber());
        
        // Increment failed attempts
        int failedAttempts = twoFactorAuthService.incrementFailedAttempts(user.getId());
        
        // Check for lockout
        if (failedAttempts >= maxVerificationAttempts) {
            logger.warn("2FA verification attempts exceeded for user: {}, locking account", user.getId());
            
            twoFactorAuthService.lockUser(user.getId(), lockoutDurationMinutes);
            
            triggerAccountLockedEvent(user, "Too many 2FA verification failures", correlationId, traceId);
            
            // Send security alert
            if (sendNotifications) {
                notificationService.send2FALockoutNotification(user.getId(), lockoutDurationMinutes, correlationId);
            }
        }
        
        auditService.record2FAVerificationFailed(user.getId(), authEvent.getTokenId(), 
                                                authEvent.getAuthMethod(), failedAttempts, 
                                                authEvent.getIpAddress(), correlationId);
        
        metricsService.record2FAVerificationFailed(user.getId(), authEvent.getAuthMethod(), failedAttempts);
        
        logger.warn("2FA verification failed for user: {}, attempt: {}, totalFailed: {}", 
                   user.getId(), authEvent.getAttemptNumber(), failedAttempts);
    }

    private void processBackupCodeUsed(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.info("Processing backup code used for user: {}", user.getId());
        
        String backupCode = (String) authEvent.getEventMetadata().get("backupCode");
        
        // Mark backup code as used
        twoFactorAuthService.markBackupCodeAsUsed(user.getId(), backupCode);
        
        // Clear failed attempts
        twoFactorAuthService.clearFailedAttempts(user.getId());
        
        // Complete authentication
        authenticationService.complete2FAAuthentication(user.getId(), TwoFactorAuthMethod.BACKUP_CODE);
        
        // Check remaining backup codes
        int remainingCodes = twoFactorAuthService.getRemainingBackupCodes(user.getId());
        if (remainingCodes <= 2 && sendNotifications) {
            notificationService.sendLowBackupCodesWarning(user.getId(), remainingCodes, correlationId);
        }
        
        auditService.record2FABackupCodeUsed(user.getId(), backupCode, remainingCodes, 
                                           authEvent.getIpAddress(), correlationId);
        
        metricsService.record2FABackupCodeUsed(user.getId());
        
        logger.info("Backup code used for user: {}, remaining codes: {}", user.getId(), remainingCodes);
    }

    private void processBackupCodesRegenerated(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.info("Processing backup codes regenerated for user: {}", user.getId());
        
        // Generate new backup codes
        List<String> newBackupCodes = twoFactorAuthService.regenerateBackupCodes(user.getId(), backupCodesCount);
        
        // Send new codes to user
        if (sendNotifications) {
            notificationService.sendNewBackupCodes(user.getId(), newBackupCodes, correlationId);
        }
        
        auditService.record2FABackupCodesRegenerated(user.getId(), backupCodesCount, correlationId);
        
        metricsService.record2FABackupCodesRegenerated(user.getId());
        
        logger.info("Backup codes regenerated for user: {}, count: {}", user.getId(), backupCodesCount);
    }

    private void processMethodDisabled(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.info("Processing 2FA method disabled for user: {}", user.getId());
        
        // Disable 2FA method
        twoFactorAuthService.disableAuthMethod(user.getId(), authEvent.getAuthMethod());
        
        // Update user if this was their primary method
        if (user.getTwoFactorMethod() == authEvent.getAuthMethod()) {
            user.setTwoFactorEnabled(false);
            user.setTwoFactorMethod(null);
            userService.updateUser(user);
        }
        
        // Send confirmation notification
        if (sendNotifications) {
            notificationService.send2FAMethodDisabled(user.getId(), authEvent.getAuthMethod(), correlationId);
        }
        
        auditService.record2FAMethodDisabled(user.getId(), authEvent.getAuthMethod(), 
                                           authEvent.getReason(), correlationId);
        
        metricsService.record2FAMethodDisabled(user.getId(), authEvent.getAuthMethod());
        
        logger.info("2FA method disabled for user: {}, method: {}", user.getId(), authEvent.getAuthMethod());
    }

    private void processAccountLocked(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing 2FA account locked for user: {}", user.getId());
        
        String reason = authEvent.getReason() != null ? authEvent.getReason() : "2FA verification failures";
        
        // Lock the account
        user.setAccountLocked(true);
        user.setLockReason(reason);
        user.setLockTimestamp(authEvent.getTimestamp());
        userService.updateUser(user);
        
        // Invalidate all sessions
        userSecurityService.invalidateAllUserSessions(user.getId(), "Account locked due to 2FA failures");
        
        // Send security notification
        if (sendNotifications) {
            notificationService.sendAccountLockedNotification(user.getId(), reason, correlationId);
        }
        
        auditService.record2FAAccountLocked(user.getId(), reason, authEvent.getIpAddress(), correlationId);
        
        metricsService.record2FAAccountLocked(user.getId());
        
        logger.warn("Account locked for user: {}, reason: {}", user.getId(), reason);
    }

    private void processSuspiciousActivity(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing suspicious 2FA activity for user: {}", user.getId());
        
        String activityDetails = authEvent.getReason() != null ? authEvent.getReason() : "Suspicious 2FA activity";
        double riskScore = authEvent.getRiskScore() != null ? authEvent.getRiskScore() : 0.8;
        
        // Record security event
        userSecurityService.recordSecurityEvent(user.getId(), "SUSPICIOUS_2FA_ACTIVITY", 
                                               activityDetails, riskScore, correlationId);
        
        // Send security alert
        triggerSecurityAlert(user, activityDetails, correlationId);
        
        // Consider locking if risk is very high
        if (riskScore > 0.9) {
            twoFactorAuthService.lockUser(user.getId(), lockoutDurationMinutes * 2); // Extended lockout
        }
        
        auditService.recordSuspicious2FAActivity(user.getId(), activityDetails, riskScore, 
                                                authEvent.getIpAddress(), correlationId);
        
        metricsService.recordSuspicious2FAActivity(user.getId(), riskScore);
        
        logger.warn("Suspicious 2FA activity for user: {}, details: {}, risk: {}", 
                   user.getId(), activityDetails, riskScore);
    }

    private void processBypassAttempt(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        logger.error("Processing 2FA bypass attempt for user: {}", user.getId());
        
        String bypassMethod = authEvent.getReason() != null ? authEvent.getReason() : "Unknown bypass method";
        
        // Immediately lock the account
        user.setAccountLocked(true);
        user.setLockReason("2FA bypass attempt detected");
        user.setLockTimestamp(authEvent.getTimestamp());
        userService.updateUser(user);
        
        // Invalidate all sessions
        userSecurityService.invalidateAllUserSessions(user.getId(), "Security incident - bypass attempt");
        
        // Create security incident
        createSecurityIncident(user.getId(), "2FA_BYPASS_ATTEMPT", authEvent, correlationId);
        
        // Send immediate security alert
        if (sendNotifications) {
            notificationService.sendSecurityIncidentAlert(user.getId(), 
                                                         "2FA bypass attempt detected. Account has been locked.", correlationId);
        }
        
        auditService.record2FABypassAttempt(user.getId(), bypassMethod, authEvent.getIpAddress(), correlationId);
        
        metricsService.record2FABypassAttempt(user.getId());
        
        logger.error("2FA bypass attempt detected for user: {}, method: {}, IP: {}", 
                    user.getId(), bypassMethod, authEvent.getIpAddress());
    }

    private double calculateVerificationRiskScore(TwoFactorAuthEvent authEvent, User user, int failedAttempts) {
        double riskScore = 0.0;
        
        // Risk from failed attempts
        riskScore += Math.min(failedAttempts * 0.2, 0.6);
        
        // Risk from IP reputation
        if (authEvent.getIpAddress() != null) {
            riskScore += userSecurityService.getIpRiskScore(authEvent.getIpAddress()) * 0.3;
        }
        
        // Risk from unusual timing patterns
        if (authEvent.getAttemptNumber() != null && authEvent.getAttemptNumber() > 1) {
            riskScore += 0.1;
        }
        
        // Risk from device anomalies
        if (authEvent.getDeviceId() != null && 
            !userSecurityService.isKnownDevice(user.getId(), authEvent.getDeviceId())) {
            riskScore += 0.2;
        }
        
        return Math.min(riskScore, 1.0);
    }

    private void triggerAccountLockedEvent(User user, String reason, String correlationId, String traceId) {
        Map<String, Object> lockedEvent = new HashMap<>();
        lockedEvent.put("userId", user.getId());
        lockedEvent.put("eventType", "ACCOUNT_LOCKED");
        lockedEvent.put("reason", reason);
        lockedEvent.put("timestamp", LocalDateTime.now().toString());
        lockedEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("two-factor-auth-events", lockedEvent);
    }

    private void triggerSuspiciousActivityEvent(User user, double riskScore, String details, String correlationId, String traceId) {
        Map<String, Object> suspiciousEvent = new HashMap<>();
        suspiciousEvent.put("userId", user.getId());
        suspiciousEvent.put("eventType", "SUSPICIOUS_ACTIVITY");
        suspiciousEvent.put("riskScore", riskScore);
        suspiciousEvent.put("reason", details);
        suspiciousEvent.put("timestamp", LocalDateTime.now().toString());
        suspiciousEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("two-factor-auth-events", suspiciousEvent);
    }

    private void triggerSecurityAlert(User user, String message, String correlationId) {
        Map<String, Object> securityAlert = new HashMap<>();
        securityAlert.put("userId", user.getId());
        securityAlert.put("alertType", "2FA_SECURITY");
        securityAlert.put("severity", "HIGH");
        securityAlert.put("message", message);
        securityAlert.put("timestamp", LocalDateTime.now().toString());
        securityAlert.put("correlationId", correlationId);
        
        kafkaTemplate.send("security-alerts", securityAlert);
    }

    private void createSecurityIncident(String userId, String incidentType, TwoFactorAuthEvent authEvent, String correlationId) {
        Map<String, Object> incident = new HashMap<>();
        incident.put("userId", userId);
        incident.put("incidentType", incidentType);
        incident.put("severity", "CRITICAL");
        incident.put("tokenId", authEvent.getTokenId());
        incident.put("authMethod", authEvent.getAuthMethod());
        incident.put("ipAddress", authEvent.getIpAddress());
        incident.put("deviceId", authEvent.getDeviceId());
        incident.put("timestamp", authEvent.getTimestamp().toString());
        incident.put("eventMetadata", authEvent.getEventMetadata());
        incident.put("correlationId", correlationId);
        
        kafkaTemplate.send("security-incidents", incident);
    }

    private void recordTwoFactorAuthEventAudit(TwoFactorAuthEvent authEvent, User user, String correlationId, String traceId) {
        TwoFactorAuthAuditLog auditLog = new TwoFactorAuthAuditLog();
        auditLog.setUserId(user.getId());
        auditLog.setEventType(authEvent.getEventType());
        auditLog.setTokenId(authEvent.getTokenId());
        auditLog.setAuthMethod(authEvent.getAuthMethod());
        auditLog.setIpAddress(authEvent.getIpAddress());
        auditLog.setUserAgent(authEvent.getUserAgent());
        auditLog.setDeviceId(authEvent.getDeviceId());
        auditLog.setAttemptNumber(authEvent.getAttemptNumber());
        auditLog.setRiskScore(authEvent.getRiskScore());
        auditLog.setSecurityFlags(authEvent.getSecurityFlags());
        auditLog.setTimestamp(authEvent.getTimestamp());
        auditLog.setCorrelationId(correlationId);
        auditLog.setTraceId(traceId);
        
        auditService.recordTwoFactorAuthEventAudit(auditLog);
    }

    public void handleCircuitBreakerFallback(String message, String topic, int partition, long offset,
                                           String correlationId, String traceId, ConsumerRecord<String, String> record,
                                           Acknowledgment acknowledgment, Exception ex) {
        logger.error("Circuit breaker fallback triggered for two-factor auth events consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        // TODO: Use dlqHandler for fallback
        acknowledgment.acknowledge();
    }

    public enum TwoFactorAuthEventType {
        SETUP_INITIATED,
        METHOD_REGISTERED,
        SETUP_COMPLETED,
        CHALLENGE_REQUESTED,
        TOKEN_GENERATED,
        TOKEN_SENT,
        VERIFICATION_ATTEMPTED,
        VERIFICATION_SUCCESSFUL,
        VERIFICATION_FAILED,
        BACKUP_CODE_USED,
        BACKUP_CODES_REGENERATED,
        METHOD_DISABLED,
        ACCOUNT_LOCKED,
        SUSPICIOUS_ACTIVITY,
        BYPASS_ATTEMPT
    }

    public static class TwoFactorAuthEvent {
        private String userId;
        private TwoFactorAuthEventType eventType;
        private LocalDateTime timestamp;
        private String tokenId;
        private TwoFactorAuthMethod authMethod;
        private String ipAddress;
        private String userAgent;
        private String deviceId;
        private Integer attemptNumber;
        private Double riskScore;
        private List<String> securityFlags;
        private String reason;
        private Boolean backupCodeUsed;
        private Map<String, Object> eventMetadata;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public TwoFactorAuthEventType getEventType() { return eventType; }
        public void setEventType(TwoFactorAuthEventType eventType) { this.eventType = eventType; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public String getTokenId() { return tokenId; }
        public void setTokenId(String tokenId) { this.tokenId = tokenId; }

        public TwoFactorAuthMethod getAuthMethod() { return authMethod; }
        public void setAuthMethod(TwoFactorAuthMethod authMethod) { this.authMethod = authMethod; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

        public Integer getAttemptNumber() { return attemptNumber; }
        public void setAttemptNumber(Integer attemptNumber) { this.attemptNumber = attemptNumber; }

        public Double getRiskScore() { return riskScore; }
        public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

        public List<String> getSecurityFlags() { return securityFlags; }
        public void setSecurityFlags(List<String> securityFlags) { this.securityFlags = securityFlags; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public Boolean isBackupCodeUsed() { return backupCodeUsed; }
        public void setBackupCodeUsed(Boolean backupCodeUsed) { this.backupCodeUsed = backupCodeUsed; }

        public Map<String, Object> getEventMetadata() { return eventMetadata; }
        public void setEventMetadata(Map<String, Object> eventMetadata) { this.eventMetadata = eventMetadata; }
    }
}