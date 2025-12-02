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
public class EmailVerificationRequest {
    private String userId;
    private String email;
    private String verificationCode;
    private Instant requestTimestamp;
}
