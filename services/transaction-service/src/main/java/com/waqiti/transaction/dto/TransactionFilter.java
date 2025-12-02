package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFilter {
    
    private String walletId;
    private String type;
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String userId;
    private String merchantId;
    private String category;
    private String currency;
}