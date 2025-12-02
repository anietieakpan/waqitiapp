package com.waqiti.corebanking.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * GDPR Right to Erasure Response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GdprErasureResponseDto {

    private Boolean success;

    private String accountId;

    private String message;

    private BigDecimal currentBalance;

    private Instant anonymizedAt;

    private String auditTrailNote;
}
