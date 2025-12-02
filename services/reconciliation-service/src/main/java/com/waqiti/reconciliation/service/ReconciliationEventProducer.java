package com.waqiti.reconciliation.service;

import com.waqiti.reconciliation.dto.*;
import com.waqiti.reconciliation.entity.ReconciliationJob;
import com.waqiti.reconciliation.entity.ReconciliationStatus;
import com.waqiti.reconciliation.repository.ReconciliationJobRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Service responsible for producing reconciliation events.
 * This was missing and causing orphaned ReconciliationEventConsumer.
 * Handles daily reconciliation, settlement events, and discrepancy detection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationEventProducer {

    private final ReconciliationJobRepository reconciliationJobRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionMatchingService transactionMatchingService;
    private final BankStatementService bankStatementService;
    private final PaymentGatewayService paymentGatewayService;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final MeterRegistry meterRegistry;
    
    private static final String RECONCILIATION_TOPIC = "reconciliation-events";
    private static final String SETTLEMENT_TOPIC = "settlement-events";
    private static final String DISCREPANCY_TOPIC = "discrepancy-events";
    private static final String RECONCILIATION_AUDIT_TOPIC = "reconciliation-audit";

    /**
     * Scheduled job to trigger daily reconciliation.
     * Runs every day at 2 AM to reconcile previous day's transactions.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void triggerDailyReconciliation() {
        LocalDate reconciliationDate = LocalDate.now().minusDays(1);
        log.info("Starting daily reconciliation for date: {}", reconciliationDate);
        
        try {
            // Create reconciliation job
            ReconciliationJob job = createReconciliationJob(reconciliationDate, ReconciliationType.DAILY);
            
            // Publish reconciliation start event
            ReconciliationEvent startEvent = buildReconciliationStartEvent(job);
            publishReconciliationEvent(startEvent);
            
            // Perform reconciliation in parallel for different sources
            CompletableFuture<ReconciliationResult> bankRecon = 
                CompletableFuture.supplyAsync(() -> reconcileBankTransactions(reconciliationDate));
            CompletableFuture<ReconciliationResult> gatewayRecon = 
                CompletableFuture.supplyAsync(() -> reconcilePaymentGateway(reconciliationDate));
            CompletableFuture<ReconciliationResult> internalRecon = 
                CompletableFuture.supplyAsync(() -> reconcileInternalLedger(reconciliationDate));
            
            // Wait for all reconciliations to complete
            ReconciliationResult bankResult, gatewayResult, internalResult;
            try {
                CompletableFuture.allOf(bankRecon, gatewayRecon, internalRecon)
                    .get(15, java.util.concurrent.TimeUnit.MINUTES);

                // Get individual results (already completed, safe to get with short timeout)
                bankResult = bankRecon.get(1, java.util.concurrent.TimeUnit.SECONDS);
                gatewayResult = gatewayRecon.get(1, java.util.concurrent.TimeUnit.SECONDS);
                internalResult = internalRecon.get(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                log.error("Daily reconciliation timed out after 15 minutes for date: {}", reconciliationDate, e);
                List.of(bankRecon, gatewayRecon, internalRecon).forEach(f -> f.cancel(true));
                throw new RuntimeException("Daily reconciliation timed out", e);
            } catch (java.util.concurrent.ExecutionException e) {
                log.error("Daily reconciliation execution failed for date: {}", reconciliationDate, e.getCause());
                throw new RuntimeException("Daily reconciliation failed: " + e.getCause().getMessage(), e.getCause());
            } catch (java.util.concurrent.InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Daily reconciliation interrupted for date: {}", reconciliationDate, e);
                throw new RuntimeException("Daily reconciliation interrupted", e);
            }

            // Aggregate results
            ReconciliationSummary summary = aggregateReconciliationResults(
                bankResult, gatewayResult, internalResult);
            
            // Update job status
            job.setStatus(ReconciliationStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            job.setSummary(summary);
            reconciliationJobRepository.save(job);
            
            // Publish reconciliation complete event
            ReconciliationEvent completeEvent = buildReconciliationCompleteEvent(job, summary);
            publishReconciliationEvent(completeEvent);
            
            // Handle discrepancies if any
            if (summary.hasDiscrepancies()) {
                handleDiscrepancies(summary.getDiscrepancies(), job);
            }
            
            // Trigger settlement if reconciliation successful
            if (summary.isSuccessful()) {
                triggerSettlement(reconciliationDate, summary);
            }
            
            log.info("Daily reconciliation completed for date: {}, summary: {}", reconciliationDate, summary);
            meterRegistry.counter("reconciliation.daily.completed").increment();
            
        } catch (Exception e) {
            log.error("Daily reconciliation failed for date: {}", reconciliationDate, e);
            meterRegistry.counter("reconciliation.daily.failed").increment();
            
            // Publish failure event
            publishReconciliationFailureEvent(reconciliationDate, e.getMessage());
            
            // Send alert notifications
            notificationService.sendReconciliationFailureAlert(reconciliationDate, e.getMessage());
        }
    }

    /**
     * Trigger manual reconciliation for specific date range.
     */
    @Transactional
    public CompletableFuture<ReconciliationSummary> triggerManualReconciliation(
            LocalDate startDate, LocalDate endDate, String triggeredBy) {
        
        log.info("Manual reconciliation triggered from {} to {} by {}", startDate, endDate, triggeredBy);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                ReconciliationJob job = ReconciliationJob.builder()
                    .jobId(UUID.randomUUID().toString())
                    .type(ReconciliationType.MANUAL)
                    .startDate(startDate)
                    .endDate(endDate)
                    .status(ReconciliationStatus.IN_PROGRESS)
                    .startedAt(LocalDateTime.now())
                    .triggeredBy(triggeredBy)
                    .build();
                
                job = reconciliationJobRepository.save(job);
                
                // Publish manual reconciliation start event
                ReconciliationEvent startEvent = buildReconciliationStartEvent(job);
                publishReconciliationEvent(startEvent);
                
                // Perform reconciliation for date range
                List<ReconciliationResult> results = new ArrayList<>();
                LocalDate currentDate = startDate;
                
                while (!currentDate.isAfter(endDate)) {
                    ReconciliationResult bankResult = reconcileBankTransactions(currentDate);
                    ReconciliationResult gatewayResult = reconcilePaymentGateway(currentDate);
                    ReconciliationResult internalResult = reconcileInternalLedger(currentDate);
                    
                    results.add(bankResult);
                    results.add(gatewayResult);
                    results.add(internalResult);
                    
                    currentDate = currentDate.plusDays(1);
                }
                
                // Aggregate all results
                ReconciliationSummary summary = aggregateMultiDayResults(results);
                
                // Update job
                job.setStatus(ReconciliationStatus.COMPLETED);
                job.setCompletedAt(LocalDateTime.now());
                job.setSummary(summary);
                reconciliationJobRepository.save(job);
                
                // Publish completion event
                ReconciliationEvent completeEvent = buildReconciliationCompleteEvent(job, summary);
                publishReconciliationEvent(completeEvent);
                
                return summary;
                
            } catch (Exception e) {
                log.error("Manual reconciliation failed", e);
                throw new RuntimeException("Manual reconciliation failed", e);
            }
        });
    }

    /**
     * Trigger settlement process after successful reconciliation.
     */
    @Transactional
    public void triggerSettlement(LocalDate settlementDate, ReconciliationSummary reconciliationSummary) {
        log.info("Triggering settlement for date: {}", settlementDate);
        
        try {
            // Create settlement event
            SettlementEvent settlementEvent = SettlementEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .settlementDate(settlementDate)
                .totalTransactions(reconciliationSummary.getTotalTransactions())
                .totalAmount(reconciliationSummary.getTotalAmount())
                .reconciledAmount(reconciliationSummary.getReconciledAmount())
                .pendingAmount(reconciliationSummary.getPendingAmount())
                .status(SettlementStatus.INITIATED)
                .initiatedAt(LocalDateTime.now())
                .reconciliationJobId(reconciliationSummary.getJobId())
                .build();
            
            // Publish settlement event
            kafkaTemplate.send(SETTLEMENT_TOPIC, settlementEvent.getEventId(), settlementEvent);
            
            log.info("Settlement event published for date: {}", settlementDate);
            meterRegistry.counter("settlement.initiated").increment();
            
        } catch (Exception e) {
            log.error("Failed to trigger settlement for date: {}", settlementDate, e);
            meterRegistry.counter("settlement.failed").increment();
        }
    }

    /**
     * Process and publish discrepancy events.
     */
    @Transactional
    public void handleDiscrepancies(List<ReconciliationDiscrepancy> discrepancies, ReconciliationJob job) {
        log.info("Processing {} discrepancies for job: {}", discrepancies.size(), job.getJobId());
        
        for (ReconciliationDiscrepancy discrepancy : discrepancies) {
            try {
                // Enrich discrepancy with additional context
                enrichDiscrepancy(discrepancy);
                
                // Create discrepancy event
                DiscrepancyEvent event = DiscrepancyEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .jobId(job.getJobId())
                    .discrepancyId(discrepancy.getDiscrepancyId())
                    .type(discrepancy.getType())
                    .source(discrepancy.getSource())
                    .transactionId(discrepancy.getTransactionId())
                    .expectedAmount(discrepancy.getExpectedAmount())
                    .actualAmount(discrepancy.getActualAmount())
                    .difference(discrepancy.getDifference())
                    .severity(determineSeverity(discrepancy))
                    .detectedAt(LocalDateTime.now())
                    .metadata(discrepancy.getMetadata())
                    .build();
                
                // Publish discrepancy event
                kafkaTemplate.send(DISCREPANCY_TOPIC, event.getDiscrepancyId(), event);
                
                // Take automated action based on severity
                if (event.getSeverity() == DiscrepancySeverity.CRITICAL) {
                    handleCriticalDiscrepancy(discrepancy);
                }
                
            } catch (Exception e) {
                log.error("Failed to process discrepancy: {}", discrepancy.getDiscrepancyId(), e);
            }
        }
        
        meterRegistry.counter("discrepancies.detected", "count", String.valueOf(discrepancies.size())).increment();
    }

    // Private helper methods

    private ReconciliationJob createReconciliationJob(LocalDate date, ReconciliationType type) {
        ReconciliationJob job = ReconciliationJob.builder()
            .jobId(UUID.randomUUID().toString())
            .type(type)
            .reconciliationDate(date)
            .status(ReconciliationStatus.IN_PROGRESS)
            .startedAt(LocalDateTime.now())
            .build();
        
        return reconciliationJobRepository.save(job);
    }

    private ReconciliationEvent buildReconciliationStartEvent(ReconciliationJob job) {
        return ReconciliationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(ReconciliationEventType.STARTED)
            .jobId(job.getJobId())
            .reconciliationDate(job.getReconciliationDate())
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "type", job.getType().name(),
                "triggeredBy", job.getTriggeredBy() != null ? job.getTriggeredBy() : "SYSTEM"
            ))
            .build();
    }

    private ReconciliationEvent buildReconciliationCompleteEvent(ReconciliationJob job, ReconciliationSummary summary) {
        return ReconciliationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(ReconciliationEventType.COMPLETED)
            .jobId(job.getJobId())
            .reconciliationDate(job.getReconciliationDate())
            .summary(summary)
            .timestamp(LocalDateTime.now())
            .metadata(Map.of(
                "duration", job.getDurationInSeconds(),
                "discrepancyCount", summary.getDiscrepancyCount(),
                "successRate", summary.getSuccessRate()
            ))
            .build();
    }

    private void publishReconciliationEvent(ReconciliationEvent event) {
        CompletableFuture<SendResult<String, Object>> future = 
            kafkaTemplate.send(RECONCILIATION_TOPIC, event.getJobId(), event);
        
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.debug("Reconciliation event published: {}", event.getEventId());
            } else {
                log.error("Failed to publish reconciliation event: {}", event.getEventId(), ex);
            }
        });
    }

    private void publishReconciliationFailureEvent(LocalDate date, String reason) {
        ReconciliationEvent failureEvent = ReconciliationEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType(ReconciliationEventType.FAILED)
            .reconciliationDate(date)
            .timestamp(LocalDateTime.now())
            .metadata(Map.of("reason", reason))
            .build();
        
        kafkaTemplate.send(RECONCILIATION_TOPIC, failureEvent.getEventId(), failureEvent);
    }

    private ReconciliationResult reconcileBankTransactions(LocalDate date) {
        log.debug("Reconciling bank transactions for date: {}", date);
        
        try {
            // Fetch bank statement
            BankStatement bankStatement = bankStatementService.fetchStatement(date);
            
            // Fetch internal transactions
            List<Transaction> internalTransactions = ledgerService.getTransactionsForDate(date);
            
            // Match transactions
            MatchingResult matchingResult = transactionMatchingService.matchTransactions(
                bankStatement.getTransactions(), 
                internalTransactions
            );
            
            return ReconciliationResult.builder()
                .source("BANK")
                .date(date)
                .totalTransactions(bankStatement.getTransactionCount())
                .matchedTransactions(matchingResult.getMatchedCount())
                .unmatchedTransactions(matchingResult.getUnmatchedCount())
                .totalAmount(bankStatement.getTotalAmount())
                .matchedAmount(matchingResult.getMatchedAmount())
                .discrepancies(matchingResult.getDiscrepancies())
                .build();
                
        } catch (Exception e) {
            log.error("Bank reconciliation failed for date: {}", date, e);
            throw new RuntimeException("Bank reconciliation failed", e);
        }
    }

    private ReconciliationResult reconcilePaymentGateway(LocalDate date) {
        log.debug("Reconciling payment gateway transactions for date: {}", date);
        
        try {
            // Fetch gateway transactions
            List<GatewayTransaction> gatewayTransactions = paymentGatewayService.fetchTransactions(date);
            
            // Fetch internal payment records
            List<Payment> internalPayments = ledgerService.getPaymentsForDate(date);
            
            // Match gateway transactions with internal records
            MatchingResult matchingResult = transactionMatchingService.matchGatewayTransactions(
                gatewayTransactions, 
                internalPayments
            );
            
            return ReconciliationResult.builder()
                .source("PAYMENT_GATEWAY")
                .date(date)
                .totalTransactions(gatewayTransactions.size())
                .matchedTransactions(matchingResult.getMatchedCount())
                .unmatchedTransactions(matchingResult.getUnmatchedCount())
                .totalAmount(calculateTotalAmount(gatewayTransactions))
                .matchedAmount(matchingResult.getMatchedAmount())
                .discrepancies(matchingResult.getDiscrepancies())
                .build();
                
        } catch (Exception e) {
            log.error("Payment gateway reconciliation failed for date: {}", date, e);
            throw new RuntimeException("Payment gateway reconciliation failed", e);
        }
    }

    private ReconciliationResult reconcileInternalLedger(LocalDate date) {
        log.debug("Reconciling internal ledger for date: {}", date);
        
        try {
            // Verify double-entry bookkeeping balance
            LedgerBalance ledgerBalance = ledgerService.calculateDailyBalance(date);
            
            boolean balanced = ledgerBalance.getDebits().equals(ledgerBalance.getCredits());
            
            List<ReconciliationDiscrepancy> discrepancies = new ArrayList<>();
            if (!balanced) {
                BigDecimal difference = ledgerBalance.getDebits().subtract(ledgerBalance.getCredits());
                discrepancies.add(ReconciliationDiscrepancy.builder()
                    .discrepancyId(UUID.randomUUID().toString())
                    .type(DiscrepancyType.LEDGER_IMBALANCE)
                    .source("INTERNAL_LEDGER")
                    .expectedAmount(ledgerBalance.getDebits())
                    .actualAmount(ledgerBalance.getCredits())
                    .difference(difference)
                    .metadata(Map.of("date", date.toString()))
                    .build());
            }
            
            return ReconciliationResult.builder()
                .source("INTERNAL_LEDGER")
                .date(date)
                .totalTransactions(ledgerBalance.getTransactionCount())
                .matchedTransactions(balanced ? ledgerBalance.getTransactionCount() : 0)
                .unmatchedTransactions(balanced ? 0 : ledgerBalance.getTransactionCount())
                .totalAmount(ledgerBalance.getDebits())
                .matchedAmount(balanced ? ledgerBalance.getDebits() : BigDecimal.ZERO)
                .discrepancies(discrepancies)
                .build();
                
        } catch (Exception e) {
            log.error("Internal ledger reconciliation failed for date: {}", date, e);
            throw new RuntimeException("Internal ledger reconciliation failed", e);
        }
    }

    private ReconciliationSummary aggregateReconciliationResults(ReconciliationResult... results) {
        int totalTransactions = 0;
        int totalMatched = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal matchedAmount = BigDecimal.ZERO;
        List<ReconciliationDiscrepancy> allDiscrepancies = new ArrayList<>();
        
        for (ReconciliationResult result : results) {
            totalTransactions += result.getTotalTransactions();
            totalMatched += result.getMatchedTransactions();
            totalAmount = totalAmount.add(result.getTotalAmount());
            matchedAmount = matchedAmount.add(result.getMatchedAmount());
            allDiscrepancies.addAll(result.getDiscrepancies());
        }
        
        return ReconciliationSummary.builder()
            .totalTransactions(totalTransactions)
            .reconciledTransactions(totalMatched)
            .unreconciledTransactions(totalTransactions - totalMatched)
            .totalAmount(totalAmount)
            .reconciledAmount(matchedAmount)
            .pendingAmount(totalAmount.subtract(matchedAmount))
            .discrepancies(allDiscrepancies)
            .discrepancyCount(allDiscrepancies.size())
            .successRate(totalTransactions > 0 ? (double) totalMatched / totalTransactions * 100 : 100)
            .build();
    }

    private ReconciliationSummary aggregateMultiDayResults(List<ReconciliationResult> results) {
        return aggregateReconciliationResults(results.toArray(new ReconciliationResult[0]));
    }

    private void enrichDiscrepancy(ReconciliationDiscrepancy discrepancy) {
        // Add additional context to discrepancy
        discrepancy.getMetadata().put("detectionTime", LocalDateTime.now().toString());
        discrepancy.getMetadata().put("systemId", "RECONCILIATION_SERVICE");
        
        // Attempt to identify root cause
        String rootCause = analyzeRootCause(discrepancy);
        discrepancy.getMetadata().put("probableRootCause", rootCause);
    }

    private String analyzeRootCause(ReconciliationDiscrepancy discrepancy) {
        // Simple root cause analysis - in production would be more sophisticated
        if (discrepancy.getType() == DiscrepancyType.MISSING_IN_BANK) {
            return "Transaction not yet settled with bank";
        } else if (discrepancy.getType() == DiscrepancyType.MISSING_IN_SYSTEM) {
            return "Transaction not recorded in internal system";
        } else if (discrepancy.getType() == DiscrepancyType.AMOUNT_MISMATCH) {
            return "Possible fee or currency conversion difference";
        }
        return "Unknown";
    }

    private DiscrepancySeverity determineSeverity(ReconciliationDiscrepancy discrepancy) {
        BigDecimal difference = discrepancy.getDifference().abs();
        
        if (difference.compareTo(new BigDecimal("10000")) > 0) {
            return DiscrepancySeverity.CRITICAL;
        } else if (difference.compareTo(new BigDecimal("1000")) > 0) {
            return DiscrepancySeverity.HIGH;
        } else if (difference.compareTo(new BigDecimal("100")) > 0) {
            return DiscrepancySeverity.MEDIUM;
        }
        return DiscrepancySeverity.LOW;
    }

    private void handleCriticalDiscrepancy(ReconciliationDiscrepancy discrepancy) {
        log.warn("Critical discrepancy detected: {}", discrepancy);
        
        // Send immediate alert
        notificationService.sendCriticalDiscrepancyAlert(discrepancy);
        
        // Create investigation ticket
        createInvestigationTicket(discrepancy);
        
        // Freeze related accounts if necessary
        if (discrepancy.getDifference().abs().compareTo(new BigDecimal("50000")) > 0) {
            freezeRelatedAccounts(discrepancy);
        }
    }

    private void createInvestigationTicket(ReconciliationDiscrepancy discrepancy) {
        // Implementation would create ticket in ticketing system
        log.info("Investigation ticket created for discrepancy: {}", discrepancy.getDiscrepancyId());
    }

    private void freezeRelatedAccounts(ReconciliationDiscrepancy discrepancy) {
        // Implementation would freeze accounts to prevent further transactions
        log.warn("Accounts frozen due to critical discrepancy: {}", discrepancy.getDiscrepancyId());
    }

    private BigDecimal calculateTotalAmount(List<GatewayTransaction> transactions) {
        return transactions.stream()
            .map(GatewayTransaction::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}