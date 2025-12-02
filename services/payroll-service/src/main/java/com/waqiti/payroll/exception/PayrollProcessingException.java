package com.waqiti.payroll.exception;

/**
 * Payroll Processing Exception
 *
 * Thrown when payroll processing encounters a recoverable error.
 * Typically triggers retry logic.
 */
public class PayrollProcessingException extends RuntimeException {

    private final String payrollBatchId;
    private final String errorCode;

    public PayrollProcessingException(String message) {
        super(message);
        this.payrollBatchId = null;
        this.errorCode = "PAYROLL_ERROR";
    }

    public PayrollProcessingException(String message, Throwable cause) {
        super(message, cause);
        this.payrollBatchId = null;
        this.errorCode = "PAYROLL_ERROR";
    }

    public PayrollProcessingException(String message, String payrollBatchId) {
        super(message);
        this.payrollBatchId = payrollBatchId;
        this.errorCode = "PAYROLL_ERROR";
    }

    public PayrollProcessingException(String message, String payrollBatchId, String errorCode) {
        super(message);
        this.payrollBatchId = payrollBatchId;
        this.errorCode = errorCode;
    }

    public PayrollProcessingException(String message, String payrollBatchId, Throwable cause) {
        super(message, cause);
        this.payrollBatchId = payrollBatchId;
        this.errorCode = "PAYROLL_ERROR";
    }

    public String getPayrollBatchId() {
        return payrollBatchId;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
