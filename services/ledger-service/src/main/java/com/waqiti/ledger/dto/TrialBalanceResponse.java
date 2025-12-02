package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrialBalanceResponse {
    private LocalDateTime asOfDate;
    private List<TrialBalanceEntry> entries;
    private BigDecimal totalDebits;
    private BigDecimal totalCredits;
    private boolean balanced;
    private LocalDateTime generatedAt;
}