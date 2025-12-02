package com.waqiti.user.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmploymentVerificationRequest {
    private String userId;
    private String employerName;
    private String jobTitle;
    private String employmentStartDate;
    private String verificationMethod;
    private String documentUrl;
    private Instant requestTimestamp;
}
