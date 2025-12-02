package com.waqiti.corebanking.service;

import com.waqiti.corebanking.dto.*;
import com.waqiti.corebanking.domain.Transaction;
import com.waqiti.corebanking.domain.Account;
import com.waqiti.corebanking.repository.AccountRepository;
import com.waqiti.corebanking.repository.TransactionRepository;
import com.waqiti.common.client.LedgerServiceClient;
import com.waqiti.corebanking.service.ComplianceIntegrationService;
import com.waqiti.common.client.NotificationServiceClient;
import com.waqiti.common.tracing.Traced;
import com.waqiti.corebanking.exception.ComplianceValidationException;
import com.waqiti.corebanking.exception.AccountResolutionException;
import com.waqiti.corebanking.exception.TransactionReversalException;
import com.waqiti.corebanking.exception.TransactionStatusUpdateException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import org.springframework.data.domain.PageImpl;
import com.waqiti.corebanking.exception.TransactionNotFoundException;
import com.waqiti.corebanking.exception.TransactionRetrievalException;
import com.waqiti.corebanking.exception.TransactionSearchException;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
@Validated
public class TransactionProcessingService {

    private final AccountManagementService accountManagementService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerServiceClient ledgerServiceClient;
    private final ComplianceIntegrationService complianceIntegrationService;
    private final NotificationServiceClient notificationServiceClient;
    private final MetricsService metricsService;

    /**
     * Processes a money transfer between accounts
     */
    @Traced(operationName = "process-transfer", businessOperation = "money-transfer", priority = Traced.TracingPriority.CRITICAL)
    public CompletableFuture<TransferResponseDto> processTransferAsync(@Valid @NotNull TransferRequestDto request) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.nanoTime();
            log.info("Processing transfer: {} {} from {} to {} (txn: {})",
                    request.getAmount(), request.getCurrency(),
                    request.getFromAccountId(), request.getToAccountId(), request.getTransactionId());

            try {
                // Validate transfer request
                validateTransferRequest(request);

                // Create temporary transaction object for compliance checking
                Transaction tempTransaction = createTransactionForCompliance(request);

                // Perform comprehensive compliance check
                long complianceStartTime = System.nanoTime();
                ComplianceIntegrationService.ComplianceCheckResult complianceResult =
                    complianceIntegrationService.performTransactionComplianceCheck(tempTransaction);
                java.time.Duration complianceDuration = java.time.Duration.ofNanos(System.nanoTime() - complianceStartTime);

                // Record compliance check metrics
                metricsService.recordComplianceCheck(
                    "P2P_TRANSFER",
                    complianceResult.isApproved() ? "APPROVED" : "REJECTED",
                    complianceDuration
                );

                if (!complianceResult.isApproved()) {
                    String errorMsg = "Transfer failed compliance validation: " +
                        String.join(", ", complianceResult.getAlerts());
                    log.warn("Compliance check failed for transaction {}: {}",
                        request.getTransactionId(), errorMsg);
                    throw new ComplianceValidationException(errorMsg, request.getTransactionId(), complianceResult.getAlerts().toString());
                }

                if (complianceResult.isRequiresManualReview()) {
                    log.info("Transaction {} requires manual review: {}",
                        request.getTransactionId(), complianceResult.getAlerts());
                    metricsService.recordComplianceManualReview("P2P_TRANSFER", complianceResult.getAlerts().toString());
                    // Continue processing but flag for review
                }

                // Reserve funds from source account
                String reservationId = "res-" + request.getTransactionId();
                reserveFundsForTransfer(request.getFromAccountId(), request.getAmount(),
                                      request.getCurrency(), reservationId, request.getTransactionId());
                metricsService.recordFundReservationCreated("P2P_TRANSFER", request.getAmount());

                // Execute the transfer
                executeTransfer(request, reservationId);

                // Send notifications
                sendTransferNotifications(request);

                // Record successful transaction metrics
                java.time.Duration duration = java.time.Duration.ofNanos(System.nanoTime() - startTime);
                metricsService.recordTransaction(
                    Transaction.TransactionType.P2P_TRANSFER,
                    Transaction.TransactionStatus.COMPLETED,
                    duration
                );

                TransferResponseDto response = TransferResponseDto.builder()
                    .transactionId(request.getTransactionId())
                    .status("COMPLETED")
                    .fromAccountId(request.getFromAccountId())
                    .toAccountId(request.getToAccountId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .description(request.getDescription())
                    .processedAt(Instant.now())
                    .build();

                log.info("Transfer completed successfully: {} in {}ms",
                    request.getTransactionId(), duration.toMillis());
                return response;

            } catch (Exception e) {
                log.error("Transfer failed: {}", request.getTransactionId(), e);

                // Record failed transaction metrics
                java.time.Duration duration = java.time.Duration.ofNanos(System.nanoTime() - startTime);
                metricsService.recordTransaction(
                    Transaction.TransactionType.P2P_TRANSFER,
                    Transaction.TransactionStatus.FAILED,
                    duration
                );

                TransferResponseDto response = TransferResponseDto.builder()
                    .transactionId(request.getTransactionId())
                    .status("FAILED")
                    .fromAccountId(request.getFromAccountId())
                    .toAccountId(request.getToAccountId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .description(request.getDescription())
                    .errorMessage(e.getMessage())
                    .processedAt(Instant.now())
                    .build();

                return response;
            }
        });
    }

    /**
     * Processes a payment transaction
     */
    @Traced(operationName = "process-payment", businessOperation = "payment-processing", priority = Traced.TracingPriority.CRITICAL)
    public CompletableFuture<PaymentResponseDto> processPaymentAsync(PaymentRequestDto request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Processing payment: {} {} from user {} (txn: {})", 
                    request.getAmount(), request.getCurrency(), 
                    request.getFromUserId(), request.getTransactionId());

            try {
                // Validate payment request
                validatePaymentRequest(request);

                // Get user's primary account
                String fromAccountId = getPrimaryAccountForUser(request.getFromUserId(), request.getCurrency());
                String toAccountId = getPrimaryAccountForUser(request.getToUserId(), request.getCurrency());

                // Check compliance
                boolean complianceApproved = checkPaymentCompliance(request);
                if (!complianceApproved) {
                    throw new ComplianceValidationException("Payment failed compliance validation", request.getTransactionId(), "User validation failed");
                }

                // Process as internal transfer
                TransferRequestDto transferRequest = TransferRequestDto.builder()
                    .transactionId(request.getTransactionId())
                    .fromAccountId(fromAccountId)
                    .toAccountId(toAccountId)
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .description(request.getDescription() != null ? 
                               request.getDescription() : "P2P Payment")
                    .metadata(request.getMetadata())
                    .build();

                TransferResponseDto transferResponse = processTransferAsync(transferRequest).get();

                PaymentResponseDto response = PaymentResponseDto.builder()
                    .transactionId(request.getTransactionId())
                    .status(transferResponse.getStatus())
                    .fromUserId(request.getFromUserId())
                    .toUserId(request.getToUserId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .description(request.getDescription())
                    .processedAt(Instant.now())
                    .errorMessage(transferResponse.getErrorMessage())
                    .build();

                log.info("Payment processed: {} (status: {})", 
                        request.getTransactionId(), response.getStatus());

                return response;

            } catch (Exception e) {
                log.error("Payment failed: {}", request.getTransactionId(), e);

                PaymentResponseDto response = PaymentResponseDto.builder()
                    .transactionId(request.getTransactionId())
                    .status("FAILED")
                    .fromUserId(request.getFromUserId())
                    .toUserId(request.getToUserId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .description(request.getDescription())
                    .errorMessage(e.getMessage())
                    .processedAt(Instant.now())
                    .build();

                return response;
            }
        });
    }

    /**
     * Retrieves transaction by ID
     */
    @Traced(operationName = "get-transaction", businessOperation = "transaction-inquiry", priority = Traced.TracingPriority.MEDIUM)
    @Transactional(readOnly = true)
    public TransactionResponseDto getTransaction(String transactionId) {
        log.debug("Retrieving transaction: {}", transactionId);

        try {
            // Parse transaction ID - support both UUID and transaction number formats
            Transaction transaction = null;
            
            // Try to find by UUID first
            try {
                UUID transactionUuid = UUID.fromString(transactionId);
                transaction = transactionRepository.findById(transactionUuid)
                    .orElse(null);
            } catch (IllegalArgumentException e) {
                // Not a UUID, try transaction number
                transaction = transactionRepository.findByTransactionNumber(transactionId)
                    .orElse(null);
            }
            
            if (transaction == null) {
                log.warn("Transaction not found: {}", transactionId);
                throw new TransactionNotFoundException("Transaction not found: " + transactionId, transactionId);
            }
            
            // Build comprehensive response
            TransactionResponseDto response = convertToTransactionResponseDto(transaction);
            
            log.debug("Successfully retrieved transaction: {} (status: {})", 
                transactionId, transaction.getStatus());
            
            return response;
            
        } catch (TransactionNotFoundException e) {
            throw e; // Re-throw as-is
        } catch (Exception e) {
            log.error("Failed to retrieve transaction: {}", transactionId, e);
            throw new TransactionRetrievalException("Failed to retrieve transaction: " + transactionId, transactionId, e);
        }
    }

    /**
     * Searches transactions with criteria
     */
    @Traced(operationName = "search-transactions", businessOperation = "transaction-search", priority = Traced.TracingPriority.MEDIUM)
    @Transactional(readOnly = true)
    public Page<TransactionResponseDto> searchTransactions(TransactionSearchCriteria criteria, Pageable pageable) {
        log.debug("Searching transactions with criteria: {}", criteria);
        
        try {
            Page<Transaction> transactions;
            
            // Execute search based on criteria
            if (criteria.getAccountId() != null) {
                UUID accountId = UUID.fromString(criteria.getAccountId());
                
                if (criteria.getStartDate() != null && criteria.getEndDate() != null) {
                    LocalDateTime startDate = criteria.getStartDate().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    LocalDateTime endDate = criteria.getEndDate().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    
                    List<Transaction> transactionList = transactionRepository.findByAccountIdAndDateRange(
                        accountId, startDate, endDate);
                    
                    // Apply additional filters
                    transactionList = applySearchFilters(transactionList, criteria);
                    
                    // Convert to page
                    int start = (int) pageable.getOffset();
                    int end = Math.min((start + pageable.getPageSize()), transactionList.size());
                    List<Transaction> pageContent = transactionList.subList(start, end);
                    
                    transactions = new PageImpl<>(pageContent, pageable, transactionList.size());
                } else {
                    transactions = transactionRepository.findByAccountId(accountId, pageable);
                }
            } else if (criteria.getUserId() != null) {
                UUID userId = UUID.fromString(criteria.getUserId());
                transactions = transactionRepository.findUserTransactions(userId, pageable);
            } else if (criteria.getStatus() != null) {
                List<Transaction> allTransactions = transactionRepository.findByStatus(
                    Transaction.TransactionStatus.valueOf(criteria.getStatus()));
                
                // Apply additional filters and pagination
                allTransactions = applySearchFilters(allTransactions, criteria);
                
                int start = (int) pageable.getOffset();
                int end = Math.min((start + pageable.getPageSize()), allTransactions.size());
                List<Transaction> pageContent = allTransactions.subList(start, end);
                
                transactions = new PageImpl<>(pageContent, pageable, allTransactions.size());
            } else {
                // General search with date range
                if (criteria.getStartDate() != null && criteria.getEndDate() != null) {
                    LocalDateTime startDate = criteria.getStartDate().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    LocalDateTime endDate = criteria.getEndDate().atZone(ZoneId.systemDefault()).toLocalDateTime();
                    
                    List<Transaction> allTransactions = transactionRepository.findByDateRange(startDate, endDate);
                    allTransactions = applySearchFilters(allTransactions, criteria);
                    
                    int start = (int) pageable.getOffset();
                    int end = Math.min((start + pageable.getPageSize()), allTransactions.size());
                    List<Transaction> pageContent = allTransactions.subList(start, end);
                    
                    transactions = new PageImpl<>(pageContent, pageable, allTransactions.size());
                } else {
                    // Return recent transactions
                    transactions = transactionRepository.findAll(pageable);
                }
            }
            
            // Convert to DTOs
            Page<TransactionResponseDto> response = transactions.map(this::convertToTransactionResponseDto);
            
            log.debug("Found {} transactions matching criteria", response.getTotalElements());
            return response;
            
        } catch (Exception e) {
            log.error("Failed to search transactions with criteria: {}", criteria, e);
            throw new TransactionSearchException("Failed to search transactions", criteria, e);
        }
    }

    /**
     * Reverses a completed transaction
     * Creates compensating ledger entries and restores account balances
     */
    @Traced(operationName = "reverse-transaction", businessOperation = "transaction-reversal", priority = Traced.TracingPriority.HIGH)
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.SERIALIZABLE, rollbackFor = {Exception.class}, timeout = 60)
    public TransactionResponseDto reverseTransaction(String transactionId, TransactionReversalRequestDto request) {
        log.info("Reversing transaction: {} (reason: {})", transactionId, request.getReason());

        try {
            // 1. Retrieve and validate original transaction
            Transaction originalTxn = transactionRepository.findById(UUID.fromString(transactionId))
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

            // 2. Validate transaction can be reversed
            if (originalTxn.getStatus() != Transaction.TransactionStatus.COMPLETED) {
                throw new IllegalStateException("Only completed transactions can be reversed. Current status: " + originalTxn.getStatus());
            }

            if (originalTxn.getReversalTransactionId() != null) {
                throw new IllegalStateException("Transaction already reversed. Reversal ID: " + originalTxn.getReversalTransactionId());
            }

            // Validate reversal is allowed within time window (e.g., 90 days)
            if (originalTxn.getCompletedAt() != null &&
                originalTxn.getCompletedAt().plusDays(90).isBefore(LocalDateTime.now())) {
                throw new IllegalStateException("Transaction reversal window expired (90 days)");
            }

            log.info("Validated original transaction for reversal: {} (type: {}, amount: {} {})",
                originalTxn.getId(), originalTxn.getTransactionType(), originalTxn.getAmount(), originalTxn.getCurrency());

            // 3. Create reversal transaction with opposite flow
            Transaction reversalTxn = Transaction.builder()
                .transactionNumber("REV-" + originalTxn.getTransactionNumber())
                .transactionType(Transaction.TransactionType.REVERSAL)
                .originalTransactionId(originalTxn.getId())
                // Reverse the direction: source becomes target, target becomes source
                .sourceAccountId(originalTxn.getTargetAccountId())
                .targetAccountId(originalTxn.getSourceAccountId())
                .amount(originalTxn.getAmount())
                .currency(originalTxn.getCurrency())
                .description("Reversal: " + request.getReason())
                .reference("REV-" + originalTxn.getReference())
                .status(Transaction.TransactionStatus.PROCESSING)
                .priority(Transaction.TransactionPriority.HIGH)
                .initiatedBy(request.getInitiatedBy() != null ? request.getInitiatedBy() : originalTxn.getInitiatedBy())
                .transactionDate(LocalDateTime.now())
                .valueDate(LocalDateTime.now())
                .idempotencyKey("reversal-" + originalTxn.getId() + "-" + System.currentTimeMillis())
                .metadata("{\"reversalReason\":\"" + request.getReason() + "\",\"initiatedBy\":\"" + request.getInitiatedBy() + "\"}")
                .build();

            reversalTxn = transactionRepository.save(reversalTxn);
            log.info("Created reversal transaction: {} for original transaction: {}", reversalTxn.getId(), originalTxn.getId());

            // 4. Reverse account balances
            try {
                // Debit the target account (which received money in original transaction)
                if (originalTxn.getTargetAccountId() != null) {
                    AccountDebitRequestDto debitRequest = AccountDebitRequestDto.builder()
                        .transactionId(reversalTxn.getId().toString())
                        .amount(originalTxn.getAmount())
                        .currency(originalTxn.getCurrency())
                        .description("Reversal debit: " + request.getReason())
                        .allowOverdraft(false) // Reversals should not allow overdraft
                        .build();

                    accountManagementService.debitAccount(originalTxn.getTargetAccountId().toString(), debitRequest);
                    log.info("Debited target account {} for reversal: {} {}",
                        originalTxn.getTargetAccountId(), originalTxn.getAmount(), originalTxn.getCurrency());
                }

                // Credit the source account (which sent money in original transaction)
                if (originalTxn.getSourceAccountId() != null) {
                    AccountCreditRequestDto creditRequest = AccountCreditRequestDto.builder()
                        .transactionId(reversalTxn.getId().toString())
                        .amount(originalTxn.getAmount())
                        .currency(originalTxn.getCurrency())
                        .description("Reversal credit: " + request.getReason())
                        .build();

                    accountManagementService.creditAccount(originalTxn.getSourceAccountId().toString(), creditRequest);
                    log.info("Credited source account {} for reversal: {} {}",
                        originalTxn.getSourceAccountId(), originalTxn.getAmount(), originalTxn.getCurrency());
                }
            } catch (Exception e) {
                log.error("Failed to reverse account balances, marking reversal as failed", e);
                reversalTxn.setStatus(Transaction.TransactionStatus.FAILED);
                reversalTxn.setFailureReason("Balance reversal failed: " + e.getMessage());
                reversalTxn.setFailedAt(LocalDateTime.now());
                transactionRepository.save(reversalTxn);
                throw new TransactionReversalException("Failed to reverse account balances", transactionId, request.getReason(), e);
            }

            // 5. Create compensating ledger entries
            try {
                ledgerServiceClient.postReversalTransaction(
                    reversalTxn.getId().toString(),
                    originalTxn.getId().toString(),
                    reversalTxn.getSourceAccountId().toString(),
                    reversalTxn.getTargetAccountId().toString(),
                    reversalTxn.getAmount(),
                    reversalTxn.getCurrency(),
                    "Reversal: " + request.getReason()
                );
                log.info("Created compensating ledger entries for reversal: {}", reversalTxn.getId());
            } catch (Exception e) {
                log.warn("Failed to post ledger entries for reversal, continuing (ledger will be reconciled): {}", e.getMessage());
                // Don't fail the reversal if ledger posting fails - reconciliation will catch it
            }

            // 6. Update both transactions
            originalTxn.setReversalTransactionId(reversalTxn.getId());
            originalTxn.setStatus(Transaction.TransactionStatus.REVERSED);
            originalTxn.setModifiedBy(request.getInitiatedBy() != null ? request.getInitiatedBy().toString() : "SYSTEM");
            originalTxn = transactionRepository.save(originalTxn);

            reversalTxn.setStatus(Transaction.TransactionStatus.COMPLETED);
            reversalTxn.setCompletedAt(LocalDateTime.now());
            reversalTxn = transactionRepository.save(reversalTxn);

            log.info("Updated transaction statuses: original={} REVERSED, reversal={} COMPLETED",
                originalTxn.getId(), reversalTxn.getId());

            // 7. Send notifications
            try {
                sendReversalNotifications(originalTxn, reversalTxn, request.getReason());
            } catch (Exception e) {
                log.warn("Failed to send reversal notifications: {}", e.getMessage());
                // Don't fail the reversal if notifications fail
            }

            // 8. Convert to DTO and return
            TransactionResponseDto response = convertToTransactionResponseDto(reversalTxn);

            log.info("Transaction reversal completed successfully: original={}, reversal={}, amount={} {}",
                transactionId, reversalTxn.getId(), originalTxn.getAmount(), originalTxn.getCurrency());

            return response;

        } catch (TransactionNotFoundException | IllegalStateException e) {
            log.error("Transaction reversal validation failed: {}", transactionId, e);
            throw e;
        } catch (TransactionReversalException e) {
            log.error("Transaction reversal failed: {}", transactionId, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during transaction reversal: {}", transactionId, e);
            throw new TransactionReversalException("Transaction reversal failed due to unexpected error", transactionId, request.getReason(), e);
        }
    }

    /**
     * Send notifications for transaction reversal
     */
    private void sendReversalNotifications(Transaction originalTxn, Transaction reversalTxn, String reason) {
        try {
            // Notify source account holder (money returned)
            if (originalTxn.getSourceAccountId() != null) {
                notificationServiceClient.sendEmail(
                    getUserEmailForAccount(originalTxn.getSourceAccountId()),
                    "Transaction Reversed - Funds Returned",
                    String.format("Transaction %s has been reversed. Amount %s %s has been returned to your account. Reason: %s",
                        originalTxn.getTransactionNumber(), originalTxn.getAmount(), originalTxn.getCurrency(), reason),
                    "transaction-reversal"
                );
            }

            // Notify target account holder (money deducted)
            if (originalTxn.getTargetAccountId() != null && !originalTxn.getTargetAccountId().equals(originalTxn.getSourceAccountId())) {
                notificationServiceClient.sendEmail(
                    getUserEmailForAccount(originalTxn.getTargetAccountId()),
                    "Transaction Reversed - Funds Deducted",
                    String.format("Transaction %s has been reversed. Amount %s %s has been deducted from your account. Reason: %s",
                        originalTxn.getTransactionNumber(), originalTxn.getAmount(), originalTxn.getCurrency(), reason),
                    "transaction-reversal"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send reversal notifications for transaction {}: {}", originalTxn.getId(), e.getMessage());
        }
    }

    /**
     * Get user email for account (placeholder - should be implemented)
     */
    private String getUserEmailForAccount(UUID accountId) {
        // TODO: Implement actual user email retrieval from account/user service
        return "user@example.com";
    }

    /**
     * Updates transaction status
     * Validates status transitions and persists changes to database
     */
    @Traced(operationName = "update-transaction-status", businessOperation = "transaction-management", priority = Traced.TracingPriority.HIGH)
    @Transactional(rollbackFor = {Exception.class})
    public TransactionResponseDto updateTransactionStatus(String transactionId, TransactionStatusUpdateDto request) {
        log.info("Updating transaction status: {} -> {}", transactionId, request.getStatus());

        try {
            // 1. Fetch real transaction from database
            Transaction transaction = transactionRepository.findById(UUID.fromString(transactionId))
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + transactionId));

            // 2. Parse and validate new status
            Transaction.TransactionStatus newStatus;
            try {
                newStatus = Transaction.TransactionStatus.valueOf(request.getStatus());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid transaction status: " + request.getStatus());
            }

            // 3. Validate status transition
            Transaction.TransactionStatus oldStatus = transaction.getStatus();
            validateStatusTransition(oldStatus, newStatus);

            log.info("Validated status transition for transaction {}: {} -> {}",
                transactionId, oldStatus, newStatus);

            // 4. Update transaction status
            transaction.setStatus(newStatus);
            transaction.setModifiedBy(request.getUpdatedBy() != null ? request.getUpdatedBy().toString() : "SYSTEM");

            // 5. Update status-specific fields
            LocalDateTime now = LocalDateTime.now();
            switch (newStatus) {
                case AUTHORIZED:
                    transaction.setAuthorizedAt(now);
                    transaction.setApprovedBy(request.getUpdatedBy());
                    break;
                case COMPLETED:
                    transaction.setCompletedAt(now);
                    break;
                case FAILED:
                    transaction.setFailedAt(now);
                    transaction.setFailureReason(request.getReason() != null ? request.getReason() : "Status manually set to FAILED");
                    break;
                case CANCELLED:
                    transaction.setFailureReason(request.getReason() != null ? request.getReason() : "Transaction cancelled");
                    break;
                default:
                    // No specific field updates for other statuses
                    break;
            }

            // 6. Save to database
            transaction = transactionRepository.save(transaction);

            log.info("Persisted transaction status update: {} changed from {} to {}",
                transactionId, oldStatus, newStatus);

            // 7. Handle status-specific side effects
            handleStatusChange(transaction, oldStatus, newStatus, request.getReason());

            // 8. Convert to DTO and return
            TransactionResponseDto response = convertToTransactionResponseDto(transaction);

            log.info("Transaction status updated successfully: {} -> {} (reason: {})",
                transactionId, newStatus, request.getReason());

            return response;

        } catch (TransactionNotFoundException | IllegalArgumentException | IllegalStateException e) {
            log.error("Transaction status update validation failed: {}", transactionId, e);
            throw e;
        } catch (Exception e) {
            log.error("Failed to update transaction status: {}", transactionId, e);
            throw new TransactionStatusUpdateException("Transaction status update failed", transactionId, request.getStatus(), e);
        }
    }

    /**
     * Validates if a status transition is allowed
     * Prevents invalid state transitions
     */
    private void validateStatusTransition(Transaction.TransactionStatus from, Transaction.TransactionStatus to) {
        // Same status is always allowed (idempotent)
        if (from == to) {
            return;
        }

        // Define valid transitions
        boolean isValid = switch (from) {
            case PENDING -> to == Transaction.TransactionStatus.AUTHORIZED ||
                           to == Transaction.TransactionStatus.PROCESSING ||
                           to == Transaction.TransactionStatus.FAILED ||
                           to == Transaction.TransactionStatus.CANCELLED ||
                           to == Transaction.TransactionStatus.REQUIRES_APPROVAL ||
                           to == Transaction.TransactionStatus.COMPLIANCE_HOLD;

            case AUTHORIZED -> to == Transaction.TransactionStatus.PROCESSING ||
                              to == Transaction.TransactionStatus.FAILED ||
                              to == Transaction.TransactionStatus.CANCELLED;

            case PROCESSING -> to == Transaction.TransactionStatus.COMPLETED ||
                              to == Transaction.TransactionStatus.FAILED ||
                              to == Transaction.TransactionStatus.PARTIALLY_COMPLETED;

            case REQUIRES_APPROVAL -> to == Transaction.TransactionStatus.AUTHORIZED ||
                                     to == Transaction.TransactionStatus.CANCELLED;

            case COMPLIANCE_HOLD -> to == Transaction.TransactionStatus.AUTHORIZED ||
                                   to == Transaction.TransactionStatus.CANCELLED ||
                                   to == Transaction.TransactionStatus.FAILED;

            case PARTIALLY_COMPLETED -> to == Transaction.TransactionStatus.COMPLETED ||
                                       to == Transaction.TransactionStatus.FAILED;

            // Terminal states cannot transition to other states (except COMPLETED can be REVERSED)
            case COMPLETED -> to == Transaction.TransactionStatus.REVERSED;
            case FAILED, CANCELLED, REVERSED -> false;
        };

        if (!isValid) {
            throw new IllegalStateException(
                String.format("Invalid status transition: %s -> %s", from, to));
        }
    }

    /**
     * Handle side effects of status changes
     */
    private void handleStatusChange(Transaction transaction, Transaction.TransactionStatus oldStatus,
                                    Transaction.TransactionStatus newStatus, String reason) {
        try {
            // Handle FAILED status
            if (newStatus == Transaction.TransactionStatus.FAILED) {
                // Release any reserved funds if transaction failed
                if (transaction.getSourceAccountId() != null) {
                    String reservationId = "res-" + transaction.getId();
                    try {
                        ledgerServiceClient.releaseFunds(
                            transaction.getSourceAccountId().toString(),
                            reservationId
                        );
                        log.info("Released reserved funds for failed transaction: {}", transaction.getId());
                    } catch (Exception e) {
                        log.warn("Failed to release funds for failed transaction {}: {}",
                            transaction.getId(), e.getMessage());
                    }
                }

                // Send failure notification
                sendTransactionFailureNotification(transaction, reason);
            }

            // Handle COMPLETED status
            if (newStatus == Transaction.TransactionStatus.COMPLETED && oldStatus != Transaction.TransactionStatus.COMPLETED) {
                sendTransactionCompletedNotification(transaction);
            }

            // Handle CANCELLED status
            if (newStatus == Transaction.TransactionStatus.CANCELLED) {
                sendTransactionCancelledNotification(transaction, reason);
            }

        } catch (Exception e) {
            log.warn("Failed to handle status change side effects for transaction {}: {}",
                transaction.getId(), e.getMessage());
            // Don't fail the status update if side effects fail
        }
    }

    /**
     * Send transaction failure notification
     */
    private void sendTransactionFailureNotification(Transaction transaction, String reason) {
        try {
            if (transaction.getInitiatedBy() != null) {
                notificationServiceClient.sendEmail(
                    getUserEmailForAccount(transaction.getSourceAccountId()),
                    "Transaction Failed",
                    String.format("Transaction %s has failed. Amount: %s %s. Reason: %s",
                        transaction.getTransactionNumber(), transaction.getAmount(),
                        transaction.getCurrency(), reason != null ? reason : "Unknown"),
                    "transaction-failed"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send transaction failure notification: {}", e.getMessage());
        }
    }

    /**
     * Send transaction completed notification
     */
    private void sendTransactionCompletedNotification(Transaction transaction) {
        try {
            if (transaction.getInitiatedBy() != null) {
                notificationServiceClient.sendEmail(
                    getUserEmailForAccount(transaction.getSourceAccountId()),
                    "Transaction Completed",
                    String.format("Transaction %s has been completed successfully. Amount: %s %s",
                        transaction.getTransactionNumber(), transaction.getAmount(), transaction.getCurrency()),
                    "transaction-completed"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send transaction completed notification: {}", e.getMessage());
        }
    }

    /**
     * Send transaction cancelled notification
     */
    private void sendTransactionCancelledNotification(Transaction transaction, String reason) {
        try {
            if (transaction.getInitiatedBy() != null) {
                notificationServiceClient.sendEmail(
                    getUserEmailForAccount(transaction.getSourceAccountId()),
                    "Transaction Cancelled",
                    String.format("Transaction %s has been cancelled. Amount: %s %s. Reason: %s",
                        transaction.getTransactionNumber(), transaction.getAmount(),
                        transaction.getCurrency(), reason != null ? reason : "Not specified"),
                    "transaction-cancelled"
                );
            }
        } catch (Exception e) {
            log.warn("Failed to send transaction cancelled notification: {}", e.getMessage());
        }
    }

    /**
     * Processes bulk transactions
     */
    @Traced(operationName = "process-bulk-transactions", businessOperation = "bulk-processing", priority = Traced.TracingPriority.HIGH)
    public CompletableFuture<BulkTransactionResponseDto> processBulkTransactionsAsync(BulkTransactionRequestDto request) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Processing bulk transactions: {} items (batch: {})", 
                    request.getTransactions().size(), request.getBatchId());

            int processedCount = 0;
            int successCount = 0;
            int failureCount = 0;

            for (TransferRequestDto transferRequest : request.getTransactions()) {
                try {
                    TransferResponseDto response = processTransferAsync(transferRequest).get();
                    processedCount++;
                    
                    if ("COMPLETED".equals(response.getStatus())) {
                        successCount++;
                    } else {
                        failureCount++;
                    }
                } catch (Exception e) {
                    log.error("Failed to process transaction in bulk: {}", transferRequest.getTransactionId(), e);
                    processedCount++;
                    failureCount++;
                }
            }

            BulkTransactionResponseDto response = BulkTransactionResponseDto.builder()
                .batchId(request.getBatchId())
                .totalCount(request.getTransactions().size())
                .processedCount(processedCount)
                .successCount(successCount)
                .failureCount(failureCount)
                .status(failureCount == 0 ? "COMPLETED" : "PARTIAL_SUCCESS")
                .processedAt(Instant.now())
                .build();

            log.info("Bulk transaction processing completed: {} (processed: {}/{}, success: {}, failures: {})", 
                    request.getBatchId(), processedCount, request.getTransactions().size(), 
                    successCount, failureCount);

            return response;
        });
    }

    // Helper methods

    private void validateTransferRequest(TransferRequestDto request) {
        if (request.getFromAccountId() == null || request.getFromAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("From account ID is required");
        }
        if (request.getToAccountId() == null || request.getToAccountId().trim().isEmpty()) {
            throw new IllegalArgumentException("To account ID is required");
        }
        if (request.getFromAccountId().equals(request.getToAccountId())) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
    }

    private void validatePaymentRequest(PaymentRequestDto request) {
        if (request.getFromUserId() == null || request.getFromUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("From user ID is required");
        }
        if (request.getToUserId() == null || request.getToUserId().trim().isEmpty()) {
            throw new IllegalArgumentException("To user ID is required");
        }
        if (request.getFromUserId().equals(request.getToUserId())) {
            throw new IllegalArgumentException("Cannot send payment to yourself");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Payment amount must be positive");
        }
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new IllegalArgumentException("Currency is required");
        }
    }

    private String getUserIdForAccount(String accountId) {
        // In a real implementation, this would query the account to get the user ID
        return "user-" + accountId.substring(0, 8);
    }

    private String getPrimaryAccountForUser(String userId, String currency) {
        // In a real implementation, this would query the user's accounts to find the primary account for the currency
        List<AccountResponseDto> accounts = accountManagementService.getUserAccounts(userId);
        
        return accounts.stream()
            .filter(account -> currency.equals(account.getCurrency()))
            .filter(account -> "USER_WALLET".equals(account.getAccountType()))
            .filter(account -> "ACTIVE".equals(account.getStatus()))
            .findFirst()
            .map(AccountResponseDto::getAccountId)
            .orElseThrow(() -> new AccountResolutionException("No active wallet account found for user " + userId + " in currency " + currency, userId, currency));
    }

    private void reserveFundsForTransfer(String accountId, BigDecimal amount, String currency, 
                                       String reservationId, String transactionId) {
        FundReservationRequestDto reservationRequest = FundReservationRequestDto.builder()
            .reservationId(reservationId)
            .amount(amount)
            .currency(currency)
            .purpose("Transfer reservation")
            .transactionId(transactionId)
            .expiresAt(Instant.now().plusSeconds(300)) // 5 minute expiration
            .build();

        accountManagementService.reserveFunds(accountId, reservationRequest);
    }

    private void executeTransfer(TransferRequestDto request, String reservationId) {
        try {
            // Debit from source account
            AccountDebitRequestDto debitRequest = AccountDebitRequestDto.builder()
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .transactionId(request.getTransactionId())
                .description(request.getDescription())
                .reservationId(reservationId)
                .build();

            accountManagementService.debitAccount(request.getFromAccountId(), debitRequest);

            // Credit to destination account
            AccountCreditRequestDto creditRequest = AccountCreditRequestDto.builder()
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .transactionId(request.getTransactionId())
                .description(request.getDescription())
                .creditSource("TRANSFER")
                .build();

            accountManagementService.creditAccount(request.getToAccountId(), creditRequest);

            // Post to ledger
            ledgerServiceClient.postTransaction(
                request.getTransactionId(),
                request.getFromAccountId(),
                request.getToAccountId(),
                request.getAmount(),
                request.getCurrency(),
                request.getDescription()
            );

        } catch (Exception e) {
            // Release reservation on failure
            try {
                accountManagementService.releaseFunds(request.getFromAccountId(), reservationId);
            } catch (Exception releaseException) {
                log.error("Failed to release funds after transfer failure: {}", reservationId, releaseException);
            }
            throw e;
        }
    }

    private void sendTransferNotifications(TransferRequestDto request) {
        try {
            // Notify sender
            String senderUserId = getUserIdForAccount(request.getFromAccountId());
            notificationServiceClient.sendPushNotification(
                senderUserId,
                "Transfer Sent",
                String.format("You sent %s %s", request.getAmount(), request.getCurrency()),
                Map.of("transactionId", request.getTransactionId(), "type", "TRANSFER_SENT")
            );

            // Notify recipient
            String recipientUserId = getUserIdForAccount(request.getToAccountId());
            notificationServiceClient.sendPushNotification(
                recipientUserId,
                "Transfer Received",
                String.format("You received %s %s", request.getAmount(), request.getCurrency()),
                Map.of("transactionId", request.getTransactionId(), "type", "TRANSFER_RECEIVED")
            );

        } catch (Exception e) {
            log.warn("Failed to send transfer notifications for transaction: {}", request.getTransactionId(), e);
        }
    }

    private Transaction createTransactionForCompliance(TransferRequestDto request) {
        // Create a temporary transaction object for compliance checking
        Transaction transaction = new Transaction();
        transaction.setId(UUID.fromString(request.getTransactionId()));
        transaction.setSourceAccountId(UUID.fromString(request.getFromAccountId()));
        transaction.setDestinationAccountId(UUID.fromString(request.getToAccountId()));
        transaction.setAmount(request.getAmount());
        transaction.setCurrency(request.getCurrency());
        transaction.setType(Transaction.TransactionType.TRANSFER);
        transaction.setDescription(request.getDescription());
        transaction.setStatus(Transaction.TransactionStatus.PENDING);
        transaction.setCreatedAt(java.time.LocalDateTime.now());
        
        return transaction;
    }
    
    /**
     * Apply additional filters to transaction search
     */
    private List<Transaction> applySearchFilters(List<Transaction> transactions, TransactionSearchCriteria criteria) {
        return transactions.stream()
            .filter(t -> criteria.getTransactionType() == null || 
                t.getTransactionType().toString().equals(criteria.getTransactionType()))
            .filter(t -> criteria.getMinAmount() == null || 
                t.getAmount().compareTo(criteria.getMinAmount()) >= 0)
            .filter(t -> criteria.getMaxAmount() == null || 
                t.getAmount().compareTo(criteria.getMaxAmount()) <= 0)
            .filter(t -> criteria.getCurrency() == null || 
                t.getCurrency().equals(criteria.getCurrency()))
            .filter(t -> criteria.getDescription() == null || 
                (t.getDescription() != null && t.getDescription().toLowerCase().contains(criteria.getDescription().toLowerCase())))
            .collect(Collectors.toList());
    }
    
    /**
     * Check payment compliance
     */
    private boolean checkPaymentCompliance(PaymentRequestDto request) {
        try {
            // Create a temporary transaction for compliance checking
            Transaction tempTransaction = new Transaction();
            tempTransaction.setId(UUID.fromString(request.getTransactionId()));
            tempTransaction.setAmount(request.getAmount());
            tempTransaction.setCurrency(request.getCurrency());
            tempTransaction.setType(Transaction.TransactionType.PAYMENT);
            tempTransaction.setDescription(request.getDescription());
            tempTransaction.setStatus(Transaction.TransactionStatus.PENDING);
            tempTransaction.setCreatedAt(LocalDateTime.now());
            
            // Use the compliance integration service to check
            ComplianceIntegrationService.ComplianceCheckResult result = 
                complianceIntegrationService.performTransactionComplianceCheck(tempTransaction);
            
            return result.isApproved();
            
        } catch (Exception e) {
            log.error("Error checking payment compliance", e);
            return false; // Fail closed
        }
    }
    
    /**
     * Convert Transaction entity to TransactionResponseDto
     */
    private TransactionResponseDto convertToTransactionResponseDto(Transaction transaction) {
        return TransactionResponseDto.builder()
            .transactionId(transaction.getId().toString())
            .transactionNumber(transaction.getTransactionNumber())
            .status(transaction.getStatus().toString())
            .type(transaction.getTransactionType().toString())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .description(transaction.getDescription())
            .sourceAccountId(transaction.getSourceAccountId() != null ? 
                transaction.getSourceAccountId().toString() : null)
            .targetAccountId(transaction.getTargetAccountId() != null ? 
                transaction.getTargetAccountId().toString() : null)
            .createdAt(transaction.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant())
            .processedAt(transaction.getProcessedAt() != null ? 
                transaction.getProcessedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
            .completedAt(transaction.getCompletedAt() != null ? 
                transaction.getCompletedAt().atZone(ZoneId.systemDefault()).toInstant() : null)
            .externalReference(transaction.getExternalReference())
            .idempotencyKey(transaction.getIdempotencyKey())
            .fee(transaction.getFee())
            .exchangeRate(transaction.getExchangeRate())
            .riskScore(transaction.getRiskScore())
            .priority(transaction.getPriority() != null ? 
                transaction.getPriority().toString() : null)
            .build();
    }
}