package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentReconciliationEvent;
import com.waqiti.payment.domain.PaymentReconciliation;
import com.waqiti.payment.repository.PaymentReconciliationRepository;
import com.waqiti.payment.service.ReconciliationService;
import com.waqiti.payment.service.GatewayReconciliationService;
import com.waqiti.payment.service.SettlementReconciliationService;
import com.waqiti.payment.service.DiscrepancyResolutionService;
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
import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentReconciliationConsumer {
    
    private final PaymentReconciliationRepository reconciliationRepository;
    private final ReconciliationService reconciliationService;
    private final GatewayReconciliationService gatewayReconciliationService;
    private final SettlementReconciliationService settlementReconciliationService;
    private final DiscrepancyResolutionService discrepancyService;
    private final PaymentMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    private static final BigDecimal DISCREPANCY_THRESHOLD = new BigDecimal("0.01");
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000.00");
    private static final int MAX_AUTO_RESOLUTION_ATTEMPTS = 3;
    private static final int RECONCILIATION_TIMEOUT_HOURS = 48;
    
    @KafkaListener(
        topics = {"payment-reconciliation", "gateway-reconciliation", "settlement-reconciliation"},
        groupId = "payment-reconciliation-service-group",
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
    public void handlePaymentReconciliation(
            @Payload PaymentReconciliationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("recon-%s-p%d-o%d", 
            event.getReconciliationId(), partition, offset);
        
        log.info("Processing payment reconciliation: id={}, type={}, batchId={}, transactionCount={}, amount={}",
            event.getReconciliationId(), event.getReconciliationType(), event.getBatchId(), 
            event.getTransactionCount(), event.getTotalAmount());
        
        try {
            switch (event.getReconciliationType()) {
                case "DAILY_RECONCILIATION":
                    processDailyReconciliation(event, correlationId);
                    break;
                    
                case "GATEWAY_RECONCILIATION":
                    processGatewayReconciliation(event, correlationId);
                    break;
                    
                case "SETTLEMENT_RECONCILIATION":
                    processSettlementReconciliation(event, correlationId);
                    break;
                    
                case "DISCREPANCY_INVESTIGATION":
                    processDiscrepancyInvestigation(event, correlationId);
                    break;
                    
                case "MANUAL_RECONCILIATION":
                    processManualReconciliation(event, correlationId);
                    break;
                    
                case "BATCH_RECONCILIATION":
                    processBatchReconciliation(event, correlationId);
                    break;
                    
                case "REAL_TIME_RECONCILIATION":
                    processRealTimeReconciliation(event, correlationId);
                    break;
                    
                case "CHARGEBACK_RECONCILIATION":
                    processChargebackReconciliation(event, correlationId);
                    break;
                    
                case "REFUND_RECONCILIATION":
                    processRefundReconciliation(event, correlationId);
                    break;
                    
                default:
                    log.warn("Unknown reconciliation type: {}", event.getReconciliationType());
                    break;
            }
            
            auditService.logPaymentEvent("RECONCILIATION_PROCESSED", event.getReconciliationId(),
                Map.of("reconciliationType", event.getReconciliationType(), "batchId", event.getBatchId(),
                    "transactionCount", event.getTransactionCount(), "totalAmount", event.getTotalAmount(),
                    "correlationId", correlationId, "timestamp", Instant.now()));
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process payment reconciliation: {}", e.getMessage(), e);
            kafkaTemplate.send("payment-reconciliation-dlq", Map.of(
                "originalEvent", event, "error", e.getMessage(), 
                "correlationId", correlationId, "timestamp", Instant.now()));
            acknowledgment.acknowledge();
        }
    }
    
    private void processDailyReconciliation(PaymentReconciliationEvent event, String correlationId) {
        PaymentReconciliation reconciliation = PaymentReconciliation.builder()
            .reconciliationId(event.getReconciliationId())
            .reconciliationType("DAILY_RECONCILIATION")
            .reconciliationDate(event.getReconciliationDate())
            .status("RECONCILIATION_STARTED")
            .startedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        reconciliationRepository.save(reconciliation);
        
        Map<String, Object> internalData = reconciliationService.getInternalTransactionData(
            event.getReconciliationDate()
        );
        
        Map<String, Object> externalData = gatewayReconciliationService.getGatewayTransactionData(
            event.getReconciliationDate(),
            event.getGatewayProvider()
        );
        
        reconciliation.setInternalTransactionCount((Integer) internalData.get("transactionCount"));
        reconciliation.setInternalTotalAmount((BigDecimal) internalData.get("totalAmount"));
        reconciliation.setExternalTransactionCount((Integer) externalData.get("transactionCount"));
        reconciliation.setExternalTotalAmount((BigDecimal) externalData.get("totalAmount"));
        
        List<String> discrepancies = new ArrayList<>();
        boolean reconciled = true;
        
        if (!reconciliation.getInternalTransactionCount().equals(reconciliation.getExternalTransactionCount())) {
            discrepancies.add(String.format("Transaction count mismatch: Internal=%d, External=%d", 
                reconciliation.getInternalTransactionCount(), reconciliation.getExternalTransactionCount()));
            reconciled = false;
        }
        
        BigDecimal amountDifference = reconciliation.getInternalTotalAmount()
            .subtract(reconciliation.getExternalTotalAmount()).abs();
        
        if (amountDifference.compareTo(DISCREPANCY_THRESHOLD) > 0) {
            discrepancies.add(String.format("Amount mismatch: Internal=%s, External=%s, Difference=%s", 
                reconciliation.getInternalTotalAmount(), reconciliation.getExternalTotalAmount(), amountDifference));
            reconciled = false;
        }
        
        reconciliation.setReconciled(reconciled);
        reconciliation.setDiscrepancies(discrepancies);
        reconciliation.setDiscrepancyCount(discrepancies.size());
        reconciliation.setAmountDifference(amountDifference);
        
        if (reconciled) {
            reconciliation.setStatus("RECONCILIATION_COMPLETED");
            reconciliation.setCompletedAt(LocalDateTime.now());
        } else {
            reconciliation.setStatus("DISCREPANCIES_FOUND");
            
            kafkaTemplate.send("payment-reconciliation", Map.of(
                "reconciliationId", UUID.randomUUID().toString(),
                "parentReconciliationId", event.getReconciliationId(),
                "reconciliationType", "DISCREPANCY_INVESTIGATION",
                "discrepancies", discrepancies,
                "amountDifference", amountDifference,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        reconciliationRepository.save(reconciliation);
        
        kafkaTemplate.send("daily-reconciliation-completed", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "reconciliationDate", event.getReconciliationDate(),
            "reconciled", reconciled,
            "discrepancyCount", discrepancies.size(),
            "amountDifference", amountDifference,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        if (!reconciled) {
            notificationService.sendNotification("FINANCE_TEAM", "Daily Reconciliation Discrepancies",
                String.format("Daily reconciliation for %s found %d discrepancies. Amount difference: %s", 
                    event.getReconciliationDate(), discrepancies.size(), amountDifference),
                correlationId);
        }
        
        metricsService.recordDailyReconciliation(
            event.getReconciliationDate(), 
            reconciled, 
            discrepancies.size(),
            amountDifference
        );
        
        log.info("Daily reconciliation completed: date={}, reconciled={}, discrepancies={}, amountDiff={}", 
            event.getReconciliationDate(), reconciled, discrepancies.size(), amountDifference);
    }
    
    private void processGatewayReconciliation(PaymentReconciliationEvent event, String correlationId) {
        PaymentReconciliation reconciliation = PaymentReconciliation.builder()
            .reconciliationId(event.getReconciliationId())
            .reconciliationType("GATEWAY_RECONCILIATION")
            .gatewayProvider(event.getGatewayProvider())
            .batchId(event.getBatchId())
            .status("GATEWAY_RECONCILIATION_STARTED")
            .startedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        reconciliationRepository.save(reconciliation);
        
        Map<String, Object> gatewayReport = gatewayReconciliationService.downloadGatewayReport(
            event.getGatewayProvider(),
            event.getBatchId()
        );
        
        Map<String, Object> internalRecords = reconciliationService.getInternalRecordsForBatch(
            event.getBatchId(),
            event.getGatewayProvider()
        );
        
        reconciliation.setGatewayReportData(gatewayReport);
        reconciliation.setInternalRecords(internalRecords);
        
        Map<String, Object> reconciliationResult = gatewayReconciliationService.performReconciliation(
            gatewayReport,
            internalRecords
        );
        
        boolean reconciled = (boolean) reconciliationResult.get("reconciled");
        List<String> discrepancies = (List<String>) reconciliationResult.get("discrepancies");
        List<String> missingTransactions = (List<String>) reconciliationResult.get("missingTransactions");
        List<String> extraTransactions = (List<String>) reconciliationResult.get("extraTransactions");
        
        reconciliation.setReconciled(reconciled);
        reconciliation.setDiscrepancies(discrepancies);
        reconciliation.setDiscrepancyCount(discrepancies.size());
        reconciliation.setMissingTransactions(missingTransactions);
        reconciliation.setExtraTransactions(extraTransactions);
        
        if (reconciled) {
            reconciliation.setStatus("GATEWAY_RECONCILIATION_COMPLETED");
            reconciliation.setCompletedAt(LocalDateTime.now());
        } else {
            reconciliation.setStatus("GATEWAY_DISCREPANCIES_FOUND");
            
            if (!missingTransactions.isEmpty()) {
                kafkaTemplate.send("missing-transaction-alerts", Map.of(
                    "reconciliationId", event.getReconciliationId(),
                    "gatewayProvider", event.getGatewayProvider(),
                    "missingTransactions", missingTransactions,
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
            
            if (!extraTransactions.isEmpty()) {
                kafkaTemplate.send("extra-transaction-alerts", Map.of(
                    "reconciliationId", event.getReconciliationId(),
                    "gatewayProvider", event.getGatewayProvider(),
                    "extraTransactions", extraTransactions,
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                ));
            }
        }
        
        reconciliationRepository.save(reconciliation);
        
        notificationService.sendNotification("PAYMENT_OPERATIONS", "Gateway Reconciliation Result",
            String.format("Gateway %s reconciliation: Reconciled=%s, Discrepancies=%d, Missing=%d, Extra=%d", 
                event.getGatewayProvider(), reconciled, discrepancies.size(), 
                missingTransactions.size(), extraTransactions.size()),
            correlationId);
        
        metricsService.recordGatewayReconciliation(
            event.getGatewayProvider(), 
            reconciled, 
            discrepancies.size()
        );
        
        log.info("Gateway reconciliation completed: gateway={}, reconciled={}, discrepancies={}", 
            event.getGatewayProvider(), reconciled, discrepancies.size());
    }
    
    private void processSettlementReconciliation(PaymentReconciliationEvent event, String correlationId) {
        PaymentReconciliation reconciliation = PaymentReconciliation.builder()
            .reconciliationId(event.getReconciliationId())
            .reconciliationType("SETTLEMENT_RECONCILIATION")
            .settlementBatchId(event.getSettlementBatchId())
            .merchantId(event.getMerchantId())
            .status("SETTLEMENT_RECONCILIATION_STARTED")
            .startedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        reconciliationRepository.save(reconciliation);
        
        Map<String, Object> settlementData = settlementReconciliationService.getSettlementData(
            event.getSettlementBatchId()
        );
        
        Map<String, Object> expectedSettlement = reconciliationService.calculateExpectedSettlement(
            event.getMerchantId(),
            event.getSettlementBatchId()
        );
        
        reconciliation.setExpectedAmount((BigDecimal) expectedSettlement.get("expectedAmount"));
        reconciliation.setActualAmount((BigDecimal) settlementData.get("actualAmount"));
        reconciliation.setExpectedFees((BigDecimal) expectedSettlement.get("expectedFees"));
        reconciliation.setActualFees((BigDecimal) settlementData.get("actualFees"));
        
        BigDecimal amountDifference = reconciliation.getExpectedAmount()
            .subtract(reconciliation.getActualAmount()).abs();
        BigDecimal feeDifference = reconciliation.getExpectedFees()
            .subtract(reconciliation.getActualFees()).abs();
        
        List<String> discrepancies = new ArrayList<>();
        boolean reconciled = true;
        
        if (amountDifference.compareTo(DISCREPANCY_THRESHOLD) > 0) {
            discrepancies.add(String.format("Settlement amount mismatch: Expected=%s, Actual=%s", 
                reconciliation.getExpectedAmount(), reconciliation.getActualAmount()));
            reconciled = false;
        }
        
        if (feeDifference.compareTo(DISCREPANCY_THRESHOLD) > 0) {
            discrepancies.add(String.format("Fee amount mismatch: Expected=%s, Actual=%s", 
                reconciliation.getExpectedFees(), reconciliation.getActualFees()));
            reconciled = false;
        }
        
        reconciliation.setReconciled(reconciled);
        reconciliation.setDiscrepancies(discrepancies);
        reconciliation.setDiscrepancyCount(discrepancies.size());
        reconciliation.setAmountDifference(amountDifference);
        reconciliation.setFeeDifference(feeDifference);
        
        if (reconciled) {
            reconciliation.setStatus("SETTLEMENT_RECONCILIATION_COMPLETED");
            reconciliation.setCompletedAt(LocalDateTime.now());
            
            kafkaTemplate.send("settlement-reconciliation-completed", Map.of(
                "reconciliationId", event.getReconciliationId(),
                "settlementBatchId", event.getSettlementBatchId(),
                "merchantId", event.getMerchantId(),
                "reconciled", true,
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            reconciliation.setStatus("SETTLEMENT_DISCREPANCIES_FOUND");
            
            if (amountDifference.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
                notificationService.sendNotification("FINANCE_MANAGER", "High Value Settlement Discrepancy",
                    String.format("Settlement batch %s has high value discrepancy: %s", 
                        event.getSettlementBatchId(), amountDifference),
                    correlationId);
            }
        }
        
        reconciliationRepository.save(reconciliation);
        
        metricsService.recordSettlementReconciliation(
            event.getMerchantId(), 
            reconciled, 
            amountDifference,
            feeDifference
        );
        
        log.info("Settlement reconciliation completed: batchId={}, reconciled={}, amountDiff={}, feeDiff={}", 
            event.getSettlementBatchId(), reconciled, amountDifference, feeDifference);
    }
    
    private void processDiscrepancyInvestigation(PaymentReconciliationEvent event, String correlationId) {
        PaymentReconciliation investigation = PaymentReconciliation.builder()
            .reconciliationId(event.getReconciliationId())
            .parentReconciliationId(event.getParentReconciliationId())
            .reconciliationType("DISCREPANCY_INVESTIGATION")
            .status("INVESTIGATION_STARTED")
            .startedAt(LocalDateTime.now())
            .investigationPriority(determineInvestigationPriority(event.getAmountDifference()))
            .correlationId(correlationId)
            .build();
        reconciliationRepository.save(investigation);
        
        Map<String, Object> investigationResult = discrepancyService.investigateDiscrepancies(
            event.getParentReconciliationId(),
            event.getDiscrepancies()
        );
        
        boolean autoResolved = (boolean) investigationResult.get("autoResolved");
        List<String> resolvedDiscrepancies = (List<String>) investigationResult.get("resolvedDiscrepancies");
        List<String> unresolvedDiscrepancies = (List<String>) investigationResult.get("unresolvedDiscrepancies");
        
        investigation.setAutoResolved(autoResolved);
        investigation.setResolvedDiscrepancies(resolvedDiscrepancies);
        investigation.setUnresolvedDiscrepancies(unresolvedDiscrepancies);
        investigation.setResolutionNotes((String) investigationResult.get("resolutionNotes"));
        
        if (autoResolved) {
            investigation.setStatus("INVESTIGATION_AUTO_RESOLVED");
            investigation.setCompletedAt(LocalDateTime.now());
            
            kafkaTemplate.send("discrepancy-auto-resolved", Map.of(
                "reconciliationId", event.getReconciliationId(),
                "parentReconciliationId", event.getParentReconciliationId(),
                "resolvedDiscrepancies", resolvedDiscrepancies,
                "resolutionNotes", investigation.getResolutionNotes(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            investigation.setStatus("INVESTIGATION_MANUAL_REQUIRED");
            investigation.setAssignedTo("FINANCE_ANALYST");
            investigation.setEscalationDeadline(LocalDateTime.now().plusHours(RECONCILIATION_TIMEOUT_HOURS));
            
            notificationService.sendNotification("FINANCE_ANALYST", "Manual Discrepancy Investigation Required",
                String.format("Investigation %s requires manual review. Unresolved discrepancies: %d", 
                    event.getReconciliationId(), unresolvedDiscrepancies.size()),
                correlationId);
            
            kafkaTemplate.send("manual-investigation-queue", Map.of(
                "reconciliationId", event.getReconciliationId(),
                "unresolvedDiscrepancies", unresolvedDiscrepancies,
                "priority", investigation.getInvestigationPriority(),
                "deadline", investigation.getEscalationDeadline(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        reconciliationRepository.save(investigation);
        
        metricsService.recordDiscrepancyInvestigation(
            autoResolved, 
            resolvedDiscrepancies.size(),
            unresolvedDiscrepancies.size()
        );
        
        log.info("Discrepancy investigation completed: reconciliationId={}, autoResolved={}, resolved={}, unresolved={}", 
            event.getReconciliationId(), autoResolved, resolvedDiscrepancies.size(), unresolvedDiscrepancies.size());
    }
    
    private void processManualReconciliation(PaymentReconciliationEvent event, String correlationId) {
        PaymentReconciliation reconciliation = reconciliationRepository
            .findByReconciliationId(event.getReconciliationId())
            .orElseThrow(() -> new RuntimeException("Reconciliation not found"));
        
        reconciliation.setStatus("MANUAL_RECONCILIATION_IN_PROGRESS");
        reconciliation.setManuallyReconciledBy(event.getReconciledBy());
        reconciliation.setManualReconciliationNotes(event.getReconciliationNotes());
        reconciliation.setManualReconciliationAt(LocalDateTime.now());
        
        if (event.getReconciliationDecision().equals("APPROVED")) {
            reconciliation.setReconciled(true);
            reconciliation.setStatus("MANUAL_RECONCILIATION_APPROVED");
            reconciliation.setCompletedAt(LocalDateTime.now());
            
            kafkaTemplate.send("manual-reconciliation-approved", Map.of(
                "reconciliationId", event.getReconciliationId(),
                "approvedBy", event.getReconciledBy(),
                "notes", event.getReconciliationNotes(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        } else {
            reconciliation.setStatus("MANUAL_RECONCILIATION_REJECTED");
            
            kafkaTemplate.send("manual-reconciliation-rejected", Map.of(
                "reconciliationId", event.getReconciliationId(),
                "rejectedBy", event.getReconciledBy(),
                "reason", event.getRejectionReason(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
        }
        
        reconciliationRepository.save(reconciliation);
        
        auditService.logPaymentEvent("MANUAL_RECONCILIATION_DECISION", event.getReconciliationId(),
            Map.of("decision", event.getReconciliationDecision(), "reconciledBy", event.getReconciledBy(),
                "notes", event.getReconciliationNotes(), "correlationId", correlationId));
        
        metricsService.recordManualReconciliation(
            event.getReconciliationDecision(), 
            event.getReconciledBy()
        );
        
        log.info("Manual reconciliation decision: reconciliationId={}, decision={}, by={}", 
            event.getReconciliationId(), event.getReconciliationDecision(), event.getReconciledBy());
    }
    
    private void processBatchReconciliation(PaymentReconciliationEvent event, String correlationId) {
        List<String> transactionIds = event.getTransactionIds();
        
        PaymentReconciliation batchReconciliation = PaymentReconciliation.builder()
            .reconciliationId(event.getReconciliationId())
            .reconciliationType("BATCH_RECONCILIATION")
            .batchId(event.getBatchId())
            .transactionCount(transactionIds.size())
            .status("BATCH_RECONCILIATION_STARTED")
            .startedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        reconciliationRepository.save(batchReconciliation);
        
        int reconciledCount = 0;
        int discrepancyCount = 0;
        BigDecimal totalAmountDifference = BigDecimal.ZERO;
        
        for (String transactionId : transactionIds) {
            Map<String, Object> transactionReconciliation = reconciliationService.reconcileTransaction(transactionId);
            
            boolean transactionReconciled = (boolean) transactionReconciliation.get("reconciled");
            BigDecimal amountDiff = (BigDecimal) transactionReconciliation.get("amountDifference");
            
            if (transactionReconciled) {
                reconciledCount++;
            } else {
                discrepancyCount++;
                totalAmountDifference = totalAmountDifference.add(amountDiff);
            }
        }
        
        boolean batchReconciled = (discrepancyCount == 0);
        
        batchReconciliation.setReconciled(batchReconciled);
        batchReconciliation.setReconciledTransactionCount(reconciledCount);
        batchReconciliation.setDiscrepancyCount(discrepancyCount);
        batchReconciliation.setAmountDifference(totalAmountDifference);
        batchReconciliation.setStatus(batchReconciled ? "BATCH_RECONCILIATION_COMPLETED" : "BATCH_DISCREPANCIES_FOUND");
        batchReconciliation.setCompletedAt(LocalDateTime.now());
        
        reconciliationRepository.save(batchReconciliation);
        
        kafkaTemplate.send("batch-reconciliation-completed", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "batchId", event.getBatchId(),
            "totalTransactions", transactionIds.size(),
            "reconciledCount", reconciledCount,
            "discrepancyCount", discrepancyCount,
            "totalAmountDifference", totalAmountDifference,
            "batchReconciled", batchReconciled,
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordBatchReconciliation(
            transactionIds.size(), 
            reconciledCount, 
            discrepancyCount
        );
        
        log.info("Batch reconciliation completed: batchId={}, total={}, reconciled={}, discrepancies={}", 
            event.getBatchId(), transactionIds.size(), reconciledCount, discrepancyCount);
    }
    
    private void processRealTimeReconciliation(PaymentReconciliationEvent event, String correlationId) {
        String transactionId = event.getTransactionId();
        
        PaymentReconciliation realTimeReconciliation = PaymentReconciliation.builder()
            .reconciliationId(event.getReconciliationId())
            .reconciliationType("REAL_TIME_RECONCILIATION")
            .transactionId(transactionId)
            .status("REAL_TIME_RECONCILIATION_STARTED")
            .startedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        reconciliationRepository.save(realTimeReconciliation);
        
        Map<String, Object> reconciliationResult = reconciliationService.performRealTimeReconciliation(transactionId);
        
        boolean reconciled = (boolean) reconciliationResult.get("reconciled");
        BigDecimal amountDifference = (BigDecimal) reconciliationResult.get("amountDifference");
        String discrepancyReason = (String) reconciliationResult.get("discrepancyReason");
        
        realTimeReconciliation.setReconciled(reconciled);
        realTimeReconciliation.setAmountDifference(amountDifference);
        realTimeReconciliation.setDiscrepancyReason(discrepancyReason);
        realTimeReconciliation.setStatus(reconciled ? "REAL_TIME_RECONCILIATION_COMPLETED" : "REAL_TIME_DISCREPANCY_FOUND");
        realTimeReconciliation.setCompletedAt(LocalDateTime.now());
        
        reconciliationRepository.save(realTimeReconciliation);
        
        if (!reconciled && amountDifference.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            notificationService.sendNotification("PAYMENT_OPERATIONS", "High Value Real-Time Discrepancy",
                String.format("Real-time reconciliation failed for transaction %s. Amount difference: %s. Reason: %s", 
                    transactionId, amountDifference, discrepancyReason),
                correlationId);
        }
        
        metricsService.recordRealTimeReconciliation(reconciled, amountDifference);
        
        log.info("Real-time reconciliation completed: transactionId={}, reconciled={}, amountDiff={}", 
            transactionId, reconciled, amountDifference);
    }
    
    private void processChargebackReconciliation(PaymentReconciliationEvent event, String correlationId) {
        String chargebackId = event.getChargebackId();
        
        Map<String, Object> chargebackData = reconciliationService.getChargebackData(chargebackId);
        Map<String, Object> originalTransaction = reconciliationService.getOriginalTransactionData(
            (String) chargebackData.get("originalTransactionId")
        );
        
        PaymentReconciliation chargebackReconciliation = PaymentReconciliation.builder()
            .reconciliationId(event.getReconciliationId())
            .reconciliationType("CHARGEBACK_RECONCILIATION")
            .chargebackId(chargebackId)
            .originalTransactionId((String) chargebackData.get("originalTransactionId"))
            .status("CHARGEBACK_RECONCILIATION_COMPLETED")
            .chargebackAmount((BigDecimal) chargebackData.get("chargebackAmount"))
            .originalAmount((BigDecimal) originalTransaction.get("originalAmount"))
            .startedAt(LocalDateTime.now())
            .completedAt(LocalDateTime.now())
            .reconciled(true)
            .correlationId(correlationId)
            .build();
        
        reconciliationRepository.save(chargebackReconciliation);
        
        kafkaTemplate.send("chargeback-reconciliation-completed", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "chargebackId", chargebackId,
            "originalTransactionId", chargebackData.get("originalTransactionId"),
            "chargebackAmount", chargebackData.get("chargebackAmount"),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordChargebackReconciliation(chargebackId);
        
        log.info("Chargeback reconciliation completed: chargebackId={}, amount={}", 
            chargebackId, chargebackData.get("chargebackAmount"));
    }
    
    private void processRefundReconciliation(PaymentReconciliationEvent event, String correlationId) {
        String refundId = event.getRefundId();
        
        Map<String, Object> refundData = reconciliationService.getRefundData(refundId);
        Map<String, Object> originalTransaction = reconciliationService.getOriginalTransactionData(
            (String) refundData.get("originalTransactionId")
        );
        
        PaymentReconciliation refundReconciliation = PaymentReconciliation.builder()
            .reconciliationId(event.getReconciliationId())
            .reconciliationType("REFUND_RECONCILIATION")
            .refundId(refundId)
            .originalTransactionId((String) refundData.get("originalTransactionId"))
            .status("REFUND_RECONCILIATION_COMPLETED")
            .refundAmount((BigDecimal) refundData.get("refundAmount"))
            .originalAmount((BigDecimal) originalTransaction.get("originalAmount"))
            .startedAt(LocalDateTime.now())
            .completedAt(LocalDateTime.now())
            .reconciled(true)
            .correlationId(correlationId)
            .build();
        
        reconciliationRepository.save(refundReconciliation);
        
        kafkaTemplate.send("refund-reconciliation-completed", Map.of(
            "reconciliationId", event.getReconciliationId(),
            "refundId", refundId,
            "originalTransactionId", refundData.get("originalTransactionId"),
            "refundAmount", refundData.get("refundAmount"),
            "correlationId", correlationId,
            "timestamp", Instant.now()
        ));
        
        metricsService.recordRefundReconciliation(refundId);
        
        log.info("Refund reconciliation completed: refundId={}, amount={}", 
            refundId, refundData.get("refundAmount"));
    }
    
    private String determineInvestigationPriority(BigDecimal amountDifference) {
        if (amountDifference.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            return "HIGH";
        } else if (amountDifference.compareTo(new BigDecimal("1000.00")) >= 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}