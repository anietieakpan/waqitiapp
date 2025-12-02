package com.waqiti.card.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * CardStatementResponse DTO - Statement details response
 *
 * @author Waqiti Engineering Team
 * @version 2.0 (Consolidated)
 * @since 2025-11-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardStatementResponse {
    private UUID id;
    private String statementId;
    private String statementNumber;
    private UUID cardId;
    private UUID userId;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private LocalDate statementDate;
    private String statementStatus;
    private Boolean isCurrentStatement;
    private BigDecimal previousBalance;
    private BigDecimal newCharges;
    private BigDecimal paymentsReceived;
    private BigDecimal feesCharged;
    private BigDecimal interestCharged;
    private BigDecimal closingBalance;
    private String currencyCode;
    private LocalDate paymentDueDate;
    private BigDecimal minimumPaymentDue;
    private BigDecimal totalAmountDue;
    private Boolean isPaid;
    private Boolean isOverdue;
    private Integer daysOverdue;
    private Integer transactionCount;
    private BigDecimal creditLimit;
    private BigDecimal availableCredit;
    private String statementFileUrl;
}
