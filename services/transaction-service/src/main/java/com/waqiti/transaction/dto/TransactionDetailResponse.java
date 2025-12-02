package com.waqiti.transaction.dto;

import com.waqiti.transaction.domain.TransactionEvent;
import com.waqiti.transaction.domain.TransactionStatus;
import com.waqiti.transaction.domain.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDetailResponse {
    
    private UUID id;
    private String reference;
    private String fromWalletId;
    private String toWalletId;
    private String fromUserId;
    private String toUserId;
    private BigDecimal amount;
    private String currency;
    private TransactionType type;
    private TransactionStatus status;
    private String description;
    private Map<String, String> metadata;
    private BigDecimal fee;
    private BigDecimal tax;
    private BigDecimal totalAmount;
    private String processorReference;
    private String externalReference;
    private LocalDateTime processedAt;
    private LocalDateTime completedAt;
    private LocalDateTime failedAt;
    private String failureReason;
    private String failureCode;
    private Integer retryCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Long version;
    private List<TransactionEvent> events;
    private String sagaId;
    private String processingResult;
    private String suspensionReason;
    private LocalDateTime suspendedAt;
    private Boolean emergencySuspension;
}