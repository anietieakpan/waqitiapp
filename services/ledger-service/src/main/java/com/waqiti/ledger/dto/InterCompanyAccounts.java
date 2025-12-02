package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Inter-Company Accounts DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterCompanyAccounts {
    
    private UUID entityId;
    private UUID counterpartyId;
    private UUID receivableAccountId;
    private UUID payableAccountId;
    private UUID revenueAccountId;
    private UUID expenseAccountId;
    private String currency;
    private Boolean active;
}