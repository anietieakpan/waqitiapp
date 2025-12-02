package com.waqiti.security.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.security.domain.AuthFailure;
import com.waqiti.security.domain.FailureType;
import com.waqiti.security.domain.RiskLevel;
import com.waqiti.security.repository.AuthFailureRepository;
import com.waqiti.security.service.AuthFailureService;
import com.waqiti.security.service.SecurityNotificationService;
import com.waqiti.security.service.ThreatDetectionService;
import com.waqiti.security.service.AccountLockService;
import com.waqiti.security.service.FraudDetectionService;
import com.waqiti.common.audit.AuditService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.DltHandler;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class AuthFailuresConsumer {

    private final AuthFailureRepository failureRepository;
    private final AuthFailureService failureService;
    private final SecurityNotificationService notificationService;
    private final ThreatDetectionService threatService;
    private final AccountLockService lockService;
    private final FraudDetectionService fraudService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    private final Counter authFailuresCounter;
    private final Counter bruteForceAttemptsCounter;
    private final Counter suspiciousActivityCounter;
    private final Counter accountLockedCounter;
    private final Timer failureProcessingTimer;
    
    private final Map<String, String> processedEvents = new ConcurrentHashMap<>();
    private static final long IDEMPOTENCY_TTL_MS = TimeUnit.HOURS.toMillis(24);

    public AuthFailuresConsumer(
            AuthFailureRepository failureRepository,
            AuthFailureService failureService,
            SecurityNotificationService notificationService,
            ThreatDetectionService threatService,
            AccountLockService lockService,
            FraudDetectionService fraudService,
            AuditService auditService,
            KafkaTemplate<String, Object> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry) {
        
        this.failureRepository = failureRepository;
        this.failureService = failureService;
        this.notificationService = notificationService;
        this.threatService = threatService;
        this.lockService = lockService;
        this.fraudService = fraudService;
        this.auditService = auditService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        
        this.authFailuresCounter = Counter.builder("auth.failures.events")
            .description("Count of authentication failure events")
            .register(meterRegistry);
        
        this.bruteForceAttemptsCounter = Counter.builder("auth.failures.brute.force.events")
            .description("Count of brute force attack attempts")
            .register(meterRegistry);
        
        this.suspiciousActivityCounter = Counter.builder("auth.failures.suspicious.activity.events")
            .description("Count of suspicious authentication activities")
            .register(meterRegistry);
        
        this.accountLockedCounter = Counter.builder("auth.failures.account.locked.events")
            .description("Count of accounts locked due to auth failures")
            .register(meterRegistry);
        
        this.failureProcessingTimer = Timer.builder("auth.failures.processing.duration")
            .description("Time taken to process auth failure events")
            .register(meterRegistry);
    }

    @KafkaListener(
        topics = "auth-failures",
        groupId = "auth-failures-processor-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
        dltTopic = "auth-failures-dlt",
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handleAuthFailureEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        Timer.Sample sample = Timer.start();
        String eventId = String.format("%s-%d-%d-%d", topic, partition, offset, System.currentTimeMillis());
        
        log.info("Received auth failure event - eventId: {}, partition: {}, offset: {}", 
            eventId, partition, offset);
        
        try {
            if (isAlreadyProcessed(eventId)) {
                log.info("Event {} already processed, skipping", eventId);
                acknowledgment.acknowledge();
                return;
            }
            
            Map<String, Object> eventData = objectMapper.readValue(message, Map.class);
            
            String username = (String) eventData.get("username");
            String ipAddress = (String) eventData.get("ipAddress");
            String userAgent = (String) eventData.get("userAgent");
            String failureReason = (String) eventData.get("failureReason");
            String error = (String) eventData.get("error");
            Object timestampObj = eventData.get("timestamp");
            String sessionId = (String) eventData.get("sessionId");
            String deviceId = (String) eventData.get("deviceId");
            
            String correlationId = String.format("auth-failure-%s-%s-%d", 
                username, failureReason, System.currentTimeMillis());
            
            log.warn("Processing auth failure - username: {}, reason: {}, ip: {}, correlationId: {}", 
                username, failureReason, ipAddress, correlationId);
            
            authFailuresCounter.increment();
            
            processAuthFailure(username, ipAddress, userAgent, failureReason, error, 
                sessionId, deviceId, eventData, correlationId);
            
            markAsProcessed(eventId);
            acknowledgment.acknowledge();
            
            sample.stop(failureProcessingTimer);
            
            log.info("Successfully processed auth failure event: {}", eventId);
            
        } catch (Exception e) {
            log.error("Failed to process auth failure event {}: {}", eventId, e.getMessage(), e);
            throw new RuntimeException("Auth failure processing failed", e);
        }
    }

    @CircuitBreaker(name = "security", fallbackMethod = "processAuthFailureFallback")
    @Retry(name = "security")
    private void processAuthFailure(
            String username,
            String ipAddress,
            String userAgent,
            String failureReason,
            String error,
            String sessionId,
            String deviceId,
            Map<String, Object> eventData,
            String correlationId) {
        
        FailureType failureType = determineFailureType(failureReason);
        RiskLevel riskLevel = threatService.assessAuthFailureRisk(username, ipAddress, failureType);
        
        AuthFailure failure = AuthFailure.builder()
            .username(username)
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .failureType(failureType)
            .failureReason(failureReason)
            .error(error)
            .sessionId(sessionId)
            .deviceId(deviceId)
            .riskLevel(riskLevel)
            .failedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .metadata(eventData)
            .build();
        
        failureRepository.save(failure);
        
        failureService.processAuthFailure(failure, correlationId);
        
        // Check for suspicious patterns
        boolean isSuspicious = threatService.detectSuspiciousActivity(failure);
        if (isSuspicious) {
            suspiciousActivityCounter.increment();
            handleSuspiciousActivity(failure, correlationId);
        }
        
        // Check for brute force attempts
        boolean isBruteForce = threatService.detectBruteForceAttempt(failure);
        if (isBruteForce) {
            bruteForceAttemptsCounter.increment();
            handleBruteForceAttempt(failure, correlationId);
        }
        
        // Handle based on failure type
        switch (failureType) {
            case INVALID_CREDENTIALS -> handleInvalidCredentials(failure, correlationId);
            case ACCOUNT_LOCKED -> handleAccountLocked(failure, correlationId);
            case SUSPICIOUS_LOCATION -> handleSuspiciousLocation(failure, correlationId);
            case DEVICE_NOT_RECOGNIZED -> handleUnrecognizedDevice(failure, correlationId);
            case MFA_FAILURE -> handleMFAFailure(failure, correlationId);
            case RATE_LIMITED -> handleRateLimited(failure, correlationId);
            default -> handleGenericFailure(failure, correlationId);
        }
        
        // Check if account should be locked
        if (shouldLockAccount(failure)) {
            accountLockedCounter.increment();
            lockService.lockAccount(username, failureReason, correlationId);
        }
        
        notificationService.sendAuthFailureNotification(
            failure,
            correlationId
        );
        
        kafkaTemplate.send("auth-failure-processed", Map.of(
            "username", username,
            "ipAddress", ipAddress,
            "failureType", failureType.toString(),
            "failureReason", failureReason,
            "riskLevel", riskLevel.toString(),
            "isSuspicious", isSuspicious,
            "isBruteForce", isBruteForce,
            "eventType", "AUTH_FAILURE_PROCESSED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        auditService.logSecurityEvent(
            "AUTH_FAILURE_PROCESSED",
            username,
            Map.of(
                "ipAddress", ipAddress,
                "userAgent", userAgent,
                "failureType", failureType.toString(),
                "failureReason", failureReason,
                "error", error,
                "riskLevel", riskLevel.toString(),
                "sessionId", sessionId,
                "deviceId", deviceId,
                "isSuspicious", isSuspicious,
                "isBruteForce", isBruteForce,
                "correlationId", correlationId,
                "timestamp", Instant.now().toString()
            )
        );
        
        if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL) {
            log.error("SECURITY ALERT: {} risk auth failure - username: {}, reason: {}, ip: {}, correlationId: {}", 
                riskLevel, username, failureReason, ipAddress, correlationId);
        } else {
            log.warn("Auth failure processed - username: {}, reason: {}, ip: {}, risk: {}, correlationId: {}", 
                username, failureReason, ipAddress, riskLevel, correlationId);
        }
    }

    private FailureType determineFailureType(String failureReason) {
        return switch (failureReason) {
            case "INVALID_PASSWORD", "INVALID_USERNAME" -> FailureType.INVALID_CREDENTIALS;
            case "ACCOUNT_LOCKED" -> FailureType.ACCOUNT_LOCKED;
            case "SUSPICIOUS_LOCATION" -> FailureType.SUSPICIOUS_LOCATION;
            case "DEVICE_NOT_RECOGNIZED" -> FailureType.DEVICE_NOT_RECOGNIZED;
            case "MFA_FAILED" -> FailureType.MFA_FAILURE;
            case "RATE_LIMITED" -> FailureType.RATE_LIMITED;
            default -> FailureType.UNKNOWN;
        };
    }

    private void handleSuspiciousActivity(AuthFailure failure, String correlationId) {
        log.warn("SECURITY ALERT: Suspicious authentication activity - username: {}, ip: {}, correlationId: {}", 
            failure.getUsername(), failure.getIpAddress(), correlationId);
        
        fraudService.investigateSuspiciousAuth(failure, correlationId);
        
        kafkaTemplate.send("suspicious-auth-activity", Map.of(
            "username", failure.getUsername(),
            "ipAddress", failure.getIpAddress(),
            "failureType", failure.getFailureType().toString(),
            "riskLevel", failure.getRiskLevel().toString(),
            "reason", "SUSPICIOUS_PATTERN_DETECTED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleBruteForceAttempt(AuthFailure failure, String correlationId) {
        log.error("SECURITY ALERT: Brute force attempt detected - username: {}, ip: {}, correlationId: {}", 
            failure.getUsername(), failure.getIpAddress(), correlationId);
        
        threatService.blockBruteForceAttack(failure, correlationId);
        
        kafkaTemplate.send("brute-force-attack-alerts", Map.of(
            "username", failure.getUsername(),
            "ipAddress", failure.getIpAddress(),
            "failureType", failure.getFailureType().toString(),
            "attackType", "BRUTE_FORCE",
            "severity", "HIGH",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
        
        notificationService.sendSecurityAlert(
            "Brute Force Attack Detected",
            String.format("Brute force attack detected for username %s from IP %s", failure.getUsername(), failure.getIpAddress()),
            Map.of(
                "username", failure.getUsername(),
                "ipAddress", failure.getIpAddress(),
                "correlationId", correlationId
            )
        );
    }

    private void handleInvalidCredentials(AuthFailure failure, String correlationId) {
        log.info("Invalid credentials attempt - username: {}, ip: {}, correlationId: {}", 
            failure.getUsername(), failure.getIpAddress(), correlationId);
        
        kafkaTemplate.send("invalid-credentials-alerts", Map.of(
            "username", failure.getUsername(),
            "ipAddress", failure.getIpAddress(),
            "failureType", "INVALID_CREDENTIALS",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleAccountLocked(AuthFailure failure, String correlationId) {
        log.warn("Attempt to access locked account - username: {}, ip: {}, correlationId: {}", 
            failure.getUsername(), failure.getIpAddress(), correlationId);
        
        kafkaTemplate.send("locked-account-access-attempts", Map.of(
            "username", failure.getUsername(),
            "ipAddress", failure.getIpAddress(),
            "failureType", "ACCOUNT_LOCKED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleSuspiciousLocation(AuthFailure failure, String correlationId) {
        log.warn("SECURITY ALERT: Login from suspicious location - username: {}, ip: {}, correlationId: {}", 
            failure.getUsername(), failure.getIpAddress(), correlationId);
        
        kafkaTemplate.send("suspicious-location-alerts", Map.of(
            "username", failure.getUsername(),
            "ipAddress", failure.getIpAddress(),
            "failureType", "SUSPICIOUS_LOCATION",
            "severity", "MEDIUM",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleUnrecognizedDevice(AuthFailure failure, String correlationId) {
        log.warn("SECURITY ALERT: Login from unrecognized device - username: {}, device: {}, correlationId: {}", 
            failure.getUsername(), failure.getDeviceId(), correlationId);
        
        kafkaTemplate.send("unrecognized-device-alerts", Map.of(
            "username", failure.getUsername(),
            "ipAddress", failure.getIpAddress(),
            "deviceId", failure.getDeviceId(),
            "failureType", "DEVICE_NOT_RECOGNIZED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleMFAFailure(AuthFailure failure, String correlationId) {
        log.warn("MFA failure - username: {}, ip: {}, correlationId: {}", 
            failure.getUsername(), failure.getIpAddress(), correlationId);
        
        kafkaTemplate.send("mfa-failure-alerts", Map.of(
            "username", failure.getUsername(),
            "ipAddress", failure.getIpAddress(),
            "failureType", "MFA_FAILURE",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleRateLimited(AuthFailure failure, String correlationId) {
        log.info("Rate limited auth attempt - username: {}, ip: {}, correlationId: {}", 
            failure.getUsername(), failure.getIpAddress(), correlationId);
        
        kafkaTemplate.send("rate-limited-auth-alerts", Map.of(
            "username", failure.getUsername(),
            "ipAddress", failure.getIpAddress(),
            "failureType", "RATE_LIMITED",
            "correlationId", correlationId,
            "timestamp", Instant.now().toString()
        ));
    }

    private void handleGenericFailure(AuthFailure failure, String correlationId) {
        log.info("Generic auth failure - username: {}, reason: {}, correlationId: {}", 
            failure.getUsername(), failure.getFailureReason(), correlationId);
    }

    private boolean shouldLockAccount(AuthFailure failure) {
        return failureService.shouldLockAccount(failure.getUsername(), failure.getFailureType());
    }

    private void processAuthFailureFallback(
            String username,
            String ipAddress,
            String userAgent,
            String failureReason,
            String error,
            String sessionId,
            String deviceId,
            Map<String, Object> eventData,
            String correlationId,
            Exception e) {
        
        log.error("Circuit breaker activated for auth failure - username: {}, reason: {}, correlationId: {}, error: {}", 
            username, failureReason, correlationId, e.getMessage());
        
        Map<String, Object> fallbackEvent = new HashMap<>();
        fallbackEvent.put("username", username);
        fallbackEvent.put("ipAddress", ipAddress);
        fallbackEvent.put("userAgent", userAgent);
        fallbackEvent.put("failureReason", failureReason);
        fallbackEvent.put("error", error);
        fallbackEvent.put("sessionId", sessionId);
        fallbackEvent.put("deviceId", deviceId);
        fallbackEvent.put("correlationId", correlationId);
        fallbackEvent.put("error", e.getMessage());
        fallbackEvent.put("fallbackTimestamp", Instant.now().toString());
        
        kafkaTemplate.send("auth-failure-fallback-events", fallbackEvent);
    }

    @DltHandler
    public void handleDlt(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String exceptionMessage) {
        
        log.error("CRITICAL: Auth failure event sent to DLT - topic: {}, partition: {}, offset: {}, error: {}", 
            topic, partition, offset, exceptionMessage);
        
        try {
            Map<String, Object> dltEvent = Map.of(
                "originalTopic", topic,
                "partition", partition,
                "offset", offset,
                "message", message,
                "error", exceptionMessage,
                "timestamp", Instant.now().toString(),
                "dltReason", "MAX_RETRIES_EXCEEDED"
            );
            
            kafkaTemplate.send("auth-failure-processing-failures", dltEvent);
            
            notificationService.sendCriticalSecurityAlert(
                "Auth Failure Processing Failed",
                String.format("CRITICAL: Failed to process auth failure event after max retries. Error: %s", exceptionMessage),
                dltEvent
            );
            
        } catch (Exception e) {
            log.error("Failed to process DLT message: {}", e.getMessage(), e);
        }
    }

    private boolean isAlreadyProcessed(String eventId) {
        return processedEvents.containsKey(eventId);
    }

    private void markAsProcessed(String eventId) {
        processedEvents.put(eventId, String.valueOf(System.currentTimeMillis()));
        
        processedEvents.entrySet().removeIf(entry -> {
            long timestamp = Long.parseLong(entry.getValue());
            return System.currentTimeMillis() - timestamp > IDEMPOTENCY_TTL_MS;
        });
    }
}