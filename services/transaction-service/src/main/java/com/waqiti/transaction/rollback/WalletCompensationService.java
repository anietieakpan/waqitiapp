package com.waqiti.transaction.rollback;

import com.waqiti.transaction.domain.Transaction;
import com.waqiti.transaction.model.CompensationAction;
import com.waqiti.common.client.WalletServiceClient;
import com.waqiti.common.client.DebitRequest;
import com.waqiti.common.client.CreditRequest;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Enterprise-grade Wallet Compensation Service
 * 
 * Handles wallet balance compensations during transaction rollbacks.
 * Ensures atomic balance reversals with comprehensive audit trail.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WalletCompensationService {

    private final WalletServiceClient walletServiceClient;
    private final CompensationAuditService compensationAuditService;

    /**
     * Execute wallet compensation for transaction rollback
     * Protected with circuit breaker for resilience
     */
    @CircuitBreaker(name = "wallet-compensation", fallbackMethod = "compensateFallback")
    @Retry(name = "wallet-compensation")
    @Bulkhead(name = "wallet-compensation")
    @Transactional
    public CompensationAction.CompensationResult compensateWalletBalances(
            Transaction transaction, CompensationAction action) {
        
        log.info("CRITICAL: Executing wallet compensation for transaction: {} - action: {}", 
                transaction.getId(), action.getActionId());

        try {
            // Validate compensation is idempotent
            if (isCompensationAlreadyApplied(transaction.getId(), action.getActionId())) {
                log.warn("Wallet compensation already applied for action: {}", action.getActionId());
                return CompensationAction.CompensationResult.builder()
                    .actionId(action.getActionId())
                    .status(CompensationAction.CompensationStatus.ALREADY_COMPLETED)
                    .message("Compensation already applied")
                    .completedAt(LocalDateTime.now())
                    .build();
            }

            // Execute compensation based on transaction type
            CompensationAction.CompensationResult result = switch (transaction.getType()) {
                case TRANSFER -> compensateTransfer(transaction, action);
                case PAYMENT -> compensatePayment(transaction, action);
                case WITHDRAWAL -> compensateWithdrawal(transaction, action);
                case DEPOSIT -> compensateDeposit(transaction, action);
                case FEE -> compensateFee(transaction, action);
                case REFUND -> compensateRefund(transaction, action);
                default -> {
                    log.warn("Unsupported transaction type for compensation: {} - using generic compensation", 
                            transaction.getType());
                    yield compensateGeneric(transaction, action);
                }
            };

            // Record compensation audit
            compensationAuditService.recordCompensation(
                transaction.getId(), 
                action.getActionId(), 
                "WALLET", 
                result.getStatus().toString()
            );

            log.info("CRITICAL: Wallet compensation completed for transaction: {} - status: {}", 
                    transaction.getId(), result.getStatus());

            return result;

        } catch (Exception e) {
            log.error("CRITICAL: Wallet compensation failed for transaction: {}", transaction.getId(), e);
            
            // Record failure in audit
            compensationAuditService.recordCompensationFailure(
                transaction.getId(), 
                action.getActionId(), 
                "WALLET", 
                e.getMessage()
            );

            return CompensationAction.CompensationResult.builder()
                .actionId(action.getActionId())
                .status(CompensationAction.CompensationStatus.FAILED)
                .errorMessage(e.getMessage())
                .failedAt(LocalDateTime.now())
                .build();
        }
    }

    /**
     * Compensate a transfer transaction (reverse the transfer)
     */
    private CompensationAction.CompensationResult compensateTransfer(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating transfer transaction: {} - reversing {} {} from {} to {}", 
                transaction.getId(), transaction.getAmount(), transaction.getCurrency(),
                transaction.getToUserId(), transaction.getFromUserId());

        // Credit back to sender
        CreditRequest creditRequest = CreditRequest.builder()
            .walletId(transaction.getFromWalletId())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .reference("ROLLBACK-" + transaction.getId())
            .description("Transaction rollback credit")
            .idempotencyKey("rollback-credit-" + transaction.getId())
            .build();

        walletServiceClient.credit(creditRequest);

        // Debit from receiver
        DebitRequest debitRequest = DebitRequest.builder()
            .walletId(transaction.getToWalletId())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .reference("ROLLBACK-" + transaction.getId())
            .description("Transaction rollback debit")
            .idempotencyKey("rollback-debit-" + transaction.getId())
            .build();

        walletServiceClient.debit(debitRequest);

        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.COMPLETED)
            .message("Transfer reversed successfully")
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Compensate a payment transaction
     */
    private CompensationAction.CompensationResult compensatePayment(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating payment transaction: {} - refunding {} {}", 
                transaction.getId(), transaction.getAmount(), transaction.getCurrency());

        // Credit back to payer
        CreditRequest creditRequest = CreditRequest.builder()
            .walletId(transaction.getFromWalletId())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .reference("PAYMENT-ROLLBACK-" + transaction.getId())
            .description("Payment rollback refund")
            .idempotencyKey("payment-rollback-" + transaction.getId())
            .build();

        walletServiceClient.credit(creditRequest);

        // Handle merchant reversal if applicable
        if (transaction.getMerchantId() != null) {
            DebitRequest merchantDebit = DebitRequest.builder()
                .walletId(transaction.getMerchantWalletId())
                .amount(transaction.getAmount().subtract(transaction.getFeeAmount()))
                .currency(transaction.getCurrency())
                .reference("MERCHANT-ROLLBACK-" + transaction.getId())
                .description("Merchant payment reversal")
                .idempotencyKey("merchant-rollback-" + transaction.getId())
                .build();

            walletServiceClient.debit(merchantDebit);
        }

        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.COMPLETED)
            .message("Payment refunded successfully")
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Compensate a withdrawal transaction
     */
    private CompensationAction.CompensationResult compensateWithdrawal(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating withdrawal transaction: {} - reversing {} {}", 
                transaction.getId(), transaction.getAmount(), transaction.getCurrency());

        // Credit back to user wallet
        CreditRequest creditRequest = CreditRequest.builder()
            .walletId(transaction.getFromWalletId())
            .amount(transaction.getAmount().add(transaction.getFeeAmount()))
            .currency(transaction.getCurrency())
            .reference("WITHDRAWAL-ROLLBACK-" + transaction.getId())
            .description("Withdrawal reversal")
            .idempotencyKey("withdrawal-rollback-" + transaction.getId())
            .build();

        walletServiceClient.credit(creditRequest);

        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.COMPLETED)
            .message("Withdrawal reversed successfully")
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Compensate a deposit transaction
     */
    private CompensationAction.CompensationResult compensateDeposit(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating deposit transaction: {} - reversing {} {}", 
                transaction.getId(), transaction.getAmount(), transaction.getCurrency());

        // Debit from user wallet
        DebitRequest debitRequest = DebitRequest.builder()
            .walletId(transaction.getToWalletId())
            .amount(transaction.getAmount().subtract(transaction.getFeeAmount()))
            .currency(transaction.getCurrency())
            .reference("DEPOSIT-ROLLBACK-" + transaction.getId())
            .description("Deposit reversal")
            .idempotencyKey("deposit-rollback-" + transaction.getId())
            .build();

        walletServiceClient.debit(debitRequest);

        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.COMPLETED)
            .message("Deposit reversed successfully")
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Compensate a fee transaction
     */
    private CompensationAction.CompensationResult compensateFee(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating fee transaction: {} - refunding {} {}", 
                transaction.getId(), transaction.getAmount(), transaction.getCurrency());

        // Refund fee to user
        CreditRequest creditRequest = CreditRequest.builder()
            .walletId(transaction.getFromWalletId())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .reference("FEE-ROLLBACK-" + transaction.getId())
            .description("Fee refund")
            .idempotencyKey("fee-rollback-" + transaction.getId())
            .build();

        walletServiceClient.credit(creditRequest);

        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.COMPLETED)
            .message("Fee refunded successfully")
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Compensate a refund transaction (reverse the refund)
     */
    private CompensationAction.CompensationResult compensateRefund(
            Transaction transaction, CompensationAction action) {
        
        log.info("Compensating refund transaction: {} - reversing refund of {} {}", 
                transaction.getId(), transaction.getAmount(), transaction.getCurrency());

        // Debit the refund amount back
        DebitRequest debitRequest = DebitRequest.builder()
            .walletId(transaction.getToWalletId())
            .amount(transaction.getAmount())
            .currency(transaction.getCurrency())
            .reference("REFUND-REVERSAL-" + transaction.getId())
            .description("Refund reversal")
            .idempotencyKey("refund-reversal-" + transaction.getId())
            .build();

        walletServiceClient.debit(debitRequest);

        // Credit back to original source if applicable
        if (transaction.getFromWalletId() != null) {
            CreditRequest creditRequest = CreditRequest.builder()
                .walletId(transaction.getFromWalletId())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .reference("REFUND-REVERSAL-CREDIT-" + transaction.getId())
                .description("Refund reversal credit")
                .idempotencyKey("refund-reversal-credit-" + transaction.getId())
                .build();

            walletServiceClient.credit(creditRequest);
        }

        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.COMPLETED)
            .message("Refund reversed successfully")
            .completedAt(LocalDateTime.now())
            .build();
    }

    /**
     * Check if compensation has already been applied (idempotency)
     */
    private boolean isCompensationAlreadyApplied(UUID transactionId, String actionId) {
        return compensationAuditService.isCompensationApplied(transactionId, actionId, "WALLET");
    }

    /**
     * Fallback method for circuit breaker
     */
    public CompensationAction.CompensationResult compensateFallback(
            Transaction transaction, CompensationAction action, Exception ex) {
        
        log.error("CIRCUIT_BREAKER: Wallet compensation circuit breaker activated for transaction: {}", 
                transaction.getId(), ex);

        return CompensationAction.CompensationResult.builder()
            .actionId(action.getActionId())
            .status(CompensationAction.CompensationStatus.CIRCUIT_BREAKER_OPEN)
            .errorMessage("Wallet service temporarily unavailable - compensation queued for retry")
            .failedAt(LocalDateTime.now())
            .retryable(true)
            .build();
    }

    /**
     * Generate wallet compensation actions for a transaction
     */
    public List<CompensationAction> generateActions(Transaction transaction) {
        List<CompensationAction> actions = new ArrayList<>();

        // Primary wallet compensation
        actions.add(CompensationAction.builder()
            .actionId(UUID.randomUUID().toString())
            .actionType(CompensationAction.ActionType.WALLET_BALANCE_REVERSAL)
            .targetService("wallet-service")
            .targetResourceId(transaction.getFromWalletId())
            .compensationData(Map.of(
                "amount", transaction.getAmount(),
                "currency", transaction.getCurrency(),
                "direction", "CREDIT"
            ))
            .priority(1)
            .retryable(true)
            .maxRetries(3)
            .build());

        // Secondary wallet compensation (receiver)
        if (transaction.getToWalletId() != null) {
            actions.add(CompensationAction.builder()
                .actionId(UUID.randomUUID().toString())
                .actionType(CompensationAction.ActionType.WALLET_BALANCE_REVERSAL)
                .targetService("wallet-service")
                .targetResourceId(transaction.getToWalletId())
                .compensationData(Map.of(
                    "amount", transaction.getAmount(),
                    "currency", transaction.getCurrency(),
                    "direction", "DEBIT"
                ))
                .priority(2)
                .retryable(true)
                .maxRetries(3)
                .build());
        }

        // Fee compensation if applicable
        if (transaction.getFeeAmount() != null && transaction.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            actions.add(CompensationAction.builder()
                .actionId(UUID.randomUUID().toString())
                .actionType(CompensationAction.ActionType.FEE_REVERSAL)
                .targetService("wallet-service")
                .targetResourceId(transaction.getFromWalletId())
                .compensationData(Map.of(
                    "feeAmount", transaction.getFeeAmount(),
                    "currency", transaction.getCurrency()
                ))
                .priority(3)
                .retryable(true)
                .maxRetries(3)
                .build());
        }

        return actions;
    }

    /**
     * Generic compensation for unsupported transaction types
     */
    private CompensationAction.CompensationResult compensateGeneric(
            Transaction transaction, CompensationAction action) {
        
        log.info("Applying generic compensation for transaction: {} - type: {}", 
                transaction.getId(), transaction.getType());

        try {
            // For unknown transaction types, attempt to reverse the core balance movement
            if (transaction.getFromWalletId() != null && transaction.getToWalletId() != null) {
                // Reverse a transfer-like operation
                
                // Credit back to sender
                CreditRequest creditRequest = CreditRequest.builder()
                    .walletId(transaction.getFromWalletId())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .reference("GENERIC-ROLLBACK-" + transaction.getId())
                    .description("Generic transaction reversal (sender)")
                    .idempotencyKey("generic-rollback-sender-" + transaction.getId())
                    .build();

                walletServiceClient.credit(creditRequest);

                // Debit from receiver
                DebitRequest debitRequest = DebitRequest.builder()
                    .walletId(transaction.getToWalletId())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .reference("GENERIC-ROLLBACK-" + transaction.getId())
                    .description("Generic transaction reversal (receiver)")
                    .idempotencyKey("generic-rollback-receiver-" + transaction.getId())
                    .build();

                walletServiceClient.debit(debitRequest);
                
            } else if (transaction.getFromWalletId() != null) {
                // Single wallet operation - likely a credit, so debit it back
                DebitRequest debitRequest = DebitRequest.builder()
                    .walletId(transaction.getFromWalletId())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .reference("GENERIC-ROLLBACK-" + transaction.getId())
                    .description("Generic transaction reversal")
                    .idempotencyKey("generic-rollback-" + transaction.getId())
                    .build();

                walletServiceClient.debit(debitRequest);
                
            } else if (transaction.getToWalletId() != null) {
                // Single wallet operation - likely a debit, so credit it back
                CreditRequest creditRequest = CreditRequest.builder()
                    .walletId(transaction.getToWalletId())
                    .amount(transaction.getAmount())
                    .currency(transaction.getCurrency())
                    .reference("GENERIC-ROLLBACK-" + transaction.getId())
                    .description("Generic transaction reversal")
                    .idempotencyKey("generic-rollback-" + transaction.getId())
                    .build();

                walletServiceClient.credit(creditRequest);
            }

            return CompensationAction.CompensationResult.builder()
                .actionId(action.getActionId())
                .status(CompensationAction.CompensationStatus.COMPLETED)
                .message("Generic compensation applied successfully")
                .completedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Generic compensation failed for transaction: {}", transaction.getId(), e);
            return CompensationAction.CompensationResult.builder()
                .actionId(action.getActionId())
                .status(CompensationAction.CompensationStatus.FAILED)
                .message("Generic compensation failed: " + e.getMessage())
                .errorDetails(e.toString())
                .completedAt(LocalDateTime.now())
                .build();
        }
    }
}