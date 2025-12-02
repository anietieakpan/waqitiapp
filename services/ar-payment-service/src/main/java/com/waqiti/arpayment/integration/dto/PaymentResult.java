package com.waqiti.arpayment.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Payment result DTO from payment service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResult {

    private boolean success;
    private String transactionId;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED, REVERSED
    private BigDecimal amount;
    private String currency;
    private String errorMessage;
    private String errorCode;
    private Instant timestamp;
    private String confirmationNumber;
    private boolean fallbackMode;
    private String processorResponse;
}
