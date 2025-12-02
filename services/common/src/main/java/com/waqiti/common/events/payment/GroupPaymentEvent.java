package com.waqiti.common.events.payment;

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
public class GroupPaymentEvent implements Serializable {

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

    @NotBlank(message = "Group payment ID is required")
    @JsonProperty("group_payment_id")
    private String groupPaymentId;

    @NotBlank(message = "Organizer ID is required")
    @JsonProperty("organizer_id")
    private String organizerId;

    @NotBlank(message = "Event type is required")
    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("title")
    private String title;

    @JsonProperty("description")
    private String description;

    @NotNull(message = "Total amount is required")
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @NotBlank(message = "Currency is required")
    @JsonProperty("currency")
    private String currency;

    @NotNull(message = "Total participants is required")
    @JsonProperty("total_participants")
    private Integer totalParticipants;

    @JsonProperty("split_type")
    private String splitType;

    @JsonProperty("participant_id")
    private String participantId;

    @JsonProperty("participant_amount")
    private BigDecimal participantAmount;

    @JsonProperty("failure_reason")
    private String failureReason;

    @JsonProperty("cancellation_reason")
    private String cancellationReason;
}