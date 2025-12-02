package com.waqiti.user.dto.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultiCurrencyWalletRequest {
    private String userId;
    private String currency;
    private String walletType;
}