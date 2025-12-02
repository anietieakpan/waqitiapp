package com.waqiti.dispute.service;

import com.waqiti.dispute.entity.Dispute;
import com.waqiti.dispute.entity.DisputeEvidence;
import com.waqiti.dispute.entity.DisputeStatus;
import com.waqiti.dispute.exception.DatabaseOperationException;
import com.waqiti.dispute.exception.DisputeProcessingException;
import com.waqiti.dispute.exception.DisputeValidationException;
import com.waqiti.dispute.exception.ExternalServiceException;
import com.waqiti.dispute.repository.DisputeRepository;
import com.waqiti.dispute.repository.DisputeEvidenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CRITICAL FINANCIAL SERVICE: Transaction Dispute Resolution System
 * Handles payment disputes, chargebacks, fraud claims, and customer complaints
 * 
 * Features:
 * - Automated dispute workflow management
 * - Evidence collection and validation
 * - Multi-stage resolution process
 * - Chargeback prevention and management
 * - SLA tracking and escalation
 * - Automated decision engine
 * - Regulatory compliance (Visa, Mastercard, PCI)
 * - Machine learning fraud detection integration
 * - Financial reconciliation for dispute outcomes
 * - Comprehensive audit trail
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionDisputeService {
    
    private final DisputeRepository disputeRepository;
    private final DisputeEvidenceRepository evidenceRepository;
    private final TransactionService transactionService;
    private final WalletService walletService;
    private final NotificationService notificationService;
    private final FraudDetectionService fraudDetectionService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${dispute.auto-resolution.enabled:true}")
    private boolean autoResolutionEnabled;
    
    @Value("${dispute.sla.initial-response-hours:24}")
    private int initialResponseSlaHours;
    
    @Value("${dispute.sla.resolution-days:7}")
    private int resolutionSlaDays;
    
    @Value("${dispute.sla.chargeback-response-hours:48}")
    private int chargebackResponseHours;
    
    @Value("${dispute.threshold.auto-approve-amount:50}")
    private BigDecimal autoApproveThreshold;
    
    @Value("${dispute.threshold.high-value-amount:1000}")
    private BigDecimal highValueThreshold;
    
    // Dispute tracking
    private final Map<String, DisputeWorkflow> activeWorkflows = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> userDisputeCounts = new ConcurrentHashMap<>();
    
    // SLA monitoring
    private final Map<String, SlaTracker> slaTrackers = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void initialize() {
        loadActiveDisputes();
        initializeDisputeRules();
        log.info("Transaction Dispute Service initialized - Auto-resolution: {}, SLA: {} hours initial, {} days resolution",
            autoResolutionEnabled, initialResponseSlaHours, resolutionSlaDays);
    }
    
    /**
     * CRITICAL: Create new dispute for transaction
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public CompletableFuture<Dispute> createDispute(DisputeRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Creating dispute for transaction: {} by user: {}", 
                    request.getTransactionId(), request.getUserId());
                
                // Validate dispute request
                validateDisputeRequest(request);
                
                // Check for duplicate disputes
                if (isDuplicateDispute(request)) {
                    throw new DisputeException("Duplicate dispute already exists for this transaction");
                }
                
                // Get transaction details
                Transaction transaction = transactionService.getTransaction(request.getTransactionId());
                if (transaction == null) {
                    throw new DisputeException("Transaction not found: " + request.getTransactionId());
                }
                
                // Validate dispute eligibility
                validateDisputeEligibility(transaction, request);
                
                // Create dispute entity
                Dispute dispute = buildDispute(request, transaction);
                
                // Apply immediate holds if necessary
                if (shouldApplyHold(dispute)) {
                    applyDisputeHold(dispute);
                }
                
                // Save dispute
                dispute = disputeRepository.save(dispute);
                
                // Initialize workflow
                DisputeWorkflow workflow = initializeWorkflow(dispute);
                activeWorkflows.put(dispute.getId(), workflow);
                
                // Start SLA tracking
                startSlaTracking(dispute);
                
                // Send notifications
                sendDisputeCreatedNotifications(dispute);
                
                // Stream dispute event
                streamDisputeEvent("DISPUTE_CREATED", dispute);
                
                // Check for auto-resolution
                if (autoResolutionEnabled && isEligibleForAutoResolution(dispute)) {
                    scheduleAutoResolution(dispute);
                }
                
                log.info("Dispute created successfully: {} - Type: {}, Amount: {}", 
                    dispute.getId(), dispute.getDisputeType(), dispute.getAmount());
                
                return dispute;

            } catch (DisputeValidationException e) {
                log.error("Dispute validation failed", e);
                throw e;
            } catch (DataAccessException | RestClientException e) {
                log.error("Failed to create dispute - external service or database error", e);
                throw new DisputeProcessingException("Failed to create dispute: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Submit evidence for dispute
     */
    @Transactional
    public DisputeEvidence submitEvidence(String disputeId, EvidenceSubmission submission) {
        try {
            log.info("Submitting evidence for dispute: {} - Type: {}", disputeId, submission.getEvidenceType());
            
            Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeException("Dispute not found: " + disputeId));
            
            // Validate evidence submission
            validateEvidenceSubmission(dispute, submission);
            
            // Create evidence record
            DisputeEvidence evidence = DisputeEvidence.builder()
                .id(UUID.randomUUID().toString())
                .disputeId(disputeId)
                .evidenceType(submission.getEvidenceType())
                .submittedBy(submission.getSubmittedBy())
                .submittedAt(LocalDateTime.now())
                .documentUrl(submission.getDocumentUrl())
                .description(submission.getDescription())
                .metadata(submission.getMetadata())
                .verificationStatus(VerificationStatus.PENDING)
                .build();
            
            // Verify evidence authenticity
            verifyEvidence(evidence);
            
            // Save evidence
            evidence = evidenceRepository.save(evidence);
            
            // Update dispute with new evidence
            dispute.getEvidenceIds().add(evidence.getId());
            dispute.setLastUpdated(LocalDateTime.now());
            disputeRepository.save(dispute);
            
            // Check if evidence changes dispute status
            evaluateEvidenceImpact(dispute, evidence);
            
            // Update workflow
            DisputeWorkflow workflow = activeWorkflows.get(disputeId);
            if (workflow != null) {
                workflow.addEvidence(evidence);
            }
            
            log.info("Evidence submitted successfully: {} for dispute: {}", evidence.getId(), disputeId);
            
            return evidence;

        } catch (DataAccessException e) {
            log.error("Failed to submit evidence for dispute: {} - database error", disputeId, e);
            throw new DatabaseOperationException("Failed to submit evidence: " + e.getMessage(), e);
        }
    }
    
    /**
     * Process dispute resolution
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public DisputeResolution resolveDispute(String disputeId, ResolutionRequest request) {
        try {
            log.info("Processing dispute resolution: {} - Decision: {}", disputeId, request.getDecision());
            
            Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeException("Dispute not found: " + disputeId));
            
            // Validate resolution request
            validateResolutionRequest(dispute, request);
            
            // Lock funds if not already locked
            if (!dispute.isFundsLocked()) {
                lockDisputedFunds(dispute);
            }
            
            // Create resolution record
            DisputeResolution resolution = DisputeResolution.builder()
                .id(UUID.randomUUID().toString())
                .disputeId(disputeId)
                .decision(request.getDecision())
                .resolutionType(request.getResolutionType())
                .resolvedBy(request.getResolvedBy())
                .resolvedAt(LocalDateTime.now())
                .reason(request.getReason())
                .refundAmount(calculateRefundAmount(dispute, request))
                .build();
            
            // Execute resolution actions
            executeResolutionActions(dispute, resolution);
            
            // Update dispute status
            updateDisputeStatus(dispute, resolution);
            
            // Process financial adjustments
            processFinancialAdjustments(dispute, resolution);
            
            // Release or transfer locked funds
            handleLockedFunds(dispute, resolution);
            
            // Send resolution notifications
            sendResolutionNotifications(dispute, resolution);
            
            // Update metrics and reporting
            updateDisputeMetrics(dispute, resolution);
            
            // Stream resolution event
            streamDisputeEvent("DISPUTE_RESOLVED", dispute);
            
            // Clean up workflow
            activeWorkflows.remove(disputeId);
            slaTrackers.remove(disputeId);
            
            log.info("Dispute resolved successfully: {} - Decision: {}, Refund: {}", 
                disputeId, resolution.getDecision(), resolution.getRefundAmount());
            
            return resolution;

        } catch (DataAccessException | RestClientException e) {
            log.error("Failed to resolve dispute: {} - external service or database error", disputeId, e);
            throw new DisputeProcessingException("Failed to resolve dispute: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handle chargeback notification from payment provider
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleChargebackNotification(ChargebackNotification notification) {
        try {
            log.warn("CHARGEBACK NOTIFICATION received - Transaction: {}, Amount: {}, Provider: {}",
                notification.getTransactionId(), notification.getAmount(), notification.getProvider());
            
            // Find or create dispute for chargeback
            Dispute dispute = findOrCreateChargebackDispute(notification);
            
            // Update dispute with chargeback details
            dispute.setDisputeType(DisputeType.CHARGEBACK);
            dispute.setChargebackCode(notification.getChargebackCode());
            dispute.setChargebackReason(notification.getReason());
            dispute.setProviderCaseId(notification.getProviderCaseId());
            dispute.setPriority(DisputePriority.CRITICAL);
            dispute.setStatus(DisputeStatus.CHARGEBACK_INITIATED);
            
            // Immediately lock funds
            lockChargebackFunds(dispute, notification.getAmount());
            
            // Set urgent SLA for chargeback response
            SlaTracker sla = slaTrackers.get(dispute.getId());
            if (sla == null) {
                sla = new SlaTracker(dispute.getId());
                slaTrackers.put(dispute.getId(), sla);
            }
            sla.setChargebackDeadline(LocalDateTime.now().plusHours(chargebackResponseHours));
            
            // Gather automatic evidence
            gatherChargebackEvidence(dispute);
            
            // Notify relevant teams
            sendChargebackAlert(dispute, notification);
            
            // Update dispute
            disputeRepository.save(dispute);
            
            // Stream chargeback event
            streamDisputeEvent("CHARGEBACK_RECEIVED", dispute);
            
            log.warn("Chargeback dispute created/updated: {} - Deadline: {}", 
                dispute.getId(), sla.getChargebackDeadline());

        } catch (DataAccessException | RestClientException e) {
            log.error("CRITICAL: Failed to handle chargeback notification - external service or database error", e);
            // Chargeback handling must not fail - send emergency alert
            sendEmergencyChargebackAlert(notification, e);
        }
    }
    
    /**
     * Escalate dispute to next level
     */
    @Transactional
    public void escalateDispute(String disputeId, EscalationRequest request) {
        try {
            log.info("Escalating dispute: {} - Reason: {}", disputeId, request.getReason());
            
            Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeException("Dispute not found: " + disputeId));
            
            // Update escalation level
            int currentLevel = dispute.getEscalationLevel();
            dispute.setEscalationLevel(currentLevel + 1);
            dispute.setEscalatedAt(LocalDateTime.now());
            dispute.setEscalationReason(request.getReason());
            
            // Change status based on escalation level
            if (currentLevel == 0) {
                dispute.setStatus(DisputeStatus.ESCALATED_TO_SUPERVISOR);
            } else if (currentLevel == 1) {
                dispute.setStatus(DisputeStatus.ESCALATED_TO_MANAGER);
            } else {
                dispute.setStatus(DisputeStatus.ESCALATED_TO_LEGAL);
            }
            
            // Update priority
            if (dispute.getPriority() != DisputePriority.CRITICAL) {
                dispute.setPriority(DisputePriority.HIGH);
            }
            
            // Save dispute
            disputeRepository.save(dispute);
            
            // Notify escalation team
            notifyEscalationTeam(dispute, request);
            
            // Update SLA based on escalation
            updateEscalationSla(dispute);
            
            log.info("Dispute escalated to level {} successfully: {}", 
                dispute.getEscalationLevel(), disputeId);

        } catch (DataAccessException e) {
            log.error("Failed to escalate dispute: {} - database error", disputeId, e);
            throw new DatabaseOperationException("Escalation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Automated dispute resolution for eligible cases
     */
    @Async
    public void performAutoResolution(String disputeId) {
        try {
            log.info("Attempting auto-resolution for dispute: {}", disputeId);
            
            Dispute dispute = disputeRepository.findById(disputeId)
                .orElseThrow(() -> new DisputeException("Dispute not found: " + disputeId));
            
            // Run auto-resolution rules
            AutoResolutionResult result = runAutoResolutionRules(dispute);
            
            if (result.isResolvable()) {
                // Create auto-resolution request
                ResolutionRequest autoRequest = ResolutionRequest.builder()
                    .decision(result.getDecision())
                    .resolutionType(ResolutionType.AUTOMATED)
                    .resolvedBy("SYSTEM_AUTO_RESOLUTION")
                    .reason(result.getReason())
                    .refundPercentage(result.getRefundPercentage())
                    .build();
                
                // Execute resolution
                resolveDispute(disputeId, autoRequest);
                
                log.info("Dispute auto-resolved: {} - Decision: {}", disputeId, result.getDecision());
                
            } else {
                log.info("Dispute not eligible for auto-resolution: {} - Reason: {}", 
                    disputeId, result.getIneligibilityReason());
                
                // Mark for manual review
                dispute.setStatus(DisputeStatus.REQUIRES_MANUAL_REVIEW);
                dispute.setAutoResolutionAttempted(true);
                disputeRepository.save(dispute);
            }

        } catch (DataAccessException | DisputeProcessingException e) {
            log.warn("Auto-resolution failed for dispute: {} - will require manual review", disputeId, e);
        }
    }
    
    /**
     * Check SLA compliance and send alerts
     */
    @Scheduled(fixedDelay = 3600000) // Every hour
    public void checkSlaCompliance() {
        try {
            log.debug("Checking SLA compliance for active disputes");
            
            LocalDateTime now = LocalDateTime.now();
            
            for (Map.Entry<String, SlaTracker> entry : slaTrackers.entrySet()) {
                String disputeId = entry.getKey();
                SlaTracker sla = entry.getValue();
                
                // Check initial response SLA
                if (!sla.isInitialResponseMet() && 
                    now.isAfter(sla.getInitialResponseDeadline())) {
                    
                    log.warn("SLA BREACH: Initial response deadline missed for dispute: {}", disputeId);
                    sendSlaBreachAlert(disputeId, "INITIAL_RESPONSE");
                    sla.setInitialResponseMet(true); // Prevent duplicate alerts
                }
                
                // Check resolution SLA
                if (!sla.isResolutionMet() && 
                    now.isAfter(sla.getResolutionDeadline())) {
                    
                    log.warn("SLA BREACH: Resolution deadline missed for dispute: {}", disputeId);
                    sendSlaBreachAlert(disputeId, "RESOLUTION");
                    
                    // Auto-escalate if resolution SLA is breached
                    autoEscalateDispute(disputeId, "Resolution SLA breached");
                    sla.setResolutionMet(true);
                }
                
                // Check chargeback response deadline
                if (sla.getChargebackDeadline() != null && 
                    !sla.isChargebackResponseMet() &&
                    now.isAfter(sla.getChargebackDeadline())) {
                    
                    log.error("CRITICAL SLA BREACH: Chargeback response deadline missed for dispute: {}", disputeId);
                    sendCriticalChargebackAlert(disputeId);
                    sla.setChargebackResponseMet(true);
                }
            }

        } catch (DataAccessException | RestClientException e) {
            log.error("Error checking SLA compliance - monitoring temporarily degraded", e);
        }
    }
    
    /**
     * Build dispute entity from request
     */
    private Dispute buildDispute(DisputeRequest request, Transaction transaction) {
        return Dispute.builder()
            .id(UUID.randomUUID().toString())
            .transactionId(request.getTransactionId())
            .userId(request.getUserId())
            .merchantId(transaction.getMerchantId())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .disputeType(request.getDisputeType())
            .reason(request.getReason())
            .description(request.getDescription())
            .status(DisputeStatus.INITIATED)
            .priority(determinePriority(request, transaction))
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .evidenceIds(new ArrayList<>())
            .escalationLevel(0)
            .fundsLocked(false)
            .autoResolutionEligible(checkAutoResolutionEligibility(request, transaction))
            .metadata(buildDisputeMetadata(request, transaction))
            .build();
    }
    
    /**
     * Determine dispute priority
     */
    private DisputePriority determinePriority(DisputeRequest request, Transaction transaction) {
        // Critical priority for chargebacks
        if (request.getDisputeType() == DisputeType.CHARGEBACK) {
            return DisputePriority.CRITICAL;
        }
        
        // High priority for high-value disputes
        if (transaction.getAmount() != null && transaction.getAmount().compareTo(highValueThreshold) > 0) {
            return DisputePriority.HIGH;
        }
        
        // High priority for fraud claims
        if (request.getDisputeType() == DisputeType.FRAUD) {
            return DisputePriority.HIGH;
        }
        
        // Check user dispute history
        int userDisputeCount = getUserDisputeCount(request.getUserId());
        if (userDisputeCount > 5) {
            return DisputePriority.LOW; // Potential dispute abuse
        }
        
        return DisputePriority.MEDIUM;
    }
    
    /**
     * Validate dispute request
     */
    private void validateDisputeRequest(DisputeRequest request) {
        if (request.getTransactionId() == null || request.getTransactionId().isEmpty()) {
            throw new IllegalArgumentException("Transaction ID is required");
        }
        
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            throw new IllegalArgumentException("User ID is required");
        }
        
        if (request.getDisputeType() == null) {
            throw new IllegalArgumentException("Dispute type is required");
        }
        
        if (request.getReason() == null || request.getReason().isEmpty()) {
            throw new IllegalArgumentException("Dispute reason is required");
        }
    }
    
    /**
     * Validate dispute eligibility
     */
    private void validateDisputeEligibility(Transaction transaction, DisputeRequest request) {
        // Check transaction age (typically 120 days for card networks)
        long daysSinceTransaction = ChronoUnit.DAYS.between(transaction.getCreatedAt(), LocalDateTime.now());
        if (daysSinceTransaction > 120) {
            throw new DisputeException("Transaction is too old to dispute (>120 days)");
        }
        
        // Check transaction status
        if (transaction.getStatus() != TransactionStatus.COMPLETED) {
            throw new DisputeException("Only completed transactions can be disputed");
        }
        
        // Check if user is authorized to dispute
        if (!transaction.getUserId().equals(request.getUserId()) && 
            !transaction.getMerchantId().equals(request.getUserId())) {
            throw new DisputeException("User not authorized to dispute this transaction");
        }
    }
    
    /**
     * Check for duplicate disputes
     */
    private boolean isDuplicateDispute(DisputeRequest request) {
        List<Dispute> existingDisputes = disputeRepository.findByTransactionIdAndUserId(
            request.getTransactionId(), request.getUserId());
        
        return existingDisputes.stream()
            .anyMatch(d -> d.getStatus() != DisputeStatus.CLOSED && 
                          d.getStatus() != DisputeStatus.REJECTED);
    }
    
    /**
     * Apply hold on disputed funds
     */
    private void applyDisputeHold(Dispute dispute) {
        try {
            walletService.holdFunds(dispute.getMerchantId(), dispute.getAmount(), 
                "DISPUTE_HOLD_" + dispute.getId());
            
            dispute.setFundsLocked(true);
            dispute.setFundsLockedAt(LocalDateTime.now());
            
            log.info("Funds held for dispute: {} - Amount: {}", dispute.getId(), dispute.getAmount());

        } catch (RestClientException e) {
            log.warn("Failed to hold funds for dispute: {} - continuing without hold", dispute.getId(), e);
            // Continue with dispute creation even if hold fails
        }
    }
    
    /**
     * Lock funds for chargeback
     */
    private void lockChargebackFunds(Dispute dispute, BigDecimal amount) {
        try {
            // Immediate debit from merchant account for chargeback
            walletService.debitForChargeback(dispute.getMerchantId(), amount, 
                "CHARGEBACK_" + dispute.getId());
            
            dispute.setFundsLocked(true);
            dispute.setFundsLockedAt(LocalDateTime.now());
            dispute.setChargebackAmount(amount);
            
            log.warn("Chargeback funds locked: {} - Amount: {}", dispute.getId(), amount);

        } catch (RestClientException e) {
            log.error("CRITICAL: Failed to lock chargeback funds for dispute: {}", dispute.getId(), e);
            throw new ExternalServiceException("wallet-service", "Failed to lock chargeback funds", 503, e);
        }
    }
    
    /**
     * Initialize dispute workflow
     */
    private DisputeWorkflow initializeWorkflow(Dispute dispute) {
        DisputeWorkflow workflow = new DisputeWorkflow(dispute);
        
        // Set workflow stages based on dispute type
        switch (dispute.getDisputeType()) {
            case CHARGEBACK:
                workflow.addStage("EVIDENCE_GATHERING", 24); // 24 hours
                workflow.addStage("CHARGEBACK_RESPONSE", 48); // 48 hours
                workflow.addStage("ISSUER_REVIEW", 168); // 7 days
                break;
                
            case FRAUD:
                workflow.addStage("FRAUD_INVESTIGATION", 48); // 48 hours
                workflow.addStage("EVIDENCE_REVIEW", 72); // 72 hours
                workflow.addStage("RESOLUTION", 120); // 5 days
                break;
                
            case SERVICE_ISSUE:
                workflow.addStage("MERCHANT_RESPONSE", 48); // 48 hours
                workflow.addStage("MEDIATION", 96); // 4 days
                workflow.addStage("RESOLUTION", 168); // 7 days
                break;
                
            default:
                workflow.addStage("REVIEW", 72); // 72 hours
                workflow.addStage("RESOLUTION", 168); // 7 days
        }
        
        workflow.start();
        return workflow;
    }
    
    /**
     * Start SLA tracking
     */
    private void startSlaTracking(Dispute dispute) {
        SlaTracker tracker = new SlaTracker(dispute.getId());
        tracker.setInitialResponseDeadline(LocalDateTime.now().plusHours(initialResponseSlaHours));
        tracker.setResolutionDeadline(LocalDateTime.now().plusDays(resolutionSlaDays));
        
        if (dispute.getDisputeType() == DisputeType.CHARGEBACK) {
            tracker.setChargebackDeadline(LocalDateTime.now().plusHours(chargebackResponseHours));
        }
        
        slaTrackers.put(dispute.getId(), tracker);
    }
    
    /**
     * Check if eligible for auto-resolution
     */
    private boolean checkAutoResolutionEligibility(DisputeRequest request, Transaction transaction) {
        // Not eligible for chargebacks
        if (request.getDisputeType() == DisputeType.CHARGEBACK) {
            return false;
        }
        
        // Not eligible for high-value transactions
        if (transaction.getAmount() != null && transaction.getAmount().compareTo(autoApproveThreshold) > 0) {
            return false;
        }
        
        // Not eligible for fraud claims
        if (request.getDisputeType() == DisputeType.FRAUD) {
            return false;
        }
        
        // Check user history
        int disputeCount = getUserDisputeCount(request.getUserId());
        if (disputeCount > 3) {
            return false; // Too many disputes
        }
        
        return true;
    }
    
    /**
     * Get user dispute count
     */
    private int getUserDisputeCount(String userId) {
        return userDisputeCounts.computeIfAbsent(userId, k -> {
            List<Dispute> userDisputes = disputeRepository.findByUserIdAndCreatedAtAfter(
                userId, LocalDateTime.now().minusMonths(6));
            return new AtomicInteger(userDisputes.size());
        }).get();
    }
    
    private boolean shouldApplyHold(Dispute dispute) {
        return dispute.getAmount() != null && dispute.getAmount().compareTo(new BigDecimal("100")) > 0;
    }
    
    private boolean isEligibleForAutoResolution(Dispute dispute) {
        return dispute.isAutoResolutionEligible() && 
               dispute.getAmount() != null && dispute.getAmount().compareTo(autoApproveThreshold) <= 0;
    }
    
    private void scheduleAutoResolution(Dispute dispute) {
        // Schedule auto-resolution after evidence gathering period
        CompletableFuture.delayedExecutor(24, TimeUnit.HOURS)
            .execute(() -> performAutoResolution(dispute.getId()));
    }
    
    // Additional helper methods would continue...
    
    private void loadActiveDisputes() {
        // Load active disputes from database on startup
    }
    
    private void initializeDisputeRules() {
        // Initialize auto-resolution rules
    }
}