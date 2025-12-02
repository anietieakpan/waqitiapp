package com.waqiti.wallet.kafka.dlq;

import com.waqiti.common.kafka.dlq.*;
import com.waqiti.common.kafka.dlq.entity.DlqRecordEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletDebitedDlqHandler implements DlqMessageHandler {

    @Override
    public DlqEventType getEventType() {
        return DlqEventType.WALLET_DEBITED;
    }

    @Override
    @Transactional
    public DlqProcessingResult reprocess(ConsumerRecord<String, String> record, DlqRecordEntity dlqRecord) {
        try {
            log.info("DLQ: Reprocessing WALLET_DEBITED - messageId={}, retry={}",
                dlqRecord.getMessageId(), dlqRecord.getRetryCount());

            String failureReason = dlqRecord.getLastFailureReason();
            String walletId = extractHeader(record, "walletId");
            String transactionId = extractHeader(record, "transactionId");
            String amount = extractHeader(record, "amount");

            // Strategy 1: Ledger update failure - retry
            if (failureReason != null && failureReason.contains("ledger")) {
                log.warn("DLQ: Ledger update failed for wallet debit: walletId={}, txId={}", walletId, transactionId);
                return DlqProcessingResult.retryLater("Retry ledger update");
            }

            // Strategy 2: Notification failure - non-critical
            if (failureReason != null && failureReason.contains("notification")) {
                log.info("DLQ: Wallet debited but notification failed: walletId={}", walletId);
                return DlqProcessingResult.success("Debit successful, notification failed (non-critical)");
            }

            // Strategy 3: Analytics failure - non-critical
            if (failureReason != null && failureReason.contains("analytics")) {
                log.info("DLQ: Analytics recording failed for debit: walletId={}", walletId);
                return DlqProcessingResult.success("Debit successful, analytics failed (non-critical)");
            }

            // Strategy 4: Insufficient balance (shouldn't happen, critical if does)
            if (failureReason != null && failureReason.contains("insufficient")) {
                log.error("DLQ: CRITICAL - Insufficient balance detected after debit: walletId={}", walletId);
                return DlqProcessingResult.manualReview("Insufficient balance after debit - investigate");
            }

            // Strategy 5: Duplicate debit detection
            if (failureReason != null && (failureReason.contains("duplicate") || failureReason.contains("already processed"))) {
                log.info("DLQ: Duplicate debit event discarded: txId={}", transactionId);
                return DlqProcessingResult.discarded("Duplicate debit event");
            }

            // Strategy 6: Transient errors - retry
            if (failureReason != null && (failureReason.contains("timeout") || failureReason.contains("database"))) {
                log.info("DLQ: Transient error during wallet debit: {}", failureReason);
                return DlqProcessingResult.retryLater("Transient error - retry");
            }

            // Strategy 7: Balance reconciliation failure
            if (failureReason != null && failureReason.contains("reconciliation")) {
                log.error("DLQ: Balance reconciliation failed after debit: walletId={}, amount={}", walletId, amount);
                return DlqProcessingResult.manualReview("Reconciliation required");
            }

            // Default: Manual review
            log.error("DLQ: Unknown wallet debit failure: {}", failureReason);
            return DlqProcessingResult.manualReview("Unknown debit failure: " + failureReason);

        } catch (Exception e) {
            log.error("DLQ: Failed to reprocess WALLET_DEBITED", e);
            return DlqProcessingResult.retryLater("Exception: " + e.getMessage());
        }
    }

    private String extractHeader(ConsumerRecord<String, String> record, String key) {
        if (record.headers() != null && record.headers().lastHeader(key) != null) {
            return new String(record.headers().lastHeader(key).value());
        }
        return "UNKNOWN";
    }
}
