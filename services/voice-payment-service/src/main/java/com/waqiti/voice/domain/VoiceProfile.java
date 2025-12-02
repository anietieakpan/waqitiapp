package com.waqiti.voice.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Voice Profile Entity - User's voice biometric profile and preferences
 *
 * CRITICAL SECURITY:
 * - Contains biometric data (GDPR Article 9 - Special Category Data)
 * - Voice signatures must be encrypted at rest
 * - Requires explicit user consent for collection
 * - Subject to strict retention and deletion policies
 *
 * Compliance:
 * - GDPR: Right to access, rectification, erasure
 * - BIPA (Biometric Information Privacy Act): Illinois compliance
 * - PCI-DSS: Secure biometric authentication
 */
@Entity
@Table(name = "voice_profiles", indexes = {
    @Index(name = "idx_voice_profiles_user_id", columnList = "user_id", unique = true),
    @Index(name = "idx_voice_profiles_status", columnList = "enrollment_status"),
    @Index(name = "idx_voice_profiles_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"voiceSignature", "biometricFeatures", "voiceSamples"})
@EqualsAndHashCode(of = "id")
public class VoiceProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @NotBlank(message = "Profile name is required")
    @Size(max = 200, message = "Profile name must not exceed 200 characters")
    @Column(name = "profile_name", nullable = false, length = 200)
    private String profileName;

    // Enrollment Status
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "enrollment_status", nullable = false, length = 50)
    @Builder.Default
    private EnrollmentStatus enrollmentStatus = EnrollmentStatus.NOT_ENROLLED;

    @Column(name = "enrollment_started_at")
    private LocalDateTime enrollmentStartedAt;

    @Column(name = "enrollment_completed_at")
    private LocalDateTime enrollmentCompletedAt;

    @Column(name = "last_enrollment_attempt_at")
    private LocalDateTime lastEnrollmentAttemptAt;

    // Voice Biometric Data (ENCRYPTED)
    @Convert(converter = com.waqiti.voice.security.encryption.EncryptedJsonConverter.class)
    @Type(JsonBinaryType.class)
    @Column(name = "voice_signature", columnDefinition = "jsonb")
    @JsonIgnore  // Never expose in API responses
    private Map<String, Object> voiceSignature;

    @Convert(converter = com.waqiti.voice.security.encryption.EncryptedJsonConverter.class)
    @Type(JsonBinaryType.class)
    @Column(name = "biometric_features", columnDefinition = "jsonb")
    @JsonIgnore  // Never expose in API responses
    private Map<String, Object> biometricFeatures;

    @Type(JsonBinaryType.class)
    @Column(name = "voice_samples", columnDefinition = "jsonb")
    @JsonIgnore  // Never expose in API responses
    @Builder.Default
    private List<String> voiceSamples = new ArrayList<>();

    @Column(name = "sample_count")
    @Builder.Default
    private Integer sampleCount = 0;

    @Column(name = "required_samples")
    @Builder.Default
    private Integer requiredSamples = 3;

    @Column(name = "signature_version", length = 20)
    @Builder.Default
    private String signatureVersion = "1.0";

    // Security Settings
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "security_level", nullable = false, length = 20)
    @Builder.Default
    private SecurityLevel securityLevel = SecurityLevel.STANDARD;

    @Column(name = "verification_threshold")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    @Builder.Default
    private Double verificationThreshold = 0.85;

    @Column(name = "anti_spoofing_enabled")
    @Builder.Default
    private Boolean antiSpoofingEnabled = true;

    @Column(name = "liveness_detection_enabled")
    @Builder.Default
    private Boolean livenessDetectionEnabled = true;

    // Language & Preferences
    @NotNull
    @Column(name = "preferred_language", nullable = false, length = 20)
    @Builder.Default
    private String preferredLanguage = "en-US";

    @Type(JsonBinaryType.class)
    @Column(name = "supported_languages", columnDefinition = "jsonb")
    @Builder.Default
    private List<String> supportedLanguages = new ArrayList<>(Arrays.asList("en-US"));

    @Type(JsonBinaryType.class)
    @Column(name = "preferences", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> preferences = new HashMap<>();

    // Authentication Statistics
    @Column(name = "successful_auth_count")
    @Builder.Default
    private Long successfulAuthCount = 0L;

    @Column(name = "failed_auth_count")
    @Builder.Default
    private Long failedAuthCount = 0L;

    @Column(name = "last_successful_auth_at")
    private LocalDateTime lastSuccessfulAuthAt;

    @Column(name = "last_failed_auth_at")
    private LocalDateTime lastFailedAuthAt;

    @Column(name = "consecutive_failures")
    @Builder.Default
    private Integer consecutiveFailures = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    // Confidence & Quality Metrics
    @Column(name = "average_confidence_score")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double averageConfidenceScore;

    @Column(name = "min_confidence_score")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double minConfidenceScore;

    @Column(name = "max_confidence_score")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double maxConfidenceScore;

    // Consent & Compliance
    @Column(name = "biometric_consent_given")
    @Builder.Default
    private Boolean biometricConsentGiven = false;

    @Column(name = "biometric_consent_date")
    private LocalDateTime biometricConsentDate;

    @Column(name = "data_retention_agreed")
    @Builder.Default
    private Boolean dataRetentionAgreed = false;

    @Column(name = "data_deletion_requested")
    @Builder.Default
    private Boolean dataDeletionRequested = false;

    @Column(name = "data_deletion_scheduled_at")
    private LocalDateTime dataDeletionScheduledAt;

    // Audit Fields
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Version
    @Column(name = "version")
    private Long version;

    // Enums
    public enum EnrollmentStatus {
        NOT_ENROLLED,      // No enrollment attempted
        IN_PROGRESS,       // Enrollment in progress
        PARTIALLY_ENROLLED, // Some samples collected
        FULLY_ENROLLED,    // All samples collected and processed
        FAILED,            // Enrollment failed
        EXPIRED            // Enrollment expired (incomplete)
    }

    public enum SecurityLevel {
        BASIC,      // Text-based verification only
        STANDARD,   // Voice biometric + text
        HIGH,       // Voice biometric + liveness + anti-spoofing
        MAXIMUM     // All security features + additional MFA
    }

    // Business Logic Methods

    /**
     * Check if profile can be used for authentication
     */
    public boolean canAuthenticate() {
        return enrollmentStatus == EnrollmentStatus.FULLY_ENROLLED &&
               !isLocked() &&
               biometricConsentGiven &&
               !dataDeletionRequested &&
               voiceSignature != null &&
               !voiceSignature.isEmpty();
    }

    /**
     * Check if profile is locked due to consecutive failures
     */
    public boolean isLocked() {
        if (lockedUntil == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(lockedUntil);
    }

    /**
     * Check if fully enrolled
     */
    public boolean isFullyEnrolled() {
        return enrollmentStatus == EnrollmentStatus.FULLY_ENROLLED &&
               sampleCount >= requiredSamples;
    }

    /**
     * Reset enrollment for re-enrollment
     */
    public void resetEnrollment() {
        this.enrollmentStatus = EnrollmentStatus.NOT_ENROLLED;
        this.voiceSamples = new ArrayList<>();
        this.sampleCount = 0;
        this.voiceSignature = null;
        this.biometricFeatures = null;
        this.enrollmentStartedAt = null;
        this.enrollmentCompletedAt = null;
    }

    /**
     * Add voice sample during enrollment
     */
    public void addVoiceSample(String sampleUrl) {
        if (voiceSamples == null) {
            voiceSamples = new ArrayList<>();
        }
        voiceSamples.add(sampleUrl);
        sampleCount = voiceSamples.size();

        if (enrollmentStatus == EnrollmentStatus.NOT_ENROLLED) {
            enrollmentStatus = EnrollmentStatus.IN_PROGRESS;
            enrollmentStartedAt = LocalDateTime.now();
        } else if (sampleCount < requiredSamples) {
            enrollmentStatus = EnrollmentStatus.PARTIALLY_ENROLLED;
        }

        lastEnrollmentAttemptAt = LocalDateTime.now();
    }

    /**
     * Update biometric features during enrollment
     */
    public void updateBiometricFeatures(Map<String, Object> features) {
        if (this.biometricFeatures == null) {
            this.biometricFeatures = new HashMap<>();
        }
        this.biometricFeatures.putAll(features);
    }

    /**
     * Complete enrollment process
     */
    public void completeEnrollment(Map<String, Object> finalVoiceSignature) {
        this.voiceSignature = finalVoiceSignature;
        this.enrollmentStatus = EnrollmentStatus.FULLY_ENROLLED;
        this.enrollmentCompletedAt = LocalDateTime.now();
    }

    /**
     * Record successful authentication
     */
    public void recordSuccessfulAuth() {
        this.successfulAuthCount++;
        this.lastSuccessfulAuthAt = LocalDateTime.now();
        this.lastUsedAt = LocalDateTime.now();
        this.consecutiveFailures = 0;
        this.lockedUntil = null;  // Unlock if was locked
    }

    /**
     * Record failed authentication
     */
    public void recordFailedAuth() {
        this.failedAuthCount++;
        this.lastFailedAuthAt = LocalDateTime.now();
        this.consecutiveFailures++;

        // Lock account after 5 consecutive failures (15 minutes)
        if (consecutiveFailures >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(15);
        }
    }

    /**
     * Update confidence score statistics
     */
    public void updateConfidenceScore(Double newScore) {
        if (newScore == null) {
            return;
        }

        if (averageConfidenceScore == null) {
            averageConfidenceScore = newScore;
            minConfidenceScore = newScore;
            maxConfidenceScore = newScore;
        } else {
            // Calculate running average
            long totalAuth = successfulAuthCount + failedAuthCount;
            averageConfidenceScore = ((averageConfidenceScore * (totalAuth - 1)) + newScore) / totalAuth;
            minConfidenceScore = Math.min(minConfidenceScore, newScore);
            maxConfidenceScore = Math.max(maxConfidenceScore, newScore);
        }
    }

    /**
     * Request data deletion (GDPR Right to Erasure)
     */
    public void requestDataDeletion() {
        this.dataDeletionRequested = true;
        this.dataDeletionScheduledAt = LocalDateTime.now().plusDays(30); // 30-day retention
    }

    /**
     * Pre-persist validation
     */
    @PrePersist
    protected void onCreate() {
        if (preferences == null) {
            preferences = new HashMap<>();
        }
        if (voiceSamples == null) {
            voiceSamples = new ArrayList<>();
        }
        if (supportedLanguages == null) {
            supportedLanguages = new ArrayList<>(Arrays.asList("en-US"));
        }
    }

    /**
     * Pre-update validation
     */
    @PreUpdate
    protected void onUpdate() {
        if (preferences == null) {
            preferences = new HashMap<>();
        }
        if (voiceSamples == null) {
            voiceSamples = new ArrayList<>();
        }
    }
}
