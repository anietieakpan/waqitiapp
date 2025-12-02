package com.waqiti.voice.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UpdateTimestamp;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Voice Command Entity - Represents a voice command received from a user
 *
 * This entity stores all information about a voice command including:
 * - Original audio metadata
 * - Transcribed text
 * - Extracted payment intent and entities
 * - Processing status and confidence scores
 * - Biometric verification results
 *
 * Security: Contains sensitive voice data - must be encrypted at rest
 * Compliance: Subject to GDPR right to erasure
 */
@Entity
@Table(name = "voice_commands", indexes = {
    @Index(name = "idx_voice_commands_user_id", columnList = "user_id"),
    @Index(name = "idx_voice_commands_session_id", columnList = "session_id"),
    @Index(name = "idx_voice_commands_status", columnList = "processing_status"),
    @Index(name = "idx_voice_commands_created_at", columnList = "created_at"),
    @Index(name = "idx_voice_commands_user_session", columnList = "user_id,session_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"biometricData", "extractedEntities"})
@EqualsAndHashCode(of = "id")
public class VoiceCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @NotNull(message = "User ID is required")
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @NotBlank(message = "Session ID is required")
    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "command_id", unique = true, length = 100)
    private String commandId;

    // Audio Metadata
    @Column(name = "original_audio_url", length = 500)
    private String originalAudioUrl;

    @Column(name = "audio_duration_seconds")
    private Double audioDurationSeconds;

    @Column(name = "audio_format", length = 50)
    private String audioFormat;

    @Column(name = "audio_quality_score")
    @DecimalMin(value = "0.0", message = "Audio quality score must be >= 0")
    @DecimalMax(value = "1.0", message = "Audio quality score must be <= 1")
    private Double audioQualityScore;

    @Column(name = "ambient_noise_level")
    private Integer ambientNoiseLevel;

    // Speech Recognition Results
    @Convert(converter = com.waqiti.voice.security.encryption.EncryptedStringConverter.class)
    @Column(name = "transcribed_text", columnDefinition = "TEXT")
    private String transcribedText;

    @Column(name = "confidence_score")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double confidenceScore;

    @NotNull
    @Column(name = "language", nullable = false, length = 20)
    @Builder.Default
    private String language = "en-US";

    // Voice Biometric Verification
    @Column(name = "voice_signature_match")
    private Boolean voiceSignatureMatch;

    @Column(name = "security_score")
    @DecimalMin(value = "0.0")
    @DecimalMax(value = "1.0")
    private Double securityScore;

    @Convert(converter = com.waqiti.voice.security.encryption.EncryptedJsonConverter.class)
    @Type(JsonBinaryType.class)
    @Column(name = "biometric_data", columnDefinition = "jsonb")
    @JsonIgnore
    private Map<String, Object> biometricData;

    // Natural Language Processing Results
    @Column(name = "intent", length = 100)
    private String intent;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 50)
    @Builder.Default
    private CommandType commandType = CommandType.UNKNOWN;

    @Type(JsonBinaryType.class)
    @Column(name = "extracted_entities", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> extractedEntities = new HashMap<>();

    // Payment Details (extracted from command)
    @Column(name = "amount", precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", length = 3)
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3-letter ISO code")
    @Builder.Default
    private String currency = "USD";

    @Column(name = "recipient_id")
    private UUID recipientId;

    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    @Column(name = "purpose", columnDefinition = "TEXT")
    private String purpose;

    @Column(name = "payment_id")
    private UUID paymentId;

    // Processing Status
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 50)
    @Builder.Default
    private ProcessingStatus processingStatus = ProcessingStatus.RECEIVED;

    @Column(name = "confirmation_required")
    @Builder.Default
    private Boolean confirmationRequired = false;

    @Column(name = "is_confirmed")
    @Builder.Default
    private Boolean isConfirmed = false;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    // Error Handling
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    // Device & Context Information
    @Column(name = "device_info", columnDefinition = "TEXT")
    private String deviceInfo;

    @Column(name = "location", length = 200)
    private String location;

    @Type(JsonBinaryType.class)
    @Column(name = "context_data", columnDefinition = "jsonb")
    private Map<String, Object> contextData;

    // Additional Parameters
    @Type(JsonBinaryType.class)
    @Column(name = "parameters", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> parameters = new HashMap<>();

    // Audit Fields
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Version
    @Column(name = "version")
    private Long version;

    // Enums
    public enum CommandType {
        SEND_PAYMENT,
        REQUEST_PAYMENT,
        CHECK_BALANCE,
        TRANSACTION_HISTORY,
        SPLIT_BILL,
        PAY_BILL,
        TRANSFER_FUNDS,
        SET_REMINDER,
        CANCEL_PAYMENT,
        ADD_CONTACT,
        HELP_COMMAND,
        UNKNOWN
    }

    public enum ProcessingStatus {
        RECEIVED,           // Command received
        TRANSCRIBING,       // Converting speech to text
        PARSING,            // Extracting intent and entities
        VALIDATING,         // Validating command parameters
        CONFIRMING,         // Waiting for user confirmation
        PROCESSING,         // Executing the command
        COMPLETED,          // Successfully completed
        FAILED,             // Failed to process
        CANCELLED,          // User cancelled
        EXPIRED             // Confirmation timeout expired
    }

    // Business Logic Methods

    /**
     * Check if this is a payment-related command
     */
    public boolean isPaymentCommand() {
        return commandType == CommandType.SEND_PAYMENT ||
               commandType == CommandType.REQUEST_PAYMENT ||
               commandType == CommandType.SPLIT_BILL ||
               commandType == CommandType.PAY_BILL ||
               commandType == CommandType.TRANSFER_FUNDS;
    }

    /**
     * Check if command has expired (10 minutes timeout for confirmation)
     */
    public boolean isExpired() {
        if (!confirmationRequired || isConfirmed) {
            return false;
        }

        LocalDateTime expiryTime = createdAt.plusMinutes(10);
        return LocalDateTime.now().isAfter(expiryTime);
    }

    /**
     * Update processing status with timestamp
     */
    public void updateProcessingStatus(ProcessingStatus newStatus) {
        this.processingStatus = newStatus;

        if (newStatus == ProcessingStatus.COMPLETED || newStatus == ProcessingStatus.FAILED) {
            this.processedAt = LocalDateTime.now();
        }
    }

    /**
     * Record confirmation
     */
    public void confirm() {
        this.isConfirmed = true;
        this.confirmedAt = LocalDateTime.now();
    }

    /**
     * Increment retry count
     */
    public void incrementRetry() {
        if (this.retryCount == null) {
            this.retryCount = 0;
        }
        this.retryCount++;
    }

    /**
     * Check if max retries exceeded
     */
    public boolean hasExceededMaxRetries() {
        return retryCount != null && retryCount >= 3;
    }

    /**
     * Validate command has required payment details
     */
    public boolean hasValidPaymentDetails() {
        if (!isPaymentCommand()) {
            return true;
        }

        return amount != null &&
               amount.compareTo(BigDecimal.ZERO) > 0 &&
               currency != null &&
               !currency.isBlank() &&
               (recipientId != null || recipientName != null);
    }

    /**
     * Pre-persist validation
     */
    @PrePersist
    protected void onCreate() {
        if (commandId == null) {
            commandId = "CMD-" + UUID.randomUUID().toString();
        }
        if (extractedEntities == null) {
            extractedEntities = new HashMap<>();
        }
        if (parameters == null) {
            parameters = new HashMap<>();
        }
    }

    /**
     * Pre-update validation
     */
    @PreUpdate
    protected void onUpdate() {
        // Ensure maps are never null
        if (extractedEntities == null) {
            extractedEntities = new HashMap<>();
        }
        if (parameters == null) {
            parameters = new HashMap<>();
        }
    }
}
