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
 * Response DTO for auto-pay configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AutoPayResponse {

    private UUID autoPayId;

    private UUID billId;

    private String billerName;

    private String accountNumber;

    private String paymentMethod;

    private String amountType;

    private BigDecimal fixedAmount;

    private String currency;

    private String paymentTiming;

    private Integer daysBeforeDue;

    private Integer daysAfterDue;

    private LocalDate nextScheduledDate;

    private Boolean isEnabled;

    private Integer successfulPayments;

    private Integer failedPayments;

    private LocalDateTime lastPaymentDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private String notes;
}
