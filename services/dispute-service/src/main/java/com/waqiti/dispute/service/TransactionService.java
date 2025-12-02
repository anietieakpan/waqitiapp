package com.waqiti.dispute.service;

import com.waqiti.dispute.client.TransactionServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Transaction Service Integration
 *
 * Provides secure access to transaction data for dispute processing
 * Implements caching and retry logic for resilience
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionServiceClient transactionServiceClient;

    @Value("${service.auth.token:default-service-token}")
    private String serviceAuthToken;

    /**
     * Get transaction details with caching
     */
    @CircuitBreaker(name = "transactionService", fallbackMethod = "getTransactionDetailsFallback")
    @Retry(name = "transactionService")
    @Cacheable(value = "transaction-details", key = "#transactionId", unless = "#result == null")
    public TransactionServiceClient.TransactionDTO getTransactionDetails(UUID transactionId) {
        log.debug("Fetching transaction details for dispute processing: {}", transactionId);

        try {
            return transactionServiceClient.getTransaction(transactionId, serviceAuthToken);
        } catch (Exception e) {
            log.error("Failed to fetch transaction details for: {}", transactionId, e);
            throw new TransactionServiceException("Unable to fetch transaction details", e);
        }
    }

    /**
     * Get detailed transaction information
     */
    @CircuitBreaker(name = "transactionService", fallbackMethod = "getDetailedTransactionInfoFallback")
    @Retry(name = "transactionService")
    @Cacheable(value = "transaction-detailed", key = "#transactionId", unless = "#result == null")
    public TransactionServiceClient.DetailedTransactionDTO getDetailedTransactionInfo(UUID transactionId) {
        log.debug("Fetching detailed transaction info for: {}", transactionId);

        try {
            return transactionServiceClient.getDetailedTransaction(transactionId, serviceAuthToken);
        } catch (Exception e) {
            log.error("Failed to fetch detailed transaction info for: {}", transactionId, e);
            throw new TransactionServiceException("Unable to fetch detailed transaction info", e);
        }
    }

    // Circuit Breaker Fallback Methods

    /**
     * Fallback for transaction details when service unavailable
     */
    private TransactionServiceClient.TransactionDTO getTransactionDetailsFallback(UUID transactionId, Exception e) {
        log.error("CIRCUIT BREAKER FALLBACK: Transaction details unavailable for: {}, Error: {}",
                transactionId, e.getMessage());
        throw new TransactionServiceException("Transaction service unavailable - Cannot fetch transaction details", e);
    }

    /**
     * Fallback for detailed transaction info when service unavailable
     */
    private TransactionServiceClient.DetailedTransactionDTO getDetailedTransactionInfoFallback(
            UUID transactionId, Exception e) {
        log.error("CIRCUIT BREAKER FALLBACK: Detailed transaction info unavailable for: {}, Error: {}",
                transactionId, e.getMessage());
        throw new TransactionServiceException("Transaction service unavailable - Cannot fetch detailed transaction info", e);
    }

    /**
     * Transaction Service Exception
     */
    public static class TransactionServiceException extends RuntimeException {
        public TransactionServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
