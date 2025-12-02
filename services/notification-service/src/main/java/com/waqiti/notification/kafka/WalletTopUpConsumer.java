package com.waqiti.notification.kafka;

import com.waqiti.common.events.WalletEvent;
import com.waqiti.notification.service.NotificationService;
// CRITICAL P1 FIX: Add user name resolution service
import com.waqiti.notification.service.UserNameResolutionService;
import com.waqiti.notification.domain.NotificationChannel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Kafka Consumer for Wallet Top-Up Notifications
 *
 * CRITICAL PRODUCTION CONSUMER - Sends multi-channel notifications when customers
 * top up their wallets.
 *
 * This consumer was identified as MISSING during forensic analysis, causing poor
 * customer experience. Top-up notifications are ESSENTIAL for:
 * - Instant confirmation of fund receipt
 * - Customer satisfaction and trust
 * - Transaction transparency
 * - Fraud detection (alerts customer to unauthorized top-ups)
 * - Compliance audit trail
 *
 * Business Impact:
 * - Customer Experience: CRITICAL (immediate feedback expected)
 * - Fraud Detection: HIGH (customer can spot unauthorized activity)
 * - Trust & Transparency: HIGH
 *
 * Notification Channels:
 * - SMS: Instant alert with amount
 * - Email: Detailed transaction receipt
 * - Push: Real-time mobile notification
 * - In-App: Transaction history update
 *
 * Features:
 * - Redis-based idempotency (24-hour TTL) to prevent duplicate notifications
 * - Multi-channel delivery (SMS, Email, Push, In-App)
 * - Automatic retry with exponential backoff (5 attempts)
 * - Dead letter queue for unprocessable messages
 * - Comprehensive metrics (Prometheus/Micrometer)
 * - Template-based messaging with localization support
 *
 * @author Waqiti Notification Team
 * @version 1.0.0
 * @since 2025-10-01
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletTopUpConsumer {

    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    // CRITICAL P1 FIX: Add user name resolution service for better UX
    private final UserNameResolutionService userNameResolutionService;

    private static final String IDEMPOTENCY_KEY_PREFIX = "notification:topup:idempotency:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;
    private static final String WALLET_TOP_UP_TEMPLATE_CODE = "WALLET_TOP_UP";

    private Counter notificationsSentCounter;
    private Counter notificationsFailedCounter;
    private Counter notificationsDuplicateCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        notificationsSentCounter = Counter.builder("notification.wallet.topup.sent")
            .description("Total wallet top-up notifications successfully sent")
            .tag("consumer", "wallet-topup-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        notificationsFailedCounter = Counter.builder("notification.wallet.topup.failed")
            .description("Total wallet top-up notifications that failed")
            .tag("consumer", "wallet-topup-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        notificationsDuplicateCounter = Counter.builder("notification.wallet.topup.duplicate")
            .description("Total duplicate wallet top-up notifications skipped")
            .tag("consumer", "wallet-topup-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        processingTimer = Timer.builder("notification.wallet.topup.processing.duration")
            .description("Time taken to process wallet top-up notifications")
            .tag("consumer", "wallet-topup-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        log.info("✅ WalletTopUpConsumer initialized with metrics - Ready to send top-up notifications");
    }

    /**
     * Process wallet top-up events and send notifications
     *
     * @param event The wallet top-up event from wallet-service
     * @param topic Kafka topic name
     * @param partition Kafka partition ID
     * @param offset Kafka offset
     * @param correlationId Distributed tracing correlation ID
     * @param acknowledgment Manual acknowledgment for exactly-once semantics
     */
    @RetryableTopic(
        attempts = "5",
        backoff = @Backoff(delay = 2000L, multiplier = 2.0),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class}
    )
    @KafkaListener(
        topics = "wallet-topup-events",
        groupId = "notification-service-wallet-topup-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleWalletTopUp(
            @Payload WalletEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("📥 Processing wallet top-up notification - WalletId: {}, UserId: {}, Amount: {} {}, TransactionId: {}, Partition: {}, Offset: {}",
                event.getWalletId(),
                event.getUserId(),
                event.getTransactionAmount(),
                event.getCurrency(),
                event.getTransactionId(),
                partition,
                offset);

            // Validate event data
            validateWalletTopUpEvent(event);

            // Idempotency check - prevent duplicate notifications
            String idempotencyKey = buildIdempotencyKey(event.getTransactionId(), event.getEventId());
            if (isAlreadyProcessed(idempotencyKey)) {
                log.warn("⚠️ Duplicate wallet top-up notification detected - TransactionId: {}, EventId: {} - Skipping to prevent spam",
                    event.getTransactionId(), event.getEventId());
                acknowledgment.acknowledge();
                notificationsDuplicateCounter.increment();
                sample.stop(processingTimer);
                return;
            }

            // Record idempotency before processing
            recordIdempotency(idempotencyKey);

            // Send multi-channel notifications
            sendTopUpNotifications(event, correlationId);

            log.info("✅ Wallet top-up notifications sent - WalletId: {}, UserId: {}, Amount: {} {}",
                event.getWalletId(),
                event.getUserId(),
                event.getTransactionAmount(),
                event.getCurrency());

            // Manual acknowledgment
            acknowledgment.acknowledge();

            notificationsSentCounter.increment();
            sample.stop(processingTimer);

        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid wallet top-up event data - TransactionId: {}, Error: {}",
                event.getTransactionId(), e.getMessage(), e);

            notificationsFailedCounter.increment();
            sample.stop(processingTimer);

            // Acknowledge invalid messages to prevent infinite retries
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("❌ Failed to send wallet top-up notification - TransactionId: {}, UserId: {}, Error: {}",
                event.getTransactionId(), event.getUserId(), e.getMessage(), e);

            notificationsFailedCounter.increment();
            sample.stop(processingTimer);

            // Don't acknowledge - message will be retried or sent to DLQ
            throw new RuntimeException("Failed to send wallet top-up notification", e);
        }
    }

    /**
     * Validates wallet top-up event has required fields
     */
    private void validateWalletTopUpEvent(WalletEvent event) {
        if (event.getUserId() == null || event.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId is required for notification");
        }

        if (event.getWalletId() == null || event.getWalletId().isBlank()) {
            throw new IllegalArgumentException("walletId is required");
        }

        if (event.getTransactionId() == null || event.getTransactionId().isBlank()) {
            throw new IllegalArgumentException("transactionId is required");
        }

        if (event.getTransactionAmount() == null || event.getTransactionAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("transactionAmount must be positive");
        }

        if (event.getCurrency() == null || event.getCurrency().isBlank()) {
            throw new IllegalArgumentException("currency is required");
        }
    }

    /**
     * Sends multi-channel notifications for wallet top-up
     */
    private void sendTopUpNotifications(WalletEvent event, String correlationId) {
        Map<String, Object> variables = buildNotificationVariables(event, correlationId);

        try {
            // Send SMS notification - Quick alert
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_TOP_UP_TEMPLATE_CODE,
                NotificationChannel.SMS,
                variables
            );
            log.debug("✉️ SMS notification sent for wallet top-up - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send SMS notification for wallet top-up - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
            // Continue with other channels even if SMS fails
        }

        try {
            // Send Email notification - Detailed receipt
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_TOP_UP_TEMPLATE_CODE,
                NotificationChannel.EMAIL,
                variables
            );
            log.debug("📧 Email notification sent for wallet top-up - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send email notification for wallet top-up - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
        }

        try {
            // Send Push notification - Real-time mobile alert
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_TOP_UP_TEMPLATE_CODE,
                NotificationChannel.PUSH,
                variables
            );
            log.debug("🔔 Push notification sent for wallet top-up - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send push notification for wallet top-up - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
        }

        try {
            // Send In-App notification - Transaction history update
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_TOP_UP_TEMPLATE_CODE,
                NotificationChannel.IN_APP,
                variables
            );
            log.debug("📱 In-app notification sent for wallet top-up - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send in-app notification for wallet top-up - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Builds notification template variables
     */
    private Map<String, Object> buildNotificationVariables(WalletEvent event, String correlationId) {
        Map<String, Object> variables = new HashMap<>();

        // Transaction details
        variables.put("transactionId", event.getTransactionId());
        variables.put("walletId", event.getWalletId());
        variables.put("amount", formatAmount(event.getTransactionAmount()));
        variables.put("currency", event.getCurrency());
        variables.put("currencySymbol", getCurrencySymbol(event.getCurrency()));

        // CRITICAL P1 FIX: User details with actual user name resolution
        variables.put("userId", event.getUserId());
        variables.put("userName", userNameResolutionService.getUserName(event.getUserId())); // Resolved from user-service

        // Balance information
        if (event.getCurrentBalance() != null) {
            variables.put("newBalance", formatAmount(event.getCurrentBalance()));
            variables.put("balanceFormatted", formatAmount(event.getCurrentBalance()) + " " + event.getCurrency());
        }

        if (event.getPreviousBalance() != null) {
            variables.put("previousBalance", formatAmount(event.getPreviousBalance()));
        }

        // Source information
        if (event.getBankAccountId() != null) {
            variables.put("sourceAccount", maskAccountNumber(event.getBankAccountId()));
        } else {
            variables.put("sourceAccount", "External Source");
        }

        // Timestamp
        LocalDateTime transactionTime = event.getTimestamp() != null
            ? LocalDateTime.ofInstant(event.getTimestamp(), ZoneOffset.UTC)
            : LocalDateTime.now();

        variables.put("transactionDate", transactionTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        variables.put("transactionTime", transactionTime.format(DateTimeFormatter.ofPattern("hh:mm a")));
        variables.put("transactionDateTime", transactionTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a")));

        // Reference details
        variables.put("referenceNumber", event.getTransactionId());
        variables.put("eventId", event.getEventId());
        variables.put("correlationId", correlationId != null ? correlationId : event.getCorrelationId());

        // Support information
        variables.put("supportEmail", "support@example.com");
        variables.put("supportPhone", "+1-800-WAQITI");
        variables.put("platformName", "Waqiti");

        return variables;
    }

    /**
     * Formats amount for display (e.g., 1000.50 -> "1,000.50")
     */
    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0.00";
        }
        return String.format("%,.2f", amount);
    }

    /**
     * Gets currency symbol for common currencies
     */
    private String getCurrencySymbol(String currency) {
        return switch (currency.toUpperCase()) {
            case "USD" -> "$";
            case "EUR" -> "€";
            case "GBP" -> "£";
            case "JPY" -> "¥";
            case "CAD" -> "C$";
            case "AUD" -> "A$";
            default -> currency;
        };
    }

    /**
     * Masks account number for security (shows last 4 digits only)
     */
    private String maskAccountNumber(String accountNumber) {
        if (accountNumber == null || accountNumber.length() <= 4) {
            return "****";
        }
        return "****" + accountNumber.substring(accountNumber.length() - 4);
    }

    /**
     * Builds idempotency key for Redis
     */
    private String buildIdempotencyKey(String transactionId, String eventId) {
        return IDEMPOTENCY_KEY_PREFIX + transactionId + ":" + eventId;
    }

    /**
     * Checks if notification was already sent using Redis
     */
    private boolean isAlreadyProcessed(String idempotencyKey) {
        Boolean exists = redisTemplate.hasKey(idempotencyKey);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Records notification processing in Redis with 24-hour TTL
     */
    private void recordIdempotency(String idempotencyKey) {
        redisTemplate.opsForValue().set(
            idempotencyKey,
            Instant.now().toString(),
            IDEMPOTENCY_TTL_HOURS,
            TimeUnit.HOURS
        );
    }
}
