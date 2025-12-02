/**
 * Invalid Address Exception
 * Thrown when a cryptocurrency address is invalid
 */
package com.waqiti.crypto.exception;

public class InvalidAddressException extends CryptoServiceException {
    
    public InvalidAddressException(String address, String currency) {
        super("INVALID_ADDRESS", "Invalid " + currency + " address: " + address, address, currency);
    }
    
    public InvalidAddressException(String address, String currency, String reason) {
        super("INVALID_ADDRESS", "Invalid " + currency + " address: " + address + ". Reason: " + reason, address, currency, reason);
    }
}