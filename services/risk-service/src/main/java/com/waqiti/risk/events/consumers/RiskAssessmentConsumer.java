package com.waqiti.risk.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.risk.RiskAssessmentEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.risk.domain.RiskAssessment;
import com.waqiti.risk.domain.RiskScore;
import com.waqiti.risk.domain.RiskLevel;
import com.waqiti.risk.domain.RiskFactor;
import com.waqiti.risk.domain.RiskDecision;
import com.waqiti.risk.repository.RiskAssessmentRepository;
import com.waqiti.risk.repository.RiskProfileRepository;
import com.waqiti.risk.service.RiskScoringService;
import com.waqiti.risk.service.MachineLearningService;
import com.waqiti.risk.service.BehaviorAnalysisService;
import com.waqiti.risk.service.VelocityCheckService;
import com.waqiti.risk.service.RiskRuleEngine;
import com.waqiti.risk.service.RiskMitigationService;
import com.waqiti.risk.service.RiskNotificationService;
import com.waqiti.common.exceptions.RiskAssessmentException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade consumer for risk assessment events.
 * Handles comprehensive transaction risk evaluation including:
 * - Real-time fraud scoring
 * - Machine learning risk models
 * - Behavioral pattern analysis
 * - Velocity and limit checks
 * - Geographic risk assessment
 * - Device fingerprinting
 * - Dynamic risk-based authentication
 * 
 * Critical for fraud prevention and transaction security.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RiskAssessmentConsumer {

    private final RiskAssessmentRepository assessmentRepository;
    private final RiskProfileRepository profileRepository;
    private final RiskScoringService scoringService;
    private final MachineLearningService mlService;
    private final BehaviorAnalysisService behaviorService;
    private final VelocityCheckService velocityService;
    private final RiskRuleEngine ruleEngine;
    private final RiskMitigationService mitigationService;
    private final RiskNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final double HIGH_RISK_THRESHOLD = 70.0;
    private static final double MEDIUM_RISK_THRESHOLD = 40.0;
    private static final BigDecimal MAX_TRANSACTION_AMOUNT = new BigDecimal("50000");

    @KafkaListener(
        topics = "risk-assessment-requests",
        groupId = "risk-service-assessment-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 500, multiplier = 2.0),
        include = {RiskAssessmentException.class}
    )
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void handleRiskAssessment(
            @Payload RiskAssessmentEvent assessmentEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "assessment-priority", required = false) String priority,
            Acknowledgment acknowledgment) {

        String eventId = assessmentEvent.getEventId() != null ? 
            assessmentEvent.getEventId() : UUID.randomUUID().toString();

        long startTime = System.currentTimeMillis();

        try {
            log.info("Processing risk assessment: {} for transaction: {} amount: {} user: {}", 
                    eventId, assessmentEvent.getTransactionId(), 
                    assessmentEvent.getTransactionAmount(), assessmentEvent.getUserId());

            // Metrics tracking
            metricsService.incrementCounter("risk.assessment.processing.started",
                Map.of(
                    "transaction_type", assessmentEvent.getTransactionType(),
                    "channel", assessmentEvent.getChannel()
                ));

            // Idempotency check
            if (isAssessmentAlreadyProcessed(assessmentEvent.getTransactionId(), eventId)) {
                log.info("Risk assessment {} already processed for transaction {}", 
                        eventId, assessmentEvent.getTransactionId());
                acknowledgment.acknowledge();
                return;
            }

            // Create assessment record
            RiskAssessment assessment = createAssessmentRecord(assessmentEvent, eventId, correlationId);

            // Execute parallel risk checks
            List<CompletableFuture<RiskCheckResult>> riskChecks = 
                createRiskCheckTasks(assessment, assessmentEvent);

            // Wait for all checks to complete with timeout
            CompletableFuture<Void> allChecks = CompletableFuture.allOf(
                riskChecks.toArray(new CompletableFuture[0])
            );

            try {
                allChecks.get(20, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Risk assessment timed out after 20 seconds for entity: {}", assessmentEvent.getEntityId(), e);
                riskChecks.forEach(check -> check.cancel(true));
            } catch (Exception e) {
                log.error("Risk assessment failed for entity: {}", assessmentEvent.getEntityId(), e);
            }

            // Aggregate risk check results (already completed, safe to get immediately)
            List<RiskCheckResult> results = riskChecks.stream()
                .map(future -> {
                    try {
                        return future.get(1, java.util.concurrent.TimeUnit.SECONDS);
                    } catch (Exception e) {
                        log.error("Risk check failed: {}", e.getMessage());
                        return RiskCheckResult.failure(e.getMessage());
                    }
                })
                .collect(Collectors.toList());

            // Calculate composite risk score
            calculateCompositeRiskScore(assessment, results);

            // Apply risk rules
            applyRiskRules(assessment, assessmentEvent);

            // Make risk decision
            makeRiskDecision(assessment, assessmentEvent);

            // Apply mitigation measures if needed
            if (assessment.getRiskLevel() != RiskLevel.LOW) {
                applyMitigationMeasures(assessment, assessmentEvent);
            }

            // Update assessment status
            updateAssessmentStatus(assessment);

            // Save assessment
            RiskAssessment savedAssessment = assessmentRepository.save(assessment);

            // Update user risk profile
            updateUserRiskProfile(savedAssessment, assessmentEvent);

            // Send notifications
            sendRiskNotifications(savedAssessment, assessmentEvent);

            // Update metrics
            updateRiskMetrics(savedAssessment, assessmentEvent, startTime);

            // Create audit trail
            createRiskAuditLog(savedAssessment, assessmentEvent, correlationId);

            // Success metrics
            metricsService.incrementCounter("risk.assessment.processing.success",
                Map.of(
                    "risk_level", savedAssessment.getRiskLevel().toString(),
                    "decision", savedAssessment.getDecision().toString()
                ));

            log.info("Successfully processed risk assessment: {} with score: {} decision: {}", 
                    savedAssessment.getId(), savedAssessment.getRiskScore(), savedAssessment.getDecision());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing risk assessment event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("risk.assessment.processing.error");
            
            auditLogger.logCriticalAlert("RISK_ASSESSMENT_PROCESSING_ERROR",
                "Critical risk assessment failure - transaction at risk",
                Map.of(
                    "transactionId", assessmentEvent.getTransactionId(),
                    "userId", assessmentEvent.getUserId(),
                    "amount", assessmentEvent.getTransactionAmount().toString(),
                    "eventId", eventId,
                    "error", e.getMessage(),
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new RiskAssessmentException("Failed to process risk assessment: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "risk-assessment-realtime",
        groupId = "risk-service-realtime-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleRealtimeRiskAssessment(
            @Payload RiskAssessmentEvent assessmentEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.info("REALTIME RISK: Processing immediate assessment for transaction: {}", 
                    assessmentEvent.getTransactionId());

            // Fast risk assessment for real-time decisions
            RiskAssessment assessment = performQuickRiskAssessment(assessmentEvent, correlationId);

            // Make immediate decision
            if (assessment.getRiskScore() > HIGH_RISK_THRESHOLD) {
                // Block high-risk transaction immediately
                blockTransaction(assessmentEvent.getTransactionId(), assessment);
                notificationService.sendHighRiskAlert(assessment);
            }

            // Save assessment asynchronously
            CompletableFuture.runAsync(() -> assessmentRepository.save(assessment));

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process realtime risk assessment: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private boolean isAssessmentAlreadyProcessed(String transactionId, String eventId) {
        return assessmentRepository.existsByTransactionIdAndEventId(transactionId, eventId);
    }

    private RiskAssessment createAssessmentRecord(RiskAssessmentEvent event, String eventId, String correlationId) {
        return RiskAssessment.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .transactionId(event.getTransactionId())
            .userId(event.getUserId())
            .merchantId(event.getMerchantId())
            .transactionType(event.getTransactionType())
            .transactionAmount(event.getTransactionAmount())
            .currency(event.getCurrency())
            .channel(event.getChannel())
            .ipAddress(event.getIpAddress())
            .deviceId(event.getDeviceId())
            .location(event.getLocation())
            .sessionId(event.getSessionId())
            .correlationId(correlationId)
            .assessmentStartedAt(LocalDateTime.now())
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private List<CompletableFuture<RiskCheckResult>> createRiskCheckTasks(
            RiskAssessment assessment, RiskAssessmentEvent event) {
        
        List<CompletableFuture<RiskCheckResult>> tasks = new ArrayList<>();

        // ML fraud scoring
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performMLFraudScoring(assessment, event)));

        // Behavioral analysis
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performBehavioralAnalysis(assessment, event)));

        // Velocity checks
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performVelocityChecks(assessment, event)));

        // Geographic risk
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performGeographicRiskCheck(assessment, event)));

        // Device risk
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performDeviceRiskCheck(assessment, event)));

        // Transaction pattern analysis
        tasks.add(CompletableFuture.supplyAsync(() -> 
            performPatternAnalysis(assessment, event)));

        // Merchant risk
        if (event.getMerchantId() != null) {
            tasks.add(CompletableFuture.supplyAsync(() -> 
                performMerchantRiskCheck(assessment, event)));
        }

        return tasks;
    }

    private RiskCheckResult performMLFraudScoring(RiskAssessment assessment, RiskAssessmentEvent event) {
        try {
            log.debug("Performing ML fraud scoring for transaction: {}", assessment.getTransactionId());

            var mlScore = mlService.calculateFraudScore(
                event.getUserId(),
                event.getTransactionAmount(),
                event.getTransactionType(),
                event.getFeatures()
            );

            assessment.setMlFraudScore(mlScore.getScore());
            assessment.setMlModelVersion(mlScore.getModelVersion());
            assessment.setMlConfidence(mlScore.getConfidence());

            // Extract risk factors from ML model
            if (mlScore.getRiskFactors() != null) {
                for (String factor : mlScore.getRiskFactors()) {
                    assessment.addRiskFactor(RiskFactor.fromString(factor));
                }
            }

            return RiskCheckResult.success("ML_FRAUD", mlScore.getScore(), mlScore.getRiskFactors());

        } catch (Exception e) {
            log.error("ML fraud scoring failed: {}", e.getMessage());
            return RiskCheckResult.failure("ML_FRAUD", e.getMessage());
        }
    }

    private RiskCheckResult performBehavioralAnalysis(RiskAssessment assessment, RiskAssessmentEvent event) {
        try {
            log.debug("Performing behavioral analysis for user: {}", event.getUserId());

            var behaviorScore = behaviorService.analyzeBehavior(
                event.getUserId(),
                event.getTransactionType(),
                event.getTransactionAmount(),
                event.getTimestamp()
            );

            assessment.setBehaviorScore(behaviorScore.getAnomalyScore());
            assessment.setBehaviorDeviation(behaviorScore.getDeviationPercentage());

            // Check for unusual patterns
            if (behaviorScore.hasUnusualPattern()) {
                assessment.addRiskFactor(RiskFactor.UNUSUAL_BEHAVIOR);
            }

            // Check for session anomalies
            if (behaviorScore.hasSessionAnomaly()) {
                assessment.addRiskFactor(RiskFactor.SESSION_ANOMALY);
            }

            return RiskCheckResult.success("BEHAVIOR", behaviorScore.getRiskScore(), behaviorScore.getAnomalies());

        } catch (Exception e) {
            log.error("Behavioral analysis failed: {}", e.getMessage());
            return RiskCheckResult.failure("BEHAVIOR", e.getMessage());
        }
    }

    private RiskCheckResult performVelocityChecks(RiskAssessment assessment, RiskAssessmentEvent event) {
        try {
            log.debug("Performing velocity checks for user: {}", event.getUserId());

            var velocityResult = velocityService.checkVelocityLimits(
                event.getUserId(),
                event.getTransactionAmount(),
                event.getTransactionType()
            );

            assessment.setVelocityScore(velocityResult.getRiskScore());

            // Check various velocity limits
            if (velocityResult.isHourlyLimitExceeded()) {
                assessment.addRiskFactor(RiskFactor.HOURLY_LIMIT_EXCEEDED);
            }
            if (velocityResult.isDailyLimitExceeded()) {
                assessment.addRiskFactor(RiskFactor.DAILY_LIMIT_EXCEEDED);
            }
            if (velocityResult.isTransactionCountExceeded()) {
                assessment.addRiskFactor(RiskFactor.HIGH_TRANSACTION_FREQUENCY);
            }

            // Amount velocity
            BigDecimal recentTotal = velocityResult.getRecentTransactionTotal();
            if (recentTotal.compareTo(MAX_TRANSACTION_AMOUNT) > 0) {
                assessment.addRiskFactor(RiskFactor.HIGH_AMOUNT_VELOCITY);
            }

            return RiskCheckResult.success("VELOCITY", velocityResult.getRiskScore(), velocityResult.getViolations());

        } catch (Exception e) {
            log.error("Velocity check failed: {}", e.getMessage());
            return RiskCheckResult.failure("VELOCITY", e.getMessage());
        }
    }

    private RiskCheckResult performGeographicRiskCheck(RiskAssessment assessment, RiskAssessmentEvent event) {
        try {
            log.debug("Performing geographic risk check for IP: {}", event.getIpAddress());

            var geoRisk = scoringService.calculateGeographicRisk(
                event.getIpAddress(),
                event.getLocation(),
                event.getUserId()
            );

            assessment.setGeographicRiskScore(geoRisk.getScore());
            assessment.setCountryCode(geoRisk.getCountryCode());
            assessment.setCity(geoRisk.getCity());

            // High-risk country
            if (geoRisk.isHighRiskCountry()) {
                assessment.addRiskFactor(RiskFactor.HIGH_RISK_COUNTRY);
            }

            // Impossible travel
            if (geoRisk.hasImpossibleTravel()) {
                assessment.addRiskFactor(RiskFactor.IMPOSSIBLE_TRAVEL);
                assessment.setImpossibleTravelDetected(true);
            }

            // VPN/Proxy detection
            if (geoRisk.isVpnDetected()) {
                assessment.addRiskFactor(RiskFactor.VPN_DETECTED);
            }
            if (geoRisk.isProxyDetected()) {
                assessment.addRiskFactor(RiskFactor.PROXY_DETECTED);
            }

            return RiskCheckResult.success("GEOGRAPHIC", geoRisk.getScore(), geoRisk.getRiskIndicators());

        } catch (Exception e) {
            log.error("Geographic risk check failed: {}", e.getMessage());
            return RiskCheckResult.failure("GEOGRAPHIC", e.getMessage());
        }
    }

    private RiskCheckResult performDeviceRiskCheck(RiskAssessment assessment, RiskAssessmentEvent event) {
        try {
            log.debug("Performing device risk check for device: {}", event.getDeviceId());

            var deviceRisk = scoringService.calculateDeviceRisk(
                event.getDeviceId(),
                event.getDeviceFingerprint(),
                event.getUserId()
            );

            assessment.setDeviceRiskScore(deviceRisk.getScore());
            assessment.setDeviceTrustLevel(deviceRisk.getTrustLevel());

            // New device
            if (deviceRisk.isNewDevice()) {
                assessment.addRiskFactor(RiskFactor.NEW_DEVICE);
            }

            // Jailbroken/Rooted
            if (deviceRisk.isJailbroken()) {
                assessment.addRiskFactor(RiskFactor.JAILBROKEN_DEVICE);
            }

            // Device sharing
            if (deviceRisk.isSharedDevice()) {
                assessment.addRiskFactor(RiskFactor.SHARED_DEVICE);
            }

            // Emulator detection
            if (deviceRisk.isEmulator()) {
                assessment.addRiskFactor(RiskFactor.EMULATOR_DETECTED);
            }

            return RiskCheckResult.success("DEVICE", deviceRisk.getScore(), deviceRisk.getRiskIndicators());

        } catch (Exception e) {
            log.error("Device risk check failed: {}", e.getMessage());
            return RiskCheckResult.failure("DEVICE", e.getMessage());
        }
    }

    private RiskCheckResult performPatternAnalysis(RiskAssessment assessment, RiskAssessmentEvent event) {
        try {
            log.debug("Performing pattern analysis for transaction: {}", assessment.getTransactionId());

            var patternResult = behaviorService.analyzeTransactionPatterns(
                event.getUserId(),
                event.getTransactionType(),
                event.getMerchantId(),
                event.getTransactionAmount()
            );

            assessment.setPatternScore(patternResult.getScore());

            // Known fraud patterns
            if (patternResult.matchesFraudPattern()) {
                assessment.addRiskFactor(RiskFactor.FRAUD_PATTERN_MATCH);
            }

            // Unusual time pattern
            if (patternResult.hasUnusualTimePattern()) {
                assessment.addRiskFactor(RiskFactor.UNUSUAL_TIME_PATTERN);
            }

            // Amount pattern anomaly
            if (patternResult.hasAmountAnomaly()) {
                assessment.addRiskFactor(RiskFactor.AMOUNT_ANOMALY);
            }

            return RiskCheckResult.success("PATTERN", patternResult.getScore(), patternResult.getPatterns());

        } catch (Exception e) {
            log.error("Pattern analysis failed: {}", e.getMessage());
            return RiskCheckResult.failure("PATTERN", e.getMessage());
        }
    }

    private RiskCheckResult performMerchantRiskCheck(RiskAssessment assessment, RiskAssessmentEvent event) {
        try {
            log.debug("Performing merchant risk check for merchant: {}", event.getMerchantId());

            var merchantRisk = scoringService.calculateMerchantRisk(
                event.getMerchantId(),
                event.getMerchantCategory()
            );

            assessment.setMerchantRiskScore(merchantRisk.getScore());
            assessment.setMerchantCategory(event.getMerchantCategory());

            // High-risk merchant category
            if (merchantRisk.isHighRiskCategory()) {
                assessment.addRiskFactor(RiskFactor.HIGH_RISK_MERCHANT);
            }

            // New merchant
            if (merchantRisk.isNewMerchant()) {
                assessment.addRiskFactor(RiskFactor.NEW_MERCHANT);
            }

            // Merchant fraud history
            if (merchantRisk.hasFraudHistory()) {
                assessment.addRiskFactor(RiskFactor.MERCHANT_FRAUD_HISTORY);
            }

            return RiskCheckResult.success("MERCHANT", merchantRisk.getScore(), merchantRisk.getRiskIndicators());

        } catch (Exception e) {
            log.error("Merchant risk check failed: {}", e.getMessage());
            return RiskCheckResult.failure("MERCHANT", e.getMessage());
        }
    }

    private void calculateCompositeRiskScore(RiskAssessment assessment, List<RiskCheckResult> results) {
        double totalScore = 0.0;
        double totalWeight = 0.0;

        // Weight configuration
        Map<String, Double> weights = Map.of(
            "ML_FRAUD", 0.30,
            "BEHAVIOR", 0.20,
            "VELOCITY", 0.15,
            "GEOGRAPHIC", 0.15,
            "DEVICE", 0.10,
            "PATTERN", 0.05,
            "MERCHANT", 0.05
        );

        for (RiskCheckResult result : results) {
            if (result.isSuccess() && result.getScore() != null) {
                double weight = weights.getOrDefault(result.getCheckType(), 0.05);
                totalScore += result.getScore() * weight;
                totalWeight += weight;
            }
        }

        // Calculate weighted average
        double compositeScore = totalWeight > 0 ? totalScore / totalWeight : 50.0;

        // Apply risk factor multipliers
        for (RiskFactor factor : assessment.getRiskFactors()) {
            compositeScore *= factor.getMultiplier();
        }

        // Normalize to 0-100 scale
        compositeScore = Math.min(100.0, Math.max(0.0, compositeScore));

        assessment.setRiskScore(compositeScore);
        assessment.setRiskLevel(determineRiskLevel(compositeScore));
    }

    private void applyRiskRules(RiskAssessment assessment, RiskAssessmentEvent event) {
        try {
            var ruleResults = ruleEngine.evaluateRules(assessment, event);

            for (var ruleResult : ruleResults) {
                if (ruleResult.isTriggered()) {
                    assessment.addTriggeredRule(ruleResult.getRuleName());
                    
                    // Apply rule actions
                    if (ruleResult.getAction() == RuleAction.BLOCK) {
                        assessment.setDecision(RiskDecision.BLOCK);
                    } else if (ruleResult.getAction() == RuleAction.CHALLENGE && 
                              assessment.getDecision() != RiskDecision.BLOCK) {
                        assessment.setDecision(RiskDecision.CHALLENGE);
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error applying risk rules: {}", e.getMessage());
        }
    }

    private void makeRiskDecision(RiskAssessment assessment, RiskAssessmentEvent event) {
        // Decision not already made by rules
        if (assessment.getDecision() == null) {
            if (assessment.getRiskScore() >= HIGH_RISK_THRESHOLD) {
                assessment.setDecision(RiskDecision.BLOCK);
            } else if (assessment.getRiskScore() >= MEDIUM_RISK_THRESHOLD) {
                assessment.setDecision(RiskDecision.CHALLENGE);
            } else {
                assessment.setDecision(RiskDecision.ALLOW);
            }
        }

        assessment.setDecisionReason(generateDecisionReason(assessment));
        assessment.setDecisionMadeAt(LocalDateTime.now());

        // Set authentication requirements
        if (assessment.getDecision() == RiskDecision.CHALLENGE) {
            assessment.setRequiredAuthentication(determineAuthenticationMethod(assessment));
        }
    }

    private void applyMitigationMeasures(RiskAssessment assessment, RiskAssessmentEvent event) {
        try {
            List<String> mitigations = new ArrayList<>();

            if (assessment.getRiskLevel() == RiskLevel.CRITICAL) {
                // Block transaction
                mitigations.add("BLOCK_TRANSACTION");
                // Freeze account temporarily
                mitigations.add("TEMPORARY_ACCOUNT_FREEZE");
                // Require manual review
                mitigations.add("MANUAL_REVIEW_REQUIRED");
            } else if (assessment.getRiskLevel() == RiskLevel.HIGH) {
                // Step-up authentication
                mitigations.add("STEP_UP_AUTHENTICATION");
                // Limit transaction amount
                mitigations.add("REDUCE_TRANSACTION_LIMIT");
                // Send security alert
                mitigations.add("SEND_SECURITY_ALERT");
            } else if (assessment.getRiskLevel() == RiskLevel.MEDIUM) {
                // Additional verification
                mitigations.add("ADDITIONAL_VERIFICATION");
                // Monitor closely
                mitigations.add("ENHANCED_MONITORING");
            }

            assessment.setMitigationMeasures(mitigations);

            // Execute mitigations
            for (String mitigation : mitigations) {
                mitigationService.executeMitigation(mitigation, assessment);
            }

        } catch (Exception e) {
            log.error("Error applying mitigation measures: {}", e.getMessage());
        }
    }

    private void updateAssessmentStatus(RiskAssessment assessment) {
        assessment.setAssessmentCompletedAt(LocalDateTime.now());
        assessment.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(assessment.getAssessmentStartedAt(), LocalDateTime.now())
        );
        assessment.setUpdatedAt(LocalDateTime.now());
    }

    private void updateUserRiskProfile(RiskAssessment assessment, RiskAssessmentEvent event) {
        try {
            var profile = profileRepository.findByUserId(event.getUserId())
                .orElseGet(() -> createNewRiskProfile(event.getUserId()));

            // Update profile metrics
            profile.updateWithAssessment(assessment);
            profile.setLastAssessmentId(assessment.getId());
            profile.setLastAssessmentAt(LocalDateTime.now());

            profileRepository.save(profile);

        } catch (Exception e) {
            log.error("Error updating risk profile: {}", e.getMessage());
        }
    }

    private void sendRiskNotifications(RiskAssessment assessment, RiskAssessmentEvent event) {
        try {
            // High risk alert
            if (assessment.getRiskLevel() == RiskLevel.HIGH || assessment.getRiskLevel() == RiskLevel.CRITICAL) {
                notificationService.sendHighRiskTransactionAlert(assessment);
            }

            // Blocked transaction notification
            if (assessment.getDecision() == RiskDecision.BLOCK) {
                notificationService.sendTransactionBlockedNotification(assessment);
            }

            // Authentication challenge notification
            if (assessment.getDecision() == RiskDecision.CHALLENGE) {
                notificationService.sendAuthenticationChallengeNotification(assessment);
            }

        } catch (Exception e) {
            log.error("Failed to send risk notifications: {}", e.getMessage());
        }
    }

    private void updateRiskMetrics(RiskAssessment assessment, RiskAssessmentEvent event, long startTime) {
        try {
            // Risk level distribution
            metricsService.incrementCounter("risk.assessment.level",
                Map.of("level", assessment.getRiskLevel().toString()));

            // Decision distribution
            metricsService.incrementCounter("risk.assessment.decision",
                Map.of("decision", assessment.getDecision().toString()));

            // Risk score distribution
            metricsService.recordGauge("risk.assessment.score", assessment.getRiskScore(),
                Map.of("transaction_type", event.getTransactionType()));

            // Processing time
            long processingTime = System.currentTimeMillis() - startTime;
            metricsService.recordTimer("risk.assessment.processing_time", processingTime,
                Map.of("risk_level", assessment.getRiskLevel().toString()));

        } catch (Exception e) {
            log.error("Failed to update risk metrics: {}", e.getMessage());
        }
    }

    private void createRiskAuditLog(RiskAssessment assessment, RiskAssessmentEvent event, String correlationId) {
        auditLogger.logRiskEvent(
            "RISK_ASSESSMENT_COMPLETED",
            assessment.getUserId(),
            assessment.getId(),
            assessment.getTransactionId(),
            assessment.getRiskScore(),
            "risk_processor",
            assessment.getDecision() != RiskDecision.BLOCK,
            Map.of(
                "assessmentId", assessment.getId(),
                "transactionId", assessment.getTransactionId(),
                "riskScore", String.valueOf(assessment.getRiskScore()),
                "riskLevel", assessment.getRiskLevel().toString(),
                "decision", assessment.getDecision().toString(),
                "riskFactors", assessment.getRiskFactors().stream()
                    .map(RiskFactor::toString)
                    .collect(Collectors.joining(",")),
                "mlScore", String.valueOf(assessment.getMlFraudScore()),
                "processingTimeMs", String.valueOf(assessment.getProcessingTimeMs()),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private RiskAssessment performQuickRiskAssessment(RiskAssessmentEvent event, String correlationId) {
        RiskAssessment assessment = createAssessmentRecord(event, UUID.randomUUID().toString(), correlationId);
        
        // Quick ML scoring only
        double mlScore = mlService.getQuickScore(event.getUserId(), event.getTransactionAmount());
        assessment.setMlFraudScore(mlScore);
        assessment.setRiskScore(mlScore);
        assessment.setRiskLevel(determineRiskLevel(mlScore));
        
        // Quick decision
        if (mlScore >= HIGH_RISK_THRESHOLD) {
            assessment.setDecision(RiskDecision.BLOCK);
        } else if (mlScore >= MEDIUM_RISK_THRESHOLD) {
            assessment.setDecision(RiskDecision.CHALLENGE);
        } else {
            assessment.setDecision(RiskDecision.ALLOW);
        }
        
        assessment.setAssessmentCompletedAt(LocalDateTime.now());
        return assessment;
    }

    private void blockTransaction(String transactionId, RiskAssessment assessment) {
        log.warn("BLOCKING high-risk transaction: {} with score: {}", transactionId, assessment.getRiskScore());
        // Call transaction service to block
    }

    private RiskLevel determineRiskLevel(double riskScore) {
        if (riskScore >= 80) return RiskLevel.CRITICAL;
        if (riskScore >= HIGH_RISK_THRESHOLD) return RiskLevel.HIGH;
        if (riskScore >= MEDIUM_RISK_THRESHOLD) return RiskLevel.MEDIUM;
        if (riskScore >= 20) return RiskLevel.LOW;
        return RiskLevel.MINIMAL;
    }

    private String generateDecisionReason(RiskAssessment assessment) {
        if (assessment.getDecision() == RiskDecision.BLOCK) {
            return "Transaction blocked due to high risk score: " + assessment.getRiskScore();
        } else if (assessment.getDecision() == RiskDecision.CHALLENGE) {
            return "Additional authentication required due to elevated risk";
        } else {
            return "Transaction approved with risk score: " + assessment.getRiskScore();
        }
    }

    private String determineAuthenticationMethod(RiskAssessment assessment) {
        if (assessment.getRiskScore() >= 60) {
            return "BIOMETRIC";
        } else if (assessment.getRiskScore() >= 40) {
            return "TWO_FACTOR";
        } else {
            return "OTP";
        }
    }

    private RiskProfile createNewRiskProfile(String userId) {
        return RiskProfile.builder()
            .id(UUID.randomUUID().toString())
            .userId(userId)
            .averageRiskScore(0.0)
            .highRiskTransactions(0)
            .blockedTransactions(0)
            .totalAssessments(0)
            .createdAt(LocalDateTime.now())
            .build();
    }

    /**
     * Internal classes
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class RiskCheckResult {
        private String checkType;
        private boolean success;
        private Double score;
        private List<String> indicators;
        private String error;

        public static RiskCheckResult success(String type, double score, List<String> indicators) {
            return new RiskCheckResult(type, true, score, indicators, null);
        }

        public static RiskCheckResult failure(String type, String error) {
            return new RiskCheckResult(type, false, null, null, error);
        }

        public static RiskCheckResult failure(String error) {
            return new RiskCheckResult(null, false, null, null, error);
        }
    }

    private enum RuleAction {
        BLOCK, CHALLENGE, MONITOR, ALLOW
    }

    @lombok.Data
    @lombok.Builder
    private static class RiskProfile {
        private String id;
        private String userId;
        private double averageRiskScore;
        private int highRiskTransactions;
        private int blockedTransactions;
        private int totalAssessments;
        private String lastAssessmentId;
        private LocalDateTime lastAssessmentAt;
        private LocalDateTime createdAt;

        public void updateWithAssessment(RiskAssessment assessment) {
            totalAssessments++;
            averageRiskScore = ((averageRiskScore * (totalAssessments - 1)) + assessment.getRiskScore()) / totalAssessments;
            if (assessment.getRiskLevel() == RiskLevel.HIGH || assessment.getRiskLevel() == RiskLevel.CRITICAL) {
                highRiskTransactions++;
            }
            if (assessment.getDecision() == RiskDecision.BLOCK) {
                blockedTransactions++;
            }
        }
    }
}