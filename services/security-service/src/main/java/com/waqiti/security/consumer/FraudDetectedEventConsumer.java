package com.waqiti.security.consumer;

import com.waqiti.common.events.FraudDetectedEvent;
import com.waqiti.security.service.AccountSecurityService;
import com.waqiti.security.service.FraudInvestigationService;
import com.waqiti.security.service.UserBlockingService;
import com.waqiti.security.service.NotificationService;
import com.waqiti.security.repository.ProcessedEventRepository;
import com.waqiti.security.repository.FraudCaseRepository;
import com.waqiti.security.model.ProcessedEvent;
import com.waqiti.security.model.FraudCase;
import com.waqiti.security.model.FraudSeverity;
import com.waqiti.security.model.SecurityAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Consumer for FraudDetectedEvent - Critical for immediate fraud response
 * Orchestrates account freezing, card blocking, and investigation initiation
 * ZERO TOLERANCE: All fraud detections must trigger immediate protective actions
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FraudDetectedEventConsumer {
    
    private final AccountSecurityService accountSecurityService;
    private final FraudInvestigationService fraudInvestigationService;
    private final UserBlockingService userBlockingService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final FraudCaseRepository fraudCaseRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("1000");
    private static final double CRITICAL_CONFIDENCE_THRESHOLD = 0.90;
    
    @KafkaListener(
        topics = "security.fraud.detected",
        groupId = "security-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for security actions
    public void handleFraudDetected(FraudDetectedEvent event) {
        log.warn("FRAUD DETECTED: User {} - Transaction {} - Confidence: {}% - Amount: ${}", 
            event.getUserId(), event.getTransactionId(), event.getFraudConfidence() * 100, event.getAmount());
        
        // IDEMPOTENCY CHECK - Prevent duplicate fraud processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Fraud event already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // STEP 1: Create fraud case for investigation
            FraudCase fraudCase = createFraudCase(event);
            
            // STEP 2: Immediately freeze the transaction
            freezeTransaction(event);
            
            // STEP 3: Take immediate protective actions based on severity
            executeProtectiveActions(fraudCase, event);
            
            // STEP 4: Block card if card transaction
            if (event.isCardTransaction()) {
                blockCompromisedCard(event);
            }
            
            // STEP 5: Initiate comprehensive investigation
            initiateInvestigation(fraudCase, event);
            
            // STEP 6: Analyze fraud pattern for network detection
            analyzeAndBlockFraudNetwork(event);
            
            // STEP 7: Send multi-channel alerts
            sendFraudAlerts(fraudCase, event);
            
            // STEP 8: Create compliance report
            createComplianceReport(fraudCase, event);
            
            // STEP 9: Trigger automatic fund recovery if applicable
            if (event.isRecoverable()) {
                initiateFundRecovery(fraudCase, event);
            }
            
            // STEP 10: Update user risk profile
            updateUserRiskProfile(event);
            
            // STEP 11: Check for account takeover patterns
            checkAccountTakeoverPatterns(event);
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("FraudDetectedEvent")
                .processedAt(Instant.now())
                .fraudCaseId(fraudCase.getId())
                .userId(event.getUserId())
                .transactionId(event.getTransactionId())
                .actionsT taken(fraudCase.getActionsTaken())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed fraud detection: {} - Case ID: {} - Actions taken: {}", 
                event.getTransactionId(), fraudCase.getId(), fraudCase.getActionsTaken().size());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process fraud detection: {}", 
                event.getTransactionId(), e);
                
            // Emergency fallback - at minimum block the transaction
            emergencyFraudResponse(event, e);
            
            throw new RuntimeException("Fraud detection processing failed", e);
        }
    }
    
    private FraudCase createFraudCase(FraudDetectedEvent event) {
        FraudCase fraudCase = FraudCase.builder()
            .id(UUID.randomUUID().toString())
            .userId(event.getUserId())
            .transactionId(event.getTransactionId())
            .amount(event.getAmount())
            .fraudConfidence(event.getFraudConfidence())
            .fraudType(event.getFraudType())
            .severity(calculateSeverity(event))
            .status("OPEN")
            .detectedAt(Instant.now())
            .detectionMethod(event.getDetectionMethod())
            .riskIndicators(event.getRiskIndicators())
            .affectedAccounts(identifyAffectedAccounts(event))
            .estimatedLoss(calculateEstimatedLoss(event))
            .actionsTaken(new ArrayList<>())
            .build();
        
        fraudCaseRepository.save(fraudCase);
        
        log.info("Fraud case created: {} with severity: {} for user: {}", 
            fraudCase.getId(), fraudCase.getSeverity(), event.getUserId());
        
        return fraudCase;
    }
    
    private void freezeTransaction(FraudDetectedEvent event) {
        // Immediately freeze the fraudulent transaction
        accountSecurityService.freezeTransaction(
            event.getTransactionId(),
            "FRAUD_DETECTED",
            event.getFraudConfidence(),
            event.getEventId()
        );
        
        // Block any pending related transactions
        accountSecurityService.blockPendingTransactions(
            event.getUserId(),
            event.getMerchantId(),
            "FRAUD_INVESTIGATION"
        );
        
        log.info("Transaction frozen: {} and related transactions blocked", event.getTransactionId());
    }
    
    private void executeProtectiveActions(FraudCase fraudCase, FraudDetectedEvent event) {
        List<SecurityAction> actions = new ArrayList<>();
        
        // Determine actions based on severity and confidence
        if (fraudCase.getSeverity() == FraudSeverity.CRITICAL || 
            event.getFraudConfidence() > CRITICAL_CONFIDENCE_THRESHOLD) {
            
            // CRITICAL: Full account freeze
            accountSecurityService.freezeAccount(
                event.getUserId(),
                "CRITICAL_FRAUD_DETECTED",
                fraudCase.getId()
            );
            actions.add(SecurityAction.ACCOUNT_FROZEN);
            
            // Disable all payment methods
            accountSecurityService.disableAllPaymentMethods(event.getUserId());
            actions.add(SecurityAction.PAYMENT_METHODS_DISABLED);
            
            // Block all withdrawals
            accountSecurityService.blockWithdrawals(event.getUserId());
            actions.add(SecurityAction.WITHDRAWALS_BLOCKED);
            
            // Force password reset
            accountSecurityService.forcePasswordReset(event.getUserId());
            actions.add(SecurityAction.PASSWORD_RESET_REQUIRED);
            
        } else if (fraudCase.getSeverity() == FraudSeverity.HIGH) {
            
            // HIGH: Partial restrictions
            accountSecurityService.restrictAccount(
                event.getUserId(),
                Arrays.asList("TRANSFERS", "WITHDRAWALS", "CARD_PAYMENTS"),
                fraudCase.getId()
            );
            actions.add(SecurityAction.ACCOUNT_RESTRICTED);
            
            // Require additional authentication
            accountSecurityService.requireStepUpAuthentication(event.getUserId());
            actions.add(SecurityAction.STEP_UP_AUTH_REQUIRED);
            
        } else {
            
            // MEDIUM/LOW: Enhanced monitoring
            accountSecurityService.enableEnhancedMonitoring(
                event.getUserId(),
                fraudCase.getId()
            );
            actions.add(SecurityAction.ENHANCED_MONITORING);
        }
        
        fraudCase.setActionsTaken(actions);
        fraudCaseRepository.save(fraudCase);
        
        log.info("Protective actions executed for fraud case {}: {}", 
            fraudCase.getId(), actions);
    }
    
    private void blockCompromisedCard(FraudDetectedEvent event) {
        // Immediately block the compromised card
        String blockResult = accountSecurityService.blockCard(
            event.getUserId(),
            event.getCardLastFour(),
            "FRAUD_DETECTED",
            event.getTransactionId()
        );
        
        // Order replacement card
        String replacementCardId = accountSecurityService.orderReplacementCard(
            event.getUserId(),
            event.getCardLastFour(),
            "FRAUD_REPLACEMENT",
            true // Expedited shipping
        );
        
        log.info("Card ending {} blocked and replacement ordered: {}", 
            event.getCardLastFour(), replacementCardId);
    }
    
    private void initiateInvestigation(FraudCase fraudCase, FraudDetectedEvent event) {
        // Create comprehensive investigation
        String investigationId = fraudInvestigationService.createInvestigation(
            fraudCase.getId(),
            event.getUserId(),
            event.getTransactionId(),
            event.getFraudType(),
            fraudCase.getSeverity()
        );
        
        fraudCase.setInvestigationId(investigationId);
        fraudCase.setInvestigationStartedAt(Instant.now());
        
        // Collect evidence automatically
        fraudInvestigationService.collectEvidence(
            investigationId,
            event.getUserId(),
            event.getTransactionId(),
            event.getRelatedTransactions()
        );
        
        // Assign to fraud analyst if high severity
        if (fraudCase.getSeverity().ordinal() >= FraudSeverity.HIGH.ordinal()) {
            String analystId = fraudInvestigationService.assignToAnalyst(
                investigationId,
                fraudCase.getSeverity(),
                event.getFraudType()
            );
            fraudCase.setAssignedAnalyst(analystId);
        }
        
        fraudCaseRepository.save(fraudCase);
        
        log.info("Investigation {} initiated for fraud case: {}", 
            investigationId, fraudCase.getId());
    }
    
    private void analyzeAndBlockFraudNetwork(FraudDetectedEvent event) {
        // Analyze for connected fraud patterns
        Set<String> relatedAccounts = fraudInvestigationService.findRelatedAccounts(
            event.getUserId(),
            event.getDeviceFingerprint(),
            event.getIpAddress(),
            event.getTransactionPattern()
        );
        
        if (!relatedAccounts.isEmpty()) {
            log.warn("Fraud network detected with {} related accounts", relatedAccounts.size());
            
            // Block entire fraud network
            for (String accountId : relatedAccounts) {
                userBlockingService.blockUserForFraudInvestigation(
                    accountId,
                    "FRAUD_NETWORK_DETECTED",
                    event.getEventId()
                );
            }
            
            // Create network fraud case
            fraudInvestigationService.createNetworkFraudCase(
                event.getEventId(),
                relatedAccounts,
                event.getFraudType()
            );
        }
    }
    
    private void sendFraudAlerts(FraudCase fraudCase, FraudDetectedEvent event) {
        // Send immediate push notification
        notificationService.sendFraudAlert(
            event.getUserId(),
            fraudCase.getId(),
            event.getTransactionId(),
            event.getAmount(),
            "URGENT: Suspicious activity detected on your account"
        );
        
        // Send SMS for critical fraud
        if (fraudCase.getSeverity() == FraudSeverity.CRITICAL) {
            notificationService.sendCriticalFraudSMS(
                event.getUserId(),
                String.format("URGENT: Fraud detected on your account. Amount: $%.2f. Your account has been secured. Call immediately: 1-800-WAQITI-1",
                    event.getAmount())
            );
        }
        
        // Send detailed email
        notificationService.sendFraudNotificationEmail(
            event.getUserId(),
            fraudCase,
            event
        );
        
        // Alert operations team
        notificationService.alertOperationsTeam(
            fraudCase.getId(),
            fraudCase.getSeverity(),
            event
        );
        
        log.info("Fraud alerts sent for case: {}", fraudCase.getId());
    }
    
    private void createComplianceReport(FraudCase fraudCase, FraudDetectedEvent event) {
        // Create SAR (Suspicious Activity Report) if required
        if (requiresSAR(event)) {
            String sarId = fraudInvestigationService.createSAR(
                fraudCase.getId(),
                event.getUserId(),
                event.getTransactionId(),
                event.getAmount(),
                event.getFraudType(),
                event.getRiskIndicators()
            );
            
            fraudCase.setSarFiled(true);
            fraudCase.setSarId(sarId);
            fraudCase.setSarFiledAt(Instant.now());
        }
        
        // Create internal compliance report
        fraudInvestigationService.createComplianceReport(
            fraudCase.getId(),
            event
        );
        
        fraudCaseRepository.save(fraudCase);
        
        log.info("Compliance reports created for fraud case: {}", fraudCase.getId());
    }
    
    private void initiateFundRecovery(FraudCase fraudCase, FraudDetectedEvent event) {
        // Attempt to recover funds
        String recoveryId = fraudInvestigationService.initiateFundRecovery(
            fraudCase.getId(),
            event.getTransactionId(),
            event.getAmount(),
            event.getMerchantId(),
            event.getPaymentMethod()
        );
        
        fraudCase.setRecoveryInitiated(true);
        fraudCase.setRecoveryId(recoveryId);
        fraudCase.setRecoveryInitiatedAt(Instant.now());
        
        fraudCaseRepository.save(fraudCase);
        
        log.info("Fund recovery initiated for fraud case {}: Recovery ID: {}", 
            fraudCase.getId(), recoveryId);
    }
    
    private void updateUserRiskProfile(FraudDetectedEvent event) {
        // Update user's risk score
        double newRiskScore = accountSecurityService.recalculateUserRiskScore(
            event.getUserId(),
            event.getFraudConfidence(),
            event.getFraudType(),
            event.getAmount()
        );
        
        // Update monitoring level
        String monitoringLevel = newRiskScore > 70 ? "HIGH" :
                                newRiskScore > 40 ? "MEDIUM" : "STANDARD";
        
        accountSecurityService.updateUserMonitoringLevel(
            event.getUserId(),
            monitoringLevel,
            newRiskScore
        );
        
        log.info("User {} risk profile updated - Score: {}, Monitoring: {}", 
            event.getUserId(), newRiskScore, monitoringLevel);
    }
    
    private void checkAccountTakeoverPatterns(FraudDetectedEvent event) {
        // Check for account takeover indicators
        boolean possibleATO = fraudInvestigationService.checkAccountTakeoverIndicators(
            event.getUserId(),
            event.getDeviceFingerprint(),
            event.getIpAddress(),
            event.getUserBehaviorScore()
        );
        
        if (possibleATO) {
            log.warn("Possible account takeover detected for user: {}", event.getUserId());
            
            // Force re-authentication
            accountSecurityService.forceReauthentication(event.getUserId());
            
            // Invalidate all sessions
            accountSecurityService.invalidateAllSessions(event.getUserId());
            
            // Send account takeover alert
            notificationService.sendAccountTakeoverAlert(
                event.getUserId(),
                event.getIpAddress(),
                event.getDeviceFingerprint()
            );
        }
    }
    
    private FraudSeverity calculateSeverity(FraudDetectedEvent event) {
        // Calculate severity based on multiple factors
        if (event.getFraudConfidence() > 0.95 || 
            event.getAmount().compareTo(new BigDecimal("10000")) > 0 ||
            event.getFraudType().equals("ACCOUNT_TAKEOVER")) {
            return FraudSeverity.CRITICAL;
        }
        
        if (event.getFraudConfidence() > 0.80 || 
            event.getAmount().compareTo(new BigDecimal("1000")) > 0) {
            return FraudSeverity.HIGH;
        }
        
        if (event.getFraudConfidence() > 0.60 || 
            event.getAmount().compareTo(new BigDecimal("100")) > 0) {
            return FraudSeverity.MEDIUM;
        }
        
        return FraudSeverity.LOW;
    }
    
    private Set<String> identifyAffectedAccounts(FraudDetectedEvent event) {
        Set<String> affected = new HashSet<>();
        affected.add(event.getUserId());
        
        if (event.getRelatedAccounts() != null) {
            affected.addAll(event.getRelatedAccounts());
        }
        
        return affected;
    }
    
    private BigDecimal calculateEstimatedLoss(FraudDetectedEvent event) {
        // Calculate potential loss including related transactions
        BigDecimal totalLoss = event.getAmount();
        
        if (event.getRelatedTransactions() != null) {
            for (String txId : event.getRelatedTransactions()) {
                // Would query transaction service for amounts
                totalLoss = totalLoss.add(event.getAmount()); // Simplified
            }
        }
        
        return totalLoss;
    }
    
    private boolean requiresSAR(FraudDetectedEvent event) {
        // Determine if Suspicious Activity Report is required
        return event.getAmount().compareTo(new BigDecimal("5000")) > 0 ||
               event.getFraudType().equals("MONEY_LAUNDERING") ||
               event.getFraudType().equals("TERRORIST_FINANCING") ||
               event.getFraudType().equals("IDENTITY_THEFT");
    }
    
    private void emergencyFraudResponse(FraudDetectedEvent event, Exception originalException) {
        try {
            // At minimum, try to block the transaction and freeze account
            log.error("EMERGENCY FRAUD RESPONSE ACTIVATED for user: {}", event.getUserId());
            
            // Try to freeze transaction
            accountSecurityService.emergencyTransactionFreeze(
                event.getTransactionId(),
                event.getUserId()
            );
            
            // Try to freeze account
            accountSecurityService.emergencyAccountFreeze(
                event.getUserId(),
                "EMERGENCY_FRAUD_RESPONSE"
            );
            
            // Send emergency alert
            notificationService.sendEmergencyFraudAlert(
                event.getUserId(),
                event.getTransactionId(),
                event.getAmount()
            );
            
            // Create critical incident
            fraudInvestigationService.createCriticalIncident(
                event,
                originalException,
                "FRAUD_PROCESSING_FAILURE"
            );
            
        } catch (Exception e) {
            log.error("CATASTROPHIC: Emergency fraud response also failed", e);
        }
    }
}