package com.waqiti.user.dto.wallet;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCreationResponse {
    private String walletId;
    private String userId;
    private String currency;
    private String status;
    private List<String> featuresEnabled;
}