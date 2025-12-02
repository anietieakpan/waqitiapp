package com.waqiti.arpayment.integration.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet data DTO from payment service
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletData {

    private UUID userId;
    private BigDecimal balance;
    private String currency;
    private List<Transaction> recentTransactions;
    private Map<String, PaymentMethodInfo> paymentMethods;
    private boolean fallbackMode;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transaction {
        private String id;
        private BigDecimal amount;
        private String type;
        private String description;
        private String timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentMethodInfo {
        private String id;
        private String type;
        private String last4;
        private String expiryDate;
        private boolean isDefault;
    }
}
