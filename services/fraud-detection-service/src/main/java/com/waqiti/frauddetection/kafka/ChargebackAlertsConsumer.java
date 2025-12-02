package com.waqiti.frauddetection.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.frauddetection.model.*;
import com.waqiti.frauddetection.repository.ChargebackRepository;
import com.waqiti.frauddetection.service.*;
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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for chargeback alerts
 * Handles chargeback detection, prevention, and merchant risk assessment
 * 
 * Critical for: Revenue protection, merchant risk management, fraud prevention
 * SLA: Must process alerts within 10 seconds for rapid chargeback mitigation
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ChargebackAlertsConsumer {

    private final ChargebackRepository chargebackRepository;
    private final ChargebackPredictionService predictionService;
    private final ChargebackPreventionService preventionService;
    private final MerchantRiskService merchantRiskService;
    private final NotificationService notificationService;
    private final WorkflowService workflowService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final ScheduledExecutorService scheduledExecutor;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long SLA_THRESHOLD_MS = 10000; // 10 seconds
    private static final Set<String> HIGH_RISK_REASON_CODES = Set.of(
        "4837", "4863", "4855", "4834", "4808", "4812", "4840"
    );
    
    @KafkaListener(
        topics = {"chargeback-alerts"},
        groupId = "chargeback-alerts-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "chargeback-alerts-processor", fallbackMethod = "handleChargebackAlertFailure")
    @Retry(name = "chargeback-alerts-processor")
    public void processChargebackAlert(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing chargeback alert: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            ChargebackAlert alert = extractChargebackAlert(payload);
            
            // Validate alert
            validateAlert(alert);
            
            // Check for duplicate alert
            if (isDuplicateAlert(alert)) {
                log.warn("Duplicate chargeback alert detected: {}, skipping", alert.getAlertId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Enrich alert with transaction and merchant data
            ChargebackAlert enrichedAlert = enrichAlert(alert);
            
            // Determine alert type and urgency
            ChargebackType chargebackType = determineChargebackType(enrichedAlert);
            
            // Process alert based on type
            ChargebackProcessingResult result = processAlert(enrichedAlert, chargebackType);
            
            // Perform chargeback prediction analysis
            if (chargebackType.requiresPrediction()) {
                performChargebackPrediction(enrichedAlert, result);
            }
            
            // Execute prevention strategies
            if (chargebackType.allowsPrevention()) {
                executePreventionStrategies(enrichedAlert, result);
            }
            
            // Update merchant risk profile
            updateMerchantRisk(enrichedAlert, result);
            
            // Trigger automated workflows
            if (chargebackType.hasAutomatedWorkflows()) {
                triggerAutomatedWorkflows(enrichedAlert, chargebackType);
            }
            
            // Send notifications
            sendChargebackNotifications(enrichedAlert, chargebackType, result);
            
            // Update tracking systems
            updateTrackingSystems(enrichedAlert, result);
            
            // Audit processing
            auditChargebackProcessing(enrichedAlert, result, event);
            
            // Record metrics
            recordChargebackMetrics(enrichedAlert, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed chargeback alert: {} type: {} amount: {} in {}ms", 
                    enrichedAlert.getAlertId(), chargebackType, enrichedAlert.getAmount(), 
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for chargeback alert: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (CriticalChargebackException e) {
            log.error("Critical chargeback processing failed: {}", eventId, e);
            handleCriticalChargebackError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process chargeback alert: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private ChargebackAlert extractChargebackAlert(Map<String, Object> payload) {
        return ChargebackAlert.builder()
            .alertId(extractString(payload, "alertId", UUID.randomUUID().toString()))
            .chargebackId(extractString(payload, "chargebackId", null))
            .transactionId(extractString(payload, "transactionId", null))
            .merchantId(extractString(payload, "merchantId", null))
            .customerId(extractString(payload, "customerId", null))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", "USD"))
            .reasonCode(extractString(payload, "reasonCode", null))
            .reasonDescription(extractString(payload, "reasonDescription", null))
            .cardNetwork(extractString(payload, "cardNetwork", null))
            .cardType(extractString(payload, "cardType", null))
            .acquirerReferenceNumber(extractString(payload, "acquirerReferenceNumber", null))
            .issuerReferenceNumber(extractString(payload, "issuerReferenceNumber", null))
            .disputeDate(extractInstant(payload, "disputeDate"))
            .responseDeadline(extractInstant(payload, "responseDeadline"))
            .liabilityShift(extractBoolean(payload, "liabilityShift", false))
            .chargebackStage(extractString(payload, "chargebackStage", "NOTIFICATION"))
            .severity(ChargebackSeverity.fromString(extractString(payload, "severity", "MEDIUM")))
            .status(ChargebackStatus.OPEN)
            .sourceSystem(extractString(payload, "sourceSystem", "UNKNOWN"))
            .metadata(extractMap(payload, "metadata"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateAlert(ChargebackAlert alert) {
        if (alert.getTransactionId() == null || alert.getTransactionId().isEmpty()) {
            throw new ValidationException("Transaction ID is required");
        }
        
        if (alert.getMerchantId() == null || alert.getMerchantId().isEmpty()) {
            throw new ValidationException("Merchant ID is required");
        }
        
        if (alert.getAmount() == null || alert.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Valid amount is required");
        }
        
        if (alert.getReasonCode() == null || alert.getReasonCode().isEmpty()) {
            throw new ValidationException("Reason code is required");
        }
        
        // Validate response deadline for active chargebacks
        if ("NOTIFICATION".equals(alert.getChargebackStage()) && alert.getResponseDeadline() == null) {
            throw new ValidationException("Response deadline required for chargeback notifications");
        }
        
        // Validate high-risk reason codes
        if (HIGH_RISK_REASON_CODES.contains(alert.getReasonCode()) && 
            alert.getSeverity().ordinal() < ChargebackSeverity.HIGH.ordinal()) {
            log.warn("High-risk reason code {} should have HIGH or CRITICAL severity", alert.getReasonCode());
        }
    }

    private boolean isDuplicateAlert(ChargebackAlert alert) {
        // Check for exact duplicate by alert ID
        if (alert.getChargebackId() != null && 
            chargebackRepository.existsByChargebackIdAndCreatedAtAfter(
                alert.getChargebackId(), 
                Instant.now().minus(24, ChronoUnit.HOURS))) {
            return true;
        }
        
        // Check for duplicate by transaction and reason code
        return chargebackRepository.existsByTransactionIdAndReasonCodeAndCreatedAtAfter(
            alert.getTransactionId(),
            alert.getReasonCode(),
            Instant.now().minus(1, ChronoUnit.HOURS)
        );
    }

    private ChargebackAlert enrichAlert(ChargebackAlert alert) {
        // Enrich with transaction data
        TransactionData transactionData = transactionService.getTransactionData(alert.getTransactionId());
        if (transactionData != null) {
            alert.setOriginalTransactionDate(transactionData.getTransactionDate());
            alert.setPaymentMethod(transactionData.getPaymentMethod());
            alert.setAcquirerName(transactionData.getAcquirerName());
            alert.setProcessorName(transactionData.getProcessorName());
            alert.setMerchantCategoryCode(transactionData.getMerchantCategoryCode());
            alert.setCountry(transactionData.getCountry());
        }
        
        // Enrich with merchant data
        MerchantProfile merchantProfile = merchantService.getMerchantProfile(alert.getMerchantId());
        if (merchantProfile != null) {
            alert.setMerchantName(merchantProfile.getMerchantName());
            alert.setMerchantRiskLevel(merchantProfile.getRiskLevel());
            alert.setMerchantIndustry(merchantProfile.getIndustry());
            alert.setChargebackHistory(merchantProfile.getChargebackHistory());
        }
        
        // Enrich with historical chargeback data
        ChargebackHistory history = chargebackRepository.getChargebackHistory(
            alert.getMerchantId(),
            Instant.now().minus(90, ChronoUnit.DAYS)
        );
        alert.setHistoricalChargebackCount(history.getTotalChargebacks());
        alert.setChargebackRate(history.getChargebackRate());
        alert.setPreviousSimilarChargebacks(history.getSimilarChargebacks(alert.getReasonCode()));
        
        // Calculate risk scores
        alert.setRiskScore(calculateRiskScore(alert, history));
        alert.setPreventionScore(calculatePreventionScore(alert));
        
        return alert;
    }

    private int calculateRiskScore(ChargebackAlert alert, ChargebackHistory history) {
        int baseScore = 50;
        
        // Reason code risk factor
        if (HIGH_RISK_REASON_CODES.contains(alert.getReasonCode())) {
            baseScore += 20;
        }
        
        // Amount factor
        if (alert.getAmount().compareTo(new BigDecimal("1000")) > 0) {
            baseScore += 15;
        } else if (alert.getAmount().compareTo(new BigDecimal("500")) > 0) {
            baseScore += 10;
        }
        
        // Merchant risk factor
        switch (alert.getMerchantRiskLevel()) {
            case "HIGH":
                baseScore += 20;
                break;
            case "MEDIUM":
                baseScore += 10;
                break;
        }
        
        // Historical chargeback factor
        if (history.getChargebackRate() > 0.75) {
            baseScore += 25;
        } else if (history.getChargebackRate() > 0.50) {
            baseScore += 15;
        } else if (history.getChargebackRate() > 0.25) {
            baseScore += 10;
        }
        
        // Time factor (faster disputes are riskier)
        if (alert.getOriginalTransactionDate() != null) {
            long daysSinceTransaction = ChronoUnit.DAYS.between(
                alert.getOriginalTransactionDate(), 
                alert.getDisputeDate()
            );
            if (daysSinceTransaction < 30) {
                baseScore += 10;
            }
        }
        
        // Card type factor
        if ("PREPAID".equals(alert.getCardType()) || "GIFT".equals(alert.getCardType())) {
            baseScore += 15;
        }
        
        return Math.min(baseScore, 100);
    }

    private int calculatePreventionScore(ChargebackAlert alert) {
        int preventionScore = 0;
        
        // Higher score means better prevention opportunity
        switch (alert.getChargebackStage()) {
            case "NOTIFICATION":
                preventionScore = 80; // Can still be disputed
                break;
            case "FIRST_CHARGEBACK":
                preventionScore = 60; // Can provide evidence
                break;
            case "SECOND_CHARGEBACK":
                preventionScore = 30; // Limited options
                break;
            case "ARBITRATION":
                preventionScore = 10; // Very limited
                break;
        }
        
        // Reduce score for high-risk reason codes
        if (HIGH_RISK_REASON_CODES.contains(alert.getReasonCode())) {
            preventionScore -= 20;
        }
        
        // Increase score for valid merchant responses
        if (alert.getMerchantRiskLevel().equals("LOW")) {
            preventionScore += 15;
        }
        
        return Math.max(preventionScore, 0);
    }

    private ChargebackType determineChargebackType(ChargebackAlert alert) {
        if (HIGH_RISK_REASON_CODES.contains(alert.getReasonCode()) || 
            alert.getRiskScore() >= 80 ||
            alert.getSeverity() == ChargebackSeverity.CRITICAL) {
            return ChargebackType.HIGH_RISK;
        }
        
        if (alert.getPreventionScore() >= 60 && 
            "NOTIFICATION".equals(alert.getChargebackStage())) {
            return ChargebackType.PREVENTABLE;
        }
        
        if (alert.getChargebackRate() > 0.50) {
            return ChargebackType.REPEAT_OFFENDER;
        }
        
        if (alert.getAmount().compareTo(new BigDecimal("5000")) > 0) {
            return ChargebackType.HIGH_VALUE;
        }
        
        return ChargebackType.STANDARD;
    }

    private ChargebackProcessingResult processAlert(ChargebackAlert alert, ChargebackType type) {
        ChargebackProcessingResult result = new ChargebackProcessingResult();
        result.setAlertId(alert.getAlertId());
        result.setChargebackType(type);
        result.setProcessingStartTime(Instant.now());
        
        try {
            // Save alert to database
            ChargebackAlert savedAlert = chargebackRepository.save(alert);
            result.setSavedAlert(savedAlert);
            
            // Process based on type
            switch (type) {
                case HIGH_RISK:
                    result = processHighRiskChargeback(alert);
                    break;
                    
                case PREVENTABLE:
                    result = processPreventableChargeback(alert);
                    break;
                    
                case REPEAT_OFFENDER:
                    result = processRepeatOffenderChargeback(alert);
                    break;
                    
                case HIGH_VALUE:
                    result = processHighValueChargeback(alert);
                    break;
                    
                case STANDARD:
                    result = processStandardChargeback(alert);
                    break;
            }
            
            result.setStatus(ProcessingStatus.COMPLETED);
            
        } catch (Exception e) {
            log.error("Failed to process chargeback alert: {}", alert.getAlertId(), e);
            result.setStatus(ProcessingStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new ChargebackProcessingException("Chargeback processing failed", e);
        }
        
        result.setProcessingEndTime(Instant.now());
        result.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(result.getProcessingStartTime(), result.getProcessingEndTime())
        );
        
        return result;
    }

    private ChargebackProcessingResult processHighRiskChargeback(ChargebackAlert alert) {
        ChargebackProcessingResult result = new ChargebackProcessingResult();
        
        // Immediate merchant notification
        notificationService.sendUrgentChargebackAlert(alert);
        
        // Freeze related transactions
        List<String> frozenTransactions = transactionService.freezeRelatedTransactions(
            alert.getMerchantId(),
            alert.getReasonCode(),
            alert.getAmount()
        );
        result.setFrozenTransactions(frozenTransactions);
        
        // Enhanced merchant monitoring
        merchantRiskService.enableEnhancedMonitoring(alert.getMerchantId(), "HIGH_RISK_CHARGEBACK");
        
        // Create urgent case
        String caseId = caseManagementService.createUrgentCase(
            "HIGH_RISK_CHARGEBACK",
            alert,
            "CHARGEBACK_TEAM"
        );
        result.setCaseId(caseId);
        
        // Schedule executive review for high amounts
        if (alert.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            workflowService.scheduleExecutiveReview(alert, Instant.now().plus(2, ChronoUnit.HOURS));
        }
        
        return result;
    }

    private ChargebackProcessingResult processPreventableChargeback(ChargebackAlert alert) {
        ChargebackProcessingResult result = new ChargebackProcessingResult();
        
        // Analyze prevention opportunities
        PreventionAnalysis analysis = preventionService.analyzePreventionOptions(alert);
        result.setPreventionAnalysis(analysis);
        
        // Auto-generate dispute response if possible
        if (analysis.canAutoRespond()) {
            DisputeResponse response = preventionService.generateDisputeResponse(alert);
            result.setDisputeResponse(response);
            
            // Submit response automatically for low-risk cases
            if (alert.getRiskScore() < 50) {
                preventionService.submitDisputeResponse(response);
                result.setAutoSubmitted(true);
            }
        }
        
        // Schedule merchant consultation
        workflowService.scheduleMerchantConsultation(
            alert.getMerchantId(),
            alert.getAlertId(),
            Instant.now().plus(4, ChronoUnit.HOURS)
        );
        
        return result;
    }

    private ChargebackProcessingResult processRepeatOffenderChargeback(ChargebackAlert alert) {
        ChargebackProcessingResult result = new ChargebackProcessingResult();
        
        // Apply stricter merchant controls
        MerchantControlResult controls = merchantRiskService.applyStricterControls(
            alert.getMerchantId(),
            "REPEAT_CHARGEBACKS"
        );
        result.setMerchantControls(controls);
        
        // Review merchant agreement
        workflowService.triggerMerchantAgreementReview(alert.getMerchantId());
        
        // Calculate potential penalties
        BigDecimal penalty = merchantRiskService.calculateChargebackPenalty(
            alert.getMerchantId(),
            alert.getAmount()
        );
        result.setPotentialPenalty(penalty);
        
        // Enhanced reporting
        reportingService.generateMerchantRiskReport(alert.getMerchantId(), alert.getAlertId());
        
        return result;
    }

    private ChargebackProcessingResult processHighValueChargeback(ChargebackAlert alert) {
        ChargebackProcessingResult result = new ChargebackProcessingResult();
        
        // Assign to senior analyst
        String analystId = assignmentService.getSeniorChargebackAnalyst();
        result.setAssignedAnalyst(analystId);
        
        // Comprehensive transaction review
        TransactionReview review = transactionService.performComprehensiveReview(
            alert.getTransactionId()
        );
        result.setTransactionReview(review);
        
        // Collect additional evidence
        EvidenceCollection evidence = evidenceService.collectTransactionEvidence(
            alert.getTransactionId(),
            alert.getReasonCode()
        );
        result.setEvidenceCollection(evidence);
        
        // Legal consultation for very high amounts
        if (alert.getAmount().compareTo(new BigDecimal("25000")) > 0) {
            workflowService.requestLegalConsultation(alert);
            result.setLegalConsultationRequested(true);
        }
        
        return result;
    }

    private ChargebackProcessingResult processStandardChargeback(ChargebackAlert alert) {
        ChargebackProcessingResult result = new ChargebackProcessingResult();
        
        // Standard processing workflow
        String workflowId = workflowService.startStandardChargebackWorkflow(alert);
        result.setWorkflowId(workflowId);
        
        // Assign to available analyst
        String analystId = assignmentService.getNextAvailableAnalyst("CHARGEBACK_TEAM");
        result.setAssignedAnalyst(analystId);
        
        // Basic evidence collection
        EvidenceCollection evidence = evidenceService.collectBasicEvidence(alert.getTransactionId());
        result.setEvidenceCollection(evidence);
        
        return result;
    }

    private void performChargebackPrediction(ChargebackAlert alert, ChargebackProcessingResult result) {
        // Predict likelihood of winning dispute
        ChargebackPrediction prediction = predictionService.predictDisputeOutcome(alert);
        result.setChargebackPrediction(prediction);
        
        // Predict future chargeback risk for merchant
        FutureRiskPrediction futureRisk = predictionService.predictFutureChargebackRisk(
            alert.getMerchantId(),
            alert.getReasonCode()
        );
        result.setFutureRiskPrediction(futureRisk);
        
        // Recommend actions based on predictions
        List<String> recommendations = predictionService.generateRecommendations(
            prediction, 
            futureRisk
        );
        result.setRecommendations(recommendations);
    }

    private void executePreventionStrategies(ChargebackAlert alert, ChargebackProcessingResult result) {
        List<String> strategiesExecuted = new ArrayList<>();
        
        // Strategy 1: Transaction velocity limits
        if (alert.getRiskScore() > 70) {
            velocityControlService.applyTemporaryLimits(
                alert.getMerchantId(),
                alert.getReasonCode()
            );
            strategiesExecuted.add("VELOCITY_LIMITS_APPLIED");
        }
        
        // Strategy 2: Enhanced verification
        if ("4837".equals(alert.getReasonCode()) || "4863".equals(alert.getReasonCode())) {
            merchantService.enableEnhancedVerification(alert.getMerchantId());
            strategiesExecuted.add("ENHANCED_VERIFICATION_ENABLED");
        }
        
        // Strategy 3: Blacklist prevention
        if (alert.getCustomerId() != null && alert.getRiskScore() > 80) {
            blacklistService.addCustomerToWatchlist(
                alert.getCustomerId(),
                "CHARGEBACK_RISK",
                alert.getReasonCode()
            );
            strategiesExecuted.add("CUSTOMER_WATCHLISTED");
        }
        
        // Strategy 4: Merchant training
        if (alert.getPreviousSimilarChargebacks() > 3) {
            trainingService.scheduleMerchantTraining(
                alert.getMerchantId(),
                alert.getReasonCode()
            );
            strategiesExecuted.add("MERCHANT_TRAINING_SCHEDULED");
        }
        
        result.setPreventionStrategies(strategiesExecuted);
    }

    private void updateMerchantRisk(ChargebackAlert alert, ChargebackProcessingResult result) {
        // Update merchant chargeback statistics
        merchantRiskService.updateChargebackStats(
            alert.getMerchantId(),
            alert.getAmount(),
            alert.getReasonCode()
        );
        
        // Recalculate merchant risk score
        int newRiskScore = merchantRiskService.recalculateRiskScore(alert.getMerchantId());
        result.setUpdatedMerchantRiskScore(newRiskScore);
        
        // Apply risk-based actions
        if (newRiskScore > 80) {
            merchantRiskService.triggerHighRiskActions(alert.getMerchantId());
        }
        
        // Update industry benchmarks
        merchantRiskService.updateIndustryBenchmarks(
            alert.getMerchantIndustry(),
            alert.getReasonCode()
        );
    }

    private void triggerAutomatedWorkflows(ChargebackAlert alert, ChargebackType type) {
        List<String> workflows = getAutomatedWorkflows(type);
        
        for (String workflowType : workflows) {
            CompletableFuture.runAsync(() -> {
                try {
                    workflowService.triggerWorkflow(workflowType, alert);
                } catch (Exception e) {
                    log.error("Failed to trigger workflow {} for chargeback alert {}", 
                             workflowType, alert.getAlertId(), e);
                }
            });
        }
    }

    private List<String> getAutomatedWorkflows(ChargebackType type) {
        Map<ChargebackType, List<String>> workflowMapping = Map.of(
            ChargebackType.HIGH_RISK, Arrays.asList("HIGH_RISK_INVESTIGATION", "EXECUTIVE_NOTIFICATION"),
            ChargebackType.PREVENTABLE, Arrays.asList("PREVENTION_ANALYSIS", "DISPUTE_PREPARATION"),
            ChargebackType.REPEAT_OFFENDER, Arrays.asList("MERCHANT_REVIEW", "PENALTY_CALCULATION"),
            ChargebackType.HIGH_VALUE, Arrays.asList("EVIDENCE_COLLECTION", "LEGAL_REVIEW"),
            ChargebackType.STANDARD, Arrays.asList("STANDARD_PROCESSING")
        );
        
        return workflowMapping.getOrDefault(type, Arrays.asList("STANDARD_PROCESSING"));
    }

    private void sendChargebackNotifications(ChargebackAlert alert, ChargebackType type, 
                                           ChargebackProcessingResult result) {
        
        Map<String, Object> notificationData = Map.of(
            "alertId", alert.getAlertId(),
            "chargebackId", alert.getChargebackId(),
            "merchantId", alert.getMerchantId(),
            "amount", alert.getAmount(),
            "reasonCode", alert.getReasonCode(),
            "chargebackType", type.toString(),
            "riskScore", alert.getRiskScore(),
            "responseDeadline", alert.getResponseDeadline()
        );
        
        // Immediate notifications for high-risk chargebacks
        if (type == ChargebackType.HIGH_RISK) {
            CompletableFuture.runAsync(() -> {
                notificationService.sendUrgentChargebackAlert(alert.getMerchantId(), notificationData);
                notificationService.sendExecutiveAlert("HIGH_RISK_CHARGEBACK", notificationData);
            });
        }
        
        // Merchant notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendMerchantChargebackNotification(
                alert.getMerchantId(),
                notificationData
            );
        });
        
        // Team notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendTeamNotification(
                "CHARGEBACK_TEAM",
                "NEW_CHARGEBACK_ALERT",
                notificationData
            );
        });
    }

    private void updateTrackingSystems(ChargebackAlert alert, ChargebackProcessingResult result) {
        // Update chargeback tracking dashboard
        dashboardService.updateChargebackDashboard(alert, result);
        
        // Update merchant portal
        merchantPortalService.updateChargebackStatus(alert.getMerchantId(), alert);
        
        // Update risk monitoring systems
        riskMonitoringService.updateChargebackMetrics(alert, result);
    }

    private void auditChargebackProcessing(ChargebackAlert alert, ChargebackProcessingResult result, 
                                         GenericKafkaEvent originalEvent) {
        auditService.auditChargebackAlert(
            alert.getAlertId(),
            alert.getChargebackId(),
            alert.getMerchantId(),
            alert.getAmount(),
            alert.getReasonCode(),
            result.getStatus().toString(),
            originalEvent.getEventId()
        );
    }

    private void recordChargebackMetrics(ChargebackAlert alert, ChargebackProcessingResult result, 
                                       long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordChargebackMetrics(
            alert.getReasonCode(),
            alert.getMerchantIndustry(),
            alert.getAmount(),
            processingTime,
            processingTime <= SLA_THRESHOLD_MS,
            result.getStatus().toString()
        );
        
        // Record chargeback volume metrics
        metricsService.recordChargebackVolumeMetrics(
            alert.getCardNetwork(),
            alert.getReasonCode(),
            alert.getSeverity().toString()
        );
        
        // Record prevention effectiveness
        if (result.getPreventionStrategies() != null) {
            metricsService.recordPreventionMetrics(
                result.getPreventionStrategies().size(),
                alert.getPreventionScore()
            );
        }
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("chargeback-alert-validation-errors", event);
    }

    private void handleCriticalChargebackError(GenericKafkaEvent event, CriticalChargebackException e) {
        emergencyAlertService.createEmergencyAlert(
            "CRITICAL_CHARGEBACK_PROCESSING_FAILED",
            event.getPayload(),
            e.getMessage()
        );
        
        kafkaTemplate.send("chargeback-alert-critical-failures", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying chargeback alert {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("chargeback-alerts-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for chargeback alert {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "chargeback-alerts");
        
        kafkaTemplate.send("chargeback-alerts.DLQ", event);
        
        alertingService.createDLQAlert(
            "chargeback-alerts",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleChargebackAlertFailure(GenericKafkaEvent event, String topic, int partition,
                                           long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for chargeback alert processing: {}", e.getMessage());
        
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
            "Chargeback Alerts Circuit Breaker Open",
            "Chargeback alert processing is failing. Revenue protection compromised."
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

    private Boolean extractBoolean(Map<String, Object> map, String key, Boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
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

    public static class CriticalChargebackException extends RuntimeException {
        public CriticalChargebackException(String message) {
            super(message);
        }
    }

    public static class ChargebackProcessingException extends RuntimeException {
        public ChargebackProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}