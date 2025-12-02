package com.waqiti.common.events.user;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDeactivationEvent implements Serializable {

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

    @JsonProperty("email")
    private String email;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @NotBlank(message = "Deactivation type is required")
    @JsonProperty("deactivation_type")
    private String deactivationType;

    @NotBlank(message = "Deactivation reason is required")
    @JsonProperty("deactivation_reason")
    private String deactivationReason;

    @JsonProperty("deactivated_by")
    private String deactivatedBy;

    @JsonProperty("scheduled_reactivation")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime scheduledReactivation;

    @JsonProperty("ticket_id")
    private String ticketId;

    @JsonProperty("refund_method")
    private String refundMethod;

    @JsonProperty("final_balance")
    private BigDecimal finalBalance;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("refund_pending")
    private Boolean refundPending;

    @JsonProperty("regulation_reference")
    private String regulationReference;

    @JsonProperty("reporting_authority")
    private String reportingAuthority;

    @JsonProperty("preserve_data")
    private Boolean preserveData;

    @JsonProperty("ip_address")
    private String ipAddress;

    @JsonProperty("device_id")
    private String deviceId;

    @JsonProperty("notes")
    private String notes;

    @NotNull(message = "Deactivated at timestamp is required")
    @JsonProperty("deactivated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime deactivatedAt;

    @JsonProperty("previous_status")
    private String previousStatus;
}