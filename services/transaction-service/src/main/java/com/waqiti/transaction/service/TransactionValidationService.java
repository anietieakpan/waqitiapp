package com.waqiti.transaction.service;

import com.waqiti.transaction.dto.*;
import com.waqiti.transaction.domain.TransactionType;
import com.waqiti.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Enhanced service for validating transaction requests
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionValidationService {

    @Value("${transaction.validation.max.amount:50000.00}")
    private BigDecimal maxTransactionAmount;

    @Value("${transaction.validation.min.amount:0.01}")
    private BigDecimal minTransactionAmount;

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    public ValidationResult validateTransaction(TransactionRequest request) {
        ValidationResult result = new ValidationResult();
        
        // Validate amount
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            result.addError("Amount must be greater than zero");
        }
        
        // Validate maximum transaction limit
        if (request.getAmount() != null && request.getAmount().compareTo(new BigDecimal("100000")) > 0) {
            result.addError("Amount exceeds maximum transaction limit");
        }
        
        // Validate account IDs
        if (request.getFromAccountId() == null || request.getFromAccountId().trim().isEmpty()) {
            result.addError("From account ID is required");
        }
        
        if (request.getToAccountId() == null || request.getToAccountId().trim().isEmpty()) {
            result.addError("To account ID is required");
        }
        
        // Validate same account transfer
        if (request.getFromAccountId() != null && request.getFromAccountId().equals(request.getToAccountId())) {
            result.addError("Cannot transfer to the same account");
        }
        
        // Validate transaction type
        if (request.getTransactionType() == null) {
            result.addError("Transaction type is required");
        }
        
        return result;
    }

    /**
     * Validate transfer request
     */
    public void validateTransferRequest(TransferRequest request) {
        log.debug("Validating transfer request: {}", request.getTransactionId());

        validateCommonFields(request.getTransactionId(), request.getAmount(), request.getCurrency());
        
        if (request.getSenderId() == null) {
            throw new ValidationException("Sender ID is required", "SENDER_ID_REQUIRED");
        }
        
        if (request.getRecipientId() == null) {
            throw new ValidationException("Recipient ID is required", "RECIPIENT_ID_REQUIRED");
        }
        
        if (request.getSenderId().equals(request.getRecipientId())) {
            throw new ValidationException("Cannot transfer to yourself", "SELF_TRANSFER_NOT_ALLOWED");
        }
        
        validateWalletId(request.getFromWalletId(), "fromWalletId");
        validateWalletId(request.getToWalletId(), "toWalletId");
        
        if (request.getFromWalletId().equals(request.getToWalletId())) {
            throw new ValidationException("Cannot transfer between same wallet", "SAME_WALLET_TRANSFER");
        }
    }

    
    public boolean isHighValueTransaction(BigDecimal amount) {
        return amount.compareTo(new BigDecimal("10000")) > 0;
    }
    
    public boolean requiresApproval(TransactionRequest request) {
        return isHighValueTransaction(request.getAmount()) || 
               "INTERNATIONAL".equals(request.getTransactionType());
    }

    private void validateCommonFields(String transactionId, BigDecimal amount, String currency) {
        // Validate transaction ID
        if (transactionId == null || transactionId.trim().isEmpty()) {
            throw new ValidationException("Transaction ID is required", "TRANSACTION_ID_REQUIRED");
        }
        
        if (!UUID_PATTERN.matcher(transactionId).matches()) {
            throw new ValidationException("Invalid transaction ID format", "INVALID_TRANSACTION_ID_FORMAT");
        }

        // Validate amount
        if (amount == null) {
            throw new ValidationException("Transaction amount is required", "AMOUNT_REQUIRED");
        }
        
        if (amount.compareTo(minTransactionAmount) < 0) {
            throw new ValidationException(
                String.format("Amount must be at least %s", minTransactionAmount), 
                "AMOUNT_TOO_SMALL");
        }
        
        if (amount.compareTo(maxTransactionAmount) > 0) {
            throw new ValidationException(
                String.format("Amount exceeds maximum limit of %s", maxTransactionAmount), 
                "AMOUNT_TOO_LARGE");
        }
        
        // Validate currency
        if (currency == null || currency.trim().isEmpty()) {
            throw new ValidationException("Currency is required", "CURRENCY_REQUIRED");
        }
        
        if (!isValidCurrency(currency)) {
            throw new ValidationException("Invalid currency code: " + currency, "INVALID_CURRENCY");
        }
    }

    private void validateWalletId(String walletId, String fieldName) {
        if (walletId == null || walletId.trim().isEmpty()) {
            throw new ValidationException(
                String.format("%s is required", fieldName), 
                "WALLET_ID_REQUIRED");
        }
        
        if (!UUID_PATTERN.matcher(walletId).matches()) {
            throw new ValidationException(
                String.format("Invalid %s format", fieldName), 
                "INVALID_WALLET_ID_FORMAT");
        }
    }

    private boolean isValidCurrency(String currency) {
        // In production, this would check against a list of supported currencies
        return currency != null && currency.length() == 3 && 
               currency.matches("[A-Z]{3}") && 
               isSupportedCurrency(currency);
    }

    private boolean isSupportedCurrency(String currency) {
        // Common supported currencies
        return java.util.Set.of("USD", "EUR", "GBP", "CAD", "AUD", "NGN", "KES", "GHS").contains(currency);
    }
    
    /**
     * Validates deposit request with comprehensive checks.
     *
     * Performs validation for:
     * - Request null check
     * - Transaction ID format
     * - Wallet ID format
     * - Amount range (min/max limits)
     * - Currency code validity
     * - Payment method ID format
     * - Description length limits
     *
     * @param request The deposit request to validate
     * @throws ValidationException if validation fails
     */
    public void validateDepositRequest(DepositRequest request) {
        log.debug("Validating deposit request: {}", request);

        // Null check
        if (request == null) {
            throw new ValidationException("Deposit request cannot be null", "NULL_REQUEST");
        }

        // Validate transaction ID if present
        if (request.getTransactionId() != null && !request.getTransactionId().trim().isEmpty()) {
            if (!UUID_PATTERN.matcher(request.getTransactionId()).matches()) {
                throw new ValidationException("Invalid transaction ID format", "INVALID_TRANSACTION_ID_FORMAT");
            }
        }

        // Validate wallet ID
        validateWalletId(request.getWalletId(), "walletId");

        // Validate amount
        validateAmount(request.getAmount());

        // Validate currency
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new ValidationException("Currency is required", "CURRENCY_REQUIRED");
        }

        if (!isValidCurrency(request.getCurrency())) {
            throw new ValidationException("Invalid or unsupported currency: " + request.getCurrency(), "INVALID_CURRENCY");
        }

        // Validate payment method ID
        if (request.getPaymentMethodId() == null || request.getPaymentMethodId().trim().isEmpty()) {
            throw new ValidationException("Payment method ID is required", "PAYMENT_METHOD_REQUIRED");
        }

        if (!UUID_PATTERN.matcher(request.getPaymentMethodId()).matches()) {
            throw new ValidationException("Invalid payment method ID format", "INVALID_PAYMENT_METHOD_FORMAT");
        }

        // Validate description length
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            throw new ValidationException("Description cannot exceed 500 characters", "DESCRIPTION_TOO_LONG");
        }

        log.debug("Deposit request validation passed for wallet: {}", request.getWalletId());
    }
    
    /**
     * Validates withdrawal request with comprehensive checks.
     *
     * Performs validation for:
     * - Request null check
     * - Transaction ID format
     * - Wallet ID format
     * - Amount range (min/max limits)
     * - Currency code validity
     * - Bank account ID format
     * - Description length limits
     *
     * @param request The withdrawal request to validate
     * @throws ValidationException if validation fails
     */
    public void validateWithdrawalRequest(WithdrawalRequest request) {
        log.debug("Validating withdrawal request: {}", request);

        // Null check
        if (request == null) {
            throw new ValidationException("Withdrawal request cannot be null", "NULL_REQUEST");
        }

        // Validate transaction ID if present
        if (request.getTransactionId() != null && !request.getTransactionId().trim().isEmpty()) {
            if (!UUID_PATTERN.matcher(request.getTransactionId()).matches()) {
                throw new ValidationException("Invalid transaction ID format", "INVALID_TRANSACTION_ID_FORMAT");
            }
        }

        // Validate wallet ID
        validateWalletId(request.getWalletId(), "walletId");

        // Validate amount
        validateAmount(request.getAmount());

        // Validate currency
        if (request.getCurrency() == null || request.getCurrency().trim().isEmpty()) {
            throw new ValidationException("Currency is required", "CURRENCY_REQUIRED");
        }

        if (!isValidCurrency(request.getCurrency())) {
            throw new ValidationException("Invalid or unsupported currency: " + request.getCurrency(), "INVALID_CURRENCY");
        }

        // Validate bank account ID
        if (request.getBankAccountId() == null || request.getBankAccountId().trim().isEmpty()) {
            throw new ValidationException("Bank account ID is required", "BANK_ACCOUNT_REQUIRED");
        }

        if (!UUID_PATTERN.matcher(request.getBankAccountId()).matches()) {
            throw new ValidationException("Invalid bank account ID format", "INVALID_BANK_ACCOUNT_FORMAT");
        }

        // Validate description length
        if (request.getDescription() != null && request.getDescription().length() > 500) {
            throw new ValidationException("Description cannot exceed 500 characters", "DESCRIPTION_TOO_LONG");
        }

        log.debug("Withdrawal request validation passed for wallet: {}", request.getWalletId());
    }
    
    private void validateAmount(BigDecimal amount) {
        if (amount == null) {
            throw new ValidationException("Amount is required", "MISSING_AMOUNT");
        }
        
        if (amount.compareTo(minTransactionAmount) < 0) {
            throw new ValidationException(
                String.format("Amount must be at least %s", minTransactionAmount), 
                "AMOUNT_TOO_SMALL");
        }
        
        if (amount.compareTo(maxTransactionAmount) > 0) {
            throw new ValidationException(
                String.format("Amount cannot exceed %s", maxTransactionAmount), 
                "AMOUNT_TOO_LARGE");
        }
    }
}