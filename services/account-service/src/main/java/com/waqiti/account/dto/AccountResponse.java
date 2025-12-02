package com.waqiti.account.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {
    private UUID accountId;
    private String accountNumber;
    private UUID userId;
    private String accountName;
    private String accountType;
    private String status;
    private String currency;
    private BigDecimal currentBalance;
    private LocalDateTime openedDate;
}