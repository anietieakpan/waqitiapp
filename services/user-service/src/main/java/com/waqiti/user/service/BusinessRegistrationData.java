package com.waqiti.user.service;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class BusinessRegistrationData {
    private String registrationNumber;
    private String businessName;
    private String registeredAddress;
    private java.time.LocalDate incorporationDate;
    private String businessType;
    private String registrationAuthority;
}
