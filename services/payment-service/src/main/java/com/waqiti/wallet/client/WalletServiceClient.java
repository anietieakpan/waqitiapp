package com.waqiti.wallet.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@FeignClient(
    name = "wallet-service",
    url = "${services.wallet.url:http://wallet-service:8084}"
)
public interface WalletServiceClient {

    @GetMapping("/api/v1/wallets/user/{userId}")
    Object getUserWallet(@PathVariable("userId") String userId);

    @PostMapping("/api/v1/wallets/{walletId}/credit")
    Object creditWallet(@PathVariable("walletId") String walletId, 
                       @RequestBody Map<String, Object> request);

    @PostMapping("/api/v1/wallets/{walletId}/debit")
    Object debitWallet(@PathVariable("walletId") String walletId, 
                      @RequestBody Map<String, Object> request);

    @GetMapping("/api/v1/wallets/{walletId}/balance")
    BigDecimal getWalletBalance(@PathVariable("walletId") String walletId);

    @PostMapping("/api/v1/wallets/{walletId}/transfer")
    Object transferFunds(@PathVariable("walletId") String walletId, 
                        @RequestBody Map<String, Object> request);
}