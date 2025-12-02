package com.waqiti.payment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID; /**
 * Request to pay a split payment share
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaySplitShareRequest {
    @NotNull(message = "Source wallet ID is required")
    private UUID sourceWalletId;
}
