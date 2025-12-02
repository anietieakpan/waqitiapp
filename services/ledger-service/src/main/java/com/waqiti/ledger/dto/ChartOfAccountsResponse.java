package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChartOfAccountsResponse {
    private List<AccountNode> accounts;
    private int totalAccounts;
    private LocalDateTime lastUpdated;
}