package com.waqiti.user.dto;

import com.waqiti.user.validation.SafeString;
import com.waqiti.user.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to reset a password
 *
 * SECURITY:
 * - Token validated to prevent injection attacks
 * - Password strength enforced at service layer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {
    @NotBlank(message = "Token is required")
    @SafeString(maxLength = 500)
    private String token;

    @NotBlank(message = "New password is required")
    @StrongPassword(minLength = 12)
    private String newPassword;
}
