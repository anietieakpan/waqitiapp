package com.waqiti.tokenization.api.dto;

import com.waqiti.tokenization.domain.TokenType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detokenize Response DTO
 *
 * Response containing the original sensitive data
 *
 * @author Waqiti Platform Engineering
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetokenizeResponse {

    /**
     * Original sensitive data (NEVER log this!)
     */
    private String sensitiveData;

    /**
     * Token type
     */
    private TokenType type;

    /**
     * Success indicator
     */
    private boolean success;
}
