package com.waqiti.common.domain.exceptions;

import com.waqiti.common.domain.Money;
import com.waqiti.common.domain.valueobjects.PaymentId;
import com.waqiti.common.domain.valueobjects.UserId;

/**
 * Payment Domain Exceptions
 * Domain-specific exceptions for payment processing
 */
public class PaymentDomainExceptions {
    
    private static final String PAYMENT_DOMAIN = "PAYMENT";
    
    /**
     * Insufficient Funds Exception
     */
    public static class InsufficientFundsException extends DomainException {
        
        private final Money requestedAmount;
        private final Money availableAmount;
        
        public InsufficientFundsException(Money requestedAmount, Money availableAmount) {
            super(String.format("Insufficient funds: requested %s, available %s", 
                    requestedAmount, availableAmount),
                    "INSUFFICIENT_FUNDS",
                    PAYMENT_DOMAIN);
            this.requestedAmount = requestedAmount;
            this.availableAmount = availableAmount;
        }
        
        public Money getRequestedAmount() {
            return requestedAmount;
        }
        
        public Money getAvailableAmount() {
            return availableAmount;
        }
        
        public Money getShortfall() {
            return requestedAmount.subtract(availableAmount);
        }
    }
    
    /**
     * Payment Limit Exceeded Exception
     */
    public static class PaymentLimitExceededException extends DomainException {
        
        private final Money amount;
        private final Money limit;
        private final String limitType;
        
        public PaymentLimitExceededException(Money amount, Money limit, String limitType) {
            super(String.format("Payment limit exceeded: amount %s exceeds %s limit of %s", 
                    amount, limitType, limit),
                    "LIMIT_EXCEEDED",
                    PAYMENT_DOMAIN);
            this.amount = amount;
            this.limit = limit;
            this.limitType = limitType;
        }
        
        public Money getAmount() {
            return amount;
        }
        
        public Money getLimit() {
            return limit;
        }
        
        public String getLimitType() {
            return limitType;
        }
    }
    
    /**
     * Invalid Payment State Exception
     */
    public static class InvalidPaymentStateException extends DomainException {
        
        private final PaymentId paymentId;
        private final String currentState;
        private final String attemptedAction;
        
        public InvalidPaymentStateException(PaymentId paymentId, String currentState, String attemptedAction) {
            super(String.format("Invalid payment state: cannot %s payment %s in state %s", 
                    attemptedAction, paymentId, currentState),
                    "INVALID_STATE",
                    PAYMENT_DOMAIN);
            this.paymentId = paymentId;
            this.currentState = currentState;
            this.attemptedAction = attemptedAction;
        }
        
        public PaymentId getPaymentId() {
            return paymentId;
        }
        
        public String getCurrentState() {
            return currentState;
        }
        
        public String getAttemptedAction() {
            return attemptedAction;
        }
    }
    
    /**
     * Duplicate Payment Exception
     */
    public static class DuplicatePaymentException extends DomainException {
        
        private final String idempotencyKey;
        private final PaymentId existingPaymentId;
        
        public DuplicatePaymentException(String idempotencyKey, PaymentId existingPaymentId) {
            super(String.format("Duplicate payment detected with idempotency key: %s, existing payment: %s", 
                    idempotencyKey, existingPaymentId),
                    "DUPLICATE_PAYMENT",
                    PAYMENT_DOMAIN);
            this.idempotencyKey = idempotencyKey;
            this.existingPaymentId = existingPaymentId;
        }
        
        public String getIdempotencyKey() {
            return idempotencyKey;
        }
        
        public PaymentId getExistingPaymentId() {
            return existingPaymentId;
        }
    }
    
    /**
     * Payment Not Found Exception
     */
    public static class PaymentNotFoundException extends DomainException {
        
        private final PaymentId paymentId;
        
        public PaymentNotFoundException(PaymentId paymentId) {
            super(String.format("Payment not found: %s", paymentId),
                    "PAYMENT_NOT_FOUND",
                    PAYMENT_DOMAIN);
            this.paymentId = paymentId;
        }
        
        public PaymentId getPaymentId() {
            return paymentId;
        }
    }
    
    /**
     * Invalid Payment Amount Exception
     */
    public static class InvalidPaymentAmountException extends DomainException {
        
        private final Money amount;
        private final String reason;
        
        public InvalidPaymentAmountException(Money amount, String reason) {
            super(String.format("Invalid payment amount %s: %s", amount, reason),
                    "INVALID_AMOUNT",
                    PAYMENT_DOMAIN);
            this.amount = amount;
            this.reason = reason;
        }
        
        public Money getAmount() {
            return amount;
        }
        
        public String getReason() {
            return reason;
        }
    }
    
    /**
     * Unsupported Currency Exception
     */
    public static class UnsupportedCurrencyException extends DomainException {
        
        private final String currencyCode;
        
        public UnsupportedCurrencyException(String currencyCode) {
            super(String.format("Currency not supported: %s", currencyCode),
                    "UNSUPPORTED_CURRENCY",
                    PAYMENT_DOMAIN);
            this.currencyCode = currencyCode;
        }
        
        public String getCurrencyCode() {
            return currencyCode;
        }
    }
    
    /**
     * Payment Expired Exception
     */
    public static class PaymentExpiredException extends DomainException {
        
        private final PaymentId paymentId;
        private final java.time.Instant expiryTime;
        
        public PaymentExpiredException(PaymentId paymentId, java.time.Instant expiryTime) {
            super(String.format("Payment %s has expired at %s", paymentId, expiryTime),
                    "PAYMENT_EXPIRED",
                    PAYMENT_DOMAIN);
            this.paymentId = paymentId;
            this.expiryTime = expiryTime;
        }
        
        public PaymentId getPaymentId() {
            return paymentId;
        }
        
        public java.time.Instant getExpiryTime() {
            return expiryTime;
        }
    }
    
    /**
     * Refund Not Allowed Exception
     */
    public static class RefundNotAllowedException extends DomainException {
        
        private final PaymentId paymentId;
        private final String reason;
        
        public RefundNotAllowedException(PaymentId paymentId, String reason) {
            super(String.format("Refund not allowed for payment %s: %s", paymentId, reason),
                    "REFUND_NOT_ALLOWED",
                    PAYMENT_DOMAIN);
            this.paymentId = paymentId;
            this.reason = reason;
        }
        
        public PaymentId getPaymentId() {
            return paymentId;
        }
        
        public String getReason() {
            return reason;
        }
    }
    
    /**
     * Payment Processing Exception
     */
    public static class PaymentProcessingException extends DomainException {
        
        private final PaymentId paymentId;
        private final String stage;
        private final String provider;
        private final boolean critical;
        
        public PaymentProcessingException(PaymentId paymentId, String stage, String message) {
            super(String.format("Payment processing failed at stage '%s': %s", stage, message),
                    "PROCESSING_FAILED",
                    PAYMENT_DOMAIN);
            this.paymentId = paymentId;
            this.stage = stage;
            this.provider = "UNKNOWN";
            this.critical = false;
        }
        
        public PaymentProcessingException(PaymentId paymentId, String stage, String message, Throwable cause) {
            super(String.format("Payment processing failed at stage '%s': %s", stage, message),
                    "PROCESSING_FAILED",
                    PAYMENT_DOMAIN,
                    cause);
            this.paymentId = paymentId;
            this.stage = stage;
            this.provider = "UNKNOWN";
            this.critical = true;
        }
        
        public PaymentProcessingException(PaymentId paymentId, String stage, String message, String provider, boolean critical) {
            super(String.format("Payment processing failed at stage '%s': %s", stage, message),
                    "PROCESSING_FAILED",
                    PAYMENT_DOMAIN);
            this.paymentId = paymentId;
            this.stage = stage;
            this.provider = provider;
            this.critical = critical;
        }
        
        public PaymentProcessingException(PaymentId paymentId, String stage, String message, String provider, boolean critical, Throwable cause) {
            super(String.format("Payment processing failed at stage '%s': %s", stage, message),
                    "PROCESSING_FAILED",
                    PAYMENT_DOMAIN,
                    cause);
            this.paymentId = paymentId;
            this.stage = stage;
            this.provider = provider;
            this.critical = critical;
        }
        
        public PaymentId getPaymentId() {
            return paymentId;
        }
        
        public String getStage() {
            return stage;
        }
        
        public String getProvider() {
            return provider;
        }
        
        public boolean isCritical() {
            return critical;
        }
    }
}