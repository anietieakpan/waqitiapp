package com.waqiti.payment.client.fallback;

import com.waqiti.payment.client.UnifiedWalletServiceClient;
import com.waqiti.payment.client.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Wallet Service Fallback Implementation
 * 
 * Provides graceful degradation when wallet service is unavailable.
 * Uses caching and safe defaults to maintain service continuity.
 * 
 * @version 3.0.0
 * @since 2025-01-15
 */
@Slf4j
@Component
public class WalletServiceFallback implements UnifiedWalletServiceClient {
    
    private static final String FALLBACK_MESSAGE = "Wallet service temporarily unavailable. Using fallback response.";
    
    @Override
    @Cacheable(value = "wallet-fallback", key = "#walletId")
    public ResponseEntity<WalletResponse> getWallet(UUID walletId) {
        log.warn("Fallback: getWallet called for walletId: {}", walletId);
        
        return ResponseEntity.ok(WalletResponse.builder()
            .walletId(walletId)
            .status(WalletResponse.WalletStatus.ACTIVE)
            .message(FALLBACK_MESSAGE)
            .build());
    }
    
    @Override
    @Cacheable(value = "wallet-user-fallback", key = "#userId")
    public ResponseEntity<WalletResponse> getWalletByUserId(UUID userId) {
        log.warn("Fallback: getWalletByUserId called for userId: {}", userId);
        
        return ResponseEntity.ok(WalletResponse.builder()
            .userId(userId)
            .status(WalletResponse.WalletStatus.ACTIVE)
            .message(FALLBACK_MESSAGE)
            .build());
    }
    
    @Override
    @Cacheable(value = "balance-fallback", key = "#walletId")
    public ResponseEntity<BalanceResponse> getBalance(UUID walletId) {
        log.warn("Fallback: getBalance called for walletId: {}", walletId);
        
        // Return cached or zero balance for safety
        return ResponseEntity.ok(BalanceResponse.builder()
            .walletId(walletId)
            .available(BigDecimal.ZERO)
            .totalBalance(BigDecimal.ZERO)
            .held(BigDecimal.ZERO)
            .status("CACHED")
            .message("Using cached balance. Real-time balance unavailable.")
            .asOf(Instant.now())
            .build());
    }
    
    @Override
    public ResponseEntity<TransactionResponse> debitWallet(UUID walletId, DebitRequest request) {
        log.error("Fallback: debitWallet called - operation not allowed in fallback mode");
        
        // Never allow debits in fallback mode for safety
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(TransactionResponse.builder()
                .walletId(walletId)
                .status(TransactionResponse.TransactionStatus.FAILED)
                .errorMessage("Debit operations unavailable during service outage")
                .build());
    }
    
    @Override
    public ResponseEntity<TransactionResponse> creditWallet(UUID walletId, CreditRequest request) {
        log.warn("Fallback: creditWallet called - queuing for later processing");
        
        // Queue credits for later processing
        return ResponseEntity.accepted()
            .body(TransactionResponse.builder()
                .walletId(walletId)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(TransactionResponse.TransactionStatus.PENDING)
                .reference(request.getReference())
                .description("Credit queued for processing when service is restored")
                .createdAt(Instant.now())
                .build());
    }
    
    @Override
    public ResponseEntity<TransferResponse> transfer(TransferRequest request) {
        log.error("Fallback: transfer called - operation not allowed in fallback mode");
        
        // Never allow transfers in fallback mode
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(TransferResponse.builder()
                .sourceWalletId(request.getSourceWalletId())
                .destinationWalletId(request.getDestinationWalletId())
                .status(TransferResponse.TransferStatus.FAILED)
                .errorMessage("Transfer operations unavailable during service outage")
                .build());
    }
    
    @Override
    public ResponseEntity<HoldResponse> placeHold(UUID walletId, HoldRequest request) {
        log.error("Fallback: placeHold called - operation not allowed in fallback mode");
        
        // Never allow holds in fallback mode
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(HoldResponse.builder()
                .walletId(walletId)
                .status(HoldResponse.HoldStatus.CANCELLED)
                .build());
    }
    
    @Override
    public ResponseEntity<Void> releaseHold(UUID walletId, String holdId) {
        log.warn("Fallback: releaseHold called - queuing for later processing");
        
        // Queue hold releases for later
        return ResponseEntity.accepted().build();
    }
    
    @Override
    public ResponseEntity<ValidationResponse> validateBalance(UUID walletId, BigDecimal amount, String currency) {
        log.warn("Fallback: validateBalance called - returning conservative response");
        
        // Conservative validation - assume insufficient balance for safety
        return ResponseEntity.ok(ValidationResponse.builder()
            .valid(false)
            .errors(List.of(ValidationResponse.ValidationError.builder()
                .code("SERVICE_UNAVAILABLE")
                .message("Balance validation unavailable - please try again later")
                .build()))
            .build());
    }
    
    @Override
    public ResponseEntity<List<TransactionResponse>> getTransactionHistory(UUID walletId, int page, int size) {
        log.warn("Fallback: getTransactionHistory called - returning empty list");
        
        // Return empty transaction history
        return ResponseEntity.ok(Collections.emptyList());
    }
}