package com.waqiti.payment.wise.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class WiseRefundResult {
    private String refundId;
    private String transferId;
    private String status; // pending, processing, completed, failed
    private BigDecimal amount;
    private String reason;
    private String estimatedCompletionTime;
    private LocalDateTime created;
}