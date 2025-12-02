package com.waqiti.business.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

// Business Account Exceptions
@ResponseStatus(HttpStatus.NOT_FOUND)
public class BusinessAccountNotFoundException extends RuntimeException {
    public BusinessAccountNotFoundException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateBusinessAccountException extends RuntimeException {
    public DuplicateBusinessAccountException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.FORBIDDEN)
public class UnauthorizedBusinessAccessException extends RuntimeException {
    public UnauthorizedBusinessAccessException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.FORBIDDEN)
public class InsufficientBusinessPermissionsException extends RuntimeException {
    public InsufficientBusinessPermissionsException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTierUpgradeException extends RuntimeException {
    public InvalidTierUpgradeException(String message) { super(message); }
}

// Sub-account Exceptions
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class SubAccountLimitExceededException extends RuntimeException {
    public SubAccountLimitExceededException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.NOT_FOUND)
public class SubAccountNotFoundException extends RuntimeException {
    public SubAccountNotFoundException(String message) { super(message); }
}

// Employee Exceptions
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EmployeeNotFoundException extends RuntimeException {
    public EmployeeNotFoundException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateEmployeeException extends RuntimeException {
    public DuplicateEmployeeException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class EmployeeLimitExceededException extends RuntimeException {
    public EmployeeLimitExceededException(String message) { super(message); }
}

// Expense Exceptions
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ExpenseNotFoundException extends RuntimeException {
    public ExpenseNotFoundException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidExpenseStatusException extends RuntimeException {
    public InvalidExpenseStatusException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ExpenseLimitExceededException extends RuntimeException {
    public ExpenseLimitExceededException(String message) { super(message); }
}

// Invoice Exceptions
@ResponseStatus(HttpStatus.NOT_FOUND)
public class InvoiceNotFoundException extends RuntimeException {
    public InvoiceNotFoundException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidInvoiceStatusException extends RuntimeException {
    public InvalidInvoiceStatusException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateInvoiceNumberException extends RuntimeException {
    public DuplicateInvoiceNumberException(String message) { super(message); }
}

// Team Management Exceptions
@ResponseStatus(HttpStatus.CONFLICT)
public class TeamMemberExistsException extends RuntimeException {
    public TeamMemberExistsException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.NOT_FOUND)
public class BusinessRoleNotFoundException extends RuntimeException {
    public BusinessRoleNotFoundException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BusinessLimitExceededException extends RuntimeException {
    public BusinessLimitExceededException(String message) { super(message); }
}

// Compliance and Verification Exceptions
@ResponseStatus(HttpStatus.FORBIDDEN)
public class BusinessComplianceException extends RuntimeException {
    public BusinessComplianceException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BusinessVerificationException extends RuntimeException {
    public BusinessVerificationException(String message) { super(message); }
}

// Tax Document Exceptions
@ResponseStatus(HttpStatus.NOT_FOUND)
public class TaxDocumentNotFoundException extends RuntimeException {
    public TaxDocumentNotFoundException(String message) { super(message); }
}

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidTaxDocumentException extends RuntimeException {
    public InvalidTaxDocumentException(String message) { super(message); }
}