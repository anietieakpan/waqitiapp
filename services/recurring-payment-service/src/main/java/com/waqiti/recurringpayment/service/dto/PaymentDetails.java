package com.waqiti.recurringpayment.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map; /**
 * Additional DTOs for service integrations
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDetails {
    private String id;
    private String status;
    private BigDecimal amount;
    private String currency;
    private String senderId;
    private String recipientId;
    private String description;
    private Instant createdAt;
    private Instant completedAt;
    private Map<String, String> metadata;
}
