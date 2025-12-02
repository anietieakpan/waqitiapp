package com.waqiti.payment.dto;

import com.waqiti.payment.entity.BankAccountType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Base ACH request interface
 */
public interface ACHRequest {
    UUID getUserId();
    UUID getWalletId();
    BigDecimal getAmount();
    String getRoutingNumber();
    String getAccountNumber();
    String getAccountHolderName();
    BankAccountType getAccountType();
    String getDescription();
    String getIdempotencyKey();
}