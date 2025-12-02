/**
 * SMS Session Entity
 * Tracks SMS banking sessions for stateful interactions
 */
package com.waqiti.smsbanking.entity;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "sms_sessions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "session_id", unique = true, nullable = false)
    private String sessionId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status;
    
    @Column(name = "current_menu")
    private String currentMenu;
    
    @Column(name = "current_step")
    private String currentStep;
    
    @Type(JsonType.class)
    @Column(name = "session_data", columnDefinition = "jsonb")
    private JsonNode sessionData;
    
    @Type(JsonType.class)
    @Column(name = "transaction_context", columnDefinition = "jsonb")
    private JsonNode transactionContext;
    
    @Column(name = "language_preference")
    private String languagePreference;
    
    @Column(name = "pin_attempts")
    private Integer pinAttempts;
    
    @Column(name = "is_authenticated")
    private Boolean isAuthenticated;
    
    @Column(name = "last_activity", nullable = false)
    private LocalDateTime lastActivity;
    
    @Column(name = "timeout_minutes")
    private Integer timeoutMinutes;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "ended_at")
    private LocalDateTime endedAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (lastActivity == null) {
            lastActivity = LocalDateTime.now();
        }
        if (status == null) {
            status = SessionStatus.ACTIVE;
        }
        if (pinAttempts == null) {
            pinAttempts = 0;
        }
        if (isAuthenticated == null) {
            isAuthenticated = false;
        }
        if (timeoutMinutes == null) {
            timeoutMinutes = 5; // 5-minute default timeout
        }
        if (languagePreference == null) {
            languagePreference = "en";
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        lastActivity = LocalDateTime.now();
    }
    
    // Business logic methods
    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }
    
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(lastActivity.plusMinutes(timeoutMinutes));
    }
    
    public boolean canAttemptPin() {
        return pinAttempts < 3;
    }
    
    public void incrementPinAttempts() {
        this.pinAttempts = (this.pinAttempts != null ? this.pinAttempts : 0) + 1;
    }
    
    public void resetPinAttempts() {
        this.pinAttempts = 0;
    }
    
    public enum Channel {
        SMS,
        USSD
    }
    
    public enum SessionStatus {
        ACTIVE,
        COMPLETED,
        EXPIRED,
        TERMINATED,
        SUSPENDED
    }
}