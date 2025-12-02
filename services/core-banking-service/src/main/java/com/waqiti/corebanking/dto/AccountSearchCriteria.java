package com.waqiti.corebanking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountSearchCriteria {
    private String status;
    private String accountType;
    private String currency;
    private String userId;
}