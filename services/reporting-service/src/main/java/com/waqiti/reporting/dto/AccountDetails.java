package com.waqiti.reporting.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDetails {
    private UUID accountId;
    private String accountNumber;
    private String accountType;
    private String accountStatus;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String userPhone;
    private Address address;
    private BigDecimal currentBalance;
    private BigDecimal availableBalance;
    private String currency;
    private BigDecimal interestRate;
    private LocalDateTime openedAt;
    private LocalDateTime lastActivityAt;
    
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
}