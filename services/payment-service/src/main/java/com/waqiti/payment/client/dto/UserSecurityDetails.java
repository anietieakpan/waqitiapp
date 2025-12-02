package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for user security details including MFA configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityDetails {
    private String userId;
    private String securityQuestionId;
    private String securityAnswer;
    private boolean biometricConfigured;
    private String biometricId;
    private boolean mfaEnabled;
    private String phoneNumber;
    private String email;
}