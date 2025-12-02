package com.waqiti.frauddetection.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.model.*;
import com.waqiti.frauddetection.repository.VelocityViolationRepository;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.math.MoneyMath;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for velocity monitoring
 * Handles transaction velocity violations, pattern detection, and automated controls
 * 
 * Critical for: Fraud prevention, AML compliance, risk management
 * SLA: Must process velocity violations within 5 seconds for real-time blocking
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class VelocityMonitoringConsumer {

    private final VelocityViolationRepository violationRepository;
    private final VelocityAnalysisService analysisService;
    private final VelocityControlService controlService;
    private final RiskScoringService riskScoringService;
    private final FraudDetectionService fraudDetectionService;
    private final NotificationService notificationService;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ScheduledExecutorService scheduledExecutor;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 5000; // 5 seconds
    private static final Set<String> CRITICAL_VELOCITY_TYPES = Set.of(
        "AMOUNT_BURST", "COUNT_BURST", "GEOGRAPHIC_VELOCITY", "CROSS_CHANNEL_VELOCITY"
    );
    
    @KafkaListener(
        topics = {"velocity-monitoring"},
        groupId = "velocity-monitoring-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "velocity-monitoring-processor", fallbackMethod = "handleVelocityMonitoringFailure")
    @Retry(name = "velocity-monitoring-processor")
    public void processVelocityViolation(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing velocity violation: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            VelocityViolation violation = extractVelocityViolation(payload);
            
            // Validate violation
            validateViolation(violation);
            
            // Check for duplicate violation
            if (isDuplicateViolation(violation)) {
                log.warn("Duplicate velocity violation detected: {}, skipping", violation.getViolationId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Enrich violation with additional context
            VelocityViolation enrichedViolation = enrichViolation(violation);
            
            // Analyze violation severity and pattern
            VelocityAnalysis analysis = analyzeViolation(enrichedViolation);
            
            // Process violation based on analysis
            VelocityProcessingResult result = processViolation(enrichedViolation, analysis);
            
            // Apply immediate controls if required
            if (analysis.requiresImmediateAction()) {
                applyImmediateControls(enrichedViolation, analysis, result);
            }
            
            // Perform pattern detection
            if (analysis.enablesPatternDetection()) {
                performPatternDetection(enrichedViolation, result);
            }
            
            // Update velocity profiles
            updateVelocityProfiles(enrichedViolation, analysis);
            
            // Trigger workflows
            if (analysis.hasAutomatedWorkflows()) {
                triggerAutomatedWorkflows(enrichedViolation, analysis);
            }
            
            // Send notifications
            sendVelocityNotifications(enrichedViolation, analysis, result);
            
            // Update monitoring systems
            updateMonitoringSystems(enrichedViolation, result);
            
            // Audit violation processing
            auditVelocityProcessing(enrichedViolation, result, event);
            
            // Record metrics
            recordVelocityMetrics(enrichedViolation, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed velocity violation: {} type: {} risk: {} in {}ms", 
                    enrichedViolation.getViolationId(), enrichedViolation.getViolationType(), 
                    analysis.getRiskLevel(), System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for velocity violation: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalVelocityException e) {
            log.error("Critical velocity processing failed: {}", eventId, e);
            handleCriticalVelocityError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process velocity violation: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private VelocityViolation extractVelocityViolation(Map<String, Object> payload) {
        return VelocityViolation.builder()
            .violationId(extractString(payload, "violationId", UUID.randomUUID().toString()))
            .violationType(VelocityType.fromString(extractString(payload, "violationType", null)))
            .entityId(extractString(payload, "entityId", null))
            .entityType(extractString(payload, "entityType", null))
            .customerId(extractString(payload, "customerId", null))
            .accountId(extractString(payload, "accountId", null))
            .merchantId(extractString(payload, "merchantId", null))
            .ruleId(extractString(payload, "ruleId", null))
            .ruleName(extractString(payload, "ruleName", null))
            .timeWindow(extractString(payload, "timeWindow", null))
            .limitValue(extractBigDecimal(payload, "limitValue"))
            .actualValue(extractBigDecimal(payload, "actualValue"))
            .exceedanceAmount(extractBigDecimal(payload, "exceedanceAmount"))
            .exceedancePercentage(extractDouble(payload, "exceedancePercentage", 0.0))
            .transactionCount(extractInteger(payload, "transactionCount", 0))
            .totalAmount(extractBigDecimal(payload, "totalAmount"))
            .currency(extractString(payload, "currency", "USD"))
            .country(extractString(payload, "country", null))
            .channel(extractString(payload, "channel", null))
            .severity(VelocitySeverity.fromString(extractString(payload, "severity", "MEDIUM")))
            .status(VelocityStatus.ACTIVE)
            .detectionTime(extractInstant(payload, "detectionTime"))
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .triggerData(extractMap(payload, "triggerData"))
            .metadata(extractMap(payload, "metadata"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateViolation(VelocityViolation violation) {
        if (violation.getViolationType() == null) {
            throw new ValidationException("Violation type is required");
        }
        
        if (violation.getEntityId() == null || violation.getEntityId().isEmpty()) {
            throw new ValidationException("Entity ID is required");
        }
        
        if (violation.getEntityType() == null || violation.getEntityType().isEmpty()) {
            throw new ValidationException("Entity type is required");
        }
        
        if (violation.getRuleId() == null || violation.getRuleId().isEmpty()) {
            throw new ValidationException("Rule ID is required");
        }
        
        if (violation.getActualValue() == null || violation.getActualValue().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("Valid actual value is required");
        }
        
        if (violation.getLimitValue() == null || violation.getLimitValue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Valid limit value is required");
        }
        
        // Validate critical velocity types
        if (CRITICAL_VELOCITY_TYPES.contains(violation.getViolationType().toString()) && 
            violation.getSeverity().ordinal() < VelocitySeverity.HIGH.ordinal()) {
            log.warn("Critical velocity type {} should have HIGH or CRITICAL severity", 
                    violation.getViolationType());
        }
        
        // Validate exceedance calculation
        if (violation.getExceedanceAmount() == null) {
            BigDecimal exceedance = violation.getActualValue().subtract(violation.getLimitValue());
            violation.setExceedanceAmount(exceedance);
        }
        
        if (violation.getExceedancePercentage() == 0.0) {
            double percentage = (double) MoneyMath.toMLFeature(
                violation.getExceedanceAmount()
                    .divide(violation.getLimitValue(), 4, RoundingMode.HALF_UP)
                    .multiply(new BigDecimal("100"))
            );
            violation.setExceedancePercentage(percentage);
        }
    }

    private boolean isDuplicateViolation(VelocityViolation violation) {
        // Check for exact duplicate by violation ID
        if (violationRepository.existsByViolationIdAndCreatedAtAfter(
                violation.getViolationId(), 
                Instant.now().minus(10, ChronoUnit.MINUTES))) {
            return true;
        }
        
        // Check for similar violation (same entity, rule within time window)
        return violationRepository.existsSimilarViolation(
            violation.getEntityId(),
            violation.getRuleId(),
            Instant.now().minus(15, ChronoUnit.MINUTES)
        );
    }

    private VelocityViolation enrichViolation(VelocityViolation violation) {
        // Enrich with entity information
        EntityProfile entityProfile = entityService.getEntityProfile(
            violation.getEntityId(),
            violation.getEntityType()
        );
        
        if (entityProfile != null) {
            violation.setEntityName(entityProfile.getName());
            violation.setEntityRiskLevel(entityProfile.getRiskLevel());
            violation.setEntityCreationDate(entityProfile.getCreationDate());
        }
        
        // Enrich with historical velocity data
        VelocityHistory history = violationRepository.getVelocityHistory(
            violation.getEntityId(),
            violation.getViolationType(),
            Instant.now().minus(30, ChronoUnit.DAYS)
        );
        
        violation.setHistoricalViolationCount(history.getTotalViolations());
        violation.setRecentViolationCount(history.getRecentViolations(7)); // Last 7 days
        violation.setPreviousViolationDate(history.getLastViolationDate());
        
        // Enrich with geographic data
        if (violation.getCountry() != null) {
            CountryRiskData countryRisk = riskDataService.getCountryRiskData(violation.getCountry());
            violation.setCountryRiskLevel(countryRisk.getRiskLevel());
            violation.setHighRiskCountry(countryRisk.isHighRisk());
        }
        
        // Enrich with current velocity metrics
        VelocityMetrics currentMetrics = analysisService.getCurrentVelocityMetrics(
            violation.getEntityId(),
            violation.getTimeWindow()
        );
        violation.setCurrentDailyVolume(currentMetrics.getDailyVolume());
        violation.setCurrentHourlyVolume(currentMetrics.getHourlyVolume());
        violation.setVelocityTrend(currentMetrics.getTrend());
        
        return violation;
    }

    private VelocityAnalysis analyzeViolation(VelocityViolation violation) {
        VelocityAnalysis analysis = new VelocityAnalysis();
        analysis.setViolationId(violation.getViolationId());
        
        // Determine risk level
        String riskLevel = determineRiskLevel(violation);
        analysis.setRiskLevel(riskLevel);
        
        // Calculate composite risk score
        int riskScore = calculateRiskScore(violation);
        analysis.setRiskScore(riskScore);
        
        // Determine required actions
        analysis.setRequiresImmediateAction(shouldTakeImmediateAction(violation, riskLevel));
        analysis.setEnablesPatternDetection(shouldDetectPatterns(violation));
        analysis.setHasAutomatedWorkflows(hasAutomatedWorkflows(violation.getViolationType()));
        
        // Determine response strategy
        VelocityResponseStrategy strategy = determineResponseStrategy(violation, riskLevel);
        analysis.setResponseStrategy(strategy);
        
        // Calculate velocity trend analysis
        VelocityTrendAnalysis trendAnalysis = analysisService.analyzeTrend(
            violation.getEntityId(),
            violation.getViolationType()
        );
        analysis.setTrendAnalysis(trendAnalysis);
        
        return analysis;
    }

    private String determineRiskLevel(VelocityViolation violation) {
        // Critical risk conditions
        if (CRITICAL_VELOCITY_TYPES.contains(violation.getViolationType().toString()) ||
            violation.getExceedancePercentage() > 500.0 ||
            violation.getSeverity() == VelocitySeverity.CRITICAL ||
            violation.isHighRiskCountry()) {
            return "CRITICAL";
        }
        
        // High risk conditions
        if (violation.getExceedancePercentage() > 200.0 ||
            violation.getSeverity() == VelocitySeverity.HIGH ||
            violation.getRecentViolationCount() > 5 ||
            (violation.getTotalAmount() != null && 
             violation.getTotalAmount().compareTo(new BigDecimal("100000")) > 0)) {
            return "HIGH";
        }
        
        // Medium risk conditions
        if (violation.getExceedancePercentage() > 100.0 ||
            violation.getSeverity() == VelocitySeverity.MEDIUM ||
            violation.getRecentViolationCount() > 2) {
            return "MEDIUM";
        }
        
        return "LOW";
    }

    private int calculateRiskScore(VelocityViolation violation) {
        int baseScore = 50;
        
        // Exceedance factor
        if (violation.getExceedancePercentage() > 1000.0) {
            baseScore += 30;
        } else if (violation.getExceedancePercentage() > 500.0) {
            baseScore += 25;
        } else if (violation.getExceedancePercentage() > 200.0) {
            baseScore += 20;
        } else if (violation.getExceedancePercentage() > 100.0) {
            baseScore += 15;
        }
        
        // Historical violations factor
        if (violation.getHistoricalViolationCount() > 10) {
            baseScore += 20;
        } else if (violation.getHistoricalViolationCount() > 5) {
            baseScore += 15;
        } else if (violation.getHistoricalViolationCount() > 2) {
            baseScore += 10;
        }
        
        // Entity risk factor
        if ("HIGH".equals(violation.getEntityRiskLevel())) {
            baseScore += 15;
        } else if ("MEDIUM".equals(violation.getEntityRiskLevel())) {
            baseScore += 10;
        }
        
        // Velocity type factor
        if (CRITICAL_VELOCITY_TYPES.contains(violation.getViolationType().toString())) {
            baseScore += 20;
        }
        
        // Country risk factor
        if (violation.isHighRiskCountry()) {
            baseScore += 15;
        }
        
        // Amount factor
        if (violation.getTotalAmount() != null) {
            if (violation.getTotalAmount().compareTo(new BigDecimal("1000000")) > 0) {
                baseScore += 20;
            } else if (violation.getTotalAmount().compareTo(new BigDecimal("100000")) > 0) {
                baseScore += 15;
            } else if (violation.getTotalAmount().compareTo(new BigDecimal("10000")) > 0) {
                baseScore += 10;
            }
        }
        
        // Recent activity burst factor
        if (violation.getRecentViolationCount() > 3 && 
            violation.getPreviousViolationDate() != null &&
            ChronoUnit.HOURS.between(violation.getPreviousViolationDate(), Instant.now()) < 24) {
            baseScore += 25;
        }
        
        return Math.min(baseScore, 100);
    }

    private boolean shouldTakeImmediateAction(VelocityViolation violation, String riskLevel) {
        return "CRITICAL".equals(riskLevel) ||
               CRITICAL_VELOCITY_TYPES.contains(violation.getViolationType().toString()) ||
               violation.getExceedancePercentage() > 500.0;
    }

    private boolean shouldDetectPatterns(VelocityViolation violation) {
        return violation.getHistoricalViolationCount() > 1 ||
               violation.getRecentViolationCount() > 0 ||
               "HIGH".equals(violation.getEntityRiskLevel());
    }

    private boolean hasAutomatedWorkflows(VelocityType velocityType) {
        return Arrays.asList(VelocityType.AMOUNT_BURST, VelocityType.COUNT_BURST, 
                           VelocityType.GEOGRAPHIC_VELOCITY, VelocityType.CROSS_CHANNEL_VELOCITY)
               .contains(velocityType);
    }

    private VelocityResponseStrategy determineResponseStrategy(VelocityViolation violation, String riskLevel) {
        switch (riskLevel) {
            case "CRITICAL":
                return VelocityResponseStrategy.IMMEDIATE_BLOCK;
            case "HIGH":
                return violation.getRecentViolationCount() > 2 ? 
                       VelocityResponseStrategy.TEMPORARY_LIMIT : VelocityResponseStrategy.ENHANCED_MONITORING;
            case "MEDIUM":
                return VelocityResponseStrategy.ENHANCED_MONITORING;
            case "LOW":
            default:
                return VelocityResponseStrategy.STANDARD_MONITORING;
        }
    }

    private VelocityProcessingResult processViolation(VelocityViolation violation, VelocityAnalysis analysis) {
        VelocityProcessingResult result = new VelocityProcessingResult();
        result.setViolationId(violation.getViolationId());
        result.setAnalysis(analysis);
        result.setProcessingStartTime(Instant.now());
        
        try {
            // Save violation to database
            VelocityViolation savedViolation = violationRepository.save(violation);
            result.setSavedViolation(savedViolation);
            
            // Process based on response strategy
            switch (analysis.getResponseStrategy()) {
                case IMMEDIATE_BLOCK:
                    result = processImmediateBlock(violation, analysis);
                    break;
                    
                case TEMPORARY_LIMIT:
                    result = processTemporaryLimit(violation, analysis);
                    break;
                    
                case ENHANCED_MONITORING:
                    result = processEnhancedMonitoring(violation, analysis);
                    break;
                    
                case STANDARD_MONITORING:
                    result = processStandardMonitoring(violation, analysis);
                    break;
            }
            
            result.setStatus(ProcessingStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Failed to process velocity violation: {}", violation.getViolationId(), e);
            result.setStatus(ProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new VelocityProcessingException("Velocity processing failed", e);
        }
        
        result.setProcessingEndTime(Instant.now());
        result.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(result.getProcessingStartTime(), result.getProcessingEndTime())
        );
        
        return result;
    }

    private VelocityProcessingResult processImmediateBlock(VelocityViolation violation, VelocityAnalysis analysis) {
        VelocityProcessingResult result = new VelocityProcessingResult();
        
        // Immediately block entity transactions
        BlockResult blockResult = controlService.blockEntityTransactions(
            violation.getEntityId(),
            violation.getEntityType(),
            "VELOCITY_VIOLATION",
            violation.getViolationId()
        );
        result.setBlockResult(blockResult);
        
        // Freeze pending transactions
        List<String> frozenTransactions = controlService.freezePendingTransactions(
            violation.getEntityId(),
            violation.getEntityType()
        );
        result.setFrozenTransactions(frozenTransactions);
        
        // Create urgent alert
        alertingService.createUrgentAlert(
            "CRITICAL_VELOCITY_VIOLATION",
            violation,
            "IMMEDIATE_BLOCK_APPLIED"
        );
        
        // Escalate to fraud team
        String caseId = caseManagementService.createUrgentCase(
            "VELOCITY_VIOLATION",
            violation,
            "FRAUD_TEAM"
        );
        result.setCaseId(caseId);
        
        return result;
    }

    private VelocityProcessingResult processTemporaryLimit(VelocityViolation violation, VelocityAnalysis analysis) {
        VelocityProcessingResult result = new VelocityProcessingResult();
        
        // Apply temporary velocity limits
        VelocityLimitResult limitResult = controlService.applyTemporaryLimits(
            violation.getEntityId(),
            violation.getEntityType(),
            violation.getViolationType(),
            determineNewLimits(violation)
        );
        result.setLimitResult(limitResult);
        
        // Enhanced transaction monitoring
        monitoringService.enableEnhancedMonitoring(
            violation.getEntityId(),
            violation.getEntityType(),
            "VELOCITY_VIOLATION"
        );
        
        // Schedule limit review
        workflowService.scheduleLimitReview(
            violation.getViolationId(),
            Instant.now().plus(24, ChronoUnit.HOURS)
        );
        
        return result;
    }

    private VelocityProcessingResult processEnhancedMonitoring(VelocityViolation violation, VelocityAnalysis analysis) {
        VelocityProcessingResult result = new VelocityProcessingResult();
        
        // Enable enhanced monitoring
        MonitoringResult monitoringResult = monitoringService.enableEnhancedMonitoring(
            violation.getEntityId(),
            violation.getEntityType(),
            "VELOCITY_PATTERN"
        );
        result.setMonitoringResult(monitoringResult);
        
        // Lower velocity thresholds temporarily
        controlService.adjustVelocityThresholds(
            violation.getEntityId(),
            violation.getViolationType(),
            0.8 // 20% reduction
        );
        
        // Create standard case
        String caseId = caseManagementService.createCase(
            "VELOCITY_MONITORING",
            violation,
            "RISK_TEAM"
        );
        result.setCaseId(caseId);
        
        return result;
    }

    private VelocityProcessingResult processStandardMonitoring(VelocityViolation violation, VelocityAnalysis analysis) {
        VelocityProcessingResult result = new VelocityProcessingResult();
        
        // Update velocity profile
        velocityProfileService.updateProfile(
            violation.getEntityId(),
            violation.getViolationType(),
            violation
        );
        
        // Log for future pattern analysis
        patternAnalysisService.recordVelocityEvent(violation);
        
        // Schedule standard review
        workflowService.scheduleStandardReview(
            violation.getViolationId(),
            Instant.now().plus(7, ChronoUnit.DAYS)
        );
        
        return result;
    }

    private Map<String, BigDecimal> determineNewLimits(VelocityViolation violation) {
        Map<String, BigDecimal> newLimits = new HashMap<>();
        
        // Reduce current limit by percentage based on exceedance
        BigDecimal reductionFactor;
        if (violation.getExceedancePercentage() > 500.0) {
            reductionFactor = new BigDecimal("0.5"); // 50% reduction
        } else if (violation.getExceedancePercentage() > 200.0) {
            reductionFactor = new BigDecimal("0.7"); // 30% reduction
        } else {
            reductionFactor = new BigDecimal("0.8"); // 20% reduction
        }
        
        BigDecimal newLimit = violation.getLimitValue().multiply(reductionFactor);
        newLimits.put(violation.getViolationType().toString(), newLimit);
        
        return newLimits;
    }

    private void applyImmediateControls(VelocityViolation violation, VelocityAnalysis analysis, 
                                      VelocityProcessingResult result) {
        List<String> controlsApplied = new ArrayList<>();
        
        // Block future transactions temporarily
        if (analysis.getRiskScore() > 90) {
            controlService.blockFutureTransactions(
                violation.getEntityId(),
                violation.getEntityType(),
                60 // 60 minutes
            );
            controlsApplied.add("FUTURE_TRANSACTIONS_BLOCKED");
        }
        
        // Apply circuit breaker pattern
        if ("AMOUNT_BURST".equals(violation.getViolationType().toString())) {
            controlService.enableCircuitBreaker(
                violation.getEntityId(),
                violation.getTimeWindow()
            );
            controlsApplied.add("CIRCUIT_BREAKER_ENABLED");
        }
        
        // Enhanced authentication requirements
        if (violation.getRecentViolationCount() > 3) {
            authenticationService.requireEnhancedAuth(
                violation.getEntityId(),
                "VELOCITY_VIOLATION"
            );
            controlsApplied.add("ENHANCED_AUTH_REQUIRED");
        }
        
        result.setImmediateControlsApplied(controlsApplied);
    }

    private void performPatternDetection(VelocityViolation violation, VelocityProcessingResult result) {
        // Detect velocity patterns
        VelocityPatternResult patternResult = patternAnalysisService.detectPatterns(
            violation.getEntityId(),
            violation.getViolationType(),
            Instant.now().minus(30, ChronoUnit.DAYS)
        );
        
        result.setPatternResult(patternResult);
        
        // Check for coordinated attacks
        if (patternResult.getPatternCount() > 3) {
            CoordinatedAttackResult attackResult = fraudDetectionService.checkCoordinatedAttack(
                violation.getEntityId(),
                violation.getViolationType()
            );
            result.setCoordinatedAttackResult(attackResult);
        }
        
        // Update ML models with new pattern data
        mlModelService.updateVelocityModels(violation, patternResult);
    }

    private void updateVelocityProfiles(VelocityViolation violation, VelocityAnalysis analysis) {
        // Update entity velocity profile
        velocityProfileService.updateEntityProfile(
            violation.getEntityId(),
            violation.getEntityType(),
            violation,
            analysis
        );
        
        // Update global velocity statistics
        velocityStatsService.updateGlobalStats(
            violation.getViolationType(),
            violation.getCountry(),
            violation.getChannel()
        );
        
        // Update industry benchmarks
        if (violation.getMerchantId() != null) {
            MerchantProfile merchantProfile = merchantService.getMerchantProfile(violation.getMerchantId());
            if (merchantProfile != null) {
                velocityBenchmarkService.updateIndustryBenchmarks(
                    merchantProfile.getIndustry(),
                    violation.getViolationType(),
                    violation.getActualValue()
                );
            }
        }
    }

    private void triggerAutomatedWorkflows(VelocityViolation violation, VelocityAnalysis analysis) {
        List<String> workflows = getAutomatedWorkflows(violation.getViolationType(), analysis.getRiskLevel());
        
        for (String workflowType : workflows) {
            CompletableFuture.runAsync(() -> {
                try {
                    workflowService.triggerWorkflow(workflowType, violation, analysis);
                } catch (Exception e) {
                    log.error("Failed to trigger workflow {} for velocity violation {}", 
                             workflowType, violation.getViolationId(), e);
                }
            });
        }
    }

    private List<String> getAutomatedWorkflows(VelocityType velocityType, String riskLevel) {
        Map<String, List<String>> workflowMapping = Map.of(
            "CRITICAL", Arrays.asList("CRITICAL_VELOCITY_INVESTIGATION", "EXECUTIVE_NOTIFICATION", "REGULATORY_ALERT"),
            "HIGH", Arrays.asList("HIGH_VELOCITY_INVESTIGATION", "ENHANCED_MONITORING", "LIMIT_ADJUSTMENT"),
            "MEDIUM", Arrays.asList("STANDARD_INVESTIGATION", "PROFILE_UPDATE"),
            "LOW", Arrays.asList("PROFILE_UPDATE", "STATISTICAL_ANALYSIS")
        );
        
        return workflowMapping.getOrDefault(riskLevel, Arrays.asList("STANDARD_INVESTIGATION"));
    }

    private void sendVelocityNotifications(VelocityViolation violation, VelocityAnalysis analysis, 
                                         VelocityProcessingResult result) {
        
        Map<String, Object> notificationData = Map.of(
            "violationId", violation.getViolationId(),
            "velocityType", violation.getViolationType().toString(),
            "entityId", violation.getEntityId(),
            "exceedancePercentage", violation.getExceedancePercentage(),
            "riskLevel", analysis.getRiskLevel(),
            "riskScore", analysis.getRiskScore(),
            "responseStrategy", analysis.getResponseStrategy().toString(),
            "actualValue", violation.getActualValue(),
            "limitValue", violation.getLimitValue()
        );
        
        // Critical notifications
        if ("CRITICAL".equals(analysis.getRiskLevel())) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendCriticalVelocityAlert(notificationData);
                notificationService.sendExecutiveAlert("CRITICAL_VELOCITY_VIOLATION", notificationData);
            });
        }
        
        // Entity notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendEntityVelocityNotification(
                violation.getEntityId(),
                violation.getEntityType(),
                notificationData
            );
        });
        
        // Team notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendTeamNotification(
                "FRAUD_TEAM",
                "VELOCITY_VIOLATION",
                notificationData
            );
        });
        
        // Merchant notifications for merchant-related violations
        if (violation.getMerchantId() != null) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendMerchantVelocityAlert(
                    violation.getMerchantId(),
                    notificationData
                );
            });
        }
    }

    private void updateMonitoringSystems(VelocityViolation violation, VelocityProcessingResult result) {
        // Update velocity monitoring dashboard
        dashboardService.updateVelocityDashboard(violation, result);
        
        // Update real-time fraud monitoring
        fraudMonitoringService.updateVelocityMetrics(violation, result);
        
        // Update risk management systems
        riskManagementService.updateVelocityRisk(violation, result);
        
        // Update compliance monitoring
        complianceMonitoringService.updateVelocityCompliance(violation, result);
    }

    private void auditVelocityProcessing(VelocityViolation violation, VelocityProcessingResult result, 
                                       GenericKafkaEvent originalEvent) {
        auditService.auditVelocityViolation(
            violation.getViolationId(),
            violation.getViolationType().toString(),
            violation.getEntityId(),
            violation.getEntityType(),
            violation.getExceedancePercentage(),
            result.getStatus().toString(),
            originalEvent.getEventId()
        );
    }

    private void recordVelocityMetrics(VelocityViolation violation, VelocityProcessingResult result, 
                                     long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordVelocityMetrics(
            violation.getViolationType().toString(),
            violation.getEntityType(),
            violation.getExceedancePercentage(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS,
            result.getStatus().toString()
        );
        
        // Record velocity volume metrics
        metricsService.recordVelocityVolumeMetrics(
            violation.getViolationType().toString(),
            violation.getCountry(),
            violation.getChannel()
        );
        
        // Record control effectiveness
        if (result.getImmediateControlsApplied() != null) {
            metricsService.recordControlEffectivenessMetrics(
                result.getImmediateControlsApplied().size(),
                violation.getExceedancePercentage()
            );
        }
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("velocity-monitoring-validation-errors", event);
    }

    private void handleCriticalVelocityError(GenericKafkaEvent event, CriticalVelocityException e) {
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_VELOCITY_PROCESSING_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("velocity-monitoring-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying velocity violation {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("velocity-monitoring-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for velocity violation {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "velocity-monitoring");
        
        kafkaTemplate.send("velocity-monitoring.DLQ", event);
        
        alertingService.createDLQAlert(
            "velocity-monitoring",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleVelocityMonitoringFailure(GenericKafkaEvent event, String topic, int partition,
                                               long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for velocity monitoring: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Velocity Monitoring Circuit Breaker Open",
            "Velocity monitoring is failing. Fraud prevention compromised."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private Integer extractInteger(Map<String, Object> map, String key, Integer defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Number) return ((Number) value).intValue();
        return Integer.parseInt(value.toString());
    }

    private Double extractDouble(Map<String, Object> map, String key, Double defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Double) return (Double) value;

        // Handle BigDecimal with MoneyMath for precision
        if (value instanceof BigDecimal) {
            return (double) MoneyMath.toMLFeature((BigDecimal) value);
        }

        if (value instanceof Number) return ((Number) value).doubleValue();
        return Double.parseDouble(value.toString());
    }

    private Instant extractInstant(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof Instant) return (Instant) value;
        if (value instanceof Long) return Instant.ofEpochMilli((Long) value);
        return Instant.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class CriticalVelocityException extends RuntimeException {
        public CriticalVelocityException(String message) {
            super(message);
        }
    }

    public static class VelocityProcessingException extends RuntimeException {
        public VelocityProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}