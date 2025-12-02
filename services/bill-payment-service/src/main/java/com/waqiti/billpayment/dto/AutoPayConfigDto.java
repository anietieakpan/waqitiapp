package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DTO for auto-pay configuration details
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AutoPayConfigDto {

    private UUID id;

    private UUID billId;

    private String billerName;

    private String accountNumber;

    private String paymentMethod;

    private String amountType;

    private BigDecimal fixedAmount;

    private String paymentTiming;

    private Integer daysBeforeDue;

    private LocalDate nextScheduledDate;

    private Boolean isEnabled;
}
