package com.waqiti.dispute.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fallback implementation for Transaction Service Client
 *
 * Provides graceful degradation when transaction service is unavailable
 * Returns cached data or minimal DTOs to prevent cascade failures
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@Slf4j
@Component
public class TransactionServiceClientFallback implements TransactionServiceClient {

    @Override
    public TransactionDTO getTransaction(UUID transactionId, String serviceToken) {
        log.warn("Transaction service unavailable, using fallback for transaction: {}", transactionId);

        // Return minimal DTO with transaction ID only
        // This allows dispute processing to continue with limited information
        return TransactionDTO.builder()
                .transactionId(transactionId)
                .status("UNKNOWN")
                .amount(BigDecimal.ZERO)
                .currency("USD")
                .transactionDate(LocalDateTime.now())
                .description("Transaction service unavailable - limited data")
                .build();
    }

    @Override
    public DetailedTransactionDTO getDetailedTransaction(UUID transactionId, String serviceToken) {
        log.warn("Transaction service unavailable, using fallback for detailed transaction: {}", transactionId);

        return DetailedTransactionDTO.builder()
                .transactionId(transactionId)
                .status("UNKNOWN")
                .amount(BigDecimal.ZERO)
                .currency("USD")
                .transactionDate(LocalDateTime.now())
                .description("Transaction service unavailable - limited data")
                .build();
    }
}
