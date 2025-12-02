package com.waqiti.payment.dwolla.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Dwolla Balance DTO
 * 
 * Represents account balance from Dwolla API.
 */
@Data
public class DwollaBalance {
    private DwollaAmount balance;
    private DwollaAmount total;
    
    @Data
    public static class DwollaAmount {
        private String currency;
        private BigDecimal value;
    }
}