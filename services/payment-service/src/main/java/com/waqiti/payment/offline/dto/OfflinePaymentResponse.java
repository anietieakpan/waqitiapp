package com.waqiti.payment.offline.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OfflinePaymentResponse {
    private String id;
    private String senderId;
    private String recipientId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private String qrCode;
    private LocalDateTime createdAt;
    private LocalDateTime syncedAt;
    private String onlinePaymentId;
    private String syncError;
}