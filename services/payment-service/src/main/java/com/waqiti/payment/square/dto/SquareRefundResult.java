package com.waqiti.payment.square.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Square Refund Result DTO
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-10-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SquareRefundResult {
    private String id;
    private String status;
    private String locationId;
    private String paymentId;
    private String orderId;
    private SquareMoney amountMoney;
    private String reason;
    private String createdAt;
    private String updatedAt;
}
