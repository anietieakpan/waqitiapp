package com.waqiti.payment.api;

import com.waqiti.common.exception.ErrorResponse;
import com.waqiti.common.exception.GlobalExceptionHandler;
import com.waqiti.payment.domain.*;
import com.waqiti.payment.exception.*;
import com.waqiti.payment.security.ErrorMessageSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

/**
 * SECURITY ENHANCED: Payment service specific exception handler with message sanitization
 * Prevents sensitive information leakage through error messages
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceExceptionHandler extends GlobalExceptionHandler {
    
    private final ErrorMessageSanitizer errorMessageSanitizer;

    @ExceptionHandler(PaymentRequestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePaymentRequestNotFound(PaymentRequestNotFoundException ex, WebRequest request) {
        // SECURITY FIX: Sanitize error message to prevent information leakage
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.warn("SECURITY: Sanitized payment request not found error - Original logged separately");
        return buildErrorResponse(HttpStatus.NOT_FOUND, sanitizedMessage, request);
    }

    @ExceptionHandler(ScheduledPaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleScheduledPaymentNotFound(ScheduledPaymentNotFoundException ex, WebRequest request) {
        // SECURITY FIX: Sanitize error message to prevent information leakage
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.warn("SECURITY: Sanitized scheduled payment not found error - Original logged separately");
        return buildErrorResponse(HttpStatus.NOT_FOUND, sanitizedMessage, request);
    }

    @ExceptionHandler(SplitPaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleSplitPaymentNotFound(SplitPaymentNotFoundException ex, WebRequest request) {
        // SECURITY FIX: Sanitize error message to prevent information leakage
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.warn("SECURITY: Sanitized split payment not found error - Original logged separately");
        return buildErrorResponse(HttpStatus.NOT_FOUND, sanitizedMessage, request);
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ErrorResponse> handlePaymentFailed(PaymentFailedException ex, WebRequest request) {
        // SECURITY FIX: Sanitize payment failure message using operation-specific sanitization
        String sanitizedMessage = errorMessageSanitizer.sanitizePaymentError("payment_process", ex, ex.getMessage());
        log.warn("SECURITY: Sanitized payment failed error - Original logged separately");
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, sanitizedMessage, request);
    }

    @ExceptionHandler(InvalidPaymentOperationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaymentOperation(InvalidPaymentOperationException ex, WebRequest request) {
        // SECURITY FIX: Sanitize error message to prevent information leakage
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.warn("SECURITY: Sanitized invalid payment operation error - Original logged separately");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, sanitizedMessage, request);
    }

    @ExceptionHandler(InvalidPaymentStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaymentStatus(InvalidPaymentStatusException ex, WebRequest request) {
        // SECURITY FIX: Sanitize error message to prevent information leakage
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.warn("SECURITY: Sanitized invalid payment status error - Original logged separately");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, sanitizedMessage, request);
    }

    @ExceptionHandler(PaymentLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handlePaymentLimitExceeded(PaymentLimitExceededException ex, WebRequest request) {
        // SECURITY FIX: Sanitize error message to prevent information leakage
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.warn("SECURITY: Sanitized payment limit exceeded error - Original logged separately");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, sanitizedMessage, request);
    }
    
    // SECURITY FIX: Additional exception handlers for payment-specific exceptions
    
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex, WebRequest request) {
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.warn("SECURITY: Sanitized insufficient funds error - Original logged separately");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, sanitizedMessage, request);
    }
    
    @ExceptionHandler(FraudDetectedException.class)
    public ResponseEntity<ErrorResponse> handleFraudDetected(FraudDetectedException ex, WebRequest request) {
        String sanitizedMessage = errorMessageSanitizer.sanitizePaymentError("fraud_check", ex, ex.getMessage());
        log.warn("SECURITY: Sanitized fraud detection error - Original logged separately");
        return buildErrorResponse(HttpStatus.FORBIDDEN, sanitizedMessage, request);
    }
    
    @ExceptionHandler(ComplianceException.class)
    public ResponseEntity<ErrorResponse> handleComplianceException(ComplianceException ex, WebRequest request) {
        String sanitizedMessage = errorMessageSanitizer.sanitizePaymentError("compliance_check", ex, ex.getMessage());
        log.warn("SECURITY: Sanitized compliance error - Original logged separately");
        return buildErrorResponse(HttpStatus.FORBIDDEN, sanitizedMessage, request);
    }
    
    @ExceptionHandler(WalletNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleWalletNotFound(WalletNotFoundException ex, WebRequest request) {
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.warn("SECURITY: Sanitized wallet not found error - Original logged separately");
        return buildErrorResponse(HttpStatus.NOT_FOUND, sanitizedMessage, request);
    }
    
    @ExceptionHandler(WalletLockedException.class)
    public ResponseEntity<ErrorResponse> handleWalletLocked(WalletLockedException ex, WebRequest request) {
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.warn("SECURITY: Sanitized wallet locked error - Original logged separately");
        return buildErrorResponse(HttpStatus.LOCKED, sanitizedMessage, request);
    }
    
    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailableException ex, WebRequest request) {
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.warn("SECURITY: Sanitized service unavailable error - Original logged separately");
        return buildErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, sanitizedMessage, request);
    }
    
    @ExceptionHandler(PaymentProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePaymentProcessing(PaymentProcessingException ex, WebRequest request) {
        String sanitizedMessage = errorMessageSanitizer.sanitizePaymentError("payment_process", ex, ex.getMessage());
        log.warn("SECURITY: Sanitized payment processing error - Original logged separately");
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, sanitizedMessage, request);
    }
    
    @ExceptionHandler(ACHTransferException.class)
    public ResponseEntity<ErrorResponse> handleACHTransfer(ACHTransferException ex, WebRequest request) {
        String sanitizedMessage = errorMessageSanitizer.sanitizePaymentError("ach_transfer", ex, ex.getMessage());
        log.warn("SECURITY: Sanitized ACH transfer error - Original logged separately");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, sanitizedMessage, request);
    }
    
    @ExceptionHandler(CheckDepositException.class)
    public ResponseEntity<ErrorResponse> handleCheckDeposit(CheckDepositException ex, WebRequest request) {
        String sanitizedMessage = errorMessageSanitizer.sanitizePaymentError("check_deposit", ex, ex.getMessage());
        log.warn("SECURITY: Sanitized check deposit error - Original logged separately");
        return buildErrorResponse(HttpStatus.BAD_REQUEST, sanitizedMessage, request);
    }
    
    @ExceptionHandler(KYCVerificationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleKYCVerificationRequired(KYCVerificationRequiredException ex, WebRequest request) {
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.warn("SECURITY: Sanitized KYC verification error - Original logged separately");
        return buildErrorResponse(HttpStatus.FORBIDDEN, sanitizedMessage, request);
    }
    
    @ExceptionHandler(CryptographyException.class)
    public ResponseEntity<ErrorResponse> handleCryptographyException(CryptographyException ex, WebRequest request) {
        // CRITICAL: Never expose cryptography error details
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, "Security processing error");
        log.warn("SECURITY: Sanitized cryptography error - Original logged separately");
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, sanitizedMessage, request);
    }
    
    /**
     * SECURITY FIX: Generic fallback handler for any unexpected exceptions
     * Ensures no sensitive information leaks through unhandled exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex, WebRequest request) {
        String sanitizedMessage = errorMessageSanitizer.sanitizeErrorMessage(ex, ex.getMessage());
        log.error("SECURITY: Unexpected exception occurred - Sanitized message returned to client", ex);
        return buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, sanitizedMessage, request);
    }
}