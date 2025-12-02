package com.waqiti.dispute.service;

import com.waqiti.dispute.client.WalletServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Wallet Service Integration
 *
 * Handles provisional credits, refunds, and wallet adjustments
 * for dispute resolution with comprehensive error handling and audit trails
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WalletService {

    private final WalletServiceClient walletServiceClient;

    @Value("${service.auth.token:default-service-token}")
    private String serviceAuthToken;

    /**
     * Issue provisional credit during dispute investigation
     * Regulation E requirement: Provisional credit within 10 business days
     */
    @CircuitBreaker(name = "walletService", fallbackMethod = "creditProvisionalFallback")
    @Retry(name = "walletService")
    public WalletServiceClient.CreditResponse creditProvisional(
            UUID userId,
            BigDecimal amount,
            UUID disputeId) {

        log.info("Issuing provisional credit - User: {}, Amount: {}, Dispute: {}",
                userId, amount, disputeId);

        WalletServiceClient.ProvisionalCreditRequest request =
                WalletServiceClient.ProvisionalCreditRequest.builder()
                        .disputeId(disputeId)
                        .transactionId(disputeId)  // Using dispute ID as transaction reference
                        .amount(amount)
                        .currency("USD")
                        .reason("Provisional credit for dispute investigation")
                        .description("Regulation E provisional credit - Dispute ID: " + disputeId)
                        .expiryDate(LocalDateTime.now().plusDays(45))  // Standard 45-day investigation period
                        .idempotencyKey(String.format("prov-credit-%s-%s", disputeId, userId))
                        .build();

        try {
            WalletServiceClient.CreditResponse response =
                    walletServiceClient.issueProvisionalCredit(userId, request, serviceAuthToken);

            if ("FAILED_SERVICE_UNAVAILABLE".equals(response.getStatus())) {
                log.error("CRITICAL: Provisional credit failed due to service unavailability - Manual intervention required");
                throw new ProvisionalCreditException("Wallet service unavailable - credit not issued");
            }

            log.info("Provisional credit issued successfully - Credit ID: {}", response.getCreditId());
            return response;

        } catch (Exception e) {
            log.error("Failed to issue provisional credit for dispute: {}", disputeId, e);
            throw new ProvisionalCreditException("Failed to issue provisional credit", e);
        }
    }

    /**
     * Reverse provisional credit if dispute resolved against customer
     */
    @CircuitBreaker(name = "walletService", fallbackMethod = "debitProvisionalFallback")
    @Retry(name = "walletService")
    public WalletServiceClient.DebitResponse debitProvisional(
            UUID userId,
            BigDecimal amount,
            UUID disputeId) {

        log.info("Reversing provisional credit - User: {}, Amount: {}, Dispute: {}",
                userId, amount, disputeId);

        WalletServiceClient.ProvisionalDebitRequest request =
                WalletServiceClient.ProvisionalDebitRequest.builder()
                        .disputeId(disputeId)
                        .transactionId(disputeId)
                        .amount(amount)
                        .currency("USD")
                        .reason("Dispute resolved in merchant favor - reversing provisional credit")
                        .idempotencyKey(String.format("prov-debit-%s-%s", disputeId, userId))
                        .build();

        try {
            WalletServiceClient.DebitResponse response =
                    walletServiceClient.reverseProvisionalCredit(userId, request, serviceAuthToken);

            if ("FAILED_SERVICE_UNAVAILABLE".equals(response.getStatus())) {
                log.error("CRITICAL: Provisional debit failed due to service unavailability - Manual intervention required");
                throw new ProvisionalDebitException("Wallet service unavailable - debit not processed");
            }

            log.info("Provisional credit reversed successfully - Debit ID: {}", response.getDebitId());
            return response;

        } catch (Exception e) {
            log.error("Failed to reverse provisional credit for dispute: {}", disputeId, e);
            throw new ProvisionalDebitException("Failed to reverse provisional credit", e);
        }
    }

    /**
     * Issue final refund after dispute resolved in customer favor
     */
    @CircuitBreaker(name = "walletService", fallbackMethod = "creditFinalFallback")
    @Retry(name = "walletService")
    public WalletServiceClient.CreditResponse creditFinal(
            UUID userId,
            BigDecimal amount,
            UUID disputeId) {

        log.info("Issuing final refund - User: {}, Amount: {}, Dispute: {}",
                userId, amount, disputeId);

        WalletServiceClient.RefundRequest request =
                WalletServiceClient.RefundRequest.builder()
                        .disputeId(disputeId)
                        .transactionId(disputeId)
                        .amount(amount)
                        .currency("USD")
                        .reason("Dispute resolved in customer favor - final refund")
                        .description("Final refund for successful dispute - Dispute ID: " + disputeId)
                        .refundType("FULL")
                        .idempotencyKey(String.format("final-refund-%s-%s", disputeId, userId))
                        .build();

        try {
            WalletServiceClient.CreditResponse response =
                    walletServiceClient.issueFinalRefund(userId, request, serviceAuthToken);

            if ("FAILED_SERVICE_UNAVAILABLE".equals(response.getStatus())) {
                log.error("CRITICAL: Final refund failed due to service unavailability - Manual intervention required");
                throw new FinalRefundException("Wallet service unavailable - refund not issued");
            }

            log.info("Final refund issued successfully - Credit ID: {}", response.getCreditId());
            return response;

        } catch (Exception e) {
            log.error("Failed to issue final refund for dispute: {}", disputeId, e);
            throw new FinalRefundException("Failed to issue final refund", e);
        }
    }

    // Circuit Breaker Fallback Methods

    /**
     * Fallback for provisional credit when wallet service unavailable
     */
    private WalletServiceClient.CreditResponse creditProvisionalFallback(
            UUID userId, BigDecimal amount, UUID disputeId, Exception e) {

        log.error("CIRCUIT BREAKER FALLBACK: Provisional credit failed - User: {}, Amount: {}, Dispute: {}, Error: {}",
                userId, amount, disputeId, e.getMessage());

        // Return fallback response indicating manual intervention required
        WalletServiceClient.CreditResponse fallbackResponse = new WalletServiceClient.CreditResponse();
        fallbackResponse.setStatus("FAILED_SERVICE_UNAVAILABLE");
        fallbackResponse.setCreditId(null);
        fallbackResponse.setMessage("Wallet service unavailable - Manual provisional credit required");

        log.warn("Provisional credit requires manual intervention for dispute: {}", disputeId);
        return fallbackResponse;
    }

    /**
     * Fallback for provisional debit when wallet service unavailable
     */
    private WalletServiceClient.DebitResponse debitProvisionalFallback(
            UUID userId, BigDecimal amount, UUID disputeId, Exception e) {

        log.error("CIRCUIT BREAKER FALLBACK: Provisional debit failed - User: {}, Amount: {}, Dispute: {}, Error: {}",
                userId, amount, disputeId, e.getMessage());

        // Return fallback response indicating manual intervention required
        WalletServiceClient.DebitResponse fallbackResponse = new WalletServiceClient.DebitResponse();
        fallbackResponse.setStatus("FAILED_SERVICE_UNAVAILABLE");
        fallbackResponse.setDebitId(null);
        fallbackResponse.setMessage("Wallet service unavailable - Manual debit reversal required");

        log.warn("Provisional debit reversal requires manual intervention for dispute: {}", disputeId);
        return fallbackResponse;
    }

    /**
     * Fallback for final refund when wallet service unavailable
     */
    private WalletServiceClient.CreditResponse creditFinalFallback(
            UUID userId, BigDecimal amount, UUID disputeId, Exception e) {

        log.error("CIRCUIT BREAKER FALLBACK: Final refund failed - User: {}, Amount: {}, Dispute: {}, Error: {}",
                userId, amount, disputeId, e.getMessage());

        // Return fallback response indicating manual intervention required
        WalletServiceClient.CreditResponse fallbackResponse = new WalletServiceClient.CreditResponse();
        fallbackResponse.setStatus("FAILED_SERVICE_UNAVAILABLE");
        fallbackResponse.setCreditId(null);
        fallbackResponse.setMessage("Wallet service unavailable - Manual refund required");

        log.error("CRITICAL: Final refund requires manual intervention for dispute: {}", disputeId);
        return fallbackResponse;
    }

    // Custom exceptions
    public static class ProvisionalCreditException extends RuntimeException {
        public ProvisionalCreditException(String message) {
            super(message);
        }
        public ProvisionalCreditException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ProvisionalDebitException extends RuntimeException {
        public ProvisionalDebitException(String message) {
            super(message);
        }
        public ProvisionalDebitException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class FinalRefundException extends RuntimeException {
        public FinalRefundException(String message) {
            super(message);
        }
        public FinalRefundException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
