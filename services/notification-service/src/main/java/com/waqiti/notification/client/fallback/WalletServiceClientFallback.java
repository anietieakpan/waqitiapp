package com.waqiti.notification.client.fallback;

import com.waqiti.notification.client.WalletServiceClient;
import com.waqiti.notification.client.dto.WalletBalanceResponse;
import com.waqiti.notification.client.dto.LowBalanceUserResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class WalletServiceClientFallback implements WalletServiceClient {
    
    @Override
    public WalletBalanceResponse getWalletBalance(UUID walletId, String currency) {
        log.warn("Fallback: Unable to fetch wallet balance for walletId {} currency {} from wallet service", 
            walletId, currency);
        return WalletBalanceResponse.builder()
            .walletId(walletId)
            .balance(BigDecimal.ZERO)
            .currency(currency)
            .status("FALLBACK")
            .build();
    }
    
    @Override
    public WalletBalanceResponse getUserWalletBalance(UUID userId, String currency) {
        log.warn("Fallback: Unable to fetch wallet balance for userId {} currency {} from wallet service", 
            userId, currency);
        return WalletBalanceResponse.builder()
            .userId(userId)
            .balance(BigDecimal.ZERO)
            .currency(currency)
            .status("FALLBACK")
            .build();
    }
    
    @Override
    public List<LowBalanceUserResponse> getUsersWithLowBalance(BigDecimal threshold, String currency) {
        log.warn("Fallback: Unable to fetch users with low balance (threshold: {} {}) from wallet service", 
            threshold, currency);
        return Collections.emptyList();
    }
    
    @Override
    public boolean isLowBalance(UUID userId, BigDecimal threshold) {
        log.warn("Fallback: Unable to check low balance for user {} with threshold {} from wallet service", 
            userId, threshold);
        return false;
    }
}