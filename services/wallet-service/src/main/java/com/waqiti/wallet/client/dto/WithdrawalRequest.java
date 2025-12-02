package com.waqiti.wallet.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; /**
 * Request to withdraw from a wallet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequest {
    private String externalId;
    private String walletType;
    private BigDecimal amount;
    private String currency;
}
