package com.waqiti.frauddetection.kafka;

import com.waqiti.frauddetection.domain.FraudAlert;
import com.waqiti.frauddetection.domain.FraudCase;
import com.waqiti.frauddetection.domain.FraudAction;
import com.waqiti.frauddetection.dto.CriticalFraudAlertEvent;
import com.waqiti.frauddetection.repository.FraudAlertRepository;
import com.waqiti.frauddetection.repository.FraudCaseRepository;
import com.waqiti.frauddetection.service.*;
import com.waqiti.common.distributed.DistributedLockService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Enterprise-grade consumer for critical fraud alerts requiring immediate action
 * Implements automated response, containment, and escalation for high-risk fraud events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FraudAlertCriticalConsumer {

    private final FraudAlertRepository fraudAlertRepository;
    private final FraudCaseRepository fraudCaseRepository;
    private final AccountService accountService;
    private final TransactionService transactionService;
    private final NotificationService notificationService;
    private final SecurityService securityService;
    private final ComplianceService complianceService;
    private final PaymentService paymentService;
    private final NetworkAnalysisService networkAnalysisService;
    private final MLFraudService mlFraudService;
    private final DistributedLockService lockService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${fraud.critical.auto-block-threshold:0.95}")
    private double autoBlockThreshold;

    @Value("${fraud.critical.network-isolation-threshold:0.98}")
    private double networkIsolationThreshold;

    @Value("${fraud.critical.law-enforcement-threshold:0.99}")
    private double lawEnforcementThreshold;

    private static final Set<String> CRITICAL_FRAUD_TYPES = Set.of(
        "ACCOUNT_TAKEOVER",
        "IDENTITY_THEFT", 
        "ORGANIZED_FRAUD_RING",
        "MONEY_LAUNDERING",
        "TERRORIST_FINANCING"
    );

    @KafkaListener(
        topics = "fraud-alert-critical",
        groupId = "fraud-critical-response-team",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Retryable(value = Exception.class, maxAttempts = 3, backoff = @Backoff(delay = 500, multiplier = 2))
    @Transactional
    public void processCriticalFraudAlert(
            @Payload CriticalFraudAlertEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String lockKey = "fraud-critical-" + event.getAlertId();
        Counter criticalFraudCounter = meterRegistry.counter("fraud.alerts.critical",
            "type", event.getFraudType(), "severity", "CRITICAL");
        
        try {
            log.error("CRITICAL FRAUD ALERT: {} - Type: {}, Score: {}, Amount: {}", 
                event.getAlertId(), event.getFraudType(), event.getRiskScore(), event.getAmount());

            // Acquire lock with short timeout - critical alerts need immediate processing
            boolean lockAcquired = lockService.tryLock(lockKey, 10, TimeUnit.SECONDS);
            if (!lockAcquired) {
                log.error("Could not acquire lock for critical fraud alert: {}", event.getAlertId());
                // For critical alerts, process anyway but flag for review
                event.setConcurrentProcessingFlag(true);
            }

            try {
                // 1. IMMEDIATE CONTAINMENT - Execute in parallel for speed
                CompletableFuture<Void> containmentFuture = CompletableFuture.runAsync(() -> 
                    executeImmediateContainment(event));
                
                // 2. Create fraud case with highest priority
                FraudCase fraudCase = createCriticalFraudCase(event);
                
                // 3. Perform deep investigation in parallel
                CompletableFuture<InvestigationResult> investigationFuture = CompletableFuture.supplyAsync(() ->
                    performDeepInvestigation(event, fraudCase));
                
                // 4. Wait for containment to complete
                containmentFuture.get(5, TimeUnit.SECONDS);
                
                // 5. Execute automated response actions
                List<FraudAction> actions = executeAutomatedResponse(event, fraudCase);
                
                // 6. Get investigation results
                InvestigationResult investigation = investigationFuture.get(10, TimeUnit.SECONDS);
                
                // 7. Perform network analysis for connected fraud
                NetworkAnalysisResult networkResult = analyzeNetworkConnections(event, investigation);
                
                // 8. Block related accounts if network fraud detected
                if (networkResult.isNetworkFraudDetected()) {
                    blockNetworkAccounts(networkResult);
                }
                
                // 9. Escalate to security team and law enforcement if needed
                escalateToSecurityTeam(fraudCase, investigation, networkResult);
                
                // 10. Submit regulatory reports
                submitRegulatoryReports(fraudCase, event, investigation);
                
                // 11. Update ML models with new fraud pattern
                updateMLModels(event, investigation, networkResult);
                
                // 12. Send comprehensive notifications
                sendCriticalNotifications(fraudCase, actions, investigation);
                
                // 13. Create recovery plan
                createRecoveryPlan(fraudCase, investigation);
                
                criticalFraudCounter.increment();
                log.info("Critical fraud alert {} processed successfully. Case ID: {}", 
                    event.getAlertId(), fraudCase.getCaseId());
                
                acknowledgment.acknowledge();
                
            } finally {
                if (lockAcquired) {
                    lockService.unlock(lockKey);
                }
            }
            
        } catch (Exception e) {
            log.error("CRITICAL ERROR processing fraud alert: {}", event.getAlertId(), e);
            handleCriticalProcessingError(event, e);
            // Don't acknowledge - let it retry or go to DLQ
            throw new RuntimeException("Critical fraud processing failed", e);
        }
    }

    private void executeImmediateContainment(CriticalFraudAlertEvent event) {
        log.info("Executing immediate containment for user: {}", event.getUserId());
        
        // 1. Freeze all accounts immediately
        List<String> accountIds = accountService.getAllAccountsForUser(event.getUserId());
        accountIds.parallelStream().forEach(accountId -> {
            accountService.freezeAccountImmediate(accountId, "CRITICAL_FRAUD_ALERT");
            log.info("Account frozen: {}", accountId);
        });
        
        // 2. Block all pending transactions
        List<String> pendingTransactions = transactionService.getPendingTransactions(event.getUserId());
        pendingTransactions.parallelStream().forEach(txId -> {
            transactionService.blockTransaction(txId, "FRAUD_DETECTED");
            log.info("Transaction blocked: {}", txId);
        });
        
        // 3. Revoke all active sessions
        securityService.revokeAllSessions(event.getUserId());
        
        // 4. Block all API access
        securityService.blockApiAccess(event.getUserId(), "FRAUD_CONTAINMENT");
        
        // 5. Disable all payment methods
        paymentService.disableAllPaymentMethods(event.getUserId());
        
        // 6. Add to global blacklist temporarily
        securityService.addToBlacklist(event.getUserId(), "FRAUD_INVESTIGATION", 72); // 72 hours
        
        // 7. Block device and IP
        if (event.getDeviceId() != null) {
            securityService.blockDevice(event.getDeviceId());
        }
        if (event.getIpAddress() != null) {
            securityService.blockIpAddress(event.getIpAddress());
        }
        
        kafkaTemplate.send("fraud-containment-executed", Map.of(
            "userId", event.getUserId(),
            "alertId", event.getAlertId(),
            "containmentActions", accountIds.size() + pendingTransactions.size()
        ));
    }

    private FraudCase createCriticalFraudCase(CriticalFraudAlertEvent event) {
        FraudCase fraudCase = new FraudCase();
        fraudCase.setCaseId(UUID.randomUUID().toString());
        fraudCase.setAlertId(event.getAlertId());
        fraudCase.setUserId(event.getUserId());
        fraudCase.setFraudType(event.getFraudType());
        fraudCase.setSeverity("CRITICAL");
        fraudCase.setPriority(1); // Highest priority
        fraudCase.setRiskScore(event.getRiskScore());
        fraudCase.setAmount(event.getAmount());
        fraudCase.setStatus("ACTIVE_INVESTIGATION");
        fraudCase.setCreatedAt(LocalDateTime.now());
        fraudCase.setAssignedTo("SECURITY_TEAM_CRITICAL");
        fraudCase.setEscalationLevel(3); // Highest escalation
        fraudCase.setSlaDeadline(LocalDateTime.now().plusHours(1)); // 1 hour SLA for critical
        fraudCase.setAutoResponseEnabled(true);
        fraudCase.setMetadata(buildCaseMetadata(event));
        
        return fraudCaseRepository.save(fraudCase);
    }

    private InvestigationResult performDeepInvestigation(CriticalFraudAlertEvent event, FraudCase fraudCase) {
        log.info("Performing deep investigation for case: {}", fraudCase.getCaseId());
        
        InvestigationResult result = new InvestigationResult();
        
        // 1. Transaction pattern analysis
        result.setSuspiciousTransactions(
            transactionService.analyzeSuspiciousPatterns(event.getUserId(), 90)); // 90 days
        
        // 2. Account behavior analysis
        result.setBehaviorAnomalies(
            mlFraudService.detectBehaviorAnomalies(event.getUserId()));
        
        // 3. Device and location analysis
        result.setDeviceAnomalies(
            securityService.analyzeDeviceHistory(event.getUserId(), event.getDeviceId()));
        
        // 4. Velocity check
        result.setVelocityViolations(
            transactionService.checkVelocityViolations(event.getUserId()));
        
        // 5. Link analysis - find connected accounts
        result.setLinkedAccounts(
            networkAnalysisService.findLinkedAccounts(event.getUserId()));
        
        // 6. Check against known fraud patterns
        result.setKnownFraudPatterns(
            mlFraudService.matchKnownFraudPatterns(event));
        
        // 7. External database checks
        result.setExternalFraudCheck(
            performExternalFraudChecks(event.getUserId()));
        
        // 8. Financial impact assessment
        result.setFinancialImpact(
            calculateFinancialImpact(event, result.getSuspiciousTransactions()));
        
        result.setCompletedAt(LocalDateTime.now());
        result.setConfidenceScore(calculateInvestigationConfidence(result));
        
        return result;
    }

    private List<FraudAction> executeAutomatedResponse(CriticalFraudAlertEvent event, FraudCase fraudCase) {
        List<FraudAction> actions = new ArrayList<>();
        
        // Determine response based on fraud type and score
        if (event.getRiskScore() >= autoBlockThreshold) {
            // Full account termination for highest risk
            actions.add(executeAccountTermination(event.getUserId(), fraudCase.getCaseId()));
        }
        
        if (event.getRiskScore() >= networkIsolationThreshold) {
            // Isolate from network to prevent spread
            actions.add(executeNetworkIsolation(event.getUserId(), fraudCase.getCaseId()));
        }
        
        if (CRITICAL_FRAUD_TYPES.contains(event.getFraudType())) {
            // Specific actions for critical fraud types
            actions.addAll(executeFraudTypeSpecificActions(event, fraudCase));
        }
        
        // Reverse recent suspicious transactions
        if (event.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            actions.add(executeTransactionReversals(event, fraudCase));
        }
        
        // Legal hold on all assets
        actions.add(executeLegalHold(event.getUserId(), fraudCase.getCaseId()));
        
        return actions;
    }

    private NetworkAnalysisResult analyzeNetworkConnections(CriticalFraudAlertEvent event, 
                                                           InvestigationResult investigation) {
        log.info("Analyzing network connections for fraud ring detection");
        
        NetworkAnalysisResult result = new NetworkAnalysisResult();
        
        // Use graph analysis to find connected fraud
        Set<String> connectedUsers = networkAnalysisService.findConnectedUsers(
            event.getUserId(),
            investigation.getLinkedAccounts(),
            3 // depth of 3 connections
        );
        
        // Check if any connected users have fraud history
        Set<String> fraudulentConnections = connectedUsers.stream()
            .filter(userId -> complianceService.hasFraudHistory(userId))
            .collect(Collectors.toSet());
        
        result.setConnectedUserCount(connectedUsers.size());
        result.setFraudulentConnectionCount(fraudulentConnections.size());
        result.setNetworkFraudDetected(fraudulentConnections.size() >= 2);
        result.setConnectedUserIds(connectedUsers);
        result.setRiskScore(calculateNetworkRiskScore(connectedUsers, fraudulentConnections));
        
        // Identify fraud ring patterns
        if (result.isNetworkFraudDetected()) {
            result.setFraudRingIndicators(identifyFraudRingPatterns(connectedUsers));
        }
        
        return result;
    }

    private void blockNetworkAccounts(NetworkAnalysisResult networkResult) {
        log.warn("Blocking {} accounts in suspected fraud network", networkResult.getConnectedUserCount());
        
        networkResult.getConnectedUserIds().parallelStream().forEach(userId -> {
            // Apply graduated response based on connection risk
            double connectionRisk = networkAnalysisService.calculateConnectionRisk(userId);
            
            if (connectionRisk > 0.8) {
                accountService.freezeAccountImmediate(userId, "FRAUD_NETWORK_HIGH_RISK");
            } else if (connectionRisk > 0.5) {
                accountService.applyRestrictions(userId, "FRAUD_NETWORK_MEDIUM_RISK");
            } else {
                accountService.enhanceMonitoring(userId, "FRAUD_NETWORK_LOW_RISK");
            }
        });
        
        kafkaTemplate.send("fraud-network-blocked", networkResult);
    }

    private void escalateToSecurityTeam(FraudCase fraudCase, InvestigationResult investigation, 
                                       NetworkAnalysisResult networkResult) {
        log.info("Escalating to security team - Case: {}", fraudCase.getCaseId());
        
        // Create security incident
        Map<String, Object> securityIncident = Map.of(
            "incidentType", "CRITICAL_FRAUD",
            "caseId", fraudCase.getCaseId(),
            "severity", "CRITICAL",
            "affectedUsers", networkResult.getConnectedUserCount(),
            "financialImpact", investigation.getFinancialImpact(),
            "requiresImmediateAction", true,
            "investigationSummary", investigation,
            "networkAnalysis", networkResult
        );
        
        // Alert security team
        notificationService.alertSecurityTeam(securityIncident, "CRITICAL");
        
        // Create PagerDuty incident for 24/7 response
        notificationService.createPagerDutyIncident(
            "CRITICAL FRAUD: " + fraudCase.getFraudType(),
            fraudCase.getCaseId(),
            "CRITICAL"
        );
        
        // Alert executives for high-value fraud
        if (investigation.getFinancialImpact().compareTo(new BigDecimal("100000")) > 0) {
            notificationService.alertExecutives(fraudCase, investigation);
        }
        
        // Contact law enforcement if threshold met
        if (fraudCase.getRiskScore() >= lawEnforcementThreshold || 
            CRITICAL_FRAUD_TYPES.contains(fraudCase.getFraudType())) {
            inititateLawEnforcementContact(fraudCase, investigation);
        }
    }

    private void submitRegulatoryReports(FraudCase fraudCase, CriticalFraudAlertEvent event, 
                                        InvestigationResult investigation) {
        log.info("Submitting regulatory reports for case: {}", fraudCase.getCaseId());
        
        // SAR (Suspicious Activity Report) filing
        if (requiresSARFiling(event, investigation)) {
            Map<String, Object> sarData = Map.of(
                "caseId", fraudCase.getCaseId(),
                "suspectUserId", event.getUserId(),
                "suspiciousActivity", event.getFraudType(),
                "amount", event.getAmount(),
                "narrative", buildSARNarrative(fraudCase, investigation),
                "filingDeadline", LocalDateTime.now().plusDays(30)
            );
            
            complianceService.fileSAR(sarData);
        }
        
        // CTR (Currency Transaction Report) if applicable
        if (event.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            complianceService.fileCTR(fraudCase.getCaseId(), event.getAmount());
        }
        
        // Report to financial intelligence unit
        complianceService.reportToFIU(fraudCase, investigation);
        
        // Update regulatory dashboard
        kafkaTemplate.send("regulatory-fraud-report", Map.of(
            "caseId", fraudCase.getCaseId(),
            "reportTypes", List.of("SAR", "CTR", "FIU"),
            "timestamp", LocalDateTime.now()
        ));
    }

    private void updateMLModels(CriticalFraudAlertEvent event, InvestigationResult investigation, 
                               NetworkAnalysisResult networkResult) {
        log.info("Updating ML models with new fraud pattern");
        
        // Extract features from this fraud case
        Map<String, Object> fraudFeatures = extractFraudFeatures(event, investigation, networkResult);
        
        // Send to ML pipeline for model update
        kafkaTemplate.send("ml-fraud-pattern-update", fraudFeatures);
        
        // Update real-time scoring rules
        mlFraudService.updateScoringRules(fraudFeatures);
        
        // Add to fraud pattern database
        mlFraudService.addKnownFraudPattern(
            event.getFraudType(),
            fraudFeatures,
            event.getRiskScore()
        );
    }

    private void sendCriticalNotifications(FraudCase fraudCase, List<FraudAction> actions, 
                                          InvestigationResult investigation) {
        // Customer notification (if not the perpetrator)
        if (!investigation.isUserPerpetrator()) {
            notificationService.sendFraudAlertToCustomer(
                fraudCase.getUserId(),
                "Critical security alert on your account",
                buildCustomerNotification(fraudCase, actions)
            );
        }
        
        // Internal notifications
        notificationService.sendInternalAlert(
            "FRAUD_TEAM",
            String.format("CRITICAL: %s fraud detected - Case %s", 
                fraudCase.getFraudType(), fraudCase.getCaseId()),
            buildInternalNotification(fraudCase, investigation)
        );
        
        // Compliance notification
        notificationService.notifyCompliance(fraudCase, investigation);
        
        // Risk team notification
        notificationService.notifyRiskTeam(fraudCase, actions);
    }

    private void createRecoveryPlan(FraudCase fraudCase, InvestigationResult investigation) {
        Map<String, Object> recoveryPlan = Map.of(
            "caseId", fraudCase.getCaseId(),
            "affectedAccounts", investigation.getAffectedAccounts(),
            "estimatedLoss", investigation.getFinancialImpact(),
            "recoverySteps", List.of(
                "Secure all affected accounts",
                "Reverse fraudulent transactions",
                "Reset all credentials",
                "Issue new payment cards",
                "Implement enhanced monitoring"
            ),
            "estimatedRecoveryTime", "72 hours",
            "assignedTeam", "FRAUD_RECOVERY_TEAM"
        );
        
        kafkaTemplate.send("fraud-recovery-plan-created", recoveryPlan);
    }

    // Helper methods and inner classes...
    
    private void handleCriticalProcessingError(CriticalFraudAlertEvent event, Exception error) {
        log.error("CRITICAL: Failed to process fraud alert {}: {}", event.getAlertId(), error.getMessage());
        
        // Even if processing fails, try to contain the threat
        try {
            accountService.emergencyFreeze(event.getUserId());
            notificationService.sendEmergencyAlert(
                "CRITICAL_FRAUD_PROCESSING_FAILURE",
                event.getAlertId(),
                error.getMessage()
            );
        } catch (Exception e) {
            log.error("Failed to execute emergency containment", e);
        }
        
        // Send to high-priority DLQ
        kafkaTemplate.send("fraud-alert-critical-dlq", Map.of(
            "event", event,
            "error", error.getMessage(),
            "timestamp", LocalDateTime.now()
        ));
    }

    @lombok.Data
    private static class InvestigationResult {
        private List<String> suspiciousTransactions;
        private Map<String, Object> behaviorAnomalies;
        private List<String> deviceAnomalies;
        private List<String> velocityViolations;
        private Set<String> linkedAccounts;
        private List<String> knownFraudPatterns;
        private Map<String, Object> externalFraudCheck;
        private BigDecimal financialImpact;
        private LocalDateTime completedAt;
        private double confidenceScore;
        private boolean userPerpetrator = true;
        private List<String> affectedAccounts;
    }

    @lombok.Data
    private static class NetworkAnalysisResult {
        private int connectedUserCount;
        private int fraudulentConnectionCount;
        private boolean networkFraudDetected;
        private Set<String> connectedUserIds;
        private double riskScore;
        private Map<String, Object> fraudRingIndicators;
    }

    // Additional helper implementations would follow...
}