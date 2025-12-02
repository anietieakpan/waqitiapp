package com.waqiti.rewards.client;

import com.waqiti.rewards.dto.WalletCreditRequest;
import com.waqiti.rewards.dto.WalletCreditResponse;
import com.waqiti.rewards.dto.WalletDetailsDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(
    name = "wallet-service",
    url = "${services.wallet.url:http://wallet-service:8083}",
    configuration = WalletServiceClientConfig.class,
    fallback = WalletServiceClientFallback.class
)
public interface WalletServiceClient {

    @GetMapping("/api/v1/wallets/user/{userId}")
    WalletDetailsDto getUserWallet(
        @PathVariable("userId") String userId,
        @RequestHeader("Authorization") String authorization
    );

    @PostMapping("/api/v1/wallets/{walletId}/credit")
    WalletCreditResponse creditWallet(
        @PathVariable("walletId") String walletId,
        @RequestBody WalletCreditRequest request,
        @RequestHeader("Authorization") String authorization
    );

    @PostMapping("/api/v1/wallets/user/{userId}/credit")
    WalletCreditResponse creditUserWallet(
        @PathVariable("userId") String userId,
        @RequestBody WalletCreditRequest request,
        @RequestHeader("Authorization") String authorization
    );
}