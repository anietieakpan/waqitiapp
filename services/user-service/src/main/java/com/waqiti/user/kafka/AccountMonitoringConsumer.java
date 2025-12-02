package com.waqiti.user.kafka;

import com.waqiti.user.event.AccountMonitoringEvent;
import com.waqiti.user.service.UserService;
import com.waqiti.user.service.AccountMonitoringService;
import com.waqiti.user.service.FraudDetectionService;
import com.waqiti.user.service.RiskAssessmentService;
import com.waqiti.user.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Production-grade Kafka consumer for account monitoring events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountMonitoringConsumer {

    private final UserService userService;
    private final AccountMonitoringService monitoringService;
    private final FraudDetectionService fraudDetectionService;
    private final RiskAssessmentService riskAssessmentService;
    private final NotificationService notificationService;

    @KafkaListener(topics = "account-monitoring", groupId = "account-monitoring-processor")
    public void processAccountMonitoring(@Payload AccountMonitoringEvent event,
                                       @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                       @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                                       @Header(KafkaHeaders.OFFSET) long offset,
                                       Acknowledgment acknowledgment) {
        try {
            log.info("Processing account monitoring for user: {} type: {} severity: {} score: {}", 
                    event.getUserId(), event.getMonitoringType(), 
                    event.getSeverity(), event.getRiskScore());
            
            // Validate event
            validateMonitoringEvent(event);
            
            // Process based on monitoring type
            switch (event.getMonitoringType()) {
                case "TRANSACTION_ANOMALY" -> handleTransactionAnomaly(event);
                case "LOGIN_ANOMALY" -> handleLoginAnomaly(event);
                case "VELOCITY_CHECK" -> handleVelocityCheck(event);
                case "PATTERN_DEVIATION" -> handlePatternDeviation(event);
                case "RISK_THRESHOLD" -> handleRiskThreshold(event);
                case "COMPLIANCE_ALERT" -> handleComplianceAlert(event);
                case "FRAUD_DETECTION" -> handleFraudDetection(event);
                case "ACCOUNT_TAKEOVER" -> handleAccountTakeover(event);
                case "SUSPICIOUS_ACTIVITY" -> handleSuspiciousActivity(event);
                case "REGULATORY_FLAG" -> handleRegulatoryFlag(event);
                default -> handleGenericMonitoring(event);
            }
            
            // Update risk score
            updateRiskScore(event);
            
            // Check if immediate action needed
            if (requiresImmediateAction(event)) {
                takeImmediateAction(event);
            }
            
            // Create monitoring record
            monitoringService.createMonitoringRecord(
                event.getUserId(),
                event.getAccountId(),
                event.getMonitoringType(),
                event.getDetectedAt(),
                event.getMonitoringDetails(),
                event.getRiskScore(),
                event.getSeverity()
            );
            
            // Send alerts if needed
            if (event.isAlertRequired()) {
                sendMonitoringAlerts(event);
            }
            
            // Schedule follow-up monitoring
            if (event.isRequiresFollowUp()) {
                scheduleFollowUpMonitoring(event);
            }
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            log.info("Successfully processed account monitoring for user: {}", event.getUserId());
            
        } catch (Exception e) {
            log.error("Failed to process account monitoring for user {}: {}", 
                    event.getUserId(), e.getMessage(), e);
            throw new RuntimeException("Account monitoring processing failed", e);
        }
    }

    private void validateMonitoringEvent(AccountMonitoringEvent event) {
        if (event.getUserId() == null || event.getUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("User ID is required for account monitoring");
        }
        
        if (event.getMonitoringType() == null || event.getMonitoringType().trim().isEmpty()) {
            throw new IllegalArgumentException("Monitoring type is required");
        }
        
        if (event.getSeverity() == null || event.getSeverity().trim().isEmpty()) {
            throw new IllegalArgumentException("Severity is required");
        }
    }

    private void handleTransactionAnomaly(AccountMonitoringEvent event) {
        // Analyze transaction patterns
        Map<String, Object> anomalyDetails = event.getAnomalyDetails();
        
        // Check transaction velocity
        if (anomalyDetails.containsKey("transactionCount")) {
            int count = (Integer) anomalyDetails.get("transactionCount");
            BigDecimal amount = new BigDecimal(anomalyDetails.get("totalAmount").toString());
            
            fraudDetectionService.analyzeTransactionVelocity(
                event.getUserId(),
                count,
                amount,
                event.getTimeWindow()
            );
        }
        
        // Check unusual merchants
        if (anomalyDetails.containsKey("unusualMerchants")) {
            List<String> merchants = (List<String>) anomalyDetails.get("unusualMerchants");
            fraudDetectionService.checkUnusualMerchants(
                event.getUserId(),
                merchants
            );
        }
        
        // Check geographic anomalies
        if (anomalyDetails.containsKey("locations")) {
            List<String> locations = (List<String>) anomalyDetails.get("locations");
            fraudDetectionService.checkGeographicAnomalies(
                event.getUserId(),
                locations,
                event.getTimeWindow()
            );
        }
        
        // Apply transaction restrictions if high risk
        if (event.getRiskScore() > 80) {
            monitoringService.applyTransactionRestrictions(
                event.getUserId(),
                event.getAccountId(),
                "HIGH_RISK_ANOMALY"
            );
        }
    }

    private void handleLoginAnomaly(AccountMonitoringEvent event) {
        Map<String, Object> loginDetails = event.getLoginDetails();
        
        // Check for impossible travel
        if (loginDetails.containsKey("impossibleTravel")) {
            Map<String, Object> travelData = (Map<String, Object>) loginDetails.get("impossibleTravel");
            
            fraudDetectionService.detectImpossibleTravel(
                event.getUserId(),
                (String) travelData.get("previousLocation"),
                (String) travelData.get("currentLocation"),
                (Long) travelData.get("timeDifference")
            );
        }
        
        // Check for new device
        if (loginDetails.containsKey("newDevice")) {
            monitoringService.handleNewDeviceLogin(
                event.getUserId(),
                (String) loginDetails.get("deviceId"),
                (String) loginDetails.get("deviceType"),
                (String) loginDetails.get("location")
            );
        }
        
        // Check for brute force attempts
        if (loginDetails.containsKey("failedAttempts")) {
            int attempts = (Integer) loginDetails.get("failedAttempts");
            if (attempts > 5) {
                monitoringService.handleBruteForceAttempt(
                    event.getUserId(),
                    attempts,
                    (String) loginDetails.get("ipAddress")
                );
            }
        }
        
        // Force re-authentication if critical
        if ("CRITICAL".equals(event.getSeverity())) {
            userService.forceReauthentication(event.getUserId());
        }
    }

    private void handleVelocityCheck(AccountMonitoringEvent event) {
        Map<String, Object> velocityData = event.getVelocityData();
        
        // Check transaction velocity
        BigDecimal transactionVelocity = new BigDecimal(velocityData.get("transactionVelocity").toString());
        BigDecimal velocityLimit = new BigDecimal(velocityData.get("velocityLimit").toString());
        
        if (transactionVelocity.compareTo(velocityLimit) > 0) {
            // Apply velocity controls
            monitoringService.applyVelocityControls(
                event.getUserId(),
                event.getAccountId(),
                transactionVelocity,
                velocityLimit
            );
            
            // Temporary limit reduction
            userService.reduceTransactionLimits(
                event.getUserId(),
                "VELOCITY_EXCEEDED",
                50 // Reduce by 50%
            );
        }
        
        // Check withdrawal velocity
        if (velocityData.containsKey("withdrawalVelocity")) {
            BigDecimal withdrawalVelocity = new BigDecimal(velocityData.get("withdrawalVelocity").toString());
            monitoringService.checkWithdrawalVelocity(
                event.getUserId(),
                withdrawalVelocity
            );
        }
    }

    private void handlePatternDeviation(AccountMonitoringEvent event) {
        Map<String, Object> deviationData = event.getDeviationData();
        
        // Analyze spending pattern changes
        if (deviationData.containsKey("spendingDeviation")) {
            Double deviation = (Double) deviationData.get("spendingDeviation");
            if (deviation > 200) { // 200% deviation
                fraudDetectionService.analyzeSpendingDeviation(
                    event.getUserId(),
                    deviation,
                    event.getHistoricalPattern(),
                    event.getCurrentPattern()
                );
            }
        }
        
        // Check behavioral changes
        if (deviationData.containsKey("behavioralChanges")) {
            List<String> changes = (List<String>) deviationData.get("behavioralChanges");
            riskAssessmentService.analyzeBehavioralChanges(
                event.getUserId(),
                changes
            );
        }
        
        // Update user profile if patterns persist
        if (event.isPatternPersistent()) {
            monitoringService.updateUserBehaviorProfile(
                event.getUserId(),
                event.getCurrentPattern()
            );
        }
    }

    private void handleRiskThreshold(AccountMonitoringEvent event) {
        int riskScore = event.getRiskScore();
        String previousRiskLevel = event.getPreviousRiskLevel();
        String currentRiskLevel = event.getCurrentRiskLevel();
        
        // Apply risk-based controls
        if (riskScore > 90) {
            // Critical risk - immediate suspension
            userService.suspendAccount(
                event.getUserId(),
                "CRITICAL_RISK_THRESHOLD",
                event.getRiskFactors()
            );
        } else if (riskScore > 70) {
            // High risk - apply restrictions
            monitoringService.applyHighRiskRestrictions(
                event.getUserId(),
                event.getAccountId(),
                riskScore
            );
        } else if (riskScore > 50) {
            // Medium risk - enhanced monitoring
            monitoringService.enableEnhancedMonitoring(
                event.getUserId(),
                "MEDIUM_RISK"
            );
        }
        
        // Log risk level change
        if (!previousRiskLevel.equals(currentRiskLevel)) {
            riskAssessmentService.logRiskLevelChange(
                event.getUserId(),
                previousRiskLevel,
                currentRiskLevel,
                event.getRiskFactors()
            );
        }
    }

    private void handleComplianceAlert(AccountMonitoringEvent event) {
        Map<String, Object> complianceData = event.getComplianceData();
        
        // Check sanctions screening
        if (complianceData.containsKey("sanctionsHit")) {
            monitoringService.handleSanctionsHit(
                event.getUserId(),
                (String) complianceData.get("sanctionsList"),
                (Double) complianceData.get("matchScore")
            );
        }
        
        // Check PEP status
        if (complianceData.containsKey("pepStatus")) {
            monitoringService.handlePepAlert(
                event.getUserId(),
                (Boolean) complianceData.get("pepStatus"),
                (String) complianceData.get("pepDetails")
            );
        }
        
        // File SAR if required
        if (complianceData.containsKey("sarRequired") && 
            (Boolean) complianceData.get("sarRequired")) {
            monitoringService.fileSuspiciousActivityReport(
                event.getUserId(),
                event.getAccountId(),
                event.getComplianceReason(),
                event.getComplianceDetails()
            );
        }
    }

    private void handleFraudDetection(AccountMonitoringEvent event) {
        Map<String, Object> fraudData = event.getFraudData();
        
        // Get fraud confidence score
        Double fraudConfidence = (Double) fraudData.get("fraudConfidence");
        
        if (fraudConfidence > 0.9) {
            // Confirmed fraud - immediate action
            fraudDetectionService.handleConfirmedFraud(
                event.getUserId(),
                event.getAccountId(),
                event.getFraudType(),
                fraudData
            );
            
            // Block account
            userService.blockAccount(
                event.getUserId(),
                "FRAUD_DETECTED",
                event.getFraudDetails()
            );
        } else if (fraudConfidence > 0.7) {
            // Suspected fraud - review required
            fraudDetectionService.flagForReview(
                event.getUserId(),
                event.getAccountId(),
                fraudConfidence,
                fraudData
            );
            
            // Apply temporary restrictions
            monitoringService.applyFraudRestrictions(
                event.getUserId(),
                "SUSPECTED_FRAUD"
            );
        }
        
        // Update fraud score
        fraudDetectionService.updateFraudScore(
            event.getUserId(),
            fraudConfidence,
            event.getFraudIndicators()
        );
    }

    private void handleAccountTakeover(AccountMonitoringEvent event) {
        // Immediate security response
        userService.initiateAccountTakeoverProtocol(
            event.getUserId(),
            event.getTakeoverIndicators()
        );
        
        // Lock account
        userService.lockAccount(
            event.getUserId(),
            "ACCOUNT_TAKEOVER_DETECTED"
        );
        
        // Revoke all tokens
        userService.revokeAllTokens(event.getUserId());
        
        // Force password reset
        userService.forcePasswordReset(
            event.getUserId(),
            "ACCOUNT_TAKEOVER"
        );
        
        // Send security alerts
        notificationService.sendAccountTakeoverAlert(
            event.getUserId(),
            event.getTakeoverDetails(),
            event.getDetectedAt()
        );
        
        // Create security incident
        monitoringService.createSecurityIncident(
            event.getUserId(),
            "ACCOUNT_TAKEOVER",
            event.getTakeoverIndicators(),
            event.getSeverity()
        );
    }

    private void handleSuspiciousActivity(AccountMonitoringEvent event) {
        // Log suspicious activity
        monitoringService.logSuspiciousActivity(
            event.getUserId(),
            event.getActivityType(),
            event.getActivityDetails(),
            event.getRiskScore()
        );
        
        // Increase monitoring frequency
        monitoringService.increaseMonitoringFrequency(
            event.getUserId(),
            event.getSeverity()
        );
        
        // Apply graduated response
        if (event.getSuspiciousCount() > 5) {
            monitoringService.applyGraduatedResponse(
                event.getUserId(),
                event.getSuspiciousCount(),
                event.getSeverity()
            );
        }
    }

    private void handleRegulatoryFlag(AccountMonitoringEvent event) {
        // Report to regulatory authority
        monitoringService.fileRegulatoryReport(
            event.getUserId(),
            event.getAccountId(),
            event.getRegulatoryType(),
            event.getRegulatoryDetails()
        );
        
        // Apply regulatory holds
        if (event.isRegulatoryHoldRequired()) {
            monitoringService.applyRegulatoryHold(
                event.getUserId(),
                event.getAccountId(),
                event.getHoldDuration()
            );
        }
        
        // Update compliance status
        userService.updateComplianceStatus(
            event.getUserId(),
            "REGULATORY_REVIEW",
            event.getRegulatoryReference()
        );
    }

    private void handleGenericMonitoring(AccountMonitoringEvent event) {
        // Log monitoring event
        monitoringService.logMonitoringEvent(
            event.getUserId(),
            event.getMonitoringType(),
            event.getMonitoringDetails(),
            event.getSeverity()
        );
        
        // Update monitoring statistics
        monitoringService.updateMonitoringStats(
            event.getUserId(),
            event.getMonitoringType(),
            event.getDetectedAt()
        );
    }

    private void updateRiskScore(AccountMonitoringEvent event) {
        // Calculate new risk score
        int newRiskScore = riskAssessmentService.calculateRiskScore(
            event.getUserId(),
            event.getMonitoringType(),
            event.getRiskFactors(),
            event.getSeverity()
        );
        
        // Update user risk profile
        riskAssessmentService.updateRiskProfile(
            event.getUserId(),
            newRiskScore,
            event.getRiskFactors()
        );
        
        // Track risk score changes
        if (Math.abs(newRiskScore - event.getRiskScore()) > 10) {
            riskAssessmentService.trackSignificantRiskChange(
                event.getUserId(),
                event.getRiskScore(),
                newRiskScore,
                event.getMonitoringType()
            );
        }
    }

    private boolean requiresImmediateAction(AccountMonitoringEvent event) {
        return "CRITICAL".equals(event.getSeverity()) ||
               event.getRiskScore() > 85 ||
               "FRAUD_DETECTION".equals(event.getMonitoringType()) ||
               "ACCOUNT_TAKEOVER".equals(event.getMonitoringType()) ||
               event.isImmediateActionRequired();
    }

    private void takeImmediateAction(AccountMonitoringEvent event) {
        // Determine action based on severity and type
        String action = monitoringService.determineImmediateAction(
            event.getMonitoringType(),
            event.getSeverity(),
            event.getRiskScore()
        );
        
        // Execute immediate action
        switch (action) {
            case "SUSPEND" -> userService.suspendAccount(
                event.getUserId(),
                event.getMonitoringType(),
                event.getMonitoringDetails()
            );
            case "LOCK" -> userService.lockAccount(
                event.getUserId(),
                event.getMonitoringType()
            );
            case "RESTRICT" -> monitoringService.applyRestrictions(
                event.getUserId(),
                event.getRestrictionsToApply()
            );
            case "REVIEW" -> monitoringService.flagForManualReview(
                event.getUserId(),
                event.getMonitoringType(),
                "IMMEDIATE_REVIEW_REQUIRED"
            );
        }
        
        // Log immediate action taken
        monitoringService.logImmediateAction(
            event.getUserId(),
            action,
            event.getMonitoringType(),
            LocalDateTime.now()
        );
    }

    private void sendMonitoringAlerts(AccountMonitoringEvent event) {
        // Send user alert if appropriate
        if (shouldAlertUser(event)) {
            notificationService.sendMonitoringAlert(
                event.getUserId(),
                event.getMonitoringType(),
                event.getSeverity(),
                event.getAlertMessage()
            );
        }
        
        // Send internal alerts
        if ("HIGH".equals(event.getSeverity()) || "CRITICAL".equals(event.getSeverity())) {
            monitoringService.sendInternalAlert(
                event.getUserId(),
                event.getMonitoringType(),
                event.getSeverity(),
                event.getMonitoringDetails()
            );
        }
        
        // Send compliance alerts
        if (event.isComplianceAlertRequired()) {
            monitoringService.sendComplianceAlert(
                event.getUserId(),
                event.getComplianceReason(),
                event.getComplianceDetails()
            );
        }
    }

    private boolean shouldAlertUser(AccountMonitoringEvent event) {
        // Don't alert user for certain investigation types
        return !"FRAUD_INVESTIGATION".equals(event.getMonitoringType()) &&
               !"AML_SCREENING".equals(event.getMonitoringType()) &&
               !"SILENT_MONITORING".equals(event.getMonitoringType());
    }

    private void scheduleFollowUpMonitoring(AccountMonitoringEvent event) {
        // Schedule follow-up based on severity
        LocalDateTime followUpTime = calculateFollowUpTime(event.getSeverity());
        
        monitoringService.scheduleFollowUp(
            event.getUserId(),
            event.getMonitoringType(),
            followUpTime,
            event.getFollowUpActions()
        );
        
        // Enable continuous monitoring if needed
        if (event.isRequiresContinuousMonitoring()) {
            monitoringService.enableContinuousMonitoring(
                event.getUserId(),
                event.getMonitoringType(),
                event.getMonitoringDuration()
            );
        }
    }

    private LocalDateTime calculateFollowUpTime(String severity) {
        return switch (severity) {
            case "CRITICAL" -> LocalDateTime.now().plusHours(1);
            case "HIGH" -> LocalDateTime.now().plusHours(6);
            case "MEDIUM" -> LocalDateTime.now().plusDays(1);
            case "LOW" -> LocalDateTime.now().plusDays(7);
            default -> LocalDateTime.now().plusDays(3);
        };
    }
}