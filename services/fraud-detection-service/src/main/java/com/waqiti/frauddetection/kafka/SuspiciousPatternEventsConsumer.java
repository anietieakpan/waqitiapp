package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.SuspiciousPatternEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.frauddetection.domain.PatternAnalysis;
import com.waqiti.frauddetection.domain.SuspiciousPattern;
import com.waqiti.frauddetection.domain.PatternType;
import com.waqiti.frauddetection.repository.PatternAnalysisRepository;
import com.waqiti.frauddetection.repository.SuspiciousPatternRepository;
import com.waqiti.frauddetection.service.PatternDetectionService;
import com.waqiti.frauddetection.service.MLPatternService;
import com.waqiti.frauddetection.service.FraudScoringService;
import com.waqiti.frauddetection.metrics.PatternMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
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
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class SuspiciousPatternEventsConsumer {
    
    private final PatternAnalysisRepository patternAnalysisRepository;
    private final SuspiciousPatternRepository suspiciousPatternRepository;
    private final PatternDetectionService patternDetectionService;
    private final MLPatternService mlPatternService;
    private final FraudScoringService fraudScoringService;
    private final PatternMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"suspicious-pattern-events", "pattern-detection-events", "anomaly-pattern-events"},
        groupId = "fraud-pattern-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleSuspiciousPatternEvent(
            @Payload SuspiciousPatternEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("pattern-%s-p%d-o%d", 
            event.getEntityId(), partition, offset);
        
        log.info("Processing suspicious pattern event: entityId={}, patternType={}, confidence={}, correlation={}",
            event.getEntityId(), event.getPatternType(), event.getConfidenceScore(), correlationId);
        
        try {
            switch (event.getEventType()) {
                case PATTERN_DETECTED:
                    processPatternDetected(event, correlationId);
                    break;
                case REPEATED_PATTERN:
                    processRepeatedPattern(event, correlationId);
                    break;
                case MULTI_ACCOUNT_PATTERN:
                    processMultiAccountPattern(event, correlationId);
                    break;
                case TIMING_PATTERN:
                    processTimingPattern(event, correlationId);
                    break;
                case AMOUNT_PATTERN:
                    processAmountPattern(event, correlationId);
                    break;
                case ACCOUNT_TAKEOVER_PATTERN:
                    processAccountTakeoverPattern(event, correlationId);
                    break;
                default:
                    log.warn("Unknown pattern event type: {}", event.getEventType());
                    break;
            }
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process pattern event: entityId={}, error={}",
                event.getEntityId(), e.getMessage(), e);
            handlePatternEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    private void processPatternDetected(SuspiciousPatternEvent event, String correlationId) {
        log.info("Processing pattern detection: type={}, confidence={}", 
            event.getPatternType(), event.getConfidenceScore());
        
        SuspiciousPattern pattern = SuspiciousPattern.builder()
            .id(UUID.randomUUID().toString())
            .entityId(event.getEntityId())
            .entityType(event.getEntityType())
            .patternType(event.getPatternType())
            .confidenceScore(event.getConfidenceScore())
            .occurrenceCount(1)
            .firstDetectedAt(LocalDateTime.now())
            .lastDetectedAt(LocalDateTime.now())
            .features(event.getPatternFeatures())
            .correlationId(correlationId)
            .build();
        
        suspiciousPatternRepository.save(pattern);
        
        double riskScore = fraudScoringService.scorePattern(pattern);
        
        if (riskScore > 70.0) {
            FraudAlertEvent alert = createFraudAlert(event, riskScore, correlationId);
            kafkaTemplate.send("fraud-alert-events", alert);
        }
        
        metricsService.recordPatternDetected(event.getPatternType(), event.getConfidenceScore());
    }
    
    private void processRepeatedPattern(SuspiciousPatternEvent event, String correlationId) {
        log.warn("Processing repeated pattern: entityId={}, type={}, count={}", 
            event.getEntityId(), event.getPatternType(), event.getRepeatCount());
        
        Optional<SuspiciousPattern> existing = suspiciousPatternRepository
            .findByEntityIdAndPatternType(event.getEntityId(), event.getPatternType());
        
        if (existing.isPresent()) {
            SuspiciousPattern pattern = existing.get();
            pattern.setOccurrenceCount(pattern.getOccurrenceCount() + 1);
            pattern.setLastDetectedAt(LocalDateTime.now());
            suspiciousPatternRepository.save(pattern);
            
            if (pattern.getOccurrenceCount() >= 3) {
                patternDetectionService.escalatePattern(pattern);
            }
        }
        
        metricsService.recordRepeatedPattern(event.getPatternType(), event.getRepeatCount());
    }
    
    private void processMultiAccountPattern(SuspiciousPatternEvent event, String correlationId) {
        log.warn("Processing multi-account pattern: accounts={}, pattern={}", 
            event.getRelatedAccounts().size(), event.getPatternType());
        
        FraudAlertEvent alert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getEntityId())
            .alertType("MULTI_ACCOUNT_PATTERN")
            .severity("HIGH")
            .riskScore(85.0)
            .riskFactors(List.of(
                String.format("Pattern across %d accounts", event.getRelatedAccounts().size()),
                String.format("Pattern type: %s", event.getPatternType())
            ))
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send("fraud-alert-events", alert);
        metricsService.recordMultiAccountPattern(event.getRelatedAccounts().size());
    }
    
    private void processTimingPattern(SuspiciousPatternEvent event, String correlationId) {
        log.warn("Processing timing pattern: entityId={}, pattern={}", 
            event.getEntityId(), event.getTimingDescription());
        
        metricsService.recordTimingPattern(event.getPatternType());
    }
    
    private void processAmountPattern(SuspiciousPatternEvent event, String correlationId) {
        log.warn("Processing amount pattern: entityId={}, amounts={}", 
            event.getEntityId(), event.getAmountPattern());
        
        if (event.getAmountPattern() != null && event.getAmountPattern().contains("JUST_BELOW_THRESHOLD")) {
            FraudAlertEvent alert = FraudAlertEvent.builder()
                .alertId(UUID.randomUUID())
                .userId(event.getEntityId())
                .alertType("STRUCTURING_PATTERN")
                .severity("HIGH")
                .riskScore(90.0)
                .riskFactors(List.of("Possible structuring/smurfing detected"))
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .build();
            
            kafkaTemplate.send("fraud-alert-events", alert);
        }
        
        metricsService.recordAmountPattern(event.getPatternType());
    }
    
    private void processAccountTakeoverPattern(SuspiciousPatternEvent event, String correlationId) {
        log.error("Processing account takeover pattern: entityId={}, indicators={}", 
            event.getEntityId(), event.getTakeoverIndicators());
        
        FraudAlertEvent alert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getEntityId())
            .alertType("ACCOUNT_TAKEOVER_PATTERN")
            .severity("CRITICAL")
            .riskScore(95.0)
            .riskFactors(event.getTakeoverIndicators())
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send("fraud-alert-events", alert);
        
        notificationService.sendSecurityAlert(
            "Account Takeover Pattern Detected",
            String.format("Account takeover pattern detected for entity %s", event.getEntityId()),
            NotificationService.Priority.CRITICAL
        );
        
        metricsService.recordAccountTakeoverPattern();
    }
    
    private FraudAlertEvent createFraudAlert(SuspiciousPatternEvent event, double riskScore, 
            String correlationId) {
        return FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getEntityId())
            .alertType("SUSPICIOUS_PATTERN")
            .severity(riskScore > 85.0 ? "HIGH" : "MEDIUM")
            .riskScore(riskScore)
            .riskFactors(List.of(
                String.format("Pattern: %s", event.getPatternType()),
                String.format("Confidence: %.2f", event.getConfidenceScore())
            ))
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
    }
    
    private void handlePatternEventError(SuspiciousPatternEvent event, Exception error, 
            String correlationId) {
        kafkaTemplate.send("suspicious-pattern-events-dlq", Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.incrementPatternEventError(event.getEventType());
    }
}