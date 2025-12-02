package com.waqiti.account.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceInquiryResponse {
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private BigDecimal reservedBalance;
    private LocalDateTime lastUpdated;
}