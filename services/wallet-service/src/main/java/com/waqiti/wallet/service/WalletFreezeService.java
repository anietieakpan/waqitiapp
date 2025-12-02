package com.waqiti.wallet.service;

import com.waqiti.common.events.AccountFreezeRequestEvent;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Wallet Freeze Service Interface
 * 
 * Handles wallet-specific freeze operations including balance restrictions,
 * transaction blocking, and compliance controls
 */
public interface WalletFreezeService {
    
    /**
     * Freeze all wallets for a user immediately
     */
    List<String> freezeAllWalletsImmediately(UUID userId, List<String> walletIds, 
                                           String freezeReason, String severity);
    
    /**
     * Freeze wallets completely (all operations blocked)
     */
    List<String> freezeWalletsCompletely(UUID userId, List<String> walletIds, String freezeReason);
    
    /**
     * Freeze only debit operations for specified wallets
     */
    List<String> freezeWalletDebits(UUID userId, List<String> walletIds, String freezeReason);
    
    /**
     * Freeze high-value transactions only
     */
    List<String> freezeHighValueTransactions(UUID userId, List<String> walletIds, BigDecimal threshold);
    
    /**
     * Freeze wallets by specified scope
     */
    List<String> freezeWalletsByScope(UUID userId, List<String> walletIds, 
                                    AccountFreezeRequestEvent.FreezeScope scope);
    
    /**
     * Apply wallet restrictions with review date
     */
    List<String> applyWalletRestrictions(UUID userId, List<String> walletIds,
                                       AccountFreezeRequestEvent.FreezeScope scope,
                                       LocalDateTime reviewDate);
    
    /**
     * Freeze a specific wallet
     */
    void freezeSpecificWallet(String walletId, UUID userId, String freezeReason,
                            AccountFreezeRequestEvent.FreezeScope scope, String caseId);
    
    /**
     * Freeze all user balances
     */
    void freezeAllBalances(UUID userId, String freezeReason);
    
    /**
     * Freeze outgoing balances only
     */
    void freezeOutgoingBalances(UUID userId);
    
    /**
     * Freeze high-value balances above threshold
     */
    void freezeHighValueBalances(UUID userId, BigDecimal threshold);
    
    /**
     * Freeze specific currency balances
     */
    void freezeSpecificCurrencyBalances(UUID userId, List<String> currencies);
}