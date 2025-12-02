package com.waqiti.common.events.security;

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
public class AccountSuspendedEvent implements Serializable {

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

    @NotBlank(message = "Suspension reason is required")
    @JsonProperty("suspension_reason")
    private String suspensionReason;

    @NotBlank(message = "Suspension type is required")
    @JsonProperty("suspension_type")
    private String suspensionType;

    @NotBlank(message = "Suspended by is required")
    @JsonProperty("suspended_by")
    private String suspendedBy;

    @NotNull(message = "Suspended at timestamp is required")
    @JsonProperty("suspended_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime suspendedAt;

    @JsonProperty("suspension_end_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime suspensionEndDate;

    @JsonProperty("suspension_notes")
    private String suspensionNotes;

    @JsonProperty("related_incident_id")
    private String relatedIncidentId;

    @JsonProperty("requires_manual_review")
    private Boolean requiresManualReview;
}