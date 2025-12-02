package com.waqiti.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionRecord {
    private UUID transactionId;
    private UUID accountId;
    private String transactionType;
    private String transactionStatus;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String reference;
    private String category;
    private LocalDateTime transactionDate;
    private LocalDateTime postedDate;
    private UUID merchantId;
    private String merchantName;
    private String paymentMethod;
    private BigDecimal fee;
    private BigDecimal runningBalance;
    private Map<String, Object> metadata;
    private Location location;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String country;
        private String city;
        private Double latitude;
        private Double longitude;
    }
}