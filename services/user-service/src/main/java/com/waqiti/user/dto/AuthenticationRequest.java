package com.waqiti.user.dto;

import com.waqiti.user.validation.SafeString;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to authenticate a user
 *
 * SECURITY:
 * - Username/email validated against injection attacks
 * - Rate limiting should be applied at controller level
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationRequest {
    @NotBlank(message = "Username or email is required")
    @SafeString(maxLength = 255)
    private String usernameOrEmail;

    @NotBlank(message = "Password is required")
    private String password;
}
