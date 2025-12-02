package com.waqiti.payment.kafka;

import com.waqiti.common.events.TransactionReversalEvent;
import com.waqiti.payment.domain.TransactionReversal;
import com.waqiti.payment.repository.TransactionReversalRepository;
import com.waqiti.payment.service.ReversalProcessingService;
import com.waqiti.payment.service.RefundService;
import com.waqiti.payment.service.VoidService;
import com.waqiti.payment.service.PaymentValidationService;
import com.waqiti.payment.metrics.PaymentMetricsService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class TransactionReversalsConsumer {
    
    private final TransactionReversalRepository reversalRepository;
    private final ReversalProcessingService reversalService;
    private final RefundService refundService;
    private final VoidService voidService;
    private final PaymentValidationService validationService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000.00");
    private static final int REVERSAL_CUTOFF_HOURS = 24;
    
    @KafkaListener(
        topics = {"transaction-reversals", "payment-reversals", "refund-requests"},
        groupId = "transaction-reversals-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "8"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleTransactionReversal(
            @Payload TransactionReversalEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("reversal-%s-p%d-o%d", 
            event.getReversalId(), partition, offset);
        
        log.info("Processing transaction reversal: id={}, originalTransactionId={}, amount={}, type={}, reason={}",
            event.getReversalId(), event.getOriginalTransactionId(), event.getAmount(), 
            event.getReversalType(), event.getReason());
        
        try {
            switch (event.getStatus()) {
                case "REVERSAL_REQUESTED":
                    processReversalRequest(event, correlationId);
                    break;
                    
                case "REVERSAL_VALIDATED":
                    validateReversal(event, correlationId);
                    break;
                    
                case "VOID_PROCESSING":
                    processVoid(event, correlationId);
                    break;
                    
                case "REFUND_PROCESSING":
                    processRefund(event, correlationId);
                    break;
                    
                case "CHARGEBACK_PROCESSING":
                    processChargeback(event, correlationId);
                    break;
                    
                case "REVERSAL_COMPLETED":
                    completeReversal(event, correlationId);
                    break;
                    
                case "REVERSAL_FAILED":
                    handleReversalFailure(event, correlationId);
                    break;
                    
                case "REVERSAL_PARTIAL":
                    handlePartialReversal(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown reversal status: {}", event.getStatus());
                    break;
            }
            
            auditService.logPaymentEvent("TRANSACTION_REVERSAL_PROCESSED", event.getReversalId(),
                Map.of("originalTransactionId", event.getOriginalTransactionId(), "status", event.getStatus(),
                    "amount", event.getAmount(), "reversalType", event.getReversalType(),
                    "reason", event.getReason(), "correlationId", correlationId, 
                    "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process transaction reversal: {}", e.getMessage(), e);
            kafkaTemplate.send("transaction-reversals-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void processReversalRequest(TransactionReversalEvent event, String correlationId) {
        TransactionReversal reversal = TransactionReversal.builder()
            .reversalId(event.getReversalId())
            .originalTransactionId(event.getOriginalTransactionId())
            .merchantId(event.getMerchantId())
            .amount(event.getAmount())
            .currency(event.getCurrency())
            .reversalType(event.getReversalType())
            .reason(event.getReason())
            .requestedBy(event.getRequestedBy())
            .status("REVERSAL_REQUESTED")
            .requestedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        reversalRepository.save(reversal);
        
        boolean canProcess = reversalService.validateOriginalTransaction(
            event.getOriginalTransactionId(),
            event.getAmount()
        );
        
        if (!canProcess) {
            reversal.setStatus("REVERSAL_FAILED");
            reversal.setFailureReason("ORIGINAL_TRANSACTION_NOT_FOUND_OR_INVALID");
            reversal.setFailedAt(LocalDateTime.now());
            reversalRepository.save(reversal);
            
            notificationService.sendNotification(event.getRequestedBy(), "Reversal Failed",
                String.format("Reversal %s failed: Original transaction not found or invalid", 
                    event.getReversalId()),
                correlationId);
            return;
        }
        
        if (event.getAmount().compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            notificationService.sendNotification("FINANCE_TEAM", "High Value Reversal Requested",
                String.format("Reversal %s for %s %s requires approval. Original transaction: %s", 
                    event.getReversalId(), event.getAmount(), event.getCurrency(), 
                    event.getOriginalTransactionId()),
                correlationId);
            
            reversal.setRequiresApproval(true);
            reversalRepository.save(reversal);
            
            kafkaTemplate.send("reversal-approval-queue", Map.of(
                "reversalId", event.getReversalId(),
                "amount", event.getAmount(),
                "reason", event.getReason(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            kafkaTemplate.send("transaction-reversals", Map.of(
                "reversalId", event.getReversalId(),
                "originalTransactionId", event.getOriginalTransactionId(),
                "status", "REVERSAL_VALIDATED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        metricsService.recordReversalRequested(event.getReversalType(), event.getAmount());
        
        log.info("Reversal request processed: id={}, originalTransactionId={}, canProcess={}", 
            event.getReversalId(), event.getOriginalTransactionId(), canProcess);
    }
    
    private void validateReversal(TransactionReversalEvent event, String correlationId) {
        TransactionReversal reversal = reversalRepository.findByReversalId(event.getReversalId())
            .orElseThrow(() -> new RuntimeException("Reversal not found"));
        
        reversal.setStatus("REVERSAL_VALIDATED");
        reversal.setValidatedAt(LocalDateTime.now());
        reversalRepository.save(reversal);
        
        long hoursSinceOriginal = reversalService.getHoursSinceOriginalTransaction(
            event.getOriginalTransactionId()
        );
        
        String nextStatus;
        if (hoursSinceOriginal <= REVERSAL_CUTOFF_HOURS && "VOID".equals(event.getReversalType())) {
            nextStatus = "VOID_PROCESSING";
        } else if ("REFUND".equals(event.getReversalType())) {
            nextStatus = "REFUND_PROCESSING";
        } else if ("CHARGEBACK".equals(event.getReversalType())) {
            nextStatus = "CHARGEBACK_PROCESSING";
        } else {
            nextStatus = "REFUND_PROCESSING";
        }
        
        reversal.setProcessingMethod(nextStatus.replace("_PROCESSING", ""));
        reversalRepository.save(reversal);
        
        kafkaTemplate.send("transaction-reversals", Map.of(
            "reversalId", event.getReversalId(),
            "originalTransactionId", event.getOriginalTransactionId(),
            "status", nextStatus,
            "processingMethod", nextStatus.replace("_PROCESSING", ""),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        log.info("Reversal validated: id={}, nextStatus={}, hoursSinceOriginal={}", 
            event.getReversalId(), nextStatus, hoursSinceOriginal);
    }
    
    private void processVoid(TransactionReversalEvent event, String correlationId) {
        TransactionReversal reversal = reversalRepository.findByReversalId(event.getReversalId())
            .orElseThrow(() -> new RuntimeException("Reversal not found"));
        
        reversal.setStatus("VOID_PROCESSING");
        reversal.setProcessingStartedAt(LocalDateTime.now());
        reversalRepository.save(reversal);
        
        String voidReference = voidService.processVoid(
            event.getOriginalTransactionId(),
            event.getAmount(),
            event.getReason()
        );
        
        reversal.setVoidReference(voidReference);
        reversalRepository.save(reversal);
        
        kafkaTemplate.send("transaction-reversals", Map.of(
            "reversalId", event.getReversalId(),
            "status", "REVERSAL_COMPLETED",
            "voidReference", voidReference,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordVoidProcessed(event.getAmount());
        
        log.info("Void processed: reversalId={}, voidReference={}", 
            event.getReversalId(), voidReference);
    }
    
    private void processRefund(TransactionReversalEvent event, String correlationId) {
        TransactionReversal reversal = reversalRepository.findByReversalId(event.getReversalId())
            .orElseThrow(() -> new RuntimeException("Reversal not found"));
        
        reversal.setStatus("REFUND_PROCESSING");
        reversal.setProcessingStartedAt(LocalDateTime.now());
        reversalRepository.save(reversal);
        
        String refundReference = refundService.processRefund(
            event.getOriginalTransactionId(),
            event.getAmount(),
            event.getReason(),
            event.getRefundMethod()
        );
        
        reversal.setRefundReference(refundReference);
        reversalRepository.save(reversal);
        
        kafkaTemplate.send("ledger-recorded-events", Map.of(
            "transactionId", event.getReversalId(),
            "originalTransactionId", event.getOriginalTransactionId(),
            "fromAccountId", event.getMerchantId(),
            "toAccountId", event.getCustomerAccountId(),
            "amount", event.getAmount(),
            "transactionType", "REFUND",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        kafkaTemplate.send("transaction-reversals", Map.of(
            "reversalId", event.getReversalId(),
            "status", "REVERSAL_COMPLETED",
            "refundReference", refundReference,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordRefundProcessed(event.getAmount());
        
        log.info("Refund processed: reversalId={}, refundReference={}", 
            event.getReversalId(), refundReference);
    }
    
    private void processChargeback(TransactionReversalEvent event, String correlationId) {
        TransactionReversal reversal = reversalRepository.findByReversalId(event.getReversalId())
            .orElseThrow(() -> new RuntimeException("Reversal not found"));
        
        reversal.setStatus("CHARGEBACK_PROCESSING");
        reversal.setProcessingStartedAt(LocalDateTime.now());
        reversalRepository.save(reversal);
        
        String chargebackId = reversalService.initiateChargeback(
            event.getOriginalTransactionId(),
            event.getAmount(),
            event.getReason(),
            event.getChargebackReasonCode()
        );
        
        reversal.setChargebackId(chargebackId);
        reversalRepository.save(reversal);
        
        kafkaTemplate.send("chargeback-alerts", Map.of(
            "chargebackId", chargebackId,
            "paymentId", event.getOriginalTransactionId(),
            "merchantId", event.getMerchantId(),
            "amount", event.getAmount(),
            "reasonCode", event.getChargebackReasonCode(),
            "status", "CHARGEBACK_RECEIVED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        kafkaTemplate.send("transaction-reversals", Map.of(
            "reversalId", event.getReversalId(),
            "status", "REVERSAL_COMPLETED",
            "chargebackId", chargebackId,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordChargebackInitiated(event.getAmount());
        
        log.error("Chargeback processed: reversalId={}, chargebackId={}", 
            event.getReversalId(), chargebackId);
    }
    
    private void completeReversal(TransactionReversalEvent event, String correlationId) {
        TransactionReversal reversal = reversalRepository.findByReversalId(event.getReversalId())
            .orElseThrow(() -> new RuntimeException("Reversal not found"));
        
        reversal.setStatus("REVERSAL_COMPLETED");
        reversal.setCompletedAt(LocalDateTime.now());
        reversal.setActualAmount(event.getActualAmount());
        reversalRepository.save(reversal);
        
        notificationService.sendNotification(event.getRequestedBy(), "Reversal Completed",
            String.format("Reversal %s completed successfully. Amount: %s %s", 
                event.getReversalId(), event.getActualAmount(), event.getCurrency()),
            correlationId);
        
        if (event.getMerchantId() != null) {
            notificationService.sendNotification(event.getMerchantId(), "Transaction Reversed",
                String.format("Transaction %s has been reversed. Amount: %s %s", 
                    event.getOriginalTransactionId(), event.getActualAmount(), event.getCurrency()),
                correlationId);
        }
        
        metricsService.recordReversalCompleted(reversal.getProcessingMethod(), event.getActualAmount());
        
        log.info("Reversal completed: id={}, originalTransactionId={}, actualAmount={}", 
            event.getReversalId(), event.getOriginalTransactionId(), event.getActualAmount());
    }
    
    private void handleReversalFailure(TransactionReversalEvent event, String correlationId) {
        TransactionReversal reversal = reversalRepository.findByReversalId(event.getReversalId())
            .orElseThrow(() -> new RuntimeException("Reversal not found"));
        
        reversal.setStatus("REVERSAL_FAILED");
        reversal.setFailedAt(LocalDateTime.now());
        reversal.setFailureReason(event.getFailureReason());
        reversal.setErrorCode(event.getErrorCode());
        reversalRepository.save(reversal);
        
        notificationService.sendNotification(event.getRequestedBy(), "Reversal Failed",
            String.format("Reversal %s failed: %s", event.getReversalId(), event.getFailureReason()),
            correlationId);
        
        notificationService.sendNotification("FINANCE_TEAM", "Reversal Processing Failed",
            String.format("Reversal %s failed for transaction %s. Reason: %s", 
                event.getReversalId(), event.getOriginalTransactionId(), event.getFailureReason()),
            correlationId);
        
        if (event.getRetryCount() < 3) {
            kafkaTemplate.send("reversal-retry-queue", Map.of(
                "reversalId", event.getReversalId(),
                "retryCount", event.getRetryCount() + 1,
                "retryAfter", LocalDateTime.now().plusHours(1),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        metricsService.recordReversalFailed(event.getReversalType(), event.getFailureReason());
        
        log.error("Reversal failed: id={}, originalTransactionId={}, reason={}, errorCode={}", 
            event.getReversalId(), event.getOriginalTransactionId(), 
            event.getFailureReason(), event.getErrorCode());
    }
    
    private void handlePartialReversal(TransactionReversalEvent event, String correlationId) {
        TransactionReversal reversal = reversalRepository.findByReversalId(event.getReversalId())
            .orElseThrow(() -> new RuntimeException("Reversal not found"));
        
        reversal.setStatus("REVERSAL_PARTIAL");
        reversal.setPartiallyCompletedAt(LocalDateTime.now());
        reversal.setPartialAmount(event.getPartialAmount());
        reversal.setRemainingAmount(event.getAmount().subtract(event.getPartialAmount()));
        reversalRepository.save(reversal);
        
        notificationService.sendNotification(event.getRequestedBy(), "Partial Reversal Completed",
            String.format("Partial reversal %s completed. Amount processed: %s %s. Remaining: %s %s", 
                event.getReversalId(), event.getPartialAmount(), event.getCurrency(),
                reversal.getRemainingAmount(), event.getCurrency()),
            correlationId);
        
        if (reversal.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
            kafkaTemplate.send("reversal-remainder-queue", Map.of(
                "originalReversalId", event.getReversalId(),
                "remainingAmount", reversal.getRemainingAmount(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        metricsService.recordPartialReversal(event.getReversalType(), event.getPartialAmount());
        
        log.warn("Partial reversal completed: id={}, partialAmount={}, remainingAmount={}", 
            event.getReversalId(), event.getPartialAmount(), reversal.getRemainingAmount());
    }
}