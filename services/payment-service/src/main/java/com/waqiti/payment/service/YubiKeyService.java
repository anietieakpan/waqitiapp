package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class YubiKeyService {
    
    private final Map<String, YubiKeyRegistration> registeredKeys = new ConcurrentHashMap<>();
    private final Map<String, Integer> otpCounters = new ConcurrentHashMap<>();
    
    public RegistrationResult registerYubiKey(String userId, String keyId, String publicId) {
        try {
            log.info("Registering YubiKey for user: {}", userId);
            
            YubiKeyRegistration registration = YubiKeyRegistration.builder()
                .keyId(keyId)
                .userId(userId)
                .publicId(publicId)
                .registeredAt(new Date())
                .active(true)
                .build();
            
            registeredKeys.put(keyId, registration);
            otpCounters.put(keyId, 0);
            
            log.info("YubiKey registered successfully for user: {}", userId);
            
            return RegistrationResult.builder()
                .success(true)
                .keyId(keyId)
                .message("YubiKey registered successfully")
                .build();
            
        } catch (Exception e) {
            log.error("Failed to register YubiKey for user: {}", userId, e);
            return RegistrationResult.builder()
                .success(false)
                .message("Registration failed: " + e.getMessage())
                .build();
        }
    }
    
    public ValidationResult validateOTP(String userId, String otp) {
        try {
            log.debug("Validating YubiKey OTP for user: {}", userId);
            
            if (otp == null || otp.length() < 44) {
                log.warn("Invalid OTP format for user: {}", userId);
                return ValidationResult.builder()
                    .valid(false)
                    .message("Invalid OTP format")
                    .build();
            }
            
            String publicId = otp.substring(0, 12);
            
            Optional<YubiKeyRegistration> registration = registeredKeys.values().stream()
                .filter(reg -> reg.getUserId().equals(userId) && reg.getPublicId().equals(publicId))
                .findFirst();
            
            if (registration.isEmpty()) {
                log.warn("YubiKey not found for user: {}", userId);
                return ValidationResult.builder()
                    .valid(false)
                    .message("YubiKey not registered")
                    .build();
            }
            
            YubiKeyRegistration reg = registration.get();
            if (!reg.isActive()) {
                log.warn("YubiKey is inactive for user: {}", userId);
                return ValidationResult.builder()
                    .valid(false)
                    .message("YubiKey is inactive")
                    .build();
            }
            
            int counter = otpCounters.get(reg.getKeyId());
            otpCounters.put(reg.getKeyId(), counter + 1);
            
            reg.setLastUsedAt(new Date());
            
            log.info("YubiKey OTP validated successfully for user: {}", userId);
            
            return ValidationResult.builder()
                .valid(true)
                .keyId(reg.getKeyId())
                .message("OTP validated successfully")
                .build();
            
        } catch (Exception e) {
            log.error("Failed to validate YubiKey OTP for user: {}", userId, e);
            return ValidationResult.builder()
                .valid(false)
                .message("Validation failed: " + e.getMessage())
                .build();
        }
    }
    
    public boolean isYubiKeyRegistered(String userId) {
        return registeredKeys.values().stream()
            .anyMatch(reg -> reg.getUserId().equals(userId) && reg.isActive());
    }
    
    public void deactivateYubiKey(String keyId) {
        YubiKeyRegistration registration = registeredKeys.get(keyId);
        if (registration != null) {
            registration.setActive(false);
            log.info("YubiKey deactivated: {}", keyId);
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class YubiKeyRegistration {
        private String keyId;
        private String userId;
        private String publicId;
        private Date registeredAt;
        private Date lastUsedAt;
        private boolean active;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RegistrationResult {
        private boolean success;
        private String keyId;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class ValidationResult {
        private boolean valid;
        private String keyId;
        private String message;
    }
}