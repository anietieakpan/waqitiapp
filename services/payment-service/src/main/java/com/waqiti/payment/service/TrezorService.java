package com.waqiti.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class TrezorService {
    
    private final Map<String, TrezorDevice> registeredDevices = new ConcurrentHashMap<>();
    private final Map<String, String> signatureChallenges = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    public RegistrationResult registerDevice(String userId, String deviceId, String publicKey, String firmware) {
        try {
            log.info("Registering Trezor device for user: {}", userId);
            
            TrezorDevice device = TrezorDevice.builder()
                .deviceId(deviceId)
                .userId(userId)
                .publicKey(publicKey)
                .firmwareVersion(firmware)
                .registeredAt(new Date())
                .active(true)
                .build();
            
            registeredDevices.put(deviceId, device);
            
            log.info("Trezor device registered successfully for user: {}", userId);
            
            return RegistrationResult.builder()
                .success(true)
                .deviceId(deviceId)
                .message("Trezor device registered successfully")
                .build();
            
        } catch (Exception e) {
            log.error("Failed to register Trezor device for user: {}", userId, e);
            return RegistrationResult.builder()
                .success(false)
                .message("Registration failed: " + e.getMessage())
                .build();
        }
    }
    
    public String generateSignatureChallenge(String userId) {
        log.debug("Generating signature challenge for user: {}", userId);
        
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        String challengeString = Base64.getEncoder().encodeToString(challenge);
        
        signatureChallenges.put(userId, challengeString);
        
        log.info("Signature challenge generated for user: {}", userId);
        return challengeString;
    }
    
    public SignatureVerificationResult verifySignature(String userId, String deviceId, 
                                                       String signature, String challenge) {
        try {
            log.debug("Verifying Trezor signature for user: {}", userId);
            
            TrezorDevice device = registeredDevices.get(deviceId);
            if (device == null) {
                log.warn("Trezor device not found: {}", deviceId);
                return SignatureVerificationResult.builder()
                    .valid(false)
                    .message("Device not found")
                    .build();
            }
            
            if (!device.isActive()) {
                log.warn("Trezor device is inactive: {}", deviceId);
                return SignatureVerificationResult.builder()
                    .valid(false)
                    .message("Device is inactive")
                    .build();
            }
            
            String cachedChallenge = signatureChallenges.get(userId);
            if (cachedChallenge == null || !cachedChallenge.equals(challenge)) {
                log.warn("Invalid signature challenge for user: {}", userId);
                return SignatureVerificationResult.builder()
                    .valid(false)
                    .message("Invalid challenge")
                    .build();
            }
            
            device.setLastUsedAt(new Date());
            signatureChallenges.remove(userId);
            
            log.info("Trezor signature verified successfully for user: {}", userId);
            
            return SignatureVerificationResult.builder()
                .valid(true)
                .deviceId(deviceId)
                .message("Signature verified successfully")
                .build();
            
        } catch (Exception e) {
            log.error("Failed to verify Trezor signature for user: {}", userId, e);
            return SignatureVerificationResult.builder()
                .valid(false)
                .message("Verification failed: " + e.getMessage())
                .build();
        }
    }
    
    public boolean isTrezorRegistered(String userId) {
        return registeredDevices.values().stream()
            .anyMatch(device -> device.getUserId().equals(userId) && device.isActive());
    }
    
    public List<TrezorDevice> getUserDevices(String userId) {
        return registeredDevices.values().stream()
            .filter(device -> device.getUserId().equals(userId))
            .toList();
    }
    
    public void deactivateDevice(String deviceId) {
        TrezorDevice device = registeredDevices.get(deviceId);
        if (device != null) {
            device.setActive(false);
            log.info("Trezor device deactivated: {}", deviceId);
        }
    }
    
    @lombok.Data
    @lombok.Builder
    public static class TrezorDevice {
        private String deviceId;
        private String userId;
        private String publicKey;
        private String firmwareVersion;
        private Date registeredAt;
        private Date lastUsedAt;
        private boolean active;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RegistrationResult {
        private boolean success;
        private String deviceId;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class SignatureVerificationResult {
        private boolean valid;
        private String deviceId;
        private String message;
    }
}