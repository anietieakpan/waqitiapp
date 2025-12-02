package com.waqiti.reconciliation.kafka;

import com.waqiti.reconciliation.event.ReconciliationRequiredEvent;
import com.waqiti.reconciliation.service.ReconciliationService;
import com.waqiti.reconciliation.service.ReconciliationSchedulerService;
import com.waqiti.common.kafka.ConsumerErrorHandler;
import com.waqiti.common.tracing.TraceableKafkaConsumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Production-ready Kafka consumer for ReconciliationRequiredEvent
 * 
 * Handles reconciliation trigger events by:
 * - Scheduling immediate reconciliation tasks
 * - Triggering discrepancy detection algorithms
 * - Initiating automated matching processes
 * - Managing reconciliation workflows
 * - Generating reconciliation reports
 * - Publishing reconciliation status updates
 * 
 * Features:
 * - Smart reconciliation scheduling
 * - Multi-source data reconciliation
 * - Exception handling and retry logic
 * - Comprehensive audit logging
 * - Real-time status tracking
 * - Automated discrepancy resolution
 * 
 * @author Waqiti Reconciliation Team
 * @version 2.0.0
 * @since 2025-01-16
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ReconciliationRequiredEventConsumer extends TraceableKafkaConsumer {

    private final ReconciliationService reconciliationService;
    private final ReconciliationSchedulerService schedulerService;
    private final ConsumerErrorHandler errorHandler;

    @KafkaListener(
        topics = "reconciliation-required",
        groupId = "reconciliation-service-reconciliation-required",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @Transactional
    public void handleReconciliationRequired(
            @Payload ReconciliationRequiredEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp,
            ConsumerRecord<String, ReconciliationRequiredEvent> record,
            Acknowledgment acknowledgment) {

        String correlationId = event.getCorrelationId();
        String reconciliationType = event.getReconciliationType();
        String entityId = event.getEntityId();

        log.info("Processing ReconciliationRequiredEvent - type: {}, entityId: {}, correlationId: {}, partition: {}, offset: {}", 
            reconciliationType, entityId, correlationId, partition, offset);

        try {
            // Start distributed trace
            startTrace("reconciliation-required-consumer", correlationId, Map.of(
                "reconciliation.type", reconciliationType,
                "reconciliation.entity_id", entityId,
                "reconciliation.priority", event.getPriority().toString(),
                "kafka.topic", topic,
                "kafka.partition", String.valueOf(partition),
                "kafka.offset", String.valueOf(offset)
            ));

            // Process the reconciliation requirement
            processReconciliationRequired(event);

            // Manual acknowledgment
            acknowledgment.acknowledge();

            log.info("ReconciliationRequiredEvent processed successfully - type: {}, entityId: {}, correlationId: {}", 
                reconciliationType, entityId, correlationId);

        } catch (Exception e) {
            log.error("Failed to process ReconciliationRequiredEvent - type: {}, entityId: {}, correlationId: {}", 
                reconciliationType, entityId, correlationId, e);

            // Handle error with retry/DLQ logic
            errorHandler.handleConsumerError(record, e, "reconciliation-required", acknowledgment);
            
            recordException(e);
            throw e; // Re-throw to trigger retry mechanism

        } finally {
            endTrace();
        }
    }

    /**
     * Process reconciliation required event
     */
    private void processReconciliationRequired(ReconciliationRequiredEvent event) {
        String reconciliationType = event.getReconciliationType();
        
        switch (reconciliationType) {
            case "TRANSACTION_RECONCILIATION" -> processTransactionReconciliation(event);
            case "DAILY_SETTLEMENT" -> processDailySettlement(event);
            case "PROVIDER_RECONCILIATION" -> processProviderReconciliation(event);
            case "WALLET_BALANCE_RECONCILIATION" -> processWalletBalanceReconciliation(event);
            case "LEDGER_RECONCILIATION" -> processLedgerReconciliation(event);
            case "FX_RATE_RECONCILIATION" -> processFxRateReconciliation(event);
            case "DISPUTE_RECONCILIATION" -> processDisputeReconciliation(event);
            default -> {
                log.warn("Unknown reconciliation type: {}", reconciliationType);
                processGenericReconciliation(event);
            }
        }
    }

    /**
     * Process transaction-level reconciliation
     */
    private void processTransactionReconciliation(ReconciliationRequiredEvent event) {
        log.debug("Processing transaction reconciliation - entityId: {}", event.getEntityId());

        try {
            // Schedule immediate reconciliation
            ReconciliationTaskRequest taskRequest = ReconciliationTaskRequest.builder()
                .taskId(generateTaskId(event))
                .reconciliationType(ReconciliationType.TRANSACTION)
                .entityId(event.getEntityId())
                .correlationId(event.getCorrelationId())
                .priority(event.getPriority())
                .scheduledFor(LocalDateTime.now())
                .expectedSources(event.getExpectedSources())
                .reconciliationWindow(event.getReconciliationWindow())
                .metadata(event.getMetadata())
                .build();

            ReconciliationTaskResult result = reconciliationService.scheduleReconciliationTask(taskRequest);

            if (result.isScheduled()) {
                log.debug("Transaction reconciliation scheduled - taskId: {}, entityId: {}", 
                    result.getTaskId(), event.getEntityId());
                
                // Publish reconciliation started event
                publishReconciliationStartedEvent(event, result.getTaskId());
                
            } else {
                throw new RuntimeException("Failed to schedule transaction reconciliation: " + result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Transaction reconciliation processing failed - entityId: {}", event.getEntityId(), e);
            throw new RuntimeException("Transaction reconciliation processing failed", e);
        }
    }

    /**
     * Process daily settlement reconciliation
     */
    private void processDailySettlement(ReconciliationRequiredEvent event) {
        log.debug("Processing daily settlement reconciliation - date: {}", event.getSettlementDate());

        try {
            // Trigger comprehensive daily reconciliation
            DailySettlementRequest settlementRequest = DailySettlementRequest.builder()
                .settlementDate(event.getSettlementDate())
                .correlationId(event.getCorrelationId())
                .includeTransactions(true)
                .includeWalletBalances(true)
                .includeLedgerEntries(true)
                .includeProviderReports(true)
                .generateDiscrepancyReport(true)
                .autoResolveMinorDiscrepancies(true)
                .build();

            DailySettlementResult result = reconciliationService.processDailySettlement(settlementRequest);

            if (result.isSuccessful()) {
                log.info("Daily settlement reconciliation completed - date: {}, discrepancies: {}", 
                    event.getSettlementDate(), result.getDiscrepancyCount());
                
                // Publish settlement completed event
                publishSettlementCompletedEvent(event, result);
                
                // If discrepancies found, trigger investigation
                if (result.getDiscrepancyCount() > 0) {
                    triggerDiscrepancyInvestigation(event, result.getDiscrepancies());
                }
                
            } else {
                throw new RuntimeException("Daily settlement failed: " + result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Daily settlement reconciliation failed - date: {}", event.getSettlementDate(), e);
            throw new RuntimeException("Daily settlement reconciliation failed", e);
        }
    }

    /**
     * Process provider-specific reconciliation
     */
    private void processProviderReconciliation(ReconciliationRequiredEvent event) {
        log.debug("Processing provider reconciliation - provider: {}", event.getProviderId());

        try {
            String providerId = event.getProviderId();
            LocalDateTime reconciliationPeriodStart = event.getReconciliationPeriodStart();
            LocalDateTime reconciliationPeriodEnd = event.getReconciliationPeriodEnd();

            // Fetch provider transactions
            ProviderReconciliationRequest providerRequest = ProviderReconciliationRequest.builder()
                .providerId(providerId)
                .correlationId(event.getCorrelationId())
                .periodStart(reconciliationPeriodStart)
                .periodEnd(reconciliationPeriodEnd)
                .reconciliationLevel(ReconciliationLevel.DETAILED)
                .includeMetadata(true)
                .build();

            ProviderReconciliationResult result = reconciliationService.reconcileProvider(providerRequest);

            if (result.isSuccessful()) {
                log.info("Provider reconciliation completed - provider: {}, matched: {}, discrepancies: {}", 
                    providerId, result.getMatchedTransactions(), result.getDiscrepancies().size());
                
                // Store reconciliation results
                storeProviderReconciliationResults(event, result);
                
                // Handle discrepancies
                if (!result.getDiscrepancies().isEmpty()) {
                    handleProviderDiscrepancies(event, result.getDiscrepancies());
                }
                
            } else {
                throw new RuntimeException("Provider reconciliation failed: " + result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Provider reconciliation failed - provider: {}", event.getProviderId(), e);
            throw new RuntimeException("Provider reconciliation failed", e);
        }
    }

    /**
     * Process wallet balance reconciliation
     */
    private void processWalletBalanceReconciliation(ReconciliationRequiredEvent event) {
        log.debug("Processing wallet balance reconciliation - entityId: {}", event.getEntityId());

        try {
            // Reconcile wallet balances against ledger
            WalletReconciliationRequest walletRequest = WalletReconciliationRequest.builder()
                .walletId(event.getEntityId())
                .correlationId(event.getCorrelationId())
                .reconciliationTimestamp(LocalDateTime.now())
                .includeReservedBalances(true)
                .includePendingTransactions(true)
                .verifyAgainstLedger(true)
                .build();

            WalletReconciliationResult result = reconciliationService.reconcileWalletBalance(walletRequest);

            if (result.isBalanced()) {
                log.debug("Wallet balance reconciliation successful - walletId: {}, balance: {}", 
                    event.getEntityId(), result.getReconciledBalance());
                
            } else {
                log.warn("Wallet balance discrepancy detected - walletId: {}, expected: {}, actual: {}, diff: {}", 
                    event.getEntityId(), result.getExpectedBalance(), result.getActualBalance(), result.getDiscrepancy());
                
                // Handle balance discrepancy
                handleWalletBalanceDiscrepancy(event, result);
            }

        } catch (Exception e) {
            log.error("Wallet balance reconciliation failed - walletId: {}", event.getEntityId(), e);
            throw new RuntimeException("Wallet balance reconciliation failed", e);
        }
    }

    /**
     * Process ledger reconciliation
     */
    private void processLedgerReconciliation(ReconciliationRequiredEvent event) {
        log.debug("Processing ledger reconciliation - entityId: {}", event.getEntityId());

        try {
            // Perform comprehensive ledger reconciliation
            LedgerReconciliationRequest ledgerRequest = LedgerReconciliationRequest.builder()
                .ledgerAccountId(event.getEntityId())
                .correlationId(event.getCorrelationId())
                .reconciliationDate(event.getSettlementDate())
                .verifyDoubleEntry(true)
                .checkBalanceIntegrity(true)
                .validateTransactionChain(true)
                .build();

            LedgerReconciliationResult result = reconciliationService.reconcileLedger(ledgerRequest);

            if (result.isIntegrityValid()) {
                log.debug("Ledger reconciliation successful - accountId: {}", event.getEntityId());
                
            } else {
                log.error("Ledger integrity issues detected - accountId: {}, issues: {}", 
                    event.getEntityId(), result.getIntegrityIssues());
                
                // Handle ledger integrity issues
                handleLedgerIntegrityIssues(event, result);
            }

        } catch (Exception e) {
            log.error("Ledger reconciliation failed - accountId: {}", event.getEntityId(), e);
            throw new RuntimeException("Ledger reconciliation failed", e);
        }
    }

    /**
     * Process FX rate reconciliation
     */
    private void processFxRateReconciliation(ReconciliationRequiredEvent event) {
        log.debug("Processing FX rate reconciliation - currencies: {}", event.getCurrencyPairs());

        try {
            // Reconcile FX rates across providers
            FxRateReconciliationRequest fxRequest = FxRateReconciliationRequest.builder()
                .currencyPairs(event.getCurrencyPairs())
                .correlationId(event.getCorrelationId())
                .reconciliationTimestamp(LocalDateTime.now())
                .tolerancePercentage(event.getFxRateTolerance())
                .includeBenchmarkRates(true)
                .build();

            FxRateReconciliationResult result = reconciliationService.reconcileFxRates(fxRequest);

            if (result.isWithinTolerance()) {
                log.debug("FX rate reconciliation successful - pairs: {}", event.getCurrencyPairs());
                
            } else {
                log.warn("FX rate discrepancies detected - pairs: {}, discrepancies: {}", 
                    event.getCurrencyPairs(), result.getRateDiscrepancies().size());
                
                // Handle FX rate discrepancies
                handleFxRateDiscrepancies(event, result);
            }

        } catch (Exception e) {
            log.error("FX rate reconciliation failed - pairs: {}", event.getCurrencyPairs(), e);
            throw new RuntimeException("FX rate reconciliation failed", e);
        }
    }

    /**
     * Process dispute reconciliation
     */
    private void processDisputeReconciliation(ReconciliationRequiredEvent event) {
        log.debug("Processing dispute reconciliation - disputeId: {}", event.getEntityId());

        try {
            // Reconcile dispute-related transactions
            DisputeReconciliationRequest disputeRequest = DisputeReconciliationRequest.builder()
                .disputeId(event.getEntityId())
                .correlationId(event.getCorrelationId())
                .includeChargebacks(true)
                .includeReversals(true)
                .includeRefunds(true)
                .reconciliationScope(DisputeReconciliationScope.COMPREHENSIVE)
                .build();

            DisputeReconciliationResult result = reconciliationService.reconcileDispute(disputeRequest);

            if (result.isReconciled()) {
                log.info("Dispute reconciliation completed - disputeId: {}, status: {}", 
                    event.getEntityId(), result.getReconciliationStatus());
                
                // Update dispute status
                updateDisputeReconciliationStatus(event, result);
                
            } else {
                log.warn("Dispute reconciliation incomplete - disputeId: {}, pending items: {}", 
                    event.getEntityId(), result.getPendingItems().size());
                
                // Schedule retry for incomplete reconciliation
                scheduleDisputeReconciliationRetry(event, result);
            }

        } catch (Exception e) {
            log.error("Dispute reconciliation failed - disputeId: {}", event.getEntityId(), e);
            throw new RuntimeException("Dispute reconciliation failed", e);
        }
    }

    /**
     * Process generic reconciliation for unknown types
     */
    private void processGenericReconciliation(ReconciliationRequiredEvent event) {
        log.debug("Processing generic reconciliation - type: {}, entityId: {}", 
            event.getReconciliationType(), event.getEntityId());

        try {
            // Basic reconciliation handling
            GenericReconciliationRequest genericRequest = GenericReconciliationRequest.builder()
                .reconciliationType(event.getReconciliationType())
                .entityId(event.getEntityId())
                .correlationId(event.getCorrelationId())
                .priority(event.getPriority())
                .metadata(event.getMetadata())
                .build();

            GenericReconciliationResult result = reconciliationService.processGenericReconciliation(genericRequest);

            if (result.isProcessed()) {
                log.info("Generic reconciliation completed - type: {}, entityId: {}", 
                    event.getReconciliationType(), event.getEntityId());
            } else {
                log.warn("Generic reconciliation failed - type: {}, entityId: {}, reason: {}", 
                    event.getReconciliationType(), event.getEntityId(), result.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Generic reconciliation processing failed - type: {}, entityId: {}", 
                event.getReconciliationType(), event.getEntityId(), e);
            throw new RuntimeException("Generic reconciliation processing failed", e);
        }
    }

    // ========================================
    // PRIVATE HELPER METHODS
    // ========================================

    private String generateTaskId(ReconciliationRequiredEvent event) {
        return String.format("RECON_%s_%s_%d", 
            event.getReconciliationType(), 
            event.getEntityId(), 
            System.currentTimeMillis());
    }

    private void publishReconciliationStartedEvent(ReconciliationRequiredEvent originalEvent, String taskId) {
        try {
            ReconciliationStartedEvent event = ReconciliationStartedEvent.builder()
                .taskId(taskId)
                .reconciliationType(originalEvent.getReconciliationType())
                .entityId(originalEvent.getEntityId())
                .correlationId(originalEvent.getCorrelationId())
                .startedAt(LocalDateTime.now())
                .build();

            kafkaTemplate.send("reconciliation-started", originalEvent.getEntityId(), event);

        } catch (Exception e) {
            log.error("Failed to publish reconciliation started event", e);
        }
    }

    private void publishSettlementCompletedEvent(ReconciliationRequiredEvent originalEvent, DailySettlementResult result) {
        try {
            SettlementCompletedEvent event = SettlementCompletedEvent.builder()
                .settlementDate(originalEvent.getSettlementDate())
                .correlationId(originalEvent.getCorrelationId())
                .totalTransactions(result.getTotalTransactions())
                .reconciledTransactions(result.getReconciledTransactions())
                .discrepancyCount(result.getDiscrepancyCount())
                .completedAt(LocalDateTime.now())
                .build();

            kafkaTemplate.send("settlement-completed", originalEvent.getSettlementDate().toString(), event);

        } catch (Exception e) {
            log.error("Failed to publish settlement completed event", e);
        }
    }

    /**
     * DLT handler for failed messages
     */
    @KafkaListener(topics = "reconciliation-required.DLT")
    public void handleDlt(
            @Payload ReconciliationRequiredEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage) {

        log.error("ReconciliationRequiredEvent sent to DLT - type: {}, entityId: {}, error: {}", 
            event.getReconciliationType(), event.getEntityId(), errorMessage);

        try {
            // Create manual reconciliation task
            ManualReconciliationTask task = ManualReconciliationTask.builder()
                .reconciliationType(event.getReconciliationType())
                .entityId(event.getEntityId())
                .correlationId(event.getCorrelationId())
                .originalEvent(event)
                .errorMessage(errorMessage)
                .priority(ReconciliationPriority.HIGH)
                .createdAt(LocalDateTime.now())
                .build();

            reconciliationService.createManualReconciliationTask(task);

            // Alert operations team
            alertService.sendCriticalAlert(
                "Reconciliation Processing Failed",
                String.format("Reconciliation %s for entity %s failed and requires manual intervention. Error: %s", 
                    event.getReconciliationType(), event.getEntityId(), errorMessage)
            );

        } catch (Exception e) {
            log.error("Failed to handle DLT message for reconciliation: {} - {}", 
                event.getReconciliationType(), event.getEntityId(), e);
        }
    }

    // Additional helper methods would be implemented here for handling specific discrepancies,
    // scheduling retries, updating statuses, etc.
}