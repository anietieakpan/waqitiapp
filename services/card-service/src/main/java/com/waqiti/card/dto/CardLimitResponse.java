package com.waqiti.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CardLimitResponse DTO - Card limit details response
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardLimitResponse {
    private UUID id;
    private String limitId;
    private String limitName;
    private UUID cardId;
    private String limitType;
    private Boolean isActive;
    private BigDecimal perTransactionLimit;
    private BigDecimal dailyTransactionLimit;
    private BigDecimal weeklyTransactionLimit;
    private BigDecimal monthlyTransactionLimit;
    private String currencyCode;
    private BigDecimal currentDailyAmount;
    private BigDecimal currentWeeklyAmount;
    private BigDecimal currentMonthlyAmount;
    private BigDecimal remainingDailyLimit;
    private BigDecimal remainingWeeklyLimit;
    private BigDecimal remainingMonthlyLimit;
    private Boolean isTemporary;
    private BigDecimal temporaryLimitAmount;
    private LocalDateTime temporaryLimitEnd;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveUntil;
}
