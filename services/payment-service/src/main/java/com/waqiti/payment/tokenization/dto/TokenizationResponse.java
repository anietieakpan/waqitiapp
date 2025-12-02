package com.waqiti.payment.tokenization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Tokenization response DTO
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenizationResponse {

    private String token;

    private String tokenType;

    private Long expiresAt;

    private Boolean formatPreserved;

    private Map<String, String> metadata;
}
