package com.waqiti.common.session.dto;

import lombok.Builder;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * DTOs for Session Management Service
 */
public class SessionDTOs {

    @Data
    @Builder
    public static class UserSession {
        @NotBlank
        private String sessionId;
        
        @NotBlank
        private String username;
        
        private String userId;
        private List<String> authorities;
        
        @NotBlank
        private String ipAddress;
        
        private String previousIpAddress;
        private String userAgent;
        private String deviceId;
        private String deviceType;
        private String deviceName;
        private GeoLocation location;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime lastAccessedAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime expiresAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime absoluteExpiresAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime terminatedAt;
        
        private String terminationReason;
        private String lastActivityType;
        private String lastActivityDetails;
        private Map<String, Object> attributes;
        private boolean active;
        
        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiresAt) || 
                   LocalDateTime.now().isAfter(absoluteExpiresAt);
        }
    }
    
    @Data
    @Builder
    public static class SessionCreationRequest {
        @NotBlank
        private String username;
        
        private String userId;
        private List<String> authorities;
        
        @NotBlank
        private String ipAddress;
        
        private String userAgent;
        private String deviceId;
        private String deviceType;
        private String deviceName;
        private GeoLocation location;
        private boolean forceCreate;
        private boolean newDevice;
    }
    
    @Data
    @Builder
    public static class SessionInfo {
        private String sessionId;
        private String username;
        private String deviceId;
        private String deviceType;
        private String deviceName;
        private String ipAddress;
        private GeoLocation location;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime createdAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime lastAccessedAt;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime expiresAt;
        
        private boolean active;
    }
    
    @Data
    @Builder
    public static class SessionActivityUpdate {
        private String activityType;
        private String activityDetails;
        private String ipAddress;
        private String userAgent;
        private GeoLocation location;
    }
    
    @Data
    @Builder
    public static class SessionValidationRequest {
        private String ipAddress;
        private String userAgent;
        private boolean validateIp;
        private boolean validateUserAgent;
        private boolean strictValidation;
    }
    
    @Data
    @Builder
    public static class SessionValidationResult {
        private boolean valid;
        private String reason;
        private UserSession session;
        
        public static SessionValidationResult valid(UserSession session) {
            return SessionValidationResult.builder()
                .valid(true)
                .session(session)
                .build();
        }
        
        public static SessionValidationResult invalid(String reason) {
            return SessionValidationResult.builder()
                .valid(false)
                .reason(reason)
                .build();
        }
        
        public static SessionValidationResult expired() {
            return SessionValidationResult.builder()
                .valid(false)
                .reason("Session expired")
                .build();
        }
    }
    
    @Data
    @Builder
    public static class SessionStatistics {
        private long totalActiveSessions;
        private long totalUsers;
        private double averageSessionsPerUser;
        private Map<String, Long> sessionsByDeviceType;
        private Map<String, Long> sessionsByLocation;
        
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime timestamp;
    }
    
    @Data
    @Builder
    public static class GeoLocation {
        private double latitude;
        private double longitude;
        private String country;
        private String city;
        private String region;
        private String postalCode;
        private String timezone;
        
        @Override
        public String toString() {
            return String.format("%s, %s, %s", city, region, country);
        }
    }
    
    // Exceptions
    
    public static class SessionCreationException extends RuntimeException {
        public SessionCreationException(String message) {
            super(message);
        }
        
        public SessionCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class SessionLimitExceededException extends RuntimeException {
        public SessionLimitExceededException(String message) {
            super(message);
        }
    }
    
    public static class SessionNotFoundException extends RuntimeException {
        public SessionNotFoundException(String message) {
            super(message);
        }
    }
}