package com.waqiti.ledger.service;

import com.waqiti.payment.dto.PaymentChargebackEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Chargeback Ledger Service - Handles double-entry bookkeeping for chargebacks
 * 
 * Provides enterprise-grade accounting operations for chargeback processing:
 * - Double-entry bookkeeping for chargeback transactions
 * - Chargeback fee accounting and allocation
 * - Revenue loss tracking and reporting
 * - Liability account management
 * - Merchant account balance updates
 * 
 * @author Waqiti Engineering Team
 * @version 2.0.0
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChargebackLedgerService {

    private final LedgerServiceImpl ledgerService;

    /**
     * Records chargeback transaction with double-entry bookkeeping
     * 
     * Creates the following journal entries:
     * 1. Debit: Chargeback Losses (Expense Account)
     * 2. Credit: Accounts Receivable - Merchant (Asset Account)
     * 3. Debit: Chargeback Fees (Expense Account)  
     * 4. Credit: Cash/Settlement Account (Asset Account)
     * 
     * @param chargebackId Unique chargeback identifier
     * @param merchantId Merchant identifier
     * @param transactionId Original transaction identifier
     * @param chargebackAmount Chargeback amount
     * @param chargebackFee Associated fees
     * @param currency Currency code
     * @param reasonCode Chargeback reason code
     * @param cardNetwork Card network
     * @param timestamp Chargeback timestamp
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordChargebackTransaction(
            String chargebackId,
            String merchantId, 
            String transactionId,
            BigDecimal chargebackAmount,
            BigDecimal chargebackFee,
            String currency,
            String reasonCode,
            PaymentChargebackEvent.CardNetwork cardNetwork,
            LocalDateTime timestamp) {

        try {
            log.info("Recording chargeback ledger entries - Chargeback: {}, Merchant: {}, Amount: {} {}", 
                chargebackId, merchantId, chargebackAmount, currency);

            // Create journal entry for chargeback loss
            ledgerService.createDoubleEntry(
                chargebackId,
                "CHARGEBACK_LOSS",
                "Chargeback Loss - " + reasonCode,
                merchantId,
                chargebackAmount,
                currency,
                "CHARGEBACK_LOSSES", // Debit expense account
                "MERCHANT_RECEIVABLES", // Credit merchant receivables
                timestamp,
                buildChargebackMetadata(chargebackId, transactionId, reasonCode, cardNetwork)
            );

            // Create journal entry for chargeback fee
            if (chargebackFee != null && chargebackFee.compareTo(BigDecimal.ZERO) > 0) {
                ledgerService.createDoubleEntry(
                    chargebackId + "_FEE",
                    "CHARGEBACK_FEE", 
                    "Chargeback Fee - " + cardNetwork.getDisplayName(),
                    merchantId,
                    chargebackFee,
                    currency,
                    "CHARGEBACK_FEES", // Debit fee expense account
                    "CASH_SETTLEMENT", // Credit settlement account
                    timestamp,
                    buildChargebackMetadata(chargebackId, transactionId, reasonCode, cardNetwork)
                );
            }

            log.info("Successfully recorded chargeback ledger entries for chargeback: {}", chargebackId);

        } catch (Exception e) {
            log.error("Failed to record chargeback ledger entries for chargeback: {}", chargebackId, e);
            throw new ChargebackLedgerException("Failed to record chargeback transaction", e);
        }
    }

    /**
     * Builds metadata for chargeback journal entries
     */
    private java.util.Map<String, Object> buildChargebackMetadata(
            String chargebackId,
            String transactionId, 
            String reasonCode,
            PaymentChargebackEvent.CardNetwork cardNetwork) {
        
        return java.util.Map.of(
            "chargeback_id", chargebackId,
            "original_transaction_id", transactionId,
            "reason_code", reasonCode,
            "card_network", cardNetwork.toString(),
            "entry_type", "CHARGEBACK",
            "created_at", LocalDateTime.now()
        );
    }

    /**
     * Custom exception for chargeback ledger operations
     */
    public static class ChargebackLedgerException extends RuntimeException {
        public ChargebackLedgerException(String message) {
            super(message);
        }
        
        public ChargebackLedgerException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}