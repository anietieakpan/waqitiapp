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
 * Response DTO for bill payment
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BillPaymentResponse {

    private UUID paymentId;

    private UUID billId;

    private String billerName;

    private String accountNumber;

    private BigDecimal amount;

    private BigDecimal fee;

    private BigDecimal totalAmount;

    private String currency;

    private String status;

    private String paymentMethod;

    private String billerReferenceNumber;

    private String transactionReferenceNumber;

    private LocalDateTime initiatedAt;

    private LocalDateTime completedAt;

    private String notes;

    private String receiptUrl;
}
