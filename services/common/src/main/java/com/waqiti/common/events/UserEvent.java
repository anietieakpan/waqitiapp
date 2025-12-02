package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * User-related event for event sourcing
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class UserEvent extends FinancialEvent {
    
    private UUID eventId;
    private String eventType;
    private String username;
    private String email;
    private String phoneNumber;
    private String userStatus;
    private List<String> roles;
    private Instant timestamp;
    private String description;
    private Map<String, Object> metadata;
    private UUID correlationId;
    private String sourceSystem;
    private String ipAddress;
    private String userAgent;
    private String sessionId;
    private Map<String, Object> changes;
    private PersonalInfo personalInfo;

    // Override DomainEvent methods to convert UUID to String
    public String getEventId() {
        return eventId != null ? eventId.toString() : null;
    }

    public String getCorrelationId() {
        return correlationId != null ? correlationId.toString() : null;
    }

    /**
     * User event types
     */
    public enum EventType {
        USER_REGISTERED,
        USER_ACTIVATED,
        USER_DEACTIVATED,
        USER_SUSPENDED,
        USER_DELETED,
        USER_UPDATED,
        USER_LOGGED_IN,
        USER_LOGGED_OUT,
        PASSWORD_CHANGED,
        PASSWORD_RESET_REQUESTED,
        PASSWORD_RESET_COMPLETED,
        EMAIL_VERIFIED,
        PHONE_VERIFIED,
        MFA_ENABLED,
        MFA_DISABLED,
        PROFILE_UPDATED,
        PREFERENCES_UPDATED,
        ROLE_ASSIGNED,
        ROLE_REVOKED,
        PERMISSION_GRANTED,
        PERMISSION_REVOKED
    }
    
    /**
     * Create user registered event
     */
    public static UserEvent userRegistered(UUID userId, String username, String email) {
        return UserEvent.builder()
            .eventId(UUID.randomUUID())
            .userId(userId)
            .username(username)
            .email(email)
            .eventType(EventType.USER_REGISTERED.name())
            .userStatus("PENDING_ACTIVATION")
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create user activated event
     */
    public static UserEvent userActivated(UUID userId) {
        return UserEvent.builder()
            .eventId(UUID.randomUUID())
            .userId(userId)
            .eventType(EventType.USER_ACTIVATED.name())
            .userStatus("ACTIVE")
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create user logged in event
     */
    public static UserEvent userLoggedIn(UUID userId, String ipAddress, String userAgent, String sessionId) {
        return UserEvent.builder()
            .eventId(UUID.randomUUID())
            .userId(userId)
            .eventType(EventType.USER_LOGGED_IN.name())
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .sessionId(sessionId)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create user logged out event
     */
    public static UserEvent userLoggedOut(UUID userId, String sessionId) {
        return UserEvent.builder()
            .eventId(UUID.randomUUID())
            .userId(userId)
            .eventType(EventType.USER_LOGGED_OUT.name())
            .sessionId(sessionId)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create password changed event
     */
    public static UserEvent passwordChanged(UUID userId, String ipAddress) {
        return UserEvent.builder()
            .eventId(UUID.randomUUID())
            .userId(userId)
            .eventType(EventType.PASSWORD_CHANGED.name())
            .ipAddress(ipAddress)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Create profile updated event
     */
    public static UserEvent profileUpdated(UUID userId, Map<String, Object> changes) {
        return UserEvent.builder()
            .eventId(UUID.randomUUID())
            .userId(userId)
            .eventType(EventType.PROFILE_UPDATED.name())
            .changes(changes)
            .timestamp(Instant.now())
            .build();
    }
    
    /**
     * Check if user is active
     */
    public boolean isActive() {
        return "ACTIVE".equals(userStatus);
    }
    
    /**
     * Check if user is suspended
     */
    public boolean isSuspended() {
        return "SUSPENDED".equals(userStatus);
    }
    
    /**
     * Check if this is a security event
     */
    public boolean isSecurityEvent() {
        return eventType != null && (
            eventType.contains("PASSWORD") ||
            eventType.contains("MFA") ||
            eventType.contains("LOGGED") ||
            eventType.contains("SUSPENDED") ||
            eventType.contains("ROLE") ||
            eventType.contains("PERMISSION")
        );
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
    
    /**
     * Create a copy of this user event
     */
    public UserEvent copy() {
        return UserEvent.builder()
            .eventId(this.eventId)
            .userId(super.userId)
            .eventType(this.eventType)
            .username(this.username)
            .email(this.email)
            .phoneNumber(this.phoneNumber)
            .userStatus(this.userStatus)
            .roles(this.roles)
            .timestamp(this.timestamp)
            .description(this.description)
            .metadata(this.metadata)
            .correlationId(this.correlationId)
            .sourceSystem(this.sourceSystem)
            .ipAddress(this.ipAddress)
            .userAgent(this.userAgent)
            .sessionId(this.sessionId)
            .changes(this.changes)
            .personalInfo(this.personalInfo)
            .build();
    }
    
    /**
     * Get personal information (if any)
     */
    public PersonalInfo getPersonalInfo() {
        return personalInfo;
    }
    
    /**
     * Simple personal info container for sensitive data masking
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonalInfo {
        private String firstName;
        private String lastName;
        private String ssn;
        private String dateOfBirth;
        private String address;
        
        public void maskSensitiveData() {
            if (ssn != null && ssn.length() > 4) {
                ssn = "***-**-" + ssn.substring(ssn.length() - 4);
            }
            if (dateOfBirth != null) {
                dateOfBirth = "****-**-**";
            }
            if (address != null && address.length() > 10) {
                address = address.substring(0, 10) + "...";
            }
        }
    }
}