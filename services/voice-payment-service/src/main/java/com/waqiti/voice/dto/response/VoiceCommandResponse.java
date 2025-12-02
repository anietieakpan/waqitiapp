package com.waqiti.voice.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for voice command processing
 *
 * Contains:
 * - Processing status and results
 * - Transcription and intent recognition
 * - Extracted entities and parameters
 * - Voice synthesis response (if applicable)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class VoiceCommandResponse {

    /**
     * Unique command ID
     */
    private UUID commandId;

    /**
     * Session ID
     */
    private String sessionId;

    /**
     * Processing status
     */
    @Builder.Default
    private Status status = Status.SUCCESS;

    /**
     * Success/failure indicator
     */
    @Builder.Default
    private Boolean success = true;

    /**
     * Human-readable message
     */
    private String message;

    /**
     * Voice response message (for text-to-speech)
     */
    private String voiceResponse;

    /**
     * URL to synthesized audio response (if available)
     */
    private String audioResponseUrl;

    /**
     * Transcribed text from voice command
     */
    private String transcribedText;

    /**
     * Recognized intent
     */
    private String intent;

    /**
     * Command type
     */
    private String commandType;

    /**
     * Confidence score (0.0 - 1.0)
     */
    private Double confidenceScore;

    /**
     * Biometric verification result
     */
    private Boolean biometricVerified;

    /**
     * Biometric confidence score
     */
    private Double biometricConfidence;

    /**
     * Extracted payment details (if payment command)
     */
    private PaymentDetails paymentDetails;

    /**
     * Whether confirmation is required
     */
    private Boolean requiresConfirmation;

    /**
     * Confirmation token (if confirmation required)
     */
    private String confirmationToken;

    /**
     * Extracted entities from command
     */
    private Map<String, Object> entities;

    /**
     * Additional metadata
     */
    private Map<String, Object> metadata;

    /**
     * Error details (if failed)
     */
    private ErrorDetails error;

    /**
     * Timestamp
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * Processing time in milliseconds
     */
    private Long processingTimeMs;

    // Nested classes

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaymentDetails {
        private BigDecimal amount;
        private String currency;
        private String recipientId;
        private String recipientName;
        private String purpose;
        private UUID transactionId;
        private String transactionStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetails {
        private String code;
        private String message;
        private String detail;
        private Map<String, Object> additionalInfo;
    }

    public enum Status {
        SUCCESS,
        PENDING_CONFIRMATION,
        PROCESSING,
        FAILED,
        INVALID_COMMAND,
        AUTHENTICATION_FAILED,
        INSUFFICIENT_CONFIDENCE,
        RATE_LIMITED
    }

    /**
     * Factory method for success response
     */
    public static VoiceCommandResponse success(String message, String voiceResponse) {
        return VoiceCommandResponse.builder()
                .status(Status.SUCCESS)
                .success(true)
                .message(message)
                .voiceResponse(voiceResponse)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Factory method for error response
     */
    public static VoiceCommandResponse error(String errorCode, String errorMessage) {
        return VoiceCommandResponse.builder()
                .status(Status.FAILED)
                .success(false)
                .message(errorMessage)
                .error(ErrorDetails.builder()
                        .code(errorCode)
                        .message(errorMessage)
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Factory method for pending confirmation response
     */
    public static VoiceCommandResponse pendingConfirmation(
            UUID commandId,
            String message,
            PaymentDetails paymentDetails,
            String confirmationToken) {
        return VoiceCommandResponse.builder()
                .commandId(commandId)
                .status(Status.PENDING_CONFIRMATION)
                .success(true)
                .message(message)
                .requiresConfirmation(true)
                .confirmationToken(confirmationToken)
                .paymentDetails(paymentDetails)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Factory method for authentication failure
     */
    public static VoiceCommandResponse authenticationFailed(String reason) {
        return VoiceCommandResponse.builder()
                .status(Status.AUTHENTICATION_FAILED)
                .success(false)
                .message("Voice authentication failed: " + reason)
                .error(ErrorDetails.builder()
                        .code("AUTH_FAILED")
                        .message(reason)
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * Factory method for low confidence response
     */
    public static VoiceCommandResponse insufficientConfidence(
            String transcribedText,
            Double confidence) {
        return VoiceCommandResponse.builder()
                .status(Status.INSUFFICIENT_CONFIDENCE)
                .success(false)
                .message("Could not understand command with sufficient confidence")
                .transcribedText(transcribedText)
                .confidenceScore(confidence)
                .voiceResponse("I'm sorry, I didn't quite understand that. Could you please repeat?")
                .error(ErrorDetails.builder()
                        .code("LOW_CONFIDENCE")
                        .message("Confidence score below threshold: " + confidence)
                        .build())
                .timestamp(LocalDateTime.now())
                .build();
    }
}
