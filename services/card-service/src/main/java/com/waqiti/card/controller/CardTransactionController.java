package com.waqiti.card.controller;

import com.waqiti.card.dto.CardTransactionResponse;
import com.waqiti.card.dto.TransactionListResponse;
import com.waqiti.card.service.CardTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * CardTransactionController - REST API for transaction management
 *
 * Endpoints:
 * - GET /api/v1/transactions/{transactionId} - Get transaction details
 * - GET /api/v1/transactions/card/{cardId} - Get card transactions
 * - GET /api/v1/transactions/user/{userId} - Get user transactions
 * - POST /api/v1/transactions/{transactionId}/reverse - Reverse transaction
 * - GET /api/v1/transactions/pending - Get pending transactions
 * - GET /api/v1/transactions/high-value - Get high-value transactions
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
public class CardTransactionController {

    private final CardTransactionService transactionService;

    /**
     * Get transaction by ID
     */
    @GetMapping("/{transactionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isTransactionOwner(#transactionId)")
    public ResponseEntity<CardTransactionResponse> getTransactionById(@PathVariable UUID transactionId) {
        log.info("REST: Get transaction by ID: {}", transactionId);
        CardTransactionResponse response = transactionService.getTransactionById(transactionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get transaction by transaction ID string
     */
    @GetMapping("/transactionId/{transactionId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isTransactionOwner(#transactionId)")
    public ResponseEntity<CardTransactionResponse> getTransactionByTransactionId(@PathVariable String transactionId) {
        log.info("REST: Get transaction by transaction ID: {}", transactionId);
        CardTransactionResponse response = transactionService.getTransactionByTransactionId(transactionId);
        return ResponseEntity.ok(response);
    }

    /**
     * Get transactions for a card with pagination
     */
    @GetMapping("/card/{cardId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<TransactionListResponse> getTransactionsByCardId(
        @PathVariable UUID cardId,
        @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("REST: Get transactions for card: {}", cardId);
        TransactionListResponse response = transactionService.getTransactionsByCardId(cardId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Get transactions for a user with pagination
     */
    @GetMapping("/user/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or #userId == authentication.principal")
    public ResponseEntity<TransactionListResponse> getTransactionsByUserId(
        @PathVariable UUID userId,
        @PageableDefault(size = 20, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {

        log.info("REST: Get transactions for user: {}", userId);
        TransactionListResponse response = transactionService.getTransactionsByUserId(userId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Get transactions by card ID and date range
     */
    @GetMapping("/card/{cardId}/range")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<List<CardTransactionResponse>> getTransactionsByCardIdAndDateRange(
        @PathVariable UUID cardId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.info("REST: Get transactions for card: {} between {} and {}", cardId, startDate, endDate);
        List<CardTransactionResponse> transactions = transactionService.getTransactionsByCardIdAndDateRange(
            cardId, startDate, endDate);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Reverse a transaction
     */
    @PostMapping("/{transactionId}/reverse")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<CardTransactionResponse> reverseTransaction(
        @PathVariable UUID transactionId,
        @RequestParam String reason) {

        log.info("REST: Reverse transaction: {} - Reason: {}", transactionId, reason);
        CardTransactionResponse response = transactionService.reverseTransaction(transactionId, reason);
        return ResponseEntity.ok(response);
    }

    /**
     * Get pending transactions
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<List<CardTransactionResponse>> getPendingTransactions() {
        log.info("REST: Get pending transactions");
        List<CardTransactionResponse> transactions = transactionService.getPendingTransactions();
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get failed transactions
     */
    @GetMapping("/failed")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<List<CardTransactionResponse>> getFailedTransactions() {
        log.info("REST: Get failed transactions");
        List<CardTransactionResponse> transactions = transactionService.getFailedTransactions();
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get high-value transactions
     */
    @GetMapping("/high-value")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<List<CardTransactionResponse>> getHighValueTransactions(
        @RequestParam BigDecimal threshold) {

        log.info("REST: Get high-value transactions above: {}", threshold);
        List<CardTransactionResponse> transactions = transactionService.getHighValueTransactions(threshold);
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get international transactions
     */
    @GetMapping("/international")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<List<CardTransactionResponse>> getInternationalTransactions() {
        log.info("REST: Get international transactions");
        List<CardTransactionResponse> transactions = transactionService.getInternationalTransactions();
        return ResponseEntity.ok(transactions);
    }

    /**
     * Get disputed transactions
     */
    @GetMapping("/disputed")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM')")
    public ResponseEntity<List<CardTransactionResponse>> getDisputedTransactions() {
        log.info("REST: Get disputed transactions");
        List<CardTransactionResponse> transactions = transactionService.getDisputedTransactions();
        return ResponseEntity.ok(transactions);
    }

    /**
     * Calculate total transaction amount for date range
     */
    @GetMapping("/card/{cardId}/total")
    @PreAuthorize("hasAnyRole('ADMIN', 'SYSTEM') or @cardOwnershipValidator.isCardOwner(#cardId)")
    public ResponseEntity<BigDecimal> calculateTotalAmount(
        @PathVariable UUID cardId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {

        log.info("REST: Calculate total amount for card: {} between {} and {}", cardId, startDate, endDate);
        BigDecimal total = transactionService.calculateTotalAmount(cardId, startDate, endDate);
        return ResponseEntity.ok(total);
    }
}
