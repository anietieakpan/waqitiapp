package com.waqiti.user.dto;

import com.waqiti.user.validation.SafeString;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Request to update a user profile
 *
 * SECURITY:
 * - All string inputs validated against injection attacks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {
    @SafeString(maxLength = 100)
    private String firstName;

    @SafeString(maxLength = 100)
    private String lastName;

    private LocalDate dateOfBirth;

    @SafeString(maxLength = 255)
    private String addressLine1;

    @SafeString(maxLength = 255)
    private String addressLine2;

    @SafeString(maxLength = 100)
    private String city;

    @SafeString(maxLength = 100)
    private String state;

    @SafeString(maxLength = 20)
    private String postalCode;

    @SafeString(maxLength = 100)
    private String country;

    @SafeString(maxLength = 50)
    private String preferredLanguage;

    @SafeString(maxLength = 10)
    private String preferredCurrency;
}
