package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response DTO for bill validation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillValidationResponse {

    private Boolean isValid;

    private String status;

    private BigDecimal totalFee;

    private BigDecimal totalAmount;

    private String currency;

    private List<String> validationMessages;

    private List<String> warnings;

    private String errorMessage;
}
