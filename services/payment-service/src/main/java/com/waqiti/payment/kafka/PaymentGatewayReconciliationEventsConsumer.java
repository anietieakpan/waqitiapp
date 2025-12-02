package com.waqiti.payment.kafka;

import com.waqiti.common.events.PaymentGatewayReconciliationEvent;
import com.waqiti.payment.domain.ReconciliationBatch;
import com.waqiti.payment.domain.ReconciliationDiscrepancy;
import com.waqiti.payment.repository.ReconciliationBatchRepository;
import com.waqiti.payment.repository.ReconciliationDiscrepancyRepository;
import com.waqiti.payment.service.GatewayReconciliationService;
import com.waqiti.payment.service.DiscrepancyResolutionService;
import com.waqiti.payment.metrics.ReconciliationMetricsService;
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

import java.time.LocalDateTime;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentGatewayReconciliationEventsConsumer {
    
    private final ReconciliationBatchRepository batchRepository;
    private final ReconciliationDiscrepancyRepository discrepancyRepository;
    private final GatewayReconciliationService reconciliationService;
    private final DiscrepancyResolutionService resolutionService;
    private final ReconciliationMetricsService metricsService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @KafkaListener(
        topics = {"gateway-reconciliation-events", "payment-settlement-reconciliation", "gateway-settlement-events"},
        groupId = "payment-reconciliation-service-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory",
        concurrency = "4"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleGatewayReconciliationEvent(
            @Payload PaymentGatewayReconciliationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("recon-%s-p%d-o%d", 
            event.getBatchId(), partition, offset);
        
        log.info("Processing gateway reconciliation event: batchId={}, type={}, gateway={}", 
            event.getBatchId(), event.getEventType(), event.getGateway());
        
        try {
            switch (event.getEventType()) {
                case RECONCILIATION_STARTED:
                    processReconciliationStarted(event, correlationId);
                    break;
                case SETTLEMENT_FILE_RECEIVED:
                    processSettlementFileReceived(event, correlationId);
                    break;
                case SETTLEMENT_FILE_PARSED:
                    processSettlementFileParsed(event, correlationId);
                    break;
                case MATCHING_STARTED:
                    processMatchingStarted(event, correlationId);
                    break;
                case TRANSACTION_MATCHED:
                    processTransactionMatched(event, correlationId);
                    break;
                case DISCREPANCY_DETECTED:
                    processDiscrepancyDetected(event, correlationId);
                    break;
                case AMOUNT_MISMATCH:
                    processAmountMismatch(event, correlationId);
                    break;
                case MISSING_TRANSACTION:
                    processMissingTransaction(event, correlationId);
                    break;
                case UNEXPECTED_TRANSACTION:
                    processUnexpectedTransaction(event, correlationId);
                    break;
                case RECONCILIATION_COMPLETED:
                    processReconciliationCompleted(event, correlationId);
                    break;
                case DISCREPANCY_RESOLVED:
                    processDiscrepancyResolved(event, correlationId);
                    break;
                default:
                    log.warn("Unknown reconciliation event type: {}", event.getEventType());
                    break;
            }
            
            auditService.logFinancialEvent(
                "RECONCILIATION_EVENT_PROCESSED",
                event.getBatchId(),
                Map.of(
                    "eventType", event.getEventType(),
                    "gateway", event.getGateway(),
                    "correlationId", correlationId,
                    "timestamp", Instant.now()
                )
            );
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process reconciliation event: {}", e.getMessage(), e);
            kafkaTemplate.send("gateway-reconciliation-events-dlq", Map.of(
                "originalEvent", event,
                "error", e.getMessage(),
                "correlationId", correlationId,
                "timestamp", Instant.now()
            ));
            acknowledgment.acknowledge();
        }
    }
    
    private void processReconciliationStarted(PaymentGatewayReconciliationEvent event, String correlationId) {
        log.info("Gateway reconciliation started: gateway={}, period={}", 
            event.getGateway(), event.getSettlementPeriod());
        
        ReconciliationBatch batch = ReconciliationBatch.builder()
            .id(event.getBatchId())
            .gateway(event.getGateway())
            .settlementPeriod(event.getSettlementPeriod())
            .settlementDate(event.getSettlementDate())
            .status("IN_PROGRESS")
            .startedAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        batchRepository.save(batch);
        reconciliationService.fetchSettlementFile(batch.getId(), event.getGateway());
        
        metricsService.recordReconciliationStarted(event.getGateway());
    }
    
    private void processSettlementFileReceived(PaymentGatewayReconciliationEvent event, String correlationId) {
        log.info("Settlement file received: batchId={}, fileName={}, fileSize={}", 
            event.getBatchId(), event.getSettlementFileName(), event.getFileSize());
        
        ReconciliationBatch batch = batchRepository.findById(event.getBatchId())
            .orElseThrow();
        
        batch.setSettlementFileName(event.getSettlementFileName());
        batch.setSettlementFileReceived(true);
        batch.setFileReceivedAt(LocalDateTime.now());
        batch.setFileSize(event.getFileSize());
        batchRepository.save(batch);
        
        reconciliationService.parseSettlementFile(batch.getId());
        metricsService.recordSettlementFileReceived(event.getGateway(), event.getFileSize());
    }
    
    private void processSettlementFileParsed(PaymentGatewayReconciliationEvent event, String correlationId) {
        log.info("Settlement file parsed: batchId={}, transactionCount={}, totalAmount={}", 
            event.getBatchId(), event.getGatewayTransactionCount(), event.getGatewayTotalAmount());
        
        ReconciliationBatch batch = batchRepository.findById(event.getBatchId())
            .orElseThrow();
        
        batch.setSettlementFileParsed(true);
        batch.setFileParsedAt(LocalDateTime.now());
        batch.setGatewayTransactionCount(event.getGatewayTransactionCount());
        batch.setGatewayTotalAmount(event.getGatewayTotalAmount());
        batchRepository.save(batch);
        
        reconciliationService.startMatching(batch.getId());
        metricsService.recordSettlementFileParsed(event.getGateway(), event.getGatewayTransactionCount());
    }
    
    private void processMatchingStarted(PaymentGatewayReconciliationEvent event, String correlationId) {
        log.info("Transaction matching started: batchId={}, internalCount={}, gatewayCount={}", 
            event.getBatchId(), event.getInternalTransactionCount(), event.getGatewayTransactionCount());
        
        ReconciliationBatch batch = batchRepository.findById(event.getBatchId())
            .orElseThrow();
        
        batch.setMatchingStartedAt(LocalDateTime.now());
        batch.setInternalTransactionCount(event.getInternalTransactionCount());
        batch.setInternalTotalAmount(event.getInternalTotalAmount());
        batchRepository.save(batch);
        
        metricsService.recordMatchingStarted(event.getGateway());
    }
    
    private void processTransactionMatched(PaymentGatewayReconciliationEvent event, String correlationId) {
        log.debug("Transaction matched: transactionId={}, gatewayRef={}", 
            event.getTransactionId(), event.getGatewayReference());
        
        ReconciliationBatch batch = batchRepository.findById(event.getBatchId())
            .orElseThrow();
        
        batch.incrementMatchedCount();
        batch.addMatchedAmount(event.getTransactionAmount());
        batchRepository.save(batch);
        
        metricsService.recordTransactionMatched(event.getGateway());
    }
    
    private void processDiscrepancyDetected(PaymentGatewayReconciliationEvent event, String correlationId) {
        log.warn("Reconciliation discrepancy detected: batchId={}, type={}, transactionId={}", 
            event.getBatchId(), event.getDiscrepancyType(), event.getTransactionId());
        
        ReconciliationBatch batch = batchRepository.findById(event.getBatchId())
            .orElseThrow();
        
        ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
            .id(UUID.randomUUID().toString())
            .batchId(event.getBatchId())
            .gateway(event.getGateway())
            .discrepancyType(event.getDiscrepancyType())
            .transactionId(event.getTransactionId())
            .gatewayReference(event.getGatewayReference())
            .detectedAt(LocalDateTime.now())
            .status("OPEN")
            .severity(calculateSeverity(event))
            .correlationId(correlationId)
            .build();
        
        discrepancyRepository.save(discrepancy);
        
        batch.incrementDiscrepancyCount();
        batchRepository.save(batch);
        
        if ("CRITICAL".equals(discrepancy.getSeverity())) {
            resolutionService.escalateDiscrepancy(discrepancy.getId());
        }
        
        metricsService.recordDiscrepancyDetected(event.getGateway(), event.getDiscrepancyType());
    }
    
    private void processAmountMismatch(PaymentGatewayReconciliationEvent event, String correlationId) {
        log.warn("Amount mismatch detected: transactionId={}, internal={}, gateway={}, diff={}", 
            event.getTransactionId(), 
            event.getInternalAmount(), 
            event.getGatewayAmount(),
            event.getAmountDifference());
        
        ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
            .id(UUID.randomUUID().toString())
            .batchId(event.getBatchId())
            .gateway(event.getGateway())
            .discrepancyType("AMOUNT_MISMATCH")
            .transactionId(event.getTransactionId())
            .gatewayReference(event.getGatewayReference())
            .internalAmount(event.getInternalAmount())
            .gatewayAmount(event.getGatewayAmount())
            .amountDifference(event.getAmountDifference())
            .detectedAt(LocalDateTime.now())
            .status("OPEN")
            .severity(calculateAmountMismatchSeverity(event.getAmountDifference()))
            .correlationId(correlationId)
            .build();
        
        discrepancyRepository.save(discrepancy);
        resolutionService.investigateAmountMismatch(discrepancy.getId());
        
        metricsService.recordAmountMismatch(
            event.getGateway(), 
            event.getAmountDifference()
        );
    }
    
    private void processMissingTransaction(PaymentGatewayReconciliationEvent event, String correlationId) {
        log.error("Missing transaction in gateway: transactionId={}, amount={}", 
            event.getTransactionId(), event.getInternalAmount());
        
        ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
            .id(UUID.randomUUID().toString())
            .batchId(event.getBatchId())
            .gateway(event.getGateway())
            .discrepancyType("MISSING_IN_GATEWAY")
            .transactionId(event.getTransactionId())
            .internalAmount(event.getInternalAmount())
            .detectedAt(LocalDateTime.now())
            .status("OPEN")
            .severity("HIGH")
            .correlationId(correlationId)
            .build();
        
        discrepancyRepository.save(discrepancy);
        resolutionService.investigateMissingTransaction(discrepancy.getId());
        
        notificationService.sendFinanceAlert(
            "Missing Transaction",
            String.format("Transaction %s (%.2f) not found in %s settlement", 
                event.getTransactionId(), event.getInternalAmount(), event.getGateway()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.recordMissingTransaction(event.getGateway());
    }
    
    private void processUnexpectedTransaction(PaymentGatewayReconciliationEvent event, String correlationId) {
        log.error("Unexpected transaction in gateway: gatewayRef={}, amount={}", 
            event.getGatewayReference(), event.getGatewayAmount());
        
        ReconciliationDiscrepancy discrepancy = ReconciliationDiscrepancy.builder()
            .id(UUID.randomUUID().toString())
            .batchId(event.getBatchId())
            .gateway(event.getGateway())
            .discrepancyType("UNEXPECTED_IN_GATEWAY")
            .gatewayReference(event.getGatewayReference())
            .gatewayAmount(event.getGatewayAmount())
            .detectedAt(LocalDateTime.now())
            .status("OPEN")
            .severity("HIGH")
            .correlationId(correlationId)
            .build();
        
        discrepancyRepository.save(discrepancy);
        resolutionService.investigateUnexpectedTransaction(discrepancy.getId());
        
        notificationService.sendFinanceAlert(
            "Unexpected Transaction",
            String.format("Unknown transaction %s (%.2f) found in %s settlement", 
                event.getGatewayReference(), event.getGatewayAmount(), event.getGateway()),
            NotificationService.Priority.HIGH
        );
        
        metricsService.recordUnexpectedTransaction(event.getGateway());
    }
    
    private void processReconciliationCompleted(PaymentGatewayReconciliationEvent event, String correlationId) {
        log.info("Gateway reconciliation completed: batchId={}, matched={}, discrepancies={}, matchRate={}%", 
            event.getBatchId(), 
            event.getMatchedCount(), 
            event.getDiscrepancyCount(),
            event.getMatchRate());
        
        ReconciliationBatch batch = batchRepository.findById(event.getBatchId())
            .orElseThrow();
        
        batch.setStatus("COMPLETED");
        batch.setCompletedAt(LocalDateTime.now());
        batch.setMatchedCount(event.getMatchedCount());
        batch.setDiscrepancyCount(event.getDiscrepancyCount());
        batch.setMatchRate(event.getMatchRate());
        
        BigDecimal amountDifference = batch.getInternalTotalAmount().subtract(batch.getGatewayTotalAmount());
        batch.setTotalAmountDifference(amountDifference);
        
        batchRepository.save(batch);
        
        if (event.getDiscrepancyCount() > 0) {
            notificationService.sendFinanceAlert(
                "Reconciliation Discrepancies",
                String.format("%s reconciliation completed with %d discrepancies. Match rate: %.2f%%", 
                    event.getGateway(), event.getDiscrepancyCount(), event.getMatchRate()),
                NotificationService.Priority.HIGH
            );
        }
        
        metricsService.recordReconciliationCompleted(
            event.getGateway(),
            event.getMatchRate(),
            event.getMatchedCount(),
            event.getDiscrepancyCount()
        );
    }
    
    private void processDiscrepancyResolved(PaymentGatewayReconciliationEvent event, String correlationId) {
        log.info("Discrepancy resolved: discrepancyId={}, resolution={}", 
            event.getDiscrepancyId(), event.getResolutionType());
        
        ReconciliationDiscrepancy discrepancy = discrepancyRepository.findById(event.getDiscrepancyId())
            .orElseThrow();
        
        discrepancy.setStatus("RESOLVED");
        discrepancy.setResolvedAt(LocalDateTime.now());
        discrepancy.setResolutionType(event.getResolutionType());
        discrepancy.setResolutionNotes(event.getResolutionNotes());
        discrepancy.setResolvedBy(event.getResolvedBy());
        discrepancyRepository.save(discrepancy);
        
        metricsService.recordDiscrepancyResolved(
            event.getGateway(),
            event.getResolutionType()
        );
    }
    
    private String calculateSeverity(PaymentGatewayReconciliationEvent event) {
        if ("MISSING_IN_GATEWAY".equals(event.getDiscrepancyType()) ||
            "UNEXPECTED_IN_GATEWAY".equals(event.getDiscrepancyType())) {
            return "HIGH";
        }
        
        if ("AMOUNT_MISMATCH".equals(event.getDiscrepancyType())) {
            return calculateAmountMismatchSeverity(event.getAmountDifference());
        }
        
        return "MEDIUM";
    }
    
    private String calculateAmountMismatchSeverity(BigDecimal amountDifference) {
        if (amountDifference == null) return "MEDIUM";
        
        BigDecimal absAmount = amountDifference.abs();
        if (absAmount.compareTo(new BigDecimal("1000")) > 0) {
            return "CRITICAL";
        } else if (absAmount.compareTo(new BigDecimal("100")) > 0) {
            return "HIGH";
        } else if (absAmount.compareTo(new BigDecimal("10")) > 0) {
            return "MEDIUM";
        }
        return "LOW";
    }
}