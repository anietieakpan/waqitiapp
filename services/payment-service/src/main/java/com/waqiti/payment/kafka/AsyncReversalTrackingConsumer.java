package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.payment.model.*;
import com.waqiti.payment.repository.ReversalRepository;
import com.waqiti.payment.service.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Production-grade Kafka consumer for async reversal tracking events
 * Handles payment reversals, chargebacks, disputes, and refund processing
 * 
 * Critical for: Payment integrity, dispute management, regulatory compliance
 * SLA: Must process reversals within 2 minutes to prevent overdrafts
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AsyncReversalTrackingConsumer {

    private final ReversalRepository reversalRepository;
    private final PaymentService paymentService;
    private final AccountService accountService;
    private final DisputeService disputeService;
    private final LedgerService ledgerService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final RiskService riskService;
    private final ScheduledExecutorService scheduledExecutor;

    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("10000.00");
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long PROCESSING_TIMEOUT_MS = 120000; // 2 minutes
    private static final long REVERSAL_WINDOW_HOURS = 24;
    
    @KafkaListener(
        topics = "async-reversal-tracking",
        groupId = "async-reversal-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "async-reversal-processor", fallbackMethod = "handleReversalFailure")
    @Retry(name = "async-reversal-processor")
    public void processAsyncReversalEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing async reversal event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            ReversalRequest reversalRequest = extractReversalRequest(payload);
            
            // Validate reversal request
            validateReversalRequest(reversalRequest);
            
            // Check for duplicate reversal
            if (isDuplicateReversal(reversalRequest)) {
                log.warn("Duplicate reversal detected: {}, skipping", reversalRequest.getReversalId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Fetch original transaction
            PaymentTransaction originalTx = getOriginalTransaction(reversalRequest);
            
            // Validate reversal eligibility
            validateReversalEligibility(reversalRequest, originalTx);
            
            // Determine reversal strategy
            ReversalStrategy strategy = determineReversalStrategy(reversalRequest, originalTx);
            
            // Process the reversal
            ReversalResult result = processReversal(reversalRequest, originalTx, strategy);
            
            // Update transaction states
            updateTransactionStates(reversalRequest, originalTx, result);
            
            // Handle downstream effects
            handleDownstreamEffects(reversalRequest, originalTx, result);
            
            // Generate regulatory reports
            generateRegulatoryReports(reversalRequest, result);
            
            // Send notifications
            sendReversalNotifications(reversalRequest, originalTx, result);
            
            // Audit the reversal
            auditReversal(reversalRequest, result, event);
            
            // Record metrics
            recordMetrics(reversalRequest, result, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed reversal: {} for transaction: {} in {}ms", 
                    reversalRequest.getReversalId(), reversalRequest.getOriginalTransactionId(),
                    System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for reversal event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (InsufficientFundsException e) {
            log.error("Insufficient funds for reversal: {}", eventId, e);
            handleInsufficientFundsError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process reversal event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private ReversalRequest extractReversalRequest(Map<String, Object> payload) {
        return ReversalRequest.builder()
            .reversalId(extractString(payload, "reversalId", UUID.randomUUID().toString()))
            .originalTransactionId(extractString(payload, "originalTransactionId", null))
            .reversalType(ReversalType.fromString(extractString(payload, "reversalType", "REFUND")))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", "USD"))
            .reason(extractString(payload, "reason", null))
            .reasonCode(extractString(payload, "reasonCode", null))
            .initiatedBy(extractString(payload, "initiatedBy", null))
            .disputeId(extractString(payload, "disputeId", null))
            .chargebackId(extractString(payload, "chargebackId", null))
            .externalReference(extractString(payload, "externalReference", null))
            .partialReversal(extractBoolean(payload, "partialReversal", false))
            .forceReversal(extractBoolean(payload, "forceReversal", false))
            .notifyCustomer(extractBoolean(payload, "notifyCustomer", true))
            .priority(extractString(payload, "priority", "NORMAL"))
            .metadata(extractMap(payload, "metadata"))
            .createdAt(Instant.now())
            .build();
    }

    private void validateReversalRequest(ReversalRequest request) {
        // Validate required fields
        if (request.getOriginalTransactionId() == null || request.getOriginalTransactionId().isEmpty()) {
            throw new ValidationException("Original transaction ID is required for reversal");
        }
        
        if (request.getReversalType() == null) {
            throw new ValidationException("Reversal type is required");
        }
        
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Invalid reversal amount: " + request.getAmount());
        }
        
        // Validate reversal type specific requirements
        switch (request.getReversalType()) {
            case CHARGEBACK:
                if (request.getChargebackId() == null) {
                    throw new ValidationException("Chargeback ID required for chargeback reversal");
                }
                if (request.getReasonCode() == null) {
                    throw new ValidationException("Reason code required for chargeback");
                }
                break;
                
            case DISPUTE:
                if (request.getDisputeId() == null) {
                    throw new ValidationException("Dispute ID required for dispute reversal");
                }
                break;
                
            case REFUND:
            case CANCELLATION:
            case FRAUD_REVERSAL:
                // Basic validation sufficient
                break;
                
            default:
                throw new ValidationException("Unsupported reversal type: " + request.getReversalType());
        }
        
        // Validate reason for manual reversals
        if (request.getInitiatedBy() != null && !request.getInitiatedBy().equals("SYSTEM")) {
            if (request.getReason() == null || request.getReason().trim().isEmpty()) {
                throw new ValidationException("Reason required for manual reversals");
            }
        }
    }

    private boolean isDuplicateReversal(ReversalRequest request) {
        // Check by reversal ID
        if (reversalRepository.existsByReversalId(request.getReversalId())) {
            return true;
        }
        
        // Check by external reference
        if (request.getExternalReference() != null) {
            return reversalRepository.existsByExternalReference(request.getExternalReference());
        }
        
        // Check for recent similar reversal
        return reversalRepository.existsSimilarReversal(
            request.getOriginalTransactionId(),
            request.getAmount(),
            request.getReversalType(),
            Instant.now().minus(5, ChronoUnit.MINUTES)
        );
    }

    private PaymentTransaction getOriginalTransaction(ReversalRequest request) {
        return paymentService.getTransaction(request.getOriginalTransactionId())
            .orElseThrow(() -> new ValidationException(
                "Original transaction not found: " + request.getOriginalTransactionId()));
    }

    private void validateReversalEligibility(ReversalRequest request, PaymentTransaction originalTx) {
        // Check transaction status
        if (!isReversibleStatus(originalTx.getStatus())) {
            throw new ValidationException(
                "Transaction cannot be reversed in current status: " + originalTx.getStatus());
        }
        
        // Check reversal window
        if (!request.isForceReversal()) {
            long hoursElapsed = ChronoUnit.HOURS.between(originalTx.getCreatedAt(), Instant.now());
            if (hoursElapsed > REVERSAL_WINDOW_HOURS && request.getReversalType() != ReversalType.CHARGEBACK) {
                throw new ValidationException(
                    "Reversal window expired. Transaction is " + hoursElapsed + " hours old");
            }
        }
        
        // Check partial reversal amount
        if (request.isPartialReversal()) {
            BigDecimal totalReversed = reversalRepository.getTotalReversedAmount(
                request.getOriginalTransactionId());
            BigDecimal remainingAmount = originalTx.getAmount().subtract(totalReversed);
            
            if (request.getAmount().compareTo(remainingAmount) > 0) {
                throw new ValidationException(String.format(
                    "Partial reversal amount exceeds remaining amount. Requested: %s, Remaining: %s",
                    request.getAmount(), remainingAmount));
            }
        } else {
            // Full reversal
            if (request.getAmount().compareTo(originalTx.getAmount()) != 0) {
                throw new ValidationException(
                    "Full reversal amount must match original transaction amount");
            }
        }
        
        // Check account status
        if (accountService.isAccountFrozen(originalTx.getDebitAccountId())) {
            throw new ValidationException("Source account is frozen, cannot process reversal");
        }
        
        // Currency validation
        if (!request.getCurrency().equals(originalTx.getCurrency())) {
            throw new ValidationException("Reversal currency must match original transaction currency");
        }
    }

    private boolean isReversibleStatus(TransactionStatus status) {
        return status == TransactionStatus.COMPLETED ||
               status == TransactionStatus.SETTLED ||
               status == TransactionStatus.PARTIALLY_REVERSED;
    }

    private ReversalStrategy determineReversalStrategy(ReversalRequest request, PaymentTransaction originalTx) {
        // High priority or urgent reversals
        if ("URGENT".equals(request.getPriority()) || request.getReversalType() == ReversalType.FRAUD_REVERSAL) {
            return ReversalStrategy.IMMEDIATE;
        }
        
        // Chargebacks require special handling
        if (request.getReversalType() == ReversalType.CHARGEBACK) {
            return ReversalStrategy.CHARGEBACK_PROCESS;
        }
        
        // High value transactions
        if (request.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            return ReversalStrategy.HIGH_VALUE_REVIEW;
        }
        
        // Check if funds are available for immediate reversal
        BigDecimal availableBalance = accountService.getAvailableBalance(originalTx.getCreditAccountId());
        if (availableBalance.compareTo(request.getAmount()) >= 0) {
            return ReversalStrategy.STANDARD;
        } else {
            // Insufficient funds - may need holds or partial processing
            return ReversalStrategy.INSUFFICIENT_FUNDS_HANDLING;
        }
    }

    private ReversalResult processReversal(ReversalRequest request, PaymentTransaction originalTx, 
                                          ReversalStrategy strategy) {
        log.info("Processing reversal {} with strategy: {}", request.getReversalId(), strategy);
        
        ReversalResult result = new ReversalResult();
        result.setReversalId(request.getReversalId());
        result.setStrategy(strategy);
        result.setStartTime(Instant.now());
        
        try {
            switch (strategy) {
                case IMMEDIATE:
                    result = processImmediateReversal(request, originalTx);
                    break;
                    
                case STANDARD:
                    result = processStandardReversal(request, originalTx);
                    break;
                    
                case HIGH_VALUE_REVIEW:
                    result = processHighValueReversal(request, originalTx);
                    break;
                    
                case CHARGEBACK_PROCESS:
                    result = processChargebackReversal(request, originalTx);
                    break;
                    
                case INSUFFICIENT_FUNDS_HANDLING:
                    result = processInsufficientFundsReversal(request, originalTx);
                    break;
                    
                default:
                    throw new IllegalStateException("Unknown reversal strategy: " + strategy);
            }
            
            result.setEndTime(Instant.now());
            result.setProcessingTimeMs(
                ChronoUnit.MILLIS.between(result.getStartTime(), result.getEndTime())
            );
            
        } catch (Exception e) {
            log.error("Failed to process reversal with strategy {}: {}", strategy, e.getMessage());
            result.setStatus(ReversalStatus.FAILED);
            result.setErrorMessage(e.getMessage());
            throw new ReversalProcessingException("Reversal processing failed", e);
        }
        
        return result;
    }

    private ReversalResult processImmediateReversal(ReversalRequest request, PaymentTransaction originalTx) {
        // Execute immediate reversal
        String reversalTransactionId = ledgerService.executeReversal(
            originalTx.getCreditAccountId(),
            originalTx.getDebitAccountId(),
            request.getAmount(),
            request.getCurrency(),
            "IMMEDIATE_REVERSAL_" + request.getReversalId()
        );
        
        // Create reversal record
        Reversal reversal = createReversalRecord(request, originalTx, reversalTransactionId);
        reversal.setStatus(ReversalStatus.COMPLETED);
        reversal.setProcessedAt(Instant.now());
        reversalRepository.save(reversal);
        
        return ReversalResult.builder()
            .reversalId(request.getReversalId())
            .status(ReversalStatus.COMPLETED)
            .reversalTransactionId(reversalTransactionId)
            .processedAmount(request.getAmount())
            .build();
    }

    private ReversalResult processStandardReversal(ReversalRequest request, PaymentTransaction originalTx) {
        // Standard reversal with validation
        validateAccountBalances(originalTx, request.getAmount());
        
        String reversalTransactionId = ledgerService.executeReversal(
            originalTx.getCreditAccountId(),
            originalTx.getDebitAccountId(),
            request.getAmount(),
            request.getCurrency(),
            "STANDARD_REVERSAL_" + request.getReversalId()
        );
        
        // Create reversal record
        Reversal reversal = createReversalRecord(request, originalTx, reversalTransactionId);
        reversal.setStatus(ReversalStatus.COMPLETED);
        reversal.setProcessedAt(Instant.now());
        reversalRepository.save(reversal);
        
        return ReversalResult.builder()
            .reversalId(request.getReversalId())
            .status(ReversalStatus.COMPLETED)
            .reversalTransactionId(reversalTransactionId)
            .processedAmount(request.getAmount())
            .build();
    }

    private ReversalResult processHighValueReversal(ReversalRequest request, PaymentTransaction originalTx) {
        // High value reversals require additional approval
        String caseId = createReversalCase(request, originalTx);
        
        // Create pending reversal record
        Reversal reversal = createReversalRecord(request, originalTx, null);
        reversal.setStatus(ReversalStatus.PENDING_APPROVAL);
        reversal.setCaseId(caseId);
        reversalRepository.save(reversal);
        
        // Notify approval team
        notificationService.notifyApprovalTeam(
            "HIGH_VALUE_REVERSAL_APPROVAL",
            Map.of(
                "reversalId", request.getReversalId(),
                "amount", request.getAmount(),
                "caseId", caseId
            )
        );
        
        return ReversalResult.builder()
            .reversalId(request.getReversalId())
            .status(ReversalStatus.PENDING_APPROVAL)
            .caseId(caseId)
            .build();
    }

    private ReversalResult processChargebackReversal(ReversalRequest request, PaymentTransaction originalTx) {
        // Process chargeback with liability assessment
        ChargebackLiability liability = assessChargebackLiability(request, originalTx);
        
        String reversalTransactionId;
        if (liability.isMerchantLiable()) {
            // Merchant liable - reverse from merchant account
            reversalTransactionId = ledgerService.executeChargeback(
                originalTx.getMerchantAccountId(),
                originalTx.getCustomerAccountId(),
                request.getAmount(),
                request.getCurrency(),
                "CHARGEBACK_" + request.getChargebackId()
            );
        } else {
            // Platform liable - use platform reserve
            reversalTransactionId = ledgerService.executeChargebackFromReserve(
                request.getAmount(),
                request.getCurrency(),
                "CHARGEBACK_RESERVE_" + request.getChargebackId()
            );
        }
        
        // Create reversal record with chargeback details
        Reversal reversal = createReversalRecord(request, originalTx, reversalTransactionId);
        reversal.setStatus(ReversalStatus.COMPLETED);
        reversal.setLiabilityAssignment(liability.getAssignment());
        reversal.setProcessedAt(Instant.now());
        reversalRepository.save(reversal);
        
        // Update dispute/chargeback status
        if (request.getDisputeId() != null) {
            disputeService.updateDisputeStatus(request.getDisputeId(), DisputeStatus.CHARGEBACK_PROCESSED);
        }
        
        return ReversalResult.builder()
            .reversalId(request.getReversalId())
            .status(ReversalStatus.COMPLETED)
            .reversalTransactionId(reversalTransactionId)
            .processedAmount(request.getAmount())
            .liabilityAssignment(liability.getAssignment())
            .build();
    }

    private ReversalResult processInsufficientFundsReversal(ReversalRequest request, PaymentTransaction originalTx) {
        BigDecimal availableBalance = accountService.getAvailableBalance(originalTx.getCreditAccountId());
        
        if (availableBalance.compareTo(BigDecimal.ZERO) <= 0) {
            // No funds available - create pending reversal
            Reversal reversal = createReversalRecord(request, originalTx, null);
            reversal.setStatus(ReversalStatus.PENDING_FUNDS);
            reversalRepository.save(reversal);
            
            // Schedule retry when funds become available
            scheduleReversalRetry(request);
            
            return ReversalResult.builder()
                .reversalId(request.getReversalId())
                .status(ReversalStatus.PENDING_FUNDS)
                .build();
                
        } else if (availableBalance.compareTo(request.getAmount()) < 0) {
            // Partial funds available
            if (request.isPartialReversal() || request.getReversalType() == ReversalType.CHARGEBACK) {
                // Process partial reversal
                String partialTransactionId = ledgerService.executePartialReversal(
                    originalTx.getCreditAccountId(),
                    originalTx.getDebitAccountId(),
                    availableBalance,
                    request.getCurrency(),
                    "PARTIAL_REVERSAL_" + request.getReversalId()
                );
                
                // Create partial reversal record
                Reversal reversal = createReversalRecord(request, originalTx, partialTransactionId);
                reversal.setStatus(ReversalStatus.PARTIALLY_PROCESSED);
                reversal.setProcessedAmount(availableBalance);
                reversal.setPendingAmount(request.getAmount().subtract(availableBalance));
                reversalRepository.save(reversal);
                
                // Schedule completion
                scheduleReversalCompletion(request, availableBalance);
                
                return ReversalResult.builder()
                    .reversalId(request.getReversalId())
                    .status(ReversalStatus.PARTIALLY_PROCESSED)
                    .reversalTransactionId(partialTransactionId)
                    .processedAmount(availableBalance)
                    .pendingAmount(request.getAmount().subtract(availableBalance))
                    .build();
            } else {
                // Full reversal required but insufficient funds
                throw new InsufficientFundsException(
                    "Insufficient funds for full reversal. Available: " + availableBalance + 
                    ", Required: " + request.getAmount());
            }
        }
        
        // This should not be reached
        throw new IllegalStateException("Unexpected state in insufficient funds processing");
    }

    private Reversal createReversalRecord(ReversalRequest request, PaymentTransaction originalTx, 
                                         String reversalTransactionId) {
        return Reversal.builder()
            .reversalId(request.getReversalId())
            .originalTransactionId(request.getOriginalTransactionId())
            .reversalTransactionId(reversalTransactionId)
            .reversalType(request.getReversalType())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .reason(request.getReason())
            .reasonCode(request.getReasonCode())
            .initiatedBy(request.getInitiatedBy())
            .disputeId(request.getDisputeId())
            .chargebackId(request.getChargebackId())
            .externalReference(request.getExternalReference())
            .partialReversal(request.isPartialReversal())
            .priority(request.getPriority())
            .createdAt(request.getCreatedAt())
            .metadata(request.getMetadata())
            .build();
    }

    private void validateAccountBalances(PaymentTransaction originalTx, BigDecimal amount) {
        BigDecimal balance = accountService.getAvailableBalance(originalTx.getCreditAccountId());
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException(
                "Insufficient balance for reversal. Available: " + balance + ", Required: " + amount);
        }
    }

    private ChargebackLiability assessChargebackLiability(ReversalRequest request, PaymentTransaction originalTx) {
        // Assess based on chargeback reason code
        String reasonCode = request.getReasonCode();
        
        if (reasonCode.startsWith("4834") || reasonCode.startsWith("4837")) {
            // Transaction processing errors - typically merchant liable
            return ChargebackLiability.merchantLiable("Processing error");
        } else if (reasonCode.startsWith("4863") || reasonCode.startsWith("4870")) {
            // Cardholder disputes - depends on evidence
            return assessDisputeEvidence(request, originalTx);
        } else if (reasonCode.startsWith("4808") || reasonCode.startsWith("4812")) {
            // Authorization issues - typically merchant liable
            return ChargebackLiability.merchantLiable("Authorization issue");
        } else {
            // Default assessment based on transaction details
            return performDefaultLiabilityAssessment(originalTx);
        }
    }

    private ChargebackLiability assessDisputeEvidence(ReversalRequest request, PaymentTransaction originalTx) {
        // Check if merchant has provided compelling evidence
        if (disputeService.hasCompellingEvidence(request.getDisputeId())) {
            return ChargebackLiability.platformLiable("Compelling evidence provided");
        } else {
            return ChargebackLiability.merchantLiable("No compelling evidence");
        }
    }

    private ChargebackLiability performDefaultLiabilityAssessment(PaymentTransaction originalTx) {
        // Default to merchant liability with ability to dispute
        return ChargebackLiability.merchantLiable("Default assessment - can be disputed");
    }

    private String createReversalCase(ReversalRequest request, PaymentTransaction originalTx) {
        return UUID.randomUUID().toString();
    }

    private void scheduleReversalRetry(ReversalRequest request) {
        // Retry every hour for 24 hours
        for (int i = 1; i <= 24; i++) {
            scheduledExecutor.schedule(() -> {
                retryPendingReversal(request.getReversalId());
            }, i, TimeUnit.HOURS);
        }
    }

    private void scheduleReversalCompletion(ReversalRequest request, BigDecimal processedAmount) {
        BigDecimal remainingAmount = request.getAmount().subtract(processedAmount);
        
        // Check every 4 hours for remaining funds
        scheduledExecutor.scheduleAtFixedRate(() -> {
            completePartialReversal(request.getReversalId(), remainingAmount);
        }, 4, 4, TimeUnit.HOURS);
    }

    private void retryPendingReversal(String reversalId) {
        try {
            Optional<Reversal> reversalOpt = reversalRepository.findById(reversalId);
            if (reversalOpt.isPresent() && reversalOpt.get().getStatus() == ReversalStatus.PENDING_FUNDS) {
                // Re-publish the reversal event
                republishReversalEvent(reversalOpt.get());
            }
        } catch (Exception e) {
            log.error("Failed to retry pending reversal: {}", reversalId, e);
        }
    }

    private void completePartialReversal(String reversalId, BigDecimal remainingAmount) {
        try {
            Optional<Reversal> reversalOpt = reversalRepository.findById(reversalId);
            if (reversalOpt.isPresent() && reversalOpt.get().getStatus() == ReversalStatus.PARTIALLY_PROCESSED) {
                // Check if sufficient funds are now available
                Reversal reversal = reversalOpt.get();
                PaymentTransaction originalTx = getOriginalTransaction(
                    ReversalRequest.builder()
                        .originalTransactionId(reversal.getOriginalTransactionId())
                        .build()
                );
                
                BigDecimal availableBalance = accountService.getAvailableBalance(originalTx.getCreditAccountId());
                if (availableBalance.compareTo(remainingAmount) >= 0) {
                    // Complete the reversal
                    String completionTransactionId = ledgerService.executeReversal(
                        originalTx.getCreditAccountId(),
                        originalTx.getDebitAccountId(),
                        remainingAmount,
                        reversal.getCurrency(),
                        "REVERSAL_COMPLETION_" + reversalId
                    );
                    
                    reversal.setStatus(ReversalStatus.COMPLETED);
                    reversal.setCompletionTransactionId(completionTransactionId);
                    reversal.setProcessedAt(Instant.now());
                    reversal.setPendingAmount(BigDecimal.ZERO);
                    reversalRepository.save(reversal);
                }
            }
        } catch (Exception e) {
            log.error("Failed to complete partial reversal: {}", reversalId, e);
        }
    }

    private void republishReversalEvent(Reversal reversal) {
        GenericKafkaEvent retryEvent = GenericKafkaEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventType("REVERSAL_RETRY")
            .timestamp(Instant.now())
            .payload(reversal.toMap())
            .build();
        
        kafkaTemplate.send("async-reversal-tracking", retryEvent);
    }

    private void updateTransactionStates(ReversalRequest request, PaymentTransaction originalTx, 
                                        ReversalResult result) {
        // Update original transaction status
        if (result.getStatus() == ReversalStatus.COMPLETED) {
            if (request.isPartialReversal()) {
                paymentService.updateTransactionStatus(
                    originalTx.getId(),
                    TransactionStatus.PARTIALLY_REVERSED,
                    "Partial reversal completed"
                );
            } else {
                paymentService.updateTransactionStatus(
                    originalTx.getId(),
                    TransactionStatus.REVERSED,
                    "Full reversal completed"
                );
            }
        }
        
        // Update account balances
        if (result.getProcessedAmount() != null) {
            accountService.updateBalanceAfterReversal(
                originalTx.getCreditAccountId(),
                originalTx.getDebitAccountId(),
                result.getProcessedAmount()
            );
        }
    }

    private void handleDownstreamEffects(ReversalRequest request, PaymentTransaction originalTx, 
                                        ReversalResult result) {
        // Update merchant risk scores for chargebacks
        if (request.getReversalType() == ReversalType.CHARGEBACK) {
            riskService.updateMerchantRiskAfterChargeback(
                originalTx.getMerchantId(),
                request.getAmount()
            );
        }
        
        // Handle loyalty points reversal
        if (originalTx.getMetadata().containsKey("loyaltyPointsAwarded")) {
            reverseLoyaltyPoints(originalTx, request.getAmount());
        }
        
        // Handle fee reversals
        if (originalTx.getFeeAmount() != null && originalTx.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            reverseTransactionFees(originalTx, request);
        }
    }

    private void reverseLoyaltyPoints(PaymentTransaction originalTx, BigDecimal reversalAmount) {
        // Calculate proportional loyalty points to reverse
        BigDecimal pointsToReverse = originalTx.getLoyaltyPoints()
            .multiply(reversalAmount)
            .divide(originalTx.getAmount(), 0, RoundingMode.DOWN);
        
        loyaltyService.reversePoints(originalTx.getCustomerId(), pointsToReverse.intValue());
    }

    private void reverseTransactionFees(PaymentTransaction originalTx, ReversalRequest request) {
        BigDecimal feeReversalAmount = originalTx.getFeeAmount()
            .multiply(request.getAmount())
            .divide(originalTx.getAmount(), 2, RoundingMode.HALF_UP);
        
        ledgerService.reverseFees(
            originalTx.getId(),
            feeReversalAmount,
            "FEE_REVERSAL_" + request.getReversalId()
        );
    }

    private void generateRegulatoryReports(ReversalRequest request, ReversalResult result) {
        // Generate reports for high-value reversals
        if (request.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            auditService.generateHighValueReversalReport(
                request.getReversalId(),
                request.getAmount(),
                request.getReversalType().toString()
            );
        }
        
        // Generate chargeback reports
        if (request.getReversalType() == ReversalType.CHARGEBACK) {
            disputeService.generateChargebackReport(
                request.getChargebackId(),
                request.getAmount(),
                result.getLiabilityAssignment()
            );
        }
    }

    private void sendReversalNotifications(ReversalRequest request, PaymentTransaction originalTx, 
                                          ReversalResult result) {
        Map<String, Object> notificationData = Map.of(
            "reversalId", request.getReversalId(),
            "originalTransactionId", request.getOriginalTransactionId(),
            "amount", request.getAmount(),
            "currency", request.getCurrency(),
            "reversalType", request.getReversalType().toString(),
            "status", result.getStatus().toString()
        );
        
        // Notify customer if requested
        if (request.isNotifyCustomer()) {
            notificationService.notifyCustomer(
                originalTx.getCustomerId(),
                "PAYMENT_REVERSAL_" + result.getStatus(),
                notificationData
            );
        }
        
        // Notify merchant
        notificationService.notifyMerchant(
            originalTx.getMerchantId(),
            "PAYMENT_REVERSAL_" + result.getStatus(),
            notificationData
        );
        
        // Send webhook if configured
        if (merchantService.hasWebhookEnabled(originalTx.getMerchantId())) {
            merchantService.sendWebhook(
                originalTx.getMerchantId(),
                "payment.reversal." + result.getStatus().toString().toLowerCase(),
                notificationData
            );
        }
    }

    private void auditReversal(ReversalRequest request, ReversalResult result, GenericKafkaEvent event) {
        auditService.auditReversal(
            request.getReversalId(),
            request.getOriginalTransactionId(),
            request.getAmount(),
            request.getReversalType().toString(),
            result.getStatus().toString(),
            event.getEventId()
        );
    }

    private void recordMetrics(ReversalRequest request, ReversalResult result, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordReversalMetrics(
            request.getReversalType().toString(),
            request.getAmount(),
            processingTime,
            result.getStatus().toString()
        );
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("async-reversal-validation-errors", event);
    }

    private void handleInsufficientFundsError(GenericKafkaEvent event, InsufficientFundsException e) {
        // Create pending reversal for later processing
        event.setMetadataValue("pendingReason", e.getMessage());
        kafkaTemplate.send("async-reversal-pending", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying reversal event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("async-reversal-tracking-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for reversal event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "async-reversal-tracking");
        
        kafkaTemplate.send("async-reversal-tracking.DLQ", event);
        
        alertingService.createDLQAlert(
            "async-reversal-tracking",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleReversalFailure(GenericKafkaEvent event, String topic, int partition,
                                      long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for reversal processing: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "Async Reversal Circuit Breaker Open",
            "Reversal processing is failing. Manual intervention required."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private boolean extractBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class InsufficientFundsException extends RuntimeException {
        public InsufficientFundsException(String message) {
            super(message);
        }
    }

    public static class ReversalProcessingException extends RuntimeException {
        public ReversalProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}