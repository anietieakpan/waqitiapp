package com.waqiti.corebanking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * GDPR Right to Data Portability Export
 * Complete machine-readable export of all user data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GdprDataExportDto {

    private String accountId;

    private String accountNumber;

    private String userId;

    private String accountType;

    private String currency;

    private BigDecimal currentBalance;

    private BigDecimal availableBalance;

    private String accountStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private Instant exportedAt;

    private List<TransactionExportDto> transactions;

    private List<BalanceSnapshotExportDto> balanceHistory;

    private AccountMetadataExportDto metadata;

    /**
     * Transaction export for GDPR compliance
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionExportDto {
        private String transactionId;
        private String transactionNumber;
        private String type;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String description;
        private LocalDateTime transactionDate;
        private LocalDateTime completedAt;
    }

    /**
     * Balance snapshot export for GDPR compliance
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceSnapshotExportDto {
        private LocalDateTime snapshotDate;
        private BigDecimal balance;
        private String currency;
    }

    /**
     * Account metadata export
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountMetadataExportDto {
        private String complianceLevel;
        private Integer riskScore;
        private BigDecimal dailyLimit;
        private BigDecimal monthlyLimit;
        private Map<String, Object> additionalData;
    }
}
