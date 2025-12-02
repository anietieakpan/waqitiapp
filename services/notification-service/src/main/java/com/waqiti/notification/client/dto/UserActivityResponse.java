package com.waqiti.notification.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserActivityResponse {
    private UUID userId;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private Integer totalTransactions;
    private BigDecimal totalSpent;
    private BigDecimal totalReceived;
    private BigDecimal netFlow;
    private String primaryCurrency;
    private Map<String, BigDecimal> spentByCurrency;
    private Map<String, BigDecimal> receivedByCurrency;
    private Map<String, Integer> transactionsByType;
    private List<String> topMerchants;
    private List<String> topCategories;
    private Integer uniqueMerchants;
    private Integer uniquePayees;
    private BigDecimal averageTransactionAmount;
    private BigDecimal largestTransaction;
    private LocalDateTime lastActivityDate;
    private String activityLevel;
    private Map<String, Object> additionalMetrics;
}