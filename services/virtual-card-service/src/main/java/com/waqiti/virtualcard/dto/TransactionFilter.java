package com.waqiti.virtualcard.dto;

import com.waqiti.virtualcard.domain.TransactionStatus;
import com.waqiti.virtualcard.domain.TransactionType;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Filter DTO for transaction queries
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFilter {
    
    private LocalDateTime fromDate;
    
    private LocalDateTime toDate;
    
    private List<TransactionStatus> status;
    
    private List<TransactionType> transactionTypes;
    
    private BigDecimal minAmount;
    
    private BigDecimal maxAmount;
    
    private String merchantName;
    
    private String merchantId;
    
    private List<String> merchantCategories;
    
    private List<String> merchantCategoryCodes;
    
    private List<String> countries;
    
    private Boolean isInternational;
    
    private Boolean isOnline;
    
    private Boolean isContactless;
    
    private Boolean isRecurring;
    
    private Boolean threeDSecure;
    
    private String authorizationCode;
    
    private String terminalId;
    
    private Integer minRiskScore;
    
    private Integer maxRiskScore;
    
    private Integer minFraudScore;
    
    private Integer maxFraudScore;
    
    @Min(value = 1, message = "Page size must be at least 1")
    private Integer pageSize;
    
    @Min(value = 0, message = "Page number must be non-negative")
    private Integer pageNumber;
    
    private String sortBy;
    
    private String sortDirection; // ASC or DESC
    
    /**
     * Get default filter for recent transactions
     */
    public static TransactionFilter recentTransactions(int limit) {
        return TransactionFilter.builder()
            .fromDate(LocalDateTime.now().minusDays(30))
            .toDate(LocalDateTime.now())
            .pageSize(limit)
            .pageNumber(0)
            .sortBy("transactionDate")
            .sortDirection("DESC")
            .build();
    }
    
    /**
     * Get filter for today's transactions
     */
    public static TransactionFilter todayTransactions() {
        return TransactionFilter.builder()
            .fromDate(LocalDateTime.now().toLocalDate().atStartOfDay())
            .toDate(LocalDateTime.now())
            .sortBy("transactionDate")
            .sortDirection("DESC")
            .build();
    }
    
    /**
     * Get filter for failed transactions
     */
    public static TransactionFilter failedTransactions(int days) {
        return TransactionFilter.builder()
            .fromDate(LocalDateTime.now().minusDays(days))
            .toDate(LocalDateTime.now())
            .status(List.of(TransactionStatus.DECLINED, TransactionStatus.FAILED, 
                          TransactionStatus.REJECTED, TransactionStatus.CANCELLED))
            .sortBy("transactionDate")
            .sortDirection("DESC")
            .build();
    }
}