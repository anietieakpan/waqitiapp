package com.waqiti.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID; /**
 * Request to approve a payment request
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovePaymentRequestRequest {
    @NotNull(message = "Source wallet ID is required")
    private UUID sourceWalletId;
}
