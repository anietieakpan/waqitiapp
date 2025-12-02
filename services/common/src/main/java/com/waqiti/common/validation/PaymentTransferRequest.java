package com.waqiti.common.validation;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Payment transfer request for validation
 */
@Data
@Builder
public class PaymentTransferRequest {
    private String senderAccount;
    private String recipientAccount;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String reference;
    private String transferType;
}