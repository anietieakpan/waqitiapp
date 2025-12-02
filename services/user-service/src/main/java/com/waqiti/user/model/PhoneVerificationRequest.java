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
public class PhoneVerificationRequest {
    private String userId;
    private String phoneNumber;
    private String verificationMethod;
    private String verificationCode;
    private Instant requestTimestamp;
}
