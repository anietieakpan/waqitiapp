package com.waqiti.ledger.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Account Hierarchy Response DTO
 * 
 * Response structure for hierarchical account tree representation
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountHierarchyResponse {
    
    private UUID accountId;
    private String accountCode;
    private String accountName;
    private String accountType;
    private Integer level;
    private String path;
    private UUID parentAccountId;
    private String parentAccountCode;
    private List<AccountHierarchyResponse> children;
    private BigDecimal balance;
    private BigDecimal debitBalance;
    private BigDecimal creditBalance;
    private String currency;
    private Boolean isActive;
    private Boolean isSystemAccount;
    private Boolean hasChildren;
    private Integer childrenCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Calculate total balance including all children
     */
    public BigDecimal getTotalBalance() {
        BigDecimal total = balance != null ? balance : BigDecimal.ZERO;
        
        if (children != null && !children.isEmpty()) {
            for (AccountHierarchyResponse child : children) {
                BigDecimal childTotal = child.getTotalBalance();
                if (childTotal != null) {
                    total = total.add(childTotal);
                }
            }
        }
        
        return total;
    }
    
    /**
     * Get the full account path
     */
    public String getFullPath() {
        if (path != null && !path.isEmpty()) {
            return path + "/" + accountCode;
        }
        return accountCode;
    }
    
    /**
     * Check if this is a leaf account (no children)
     */
    public boolean isLeafAccount() {
        return children == null || children.isEmpty();
    }
}