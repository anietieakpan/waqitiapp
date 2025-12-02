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
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MassPaymentCancellationEvent implements Serializable {

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

    @NotBlank(message = "Batch ID is required")
    @JsonProperty("batch_id")
    private String batchId;

    @NotBlank(message = "Batch name is required")
    @JsonProperty("batch_name")
    private String batchName;

    @NotBlank(message = "Initiator user ID is required")
    @JsonProperty("initiator_user_id")
    private String initiatorUserId;

    @JsonProperty("initiator_name")
    private String initiatorName;

    @JsonProperty("initiator_email")
    private String initiatorEmail;

    @NotNull(message = "Affected recipients is required")
    @JsonProperty("affected_recipients")
    private Integer affectedRecipients;

    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @NotBlank(message = "Currency is required")
    @JsonProperty("currency")
    private String currency;

    @NotBlank(message = "Cancellation reason is required")
    @JsonProperty("cancellation_reason")
    private String cancellationReason;

    @NotBlank(message = "Cancelled by is required")
    @JsonProperty("cancelled_by")
    private String cancelledBy;

    @NotNull(message = "Cancelled at timestamp is required")
    @JsonProperty("cancelled_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime cancelledAt;

    @JsonProperty("refund_amount")
    private BigDecimal refundAmount;

    @JsonProperty("refund_status")
    private String refundStatus;

    @JsonProperty("refund_method")
    private String refundMethod;

    @JsonProperty("refund_eta")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime refundEta;

    @JsonProperty("recipient_details")
    private List<Map<String, Object>> recipientDetails;

    @JsonProperty("batch_status_before_cancellation")
    private String batchStatusBeforeCancellation;

    @JsonProperty("cancellation_notes")
    private String cancellationNotes;
}