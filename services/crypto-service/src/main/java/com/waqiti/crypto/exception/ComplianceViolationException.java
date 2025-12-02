/**
 * Compliance Violation Exception
 * Thrown when a transaction violates compliance rules
 */
package com.waqiti.crypto.exception;

public class ComplianceViolationException extends CryptoServiceException {
    
    public ComplianceViolationException(String reason) {
        super("COMPLIANCE_VIOLATION", "Transaction blocked due to compliance violation: " + reason, reason);
    }
    
    public ComplianceViolationException(String address, String reason) {
        super("COMPLIANCE_VIOLATION", "Address " + address + " blocked due to compliance violation: " + reason, address, reason);
    }
    
    public ComplianceViolationException(String reason, boolean requiresReporting) {
        super("COMPLIANCE_VIOLATION", 
              "Transaction blocked due to compliance violation: " + reason + 
              (requiresReporting ? " (requires regulatory reporting)" : ""), 
              reason, requiresReporting);
    }
}