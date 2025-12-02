package com.waqiti.tokenization.api.dto;

import com.waqiti.tokenization.domain.TokenType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tokenize Response DTO
 *
 * Response containing the generated token
 *
 * @author Waqiti Platform Engineering
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenizeResponse {

    /**
     * Generated token
     */
    private String token;

    /**
     * Token type
     */
    private TokenType type;

    /**
     * Token expiration timestamp
     */
    private Instant expiresAt;

    /**
     * Success indicator
     */
    private boolean success;
}
