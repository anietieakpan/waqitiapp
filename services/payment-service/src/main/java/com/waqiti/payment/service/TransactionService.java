package com.waqiti.payment.service;

import com.waqiti.payment.domain.Transaction;
import com.waqiti.payment.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing payment transactions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {
    
    private final TransactionRepository transactionRepository;
    
    /**
     * Create a new transaction
     */
    @Transactional
    public Transaction createTransaction(Transaction transaction) {
        log.info("Creating transaction for payment: {} amount: {} {}", 
                transaction.getPaymentId(), transaction.getAmount(), transaction.getCurrency());
        
        transaction.setCreatedAt(LocalDateTime.now());
        transaction.setUpdatedAt(LocalDateTime.now());
        
        return transactionRepository.save(transaction);
    }
    
    /**
     * Update transaction status
     */
    @Transactional
    public Transaction updateTransactionStatus(UUID transactionId, Transaction.Status status) {
        log.info("Updating transaction {} status to: {}", transactionId, status);
        
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        
        transaction.setStatus(status);
        transaction.setUpdatedAt(LocalDateTime.now());
        
        if (status == Transaction.Status.COMPLETED) {
            transaction.setCompletedAt(LocalDateTime.now());
        }
        
        return transactionRepository.save(transaction);
    }
    
    /**
     * Find transaction by ID
     */
    public Optional<Transaction> findById(UUID transactionId) {
        return transactionRepository.findById(transactionId);
    }
    
    /**
     * Find transaction by provider transaction ID
     */
    public Optional<Transaction> findByProviderTransactionId(String providerTransactionId) {
        return transactionRepository.findByProviderTransactionId(providerTransactionId);
    }
    
    /**
     * Find transactions by payment ID
     */
    public List<Transaction> findByPaymentId(UUID paymentId) {
        return transactionRepository.findByPaymentId(paymentId);
    }
    
    /**
     * Find transactions by user ID
     */
    public List<Transaction> findByUserId(UUID userId) {
        return transactionRepository.findByUserId(userId);
    }
    
    /**
     * Update transaction with failure reason
     */
    @Transactional
    public Transaction failTransaction(UUID transactionId, String failureReason) {
        log.warn("Failing transaction {} with reason: {}", transactionId, failureReason);
        
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found: " + transactionId));
        
        transaction.setStatus(Transaction.Status.FAILED);
        transaction.setFailureReason(failureReason);
        transaction.setUpdatedAt(LocalDateTime.now());
        
        return transactionRepository.save(transaction);
    }
    
    /**
     * Create refund transaction
     */
    @Transactional
    public Transaction createRefundTransaction(UUID originalTransactionId, BigDecimal refundAmount, String reason) {
        Transaction originalTransaction = transactionRepository.findById(originalTransactionId)
                .orElseThrow(() -> new IllegalArgumentException("Original transaction not found: " + originalTransactionId));
        
        Transaction refund = Transaction.builder()
                .paymentId(originalTransaction.getPaymentId())
                .userId(originalTransaction.getUserId())
                .amount(refundAmount.negate())
                .currency(originalTransaction.getCurrency())
                .type(Transaction.TransactionType.REFUND)
                .status(Transaction.Status.PENDING)
                .provider(originalTransaction.getProvider())
                .description("Refund for transaction: " + originalTransactionId + " - " + reason)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        return transactionRepository.save(refund);
    }
    
    /**
     * Get transaction statistics for a user
     */
    public TransactionStats getTransactionStats(UUID userId) {
        List<Transaction> transactions = transactionRepository.findByUserId(userId);
        
        long totalCount = transactions.size();
        long completedCount = transactions.stream()
                .filter(t -> t.getStatus() == Transaction.Status.COMPLETED)
                .count();
        
        BigDecimal totalAmount = transactions.stream()
                .filter(t -> t.getStatus() == Transaction.Status.COMPLETED)
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return new TransactionStats(totalCount, completedCount, totalAmount);
    }
    
    public record TransactionStats(long totalCount, long completedCount, BigDecimal totalAmount) {}
}