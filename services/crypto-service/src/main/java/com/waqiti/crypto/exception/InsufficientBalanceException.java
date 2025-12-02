/**
 * Insufficient Balance Exception
 * Thrown when user has insufficient balance for a transaction
 */
package com.waqiti.crypto.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientBalanceException extends CryptoServiceException {
    
    public InsufficientBalanceException(UUID walletId, BigDecimal requested, BigDecimal available) {
        super("INSUFFICIENT_BALANCE", 
              "Insufficient balance in wallet " + walletId + ". Requested: " + requested + ", Available: " + available,
              walletId, requested, available);
    }
    
    public InsufficientBalanceException(String currency, BigDecimal requested, BigDecimal available) {
        super("INSUFFICIENT_BALANCE", 
              "Insufficient " + currency + " balance. Requested: " + requested + ", Available: " + available,
              currency, requested, available);
    }
}