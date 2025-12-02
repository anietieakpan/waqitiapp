package com.waqiti.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Individual Fee Component
 *
 * @author Waqiti Platform Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeeComponent {

    private String feeId;
    private String feeType;
    private BigDecimal amount;
    private String incomeAccountId;
    private String description;
}
