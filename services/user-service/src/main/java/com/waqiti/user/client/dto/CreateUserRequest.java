package com.waqiti.user.client.dto;

import com.waqiti.user.validation.SafeString;
import com.waqiti.user.validation.ValidPhoneNumber;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request to create a user in the external system
 *
 * SECURITY:
 * - All user data validated before external system creation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {
    private UUID userId;

    @NotBlank
    @SafeString(maxLength = 50)
    private String username;

    @NotBlank
    @Email
    @SafeString(maxLength = 255)
    private String email;

    @ValidPhoneNumber
    private String phoneNumber;

    @SafeString(maxLength = 100)
    private String firstName;

    @SafeString(maxLength = 100)
    private String lastName;

    @SafeString(maxLength = 50)
    private String externalSystem; // "INTERNAL" (legacy field for backwards compatibility)
}
