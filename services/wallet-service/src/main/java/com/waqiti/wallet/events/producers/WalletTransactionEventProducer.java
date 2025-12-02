package com.waqiti.wallet.events.producers;

import com.waqiti.common.events.WalletCreditedEvent;
import com.waqiti.common.events.WalletDebitedEvent;
import com.waqiti.common.events.WalletTransferEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * PRODUCTION EVENT PUBLISHER - P1 FIX
 *
 * Wallet Transaction Event Producer
 *
 * Publishes wallet transaction events to Kafka for:
 * - Analytics and reporting
 * - Fraud detection
 * - Accounting/reconciliation
 * - Audit trail
 * - Real-time notifications
 * - Downstream service integration
 *
 * Events Published:
 * - WalletCreditedEvent: Funds added to wallet
 * - WalletDebitedEvent: Funds removed from wallet
 * - WalletTransferEvent: Funds transferred between wallets
 *
 * @author Waqiti Wallet Team
 * @version 2.0.0 - Production Event Publishing
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WalletTransactionEventProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Kafka topics
    private static final String WALLET_CREDITED_TOPIC = "wallet-credited-events";
    private static final String WALLET_DEBITED_TOPIC = "wallet-debited-events";
    private static final String WALLET_TRANSFER_TOPIC = "wallet-transfer-events";

    /**
     * Publish wallet credited event
     *
     * @param walletId Wallet that received funds
     * @param userId User who owns the wallet
     * @param amount Amount credited
     * @param currency Currency code
     * @param transactionId Transaction identifier
     * @param description Transaction description
     * @param balanceAfter Wallet balance after credit
     */
    public void publishWalletCredited(
            UUID walletId,
            UUID userId,
            BigDecimal amount,
            String currency,
            UUID transactionId,
            String description,
            BigDecimal balanceAfter) {

        try {
            WalletCreditedEvent event = WalletCreditedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("WALLET_CREDITED")
                .timestamp(LocalDateTime.now())
                .walletId(walletId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId)
                .description(description)
                .balanceAfter(balanceAfter)
                .build();

            CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(WALLET_CREDITED_TOPIC, walletId.toString(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("EVENT_PUBLISHED: WalletCreditedEvent - Wallet: {}, Amount: {} {}, Transaction: {}",
                            walletId, amount, currency, transactionId);
                } else {
                    log.error("EVENT_PUBLISH_FAILED: WalletCreditedEvent - Wallet: {}, Error: {}",
                            walletId, ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("EVENT_PUBLISH_ERROR: Failed to publish WalletCreditedEvent - Wallet: {}",
                    walletId, e);
            // Don't throw - event publishing should not fail wallet operation
        }
    }

    /**
     * Publish wallet debited event
     *
     * @param walletId Wallet that lost funds
     * @param userId User who owns the wallet
     * @param amount Amount debited
     * @param currency Currency code
     * @param transactionId Transaction identifier
     * @param description Transaction description
     * @param balanceAfter Wallet balance after debit
     */
    public void publishWalletDebited(
            UUID walletId,
            UUID userId,
            BigDecimal amount,
            String currency,
            UUID transactionId,
            String description,
            BigDecimal balanceAfter) {

        try {
            WalletDebitedEvent event = WalletDebitedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("WALLET_DEBITED")
                .timestamp(LocalDateTime.now())
                .walletId(walletId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId)
                .description(description)
                .balanceAfter(balanceAfter)
                .build();

            CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(WALLET_DEBITED_TOPIC, walletId.toString(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("EVENT_PUBLISHED: WalletDebitedEvent - Wallet: {}, Amount: {} {}, Transaction: {}",
                            walletId, amount, currency, transactionId);
                } else {
                    log.error("EVENT_PUBLISH_FAILED: WalletDebitedEvent - Wallet: {}, Error: {}",
                            walletId, ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("EVENT_PUBLISH_ERROR: Failed to publish WalletDebitedEvent - Wallet: {}",
                    walletId, e);
            // Don't throw - event publishing should not fail wallet operation
        }
    }

    /**
     * Publish wallet transfer event
     *
     * @param fromWalletId Source wallet
     * @param fromUserId Source user
     * @param toWalletId Destination wallet
     * @param toUserId Destination user
     * @param amount Amount transferred
     * @param currency Currency code
     * @param transactionId Transaction identifier
     * @param description Transfer description
     * @param fromBalanceAfter Source wallet balance after transfer
     * @param toBalanceAfter Destination wallet balance after transfer
     */
    public void publishWalletTransfer(
            UUID fromWalletId,
            UUID fromUserId,
            UUID toWalletId,
            UUID toUserId,
            BigDecimal amount,
            String currency,
            UUID transactionId,
            String description,
            BigDecimal fromBalanceAfter,
            BigDecimal toBalanceAfter) {

        try {
            WalletTransferEvent event = WalletTransferEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("WALLET_TRANSFER")
                .timestamp(LocalDateTime.now())
                .fromWalletId(fromWalletId)
                .fromUserId(fromUserId)
                .toWalletId(toWalletId)
                .toUserId(toUserId)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId)
                .description(description)
                .fromBalanceAfter(fromBalanceAfter)
                .toBalanceAfter(toBalanceAfter)
                .build();

            CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(WALLET_TRANSFER_TOPIC, transactionId.toString(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.debug("EVENT_PUBLISHED: WalletTransferEvent - From: {}, To: {}, Amount: {} {}, Transaction: {}",
                            fromWalletId, toWalletId, amount, currency, transactionId);
                } else {
                    log.error("EVENT_PUBLISH_FAILED: WalletTransferEvent - Transaction: {}, Error: {}",
                            transactionId, ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("EVENT_PUBLISH_ERROR: Failed to publish WalletTransferEvent - Transaction: {}",
                    transactionId, e);
            // Don't throw - event publishing should not fail wallet operation
        }
    }

    /**
     * Publish compensation event (used by compensation methods)
     */
    public void publishCompensationEvent(
            UUID walletId,
            UUID userId,
            String compensationType,
            BigDecimal amount,
            String currency,
            UUID transactionId,
            String reason,
            BigDecimal balanceAfter) {

        try {
            // Publish as wallet credited event with compensation context
            WalletCreditedEvent event = WalletCreditedEvent.builder()
                .eventId(UUID.randomUUID())
                .eventType("WALLET_COMPENSATION")
                .timestamp(LocalDateTime.now())
                .walletId(walletId)
                .userId(userId)
                .amount(amount)
                .currency(currency)
                .transactionId(transactionId)
                .description("COMPENSATION: " + compensationType + " - " + reason)
                .balanceAfter(balanceAfter)
                .metadata(java.util.Map.of(
                    "compensationType", compensationType,
                    "reason", reason
                ))
                .build();

            CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send("wallet-compensation-events", walletId.toString(), event);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("EVENT_PUBLISHED: WalletCompensationEvent - Wallet: {}, Type: {}, Amount: {} {}",
                            walletId, compensationType, amount, currency);
                } else {
                    log.error("EVENT_PUBLISH_FAILED: WalletCompensationEvent - Wallet: {}, Error: {}",
                            walletId, ex.getMessage(), ex);
                }
            });

        } catch (Exception e) {
            log.error("EVENT_PUBLISH_ERROR: Failed to publish WalletCompensationEvent - Wallet: {}",
                    walletId, e);
        }
    }
}
