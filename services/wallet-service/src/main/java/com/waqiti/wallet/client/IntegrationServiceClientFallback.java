package com.waqiti.wallet.client;

import com.waqiti.wallet.client.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class IntegrationServiceClientFallback implements IntegrationServiceClient {

    @Override
    public CreateWalletResponse createWallet(CreateWalletRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING wallet creation - Integration Service unavailable. " +
                "User: {}", request.getUserId());
        
        return CreateWalletResponse.builder()
                .success(false)
                .walletId(null)
                .status("FAILED")
                .message("Wallet creation temporarily unavailable - integration service down")
                .errorCode("INTEGRATION_SERVICE_UNAVAILABLE")
                .build();
    }

    @Override
    public GetBalanceResponse getBalance(GetBalanceRequest request) {
        log.warn("FALLBACK ACTIVATED: Cannot retrieve balance - Integration Service unavailable. " +
                "Wallet: {}", request.getWalletId());
        
        return GetBalanceResponse.builder()
                .walletId(request.getWalletId())
                .balance(null)
                .currency(null)
                .status("UNAVAILABLE")
                .message("Balance temporarily unavailable")
                .isStale(true)
                .build();
    }

    @Override
    public TransferResponse transfer(TransferRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING transfer - Integration Service unavailable. " +
                "From: {}, To: {}, Amount: {}", 
                request.getFromWallet(), request.getToWallet(), request.getAmount());
        
        // CRITICAL: Block all transfers when integration unavailable
        return TransferResponse.builder()
                .success(false)
                .transactionId(null)
                .status("BLOCKED")
                .message("Transfer blocked - integration service temporarily unavailable")
                .errorCode("TRANSFER_SERVICE_UNAVAILABLE")
                .timestamp(LocalDateTime.now())
                .build();
    }

    @Override
    public DepositResponse deposit(DepositRequest request) {
        log.warn("FALLBACK ACTIVATED: QUEUING deposit - Integration Service unavailable. " +
                "Wallet: {}, Amount: {}", request.getWalletId(), request.getAmount());
        
        // Queue deposits for processing when service recovers
        return DepositResponse.builder()
                .success(true)
                .transactionId("QUEUED-" + UUID.randomUUID())
                .status("QUEUED")
                .message("Deposit queued for processing - will be credited when service recovers")
                .queuedAmount(request.getAmount())
                .estimatedProcessingTime("Within 4 hours")
                .build();
    }

    @Override
    public WithdrawalResponse withdraw(WithdrawalRequest request) {
        log.error("FALLBACK ACTIVATED: BLOCKING withdrawal - Integration Service unavailable. " +
                "Wallet: {}, Amount: {}", request.getWalletId(), request.getAmount());
        
        // CRITICAL: Block all withdrawals when integration unavailable
        return WithdrawalResponse.builder()
                .success(false)
                .transactionId(null)
                .status("BLOCKED")
                .message("Withdrawal blocked - integration service temporarily unavailable")
                .errorCode("WITHDRAWAL_SERVICE_UNAVAILABLE")
                .build();
    }
}