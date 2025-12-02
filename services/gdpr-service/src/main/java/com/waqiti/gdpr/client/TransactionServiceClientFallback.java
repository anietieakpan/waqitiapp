package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserTransactionsDataDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Fallback for Transaction Service Client
 */
@Slf4j
@Component
public class TransactionServiceClientFallback implements TransactionServiceClient {

    @Override
    public UserTransactionsDataDTO getUserTransactions(String userId, LocalDateTime fromDate,
                                                       LocalDateTime toDate, String correlationId) {
        log.error("Transaction Service unavailable for GDPR data export - userId: {} correlationId: {}",
            userId, correlationId);

        return UserTransactionsDataDTO.builder()
            .userId(userId)
            .dataRetrievalFailed(true)
            .failureReason("Transaction Service unavailable")
            .requiresManualReview(true)
            .build();
    }
}
