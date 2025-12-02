package com.waqiti.wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Result DTO for transaction cancellation operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionCancellationResult {

    private boolean success;
    private String walletId;
    private int totalPendingFound;
    private int successfullyCancelled;
    private int failed;
    private List<String> cancelledTransactionIds;
    private List<String> failedTransactionIds;
    private BigDecimal totalReleasedAmount;
    private String message;
    private LocalDateTime timestamp;
}
