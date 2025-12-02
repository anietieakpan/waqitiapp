package com.waqiti.payment.service;

import com.waqiti.payment.dto.MobileMoneyTransferRequest;
import com.waqiti.payment.dto.MobileMoneyTransferResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Mobile Wallet Service Interface
 * 
 * Provides mobile wallet management functionality including
 * balance validation, wallet operations, and provider integration.
 */
@Service
public interface MobileWalletService {

    /**
     * Validates provider credentials for mobile wallet operations
     */
    void validateProviderCredentials(String provider, String userId);

    /**
     * Validates sender wallet balance
     */
    void validateSenderBalance(String senderId, BigDecimal amount, String currency);

    /**
     * Validates receiver wallet status and capabilities
     */
    void validateReceiverWallet(String receiverId, String currency);

    /**
     * Processes wallet-to-wallet transfer
     */
    MobileMoneyTransferResult processWalletToWalletTransfer(MobileMoneyTransferRequest request);

    /**
     * Deducts fees from user's wallet
     */
    void deductFees(String userId, BigDecimal fees, String currency);

    /**
     * Initiates settlement with mobile money provider
     */
    void initiateProviderSettlement(MobileMoneyTransferRequest request, MobileMoneyTransferResult result);

    /**
     * Checks if user has loyalty program
     */
    boolean hasLoyaltyProgram(String userId);

    /**
     * Updates loyalty points for user
     */
    void updateLoyaltyPoints(String userId, BigDecimal amount);

    /**
     * Checks if email notifications are enabled for user
     */
    boolean hasEmailNotificationEnabled(String userId);
}