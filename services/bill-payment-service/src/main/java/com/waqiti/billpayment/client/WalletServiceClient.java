package com.waqiti.billpayment.client;

import com.waqiti.billpayment.client.dto.WalletDebitRequest;
import com.waqiti.billpayment.client.dto.WalletDebitResponse;
import com.waqiti.billpayment.client.dto.WalletCreditRequest;
import com.waqiti.billpayment.client.dto.WalletCreditResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for Wallet Service
 * Handles wallet debit/credit operations for bill payments
 */
@FeignClient(
    name = "wallet-service",
    url = "${wallet.service.url:http://localhost:8091}",
    configuration = WalletServiceClientConfig.class
)
public interface WalletServiceClient {

    /**
     * Debit amount from user's wallet
     */
    @PostMapping("/api/v1/wallet/debit")
    WalletDebitResponse debit(@RequestBody WalletDebitRequest request);

    /**
     * Credit amount to user's wallet (refund/compensation)
     */
    @PostMapping("/api/v1/wallet/credit")
    WalletCreditResponse credit(@RequestBody WalletCreditRequest request);
}
