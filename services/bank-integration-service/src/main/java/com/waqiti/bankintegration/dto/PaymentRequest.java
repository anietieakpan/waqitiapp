package com.waqiti.bankintegration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Payment Request DTO
 * 
 * Contains all necessary information for processing a payment
 * through various payment providers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    
    @NotBlank(message = "Request ID is required")
    private String requestId;
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    private String merchantId;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @Digits(integer = 10, fraction = 2, message = "Amount format is invalid")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;
    
    @Size(max = 500, message = "Description cannot exceed 500 characters")
    private String description;
    
    // Payment method identifiers (provider-specific)
    private String paymentMethodId;  // For Stripe Payment Methods
    private String cardToken;        // For legacy token-based payments
    private String bankAccountId;    // For ACH/bank transfers
    private String customerId;       // Provider's customer ID
    
    // Payment configuration
    private Boolean autoConfirm = false;
    private String returnUrl;
    private String statementDescriptor;
    
    // Additional metadata
    private Map<String, String> metadata;
    
    // Billing information
    private BillingDetails billingDetails;
    
    // Shipping information (if applicable)
    private ShippingDetails shippingDetails;
    
    // Risk assessment data
    private String ipAddress;
    private String userAgent;
    private String deviceFingerprint;
    
    // ACH-specific fields
    private String reference;
    private AccountDetails fromAccount;
    private AccountDetails toAccount;
    private boolean expedited = false;
}