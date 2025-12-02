package com.waqiti.payment.exception;

/**
 * CRITICAL: PCI DSS Compliance Exception
 * 
 * This exception is thrown when operations violate PCI DSS requirements.
 * 
 * @author Waqiti Security Team
 * @since 1.0.0
 */
public class PCIComplianceException extends RuntimeException {
    
    public PCIComplianceException(String message) {
        super(message);
    }
    
    public PCIComplianceException(String message, Throwable cause) {
        super(message, cause);
    }
}