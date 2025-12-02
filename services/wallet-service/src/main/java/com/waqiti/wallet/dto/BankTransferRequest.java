package com.waqiti.wallet.dto;

import lombok.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Request DTO for initiating bank transfers through external bank integration service.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankTransferRequest {
    
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "From account is required")
    private String fromAccount;
    
    @NotBlank(message = "To account is required")
    private String toAccount;
    
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    @DecimalMax(value = "1000000.00", message = "Amount exceeds maximum transfer limit")
    @Digits(integer = 10, fraction = 2, message = "Invalid amount format")
    private BigDecimal amount;
    
    @NotBlank(message = "Currency is required")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code")
    private String currency;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    @NotBlank(message = "Transfer type is required")
    @Pattern(regexp = "^(ach|wire|same_day_ach|rtp|internal)$", 
             message = "Transfer type must be ach, wire, same_day_ach, rtp, or internal")
    private String transferType;
    
    @Size(max = 100, message = "Reference must not exceed 100 characters")
    private String reference;
    
    private String idempotencyKey;
    
    @Pattern(regexp = "^(standard|express|instant)$", 
             message = "Speed must be standard, express, or instant")
    private String speed;
    
    private boolean requireConfirmation;
    
    private String confirmationCode;
    
    private Map<String, Object> metadata;
    
    private AchDetails achDetails;
    
    private WireDetails wireDetails;
    
    /**
     * ACH-specific transfer details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AchDetails {
        private String secCode;
        private String companyName;
        private String companyId;
        private String companyEntryDescription;
    }
    
    /**
     * Wire-specific transfer details
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WireDetails {
        private String beneficiaryName;
        private String beneficiaryAddress;
        private String beneficiaryBankName;
        private String beneficiaryBankAddress;
        private String intermediaryBankName;
        private String intermediaryBankSwift;
        private String purposeOfPayment;
    }
}