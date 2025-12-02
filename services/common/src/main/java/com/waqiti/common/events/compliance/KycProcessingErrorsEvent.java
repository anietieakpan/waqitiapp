package com.waqiti.common.events.compliance;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycProcessingErrorsEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Event ID is required")
    @JsonProperty("event_id")
    private String eventId;

    @NotBlank(message = "Correlation ID is required")
    @JsonProperty("correlation_id")
    private String correlationId;

    @NotNull(message = "Timestamp is required")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @JsonProperty("event_version")
    private String eventVersion = "1.0";

    @NotBlank(message = "Event source is required")
    @JsonProperty("source")
    private String source;

    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "Verification ID is required")
    @JsonProperty("verification_id")
    private String verificationId;

    @NotBlank(message = "Error type is required")
    @JsonProperty("error_type")
    private String errorType;

    @NotBlank(message = "Error code is required")
    @JsonProperty("error_code")
    private String errorCode;

    @NotBlank(message = "Error message is required")
    @JsonProperty("error_message")
    private String errorMessage;

    @JsonProperty("error_severity")
    private String errorSeverity;

    @JsonProperty("error_category")
    private String errorCategory;

    @JsonProperty("processing_stage")
    private String processingStage;

    @JsonProperty("document_type")
    private String documentType;

    @JsonProperty("document_id")
    private String documentId;

    @JsonProperty("verification_provider")
    private String verificationProvider;

    @JsonProperty("retry_count")
    private Integer retryCount;

    @JsonProperty("max_retries")
    private Integer maxRetries;

    @JsonProperty("stack_trace")
    private String stackTrace;

    @JsonProperty("resolution_required")
    private Boolean resolutionRequired;

    @JsonProperty("resolved_by")
    private String resolvedBy;

    @JsonProperty("resolved_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime resolvedAt;

    @JsonProperty("resolution_notes")
    private String resolutionNotes;
}