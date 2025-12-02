package com.waqiti.payment.dto;

import com.waqiti.payment.entity.BankAccountType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Banking API Request DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankingAPIRequest {
    private UUID transferId;
    private BigDecimal amount;
    private String direction;
    private String routingNumber;
    private String accountNumber;
    private String accountHolderName;
    private BankAccountType accountType;
}