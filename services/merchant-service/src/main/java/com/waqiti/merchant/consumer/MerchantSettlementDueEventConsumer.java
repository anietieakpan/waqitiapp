package com.waqiti.merchant.consumer;

import com.waqiti.common.events.MerchantSettlementDueEvent;
import com.waqiti.merchant.service.MerchantSettlementService;
import com.waqiti.merchant.service.PaymentProcessingService;
import com.waqiti.merchant.service.NotificationService;
import com.waqiti.merchant.service.RiskAssessmentService;
import com.waqiti.merchant.repository.ProcessedEventRepository;
import com.waqiti.merchant.repository.MerchantSettlementRepository;
import com.waqiti.merchant.model.ProcessedEvent;
import com.waqiti.merchant.model.MerchantSettlement;
import com.waqiti.merchant.model.SettlementStatus;
import com.waqiti.merchant.model.RiskLevel;
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
 * Consumer for MerchantSettlementDueEvent - Critical for merchant payment processing
 * Handles automatic settlements, risk checks, and payment distribution
 * ZERO TOLERANCE: All merchant settlements must be processed accurately and timely
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MerchantSettlementDueEventConsumer {
    
    private final MerchantSettlementService merchantSettlementService;
    private final PaymentProcessingService paymentProcessingService;
    private final NotificationService notificationService;
    private final RiskAssessmentService riskAssessmentService;
    private final ProcessedEventRepository processedEventRepository;
    private final MerchantSettlementRepository merchantSettlementRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal LARGE_SETTLEMENT_THRESHOLD = new BigDecimal("50000");
    private static final BigDecimal DAILY_VELOCITY_LIMIT = new BigDecimal("500000");
    
    @KafkaListener(
        topics = "merchant.settlement.due",
        groupId = "merchant-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for financial operations
    public void handleMerchantSettlementDue(MerchantSettlementDueEvent event) {
        log.info("Processing merchant settlement: Merchant {} - Amount: ${} - Due: {}", 
            event.getMerchantId(), event.getSettlementAmount(), event.getSettlementDue());
        
        // IDEMPOTENCY CHECK - Prevent duplicate settlements
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Merchant settlement already processed: {}", event.getEventId());
            return;
        }
        
        try {
            // Get settlement record
            MerchantSettlement settlement = merchantSettlementRepository.findById(event.getSettlementId())
                .orElseThrow(() -> new RuntimeException("Settlement not found: " + event.getSettlementId()));
            
            // STEP 1: Perform pre-settlement risk assessment
            performRiskAssessment(settlement, event);
            
            // STEP 2: Verify merchant account status and compliance
            verifyMerchantCompliance(settlement, event);
            
            // STEP 3: Calculate fees and deductions
            calculateSettlementAmount(settlement, event);
            
            // STEP 4: Perform velocity and limit checks
            performVelocityChecks(settlement, event);
            
            // STEP 5: Execute settlement payment
            executeSettlementPayment(settlement, event);
            
            // STEP 6: Update settlement status and records
            updateSettlementStatus(settlement, event);
            
            // STEP 7: Generate settlement report
            generateSettlementReport(settlement, event);
            
            // STEP 8: Send settlement notifications
            sendSettlementNotifications(settlement, event);
            
            // STEP 9: Create accounting entries
            createAccountingEntries(settlement, event);
            
            // STEP 10: Update merchant statistics
            updateMerchantStatistics(settlement, event);
            
            // STEP 11: Schedule next settlement if recurring
            if (event.isRecurringSettlement()) {
                scheduleNextSettlement(settlement, event);
            }
            
            // STEP 12: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("MerchantSettlementDueEvent")
                .processedAt(Instant.now())
                .settlementId(event.getSettlementId())
                .merchantId(event.getMerchantId())
                .settlementAmount(event.getSettlementAmount())
                .netAmount(settlement.getNetAmount())
                .status(settlement.getStatus())
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed merchant settlement: {} - Net amount: ${}", 
                event.getSettlementId(), settlement.getNetAmount());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process merchant settlement: {}", 
                event.getSettlementId(), e);
                
            // Create manual intervention record
            createManualInterventionRecord(event, e);
            
            throw new RuntimeException("Merchant settlement processing failed", e);
        }
    }
    
    private void performRiskAssessment(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        // Assess merchant risk profile
        RiskLevel riskLevel = riskAssessmentService.assessMerchantRisk(
            event.getMerchantId(),
            event.getSettlementAmount(),
            settlement.getTransactionCount(),
            settlement.getSettlementPeriod()
        );
        
        settlement.setRiskLevel(riskLevel);
        
        // Check for suspicious patterns
        boolean suspiciousActivity = riskAssessmentService.checkSuspiciousPatterns(
            event.getMerchantId(),
            LocalDateTime.now().minusDays(30)
        );
        
        if (suspiciousActivity) {
            settlement.addRiskFlag("SUSPICIOUS_PATTERN_DETECTED");
            settlement.setRequiresManualReview(true);
            
            log.warn("Suspicious activity detected for merchant {} - Settlement {} flagged for review", 
                event.getMerchantId(), event.getSettlementId());
        }
        
        // Check chargeback ratio
        double chargebackRatio = riskAssessmentService.calculateChargebackRatio(
            event.getMerchantId(),
            LocalDateTime.now().minusDays(90)
        );
        
        if (chargebackRatio > 0.02) { // 2% threshold
            settlement.addRiskFlag("HIGH_CHARGEBACK_RATIO");
            settlement.setChargebackRatio(chargebackRatio);
        }
        
        // Apply risk-based controls
        if (riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL) {
            settlement.setRequiresManualReview(true);
            settlement.setRiskHoldApplied(true);
            
            // Reduce settlement amount for high-risk merchants
            BigDecimal riskReduction = event.getSettlementAmount().multiply(new BigDecimal("0.10")); // 10% hold
            settlement.setRiskHoldAmount(riskReduction);
        }
        
        merchantSettlementRepository.save(settlement);
        
        log.info("Risk assessment completed for settlement {}: Risk Level: {}, Manual Review: {}", 
            settlement.getId(), riskLevel, settlement.isRequiresManualReview());
    }
    
    private void verifyMerchantCompliance(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        // Check merchant account status
        String merchantStatus = merchantSettlementService.getMerchantAccountStatus(event.getMerchantId());
        
        if (!"ACTIVE".equals(merchantStatus)) {
            settlement.setStatus(SettlementStatus.BLOCKED);
            settlement.addComplianceFlag("MERCHANT_ACCOUNT_INACTIVE");
            
            throw new RuntimeException("Cannot settle - Merchant account not active: " + merchantStatus);
        }
        
        // Check KYB (Know Your Business) compliance
        boolean kybCompliant = merchantSettlementService.isKYBCompliant(event.getMerchantId());
        
        if (!kybCompliant) {
            settlement.setStatus(SettlementStatus.COMPLIANCE_HOLD);
            settlement.addComplianceFlag("KYB_INCOMPLETE");
            
            throw new RuntimeException("Cannot settle - KYB compliance incomplete");
        }
        
        // Check tax reporting requirements
        boolean taxCompliant = merchantSettlementService.isTaxCompliant(
            event.getMerchantId(),
            event.getSettlementAmount()
        );
        
        if (!taxCompliant) {
            settlement.addComplianceFlag("TAX_REPORTING_REQUIRED");
            settlement.setTaxReportingRequired(true);
        }
        
        // Verify bank account details
        boolean validBankAccount = merchantSettlementService.validateBankAccount(event.getMerchantId());
        
        if (!validBankAccount) {
            settlement.setStatus(SettlementStatus.BANK_DETAILS_INVALID);
            settlement.addComplianceFlag("INVALID_BANK_DETAILS");
            
            throw new RuntimeException("Cannot settle - Invalid bank account details");
        }
        
        merchantSettlementRepository.save(settlement);
        
        log.info("Compliance verification completed for merchant {}: KYB: {}, Tax: {}, Bank: {}", 
            event.getMerchantId(), kybCompliant, taxCompliant, validBankAccount);
    }
    
    private void calculateSettlementAmount(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        BigDecimal grossAmount = event.getSettlementAmount();
        BigDecimal netAmount = grossAmount;
        
        // Calculate processing fees
        BigDecimal processingFees = merchantSettlementService.calculateProcessingFees(
            event.getMerchantId(),
            grossAmount,
            settlement.getTransactionCount()
        );
        settlement.setProcessingFees(processingFees);
        netAmount = netAmount.subtract(processingFees);
        
        // Calculate platform fees
        BigDecimal platformFees = merchantSettlementService.calculatePlatformFees(
            event.getMerchantId(),
            grossAmount
        );
        settlement.setPlatformFees(platformFees);
        netAmount = netAmount.subtract(platformFees);
        
        // Deduct chargeback reserves if applicable
        if (settlement.getChargebackRatio() != null && settlement.getChargebackRatio() > 0.01) {
            BigDecimal chargebackReserve = grossAmount.multiply(new BigDecimal("0.05")); // 5% reserve
            settlement.setChargebackReserve(chargebackReserve);
            netAmount = netAmount.subtract(chargebackReserve);
        }
        
        // Apply risk hold amount
        if (settlement.getRiskHoldAmount() != null) {
            netAmount = netAmount.subtract(settlement.getRiskHoldAmount());
        }
        
        // Calculate tax withholdings
        BigDecimal taxWithholding = merchantSettlementService.calculateTaxWithholding(
            event.getMerchantId(),
            grossAmount
        );
        settlement.setTaxWithholding(taxWithholding);
        netAmount = netAmount.subtract(taxWithholding);
        
        settlement.setGrossAmount(grossAmount);
        settlement.setNetAmount(netAmount);
        
        merchantSettlementRepository.save(settlement);
        
        log.info("Settlement amount calculated for {}: Gross: ${}, Net: ${}, Fees: ${}", 
            settlement.getId(), grossAmount, netAmount, processingFees.add(platformFees));
    }
    
    private void performVelocityChecks(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        // Check daily settlement velocity
        BigDecimal dailyTotal = merchantSettlementService.getDailySettlementTotal(
            event.getMerchantId(),
            LocalDateTime.now().toLocalDate()
        );
        
        if (dailyTotal.add(settlement.getNetAmount()).compareTo(DAILY_VELOCITY_LIMIT) > 0) {
            settlement.setStatus(SettlementStatus.VELOCITY_LIMIT_EXCEEDED);
            settlement.addRiskFlag("DAILY_VELOCITY_EXCEEDED");
            
            throw new RuntimeException("Daily velocity limit exceeded: " + dailyTotal.add(settlement.getNetAmount()));
        }
        
        // Check settlement frequency
        int settlementsToday = merchantSettlementService.getSettlementCountToday(event.getMerchantId());
        
        if (settlementsToday > 5) { // Max 5 settlements per day
            settlement.setStatus(SettlementStatus.FREQUENCY_LIMIT_EXCEEDED);
            settlement.addRiskFlag("SETTLEMENT_FREQUENCY_EXCEEDED");
            
            throw new RuntimeException("Settlement frequency limit exceeded: " + settlementsToday);
        }
        
        // Check for unusual settlement patterns
        boolean unusualPattern = riskAssessmentService.checkUnusualSettlementPattern(
            event.getMerchantId(),
            settlement.getNetAmount(),
            LocalDateTime.now().minusDays(30)
        );
        
        if (unusualPattern) {
            settlement.addRiskFlag("UNUSUAL_SETTLEMENT_PATTERN");
            settlement.setRequiresManualReview(true);
        }
        
        merchantSettlementRepository.save(settlement);
        
        log.info("Velocity checks completed for settlement {}: Daily total: ${}, Count: {}", 
            settlement.getId(), dailyTotal, settlementsToday);
    }
    
    private void executeSettlementPayment(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        // Skip payment execution if manual review required
        if (settlement.isRequiresManualReview()) {
            settlement.setStatus(SettlementStatus.PENDING_REVIEW);
            merchantSettlementRepository.save(settlement);
            
            log.info("Settlement {} held for manual review", settlement.getId());
            return;
        }
        
        try {
            // Execute ACH payment to merchant
            String paymentId = paymentProcessingService.executeACHPayment(
                event.getMerchantId(),
                settlement.getNetAmount(),
                "MERCHANT_SETTLEMENT",
                settlement.getId(),
                generateSettlementDescription(settlement, event)
            );
            
            settlement.setPaymentId(paymentId);
            settlement.setStatus(SettlementStatus.PAYMENT_INITIATED);
            settlement.setPaymentInitiatedAt(LocalDateTime.now());
            
            merchantSettlementRepository.save(settlement);
            
            // Publish payment initiated event
            Map<String, Object> paymentEvent = Map.of(
                "paymentId", paymentId,
                "merchantId", event.getMerchantId(),
                "amount", settlement.getNetAmount(),
                "settlementId", settlement.getId(),
                "type", "MERCHANT_SETTLEMENT"
            );
            
            kafkaTemplate.send("payment.initiated", paymentEvent);
            
            log.info("Settlement payment initiated for {}: Payment ID: {}, Amount: ${}", 
                settlement.getId(), paymentId, settlement.getNetAmount());
                
        } catch (Exception e) {
            settlement.setStatus(SettlementStatus.PAYMENT_FAILED);
            settlement.setPaymentError(e.getMessage());
            merchantSettlementRepository.save(settlement);
            
            log.error("Settlement payment failed for {}: {}", settlement.getId(), e.getMessage());
            throw e;
        }
    }
    
    private void updateSettlementStatus(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        // Update settlement timestamps
        settlement.setProcessedAt(LocalDateTime.now());
        settlement.setLastUpdated(LocalDateTime.now());
        
        // Set final status based on processing outcome
        if (settlement.getStatus() == null || settlement.getStatus() == SettlementStatus.PENDING) {
            if (settlement.isRequiresManualReview()) {
                settlement.setStatus(SettlementStatus.PENDING_REVIEW);
            } else if (settlement.getPaymentId() != null) {
                settlement.setStatus(SettlementStatus.PAYMENT_INITIATED);
            } else {
                settlement.setStatus(SettlementStatus.PROCESSED);
            }
        }
        
        // Calculate processing metrics
        long processingTimeMs = LocalDateTime.now().atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() 
            - event.getCreatedAt().toEpochMilli();
        settlement.setProcessingTimeMs(processingTimeMs);
        
        merchantSettlementRepository.save(settlement);
        
        log.info("Settlement status updated for {}: Status: {}, Processing time: {}ms", 
            settlement.getId(), settlement.getStatus(), processingTimeMs);
    }
    
    private void generateSettlementReport(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        // Generate detailed settlement report
        String reportId = merchantSettlementService.generateSettlementReport(
            settlement.getId(),
            event.getMerchantId(),
            settlement.getGrossAmount(),
            settlement.getNetAmount(),
            settlement.getProcessingFees(),
            settlement.getPlatformFees(),
            settlement.getTaxWithholding(),
            settlement.getTransactionCount(),
            settlement.getSettlementPeriod()
        );
        
        settlement.setReportId(reportId);
        
        // Create merchant statement entry
        merchantSettlementService.createStatementEntry(
            event.getMerchantId(),
            settlement.getId(),
            settlement.getNetAmount(),
            "SETTLEMENT_CREDIT",
            generateSettlementDescription(settlement, event)
        );
        
        merchantSettlementRepository.save(settlement);
        
        log.info("Settlement report generated for {}: Report ID: {}", settlement.getId(), reportId);
    }
    
    private void sendSettlementNotifications(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        // Send settlement confirmation email
        notificationService.sendSettlementConfirmation(
            event.getMerchantId(),
            settlement.getId(),
            settlement.getGrossAmount(),
            settlement.getNetAmount(),
            settlement.getPaymentId(),
            settlement.getReportId()
        );
        
        // Send SMS for large settlements
        if (settlement.getNetAmount().compareTo(LARGE_SETTLEMENT_THRESHOLD) > 0) {
            notificationService.sendLargeSettlementSMS(
                event.getMerchantId(),
                settlement.getNetAmount(),
                settlement.getPaymentId()
            );
        }
        
        // Send webhook notification
        notificationService.sendSettlementWebhook(
            event.getMerchantId(),
            Map.of(
                "settlementId", settlement.getId(),
                "grossAmount", settlement.getGrossAmount(),
                "netAmount", settlement.getNetAmount(),
                "status", settlement.getStatus().toString(),
                "paymentId", settlement.getPaymentId(),
                "processedAt", settlement.getProcessedAt()
            )
        );
        
        // Send alerts if manual review required
        if (settlement.isRequiresManualReview()) {
            notificationService.sendManualReviewAlert(
                settlement.getId(),
                event.getMerchantId(),
                settlement.getRiskFlags(),
                settlement.getNetAmount()
            );
        }
        
        log.info("Settlement notifications sent for {}", settlement.getId());
    }
    
    private void createAccountingEntries(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        // Create double-entry accounting records
        List<Map<String, Object>> journalEntries = new ArrayList<>();
        
        // Debit: Merchant Settlements Payable
        journalEntries.add(Map.of(
            "account", "MERCHANT_SETTLEMENTS_PAYABLE",
            "type", "DEBIT",
            "amount", settlement.getNetAmount(),
            "description", "Settlement to merchant " + event.getMerchantId(),
            "reference", settlement.getId()
        ));
        
        // Credit: Cash/Bank Account
        journalEntries.add(Map.of(
            "account", "CASH_MERCHANT_SETTLEMENTS",
            "type", "CREDIT",
            "amount", settlement.getNetAmount(),
            "description", "Settlement payment to merchant " + event.getMerchantId(),
            "reference", settlement.getId()
        ));
        
        // Credit: Fee Revenue for processing fees
        if (settlement.getProcessingFees().compareTo(BigDecimal.ZERO) > 0) {
            journalEntries.add(Map.of(
                "account", "PROCESSING_FEE_REVENUE",
                "type", "CREDIT",
                "amount", settlement.getProcessingFees(),
                "description", "Processing fees from settlement " + settlement.getId(),
                "reference", settlement.getId()
            ));
        }
        
        // Create journal entry batch
        String journalEntryId = merchantSettlementService.createJournalEntries(
            journalEntries,
            "MERCHANT_SETTLEMENT",
            settlement.getId()
        );
        
        settlement.setJournalEntryId(journalEntryId);
        merchantSettlementRepository.save(settlement);
        
        log.info("Accounting entries created for settlement {}: Journal ID: {}", 
            settlement.getId(), journalEntryId);
    }
    
    private void updateMerchantStatistics(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        // Update merchant lifetime statistics
        merchantSettlementService.updateMerchantStats(
            event.getMerchantId(),
            settlement.getGrossAmount(),
            settlement.getNetAmount(),
            settlement.getProcessingFees(),
            settlement.getTransactionCount()
        );
        
        // Update settlement performance metrics
        merchantSettlementService.updateSettlementMetrics(
            settlement.getId(),
            settlement.getProcessingTimeMs(),
            settlement.getStatus(),
            settlement.getRiskLevel()
        );
        
        log.info("Merchant statistics updated for {}", event.getMerchantId());
    }
    
    private void scheduleNextSettlement(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        if (event.getNextSettlementDate() != null) {
            merchantSettlementService.scheduleNextSettlement(
                event.getMerchantId(),
                event.getNextSettlementDate(),
                event.getSettlementFrequency(),
                settlement.getId()
            );
            
            log.info("Next settlement scheduled for merchant {} on {}", 
                event.getMerchantId(), event.getNextSettlementDate());
        }
    }
    
    private String generateSettlementDescription(MerchantSettlement settlement, MerchantSettlementDueEvent event) {
        return String.format("Settlement for period %s - %d transactions totaling $%.2f", 
            settlement.getSettlementPeriod(),
            settlement.getTransactionCount(),
            settlement.getGrossAmount());
    }
    
    private void createManualInterventionRecord(MerchantSettlementDueEvent event, Exception exception) {
        manualInterventionService.createTask(
            "MERCHANT_SETTLEMENT_PROCESSING_FAILED",
            String.format(
                "Failed to process merchant settlement. " +
                "Settlement ID: %s, Merchant ID: %s, Amount: $%.2f. " +
                "Merchant may not have received payment. " +
                "Exception: %s. Manual intervention required.",
                event.getSettlementId(),
                event.getMerchantId(),
                event.getSettlementAmount(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
    }
}