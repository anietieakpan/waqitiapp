package com.waqiti.wallet.service;

import com.waqiti.wallet.domain.Wallet;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Service interface for integrating with external wallet providers
 */
public interface IntegrationService {

    /**
     * Creates a wallet in the external system
     *
     * @param userId      The user ID
     * @param walletType  The type of wallet (INTERNAL, etc.)
     * @param accountType The type of account
     * @param currency    The currency code
     * @return The external ID of the created wallet
     */
    String createWallet(UUID userId, String walletType, String accountType, String currency);

    /**
     * Retrieves the current balance for a wallet from the external system
     *
     * @param wallet The wallet entity
     * @return The current balance
     */
    BigDecimal getWalletBalance(Wallet wallet);

    /**
     * Transfers money between wallets
     *
     * @param sourceWallet The source wallet
     * @param targetWallet The target wallet
     * @param amount       The amount to transfer
     * @return The external transaction ID
     */
    String transferBetweenWallets(Wallet sourceWallet, Wallet targetWallet, BigDecimal amount);

    /**
     * Deposits money into a wallet
     *
     * @param wallet The target wallet
     * @param amount The amount to deposit
     * @return The external transaction ID
     */
    String depositToWallet(Wallet wallet, BigDecimal amount);

    /**
     * Withdraws money from a wallet
     *
     * @param wallet The source wallet
     * @param amount The amount to withdraw
     * @return The external transaction ID
     */
    String withdrawFromWallet(Wallet wallet, BigDecimal amount);
    
    /**
     * SECURITY FIX: Performs dummy balance call for timing attack protection
     * Simulates network delay of real balance calls without actual external API call
     */
    void performDummyBalanceCall();
}