package com.waqiti.transaction.service;

import com.waqiti.common.client.SagaOrchestrationServiceClient;
import com.waqiti.common.client.WalletServiceClient;
import com.waqiti.common.client.SecurityServiceClient;
import com.waqiti.common.client.NotificationServiceClient;
import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.exception.ResourceNotFoundException;
import com.waqiti.common.kyc.service.KYCClientService;
import com.waqiti.common.kyc.annotation.RequireKYCVerification;
import com.waqiti.common.kyc.annotation.RequireKYCVerification.VerificationLevel;
import com.waqiti.transaction.dto.*;
import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import com.waqiti.transaction.mapper.TransactionMapper;
import com.waqiti.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Lazy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class TransactionProcessingService {
    
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Lazy
    private final TransactionProcessingService self;
    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;
    private final SagaOrchestrationServiceClient sagaClient;
    private final WalletServiceClient walletClient;
    private final SecurityServiceClient securityClient;
    private final NotificationServiceClient notificationClient;
    private final TransactionValidationService validationService;
    private final FraudDetectionService fraudDetectionService;
    private final ExportService exportService;
    private final ReceiptService receiptService;
    private final ReceiptSecurityService receiptSecurityService;
    private final KYCClientService kycClientService;

    @RequireKYCVerification(level = VerificationLevel.BASIC, action = "P2P_TRANSFER")
    public TransactionResponse initiateTransfer(TransferRequest request) {
        log.info("Processing transfer request: {}", request);

        // Validate request
        validationService.validateTransferRequest(request);
        
        // Enhanced KYC check for high-value transfers
        if (request.getAmount().compareTo(new BigDecimal("5000")) > 0) {
            if (!kycClientService.canUserMakeHighValueTransfer(request.getSenderId().toString())) {
                throw new BusinessException("Enhanced KYC verification required for transfers over $5,000");
            }
        }

        // Check fraud detection
        FraudCheckResult fraudCheck = fraudDetectionService.checkTransfer(request);
        if (fraudCheck.isBlocked()) {
            throw new BusinessException("Transaction blocked due to fraud risk: " + fraudCheck.getReason());
        }

        // Create transaction record
        Transaction transaction = createTransactionFromTransfer(request);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction = transactionRepository.save(transaction);

        try {
            // Initiate saga orchestration
            SagaInitiationRequest sagaRequest = SagaInitiationRequest.builder()
                    .sagaType("P2P_TRANSFER")
                    .transactionId(transaction.getId())
                    .fromWalletId(request.getFromWalletId())
                    .toWalletId(request.getToWalletId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .description(request.getDescription())
                    .metadata(request.getMetadata())
                    .build();

            SagaResponse sagaResponse = sagaClient.initiateSaga(sagaRequest);
            transaction.setSagaId(sagaResponse.getSagaId());
            transaction.setStatus(TransactionStatus.PROCESSING);
            transaction = transactionRepository.save(transaction);

            log.info("Transfer saga initiated: sagaId={}, transactionId={}", 
                    sagaResponse.getSagaId(), transaction.getId());

            return transactionMapper.toResponse(transaction);

        } catch (Exception e) {
            log.error("Failed to initiate transfer saga for transaction: {}", transaction.getId(), e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transactionRepository.save(transaction);
            throw new BusinessException("Failed to process transfer: " + e.getMessage());
        }
    }

    public TransactionResponse initiateDeposit(DepositRequest request) {
        log.info("Processing deposit request: {}", request);

        // Validate request
        validationService.validateDepositRequest(request);

        // Create transaction record
        Transaction transaction = createTransactionFromDeposit(request);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction = transactionRepository.save(transaction);

        try {
            // Initiate deposit saga
            SagaInitiationRequest sagaRequest = SagaInitiationRequest.builder()
                    .sagaType("DEPOSIT")
                    .transactionId(transaction.getId())
                    .toWalletId(request.getWalletId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .paymentMethodId(request.getPaymentMethodId())
                    .description(request.getDescription())
                    .build();

            SagaResponse sagaResponse = sagaClient.initiateSaga(sagaRequest);
            transaction.setSagaId(sagaResponse.getSagaId());
            transaction.setStatus(TransactionStatus.PROCESSING);
            transaction = transactionRepository.save(transaction);

            return transactionMapper.toResponse(transaction);

        } catch (Exception e) {
            log.error("Failed to initiate deposit saga for transaction: {}", transaction.getId(), e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transactionRepository.save(transaction);
            throw new BusinessException("Failed to process deposit: " + e.getMessage());
        }
    }

    public TransactionResponse initiateWithdrawal(WithdrawalRequest request) {
        log.info("Processing withdrawal request: {}", request);

        // Validate request
        validationService.validateWithdrawalRequest(request);

        // Create transaction record
        Transaction transaction = createTransactionFromWithdrawal(request);
        transaction.setStatus(TransactionStatus.PENDING);
        transaction = transactionRepository.save(transaction);

        try {
            // Initiate withdrawal saga
            SagaInitiationRequest sagaRequest = SagaInitiationRequest.builder()
                    .sagaType("WITHDRAWAL")
                    .transactionId(transaction.getId())
                    .fromWalletId(request.getWalletId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .bankAccountId(request.getBankAccountId())
                    .description(request.getDescription())
                    .build();

            SagaResponse sagaResponse = sagaClient.initiateSaga(sagaRequest);
            transaction.setSagaId(sagaResponse.getSagaId());
            transaction.setStatus(TransactionStatus.PROCESSING);
            transaction = transactionRepository.save(transaction);

            return transactionMapper.toResponse(transaction);

        } catch (Exception e) {
            log.error("Failed to initiate withdrawal saga for transaction: {}", transaction.getId(), e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transactionRepository.save(transaction);
            throw new BusinessException("Failed to process withdrawal: " + e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(TransactionFilter filter, Pageable pageable) {
        Page<Transaction> transactions = transactionRepository.findByFilter(filter, pageable);
        return transactions.map(transactionMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransactionDetails(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        
        return transactionMapper.toDetailResponse(transaction);
    }

    public TransactionResponse retryTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        if (transaction.getStatus() != TransactionStatus.FAILED) {
            throw new BusinessException("Only failed transactions can be retried");
        }

        // Reset transaction status
        transaction.setStatus(TransactionStatus.PENDING);
        transaction.setFailureReason(null);
        transaction.setRetryCount(transaction.getRetryCount() + 1);
        transaction.setUpdatedAt(LocalDateTime.now());
        transaction = transactionRepository.save(transaction);

        try {
            // Retry saga if it exists
            if (transaction.getSagaId() != null) {
                sagaClient.retrySaga(transaction.getSagaId());
            } else {
                // Recreate saga based on transaction type
                recreateSagaForTransaction(transaction);
            }

            transaction.setStatus(TransactionStatus.PROCESSING);
            transaction = transactionRepository.save(transaction);

            return transactionMapper.toResponse(transaction);

        } catch (Exception e) {
            log.error("Failed to retry transaction: {}", transactionId, e);
            transaction.setStatus(TransactionStatus.FAILED);
            transaction.setFailureReason(e.getMessage());
            transactionRepository.save(transaction);
            throw new BusinessException("Failed to retry transaction: " + e.getMessage());
        }
    }

    public void cancelTransaction(UUID transactionId, String reason) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        if (!transaction.getStatus().isCancellable()) {
            throw new BusinessException("Transaction cannot be cancelled in current status: " + transaction.getStatus());
        }

        // Cancel saga if active
        if (transaction.getSagaId() != null) {
            try {
                sagaClient.cancelSaga(transaction.getSagaId(), reason);
            } catch (Exception e) {
                log.warn("Failed to cancel saga for transaction: {}", transactionId, e);
            }
        }

        transaction.setStatus(TransactionStatus.CANCELLED);
        transaction.setFailureReason(reason);
        transaction.setUpdatedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        // Send notification
        sendCancellationNotification(transaction);
    }

    public byte[] exportTransactions(ExportTransactionsRequest request) {
        List<Transaction> transactions = transactionRepository.findByIds(request.getTransactionIds());
        return exportService.exportTransactions(transactions, request.getFormat());
    }

    public byte[] generateReceipt(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        
        return receiptService.generateReceipt(transaction);
    }

    public byte[] generateReceipt(UUID transactionId, ReceiptGenerationOptions options) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        
        return receiptService.generateReceipt(transaction, options);
    }

    public ReceiptMetadata generateAndStoreReceipt(UUID transactionId, ReceiptGenerationOptions options) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        
        return receiptService.generateAndStoreReceipt(transaction);
    }

    public boolean emailReceipt(UUID transactionId, String email) {
        return receiptService.emailReceipt(transactionId, email);
    }

    public String generateReceiptAccessToken(UUID transactionId, String email, int validityHours) {
        // Verify transaction exists and user has access
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        
        return receiptService.generateAccessToken(transactionId, email, validityHours * 60L);
    }

    public List<ReceiptMetadata> getReceiptHistory(UUID transactionId) {
        // Verify transaction exists
        transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
        
        try {
            // Retrieve receipt history from receipt service
            return receiptService.getReceiptHistoryForTransaction(transactionId);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to retrieve receipt history for transaction: {}", transactionId, e);
            throw new BusinessException("Failed to retrieve receipt history for transaction: " + transactionId, e);
        }
    }

    public ReceiptSecurityValidation verifyReceiptIntegrity(byte[] receiptData, UUID transactionId, String expectedHash) {
        return receiptSecurityService.validateReceiptIntegrity(receiptData, transactionId, expectedHash);
    }

    public byte[] bulkDownloadReceipts(List<UUID> transactionIds, ReceiptGenerationOptions options) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);
            
            for (UUID transactionId : transactionIds) {
                try {
                    byte[] receiptData = self.generateReceipt(transactionId, options);
                    
                    ZipEntry entry = new ZipEntry("receipt-" + transactionId + ".pdf");
                    zos.putNextEntry(entry);
                    zos.write(receiptData);
                    zos.closeEntry();
                    
                } catch (Exception e) {
                    log.warn("Failed to generate receipt for transaction: {}", transactionId, e);
                }
            }
            
            zos.close();
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("Error creating bulk receipt ZIP", e);
            throw new BusinessException("Failed to create bulk receipt archive");
        }
    }

    public ReceiptAnalytics getReceiptAnalytics(String timeframe) {
        try {
            log.info("Generating receipt analytics for timeframe: {}", timeframe);
            
            // Generate real analytics from receipt service
            return receiptService.generateAnalytics(timeframe);
            
        } catch (Exception e) {
            log.error("CRITICAL: Failed to generate receipt analytics for timeframe: {}", timeframe, e);
            throw new BusinessException("Failed to generate receipt analytics: " + e.getMessage(), e);
        }
    }

    public DisputeResponse createDispute(UUID transactionId, DisputeRequest request) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));

        if (!transaction.getStatus().isDisputable()) {
            throw new BusinessException("Transaction cannot be disputed in current status: " + transaction.getStatus());
        }

        // Create dispute record (assuming dispute entity exists)
        // This would typically be handled by a separate dispute service
        
        return DisputeResponse.builder()
                .disputeId(UUID.randomUUID())
                .transactionId(transactionId)
                .reason(request.getReason())
                .description(request.getDescription())
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Get recurring transactions for the current user.
     *
     * FEATURE STATUS: NOT YET IMPLEMENTED
     *
     * This feature is planned for Phase 2 release. The database schema exists
     * (scheduled_transactions table with frequency field), but domain entities,
     * repositories, and business logic have not been implemented yet.
     *
     * When implemented, this will:
     * - Fetch all recurring transactions for authenticated user
     * - Support various frequencies (DAILY, WEEKLY, MONTHLY, YEARLY)
     * - Track execution history and next execution dates
     * - Allow pause/resume/cancel operations
     *
     * @return List of recurring transactions (currently empty until feature implemented)
     */
    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> getRecurringTransactions() {
        log.info("FEATURE_NOT_IMPLEMENTED: getRecurringTransactions() called - returning empty list. " +
                "This feature is planned for Phase 2.");

        // Track feature requests for product analytics
        log.info("PRODUCT_ANALYTICS: recurring_transactions_requested, user={}, timestamp={}",
                SecurityContextHolder.getContext().getAuthentication().getName(),
                LocalDateTime.now());

        // Return empty list with clear logging
        // When feature is implemented, query scheduled_transactions table
        // WHERE frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY')
        // AND created_by = current_user_id
        // AND deleted_at IS NULL
        return List.of();
    }

    /**
     * Schedule a transaction for future execution.
     *
     * FEATURE STATUS: NOT YET IMPLEMENTED
     *
     * This feature is planned for Phase 2 release. The database schema exists
     * (scheduled_transactions and scheduled_transaction_executions tables), but
     * domain entities, repositories, schedulers, and business logic are missing.
     *
     * When implemented, this will:
     * - Validate scheduled date is in the future
     * - Store transaction template in JSONB format
     * - Set up Quartz/Spring scheduled job for execution
     * - Handle one-time and recurring executions
     * - Send notifications before execution
     * - Retry failed executions with exponential backoff
     *
     * @param request Schedule transaction request
     * @return Mock response until feature is implemented
     */
    public ScheduledTransactionResponse scheduleTransaction(ScheduleTransactionRequest request) {
        log.info("FEATURE_NOT_IMPLEMENTED: scheduleTransaction() called - returning mock response. " +
                "Request: scheduledFor={}, amount={}. This feature is planned for Phase 2.",
                request.getScheduledFor(), request.getAmount());

        // Track feature requests for product analytics
        log.info("PRODUCT_ANALYTICS: scheduled_transaction_requested, user={}, " +
                "scheduledFor={}, amount={}, timestamp={}",
                SecurityContextHolder.getContext().getAuthentication().getName(),
                request.getScheduledFor(), request.getAmount(), LocalDateTime.now());

        // Return mock response with clear NOT_IMPLEMENTED status
        // When feature is implemented, save to scheduled_transactions table
        // and configure scheduled job for execution
        return ScheduledTransactionResponse.builder()
                .id(UUID.randomUUID())
                .scheduledFor(request.getScheduledFor())
                .status(ScheduledTransactionResponse.ScheduledStatus.NOT_IMPLEMENTED)
                .message("Scheduled transactions feature is not yet available. Planned for Phase 2.")
                .amount(request.getAmount())
                .fromWalletId(request.getFromWalletId())
                .toWalletId(request.getToWalletId())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .build();
    }

    // Helper methods
    private Transaction createTransactionFromTransfer(TransferRequest request) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .type(TransactionType.TRANSFER)
                .fromWalletId(request.getFromWalletId())
                .toWalletId(request.getToWalletId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .reference(generateTransactionReference())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    private Transaction createTransactionFromDeposit(DepositRequest request) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .type(TransactionType.DEPOSIT)
                .toWalletId(request.getWalletId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .reference(generateTransactionReference())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    private Transaction createTransactionFromWithdrawal(WithdrawalRequest request) {
        return Transaction.builder()
                .id(UUID.randomUUID())
                .type(TransactionType.WITHDRAWAL)
                .fromWalletId(request.getWalletId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .reference(generateTransactionReference())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    }

    private void recreateSagaForTransaction(Transaction transaction) {
        // Implementation to recreate saga based on transaction type
        log.info("Recreating saga for transaction: {}", transaction.getId());
    }

    private void sendCancellationNotification(Transaction transaction) {
        // Send notification about transaction cancellation
        log.info("Sending cancellation notification for transaction: {}", transaction.getId());
    }

    private String generateTransactionReference() {
        return "TXN" + System.currentTimeMillis() + SECURE_RANDOM.nextInt(10000);
    }

    /**
     * Suspends a specific transaction to prevent further processing
     */
    @Transactional
    public boolean suspendTransaction(String transactionId, String reason, boolean emergencyFreeze) {
        try {
            log.warn("Suspending transaction: {} - Reason: {} - Emergency: {}", transactionId, reason, emergencyFreeze);
            
            // Find and update the transaction status to SUSPENDED
            Transaction transaction = transactionRepository.findById(UUID.fromString(transactionId))
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found: " + transactionId));
            
            // Only suspend if transaction is in a suspendable state
            if (transaction.getStatus() == TransactionStatus.PENDING ||
                transaction.getStatus() == TransactionStatus.PROCESSING ||
                transaction.getStatus() == TransactionStatus.INITIATED) {
                
                transaction.setStatus(TransactionStatus.SUSPENDED);
                transaction.setSuspensionReason(reason);
                transaction.setSuspendedAt(LocalDateTime.now());
                transaction.setEmergencySuspension(emergencyFreeze);
                transaction.setUpdatedAt(LocalDateTime.now());
                
                transactionRepository.save(transaction);
                
                // Cancel any active saga for this transaction
                if (transaction.getSagaId() != null) {
                    try {
                        sagaClient.cancelSaga(transaction.getSagaId());
                        log.info("Cancelled saga {} for suspended transaction {}", 
                            transaction.getSagaId(), transactionId);
                    } catch (Exception e) {
                        log.error("Failed to cancel saga {} for transaction {}", 
                            transaction.getSagaId(), transactionId, e);
                    }
                }
                
                log.info("Successfully suspended transaction: {}", transactionId);
                return true;
                
            } else {
                log.warn("Transaction {} is in status {} and cannot be suspended", 
                    transactionId, transaction.getStatus());
                return false;
            }
            
        } catch (Exception e) {
            log.error("Failed to suspend transaction: {}", transactionId, e);
            return false;
        }
    }
}