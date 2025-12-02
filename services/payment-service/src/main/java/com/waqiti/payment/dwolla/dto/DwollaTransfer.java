package com.waqiti.payment.dwolla.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Dwolla Transfer DTO
 * 
 * Represents a transfer from Dwolla API.
 */
@Data
public class DwollaTransfer {
    private String id;
    private String status;
    private DwollaAmount amount;
    private LocalDateTime created;
    private LocalDateTime processingChannel;
    private String correlationId;
    private DwollaIndividualAchDetails individualAchDetails;
    private DwollaClearing clearing;
    
    // HAL Links
    private Map<String, Object> _links;
    
    @Data
    public static class DwollaAmount {
        private String currency;
        private BigDecimal value;
    }
    
    @Data
    public static class DwollaIndividualAchDetails {
        private String source;
        private String destination;
        private String addenda;
    }
    
    @Data
    public static class DwollaClearing {
        private String source;
        private String destination;
    }
}