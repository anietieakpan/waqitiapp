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
public class SuspiciousActivityReport {
    private String walletId;
    private String userId;
    private String activityType;
    private BigDecimal amount;
    private String currency;
    private Map<String, Object> details;
    private LocalDateTime timestamp;
}