package com.waqiti.gdpr.client;

import com.waqiti.gdpr.dto.UserTransactionsDataDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

/**
 * Feign client for Transaction Service integration
 * Used for GDPR data export to retrieve user transaction history
 */
@FeignClient(
    name = "transaction-service",
    url = "${services.transaction-service.url:http://transaction-service:8088}",
    fallback = TransactionServiceClientFallback.class
)
public interface TransactionServiceClient {

    /**
     * Get complete user transaction history for GDPR export
     * Includes all transactions, transfers, payments
     *
     * @param userId User ID
     * @param fromDate Optional start date for filtering
     * @param toDate Optional end date for filtering
     * @param correlationId Tracing correlation ID
     * @return Complete transaction history
     */
    @GetMapping("/api/v1/transactions/users/{userId}/gdpr/export")
    UserTransactionsDataDTO getUserTransactions(
        @PathVariable("userId") String userId,
        @RequestParam(value = "fromDate", required = false) LocalDateTime fromDate,
        @RequestParam(value = "toDate", required = false) LocalDateTime toDate,
        @RequestHeader("X-Correlation-ID") String correlationId
    );
}
