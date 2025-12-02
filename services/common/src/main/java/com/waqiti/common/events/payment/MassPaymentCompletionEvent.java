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
public class MassPaymentCompletionEvent implements Serializable {

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

    @NotNull(message = "Total recipients is required")
    @JsonProperty("total_recipients")
    private Integer totalRecipients;

    @NotNull(message = "Successful payments is required")
    @JsonProperty("successful_payments")
    private Integer successfulPayments;

    @JsonProperty("failed_payments")
    private Integer failedPayments;

    @JsonProperty("pending_payments")
    private Integer pendingPayments;

    @NotNull(message = "Total amount is required")
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @NotBlank(message = "Currency is required")
    @JsonProperty("currency")
    private String currency;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("initiated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime initiatedAt;

    @NotNull(message = "Completed at timestamp is required")
    @JsonProperty("completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime completedAt;

    @JsonProperty("processing_duration_seconds")
    private Long processingDurationSeconds;

    @JsonProperty("recipient_details")
    private List<Map<String, Object>> recipientDetails;

    @JsonProperty("failure_reasons")
    private List<String> failureReasons;

    @JsonProperty("batch_description")
    private String batchDescription;

    @JsonProperty("batch_type")
    private String batchType;

    @JsonProperty("schedule_type")
    private String scheduleType;
}