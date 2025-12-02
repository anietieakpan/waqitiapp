package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO for age analysis of outstanding reconciliation items
 * Provides aging bucket analysis for unreconciled items
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutstandingItemsAgeAnalysis {
    
    private UUID analysisId;
    private LocalDate analysisDate;
    private UUID entityId;
    private String entityName;
    private String currency;
    private int totalOutstandingItems;
    private BigDecimal totalOutstandingAmount;
    
    // Age buckets (in days)
    private AgeBucket current;           // 0-30 days
    private AgeBucket thirtyToSixty;     // 31-60 days  
    private AgeBucket sixtyToNinety;     // 61-90 days
    private AgeBucket ninetyToOneTwenty; // 91-120 days
    private AgeBucket overOneTwenty;     // 120+ days
    
    private Map<String, AgeBucket> agingByAccount;
    private Map<String, AgeBucket> agingByTransactionType;
    private List<OutstandingItemDetail> significantItems;
    
    private BigDecimal oldestItemAmount;
    private LocalDate oldestItemDate;
    private String oldestItemReference;
    
    private int highPriorityItems;
    private BigDecimal highPriorityAmount;
    private int mediumPriorityItems;
    private BigDecimal mediumPriorityAmount;
    private int lowPriorityItems;
    private BigDecimal lowPriorityAmount;
    
    private String trendAnalysis;
    private boolean deterioratingTrend;
    private String recommendedActions;
    private List<String> focusAreas;
    private String analysisNotes;
    private String performedBy;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AgeBucket {
    private String bucketName;
    private int itemCount;
    private BigDecimal totalAmount;
    private BigDecimal percentage;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class OutstandingItemDetail {
    private UUID itemId;
    private String accountCode;
    private String description;
    private BigDecimal amount;
    private LocalDate itemDate;
    private int ageDays;
    private String priority;
    private String assignedTo;
    private String status;
}