package com.waqiti.common.auth.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * DTOs for Device Trust and Authentication Services
 */
public class DeviceAuthDTOs {

    @Data
    @Builder
    public static class DeviceFingerprint {
        private String fingerprintId;
        private String deviceId;
        private String hash;
        private DeviceType deviceType;
        private String deviceName;
        private String platform;
        private String browser;
        private String browserVersion;
        private String osName;
        private String osVersion;
        private String screenResolution;
        private int colorDepth;
        private String timezone;
        private String language;
        private List<String> plugins;
        private List<String> fonts;
        private String webGLVendor;
        private String webGLRenderer;
        private int hardwareConcurrency;
        private int deviceMemory;
        private boolean touchSupport;
        private String audioFingerprint;
        private String canvasFingerprint;
        private double confidence;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
        
        private Map<String, String> signals;
    }
    
    @Data
    @Builder
    public static class DeviceFingerprintRequest {
        @NotBlank
        private String deviceId;
        
        private String deviceName;
        private String platform;
        private String userAgent;
        private String browser;
        private String browserVersion;
        private String osName;
        private String osVersion;
        private String screenResolution;
        private int colorDepth;
        private String timezone;
        private String language;
        private List<String> plugins;
        private List<String> fonts;
        private String webGLVendor;
        private String webGLRenderer;
        private int hardwareConcurrency;
        private int deviceMemory;
        private boolean touchSupport;
        private String audioFingerprint;
        private String canvasFingerprint;
    }
    
    @Data
    @Builder
    public static class TrustedDevice {
        private String trustId;
        private String userId;
        private String deviceId;
        private String fingerprintHash;
        private String deviceName;
        private DeviceType deviceType;
        private String platform;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime trustedAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime trustedUntil;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime lastUsed;
        
        private TrustLevel trustLevel;
        private Map<String, Object> metadata;
        private boolean active;
    }
    
    @Data
    @Builder
    public static class TrustDeviceRequest {
        @NotBlank
        private String userId;
        
        @NotNull
        private DeviceFingerprint fingerprint;
        
        private String attestationToken;
        private String userConsent;
        private Map<String, Object> additionalData;
    }
    
    @Data
    @Builder
    public static class TrustedDeviceInfo {
        private String deviceId;
        private String deviceName;
        private DeviceType deviceType;
        private String platform;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime trustedAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime trustedUntil;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime lastUsed;
        
        private TrustLevel trustLevel;
        private boolean active;
    }
    
    @Data
    @Builder
    public static class DeviceVerificationRequest {
        @NotBlank
        private String userId;
        
        @NotNull
        private DeviceFingerprint fingerprint;
        
        private boolean strictValidation;
        private Map<String, Object> context;
    }
    
    @Data
    @Builder
    public static class DeviceVerificationResult {
        private boolean trusted;
        private VerificationStatus status;
        private String reason;
        private TrustedDevice trustedDevice;
        private DeviceRiskScore riskScore;
        private List<String> recommendations;
        
        public static DeviceVerificationResult notTrusted() {
            return DeviceVerificationResult.builder()
                .trusted(false)
                .status(VerificationStatus.NOT_TRUSTED)
                .reason("Device is not trusted")
                .build();
        }
        
        public static DeviceVerificationResult expired() {
            return DeviceVerificationResult.builder()
                .trusted(false)
                .status(VerificationStatus.EXPIRED)
                .reason("Device trust has expired")
                .build();
        }
        
        public static DeviceVerificationResult inactive() {
            return DeviceVerificationResult.builder()
                .trusted(false)
                .status(VerificationStatus.INACTIVE)
                .reason("Device trust is inactive")
                .build();
        }
        
        public static DeviceVerificationResult highRisk(DeviceRiskScore riskScore) {
            return DeviceVerificationResult.builder()
                .trusted(false)
                .status(VerificationStatus.HIGH_RISK)
                .reason("Device has high risk score")
                .riskScore(riskScore)
                .build();
        }
        
        public static DeviceVerificationResult trusted(TrustedDevice device, DeviceRiskScore riskScore) {
            return DeviceVerificationResult.builder()
                .trusted(true)
                .status(VerificationStatus.TRUSTED)
                .trustedDevice(device)
                .riskScore(riskScore)
                .build();
        }
        
        public static DeviceVerificationResult error(String reason) {
            return DeviceVerificationResult.builder()
                .trusted(false)
                .status(VerificationStatus.ERROR)
                .reason(reason)
                .build();
        }
    }
    
    @Data
    @Builder
    public static class DeviceRiskScore {
        private double score;
        private List<RiskFactor> factors;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime timestamp;
        
        public RiskLevel getRiskLevel() {
            if (score >= 0.8) return RiskLevel.CRITICAL;
            if (score >= 0.6) return RiskLevel.HIGH;
            if (score >= 0.4) return RiskLevel.MEDIUM;
            if (score >= 0.2) return RiskLevel.LOW;
            return RiskLevel.MINIMAL;
        }
    }
    
    @Data
    @lombok.AllArgsConstructor
    public static class RiskFactor {
        private String type;
        private double weight;
        private String description;
    }
    
    // Authentication Enhancement DTOs
    
    @Data
    @Builder
    public static class AuthenticationRequest {
        @NotBlank
        private String username;
        
        @NotBlank
        private String password;
        
        private String deviceId;
        private String deviceName;
        private String deviceType;
        private String ipAddress;
        private String userAgent;
        private String screenResolution;
        private String timezone;
        private String language;
        private boolean trustDevice;
        private boolean mfaProvided;
        private String mfaCode;
        private String mfaMethod;
        private Map<String, Object> deviceAttributes;
    }
    
    @Data
    @Builder
    public static class AuthenticationResult {
        private boolean success;
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
        private String sessionId;
        private UserInfo user;
        private boolean mfaRequired;
        private String mfaToken;
        private List<String> mfaMethods;
        private String errorMessage;
        private Map<String, Object> additionalData;
    }
    
    @Data
    @Builder
    public static class UserInfo {
        private String username;
        private String userId;
        private String email;
        private List<String> authorities;
        private boolean enabled;
        private Map<String, Object> attributes;
    }
    
    @Data
    @Builder
    public static class RefreshTokenRequest {
        @NotBlank
        private String refreshToken;
        
        private String deviceId;
        private String ipAddress;
        private Map<String, Object> context;
    }
    
    @Data
    @Builder
    public static class RefreshTokenResult {
        private String accessToken;
        private String refreshToken;
        private long expiresIn;
        private boolean rotated;
        private String errorMessage;
    }
    
    @Data
    @Builder
    public static class LogoutRequest {
        private String accessToken;
        private String refreshToken;
        private String sessionId;
        private boolean logoutAllSessions;
    }
    
    @Data
    @Builder
    public static class TokenPair {
        private String accessToken;
        private String refreshToken;
        private String tokenType;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime issuedAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime expiresAt;
    }
    
    @Data
    @Builder
    public static class RefreshTokenFamily {
        private String familyId;
        private String username;
        private String sessionId;
        private Set<String> tokens;
        private Set<String> usedTokens;
        private java.time.Instant createdAt;
        private java.time.Instant lastUsed;
        
        public void addToken(String token) {
            tokens.add(token);
            lastUsed = java.time.Instant.now();
        }
        
        public boolean isTokenUsed(String token) {
            return usedTokens.contains(token);
        }
        
        public void markTokenAsUsed(String token) {
            usedTokens.add(token);
            lastUsed = java.time.Instant.now();
        }
        
        public void setLastUsed(java.time.Instant lastUsed) {
            this.lastUsed = lastUsed;
        }
        
        public void cleanOldTokens(java.time.Duration maxAge) {
            java.time.Instant cutoff = java.time.Instant.now().minus(maxAge);
            if (createdAt.isBefore(cutoff)) {
                tokens.clear();
                usedTokens.clear();
            }
        }
    }
    
    // Enums
    
    public enum DeviceType {
        DESKTOP, MOBILE, TABLET, TV, UNKNOWN
    }
    
    public enum TrustLevel {
        LOW, MEDIUM, HIGH
    }
    
    public enum VerificationStatus {
        TRUSTED, NOT_TRUSTED, EXPIRED, INACTIVE, HIGH_RISK, ERROR
    }
    
    public enum RiskLevel {
        MINIMAL, LOW, MEDIUM, HIGH, CRITICAL
    }
    
    // Exceptions
    
    public static class DeviceFingerprintException extends RuntimeException {
        public DeviceFingerprintException(String message) {
            super(message);
        }
        
        public DeviceFingerprintException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class DeviceTrustException extends RuntimeException {
        public DeviceTrustException(String message) {
            super(message);
        }
        
        public DeviceTrustException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class AuthenticationFailedException extends RuntimeException {
        public AuthenticationFailedException(String message) {
            super(message);
        }
        
        public AuthenticationFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class AccountLockedException extends RuntimeException {
        public AccountLockedException(String message) {
            super(message);
        }
    }
    
    public static class InvalidMfaException extends RuntimeException {
        public InvalidMfaException(String message) {
            super(message);
        }
    }
    
    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }
    
    public static class TokenReuseDetectedException extends RuntimeException {
        public TokenReuseDetectedException(String message) {
            super(message);
        }
    }
    
    public static class TokenRefreshException extends RuntimeException {
        public TokenRefreshException(String message) {
            super(message);
        }
        
        public TokenRefreshException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class UnauthorizedException extends RuntimeException {
        public UnauthorizedException(String message) {
            super(message);
        }
    }
}