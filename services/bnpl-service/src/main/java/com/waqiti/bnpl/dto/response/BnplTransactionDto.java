package com.waqiti.bnpl.dto.response;

import com.waqiti.bnpl.domain.enums.TransactionStatus;
import com.waqiti.bnpl.domain.enums.TransactionType;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Response DTO for BNPL transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BnplTransactionDto {

    private Long id;
    private String transactionId;
    private TransactionType type;
    private BigDecimal amount;
    private TransactionStatus status;
    private String paymentMethod;
    private String description;
    private LocalDateTime createdAt;
}