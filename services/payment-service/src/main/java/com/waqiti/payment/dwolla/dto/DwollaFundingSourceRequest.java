package com.waqiti.payment.dwolla.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Dwolla Funding Source Request DTO
 * 
 * Request object for creating funding sources (bank accounts).
 */
@Data
@Builder
public class DwollaFundingSourceRequest {
    private String routingNumber;
    private String accountNumber;
    private String bankAccountType; // "checking" or "savings"
    private String name;
    private Boolean plaidToken;
    private String channels; // "debit", "credit"
}