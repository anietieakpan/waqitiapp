package com.waqiti.investment.dto.response;

import com.waqiti.investment.domain.enums.TransferStatus;
import com.waqiti.investment.domain.enums.TransferType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferDto {

    private String id;
    private String investmentAccountId;
    private String transferNumber;
    private TransferType type;
    private BigDecimal amount;
    private String fromAccountId;
    private String fromAccountName;
    private String toAccountId;
    private String toAccountName;
    private TransferStatus status;
    private String statusDisplay;
    private String walletTransactionId;
    private String brokerageTransferId;
    private String description;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime processedAt;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    
    // Display fields
    private String amountDisplay;
    private String typeDisplay;
    private Boolean canBeCancelled;
    private String estimatedCompletionTime;
    
    public String getAmountDisplay() {
        if (amount == null) return "$0.00";
        return String.format("$%,.2f", amount);
    }
    
    public String getTypeDisplay() {
        if (type == null) return "";
        return type.getDisplayName();
    }
    
    public String getStatusDisplay() {
        if (status == null) return "";
        return status.getDisplayName();
    }
    
    public boolean isCompleted() {
        return TransferStatus.COMPLETED.equals(status);
    }
    
    public boolean isFailed() {
        return TransferStatus.FAILED.equals(status);
    }
    
    public boolean isPending() {
        return status != null && status.isPending();
    }
    
    public boolean canBeCancelled() {
        return TransferStatus.PENDING.equals(status);
    }
}