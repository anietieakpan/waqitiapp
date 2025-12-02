package com.waqiti.merchant.kafka;

import com.waqiti.common.events.DisputeSettlementEvent;
import com.waqiti.merchant.domain.DisputeSettlement;
import com.waqiti.merchant.repository.DisputeSettlementRepository;
import com.waqiti.merchant.service.SettlementService;
import com.waqiti.merchant.service.PaymentProcessingService;
import com.waqiti.merchant.metrics.DisputeMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.*;

/**
 * Dispute Settlement Events Consumer
 * Processes financial settlement of resolved disputes
 * Implements 12-step zero-tolerance processing for settlement execution
 * 
 * Business Context:
 * - Final step in dispute resolution lifecycle
 * - Funds transfer between merchant, issuer, and cardholder
 * - Settlement can result from: representment win, arbitration decision, negotiated agreement
 * - Settlement timeframes: 1-5 business days depending on method
 * - Reversal of chargebacks or confirmation of liability
 * - Fee allocation (dispute fees, arbitration fees, processing fees)
 * - Accounting reconciliation and reporting
 * 
 * Settlement Types:
 * - FULL_REVERSAL: Merchant wins, all funds returned
 * - PARTIAL_SETTLEMENT: Negotiated split of disputed amount
 * - LIABILITY_CONFIRMED: Merchant loses, chargeback stands
 * - NEGOTIATED_REFUND: Agreed refund amount different from original
 * 
 * @author Waqiti Merchant Services Team
 * @version 1.0.0
 * @since 2025-09-27
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DisputeSettlementEventsConsumer {
    
    private final DisputeSettlementRepository settlementRepository;
    private final SettlementService settlementService;
    private final PaymentProcessingService paymentProcessingService;
    private final DisputeMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"dispute-settlement-events", "chargeback-settlement-events", "resolution-payment-events"},
        groupId = "merchant-dispute-settlement-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "3"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 60000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, timeout = 180)
    public void handleDisputeSettlementEvent(
            @Payload DisputeSettlementEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("settlement-%s-p%d-o%d", 
            event.getDisputeId(), partition, offset);
        
        log.info("Processing dispute settlement event: disputeId={}, type={}, settlementType={}, amount={}", 
            event.getDisputeId(), event.getEventType(), event.getSettlementType(), event.getSettlementAmount());
        
        try {
            switch (event.getEventType()) {
                case SETTLEMENT_INITIATED:
                    processSettlementInitiated(event, correlationId);
                    break;
                case SETTLEMENT_AMOUNT_CALCULATED:
                    processSettlementAmountCalculated(event, correlationId);
                    break;
                case SETTLEMENT_APPROVED:
                    processSettlementApproved(event, correlationId);
                    break;
                case FUNDS_TRANSFER_INITIATED:
                    processFundsTransferInitiated(event, correlationId);
                    break;
                case FUNDS_TRANSFER_COMPLETED:
                    processFundsTransferCompleted(event, correlationId);
                    break;
                case FUNDS_TRANSFER_FAILED:
                    processFundsTransferFailed(event, correlationId);
                    break;
                case FEES_ALLOCATED:
                    processFeesAllocated(event, correlationId);
                    break;
                case ACCOUNTING_RECONCILED:
                    processAccountingReconciled(event, correlationId);
                    break;
                case SETTLEMENT_COMPLETED:
                    processSettlementCompleted(event, correlationId);
                    break;
                case SETTLEMENT_REVERSED:
                    processSettlementReversed(event, correlationId);
                    break;
                default:
                    log.warn("Unknown dispute settlement event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logDisputeEvent(
                "SETTLEMENT_EVENT_PROCESSED",
                event.getDisputeId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "settlementId", event.getSettlementId() != null ? event.getSettlementId() : "N/A",
                    "settlementType", event.getSettlementType() != null ? event.getSettlementType() : "N/A",
                    "settlementAmount", event.getSettlementAmount() != null ? event.getSettlementAmount().toString() : "0",
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process dispute settlement event: {}", e.getMessage(), e);
            kafkaTemplate.send("dispute-settlement-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processSettlementInitiated(DisputeSettlementEvent event, String correlationId) {
        log.info("Settlement initiated: disputeId={}, settlementType={}, resolutionMethod={}", 
            event.getDisputeId(), event.getSettlementType(), event.getResolutionMethod());
        
        DisputeSettlement settlement = DisputeSettlement.builder()
            .id(UUID.randomUUID().toString())
            .disputeId(event.getDisputeId())
            .merchantId(event.getMerchantId())
            .transactionId(event.getTransactionId())
            .originalDisputeAmount(event.getOriginalDisputeAmount())
            .settlementType(event.getSettlementType())
            .resolutionMethod(event.getResolutionMethod())
            .initiatedAt(LocalDateTime.now())
            .status("INITIATED")
            .cardNetwork(event.getCardNetwork())
            .winner(event.getWinner())
            .correlationId(correlationId)
            .build();
        
        settlementRepository.save(settlement);
        
        settlementService.calculateSettlementAmount(settlement.getId());
        
        metricsService.recordSettlementInitiated(event.getSettlementType(), event.getResolutionMethod());
    }
    
    private void processSettlementAmountCalculated(DisputeSettlementEvent event, String correlationId) {
        log.info("Settlement amount calculated: settlementId={}, settlementAmount={}, fees={}", 
            event.getSettlementId(), event.getSettlementAmount(), event.getTotalFees());
        
        DisputeSettlement settlement = settlementRepository.findById(event.getSettlementId())
            .orElseThrow();
        
        settlement.setSettlementAmount(event.getSettlementAmount());
        settlement.setDisputeFees(event.getDisputeFees());
        settlement.setArbitrationFees(event.getArbitrationFees());
        settlement.setProcessingFees(event.getProcessingFees());
        settlement.setTotalFees(event.getTotalFees());
        settlement.setNetSettlementAmount(event.getSettlementAmount().subtract(event.getTotalFees()));
        settlement.setAmountCalculatedAt(LocalDateTime.now());
        settlementRepository.save(settlement);
        
        // Request approval for settlement
        settlementService.requestSettlementApproval(settlement.getId());
        
        metricsService.recordSettlementAmountCalculated(event.getSettlementAmount());
    }
    
    private void processSettlementApproved(DisputeSettlementEvent event, String correlationId) {
        log.info("Settlement approved: settlementId={}, approvedBy={}", 
            event.getSettlementId(), event.getApprovedBy());
        
        DisputeSettlement settlement = settlementRepository.findById(event.getSettlementId())
            .orElseThrow();
        
        settlement.setStatus("APPROVED");
        settlement.setApprovedAt(LocalDateTime.now());
        settlement.setApprovedBy(event.getApprovedBy());
        settlementRepository.save(settlement);
        
        // Initiate funds transfer
        settlementService.initiateFundsTransfer(settlement.getId());
        
        metricsService.recordSettlementApproved();
    }
    
    private void processFundsTransferInitiated(DisputeSettlementEvent event, String correlationId) {
        log.info("Funds transfer initiated: settlementId={}, amount={}, transferMethod={}", 
            event.getSettlementId(), event.getTransferAmount(), event.getTransferMethod());
        
        DisputeSettlement settlement = settlementRepository.findById(event.getSettlementId())
            .orElseThrow();
        
        settlement.setStatus("FUNDS_TRANSFER_INITIATED");
        settlement.setFundsTransferInitiatedAt(LocalDateTime.now());
        settlement.setTransferMethod(event.getTransferMethod());
        settlement.setTransferReference(event.getTransferReference());
        settlement.setExpectedSettlementDate(event.getExpectedSettlementDate());
        settlementRepository.save(settlement);
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Dispute Settlement In Progress",
            String.format("Funds transfer for dispute %s has been initiated. " +
                "Amount: %s. Expected settlement date: %s. Reference: %s",
                event.getDisputeId(), event.getTransferAmount(), 
                event.getExpectedSettlementDate(), event.getTransferReference()),
            correlationId
        );
        
        metricsService.recordFundsTransferInitiated(event.getTransferMethod());
    }
    
    private void processFundsTransferCompleted(DisputeSettlementEvent event, String correlationId) {
        log.info("Funds transfer completed: settlementId={}, confirmationNumber={}", 
            event.getSettlementId(), event.getConfirmationNumber());
        
        DisputeSettlement settlement = settlementRepository.findById(event.getSettlementId())
            .orElseThrow();
        
        settlement.setFundsTransferCompleted(true);
        settlement.setFundsTransferCompletedAt(LocalDateTime.now());
        settlement.setConfirmationNumber(event.getConfirmationNumber());
        settlement.setActualSettlementDate(LocalDateTime.now());
        settlement.setStatus("FUNDS_TRANSFERRED");
        settlementRepository.save(settlement);
        
        // Update merchant account balance
        paymentProcessingService.updateMerchantBalance(event.getMerchantId(), 
            settlement.getNetSettlementAmount(), "DISPUTE_SETTLEMENT");
        
        // Process fee allocation
        settlementService.allocateFees(settlement.getId());
        
        metricsService.recordFundsTransferCompleted(settlement.getNetSettlementAmount());
    }
    
    private void processFundsTransferFailed(DisputeSettlementEvent event, String correlationId) {
        log.error("Funds transfer failed: settlementId={}, failureReason={}", 
            event.getSettlementId(), event.getFailureReason());
        
        DisputeSettlement settlement = settlementRepository.findById(event.getSettlementId())
            .orElseThrow();
        
        settlement.setFundsTransferFailed(true);
        settlement.setFundsTransferFailedAt(LocalDateTime.now());
        settlement.setTransferFailureReason(event.getFailureReason());
        settlement.setStatus("TRANSFER_FAILED");
        settlementRepository.save(settlement);
        
        // Retry transfer
        settlementService.retryFundsTransfer(settlement.getId());
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Dispute Settlement - Transfer Issue",
            String.format("There was an issue with the funds transfer for dispute %s. " +
                "Reason: %s. We are retrying the transfer.",
                event.getDisputeId(), event.getFailureReason()),
            correlationId
        );
        
        metricsService.recordFundsTransferFailed(event.getFailureReason());
    }
    
    private void processFeesAllocated(DisputeSettlementEvent event, String correlationId) {
        log.info("Fees allocated: settlementId={}, disputeFee={}, processingFee={}", 
            event.getSettlementId(), event.getDisputeFees(), event.getProcessingFees());
        
        DisputeSettlement settlement = settlementRepository.findById(event.getSettlementId())
            .orElseThrow();
        
        settlement.setFeesAllocated(true);
        settlement.setFeesAllocatedAt(LocalDateTime.now());
        settlement.setFeeAllocationBreakdown(event.getFeeAllocationBreakdown());
        settlementRepository.save(settlement);
        
        // Process fee payments
        settlementService.processFeePayments(settlement.getId());
        
        metricsService.recordFeesAllocated(settlement.getTotalFees());
    }
    
    private void processAccountingReconciled(DisputeSettlementEvent event, String correlationId) {
        log.info("Accounting reconciled: settlementId={}, reconciliationId={}", 
            event.getSettlementId(), event.getReconciliationId());
        
        DisputeSettlement settlement = settlementRepository.findById(event.getSettlementId())
            .orElseThrow();
        
        settlement.setAccountingReconciled(true);
        settlement.setAccountingReconciledAt(LocalDateTime.now());
        settlement.setReconciliationId(event.getReconciliationId());
        settlementRepository.save(settlement);
        
        // Generate settlement reports
        settlementService.generateSettlementReports(settlement.getId());
        
        metricsService.recordAccountingReconciled();
    }
    
    private void processSettlementCompleted(DisputeSettlementEvent event, String correlationId) {
        log.info("Settlement completed: settlementId={}, disputeId={}, finalAmount={}", 
            event.getSettlementId(), event.getDisputeId(), event.getFinalSettlementAmount());
        
        DisputeSettlement settlement = settlementRepository.findById(event.getSettlementId())
            .orElseThrow();
        
        settlement.setStatus("COMPLETED");
        settlement.setCompletedAt(LocalDateTime.now());
        settlement.setFinalSettlementAmount(event.getFinalSettlementAmount());
        settlementRepository.save(settlement);
        
        // Close dispute
        settlementService.closeDispute(event.getDisputeId(), settlement.getId());
        
        String outcomeMessage = getSettlementOutcomeMessage(settlement);
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Dispute Settlement Completed",
            String.format("Settlement for dispute %s has been completed. %s " +
                "Confirmation: %s. Final amount: %s",
                event.getDisputeId(), outcomeMessage, 
                settlement.getConfirmationNumber(), event.getFinalSettlementAmount()),
            correlationId
        );
        
        metricsService.recordSettlementCompleted(
            settlement.getSettlementType(),
            settlement.getResolutionMethod(),
            event.getFinalSettlementAmount()
        );
    }
    
    private void processSettlementReversed(DisputeSettlementEvent event, String correlationId) {
        log.warn("Settlement reversed: settlementId={}, reversalReason={}", 
            event.getSettlementId(), event.getReversalReason());
        
        DisputeSettlement settlement = settlementRepository.findById(event.getSettlementId())
            .orElseThrow();
        
        settlement.setStatus("REVERSED");
        settlement.setReversed(true);
        settlement.setReversedAt(LocalDateTime.now());
        settlement.setReversalReason(event.getReversalReason());
        settlementRepository.save(settlement);
        
        // Reverse funds transfer
        paymentProcessingService.reverseFundsTransfer(settlement.getTransferReference(), 
            settlement.getNetSettlementAmount());
        
        notificationService.sendNotification(
            event.getMerchantId(),
            "Settlement Reversed",
            String.format("Settlement for dispute %s has been reversed. Reason: %s",
                event.getDisputeId(), event.getReversalReason()),
            correlationId
        );
        
        metricsService.recordSettlementReversed(event.getReversalReason());
    }
    
    private String getSettlementOutcomeMessage(DisputeSettlement settlement) {
        return switch (settlement.getSettlementType()) {
            case "FULL_REVERSAL" -> "Congratulations! You won the dispute. All funds have been returned to your account.";
            case "PARTIAL_SETTLEMENT" -> "The dispute was settled with a partial refund. The negotiated amount has been processed.";
            case "LIABILITY_CONFIRMED" -> "The dispute decision was upheld. The chargeback remains in effect.";
            case "NEGOTIATED_REFUND" -> "An agreed refund amount has been processed as per the settlement agreement.";
            default -> "The dispute has been resolved and settlement processed.";
        };
    }
}