package com.waqiti.wallet.service;

import com.waqiti.wallet.client.IntegrationServiceClient;
import com.waqiti.wallet.client.dto.*;
import com.waqiti.wallet.client.dto.*;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.domain.TransactionFailedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeignIntegrationService implements IntegrationService {
    private final IntegrationServiceClient integrationClient;

    @Override
    @CircuitBreaker(name = "integrationService", fallbackMethod = "createWalletFallback")
    @Retry(name = "integrationService")
    public String createWallet(UUID userId, String walletType, String accountType, String currency) {
        log.info("Creating wallet in external system for user: {}, type: {}, currency: {}",
                userId, walletType, currency);

        try {
            CreateWalletRequest request = CreateWalletRequest.builder()
                    .userId(userId)
                    .walletType(walletType)
                    .accountType(accountType)
                    .currency(currency)
                    .build();

            CreateWalletResponse response = integrationClient.createWallet(request);
            log.info("Wallet created in external system with ID: {}", response.getExternalId());

            return response.getExternalId();
        } catch (Exception e) {
            log.error("Error creating wallet in external system", e);
            throw new TransactionFailedException("Failed to create wallet in external system", e);
        }
    }

    private String createWalletFallback(UUID userId, String walletType, String accountType,
                                        String currency, Throwable t) {
        log.warn("Fallback for createWallet executed due to: {}", t.getMessage());
        throw new TransactionFailedException("External service unavailable. Please try again later.");
    }

    @Override
    @CircuitBreaker(name = "integrationService", fallbackMethod = "getWalletBalanceFallback")
    @Retry(name = "integrationService")
    public BigDecimal getWalletBalance(Wallet wallet) {
        log.info("Getting balance from external system for wallet: {}, type: {}",
                wallet.getId(), wallet.getWalletType());

        try {
            GetBalanceRequest request = GetBalanceRequest.builder()
                    .externalId(wallet.getExternalId())
                    .walletType(wallet.getWalletType())
                    .build();

            GetBalanceResponse response = integrationClient.getBalance(request);
            log.info("Retrieved balance from external system: {}", response.getBalance());

            return response.getBalance();
        } catch (Exception e) {
            log.error("Error getting balance from external system", e);
            // Return the last known balance
            return wallet.getBalance();
        }
    }

    private BigDecimal getWalletBalanceFallback(Wallet wallet, Throwable t) {
        log.warn("Fallback for getWalletBalance executed due to: {}", t.getMessage());
        // Return the last known balance
        return wallet.getBalance();
    }

    @Override
    @CircuitBreaker(name = "integrationService", fallbackMethod = "transferBetweenWalletsFallback")
    @Retry(name = "integrationService")
    public String transferBetweenWallets(Wallet sourceWallet, Wallet targetWallet, BigDecimal amount) {
        log.info("Transferring {} {} from wallet {} to wallet {} in external system",
                amount, sourceWallet.getCurrency(), sourceWallet.getId(), targetWallet.getId());

        try {
            TransferRequest request = TransferRequest.builder()
                    .sourceExternalId(sourceWallet.getExternalId())
                    .sourceWalletType(sourceWallet.getWalletType())
                    .targetExternalId(targetWallet.getExternalId())
                    .targetWalletType(targetWallet.getWalletType())
                    .amount(amount)
                    .currency(sourceWallet.getCurrency())
                    .build();

            TransferResponse response = integrationClient.transfer(request);
            log.info("Transfer completed in external system with ID: {}", response.getExternalId());

            return response.getExternalId();
        } catch (Exception e) {
            log.error("Error transferring between wallets in external system", e);
            throw new TransactionFailedException("Failed to transfer between wallets in external system", e);
        }
    }

    private String transferBetweenWalletsFallback(Wallet sourceWallet, Wallet targetWallet,
                                                  BigDecimal amount, Throwable t) {
        log.warn("Fallback for transferBetweenWallets executed due to: {}", t.getMessage());
        throw new TransactionFailedException("External service unavailable. Please try again later.");
    }

    @Override
    @CircuitBreaker(name = "integrationService", fallbackMethod = "depositToWalletFallback")
    @Retry(name = "integrationService")
    public String depositToWallet(Wallet wallet, BigDecimal amount) {
        log.info("Depositing {} {} to wallet {} in external system",
                amount, wallet.getCurrency(), wallet.getId());

        try {
            DepositRequest request = DepositRequest.builder()
                    .externalId(wallet.getExternalId())
                    .walletType(wallet.getWalletType())
                    .amount(amount)
                    .currency(wallet.getCurrency())
                    .build();

            DepositResponse response = integrationClient.deposit(request);
            log.info("Deposit completed in external system with ID: {}", response.getExternalId());

            return response.getExternalId();
        } catch (Exception e) {
            log.error("Error depositing to wallet in external system", e);
            throw new TransactionFailedException("Failed to deposit to wallet in external system", e);
        }
    }

    private String depositToWalletFallback(Wallet wallet, BigDecimal amount, Throwable t) {
        log.warn("Fallback for depositToWallet executed due to: {}", t.getMessage());
        throw new TransactionFailedException("External service unavailable. Please try again later.");
    }

    @Override
    @CircuitBreaker(name = "integrationService", fallbackMethod = "withdrawFromWalletFallback")
    @Retry(name = "integrationService")
    public String withdrawFromWallet(Wallet wallet, BigDecimal amount) {
        log.info("Withdrawing {} {} from wallet {} in external system",
                amount, wallet.getCurrency(), wallet.getId());

        try {
            WithdrawalRequest request = WithdrawalRequest.builder()
                    .externalId(wallet.getExternalId())
                    .walletType(wallet.getWalletType())
                    .amount(amount)
                    .currency(wallet.getCurrency())
                    .build();

            WithdrawalResponse response = integrationClient.withdraw(request);
            log.info("Withdrawal completed in external system with ID: {}", response.getExternalId());

            return response.getExternalId();
        } catch (Exception e) {
            log.error("Error withdrawing from wallet in external system", e);
            throw new TransactionFailedException("Failed to withdraw from wallet in external system", e);
        }
    }

    private String withdrawFromWalletFallback(Wallet wallet, BigDecimal amount, Throwable t) {
        log.warn("Fallback for withdrawFromWallet executed due to: {}", t.getMessage());
        throw new TransactionFailedException("External service unavailable. Please try again later.");
    }
    
    @Override
    public void performDummyBalanceCall() {
        try {
            log.debug("SECURITY: Performing dummy balance call for timing attack protection");
            
            // Simulate the same network delay as real balance calls
            // Create dummy request objects that would be used in real calls
            GetBalanceRequest dummyRequest = GetBalanceRequest.builder()
                .externalId("dummy-" + UUID.randomUUID())
                .walletType("DUMMY")
                .build();
            
            // Simulate the time it takes to construct and serialize the request
            Thread.sleep(5); // Small delay for request construction
            
            // Simulate network timeout (shorter than real call to prevent actual delay)
            // Real balance calls typically take 20-100ms, simulate 30ms
            Thread.sleep(30);
            
            // Simulate response processing
            BigDecimal dummyBalance = BigDecimal.ZERO;
            
            log.debug("SECURITY: Completed dummy balance call simulation");
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("SECURITY: Dummy balance call interrupted");
        } catch (Exception e) {
            log.debug("SECURITY: Dummy balance call failed (expected for timing protection)", e);
        }
    }
}