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
 * Response DTO for recurring payment configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RecurringPaymentResponse {

    private UUID recurringPaymentId;

    private UUID billId;

    private String billerName;

    private String accountNumber;

    private String frequency;

    private BigDecimal amount;

    private String currency;

    private String status;

    private String paymentMethod;

    private LocalDate startDate;

    private LocalDate endDate;

    private LocalDate nextPaymentDate;

    private Integer successfulPayments;

    private Integer failedPayments;

    private LocalDateTime createdAt;

    private String notes;

    private Boolean isActive;
}
