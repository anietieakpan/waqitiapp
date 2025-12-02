package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for top biller information in reports
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TopBillerDto {

    private String billerName;

    private String category;

    private Integer paymentCount;

    private BigDecimal totalAmount;

    private String currency;

    private BigDecimal averageAmount;
}
