package com.waqiti.notification.client.dto;

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
public class LowBalanceUserResponse {
    private UUID userId;
    private UUID walletId;
    private String userName;
    private String userEmail;
    private String userPhone;
    private BigDecimal currentBalance;
    private BigDecimal lowBalanceThreshold;
    private String currency;
    private String formattedBalance;
    private String formattedThreshold;
    private BigDecimal balanceDeficit;
    private LocalDateTime lastTopUpDate;
    private LocalDateTime lastNotificationDate;
    private boolean notificationEnabled;
    private String preferredNotificationChannel;
}