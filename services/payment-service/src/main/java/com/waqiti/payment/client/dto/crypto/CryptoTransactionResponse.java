package com.waqiti.payment.client.dto.crypto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response DTO for cryptocurrency transaction
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoTransactionResponse {
    private UUID transactionId;
    private UUID walletId;
    private UUID userId;
    private String transactionType; // SEND, RECEIVE, BUY, SELL
    private String currency;
    private BigDecimal amount;
    private BigDecimal networkFee;
    private String status; // PENDING, CONFIRMED, FAILED, CANCELLED
    private String blockchainTxHash;
    private String fromAddress;
    private String toAddress;
    private Integer confirmations;
    private Integer requiredConfirmations;
    private String memo;
    private BigDecimal usdValue;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
    private boolean available;
    private String errorMessage;

    public static CryptoTransactionResponse unavailable(String message) {
        return CryptoTransactionResponse.builder()
                .available(false)
                .errorMessage(message)
                .build();
    }
}
