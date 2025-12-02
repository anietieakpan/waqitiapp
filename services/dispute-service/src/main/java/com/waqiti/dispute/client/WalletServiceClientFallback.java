package com.waqiti.dispute.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fallback implementation for Wallet Service Client
 *
 * Provides graceful degradation when wallet service is unavailable
 * Critical operations are logged for manual processing
 *
 * @author Waqiti Platform Team
 * @version 1.0
 */
@Slf4j
@Component
public class WalletServiceClientFallback implements WalletServiceClient {

    @Override
    public CreditResponse issueProvisionalCredit(UUID userId, ProvisionalCreditRequest request, String serviceToken) {
        log.error("CRITICAL: Wallet service unavailable - Provisional credit FAILED for user: {}, amount: {}, dispute: {}",
                userId, request.getAmount(), request.getDisputeId());
        log.error("ACTION REQUIRED: Manual provisional credit processing needed");

        // Return failure response - dispute processing will handle retry
        return CreditResponse.builder()
                .creditId(UUID.randomUUID())
                .userId(userId)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status("FAILED_SERVICE_UNAVAILABLE")
                .processedAt(LocalDateTime.now())
                .newBalance(BigDecimal.ZERO)
                .build();
    }

    @Override
    public DebitResponse reverseProvisionalCredit(UUID userId, ProvisionalDebitRequest request, String serviceToken) {
        log.error("CRITICAL: Wallet service unavailable - Provisional debit FAILED for user: {}, amount: {}, dispute: {}",
                userId, request.getAmount(), request.getDisputeId());
        log.error("ACTION REQUIRED: Manual debit processing needed");

        return DebitResponse.builder()
                .debitId(UUID.randomUUID())
                .userId(userId)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status("FAILED_SERVICE_UNAVAILABLE")
                .processedAt(LocalDateTime.now())
                .newBalance(BigDecimal.ZERO)
                .build();
    }

    @Override
    public CreditResponse issueFinalRefund(UUID userId, RefundRequest request, String serviceToken) {
        log.error("CRITICAL: Wallet service unavailable - Final refund FAILED for user: {}, amount: {}, dispute: {}",
                userId, request.getAmount(), request.getDisputeId());
        log.error("ACTION REQUIRED: Manual refund processing needed");

        return CreditResponse.builder()
                .creditId(UUID.randomUUID())
                .userId(userId)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status("FAILED_SERVICE_UNAVAILABLE")
                .processedAt(LocalDateTime.now())
                .newBalance(BigDecimal.ZERO)
                .build();
    }
}
