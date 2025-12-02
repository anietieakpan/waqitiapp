package com.waqiti.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Compliance Check Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplianceCheckRequest {
    private UUID userId;
    private BigDecimal amount;
    private String transactionType;
    private String bankAccountLast4;
    private String routingNumber;
}