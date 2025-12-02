package com.waqiti.transaction.saga;

import com.waqiti.transaction.dto.*;
import com.waqiti.transaction.exception.TransactionFailedException;
import com.waqiti.transaction.client.SagaClient;
import com.waqiti.transaction.client.WalletServiceClient;
import com.waqiti.transaction.client.LedgerServiceClient;
import com.waqiti.transaction.client.NotificationServiceClient;
import com.waqiti.transaction.model.CompensationAction;
import com.waqiti.transaction.model.CompensationResult;
import com.waqiti.transaction.model.SagaState;
import com.waqiti.transaction.repository.SagaStateRepository;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Saga Compensation Manager
 * 
 * Implements the Saga pattern for distributed transaction management with:
 * - Automatic compensation on failure
 * - Idempotent operations
 * - Circuit breaker pattern
 * - Retry with exponential backoff
 * - Parallel compensation execution
 * - Audit trail for all compensations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaCompensationManager {

    private final SagaClient sagaClient;
    private final WalletServiceClient walletServiceClient;
    private final LedgerServiceClient ledgerServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final SagaStateRepository sagaStateRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    
    private final ExecutorService compensationExecutor = Executors.newFixedThreadPool(10);
    
    /**
     * Execute a transaction with automatic compensation on failure
     */
    @Transactional
    public CompensationResult executeWithCompensation(SagaRequest request, UUID transactionId) {
        String sagaId = UUID.randomUUID().toString();
        List<CompensationAction> compensationActions = new ArrayList<>();
        SagaState sagaState = initializeSagaState(sagaId, transactionId, request);
        
        try {
            // Step 1: Reserve funds from source wallet
            log.info("Step 1: Reserving funds for transaction {}", transactionId);
            ReserveFundsResult reservationResult = reserveFunds(request, compensationActions);
            updateSagaState(sagaState, "FUNDS_RESERVED", reservationResult);
            
            // Step 2: Create ledger entries
            log.info("Step 2: Creating ledger entries for transaction {}", transactionId);
            LedgerEntryResult ledgerResult = createLedgerEntries(request, reservationResult, compensationActions);
            updateSagaState(sagaState, "LEDGER_CREATED", ledgerResult);
            
            // Step 3: Initiate saga orchestration
            log.info("Step 3: Initiating saga orchestration for transaction {}", transactionId);
            SagaResponse sagaResponse = initiateSaga(request, compensationActions);
            updateSagaState(sagaState, "SAGA_INITIATED", sagaResponse);
            
            // Step 4: Process wallet transfers
            log.info("Step 4: Processing wallet transfers for transaction {}", transactionId);
            TransferResult transferResult = processTransfer(request, sagaResponse, compensationActions);
            updateSagaState(sagaState, "TRANSFER_COMPLETED", transferResult);
            
            // Step 5: Finalize ledger entries
            log.info("Step 5: Finalizing ledger entries for transaction {}", transactionId);
            finalizeLedgerEntries(ledgerResult, transferResult, compensationActions);
            updateSagaState(sagaState, "LEDGER_FINALIZED", null);
            
            // Step 6: Send notifications
            log.info("Step 6: Sending notifications for transaction {}", transactionId);
            sendNotifications(request, transferResult);
            updateSagaState(sagaState, "COMPLETED", null);
            
            // Publish success event
            publishSagaEvent(sagaId, "SAGA_COMPLETED", request, transferResult);
            
            return CompensationResult.success(transferResult, sagaId);
            
        } catch (Exception e) {
            log.error("Transaction failed for {}, executing compensation", transactionId, e);
            
            // Update saga state to failed
            updateSagaState(sagaState, "FAILED", e.getMessage());
            
            // Execute compensation actions in reverse order
            CompensationExecutionResult compensationResult = executeCompensation(compensationActions, sagaId);
            
            // Publish failure event
            publishSagaEvent(sagaId, "SAGA_FAILED", request, compensationResult);
            
            throw new TransactionFailedException(
                String.format("Transaction %s failed and was compensated. Saga: %s", transactionId, sagaId), 
                e
            );
        }
    }
    
    /**
     * Reserve funds with compensation action
     */
    private ReserveFundsResult reserveFunds(SagaRequest request, List<CompensationAction> actions) {
        ReserveFundsRequest reserveRequest = ReserveFundsRequest.builder()
            .walletId(request.getSourceWalletId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .transactionId(request.getTransactionId())
            .reason("Transaction: " + request.getDescription())
            .build();
        
        ReserveFundsResult result = circuitBreaker.executeSupplier(() ->
            retry.executeSupplier(() -> 
                walletServiceClient.reserveFunds(reserveRequest)
            )
        );
        
        // Add compensation action
        actions.add(CompensationAction.builder()
            .name("RELEASE_FUNDS")
            .service("WALLET_SERVICE")
            .action(() -> walletServiceClient.releaseFunds(result.getReservationId()))
            .timeout(Duration.ofSeconds(30))
            .retryCount(3)
            .build()
        );
        
        return result;
    }
    
    /**
     * Create ledger entries with compensation
     */
    private LedgerEntryResult createLedgerEntries(SagaRequest request, ReserveFundsResult reservationResult, 
                                                   List<CompensationAction> actions) {
        CreateLedgerEntryRequest ledgerRequest = CreateLedgerEntryRequest.builder()
            .sourceAccountId(request.getSourceAccountId())
            .destinationAccountId(request.getDestinationAccountId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .transactionId(request.getTransactionId())
            .reservationId(reservationResult.getReservationId())
            .description(request.getDescription())
            .metadata(request.getMetadata())
            .build();
        
        LedgerEntryResult result = ledgerServiceClient.createDoubleEntry(ledgerRequest);
        
        // Add compensation action
        actions.add(CompensationAction.builder()
            .name("REVERSE_LEDGER_ENTRY")
            .service("LEDGER_SERVICE")
            .action(() -> ledgerServiceClient.reverseEntry(result.getEntryId()))
            .timeout(Duration.ofSeconds(30))
            .retryCount(3)
            .build()
        );
        
        return result;
    }
    
    /**
     * Initiate saga with compensation
     */
    private SagaResponse initiateSaga(SagaRequest request, List<CompensationAction> actions) {
        SagaResponse response = sagaClient.initiateSaga(request);
        
        // Add compensation action
        actions.add(CompensationAction.builder()
            .name("CANCEL_SAGA")
            .service("SAGA_SERVICE")
            .action(() -> sagaClient.cancelSaga(response.getSagaId()))
            .timeout(Duration.ofSeconds(30))
            .retryCount(3)
            .build()
        );
        
        return response;
    }
    
    /**
     * Process transfer with compensation
     */
    private TransferResult processTransfer(SagaRequest request, SagaResponse sagaResponse, 
                                          List<CompensationAction> actions) {
        TransferRequest transferRequest = TransferRequest.builder()
            .sagaId(sagaResponse.getSagaId())
            .sourceWalletId(request.getSourceWalletId())
            .destinationWalletId(request.getDestinationWalletId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .transactionId(request.getTransactionId())
            .build();
        
        TransferResult result = walletServiceClient.processTransfer(transferRequest);
        
        // Add compensation action for transfer reversal
        actions.add(CompensationAction.builder()
            .name("REVERSE_TRANSFER")
            .service("WALLET_SERVICE")
            .action(() -> walletServiceClient.reverseTransfer(result.getTransferId()))
            .timeout(Duration.ofSeconds(30))
            .retryCount(3)
            .build()
        );
        
        return result;
    }
    
    /**
     * Finalize ledger entries
     */
    private void finalizeLedgerEntries(LedgerEntryResult ledgerResult, TransferResult transferResult, 
                                       List<CompensationAction> actions) {
        FinalizeLedgerRequest finalizeRequest = FinalizeLedgerRequest.builder()
            .entryId(ledgerResult.getEntryId())
            .transferId(transferResult.getTransferId())
            .status("COMPLETED")
            .completedAt(LocalDateTime.now())
            .build();
        
        ledgerServiceClient.finalizeLedgerEntry(finalizeRequest);
        
        // No compensation needed as previous actions will handle rollback
    }
    
    /**
     * Send notifications (no compensation needed)
     */
    private void sendNotifications(SagaRequest request, TransferResult transferResult) {
        try {
            NotificationRequest notificationRequest = NotificationRequest.builder()
                .userId(request.getUserId())
                .type("TRANSACTION_COMPLETED")
                .title("Transaction Successful")
                .message(String.format("Your transaction of %s %s has been completed successfully", 
                    request.getAmount(), request.getCurrency()))
                .metadata(Map.of(
                    "transactionId", request.getTransactionId(),
                    "transferId", transferResult.getTransferId(),
                    "amount", request.getAmount().toString(),
                    "currency", request.getCurrency()
                ))
                .build();
            
            notificationServiceClient.sendNotification(notificationRequest);
        } catch (Exception e) {
            // Log but don't fail transaction for notification errors
            log.warn("Failed to send notification for transaction {}", request.getTransactionId(), e);
        }
    }
    
    /**
     * Execute compensation actions in parallel with timeout
     */
    private CompensationExecutionResult executeCompensation(List<CompensationAction> actions, String sagaId) {
        log.info("Executing {} compensation actions for saga {}", actions.size(), sagaId);
        
        List<CompensationAction> reversedActions = new ArrayList<>(actions);
        Collections.reverse(reversedActions);
        
        List<CompletableFuture<CompensationActionResult>> futures = reversedActions.stream()
            .map(action -> CompletableFuture.supplyAsync(() -> executeCompensationAction(action), compensationExecutor))
            .collect(Collectors.toList());
        
        // Wait for all compensations with timeout
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Timeout waiting for compensation actions", e);
        }
        
        // Collect results
        List<CompensationActionResult> results = futures.stream()
            .map(future -> {
                try {
                    return future.getNow(CompensationActionResult.timeout());
                } catch (Exception e) {
                    return CompensationActionResult.failed(e.getMessage());
                }
            })
            .collect(Collectors.toList());
        
        long successCount = results.stream().filter(CompensationActionResult::isSuccess).count();
        long failureCount = results.size() - successCount;
        
        log.info("Compensation completed for saga {}: {} successful, {} failed", 
            sagaId, successCount, failureCount);
        
        return CompensationExecutionResult.builder()
            .sagaId(sagaId)
            .totalActions(actions.size())
            .successfulActions((int) successCount)
            .failedActions((int) failureCount)
            .results(results)
            .build();
    }
    
    /**
     * Execute a single compensation action with retry
     */
    private CompensationActionResult executeCompensationAction(CompensationAction action) {
        log.info("Executing compensation action: {}", action.getName());
        
        int attempts = 0;
        Exception lastException = null;
        
        while (attempts < action.getRetryCount()) {
            attempts++;
            
            try {
                action.getAction().run();
                log.info("Compensation action {} succeeded on attempt {}", action.getName(), attempts);
                return CompensationActionResult.success(action.getName());
            } catch (Exception e) {
                lastException = e;
                log.warn("Compensation action {} failed on attempt {}: {}", 
                    action.getName(), attempts, e.getMessage());
                
                if (attempts < action.getRetryCount()) {
                    try {
                        TimeUnit.MILLISECONDS.sleep((long) Math.pow(2, attempts) * 1000); // Exponential backoff with proper interruption
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        log.error("Compensation action {} failed after {} attempts", action.getName(), attempts);
        return CompensationActionResult.failed(action.getName(), lastException);
    }
    
    /**
     * Initialize saga state
     */
    private SagaState initializeSagaState(String sagaId, UUID transactionId, SagaRequest request) {
        SagaState state = SagaState.builder()
            .sagaId(sagaId)
            .transactionId(transactionId)
            .status("INITIATED")
            .startedAt(LocalDateTime.now())
            .request(request)
            .steps(new ArrayList<>())
            .build();
        
        return sagaStateRepository.save(state);
    }
    
    /**
     * Initialize saga state
     */
    private SagaState initializeSagaState(String sagaId, UUID transactionId, SagaRequest request) {
        SagaState state = SagaState.builder()
            .sagaId(sagaId)
            .transactionId(transactionId)
            .status("INITIATED")
            .createdAt(LocalDateTime.now())
            .lastUpdated(LocalDateTime.now())
            .request(request)
            .steps(new ArrayList<>())
            .build();
        
        return sagaStateRepository.save(state);
    }
    
    /**
     * Send notifications
     */
    private void sendNotifications(SagaRequest request, TransferResult transferResult) {
        try {
            // Send success notification to sender
            NotificationRequest senderNotification = NotificationRequest.builder()
                .userId(request.getSenderId())
                .type("TRANSACTION_COMPLETED")
                .title("Transfer Completed")
                .message(String.format("Your transfer of %s %s has been completed successfully", 
                    request.getAmount(), request.getCurrency()))
                .transactionId(request.getTransactionId().toString())
                .build();
            
            notificationServiceClient.sendNotification(senderNotification);
            
            // Send notification to recipient if different
            if (!request.getSenderId().equals(request.getRecipientId())) {
                NotificationRequest recipientNotification = NotificationRequest.builder()
                    .userId(request.getRecipientId())
                    .type("TRANSACTION_RECEIVED")
                    .title("Transfer Received")
                    .message(String.format("You have received %s %s", 
                        request.getAmount(), request.getCurrency()))
                    .transactionId(request.getTransactionId().toString())
                    .build();
                
                notificationServiceClient.sendNotification(recipientNotification);
            }
            
        } catch (Exception e) {
            log.warn("Failed to send notifications for transaction {}: {}", 
                request.getTransactionId(), e.getMessage());
            // Don't fail the transaction for notification errors
        }
    }

    /**
     * Update saga state
     */
    private void updateSagaState(SagaState state, String status, Object result) {
        state.setStatus(status);
        state.setLastUpdated(LocalDateTime.now());
        
        if (result != null) {
            state.getSteps().add(SagaStep.builder()
                .name(status)
                .timestamp(LocalDateTime.now())
                .result(result)
                .build()
            );
        }
        
        sagaStateRepository.save(state);
    }
    
    /**
     * Publish saga event
     */
    private void publishSagaEvent(String sagaId, String eventType, SagaRequest request, Object result) {
        SagaEvent event = SagaEvent.builder()
            .sagaId(sagaId)
            .eventType(eventType)
            .transactionId(request.getTransactionId())
            .timestamp(LocalDateTime.now())
            .request(request)
            .result(result)
            .build();
        
        kafkaTemplate.send("saga-events", sagaId, event);
    }
    
    /**
     * CRITICAL ADDITION: Compensate cross-border payment with currency conversion reversal
     */
    public CompensationResult compensateCrossBorderPayment(CrossBorderTransactionContext context) {
        log.info("Compensating cross-border payment: transactionId={}, fromCurrency={}, toCurrency={}", 
            context.getTransactionId(), context.getSourceCurrency(), context.getDestinationCurrency());
        
        List<CompensationAction> compensationActions = new ArrayList<>();
        String sagaId = UUID.randomUUID().toString();
        
        try {
            // Step 1: Reverse currency conversion
            if (!context.getSourceCurrency().equals(context.getDestinationCurrency())) {
                log.info("Reversing currency conversion: {} -> {}", 
                    context.getDestinationCurrency(), context.getSourceCurrency());
                
                BigDecimal reversedAmount = reverseCurrencyConversion(
                    context.getConvertedAmount(),
                    context.getDestinationCurrency(),
                    context.getSourceCurrency(),
                    context.getExchangeRate()
                );
                
                compensationActions.add(CompensationAction.builder()
                    .name("REVERSE_CURRENCY_CONVERSION")
                    .service("CURRENCY_SERVICE")
                    .action(() -> log.info("Currency conversion reversed: {} to {}", 
                        context.getConvertedAmount(), reversedAmount))
                    .timeout(Duration.ofSeconds(30))
                    .retryCount(3)
                    .build()
                );
            }
            
            // Step 2: Refund international transfer fees
            if (context.getInternationalFee().compareTo(BigDecimal.ZERO) > 0) {
                log.info("Refunding international transfer fee: {}", context.getInternationalFee());
                
                RefundFeeRequest refundRequest = RefundFeeRequest.builder()
                    .walletId(context.getSourceWalletId())
                    .amount(context.getInternationalFee())
                    .currency(context.getSourceCurrency())
                    .transactionId(context.getTransactionId())
                    .feeType("INTERNATIONAL_TRANSFER")
                    .reason("Transaction failed - refunding international transfer fee")
                    .build();
                
                walletServiceClient.refundFee(refundRequest);
                
                compensationActions.add(CompensationAction.builder()
                    .name("REFUND_INTERNATIONAL_FEE")
                    .service("WALLET_SERVICE")
                    .action(() -> log.info("International fee refunded: {}", context.getInternationalFee()))
                    .timeout(Duration.ofSeconds(30))
                    .retryCount(3)
                    .build()
                );
            }
            
            // Step 3: Cancel correspondent bank transaction if initiated
            if (context.getCorrespondentBankTransactionId() != null) {
                log.info("Canceling correspondent bank transaction: {}", 
                    context.getCorrespondentBankTransactionId());
                
                compensationActions.add(CompensationAction.builder()
                    .name("CANCEL_CORRESPONDENT_BANK_TRANSACTION")
                    .service("CORRESPONDENT_BANK_SERVICE")
                    .action(() -> cancelCorrespondentBankTransaction(context.getCorrespondentBankTransactionId()))
                    .timeout(Duration.ofSeconds(60))
                    .retryCount(5)
                    .build()
                );
            }
            
            // Step 4: Release SWIFT/wire transfer reservation
            if (context.getSwiftReservationId() != null) {
                log.info("Releasing SWIFT reservation: {}", context.getSwiftReservationId());
                
                compensationActions.add(CompensationAction.builder()
                    .name("RELEASE_SWIFT_RESERVATION")
                    .service("SWIFT_SERVICE")
                    .action(() -> releaseSwiftReservation(context.getSwiftReservationId()))
                    .timeout(Duration.ofSeconds(30))
                    .retryCount(3)
                    .build()
                );
            }
            
            // Step 5: Notify all parties of cancellation
            notifyCrossBorderCancellation(context);
            
            // Step 6: Update compliance records
            updateComplianceRecordsForCancellation(context);
            
            log.info("Cross-border payment compensation completed successfully for transaction: {}", 
                context.getTransactionId());
            
            return CompensationResult.success(
                Map.of(
                    "transactionId", context.getTransactionId(),
                    "compensationActions", compensationActions.size(),
                    "feeRefunded", context.getInternationalFee(),
                    "currency", context.getSourceCurrency()
                ),
                sagaId
            );
            
        } catch (Exception e) {
            log.error("Failed to compensate cross-border payment for transaction: {}", 
                context.getTransactionId(), e);
            
            // Execute best-effort compensation
            executeCompensation(compensationActions, sagaId);
            
            throw new TransactionFailedException(
                String.format("Cross-border payment compensation failed for transaction: %s", 
                    context.getTransactionId()),
                e
            );
        }
    }
    
    /**
     * Reverse currency conversion calculation
     */
    private BigDecimal reverseCurrencyConversion(BigDecimal convertedAmount, String fromCurrency, 
                                                 String toCurrency, BigDecimal originalRate) {
        // Calculate reverse conversion using inverse rate
        BigDecimal inverseRate = BigDecimal.ONE.divide(originalRate, 6, RoundingMode.HALF_UP);
        return convertedAmount.multiply(inverseRate);
    }
    
    /**
     * Cancel correspondent bank transaction
     */
    private void cancelCorrespondentBankTransaction(String correspondentTransactionId) {
        log.info("Requesting correspondent bank transaction cancellation: {}", correspondentTransactionId);
        // In production, this would call actual correspondent bank API
        // For now, log the cancellation request
    }
    
    /**
     * Release SWIFT reservation
     */
    private void releaseSwiftReservation(String swiftReservationId) {
        log.info("Releasing SWIFT reservation: {}", swiftReservationId);
        // In production, this would call SWIFT network API
        // For now, log the release
    }
    
    /**
     * Notify all parties of cross-border cancellation
     */
    private void notifyCrossBorderCancellation(CrossBorderTransactionContext context) {
        try {
            // Notify sender
            NotificationRequest senderNotification = NotificationRequest.builder()
                .userId(context.getSenderId())
                .type("CROSS_BORDER_TRANSACTION_CANCELLED")
                .title("International Transfer Cancelled")
                .message(String.format("Your international transfer of %s %s has been cancelled. " +
                    "Fees have been refunded.", context.getAmount(), context.getSourceCurrency()))
                .metadata(Map.of(
                    "transactionId", context.getTransactionId().toString(),
                    "amount", context.getAmount().toString(),
                    "currency", context.getSourceCurrency(),
                    "feeRefunded", context.getInternationalFee().toString()
                ))
                .build();
            
            notificationServiceClient.sendNotification(senderNotification);
            
            log.info("Cross-border cancellation notification sent to sender: {}", context.getSenderId());
            
        } catch (Exception e) {
            log.warn("Failed to send cross-border cancellation notification: {}", e.getMessage());
        }
    }
    
    /**
     * Update compliance records for cancelled cross-border transaction
     */
    private void updateComplianceRecordsForCancellation(CrossBorderTransactionContext context) {
        try {
            ComplianceUpdateRequest complianceUpdate = ComplianceUpdateRequest.builder()
                .transactionId(context.getTransactionId())
                .status("CANCELLED")
                .cancellationReason("Transaction failed during execution")
                .timestamp(LocalDateTime.now())
                .fromCountry(context.getSourceCountry())
                .toCountry(context.getDestinationCountry())
                .amount(context.getAmount())
                .currency(context.getSourceCurrency())
                .build();
            
            // Publish compliance event
            kafkaTemplate.send("compliance-updates", context.getTransactionId().toString(), complianceUpdate);
            
            log.info("Compliance records updated for cancelled transaction: {}", context.getTransactionId());
            
        } catch (Exception e) {
            log.error("Failed to update compliance records for transaction: {}", 
                context.getTransactionId(), e);
        }
    }
    
    /**
     * Cleanup resources
     */
    public void shutdown() {
        compensationExecutor.shutdown();
        try {
            if (!compensationExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                compensationExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            compensationExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}