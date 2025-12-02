/**
 * Wallet Not Found Exception
 * Thrown when a requested wallet cannot be found
 */
package com.waqiti.crypto.exception;

import java.util.UUID;

public class WalletNotFoundException extends CryptoServiceException {
    
    public WalletNotFoundException(UUID walletId) {
        super("WALLET_NOT_FOUND", "Wallet not found: " + walletId, walletId);
    }
    
    public WalletNotFoundException(UUID userId, String currency) {
        super("WALLET_NOT_FOUND", "Wallet not found for user " + userId + " and currency " + currency, userId, currency);
    }
}