package com.waqiti.payment.dwolla.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Dwolla Transfer Request DTO
 * 
 * Request object for creating transfers.
 */
@Data
@Builder
public class DwollaTransferRequest {
    private String source;
    private String destination;
    private DwollaAmount amount;
    private DwollaClearing clearing;
    private String correlationId;
    private String achDetails;
    private DwollaFees fees;
    
    @Data
    @Builder
    public static class DwollaAmount {
        private String currency;
        private BigDecimal value;
    }
    
    @Data
    @Builder
    public static class DwollaClearing {
        private String source; // "standard" or "next-available"
        private String destination; // "standard" or "next-available"
    }
    
    @Data
    @Builder
    public static class DwollaFees {
        private String source;
        private String destination;
    }
}