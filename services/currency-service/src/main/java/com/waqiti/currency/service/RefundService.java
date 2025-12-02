package com.waqiti.currency.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Refund Service
 *
 * Processes refunds for failed currency conversions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RefundService {

    private final MeterRegistry meterRegistry;

    /**
     * Process conversion refund
     */
    @Transactional
    public String processConversionRefund(String accountId, String currency, BigDecimal amount,
                                         String reason, String correlationId) {

        String refundId = generateRefundId();

        log.info("Processing conversion refund: refundId={} accountId={} amount={} {} reason={} correlationId={}",
                refundId, accountId, amount, currency, reason, correlationId);

        try {
            // Credit refund amount to account
            creditRefund(accountId, currency, amount, refundId, correlationId);

            // Create refund record
            createRefundRecord(refundId, accountId, currency, amount, reason, correlationId);

            // Notify customer
            notifyCustomerOfRefund(accountId, refundId, currency, amount, correlationId);

            Counter.builder("currency.refund.processed")
                    .tag("currency", currency)
                    .tag("reason", reason)
                    .register(meterRegistry)
                    .increment();

            log.info("Conversion refund processed successfully: refundId={} correlationId={}", refundId, correlationId);

            return refundId;

        } catch (Exception e) {
            log.error("Failed to process refund: refundId={} correlationId={}", refundId, correlationId, e);

            Counter.builder("currency.refund.error")
                    .tag("currency", currency)
                    .register(meterRegistry)
                    .increment();

            throw new RuntimeException("Refund processing failed", e);
        }
    }

    /**
     * Generate refund ID
     */
    private String generateRefundId() {
        return "REF-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }

    /**
     * Credit refund to account
     */
    private void creditRefund(String accountId, String currency, BigDecimal amount, String refundId,
                             String correlationId) {
        log.debug("Crediting refund: accountId={} amount={} {} refundId={} correlationId={}",
                accountId, amount, currency, refundId, correlationId);

        // In production: Credit account via account service
        Counter.builder("currency.refund.credited")
                .tag("currency", currency)
                .register(meterRegistry)
                .increment();
    }

    /**
     * Create refund record
     */
    private void createRefundRecord(String refundId, String accountId, String currency,
                                   BigDecimal amount, String reason, String correlationId) {
        log.debug("Creating refund record: refundId={} correlationId={}", refundId, correlationId);
        // In production: Persist to database
    }

    /**
     * Notify customer of refund
     */
    private void notifyCustomerOfRefund(String accountId, String refundId, String currency,
                                       BigDecimal amount, String correlationId) {
        log.debug("Notifying customer of refund: accountId={} refundId={} correlationId={}",
                accountId, refundId, correlationId);
        // In production: Send notification via email/SMS
    }
}
