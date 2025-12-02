package com.waqiti.notification.client;

import com.waqiti.notification.client.dto.WalletBalanceResponse;
import com.waqiti.notification.client.dto.LowBalanceUserResponse;
import com.waqiti.notification.client.fallback.WalletServiceClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@FeignClient(
    name = "wallet-service",
    fallback = WalletServiceClientFallback.class,
    configuration = FeignClientConfiguration.class
)
public interface WalletServiceClient {
    
    @GetMapping("/api/v1/wallets/{walletId}/balance")
    WalletBalanceResponse getWalletBalance(
        @PathVariable("walletId") UUID walletId,
        @RequestParam(value = "currency", required = false) String currency
    );
    
    @GetMapping("/api/v1/wallets/user/{userId}/balance")
    WalletBalanceResponse getUserWalletBalance(
        @PathVariable("userId") UUID userId,
        @RequestParam(value = "currency", defaultValue = "USD") String currency
    );
    
    @GetMapping("/api/v1/wallets/low-balance")
    List<LowBalanceUserResponse> getUsersWithLowBalance(
        @RequestParam(value = "threshold", defaultValue = "50.00") BigDecimal threshold,
        @RequestParam(value = "currency", defaultValue = "USD") String currency
    );
    
    @GetMapping("/api/v1/wallets/user/{userId}/low-balance-check")
    boolean isLowBalance(
        @PathVariable("userId") UUID userId,
        @RequestParam(value = "threshold", required = false) BigDecimal threshold
    );
}