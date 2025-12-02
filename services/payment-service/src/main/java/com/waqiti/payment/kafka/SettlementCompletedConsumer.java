package com.waqiti.payment.kafka;

import com.waqiti.common.events.SettlementCompletedEvent;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.payment.domain.SettlementBatch;
import com.waqiti.payment.repository.SettlementBatchRepository;
import com.waqiti.payment.service.SettlementReconciliationService;
import com.waqiti.payment.service.MerchantPayoutService;
import com.waqiti.payment.service.SettlementReportingService;
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
public class SettlementCompletedConsumer {
    
    private final SettlementBatchRepository batchRepository;
    private final SettlementReconciliationService reconciliationService;
    private final MerchantPayoutService payoutService;
    private final SettlementReportingService reportingService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final UniversalDLQHandler dlqHandler;
    
    @KafkaListener(
        topics = {"settlement-completed", "batch-settlement-events", "payout-completed"},
        groupId = "settlement-completed-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "6"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleSettlementCompleted(
            @Payload SettlementCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("settlement-%s-p%d-o%d", 
            event.getSettlementBatchId(), partition, offset);
        
        log.info("Processing settlement completed: batchId={}, merchantId={}, amount={}, currency={}",
            event.getSettlementBatchId(), event.getMerchantId(), event.getSettlementAmount(), event.getCurrency());
        
        try {
            SettlementBatch batch = batchRepository.findByBatchId(event.getSettlementBatchId())
                .orElse(createSettlementBatch(event, correlationId));
            
            switch (event.getStatus()) {
                case "SETTLEMENT_INITIATED":
                    initiateSettlement(batch, event, correlationId);
                    break;
                    
                case "FUNDS_COLLECTED":
                    processFundsCollection(batch, event, correlationId);
                    break;
                    
                case "RECONCILIATION_COMPLETED":
                    completeReconciliation(batch, event, correlationId);
                    break;
                    
                case "PAYOUT_INITIATED":
                    initiatePayout(batch, event, correlationId);
                    break;
                    
                case "PAYOUT_COMPLETED":
                    completePayout(batch, event, correlationId);
                    break;
                    
                case "SETTLEMENT_FAILED":
                    handleSettlementFailure(batch, event, correlationId);
                    break;
                    
                case "SETTLEMENT_RECONCILED":
                    finalizeSettlement(batch, event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown settlement status: {}", event.getStatus());
                    break;
            }
            
            auditService.logPaymentEvent("SETTLEMENT_COMPLETED_PROCESSED", event.getSettlementBatchId(),
                Map.of("merchantId", event.getMerchantId(), "status", event.getStatus(),
                    "amount", event.getSettlementAmount(), "currency", event.getCurrency(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing settlement completed event: partition={}, offset={}, error={}",
                partition, offset, e.getMessage(), e);

            kafkaTemplate.send("settlement-completed-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(),
                "correlationId", correlationId, "timestamp", Instant.now()));

            dlqHandler.handleFailedMessage(org.apache.kafka.clients.consumer.ConsumerRecord.class.cast(event), e)
                .thenAccept(result -> log.info("Message sent to DLQ: offset={}, destination={}, category={}",
                        offset, result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed - MESSAGE MAY BE LOST! " +
                            "partition={}, offset={}, error={}",
                            partition, offset, dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Settlement completed event processing failed", e);
        }
    }
    
    private void initiateSettlement(SettlementBatch batch, SettlementCompletedEvent event, String correlationId) {
        batch.setStatus("SETTLEMENT_INITIATED");
        batch.setInitiatedAt(LocalDateTime.now());
        batch.setTransactionCount(event.getTransactionCount());
        batch.setGrossAmount(event.getGrossAmount());
        batch.setFeeAmount(event.getFeeAmount());
        batch.setNetAmount(event.getSettlementAmount());
        batchRepository.save(batch);
        
        List<String> transactionIds = reconciliationService.validateBatchTransactions(
            event.getSettlementBatchId(),
            event.getTransactionIds()
        );
        
        batch.setValidatedTransactionIds(transactionIds);
        batchRepository.save(batch);
        
        kafkaTemplate.send("settlement-completed", Map.of(
            "settlementBatchId", event.getSettlementBatchId(),
            "merchantId", event.getMerchantId(),
            "status", "FUNDS_COLLECTED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordSettlementInitiated(event.getMerchantId(), event.getSettlementAmount());
        
        log.info("Settlement initiated: batchId={}, merchantId={}, transactionCount={}", 
            event.getSettlementBatchId(), event.getMerchantId(), event.getTransactionCount());
    }
    
    private void processFundsCollection(SettlementBatch batch, SettlementCompletedEvent event, String correlationId) {
        batch.setStatus("FUNDS_COLLECTED");
        batch.setFundsCollectedAt(LocalDateTime.now());
        batchRepository.save(batch);
        
        BigDecimal collectedAmount = reconciliationService.collectMerchantFunds(
            event.getMerchantId(),
            event.getSettlementBatchId(),
            event.getGrossAmount()
        );
        
        batch.setCollectedAmount(collectedAmount);
        batchRepository.save(batch);
        
        if (collectedAmount.compareTo(event.getGrossAmount()) != 0) {
            kafkaTemplate.send("settlement-discrepancy-alerts", Map.of(
                "settlementBatchId", event.getSettlementBatchId(),
                "merchantId", event.getMerchantId(),
                "expectedAmount", event.getGrossAmount(),
                "collectedAmount", collectedAmount,
                "discrepancy", event.getGrossAmount().subtract(collectedAmount),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        kafkaTemplate.send("settlement-completed", Map.of(
            "settlementBatchId", event.getSettlementBatchId(),
            "status", "RECONCILIATION_COMPLETED",
            "collectedAmount", collectedAmount,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordFundsCollected(event.getMerchantId(), collectedAmount);
        
        log.info("Funds collected: batchId={}, expected={}, collected={}", 
            event.getSettlementBatchId(), event.getGrossAmount(), collectedAmount);
    }
    
    private void completeReconciliation(SettlementBatch batch, SettlementCompletedEvent event, String correlationId) {
        batch.setStatus("RECONCILIATION_COMPLETED");
        batch.setReconciledAt(LocalDateTime.now());
        batchRepository.save(batch);
        
        Map<String, Object> reconciliationResult = reconciliationService.performSettlementReconciliation(
            event.getSettlementBatchId(),
            event.getTransactionIds(),
            event.getGrossAmount(),
            event.getFeeAmount()
        );
        
        boolean reconciled = (boolean) reconciliationResult.get("reconciled");
        List<String> discrepancies = (List<String>) reconciliationResult.get("discrepancies");
        
        batch.setReconciled(reconciled);
        batch.setDiscrepancies(discrepancies);
        batchRepository.save(batch);
        
        if (reconciled) {
            kafkaTemplate.send("settlement-completed", Map.of(
                "settlementBatchId", event.getSettlementBatchId(),
                "merchantId", event.getMerchantId(),
                "status", "PAYOUT_INITIATED",
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            notificationService.sendNotification("SETTLEMENT_TEAM", "Settlement Reconciliation Failed",
                String.format("Settlement batch %s failed reconciliation. Discrepancies: %s", 
                    event.getSettlementBatchId(), String.join(", ", discrepancies)),
                correlationId);
        }
        
        metricsService.recordReconciliationCompleted(event.getMerchantId(), reconciled);
        
        log.info("Reconciliation completed: batchId={}, reconciled={}, discrepancies={}", 
            event.getSettlementBatchId(), reconciled, discrepancies.size());
    }
    
    private void initiatePayout(SettlementBatch batch, SettlementCompletedEvent event, String correlationId) {
        batch.setStatus("PAYOUT_INITIATED");
        batch.setPayoutInitiatedAt(LocalDateTime.now());
        batchRepository.save(batch);
        
        String payoutReference = payoutService.initiateMerchantPayout(
            event.getMerchantId(),
            event.getSettlementAmount(),
            event.getCurrency(),
            event.getSettlementBatchId()
        );
        
        batch.setPayoutReference(payoutReference);
        batchRepository.save(batch);
        
        notificationService.sendNotification(event.getMerchantId(), "Payout Initiated",
            String.format("Your payout of %s %s has been initiated. Reference: %s", 
                event.getSettlementAmount(), event.getCurrency(), payoutReference),
            correlationId);
        
        metricsService.recordPayoutInitiated(event.getMerchantId(), event.getSettlementAmount());
        
        log.info("Payout initiated: batchId={}, merchantId={}, amount={}, reference={}", 
            event.getSettlementBatchId(), event.getMerchantId(), event.getSettlementAmount(), payoutReference);
    }
    
    private void completePayout(SettlementBatch batch, SettlementCompletedEvent event, String correlationId) {
        batch.setStatus("PAYOUT_COMPLETED");
        batch.setPayoutCompletedAt(LocalDateTime.now());
        batch.setActualPayoutAmount(event.getActualPayoutAmount());
        batchRepository.save(batch);
        
        payoutService.confirmPayoutDelivery(
            event.getMerchantId(),
            batch.getPayoutReference(),
            event.getActualPayoutAmount()
        );
        
        notificationService.sendNotification(event.getMerchantId(), "Payout Completed",
            String.format("Your payout of %s %s has been completed and sent to your bank account", 
                event.getActualPayoutAmount(), event.getCurrency()),
            correlationId);
        
        kafkaTemplate.send("settlement-completed", Map.of(
            "settlementBatchId", event.getSettlementBatchId(),
            "merchantId", event.getMerchantId(),
            "status", "SETTLEMENT_RECONCILED",
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordPayoutCompleted(event.getMerchantId(), event.getActualPayoutAmount());
        
        log.info("Payout completed: batchId={}, merchantId={}, amount={}", 
            event.getSettlementBatchId(), event.getMerchantId(), event.getActualPayoutAmount());
    }
    
    private void handleSettlementFailure(SettlementBatch batch, SettlementCompletedEvent event, String correlationId) {
        batch.setStatus("SETTLEMENT_FAILED");
        batch.setFailedAt(LocalDateTime.now());
        batch.setFailureReason(event.getFailureReason());
        batchRepository.save(batch);
        
        notificationService.sendNotification("SETTLEMENT_TEAM", "Settlement Failed",
            String.format("Settlement batch %s failed: %s. Merchant: %s, Amount: %s %s", 
                event.getSettlementBatchId(), event.getFailureReason(), 
                event.getMerchantId(), event.getSettlementAmount(), event.getCurrency()),
            correlationId);
        
        notificationService.sendNotification(event.getMerchantId(), "Settlement Delayed",
            String.format("Your settlement has been delayed due to a processing issue. We're working to resolve this. Reference: %s", 
                event.getSettlementBatchId()),
            correlationId);
        
        kafkaTemplate.send("settlement-retry-queue", Map.of(
            "settlementBatchId", event.getSettlementBatchId(),
            "merchantId", event.getMerchantId(),
            "retryAfter", LocalDateTime.now().plusHours(1),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordSettlementFailed(event.getMerchantId(), event.getFailureReason());
        
        log.error("Settlement failed: batchId={}, merchantId={}, reason={}", 
            event.getSettlementBatchId(), event.getMerchantId(), event.getFailureReason());
    }
    
    private void finalizeSettlement(SettlementBatch batch, SettlementCompletedEvent event, String correlationId) {
        batch.setStatus("SETTLEMENT_RECONCILED");
        batch.setFinalizedAt(LocalDateTime.now());
        batchRepository.save(batch);
        
        reportingService.generateSettlementReport(
            event.getSettlementBatchId(),
            event.getMerchantId(),
            batch
        );
        
        reconciliationService.archiveSettlementBatch(event.getSettlementBatchId());
        
        metricsService.recordSettlementFinalized(event.getMerchantId(), event.getSettlementAmount());
        
        log.info("Settlement finalized: batchId={}, merchantId={}, totalAmount={}", 
            event.getSettlementBatchId(), event.getMerchantId(), event.getSettlementAmount());
    }
    
    private SettlementBatch createSettlementBatch(SettlementCompletedEvent event, String correlationId) {
        return SettlementBatch.builder()
            .batchId(event.getSettlementBatchId())
            .merchantId(event.getMerchantId())
            .currency(event.getCurrency())
            .settlementDate(event.getSettlementDate())
            .status("SETTLEMENT_INITIATED")
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
    }
}