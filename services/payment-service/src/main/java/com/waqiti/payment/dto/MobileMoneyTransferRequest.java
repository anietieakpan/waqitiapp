package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Mobile Money Transfer Request DTO
 * 
 * Represents a mobile money transfer request with all necessary
 * fields for processing mobile payments across different providers
 * and countries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MobileMoneyTransferRequest {

    @NotBlank(message = "Transfer ID is required")
    private String transferId;

    @NotBlank(message = "Sender ID is required")
    private String senderId;

    @NotBlank(message = "Receiver ID is required")
    private String receiverId;

    @NotBlank(message = "Sender mobile number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid sender mobile number format")
    private String senderMobileNumber;

    @NotBlank(message = "Receiver mobile number is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{1,14}$", message = "Invalid receiver mobile number format")
    private String receiverMobileNumber;

    @NotBlank(message = "Mobile money provider is required")
    private String mobileMoneyProvider;

    @NotBlank(message = "Transfer type is required")
    private String transferType;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Amount format is invalid")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be 3 characters")
    private String currency;

    @Size(max = 255, message = "Purpose cannot exceed 255 characters")
    private String purpose;

    @Size(min = 2, max = 2, message = "Sender country code must be 2 characters")
    private String senderCountryCode;

    @Size(min = 2, max = 2, message = "Receiver country code must be 2 characters")
    private String receiverCountryCode;

    private String ussdCode;

    private String agentCode;

    private String reference;

    private Map<String, Object> metadata;

    private boolean crossBorder;

    private LocalDateTime requestedAt;
}