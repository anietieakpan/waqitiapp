package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.merchant.service.MerchantRiskService;
import com.waqiti.merchant.service.MerchantAccountService;
import com.waqiti.merchant.service.MerchantComplianceService;
import com.waqiti.merchant.service.MerchantNotificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import jakarta.annotation.PostConstruct;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Critical Event Consumer: Merchant Risk Alerts
 * 
 * Handles real-time merchant risk monitoring and alert processing:
 * - High-risk merchant identification and flagging
 * - Chargeback ratio monitoring and threshold alerts
 * - Fraud pattern detection and merchant blocking
 * - Account health monitoring and intervention
 * - Processing volume anomaly detection
 * - Compliance violation alerts and escalation
 * - Merchant onboarding risk assessment alerts
 * 
 * BUSINESS IMPACT: Without this consumer, merchant risk alerts are published
 * but NOT processed, leading to:
 * - Undetected high-risk merchants causing losses (~$10M+ annually)
 * - Excessive chargeback ratios triggering card scheme penalties
 * - Fraudulent merchants operating undetected
 * - Regulatory compliance failures and fines
 * - Reputation damage from association with bad merchants
 * - Loss of payment processor relationships
 * 
 * This consumer enables:
 * - Real-time merchant risk monitoring and intervention
 * - Automated merchant account restrictions and blocking
 * - Chargeback prevention through early detection
 * - Compliance monitoring and regulatory reporting
 * - Merchant relationship management and optimization
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantRiskAlertsConsumer {

    private final MerchantRiskService merchantRiskService;
    private final MerchantAccountService merchantAccountService;
    private final MerchantComplianceService merchantComplianceService;
    private final MerchantNotificationService merchantNotificationService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    // Metrics
    private Counter merchantRiskAlertsProcessed;
    private Counter merchantRiskAlertsSuccessful;
    private Counter merchantRiskAlertsFailed;
    private Counter highRiskMerchantsIdentified;
    private Counter chargebackRatioViolations;
    private Counter fraudulentMerchantsBlocked;
    private Counter merchantAccountsSuspended;
    private Counter complianceViolationsDetected;
    private Counter processingLimitsImposed;
    private Counter merchantEscalationsTriggered;
    private Timer alertProcessingTime;
    private Counter emergencyMerchantBlocks;
    private Counter merchantRelationshipTerminations;

    @PostConstruct
    public void initializeMetrics() {
        merchantRiskAlertsProcessed = Counter.builder("waqiti.merchant.risk_alerts.processed.total")
            .description("Total merchant risk alerts processed")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        merchantRiskAlertsSuccessful = Counter.builder("waqiti.merchant.risk_alerts.successful")
            .description("Successful merchant risk alert processing")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        merchantRiskAlertsFailed = Counter.builder("waqiti.merchant.risk_alerts.failed")
            .description("Failed merchant risk alert processing")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        highRiskMerchantsIdentified = Counter.builder("waqiti.merchant.high_risk.identified")
            .description("High-risk merchants identified")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        chargebackRatioViolations = Counter.builder("waqiti.merchant.chargeback_ratio.violations")
            .description("Chargeback ratio violations detected")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        fraudulentMerchantsBlocked = Counter.builder("waqiti.merchant.fraudulent.blocked")
            .description("Fraudulent merchants blocked")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        merchantAccountsSuspended = Counter.builder("waqiti.merchant.accounts.suspended")
            .description("Merchant accounts suspended")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        complianceViolationsDetected = Counter.builder("waqiti.merchant.compliance_violations.detected")
            .description("Merchant compliance violations detected")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        processingLimitsImposed = Counter.builder("waqiti.merchant.processing_limits.imposed")
            .description("Processing limits imposed on merchants")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        merchantEscalationsTriggered = Counter.builder("waqiti.merchant.escalations.triggered")
            .description("Merchant risk escalations triggered")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        alertProcessingTime = Timer.builder("waqiti.merchant.risk_alert.processing.duration")
            .description("Time taken to process merchant risk alerts")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        emergencyMerchantBlocks = Counter.builder("waqiti.merchant.emergency_blocks")
            .description("Emergency merchant blocks executed")
            .tag("service", "merchant-service")
            .register(meterRegistry);

        merchantRelationshipTerminations = Counter.builder("waqiti.merchant.relationship_terminations")
            .description("Merchant relationships terminated")
            .tag("service", "merchant-service")
            .register(meterRegistry);
    }

    /**
     * Consumes merchant-risk-alerts with comprehensive risk processing
     * 
     * @param alertPayload The merchant risk alert data as Map
     * @param partition Kafka partition
     * @param offset Kafka offset
     * @param acknowledgment Kafka acknowledgment
     */
    @KafkaListener(
        topics = "merchant-risk-alerts",
        groupId = "merchant-service-risk-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        autoCreateTopics = "true",
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional(rollbackFor = Exception.class)
    public void handleMerchantRiskAlert(
            @Payload Map<String, Object> alertPayload,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String alertId = null;
        
        try {
            merchantRiskAlertsProcessed.increment();
            
            log.info("Processing merchant risk alert from partition: {}, offset: {}", partition, offset);
            
            // Extract key identifiers for logging
            alertId = (String) alertPayload.get("alertId");
            String riskType = (String) alertPayload.get("riskType");
            
            if (alertId == null || riskType == null) {
                throw new IllegalArgumentException("Missing required alert identifiers");
            }
            
            log.info("Processing merchant risk alert: {} - Type: {}", alertId, riskType);
            
            // Convert to structured merchant risk alert
            MerchantRiskAlert riskAlert = convertToMerchantRiskAlert(alertPayload);
            
            // Validate merchant risk alert data
            validateMerchantRiskAlert(riskAlert);
            
            // Capture business metrics
            captureBusinessMetrics(riskAlert);
            
            // Check alert severity and urgency
            checkAlertSeverityAndUrgency(riskAlert);
            
            // Process alert based on risk type in parallel operations
            CompletableFuture<Void> riskAssessment = performMerchantRiskAssessment(riskAlert);
            CompletableFuture<Void> accountReview = performMerchantAccountReview(riskAlert);
            CompletableFuture<Void> complianceCheck = performComplianceCheck(riskAlert);
            CompletableFuture<Void> notificationProcessing = processRiskNotifications(riskAlert);
            
            // Wait for parallel processing to complete
            CompletableFuture.allOf(
                riskAssessment, 
                accountReview, 
                complianceCheck, 
                notificationProcessing
            ).join();
            
            // Determine and execute risk mitigation actions
            executeMerchantRiskActions(riskAlert);
            
            // Update merchant risk profile and tracking
            updateMerchantRiskProfile(riskAlert);
            
            merchantRiskAlertsSuccessful.increment();
            log.info("Successfully processed merchant risk alert: {}", alertId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            merchantRiskAlertsFailed.increment();
            log.error("Failed to process merchant risk alert: {} - Error: {}", alertId, e.getMessage(), e);
            
            // Don't acknowledge - this will trigger retry mechanism
            throw new MerchantRiskAlertProcessingException(
                "Failed to process merchant risk alert: " + alertId, e);
                
        } finally {
            sample.stop(alertProcessingTime);
        }
    }

    /**
     * Converts alert payload to structured MerchantRiskAlert
     */
    private MerchantRiskAlert convertToMerchantRiskAlert(Map<String, Object> alertPayload) {
        try {
            // Extract alert data
            Map<String, Object> alertData = (Map<String, Object>) alertPayload.get("data");
            
            return MerchantRiskAlert.builder()
                .alertId((String) alertPayload.get("alertId"))
                .riskType((String) alertPayload.get("riskType"))
                .severity((String) alertPayload.get("severity"))
                .timestamp(LocalDateTime.parse(alertPayload.get("timestamp").toString()))
                .data(alertData)
                .merchantId(alertData != null ? (String) alertData.get("merchantId") : null)
                .accountId(alertData != null ? (String) alertData.get("accountId") : null)
                .riskScore(alertData != null && alertData.get("riskScore") != null ? 
                    Double.parseDouble(alertData.get("riskScore").toString()) : null)
                .chargebackRatio(alertData != null && alertData.get("chargebackRatio") != null ? 
                    Double.parseDouble(alertData.get("chargebackRatio").toString()) : null)
                .processingVolume(alertData != null && alertData.get("processingVolume") != null ? 
                    new BigDecimal(alertData.get("processingVolume").toString()) : null)
                .currency(alertData != null ? (String) alertData.get("currency") : "USD")
                .alertReason(alertData != null ? (String) alertData.get("alertReason") : null)
                .riskIndicators(alertData != null ? (Map<String, String>) alertData.get("riskIndicators") : null)
                .merchantCategory(alertData != null ? (String) alertData.get("merchantCategory") : null)
                .accountAge(alertData != null && alertData.get("accountAge") != null ? 
                    Integer.parseInt(alertData.get("accountAge").toString()) : null)
                .previousAlerts(alertData != null ? (Map<String, String>) alertData.get("previousAlerts") : null)
                .build();
                
        } catch (Exception e) {
            log.error("Failed to convert merchant risk alert payload", e);
            throw new IllegalArgumentException("Invalid merchant risk alert format", e);
        }
    }

    /**
     * Validates merchant risk alert data
     */
    private void validateMerchantRiskAlert(MerchantRiskAlert alert) {
        if (alert.getAlertId() == null || alert.getAlertId().trim().isEmpty()) {
            throw new IllegalArgumentException("Alert ID is required");
        }
        
        if (alert.getMerchantId() == null || alert.getMerchantId().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant ID is required");
        }
        
        if (alert.getRiskType() == null || alert.getRiskType().trim().isEmpty()) {
            throw new IllegalArgumentException("Risk type is required");
        }
        
        if (alert.getTimestamp() == null) {
            throw new IllegalArgumentException("Alert timestamp is required");
        }
        
        // Validate severity
        if (alert.getSeverity() != null && 
            !alert.getSeverity().matches("(?i)(LOW|MEDIUM|HIGH|CRITICAL|EMERGENCY)")) {
            throw new IllegalArgumentException("Invalid severity level");
        }
        
        // Validate risk score if present
        if (alert.getRiskScore() != null && 
            (alert.getRiskScore() < 0.0 || alert.getRiskScore() > 100.0)) {
            throw new IllegalArgumentException("Risk score must be between 0 and 100");
        }
        
        // Validate chargeback ratio if present
        if (alert.getChargebackRatio() != null && 
            (alert.getChargebackRatio() < 0.0 || alert.getChargebackRatio() > 100.0)) {
            throw new IllegalArgumentException("Chargeback ratio must be between 0 and 100");
        }
    }

    /**
     * Captures business metrics for monitoring and alerting
     */
    private void captureBusinessMetrics(MerchantRiskAlert alert) {
        // Track alerts by risk type
        switch (alert.getRiskType().toUpperCase()) {
            case "HIGH_CHARGEBACK_RATIO":
                chargebackRatioViolations.increment(
                    "severity", alert.getSeverity(),
                    "merchant_category", alert.getMerchantCategory()
                );
                break;
            case "FRAUD_PATTERN_DETECTED":
            case "SUSPICIOUS_ACTIVITY":
                fraudulentMerchantsBlocked.increment(
                    "severity", alert.getSeverity(),
                    "merchant_category", alert.getMerchantCategory()
                );
                break;
            case "HIGH_RISK_MERCHANT":
            case "RISK_SCORE_THRESHOLD":
                highRiskMerchantsIdentified.increment(
                    "risk_score_range", getRiskScoreRange(alert.getRiskScore()),
                    "merchant_category", alert.getMerchantCategory()
                );
                break;
            case "COMPLIANCE_VIOLATION":
                complianceViolationsDetected.increment(
                    "violation_type", extractViolationType(alert.getRiskIndicators()),
                    "severity", alert.getSeverity()
                );
                break;
        }
        
        // Track high-risk merchants
        if (alert.getRiskScore() != null && alert.getRiskScore() > 80.0) {
            highRiskMerchantsIdentified.increment(
                "alert_type", alert.getRiskType(),
                "severity", alert.getSeverity()
            );
        }
        
        // Track emergency alerts
        if ("EMERGENCY".equals(alert.getSeverity())) {
            emergencyMerchantBlocks.increment(
                "risk_type", alert.getRiskType(),
                "merchant_category", alert.getMerchantCategory()
            );
        }
    }

    /**
     * Checks alert severity and triggers immediate actions if needed
     */
    private void checkAlertSeverityAndUrgency(MerchantRiskAlert alert) {
        try {
            if ("EMERGENCY".equals(alert.getSeverity())) {
                // Emergency block - immediate action required
                log.error("EMERGENCY merchant risk alert: {} - Merchant: {} - Type: {}", 
                    alert.getAlertId(), alert.getMerchantId(), alert.getRiskType());
                
                // Immediate emergency block
                merchantAccountService.emergencyBlockMerchant(
                    alert.getMerchantId(), 
                    alert.getAlertReason(),
                    alert.getAlertId()
                );
                
                emergencyMerchantBlocks.increment();
                
                // Immediate executive notification
                merchantNotificationService.sendEmergencyExecutiveAlert(
                    alert.getMerchantId(),
                    alert.getRiskType(),
                    alert.getAlertReason(),
                    alert.getRiskScore()
                );
                
            } else if ("CRITICAL".equals(alert.getSeverity())) {
                // Critical alert - suspend processing within 1 hour
                log.warn("CRITICAL merchant risk alert: {} - Merchant: {} - Type: {}", 
                    alert.getAlertId(), alert.getMerchantId(), alert.getRiskType());
                
                // Schedule processing suspension
                merchantAccountService.scheduleProcessingSuspension(
                    alert.getMerchantId(),
                    alert.getAlertReason(),
                    LocalDateTime.now().plusHours(1)
                );
                
                // Executive notification
                merchantNotificationService.sendCriticalAlert(
                    alert.getMerchantId(),
                    alert.getRiskType(),
                    alert.getAlertReason()
                );
            }
            
        } catch (Exception e) {
            log.error("Failed to check alert severity and urgency", e);
        }
    }

    /**
     * Performs comprehensive merchant risk assessment
     */
    private CompletableFuture<Void> performMerchantRiskAssessment(MerchantRiskAlert alert) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Performing merchant risk assessment for: {}", alert.getMerchantId());
                
                // Update merchant risk score
                merchantRiskService.updateMerchantRiskScore(
                    alert.getMerchantId(),
                    alert.getRiskScore(),
                    alert.getRiskType(),
                    alert.getRiskIndicators(),
                    alert.getTimestamp()
                );
                
                // Analyze chargeback patterns
                if (alert.getChargebackRatio() != null) {
                    merchantRiskService.analyzeChargebackPatterns(
                        alert.getMerchantId(),
                        alert.getChargebackRatio(),
                        alert.getProcessingVolume(),
                        alert.getTimestamp()
                    );
                }
                
                // Assess fraud risk patterns
                if (alert.getRiskType().contains("FRAUD")) {
                    merchantRiskService.assessFraudRiskPatterns(
                        alert.getMerchantId(),
                        alert.getRiskIndicators(),
                        alert.getAlertReason(),
                        alert.getTimestamp()
                    );
                }
                
                // Update merchant risk profile
                merchantRiskService.updateMerchantRiskProfile(
                    alert.getMerchantId(),
                    alert.getRiskType(),
                    alert.getSeverity(),
                    alert.getTimestamp()
                );
                
                log.info("Merchant risk assessment completed for: {}", alert.getMerchantId());
                
            } catch (Exception e) {
                log.error("Failed to perform merchant risk assessment for: {}", 
                    alert.getMerchantId(), e);
                throw new MerchantRiskAlertProcessingException("Risk assessment failed", e);
            }
        });
    }

    /**
     * Performs merchant account review and restrictions
     */
    private CompletableFuture<Void> performMerchantAccountReview(MerchantRiskAlert alert) {
        return CompletableFuture.runAsync(() -> {
            try {
                // Only perform review for non-emergency alerts (emergency already handled)
                if (!"EMERGENCY".equals(alert.getSeverity())) {
                    log.debug("Performing merchant account review for: {}", alert.getMerchantId());
                    
                    // Review account status and history
                    merchantAccountService.reviewMerchantAccount(
                        alert.getMerchantId(),
                        alert.getAccountId(),
                        alert.getRiskType(),
                        alert.getRiskScore(),
                        alert.getTimestamp()
                    );
                    
                    // Determine account restrictions based on risk
                    String restrictionAction = determineAccountRestrictions(alert);
                    
                    if (restrictionAction != null && !restrictionAction.equals("NO_ACTION")) {
                        merchantAccountService.applyAccountRestrictions(
                            alert.getMerchantId(),
                            restrictionAction,
                            alert.getAlertReason(),
                            alert.getTimestamp()
                        );
                        
                        // Track restriction metrics
                        if (restrictionAction.contains("SUSPEND")) {
                            merchantAccountsSuspended.increment();
                        } else if (restrictionAction.contains("LIMIT")) {
                            processingLimitsImposed.increment();
                        }
                    }
                    
                    // Check if merchant requires enhanced monitoring
                    if (requiresEnhancedMonitoring(alert)) {
                        merchantAccountService.enableEnhancedMonitoring(
                            alert.getMerchantId(),
                            alert.getRiskType(),
                            alert.getTimestamp().plusDays(90)
                        );
                    }
                    
                    log.info("Merchant account review completed for: {}", alert.getMerchantId());
                }
                
            } catch (Exception e) {
                log.error("Failed to perform merchant account review for: {}", 
                    alert.getMerchantId(), e);
                // Don't throw exception for account review failures - log and continue
            }
        });
    }

    /**
     * Performs compliance check and regulatory review
     */
    private CompletableFuture<Void> performComplianceCheck(MerchantRiskAlert alert) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Performing compliance check for merchant: {}", alert.getMerchantId());
                
                // Check regulatory compliance requirements
                merchantComplianceService.checkRegulatoryCompliance(
                    alert.getMerchantId(),
                    alert.getRiskType(),
                    alert.getMerchantCategory(),
                    alert.getProcessingVolume(),
                    alert.getTimestamp()
                );
                
                // Verify KYB (Know Your Business) status
                merchantComplianceService.verifyKYBStatus(
                    alert.getMerchantId(),
                    alert.getRiskScore(),
                    alert.getTimestamp()
                );
                
                // Check PCI compliance status
                merchantComplianceService.checkPCIComplianceStatus(
                    alert.getMerchantId(),
                    alert.getRiskType(),
                    alert.getTimestamp()
                );
                
                // Assess AML/BSA compliance
                if (alert.getProcessingVolume() != null && 
                    alert.getProcessingVolume().compareTo(new BigDecimal("10000")) > 0) {
                    
                    merchantComplianceService.assessAMLBSACompliance(
                        alert.getMerchantId(),
                        alert.getProcessingVolume(),
                        alert.getCurrency(),
                        alert.getRiskIndicators()
                    );
                }
                
                log.info("Compliance check completed for merchant: {}", alert.getMerchantId());
                
            } catch (Exception e) {
                log.error("Failed to perform compliance check for merchant: {}", 
                    alert.getMerchantId(), e);
                // Don't throw exception for compliance check failures - log and continue
            }
        });
    }

    /**
     * Processes risk notifications
     */
    private CompletableFuture<Void> processRiskNotifications(MerchantRiskAlert alert) {
        return CompletableFuture.runAsync(() -> {
            try {
                log.debug("Processing risk notifications for merchant: {}", alert.getMerchantId());
                
                // Notify merchant of risk alert
                merchantNotificationService.notifyMerchantOfRiskAlert(
                    alert.getMerchantId(),
                    alert.getRiskType(),
                    alert.getSeverity(),
                    alert.getAlertReason()
                );
                
                // Notify risk management team
                merchantNotificationService.notifyRiskManagementTeam(
                    alert.getMerchantId(),
                    alert.getRiskType(),
                    alert.getSeverity(),
                    alert.getRiskScore(),
                    alert.getChargebackRatio()
                );
                
                // Notify account managers for high/critical alerts
                if ("HIGH".equals(alert.getSeverity()) || "CRITICAL".equals(alert.getSeverity())) {
                    merchantNotificationService.notifyAccountManagers(
                        alert.getMerchantId(),
                        alert.getRiskType(),
                        alert.getSeverity(),
                        alert.getProcessingVolume()
                    );
                }
                
                // Notify compliance team for compliance-related alerts
                if (alert.getRiskType().contains("COMPLIANCE") || 
                    alert.getRiskType().contains("REGULATORY")) {
                    merchantNotificationService.notifyComplianceTeam(
                        alert.getMerchantId(),
                        alert.getRiskType(),
                        alert.getRiskIndicators()
                    );
                }
                
                log.info("Risk notifications processed for merchant: {}", alert.getMerchantId());
                
            } catch (Exception e) {
                log.error("Failed to process risk notifications for merchant: {}", 
                    alert.getMerchantId(), e);
                // Don't throw exception for notification failures - log and continue
            }
        });
    }

    /**
     * Executes merchant risk mitigation actions
     */
    private void executeMerchantRiskActions(MerchantRiskAlert alert) {
        try {
            log.debug("Executing merchant risk actions for: {}", alert.getMerchantId());
            
            // Determine risk mitigation actions
            String riskAction = merchantRiskService.determineRiskMitigationAction(
                alert.getRiskType(),
                alert.getSeverity(),
                alert.getRiskScore(),
                alert.getChargebackRatio(),
                alert.getPreviousAlerts()
            );
            
            // Execute determined actions
            merchantRiskService.executeRiskMitigationAction(
                alert.getMerchantId(),
                riskAction,
                alert.getAlertReason(),
                alert.getTimestamp()
            );
            
            // Handle specific action types
            switch (riskAction) {
                case "TERMINATE_RELATIONSHIP":
                    merchantAccountService.terminateMerchantRelationship(
                        alert.getMerchantId(),
                        alert.getAlertReason(),
                        alert.getTimestamp()
                    );
                    merchantRelationshipTerminations.increment();
                    break;
                    
                case "IMPOSE_ROLLING_RESERVE":
                    merchantAccountService.imposeRollingReserve(
                        alert.getMerchantId(),
                        calculateReservePercentage(alert.getRiskScore()),
                        alert.getAlertReason()
                    );
                    break;
                    
                case "ESCALATE_TO_UNDERWRITING":
                    merchantRiskService.escalateToUnderwriting(
                        alert.getMerchantId(),
                        alert.getRiskType(),
                        alert.getSeverity(),
                        alert.getRiskScore()
                    );
                    merchantEscalationsTriggered.increment();
                    break;
            }
            
            log.info("Merchant risk actions executed for: {} - Action: {}", 
                alert.getMerchantId(), riskAction);
            
        } catch (Exception e) {
            log.error("Failed to execute merchant risk actions for: {}", alert.getMerchantId(), e);
        }
    }

    /**
     * Updates merchant risk profile and tracking
     */
    private void updateMerchantRiskProfile(MerchantRiskAlert alert) {
        try {
            log.debug("Updating merchant risk profile for: {}", alert.getMerchantId());
            
            // Update risk profile
            merchantRiskService.updateRiskProfile(
                alert.getMerchantId(),
                alert.getRiskType(),
                alert.getSeverity(),
                alert.getRiskScore(),
                alert.getChargebackRatio(),
                alert.getTimestamp()
            );
            
            // Update alert history
            merchantRiskService.updateAlertHistory(
                alert.getMerchantId(),
                alert.getAlertId(),
                alert.getRiskType(),
                alert.getSeverity(),
                alert.getTimestamp()
            );
            
            // Update merchant monitoring configuration
            merchantRiskService.updateMonitoringConfiguration(
                alert.getMerchantId(),
                alert.getRiskType(),
                alert.getSeverity()
            );
            
            log.debug("Merchant risk profile updated for: {}", alert.getMerchantId());
            
        } catch (Exception e) {
            log.error("Failed to update merchant risk profile: {}", alert.getMerchantId(), e);
        }
    }

    // Helper methods

    private String determineAccountRestrictions(MerchantRiskAlert alert) {
        // Emergency and critical already handled
        if ("EMERGENCY".equals(alert.getSeverity()) || "CRITICAL".equals(alert.getSeverity())) {
            return "ALREADY_HANDLED";
        }
        
        // High severity - impose processing limits
        if ("HIGH".equals(alert.getSeverity())) {
            if (alert.getChargebackRatio() != null && alert.getChargebackRatio() > 2.0) {
                return "SUSPEND_PROCESSING";
            } else if (alert.getRiskScore() != null && alert.getRiskScore() > 75.0) {
                return "LIMIT_PROCESSING_VOLUME";
            }
            return "ENHANCED_MONITORING";
        }
        
        // Medium severity - enhanced monitoring
        if ("MEDIUM".equals(alert.getSeverity())) {
            return "ENHANCED_MONITORING";
        }
        
        return "NO_ACTION";
    }

    private boolean requiresEnhancedMonitoring(MerchantRiskAlert alert) {
        return alert.getRiskScore() != null && alert.getRiskScore() > 60.0 ||
               alert.getChargebackRatio() != null && alert.getChargebackRatio() > 1.0 ||
               "HIGH".equals(alert.getSeverity()) ||
               (alert.getPreviousAlerts() != null && alert.getPreviousAlerts().size() > 2);
    }

    private double calculateReservePercentage(Double riskScore) {
        if (riskScore == null) return 10.0;
        
        if (riskScore > 90.0) return 25.0;
        if (riskScore > 80.0) return 20.0;
        if (riskScore > 70.0) return 15.0;
        return 10.0;
    }

    private String getRiskScoreRange(Double riskScore) {
        if (riskScore == null) return "UNKNOWN";
        if (riskScore >= 80.0) return "VERY_HIGH";
        if (riskScore >= 60.0) return "HIGH";
        if (riskScore >= 40.0) return "MEDIUM";
        return "LOW";
    }

    private String extractViolationType(Map<String, String> riskIndicators) {
        if (riskIndicators == null) return "UNKNOWN";
        if (riskIndicators.containsKey("PCI")) return "PCI_COMPLIANCE";
        if (riskIndicators.containsKey("AML")) return "AML_COMPLIANCE";
        if (riskIndicators.containsKey("KYB")) return "KYB_COMPLIANCE";
        return "OTHER";
    }

    /**
     * Merchant risk alert data structure
     */
    @lombok.Data
    @lombok.Builder
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    private static class MerchantRiskAlert {
        private String alertId;
        private String riskType;
        private String severity;
        private LocalDateTime timestamp;
        private Map<String, Object> data;
        private String merchantId;
        private String accountId;
        private Double riskScore;
        private Double chargebackRatio;
        private BigDecimal processingVolume;
        private String currency;
        private String alertReason;
        private Map<String, String> riskIndicators;
        private String merchantCategory;
        private Integer accountAge;
        private Map<String, String> previousAlerts;
    }

    /**
     * Custom exception for merchant risk alert processing
     */
    public static class MerchantRiskAlertProcessingException extends RuntimeException {
        public MerchantRiskAlertProcessingException(String message) {
            super(message);
        }
        
        public MerchantRiskAlertProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}