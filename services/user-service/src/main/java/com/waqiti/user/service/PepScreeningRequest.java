package com.waqiti.user.service;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class PepScreeningRequest {
    private String userId;
    private String firstName;
    private String lastName;
    private String dateOfBirth;
    private String countryCode;
    private String screeningLevel;
    private java.time.Instant requestTimestamp;
}
