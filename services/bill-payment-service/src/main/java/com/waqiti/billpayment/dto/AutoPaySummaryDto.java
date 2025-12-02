package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for auto-pay summary
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AutoPaySummaryDto {

    private Integer totalAutoPayBills;

    private BigDecimal monthlyAutoPayAmount;

    private String currency;

    private LocalDate nextAutoPayDate;

    private List<AutoPayConfigDto> upcomingPayments;

    private Integer activeConfigs;

    private Integer pausedConfigs;
}
