package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for scheduled bill payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduledPaymentResponse {

    private UUID paymentId;

    private UUID billId;

    private String billerName;

    private String accountNumber;

    private BigDecimal amount;

    private String currency;

    private String status;

    private String paymentMethod;

    private LocalDate scheduledDate;

    private LocalDateTime createdAt;

    private String notes;

    private Boolean canCancel;
}
