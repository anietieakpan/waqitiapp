package com.waqiti.tokenization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Tokenization Request DTO
 *
 * @author Waqiti Security Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenizationRequest {

    /**
     * Card number to tokenize (will be immediately encrypted, never logged)
     */
    @NotBlank(message = "Card number is required")
    @Pattern(regexp = "\\d{13,19}", message = "Card number must be 13-19 digits")
    private String cardNumber;

    /**
     * User ID requesting tokenization
     */
    @NotNull(message = "User ID is required")
    private UUID userId;

    /**
     * Card expiration date
     */
    private Instant expirationDate;

    /**
     * CVV (for validation, never stored)
     */
    @Pattern(regexp = "\\d{3,4}", message = "CVV must be 3-4 digits")
    private String cvv;

    /**
     * Cardholder name
     */
    private String cardholderName;

    /**
     * Billing address (for AVS verification)
     */
    private String billingAddress;

    /**
     * Billing ZIP code (for AVS verification)
     */
    private String billingZipCode;

    @Override
    public String toString() {
        return "TokenizationRequest{" +
                "userId=" + userId +
                ", expirationDate=" + expirationDate +
                ", cardNumber=REDACTED" +
                ", cvv=REDACTED" +
                '}';
    }
}
