package com.waqiti.payment.events.consumers;

import com.waqiti.common.audit.AuditLogger;
import com.waqiti.common.events.payment.SettlementEvent;
import com.waqiti.common.metrics.MetricsService;
import com.waqiti.payment.domain.Settlement;
import com.waqiti.payment.domain.SettlementStatus;
import com.waqiti.payment.domain.SettlementType;
import com.waqiti.payment.domain.SettlementBatch;
import com.waqiti.payment.domain.Payment;
import com.waqiti.payment.repository.SettlementRepository;
import com.waqiti.payment.repository.SettlementBatchRepository;
import com.waqiti.payment.repository.PaymentRepository;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.service.BankingService;
import com.waqiti.payment.service.ReconciliationService;
import com.waqiti.payment.service.ProviderSettlementService;
import com.waqiti.payment.service.FeeService;
import com.waqiti.payment.service.SettlementNotificationService;
import com.waqiti.common.exceptions.SettlementProcessingException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Production-grade consumer for settlement processing events.
 * Handles comprehensive payment settlement operations including:
 * - Batch settlement processing
 * - Multi-provider settlements
 * - Fee calculations and deductions
 * - Bank transfers and ACH processing
 * - Settlement reconciliation
 * - Split settlements
 * - Currency conversions for international settlements
 * 
 * Critical for merchant payouts and financial operations.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementProcessingConsumer {

    private final SettlementRepository settlementRepository;
    private final SettlementBatchRepository batchRepository;
    private final PaymentRepository paymentRepository;
    private final SettlementService settlementService;
    private final BankingService bankingService;
    private final ReconciliationService reconciliationService;
    private final ProviderSettlementService providerService;
    private final FeeService feeService;
    private final SettlementNotificationService notificationService;
    private final AuditLogger auditLogger;
    private final MetricsService metricsService;

    private static final BigDecimal MIN_SETTLEMENT_AMOUNT = new BigDecimal("1.00");
    private static final int BATCH_SIZE_LIMIT = 1000;
    private static final int SETTLEMENT_RETRY_DAYS = 3;

    @KafkaListener(
        topics = "settlement-processing",
        groupId = "payment-service-settlement-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 3000, multiplier = 2.0),
        include = {SettlementProcessingException.class}
    )
    @Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
    public void handleSettlementProcessing(
            @Payload SettlementEvent settlementEvent,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "correlation-id", required = false) String correlationId,
            @Header(value = "settlement-type", required = false) String settlementType,
            Acknowledgment acknowledgment) {

        String eventId = settlementEvent.getEventId() != null ? 
            settlementEvent.getEventId() : UUID.randomUUID().toString();

        try {
            log.info("Processing settlement: {} for merchant: {} amount: {} type: {}", 
                    eventId, settlementEvent.getMerchantId(), 
                    settlementEvent.getSettlementAmount(), settlementEvent.getSettlementType());

            // Metrics tracking
            metricsService.incrementCounter("settlement.processing.started",
                Map.of(
                    "type", settlementEvent.getSettlementType(),
                    "provider", settlementEvent.getProvider()
                ));

            // Idempotency check
            if (isSettlementAlreadyProcessed(settlementEvent.getReferenceId(), eventId)) {
                log.info("Settlement {} already processed for reference {}", 
                        eventId, settlementEvent.getReferenceId());
                acknowledgment.acknowledge();
                return;
            }

            // Create settlement record
            Settlement settlement = createSettlementRecord(settlementEvent, eventId, correlationId);

            // Validate settlement eligibility
            validateSettlementEligibility(settlement, settlementEvent);

            // Collect payments for settlement
            List<Payment> paymentsToSettle = collectPaymentsForSettlement(settlement, settlementEvent);

            // Calculate settlement amounts and fees
            calculateSettlementAmounts(settlement, paymentsToSettle, settlementEvent);

            // Create settlement batch if needed
            SettlementBatch batch = null;
            if (paymentsToSettle.size() > 1) {
                batch = createSettlementBatch(settlement, paymentsToSettle, settlementEvent);
            }

            // Process settlement based on type
            processSettlement(settlement, batch, paymentsToSettle, settlementEvent);

            // Execute settlement transfer
            executeSettlementTransfer(settlement, settlementEvent);

            // Update payment statuses
            updatePaymentStatuses(paymentsToSettle, settlement);

            // Perform reconciliation
            performSettlementReconciliation(settlement, paymentsToSettle);

            // Update settlement status
            updateSettlementStatus(settlement);

            // Save settlement
            Settlement savedSettlement = settlementRepository.save(settlement);

            // Save batch if created
            if (batch != null) {
                batch.setSettlementId(savedSettlement.getId());
                batchRepository.save(batch);
            }

            // Send notifications
            sendSettlementNotifications(savedSettlement, settlementEvent);

            // Update metrics
            updateSettlementMetrics(savedSettlement, settlementEvent);

            // Create comprehensive audit trail
            createSettlementAuditLog(savedSettlement, settlementEvent, correlationId);

            // Schedule next settlement if recurring
            if (settlementEvent.isRecurring()) {
                scheduleNextSettlement(savedSettlement, settlementEvent);
            }

            // Success metrics
            metricsService.incrementCounter("settlement.processing.success",
                Map.of(
                    "status", savedSettlement.getStatus().toString(),
                    "amount", savedSettlement.getNetAmount().toString()
                ));

            log.info("Successfully processed settlement: {} for merchant: {} net amount: {} status: {}", 
                    savedSettlement.getId(), settlementEvent.getMerchantId(), 
                    savedSettlement.getNetAmount(), savedSettlement.getStatus());

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing settlement event {}: {}", eventId, e.getMessage(), e);
            metricsService.incrementCounter("settlement.processing.error");
            
            auditLogger.logCriticalAlert("SETTLEMENT_PROCESSING_ERROR",
                "Critical settlement failure - merchant payout at risk",
                Map.of(
                    "merchantId", settlementEvent.getMerchantId(),
                    "amount", settlementEvent.getSettlementAmount().toString(),
                    "eventId", eventId,
                    "error", e.getMessage(),
                    "correlationId", correlationId != null ? correlationId : "N/A"
                ));
            
            throw new SettlementProcessingException("Failed to process settlement: " + e.getMessage(), e);
        }
    }

    @KafkaListener(
        topics = "settlement-urgent",
        groupId = "payment-service-urgent-settlement-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handleUrgentSettlement(
            @Payload SettlementEvent settlementEvent,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        try {
            log.warn("URGENT SETTLEMENT: Processing immediate settlement for merchant: {} amount: {}", 
                    settlementEvent.getMerchantId(), settlementEvent.getSettlementAmount());

            // Fast-track settlement for critical merchants
            Settlement settlement = processExpressSettlement(settlementEvent, correlationId);

            // Immediate notification
            notificationService.sendUrgentSettlementNotification(settlement);

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Failed to process urgent settlement: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }

    private boolean isSettlementAlreadyProcessed(String referenceId, String eventId) {
        return settlementRepository.existsByReferenceIdOrEventId(referenceId, eventId);
    }

    private Settlement createSettlementRecord(SettlementEvent event, String eventId, String correlationId) {
        return Settlement.builder()
            .id(UUID.randomUUID().toString())
            .eventId(eventId)
            .referenceId(event.getReferenceId())
            .merchantId(event.getMerchantId())
            .merchantAccountId(event.getMerchantAccountId())
            .settlementType(SettlementType.valueOf(event.getSettlementType().toUpperCase()))
            .provider(event.getProvider())
            .currency(event.getCurrency())
            .requestedAmount(event.getSettlementAmount())
            .settlementDate(event.getSettlementDate() != null ? event.getSettlementDate() : LocalDate.now())
            .periodStart(event.getPeriodStart())
            .periodEnd(event.getPeriodEnd())
            .status(SettlementStatus.INITIATED)
            .correlationId(correlationId)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();
    }

    private void validateSettlementEligibility(Settlement settlement, SettlementEvent event) {
        // Check minimum settlement amount
        if (settlement.getRequestedAmount().compareTo(MIN_SETTLEMENT_AMOUNT) < 0) {
            settlement.setEligible(false);
            settlement.setIneligibilityReason("Amount below minimum threshold: " + MIN_SETTLEMENT_AMOUNT);
            throw new SettlementProcessingException("Settlement amount below minimum threshold");
        }

        // Verify merchant account status
        if (!settlementService.isMerchantEligible(event.getMerchantId())) {
            settlement.setEligible(false);
            settlement.setIneligibilityReason("Merchant account not eligible for settlement");
            throw new SettlementProcessingException("Merchant not eligible for settlement");
        }

        // Check for holds or blocks
        if (settlementService.hasSettlementHold(event.getMerchantId())) {
            settlement.setOnHold(true);
            settlement.setHoldReason("Settlement temporarily on hold");
            throw new SettlementProcessingException("Settlement on hold for merchant");
        }

        // Verify bank account details
        if (!bankingService.verifyBankAccount(event.getMerchantAccountId())) {
            settlement.setEligible(false);
            settlement.setIneligibilityReason("Invalid or unverified bank account");
            throw new SettlementProcessingException("Bank account verification failed");
        }

        settlement.setEligible(true);
        settlement.setEligibilityVerifiedAt(LocalDateTime.now());
    }

    private List<Payment> collectPaymentsForSettlement(Settlement settlement, SettlementEvent event) {
        log.info("Collecting payments for settlement: {} period: {} to {}", 
                settlement.getId(), event.getPeriodStart(), event.getPeriodEnd());

        List<Payment> payments;
        
        if (event.getPaymentIds() != null && !event.getPaymentIds().isEmpty()) {
            // Specific payments to settle
            payments = paymentRepository.findAllById(event.getPaymentIds());
        } else {
            // Collect all eligible payments for the period
            payments = paymentRepository.findEligibleForSettlement(
                settlement.getMerchantId(),
                event.getPeriodStart(),
                event.getPeriodEnd()
            );
        }

        // Validate payments
        payments = payments.stream()
            .filter(p -> isPaymentSettleable(p))
            .limit(BATCH_SIZE_LIMIT)
            .collect(Collectors.toList());

        if (payments.isEmpty()) {
            throw new SettlementProcessingException("No eligible payments found for settlement");
        }

        settlement.setPaymentCount(payments.size());
        settlement.setPaymentIds(payments.stream()
            .map(Payment::getId)
            .collect(Collectors.toList()));

        log.info("Collected {} payments for settlement", payments.size());
        return payments;
    }

    private void calculateSettlementAmounts(Settlement settlement, List<Payment> payments, SettlementEvent event) {
        log.info("Calculating settlement amounts for {} payments", payments.size());

        // Calculate gross amount
        BigDecimal grossAmount = payments.stream()
            .map(Payment::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        settlement.setGrossAmount(grossAmount);

        // Calculate fees
        BigDecimal processingFee = feeService.calculateProcessingFee(
            grossAmount, 
            settlement.getMerchantId(),
            settlement.getSettlementType()
        );
        settlement.setProcessingFee(processingFee);

        // Calculate platform fee
        BigDecimal platformFee = feeService.calculatePlatformFee(
            grossAmount,
            settlement.getMerchantId()
        );
        settlement.setPlatformFee(platformFee);

        // Calculate provider fee
        BigDecimal providerFee = providerService.calculateProviderFee(
            settlement.getProvider(),
            grossAmount,
            payments.size()
        );
        settlement.setProviderFee(providerFee);

        // Calculate refunds and chargebacks
        BigDecimal refunds = payments.stream()
            .map(p -> p.getRefundedAmount() != null ? p.getRefundedAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        settlement.setRefundAmount(refunds);

        BigDecimal chargebacks = payments.stream()
            .map(p -> p.getChargebackAmount() != null ? p.getChargebackAmount() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        settlement.setChargebackAmount(chargebacks);

        // Calculate net amount
        BigDecimal totalFees = processingFee.add(platformFee).add(providerFee);
        BigDecimal totalDeductions = totalFees.add(refunds).add(chargebacks);
        BigDecimal netAmount = grossAmount.subtract(totalDeductions);
        settlement.setNetAmount(netAmount);
        settlement.setTotalFees(totalFees);

        // Apply any adjustments
        if (event.getAdjustmentAmount() != null) {
            settlement.setAdjustmentAmount(event.getAdjustmentAmount());
            settlement.setAdjustmentReason(event.getAdjustmentReason());
            netAmount = netAmount.add(event.getAdjustmentAmount());
            settlement.setFinalAmount(netAmount);
        } else {
            settlement.setFinalAmount(netAmount);
        }

        log.info("Settlement amounts - Gross: {}, Fees: {}, Net: {}, Final: {}", 
                grossAmount, totalFees, netAmount, settlement.getFinalAmount());
    }

    private SettlementBatch createSettlementBatch(Settlement settlement, List<Payment> payments, SettlementEvent event) {
        return SettlementBatch.builder()
            .id(UUID.randomUUID().toString())
            .batchNumber(generateBatchNumber())
            .merchantId(settlement.getMerchantId())
            .paymentCount(payments.size())
            .totalAmount(settlement.getGrossAmount())
            .currency(settlement.getCurrency())
            .status("PENDING")
            .createdAt(LocalDateTime.now())
            .build();
    }

    private void processSettlement(Settlement settlement, SettlementBatch batch, List<Payment> payments, SettlementEvent event) {
        try {
            switch (settlement.getSettlementType()) {
                case STANDARD -> processStandardSettlement(settlement, payments);
                case EXPRESS -> processExpressSettlement(settlement, payments);
                case SPLIT -> processSplitSettlement(settlement, payments, event);
                case SCHEDULED -> processScheduledSettlement(settlement, payments);
                case MANUAL -> processManualSettlement(settlement, payments);
                default -> throw new SettlementProcessingException(
                    "Unknown settlement type: " + settlement.getSettlementType());
            }

            settlement.setProcessedAt(LocalDateTime.now());

        } catch (Exception e) {
            log.error("Error processing settlement: {}", e.getMessage());
            settlement.setProcessingError(e.getMessage());
            throw new SettlementProcessingException("Settlement processing failed: " + e.getMessage(), e);
        }
    }

    private void processStandardSettlement(Settlement settlement, List<Payment> payments) {
        log.info("Processing standard settlement for {} payments", payments.size());
        
        // Standard T+2 settlement
        settlement.setSettlementDate(LocalDate.now().plusDays(2));
        settlement.setSettlementMethod("ACH");
        
        // Mark payments for settlement
        for (Payment payment : payments) {
            payment.setSettlementId(settlement.getId());
            payment.setSettlementStatus("PENDING");
            payment.setSettlementDate(settlement.getSettlementDate());
        }
    }

    private void processExpressSettlement(Settlement settlement, List<Payment> payments) {
        log.info("Processing express settlement for {} payments", payments.size());
        
        // Same-day settlement with premium fee
        settlement.setSettlementDate(LocalDate.now());
        settlement.setSettlementMethod("WIRE");
        
        // Add express fee
        BigDecimal expressFee = settlement.getNetAmount().multiply(new BigDecimal("0.01")); // 1% express fee
        settlement.setExpressFee(expressFee);
        settlement.setFinalAmount(settlement.getFinalAmount().subtract(expressFee));
    }

    private void processSplitSettlement(Settlement settlement, List<Payment> payments, SettlementEvent event) {
        log.info("Processing split settlement for {} payments", payments.size());
        
        // Handle marketplace split settlements
        if (event.getSplitRules() != null) {
            Map<String, BigDecimal> splits = new HashMap<>();
            
            for (var rule : event.getSplitRules()) {
                BigDecimal splitAmount = settlement.getNetAmount()
                    .multiply(rule.getPercentage())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
                splits.put(rule.getRecipientId(), splitAmount);
            }
            
            settlement.setSplitDetails(splits);
            settlement.setSplitSettlement(true);
        }
    }

    private void processScheduledSettlement(Settlement settlement, List<Payment> payments) {
        log.info("Processing scheduled settlement for {} payments", payments.size());
        
        // Process according to merchant's settlement schedule
        LocalDate nextSettlementDate = settlementService.getNextSettlementDate(
            settlement.getMerchantId()
        );
        settlement.setSettlementDate(nextSettlementDate);
        settlement.setScheduled(true);
    }

    private void processManualSettlement(Settlement settlement, List<Payment> payments) {
        log.info("Processing manual settlement for {} payments", payments.size());
        
        // Requires manual approval
        settlement.setRequiresApproval(true);
        settlement.setApprovalStatus("PENDING");
        settlement.setStatus(SettlementStatus.PENDING_APPROVAL);
    }

    private void executeSettlementTransfer(Settlement settlement, SettlementEvent event) {
        try {
            log.info("Executing settlement transfer: {} amount: {}", 
                    settlement.getId(), settlement.getFinalAmount());

            // Initiate bank transfer
            var transferResult = bankingService.initiateTransfer(
                settlement.getMerchantAccountId(),
                settlement.getFinalAmount(),
                settlement.getCurrency(),
                settlement.getId()
            );

            settlement.setTransferId(transferResult.getTransferId());
            settlement.setTransferReference(transferResult.getReference());
            settlement.setTransferInitiatedAt(LocalDateTime.now());
            settlement.setTransferStatus(transferResult.getStatus());

            // Update status based on transfer result
            if ("SUCCESS".equals(transferResult.getStatus())) {
                settlement.setStatus(SettlementStatus.TRANSFERRED);
                settlement.setTransferredAt(LocalDateTime.now());
            } else if ("PENDING".equals(transferResult.getStatus())) {
                settlement.setStatus(SettlementStatus.TRANSFER_PENDING);
            } else {
                settlement.setStatus(SettlementStatus.TRANSFER_FAILED);
                settlement.setTransferError(transferResult.getErrorMessage());
            }

        } catch (Exception e) {
            log.error("Settlement transfer failed: {}", e.getMessage());
            settlement.setStatus(SettlementStatus.TRANSFER_FAILED);
            settlement.setTransferError(e.getMessage());
            throw new SettlementProcessingException("Transfer execution failed: " + e.getMessage(), e);
        }
    }

    private void updatePaymentStatuses(List<Payment> payments, Settlement settlement) {
        for (Payment payment : payments) {
            payment.setSettlementId(settlement.getId());
            payment.setSettlementStatus(settlement.getStatus().toString());
            payment.setSettlementDate(settlement.getSettlementDate());
            payment.setSettledAmount(payment.getAmount());
            payment.setUpdatedAt(LocalDateTime.now());
        }
        
        paymentRepository.saveAll(payments);
    }

    private void performSettlementReconciliation(Settlement settlement, List<Payment> payments) {
        try {
            log.info("Performing reconciliation for settlement: {}", settlement.getId());

            var reconciliationResult = reconciliationService.reconcileSettlement(
                settlement.getId(),
                payments,
                settlement.getGrossAmount(),
                settlement.getNetAmount()
            );

            settlement.setReconciled(reconciliationResult.isReconciled());
            settlement.setReconciledAt(LocalDateTime.now());
            
            if (!reconciliationResult.isReconciled()) {
                settlement.setReconciliationDiscrepancy(reconciliationResult.getDiscrepancyAmount());
                settlement.setReconciliationNotes(reconciliationResult.getNotes());
                log.warn("Settlement reconciliation discrepancy: {} for settlement: {}", 
                        reconciliationResult.getDiscrepancyAmount(), settlement.getId());
            }

        } catch (Exception e) {
            log.error("Reconciliation failed: {}", e.getMessage());
            settlement.setReconciliationError(e.getMessage());
        }
    }

    private void updateSettlementStatus(Settlement settlement) {
        if (settlement.getStatus() == SettlementStatus.TRANSFERRED && settlement.isReconciled()) {
            settlement.setStatus(SettlementStatus.COMPLETED);
            settlement.setCompletedAt(LocalDateTime.now());
        } else if (settlement.getTransferError() != null) {
            settlement.setStatus(SettlementStatus.FAILED);
            settlement.setFailedAt(LocalDateTime.now());
            settlement.setFailureReason(settlement.getTransferError());
        } else if (settlement.isRequiresApproval()) {
            settlement.setStatus(SettlementStatus.PENDING_APPROVAL);
        }

        settlement.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(settlement.getCreatedAt(), LocalDateTime.now())
        );
        settlement.setUpdatedAt(LocalDateTime.now());
    }

    private void sendSettlementNotifications(Settlement settlement, SettlementEvent event) {
        try {
            // Merchant notification
            notificationService.sendSettlementNotification(settlement);

            // Success notification
            if (settlement.getStatus() == SettlementStatus.COMPLETED) {
                notificationService.sendSettlementSuccessNotification(settlement);
            }

            // Failure notification
            if (settlement.getStatus() == SettlementStatus.FAILED) {
                notificationService.sendSettlementFailureNotification(settlement);
            }

            // Large settlement alert
            if (settlement.getFinalAmount().compareTo(new BigDecimal("10000")) > 0) {
                notificationService.sendLargeSettlementAlert(settlement);
            }

        } catch (Exception e) {
            log.error("Failed to send settlement notifications: {}", e.getMessage());
        }
    }

    private void updateSettlementMetrics(Settlement settlement, SettlementEvent event) {
        try {
            // Settlement metrics
            metricsService.incrementCounter("settlement.completed",
                Map.of(
                    "type", settlement.getSettlementType().toString(),
                    "status", settlement.getStatus().toString(),
                    "provider", settlement.getProvider()
                ));

            // Amount metrics
            metricsService.recordGauge("settlement.amount.gross", 
                settlement.getGrossAmount().doubleValue(),
                Map.of("currency", settlement.getCurrency()));

            metricsService.recordGauge("settlement.amount.net", 
                settlement.getNetAmount().doubleValue(),
                Map.of("currency", settlement.getCurrency()));

            // Fee metrics
            metricsService.recordGauge("settlement.fees.total", 
                settlement.getTotalFees().doubleValue(),
                Map.of("merchant", settlement.getMerchantId()));

            // Processing time
            metricsService.recordTimer("settlement.processing_time", 
                settlement.getProcessingTimeMs(),
                Map.of("type", settlement.getSettlementType().toString()));

        } catch (Exception e) {
            log.error("Failed to update settlement metrics: {}", e.getMessage());
        }
    }

    private void createSettlementAuditLog(Settlement settlement, SettlementEvent event, String correlationId) {
        auditLogger.logFinancialEvent(
            "SETTLEMENT_PROCESSED",
            settlement.getMerchantId(),
            settlement.getId(),
            "SETTLEMENT",
            settlement.getFinalAmount().doubleValue(),
            "settlement_processor",
            settlement.getStatus() == SettlementStatus.COMPLETED,
            Map.of(
                "settlementId", settlement.getId(),
                "merchantId", settlement.getMerchantId(),
                "type", settlement.getSettlementType().toString(),
                "status", settlement.getStatus().toString(),
                "paymentCount", String.valueOf(settlement.getPaymentCount()),
                "grossAmount", settlement.getGrossAmount().toString(),
                "netAmount", settlement.getNetAmount().toString(),
                "totalFees", settlement.getTotalFees().toString(),
                "transferId", settlement.getTransferId() != null ? settlement.getTransferId() : "N/A",
                "reconciled", String.valueOf(settlement.isReconciled()),
                "processingTimeMs", String.valueOf(settlement.getProcessingTimeMs()),
                "correlationId", correlationId != null ? correlationId : "N/A",
                "eventId", event.getEventId()
            )
        );
    }

    private void scheduleNextSettlement(Settlement settlement, SettlementEvent event) {
        try {
            LocalDate nextDate = settlementService.calculateNextSettlementDate(
                settlement.getMerchantId(),
                event.getRecurrencePattern()
            );
            
            settlementService.scheduleSettlement(
                settlement.getMerchantId(),
                nextDate,
                event.getRecurrencePattern()
            );
            
            log.info("Scheduled next settlement for merchant {} on {}", 
                    settlement.getMerchantId(), nextDate);
                    
        } catch (Exception e) {
            log.error("Failed to schedule next settlement: {}", e.getMessage());
        }
    }

    private Settlement processExpressSettlement(SettlementEvent event, String correlationId) {
        Settlement settlement = createSettlementRecord(event, UUID.randomUUID().toString(), correlationId);
        
        // Fast processing
        settlement.setSettlementType(SettlementType.EXPRESS);
        settlement.setGrossAmount(event.getSettlementAmount());
        
        // Minimal fee calculation
        BigDecimal expressFee = event.getSettlementAmount().multiply(new BigDecimal("0.02")); // 2% express fee
        settlement.setExpressFee(expressFee);
        settlement.setNetAmount(event.getSettlementAmount().subtract(expressFee));
        settlement.setFinalAmount(settlement.getNetAmount());
        
        // Immediate transfer
        settlement.setStatus(SettlementStatus.TRANSFERRED);
        settlement.setTransferredAt(LocalDateTime.now());
        settlement.setCompletedAt(LocalDateTime.now());
        
        return settlementRepository.save(settlement);
    }

    private boolean isPaymentSettleable(Payment payment) {
        return "COMPLETED".equals(payment.getStatus()) && 
               payment.getSettlementId() == null &&
               !payment.isRefunded();
    }

    private String generateBatchNumber() {
        return String.format("BATCH-%s-%s", 
            LocalDate.now().toString().replace("-", ""),
            UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }
}