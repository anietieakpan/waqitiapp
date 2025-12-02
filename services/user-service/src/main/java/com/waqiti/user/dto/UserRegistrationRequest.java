package com.waqiti.user.dto;

import com.waqiti.user.validation.SafeString;
import com.waqiti.user.validation.StrongPassword;
import com.waqiti.user.validation.ValidPhoneNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to register a new user
 *
 * SECURITY:
 * - All string inputs validated against XSS/SQL injection
 * - Phone numbers validated in E.164 international format
 * - Username restricted to safe characters only
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegistrationRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username can only contain letters, numbers, periods, underscores, and hyphens")
    @SafeString(maxLength = 50)
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @SafeString(maxLength = 255)
    private String email;

    @NotBlank(message = "Password is required")
    @StrongPassword(minLength = 12)
    private String password;

    @ValidPhoneNumber(allowNull = false)
    private String phoneNumber;
}
