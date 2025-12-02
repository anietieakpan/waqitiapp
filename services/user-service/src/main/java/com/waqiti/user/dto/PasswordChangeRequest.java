package com.waqiti.user.dto;

import com.waqiti.user.validation.StrongPassword;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to change a password
 *
 * SECURITY:
 * - New password must meet strong password requirements
 * - Current password verified at service layer
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordChangeRequest {
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @StrongPassword(minLength = 12)
    private String newPassword;
}
