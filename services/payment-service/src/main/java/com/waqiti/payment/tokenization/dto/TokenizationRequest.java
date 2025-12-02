package com.waqiti.payment.tokenization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Map;

/**
 * Tokenization request DTO
 *
 * @author Waqiti Security Team
 * @version 3.0.0
 * @since 2025-10-11
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenizationRequest {

    @NotBlank(message = "Sensitive data is required")
    private String sensitiveData;

    @NotBlank(message = "Data type is required")
    private String dataType; // CREDIT_CARD, ACCOUNT_NUMBER, CVV, SSN, etc.

    @NotBlank(message = "User ID is required")
    private String userId;

    private String ipAddress;

    private Map<String, String> metadata;
}
