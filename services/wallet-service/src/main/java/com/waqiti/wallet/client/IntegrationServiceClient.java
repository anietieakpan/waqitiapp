package com.waqiti.wallet.client;

import com.waqiti.wallet.client.dto.*;
import com.waqiti.wallet.client.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "integration-service", 
    url = "${integration-service.url}",
    fallback = IntegrationServiceClientFallback.class
)
public interface IntegrationServiceClient {

    @PostMapping("/api/v1/wallets/create")
    CreateWalletResponse createWallet(@RequestBody CreateWalletRequest request);

    @PostMapping("/api/v1/wallets/balance")
    GetBalanceResponse getBalance(@RequestBody GetBalanceRequest request);

    @PostMapping("/api/v1/wallets/transfer")
    TransferResponse transfer(@RequestBody TransferRequest request);

    @PostMapping("/api/v1/wallets/deposit")
    DepositResponse deposit(@RequestBody DepositRequest request);

    @PostMapping("/api/v1/wallets/withdraw")
    WithdrawalResponse withdraw(@RequestBody WithdrawalRequest request);
}