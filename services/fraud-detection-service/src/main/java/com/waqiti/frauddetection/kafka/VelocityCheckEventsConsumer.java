package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.VelocityCheckEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.frauddetection.domain.VelocityRule;
import com.waqiti.frauddetection.domain.VelocityViolation;
import com.waqiti.frauddetection.domain.VelocityThreshold;
import com.waqiti.frauddetection.repository.VelocityRuleRepository;
import com.waqiti.frauddetection.repository.VelocityViolationRepository;
import com.waqiti.frauddetection.service.VelocityCheckService;
import com.waqiti.frauddetection.service.RateLimitService;
import com.waqiti.frauddetection.service.FraudScoringService;
import com.waqiti.frauddetection.metrics.VelocityMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.security.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class VelocityCheckEventsConsumer {
    
    private final VelocityRuleRepository velocityRuleRepository;
    private final VelocityViolationRepository violationRepository;
    private final VelocityCheckService velocityCheckService;
    private final RateLimitService rateLimitService;
    private final FraudScoringService fraudScoringService;
    private final VelocityMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final Map<String, VelocityThreshold> DEFAULT_THRESHOLDS = Map.of(
        "TRANSACTION_COUNT_PER_HOUR", new VelocityThreshold(10, "HOUR"),
        "TRANSACTION_COUNT_PER_DAY", new VelocityThreshold(50, "DAY"),
        "TRANSACTION_AMOUNT_PER_HOUR", new VelocityThreshold(5000, "HOUR"),
        "TRANSACTION_AMOUNT_PER_DAY", new VelocityThreshold(25000, "DAY"),
        "CARD_USAGE_PER_HOUR", new VelocityThreshold(5, "HOUR"),
        "UNIQUE_MERCHANTS_PER_HOUR", new VelocityThreshold(10, "HOUR"),
        "FAILED_ATTEMPTS_PER_HOUR", new VelocityThreshold(3, "HOUR")
    );
    
    @KafkaListener(
        topics = {"velocity-check-events", "rate-limit-events", "transaction-velocity-events"},
        groupId = "fraud-velocity-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "10"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 1500, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleVelocityCheckEvent(
            @Payload VelocityCheckEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("velocity-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.info("Processing velocity check event: userId={}, eventType={}, checkType={}, correlation={}",
            event.getUserId(), event.getEventType(), event.getCheckType(), correlationId);
        
        try {
            validateVelocityCheckEvent(event);
            
            switch (event.getEventType()) {
                case VELOCITY_CHECK_REQUESTED:
                    processVelocityCheckRequested(event, correlationId);
                    break;
                case VELOCITY_LIMIT_EXCEEDED:
                    processVelocityLimitExceeded(event, correlationId);
                    break;
                case RATE_LIMIT_TRIGGERED:
                    processRateLimitTriggered(event, correlationId);
                    break;
                case VELOCITY_THRESHOLD_UPDATED:
                    processVelocityThresholdUpdated(event, correlationId);
                    break;
                case SUSPICIOUS_VELOCITY_PATTERN:
                    processSuspiciousVelocityPattern(event, correlationId);
                    break;
                case VELOCITY_BLOCK_APPLIED:
                    processVelocityBlockApplied(event, correlationId);
                    break;
                case BURST_ACTIVITY_DETECTED:
                    processBurstActivityDetected(event, correlationId);
                    break;
                default:
                    log.warn("Unknown velocity check event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logSecurityEvent(
                "VELOCITY_CHECK_EVENT_PROCESSED",
                event.getUserId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "checkType", event.getCheckType(),
                    "threshold", event.getThreshold(),
                    "actualValue", event.getActualValue(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process velocity check event: userId={}, error={}",
                event.getUserId(), e.getMessage(), e);
            
            handleVelocityCheckEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    private void processVelocityCheckRequested(VelocityCheckEvent event, String correlationId) {
        log.info("Processing velocity check: userId={}, checkType={}, value={}", 
            event.getUserId(), event.getCheckType(), event.getActualValue());
        
        VelocityThreshold threshold = getThreshold(event.getCheckType(), event.getUserId());
        
        boolean violated = velocityCheckService.checkVelocity(
            event.getUserId(),
            event.getCheckType(),
            event.getActualValue(),
            threshold
        );
        
        if (violated) {
            log.warn("Velocity limit exceeded: userId={}, checkType={}, actual={}, threshold={}", 
                event.getUserId(), event.getCheckType(), 
                event.getActualValue(), threshold.getLimit());
            
            VelocityCheckEvent violationEvent = VelocityCheckEvent.builder()
                .userId(event.getUserId())
                .eventType("VELOCITY_LIMIT_EXCEEDED")
                .checkType(event.getCheckType())
                .threshold(threshold.getLimit())
                .actualValue(event.getActualValue())
                .timeWindow(threshold.getTimeWindow())
                .entityType(event.getEntityType())
                .entityId(event.getEntityId())
                .timestamp(Instant.now())
                .build();
            
            kafkaTemplate.send("velocity-check-events", violationEvent);
        }
        
        metricsService.recordVelocityCheck(
            event.getCheckType(),
            event.getActualValue(),
            threshold.getLimit(),
            violated
        );
    }
    
    private void processVelocityLimitExceeded(VelocityCheckEvent event, String correlationId) {
        log.warn("Processing velocity limit exceeded: userId={}, checkType={}, actual={}, threshold={}", 
            event.getUserId(), event.getCheckType(), 
            event.getActualValue(), event.getThreshold());
        
        VelocityViolation violation = VelocityViolation.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .checkType(event.getCheckType())
            .threshold(event.getThreshold())
            .actualValue(event.getActualValue())
            .exceedPercentage(calculateExceedPercentage(event.getActualValue(), event.getThreshold()))
            .timeWindow(event.getTimeWindow())
            .entityType(event.getEntityType())
            .entityId(event.getEntityId())
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        violationRepository.save(violation);
        
        double riskScore = fraudScoringService.calculateVelocityRiskScore(violation);
        
        String action = determineAction(riskScore, event.getCheckType());
        violation.setAction(action);
        violation.setRiskScore(riskScore);
        violationRepository.save(violation);
        
        if ("BLOCK".equals(action)) {
            velocityCheckService.applyVelocityBlock(
                event.getUserId(),
                event.getCheckType(),
                calculateBlockDuration(riskScore)
            );
        }
        
        FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getUserId())
            .alertType("VELOCITY_LIMIT_EXCEEDED")
            .severity(determineSeverity(riskScore))
            .riskScore(riskScore)
            .riskFactors(List.of(
                String.format("Velocity check: %s", event.getCheckType()),
                String.format("Exceeded threshold by %.1f%%", violation.getExceedPercentage()),
                String.format("Actual: %d, Threshold: %d", event.getActualValue(), event.getThreshold())
            ))
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send("fraud-alert-events", fraudAlert);
        
        if (riskScore > 80.0) {
            notificationService.sendSecurityAlert(
                "Critical Velocity Limit Exceeded",
                String.format("User %s exceeded %s limit: %d (threshold: %d)",
                    event.getUserId(), event.getCheckType(), 
                    event.getActualValue(), event.getThreshold()),
                NotificationService.Priority.HIGH
            );
        }
        
        metricsService.recordVelocityViolation(
            event.getCheckType(),
            violation.getExceedPercentage(),
            action
        );
    }
    
    private void processRateLimitTriggered(VelocityCheckEvent event, String correlationId) {
        log.warn("Processing rate limit trigger: userId={}, endpoint={}, count={}", 
            event.getUserId(), event.getEndpoint(), event.getActualValue());
        
        rateLimitService.recordRateLimitHit(
            event.getUserId(),
            event.getEndpoint(),
            event.getActualValue(),
            event.getTimeWindow()
        );
        
        int remainingAttempts = rateLimitService.getRemainingAttempts(
            event.getUserId(),
            event.getEndpoint(),
            event.getTimeWindow()
        );
        
        if (remainingAttempts == 0) {
            rateLimitService.applyTemporaryBlock(
                event.getUserId(),
                event.getEndpoint(),
                calculateRateLimitBlockDuration(event.getActualValue(), event.getThreshold())
            );
            
            FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
                .alertId(UUID.randomUUID())
                .userId(event.getUserId())
                .alertType("RATE_LIMIT_BLOCKED")
                .severity("MEDIUM")
                .riskScore(70.0)
                .riskFactors(List.of(
                    String.format("Rate limit exceeded: %s", event.getEndpoint()),
                    String.format("Request count: %d in %s", 
                        event.getActualValue(), event.getTimeWindow())
                ))
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .build();
            
            kafkaTemplate.send("fraud-alert-events", fraudAlert);
        }
        
        metricsService.recordRateLimitTriggered(
            event.getEndpoint(),
            event.getActualValue(),
            remainingAttempts
        );
    }
    
    private void processVelocityThresholdUpdated(VelocityCheckEvent event, String correlationId) {
        log.info("Processing threshold update: checkType={}, newThreshold={}", 
            event.getCheckType(), event.getNewThreshold());
        
        VelocityRule rule = velocityRuleRepository
            .findByCheckTypeAndUserId(event.getCheckType(), event.getUserId())
            .orElse(VelocityRule.builder()
                .id(UUID.randomUUID().toString())
                .checkType(event.getCheckType())
                .userId(event.getUserId())
                .build());
        
        rule.setThreshold(event.getNewThreshold());
        rule.setTimeWindow(event.getTimeWindow());
        rule.setUpdatedAt(LocalDateTime.now());
        rule.setUpdatedBy(event.getUpdatedBy());
        
        velocityRuleRepository.save(rule);
        
        metricsService.recordThresholdUpdated(event.getCheckType(), event.getNewThreshold());
    }
    
    private void processSuspiciousVelocityPattern(VelocityCheckEvent event, String correlationId) {
        log.warn("Processing suspicious velocity pattern: userId={}, pattern={}", 
            event.getUserId(), event.getPatternType());
        
        double riskScore = fraudScoringService.assessVelocityPattern(
            event.getUserId(),
            event.getPatternType(),
            event.getPatternDetails()
        );
        
        FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getUserId())
            .alertType("SUSPICIOUS_VELOCITY_PATTERN")
            .severity(determineSeverity(riskScore))
            .riskScore(riskScore)
            .riskFactors(List.of(
                String.format("Pattern type: %s", event.getPatternType()),
                String.format("Pattern details: %s", event.getPatternDetails())
            ))
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send("fraud-alert-events", fraudAlert);
        
        metricsService.recordSuspiciousPattern(event.getPatternType(), riskScore);
    }
    
    private void processVelocityBlockApplied(VelocityCheckEvent event, String correlationId) {
        log.warn("Processing velocity block: userId={}, checkType={}, duration={}min", 
            event.getUserId(), event.getCheckType(), event.getBlockDurationMinutes());
        
        velocityCheckService.applyVelocityBlock(
            event.getUserId(),
            event.getCheckType(),
            event.getBlockDurationMinutes()
        );
        
        notificationService.sendNotification(
            event.getUserId(),
            "Account Temporarily Restricted",
            String.format("Your account has been temporarily restricted due to unusual activity. " +
                "Access will be restored in %d minutes.", event.getBlockDurationMinutes()),
            correlationId
        );
        
        metricsService.recordVelocityBlockApplied(
            event.getCheckType(),
            event.getBlockDurationMinutes()
        );
    }
    
    private void processBurstActivityDetected(VelocityCheckEvent event, String correlationId) {
        log.warn("Processing burst activity: userId={}, activityType={}, count={}, duration={}sec", 
            event.getUserId(), event.getActivityType(), 
            event.getActualValue(), event.getBurstDurationSeconds());
        
        double burstRate = (double) event.getActualValue() / event.getBurstDurationSeconds();
        
        if (burstRate > 1.0) {
            FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
                .alertId(UUID.randomUUID())
                .userId(event.getUserId())
                .alertType("BURST_ACTIVITY_DETECTED")
                .severity("HIGH")
                .riskScore(85.0)
                .riskFactors(List.of(
                    String.format("Burst activity: %s", event.getActivityType()),
                    String.format("Rate: %.2f per second", burstRate),
                    String.format("Count: %d in %d seconds", 
                        event.getActualValue(), event.getBurstDurationSeconds())
                ))
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .build();
            
            kafkaTemplate.send("fraud-alert-events", fraudAlert);
            
            velocityCheckService.applyBurstProtection(
                event.getUserId(),
                event.getActivityType(),
                30
            );
        }
        
        metricsService.recordBurstActivity(
            event.getActivityType(),
            burstRate,
            event.getBurstDurationSeconds()
        );
    }
    
    private VelocityThreshold getThreshold(String checkType, String userId) {
        return velocityRuleRepository
            .findByCheckTypeAndUserId(checkType, userId)
            .map(rule -> new VelocityThreshold(rule.getThreshold(), rule.getTimeWindow()))
            .orElseGet(() -> DEFAULT_THRESHOLDS.getOrDefault(
                checkType, 
                new VelocityThreshold(100, "DAY")
            ));
    }
    
    private double calculateExceedPercentage(int actualValue, int threshold) {
        if (threshold == 0) return 100.0;
        return ((double) (actualValue - threshold) / threshold) * 100.0;
    }
    
    private String determineAction(double riskScore, String checkType) {
        if (riskScore >= 90.0) return "BLOCK";
        if (riskScore >= 75.0) return "REVIEW";
        if (riskScore >= 60.0) return "CHALLENGE";
        return "MONITOR";
    }
    
    private String determineSeverity(double riskScore) {
        if (riskScore >= 85.0) return "CRITICAL";
        if (riskScore >= 70.0) return "HIGH";
        if (riskScore >= 50.0) return "MEDIUM";
        return "LOW";
    }
    
    private int calculateBlockDuration(double riskScore) {
        if (riskScore >= 95.0) return 1440;
        if (riskScore >= 90.0) return 720;
        if (riskScore >= 80.0) return 360;
        if (riskScore >= 70.0) return 180;
        return 60;
    }
    
    private int calculateRateLimitBlockDuration(int actualValue, int threshold) {
        double exceedRatio = (double) actualValue / threshold;
        
        if (exceedRatio >= 5.0) return 60;
        if (exceedRatio >= 3.0) return 30;
        if (exceedRatio >= 2.0) return 15;
        return 10;
    }
    
    private void validateVelocityCheckEvent(VelocityCheckEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getCheckType() == null || event.getCheckType().trim().isEmpty()) {
            throw new IllegalArgumentException("Check type is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    private void handleVelocityCheckEventError(VelocityCheckEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("velocity-check-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Velocity Check Event Processing Failed",
            String.format("Failed to process velocity check event for user %s: %s",
                event.getUserId(), error.getMessage()),
            NotificationService.Priority.MEDIUM
        );
        
        metricsService.incrementVelocityCheckEventError(event.getEventType());
    }
}