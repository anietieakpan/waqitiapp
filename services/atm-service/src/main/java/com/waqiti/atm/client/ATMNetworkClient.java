package com.waqiti.atm.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Feign Client for ATM Network Provider
 * Provides ATM hardware integration for cash dispensing, status monitoring, etc.
 */
@FeignClient(name = "atm-network-provider", path = "/api/v1/atm-network")
public interface ATMNetworkClient {

    @GetMapping("/{atmId}/cash-balance")
    BigDecimal getATMCashBalance(@PathVariable("atmId") UUID atmId);

    @PostMapping("/{atmId}/dispense")
    boolean dispenseCash(@PathVariable("atmId") UUID atmId,
                        @RequestParam("amount") BigDecimal amount);

    @PostMapping("/{atmId}/cash-balance")
    void updateCashBalance(@PathVariable("atmId") UUID atmId,
                          @RequestParam("adjustment") BigDecimal adjustment);

    @PostMapping("/{atmId}/check-count")
    void incrementCheckCount(@PathVariable("atmId") UUID atmId,
                            @RequestParam("count") int count);

    @GetMapping("/{atmId}/status")
    String getATMStatus(@PathVariable("atmId") UUID atmId);

    @PostMapping("/{atmId}/status")
    void updateATMStatus(@PathVariable("atmId") UUID atmId,
                        @RequestParam("status") String status);
}
