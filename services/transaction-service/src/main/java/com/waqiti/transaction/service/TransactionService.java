package com.waqiti.transaction.service;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import com.waqiti.transaction.dto.TransactionRequest;
import com.waqiti.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public Transaction createTransaction(TransactionRequest request) {
        log.info("Creating new transaction from {} to {}", request.getFromAccountId(), request.getToAccountId());
        
        Transaction transaction = Transaction.builder()
            .reference(generateTransactionReference())
            .sourceAccountId(request.getFromAccountId())
            .targetAccountId(request.getToAccountId())
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .type(mapTransactionType(request.getTransactionType()))
            .status(TransactionStatus.INITIATED)
            .description(request.getDescription())
            .createdAt(LocalDateTime.now())
            .build();
        
        return transactionRepository.save(transaction);
    }
    
    private String generateTransactionReference() {
        return "TXN-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
    
    private TransactionType mapTransactionType(String type) {
        try {
            return TransactionType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unknown transaction type: {}, defaulting to P2P_TRANSFER", type);
            return TransactionType.P2P_TRANSFER;
        }
    }

    /**
     * Freezes a customer account to prevent new transactions
     *
     * CRITICAL FIX: Removed exception swallowing to allow proper transaction rollback.
     * Exceptions now propagate correctly, triggering @Transactional rollback.
     *
     * @param customerId the customer ID
     * @param reason the reason for freezing
     * @param durationHours duration in hours (null for indefinite)
     * @param emergencyFreeze whether this is an emergency freeze
     * @return number of transactions frozen
     * @throws IllegalArgumentException if customerId is null or empty
     * @throws TransactionFreezeException if freeze operation fails
     */
    @Transactional(rollbackFor = Exception.class)
    public int freezeCustomerAccount(String customerId, String reason, Integer durationHours, boolean emergencyFreeze) {
        if (customerId == null || customerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer ID cannot be null or empty");
        }

        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Freeze reason cannot be null or empty");
        }

        log.warn("SECURITY: Freezing customer account: {} - Reason: {} - Emergency: {} - Duration: {} hours",
            customerId, reason, emergencyFreeze, durationHours);

        // Update all pending transactions for this customer to FROZEN status
        int updatedCount = transactionRepository.updateTransactionStatusByCustomer(
            customerId, TransactionStatus.PENDING, TransactionStatus.FROZEN);

        if (updatedCount == 0) {
            log.info("No pending transactions found to freeze for customer: {}", customerId);
            // This is not an error - customer may simply have no pending transactions
            return 0;
        }

        log.info("SECURITY: Successfully froze {} pending transactions for customer: {}", updatedCount, customerId);

        // Exception will propagate naturally if repository call fails
        // @Transactional will handle rollback automatically
        return updatedCount;
    }

    /**
     * Freezes a merchant account to prevent new transactions
     *
     * CRITICAL FIX: Removed exception swallowing to allow proper transaction rollback.
     * Exceptions now propagate correctly, triggering @Transactional rollback.
     *
     * @param merchantId the merchant ID
     * @param reason the reason for freezing
     * @param durationHours duration in hours (null for indefinite)
     * @param emergencyFreeze whether this is an emergency freeze
     * @return number of transactions frozen
     * @throws IllegalArgumentException if merchantId is null or empty
     * @throws TransactionFreezeException if freeze operation fails
     */
    @Transactional(rollbackFor = Exception.class)
    public int freezeMerchantAccount(String merchantId, String reason, Integer durationHours, boolean emergencyFreeze) {
        if (merchantId == null || merchantId.trim().isEmpty()) {
            throw new IllegalArgumentException("Merchant ID cannot be null or empty");
        }

        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Freeze reason cannot be null or empty");
        }

        log.warn("SECURITY: Freezing merchant account: {} - Reason: {} - Emergency: {} - Duration: {} hours",
            merchantId, reason, emergencyFreeze, durationHours);

        // Update all pending transactions for this merchant to FROZEN status
        int updatedCount = transactionRepository.updateTransactionStatusByMerchant(
            merchantId, TransactionStatus.PENDING, TransactionStatus.FROZEN);

        if (updatedCount == 0) {
            log.info("No pending transactions found to freeze for merchant: {}", merchantId);
            // This is not an error - merchant may simply have no pending transactions
            return 0;
        }

        log.info("SECURITY: Successfully froze {} pending transactions for merchant: {}", updatedCount, merchantId);

        // Exception will propagate naturally if repository call fails
        // @Transactional will handle rollback automatically
        return updatedCount;
    }
}