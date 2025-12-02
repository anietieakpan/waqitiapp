package com.waqiti.payment.dto.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordRefundUpdateResponse {
    
    private boolean success;
    
    private String ledgerEntryId;
    
    private BigDecimal refundRecorded;
    
    private Instant timestamp;
}