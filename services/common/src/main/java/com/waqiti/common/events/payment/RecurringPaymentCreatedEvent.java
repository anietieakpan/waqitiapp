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
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringPaymentCreatedEvent implements Serializable {

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

    @NotBlank(message = "Recurring payment ID is required")
    @JsonProperty("recurring_payment_id")
    private String recurringPaymentId;

    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "Payment type is required")
    @JsonProperty("payment_type")
    private String paymentType;

    @NotNull(message = "Amount is required")
    @JsonProperty("amount")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @JsonProperty("currency")
    private String currency;

    @NotBlank(message = "Frequency is required")
    @JsonProperty("frequency")
    private String frequency;

    @NotNull(message = "Start date is required")
    @JsonProperty("start_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate startDate;

    @NotNull(message = "Next payment date is required")
    @JsonProperty("next_payment_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nextPaymentDate;

    @JsonProperty("end_date")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate endDate;

    @NotNull(message = "Created at timestamp is required")
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime createdAt;

    @JsonProperty("payee_name")
    private String payeeName;

    @JsonProperty("payee_account")
    private String payeeAccount;

    @JsonProperty("payment_description")
    private String paymentDescription;

    @JsonProperty("is_active")
    private Boolean isActive;
}