package com.waqiti.recurringpayment.service.clients;

// DEPRECATED: This interface is replaced by UnifiedWalletServiceClient
// Please use com.waqiti.payment.client.UnifiedWalletServiceClient instead
// This interface is kept temporarily for backward compatibility

import com.waqiti.recurringpayment.service.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Wallet Service Client for Recurring Payment Service
 * @deprecated Use UnifiedWalletServiceClient instead
 */
@Deprecated
@FeignClient(
    name = "wallet-service", 
    path = "/api/v1/wallets",
    fallback = WalletServiceClientFallback.class
)
public interface WalletServiceClient {
    
    @GetMapping("/balance")
    BigDecimal getBalance(@RequestParam String userId, @RequestParam String currency);
    
    @GetMapping("/balances")
    BalancesResponse getAllBalances(@RequestParam String userId);
    
    @PostMapping("/hold")
    HoldResult holdFunds(@RequestBody HoldRequest request);
    
    @PostMapping("/release")
    void releaseFunds(@RequestParam String holdId);
    
    @PostMapping("/reserve")
    ReservationResult reserveFunds(@RequestBody ReservationRequest request);
    
    @GetMapping("/limits")
    WalletLimits getWalletLimits(@RequestParam String userId);
}