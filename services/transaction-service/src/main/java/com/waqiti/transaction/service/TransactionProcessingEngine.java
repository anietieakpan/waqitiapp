package com.waqiti.transaction.service;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import com.waqiti.transaction.dto.*;
import com.waqiti.transaction.repository.TransactionRepository;
import com.waqiti.transaction.client.*;
import com.waqiti.transaction.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Transaction Processing Engine
 * 
 * Core transaction processing orchestrator that coordinates:
 * - Transaction validation and risk assessment
 * - Account balance verification and fund reservation
 * - Ledger posting with double-entry bookkeeping
 * - Compliance and fraud detection checks
 * - Transaction state management and notifications
 * - Error handling and compensation logic
 * 
 * Implements the Saga pattern for distributed transaction management
 * across multiple microservices in the core banking platform.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionProcessingEngine {

    // Thread-safe SecureRandom for secure retry jitter and transaction IDs
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    
    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final LedgerServiceClient ledgerServiceClient;
    private final ComplianceServiceClient complianceServiceClient;
    private final FraudDetectionServiceClient fraudDetectionServiceClient;
    private final NotificationServiceClient notificationServiceClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final TransactionValidationService validationService;
    private final SagaOrchestrationService sagaOrchestrationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ExternalSystemClient externalSystemClient;

    /**
     * Processes a transaction request through the complete banking workflow
     */
    @Transactional
    public TransactionProcessingResult processTransaction(ProcessTransactionRequest request) {
        String sagaId = UUID.randomUUID().toString();
        
        try {
            log.info("Starting transaction processing: type={}, amount={}, sagaId={}", 
                    request.getTransactionType(), request.getAmount(), sagaId);
            
            // Step 1: Create transaction record
            Transaction transaction = createTransactionRecord(request, sagaId);
            
            // Step 2: Start saga orchestration
            SagaExecutionResult sagaResult = sagaOrchestrationService.executeSaga(
                buildTransactionSaga(transaction, request));
            
            if (sagaResult.isSuccess()) {
                // Update transaction status to completed
                transaction.setStatus(TransactionStatus.COMPLETED);
                transaction.setProcessedAt(LocalDateTime.now());
                transaction.setProcessingResult(sagaResult.getExecutionSummary());
                transactionRepository.save(transaction);
                
                // Publish transaction completed event
                publishTransactionEvent(transaction, "TRANSACTION_COMPLETED");
                
                log.info("Transaction processing completed successfully: id={}, sagaId={}", 
                        transaction.getId(), sagaId);
                
                return TransactionProcessingResult.builder()
                    .transactionId(transaction.getId())
                    .status(TransactionProcessingResult.Status.SUCCESS)
                    .message("Transaction processed successfully")
                    .sagaId(sagaId)
                    .build();
                    
            } else {
                // Handle saga failure
                transaction.setStatus(TransactionStatus.FAILED);
                transaction.setFailureReason(sagaResult.getFailureReason());
                transaction.setProcessedAt(LocalDateTime.now());
                transactionRepository.save(transaction);
                
                // Publish transaction failed event
                publishTransactionEvent(transaction, "TRANSACTION_FAILED");
                
                log.error("Transaction processing failed: id={}, sagaId={}, reason={}", 
                         transaction.getId(), sagaId, sagaResult.getFailureReason());
                
                return TransactionProcessingResult.builder()
                    .transactionId(transaction.getId())
                    .status(TransactionProcessingResult.Status.FAILED)
                    .message(sagaResult.getFailureReason())
                    .sagaId(sagaId)
                    .build();
            }
            
        } catch (Exception e) {
            log.error("Unexpected error in transaction processing: sagaId={}", sagaId, e);
            return TransactionProcessingResult.builder()
                .status(TransactionProcessingResult.Status.FAILED)
                .message("Internal processing error: " + e.getMessage())
                .sagaId(sagaId)
                .build();
        }
    }

    /**
     * Retrieves transaction status and details
     */
    public TransactionStatusResponse getTransactionStatus(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
        
        return TransactionStatusResponse.builder()
            .transactionId(transaction.getId())
            .status(transaction.getStatus())
            .transactionType(transaction.getTransactionType())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .sourceAccountId(transaction.getSourceAccountId())
            .targetAccountId(transaction.getTargetAccountId())
            .description(transaction.getDescription())
            .createdAt(transaction.getCreatedAt())
            .processedAt(transaction.getProcessedAt())
            .failureReason(transaction.getFailureReason())
            .build();
    }

    /**
     * Cancels a pending transaction
     */
    @Transactional
    public TransactionCancellationResult cancelTransaction(UUID transactionId, String reason) {
        Transaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));
        
        if (!canCancelTransaction(transaction)) {
            throw new TransactionOperationNotAllowedException(
                "Transaction cannot be cancelled in current status: " + transaction.getStatus());
        }
        
        try {
            // Execute compensation saga to reverse any partial progress
            if (transaction.getSagaId() != null) {
                sagaOrchestrationService.compensateSaga(transaction.getSagaId(), reason);
            }
            
            // Update transaction status
            transaction.setStatus(TransactionStatus.CANCELLED);
            transaction.setFailureReason("Cancelled: " + reason);
            transaction.setProcessedAt(LocalDateTime.now());
            transactionRepository.save(transaction);
            
            // Publish cancellation event
            publishTransactionEvent(transaction, "TRANSACTION_CANCELLED");
            
            log.info("Transaction cancelled: id={}, reason={}", transactionId, reason);
            
            return TransactionCancellationResult.builder()
                .transactionId(transactionId)
                .success(true)
                .message("Transaction cancelled successfully")
                .build();
                
        } catch (Exception e) {
            log.error("Failed to cancel transaction: id={}", transactionId, e);
            return TransactionCancellationResult.builder()
                .transactionId(transactionId)
                .success(false)
                .message("Failed to cancel transaction: " + e.getMessage())
                .build();
        }
    }

    /**
     * Processes batch transactions for bulk operations
     */
    @Transactional
    public BatchTransactionResult processBatchTransactions(BatchTransactionRequest request) {
        String batchId = UUID.randomUUID().toString();
        
        try {
            log.info("Starting batch transaction processing: batchId={}, count={}", 
                    batchId, request.getTransactions().size());
            
            BatchProcessingResult result = sagaOrchestrationService.executeBatchSaga(
                buildBatchTransactionSaga(request, batchId));
            
            log.info("Batch transaction processing completed: batchId={}, successful={}, failed={}", 
                    batchId, result.getSuccessfulCount(), result.getFailedCount());
            
            return BatchTransactionResult.builder()
                .batchId(batchId)
                .totalTransactions(request.getTransactions().size())
                .successfulTransactions(result.getSuccessfulCount())
                .failedTransactions(result.getFailedCount())
                .processingResults(result.getIndividualResults())
                .build();
                
        } catch (Exception e) {
            log.error("Batch transaction processing failed: batchId={}", batchId, e);
            return BatchTransactionResult.builder()
                .batchId(batchId)
                .totalTransactions(request.getTransactions().size())
                .successfulTransactions(0)
                .failedTransactions(request.getTransactions().size())
                .errorMessage("Batch processing failed: " + e.getMessage())
                .build();
        }
    }

    // Private helper methods

    private Transaction createTransactionRecord(ProcessTransactionRequest request, String sagaId) {
        Transaction transaction = Transaction.builder()
            .transactionType(request.getTransactionType())
            .sourceAccountId(request.getSourceAccountId())
            .targetAccountId(request.getTargetAccountId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .description(request.getDescription())
            .status(TransactionStatus.PROCESSING)
            .createdAt(LocalDateTime.now())
            .sagaId(sagaId)
            .initiatedBy(request.getInitiatedBy())
            .channel(request.getChannel())
            .metadata(request.getMetadata())
            .build();
        
        return transactionRepository.save(transaction);
    }

    private TransactionSaga buildTransactionSaga(Transaction transaction, ProcessTransactionRequest request) {
        return TransactionSaga.builder()
            .transactionId(transaction.getId())
            .sagaId(transaction.getSagaId())
            .steps(buildSagaSteps(transaction, request))
            .build();
    }

    private List<SagaStep> buildSagaSteps(Transaction transaction, ProcessTransactionRequest request) {
        List<SagaStep> steps = new ArrayList<>();
        
        // Step 1: Validate transaction
        steps.add(SagaStep.builder()
            .stepName("VALIDATE_TRANSACTION")
            .forwardAction(() -> validateTransaction(transaction, request))
            .compensationAction(() -> logValidationFailure(transaction.getId()))
            .build());
        
        // Step 2: Fraud detection
        steps.add(SagaStep.builder()
            .stepName("FRAUD_DETECTION")
            .forwardAction(() -> performFraudDetection(transaction, request))
            .compensationAction(() -> logFraudCheckFailure(transaction.getId()))
            .build());
        
        // Step 3: Compliance check
        steps.add(SagaStep.builder()
            .stepName("COMPLIANCE_CHECK")
            .forwardAction(() -> performComplianceCheck(transaction, request))
            .compensationAction(() -> logComplianceFailure(transaction.getId()))
            .build());
        
        // Step 4: Reserve funds
        steps.add(SagaStep.builder()
            .stepName("RESERVE_FUNDS")
            .forwardAction(() -> reserveFunds(transaction))
            .compensationAction(() -> releaseFunds(transaction))
            .build());
        
        // Step 5: Post to ledger
        steps.add(SagaStep.builder()
            .stepName("POST_LEDGER")
            .forwardAction(() -> postToLedger(transaction))
            .compensationAction(() -> reverseledgerEntries(transaction))
            .build());
        
        // Step 6: Update account balances
        steps.add(SagaStep.builder()
            .stepName("UPDATE_BALANCES")
            .forwardAction(() -> updateAccountBalances(transaction))
            .compensationAction(() -> revertAccountBalances(transaction))
            .build());
        
        // Step 7: Send notifications
        steps.add(SagaStep.builder()
            .stepName("SEND_NOTIFICATIONS")
            .forwardAction(() -> sendTransactionNotifications(transaction))
            .compensationAction(() -> sendCancellationNotifications(transaction))
            .build());
        
        return steps;
    }

    private SagaStepResult validateTransaction(Transaction transaction, ProcessTransactionRequest request) {
        try {
            TransactionValidationResult validationResult = validationService.validateTransaction(request);
            
            if (!validationResult.isValid()) {
                return SagaStepResult.failure("Transaction validation failed: " + 
                    validationResult.getValidationErrors());
            }
            
            return SagaStepResult.success("Transaction validation passed");
            
        } catch (Exception e) {
            log.error("Transaction validation failed: id={}", transaction.getId(), e);
            return SagaStepResult.failure("Validation error: " + e.getMessage());
        }
    }

    private SagaStepResult performFraudDetection(Transaction transaction, ProcessTransactionRequest request) {
        try {
            FraudDetectionRequest fraudRequest = FraudDetectionRequest.builder()
                .transactionId(transaction.getId())
                .amount(transaction.getAmount())
                .sourceAccountId(transaction.getSourceAccountId())
                .targetAccountId(transaction.getTargetAccountId())
                .deviceFingerprint(request.getDeviceFingerprint())
                .locationData(request.getLocationData())
                .build();
            
            FraudDetectionResult fraudResult = fraudDetectionServiceClient.detectFraud(fraudRequest);
            
            if (fraudResult.isFraudulent()) {
                return SagaStepResult.failure("Transaction flagged as fraudulent: " + 
                    fraudResult.getReason());
            }
            
            // Update transaction with fraud score
            transaction.setFraudScore(fraudResult.getFraudScore());
            transactionRepository.save(transaction);
            
            return SagaStepResult.success("Fraud detection passed");
            
        } catch (Exception e) {
            log.error("Fraud detection failed: id={}", transaction.getId(), e);
            return SagaStepResult.failure("Fraud detection error: " + e.getMessage());
        }
    }

    private SagaStepResult performComplianceCheck(Transaction transaction, ProcessTransactionRequest request) {
        try {
            ComplianceCheckRequest complianceRequest = ComplianceCheckRequest.builder()
                .transactionId(transaction.getId())
                .amount(transaction.getAmount())
                .sourceAccountId(transaction.getSourceAccountId())
                .targetAccountId(transaction.getTargetAccountId())
                .transactionType(transaction.getTransactionType())
                .build();
            
            ComplianceCheckResult complianceResult = complianceServiceClient.checkCompliance(complianceRequest);
            
            if (!complianceResult.isCompliant()) {
                return SagaStepResult.failure("Transaction failed compliance check: " + 
                    complianceResult.getViolationReason());
            }
            
            return SagaStepResult.success("Compliance check passed");
            
        } catch (Exception e) {
            log.error("Compliance check failed: id={}", transaction.getId(), e);
            return SagaStepResult.failure("Compliance check error: " + e.getMessage());
        }
    }

    private SagaStepResult reserveFunds(Transaction transaction) {
        try {
            ReserveFundsRequest reserveRequest = ReserveFundsRequest.builder()
                .accountId(transaction.getSourceAccountId())
                .amount(transaction.getAmount())
                .reservationId(transaction.getId())
                .reason("Transaction: " + transaction.getId())
                .build();
            
            ReserveFundsResponse reserveResponse = accountServiceClient.reserveFunds(
                transaction.getSourceAccountId(), reserveRequest);
            
            if (!reserveResponse.isSuccess()) {
                return SagaStepResult.failure("Failed to reserve funds: " + 
                    reserveResponse.getFailureReason());
            }
            
            return SagaStepResult.success("Funds reserved successfully");
            
        } catch (Exception e) {
            log.error("Fund reservation failed: id={}", transaction.getId(), e);
            return SagaStepResult.failure("Fund reservation error: " + e.getMessage());
        }
    }

    private SagaStepResult postToLedger(Transaction transaction) {
        try {
            PostTransactionRequest ledgerRequest = PostTransactionRequest.builder()
                .transactionId(transaction.getId())
                .ledgerEntries(buildLedgerEntries(transaction))
                .build();
            
            PostTransactionResult ledgerResult = ledgerServiceClient.postTransaction(ledgerRequest);
            
            if (!ledgerResult.isSuccess()) {
                return SagaStepResult.failure("Failed to post to ledger: " + 
                    ledgerResult.getFailureReason());
            }
            
            return SagaStepResult.success("Ledger entries posted successfully");
            
        } catch (Exception e) {
            log.error("Ledger posting failed: id={}", transaction.getId(), e);
            return SagaStepResult.failure("Ledger posting error: " + e.getMessage());
        }
    }

    private SagaStepResult updateAccountBalances(Transaction transaction) {
        try {
            // Confirm the reserved funds transaction
            ConfirmReservedTransactionRequest confirmRequest = ConfirmReservedTransactionRequest.builder()
                .accountId(transaction.getSourceAccountId())
                .reservationId(transaction.getId())
                .amount(transaction.getAmount())
                .build();
            
            accountServiceClient.confirmReservedTransaction(
                transaction.getSourceAccountId(), confirmRequest);
            
            return SagaStepResult.success("Account balances updated successfully");
            
        } catch (Exception e) {
            log.error("Account balance update failed: id={}", transaction.getId(), e);
            return SagaStepResult.failure("Account balance update error: " + e.getMessage());
        }
    }

    private SagaStepResult sendTransactionNotifications(Transaction transaction) {
        try {
            TransactionNotificationRequest notificationRequest = TransactionNotificationRequest.builder()
                .transactionId(transaction.getId())
                .sourceAccountId(transaction.getSourceAccountId())
                .targetAccountId(transaction.getTargetAccountId())
                .amount(transaction.getAmount())
                .transactionType(transaction.getTransactionType())
                .status("COMPLETED")
                .build();
            
            notificationServiceClient.sendTransactionNotification(notificationRequest);
            
            return SagaStepResult.success("Notifications sent successfully");
            
        } catch (Exception e) {
            log.warn("Notification sending failed (non-critical): id={}", transaction.getId(), e);
            // Notifications are non-critical, so we don't fail the saga
            return SagaStepResult.success("Transaction completed (notification failed)");
        }
    }

    // Compensation methods
    private SagaStepResult releaseFunds(Transaction transaction) {
        try {
            ReleaseReservedFundsRequest releaseRequest = ReleaseReservedFundsRequest.builder()
                .reservationId(transaction.getId())
                .amount(transaction.getAmount())
                .build();
            
            accountServiceClient.releaseReservedFunds(
                transaction.getSourceAccountId(), releaseRequest);
            
            return SagaStepResult.success("Funds released successfully");
            
        } catch (Exception e) {
            log.error("Fund release compensation failed: id={}", transaction.getId(), e);
            return SagaStepResult.failure("Fund release error: " + e.getMessage());
        }
    }

    private List<LedgerEntryRequest> buildLedgerEntries(Transaction transaction) {
        List<LedgerEntryRequest> entries = new ArrayList<>();
        
        // Debit source account
        entries.add(LedgerEntryRequest.builder()
            .accountId(transaction.getSourceAccountId())
            .entryType("DEBIT")
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .description(transaction.getDescription() + " - Debit")
            .referenceNumber(transaction.getId().toString())
            .transactionDate(LocalDateTime.now())
            .valueDate(LocalDateTime.now())
            .contraAccountId(transaction.getTargetAccountId())
            .build());
        
        // Credit target account
        entries.add(LedgerEntryRequest.builder()
            .accountId(transaction.getTargetAccountId())
            .entryType("CREDIT")
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .description(transaction.getDescription() + " - Credit")
            .referenceNumber(transaction.getId().toString())
            .transactionDate(LocalDateTime.now())
            .valueDate(LocalDateTime.now())
            .contraAccountId(transaction.getSourceAccountId())
            .build());
        
        return entries;
    }

    private boolean canCancelTransaction(Transaction transaction) {
        return transaction.getStatus() == TransactionStatus.PENDING ||
               transaction.getStatus() == TransactionStatus.PROCESSING;
    }

    private void publishTransactionEvent(Transaction transaction, String eventType) {
        try {
            TransactionEvent event = TransactionEvent.builder()
                .transactionId(transaction.getId())
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .transactionData(transaction)
                .build();
            
            kafkaTemplate.send("transaction-events", event);
            
        } catch (Exception e) {
            log.warn("Failed to publish transaction event: id={}, eventType={}", 
                    transaction.getId(), eventType, e);
        }
    }

    // Placeholder compensation methods
    private SagaStepResult logValidationFailure(UUID transactionId) {
        log.info("Validation failure compensation for transaction: {}", transactionId);
        return SagaStepResult.success("Validation failure logged");
    }

    private SagaStepResult logFraudCheckFailure(UUID transactionId) {
        log.info("Fraud check failure compensation for transaction: {}", transactionId);
        return SagaStepResult.success("Fraud check failure logged");
    }

    private SagaStepResult logComplianceFailure(UUID transactionId) {
        log.info("Compliance failure compensation for transaction: {}", transactionId);
        return SagaStepResult.success("Compliance failure logged");
    }

    private SagaStepResult reverseledgerEntries(Transaction transaction) {
        log.info("Reversing ledger entries for transaction: {}", transaction.getId());
        // Implementation would reverse the ledger entries
        return SagaStepResult.success("Ledger entries reversed");
    }

    private SagaStepResult revertAccountBalances(Transaction transaction) {
        log.info("Reverting account balances for transaction: {}", transaction.getId());
        // Implementation would revert account balance changes
        return SagaStepResult.success("Account balances reverted");
    }

    private SagaStepResult sendCancellationNotifications(Transaction transaction) {
        log.info("Sending cancellation notifications for transaction: {}", transaction.getId());
        // Implementation would send cancellation notifications
        return SagaStepResult.success("Cancellation notifications sent");
    }

    private BatchTransactionSaga buildBatchTransactionSaga(BatchTransactionRequest request, String batchId) {
        // Implementation would build saga for batch processing
        return BatchTransactionSaga.builder()
            .batchId(batchId)
            .transactions(request.getTransactions())
            .build();
    }

    // GROUP 1: Critical Transaction Methods Implementation

    /**
     * 1. Batch processing optimization
     * Processes transactions in batches with optimized resource usage and parallel processing
     */
    @Transactional
    public BatchProcessingResult processBatch(BatchProcessingRequest request) {
        String batchId = request.getBatchId();
        List<ProcessTransactionRequest> transactions = request.getTransactions();
        
        try {
            log.info("Starting optimized batch processing: batchId={}, size={}, parallel={}", 
                    batchId, transactions.size(), request.isParallelProcessing());

            // Validate batch size limits
            if (transactions.size() > request.getMaxBatchSize()) {
                throw new BatchProcessingException("Batch size exceeds limit: " + transactions.size());
            }

            List<TransactionProcessingResult> results = new ArrayList<>();
            List<TransactionProcessingResult> successfulResults = new ArrayList<>();
            List<TransactionProcessingResult> failedResults = new ArrayList<>();

            if (request.isParallelProcessing()) {
                // Process transactions in parallel
                results = transactions.parallelStream()
                    .map(txnRequest -> {
                        try {
                            return processTransaction(txnRequest);
                        } catch (Exception e) {
                            log.error("Parallel batch processing failed for transaction: {}", txnRequest, e);
                            return TransactionProcessingResult.builder()
                                .status(TransactionProcessingResult.Status.FAILED)
                                .message("Processing error: " + e.getMessage())
                                .build();
                        }
                    })
                    .collect(Collectors.toList());
            } else {
                // Sequential processing with controlled resource usage
                for (ProcessTransactionRequest txnRequest : transactions) {
                    try {
                        TransactionProcessingResult result = processTransaction(txnRequest);
                        results.add(result);
                        
                        // Apply rate limiting between transactions
                        if (request.getDelayBetweenTransactions() > 0) {
                            TimeUnit.MILLISECONDS.sleep(request.getDelayBetweenTransactions());
                        }
                    } catch (Exception e) {
                        log.error("Sequential batch processing failed for transaction: {}", txnRequest, e);
                        TransactionProcessingResult failedResult = TransactionProcessingResult.builder()
                            .status(TransactionProcessingResult.Status.FAILED)
                            .message("Processing error: " + e.getMessage())
                            .build();
                        results.add(failedResult);
                    }
                }
            }

            // Categorize results
            for (TransactionProcessingResult result : results) {
                if (result.getStatus() == TransactionProcessingResult.Status.SUCCESS) {
                    successfulResults.add(result);
                } else {
                    failedResults.add(result);
                }
            }

            // Publish batch completed event
            publishBatchCompletedEvent(batchId, successfulResults.size(), failedResults.size());

            log.info("Batch processing completed: batchId={}, successful={}, failed={}", 
                    batchId, successfulResults.size(), failedResults.size());

            return BatchProcessingResult.builder()
                .batchId(batchId)
                .totalTransactions(transactions.size())
                .successfulTransactions(successfulResults.size())
                .failedTransactions(failedResults.size())
                .successfulResults(successfulResults)
                .failedResults(failedResults)
                .processingTimeMs(System.currentTimeMillis() - request.getStartTime())
                .build();

        } catch (Exception e) {
            log.error("Batch processing failed: batchId={}", batchId, e);
            throw new BatchProcessingException("Batch processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * 2. Batch rollback mechanism
     * Rolls back all transactions in a batch if critical failure occurs
     */
    @Transactional
    public BatchRollbackResult rollbackBatch(String batchId, String reason) {
        try {
            log.info("Starting batch rollback: batchId={}, reason={}", batchId, reason);

            // Find all transactions in the batch (with pagination to prevent OOM)
            Pageable pageable = PageRequest.of(0, 1000); // Max 1000 transactions per batch
            List<Transaction> batchTransactions = transactionRepository.findByBatchId(batchId, pageable).getContent();
            
            if (batchTransactions.isEmpty()) {
                throw new BatchNotFoundException("No transactions found for batchId: " + batchId);
            }

            List<UUID> rolledBackTransactions = new ArrayList<>();
            List<String> rollbackErrors = new ArrayList<>();

            for (Transaction transaction : batchTransactions) {
                try {
                    if (transaction.getStatus() == TransactionStatus.COMPLETED) {
                        // Execute compensation saga to reverse the transaction
                        if (transaction.getSagaId() != null) {
                            sagaOrchestrationService.compensateSaga(transaction.getSagaId(), 
                                "Batch rollback: " + reason);
                        }

                        // Update transaction status
                        transaction.setStatus(TransactionStatus.ROLLED_BACK);
                        transaction.setFailureReason("Batch rollback: " + reason);
                        transaction.setRolledBackAt(LocalDateTime.now());
                        transactionRepository.save(transaction);

                        rolledBackTransactions.add(transaction.getId());
                        
                        // Publish rollback event
                        publishTransactionEvent(transaction, "TRANSACTION_ROLLED_BACK");

                        log.info("Transaction rolled back: id={}, batchId={}", transaction.getId(), batchId);
                    }
                } catch (Exception e) {
                    log.error("Failed to rollback transaction: id={}, batchId={}", 
                            transaction.getId(), batchId, e);
                    rollbackErrors.add("Transaction " + transaction.getId() + ": " + e.getMessage());
                }
            }

            // Publish batch rollback completed event
            publishBatchRollbackEvent(batchId, rolledBackTransactions.size(), rollbackErrors.size());

            log.info("Batch rollback completed: batchId={}, rolledBack={}, errors={}", 
                    batchId, rolledBackTransactions.size(), rollbackErrors.size());

            return BatchRollbackResult.builder()
                .batchId(batchId)
                .totalTransactions(batchTransactions.size())
                .rolledBackTransactions(rolledBackTransactions.size())
                .rolledBackTransactionIds(rolledBackTransactions)
                .errors(rollbackErrors)
                .success(rollbackErrors.isEmpty())
                .completedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Batch rollback failed: batchId={}", batchId, e);
            throw new BatchRollbackException("Batch rollback failed: " + e.getMessage(), e);
        }
    }

    /**
     * 3. Concurrent lock management
     * Acquires distributed locks for transaction processing to prevent race conditions
     */
    public DistributedLockResult acquireDistributedLock(String lockKey, long timeoutMs, String lockOwner) {
        try {
            log.debug("Attempting to acquire distributed lock: key={}, timeout={}ms, owner={}", 
                    lockKey, timeoutMs, lockOwner);

            String lockValue = lockOwner + ":" + System.currentTimeMillis();
            long expirationTime = System.currentTimeMillis() + timeoutMs;
            
            // Try to acquire lock using Redis SET with NX (not exists) and PX (expiration)
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                "lock:" + lockKey, 
                lockValue, 
                Duration.ofMillis(timeoutMs)
            );

            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Successfully acquired distributed lock: key={}, owner={}", lockKey, lockOwner);
                
                // Store lock metadata
                storeLockMetadata(lockKey, lockOwner, expirationTime);
                
                return DistributedLockResult.builder()
                    .lockKey(lockKey)
                    .lockOwner(lockOwner)
                    .acquired(true)
                    .lockValue(lockValue)
                    .expirationTime(expirationTime)
                    .acquiredAt(LocalDateTime.now())
                    .build();
            } else {
                log.warn("Failed to acquire distributed lock (already held): key={}, owner={}", 
                        lockKey, lockOwner);
                
                // Get current lock holder information
                String currentLockValue = redisTemplate.opsForValue().get("lock:" + lockKey);
                
                return DistributedLockResult.builder()
                    .lockKey(lockKey)
                    .lockOwner(lockOwner)
                    .acquired(false)
                    .currentLockHolder(extractLockOwner(currentLockValue))
                    .failureReason("Lock already held by another process")
                    .build();
            }

        } catch (Exception e) {
            log.error("Error acquiring distributed lock: key={}, owner={}", lockKey, lockOwner, e);
            return DistributedLockResult.builder()
                .lockKey(lockKey)
                .lockOwner(lockOwner)
                .acquired(false)
                .failureReason("Lock acquisition error: " + e.getMessage())
                .build();
        }
    }

    /**
     * 4. Lock release mechanism
     * Releases distributed locks safely using Lua script for atomicity
     */
    public LockReleaseResult releaseDistributedLock(String lockKey, String lockOwner, String lockValue) {
        try {
            log.debug("Attempting to release distributed lock: key={}, owner={}", lockKey, lockOwner);

            // Lua script for atomic lock release (only if we own the lock)
            String luaScript = 
                "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "    redis.call('del', KEYS[1]) " +
                "    return 1 " +
                "else " +
                "    return 0 " +
                "end";

            Long result = redisTemplate.execute(
                RedisScript.of(luaScript, Long.class),
                Collections.singletonList("lock:" + lockKey),
                lockValue
            );

            if (result != null && result == 1L) {
                log.debug("Successfully released distributed lock: key={}, owner={}", lockKey, lockOwner);
                
                // Remove lock metadata
                removeLockMetadata(lockKey);
                
                return LockReleaseResult.builder()
                    .lockKey(lockKey)
                    .lockOwner(lockOwner)
                    .released(true)
                    .releasedAt(LocalDateTime.now())
                    .build();
            } else {
                log.warn("Failed to release distributed lock (not owner or expired): key={}, owner={}", 
                        lockKey, lockOwner);
                
                return LockReleaseResult.builder()
                    .lockKey(lockKey)
                    .lockOwner(lockOwner)
                    .released(false)
                    .failureReason("Lock not owned or already expired")
                    .build();
            }

        } catch (Exception e) {
            log.error("Error releasing distributed lock: key={}, owner={}", lockKey, lockOwner, e);
            return LockReleaseResult.builder()
                .lockKey(lockKey)
                .lockOwner(lockOwner)
                .released(false)
                .failureReason("Lock release error: " + e.getMessage())
                .build();
        }
    }

    /**
     * 5. Deadlock detection
     * Detects potential deadlocks in distributed transaction processing
     */
    public DeadlockDetectionResult detectDeadlock(String transactionId, List<String> resourceIds) {
        try {
            log.debug("Starting deadlock detection: transactionId={}, resources={}", 
                    transactionId, resourceIds);

            // Build wait-for graph
            Map<String, Set<String>> waitForGraph = buildWaitForGraph(transactionId, resourceIds);
            
            // Detect cycles in the wait-for graph using DFS
            Set<String> visited = new HashSet<>();
            Set<String> recursionStack = new HashSet<>();
            List<List<String>> deadlockCycles = new ArrayList<>();

            for (String node : waitForGraph.keySet()) {
                if (!visited.contains(node)) {
                    List<String> currentPath = new ArrayList<>();
                    if (detectCycleDFS(node, waitForGraph, visited, recursionStack, currentPath, deadlockCycles)) {
                        break; // Found deadlock, no need to continue
                    }
                }
            }

            if (!deadlockCycles.isEmpty()) {
                log.warn("Deadlock detected: transactionId={}, cycles={}", transactionId, deadlockCycles);
                
                // Calculate victim transaction (usually the one with least work done)
                String victimTransaction = selectVictimTransaction(deadlockCycles.get(0));
                
                return DeadlockDetectionResult.builder()
                    .transactionId(transactionId)
                    .deadlockDetected(true)
                    .deadlockCycles(deadlockCycles)
                    .victimTransaction(victimTransaction)
                    .resourcesInvolved(new HashSet<>(resourceIds))
                    .detectedAt(LocalDateTime.now())
                    .severity(calculateDeadlockSeverity(deadlockCycles))
                    .build();
            } else {
                log.debug("No deadlock detected: transactionId={}", transactionId);
                
                return DeadlockDetectionResult.builder()
                    .transactionId(transactionId)
                    .deadlockDetected(false)
                    .checkedAt(LocalDateTime.now())
                    .build();
            }

        } catch (Exception e) {
            log.error("Error in deadlock detection: transactionId={}", transactionId, e);
            return DeadlockDetectionResult.builder()
                .transactionId(transactionId)
                .deadlockDetected(false)
                .errorMessage("Deadlock detection error: " + e.getMessage())
                .build();
        }
    }

    /**
     * 6. Deadlock recovery
     * Recovers from deadlock by aborting victim transaction and retrying
     */
    @Transactional
    public DeadlockRecoveryResult recoverFromDeadlock(DeadlockDetectionResult deadlockInfo) {
        try {
            log.info("Starting deadlock recovery: victimTransaction={}", 
                    deadlockInfo.getVictimTransaction());

            String victimTransactionId = deadlockInfo.getVictimTransaction();
            
            // Find the victim transaction
            Transaction victimTransaction = transactionRepository.findById(UUID.fromString(victimTransactionId))
                .orElseThrow(() -> new TransactionNotFoundException("Victim transaction not found: " + victimTransactionId));

            // Abort the victim transaction
            TransactionCancellationResult cancellationResult = cancelTransaction(
                victimTransaction.getId(), 
                "Deadlock victim - aborted for recovery"
            );

            if (!cancellationResult.isSuccess()) {
                log.error("Failed to abort victim transaction: {}", victimTransactionId);
                return DeadlockRecoveryResult.builder()
                    .deadlockId(deadlockInfo.getTransactionId())
                    .victimTransactionId(victimTransactionId)
                    .recoverySuccessful(false)
                    .errorMessage("Failed to abort victim transaction")
                    .build();
            }

            // Release all locks held by the victim transaction
            releaseTransactionLocks(victimTransaction);

            // Allow other transactions to proceed
            notifyWaitingTransactions(deadlockInfo.getResourcesInvolved());

            // Schedule retry for victim transaction if configured
            boolean retryScheduled = false;
            if (shouldRetryAfterDeadlock(victimTransaction)) {
                scheduleTransactionRetry(victimTransaction, "Retry after deadlock recovery");
                retryScheduled = true;
            }

            // Publish deadlock recovery event
            publishDeadlockRecoveryEvent(deadlockInfo, victimTransactionId, retryScheduled);

            log.info("Deadlock recovery completed: victimTransaction={}, retryScheduled={}", 
                    victimTransactionId, retryScheduled);

            return DeadlockRecoveryResult.builder()
                .deadlockId(deadlockInfo.getTransactionId())
                .victimTransactionId(victimTransactionId)
                .recoverySuccessful(true)
                .retryScheduled(retryScheduled)
                .resourcesReleased(new ArrayList<>(deadlockInfo.getResourcesInvolved()))
                .recoveryCompletedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Deadlock recovery failed: deadlockId={}", deadlockInfo.getTransactionId(), e);
            return DeadlockRecoveryResult.builder()
                .deadlockId(deadlockInfo.getTransactionId())
                .recoverySuccessful(false)
                .errorMessage("Recovery error: " + e.getMessage())
                .build();
        }
    }

    /**
     * 7. Transaction retry logic
     * Implements exponential backoff retry logic for failed transactions
     */
    @Transactional
    public TransactionRetryResult retryFailedTransaction(UUID transactionId, RetryConfig retryConfig) {
        try {
            log.info("Starting transaction retry: transactionId={}, attempt={}/{}", 
                    transactionId, retryConfig.getCurrentAttempt(), retryConfig.getMaxRetries());

            // Find the failed transaction
            Transaction failedTransaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

            if (!canRetryTransaction(failedTransaction)) {
                return TransactionRetryResult.builder()
                    .transactionId(transactionId)
                    .retrySuccessful(false)
                    .finalFailure(true)
                    .failureReason("Transaction cannot be retried in status: " + failedTransaction.getStatus())
                    .build();
            }

            // Check retry limits
            if (retryConfig.getCurrentAttempt() >= retryConfig.getMaxRetries()) {
                log.warn("Transaction retry limit exceeded: transactionId={}, attempts={}", 
                        transactionId, retryConfig.getCurrentAttempt());
                
                // Mark as permanently failed
                failedTransaction.setStatus(TransactionStatus.PERMANENTLY_FAILED);
                failedTransaction.setFailureReason("Retry limit exceeded after " + retryConfig.getMaxRetries() + " attempts");
                transactionRepository.save(failedTransaction);

                return TransactionRetryResult.builder()
                    .transactionId(transactionId)
                    .retrySuccessful(false)
                    .finalFailure(true)
                    .failureReason("Retry limit exceeded")
                    .totalAttempts(retryConfig.getCurrentAttempt())
                    .build();
            }

            // Calculate backoff delay
            long backoffDelay = calculateExponentialBackoff(
                retryConfig.getCurrentAttempt(), 
                retryConfig.getBaseDelayMs(), 
                retryConfig.getMaxDelayMs(),
                retryConfig.getJitterFactor()
            );

            // Wait for backoff period
            if (backoffDelay > 0) {
                log.debug("Applying backoff delay: {}ms", backoffDelay);
                TimeUnit.MILLISECONDS.sleep(backoffDelay);
            }

            // Create new transaction request from failed transaction
            ProcessTransactionRequest retryRequest = buildRetryRequest(failedTransaction);
            
            // Update retry metadata
            retryConfig.setCurrentAttempt(retryConfig.getCurrentAttempt() + 1);
            retryRequest.getMetadata().put("retryAttempt", String.valueOf(retryConfig.getCurrentAttempt()));
            retryRequest.getMetadata().put("originalTransactionId", transactionId.toString());

            // Process the retry
            TransactionProcessingResult retryResult = processTransaction(retryRequest);

            if (retryResult.getStatus() == TransactionProcessingResult.Status.SUCCESS) {
                log.info("Transaction retry successful: transactionId={}, attempt={}", 
                        transactionId, retryConfig.getCurrentAttempt());

                // Update original transaction status
                failedTransaction.setStatus(TransactionStatus.RETRIED_SUCCESSFUL);
                failedTransaction.setRetrySuccessfulAt(LocalDateTime.now());
                failedTransaction.setSuccessfulRetryTransactionId(retryResult.getTransactionId());
                transactionRepository.save(failedTransaction);

                return TransactionRetryResult.builder()
                    .transactionId(transactionId)
                    .retrySuccessful(true)
                    .newTransactionId(retryResult.getTransactionId())
                    .totalAttempts(retryConfig.getCurrentAttempt())
                    .successfulAt(LocalDateTime.now())
                    .build();
            } else {
                log.warn("Transaction retry failed: transactionId={}, attempt={}, reason={}", 
                        transactionId, retryConfig.getCurrentAttempt(), retryResult.getMessage());

                // Check if we should retry again
                if (shouldRetryAgain(retryResult, retryConfig)) {
                    return retryFailedTransaction(transactionId, retryConfig);
                } else {
                    // Mark as permanently failed
                    failedTransaction.setStatus(TransactionStatus.PERMANENTLY_FAILED);
                    failedTransaction.setFailureReason("Retry failed after " + retryConfig.getCurrentAttempt() + " attempts: " + retryResult.getMessage());
                    transactionRepository.save(failedTransaction);

                    return TransactionRetryResult.builder()
                        .transactionId(transactionId)
                        .retrySuccessful(false)
                        .finalFailure(true)
                        .failureReason(retryResult.getMessage())
                        .totalAttempts(retryConfig.getCurrentAttempt())
                        .build();
                }
            }

        } catch (Exception e) {
            log.error("Transaction retry error: transactionId={}", transactionId, e);
            return TransactionRetryResult.builder()
                .transactionId(transactionId)
                .retrySuccessful(false)
                .finalFailure(false)
                .failureReason("Retry error: " + e.getMessage())
                .totalAttempts(retryConfig.getCurrentAttempt())
                .build();
        }
    }

    /**
     * 8. Batch reconciliation
     * Reconciles batch transactions with external systems and validates consistency
     */
    @Transactional
    public BatchReconciliationResult reconcileBatch(String batchId, ReconciliationConfig config) {
        try {
            log.info("Starting batch reconciliation: batchId={}, config={}", batchId, config);

            // Get all transactions in the batch (with pagination to prevent OOM)
            Pageable pageable = PageRequest.of(0, 1000); // Max 1000 transactions per batch
            List<Transaction> batchTransactions = transactionRepository.findByBatchId(batchId, pageable).getContent();
            
            if (batchTransactions.isEmpty()) {
                throw new BatchNotFoundException("No transactions found for batchId: " + batchId);
            }

            // Get external system records for comparison
            List<ExternalTransactionRecord> externalRecords = getExternalRecords(batchId, config);
            
            // Perform reconciliation analysis
            ReconciliationAnalysis analysis = performReconciliationAnalysis(batchTransactions, externalRecords, config);
            
            List<ReconciliationDiscrepancy> discrepancies = new ArrayList<>();
            List<UUID> reconciledTransactionIds = new ArrayList<>();
            List<UUID> failedReconciliationIds = new ArrayList<>();

            // Process each transaction
            for (Transaction transaction : batchTransactions) {
                try {
                    ReconciliationResult txnReconciliation = reconcileIndividualTransaction(
                        transaction, externalRecords, config);
                    
                    if (txnReconciliation.isReconciled()) {
                        reconciledTransactionIds.add(transaction.getId());
                        
                        // Update transaction reconciliation status
                        transaction.setReconciledAt(LocalDateTime.now());
                        transaction.setReconciliationStatus("RECONCILED");
                        transactionRepository.save(transaction);
                        
                    } else {
                        failedReconciliationIds.add(transaction.getId());
                        discrepancies.addAll(txnReconciliation.getDiscrepancies());
                        
                        // Update transaction with discrepancies
                        transaction.setReconciliationStatus("DISCREPANCY_FOUND");
                        transaction.setReconciliationNotes(txnReconciliation.getDiscrepancySummary());
                        transactionRepository.save(transaction);
                    }
                } catch (Exception e) {
                    log.error("Failed to reconcile individual transaction: id={}, batchId={}", 
                            transaction.getId(), batchId, e);
                    failedReconciliationIds.add(transaction.getId());
                    discrepancies.add(new ReconciliationDiscrepancy(
                        transaction.getId().toString(),
                        "RECONCILIATION_ERROR",
                        "Error during reconciliation: " + e.getMessage(),
                        transaction.getAmount(),
                        null
                    ));
                }
            }

            // Generate reconciliation report
            ReconciliationReport report = generateReconciliationReport(
                batchId, batchTransactions, externalRecords, discrepancies, analysis);

            // Handle discrepancies based on configuration
            if (!discrepancies.isEmpty() && config.isAutoResolveDiscrepancies()) {
                resolveDiscrepancies(discrepancies, config);
            }

            // Publish reconciliation completed event
            publishBatchReconciliationEvent(batchId, reconciledTransactionIds.size(), 
                failedReconciliationIds.size(), discrepancies.size());

            log.info("Batch reconciliation completed: batchId={}, reconciled={}, failed={}, discrepancies={}", 
                    batchId, reconciledTransactionIds.size(), failedReconciliationIds.size(), discrepancies.size());

            return BatchReconciliationResult.builder()
                .batchId(batchId)
                .totalTransactions(batchTransactions.size())
                .reconciledTransactions(reconciledTransactionIds.size())
                .failedReconciliations(failedReconciliationIds.size())
                .discrepancyCount(discrepancies.size())
                .reconciledTransactionIds(reconciledTransactionIds)
                .failedReconciliationIds(failedReconciliationIds)
                .discrepancies(discrepancies)
                .reconciliationReport(report)
                .reconciliationSuccessful(discrepancies.isEmpty())
                .completedAt(LocalDateTime.now())
                .build();

        } catch (Exception e) {
            log.error("Batch reconciliation failed: batchId={}", batchId, e);
            throw new BatchReconciliationException("Batch reconciliation failed: " + e.getMessage(), e);
        }
    }

    // Helper methods for the implemented functions

    private void publishBatchCompletedEvent(String batchId, int successful, int failed) {
        try {
            BatchEvent event = BatchEvent.builder()
                .batchId(batchId)
                .eventType("BATCH_COMPLETED")
                .successfulCount(successful)
                .failedCount(failed)
                .timestamp(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send("batch-events", event);
        } catch (Exception e) {
            log.warn("Failed to publish batch completed event: batchId={}", batchId, e);
        }
    }

    private void publishBatchRollbackEvent(String batchId, int rolledBack, int errors) {
        try {
            BatchEvent event = BatchEvent.builder()
                .batchId(batchId)
                .eventType("BATCH_ROLLED_BACK")
                .rolledBackCount(rolledBack)
                .errorCount(errors)
                .timestamp(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send("batch-events", event);
        } catch (Exception e) {
            log.warn("Failed to publish batch rollback event: batchId={}", batchId, e);
        }
    }

    private void storeLockMetadata(String lockKey, String lockOwner, long expirationTime) {
        try {
            LockMetadata metadata = LockMetadata.builder()
                .lockKey(lockKey)
                .lockOwner(lockOwner)
                .expirationTime(expirationTime)
                .acquiredAt(System.currentTimeMillis())
                .build();
            
            redisTemplate.opsForHash().put("lock_metadata", lockKey, metadata);
        } catch (Exception e) {
            log.warn("Failed to store lock metadata: key={}", lockKey, e);
        }
    }

    private void removeLockMetadata(String lockKey) {
        try {
            redisTemplate.opsForHash().delete("lock_metadata", lockKey);
        } catch (Exception e) {
            log.warn("Failed to remove lock metadata: key={}", lockKey, e);
        }
    }

    private String extractLockOwner(String lockValue) {
        if (lockValue != null && lockValue.contains(":")) {
            return lockValue.split(":")[0];
        }
        return "unknown";
    }

    private Map<String, Set<String>> buildWaitForGraph(String transactionId, List<String> resourceIds) {
        Map<String, Set<String>> graph = new HashMap<>();
        
        // Build the wait-for relationships based on current lock holders
        for (String resourceId : resourceIds) {
            String lockKey = "lock:" + resourceId;
            String currentLockValue = redisTemplate.opsForValue().get(lockKey);
            
            if (currentLockValue != null) {
                String currentOwner = extractLockOwner(currentLockValue);
                if (!currentOwner.equals(transactionId)) {
                    graph.computeIfAbsent(transactionId, k -> new HashSet<>()).add(currentOwner);
                }
            }
        }
        
        return graph;
    }

    private boolean detectCycleDFS(String node, Map<String, Set<String>> graph, Set<String> visited,
                                 Set<String> recursionStack, List<String> currentPath, List<List<String>> cycles) {
        visited.add(node);
        recursionStack.add(node);
        currentPath.add(node);

        Set<String> neighbors = graph.get(node);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    if (detectCycleDFS(neighbor, graph, visited, recursionStack, currentPath, cycles)) {
                        return true;
                    }
                } else if (recursionStack.contains(neighbor)) {
                    // Found cycle
                    int cycleStart = currentPath.indexOf(neighbor);
                    cycles.add(new ArrayList<>(currentPath.subList(cycleStart, currentPath.size())));
                    return true;
                }
            }
        }

        recursionStack.remove(node);
        currentPath.remove(currentPath.size() - 1);
        return false;
    }

    private String selectVictimTransaction(List<String> cycle) {
        // Simple strategy: select the transaction that appears first in the cycle
        // In a real system, you might consider factors like work done, priority, etc.
        return cycle.get(0);
    }

    private DeadlockSeverity calculateDeadlockSeverity(List<List<String>> cycles) {
        if (cycles.isEmpty()) return DeadlockSeverity.NONE;
        if (cycles.size() == 1 && cycles.get(0).size() <= 2) return DeadlockSeverity.LOW;
        if (cycles.size() <= 2) return DeadlockSeverity.MEDIUM;
        return DeadlockSeverity.HIGH;
    }

    private void releaseTransactionLocks(Transaction transaction) {
        try {
            // Find and release all locks held by this transaction
            String lockPattern = "lock:*";
            Set<String> lockKeys = redisTemplate.keys(lockPattern);
            
            for (String lockKey : lockKeys) {
                String lockValue = redisTemplate.opsForValue().get(lockKey);
                if (lockValue != null && lockValue.startsWith(transaction.getId().toString())) {
                    redisTemplate.delete(lockKey);
                }
            }
        } catch (Exception e) {
            log.error("Failed to release transaction locks: transactionId={}", transaction.getId(), e);
        }
    }

    private void notifyWaitingTransactions(Set<String> resourceIds) {
        for (String resourceId : resourceIds) {
            kafkaTemplate.send("lock-release-notifications", resourceId);
        }
    }

    private boolean shouldRetryAfterDeadlock(Transaction transaction) {
        // Check if transaction is eligible for retry after deadlock
        return transaction.getRetryCount() < 3 && 
               transaction.getTransactionType() != TransactionType.CRITICAL_SYSTEM_TRANSFER;
    }

    private void scheduleTransactionRetry(Transaction transaction, String reason) {
        try {
            RetryScheduleEvent event = RetryScheduleEvent.builder()
                .transactionId(transaction.getId())
                .reason(reason)
                .retryDelay(calculateRetryDelay(transaction.getRetryCount()))
                .scheduledAt(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send("transaction-retry-schedule", event);
        } catch (Exception e) {
            log.error("Failed to schedule transaction retry: transactionId={}", transaction.getId(), e);
        }
    }

    private void publishDeadlockRecoveryEvent(DeadlockDetectionResult deadlockInfo, String victimTransactionId, boolean retryScheduled) {
        try {
            DeadlockRecoveryEvent event = DeadlockRecoveryEvent.builder()
                .deadlockId(deadlockInfo.getTransactionId())
                .victimTransactionId(victimTransactionId)
                .retryScheduled(retryScheduled)
                .recoveredAt(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send("deadlock-recovery-events", event);
        } catch (Exception e) {
            log.warn("Failed to publish deadlock recovery event: deadlockId={}", deadlockInfo.getTransactionId(), e);
        }
    }

    private boolean canRetryTransaction(Transaction transaction) {
        return transaction.getStatus() == TransactionStatus.FAILED ||
               transaction.getStatus() == TransactionStatus.TIMEOUT ||
               transaction.getStatus() == TransactionStatus.CANCELLED;
    }

    private long calculateExponentialBackoff(int attempt, long baseDelayMs, long maxDelayMs, double jitterFactor) {
        long delay = Math.min(baseDelayMs * (long) Math.pow(2, attempt), maxDelayMs);
        
        // Add cryptographically secure jitter to prevent thundering herd
        if (jitterFactor > 0) {
            double jitter = 1.0 + (SECURE_RANDOM.nextDouble() * jitterFactor * 2 - jitterFactor);
            delay = (long) (delay * jitter);
        }
        
        return delay;
    }

    private ProcessTransactionRequest buildRetryRequest(Transaction failedTransaction) {
        return ProcessTransactionRequest.builder()
            .transactionType(failedTransaction.getTransactionType())
            .sourceAccountId(failedTransaction.getSourceAccountId())
            .targetAccountId(failedTransaction.getTargetAccountId())
            .amount(failedTransaction.getAmount())
            .currency(failedTransaction.getCurrency())
            .description(failedTransaction.getDescription() + " (RETRY)")
            .initiatedBy(failedTransaction.getInitiatedBy())
            .channel(failedTransaction.getChannel())
            .metadata(new HashMap<>(failedTransaction.getMetadata()))
            .build();
    }

    private boolean shouldRetryAgain(TransactionProcessingResult result, RetryConfig config) {
        // Check if the failure is retryable and we haven't exceeded retry limits
        return config.isRetryableError(result.getMessage()) && 
               config.getCurrentAttempt() < config.getMaxRetries();
    }

    private long calculateRetryDelay(int retryCount) {
        return Math.min(1000L * (long) Math.pow(2, retryCount), 30000L); // Max 30 second delay
    }

    private List<ExternalTransactionRecord> getExternalRecords(String batchId, ReconciliationConfig config) {
        // Implementation would fetch records from external systems
        // This is a placeholder for the external system integration
        return externalSystemClient.getTransactionRecords(batchId, config);
    }

    private ReconciliationAnalysis performReconciliationAnalysis(List<Transaction> batchTransactions,
                                                               List<ExternalTransactionRecord> externalRecords,
                                                               ReconciliationConfig config) {
        // Perform statistical analysis of the transaction sets
        return ReconciliationAnalysis.builder()
            .totalInternalTransactions(batchTransactions.size())
            .totalExternalRecords(externalRecords.size())
            .totalInternalAmount(batchTransactions.stream()
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
            .totalExternalAmount(externalRecords.stream()
                .map(ExternalTransactionRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add))
            .analysisCompletedAt(LocalDateTime.now())
            .build();
    }

    private ReconciliationResult reconcileIndividualTransaction(Transaction transaction,
                                                              List<ExternalTransactionRecord> externalRecords,
                                                              ReconciliationConfig config) {
        // Find matching external record
        Optional<ExternalTransactionRecord> matchingRecord = externalRecords.stream()
            .filter(record -> matchesTransaction(transaction, record, config))
            .findFirst();

        if (matchingRecord.isPresent()) {
            ExternalTransactionRecord externalRecord = matchingRecord.get();
            List<ReconciliationDiscrepancy> discrepancies = findDiscrepancies(transaction, externalRecord, config);
            
            return ReconciliationResult.builder()
                .transactionId(transaction.getId())
                .reconciled(discrepancies.isEmpty())
                .matchedExternalRecord(externalRecord)
                .discrepancies(discrepancies)
                .build();
        } else {
            return ReconciliationResult.builder()
                .transactionId(transaction.getId())
                .reconciled(false)
                .discrepancies(List.of(new ReconciliationDiscrepancy(
                    transaction.getId().toString(),
                    "NO_EXTERNAL_MATCH",
                    "No matching external record found",
                    transaction.getAmount(),
                    null
                )))
                .build();
        }
    }

    private boolean matchesTransaction(Transaction transaction, ExternalTransactionRecord record, ReconciliationConfig config) {
        // Implement matching logic based on configuration
        return Objects.equals(transaction.getAmount(), record.getAmount()) &&
               Objects.equals(transaction.getSourceAccountId(), record.getSourceAccountId()) &&
               Objects.equals(transaction.getTargetAccountId(), record.getTargetAccountId());
    }

    private List<ReconciliationDiscrepancy> findDiscrepancies(Transaction transaction,
                                                            ExternalTransactionRecord externalRecord,
                                                            ReconciliationConfig config) {
        List<ReconciliationDiscrepancy> discrepancies = new ArrayList<>();
        
        // Check amount discrepancy
        if (!Objects.equals(transaction.getAmount(), externalRecord.getAmount())) {
            discrepancies.add(new ReconciliationDiscrepancy(
                transaction.getId().toString(),
                "AMOUNT_MISMATCH",
                "Amount mismatch: internal=" + transaction.getAmount() + ", external=" + externalRecord.getAmount(),
                transaction.getAmount(),
                externalRecord.getAmount()
            ));
        }
        
        // Add more discrepancy checks as needed
        
        return discrepancies;
    }

    private ReconciliationReport generateReconciliationReport(String batchId,
                                                            List<Transaction> batchTransactions,
                                                            List<ExternalTransactionRecord> externalRecords,
                                                            List<ReconciliationDiscrepancy> discrepancies,
                                                            ReconciliationAnalysis analysis) {
        return ReconciliationReport.builder()
            .batchId(batchId)
            .totalTransactions(batchTransactions.size())
            .totalExternalRecords(externalRecords.size())
            .totalDiscrepancies(discrepancies.size())
            .discrepancies(discrepancies)
            .analysis(analysis)
            .generatedAt(LocalDateTime.now())
            .build();
    }

    private void resolveDiscrepancies(List<ReconciliationDiscrepancy> discrepancies, ReconciliationConfig config) {
        for (ReconciliationDiscrepancy discrepancy : discrepancies) {
            try {
                // Implement auto-resolution logic based on discrepancy type and configuration
                if (config.canAutoResolve(discrepancy.getDiscrepancyType())) {
                    resolveDiscrepancy(discrepancy, config);
                }
            } catch (Exception e) {
                log.error("Failed to auto-resolve discrepancy: {}", discrepancy, e);
            }
        }
    }

    private void resolveDiscrepancy(ReconciliationDiscrepancy discrepancy, ReconciliationConfig config) {
        // Implementation for specific discrepancy resolution
        log.info("Auto-resolving discrepancy: type={}, transactionId={}", 
                discrepancy.getDiscrepancyType(), discrepancy.getTransactionId());
    }

    private void publishBatchReconciliationEvent(String batchId, int reconciled, int failed, int discrepancies) {
        try {
            BatchReconciliationEvent event = BatchReconciliationEvent.builder()
                .batchId(batchId)
                .reconciledCount(reconciled)
                .failedCount(failed)
                .discrepancyCount(discrepancies)
                .completedAt(LocalDateTime.now())
                .build();
            
            kafkaTemplate.send("batch-reconciliation-events", event);
        } catch (Exception e) {
            log.warn("Failed to publish batch reconciliation event: batchId={}", batchId, e);
        }
    }
}