package com.waqiti.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatementDocument {
    
    private UUID statementId;
    private UUID accountId;
    private String accountNumber;
    private String accountType;
    private AccountHolder accountHolder;
    private StatementPeriod period;
    private BalanceSummary balanceSummary;
    private List<Transaction> transactions;
    private FeesSummary feesSummary;
    private InterestSummary interestSummary;
    private String format;
    private byte[] content;
    private LocalDateTime generatedAt;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountHolder {
        private String name;
        private String email;
        private String phone;
        private Address address;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Address {
        private String street;
        private String city;
        private String state;
        private String postalCode;
        private String country;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatementPeriod {
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer totalDays;
        private String statementNumber;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BalanceSummary {
        private BigDecimal openingBalance;
        private BigDecimal closingBalance;
        private BigDecimal averageBalance;
        private BigDecimal lowestBalance;
        private BigDecimal highestBalance;
        private LocalDate lowestBalanceDate;
        private LocalDate highestBalanceDate;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Transaction {
        private UUID transactionId;
        private LocalDateTime date;
        private String description;
        private String reference;
        private String type;
        private BigDecimal debit;
        private BigDecimal credit;
        private BigDecimal balance;
        private String status;
        private String category;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeesSummary {
        private BigDecimal totalFees;
        private List<FeeDetail> feeDetails;
        private BigDecimal feeWaivers;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FeeDetail {
        private String feeType;
        private String description;
        private BigDecimal amount;
        private LocalDate chargedOn;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InterestSummary {
        private BigDecimal interestEarned;
        private BigDecimal interestRate;
        private BigDecimal apy;
        private String compoundingFrequency;
    }
}