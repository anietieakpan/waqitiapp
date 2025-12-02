package com.waqiti.payment.commons.exception;

import com.waqiti.payment.commons.domain.PaymentStatus;
import lombok.Getter;

import java.util.Map;

/**
 * Base exception for all payment-related errors
 */
@Getter
public class PaymentException extends RuntimeException {
    
    private final String errorCode;
    private final PaymentStatus suggestedStatus;
    private final Map<String, Object> errorContext;
    private final boolean retryable;
    
    public PaymentException(String message) {
        super(message);
        this.errorCode = "PAYMENT_ERROR";
        this.suggestedStatus = PaymentStatus.FAILED;
        this.errorContext = null;
        this.retryable = false;
    }
    
    public PaymentException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "PAYMENT_ERROR";
        this.suggestedStatus = PaymentStatus.FAILED;
        this.errorContext = null;
        this.retryable = false;
    }
    
    public PaymentException(String errorCode, String message, PaymentStatus suggestedStatus) {
        super(message);
        this.errorCode = errorCode;
        this.suggestedStatus = suggestedStatus;
        this.errorContext = null;
        this.retryable = false;
    }
    
    public PaymentException(String errorCode, String message, PaymentStatus suggestedStatus, 
                           boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.suggestedStatus = suggestedStatus;
        this.errorContext = null;
        this.retryable = retryable;
    }
    
    public PaymentException(String errorCode, String message, PaymentStatus suggestedStatus, 
                           Map<String, Object> errorContext, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.suggestedStatus = suggestedStatus;
        this.errorContext = errorContext;
        this.retryable = retryable;
    }
    
    public PaymentException(String errorCode, String message, Throwable cause, 
                           PaymentStatus suggestedStatus, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.suggestedStatus = suggestedStatus;
        this.errorContext = null;
        this.retryable = retryable;
    }
    
    /**
     * Specific payment exception types
     */
    
    public static class InsufficientFundsException extends PaymentException {
        public InsufficientFundsException(String message) {
            super("INSUFFICIENT_FUNDS", message, PaymentStatus.DECLINED, false);
        }
    }
    
    public static class PaymentMethodNotSupportedException extends PaymentException {
        public PaymentMethodNotSupportedException(String message) {
            super("PAYMENT_METHOD_NOT_SUPPORTED", message, PaymentStatus.REJECTED, false);
        }
    }
    
    public static class InvalidAmountException extends PaymentException {
        public InvalidAmountException(String message) {
            super("INVALID_AMOUNT", message, PaymentStatus.REJECTED, false);
        }
    }
    
    public static class PaymentLimitExceededException extends PaymentException {
        public PaymentLimitExceededException(String message) {
            super("PAYMENT_LIMIT_EXCEEDED", message, PaymentStatus.REJECTED, false);
        }
    }
    
    public static class PaymentAuthorizationException extends PaymentException {
        public PaymentAuthorizationException(String message) {
            super("AUTHORIZATION_FAILED", message, PaymentStatus.DECLINED, false);
        }
        
        public PaymentAuthorizationException(String message, boolean retryable) {
            super("AUTHORIZATION_FAILED", message, PaymentStatus.DECLINED, retryable);
        }
    }
    
    public static class PaymentTimeoutException extends PaymentException {
        public PaymentTimeoutException(String message) {
            super("PAYMENT_TIMEOUT", message, PaymentStatus.TIMEOUT, true);
        }
    }
    
    public static class PaymentNetworkException extends PaymentException {
        public PaymentNetworkException(String message) {
            super("NETWORK_ERROR", message, PaymentStatus.ERROR, true);
        }
        
        public PaymentNetworkException(String message, Throwable cause) {
            super("NETWORK_ERROR", message, cause, PaymentStatus.ERROR, true);
        }
    }
    
    public static class FraudDetectedException extends PaymentException {
        public FraudDetectedException(String message) {
            super("FRAUD_DETECTED", message, PaymentStatus.FLAGGED, false);
        }
        
        public FraudDetectedException(String message, Map<String, Object> fraudContext) {
            super("FRAUD_DETECTED", message, PaymentStatus.FLAGGED, fraudContext, false);
        }
    }
    
    public static class ComplianceViolationException extends PaymentException {
        public ComplianceViolationException(String message) {
            super("COMPLIANCE_VIOLATION", message, PaymentStatus.UNDER_REVIEW, false);
        }
        
        public ComplianceViolationException(String message, Map<String, Object> complianceContext) {
            super("COMPLIANCE_VIOLATION", message, PaymentStatus.UNDER_REVIEW, complianceContext, false);
        }
    }
    
    public static class PaymentExpiredException extends PaymentException {
        public PaymentExpiredException(String message) {
            super("PAYMENT_EXPIRED", message, PaymentStatus.EXPIRED, false);
        }
    }
    
    public static class PaymentAlreadyProcessedException extends PaymentException {
        public PaymentAlreadyProcessedException(String message) {
            super("PAYMENT_ALREADY_PROCESSED", message, PaymentStatus.COMPLETED, false);
        }
    }
    
    public static class PaymentNotFoundException extends PaymentException {
        public PaymentNotFoundException(String message) {
            super("PAYMENT_NOT_FOUND", message, PaymentStatus.ERROR, false);
        }
    }
    
    public static class InvalidPaymentStatusException extends PaymentException {
        public InvalidPaymentStatusException(String message) {
            super("INVALID_PAYMENT_STATUS", message, PaymentStatus.ERROR, false);
        }
    }
    
    public static class PaymentConfigurationException extends PaymentException {
        public PaymentConfigurationException(String message) {
            super("PAYMENT_CONFIGURATION_ERROR", message, PaymentStatus.ERROR, false);
        }
        
        public PaymentConfigurationException(String message, Throwable cause) {
            super("PAYMENT_CONFIGURATION_ERROR", message, cause, PaymentStatus.ERROR, false);
        }
    }
    
    public static class ExternalServiceException extends PaymentException {
        public ExternalServiceException(String service, String message) {
            super("EXTERNAL_SERVICE_ERROR", 
                  String.format("External service %s error: %s", service, message), 
                  PaymentStatus.ERROR, true);
        }
        
        public ExternalServiceException(String service, String message, Throwable cause) {
            super("EXTERNAL_SERVICE_ERROR", 
                  String.format("External service %s error: %s", service, message), 
                  cause, PaymentStatus.ERROR, true);
        }
    }
    
    public static class RateLimitExceededException extends PaymentException {
        public RateLimitExceededException(String message) {
            super("RATE_LIMIT_EXCEEDED", message, PaymentStatus.ON_HOLD, true);
        }
    }
    
    public static class CurrencyNotSupportedException extends PaymentException {
        public CurrencyNotSupportedException(String currency) {
            super("CURRENCY_NOT_SUPPORTED", 
                  String.format("Currency %s is not supported", currency), 
                  PaymentStatus.REJECTED, false);
        }
    }
    
    public static class InvalidRecipientException extends PaymentException {
        public InvalidRecipientException(String message) {
            super("INVALID_RECIPIENT", message, PaymentStatus.REJECTED, false);
        }
    }
    
    public static class KYCRequiredException extends PaymentException {
        public KYCRequiredException(String message) {
            super("KYC_REQUIRED", message, PaymentStatus.UNDER_REVIEW, false);
        }
    }
    
    public static class GeographicRestrictionException extends PaymentException {
        public GeographicRestrictionException(String message) {
            super("GEOGRAPHIC_RESTRICTION", message, PaymentStatus.REJECTED, false);
        }
    }
    
    public static class DuplicatePaymentException extends PaymentException {
        public DuplicatePaymentException(String message) {
            super("DUPLICATE_PAYMENT", message, PaymentStatus.REJECTED, false);
        }
    }
    
    public static class PaymentCancelledException extends PaymentException {
        public PaymentCancelledException(String message) {
            super("PAYMENT_CANCELLED", message, PaymentStatus.CANCELLED, false);
        }
    }
    
    public static class RefundException extends PaymentException {
        public RefundException(String message) {
            super("REFUND_ERROR", message, PaymentStatus.FAILED, false);
        }
        
        public RefundException(String message, Throwable cause) {
            super("REFUND_ERROR", message, cause, PaymentStatus.FAILED, false);
        }
    }
    
    /**
     * Factory methods for common scenarios
     */
    
    public static PaymentException insufficientFunds(String accountId) {
        return new InsufficientFundsException(
            String.format("Insufficient funds in account %s", accountId)
        );
    }
    
    public static PaymentException paymentMethodNotSupported(String method, String currency) {
        return new PaymentMethodNotSupportedException(
            String.format("Payment method %s does not support currency %s", method, currency)
        );
    }
    
    public static PaymentException invalidAmount(String reason) {
        return new InvalidAmountException(String.format("Invalid payment amount: %s", reason));
    }
    
    public static PaymentException limitExceeded(String limitType, String limit) {
        return new PaymentLimitExceededException(
            String.format("%s limit of %s exceeded", limitType, limit)
        );
    }
    
    public static PaymentException authorizationFailed(String reason) {
        return new PaymentAuthorizationException(
            String.format("Payment authorization failed: %s", reason)
        );
    }
    
    public static PaymentException timeout(String operation) {
        return new PaymentTimeoutException(
            String.format("Payment operation timed out: %s", operation)
        );
    }
    
    public static PaymentException networkError(String service) {
        return new PaymentNetworkException(
            String.format("Network error communicating with %s", service)
        );
    }
    
    public static PaymentException fraudDetected(String riskScore) {
        return new FraudDetectedException(
            String.format("Fraud detected with risk score: %s", riskScore)
        );
    }
    
    public static PaymentException complianceViolation(String rule) {
        return new ComplianceViolationException(
            String.format("Compliance violation: %s", rule)
        );
    }
    
    public static PaymentException paymentExpired(String paymentId) {
        return new PaymentExpiredException(
            String.format("Payment %s has expired", paymentId)
        );
    }
    
    public static PaymentException alreadyProcessed(String paymentId) {
        return new PaymentAlreadyProcessedException(
            String.format("Payment %s has already been processed", paymentId)
        );
    }
    
    public static PaymentException notFound(String paymentId) {
        return new PaymentNotFoundException(
            String.format("Payment %s not found", paymentId)
        );
    }
    
    public static PaymentException invalidStatus(String currentStatus, String requiredStatus) {
        return new InvalidPaymentStatusException(
            String.format("Invalid payment status %s, required %s", currentStatus, requiredStatus)
        );
    }
    
    public static PaymentException configurationError(String component) {
        return new PaymentConfigurationException(
            String.format("Payment configuration error in %s", component)
        );
    }
    
    public static PaymentException externalServiceError(String service, String error) {
        return new ExternalServiceException(service, error);
    }
    
    public static PaymentException rateLimitExceeded(String rateLimitType) {
        return new RateLimitExceededException(
            String.format("Rate limit exceeded for %s", rateLimitType)
        );
    }
    
    public static PaymentException currencyNotSupported(String currency) {
        return new CurrencyNotSupportedException(currency);
    }
    
    public static PaymentException invalidRecipient(String reason) {
        return new InvalidRecipientException(
            String.format("Invalid recipient: %s", reason)
        );
    }
    
    public static PaymentException kycRequired(String requirement) {
        return new KYCRequiredException(
            String.format("KYC verification required: %s", requirement)
        );
    }
    
    public static PaymentException geographicRestriction(String restriction) {
        return new GeographicRestrictionException(
            String.format("Geographic restriction: %s", restriction)
        );
    }
    
    public static PaymentException duplicatePayment(String duplicateKey) {
        return new DuplicatePaymentException(
            String.format("Duplicate payment detected: %s", duplicateKey)
        );
    }
    
    public static PaymentException cancelled(String reason) {
        return new PaymentCancelledException(
            String.format("Payment cancelled: %s", reason)
        );
    }
    
    public static PaymentException refundFailed(String reason) {
        return new RefundException(
            String.format("Refund failed: %s", reason)
        );
    }
}