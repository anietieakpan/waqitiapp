package com.waqiti.common.events.billpayment;

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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillPaymentStatusEvent implements Serializable {

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

    @JsonProperty("user_email")
    private String userEmail;

    @JsonProperty("user_phone")
    private String userPhone;

    @NotBlank(message = "Bill payment ID is required")
    @JsonProperty("bill_payment_id")
    private String billPaymentId;

    @NotBlank(message = "Biller name is required")
    @JsonProperty("biller_name")
    private String billerName;

    @JsonProperty("biller_id")
    private String billerId;

    @NotBlank(message = "Bill type is required")
    @JsonProperty("bill_type")
    private String billType;

    @JsonProperty("account_number")
    private String accountNumber;

    @NotNull(message = "Amount is required")
    @JsonProperty("amount")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @JsonProperty("currency")
    private String currency;

    @NotBlank(message = "Status is required")
    @JsonProperty("status")
    private String status;

    @JsonProperty("previous_status")
    private String previousStatus;

    @JsonProperty("reference_number")
    private String referenceNumber;

    @JsonProperty("due_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    @JsonProperty("scheduled_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduledDate;

    @JsonProperty("paid_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime paidDate;

    @JsonProperty("is_recurring")
    private Boolean isRecurring;

    @JsonProperty("recurring_frequency")
    private String recurringFrequency;

    @JsonProperty("next_scheduled_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextScheduledDate;

    @JsonProperty("failure_reason")
    private String failureReason;

    @JsonProperty("is_retryable")
    private Boolean isRetryable;

    @JsonProperty("payment_method")
    private String paymentMethod;

    @JsonProperty("transaction_id")
    private String transactionId;
}