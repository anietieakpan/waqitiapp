package com.waqiti.billpayment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Request to credit amount to wallet (refund)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCreditRequest {
    private String userId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String referenceId;
    private String referenceType;
    private Map<String, Object> metadata;
}
