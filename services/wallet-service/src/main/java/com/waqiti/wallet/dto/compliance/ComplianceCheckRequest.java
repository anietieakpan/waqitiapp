package com.waqiti.wallet.dto.compliance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCheckRequest {
    private String checkId;
    private String walletId;
    private String userId;
    private String checkType;
    private BigDecimal amount;
    private String currency;
    private Map<String, Object> transactionDetails;
    private LocalDateTime timestamp;
}