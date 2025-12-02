package com.waqiti.frauddetection.kafka;

import com.waqiti.common.events.BehavioralBiometricEvent;
import com.waqiti.common.events.FraudAlertEvent;
import com.waqiti.frauddetection.domain.BiometricProfile;
import com.waqiti.frauddetection.domain.BiometricAnomaly;
import com.waqiti.frauddetection.domain.BiometricSignature;
import com.waqiti.frauddetection.repository.BiometricProfileRepository;
import com.waqiti.frauddetection.repository.BiometricAnomalyRepository;
import com.waqiti.frauddetection.service.BehavioralBiometricService;
import com.waqiti.frauddetection.service.BiometricMLService;
import com.waqiti.frauddetection.service.AnomalyDetectionService;
import com.waqiti.frauddetection.metrics.BiometricMetricsService;
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
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class BehavioralBiometricEventsConsumer {
    
    private final BiometricProfileRepository profileRepository;
    private final BiometricAnomalyRepository anomalyRepository;
    private final BehavioralBiometricService biometricService;
    private final BiometricMLService mlService;
    private final AnomalyDetectionService anomalyDetectionService;
    private final BiometricMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final SecurityContext securityContext;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final double ANOMALY_THRESHOLD = 0.75;
    private static final int MIN_SAMPLES_FOR_PROFILE = 10;
    
    @KafkaListener(
        topics = {"behavioral-biometric-events", "keystroke-dynamics-events", "mouse-dynamics-events"},
        groupId = "fraud-biometric-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        include = {Exception.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleBehavioralBiometricEvent(
            @Payload BehavioralBiometricEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("biometric-%s-p%d-o%d", 
            event.getUserId(), partition, offset);
        
        log.info("Processing behavioral biometric event: userId={}, eventType={}, biometricType={}, correlation={}",
            event.getUserId(), event.getEventType(), event.getBiometricType(), correlationId);
        
        try {
            validateBiometricEvent(event);
            
            switch (event.getEventType()) {
                case BIOMETRIC_SAMPLE_COLLECTED:
                    processBiometricSampleCollected(event, correlationId);
                    break;
                case BIOMETRIC_PROFILE_CREATED:
                    processBiometricProfileCreated(event, correlationId);
                    break;
                case BIOMETRIC_ANOMALY_DETECTED:
                    processBiometricAnomalyDetected(event, correlationId);
                    break;
                case KEYSTROKE_PATTERN_ANALYZED:
                    processKeystrokePatternAnalyzed(event, correlationId);
                    break;
                case MOUSE_MOVEMENT_ANALYZED:
                    processMouseMovementAnalyzed(event, correlationId);
                    break;
                case TOUCH_DYNAMICS_ANALYZED:
                    processTouchDynamicsAnalyzed(event, correlationId);
                    break;
                case BIOMETRIC_MISMATCH:
                    processBiometricMismatch(event, correlationId);
                    break;
                case BOT_BEHAVIOR_DETECTED:
                    processBotBehaviorDetected(event, correlationId);
                    break;
                default:
                    log.warn("Unknown biometric event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logSecurityEvent(
                "BIOMETRIC_EVENT_PROCESSED",
                event.getUserId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "biometricType", event.getBiometricType(),
                    "anomalyScore", event.getAnomalyScore(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process biometric event: userId={}, error={}",
                event.getUserId(), e.getMessage(), e);
            
            handleBiometricEventError(event, e, correlationId);
            acknowledgment.acknowledge();
        }
    }
    
    private void processBiometricSampleCollected(BehavioralBiometricEvent event, String correlationId) {
        log.info("Processing biometric sample: userId={}, type={}, sessionId={}", 
            event.getUserId(), event.getBiometricType(), event.getSessionId());
        
        BiometricProfile profile = getOrCreateProfile(event.getUserId(), event.getBiometricType());
        
        BiometricSignature signature = extractSignature(event);
        biometricService.addSample(profile, signature);
        
        if (profile.getSampleCount() >= MIN_SAMPLES_FOR_PROFILE && !profile.isProfileComplete()) {
            biometricService.buildProfile(profile);
            profile.setProfileComplete(true);
            profile.setProfileCompletedAt(LocalDateTime.now());
            
            log.info("Biometric profile completed: userId={}, type={}, sampleCount={}", 
                event.getUserId(), event.getBiometricType(), profile.getSampleCount());
        }
        
        if (profile.isProfileComplete()) {
            double anomalyScore = mlService.calculateAnomalyScore(signature, profile);
            
            if (anomalyScore > ANOMALY_THRESHOLD) {
                BehavioralBiometricEvent anomalyEvent = BehavioralBiometricEvent.builder()
                    .userId(event.getUserId())
                    .eventType("BIOMETRIC_ANOMALY_DETECTED")
                    .biometricType(event.getBiometricType())
                    .sessionId(event.getSessionId())
                    .anomalyScore(anomalyScore)
                    .timestamp(Instant.now())
                    .build();
                
                kafkaTemplate.send("behavioral-biometric-events", anomalyEvent);
            }
        }
        
        profileRepository.save(profile);
        metricsService.recordBiometricSample(event.getBiometricType(), profile.getSampleCount());
    }
    
    private void processBiometricProfileCreated(BehavioralBiometricEvent event, String correlationId) {
        log.info("Processing profile creation: userId={}, type={}", 
            event.getUserId(), event.getBiometricType());
        
        BiometricProfile profile = getOrCreateProfile(event.getUserId(), event.getBiometricType());
        profile.setProfileComplete(true);
        profile.setProfileCompletedAt(LocalDateTime.now());
        profileRepository.save(profile);
        
        metricsService.recordProfileCreated(event.getBiometricType());
    }
    
    private void processBiometricAnomalyDetected(BehavioralBiometricEvent event, String correlationId) {
        log.warn("Processing biometric anomaly: userId={}, type={}, anomalyScore={}", 
            event.getUserId(), event.getBiometricType(), event.getAnomalyScore());
        
        BiometricAnomaly anomaly = BiometricAnomaly.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .sessionId(event.getSessionId())
            .biometricType(event.getBiometricType())
            .anomalyScore(event.getAnomalyScore())
            .anomalyFeatures(event.getAnomalyFeatures())
            .expectedPattern(event.getExpectedPattern())
            .observedPattern(event.getObservedPattern())
            .deviationPercentage(calculateDeviation(event))
            .detectedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        anomalyRepository.save(anomaly);
        
        double riskScore = calculateRiskScore(anomaly);
        anomaly.setRiskScore(riskScore);
        
        String action = determineAction(riskScore);
        anomaly.setAction(action);
        anomalyRepository.save(anomaly);
        
        if (riskScore > 80.0) {
            FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
                .alertId(UUID.randomUUID())
                .userId(event.getUserId())
                .alertType("BIOMETRIC_ANOMALY")
                .severity("HIGH")
                .riskScore(riskScore)
                .riskFactors(List.of(
                    String.format("Biometric type: %s", event.getBiometricType()),
                    String.format("Anomaly score: %.2f", event.getAnomalyScore()),
                    String.format("Deviation: %.1f%%", anomaly.getDeviationPercentage())
                ))
                .timestamp(Instant.now())
                .correlationId(correlationId)
                .build();
            
            kafkaTemplate.send("fraud-alert-events", fraudAlert);
            
            if ("BLOCK".equals(action)) {
                biometricService.challengeUser(event.getUserId(), event.getSessionId());
            }
        }
        
        metricsService.recordBiometricAnomaly(
            event.getBiometricType(),
            event.getAnomalyScore(),
            action
        );
    }
    
    private void processKeystrokePatternAnalyzed(BehavioralBiometricEvent event, String correlationId) {
        log.info("Processing keystroke analysis: userId={}, avgSpeed={}, rhythm={}", 
            event.getUserId(), event.getAvgKeystrokeSpeed(), event.getTypingRhythmScore());
        
        BiometricProfile profile = getOrCreateProfile(event.getUserId(), "KEYSTROKE");
        
        Map<String, Double> keystrokeFeatures = Map.of(
            "avgSpeed", event.getAvgKeystrokeSpeed(),
            "rhythm", event.getTypingRhythmScore(),
            "dwellTime", event.getAvgDwellTime(),
            "flightTime", event.getAvgFlightTime(),
            "errorRate", event.getTypingErrorRate()
        );
        
        if (profile.isProfileComplete()) {
            double similarity = biometricService.compareKeystrokeDynamics(
                keystrokeFeatures,
                profile.getKeystrokeProfile()
            );
            
            if (similarity < 0.60) {
                BehavioralBiometricEvent mismatchEvent = BehavioralBiometricEvent.builder()
                    .userId(event.getUserId())
                    .eventType("BIOMETRIC_MISMATCH")
                    .biometricType("KEYSTROKE")
                    .sessionId(event.getSessionId())
                    .similarityScore(similarity)
                    .timestamp(Instant.now())
                    .build();
                
                kafkaTemplate.send("behavioral-biometric-events", mismatchEvent);
            }
        } else {
            profile.setKeystrokeProfile(keystrokeFeatures);
            profile.setSampleCount(profile.getSampleCount() + 1);
            profileRepository.save(profile);
        }
        
        metricsService.recordKeystrokeAnalysis(event.getAvgKeystrokeSpeed(), event.getTypingRhythmScore());
    }
    
    private void processMouseMovementAnalyzed(BehavioralBiometricEvent event, String correlationId) {
        log.info("Processing mouse movement analysis: userId={}, movementSpeed={}, clickPattern={}", 
            event.getUserId(), event.getMouseMovementSpeed(), event.getClickPatternScore());
        
        BiometricProfile profile = getOrCreateProfile(event.getUserId(), "MOUSE");
        
        Map<String, Double> mouseFeatures = Map.of(
            "movementSpeed", event.getMouseMovementSpeed(),
            "clickPattern", event.getClickPatternScore(),
            "scrollPattern", event.getScrollPatternScore(),
            "curvature", event.getMovementCurvature(),
            "acceleration", event.getMovementAcceleration()
        );
        
        if (profile.isProfileComplete()) {
            double similarity = biometricService.compareMouseDynamics(
                mouseFeatures,
                profile.getMouseProfile()
            );
            
            if (event.getMouseMovementSpeed() > 5000 || 
                event.getMovementCurvature() < 0.1) {
                
                BehavioralBiometricEvent botEvent = BehavioralBiometricEvent.builder()
                    .userId(event.getUserId())
                    .eventType("BOT_BEHAVIOR_DETECTED")
                    .biometricType("MOUSE")
                    .sessionId(event.getSessionId())
                    .botIndicators(List.of("Abnormal speed", "Linear movement"))
                    .timestamp(Instant.now())
                    .build();
                
                kafkaTemplate.send("behavioral-biometric-events", botEvent);
            }
        } else {
            profile.setMouseProfile(mouseFeatures);
            profile.setSampleCount(profile.getSampleCount() + 1);
            profileRepository.save(profile);
        }
        
        metricsService.recordMouseAnalysis(event.getMouseMovementSpeed(), event.getClickPatternScore());
    }
    
    private void processTouchDynamicsAnalyzed(BehavioralBiometricEvent event, String correlationId) {
        log.info("Processing touch dynamics: userId={}, pressure={}, holdTime={}", 
            event.getUserId(), event.getAvgTouchPressure(), event.getAvgTouchHoldTime());
        
        BiometricProfile profile = getOrCreateProfile(event.getUserId(), "TOUCH");
        
        Map<String, Double> touchFeatures = Map.of(
            "pressure", event.getAvgTouchPressure(),
            "holdTime", event.getAvgTouchHoldTime(),
            "swipeVelocity", event.getAvgSwipeVelocity(),
            "tapAccuracy", event.getTapAccuracy(),
            "gestureComplexity", event.getGestureComplexity()
        );
        
        profile.setTouchProfile(touchFeatures);
        profile.setSampleCount(profile.getSampleCount() + 1);
        profileRepository.save(profile);
        
        metricsService.recordTouchAnalysis(event.getAvgTouchPressure(), event.getAvgTouchHoldTime());
    }
    
    private void processBiometricMismatch(BehavioralBiometricEvent event, String correlationId) {
        log.warn("Processing biometric mismatch: userId={}, type={}, similarity={}", 
            event.getUserId(), event.getBiometricType(), event.getSimilarityScore());
        
        double riskScore = 100.0 - (event.getSimilarityScore() * 100.0);
        
        FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getUserId())
            .alertType("BIOMETRIC_MISMATCH")
            .severity(riskScore > 70.0 ? "HIGH" : "MEDIUM")
            .riskScore(riskScore)
            .riskFactors(List.of(
                String.format("Biometric type: %s", event.getBiometricType()),
                String.format("Similarity: %.1f%%", event.getSimilarityScore() * 100),
                String.format("Session: %s", event.getSessionId())
            ))
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send("fraud-alert-events", fraudAlert);
        
        if (riskScore > 85.0) {
            biometricService.challengeUser(event.getUserId(), event.getSessionId());
        }
        
        metricsService.recordBiometricMismatch(event.getBiometricType(), event.getSimilarityScore());
    }
    
    private void processBotBehaviorDetected(BehavioralBiometricEvent event, String correlationId) {
        log.error("Processing bot behavior detection: userId={}, sessionId={}, indicators={}", 
            event.getUserId(), event.getSessionId(), event.getBotIndicators());
        
        FraudAlertEvent fraudAlert = FraudAlertEvent.builder()
            .alertId(UUID.randomUUID())
            .userId(event.getUserId())
            .alertType("BOT_BEHAVIOR_DETECTED")
            .severity("CRITICAL")
            .riskScore(95.0)
            .riskFactors(event.getBotIndicators())
            .timestamp(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send("fraud-alert-events", fraudAlert);
        
        biometricService.blockSession(event.getSessionId(), "Bot behavior detected");
        
        notificationService.sendSecurityAlert(
            "Bot Behavior Detected",
            String.format("Bot behavior detected for user %s. Session blocked.",
                event.getUserId()),
            NotificationService.Priority.CRITICAL
        );
        
        metricsService.recordBotBehaviorDetected(event.getBotIndicators().size());
    }
    
    private BiometricProfile getOrCreateProfile(String userId, String biometricType) {
        return profileRepository.findByUserIdAndBiometricType(userId, biometricType)
            .orElse(BiometricProfile.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .biometricType(biometricType)
                .sampleCount(0)
                .profileComplete(false)
                .createdAt(LocalDateTime.now())
                .build());
    }
    
    private BiometricSignature extractSignature(BehavioralBiometricEvent event) {
        return BiometricSignature.builder()
            .biometricType(event.getBiometricType())
            .features(event.getBiometricFeatures())
            .timestamp(Instant.now())
            .build();
    }
    
    private double calculateDeviation(BehavioralBiometricEvent event) {
        if (event.getExpectedPattern() == null || event.getObservedPattern() == null) {
            return 0.0;
        }
        
        return Math.abs(event.getObservedPattern() - event.getExpectedPattern()) / 
               event.getExpectedPattern() * 100.0;
    }
    
    private double calculateRiskScore(BiometricAnomaly anomaly) {
        double baseScore = anomaly.getAnomalyScore() * 100.0;
        double deviationFactor = Math.min(anomaly.getDeviationPercentage() / 100.0, 1.0);
        
        return Math.min(baseScore + (deviationFactor * 20.0), 100.0);
    }
    
    private String determineAction(double riskScore) {
        if (riskScore >= 90.0) return "BLOCK";
        if (riskScore >= 75.0) return "CHALLENGE";
        if (riskScore >= 60.0) return "STEP_UP_AUTH";
        return "MONITOR";
    }
    
    private void validateBiometricEvent(BehavioralBiometricEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (event.getEventType() == null || event.getEventType().trim().isEmpty()) {
            throw new IllegalArgumentException("Event type is required");
        }
        
        if (event.getBiometricType() == null || event.getBiometricType().trim().isEmpty()) {
            throw new IllegalArgumentException("Biometric type is required");
        }
        
        if (event.getTimestamp() == null) {
            throw new IllegalArgumentException("Timestamp is required");
        }
    }
    
    private void handleBiometricEventError(BehavioralBiometricEvent event, Exception error, 
            String correlationId) {
        
        Map<String, Object> dlqPayload = Map.of(
            "originalEvent", event,
            "error", error.getMessage(),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        );
        
        kafkaTemplate.send("behavioral-biometric-events-dlq", dlqPayload);
        
        notificationService.sendOperationalAlert(
            "Biometric Event Processing Failed",
            String.format("Failed to process biometric event for user %s: %s",
                event.getUserId(), error.getMessage()),
            NotificationService.Priority.MEDIUM
        );
        
        metricsService.incrementBiometricEventError(event.getEventType());
    }
}