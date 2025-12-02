package com.waqiti.bankintegration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Authentication requirements for payment processing
 * 
 * Contains details about additional authentication needed for a payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationRequired {
    private String type;  // 3ds, otp, bank_redirect, biometric
    private String redirectUrl;
    private String clientSecret;
    private Map<String, String> parameters;
    private String challengeUrl;
    private Long timeoutSeconds;
    private String instructions;
}