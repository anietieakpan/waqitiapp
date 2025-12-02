package com.waqiti.gdpr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for user transaction history in GDPR export
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTransactionsDataDTO {

    private String userId;
    private List<TransactionDTO> transactions;
    private List<PaymentDTO> payments;
    private List<TransferDTO> transfers;
    private TransactionSummaryDTO summary;

    // Data retrieval metadata
    private boolean dataRetrievalFailed;
    private String failureReason;
    private boolean requiresManualReview;
    private LocalDateTime retrievedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionDTO {
        private String transactionId;
        private String type;
        private BigDecimal amount;
        private String currency;
        private String status;
        private LocalDateTime createdAt;
        private LocalDateTime completedAt;
        private String description;
        private String category;
        private String merchantName;
        private String paymentMethod;
        private FeeDTO fees;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentDTO {
        private String paymentId;
        private String paymentType;
        private BigDecimal amount;
        private String currency;
        private String recipient;
        private String status;
        private LocalDateTime paidAt;
        private String reference;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransferDTO {
        private String transferId;
        private String transferType;
        private BigDecimal amount;
        private String currency;
        private String fromWallet;
        private String toWallet;
        private LocalDateTime transferredAt;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeeDTO {
        private BigDecimal transactionFee;
        private BigDecimal platformFee;
        private BigDecimal totalFees;
        private String currency;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionSummaryDTO {
        private Integer totalTransactions;
        private BigDecimal totalVolume;
        private Integer successfulTransactions;
        private Integer failedTransactions;
        private LocalDateTime firstTransactionDate;
        private LocalDateTime lastTransactionDate;
    }
}
