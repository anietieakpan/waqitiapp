package com.waqiti.payment.plaid.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PlaidTransferReversalRequest {
    private String transferId;
    private String amount; // Amount to reverse (null for full reversal)
    private String description;
}