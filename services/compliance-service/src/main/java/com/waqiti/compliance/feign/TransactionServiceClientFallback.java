package com.waqiti.compliance.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Transaction Service Client Fallback
 *
 * @author Waqiti Compliance Team
 * @version 1.0
 */
@Slf4j
@Component
public class TransactionServiceClientFallback implements TransactionServiceClient {

    @Override
    public List<Map<String, Object>> getTransactionsByUser(String userId, String authToken) {
        log.error("[FALLBACK] Transaction service unavailable - getTransactionsByUser: userId={}", userId);
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> getTransaction(String transactionId, String authToken) {
        log.error("[FALLBACK] Transaction service unavailable - getTransaction: transactionId={}", transactionId);
        return Map.of("error", "TRANSACTION_SERVICE_UNAVAILABLE");
    }

    @Override
    public List<Map<String, Object>> getSuspiciousTransactions(String authToken) {
        log.error("[FALLBACK] Transaction service unavailable - getSuspiciousTransactions");
        return Collections.emptyList();
    }
}
