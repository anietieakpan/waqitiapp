package com.waqiti.bnpl.kafka;

import com.waqiti.bnpl.event.BnplEvent;
import com.waqiti.bnpl.service.BnplService;
import com.waqiti.bnpl.service.InstallmentService;
import com.waqiti.bnpl.service.CreditAssessmentService;
import com.waqiti.bnpl.service.CollectionService;
import com.waqiti.bnpl.service.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-grade Kafka consumer for BNPL and specialized payment events
 * Handles: bnpl-installment-events, bnpl-payment-events, collection-cases, 
 * lightning-events, currency-conversion-events, qr-code-events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BnplPaymentConsumer {

    private final BnplService bnplService;
    private final InstallmentService installmentService;
    private final CreditAssessmentService creditAssessmentService;
    private final CollectionService collectionService;
    private final IdempotencyService idempotencyService;

    @KafkaListener(topics = {"bnpl-installment-events", "bnpl-payment-events", "collection-cases",
                             "lightning-events", "currency-conversion-events", "qr-code-events"},
                   groupId = "bnpl-payment-processor")
    @Transactional
    public void processBnplEvent(@Payload BnplEvent event,
                               @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                               @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                               @Header(KafkaHeaders.OFFSET) long offset,
                               Acknowledgment acknowledgment) {
        // CRITICAL: Check idempotency to prevent duplicate processing
        String idempotencyKey = idempotencyService.generateKafkaKey(topic, partition, offset);

        if (!idempotencyService.checkAndMarkProcessed(idempotencyKey, java.time.Duration.ofDays(7))) {
            log.warn("Duplicate event detected and skipped: {} - Topic: {} - Partition: {} - Offset: {}",
                    event.getEventId(), topic, partition, offset);
            acknowledgment.acknowledge();
            return;
        }

        try {
            log.info("Processing BNPL event: {} - Type: {} - User: {} - Amount: {} - Idempotency: {}",
                    event.getEventId(), event.getEventType(), event.getUserId(), event.getAmount(), idempotencyKey);
            
            // Process based on topic
            switch (topic) {
                case "bnpl-installment-events" -> handleBnplInstallment(event);
                case "bnpl-payment-events" -> handleBnplPayment(event);
                case "collection-cases" -> handleCollectionCase(event);
                case "lightning-events" -> handleLightningEvent(event);
                case "currency-conversion-events" -> handleCurrencyConversion(event);
                case "qr-code-events" -> handleQrCodeEvent(event);
            }
            
            // Update BNPL metrics
            updateBnplMetrics(event);
            
            // Acknowledge
            acknowledgment.acknowledge();
            
            log.info("Successfully processed BNPL event: {}", event.getEventId());
            
        } catch (Exception e) {
            log.error("Failed to process BNPL event {}: {} - Removing idempotency key for retry",
                    event.getEventId(), e.getMessage(), e);
            // CRITICAL: Remove idempotency key on failure to allow retry
            idempotencyService.removeKey(idempotencyKey);
            throw new RuntimeException("BNPL processing failed", e);
        }
    }

    private void handleBnplInstallment(BnplEvent event) {
        String installmentEventType = event.getInstallmentEventType();
        String loanId = event.getLoanId();
        
        switch (installmentEventType) {
            case "INSTALLMENT_DUE" -> {
                // Process installment due notification
                installmentService.processInstallmentDue(
                    loanId,
                    event.getInstallmentNumber(),
                    event.getDueDate(),
                    event.getInstallmentAmount()
                );
                
                // Send due reminder
                installmentService.sendInstallmentReminder(
                    event.getUserId(),
                    loanId,
                    event.getInstallmentNumber(),
                    event.getDueDate(),
                    event.getInstallmentAmount()
                );
                
                // Check for auto-debit setup
                if (event.hasAutoDebit()) {
                    installmentService.scheduleAutoDebit(
                        loanId,
                        event.getInstallmentNumber(),
                        event.getPaymentMethodId(),
                        event.getDueDate()
                    );
                }
            }
            case "INSTALLMENT_PAID" -> {
                // Process installment payment
                installmentService.processInstallmentPayment(
                    loanId,
                    event.getInstallmentNumber(),
                    event.getPaidAmount(),
                    event.getPaymentDate(),
                    event.getPaymentMethodId()
                );
                
                // Update loan status
                bnplService.updateLoanStatus(
                    loanId,
                    event.getInstallmentNumber(),
                    event.getTotalInstallments()
                );
                
                // Calculate remaining balance
                BigDecimal remainingBalance = bnplService.calculateRemainingBalance(
                    loanId,
                    event.getPaidAmount()
                );
                
                // Send payment confirmation
                installmentService.sendPaymentConfirmation(
                    event.getUserId(),
                    loanId,
                    event.getInstallmentNumber(),
                    event.getPaidAmount(),
                    remainingBalance
                );
                
                // Check if loan is fully paid
                if (remainingBalance.compareTo(BigDecimal.ZERO) == 0) {
                    bnplService.completeLoan(
                        loanId,
                        event.getPaymentDate()
                    );
                }
            }
            case "INSTALLMENT_OVERDUE" -> {
                // Handle overdue installment
                installmentService.processOverdueInstallment(
                    loanId,
                    event.getInstallmentNumber(),
                    event.getDaysPastDue(),
                    event.getOverdueAmount()
                );
                
                // Apply late fees
                BigDecimal lateFee = installmentService.calculateLateFee(
                    event.getOverdueAmount(),
                    event.getDaysPastDue(),
                    event.getLateFeeStructure()
                );
                
                if (lateFee.compareTo(BigDecimal.ZERO) > 0) {
                    installmentService.applyLateFee(
                        loanId,
                        event.getInstallmentNumber(),
                        lateFee
                    );
                }
                
                // Send overdue notice
                installmentService.sendOverdueNotice(
                    event.getUserId(),
                    loanId,
                    event.getInstallmentNumber(),
                    event.getOverdueAmount(),
                    lateFee,
                    event.getDaysPastDue()
                );
                
                // Escalate if severely overdue
                if (event.getDaysPastDue() >= event.getEscalationThreshold()) {
                    collectionService.escalateOverdueAccount(
                        loanId,
                        event.getUserId(),
                        event.getOverdueAmount().add(lateFee),
                        event.getDaysPastDue()
                    );
                }
            }
            case "INSTALLMENT_FAILED" -> {
                // Handle failed installment payment
                installmentService.processFailedPayment(
                    loanId,
                    event.getInstallmentNumber(),
                    event.getFailureReason(),
                    event.getAttemptedAmount()
                );
                
                // Retry payment if configured
                if (event.isRetryEnabled() && event.getRetryCount() < event.getMaxRetries()) {
                    installmentService.schedulePaymentRetry(
                        loanId,
                        event.getInstallmentNumber(),
                        event.getPaymentMethodId(),
                        event.getRetryDelay()
                    );
                } else {
                    // Send payment failure notification
                    installmentService.sendPaymentFailureNotification(
                        event.getUserId(),
                        loanId,
                        event.getInstallmentNumber(),
                        event.getFailureReason(),
                        event.getAlternativePaymentOptions()
                    );
                }
            }
            case "INSTALLMENT_RESCHEDULED" -> {
                // Handle installment rescheduling
                installmentService.rescheduleInstallment(
                    loanId,
                    event.getInstallmentNumber(),
                    event.getNewDueDate(),
                    event.getRescheduleReason()
                );
                
                // Update payment schedule
                installmentService.updatePaymentSchedule(
                    loanId,
                    event.getUpdatedSchedule()
                );
                
                // Send reschedule confirmation
                installmentService.sendRescheduleConfirmation(
                    event.getUserId(),
                    loanId,
                    event.getInstallmentNumber(),
                    event.getNewDueDate()
                );
            }
        }
    }

    private void handleBnplPayment(BnplEvent event) {
        String paymentEventType = event.getPaymentEventType();
        String loanId = event.getLoanId();
        
        switch (paymentEventType) {
            case "LOAN_CREATED" -> {
                // Create new BNPL loan
                String newLoanId = bnplService.createLoan(
                    event.getUserId(),
                    event.getMerchantId(),
                    event.getOrderId(),
                    event.getTotalAmount(),
                    event.getLoanTerms()
                );
                
                // Perform credit assessment
                Map<String, Object> creditAssessment = creditAssessmentService.assessCredit(
                    event.getUserId(),
                    event.getTotalAmount(),
                    event.getCreditFactors()
                );
                
                // Generate payment schedule
                Map<String, Object> paymentSchedule = installmentService.generatePaymentSchedule(
                    newLoanId,
                    event.getTotalAmount(),
                    event.getInstallmentCount(),
                    event.getFirstPaymentDate()
                );
                
                // Store loan details
                bnplService.storeLoanDetails(
                    newLoanId,
                    event.getLoanTerms(),
                    paymentSchedule,
                    creditAssessment
                );
                
                // Send loan confirmation
                bnplService.sendLoanConfirmation(
                    event.getUserId(),
                    newLoanId,
                    paymentSchedule
                );
            }
            case "PAYMENT_PROCESSED" -> {
                // Process BNPL payment
                bnplService.processPayment(
                    loanId,
                    event.getPaymentAmount(),
                    event.getPaymentDate(),
                    event.getPaymentMethodId()
                );
                
                // Allocate payment to installments
                installmentService.allocatePayment(
                    loanId,
                    event.getPaymentAmount(),
                    event.getAllocationStrategy()
                );
                
                // Update credit profile
                creditAssessmentService.updateCreditProfile(
                    event.getUserId(),
                    "PAYMENT_MADE",
                    event.getPaymentAmount(),
                    LocalDateTime.now()
                );
            }
            case "EARLY_SETTLEMENT" -> {
                // Handle early settlement
                BigDecimal settlementAmount = bnplService.calculateSettlementAmount(
                    loanId,
                    event.getSettlementDate(),
                    event.getDiscountRate()
                );
                
                // Process early settlement
                bnplService.processEarlySettlement(
                    loanId,
                    settlementAmount,
                    event.getSettlementDate()
                );
                
                // Send settlement confirmation
                bnplService.sendSettlementConfirmation(
                    event.getUserId(),
                    loanId,
                    settlementAmount,
                    event.getSavingsAmount()
                );
                
                // Update credit profile positively
                creditAssessmentService.updateCreditProfile(
                    event.getUserId(),
                    "EARLY_SETTLEMENT",
                    settlementAmount,
                    event.getSettlementDate()
                );
            }
            case "LOAN_DEFAULTED" -> {
                // Handle loan default
                bnplService.markLoanAsDefault(
                    loanId,
                    event.getDefaultDate(),
                    event.getOutstandingAmount()
                );
                
                // Create collection case
                String collectionCaseId = collectionService.createCollectionCase(
                    loanId,
                    event.getUserId(),
                    event.getOutstandingAmount(),
                    event.getDaysPastDue()
                );
                
                // Update credit profile negatively
                creditAssessmentService.updateCreditProfile(
                    event.getUserId(),
                    "LOAN_DEFAULT",
                    event.getOutstandingAmount(),
                    event.getDefaultDate()
                );
                
                // Report to credit bureaus
                creditAssessmentService.reportToCreditBureaus(
                    event.getUserId(),
                    "DEFAULT",
                    loanId,
                    event.getOutstandingAmount()
                );
            }
        }
    }

    private void handleCollectionCase(BnplEvent event) {
        String caseEventType = event.getCaseEventType();
        String caseId = event.getCaseId();
        
        switch (caseEventType) {
            case "CASE_CREATED" -> {
                // Initialize collection case
                collectionService.initializeCase(
                    caseId,
                    event.getUserId(),
                    event.getLoanId(),
                    event.getOutstandingAmount(),
                    event.getCaseDetails()
                );
                
                // Assign to collection agent
                String agentId = collectionService.assignCollectionAgent(
                    caseId,
                    event.getCasePriority(),
                    event.getSkillRequirements()
                );
                
                // Create collection strategy
                collectionService.createCollectionStrategy(
                    caseId,
                    event.getCollectionProfile(),
                    event.getStrategyParameters()
                );
            }
            case "CONTACT_ATTEMPT" -> {
                // Record contact attempt
                collectionService.recordContactAttempt(
                    caseId,
                    event.getContactMethod(),
                    event.getContactOutcome(),
                    event.getContactNotes()
                );
                
                // Schedule follow-up if needed
                if (event.isFollowUpRequired()) {
                    collectionService.scheduleFollowUp(
                        caseId,
                        event.getFollowUpDate(),
                        event.getFollowUpMethod(),
                        event.getFollowUpNotes()
                    );
                }
            }
            case "PAYMENT_ARRANGEMENT" -> {
                // Create payment arrangement
                collectionService.createPaymentArrangement(
                    caseId,
                    event.getArrangementAmount(),
                    event.getArrangementSchedule(),
                    event.getArrangementTerms()
                );
                
                // Update loan status
                bnplService.updateLoanStatusFromCollection(
                    event.getLoanId(),
                    "PAYMENT_ARRANGEMENT",
                    event.getArrangementDetails()
                );
            }
            case "CASE_RESOLVED" -> {
                // Resolve collection case
                collectionService.resolveCase(
                    caseId,
                    event.getResolutionType(),
                    event.getResolutionAmount(),
                    event.getResolutionDate()
                );
                
                // Update credit profile
                creditAssessmentService.updateCreditProfile(
                    event.getUserId(),
                    event.getResolutionType(),
                    event.getResolutionAmount(),
                    event.getResolutionDate()
                );
            }
        }
    }

    private void handleLightningEvent(BnplEvent event) {
        String lightningEventType = event.getLightningEventType();
        
        switch (lightningEventType) {
            case "LIGHTNING_PAYMENT_REQUESTED" -> {
                // Process Lightning Network payment request
                String paymentHash = bnplService.createLightningPaymentRequest(
                    event.getAmount(),
                    event.getDescription(),
                    event.getExpiryTime()
                );
                
                // Store payment request
                bnplService.storeLightningPaymentRequest(
                    event.getPaymentId(),
                    paymentHash,
                    event.getAmount(),
                    event.getExpiryTime()
                );
            }
            case "LIGHTNING_PAYMENT_RECEIVED" -> {
                // Process received Lightning payment
                bnplService.processLightningPayment(
                    event.getPaymentId(),
                    event.getPaymentHash(),
                    event.getAmount(),
                    event.getPreimage()
                );
                
                // Apply to loan if applicable
                if (event.getLoanId() != null) {
                    installmentService.applyLightningPayment(
                        event.getLoanId(),
                        event.getAmount(),
                        event.getPaymentHash()
                    );
                }
            }
            case "LIGHTNING_INVOICE_EXPIRED" -> {
                // Handle expired Lightning invoice
                bnplService.handleExpiredLightningInvoice(
                    event.getPaymentId(),
                    event.getPaymentHash(),
                    event.getExpiryTime()
                );
            }
        }
    }

    private void handleCurrencyConversion(BnplEvent event) {
        String conversionEventType = event.getConversionEventType();
        
        switch (conversionEventType) {
            case "CONVERSION_REQUESTED" -> {
                // Process currency conversion request
                Map<String, Object> conversionResult = bnplService.processCurrencyConversion(
                    event.getFromCurrency(),
                    event.getToCurrency(),
                    event.getAmount(),
                    event.getConversionRate()
                );
                
                // Apply conversion to loan if applicable
                if (event.getLoanId() != null) {
                    bnplService.applyCurrencyConversion(
                        event.getLoanId(),
                        conversionResult,
                        event.getConversionReason()
                    );
                }
            }
            case "RATE_UPDATE" -> {
                // Update currency rates
                bnplService.updateCurrencyRates(
                    event.getCurrencyPair(),
                    event.getNewRate(),
                    event.getRateTimestamp()
                );
                
                // Recalculate multi-currency loans
                bnplService.recalculateMultiCurrencyLoans(
                    event.getCurrencyPair(),
                    event.getNewRate()
                );
            }
        }
    }

    private void handleQrCodeEvent(BnplEvent event) {
        String qrEventType = event.getQrEventType();
        
        switch (qrEventType) {
            case "QR_CODE_GENERATED" -> {
                // Generate QR code for payment
                String qrCode = bnplService.generatePaymentQrCode(
                    event.getPaymentAmount(),
                    event.getPaymentDescription(),
                    event.getMerchantId(),
                    event.getQrExpiryTime()
                );
                
                // Store QR code details
                bnplService.storeQrCodeDetails(
                    event.getQrCodeId(),
                    qrCode,
                    event.getPaymentAmount(),
                    event.getQrExpiryTime()
                );
            }
            case "QR_CODE_SCANNED" -> {
                // Process QR code scan
                bnplService.processQrCodeScan(
                    event.getQrCodeId(),
                    event.getUserId(),
                    event.getScanTimestamp(),
                    event.getDeviceInfo()
                );
                
                // Initiate payment if valid
                if (event.isQrCodeValid()) {
                    bnplService.initiateQrCodePayment(
                        event.getQrCodeId(),
                        event.getUserId(),
                        event.getPaymentAmount()
                    );
                }
            }
            case "QR_CODE_PAYMENT_COMPLETED" -> {
                // Complete QR code payment
                bnplService.completeQrCodePayment(
                    event.getQrCodeId(),
                    event.getPaymentId(),
                    event.getPaymentAmount(),
                    LocalDateTime.now()
                );
                
                // Apply to loan if applicable
                if (event.getLoanId() != null) {
                    installmentService.applyQrCodePayment(
                        event.getLoanId(),
                        event.getPaymentAmount(),
                        event.getQrCodeId()
                    );
                }
            }
        }
    }

    private void updateBnplMetrics(BnplEvent event) {
        // Update BNPL processing metrics
        bnplService.updateBnplMetrics(
            event.getEventType(),
            event.getAmount(),
            event.getProcessingTime(),
            event.isSuccessful()
        );
        
        // Update installment metrics
        if (event.getLoanId() != null) {
            installmentService.updateInstallmentMetrics(
                event.getLoanId(),
                event.getInstallmentNumber(),
                event.getEventType()
            );
        }
        
        // Update collection metrics
        if (event.getCaseId() != null) {
            collectionService.updateCollectionMetrics(
                event.getCaseId(),
                event.getCaseEventType(),
                event.getCollectionOutcome()
            );
        }
    }
}