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
public class FIDOAuthenticatorService {
    
    private final Map<String, FIDOCredential> registeredCredentials = new ConcurrentHashMap<>();
    private final Map<String, String> challengeCache = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    
    public String generateChallenge(String userId) {
        log.debug("Generating FIDO challenge for user: {}", userId);
        
        byte[] challenge = new byte[32];
        secureRandom.nextBytes(challenge);
        String challengeString = Base64.getEncoder().encodeToString(challenge);
        
        challengeCache.put(userId, challengeString);
        
        log.info("FIDO challenge generated for user: {}", userId);
        return challengeString;
    }
    
    public RegistrationResult registerAuthenticator(String userId, String credentialId, 
                                                    String publicKey, String attestation) {
        try {
            log.info("Registering FIDO authenticator for user: {}", userId);
            
            FIDOCredential credential = FIDOCredential.builder()
                .credentialId(credentialId)
                .userId(userId)
                .publicKey(publicKey)
                .attestation(attestation)
                .registeredAt(new Date())
                .lastUsedAt(new Date())
                .build();
            
            registeredCredentials.put(credentialId, credential);
            
            log.info("FIDO authenticator registered successfully for user: {}", userId);
            
            return RegistrationResult.builder()
                .success(true)
                .credentialId(credentialId)
                .message("Authenticator registered successfully")
                .build();
            
        } catch (Exception e) {
            log.error("Failed to register FIDO authenticator for user: {}", userId, e);
            return RegistrationResult.builder()
                .success(false)
                .message("Registration failed: " + e.getMessage())
                .build();
        }
    }
    
    public AuthenticationResult authenticate(String userId, String credentialId, 
                                            String signature, String challenge) {
        try {
            log.debug("Authenticating user with FIDO: userId={}, credentialId={}", userId, credentialId);
            
            FIDOCredential credential = registeredCredentials.get(credentialId);
            if (credential == null) {
                log.warn("FIDO credential not found: {}", credentialId);
                return AuthenticationResult.builder()
                    .success(false)
                    .message("Credential not found")
                    .build();
            }
            
            String cachedChallenge = challengeCache.get(userId);
            if (cachedChallenge == null || !cachedChallenge.equals(challenge)) {
                log.warn("Invalid FIDO challenge for user: {}", userId);
                return AuthenticationResult.builder()
                    .success(false)
                    .message("Invalid challenge")
                    .build();
            }
            
            credential.setLastUsedAt(new Date());
            challengeCache.remove(userId);
            
            log.info("FIDO authentication successful for user: {}", userId);
            
            return AuthenticationResult.builder()
                .success(true)
                .userId(userId)
                .credentialId(credentialId)
                .message("Authentication successful")
                .build();
            
        } catch (Exception e) {
            log.error("FIDO authentication failed for user: {}", userId, e);
            return AuthenticationResult.builder()
                .success(false)
                .message("Authentication failed: " + e.getMessage())
                .build();
        }
    }
    
    public boolean isAuthenticatorRegistered(String userId) {
        return registeredCredentials.values().stream()
            .anyMatch(cred -> cred.getUserId().equals(userId));
    }
    
    public List<FIDOCredential> getUserCredentials(String userId) {
        return registeredCredentials.values().stream()
            .filter(cred -> cred.getUserId().equals(userId))
            .toList();
    }
    
    @lombok.Data
    @lombok.Builder
    public static class FIDOCredential {
        private String credentialId;
        private String userId;
        private String publicKey;
        private String attestation;
        private Date registeredAt;
        private Date lastUsedAt;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class RegistrationResult {
        private boolean success;
        private String credentialId;
        private String message;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class AuthenticationResult {
        private boolean success;
        private String userId;
        private String credentialId;
        private String message;
    }
}