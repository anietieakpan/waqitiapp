package com.waqiti.payment.security;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSecurityDetails {
    
    private String userId;
    private String securityQuestion;
    private String securityAnswer;
    private boolean biometricEnabled;
    private String biometricId;
    private boolean mfaEnabled;
    private String phoneNumber;
    private String email;
    private LocalDateTime lastSecurityUpdate;
    private int failedSecurityAttempts;
    private LocalDateTime lastFailedAttempt;
    private boolean accountLocked;
    private LocalDateTime lockoutExpiry;
    
    public boolean isSecurityAnswerConfigured() {
        return securityAnswer != null && !securityAnswer.trim().isEmpty();
    }
    
    public boolean isBiometricConfigured() {
        return biometricEnabled && biometricId != null && !biometricId.trim().isEmpty();
    }
    
    public boolean isAccountCurrentlyLocked() {
        return accountLocked && 
               lockoutExpiry != null && 
               LocalDateTime.now().isBefore(lockoutExpiry);
    }
    
    public boolean canAttemptSecurity() {
        return !isAccountCurrentlyLocked() && failedSecurityAttempts < 5;
    }
}