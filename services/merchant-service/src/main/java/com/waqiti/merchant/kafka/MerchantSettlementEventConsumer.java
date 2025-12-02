package com.waqiti.merchant.kafka;

import com.waqiti.common.events.MerchantSettlementEvent;
import com.waqiti.common.events.SettlementProcessedEvent;
import com.waqiti.merchant.domain.MerchantAccount;
import com.waqiti.merchant.domain.SettlementEntry;
import com.waqiti.merchant.domain.SettlementStatus;
import com.waqiti.merchant.domain.SettlementType;
import com.waqiti.merchant.repository.MerchantAccountRepository;
import com.waqiti.merchant.repository.SettlementRepository;
import com.waqiti.merchant.service.SettlementService;
import com.waqiti.merchant.service.BankTransferService;
import com.waqiti.merchant.service.ComplianceService;
import com.waqiti.merchant.service.NotificationService;
import com.waqiti.merchant.service.AuditService;
import com.waqiti.merchant.service.RiskAssessmentService;
import com.waqiti.merchant.service.TaxCalculationService;
import com.waqiti.merchant.service.FeeCalculationService;
import com.waqiti.merchant.exception.SettlementException;
import com.waqiti.merchant.exception.ComplianceViolationException;
import com.waqiti.merchant.exception.InsufficientFundsException;
import com.waqiti.common.security.encryption.EncryptionService;
import com.waqiti.common.compliance.ComplianceValidator;
import com.waqiti.common.audit.AuditEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * CRITICAL MERCHANT SETTLEMENT EVENT CONSUMER - Consumer 38
 * 
 * Processes merchant settlement events with zero-tolerance 12-step processing:
 * 1. Event validation and sanitization
 * 2. Idempotency and duplicate detection
 * 3. Regulatory compliance verification
 * 4. Merchant account validation
 * 5. Risk assessment and scoring
 * 6. Fee calculation and verification
 * 7. Tax calculation and withholding
 * 8. Settlement amount verification
 * 9. Bank transfer initiation
 * 10. Compliance reporting
 * 11. Audit trail creation
 * 12. Notification dispatch
 * 
 * REGULATORY COMPLIANCE:
 * - PCI DSS Level 1 compliance for payment data
 * - Anti-Money Laundering (AML) checks
 * - Know Your Customer (KYC) verification
 * - OFAC sanctions screening
 * - Tax withholding regulations
 * 
 * SLA: 99.99% uptime, <5s processing time
 * 
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 1.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Validated
public class MerchantSettlementEventConsumer {
    
    private final MerchantAccountRepository merchantAccountRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementService settlementService;
    private final BankTransferService bankTransferService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final RiskAssessmentService riskAssessmentService;
    private final TaxCalculationService taxCalculationService;
    private final FeeCalculationService feeCalculationService;
    private final EncryptionService encryptionService;
    private final ComplianceValidator complianceValidator;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final String SETTLEMENT_PROCESSED_TOPIC = "settlement-processed-events";
    private static final String COMPLIANCE_ALERT_TOPIC = "compliance-alert-events";
    private static final String DLQ_TOPIC = "merchant-settlement-events-dlq";
    
    private static final BigDecimal MAX_SETTLEMENT_AMOUNT = new BigDecimal("10000000.00");
    private static final BigDecimal MIN_SETTLEMENT_AMOUNT = new BigDecimal("1.00");
    private static final int MAX_RETRIES = 3;
    private static final long PROCESSING_TIMEOUT_MS = 30000;

    @KafkaListener(
        topics = "merchant-settlement-events",
        groupId = "merchant-settlement-processor",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    @Retryable(
        value = {SettlementException.class, InsufficientFundsException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 10000)
    )
    public void handleMerchantSettlementEvent(
            @Payload @Valid MerchantSettlementEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            Acknowledgment acknowledgment) {
        
        String correlationId = generateCorrelationId(event, partition, offset);
        long processingStartTime = System.currentTimeMillis();
        
        log.info("STEP 1: Processing merchant settlement event - ID: {}, Merchant: {}, Amount: {}, Correlation: {}",
            event.getSettlementId(), event.getMerchantId(), event.getGrossAmount(), correlationId);
        
        try {
            // STEP 1: Event validation and sanitization
            validateAndSanitizeEvent(event, correlationId);
            
            // STEP 2: Idempotency and duplicate detection
            if (checkIdempotencyAndDuplicates(event, correlationId)) {
                acknowledgeAndReturn(acknowledgment, "Duplicate settlement event detected");
                return;
            }
            
            // STEP 3: Regulatory compliance verification
            performComplianceVerification(event, correlationId);
            
            // STEP 4: Merchant account validation
            MerchantAccount merchantAccount = validateMerchantAccount(event, correlationId);
            
            // STEP 5: Risk assessment and scoring
            performRiskAssessment(event, merchantAccount, correlationId);
            
            // STEP 6: Fee calculation and verification
            FeeCalculationResult feeResult = calculateAndVerifyFees(event, merchantAccount, correlationId);
            
            // STEP 7: Tax calculation and withholding
            TaxCalculationResult taxResult = calculateTaxes(event, merchantAccount, feeResult, correlationId);
            
            // STEP 8: Settlement amount verification
            BigDecimal finalSettlementAmount = verifySettlementAmount(event, feeResult, taxResult, correlationId);
            
            // STEP 9: Bank transfer initiation
            BankTransferResult transferResult = initiateBankTransfer(event, merchantAccount, finalSettlementAmount, correlationId);
            
            // STEP 10: Compliance reporting
            performComplianceReporting(event, merchantAccount, feeResult, taxResult, transferResult, correlationId);
            
            // STEP 11: Audit trail creation
            createAuditTrail(event, merchantAccount, feeResult, taxResult, transferResult, correlationId, processingStartTime);
            
            // STEP 12: Notification dispatch
            dispatchNotifications(event, merchantAccount, finalSettlementAmount, transferResult, correlationId);
            
            // Acknowledge successful processing
            acknowledgment.acknowledge();
            
            long processingTime = System.currentTimeMillis() - processingStartTime;
            log.info("Successfully processed merchant settlement - ID: {}, Time: {}ms, Correlation: {}",
                event.getSettlementId(), processingTime, correlationId);
            
        } catch (ComplianceViolationException e) {
            handleComplianceViolation(event, e, correlationId, acknowledgment);
        } catch (InsufficientFundsException e) {
            handleInsufficientFunds(event, e, correlationId, acknowledgment);
        } catch (SettlementException e) {
            handleSettlementError(event, e, correlationId, acknowledgment);
        } catch (Exception e) {
            handleCriticalError(event, e, correlationId, acknowledgment);
        }
    }

    /**
     * STEP 1: Event validation and sanitization
     */
    private void validateAndSanitizeEvent(MerchantSettlementEvent event, String correlationId) {
        log.debug("STEP 1: Validating settlement event - Correlation: {}", correlationId);
        
        if (event == null) {
            throw new IllegalArgumentException("Settlement event cannot be null");
        }
        
        if (event.getSettlementId() == null || event.getSettlementId().trim().isEmpty()) {
            throw new IllegalArgumentException("Settlement ID is required");
        }
        
        if (event.getMerchantId() == null || event.getMerchantId().trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant ID is required");
        }
        
        if (event.getGrossAmount() == null || event.getGrossAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid gross amount: " + event.getGrossAmount());
        }
        
        if (event.getGrossAmount().compareTo(MAX_SETTLEMENT_AMOUNT) > 0) {
            throw new SettlementException("Settlement amount exceeds maximum limit: " + MAX_SETTLEMENT_AMOUNT);
        }
        
        if (event.getGrossAmount().compareTo(MIN_SETTLEMENT_AMOUNT) < 0) {
            throw new SettlementException("Settlement amount below minimum limit: " + MIN_SETTLEMENT_AMOUNT);
        }
        
        if (event.getCurrency() == null || event.getCurrency().trim().isEmpty()) {
            event.setCurrency("USD"); // Default currency
        }
        
        if (event.getTransactionCount() == null || event.getTransactionCount() <= 0) {
            throw new IllegalArgumentException("Invalid transaction count: " + event.getTransactionCount());
        }
        
        // Sanitize string fields
        event.setSettlementId(sanitizeString(event.getSettlementId()));
        event.setMerchantId(sanitizeString(event.getMerchantId()));
        event.setCurrency(sanitizeString(event.getCurrency().toUpperCase()));
        
        log.debug("STEP 1: Event validation completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 2: Idempotency and duplicate detection
     */
    private boolean checkIdempotencyAndDuplicates(MerchantSettlementEvent event, String correlationId) {
        log.debug("STEP 2: Checking idempotency - Correlation: {}", correlationId);
        
        boolean isDuplicate = settlementRepository.existsBySettlementIdAndMerchantId(
            event.getSettlementId(), event.getMerchantId());
        
        if (isDuplicate) {
            log.warn("Duplicate settlement detected - ID: {}, Merchant: {}, Correlation: {}",
                event.getSettlementId(), event.getMerchantId(), correlationId);
            
            auditService.logEvent(AuditEventType.DUPLICATE_SETTLEMENT_DETECTED, 
                event.getMerchantId(), event.getSettlementId(), correlationId);
        }
        
        return isDuplicate;
    }

    /**
     * STEP 3: Regulatory compliance verification
     */
    private void performComplianceVerification(MerchantSettlementEvent event, String correlationId) {
        log.debug("STEP 3: Performing compliance verification - Correlation: {}", correlationId);
        
        // AML screening
        ComplianceResult amlResult = complianceService.performAMLScreening(event.getMerchantId(), event.getGrossAmount());
        if (amlResult.hasViolations()) {
            throw new ComplianceViolationException("AML violations detected: " + amlResult.getViolations());
        }
        
        // OFAC sanctions screening
        ComplianceResult sanctionsResult = complianceService.performSanctionsScreening(event.getMerchantId());
        if (sanctionsResult.hasViolations()) {
            throw new ComplianceViolationException("Sanctions violations detected: " + sanctionsResult.getViolations());
        }
        
        // KYC verification
        if (!complianceService.isKYCCompliant(event.getMerchantId())) {
            throw new ComplianceViolationException("Merchant KYC not compliant: " + event.getMerchantId());
        }
        
        log.debug("STEP 3: Compliance verification completed - Correlation: {}", correlationId);
    }

    /**
     * STEP 4: Merchant account validation
     */
    private MerchantAccount validateMerchantAccount(MerchantSettlementEvent event, String correlationId) {
        log.debug("STEP 4: Validating merchant account - Correlation: {}", correlationId);
        
        MerchantAccount merchant = merchantAccountRepository.findById(event.getMerchantId())
            .orElseThrow(() -> new SettlementException("Merchant account not found: " + event.getMerchantId()));
        
        if (!merchant.isActive()) {
            throw new SettlementException("Merchant account is inactive: " + event.getMerchantId());
        }
        
        if (merchant.isSettlementSuspended()) {
            throw new SettlementException("Settlement suspended for merchant: " + event.getMerchantId());
        }
        
        if (merchant.getBankAccountId() == null) {
            throw new SettlementException("No verified bank account for merchant: " + event.getMerchantId());
        }
        
        if (!bankTransferService.isBankAccountActive(merchant.getBankAccountId())) {
            throw new SettlementException("Merchant bank account is inactive: " + merchant.getBankAccountId());
        }
        
        log.debug("STEP 4: Merchant account validation completed - Correlation: {}", correlationId);
        return merchant;
    }

    /**
     * STEP 5: Risk assessment and scoring
     */
    private void performRiskAssessment(MerchantSettlementEvent event, MerchantAccount merchant, String correlationId) {
        log.debug("STEP 5: Performing risk assessment - Correlation: {}", correlationId);
        
        RiskAssessmentResult riskResult = riskAssessmentService.assessSettlementRisk(
            event.getMerchantId(), event.getGrossAmount(), event.getTransactionCount());
        
        if (riskResult.getRiskScore() > 80) {
            log.warn("High risk settlement detected - Score: {}, Merchant: {}, Correlation: {}",
                riskResult.getRiskScore(), event.getMerchantId(), correlationId);
            
            // Trigger enhanced monitoring
            complianceService.triggerEnhancedMonitoring(event.getMerchantId(), riskResult);
        }
        
        if (riskResult.getRiskScore() > 95) {
            throw new SettlementException("Settlement blocked due to high risk score: " + riskResult.getRiskScore());
        }
        
        log.debug("STEP 5: Risk assessment completed - Score: {}, Correlation: {}",
            riskResult.getRiskScore(), correlationId);
    }

    /**
     * STEP 6: Fee calculation and verification
     */
    private FeeCalculationResult calculateAndVerifyFees(MerchantSettlementEvent event, MerchantAccount merchant, String correlationId) {
        log.debug("STEP 6: Calculating fees - Correlation: {}", correlationId);
        
        FeeCalculationResult feeResult = feeCalculationService.calculateSettlementFees(
            merchant, event.getGrossAmount(), event.getTransactionCount(), event.getCurrency());
        
        // Verify fee calculations
        if (feeResult.getTotalFees().compareTo(event.getGrossAmount()) >= 0) {
            throw new SettlementException("Total fees exceed gross amount");
        }
        
        if (feeResult.getNetAmount().compareTo(MIN_SETTLEMENT_AMOUNT) < 0) {
            throw new SettlementException("Net settlement amount below minimum threshold");
        }
        
        log.debug("STEP 6: Fee calculation completed - Total: {}, Net: {}, Correlation: {}",
            feeResult.getTotalFees(), feeResult.getNetAmount(), correlationId);
        
        return feeResult;
    }

    /**
     * STEP 7: Tax calculation and withholding
     */
    private TaxCalculationResult calculateTaxes(MerchantSettlementEvent event, MerchantAccount merchant,
            FeeCalculationResult feeResult, String correlationId) {
        log.debug("STEP 7: Calculating taxes - Correlation: {}", correlationId);
        
        TaxCalculationResult taxResult = taxCalculationService.calculateTaxes(
            merchant, event.getGrossAmount(), feeResult.getNetAmount());
        
        // Validate tax calculations
        if (taxResult.getTotalTax().compareTo(BigDecimal.ZERO) < 0) {
            throw new SettlementException("Invalid tax calculation");
        }
        
        log.debug("STEP 7: Tax calculation completed - Tax: {}, Correlation: {}",
            taxResult.getTotalTax(), correlationId);
        
        return taxResult;
    }

    /**
     * STEP 8: Settlement amount verification
     */
    private BigDecimal verifySettlementAmount(MerchantSettlementEvent event, FeeCalculationResult feeResult,
            TaxCalculationResult taxResult, String correlationId) {
        log.debug("STEP 8: Verifying settlement amount - Correlation: {}", correlationId);
        
        BigDecimal finalAmount = event.getGrossAmount()
            .subtract(feeResult.getTotalFees())
            .subtract(taxResult.getTotalTax())
            .setScale(2, RoundingMode.HALF_UP);
        
        if (finalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InsufficientFundsException("Final settlement amount is zero or negative: " + finalAmount);
        }
        
        if (finalAmount.compareTo(MIN_SETTLEMENT_AMOUNT) < 0) {
            throw new SettlementException("Final settlement amount below minimum: " + finalAmount);
        }
        
        log.debug("STEP 8: Settlement amount verified - Final: {}, Correlation: {}", finalAmount, correlationId);
        return finalAmount;
    }

    /**
     * STEP 9: Bank transfer initiation
     */
    private BankTransferResult initiateBankTransfer(MerchantSettlementEvent event, MerchantAccount merchant,
            BigDecimal finalAmount, String correlationId) {
        log.debug("STEP 9: Initiating bank transfer - Correlation: {}", correlationId);
        
        BankTransferRequest transferRequest = BankTransferRequest.builder()
            .merchantId(merchant.getId())
            .settlementId(event.getSettlementId())
            .bankAccountId(merchant.getBankAccountId())
            .amount(finalAmount)
            .currency(event.getCurrency())
            .description(String.format("Settlement for period %s", event.getSettlementPeriod()))
            .correlationId(correlationId)
            .build();
        
        BankTransferResult transferResult = bankTransferService.initiateTransfer(transferRequest);
        
        if (!transferResult.isSuccessful()) {
            throw new SettlementException("Bank transfer initiation failed: " + transferResult.getErrorMessage());
        }
        
        log.debug("STEP 9: Bank transfer initiated - Transfer ID: {}, Correlation: {}",
            transferResult.getTransferId(), correlationId);
        
        return transferResult;
    }

    /**
     * STEP 10: Compliance reporting
     */
    private void performComplianceReporting(MerchantSettlementEvent event, MerchantAccount merchant,
            FeeCalculationResult feeResult, TaxCalculationResult taxResult, BankTransferResult transferResult,
            String correlationId) {
        log.debug("STEP 10: Performing compliance reporting - Correlation: {}", correlationId);
        
        // Generate required compliance reports asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                complianceService.generateSettlementReport(event, merchant, feeResult, taxResult, transferResult);
                
                // Check if CTR (Currency Transaction Report) is required
                if (event.getGrossAmount().compareTo(new BigDecimal("10000")) >= 0) {
                    complianceService.generateCTR(event, merchant, correlationId);
                }
                
                // Check if SAR (Suspicious Activity Report) is required
                if (complianceService.requiresSAR(event, merchant)) {
                    complianceService.generateSAR(event, merchant, correlationId);
                }
                
            } catch (Exception e) {
                log.error("Failed to generate compliance reports - Correlation: {}", correlationId, e);
            }
        });
        
        log.debug("STEP 10: Compliance reporting initiated - Correlation: {}", correlationId);
    }

    /**
     * STEP 11: Audit trail creation
     */
    private void createAuditTrail(MerchantSettlementEvent event, MerchantAccount merchant,
            FeeCalculationResult feeResult, TaxCalculationResult taxResult, BankTransferResult transferResult,
            String correlationId, long processingStartTime) {
        log.debug("STEP 11: Creating audit trail - Correlation: {}", correlationId);
        
        SettlementEntry settlement = SettlementEntry.builder()
            .settlementId(event.getSettlementId())
            .merchantId(event.getMerchantId())
            .grossAmount(event.getGrossAmount())
            .totalFees(feeResult.getTotalFees())
            .totalTax(taxResult.getTotalTax())
            .netAmount(feeResult.getNetAmount().subtract(taxResult.getTotalTax()))
            .currency(event.getCurrency())
            .transactionCount(event.getTransactionCount())
            .settlementPeriod(event.getSettlementPeriod())
            .transferId(transferResult.getTransferId())
            .status(SettlementStatus.PROCESSING)
            .type(SettlementType.REGULAR)
            .correlationId(correlationId)
            .processedAt(LocalDateTime.now())
            .processingTimeMs(System.currentTimeMillis() - processingStartTime)
            .build();
        
        settlementRepository.save(settlement);
        
        // Create detailed audit log
        auditService.logSettlementEvent(
            event, merchant, feeResult, taxResult, transferResult, correlationId);
        
        log.debug("STEP 11: Audit trail created - Correlation: {}", correlationId);
    }

    /**
     * STEP 12: Notification dispatch
     */
    private void dispatchNotifications(MerchantSettlementEvent event, MerchantAccount merchant,
            BigDecimal finalAmount, BankTransferResult transferResult, String correlationId) {
        log.debug("STEP 12: Dispatching notifications - Correlation: {}", correlationId);
        
        // Send merchant notification
        CompletableFuture.runAsync(() -> {
            notificationService.sendSettlementNotification(
                merchant.getId(),
                event.getSettlementId(),
                finalAmount,
                event.getCurrency(),
                transferResult.getTransferId()
            );
        });
        
        // Send internal notifications
        CompletableFuture.runAsync(() -> {
            notificationService.sendInternalSettlementAlert(
                event, merchant, finalAmount, transferResult, correlationId);
        });
        
        // Publish settlement processed event
        SettlementProcessedEvent processedEvent = SettlementProcessedEvent.builder()
            .settlementId(event.getSettlementId())
            .merchantId(event.getMerchantId())
            .netAmount(finalAmount)
            .status("PROCESSED")
            .transferId(transferResult.getTransferId())
            .correlationId(correlationId)
            .processedAt(Instant.now())
            .build();
        
        kafkaTemplate.send(SETTLEMENT_PROCESSED_TOPIC, processedEvent);
        
        log.debug("STEP 12: Notifications dispatched - Correlation: {}", correlationId);
    }

    // Error handling methods
    private void handleComplianceViolation(MerchantSettlementEvent event, ComplianceViolationException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Compliance violation in settlement - ID: {}, Error: {}, Correlation: {}",
            event.getSettlementId(), e.getMessage(), correlationId);
        
        // Block merchant account
        merchantAccountRepository.findById(event.getMerchantId()).ifPresent(merchant -> {
            merchant.setSettlementSuspended(true);
            merchant.setSuspensionReason("COMPLIANCE_VIOLATION: " + e.getMessage());
            merchantAccountRepository.save(merchant);
        });
        
        // Send compliance alert
        kafkaTemplate.send(COMPLIANCE_ALERT_TOPIC, Map.of(
            "eventType", "COMPLIANCE_VIOLATION",
            "settlementId", event.getSettlementId(),
            "merchantId", event.getMerchantId(),
            "violation", e.getMessage(),
            "correlationId", correlationId
        ));
        
        acknowledgment.acknowledge();
    }

    private void handleInsufficientFunds(MerchantSettlementEvent event, InsufficientFundsException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Insufficient funds for settlement - ID: {}, Error: {}, Correlation: {}",
            event.getSettlementId(), e.getMessage(), correlationId);
        
        // Create settlement record with failed status
        SettlementEntry settlement = SettlementEntry.builder()
            .settlementId(event.getSettlementId())
            .merchantId(event.getMerchantId())
            .grossAmount(event.getGrossAmount())
            .status(SettlementStatus.FAILED)
            .failureReason(e.getMessage())
            .correlationId(correlationId)
            .processedAt(LocalDateTime.now())
            .build();
        
        settlementRepository.save(settlement);
        acknowledgment.acknowledge();
    }

    private void handleSettlementError(MerchantSettlementEvent event, SettlementException e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Settlement processing error - ID: {}, Error: {}, Correlation: {}",
            event.getSettlementId(), e.getMessage(), correlationId);
        
        sendToDeadLetterQueue(event, e, correlationId);
        acknowledgment.acknowledge();
    }

    private void handleCriticalError(MerchantSettlementEvent event, Exception e,
            String correlationId, Acknowledgment acknowledgment) {
        log.error("Critical error in settlement processing - ID: {}, Error: {}, Correlation: {}",
            event.getSettlementId(), e.getMessage(), e, correlationId);
        
        sendToDeadLetterQueue(event, e, correlationId);
        
        // Send critical alert
        notificationService.sendCriticalAlert(
            "SETTLEMENT_PROCESSING_ERROR",
            String.format("Critical error processing settlement %s: %s", event.getSettlementId(), e.getMessage()),
            correlationId
        );
        
        acknowledgment.acknowledge();
    }

    // Utility methods
    private String generateCorrelationId(MerchantSettlementEvent event, int partition, long offset) {
        return String.format("settlement-%s-p%d-o%d-%d",
            event.getSettlementId(), partition, offset, System.currentTimeMillis());
    }

    private String sanitizeString(String input) {
        if (input == null) return null;
        return input.trim().replaceAll("[<>\"'&]", "");
    }

    private void acknowledgeAndReturn(Acknowledgment acknowledgment, String message) {
        log.info(message);
        acknowledgment.acknowledge();
    }

    private void sendToDeadLetterQueue(MerchantSettlementEvent event, Exception error, String correlationId) {
        try {
            Map<String, Object> dlqMessage = Map.of(
                "originalEvent", event,
                "errorMessage", error.getMessage(),
                "errorClass", error.getClass().getName(),
                "correlationId", correlationId,
                "failedAt", Instant.now(),
                "service", "merchant-service"
            );
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            log.warn("Sent failed settlement to DLQ - ID: {}, Correlation: {}",
                event.getSettlementId(), correlationId);
                
        } catch (Exception dlqError) {
            log.error("Failed to send settlement to DLQ - Correlation: {}", correlationId, dlqError);
        }
    }

    // Inner classes for results
    @lombok.Data
    @lombok.Builder
    private static class FeeCalculationResult {
        private BigDecimal totalFees;
        private BigDecimal netAmount;
        private Map<String, BigDecimal> feeBreakdown;
    }

    @lombok.Data
    @lombok.Builder
    private static class TaxCalculationResult {
        private BigDecimal totalTax;
        private Map<String, BigDecimal> taxBreakdown;
    }

    @lombok.Data
    @lombok.Builder
    private static class BankTransferResult {
        private String transferId;
        private boolean successful;
        private String errorMessage;
        private LocalDateTime initiatedAt;
    }

    @lombok.Data
    @lombok.Builder
    private static class BankTransferRequest {
        private String merchantId;
        private String settlementId;
        private String bankAccountId;
        private BigDecimal amount;
        private String currency;
        private String description;
        private String correlationId;
    }

    @lombok.Data
    @lombok.Builder
    private static class ComplianceResult {
        private boolean compliant;
        private List<String> violations;
        
        public boolean hasViolations() {
            return violations != null && !violations.isEmpty();
        }
    }

    @lombok.Data
    @lombok.Builder
    private static class RiskAssessmentResult {
        private int riskScore;
        private List<String> riskFactors;
    }
}