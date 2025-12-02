package com.waqiti.corebanking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSearchCriteria {
    private String accountId;
    private String type;
    private String status;
    private String startDate;
    private String endDate;
}