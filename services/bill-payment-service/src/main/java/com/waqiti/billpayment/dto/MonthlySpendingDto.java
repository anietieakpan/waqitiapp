package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for monthly spending data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MonthlySpendingDto {

    private Integer year;

    private Integer month;

    private String monthName;

    private BigDecimal totalAmount;

    private Integer paymentCount;

    private String currency;

    private BigDecimal averageAmount;
}
