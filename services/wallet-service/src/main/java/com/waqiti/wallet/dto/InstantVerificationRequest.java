package com.waqiti.wallet.dto;

import lombok.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/**
 * Request DTO for instant bank account verification using direct bank credentials.
 * 
 * Supports various verification methods including OAuth, direct credentials,
 * and third-party services like Plaid or Yodlee.
 * 
 * @author Principal Software Engineer
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InstantVerificationRequest {
    
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotBlank(message = "Bank ID is required")
    private String bankId;
    
    private String verificationMethod;
    
    private Map<String, Object> credentials;
    
    private String plaidPublicToken;
    
    private String plaidAccountId;
    
    private String yodleeAccessToken;
    
    private String yodleeAccountId;
    
    private OAuthCredentials oauthCredentials;
    
    private DirectBankCredentials directCredentials;
    
    private Map<String, Object> metadata;
    
    /**
     * OAuth-based verification credentials
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OAuthCredentials {
        private String authorizationCode;
        private String redirectUri;
        private String state;
        private String codeVerifier;
    }
    
    /**
     * Direct bank login credentials (for banks that support it)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DirectBankCredentials {
        private String username;
        private String password;
        private String securityAnswer1;
        private String securityAnswer2;
        private String mfaCode;
    }
}