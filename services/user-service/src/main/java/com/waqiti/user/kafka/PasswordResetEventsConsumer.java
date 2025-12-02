package com.waqiti.user.kafka;

import com.waqiti.common.kafka.dlq.UniversalDLQHandler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.user.model.User;
import com.waqiti.user.model.PasswordResetToken;
import com.waqiti.user.model.PasswordResetStatus;
import com.waqiti.user.model.PasswordResetAuditLog;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.PasswordResetService;
import com.waqiti.user.service.PasswordPolicyService;
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
public class PasswordResetEventsConsumer {

    private static final Logger logger = LoggerFactory.getLogger(PasswordResetEventsConsumer.class);
    private static final String CONSUMER_NAME = "password-reset-events-consumer";
    private static final String DLQ_TOPIC = "password-reset-events-dlq";
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int PROCESSING_TIMEOUT_SECONDS = 10;

    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final PasswordResetService passwordResetService;
    private final PasswordPolicyService passwordPolicyService;
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

    @Value("${kafka.consumer.password-reset-events.enabled:true}")
    private boolean consumerEnabled;

    @Value("${password.reset.token-expiry-hours:24}")
    private int tokenExpiryHours;

    @Value("${password.reset.max-attempts-per-day:5}")
    private int maxAttemptsPerDay;

    @Value("${password.reset.rate-limit-window-minutes:15}")
    private int rateLimitWindowMinutes;

    @Value("${password.reset.max-requests-per-window:3}")
    private int maxRequestsPerWindow;

    @Value("${password.reset.security-monitoring-enabled:true}")
    private boolean securityMonitoringEnabled;

    @Value("${password.reset.send-notifications:true}")
    private boolean sendNotifications;

    @Value("${password.reset.require-identity-verification:true}")
    private boolean requireIdentityVerification;

    private Counter processedCounter;
    private Counter errorCounter;
    private Counter dlqCounter;
    private Timer processingTimer;
    private Counter passwordResetEventTypeCounters;

    @PostConstruct
    public void initializeMetrics() {
        this.processedCounter = Counter.builder("password_reset_events_processed_total")
                .description("Total processed password reset events")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.errorCounter = Counter.builder("password_reset_events_errors_total")
                .description("Total password reset events processing errors")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("password_reset_events_dlq_total")
                .description("Total password reset events sent to DLQ")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        this.processingTimer = Timer.builder("password_reset_events_processing_duration")
                .description("Password reset events processing duration")
                .tag("consumer", CONSUMER_NAME)
                .register(meterRegistry);

        logger.info("PasswordResetEventsConsumer metrics initialized");
    }

    @KafkaListener(
        topics = "${kafka.topics.password-reset-events:password-reset-events}",
        groupId = "${kafka.consumer.group-id:user-service-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @CircuitBreaker(name = "password-reset-events-circuit-breaker", fallbackMethod = "handleCircuitBreakerFallback")
    @Retry(name = "password-reset-events-retry")
    public void processPasswordResetEvents(
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
                logger.warn("Password reset events consumer is disabled, skipping message processing");
                acknowledgment.acknowledge();
                return;
            }

            logger.info("Processing password reset event message: messageId={}, topic={}, partition={}, offset={}",
                    messageId, topic, partition, offset);

            if (!StringUtils.hasText(message)) {
                logger.warn("Received empty or null message, skipping processing");
                acknowledgment.acknowledge();
                return;
            }

            JsonNode messageNode = objectMapper.readTree(message);
            
            if (!isValidPasswordResetEventMessage(messageNode)) {
                logger.error("Invalid password reset event message format: {}", message);
                // TODO: Use dlqHandler for fallback
                acknowledgment.acknowledge();
                return;
            }

            String userId = messageNode.get("userId").asText();
            String eventType = messageNode.get("eventType").asText();

            long startTime = System.currentTimeMillis();
            
            CompletableFuture<Void> processingFuture = CompletableFuture.runAsync(() -> {
                try {
                    processPasswordResetEvent(messageNode, userId, eventType, correlationId, traceId);
                } catch (Exception e) {
                    logger.error("Error in async processing: {}", e.getMessage(), e);
                    throw new RuntimeException(e);
                }
            }).orTimeout(PROCESSING_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            processingFuture.get();

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("Successfully processed password reset event: messageId={}, userId={}, eventType={}, processingTime={}ms",
                    messageId, userId, eventType, processingTime);

            processedCounter.increment();
            metricsService.recordPasswordResetEventProcessed(eventType, processingTime);
            acknowledgment.acknowledge();

        } catch (JsonProcessingException e) {
            logger.error("Failed to parse password reset event message: messageId={}, error={}", messageId, e.getMessage());
            dlqHandler.handleFailedMessage(
                new ConsumerRecord<>(topic, partition, offset, null, null),
                e
            ).exceptionally(dlqError -> {
                logger.error("CRITICAL: DLQ handling failed", dlqError);
                return null;
            });

            throw new RuntimeException("Processing failed", e);
        } catch (Exception e) {
            
            logger.error("Unexpected error processing password reset event: messageId={}, error={}", messageId, e.getMessage(), e);

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

    private boolean isValidPasswordResetEventMessage(JsonNode messageNode) {
        try {
            return messageNode != null &&
                   messageNode.has("userId") && StringUtils.hasText(messageNode.get("userId").asText()) &&
                   messageNode.has("eventType") && StringUtils.hasText(messageNode.get("eventType").asText()) &&
                   messageNode.has("timestamp");
        } catch (Exception e) {
            logger.error("Error validating password reset event message: {}", e.getMessage());
            return false;
        }
    }

    @Transactional
    private void processPasswordResetEvent(JsonNode messageNode, String userId, String eventType, 
                                         String correlationId, String traceId) {
        try {
            User user = userService.findById(userId);
            if (user == null) {
                throw new ValidationException("User not found: " + userId);
            }

            PasswordResetEvent resetEvent = parsePasswordResetEvent(messageNode);
            
            validatePasswordResetEvent(resetEvent, user);
            
            switch (resetEvent.getEventType()) {
                case RESET_REQUESTED:
                    processResetRequested(resetEvent, user, correlationId, traceId);
                    break;
                case TOKEN_GENERATED:
                    processTokenGenerated(resetEvent, user, correlationId, traceId);
                    break;
                case TOKEN_SENT:
                    processTokenSent(resetEvent, user, correlationId, traceId);
                    break;
                case TOKEN_VERIFIED:
                    processTokenVerified(resetEvent, user, correlationId, traceId);
                    break;
                case PASSWORD_CHANGED:
                    processPasswordChanged(resetEvent, user, correlationId, traceId);
                    break;
                case RESET_COMPLETED:
                    processResetCompleted(resetEvent, user, correlationId, traceId);
                    break;
                case RESET_CANCELLED:
                    processResetCancelled(resetEvent, user, correlationId, traceId);
                    break;
                case TOKEN_EXPIRED:
                    processTokenExpired(resetEvent, user, correlationId, traceId);
                    break;
                case INVALID_TOKEN_USED:
                    processInvalidTokenUsed(resetEvent, user, correlationId, traceId);
                    break;
                case RATE_LIMIT_EXCEEDED:
                    processRateLimitExceeded(resetEvent, user, correlationId, traceId);
                    break;
                case SUSPICIOUS_ACTIVITY_DETECTED:
                    processSuspiciousActivityDetected(resetEvent, user, correlationId, traceId);
                    break;
                case IDENTITY_VERIFICATION_REQUIRED:
                    processIdentityVerificationRequired(resetEvent, user, correlationId, traceId);
                    break;
                case IDENTITY_VERIFICATION_COMPLETED:
                    processIdentityVerificationCompleted(resetEvent, user, correlationId, traceId);
                    break;
                case RESET_BLOCKED:
                    processResetBlocked(resetEvent, user, correlationId, traceId);
                    break;
                case POLICY_VIOLATION:
                    processPolicyViolation(resetEvent, user, correlationId, traceId);
                    break;
                default:
                    logger.warn("Unknown password reset event type: {}", resetEvent.getEventType());
                    throw new ValidationException("Unknown password reset event type: " + resetEvent.getEventType());
            }

            recordPasswordResetEventAudit(resetEvent, user, correlationId, traceId);

        } catch (Exception e) {
            logger.error("Error processing password reset event for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }

    private PasswordResetEvent parsePasswordResetEvent(JsonNode messageNode) {
        try {
            PasswordResetEvent event = new PasswordResetEvent();
            event.setUserId(messageNode.get("userId").asText());
            event.setEventType(PasswordResetEventType.valueOf(messageNode.get("eventType").asText()));
            event.setTimestamp(LocalDateTime.parse(messageNode.get("timestamp").asText(), DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            if (messageNode.has("resetTokenId")) {
                event.setResetTokenId(messageNode.get("resetTokenId").asText());
            }
            
            if (messageNode.has("ipAddress")) {
                event.setIpAddress(messageNode.get("ipAddress").asText());
            }
            
            if (messageNode.has("userAgent")) {
                event.setUserAgent(messageNode.get("userAgent").asText());
            }
            
            if (messageNode.has("requestMethod")) {
                event.setRequestMethod(messageNode.get("requestMethod").asText());
            }
            
            if (messageNode.has("verificationMethod")) {
                event.setVerificationMethod(messageNode.get("verificationMethod").asText());
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
            
            if (messageNode.has("previousAttempts")) {
                event.setPreviousAttempts(messageNode.get("previousAttempts").asInt());
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
            logger.error("Error parsing password reset event: {}", e.getMessage(), e);
            throw new ValidationException("Invalid password reset event format: " + e.getMessage());
        }
    }

    private void validatePasswordResetEvent(PasswordResetEvent resetEvent, User user) {
        if (!user.isActive()) {
            throw new ValidationException("User is not active: " + user.getId());
        }
        
        if (user.isAccountLocked()) {
            throw new ValidationException("User account is locked: " + user.getId());
        }
    }

    private void processResetRequested(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.info("Processing password reset requested for user: {}", user.getId());
        
        // Check rate limiting
        if (isRateLimited(user.getId(), resetEvent.getTimestamp())) {
            logger.warn("Password reset rate limit exceeded for user: {}", user.getId());
            triggerRateLimitEvent(user, correlationId, traceId);
            return;
        }
        
        // Check daily attempt limit
        int dailyAttempts = passwordResetService.getDailyResetAttempts(user.getId(), resetEvent.getTimestamp().toLocalDate());
        if (dailyAttempts >= maxAttemptsPerDay) {
            logger.warn("Daily password reset attempt limit exceeded for user: {}", user.getId());
            triggerDailyLimitEvent(user, dailyAttempts, correlationId, traceId);
            return;
        }
        
        // Security monitoring
        if (securityMonitoringEnabled) {
            double riskScore = calculateResetRiskScore(resetEvent, user);
            if (riskScore > 0.7) {
                logger.warn("High risk password reset request detected for user: {}, risk score: {}", user.getId(), riskScore);
                triggerSuspiciousActivityEvent(user, riskScore, "High risk reset request", correlationId, traceId);
                
                if (riskScore > 0.9) {
                    triggerResetBlockedEvent(user, "Risk score too high: " + riskScore, correlationId, traceId);
                    return;
                }
            }
        }
        
        // Identity verification check
        if (requireIdentityVerification && shouldRequireVerification(user, resetEvent)) {
            triggerIdentityVerificationRequired(user, correlationId, traceId);
            return;
        }
        
        // Generate reset token
        PasswordResetToken resetToken = passwordResetService.generateResetToken(user.getId(), tokenExpiryHours);
        
        // Send notification
        if (sendNotifications) {
            sendPasswordResetNotification(user, resetToken, resetEvent, correlationId);
        }
        
        auditService.recordPasswordResetRequested(user.getId(), resetEvent.getIpAddress(), 
                                                 resetEvent.getUserAgent(), dailyAttempts + 1, correlationId);
        
        metricsService.recordPasswordResetRequested(user.getId());
        
        logger.info("Password reset requested successfully for user: {}", user.getId());
    }

    private void processTokenGenerated(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.info("Processing token generated for user: {}", user.getId());
        
        String tokenId = resetEvent.getResetTokenId();
        PasswordResetToken resetToken = passwordResetService.getResetToken(tokenId);
        
        if (resetToken != null) {
            resetToken.setStatus(PasswordResetStatus.GENERATED);
            resetToken.setGeneratedAt(resetEvent.getTimestamp());
            passwordResetService.updateResetToken(resetToken);
            
            auditService.recordPasswordResetTokenGenerated(user.getId(), tokenId, correlationId);
            
            logger.info("Password reset token generated for user: {}, tokenId: {}", user.getId(), tokenId);
        } else {
            logger.warn("Reset token not found for token generated event: {}", tokenId);
        }
    }

    private void processTokenSent(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.info("Processing token sent for user: {}", user.getId());
        
        String tokenId = resetEvent.getResetTokenId();
        PasswordResetToken resetToken = passwordResetService.getResetToken(tokenId);
        
        if (resetToken != null) {
            resetToken.setStatus(PasswordResetStatus.SENT);
            resetToken.setSentAt(resetEvent.getTimestamp());
            resetToken.setSentMethod(resetEvent.getVerificationMethod());
            passwordResetService.updateResetToken(resetToken);
            
            auditService.recordPasswordResetTokenSent(user.getId(), tokenId, 
                                                    resetEvent.getVerificationMethod(), correlationId);
            
            logger.info("Password reset token sent for user: {}, tokenId: {}, method: {}", 
                       user.getId(), tokenId, resetEvent.getVerificationMethod());
        } else {
            logger.warn("Reset token not found for token sent event: {}", tokenId);
        }
    }

    private void processTokenVerified(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.info("Processing token verified for user: {}", user.getId());
        
        String tokenId = resetEvent.getResetTokenId();
        PasswordResetToken resetToken = passwordResetService.getResetToken(tokenId);
        
        if (resetToken != null) {
            if (resetToken.isExpired()) {
                logger.warn("Attempted to verify expired token for user: {}, tokenId: {}", user.getId(), tokenId);
                triggerTokenExpiredEvent(user, tokenId, correlationId, traceId);
                return;
            }
            
            if (!resetToken.isValid()) {
                logger.warn("Attempted to verify invalid token for user: {}, tokenId: {}", user.getId(), tokenId);
                triggerInvalidTokenEvent(user, tokenId, correlationId, traceId);
                return;
            }
            
            resetToken.setStatus(PasswordResetStatus.VERIFIED);
            resetToken.setVerifiedAt(resetEvent.getTimestamp());
            passwordResetService.updateResetToken(resetToken);
            
            auditService.recordPasswordResetTokenVerified(user.getId(), tokenId, 
                                                        resetEvent.getIpAddress(), correlationId);
            
            metricsService.recordPasswordResetTokenVerified(user.getId());
            
            logger.info("Password reset token verified for user: {}, tokenId: {}", user.getId(), tokenId);
        } else {
            logger.warn("Reset token not found for token verified event: {}", tokenId);
        }
    }

    private void processPasswordChanged(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.info("Processing password changed for user: {}", user.getId());
        
        String tokenId = resetEvent.getResetTokenId();
        PasswordResetToken resetToken = passwordResetService.getResetToken(tokenId);
        
        if (resetToken != null && resetToken.getStatus() == PasswordResetStatus.VERIFIED) {
            resetToken.setStatus(PasswordResetStatus.USED);
            resetToken.setUsedAt(resetEvent.getTimestamp());
            passwordResetService.updateResetToken(resetToken);
            
            // Update user password metadata
            user.setLastPasswordChange(resetEvent.getTimestamp());
            user.setPasswordResetRequired(false);
            userService.updateUser(user);
            
            // Invalidate all existing sessions
            userSecurityService.invalidateAllUserSessions(user.getId(), "Password changed");
            
            // Send confirmation notification
            if (sendNotifications) {
                notificationService.sendPasswordChangeConfirmation(user.getId(), 
                                                                  resetEvent.getIpAddress(), correlationId);
            }
            
            auditService.recordPasswordChanged(user.getId(), tokenId, resetEvent.getIpAddress(), correlationId);
            
            metricsService.recordPasswordChanged(user.getId());
            
            logger.info("Password changed successfully for user: {}, tokenId: {}", user.getId(), tokenId);
        } else {
            logger.warn("Invalid token state for password change: user={}, tokenId={}, status={}", 
                       user.getId(), tokenId, resetToken != null ? resetToken.getStatus() : "NOT_FOUND");
        }
    }

    private void processResetCompleted(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.info("Processing reset completed for user: {}", user.getId());
        
        String tokenId = resetEvent.getResetTokenId();
        PasswordResetToken resetToken = passwordResetService.getResetToken(tokenId);
        
        if (resetToken != null) {
            resetToken.setStatus(PasswordResetStatus.COMPLETED);
            resetToken.setCompletedAt(resetEvent.getTimestamp());
            passwordResetService.updateResetToken(resetToken);
            
            // Clean up any other pending reset tokens for this user
            passwordResetService.invalidateOtherResetTokens(user.getId(), tokenId);
            
            auditService.recordPasswordResetCompleted(user.getId(), tokenId, correlationId);
            
            metricsService.recordPasswordResetCompleted(user.getId());
            
            logger.info("Password reset completed for user: {}, tokenId: {}", user.getId(), tokenId);
        }
    }

    private void processResetCancelled(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.info("Processing reset cancelled for user: {}", user.getId());
        
        String tokenId = resetEvent.getResetTokenId();
        String reason = resetEvent.getReason() != null ? resetEvent.getReason() : "User cancelled";
        
        if (tokenId != null) {
            PasswordResetToken resetToken = passwordResetService.getResetToken(tokenId);
            if (resetToken != null) {
                resetToken.setStatus(PasswordResetStatus.CANCELLED);
                resetToken.setCancelledAt(resetEvent.getTimestamp());
                resetToken.setCancellationReason(reason);
                passwordResetService.updateResetToken(resetToken);
            }
        } else {
            // Cancel all pending reset tokens for user
            passwordResetService.cancelAllPendingResets(user.getId(), reason);
        }
        
        auditService.recordPasswordResetCancelled(user.getId(), tokenId, reason, correlationId);
        
        metricsService.recordPasswordResetCancelled(user.getId(), reason);
        
        logger.info("Password reset cancelled for user: {}, reason: {}", user.getId(), reason);
    }

    private void processTokenExpired(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.info("Processing token expired for user: {}", user.getId());
        
        String tokenId = resetEvent.getResetTokenId();
        PasswordResetToken resetToken = passwordResetService.getResetToken(tokenId);
        
        if (resetToken != null) {
            resetToken.setStatus(PasswordResetStatus.EXPIRED);
            resetToken.setExpiredAt(resetEvent.getTimestamp());
            passwordResetService.updateResetToken(resetToken);
            
            // Send notification about expired token
            if (sendNotifications) {
                notificationService.sendPasswordResetTokenExpired(user.getId(), tokenId, correlationId);
            }
            
            auditService.recordPasswordResetTokenExpired(user.getId(), tokenId, correlationId);
            
            metricsService.recordPasswordResetTokenExpired(user.getId());
            
            logger.info("Password reset token expired for user: {}, tokenId: {}", user.getId(), tokenId);
        }
    }

    private void processInvalidTokenUsed(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing invalid token used for user: {}", user.getId());
        
        String tokenId = resetEvent.getResetTokenId();
        
        // Track invalid attempts
        passwordResetService.recordInvalidTokenAttempt(user.getId(), tokenId, resetEvent.getIpAddress());
        
        // Check for suspicious activity
        int recentInvalidAttempts = passwordResetService.getRecentInvalidAttempts(user.getId(), 15); // Last 15 minutes
        if (recentInvalidAttempts >= 3) {
            logger.warn("Multiple invalid token attempts detected for user: {}, count: {}", user.getId(), recentInvalidAttempts);
            triggerSuspiciousActivityEvent(user, 0.8, "Multiple invalid token attempts", correlationId, traceId);
        }
        
        auditService.recordInvalidPasswordResetToken(user.getId(), tokenId, 
                                                   resetEvent.getIpAddress(), recentInvalidAttempts, correlationId);
        
        metricsService.recordInvalidPasswordResetToken(user.getId());
        
        logger.warn("Invalid password reset token used for user: {}, tokenId: {}, attempts: {}", 
                   user.getId(), tokenId, recentInvalidAttempts);
    }

    private void processRateLimitExceeded(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing rate limit exceeded for user: {}", user.getId());
        
        // Record rate limit violation
        passwordResetService.recordRateLimitViolation(user.getId(), resetEvent.getIpAddress(), resetEvent.getTimestamp());
        
        // Send notification about rate limiting
        if (sendNotifications) {
            notificationService.sendPasswordResetRateLimitNotification(user.getId(), correlationId);
        }
        
        auditService.recordPasswordResetRateLimitExceeded(user.getId(), resetEvent.getIpAddress(), 
                                                        resetEvent.getPreviousAttempts(), correlationId);
        
        metricsService.recordPasswordResetRateLimitExceeded(user.getId());
        
        logger.warn("Password reset rate limit exceeded for user: {}, IP: {}", user.getId(), resetEvent.getIpAddress());
    }

    private void processSuspiciousActivityDetected(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing suspicious activity detected for user: {}", user.getId());
        
        String activityDetails = resetEvent.getReason() != null ? resetEvent.getReason() : "Suspicious reset activity";
        double riskScore = resetEvent.getRiskScore() != null ? resetEvent.getRiskScore() : 0.8;
        
        // Record security event
        userSecurityService.recordSecurityEvent(user.getId(), "SUSPICIOUS_PASSWORD_RESET", 
                                               activityDetails, riskScore, correlationId);
        
        // Send security alert
        triggerSecurityAlert(user, activityDetails, correlationId);
        
        // Consider blocking further reset attempts
        if (riskScore > 0.9) {
            passwordResetService.blockPasswordResets(user.getId(), "High risk activity detected", 24); // Block for 24 hours
        }
        
        auditService.recordSuspiciousPasswordResetActivity(user.getId(), activityDetails, 
                                                          riskScore, resetEvent.getIpAddress(), correlationId);
        
        metricsService.recordSuspiciousPasswordResetActivity(user.getId(), riskScore);
        
        logger.warn("Suspicious password reset activity detected for user: {}, details: {}, risk: {}", 
                   user.getId(), activityDetails, riskScore);
    }

    private void processIdentityVerificationRequired(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.info("Processing identity verification required for user: {}", user.getId());
        
        // Create identity verification challenge
        String verificationId = userSecurityService.createIdentityVerificationChallenge(user.getId(), "PASSWORD_RESET");
        
        // Send verification notification
        if (sendNotifications) {
            notificationService.sendIdentityVerificationRequired(user.getId(), verificationId, correlationId);
        }
        
        auditService.recordIdentityVerificationRequired(user.getId(), verificationId, "PASSWORD_RESET", correlationId);
        
        metricsService.recordIdentityVerificationRequired(user.getId(), "PASSWORD_RESET");
        
        logger.info("Identity verification required for user: {}, verificationId: {}", user.getId(), verificationId);
    }

    private void processIdentityVerificationCompleted(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.info("Processing identity verification completed for user: {}", user.getId());
        
        String verificationId = (String) resetEvent.getEventMetadata().get("verificationId");
        boolean verificationPassed = Boolean.parseBoolean((String) resetEvent.getEventMetadata().get("passed"));
        
        if (verificationPassed) {
            // Proceed with password reset process
            userSecurityService.clearPasswordResetBlocks(user.getId());
            
            auditService.recordIdentityVerificationCompleted(user.getId(), verificationId, true, correlationId);
            
            logger.info("Identity verification passed for user: {}, allowing password reset", user.getId());
        } else {
            // Block password reset attempts
            passwordResetService.blockPasswordResets(user.getId(), "Identity verification failed", 24);
            
            auditService.recordIdentityVerificationCompleted(user.getId(), verificationId, false, correlationId);
            
            logger.warn("Identity verification failed for user: {}, blocking password resets", user.getId());
        }
        
        metricsService.recordIdentityVerificationCompleted(user.getId(), verificationPassed);
    }

    private void processResetBlocked(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing reset blocked for user: {}", user.getId());
        
        String reason = resetEvent.getReason() != null ? resetEvent.getReason() : "Security policy violation";
        
        // Record the block
        passwordResetService.recordPasswordResetBlock(user.getId(), reason, resetEvent.getTimestamp());
        
        // Send notification
        if (sendNotifications) {
            notificationService.sendPasswordResetBlocked(user.getId(), reason, correlationId);
        }
        
        auditService.recordPasswordResetBlocked(user.getId(), reason, resetEvent.getIpAddress(), correlationId);
        
        metricsService.recordPasswordResetBlocked(user.getId(), reason);
        
        logger.warn("Password reset blocked for user: {}, reason: {}", user.getId(), reason);
    }

    private void processPolicyViolation(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        logger.warn("Processing policy violation for user: {}", user.getId());
        
        String violation = resetEvent.getReason() != null ? resetEvent.getReason() : "Password policy violation";
        
        // Record policy violation
        passwordPolicyService.recordPolicyViolation(user.getId(), violation, resetEvent.getTimestamp());
        
        auditService.recordPasswordPolicyViolation(user.getId(), violation, correlationId);
        
        metricsService.recordPasswordPolicyViolation(user.getId(), violation);
        
        logger.warn("Password policy violation for user: {}, violation: {}", user.getId(), violation);
    }

    private boolean isRateLimited(String userId, LocalDateTime requestTime) {
        return passwordResetService.isRateLimited(userId, requestTime, rateLimitWindowMinutes, maxRequestsPerWindow);
    }

    private double calculateResetRiskScore(PasswordResetEvent resetEvent, User user) {
        double riskScore = 0.0;
        
        // Base risk from IP reputation
        if (resetEvent.getIpAddress() != null) {
            riskScore += userSecurityService.getIpRiskScore(resetEvent.getIpAddress()) * 0.3;
        }
        
        // Risk from frequency of requests
        int recentAttempts = passwordResetService.getRecentResetAttempts(user.getId(), 24); // Last 24 hours
        if (recentAttempts > 1) {
            riskScore += Math.min(recentAttempts * 0.1, 0.3);
        }
        
        // Risk from user agent anomalies
        if (resetEvent.getUserAgent() != null) {
            riskScore += userSecurityService.getUserAgentRiskScore(user.getId(), resetEvent.getUserAgent()) * 0.2;
        }
        
        // Risk from account status
        if (user.getLastPasswordChange() != null && 
            ChronoUnit.DAYS.between(user.getLastPasswordChange(), resetEvent.getTimestamp()) < 1) {
            riskScore += 0.4; // Recently changed password
        }
        
        // Risk from previous security incidents
        if (userSecurityService.hasRecentSecurityIncidents(user.getId(), 7)) {
            riskScore += 0.3;
        }
        
        return Math.min(riskScore, 1.0);
    }

    private boolean shouldRequireVerification(User user, PasswordResetEvent resetEvent) {
        // Require verification for high-risk scenarios
        double riskScore = calculateResetRiskScore(resetEvent, user);
        if (riskScore > 0.6) {
            return true;
        }
        
        // Require verification if recent suspicious activity
        if (userSecurityService.hasRecentSecurityIncidents(user.getId(), 30)) {
            return true;
        }
        
        // Require verification for privileged accounts
        if (user.hasElevatedPrivileges()) {
            return true;
        }
        
        return false;
    }

    private void sendPasswordResetNotification(User user, PasswordResetToken resetToken, 
                                             PasswordResetEvent resetEvent, String correlationId) {
        try {
            notificationService.sendPasswordResetToken(user.getId(), resetToken.getToken(), 
                                                      resetToken.getExpiryTime(), correlationId);
            
            // Also send security notification about the reset request
            notificationService.sendPasswordResetSecurityNotification(user.getId(), 
                                                                     resetEvent.getIpAddress(), 
                                                                     resetEvent.getTimestamp(), correlationId);
        } catch (Exception e) {
            logger.error("Failed to send password reset notification for user: {}, error: {}", user.getId(), e.getMessage());
        }
    }

    private void triggerRateLimitEvent(User user, String correlationId, String traceId) {
        Map<String, Object> rateLimitEvent = new HashMap<>();
        rateLimitEvent.put("userId", user.getId());
        rateLimitEvent.put("eventType", "RATE_LIMIT_EXCEEDED");
        rateLimitEvent.put("timestamp", LocalDateTime.now().toString());
        rateLimitEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("password-reset-events", rateLimitEvent);
    }

    private void triggerDailyLimitEvent(User user, int attempts, String correlationId, String traceId) {
        Map<String, Object> limitEvent = new HashMap<>();
        limitEvent.put("userId", user.getId());
        limitEvent.put("eventType", "DAILY_LIMIT_EXCEEDED");
        limitEvent.put("attempts", attempts);
        limitEvent.put("timestamp", LocalDateTime.now().toString());
        limitEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("password-reset-events", limitEvent);
    }

    private void triggerSuspiciousActivityEvent(User user, double riskScore, String details, String correlationId, String traceId) {
        Map<String, Object> suspiciousEvent = new HashMap<>();
        suspiciousEvent.put("userId", user.getId());
        suspiciousEvent.put("eventType", "SUSPICIOUS_ACTIVITY_DETECTED");
        suspiciousEvent.put("riskScore", riskScore);
        suspiciousEvent.put("reason", details);
        suspiciousEvent.put("timestamp", LocalDateTime.now().toString());
        suspiciousEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("password-reset-events", suspiciousEvent);
    }

    private void triggerResetBlockedEvent(User user, String reason, String correlationId, String traceId) {
        Map<String, Object> blockedEvent = new HashMap<>();
        blockedEvent.put("userId", user.getId());
        blockedEvent.put("eventType", "RESET_BLOCKED");
        blockedEvent.put("reason", reason);
        blockedEvent.put("timestamp", LocalDateTime.now().toString());
        blockedEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("password-reset-events", blockedEvent);
    }

    private void triggerIdentityVerificationRequired(User user, String correlationId, String traceId) {
        Map<String, Object> verificationEvent = new HashMap<>();
        verificationEvent.put("userId", user.getId());
        verificationEvent.put("eventType", "IDENTITY_VERIFICATION_REQUIRED");
        verificationEvent.put("timestamp", LocalDateTime.now().toString());
        verificationEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("password-reset-events", verificationEvent);
    }

    private void triggerTokenExpiredEvent(User user, String tokenId, String correlationId, String traceId) {
        Map<String, Object> expiredEvent = new HashMap<>();
        expiredEvent.put("userId", user.getId());
        expiredEvent.put("eventType", "TOKEN_EXPIRED");
        expiredEvent.put("resetTokenId", tokenId);
        expiredEvent.put("timestamp", LocalDateTime.now().toString());
        expiredEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("password-reset-events", expiredEvent);
    }

    private void triggerInvalidTokenEvent(User user, String tokenId, String correlationId, String traceId) {
        Map<String, Object> invalidEvent = new HashMap<>();
        invalidEvent.put("userId", user.getId());
        invalidEvent.put("eventType", "INVALID_TOKEN_USED");
        invalidEvent.put("resetTokenId", tokenId);
        invalidEvent.put("timestamp", LocalDateTime.now().toString());
        invalidEvent.put("correlationId", correlationId);
        
        kafkaTemplate.send("password-reset-events", invalidEvent);
    }

    private void triggerSecurityAlert(User user, String message, String correlationId) {
        Map<String, Object> securityAlert = new HashMap<>();
        securityAlert.put("userId", user.getId());
        securityAlert.put("alertType", "PASSWORD_RESET_SECURITY");
        securityAlert.put("severity", "MEDIUM");
        securityAlert.put("message", message);
        securityAlert.put("timestamp", LocalDateTime.now().toString());
        securityAlert.put("correlationId", correlationId);
        
        kafkaTemplate.send("security-alerts", securityAlert);
    }

    private void recordPasswordResetEventAudit(PasswordResetEvent resetEvent, User user, String correlationId, String traceId) {
        PasswordResetAuditLog auditLog = new PasswordResetAuditLog();
        auditLog.setUserId(user.getId());
        auditLog.setEventType(resetEvent.getEventType());
        auditLog.setResetTokenId(resetEvent.getResetTokenId());
        auditLog.setIpAddress(resetEvent.getIpAddress());
        auditLog.setUserAgent(resetEvent.getUserAgent());
        auditLog.setRequestMethod(resetEvent.getRequestMethod());
        auditLog.setVerificationMethod(resetEvent.getVerificationMethod());
        auditLog.setRiskScore(resetEvent.getRiskScore());
        auditLog.setSecurityFlags(resetEvent.getSecurityFlags());
        auditLog.setTimestamp(resetEvent.getTimestamp());
        auditLog.setCorrelationId(correlationId);
        auditLog.setTraceId(traceId);
        
        auditService.recordPasswordResetEventAudit(auditLog);
    }

    public void handleCircuitBreakerFallback(String message, String topic, int partition, long offset,
                                           String correlationId, String traceId, ConsumerRecord<String, String> record,
                                           Acknowledgment acknowledgment, Exception ex) {
        logger.error("Circuit breaker fallback triggered for password reset events consumer: {}", ex.getMessage());
        
        errorCounter.increment();
        // TODO: Use dlqHandler for fallback
        acknowledgment.acknowledge();
    }

    public enum PasswordResetEventType {
        RESET_REQUESTED,
        TOKEN_GENERATED,
        TOKEN_SENT,
        TOKEN_VERIFIED,
        PASSWORD_CHANGED,
        RESET_COMPLETED,
        RESET_CANCELLED,
        TOKEN_EXPIRED,
        INVALID_TOKEN_USED,
        RATE_LIMIT_EXCEEDED,
        SUSPICIOUS_ACTIVITY_DETECTED,
        IDENTITY_VERIFICATION_REQUIRED,
        IDENTITY_VERIFICATION_COMPLETED,
        RESET_BLOCKED,
        POLICY_VIOLATION
    }

    public static class PasswordResetEvent {
        private String userId;
        private PasswordResetEventType eventType;
        private LocalDateTime timestamp;
        private String resetTokenId;
        private String ipAddress;
        private String userAgent;
        private String requestMethod;
        private String verificationMethod;
        private Double riskScore;
        private List<String> securityFlags;
        private String reason;
        private Integer previousAttempts;
        private Map<String, Object> eventMetadata;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }

        public PasswordResetEventType getEventType() { return eventType; }
        public void setEventType(PasswordResetEventType eventType) { this.eventType = eventType; }

        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public String getResetTokenId() { return resetTokenId; }
        public void setResetTokenId(String resetTokenId) { this.resetTokenId = resetTokenId; }

        public String getIpAddress() { return ipAddress; }
        public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

        public String getUserAgent() { return userAgent; }
        public void setUserAgent(String userAgent) { this.userAgent = userAgent; }

        public String getRequestMethod() { return requestMethod; }
        public void setRequestMethod(String requestMethod) { this.requestMethod = requestMethod; }

        public String getVerificationMethod() { return verificationMethod; }
        public void setVerificationMethod(String verificationMethod) { this.verificationMethod = verificationMethod; }

        public Double getRiskScore() { return riskScore; }
        public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }

        public List<String> getSecurityFlags() { return securityFlags; }
        public void setSecurityFlags(List<String> securityFlags) { this.securityFlags = securityFlags; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public Integer getPreviousAttempts() { return previousAttempts; }
        public void setPreviousAttempts(Integer previousAttempts) { this.previousAttempts = previousAttempts; }

        public Map<String, Object> getEventMetadata() { return eventMetadata; }
        public void setEventMetadata(Map<String, Object> eventMetadata) { this.eventMetadata = eventMetadata; }
    }
}