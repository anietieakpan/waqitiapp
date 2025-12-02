package com.waqiti.billpayment.events;

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
import java.time.Instant;
import java.time.LocalDate;

/**
 * Bill Payment Event - Main event model for bill payment operations
 *
 * Used for all bill payment event types:
 * - BILL_PAYMENT_INITIATED
 * - BILL_PAYMENT_SCHEDULED
 * - BILL_PAYMENT_COMPLETED
 * - BILL_PAYMENT_FAILED
 * - RECURRING_BILL_SETUP
 * - RECURRING_BILL_EXECUTED
 * - RECURRING_BILL_CANCELLED
 * - BILL_DUE_REMINDER
 * - BILLER_CONFIRMATION_RECEIVED
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillPaymentEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Event ID is required")
    @JsonProperty("event_id")
    private String eventId;

    @NotBlank(message = "Event type is required")
    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("correlation_id")
    private String correlationId;

    @NotNull(message = "Timestamp is required")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private Instant timestamp;

    @JsonProperty("version")
    private String version;

    // Payment identifiers
    @JsonProperty("payment_id")
    private String paymentId;

    @JsonProperty("recurring_setup_id")
    private String recurringSetupId;

    // User information
    @JsonProperty("user_id")
    private String userId;

    // Biller information
    @JsonProperty("biller_id")
    private String billerId;

    @JsonProperty("biller_name")
    private String billerName;

    @JsonProperty("bill_type")
    private String billType;

    @JsonProperty("account_number")
    private String accountNumber;

    // Financial details
    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    // Dates
    @JsonProperty("due_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate dueDate;

    @JsonProperty("scheduled_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduledDate;

    @JsonProperty("paid_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate paidDate;

    @JsonProperty("execution_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate executionDate;

    @JsonProperty("start_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @JsonProperty("next_payment_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextPaymentDate;

    // Recurring payment details
    @JsonProperty("frequency")
    private String frequency;

    @JsonProperty("is_auto_pay")
    private Boolean isAutoPay;

    // Payment details
    @JsonProperty("confirmation_number")
    private String confirmationNumber;

    @JsonProperty("reference_number")
    private String referenceNumber;

    @JsonProperty("memo")
    private String memo;

    // Failure/cancellation details
    @JsonProperty("failure_reason")
    private String failureReason;

    @JsonProperty("is_retryable")
    private Boolean isRetryable;

    @JsonProperty("cancellation_reason")
    private String cancellationReason;

    // Reminder details
    @JsonProperty("days_until_due")
    private Integer daysUntilDue;

    // Status
    @JsonProperty("status")
    private String status;

    @JsonProperty("previous_status")
    private String previousStatus;

    // Payment method
    @JsonProperty("payment_method")
    private String paymentMethod;

    // Transaction details
    @JsonProperty("transaction_id")
    private String transactionId;
}
