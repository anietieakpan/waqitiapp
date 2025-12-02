package com.waqiti.tokenization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tokenization Response DTO
 *
 * @author Waqiti Security Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenizationResponse {

    /**
     * Generated token (format: tok_[32-char-uuid])
     */
    private String token;

    /**
     * Last 4 digits of card (PCI-DSS compliant to return)
     */
    private String last4Digits;

    /**
     * Card type (VISA, MASTERCARD, AMEX, etc.)
     */
    private String cardType;

    /**
     * Card expiration date
     */
    private Instant expiresAt;

    /**
     * Success indicator
     */
    private boolean success;

    /**
     * Error message (if any)
     */
    private String errorMessage;
}
