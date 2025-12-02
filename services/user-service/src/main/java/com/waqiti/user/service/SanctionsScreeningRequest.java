package com.waqiti.user.service;

import java.util.List;

@lombok.Data
@lombok.Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
public class SanctionsScreeningRequest {
    private String userId;
    private String firstName;
    private String lastName;
    private String dateOfBirth;
    private String countryCode;
    private List<String> screeningLists;
    private java.time.Instant requestTimestamp;
}
