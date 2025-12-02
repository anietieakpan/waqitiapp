package com.waqiti.investment.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * User Profile DTO - Response from user-service
 *
 * Contains user personal information required for:
 * - Tax form generation (1099-B, 1099-DIV, 1099-INT)
 * - Regulatory reporting
 * - Account correspondence
 *
 * @author Waqiti Platform
 * @version 1.0.0
 * @since 2025-10-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileDto {
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String email;
    private String phoneNumber;
    private String preferredLanguage;
    private String preferredCurrency;

    /**
     * Get user's full name.
     */
    public String getFullName() {
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        } else if (firstName != null) {
            return firstName;
        } else if (lastName != null) {
            return lastName;
        } else {
            return "";
        }
    }

    /**
     * Get formatted address for tax forms (single line).
     */
    public String getFormattedAddress() {
        StringBuilder address = new StringBuilder();
        if (addressLine1 != null && !addressLine1.isBlank()) {
            address.append(addressLine1);
        }
        if (addressLine2 != null && !addressLine2.isBlank()) {
            if (address.length() > 0) {
                address.append(", ");
            }
            address.append(addressLine2);
        }
        return address.toString();
    }

    /**
     * Get formatted city, state, zip for tax forms.
     */
    public String getFormattedCityStateZip() {
        StringBuilder csz = new StringBuilder();
        if (city != null && !city.isBlank()) {
            csz.append(city);
        }
        if (state != null && !state.isBlank()) {
            if (csz.length() > 0) {
                csz.append(", ");
            }
            csz.append(state);
        }
        if (postalCode != null && !postalCode.isBlank()) {
            if (csz.length() > 0) {
                csz.append(" ");
            }
            csz.append(postalCode);
        }
        return csz.toString();
    }
}
