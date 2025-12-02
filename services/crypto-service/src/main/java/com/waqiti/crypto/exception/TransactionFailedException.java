/**
 * Transaction Failed Exception
 * Thrown when a cryptocurrency transaction fails
 */
package com.waqiti.crypto.exception;

import java.util.UUID;

public class TransactionFailedException extends CryptoServiceException {
    
    public TransactionFailedException(UUID transactionId, String reason) {
        super("TRANSACTION_FAILED", "Transaction " + transactionId + " failed: " + reason, transactionId, reason);
    }
    
    public TransactionFailedException(String txHash, String reason) {
        super("TRANSACTION_FAILED", "Transaction " + txHash + " failed: " + reason, txHash, reason);
    }
    
    public TransactionFailedException(String reason, Throwable cause) {
        super("TRANSACTION_FAILED", "Transaction failed: " + reason, cause);
    }
}