package com.waqiti.payment.square.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Square Refund Request DTO
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-10-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SquareRefundRequest {
    private String idempotencyKey;
    private String paymentId;
    private SquareMoney amountMoney;
    private String reason;
}
