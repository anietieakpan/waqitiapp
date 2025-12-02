package com.waqiti.payment.dto.ledger;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinalizePaymentEntryResponse implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private UUID paymentId;
    private List<UUID> ledgerEntryIds;
    private boolean finalized;
    private String message;
}