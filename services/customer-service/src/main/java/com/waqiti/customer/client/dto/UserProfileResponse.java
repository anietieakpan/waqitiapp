package com.waqiti.customer.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Response DTO for user profile information from user-service.
 *
 * @author Waqiti Engineering Team
 * @version 1.0
 * @since 2025-11-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileResponse {

    /**
     * User identifier
     */
    @NotBlank(message = "User ID is required")
    private String userId;

    /**
     * Full name
     */
    @NotBlank(message = "Full name is required")
    private String fullName;

    /**
     * Date of birth
     */
    private LocalDate dateOfBirth;

    /**
     * Gender
     */
    private String gender;

    /**
     * Nationality
     */
    private String nationality;

    /**
     * Address line 1
     */
    private String addressLine1;

    /**
     * Address line 2
     */
    private String addressLine2;

    /**
     * City
     */
    private String city;

    /**
     * State or province
     */
    private String state;

    /**
     * Postal code
     */
    private String postalCode;

    /**
     * Country
     */
    private String country;

    /**
     * Occupation
     */
    private String occupation;

    /**
     * Employer name
     */
    private String employerName;

    /**
     * Profile picture URL
     */
    private String profilePictureUrl;

    /**
     * Preferred language
     */
    private String preferredLanguage;

    /**
     * Timezone
     */
    private String timezone;

    /**
     * Profile last updated timestamp
     */
    private LocalDateTime updatedAt;
}
