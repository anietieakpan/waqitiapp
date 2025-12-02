package com.waqiti.wallet.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor; /**
 * Response from creating a wallet in the external system
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWalletResponse {
    private String externalId;
    private String status;
}
