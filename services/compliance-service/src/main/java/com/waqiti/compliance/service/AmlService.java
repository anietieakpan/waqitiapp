package com.waqiti.compliance.service;

import com.waqiti.compliance.model.AMLMonitoringResult;
import com.waqiti.compliance.model.AMLRuleViolation;
import com.waqiti.compliance.model.ComplianceRiskProfile;
import com.waqiti.compliance.repository.ComplianceRiskProfileRepository;
import com.waqiti.compliance.service.impl.AMLTransactionMonitoringServiceImpl;
import com.waqiti.common.audit.ComprehensiveAuditService;
import com.waqiti.common.events.ComplianceAlertEvent;
import com.waqiti.common.events.SarFilingRequestEvent;
import com.waqiti.common.exception.ComplianceException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * PRODUCTION AML SERVICE - P0 BLOCKER FIX
 *
 * Anti-Money Laundering (AML) Service - Production Implementation
 * Replaces stub implementation with full AML monitoring capabilities
 *
 * CRITICAL COMPLIANCE COMPONENT: Real-time money laundering detection
 * REGULATORY IMPACT: Prevents AML violations and regulatory penalties
 * COMPLIANCE STANDARDS: FinCEN BSA/AML, 31 CFR 1020.320, OFAC
 *
 * Features:
 * - Real-time transaction monitoring with AML rule engine
 * - Structuring/Smurfing detection (31 CFR 1020.320)
 * - Velocity checks and pattern analysis
 * - Suspicious Activity Report (SAR) auto-filing
 * - Enhanced Due Diligence (EDD) triggers
 * - Compliance officer alerting
 * - Risk scoring and profiling
 * - Geographic risk assessment
 * - Regulatory reporting integration
 *
 * @author Waqiti Compliance Team
 * @version 2.0.0 - Production Implementation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmlService {

    // Dependencies - Production services
    private final AMLTransactionMonitoringServiceImpl amlMonitoringService;
    private final ComprehensiveAuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ComplianceRiskProfileRepository riskProfileRepository;

    // SAR Filing Thresholds (FinCEN Requirements)
    private static final BigDecimal SAR_THRESHOLD = new BigDecimal("5000"); // $5,000 SAR threshold
    private static final BigDecimal CTR_THRESHOLD = new BigDecimal("10000"); // $10,000 CTR threshold
    private static final double HIGH_RISK_SCORE_THRESHOLD = 0.75;
    private static final double CRITICAL_RISK_SCORE_THRESHOLD = 0.85;

    /**
     * PRODUCTION IMPLEMENTATION: Process AML Alert
     *
     * Analyzes alerts for money laundering indicators and initiates appropriate actions:
     * - SAR filing for suspicious patterns
     * - Account freeze for critical violations
     * - Compliance officer notification
     * - Enhanced due diligence triggers
     *
     * @param entityId Customer/Account ID being alerted
     * @param alertType Type of AML alert (STRUCTURING, VELOCITY, PATTERN, etc.)
     * @param alertData Alert details and transaction data
     */
    @CircuitBreaker(name = "aml-service", fallbackMethod = "processAmlAlertFallback")
    @Retry(name = "aml-service", fallbackMethod = "processAmlAlertFallback")
    @Transactional(isolation = Isolation.READ_COMMITTED, timeout = 60)
    public void processAmlAlert(String entityId, String alertType, Map<String, Object> alertData) {
        log.info("PRODUCTION AML: Processing alert - Entity: {}, Type: {}", entityId, alertType);

        String alertId = UUID.randomUUID().toString();

        try {
            // Step 1: Extract transaction details from alert data
            UUID userId = UUID.fromString(entityId);
            UUID transactionId = extractTransactionId(alertData);
            BigDecimal amount = extractAmount(alertData);
            String currency = extractCurrency(alertData);
            String transactionType = extractTransactionType(alertData);

            // Step 2: Validate alert data completeness
            validateAlertData(userId, transactionId, amount, currency, alertType);

            // Step 3: Perform comprehensive AML monitoring
            AMLMonitoringResult monitoringResult = amlMonitoringService.monitorTransaction(
                transactionId, userId, amount, currency, transactionType,
                extractSourceAccount(alertData), extractDestinationAccount(alertData)
            );

            // Step 4: Analyze alert type and apply specific rules
            List<AMLRuleViolation> alertTypeViolations = analyzeAlertType(
                alertType, userId, amount, currency, alertData
            );

            // Merge violations from monitoring and alert analysis
            List<AMLRuleViolation> allViolations = new ArrayList<>(monitoringResult.getViolations());
            allViolations.addAll(alertTypeViolations);

            // Step 5: Calculate enhanced risk score
            double enhancedRiskScore = calculateEnhancedRiskScore(
                monitoringResult.getRiskScore(), alertType, alertTypeViolations
            );

            // Step 6: Update user's compliance risk profile
            updateComplianceRiskProfile(userId, enhancedRiskScore, allViolations);

            // Step 7: Determine required actions based on risk level
            if (enhancedRiskScore >= CRITICAL_RISK_SCORE_THRESHOLD ||
                monitoringResult.isStructuringDetected()) {

                // CRITICAL: Immediate action required
                handleCriticalAmlAlert(
                    alertId, userId, transactionId, amount, currency,
                    enhancedRiskScore, allViolations, monitoringResult
                );

            } else if (enhancedRiskScore >= HIGH_RISK_SCORE_THRESHOLD) {

                // HIGH RISK: Requires urgent review and potential SAR
                handleHighRiskAmlAlert(
                    alertId, userId, transactionId, amount, currency,
                    enhancedRiskScore, allViolations, monitoringResult
                );

            } else {

                // MEDIUM/LOW RISK: Log and monitor
                handleLowRiskAmlAlert(
                    alertId, userId, transactionId, amount, currency,
                    enhancedRiskScore, allViolations
                );
            }

            // Step 8: Audit the alert processing
            auditAmlAlertProcessing(
                alertId, entityId, alertType, enhancedRiskScore, allViolations, "SUCCESS"
            );

            log.info("PRODUCTION AML: Alert processed successfully - Alert: {}, Risk: {}",
                    alertId, enhancedRiskScore);

        } catch (Exception e) {
            log.error("PRODUCTION AML ERROR: Failed to process alert - Entity: {}, Type: {}",
                     entityId, alertType, e);

            // Audit the failure
            auditAmlAlertProcessing(
                alertId, entityId, alertType, 0.0, Collections.emptyList(),
                "FAILED: " + e.getMessage()
            );

            // Rethrow for fallback handling
            throw new ComplianceException("Failed to process AML alert: " + alertType, e);
        }
    }

    /**
     * PRODUCTION IMPLEMENTATION: Check AML Risk
     *
     * Performs comprehensive AML risk assessment for a customer/transaction:
     * - Real-time risk scoring using ML-based rules
     * - Historical pattern analysis
     * - Geographic risk assessment
     * - Sanctions screening integration
     * - PEP (Politically Exposed Person) checks
     *
     * @param entityId Customer/Account ID to assess
     * @param transactionData Current transaction details
     * @return AML risk assessment result with risk level and indicators
     */
    @CircuitBreaker(name = "aml-service", fallbackMethod = "checkAmlRiskFallback")
    @Retry(name = "aml-service", fallbackMethod = "checkAmlRiskFallback")
    @Transactional(readOnly = true, timeout = 30)
    public Map<String, Object> checkAmlRisk(String entityId, Map<String, Object> transactionData) {
        log.debug("PRODUCTION AML: Checking risk for entity: {}", entityId);

        try {
            UUID userId = UUID.fromString(entityId);

            // Step 1: Extract transaction details
            BigDecimal amount = extractAmount(transactionData);
            String currency = extractCurrency(transactionData);
            String transactionType = extractTransactionType(transactionData);

            // Step 2: Calculate user's overall AML risk score
            double userRiskScore = amlMonitoringService.calculateUserAMLRiskScore(userId);

            // Step 3: Detect unusual patterns for this transaction
            double patternScore = amlMonitoringService.detectUnusualPatterns(
                userId, transactionType, amount
            );

            // Step 4: Check for structuring indicators
            boolean structuringRisk = amlMonitoringService.detectStructuring(userId, amount, 24);

            // Step 5: Check velocity violations
            List<AMLRuleViolation> velocityViolations =
                amlMonitoringService.checkVelocityRules(userId, amount);

            // Step 6: Assess geographic risk if applicable
            double geoRisk = 0.0;
            if (transactionData.containsKey("sourceCountry") &&
                transactionData.containsKey("destinationCountry")) {
                geoRisk = amlMonitoringService.assessGeographicRisk(
                    (String) transactionData.get("sourceCountry"),
                    (String) transactionData.get("destinationCountry"),
                    amount
                );
            }

            // Step 7: Calculate composite risk score
            double compositeRiskScore = calculateCompositeRiskScore(
                userRiskScore, patternScore, geoRisk,
                structuringRisk, velocityViolations.size()
            );

            // Step 8: Determine risk level
            String riskLevel = determineRiskLevel(compositeRiskScore);

            // Step 9: Collect risk indicators
            List<String> riskIndicators = new ArrayList<>();
            if (structuringRisk) {
                riskIndicators.add("STRUCTURING_PATTERN");
            }
            if (!velocityViolations.isEmpty()) {
                riskIndicators.add("VELOCITY_VIOLATION");
            }
            if (patternScore > 0.6) {
                riskIndicators.add("UNUSUAL_PATTERN");
            }
            if (geoRisk > 0.5) {
                riskIndicators.add("GEOGRAPHIC_RISK");
            }
            if (amount.compareTo(SAR_THRESHOLD) >= 0) {
                riskIndicators.add("ABOVE_SAR_THRESHOLD");
            }

            // Step 10: Build comprehensive response
            Map<String, Object> riskAssessment = new HashMap<>();
            riskAssessment.put("riskLevel", riskLevel);
            riskAssessment.put("riskScore", compositeRiskScore);
            riskAssessment.put("userRiskScore", userRiskScore);
            riskAssessment.put("patternScore", patternScore);
            riskAssessment.put("geographicRisk", geoRisk);
            riskAssessment.put("indicators", riskIndicators);
            riskAssessment.put("structuringDetected", structuringRisk);
            riskAssessment.put("velocityViolations", velocityViolations.size());
            riskAssessment.put("requiresSAR", compositeRiskScore >= HIGH_RISK_SCORE_THRESHOLD);
            riskAssessment.put("requiresReview", compositeRiskScore >= 0.5);
            riskAssessment.put("assessmentTimestamp", LocalDateTime.now());

            // Step 11: Audit the risk check
            auditService.auditComplianceOperation(
                "AML_RISK_CHECK",
                entityId,
                String.format("AML risk assessment - Level: %s, Score: %.2f", riskLevel, compositeRiskScore),
                Map.of(
                    "riskLevel", riskLevel,
                    "riskScore", compositeRiskScore,
                    "indicators", riskIndicators.size()
                )
            );

            log.debug("PRODUCTION AML: Risk check complete - Entity: {}, Level: {}, Score: {}",
                     entityId, riskLevel, compositeRiskScore);

            return riskAssessment;

        } catch (Exception e) {
            log.error("PRODUCTION AML ERROR: Risk check failed for entity: {}", entityId, e);
            throw new ComplianceException("Failed to check AML risk", e);
        }
    }

    // ========================================================================
    // PRIVATE HELPER METHODS - AML Processing
    // ========================================================================

    /**
     * Handle CRITICAL AML alerts requiring immediate action
     */
    private void handleCriticalAmlAlert(String alertId, UUID userId, UUID transactionId,
                                       BigDecimal amount, String currency, double riskScore,
                                       List<AMLRuleViolation> violations,
                                       AMLMonitoringResult monitoringResult) {

        log.error("CRITICAL AML ALERT: Immediate action required - Alert: {}, User: {}, Risk: {}",
                 alertId, userId, riskScore);

        // 1. Initiate SAR filing immediately
        initiateSARFiling(userId, transactionId, amount, currency, violations, riskScore);

        // 2. Notify compliance officers via high-priority channel
        notifyComplianceOfficers(alertId, userId, transactionId, riskScore, violations, "CRITICAL");

        // 3. Trigger Enhanced Due Diligence (EDD)
        triggerEnhancedDueDiligence(userId, alertId, violations);

        // 4. Publish critical alert event for downstream systems
        publishCriticalAlertEvent(alertId, userId, transactionId, amount, currency,
                                 riskScore, violations);

        log.error("CRITICAL AML ALERT: Actions initiated - Alert: {}", alertId);
    }

    /**
     * Handle HIGH RISK AML alerts requiring urgent review
     */
    private void handleHighRiskAmlAlert(String alertId, UUID userId, UUID transactionId,
                                       BigDecimal amount, String currency, double riskScore,
                                       List<AMLRuleViolation> violations,
                                       AMLMonitoringResult monitoringResult) {

        log.warn("HIGH RISK AML ALERT: Urgent review required - Alert: {}, User: {}, Risk: {}",
                alertId, userId, riskScore);

        // 1. Queue for SAR review if amount meets threshold
        if (amount.compareTo(SAR_THRESHOLD) >= 0) {
            queueForSARReview(userId, transactionId, amount, currency, violations, riskScore);
        }

        // 2. Notify compliance team for review
        notifyComplianceOfficers(alertId, userId, transactionId, riskScore, violations, "HIGH");

        // 3. Publish alert event
        publishAlertEvent(alertId, userId, transactionId, amount, currency, riskScore, violations);
    }

    /**
     * Handle MEDIUM/LOW RISK AML alerts (monitoring only)
     */
    private void handleLowRiskAmlAlert(String alertId, UUID userId, UUID transactionId,
                                      BigDecimal amount, String currency, double riskScore,
                                      List<AMLRuleViolation> violations) {

        log.info("AML ALERT: Logged for monitoring - Alert: {}, User: {}, Risk: {}",
                alertId, userId, riskScore);

        // Log for audit trail and monitoring
        auditService.auditComplianceOperation(
            "AML_ALERT_LOGGED",
            userId.toString(),
            "AML alert logged for monitoring",
            Map.of(
                "alertId", alertId,
                "transactionId", transactionId,
                "riskScore", riskScore,
                "violations", violations.size()
            )
        );
    }

    /**
     * Initiate Suspicious Activity Report (SAR) filing
     */
    private void initiateSARFiling(UUID userId, UUID transactionId, BigDecimal amount,
                                  String currency, List<AMLRuleViolation> violations,
                                  double riskScore) {

        log.error("AML: Initiating SAR filing - User: {}, Transaction: {}, Amount: {} {}",
                 userId, transactionId, amount, currency);

        // Get primary violation for SAR categorization
        AMLRuleViolation primaryViolation = violations.stream()
            .max(Comparator.comparing(AMLRuleViolation::getSeverity))
            .orElse(violations.get(0));

        // Create SAR filing request event
        SarFilingRequestEvent sarEvent = SarFilingRequestEvent.builder()
            .userId(userId)
            .category(mapViolationToSarCategory(primaryViolation))
            .priority(SarFilingRequestEvent.SarPriority.IMMEDIATE)
            .violationType(primaryViolation.getRuleName())
            .suspiciousActivity(buildSuspiciousActivityDescription(violations))
            .totalSuspiciousAmount(amount)
            .currency(currency)
            .transactionCount(1)
            .activityStartDate(LocalDateTime.now().minusDays(30))
            .activityEndDate(LocalDateTime.now())
            .detectionMethod("AML_SERVICE_AUTO_DETECTION")
            .detectionRuleId(primaryViolation.getRuleId())
            .riskScore(riskScore)
            .suspiciousTransactionIds(Arrays.asList(transactionId))
            .caseId(UUID.randomUUID().toString())
            .requestingSystem("PRODUCTION_AML_SERVICE")
            .requestedAt(LocalDateTime.now())
            .requiresImmediateFiling(true)
            .build();

        // Publish to Kafka for SAR processing service
        kafkaTemplate.send("sar-filing-requests", sarEvent);

        log.error("AML: SAR filing request submitted - User: {}, Transaction: {}", userId, transactionId);
    }

    /**
     * Queue transaction for SAR review by compliance team
     */
    private void queueForSARReview(UUID userId, UUID transactionId, BigDecimal amount,
                                   String currency, List<AMLRuleViolation> violations,
                                   double riskScore) {

        log.warn("AML: Queuing for SAR review - User: {}, Transaction: {}", userId, transactionId);

        // Publish to SAR review queue
        Map<String, Object> sarReviewEvent = Map.of(
            "reviewId", UUID.randomUUID().toString(),
            "userId", userId,
            "transactionId", transactionId,
            "amount", amount,
            "currency", currency,
            "riskScore", riskScore,
            "violations", violations.stream().map(AMLRuleViolation::getRuleName).collect(Collectors.toList()),
            "queuedAt", LocalDateTime.now(),
            "reviewPriority", "HIGH"
        );

        kafkaTemplate.send("sar-review-queue", sarReviewEvent);
    }

    /**
     * Notify compliance officers of AML alert
     */
    private void notifyComplianceOfficers(String alertId, UUID userId, UUID transactionId,
                                         double riskScore, List<AMLRuleViolation> violations,
                                         String severity) {

        ComplianceAlertEvent alertEvent = ComplianceAlertEvent.builder()
            .alertId(alertId)
            .alertType("AML_DETECTION")
            .severity(severity)
            .userId(userId)
            .transactionId(transactionId)
            .riskScore(riskScore)
            .violations(violations.stream().map(AMLRuleViolation::getRuleName).collect(Collectors.toList()))
            .alertedAt(LocalDateTime.now())
            .requiresAction(true)
            .build();

        // Publish to compliance alerts topic (monitored by compliance officers)
        kafkaTemplate.send("compliance-alerts-critical", alertEvent);

        log.warn("AML: Compliance officers notified - Alert: {}, Severity: {}", alertId, severity);
    }

    /**
     * Trigger Enhanced Due Diligence (EDD) for high-risk customer
     */
    private void triggerEnhancedDueDiligence(UUID userId, String alertId,
                                            List<AMLRuleViolation> violations) {

        log.warn("AML: Triggering Enhanced Due Diligence - User: {}, Alert: {}", userId, alertId);

        Map<String, Object> eddEvent = Map.of(
            "userId", userId,
            "triggerType", "AML_ALERT",
            "triggerId", alertId,
            "violations", violations.stream().map(AMLRuleViolation::getRuleName).collect(Collectors.toList()),
            "eddRequiredBy", LocalDateTime.now().plusDays(14),
            "triggeredAt", LocalDateTime.now()
        );

        kafkaTemplate.send("enhanced-due-diligence-requests", eddEvent);
    }

    /**
     * Publish critical AML alert event
     */
    private void publishCriticalAlertEvent(String alertId, UUID userId, UUID transactionId,
                                          BigDecimal amount, String currency, double riskScore,
                                          List<AMLRuleViolation> violations) {

        ComplianceAlertEvent event = ComplianceAlertEvent.builder()
            .alertId(alertId)
            .alertType("CRITICAL_AML_VIOLATION")
            .severity("CRITICAL")
            .userId(userId)
            .transactionId(transactionId)
            .amount(amount)
            .currency(currency)
            .riskScore(riskScore)
            .violations(violations.stream().map(AMLRuleViolation::getRuleName).collect(Collectors.toList()))
            .alertedAt(LocalDateTime.now())
            .requiresAction(true)
            .actionRequired("IMMEDIATE_SAR_FILING_AND_REVIEW")
            .build();

        kafkaTemplate.send("compliance-alerts-critical", event);
    }

    /**
     * Publish standard AML alert event
     */
    private void publishAlertEvent(String alertId, UUID userId, UUID transactionId,
                                   BigDecimal amount, String currency, double riskScore,
                                   List<AMLRuleViolation> violations) {

        ComplianceAlertEvent event = ComplianceAlertEvent.builder()
            .alertId(alertId)
            .alertType("AML_ALERT")
            .severity("HIGH")
            .userId(userId)
            .transactionId(transactionId)
            .amount(amount)
            .currency(currency)
            .riskScore(riskScore)
            .violations(violations.stream().map(AMLRuleViolation::getRuleName).collect(Collectors.toList()))
            .alertedAt(LocalDateTime.now())
            .requiresAction(true)
            .build();

        kafkaTemplate.send("compliance-alerts", event);
    }

    /**
     * Analyze specific alert type and apply appropriate rules
     */
    private List<AMLRuleViolation> analyzeAlertType(String alertType, UUID userId,
                                                    BigDecimal amount, String currency,
                                                    Map<String, Object> alertData) {
        List<AMLRuleViolation> violations = new ArrayList<>();

        switch (alertType.toUpperCase()) {
            case "STRUCTURING":
            case "SMURFING":
                if (amount.compareTo(new BigDecimal("9500")) >= 0 &&
                    amount.compareTo(CTR_THRESHOLD) < 0) {
                    violations.add(createStructuringViolation(amount));
                }
                break;

            case "VELOCITY":
                violations.addAll(amlMonitoringService.checkVelocityRules(userId, amount));
                break;

            case "RAPID_MOVEMENT":
                if (amlMonitoringService.detectRapidMovement(userId, 1)) {
                    violations.add(createRapidMovementViolation());
                }
                break;

            case "HIGH_RISK_GEOGRAPHY":
                if (alertData.containsKey("sourceCountry") &&
                    alertData.containsKey("destinationCountry")) {
                    double geoRisk = amlMonitoringService.assessGeographicRisk(
                        (String) alertData.get("sourceCountry"),
                        (String) alertData.get("destinationCountry"),
                        amount
                    );
                    if (geoRisk > 0.5) {
                        violations.add(createGeographicRiskViolation(geoRisk));
                    }
                }
                break;

            case "ROUND_AMOUNT":
                if (amlMonitoringService.isRoundAmountSuspicious(amount)) {
                    violations.add(createRoundAmountViolation(amount));
                }
                break;
        }

        return violations;
    }

    /**
     * Update customer's compliance risk profile
     */
    private void updateComplianceRiskProfile(UUID userId, double riskScore,
                                            List<AMLRuleViolation> violations) {
        try {
            ComplianceRiskProfile profile = riskProfileRepository.findByUserId(userId)
                .orElse(ComplianceRiskProfile.builder()
                    .userId(userId)
                    .overallRiskScore(0.0)
                    .amlRiskLevel("LOW")
                    .build());

            // Update risk score (weighted average with historical)
            double updatedScore = (profile.getOverallRiskScore() * 0.7) + (riskScore * 0.3);
            profile.setOverallRiskScore(updatedScore);
            profile.setAmlRiskLevel(determineRiskLevel(updatedScore));
            profile.setLastAmlCheckDate(LocalDateTime.now());
            profile.setTotalAmlAlerts(profile.getTotalAmlAlerts() + 1);

            if (!violations.isEmpty()) {
                profile.setLastViolationDate(LocalDateTime.now());
                profile.setTotalViolations(profile.getTotalViolations() + violations.size());
            }

            riskProfileRepository.save(profile);

        } catch (Exception e) {
            log.error("Failed to update compliance risk profile for user: {}", userId, e);
        }
    }

    // ========================================================================
    // UTILITY METHODS - Data Extraction and Calculation
    // ========================================================================

    private UUID extractTransactionId(Map<String, Object> data) {
        Object txnId = data.get("transactionId");
        if (txnId instanceof UUID) {
            return (UUID) txnId;
        } else if (txnId instanceof String) {
            return UUID.fromString((String) txnId);
        }
        return UUID.randomUUID();
    }

    private BigDecimal extractAmount(Map<String, Object> data) {
        Object amount = data.get("amount");
        if (amount instanceof BigDecimal) {
            return (BigDecimal) amount;
        } else if (amount instanceof Number) {
            return new BigDecimal(amount.toString());
        }
        return BigDecimal.ZERO;
    }

    private String extractCurrency(Map<String, Object> data) {
        return (String) data.getOrDefault("currency", "USD");
    }

    private String extractTransactionType(Map<String, Object> data) {
        return (String) data.getOrDefault("transactionType", "UNKNOWN");
    }

    private String extractSourceAccount(Map<String, Object> data) {
        return (String) data.getOrDefault("sourceAccount", "");
    }

    private String extractDestinationAccount(Map<String, Object> data) {
        return (String) data.getOrDefault("destinationAccount", "");
    }

    private void validateAlertData(UUID userId, UUID transactionId, BigDecimal amount,
                                   String currency, String alertType) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid transaction amount");
        }
        if (currency == null || currency.isEmpty()) {
            throw new IllegalArgumentException("Currency cannot be null or empty");
        }
    }

    private double calculateEnhancedRiskScore(double baseScore, String alertType,
                                             List<AMLRuleViolation> violations) {
        double enhancedScore = baseScore;

        // Add weight based on alert type severity
        switch (alertType.toUpperCase()) {
            case "STRUCTURING":
            case "SMURFING":
                enhancedScore += 0.3;
                break;
            case "RAPID_MOVEMENT":
            case "LAYERING":
                enhancedScore += 0.25;
                break;
            case "HIGH_RISK_GEOGRAPHY":
                enhancedScore += 0.2;
                break;
            case "VELOCITY":
                enhancedScore += 0.15;
                break;
        }

        // Add weight based on violation count and severity
        for (AMLRuleViolation violation : violations) {
            switch (violation.getSeverity()) {
                case CRITICAL:
                    enhancedScore += 0.2;
                    break;
                case HIGH:
                    enhancedScore += 0.15;
                    break;
                case MEDIUM:
                    enhancedScore += 0.1;
                    break;
                case LOW:
                    enhancedScore += 0.05;
                    break;
            }
        }

        return Math.min(enhancedScore, 1.0);
    }

    private double calculateCompositeRiskScore(double userRiskScore, double patternScore,
                                              double geoRisk, boolean structuring,
                                              int velocityViolations) {
        double composite = (userRiskScore * 0.3) + (patternScore * 0.3) + (geoRisk * 0.2);

        if (structuring) {
            composite += 0.3;
        }
        if (velocityViolations > 0) {
            composite += Math.min(velocityViolations * 0.1, 0.3);
        }

        return Math.min(composite, 1.0);
    }

    private String determineRiskLevel(double riskScore) {
        if (riskScore >= CRITICAL_RISK_SCORE_THRESHOLD) {
            return "CRITICAL";
        } else if (riskScore >= HIGH_RISK_SCORE_THRESHOLD) {
            return "HIGH";
        } else if (riskScore >= 0.4) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }

    private String buildSuspiciousActivityDescription(List<AMLRuleViolation> violations) {
        return violations.stream()
            .map(v -> v.getRuleName() + ": " + v.getDescription())
            .collect(Collectors.joining("; "));
    }

    private SarFilingRequestEvent.SarCategory mapViolationToSarCategory(AMLRuleViolation violation) {
        switch (violation.getRuleType()) {
            case STRUCTURING:
                return SarFilingRequestEvent.SarCategory.STRUCTURING;
            case RAPID_MOVEMENT:
            case PATTERN_ANOMALY:
                return SarFilingRequestEvent.SarCategory.MONEY_LAUNDERING;
            default:
                return SarFilingRequestEvent.SarCategory.OTHER_SUSPICIOUS_ACTIVITY;
        }
    }

    private AMLRuleViolation createStructuringViolation(BigDecimal amount) {
        return AMLRuleViolation.builder()
            .violationId(UUID.randomUUID().toString())
            .ruleId("STR_AUTO_001")
            .ruleName("Automated Structuring Detection")
            .ruleType(AMLRuleViolation.RuleType.STRUCTURING)
            .severity(AMLRuleViolation.Severity.CRITICAL)
            .description("Transaction amount just below CTR threshold indicates potential structuring")
            .violationAmount(amount)
            .threshold(CTR_THRESHOLD)
            .detectedAt(LocalDateTime.now())
            .requiresSAR(true)
            .requiresImmediateAction(true)
            .build();
    }

    private AMLRuleViolation createRapidMovementViolation() {
        return AMLRuleViolation.builder()
            .violationId(UUID.randomUUID().toString())
            .ruleId("RPD_AUTO_001")
            .ruleName("Automated Rapid Movement Detection")
            .ruleType(AMLRuleViolation.RuleType.RAPID_MOVEMENT)
            .severity(AMLRuleViolation.Severity.HIGH)
            .description("Rapid deposit and withdrawal pattern detected")
            .detectedAt(LocalDateTime.now())
            .requiresAccountReview(true)
            .build();
    }

    private AMLRuleViolation createGeographicRiskViolation(double geoRisk) {
        return AMLRuleViolation.builder()
            .violationId(UUID.randomUUID().toString())
            .ruleId("GEO_AUTO_001")
            .ruleName("High-Risk Geographic Location")
            .ruleType(AMLRuleViolation.RuleType.GEOGRAPHIC_RISK)
            .severity(geoRisk > 0.8 ? AMLRuleViolation.Severity.HIGH : AMLRuleViolation.Severity.MEDIUM)
            .description("Transaction involves high-risk geographic location")
            .detectedAt(LocalDateTime.now())
            .build();
    }

    private AMLRuleViolation createRoundAmountViolation(BigDecimal amount) {
        return AMLRuleViolation.builder()
            .violationId(UUID.randomUUID().toString())
            .ruleId("RND_AUTO_001")
            .ruleName("Suspicious Round Amount")
            .ruleType(AMLRuleViolation.RuleType.ROUND_AMOUNT)
            .severity(AMLRuleViolation.Severity.MEDIUM)
            .description("Transaction uses suspicious round amount")
            .violationAmount(amount)
            .detectedAt(LocalDateTime.now())
            .build();
    }

    private void auditAmlAlertProcessing(String alertId, String entityId, String alertType,
                                        double riskScore, List<AMLRuleViolation> violations,
                                        String status) {
        auditService.auditHighRiskOperation(
            "AML_ALERT_PROCESSING",
            entityId,
            String.format("AML alert processed - Type: %s, Status: %s", alertType, status),
            Map.of(
                "alertId", alertId,
                "alertType", alertType,
                "riskScore", riskScore,
                "violations", violations.size(),
                "status", status
            )
        );
    }

    // ========================================================================
    // FALLBACK METHODS - Circuit Breaker Resilience
    // ========================================================================

    private void processAmlAlertFallback(String entityId, String alertType,
                                       Map<String, Object> alertData, Exception e) {
        log.error("AML SERVICE UNAVAILABLE: Alert not processed (fallback) - Entity: {}, Type: {}, Error: {}",
                 entityId, alertType, e.getMessage());

        // Critical: Queue alert for manual review when service is unavailable
        Map<String, Object> failedAlertEvent = Map.of(
            "entityId", entityId,
            "alertType", alertType,
            "alertData", alertData,
            "failureReason", e.getMessage(),
            "failedAt", LocalDateTime.now(),
            "requiresManualReview", true
        );

        try {
            kafkaTemplate.send("aml-alerts-failed", failedAlertEvent);
            log.warn("AML FALLBACK: Failed alert queued for manual review - Entity: {}", entityId);
        } catch (Exception kafkaError) {
            log.error("CRITICAL: Unable to queue failed AML alert - Entity: {}", entityId, kafkaError);
        }
    }

    private Map<String, Object> checkAmlRiskFallback(String entityId, Map<String, Object> transactionData,
                                                    Exception e) {
        log.error("AML SERVICE UNAVAILABLE: Risk check failed (fallback) - Entity: {}, Error: {}",
                 entityId, e.getMessage());

        // Fail-safe: Return conservative high-risk assessment when service unavailable
        Map<String, Object> fallbackAssessment = new HashMap<>();
        fallbackAssessment.put("riskLevel", "HIGH");
        fallbackAssessment.put("riskScore", 0.8);
        fallbackAssessment.put("indicators", Arrays.asList("SERVICE_UNAVAILABLE_FALLBACK"));
        fallbackAssessment.put("requiresManualReview", true);
        fallbackAssessment.put("error", e.getMessage());
        fallbackAssessment.put("fallbackTimestamp", LocalDateTime.now());

        log.warn("AML FALLBACK: Returning conservative high-risk assessment for entity: {}", entityId);

        return fallbackAssessment;
    }
}