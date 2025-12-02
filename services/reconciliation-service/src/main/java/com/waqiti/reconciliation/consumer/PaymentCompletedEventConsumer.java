package com.waqiti.reconciliation.consumer;

import com.waqiti.common.events.PaymentCompletedEvent;
import com.waqiti.reconciliation.service.ReconciliationService;
import com.waqiti.reconciliation.service.SettlementService;
import com.waqiti.reconciliation.service.ReportingService;
import com.waqiti.reconciliation.repository.ProcessedEventRepository;
import com.waqiti.reconciliation.model.ProcessedEvent;
import com.waqiti.reconciliation.model.ReconciliationRecord;
import com.waqiti.reconciliation.model.SettlementBatch;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.time.Instant;
import java.math.BigDecimal;

/**
 * Consumer for PaymentCompletedEvent - Critical for financial reconciliation
 * Handles payment completion reconciliation and settlement
 * CRITICAL: Ensures all completed payments are properly recorded and reconciled
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentCompletedEventConsumer {
    
    private final ReconciliationService reconciliationService;
    private final SettlementService settlementService;
    private final ReportingService reportingService;
    private final ProcessedEventRepository processedEventRepository;
    
    @KafkaListener(
        topics = "payment.completed",
        groupId = "reconciliation-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Processing payment completion reconciliation for: {}", event.getPaymentId());
        
        // IDEMPOTENCY CHECK - Critical for financial accuracy
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Payment completion already reconciled for event: {}", event.getEventId());
            return;
        }
        
        try {
            // STEP 1: Create reconciliation record
            ReconciliationRecord reconciliationRecord = createReconciliationRecord(event);
            
            // STEP 2: Verify transaction consistency
            boolean isConsistent = reconciliationService.verifyTransactionConsistency(
                event.getPaymentId(),
                event.getAmount(),
                event.getFees(),
                event.getFromAccount(),
                event.getToAccount()
            );
            
            if (!isConsistent) {
                log.error("CRITICAL: Transaction consistency check failed for payment: {}", 
                    event.getPaymentId());
                handleInconsistentTransaction(event);
                return;
            }
            
            // STEP 3: Record successful reconciliation
            reconciliationRecord.setStatus("RECONCILED");
            reconciliationRecord.setReconciledAt(Instant.now());
            reconciliationService.saveReconciliationRecord(reconciliationRecord);
            
            // STEP 4: Add to settlement batch
            addToSettlementBatch(event);
            
            // STEP 5: Update financial reporting
            updateFinancialReporting(event);
            
            // STEP 6: Generate compliance records
            generateComplianceRecords(event);
            
            // STEP 7: Mark event as processed
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("PaymentCompletedEvent")
                .processedAt(Instant.now())
                .reconciliationRecordId(reconciliationRecord.getId())
                .status("SUCCESS")
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully reconciled payment completion: {}", event.getPaymentId());
            
        } catch (Exception e) {
            log.error("Failed to reconcile payment completion for event: {}", 
                event.getEventId(), e);
                
            // Create exception record for investigation
            createExceptionRecord(event, e);
            
            throw new RuntimeException("Payment reconciliation failed", e);
        }
    }
    
    private ReconciliationRecord createReconciliationRecord(PaymentCompletedEvent event) {
        return ReconciliationRecord.builder()
            .transactionId(event.getPaymentId())
            .eventId(event.getEventId())
            .fromAccount(event.getFromAccount())
            .toAccount(event.getToAccount())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .fees(event.getFees())
            .completedAt(event.getCompletedAt())
            .paymentProvider(event.getPaymentProvider())
            .providerTransactionId(event.getProviderTransactionId())
            .status("PENDING")
            .createdAt(Instant.now())
            .build();
    }
    
    private void handleInconsistentTransaction(PaymentCompletedEvent event) {
        log.error("CRITICAL INCONSISTENCY: Payment {} failed consistency check", 
            event.getPaymentId());
        
        // Create high-priority exception for manual review
        reconciliationService.createHighPriorityException(
            event.getPaymentId(),
            "CONSISTENCY_CHECK_FAILED",
            "Transaction amounts or accounts do not match ledger records",
            event
        );
        
        // Alert operations team immediately
        alertService.sendCriticalAlert(
            "Payment Reconciliation Failure",
            String.format("Payment %s failed consistency check and requires immediate investigation", 
                event.getPaymentId())
        );
        
        // Freeze related accounts pending investigation
        reconciliationService.freezeAccountsForInvestigation(
            event.getFromAccount(),
            event.getToAccount(),
            "Inconsistent transaction detected: " + event.getPaymentId()
        );
    }
    
    private void addToSettlementBatch(PaymentCompletedEvent event) {
        // Add transaction to appropriate settlement batch based on provider and currency
        SettlementBatch batch = settlementService.getOrCreateBatch(
            event.getPaymentProvider(),
            event.getCurrency(),
            event.getCompletedAt().toLocalDate()
        );
        
        settlementService.addTransactionToBatch(
            batch.getId(),
            event.getPaymentId(),
            event.getAmount(),
            event.getFees()
        );
        
        // Check if batch is ready for settlement
        if (settlementService.isBatchReadyForSettlement(batch.getId())) {
            settlementService.submitBatchForSettlement(batch.getId());
        }
    }
    
    private void updateFinancialReporting(PaymentCompletedEvent event) {
        // Update daily, monthly, and annual financial reports
        reportingService.updateDailyReports(
            event.getCompletedAt().toLocalDate(),
            event.getAmount(),
            event.getFees(),
            event.getCurrency()
        );
        
        // Update revenue recognition
        reportingService.recognizeRevenue(
            event.getFees(),
            event.getCurrency(),
            event.getCompletedAt()
        );
        
        // Update customer transaction limits and history
        reportingService.updateCustomerMetrics(
            event.getSenderUserId(),
            event.getReceiverUserId(),
            event.getAmount(),
            event.getCurrency()
        );
    }
    
    private void generateComplianceRecords(PaymentCompletedEvent event) {
        // Generate records for regulatory reporting
        if (event.getAmount().compareTo(new BigDecimal("3000")) >= 0) {
            // BSA/AML reporting threshold
            reconciliationService.generateCTRRecord(event);
        }
        
        if (isInternationalTransfer(event)) {
            // International transfer reporting
            reconciliationService.generateInternationalTransferRecord(event);
        }
        
        // Always generate audit trail record
        reconciliationService.generateAuditTrailRecord(event);
    }
    
    private boolean isInternationalTransfer(PaymentCompletedEvent event) {
        // Check if transfer crosses country boundaries
        return !event.getSenderCountry().equals(event.getReceiverCountry());
    }
    
    private void createExceptionRecord(PaymentCompletedEvent event, Exception exception) {
        reconciliationService.createExceptionRecord(
            event.getPaymentId(),
            "RECONCILIATION_ERROR",
            exception.getMessage(),
            event,
            "HIGH" // Priority level
        );
    }
}