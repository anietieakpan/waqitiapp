package com.waqiti.payment.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID; /**
 * Request to transfer funds between wallets
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    private UUID sourceWalletId;
    private UUID targetWalletId;
    private BigDecimal amount;
    private String description;
}
