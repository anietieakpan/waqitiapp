package com.waqiti.card.service;

import com.waqiti.card.dto.CardTransactionResponse;
import com.waqiti.card.dto.TransactionListResponse;
import com.waqiti.card.entity.Card;
import com.waqiti.card.entity.CardTransaction;
import com.waqiti.card.enums.TransactionStatus;
import com.waqiti.card.repository.CardRepository;
import com.waqiti.card.repository.CardTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * CardTransactionService - Business logic for transaction management
 *
 * Handles:
 * - Transaction recording and tracking
 * - Transaction history and reporting
 * - Transaction reversals
 * - Settlement tracking
 * - Financial calculations
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardTransactionService {

    private final CardTransactionRepository transactionRepository;
    private final CardRepository cardRepository;

    /**
     * Get transaction by ID
     */
    @Transactional(readOnly = true)
    public CardTransactionResponse getTransactionById(UUID transactionId) {
        CardTransaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        return mapToTransactionResponse(transaction);
    }

    /**
     * Get transaction by transaction ID string
     */
    @Transactional(readOnly = true)
    public CardTransactionResponse getTransactionByTransactionId(String transactionId) {
        CardTransaction transaction = transactionRepository.findByTransactionId(transactionId)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        return mapToTransactionResponse(transaction);
    }

    /**
     * Get transactions for a card with pagination
     */
    @Transactional(readOnly = true)
    public TransactionListResponse getTransactionsByCardId(UUID cardId, Pageable pageable) {
        Page<CardTransaction> transactionPage = transactionRepository.findByCardId(cardId, pageable);

        List<CardTransactionResponse> transactions = transactionPage.getContent().stream()
            .map(this::mapToTransactionResponse)
            .collect(Collectors.toList());

        return TransactionListResponse.builder()
            .transactions(transactions)
            .totalElements((int) transactionPage.getTotalElements())
            .totalPages(transactionPage.getTotalPages())
            .currentPage(transactionPage.getNumber())
            .pageSize(transactionPage.getSize())
            .build();
    }

    /**
     * Get transactions for a user with pagination
     */
    @Transactional(readOnly = true)
    public TransactionListResponse getTransactionsByUserId(UUID userId, Pageable pageable) {
        Page<CardTransaction> transactionPage = transactionRepository.findByUserId(userId, pageable);

        List<CardTransactionResponse> transactions = transactionPage.getContent().stream()
            .map(this::mapToTransactionResponse)
            .collect(Collectors.toList());

        return TransactionListResponse.builder()
            .transactions(transactions)
            .totalElements((int) transactionPage.getTotalElements())
            .totalPages(transactionPage.getTotalPages())
            .currentPage(transactionPage.getNumber())
            .pageSize(transactionPage.getSize())
            .build();
    }

    /**
     * Get transactions by card ID and date range
     */
    @Transactional(readOnly = true)
    public List<CardTransactionResponse> getTransactionsByCardIdAndDateRange(
        UUID cardId, LocalDateTime startDate, LocalDateTime endDate) {

        List<CardTransaction> transactions = transactionRepository.findByCardIdAndDateRange(
            cardId, startDate, endDate);

        return transactions.stream()
            .map(this::mapToTransactionResponse)
            .collect(Collectors.toList());
    }

    /**
     * Create transaction from authorization capture
     */
    @Transactional
    public CardTransactionResponse createTransactionFromCapture(
        UUID authorizationId,
        UUID cardId,
        BigDecimal amount,
        String currencyCode,
        String merchantId,
        String merchantName,
        String authorizationCode) {

        log.info("Creating transaction from authorization capture: {}", authorizationId);

        Card card = cardRepository.findById(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        String transactionId = generateTransactionId();

        CardTransaction transaction = CardTransaction.builder()
            .transactionId(transactionId)
            .cardId(cardId)
            .userId(card.getUserId())
            .accountId(card.getAccountId())
            .authorizationId(authorizationId)
            .transactionType(com.waqiti.card.enums.TransactionType.PURCHASE)
            .transactionStatus(TransactionStatus.COMPLETED)
            .transactionDate(LocalDateTime.now())
            .amount(amount)
            .currencyCode(currencyCode)
            .billingAmount(amount)
            .billingCurrencyCode(currencyCode)
            .merchantId(merchantId)
            .merchantName(merchantName)
            .authorizationCode(authorizationCode)
            .availableBalanceBefore(card.getAvailableCredit())
            .availableBalanceAfter(card.getAvailableCredit().subtract(amount))
            .isReversed(false)
            .isDisputed(false)
            .build();

        transaction = transactionRepository.save(transaction);

        log.info("Transaction created: {}", transactionId);

        return mapToTransactionResponse(transaction);
    }

    /**
     * Record refund transaction
     */
    @Transactional
    public CardTransactionResponse recordRefund(
        UUID originalTransactionId,
        UUID cardId,
        BigDecimal amount,
        String reason) {

        log.info("Recording refund for transaction: {}", originalTransactionId);

        CardTransaction originalTransaction = transactionRepository.findById(originalTransactionId)
            .orElseThrow(() -> new RuntimeException("Original transaction not found"));

        Card card = cardRepository.findById(cardId)
            .orElseThrow(() -> new RuntimeException("Card not found: " + cardId));

        String transactionId = generateTransactionId();

        CardTransaction refundTransaction = CardTransaction.builder()
            .transactionId(transactionId)
            .cardId(cardId)
            .userId(card.getUserId())
            .accountId(card.getAccountId())
            .originalTransactionId(originalTransactionId)
            .transactionType(com.waqiti.card.enums.TransactionType.REFUND)
            .transactionStatus(TransactionStatus.COMPLETED)
            .transactionDate(LocalDateTime.now())
            .amount(amount)
            .currencyCode(originalTransaction.getCurrencyCode())
            .billingAmount(amount)
            .billingCurrencyCode(originalTransaction.getBillingCurrencyCode())
            .merchantId(originalTransaction.getMerchantId())
            .merchantName(originalTransaction.getMerchantName())
            .description("Refund for transaction " + originalTransaction.getTransactionId())
            .notes(reason)
            .isReversed(false)
            .isDisputed(false)
            .build();

        refundTransaction = transactionRepository.save(refundTransaction);

        log.info("Refund transaction created: {}", transactionId);

        return mapToTransactionResponse(refundTransaction);
    }

    /**
     * Reverse a transaction
     */
    @Transactional
    public CardTransactionResponse reverseTransaction(UUID transactionId, String reason) {
        log.info("Reversing transaction: {} - Reason: {}", transactionId, reason);

        CardTransaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        if (transaction.isReversed()) {
            throw new RuntimeException("Transaction is already reversed");
        }

        if (!transaction.isPending() && !transaction.isCompleted()) {
            throw new RuntimeException("Transaction cannot be reversed - status: " + transaction.getTransactionStatus());
        }

        // Mark as reversed
        transaction.reverse(reason);
        transactionRepository.save(transaction);

        // Restore credit to card
        Card card = cardRepository.findById(transaction.getCardId())
            .orElseThrow(() -> new RuntimeException("Card not found"));

        card.restoreCredit(transaction.getAmount());
        cardRepository.save(card);

        log.info("Transaction reversed successfully: {}", transactionId);

        return mapToTransactionResponse(transaction);
    }

    /**
     * Mark transaction as disputed
     */
    @Transactional
    public void markAsDisputed(UUID transactionId, UUID disputeId) {
        log.info("Marking transaction as disputed: {}", transactionId);

        CardTransaction transaction = transactionRepository.findById(transactionId)
            .orElseThrow(() -> new RuntimeException("Transaction not found: " + transactionId));

        transaction.markAsDisputed(disputeId);
        transactionRepository.save(transaction);

        log.info("Transaction marked as disputed: {}", transactionId);
    }

    /**
     * Get pending transactions
     */
    @Transactional(readOnly = true)
    public List<CardTransactionResponse> getPendingTransactions() {
        List<CardTransaction> transactions = transactionRepository.findPendingTransactions();
        return transactions.stream()
            .map(this::mapToTransactionResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get failed transactions
     */
    @Transactional(readOnly = true)
    public List<CardTransactionResponse> getFailedTransactions() {
        List<CardTransaction> transactions = transactionRepository.findFailedTransactions();
        return transactions.stream()
            .map(this::mapToTransactionResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get high-value transactions
     */
    @Transactional(readOnly = true)
    public List<CardTransactionResponse> getHighValueTransactions(BigDecimal threshold) {
        List<CardTransaction> transactions = transactionRepository.findHighValueTransactions(threshold);
        return transactions.stream()
            .map(this::mapToTransactionResponse)
            .collect(Collectors.toList());
    }

    /**
     * Calculate total transaction amount for card in date range
     */
    @Transactional(readOnly = true)
    public BigDecimal calculateTotalAmount(UUID cardId, LocalDateTime startDate, LocalDateTime endDate) {
        return transactionRepository.calculateTotalAmountByCardIdAndDateRange(cardId, startDate, endDate);
    }

    /**
     * Count transactions for card in time window (for velocity checks)
     */
    @Transactional(readOnly = true)
    public long countTransactionsSince(UUID cardId, LocalDateTime sinceDate) {
        return transactionRepository.countByCardIdSince(cardId, sinceDate);
    }

    /**
     * Get recent transactions for card
     */
    @Transactional(readOnly = true)
    public List<CardTransactionResponse> getRecentTransactions(UUID cardId, LocalDateTime sinceDate) {
        List<CardTransaction> transactions = transactionRepository.findRecentTransactionsByCardId(cardId, sinceDate);
        return transactions.stream()
            .map(this::mapToTransactionResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get international transactions
     */
    @Transactional(readOnly = true)
    public List<CardTransactionResponse> getInternationalTransactions() {
        List<CardTransaction> transactions = transactionRepository.findInternationalTransactions();
        return transactions.stream()
            .map(this::mapToTransactionResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get fraud blocked transactions
     */
    @Transactional(readOnly = true)
    public List<CardTransactionResponse> getFraudBlockedTransactions() {
        List<CardTransaction> transactions = transactionRepository.findFraudBlockedTransactions();
        return transactions.stream()
            .map(this::mapToTransactionResponse)
            .collect(Collectors.toList());
    }

    /**
     * Get disputed transactions
     */
    @Transactional(readOnly = true)
    public List<CardTransactionResponse> getDisputedTransactions() {
        List<CardTransaction> transactions = transactionRepository.findDisputedTransactions();
        return transactions.stream()
            .map(this::mapToTransactionResponse)
            .collect(Collectors.toList());
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    private CardTransactionResponse mapToTransactionResponse(CardTransaction transaction) {
        return CardTransactionResponse.builder()
            .id(transaction.getId())
            .transactionId(transaction.getTransactionId())
            .cardId(transaction.getCardId())
            .userId(transaction.getUserId())
            .transactionType(transaction.getTransactionType())
            .transactionStatus(transaction.getTransactionStatus())
            .transactionDate(transaction.getTransactionDate())
            .amount(transaction.getAmount())
            .currencyCode(transaction.getCurrencyCode())
            .billingAmount(transaction.getBillingAmount())
            .billingCurrencyCode(transaction.getBillingCurrencyCode())
            .merchantId(transaction.getMerchantId())
            .merchantName(transaction.getMerchantName())
            .merchantCategoryCode(transaction.getMerchantCategoryCode())
            .merchantCountry(transaction.getMerchantCountry())
            .authorizationCode(transaction.getAuthorizationCode())
            .isInternational(transaction.getIsInternational())
            .isOnline(transaction.getIsOnline())
            .isContactless(transaction.getIsContactless())
            .fraudScore(transaction.getFraudScore())
            .riskLevel(transaction.getRiskLevel())
            .isReversed(transaction.getIsReversed())
            .isDisputed(transaction.getIsDisputed())
            .createdAt(transaction.getCreatedAt())
            .build();
    }

    private String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
