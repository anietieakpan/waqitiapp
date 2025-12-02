package com.waqiti.payment.client;

import com.waqiti.payment.client.dto.TransferRequest;
import com.waqiti.payment.client.dto.TransferResponse;
import com.waqiti.payment.client.dto.WalletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fallback implementation for WalletServiceClient
 * Provides graceful degradation when wallet service is unavailable
 */
@Component
@Slf4j
public class WalletServiceClientFallback implements WalletServiceClient {
    
    @Override
    public WalletResponse getWallet(UUID walletId) {
        log.warn("Wallet service unavailable, returning fallback wallet for ID: {}", walletId);
        return createFallbackWallet(walletId, BigDecimal.ZERO);
    }
    
    @Override
    public List<WalletResponse> getUserWallets(UUID userId) {
        log.warn("Wallet service unavailable, returning empty wallets for user: {}", userId);
        return Collections.emptyList();
    }
    
    @Override
    public WalletResponse getWalletByUserAndCurrency(UUID userId, String currency) {
        log.warn("Wallet service unavailable, returning fallback wallet for user: {} currency: {}", userId, currency);
        return createFallbackWallet(UUID.randomUUID(), BigDecimal.ZERO, currency);
    }
    
    @Override
    public WalletResponse getWalletBalance(UUID walletId, String currency) {
        log.warn("Wallet service unavailable, returning zero balance for wallet: {} currency: {}", walletId, currency);
        return createFallbackWallet(walletId, BigDecimal.ZERO, currency);
    }
    
    @Override
    public TransferResponse transfer(TransferRequest request) {
        log.error("Wallet service unavailable, cannot process transfer: {}", request);
        return TransferResponse.builder()
                .success(false)
                .errorMessage("Wallet service temporarily unavailable")
                .build();
    }
    
    private WalletResponse createFallbackWallet(UUID walletId, BigDecimal balance) {
        return createFallbackWallet(walletId, balance, "USD");
    }
    
    private WalletResponse createFallbackWallet(UUID walletId, BigDecimal balance, String currency) {
        return WalletResponse.builder()
                .id(walletId)
                .balance(balance)
                .currency(currency)
                .status("UNAVAILABLE")
                .walletType("PERSONAL")
                .accountType("SAVINGS")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}