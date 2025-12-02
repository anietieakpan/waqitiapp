package com.waqiti.card.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * CardUpdateRequest DTO - Request to update card details
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardUpdateRequest {

    @Size(max = 255)
    private String embossedName;

    @DecimalMin(value = "0.00")
    private BigDecimal creditLimit;

    private BigDecimal dailySpendLimit;

    private BigDecimal monthlySpendLimit;

    private Boolean isInternationalEnabled;

    private Boolean isOnlineEnabled;

    @Size(max = 500)
    private String deliveryAddress;
}
