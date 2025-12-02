package com.waqiti.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.UUID;

/**
 * Exception thrown when a requested resource is not found.
 *
 * Features:
 * - Resource type and ID tracking
 * - Custom error codes
 * - Search criteria support
 * - User-friendly messages
 *
 * @author Waqiti Platform Team
 * @version 2.0.0
 */
@Getter
public class ResourceNotFoundException extends BusinessException {

    private static final long serialVersionUID = 2L;

    private final String resourceType;
    private final String resourceId;
    private final String searchField;
    private final Object searchValue;

    /**
     * Basic constructor with message
     */
    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND.getCode(), message, HttpStatus.NOT_FOUND.value());
        this.resourceType = null;
        this.resourceId = null;
        this.searchField = null;
        this.searchValue = null;
    }

    /**
     * Constructor with resource name and ID
     */
    public ResourceNotFoundException(String resourceType, String resourceId) {
        super(
            ErrorCode.RESOURCE_NOT_FOUND.getCode(),
            String.format("%s not found with ID: %s", resourceType, resourceId),
            HttpStatus.NOT_FOUND.value()
        );
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.searchField = "id";
        this.searchValue = resourceId;
    }

    /**
     * Constructor with resource name and UUID
     */
    public ResourceNotFoundException(String resourceType, UUID id) {
        super(
            ErrorCode.RESOURCE_NOT_FOUND.getCode(),
            String.format("%s not found with ID: %s", resourceType, id),
            HttpStatus.NOT_FOUND.value()
        );
        this.resourceType = resourceType;
        this.resourceId = id != null ? id.toString() : null;
        this.searchField = "id";
        this.searchValue = id;
    }

    /**
     * Constructor with resource name, field name, and field value
     */
    public ResourceNotFoundException(String resourceType, String fieldName, Object fieldValue) {
        super(
            ErrorCode.RESOURCE_NOT_FOUND.getCode(),
            String.format("%s not found with %s: %s", resourceType, fieldName, fieldValue),
            HttpStatus.NOT_FOUND.value()
        );
        this.resourceType = resourceType;
        this.resourceId = fieldValue != null ? fieldValue.toString() : null;
        this.searchField = fieldName;
        this.searchValue = fieldValue;
    }

    /**
     * Constructor with cause
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(ErrorCode.RESOURCE_NOT_FOUND.getCode(), message, HttpStatus.NOT_FOUND.value(), cause);
        this.resourceType = null;
        this.resourceId = null;
        this.searchField = null;
        this.searchValue = null;
    }

    /**
     * Constructor with custom error code
     */
    public ResourceNotFoundException(ErrorCode errorCode, String resourceType, String resourceId) {
        super(
            errorCode.getCode(),
            String.format("%s not found with ID: %s", resourceType, resourceId),
            HttpStatus.NOT_FOUND.value()
        );
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.searchField = "id";
        this.searchValue = resourceId;
    }

    /**
     * Full constructor with all parameters
     */
    public ResourceNotFoundException(ErrorCode errorCode, String message,
                                     String resourceType, String resourceId,
                                     String searchField, Object searchValue) {
        super(errorCode.getCode(), message, HttpStatus.NOT_FOUND.value());
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.searchField = searchField;
        this.searchValue = searchValue;

        // Add metadata
        if (resourceType != null) {
            withMetadata("resourceType", resourceType);
        }
        if (resourceId != null) {
            withMetadata("resourceId", resourceId);
        }
        if (searchField != null) {
            withMetadata("searchField", searchField);
        }
        if (searchValue != null) {
            withMetadata("searchValue", searchValue);
        }
    }

    // ===== Static Factory Methods =====

    /**
     * Create exception for user not found
     */
    public static ResourceNotFoundException userNotFound(String userId) {
        return new ResourceNotFoundException(
            ErrorCode.USER_NOT_FOUND,
            "User",
            userId
        );
    }

    /**
     * Create exception for account not found
     */
    public static ResourceNotFoundException accountNotFound(String accountId) {
        return new ResourceNotFoundException(
            ErrorCode.ACCOUNT_NOT_FOUND,
            "Account",
            accountId
        );
    }

    /**
     * Create exception for payment not found
     */
    public static ResourceNotFoundException paymentNotFound(String paymentId) {
        return new ResourceNotFoundException(
            ErrorCode.PAYMENT_NOT_FOUND,
            "Payment",
            paymentId
        );
    }

    /**
     * Create exception for transaction not found
     */
    public static ResourceNotFoundException transactionNotFound(String transactionId) {
        return new ResourceNotFoundException(
            ErrorCode.TRANSACTION_NOT_FOUND,
            "Transaction",
            transactionId
        );
    }

    /**
     * Create exception for wallet not found
     */
    public static ResourceNotFoundException walletNotFound(String walletId) {
        return new ResourceNotFoundException(
            ErrorCode.WALLET_NOT_FOUND,
            "Wallet",
            walletId
        );
    }

    /**
     * Create exception for card not found
     */
    public static ResourceNotFoundException cardNotFound(String cardId) {
        return new ResourceNotFoundException(
            ErrorCode.CARD_NOT_FOUND,
            "Card",
            cardId
        );
    }

    /**
     * Create exception for merchant not found
     */
    public static ResourceNotFoundException merchantNotFound(String merchantId) {
        return new ResourceNotFoundException(
            ErrorCode.MERCHANT_NOT_FOUND,
            "Merchant",
            merchantId
        );
    }

    /**
     * Create exception for loan not found
     */
    public static ResourceNotFoundException loanNotFound(String loanId) {
        return new ResourceNotFoundException(
            ErrorCode.LOAN_NOT_FOUND,
            "Loan",
            loanId
        );
    }

    /**
     * Create exception for investment not found
     */
    public static ResourceNotFoundException investmentNotFound(String investmentId) {
        return new ResourceNotFoundException(
            ErrorCode.INVESTMENT_NOT_FOUND,
            "Investment",
            investmentId
        );
    }

    /**
     * Create exception by email
     */
    public static ResourceNotFoundException byEmail(String resourceType, String email) {
        return new ResourceNotFoundException(resourceType, "email", email);
    }

    /**
     * Create exception by username
     */
    public static ResourceNotFoundException byUsername(String resourceType, String username) {
        return new ResourceNotFoundException(resourceType, "username", username);
    }

    /**
     * Create exception by account number
     */
    public static ResourceNotFoundException byAccountNumber(String accountNumber) {
        return new ResourceNotFoundException("Account", "accountNumber", accountNumber);
    }

    /**
     * Create exception by phone number
     */
    public static ResourceNotFoundException byPhone(String resourceType, String phone) {
        return new ResourceNotFoundException(resourceType, "phoneNumber", phone);
    }

    /**
     * Create exception with custom message
     */
    public static ResourceNotFoundException withMessage(String resourceType, String message) {
        ResourceNotFoundException exception = new ResourceNotFoundException(message);
        exception.withMetadata("resourceType", resourceType);
        return exception;
    }

    @Override
    public String toString() {
        return String.format(
            "ResourceNotFoundException[resourceType=%s, resourceId=%s, searchField=%s, searchValue=%s, message=%s]",
            resourceType, resourceId, searchField, searchValue, getMessage()
        );
    }
}
