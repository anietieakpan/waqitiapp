package com.waqiti.wallet.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Response from withdrawing from a wallet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalResponse {
    private String externalId;
    private String status;
}
