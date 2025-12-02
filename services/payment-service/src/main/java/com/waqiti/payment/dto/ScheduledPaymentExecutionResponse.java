package com.waqiti.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID; /**
 * Response for scheduled payment execution
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduledPaymentExecutionResponse {
    private UUID id;
    private UUID transactionId;
    private BigDecimal amount;
    private String currency;
    private String status;
    private String errorMessage;
    private LocalDateTime executionDate;
}
