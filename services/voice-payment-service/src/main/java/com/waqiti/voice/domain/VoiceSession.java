package com.waqiti.voice.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Voice Session Entity - Represents a conversational voice interaction session
 *
 * Multi-turn conversation support:
 * - Tracks context across multiple voice commands
 * - Maintains session state for complex interactions
 * - Handles session timeout and expiration
 *
 * Security:
 * - Session binding to user and device
 * - Activity tracking for fraud detection
 * - Automatic expiration after inactivity
 */
@Entity
@Table(name = "voice_sessions", indexes = {
    @Index(name = "idx_voice_sessions_session_id", columnList = "session_id", unique = true),
    @Index(name = "idx_voice_sessions_user_id", columnList = "user_id"),
    @Index(name = "idx_voice_sessions_status", columnList = "status"),
    @Index(name = "idx_voice_sessions_created_at", columnList = "created_at"),
    @Index(name = "idx_voice_sessions_user_status", columnList = "user_id,status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"conversationContext", "sessionMetadata"})
@EqualsAndHashCode(of = "id")
public class VoiceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotBlank(message = "Session ID is required")
    @Column(name = "session_id", nullable = false, unique = true, length = 100)
    private String sessionId;

    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // Session Type & Classification
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", nullable = false, length = 50)
    @Builder.Default
    private SessionType sessionType = SessionType.PAYMENT_ASSISTANT;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    // Language & Locale
    @NotBlank(message = "Language is required")
    @Column(name = "language", nullable = false, length = 20)
    @Builder.Default
    private String language = "en-US";

    @Column(name = "locale", length = 20)
    private String locale;

    // Device Information
    @Column(name = "device_id", length = 100)
    private String deviceId;

    @Column(name = "device_type", length = 50)
    private String deviceType;

    @Column(name = "device_model", length = 100)
    private String deviceModel;

    @Column(name = "platform", length = 50)
    private String platform;

    @Column(name = "app_version", length = 30)
    private String appVersion;

    // Session Activity Tracking
    @Column(name = "turn_count")
    @Builder.Default
    private Integer turnCount = 0;

    @Column(name = "command_count")
    @Builder.Default
    private Integer commandCount = 0;

    @Column(name = "successful_commands")
    @Builder.Default
    private Integer successfulCommands = 0;

    @Column(name = "failed_commands")
    @Builder.Default
    private Integer failedCommands = 0;

    // Conversation Context (maintains state across turns)
    @Type(JsonBinaryType.class)
    @Column(name = "conversation_context", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> conversationContext = new HashMap<>();

    @Column(name = "current_intent", length = 100)
    private String currentIntent;

    @Column(name = "pending_confirmation")
    @Builder.Default
    private Boolean pendingConfirmation = false;

    @Column(name = "last_command_id")
    private UUID lastCommandId;

    // Session Metadata
    @Type(JsonBinaryType.class)
    @Column(name = "session_metadata", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> sessionMetadata = new HashMap<>();

    // Location & Network
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "timezone", length = 50)
    private String timezone;

    // Session Duration & Timing
    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_activity_at")
    private LocalDateTime lastActivityAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "session_timeout_minutes")
    @Builder.Default
    private Integer sessionTimeoutMinutes = 15;

    // Session Quality Metrics
    @Column(name = "average_response_time_ms")
    private Long averageResponseTimeMs;

    @Column(name = "total_processing_time_ms")
    @Builder.Default
    private Long totalProcessingTimeMs = 0L;

    @Column(name = "average_confidence_score")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double averageConfidenceScore;

    // Session Termination
    @Enumerated(EnumType.STRING)
    @Column(name = "termination_reason", length = 50)
    private TerminationReason terminationReason;

    @Column(name = "termination_details", columnDefinition = "TEXT")
    private String terminationDetails;

    // Audit Fields
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    // Enums
    public enum SessionType {
        PAYMENT_ASSISTANT,      // Payment-focused interaction
        GENERAL_BANKING,        // General banking queries
        CUSTOMER_SERVICE,       // Customer support
        ENROLLMENT,             // Voice enrollment session
        AUTHENTICATION_ONLY,    // Quick auth check
        TRANSACTION_REVIEW,     // Review past transactions
        ACCOUNT_MANAGEMENT      // Account settings/management
    }

    public enum SessionStatus {
        ACTIVE,         // Currently active
        PAUSED,         // Temporarily paused
        EXPIRED,        // Timed out due to inactivity
        COMPLETED,      // Successfully completed
        TERMINATED,     // Manually terminated
        ERROR           // Ended due to error
    }

    public enum TerminationReason {
        USER_REQUEST,           // User ended session
        TIMEOUT,                // Inactivity timeout
        ERROR,                  // Technical error
        SECURITY_VIOLATION,     // Security issue detected
        MAX_TURNS_EXCEEDED,     // Too many conversation turns
        TRANSACTION_COMPLETE,   // Transaction completed
        SYSTEM_SHUTDOWN         // System maintenance
    }

    // Business Logic Methods

    /**
     * Check if session is currently active and valid
     */
    public boolean isActive() {
        if (status != SessionStatus.ACTIVE) {
            return false;
        }

        // Check if session has expired
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) {
            return false;
        }

        // Check inactivity timeout
        if (lastActivityAt != null) {
            LocalDateTime inactivityThreshold = lastActivityAt.plusMinutes(sessionTimeoutMinutes);
            if (LocalDateTime.now().isAfter(inactivityThreshold)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Increment conversation turn counter
     */
    public void incrementTurn(boolean wasSuccessful) {
        this.turnCount++;
        this.commandCount++;

        if (wasSuccessful) {
            this.successfulCommands++;
        } else {
            this.failedCommands++;
        }

        updateActivity();
    }

    /**
     * Update last activity timestamp and extend expiration
     */
    public void updateActivity() {
        this.lastActivityAt = LocalDateTime.now();
        this.expiresAt = LocalDateTime.now().plusMinutes(sessionTimeoutMinutes);
    }

    /**
     * Add context to conversation
     */
    public void addContext(String key, Object value) {
        if (conversationContext == null) {
            conversationContext = new HashMap<>();
        }
        conversationContext.put(key, value);
    }

    /**
     * Get context value
     */
    public Object getContext(String key) {
        if (conversationContext == null) {
            return null;
        }
        return conversationContext.get(key);
    }

    /**
     * Clear conversation context
     */
    public void clearContext() {
        if (conversationContext != null) {
            conversationContext.clear();
        }
    }

    /**
     * Pause session
     */
    public void pause() {
        this.status = SessionStatus.PAUSED;
    }

    /**
     * Resume paused session
     */
    public void resume() {
        if (this.status == SessionStatus.PAUSED) {
            this.status = SessionStatus.ACTIVE;
            updateActivity();
        }
    }

    /**
     * End session with reason
     */
    public void end(TerminationReason reason, String details) {
        this.status = SessionStatus.COMPLETED;
        this.terminationReason = reason;
        this.terminationDetails = details;
        this.endedAt = LocalDateTime.now();
    }

    /**
     * Terminate session due to error
     */
    public void terminateWithError(String errorDetails) {
        this.status = SessionStatus.ERROR;
        this.terminationReason = TerminationReason.ERROR;
        this.terminationDetails = errorDetails;
        this.endedAt = LocalDateTime.now();
    }

    /**
     * Mark session as expired
     */
    public void markExpired() {
        this.status = SessionStatus.EXPIRED;
        this.terminationReason = TerminationReason.TIMEOUT;
        this.endedAt = LocalDateTime.now();
    }

    /**
     * Update session state
     */
    public void updateState(String intent, boolean confirmationPending) {
        this.currentIntent = intent;
        this.pendingConfirmation = confirmationPending;
        updateActivity();
    }

    /**
     * Calculate session duration
     */
    public Duration getSessionDuration() {
        LocalDateTime end = endedAt != null ? endedAt : LocalDateTime.now();
        return Duration.between(startedAt, end);
    }

    /**
     * Calculate success rate
     */
    public double getSuccessRate() {
        if (commandCount == 0) {
            return 0.0;
        }
        return (double) successfulCommands / commandCount;
    }

    /**
     * Update response time metrics
     */
    public void recordResponseTime(long responseTimeMs) {
        if (totalProcessingTimeMs == null) {
            totalProcessingTimeMs = 0L;
        }
        totalProcessingTimeMs += responseTimeMs;

        if (commandCount > 0) {
            averageResponseTimeMs = totalProcessingTimeMs / commandCount;
        }
    }

    /**
     * Update confidence score
     */
    public void recordConfidenceScore(double score) {
        if (averageConfidenceScore == null) {
            averageConfidenceScore = score;
        } else {
            // Running average
            averageConfidenceScore = ((averageConfidenceScore * (commandCount - 1)) + score) / commandCount;
        }
    }

    /**
     * Pre-persist initialization
     */
    @PrePersist
    protected void onCreate() {
        if (sessionId == null) {
            sessionId = "VS-" + UUID.randomUUID().toString();
        }
        if (conversationContext == null) {
            conversationContext = new HashMap<>();
        }
        if (sessionMetadata == null) {
            sessionMetadata = new HashMap<>();
        }
        if (expiresAt == null) {
            expiresAt = LocalDateTime.now().plusMinutes(sessionTimeoutMinutes);
        }
        lastActivityAt = LocalDateTime.now();
    }

    /**
     * Pre-update validation
     */
    @PreUpdate
    protected void onUpdate() {
        if (conversationContext == null) {
            conversationContext = new HashMap<>();
        }
        if (sessionMetadata == null) {
            sessionMetadata = new HashMap<>();
        }
    }
}
