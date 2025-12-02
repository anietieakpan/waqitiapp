package com.waqiti.wallet.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Response from depositing to a wallet
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositResponse {
    private String externalId;
    private String status;
}
