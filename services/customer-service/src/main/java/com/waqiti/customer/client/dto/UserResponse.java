package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

/**
 * Response DTO for user information from user-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {

    /**
     * Unique user identifier
     */
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Customer ID associated with the user
     */
    @NotBlank(message = "Customer ID is required")
    private String customerId;

    /**
     * Username
     */
    @NotBlank(message = "Username is required")
    private String username;

    /**
     * Email address
     */
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    private String email;

    /**
     * Phone number
     */
    private String phoneNumber;

    /**
     * First name
     */
    @NotBlank(message = "First name is required")
    private String firstName;

    /**
     * Last name
     */
    @NotBlank(message = "Last name is required")
    private String lastName;

    /**
     * User status (ACTIVE, INACTIVE, SUSPENDED, etc.)
     */
    @NotBlank(message = "Status is required")
    private String status;

    /**
     * Whether user is active
     */
    @NotNull(message = "Active flag is required")
    private Boolean active;

    /**
     * Whether user is verified
     */
    private Boolean verified;

    /**
     * Whether email is verified
     */
    private Boolean emailVerified;

    /**
     * Whether phone is verified
     */
    private Boolean phoneVerified;

    /**
     * User creation timestamp
     */
    @NotNull(message = "Created date is required")
    private LocalDateTime createdAt;

    /**
     * Last login timestamp
     */
    private LocalDateTime lastLoginAt;

    /**
     * Last updated timestamp
     */
    private LocalDateTime updatedAt;
}
