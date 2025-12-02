package com.waqiti.reconciliation.client;

import com.waqiti.reconciliation.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "transaction-service", 
    url = "${services.transaction-service.url:http://transaction-service:8080}",
    fallback = TransactionServiceClientFallback.class
)
public interface TransactionServiceClient {

    /**
     * Get transaction details by ID
     */
    @GetMapping("/api/v1/transactions/{transactionId}")
    TransactionDetails getTransactionDetails(@PathVariable UUID transactionId);

    /**
     * Get transactions for reconciliation within date range
     */
    @GetMapping("/api/v1/transactions/reconciliation")
    List<TransactionSummary> getTransactionsForReconciliation(
        @RequestParam("startDate") LocalDateTime startDate,
        @RequestParam("endDate") LocalDateTime endDate,
        @RequestParam(value = "status", required = false) String status
    );

    /**
     * Get unmatched transactions
     */
    @GetMapping("/api/v1/transactions/unmatched")
    List<UnmatchedTransaction> getUnmatchedTransactions(
        @RequestParam("asOfDate") LocalDateTime asOfDate
    );

    /**
     * Get transaction by external reference
     */
    @GetMapping("/api/v1/transactions/external-reference/{externalReference}")
    TransactionDetails getTransactionByExternalReference(@PathVariable String externalReference);

    /**
     * Search transactions by criteria
     */
    @PostMapping("/api/v1/transactions/search")
    List<TransactionDetails> searchTransactions(@RequestBody TransactionSearchRequest request);

    /**
     * Get pending transactions
     */
    @GetMapping("/api/v1/transactions/pending")
    List<PendingTransaction> getPendingTransactions();

    /**
     * Get failed transactions
     */
    @GetMapping("/api/v1/transactions/failed")
    List<FailedTransaction> getFailedTransactions(
        @RequestParam("startDate") LocalDateTime startDate,
        @RequestParam("endDate") LocalDateTime endDate
    );

    /**
     * Validate transaction integrity
     */
    @PostMapping("/api/v1/transactions/{transactionId}/validate")
    TransactionValidationResult validateTransaction(
        @PathVariable UUID transactionId,
        @RequestBody TransactionValidationRequest request
    );

    /**
     * Get transaction statistics
     */
    @GetMapping("/api/v1/transactions/statistics")
    TransactionStatistics getTransactionStatistics(
        @RequestParam("startDate") LocalDateTime startDate,
        @RequestParam("endDate") LocalDateTime endDate
    );

    /**
     * Update transaction status
     */
    @PutMapping("/api/v1/transactions/{transactionId}/status")
    TransactionStatusUpdateResult updateTransactionStatus(
        @PathVariable UUID transactionId,
        @RequestBody TransactionStatusUpdateRequest request
    );

    /**
     * Get duplicate transactions
     */
    @GetMapping("/api/v1/transactions/duplicates")
    List<DuplicateTransactionGroup> getDuplicateTransactions(
        @RequestParam("startDate") LocalDateTime startDate,
        @RequestParam("endDate") LocalDateTime endDate
    );

    /**
     * Get transactions by account
     */
    @GetMapping("/api/v1/transactions/account/{accountId}")
    List<AccountTransaction> getTransactionsByAccount(
        @PathVariable UUID accountId,
        @RequestParam("startDate") LocalDateTime startDate,
        @RequestParam("endDate") LocalDateTime endDate
    );

    /**
     * Reverse transaction
     */
    @PostMapping("/api/v1/transactions/{transactionId}/reverse")
    TransactionReversalResult reverseTransaction(
        @PathVariable UUID transactionId,
        @RequestBody TransactionReversalRequest request
    );
}