package com.waqiti.billpayment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for payment status inquiry
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaymentStatusResponse {

    private UUID paymentId;

    private String status;

    private String statusDescription;

    private BigDecimal amount;

    private String currency;

    private String billerName;

    private String accountNumber;

    private String billerReferenceNumber;

    private String transactionReferenceNumber;

    private LocalDateTime initiatedAt;

    private LocalDateTime completedAt;

    private LocalDateTime lastUpdatedAt;

    private String failureReason;

    private Boolean canRetry;

    private Boolean canCancel;
}
