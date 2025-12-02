package com.waqiti.payment.dispute.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Dispute Request
 */
@Data
@Builder
public class DisputeRequest {
    private String transactionId;
    private String userId;
    private DisputeReason reason;
    private String description;
    private BigDecimal disputedAmount;
    private String currency;
    private String evidence;
    private Map<String, String> attachments;
}
