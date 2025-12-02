package com.waqiti.tokenization.api.dto;

import com.waqiti.tokenization.domain.TokenType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tokenize Request DTO
 *
 * Request body for tokenizing sensitive data
 *
 * @author Waqiti Platform Engineering
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenizeRequest {

    /**
     * Sensitive data to tokenize (e.g., card number, SSN, bank account)
     */
    @NotBlank(message = "Sensitive data is required")
    private String sensitiveData;

    /**
     * Type of token to generate (CARD, BANK_ACCOUNT, SSN, etc.)
     */
    @NotNull(message = "Token type is required")
    private TokenType type;

    /**
     * Optional: Custom expiration days (overrides default for token type)
     */
    private Integer expirationDays;

    /**
     * Optional: Custom KMS key ID (overrides default)
     */
    private String kmsKeyId;

    /**
     * Optional: Metadata (JSON format)
     * e.g., {"last4": "1234", "brand": "visa"}
     */
    private String metadata;
}
