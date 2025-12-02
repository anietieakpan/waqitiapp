package com.waqiti.recurringpayment.service;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

// Wallet Service Client
@FeignClient(
    name = "wallet-service",
    url = "${feign.client.config.wallet-service.url}",
    fallback = WalletServiceFallback.class
)
public interface WalletService {
    
    @GetMapping("/wallets/{userId}/balance")
    BigDecimal getBalance(@PathVariable String userId, @RequestParam String currency);
    
    @PostMapping("/wallets/{userId}/hold")
    void holdFunds(@PathVariable String userId, 
                   @RequestParam BigDecimal amount, 
                   @RequestParam String currency,
                   @RequestParam String reference);
    
    @PostMapping("/wallets/{userId}/release")
    void releaseFunds(@PathVariable String userId, 
                      @RequestParam BigDecimal amount, 
                      @RequestParam String currency,
                      @RequestParam String reference);
}
