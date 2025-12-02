package com.waqiti.common.data;

import com.waqiti.common.dto.RecentTransactionDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Wallet Summary DTO for optimized wallet overview queries
 */
@Data
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class WalletSummaryDTO {
    private String walletId;
    private String userId;
    private String walletType;
    private BigDecimal balance;
    private BigDecimal availableBalance;
    private BigDecimal pendingBalance;
    private String currency;
    private String status;
    private LocalDateTime lastTransactionDate;
    private int transactionCount30Days;
    private BigDecimal totalInflow30Days;
    private BigDecimal totalOutflow30Days;
    private Map<String, Object> limits;
    private boolean isDefault;
    private List<RecentTransactionDTO> recentTransactions;
    
    public String getId() {
        return walletId;
    }
    
    public void setRecentTransactions(List<RecentTransactionDTO> recentTransactions) {
        this.recentTransactions = recentTransactions;
    }
}