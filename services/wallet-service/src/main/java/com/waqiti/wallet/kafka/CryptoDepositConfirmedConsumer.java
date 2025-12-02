package com.waqiti.wallet.kafka;

import com.waqiti.common.exception.BusinessException;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.locking.DistributedLockService;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.wallet.domain.Transaction;
import com.waqiti.wallet.domain.TransactionStatus;
import com.waqiti.wallet.domain.TransactionType;
import com.waqiti.wallet.domain.Wallet;
import com.waqiti.wallet.repository.TransactionRepository;
import com.waqiti.wallet.repository.WalletRepository;
import com.waqiti.wallet.service.WalletNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CRITICAL FIX #26: CryptoDepositConfirmedConsumer
 * Credits wallet when crypto deposits reach required confirmations
 * Impact: Improves crypto user experience and trust
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CryptoDepositConfirmedConsumer {
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final WalletNotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final DistributedLockService lockService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "crypto.deposit.confirmed", groupId = "wallet-crypto-deposit-processor")
    @Transactional
    public void handle(CryptoDepositEvent event, Acknowledgment ack) {
        long startTime = System.currentTimeMillis();
        String lockId = null;

        try {
            log.info("₿ CRYPTO DEPOSIT CONFIRMED: depositId={}, userId={}, amount={} {}, confirmations={}",
                event.getDepositId(), event.getUserId(), event.getAmount(), event.getCryptocurrency(),
                event.getConfirmations());

            String key = "crypto:deposit:" + event.getDepositId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            // Acquire lock on wallet
            lockId = lockService.acquireLock("wallet-" + event.getWalletId(), Duration.ofMinutes(5));
            if (lockId == null) {
                throw new BusinessException("Failed to acquire wallet lock");
            }

            Wallet wallet = walletRepository.findById(event.getWalletId())
                .orElseThrow(() -> new BusinessException("Wallet not found"));

            // Credit wallet
            BigDecimal oldBalance = wallet.getBalance();
            wallet.setBalance(wallet.getBalance().add(event.getUsdValue()));
            walletRepository.save(wallet);

            // Create transaction record
            Transaction transaction = Transaction.builder()
                .id(UUID.randomUUID())
                .walletId(event.getWalletId())
                .userId(event.getUserId())
                .type(TransactionType.CRYPTO_DEPOSIT)
                .amount(event.getUsdValue())
                .balanceBefore(oldBalance)
                .balanceAfter(wallet.getBalance())
                .status(TransactionStatus.COMPLETED)
                .description(String.format("Crypto deposit: %s %s",
                    event.getAmount(), event.getCryptocurrency()))
                .cryptoAmount(event.getAmount())
                .cryptoCurrency(event.getCryptocurrency())
                .cryptoAddress(event.getDepositAddress())
                .transactionHash(event.getTransactionHash())
                .networkConfirmations(event.getConfirmations())
                .blockchainNetwork(event.getNetwork())
                .createdAt(LocalDateTime.now())
                .build();

            transactionRepository.save(transaction);

            log.info("✅ CRYPTO DEPOSITED: depositId={}, amount={} {} (${} USD), newBalance=${}",
                event.getDepositId(), event.getAmount(), event.getCryptocurrency(),
                event.getUsdValue(), wallet.getBalance());

            // Notify user
            notifyCryptoDeposit(event, wallet);

            metricsCollector.incrementCounter("wallet.crypto.deposit.confirmed");
            metricsCollector.incrementCounter("wallet.crypto.deposit." +
                event.getCryptocurrency().toLowerCase());
            metricsCollector.recordGauge("wallet.crypto.deposit.amount.usd", event.getUsdValue().doubleValue());
            metricsCollector.recordHistogram("wallet.crypto.deposit.processing.duration.ms",
                System.currentTimeMillis() - startTime);

            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process crypto deposit confirmed event", e);
            dlqHandler.sendToDLQ("crypto.deposit.confirmed", event, e, "Processing failed");
            ack.acknowledge();
        } finally {
            if (lockId != null) {
                lockService.releaseLock("wallet-" + event.getWalletId(), lockId);
            }
        }
    }

    private void notifyCryptoDeposit(CryptoDepositEvent event, Wallet wallet) {
        String message = String.format("""
            Cryptocurrency Deposit Confirmed!

            Your crypto deposit has been confirmed and credited to your wallet.

            Deposit Details:
            - Cryptocurrency: %s
            - Amount: %s %s
            - USD Value: $%s
            - Exchange Rate: $%s per %s
            - Network: %s
            - Confirmations: %d

            Blockchain Information:
            - Transaction Hash: %s
            - From Address: %s
            - To Address: %s
            - Block Number: %s
            - Blockchain Explorer: %s

            Your Wallet:
            - Previous Balance: $%s
            - Deposit Amount: $%s
            - New Balance: $%s

            What's Next:
            • Your funds are now available for trading or withdrawal
            • View transaction history: https://example.com/wallet/transactions
            • Track on blockchain: %s

            Security Notice:
            If you did not initiate this deposit, contact security immediately:
            security@example.com | 1-800-WAQITI-SEC

            Questions? Contact crypto support:
            Email: crypto@example.com
            Phone: 1-800-WAQITI-CRYPTO
            """,
            event.getCryptocurrency(),
            event.getAmount(),
            event.getCryptocurrency(),
            event.getUsdValue(),
            event.getExchangeRate(),
            event.getCryptocurrency(),
            event.getNetwork(),
            event.getConfirmations(),
            event.getTransactionHash(),
            maskAddress(event.getFromAddress()),
            maskAddress(event.getDepositAddress()),
            event.getBlockNumber() != null ? event.getBlockNumber().toString() : "Pending",
            event.getBlockchainExplorerUrl(),
            wallet.getBalance().subtract(event.getUsdValue()),
            event.getUsdValue(),
            wallet.getBalance(),
            event.getBlockchainExplorerUrl());

        notificationService.sendCryptoDepositNotification(
            event.getUserId(), event.getWalletId(), event.getAmount(),
            event.getCryptocurrency(), event.getUsdValue(), message);
    }

    private String maskAddress(String address) {
        if (address == null || address.length() < 10) return address;
        return address.substring(0, 6) + "..." + address.substring(address.length() - 4);
    }

    private static class CryptoDepositEvent {
        private UUID depositId, userId, walletId;
        private String cryptocurrency, network, transactionHash, depositAddress, fromAddress;
        private String blockchainExplorerUrl;
        private BigDecimal amount, usdValue, exchangeRate;
        private int confirmations;
        private Long blockNumber;
        private LocalDateTime confirmedAt;

        public UUID getDepositId() { return depositId; }
        public UUID getUserId() { return userId; }
        public UUID getWalletId() { return walletId; }
        public String getCryptocurrency() { return cryptocurrency; }
        public String getNetwork() { return network; }
        public String getTransactionHash() { return transactionHash; }
        public String getDepositAddress() { return depositAddress; }
        public String getFromAddress() { return fromAddress; }
        public String getBlockchainExplorerUrl() { return blockchainExplorerUrl; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getUsdValue() { return usdValue; }
        public BigDecimal getExchangeRate() { return exchangeRate; }
        public int getConfirmations() { return confirmations; }
        public Long getBlockNumber() { return blockNumber; }
        public LocalDateTime getConfirmedAt() { return confirmedAt; }
    }
}
