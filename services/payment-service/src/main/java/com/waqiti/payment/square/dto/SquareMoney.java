package com.waqiti.payment.square.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Square Money DTO
 *
 * Represents monetary amount in Square API format.
 * Amount is in smallest currency unit (cents for USD).
 *
 * @author Waqiti Engineering Team
 * @version 1.0.0
 * @since 2025-10-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SquareMoney {
    /**
     * Amount in smallest currency unit (e.g., cents for USD)
     */
    private Long amount;

    /**
     * Currency code (ISO 4217)
     */
    private String currency;
}
