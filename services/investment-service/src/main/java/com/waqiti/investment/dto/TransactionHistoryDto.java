package com.waqiti.investment.dto;

import com.waqiti.investment.domain.enums.OrderSide;
import com.waqiti.investment.domain.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO for transaction history
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHistoryDto {
    private Long id;
    private String transactionId;
    private Long accountId;
    private String symbol;
    private OrderType type;
    private OrderSide side;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal commission;
    private BigDecimal netAmount;
    private LocalDateTime transactionDate;
    private String description;
}