package com.waqiti.dispute.consumer;

import com.waqiti.common.events.TransactionDisputeOpenedEvent;
import com.waqiti.dispute.service.DisputeManagementService;
import com.waqiti.dispute.service.InvestigationService;
import com.waqiti.dispute.service.ChargebackService;
import com.waqiti.dispute.service.NotificationService;
import com.waqiti.dispute.repository.ProcessedEventRepository;
import com.waqiti.dispute.repository.DisputeRepository;
import com.waqiti.dispute.model.ProcessedEvent;
import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.entity.DisputeStatus;
import com.waqiti.dispute.entity.DisputePriority;
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
import java.util.UUID;

/**
 * Consumer for TransactionDisputeOpenedEvent - Critical for dispute resolution workflow
 * Initiates investigation and provisional credits for disputed transactions
 * ZERO TOLERANCE: All disputes must be acknowledged within regulatory timeframes
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionDisputeOpenedEventConsumer {
    
    private final DisputeManagementService disputeManagementService;
    private final InvestigationService investigationService;
    private final ChargebackService chargebackService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final DisputeRepository disputeRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final int PROVISIONAL_CREDIT_THRESHOLD_DAYS = 10; // Regulation E requirement
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("500");
    
    @KafkaListener(
        topics = "transaction.dispute.opened",
        groupId = "dispute-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for dispute handling
    public void handleDisputeOpened(TransactionDisputeOpenedEvent event) {
        log.info("Processing transaction dispute: Transaction {} disputed by user {} for amount ${} - Reason: {}", 
            event.getTransactionId(), event.getUserId(), event.getDisputeAmount(), event.getDisputeReason());
        
        // IDEMPOTENCY CHECK - Prevent duplicate dispute processing
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Dispute already processed for event: {}", event.getEventId());
            return;
        }
        
        try {
            // STEP 1: Create dispute case with all details
            Dispute dispute = createDisputeCase(event);
            
            // STEP 2: Freeze the disputed transaction immediately
            freezeDisputedTransaction(event);
            
            // STEP 3: Issue provisional credit if eligible
            handleProvisionalCredit(dispute, event);
            
            // STEP 4: Initiate investigation workflow
            initiateInvestigation(dispute, event);
            
            // STEP 5: Notify merchant if applicable
            if (event.getMerchantId() != null) {
                notifyMerchantOfDispute(event);
            }
            
            // STEP 6: Set up automated evidence collection
            initiateEvidenceCollection(dispute, event);
            
            // STEP 7: Calculate and set SLA deadlines
            setDisputeSLADeadlines(dispute, event);
            
            // STEP 8: Send acknowledgment to customer
            sendDisputeAcknowledgment(dispute, event);
            
            // STEP 9: Create audit trail for regulatory compliance
            createRegulatoryAuditTrail(dispute, event);
            
            // STEP 10: Check for fraud patterns
            checkFraudPatterns(dispute, event);
            
            // STEP 11: Initiate chargeback if card transaction
            if (event.isCardTransaction()) {
                initiateChargebackProcess(dispute, event);
            }
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("TransactionDisputeOpenedEvent")
                .processedAt(Instant.now())
                .disputeId(dispute.getId())
                .transactionId(event.getTransactionId())
                .userId(event.getUserId())
                .disputeAmount(event.getDisputeAmount())
                .provisionalCreditIssued(dispute.isProvisionalCreditIssued())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed dispute opening: {} - Investigation initiated", 
                dispute.getId());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process dispute opening: {}", 
                event.getTransactionId(), e);
                
            // Create high-priority manual intervention record
            createManualInterventionRecord(event, e);
            
            // Send emergency notification to operations team
            sendEmergencyNotification(event, e);
            
            throw new RuntimeException("Dispute opening processing failed", e);
        }
    }
    
    private Dispute createDisputeCase(TransactionDisputeOpenedEvent event) {
        // Create comprehensive dispute case
        Dispute dispute = Dispute.builder()
            .id(UUID.randomUUID().toString())
            .transactionId(event.getTransactionId())
            .userId(event.getUserId())
            .merchantId(event.getMerchantId())
            .disputeAmount(event.getDisputeAmount())
            .originalTransactionAmount(event.getOriginalTransactionAmount())
            .disputeReason(event.getDisputeReason())
            .disputeCategory(categorizeDispute(event.getDisputeReason()))
            .description(event.getDescription())
            .status(DisputeStatus.OPENED)
            .priority(calculateDisputePriority(event))
            .openedAt(Instant.now())
            .transactionDate(event.getTransactionDate())
            .isCardTransaction(event.isCardTransaction())
            .cardLastFour(event.getCardLastFour())
            .merchantName(event.getMerchantName())
            .attachedDocuments(event.getAttachedDocuments())
            .customerStatement(event.getCustomerStatement())
            .build();
        
        disputeRepository.save(dispute);
        
        log.info("Dispute case created: {} for transaction: {} with priority: {}", 
            dispute.getId(), event.getTransactionId(), dispute.getPriority());
        
        return dispute;
    }
    
    private void freezeDisputedTransaction(TransactionDisputeOpenedEvent event) {
        // Immediately freeze the disputed transaction
        disputeManagementService.freezeTransaction(
            event.getTransactionId(),
            event.getUserId(),
            "DISPUTE_OPENED",
            event.getEventId()
        );
        
        // Prevent any related transactions
        disputeManagementService.blockRelatedTransactions(
            event.getMerchantId(),
            event.getUserId(),
            event.getTransactionId()
        );
        
        log.info("Transaction frozen: {} and related transactions blocked", event.getTransactionId());
    }
    
    private void handleProvisionalCredit(Dispute dispute, TransactionDisputeOpenedEvent event) {
        // Check eligibility for provisional credit under Regulation E
        boolean isEligible = checkProvisionalCreditEligibility(event);
        
        if (isEligible) {
            // Issue provisional credit immediately for eligible disputes
            BigDecimal creditAmount = event.getDisputeAmount();
            
            String creditId = disputeManagementService.issueProvisionalCredit(
                event.getUserId(),
                creditAmount,
                dispute.getId(),
                event.getTransactionId()
            );
            
            dispute.setProvisionalCreditIssued(true);
            dispute.setProvisionalCreditAmount(creditAmount);
            dispute.setProvisionalCreditId(creditId);
            dispute.setProvisionalCreditIssuedAt(Instant.now());
            
            // Publish provisional credit event
            publishProvisionalCreditEvent(dispute, event, creditId);
            
            log.info("Provisional credit of ${} issued for dispute: {} with credit ID: {}", 
                creditAmount, dispute.getId(), creditId);
        } else {
            // Schedule provisional credit decision within regulatory timeframe
            LocalDateTime creditDeadline = LocalDateTime.now().plusDays(PROVISIONAL_CREDIT_THRESHOLD_DAYS);
            
            disputeManagementService.scheduleProvisionalCreditDecision(
                dispute.getId(),
                creditDeadline
            );
            
            log.info("Provisional credit decision scheduled for dispute: {} by {}", 
                dispute.getId(), creditDeadline);
        }
    }
    
    private void initiateInvestigation(Dispute dispute, TransactionDisputeOpenedEvent event) {
        // Start comprehensive investigation workflow
        String investigationId = investigationService.createInvestigation(
            dispute.getId(),
            event.getTransactionId(),
            event.getUserId(),
            event.getMerchantId(),
            event.getDisputeReason(),
            dispute.getPriority()
        );
        
        dispute.setInvestigationId(investigationId);
        dispute.setInvestigationStartedAt(Instant.now());
        dispute.setStatus(DisputeStatus.UNDER_INVESTIGATION);
        
        // Assign to appropriate investigation team
        String assignedTeam = investigationService.assignToTeam(
            investigationId,
            dispute.getDisputeCategory(),
            dispute.getPriority(),
            event.getDisputeAmount()
        );
        
        dispute.setAssignedTeam(assignedTeam);
        
        log.info("Investigation {} initiated for dispute: {} assigned to team: {}", 
            investigationId, dispute.getId(), assignedTeam);
    }
    
    private void notifyMerchantOfDispute(TransactionDisputeOpenedEvent event) {
        // Notify merchant about the dispute
        notificationService.notifyMerchantOfDispute(
            event.getMerchantId(),
            event.getTransactionId(),
            event.getDisputeAmount(),
            event.getDisputeReason(),
            calculateMerchantResponseDeadline()
        );
        
        // Request merchant evidence
        disputeManagementService.requestMerchantEvidence(
            event.getMerchantId(),
            event.getTransactionId(),
            event.getDisputeReason()
        );
        
        log.info("Merchant {} notified of dispute for transaction: {}", 
            event.getMerchantId(), event.getTransactionId());
    }
    
    private void initiateEvidenceCollection(Dispute dispute, TransactionDisputeOpenedEvent event) {
        // Automatically collect relevant evidence
        investigationService.collectTransactionLogs(
            event.getTransactionId(),
            dispute.getId()
        );
        
        investigationService.collectAuthorizationData(
            event.getTransactionId(),
            event.getCardLastFour()
        );
        
        investigationService.collectDeviceFingerprints(
            event.getUserId(),
            event.getTransactionDate()
        );
        
        investigationService.collectIPAddressData(
            event.getTransactionId(),
            event.getUserId()
        );
        
        // Collect merchant response history
        if (event.getMerchantId() != null) {
            investigationService.collectMerchantHistory(
                event.getMerchantId(),
                event.getUserId()
            );
        }
        
        log.info("Evidence collection initiated for dispute: {}", dispute.getId());
    }
    
    private void setDisputeSLADeadlines(Dispute dispute, TransactionDisputeOpenedEvent event) {
        // Set regulatory and internal SLA deadlines
        LocalDateTime now = LocalDateTime.now();
        
        // Regulation E deadlines
        dispute.setProvisionalCreditDeadline(now.plusDays(10));
        dispute.setInvestigationDeadline(now.plusDays(45));
        dispute.setFinalResolutionDeadline(now.plusDays(90));
        
        // Internal SLA based on priority
        switch (dispute.getPriority()) {
            case CRITICAL -> {
                dispute.setInternalSLA(now.plusDays(1));
                dispute.setFirstResponseSLA(now.plusHours(2));
            }
            case HIGH -> {
                dispute.setInternalSLA(now.plusDays(3));
                dispute.setFirstResponseSLA(now.plusHours(6));
            }
            case MEDIUM -> {
                dispute.setInternalSLA(now.plusDays(7));
                dispute.setFirstResponseSLA(now.plusHours(24));
            }
            case LOW -> {
                dispute.setInternalSLA(now.plusDays(14));
                dispute.setFirstResponseSLA(now.plusHours(48));
            }
        }
        
        disputeRepository.save(dispute);
        
        log.info("SLA deadlines set for dispute: {} - Internal SLA: {}", 
            dispute.getId(), dispute.getInternalSLA());
    }
    
    private void sendDisputeAcknowledgment(Dispute dispute, TransactionDisputeOpenedEvent event) {
        // Send immediate acknowledgment to customer
        notificationService.sendDisputeAcknowledgment(
            event.getUserId(),
            dispute.getId(),
            event.getTransactionId(),
            event.getDisputeAmount(),
            dispute.getProvisionalCreditIssued(),
            dispute.getInvestigationDeadline()
        );
        
        // Create dispute status page
        String statusUrl = disputeManagementService.createDisputeStatusPage(
            dispute.getId(),
            event.getUserId()
        );
        
        // Send SMS for high-value disputes
        if (event.getDisputeAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            notificationService.sendHighValueDisputeSMS(
                event.getUserId(),
                dispute.getId(),
                event.getDisputeAmount(),
                statusUrl
            );
        }
        
        log.info("Dispute acknowledgment sent to user: {} with status URL: {}", 
            event.getUserId(), statusUrl);
    }
    
    private void createRegulatoryAuditTrail(Dispute dispute, TransactionDisputeOpenedEvent event) {
        // Create comprehensive audit trail for regulatory compliance
        disputeManagementService.createAuditEntry(
            dispute.getId(),
            "DISPUTE_OPENED",
            event,
            "Dispute case opened and investigation initiated"
        );
        
        // Record Regulation E compliance
        disputeManagementService.recordRegulationECompliance(
            dispute.getId(),
            "ACKNOWLEDGMENT_SENT",
            Instant.now(),
            dispute.getProvisionalCreditDeadline()
        );
        
        // Archive original transaction data
        disputeManagementService.archiveTransactionData(
            event.getTransactionId(),
            dispute.getId()
        );
        
        log.info("Regulatory audit trail created for dispute: {}", dispute.getId());
    }
    
    private void checkFraudPatterns(Dispute dispute, TransactionDisputeOpenedEvent event) {
        // Check for potential fraud patterns
        boolean isSuspiciousFraud = investigationService.checkFraudPatterns(
            event.getUserId(),
            event.getMerchantId(),
            event.getTransactionId(),
            event.getDisputeReason()
        );
        
        if (isSuspiciousFraud) {
            dispute.setPriority(DisputePriority.CRITICAL);
            dispute.setFraudSuspected(true);
            
            // Escalate to fraud team
            investigationService.escalateToFraudTeam(
                dispute.getId(),
                event.getUserId(),
                "SUSPICIOUS_PATTERN_DETECTED"
            );
            
            // Block user's card if necessary
            if (event.isCardTransaction() && event.getDisputeReason().contains("UNAUTHORIZED")) {
                disputeManagementService.requestCardBlock(
                    event.getUserId(),
                    event.getCardLastFour(),
                    dispute.getId()
                );
            }
            
            log.warn("Fraud pattern detected for dispute: {} - Escalated to fraud team", dispute.getId());
        }
    }
    
    private void initiateChargebackProcess(Dispute dispute, TransactionDisputeOpenedEvent event) {
        // Initiate chargeback process for card transactions
        String chargebackId = chargebackService.initiateChargeback(
            dispute.getId(),
            event.getTransactionId(),
            event.getCardNetworkReferenceId(),
            event.getDisputeAmount(),
            event.getDisputeReason(),
            event.getMerchantId()
        );
        
        dispute.setChargebackId(chargebackId);
        dispute.setChargebackInitiatedAt(Instant.now());
        dispute.setChargebackStatus("INITIATED");
        
        disputeRepository.save(dispute);
        
        log.info("Chargeback {} initiated for dispute: {}", chargebackId, dispute.getId());
    }
    
    private boolean checkProvisionalCreditEligibility(TransactionDisputeOpenedEvent event) {
        // Check if eligible for immediate provisional credit
        return event.getDisputeAmount().compareTo(new BigDecimal("50")) >= 0 &&
               event.getTransactionDate().isAfter(LocalDateTime.now().minusDays(60)) &&
               !event.getDisputeReason().equals("MERCHANT_CREDIT_NOT_PROCESSED") &&
               event.isFirstDispute();
    }
    
    private String categorizeDispute(String disputeReason) {
        return switch (disputeReason) {
            case "UNAUTHORIZED_TRANSACTION", "FRAUD" -> "FRAUD";
            case "DUPLICATE_CHARGE", "INCORRECT_AMOUNT" -> "BILLING_ERROR";
            case "SERVICE_NOT_PROVIDED", "PRODUCT_NOT_RECEIVED" -> "NON_RECEIPT";
            case "PRODUCT_UNACCEPTABLE", "SERVICE_UNSATISFACTORY" -> "QUALITY";
            case "CANCELED_RECURRING", "SUBSCRIPTION_CANCELED" -> "CANCELLATION";
            default -> "OTHER";
        };
    }
    
    private DisputePriority calculateDisputePriority(TransactionDisputeOpenedEvent event) {
        // Calculate priority based on multiple factors
        if (event.getDisputeAmount().compareTo(new BigDecimal("1000")) > 0) {
            return DisputePriority.CRITICAL;
        }
        if (event.getDisputeReason().contains("FRAUD") || event.getDisputeReason().contains("UNAUTHORIZED")) {
            return DisputePriority.HIGH;
        }
        if (event.getDisputeAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            return DisputePriority.HIGH;
        }
        if (event.isRecurringTransaction()) {
            return DisputePriority.MEDIUM;
        }
        return DisputePriority.LOW;
    }
    
    private LocalDateTime calculateMerchantResponseDeadline() {
        return LocalDateTime.now().plusDays(7); // Standard merchant response time
    }
    
    private void publishProvisionalCreditEvent(Dispute dispute, TransactionDisputeOpenedEvent event, String creditId) {
        Map<String, Object> creditEvent = Map.of(
            "eventId", UUID.randomUUID().toString(),
            "disputeId", dispute.getId(),
            "userId", event.getUserId(),
            "creditId", creditId,
            "creditAmount", dispute.getProvisionalCreditAmount(),
            "transactionId", event.getTransactionId(),
            "issuedAt", dispute.getProvisionalCreditIssuedAt()
        );
        
        kafkaTemplate.send("dispute.provisional.credit.issued", creditEvent);
    }
    
    private void createManualInterventionRecord(TransactionDisputeOpenedEvent event, Exception exception) {
        manualInterventionService.createCriticalTask(
            "DISPUTE_OPENING_PROCESSING_FAILED",
            String.format(
                "CRITICAL: Failed to process dispute opening. " +
                "Transaction ID: %s, User ID: %s, Amount: $%.2f. " +
                "Regulatory deadlines at risk. Customer not acknowledged. " +
                "Exception: %s. IMMEDIATE MANUAL INTERVENTION REQUIRED.",
                event.getTransactionId(),
                event.getUserId(),
                event.getDisputeAmount(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
    }
    
    private void sendEmergencyNotification(TransactionDisputeOpenedEvent event, Exception exception) {
        try {
            notificationService.sendEmergencyAlert(
                "DISPUTE_SYSTEM_FAILURE",
                String.format(
                    "CRITICAL: Dispute system failed to process dispute for transaction %s. " +
                    "Amount: $%.2f. User: %s. Regulatory compliance at risk. " +
                    "Manual processing required immediately.",
                    event.getTransactionId(),
                    event.getDisputeAmount(),
                    event.getUserId()
                ),
                event
            );
        } catch (Exception e) {
            log.error("Failed to send emergency notification for dispute system failure", e);
        }
    }
}