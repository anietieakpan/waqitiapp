package com.waqiti.tokenization.api.dto;

import com.waqiti.tokenization.domain.TokenType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Token Validation Response DTO
 *
 * Response for token validation requests
 *
 * @author Waqiti Platform Engineering
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationResponse {

    /**
     * Whether token is valid
     */
    private boolean valid;

    /**
     * Token type (if valid)
     */
    private TokenType type;

    /**
     * Expiration timestamp (if valid)
     */
    private Instant expiresAt;

    /**
     * Reason if invalid
     */
    private String reason;
}
