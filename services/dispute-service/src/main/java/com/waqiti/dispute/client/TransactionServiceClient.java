package com.waqiti.dispute.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Feign Client for Transaction Service
 *
 * Provides secure communication with the Transaction Service for dispute resolution
 * Uses service-to-service authentication and circuit breaker patterns
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@FeignClient(
    name = "transaction-service",
    url = "${services.transaction-service.url:http://transaction-service:8080}",
    fallback = TransactionServiceClientFallback.class,
    configuration = FeignClientConfiguration.class
)
public interface TransactionServiceClient {

    /**
     * Get transaction details for dispute processing
     */
    @GetMapping("/api/v1/transactions/{transactionId}")
    TransactionDTO getTransaction(
            @PathVariable("transactionId") UUID transactionId,
            @RequestHeader("X-Service-Auth") String serviceToken);

    /**
     * Get transaction with full detail including merchant info
     */
    @GetMapping("/api/v1/transactions/{transactionId}/detailed")
    DetailedTransactionDTO getDetailedTransaction(
            @PathVariable("transactionId") UUID transactionId,
            @RequestHeader("X-Service-Auth") String serviceToken);

    /**
     * Transaction Data Transfer Object
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class TransactionDTO {
        private UUID transactionId;
        private UUID userId;
        private UUID merchantId;
        private String merchantName;
        private BigDecimal amount;
        private String currency;
        private String status;
        private String transactionType;
        private String paymentMethod;
        private LocalDateTime transactionDate;
        private String authorizationCode;
        private String cardLast4;
        private String description;
    }

    /**
     * Detailed Transaction DTO with extended information
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class DetailedTransactionDTO {
        private UUID transactionId;
        private UUID userId;
        private UUID walletId;
        private UUID merchantId;
        private String merchantName;
        private String merchantCategory;
        private BigDecimal amount;
        private BigDecimal fee;
        private String currency;
        private String status;
        private String transactionType;
        private String paymentMethod;
        private LocalDateTime transactionDate;
        private LocalDateTime settlementDate;
        private String authorizationCode;
        private String cardLast4;
        private String cardBrand;
        private String description;
        private String ipAddress;
        private String deviceId;
        private String location;
        private boolean recurring;
        private UUID recurringPlanId;
    }
}
