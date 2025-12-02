package com.waqiti.grouppayment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupPaymentEvent {
    private String eventType;
    private String groupPaymentId;
    private String createdBy;
    private String title;
    private BigDecimal totalAmount;
    private String currency;
    private String status;
    private Instant timestamp;
}