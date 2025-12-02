package com.waqiti.corebanking.exception;

/**
 * Comprehensive exception hierarchy for BankAccount domain operations in core banking.
 * Provides specific, actionable error handling for all business rule violations
 * and operational scenarios in enterprise bank account management.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
public class BankAccountExceptions {
    
    /**
     * Base exception for all bank account related errors
     */
    public static abstract class BankAccountException extends RuntimeException {
        public BankAccountException(String message) {
            super(message);
        }
        
        public BankAccountException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    /**
     * Invalid account number format or value
     */
    public static class InvalidAccountNumberException extends BankAccountException {
        public InvalidAccountNumberException(String message) {
            super(message);
        }
    }
    
    /**
     * Invalid routing number format or value
     */
    public static class InvalidRoutingNumberException extends BankAccountException {
        public InvalidRoutingNumberException(String message) {
            super(message);
        }
    }
    
    /**
     * Account is not in valid status for requested operation
     */
    public static class InvalidAccountStatusException extends BankAccountException {
        public InvalidAccountStatusException(String message) {
            super(message);
        }
    }
    
    /**
     * Invalid verification amounts provided
     */
    public static class InvalidVerificationAmountException extends BankAccountException {
        public InvalidVerificationAmountException(String message) {
            super(message);
        }
    }
    
    /**
     * Maximum verification attempts exceeded
     */
    public static class MaxVerificationAttemptsExceededException extends BankAccountException {
        public MaxVerificationAttemptsExceededException(String message) {
            super(message);
        }
    }
    
    /**
     * Verification deadline has passed
     */
    public static class VerificationExpiredException extends BankAccountException {
        public VerificationExpiredException(String message) {
            super(message);
        }
    }
    
    /**
     * Bank account not found
     */
    public static class BankAccountNotFoundException extends BankAccountException {
        public BankAccountNotFoundException(String message) {
            super(message);
        }
    }
    
    /**
     * Compliance violation prevents operation
     */
    public static class ComplianceViolationException extends BankAccountException {
        public ComplianceViolationException(String message) {
            super(message);
        }
    }
}