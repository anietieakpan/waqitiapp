/**
 * Transaction Response DTO
 * Response containing transaction information
 */
package com.waqiti.crypto.dto.response;

import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.entity.TransactionStatus;
import com.waqiti.crypto.entity.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {
    
    private UUID transactionId;
    private UUID userId;
    private UUID walletId;
    private CryptoCurrency currency;
    private TransactionType type;
    private TransactionStatus status;
    private String fromAddress;
    private String toAddress;
    private BigDecimal amount;
    private BigDecimal fee;
    private String txHash;
    private Integer confirmations;
    private Integer requiredConfirmations;
    private String memo;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private boolean expedited;
    private String failureReason;
    private SecurityInfo securityInfo;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SecurityInfo {
        private boolean fraudChecked;
        private Integer riskScore;
        private boolean complianceChecked;
        private boolean requiresManualReview;
        private String complianceNote;
    }
}