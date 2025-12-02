package com.waqiti.tokenization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Detokenization Request DTO
 *
 * @author Waqiti Security Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetokenizationRequest {

    /**
     * Token to detokenize
     */
    @NotBlank(message = "Token is required")
    private String token;

    /**
     * User ID requesting detokenization (for authorization)
     */
    @NotNull(message = "User ID is required")
    private UUID userId;

    /**
     * Purpose of detokenization (for audit logging)
     */
    @NotBlank(message = "Purpose is required")
    private String purpose;

    /**
     * IP address of requester (for audit logging)
     */
    private String ipAddress;
}
