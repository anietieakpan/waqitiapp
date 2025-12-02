package com.waqiti.common.validation;

import lombok.Builder;
import lombok.Data;

/**
 * User registration request for validation
 */
@Data
@Builder
public class UserRegistrationRequest {
    private String email;
    private String password;
    private String firstName;
    private String lastName;
    private String phoneNumber;
    private String dateOfBirth;
    private String country;
    private String referralCode;
}