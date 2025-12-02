package com.waqiti.tokenization.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Detokenize Request DTO
 *
 * Request body for retrieving original sensitive data
 *
 * @author Waqiti Platform Engineering
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetokenizeRequest {

    /**
     * Token to detokenize
     */
    @NotBlank(message = "Token is required")
    private String token;
}
