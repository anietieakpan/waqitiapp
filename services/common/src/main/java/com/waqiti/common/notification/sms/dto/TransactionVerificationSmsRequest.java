package com.waqiti.common.notification.sms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Request DTO for sending transaction verification SMS messages
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionVerificationSmsRequest {
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Invalid phone number format")
    private String phoneNumber;
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Transaction ID is required")
    private String transactionId;
    
    @NotNull(message = "Transaction amount is required")
    private BigDecimal transactionAmount;
    
    @NotBlank(message = "Currency is required")
    private String currency;
    
    @NotBlank(message = "Recipient is required")
    private String recipient;
    
    /**
     * Verification code
     */
    @Pattern(regexp = "^\\d{6,8}$", message = "Verification code must be 6-8 digits")
    private String verificationCode;
    
    @Builder.Default
    private SmsPriority priority = SmsPriority.HIGH;
    
    @Builder.Default
    private SmsType type = SmsType.TRANSACTION_VERIFICATION;
    
    /**
     * Transaction type (transfer, payment, withdrawal, etc.)
     */
    private String transactionType;
    
    /**
     * Merchant or recipient details
     */
    private String merchantName;
    
    /**
     * Transaction location
     */
    private String transactionLocation;
    
    /**
     * Expiry time for verification
     */
    @Builder.Default
    private int expiryMinutes = 10;
    
    /**
     * Risk score for the transaction
     */
    private double riskScore;
    
    /**
     * Whether this is a high-value transaction
     */
    @Builder.Default
    private boolean highValueTransaction = false;
    
    /**
     * Additional context for the verification
     */
    private Map<String, Object> context;
    
    /**
     * Approval deadline
     */
    private LocalDateTime approvalDeadline;
    
    /**
     * Get amount
     */
    public BigDecimal getAmount() {
        return transactionAmount;
    }
    
    /**
     * Language code for message localization
     */
    @Builder.Default
    private String languageCode = "en";
    
    /**
     * Custom message override
     */
    private String customMessage;
    
    /**
     * Device fingerprint of the transaction initiator
     */
    private String deviceFingerprint;
    
    /**
     * IP address of the transaction origin
     */
    private String originIpAddress;
    
    @Builder.Default
    private LocalDateTime requestTime = LocalDateTime.now();
    
    /**
     * Get recipient name (alias for recipient)
     */
    public String getRecipientName() {
        return recipient;
    }
    
    /**
     * Get recipient account (alias for recipient)
     */
    public String getRecipientAccount() {
        return recipient;
    }
}