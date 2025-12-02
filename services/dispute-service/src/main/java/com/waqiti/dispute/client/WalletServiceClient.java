package com.waqiti.dispute.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Feign Client for Wallet Service
 *
 * Handles provisional credits, refunds, and wallet adjustments
 * for dispute resolution processes
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@FeignClient(
    name = "wallet-service",
    url = "${services.wallet-service.url:http://wallet-service:8080}",
    fallback = WalletServiceClientFallback.class,
    configuration = FeignClientConfiguration.class
)
public interface WalletServiceClient {

    /**
     * Issue provisional credit for disputed transaction
     */
    @PostMapping("/api/v1/wallets/{userId}/provisional-credit")
    CreditResponse issueProvisionalCredit(
            @PathVariable("userId") UUID userId,
            @RequestBody ProvisionalCreditRequest request,
            @RequestHeader("X-Service-Auth") String serviceToken);

    /**
     * Reverse provisional credit if dispute resolved against customer
     */
    @PostMapping("/api/v1/wallets/{userId}/provisional-debit")
    DebitResponse reverseProvisionalCredit(
            @PathVariable("userId") UUID userId,
            @RequestBody ProvisionalDebitRequest request,
            @RequestHeader("X-Service-Auth") String serviceToken);

    /**
     * Issue final refund for successful dispute
     */
    @PostMapping("/api/v1/wallets/{userId}/refund")
    CreditResponse issueFinalRefund(
            @PathVariable("userId") UUID userId,
            @RequestBody RefundRequest request,
            @RequestHeader("X-Service-Auth") String serviceToken);

    /**
     * Provisional Credit Request
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class ProvisionalCreditRequest {
        private UUID disputeId;
        private UUID transactionId;
        private BigDecimal amount;
        private String currency;
        private String reason;
        private String description;
        private LocalDateTime expiryDate;
        private String idempotencyKey;
    }

    /**
     * Provisional Debit Request
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class ProvisionalDebitRequest {
        private UUID disputeId;
        private UUID transactionId;
        private BigDecimal amount;
        private String currency;
        private String reason;
        private String idempotencyKey;
    }

    /**
     * Refund Request
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class RefundRequest {
        private UUID disputeId;
        private UUID transactionId;
        private BigDecimal amount;
        private String currency;
        private String reason;
        private String description;
        private String refundType;  // FULL, PARTIAL
        private String idempotencyKey;
    }

    /**
     * Credit Response
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class CreditResponse {
        private UUID creditId;
        private UUID walletId;
        private UUID userId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private LocalDateTime processedAt;
        private BigDecimal newBalance;
    }

    /**
     * Debit Response
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    class DebitResponse {
        private UUID debitId;
        private UUID walletId;
        private UUID userId;
        private BigDecimal amount;
        private String currency;
        private String status;
        private LocalDateTime processedAt;
        private BigDecimal newBalance;
    }
}
