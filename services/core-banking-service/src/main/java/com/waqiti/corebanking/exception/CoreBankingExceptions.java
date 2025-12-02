package com.waqiti.corebanking.exception;

/**
 * Core Banking Exception Classes
 */

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String message) {
        super(message);
    }
}

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}

public class TransactionValidationException extends RuntimeException {
    public TransactionValidationException(String message) {
        super(message);
    }
}

public class DoubleEntryValidationException extends RuntimeException {
    public DoubleEntryValidationException(String message) {
        super(message);
    }
}

public class TransactionLockException extends RuntimeException {
    public TransactionLockException(String message) {
        super(message);
    }
}

public class FraudDetectedException extends RuntimeException {
    public FraudDetectedException(String message) {
        super(message);
    }
}

public class ComplianceViolationException extends RuntimeException {
    public ComplianceViolationException(String message) {
        super(message);
    }
}