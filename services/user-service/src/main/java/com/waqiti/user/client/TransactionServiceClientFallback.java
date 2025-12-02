package com.waqiti.user.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class TransactionServiceClientFallback implements TransactionServiceClient {

    @Override
    public Boolean healthCheck() {
        log.warn("FALLBACK: transaction-service health check failed");
        return false;
    }

    @Override
    public void anonymizeUserTransactions(UUID userId, String anonymizedId) {
        log.error("FALLBACK: Cannot anonymize transactions for user {} - transaction-service unavailable",
                userId);
        throw new RuntimeException("Transaction service unavailable - cannot complete anonymization");
    }
}
