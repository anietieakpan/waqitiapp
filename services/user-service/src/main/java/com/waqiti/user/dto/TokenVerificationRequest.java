package com.waqiti.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Request to verify a token
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenVerificationRequest {
    @NotBlank(message = "Token is required")
    private String token;
}
