package com.waqiti.merchant.kafka;

import com.waqiti.common.events.MerchantSettlementFailedEvent;
import com.waqiti.merchant.domain.MerchantAccount;
import com.waqiti.merchant.domain.SettlementEntry;
import com.waqiti.merchant.domain.SettlementStatus;
import com.waqiti.merchant.domain.SettlementFailureRecord;
import com.waqiti.merchant.repository.MerchantAccountRepository;
import com.waqiti.merchant.repository.SettlementRepository;
import com.waqiti.merchant.repository.SettlementFailureRepository;
import com.waqiti.merchant.service.MerchantNotificationService;
import com.waqiti.merchant.service.SettlementRetryService;
import com.waqiti.merchant.service.ComplianceService;
import com.waqiti.merchant.service.RiskAssessmentService;
import com.waqiti.merchant.service.SupportTicketService;
import com.waqiti.merchant.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumer for MerchantSettlementFailedEvent
 * Handles settlement failure recovery, merchant notifications, retry scheduling, and support escalation
 * Critical for maintaining merchant trust and operational continuity
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MerchantSettlementFailedConsumer {
    
    private final MerchantAccountRepository merchantAccountRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementFailureRepository settlementFailureRepository;
    private final MerchantNotificationService merchantNotificationService;
    private final SettlementRetryService settlementRetryService;
    private final ComplianceService complianceService;
    private final RiskAssessmentService riskAssessmentService;
    private final SupportTicketService supportTicketService;
    private final AuditService auditService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    // Deduplication tracking
    private final Map<String, LocalDateTime> processedEvents = new HashMap<>();
    private static final int DEDUP_WINDOW_MINUTES = 15;
    
    @KafkaListener(
        topics = "merchant.settlement.failed",
        groupId = "merchant-settlement-failure-handler",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleMerchantSettlementFailed(MerchantSettlementFailedEvent event) {
        log.error("Processing settlement failure: Settlement ID: {}, Merchant: {}, Amount: ${}, Reason: {}, Category: {}", 
            event.getSettlementId(), event.getMerchantId(), event.getGrossAmount(), 
            event.getFailureReason(), event.getFailureCategory());
        
        try {
            // STEP 1: Idempotency check
            if (isDuplicateEvent(event)) {
                log.info("Duplicate settlement failure event, skipping: {}", event.getEventId());
                return;
            }
            
            // STEP 2: Record settlement failure
            SettlementFailureRecord failureRecord = recordSettlementFailure(event);
            
            // STEP 3: Update merchant account status
            MerchantAccount merchant = updateMerchantAccountStatus(event);
            
            // STEP 4: Update settlement entry
            updateSettlementEntry(event);
            
            // STEP 5: Handle compliance violations
            if (event.isComplianceViolation()) {
                handleComplianceViolation(event, merchant, failureRecord);
            }
            
            // STEP 6: Assess retry eligibility and schedule retry
            if (event.isRetryable() && shouldRetry(event)) {
                scheduleSettlementRetry(event, failureRecord);
            }
            
            // STEP 7: Send merchant notifications
            sendMerchantNotifications(event, merchant, failureRecord);
            
            // STEP 8: Create support ticket if needed
            if (requiresSupportTicket(event)) {
                createSupportTicket(event, merchant, failureRecord);
            }
            
            // STEP 9: Update risk assessment
            updateRiskAssessment(event, merchant);
            
            // STEP 10: Publish failure analytics event
            publishFailureAnalytics(event, failureRecord);
            
            // STEP 11: Create audit trail
            createAuditTrail(event, merchant, failureRecord);
            
            // STEP 12: Mark event as processed
            markEventAsProcessed(event);
            
            log.info("Successfully processed settlement failure: Settlement ID: {}, Failure record ID: {}", 
                event.getSettlementId(), failureRecord.getId());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process settlement failure event for settlement {}: {}", 
                event.getSettlementId(), e.getMessage(), e);
            
            // Create critical alert
            createCriticalAlert(event, e);
            
            throw new RuntimeException("Settlement failure processing failed", e);
        }
    }
    
    private boolean isDuplicateEvent(MerchantSettlementFailedEvent event) {
        // Clean up old entries
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(DEDUP_WINDOW_MINUTES);
        processedEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        
        String eventKey = event.getEventId();
        if (processedEvents.containsKey(eventKey)) {
            return true;
        }
        
        // Also check database for persistent deduplication
        return settlementFailureRepository.existsByEventId(event.getEventId());
    }
    
    private SettlementFailureRecord recordSettlementFailure(MerchantSettlementFailedEvent event) {
        log.debug("Recording settlement failure for settlement: {}", event.getSettlementId());
        
        SettlementFailureRecord failureRecord = SettlementFailureRecord.builder()
            .eventId(event.getEventId())
            .settlementId(event.getSettlementId())
            .merchantId(event.getMerchantId())
            .grossAmount(event.getGrossAmount())
            .netAmount(event.getNetAmount())
            .totalFees(event.getTotalFees())
            .totalTax(event.getTotalTax())
            .currency(event.getCurrency())
            .transactionCount(event.getTransactionCount())
            .settlementPeriod(event.getSettlementPeriod())
            .failureReason(event.getFailureReason())
            .failureCategory(event.getFailureCategory())
            .failureCode(event.getFailureCode())
            .bankTransferId(event.getBankTransferId())
            .bankErrorCode(event.getBankErrorCode())
            .bankErrorMessage(event.getBankErrorMessage())
            .retryAttempt(event.getRetryAttempt() != null ? event.getRetryAttempt() : 0)
            .maxRetryAttempts(event.getMaxRetryAttempts() != null ? event.getMaxRetryAttempts() : 3)
            .retryable(event.isRetryable())
            .nextRetryAt(event.getNextRetryAt() != null ? 
                LocalDateTime.ofInstant(event.getNextRetryAt(), java.time.ZoneId.systemDefault()) : null)
            .complianceViolation(event.isComplianceViolation())
            .complianceFlag(event.getComplianceFlag())
            .amlViolation(event.isAmlViolation())
            .sanctionsViolation(event.isSanctionsViolation())
            .kycNonCompliant(event.isKycNonCompliant())
            .riskScore(event.getRiskScore())
            .highRisk(event.isHighRisk())
            .impactLevel(event.getImpactLevel())
            .requiresImmediateAction(event.isRequiresImmediateAction())
            .correlationId(event.getCorrelationId())
            .failedAt(LocalDateTime.now())
            .resolved(false)
            .build();
        
        failureRecord = settlementFailureRepository.save(failureRecord);
        
        log.info("Settlement failure recorded: Failure ID: {}, Settlement ID: {}", 
            failureRecord.getId(), event.getSettlementId());
        
        return failureRecord;
    }
    
    private MerchantAccount updateMerchantAccountStatus(MerchantSettlementFailedEvent event) {
        log.debug("Updating merchant account status for merchant: {}", event.getMerchantId());
        
        MerchantAccount merchant = merchantAccountRepository.findById(event.getMerchantId())
            .orElseThrow(() -> new RuntimeException("Merchant not found: " + event.getMerchantId()));
        
        // Increment failure count
        merchant.setSettlementFailureCount(
            (merchant.getSettlementFailureCount() != null ? merchant.getSettlementFailureCount() : 0) + 1
        );
        
        merchant.setLastSettlementFailureAt(LocalDateTime.now());
        merchant.setLastSettlementFailureReason(event.getFailureReason());
        
        // Handle compliance violations
        if (event.isComplianceViolation()) {
            merchant.setSettlementSuspended(true);
            merchant.setSuspensionReason(event.getFailureReason());
            merchant.setSuspendedAt(LocalDateTime.now());
            
            log.warn("Merchant settlement suspended due to compliance violation: Merchant: {}, Reason: {}", 
                event.getMerchantId(), event.getFailureReason());
        }
        
        // Handle repeated failures - suspend after 5 failures
        if (merchant.getSettlementFailureCount() >= 5) {
            merchant.setSettlementSuspended(true);
            merchant.setSuspensionReason("Excessive settlement failures: " + merchant.getSettlementFailureCount());
            merchant.setSuspendedAt(LocalDateTime.now());
            
            log.warn("Merchant settlement suspended due to excessive failures: Merchant: {}, Failures: {}", 
                event.getMerchantId(), merchant.getSettlementFailureCount());
        }
        
        // Handle bank account issues
        if ("BANK_TRANSFER".equals(event.getFailureCategory())) {
            merchant.setBankAccountVerified(false);
            merchant.setBankAccountIssue(event.getBankErrorMessage());
            
            log.warn("Merchant bank account marked as unverified: Merchant: {}, Issue: {}", 
                event.getMerchantId(), event.getBankErrorMessage());
        }
        
        merchantAccountRepository.save(merchant);
        
        log.info("Merchant account status updated: Merchant: {}, Suspended: {}, Failure count: {}", 
            event.getMerchantId(), merchant.isSettlementSuspended(), merchant.getSettlementFailureCount());
        
        return merchant;
    }
    
    private void updateSettlementEntry(MerchantSettlementFailedEvent event) {
        log.debug("Updating settlement entry for settlement: {}", event.getSettlementId());
        
        settlementRepository.findBySettlementIdAndMerchantId(
            event.getSettlementId(), event.getMerchantId()
        ).ifPresent(settlement -> {
            settlement.setStatus(SettlementStatus.FAILED);
            settlement.setFailureReason(event.getFailureReason());
            settlement.setFailureCategory(event.getFailureCategory());
            settlement.setFailureCode(event.getFailureCode());
            settlement.setBankErrorCode(event.getBankErrorCode());
            settlement.setBankErrorMessage(event.getBankErrorMessage());
            settlement.setRetryable(event.isRetryable());
            settlement.setRetryAttempt(event.getRetryAttempt() != null ? event.getRetryAttempt() : 0);
            settlement.setFailedAt(LocalDateTime.now());
            
            settlementRepository.save(settlement);
            
            log.info("Settlement entry updated to FAILED status: Settlement ID: {}", event.getSettlementId());
        });
    }
    
    private void handleComplianceViolation(MerchantSettlementFailedEvent event, MerchantAccount merchant, 
            SettlementFailureRecord failureRecord) {
        log.warn("Handling compliance violation for settlement: {}, Merchant: {}", 
            event.getSettlementId(), event.getMerchantId());
        
        try {
            // Create compliance case
            String caseId = complianceService.createComplianceCase(
                merchant.getId(),
                event.getSettlementId(),
                event.getComplianceFlag(),
                event.getFailureReason()
            );
            
            failureRecord.setComplianceCaseId(caseId);
            settlementFailureRepository.save(failureRecord);
            
            // Generate SAR if needed
            if (event.isSanctionsViolation() || event.isAmlViolation()) {
                complianceService.generateSAR(
                    merchant,
                    event.getSettlementId(),
                    event.getFailureReason()
                );
                
                log.warn("SAR generated for compliance violation: Case ID: {}, Settlement: {}", 
                    caseId, event.getSettlementId());
            }
            
            // Notify compliance team
            complianceService.notifyComplianceTeam(
                event.getMerchantId(),
                event.getSettlementId(),
                event.getComplianceFlag(),
                event.getFailureReason()
            );
            
            log.info("Compliance violation handled: Case ID: {}, Settlement: {}", 
                caseId, event.getSettlementId());
                
        } catch (Exception e) {
            log.error("Failed to handle compliance violation for settlement {}: {}", 
                event.getSettlementId(), e.getMessage(), e);
        }
    }
    
    private boolean shouldRetry(MerchantSettlementFailedEvent event) {
        // Don't retry compliance violations
        if (event.isComplianceViolation()) {
            log.info("Skipping retry for compliance violation: Settlement: {}", event.getSettlementId());
            return false;
        }
        
        // Don't retry if max attempts reached
        if (event.getRetryAttempt() != null && event.getMaxRetryAttempts() != null &&
            event.getRetryAttempt() >= event.getMaxRetryAttempts()) {
            log.info("Max retry attempts reached: Settlement: {}, Attempts: {}/{}", 
                event.getSettlementId(), event.getRetryAttempt(), event.getMaxRetryAttempts());
            return false;
        }
        
        // Retry based on failure category
        switch (event.getFailureCategory()) {
            case "BANK_TRANSFER":
                // Retry bank transfer failures (temporary issues)
                return "TEMPORARY_ERROR".equals(event.getFailureCode()) || 
                       "TIMEOUT".equals(event.getFailureCode());
                       
            case "INSUFFICIENT_FUNDS":
                // Don't retry insufficient funds
                return false;
                
            case "VALIDATION":
                // Don't retry validation errors
                return false;
                
            case "SYSTEM_ERROR":
                // Retry system errors
                return true;
                
            default:
                return false;
        }
    }
    
    private void scheduleSettlementRetry(MerchantSettlementFailedEvent event, SettlementFailureRecord failureRecord) {
        log.info("Scheduling settlement retry: Settlement: {}, Attempt: {}/{}", 
            event.getSettlementId(), 
            event.getRetryAttempt() != null ? event.getRetryAttempt() + 1 : 1,
            event.getMaxRetryAttempts());
        
        try {
            // Calculate retry delay with exponential backoff
            int retryAttempt = event.getRetryAttempt() != null ? event.getRetryAttempt() : 0;
            long delayMinutes = calculateRetryDelay(retryAttempt);
            
            LocalDateTime retryAt = LocalDateTime.now().plus(delayMinutes, ChronoUnit.MINUTES);
            
            // Schedule retry
            settlementRetryService.scheduleRetry(
                event.getSettlementId(),
                event.getMerchantId(),
                retryAt,
                retryAttempt + 1,
                event.getMaxRetryAttempts()
            );
            
            failureRecord.setRetryScheduled(true);
            failureRecord.setNextRetryAt(retryAt);
            settlementFailureRepository.save(failureRecord);
            
            log.info("Settlement retry scheduled: Settlement: {}, Retry at: {}, Attempt: {}", 
                event.getSettlementId(), retryAt, retryAttempt + 1);
                
        } catch (Exception e) {
            log.error("Failed to schedule settlement retry for {}: {}", 
                event.getSettlementId(), e.getMessage(), e);
        }
    }
    
    private long calculateRetryDelay(int retryAttempt) {
        // Exponential backoff: 5min, 15min, 60min
        switch (retryAttempt) {
            case 0: return 5;
            case 1: return 15;
            case 2: return 60;
            default: return 120;
        }
    }
    
    private void sendMerchantNotifications(MerchantSettlementFailedEvent event, MerchantAccount merchant, 
            SettlementFailureRecord failureRecord) {
        log.debug("Sending merchant notifications for failed settlement: {}", event.getSettlementId());
        
        try {
            // Determine notification urgency
            String urgency = event.isRequiresImmediateAction() ? "URGENT" : "NORMAL";
            
            // Send email notification
            merchantNotificationService.sendSettlementFailureEmail(
                merchant.getEmail(),
                event.getSettlementId(),
                event.getGrossAmount(),
                event.getCurrency(),
                event.getFailureReason(),
                event.isRetryable(),
                failureRecord.getNextRetryAt(),
                urgency
            );
            
            // Send SMS for critical failures
            if ("CRITICAL".equals(event.getImpactLevel()) || event.isComplianceViolation()) {
                merchantNotificationService.sendSettlementFailureSMS(
                    merchant.getPhone(),
                    event.getSettlementId(),
                    event.getFailureReason()
                );
            }
            
            // Send in-app notification
            merchantNotificationService.sendInAppNotification(
                merchant.getId(),
                "SETTLEMENT_FAILED",
                event.getSettlementId(),
                event.getFailureReason(),
                urgency
            );
            
            // Send dashboard alert
            merchantNotificationService.createDashboardAlert(
                merchant.getId(),
                "Settlement Failed",
                String.format("Settlement %s for $%s %s failed: %s", 
                    event.getSettlementId(), event.getGrossAmount(), event.getCurrency(), event.getFailureReason()),
                urgency
            );
            
            log.info("Merchant notifications sent for failed settlement: Settlement: {}, Merchant: {}", 
                event.getSettlementId(), event.getMerchantId());
                
        } catch (Exception e) {
            log.error("Failed to send merchant notifications for settlement {}: {}", 
                event.getSettlementId(), e.getMessage(), e);
        }
    }
    
    private boolean requiresSupportTicket(MerchantSettlementFailedEvent event) {
        // Always create ticket for compliance violations
        if (event.isComplianceViolation()) {
            return true;
        }
        
        // Create ticket for non-retryable failures
        if (!event.isRetryable()) {
            return true;
        }
        
        // Create ticket for critical impact
        if ("CRITICAL".equals(event.getImpactLevel())) {
            return true;
        }
        
        // Create ticket for bank account issues
        if ("BANK_TRANSFER".equals(event.getFailureCategory()) && 
            !"TEMPORARY_ERROR".equals(event.getFailureCode())) {
            return true;
        }
        
        // Create ticket after max retries reached
        if (event.getRetryAttempt() != null && event.getMaxRetryAttempts() != null &&
            event.getRetryAttempt() >= event.getMaxRetryAttempts()) {
            return true;
        }
        
        return false;
    }
    
    private void createSupportTicket(MerchantSettlementFailedEvent event, MerchantAccount merchant, 
            SettlementFailureRecord failureRecord) {
        log.info("Creating support ticket for failed settlement: Settlement: {}, Merchant: {}", 
            event.getSettlementId(), event.getMerchantId());
        
        try {
            String priority = event.isRequiresImmediateAction() ? "HIGH" : "MEDIUM";
            
            String ticketId = supportTicketService.createTicket(
                "Settlement Failure - " + event.getSettlementId(),
                String.format(
                    "Merchant settlement failed\n\n" +
                    "Settlement ID: %s\n" +
                    "Merchant: %s (%s)\n" +
                    "Amount: $%s %s\n" +
                    "Failure Reason: %s\n" +
                    "Category: %s\n" +
                    "Impact: %s\n" +
                    "Retryable: %s\n" +
                    "Retry Attempt: %d/%d\n\n" +
                    "Additional Details:\n%s",
                    event.getSettlementId(),
                    merchant.getName(),
                    event.getMerchantId(),
                    event.getGrossAmount(),
                    event.getCurrency(),
                    event.getFailureReason(),
                    event.getFailureCategory(),
                    event.getImpactLevel(),
                    event.isRetryable(),
                    event.getRetryAttempt() != null ? event.getRetryAttempt() : 0,
                    event.getMaxRetryAttempts() != null ? event.getMaxRetryAttempts() : 0,
                    event.getBankErrorMessage() != null ? event.getBankErrorMessage() : "N/A"
                ),
                priority,
                "SETTLEMENTS",
                event.getMerchantId(),
                event.getSettlementId()
            );
            
            failureRecord.setSupportTicketId(ticketId);
            settlementFailureRepository.save(failureRecord);
            
            log.info("Support ticket created: Ticket ID: {}, Settlement: {}", 
                ticketId, event.getSettlementId());
                
        } catch (Exception e) {
            log.error("Failed to create support ticket for settlement {}: {}", 
                event.getSettlementId(), e.getMessage(), e);
        }
    }
    
    private void updateRiskAssessment(MerchantSettlementFailedEvent event, MerchantAccount merchant) {
        log.debug("Updating risk assessment for merchant: {}", event.getMerchantId());
        
        try {
            riskAssessmentService.recordSettlementFailure(
                merchant.getId(),
                event.getSettlementId(),
                event.getFailureCategory(),
                event.getGrossAmount()
            );
            
            // Recalculate merchant risk score
            int newRiskScore = riskAssessmentService.calculateMerchantRiskScore(merchant.getId());
            
            merchant.setRiskScore(newRiskScore);
            merchantAccountRepository.save(merchant);
            
            log.info("Risk assessment updated: Merchant: {}, New risk score: {}", 
                event.getMerchantId(), newRiskScore);
                
        } catch (Exception e) {
            log.error("Failed to update risk assessment for merchant {}: {}", 
                event.getMerchantId(), e.getMessage(), e);
        }
    }
    
    private void publishFailureAnalytics(MerchantSettlementFailedEvent event, SettlementFailureRecord failureRecord) {
        log.debug("Publishing failure analytics for settlement: {}", event.getSettlementId());
        
        try {
            Map<String, Object> analyticsData = new HashMap<>();
            analyticsData.put("eventType", "settlement_failure");
            analyticsData.put("settlementId", event.getSettlementId());
            analyticsData.put("merchantId", event.getMerchantId());
            analyticsData.put("amount", event.getGrossAmount());
            analyticsData.put("currency", event.getCurrency());
            analyticsData.put("failureCategory", event.getFailureCategory());
            analyticsData.put("failureReason", event.getFailureReason());
            analyticsData.put("retryable", event.isRetryable());
            analyticsData.put("retryAttempt", event.getRetryAttempt());
            analyticsData.put("impactLevel", event.getImpactLevel());
            analyticsData.put("complianceViolation", event.isComplianceViolation());
            analyticsData.put("timestamp", event.getTimestamp());
            
            kafkaTemplate.send("analytics.settlement.failure", analyticsData);
            
            log.debug("Failure analytics published for settlement: {}", event.getSettlementId());
            
        } catch (Exception e) {
            log.error("Failed to publish failure analytics for settlement {}: {}", 
                event.getSettlementId(), e.getMessage());
        }
    }
    
    private void createAuditTrail(MerchantSettlementFailedEvent event, MerchantAccount merchant, 
            SettlementFailureRecord failureRecord) {
        log.debug("Creating audit trail for failed settlement: {}", event.getSettlementId());
        
        try {
            auditService.logSettlementFailure(
                event.getSettlementId(),
                merchant.getId(),
                merchant.getName(),
                event.getGrossAmount(),
                event.getCurrency(),
                event.getFailureReason(),
                event.getFailureCategory(),
                event.isComplianceViolation(),
                failureRecord.getId(),
                event.getCorrelationId()
            );
            
            log.debug("Audit trail created for failed settlement: {}", event.getSettlementId());
            
        } catch (Exception e) {
            log.error("Failed to create audit trail for settlement {}: {}", 
                event.getSettlementId(), e.getMessage());
        }
    }
    
    private void markEventAsProcessed(MerchantSettlementFailedEvent event) {
        processedEvents.put(event.getEventId(), LocalDateTime.now());
        log.debug("Marked event as processed: {}", event.getEventId());
    }
    
    private void createCriticalAlert(MerchantSettlementFailedEvent event, Exception exception) {
        try {
            Map<String, Object> alertData = new HashMap<>();
            alertData.put("eventType", "SETTLEMENT_FAILURE_PROCESSING_FAILED");
            alertData.put("settlementId", event.getSettlementId());
            alertData.put("merchantId", event.getMerchantId());
            alertData.put("amount", event.getGrossAmount());
            alertData.put("originalFailureReason", event.getFailureReason());
            alertData.put("processingError", exception.getMessage());
            alertData.put("severity", "CRITICAL");
            alertData.put("requiresImmediateAction", true);
            alertData.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("monitoring.critical-alerts", alertData);
            
            log.error("Created critical alert for settlement failure processing error: Settlement: {}", 
                event.getSettlementId());
                
        } catch (Exception e) {
            log.error("Failed to create critical alert: {}", e.getMessage());
        }
    }
}