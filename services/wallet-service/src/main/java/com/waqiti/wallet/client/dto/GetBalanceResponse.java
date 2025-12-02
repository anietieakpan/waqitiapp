package com.waqiti.wallet.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal; /**
 * Response with the balance of a wallet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GetBalanceResponse {
    private BigDecimal balance;
    private String currency;
}
