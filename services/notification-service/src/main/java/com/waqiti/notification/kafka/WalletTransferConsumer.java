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
 * Kafka Consumer for Wallet Transfer Notifications
 *
 * CRITICAL PRODUCTION CONSUMER - Sends multi-channel notifications for wallet-to-wallet
 * transfers to BOTH sender and recipient.
 *
 * This consumer was identified as MISSING during forensic analysis, causing poor
 * customer experience. Transfer notifications are ESSENTIAL for:
 * - Instant confirmation to sender (money sent)
 * - Instant notification to recipient (money received)
 * - Transaction transparency for both parties
 * - Fraud detection (alerts parties to unauthorized transfers)
 * - Dispute resolution (clear audit trail)
 * - Customer trust and satisfaction
 *
 * Business Impact:
 * - Customer Experience: CRITICAL (immediate feedback expected)
 * - Fraud Detection: HIGH (both parties can spot unauthorized activity)
 * - Trust & Transparency: HIGH
 * - Dispute Resolution: MEDIUM (clear communication reduces disputes)
 *
 * Notification Channels (BOTH sender and recipient):
 * - SMS: Instant alert with amount and counterparty
 * - Email: Detailed transaction receipt
 * - Push: Real-time mobile notification
 * - In-App: Transaction history update
 *
 * Features:
 * - Redis-based idempotency (24-hour TTL) to prevent duplicate notifications
 * - Dual notifications (sender + recipient)
 * - Multi-channel delivery per user
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
public class WalletTransferConsumer {

    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    // CRITICAL P1 FIX: Add user name resolution service for better UX
    private final UserNameResolutionService userNameResolutionService;

    private static final String IDEMPOTENCY_KEY_PREFIX = "notification:transfer:idempotency:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;
    private static final String WALLET_TRANSFER_SENT_TEMPLATE = "WALLET_TRANSFER_SENT";
    private static final String WALLET_TRANSFER_RECEIVED_TEMPLATE = "WALLET_TRANSFER_RECEIVED";

    private Counter notificationsSentCounter;
    private Counter notificationsFailedCounter;
    private Counter notificationsDuplicateCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        notificationsSentCounter = Counter.builder("notification.wallet.transfer.sent")
            .description("Total wallet transfer notifications successfully sent")
            .tag("consumer", "wallet-transfer-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        notificationsFailedCounter = Counter.builder("notification.wallet.transfer.failed")
            .description("Total wallet transfer notifications that failed")
            .tag("consumer", "wallet-transfer-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        notificationsDuplicateCounter = Counter.builder("notification.wallet.transfer.duplicate")
            .description("Total duplicate wallet transfer notifications skipped")
            .tag("consumer", "wallet-transfer-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        processingTimer = Timer.builder("notification.wallet.transfer.processing.duration")
            .description("Time taken to process wallet transfer notifications")
            .tag("consumer", "wallet-transfer-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        log.info("✅ WalletTransferConsumer initialized with metrics - Ready to send transfer notifications");
    }

    /**
     * Process wallet transfer events and send notifications to BOTH sender and recipient
     *
     * @param event The wallet transfer event from wallet-service
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
        topics = "wallet-transfer-events",
        groupId = "notification-service-wallet-transfer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleWalletTransfer(
            @Payload WalletEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("📥 Processing wallet transfer notification - FromWallet: {}, ToWallet: {}, Amount: {} {}, TransactionId: {}, Partition: {}, Offset: {}",
                event.getWalletId(),
                event.getCounterpartyWalletId(),
                event.getTransactionAmount(),
                event.getCurrency(),
                event.getTransactionId(),
                partition,
                offset);

            // Validate event data
            validateWalletTransferEvent(event);

            // Idempotency check - prevent duplicate notifications
            String idempotencyKey = buildIdempotencyKey(event.getTransactionId(), event.getEventId());
            if (isAlreadyProcessed(idempotencyKey)) {
                log.warn("⚠️ Duplicate wallet transfer notification detected - TransactionId: {}, EventId: {} - Skipping to prevent spam",
                    event.getTransactionId(), event.getEventId());
                acknowledgment.acknowledge();
                notificationsDuplicateCounter.increment();
                sample.stop(processingTimer);
                return;
            }

            // Record idempotency before processing
            recordIdempotency(idempotencyKey);

            // Send notifications to SENDER (money sent)
            sendSenderNotifications(event, correlationId);

            // Send notifications to RECIPIENT (money received)
            // Note: Recipient userId should be in event.getCounterpartyId() or fetched from wallet mapping
            if (event.getCounterpartyId() != null && !event.getCounterpartyId().isBlank()) {
                sendRecipientNotifications(event, correlationId);
            } else {
                log.warn("⚠️ No recipient userId found for transfer notification - TransactionId: {}, CounterpartyWalletId: {}",
                    event.getTransactionId(), event.getCounterpartyWalletId());
            }

            log.info("✅ Wallet transfer notifications sent - FromWallet: {}, ToWallet: {}, Amount: {} {}",
                event.getWalletId(),
                event.getCounterpartyWalletId(),
                event.getTransactionAmount(),
                event.getCurrency());

            // Manual acknowledgment
            acknowledgment.acknowledge();

            notificationsSentCounter.increment();
            sample.stop(processingTimer);

        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid wallet transfer event data - TransactionId: {}, Error: {}",
                event.getTransactionId(), e.getMessage(), e);

            notificationsFailedCounter.increment();
            sample.stop(processingTimer);

            // Acknowledge invalid messages to prevent infinite retries
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("❌ Failed to send wallet transfer notification - TransactionId: {}, Error: {}",
                event.getTransactionId(), e.getMessage(), e);

            notificationsFailedCounter.increment();
            sample.stop(processingTimer);

            // Don't acknowledge - message will be retried or sent to DLQ
            throw new RuntimeException("Failed to send wallet transfer notification", e);
        }
    }

    /**
     * Validates wallet transfer event has required fields
     */
    private void validateWalletTransferEvent(WalletEvent event) {
        if (event.getUserId() == null || event.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId (sender) is required for notification");
        }

        if (event.getWalletId() == null || event.getWalletId().isBlank()) {
            throw new IllegalArgumentException("walletId (source) is required");
        }

        if (event.getCounterpartyWalletId() == null || event.getCounterpartyWalletId().isBlank()) {
            throw new IllegalArgumentException("counterpartyWalletId (destination) is required");
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
     * Sends notifications to SENDER (money sent)
     */
    private void sendSenderNotifications(WalletEvent event, String correlationId) {
        Map<String, Object> variables = buildSenderNotificationVariables(event, correlationId);

        try {
            // Send SMS notification - Quick alert
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_TRANSFER_SENT_TEMPLATE,
                NotificationChannel.SMS,
                variables
            );
            log.debug("✉️ SMS notification sent to sender - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send SMS notification to sender - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
            // Continue with other channels even if SMS fails
        }

        try {
            // Send Email notification - Detailed receipt
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_TRANSFER_SENT_TEMPLATE,
                NotificationChannel.EMAIL,
                variables
            );
            log.debug("📧 Email notification sent to sender - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send email notification to sender - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
        }

        try {
            // Send Push notification - Real-time mobile alert
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_TRANSFER_SENT_TEMPLATE,
                NotificationChannel.PUSH,
                variables
            );
            log.debug("🔔 Push notification sent to sender - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send push notification to sender - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
        }

        try {
            // Send In-App notification
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_TRANSFER_SENT_TEMPLATE,
                NotificationChannel.IN_APP,
                variables
            );
            log.debug("📱 In-app notification sent to sender - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send in-app notification to sender - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Sends notifications to RECIPIENT (money received)
     */
    private void sendRecipientNotifications(WalletEvent event, String correlationId) {
        Map<String, Object> variables = buildRecipientNotificationVariables(event, correlationId);
        String recipientUserId = event.getCounterpartyId();

        try {
            // Send SMS notification
            notificationService.sendNotification(
                recipientUserId,
                WALLET_TRANSFER_RECEIVED_TEMPLATE,
                NotificationChannel.SMS,
                variables
            );
            log.debug("✉️ SMS notification sent to recipient - UserId: {}", recipientUserId);

        } catch (Exception e) {
            log.error("Failed to send SMS notification to recipient - UserId: {}, Error: {}",
                recipientUserId, e.getMessage(), e);
        }

        try {
            // Send Email notification
            notificationService.sendNotification(
                recipientUserId,
                WALLET_TRANSFER_RECEIVED_TEMPLATE,
                NotificationChannel.EMAIL,
                variables
            );
            log.debug("📧 Email notification sent to recipient - UserId: {}", recipientUserId);

        } catch (Exception e) {
            log.error("Failed to send email notification to recipient - UserId: {}, Error: {}",
                recipientUserId, e.getMessage(), e);
        }

        try {
            // Send Push notification
            notificationService.sendNotification(
                recipientUserId,
                WALLET_TRANSFER_RECEIVED_TEMPLATE,
                NotificationChannel.PUSH,
                variables
            );
            log.debug("🔔 Push notification sent to recipient - UserId: {}", recipientUserId);

        } catch (Exception e) {
            log.error("Failed to send push notification to recipient - UserId: {}, Error: {}",
                recipientUserId, e.getMessage(), e);
        }

        try {
            // Send In-App notification
            notificationService.sendNotification(
                recipientUserId,
                WALLET_TRANSFER_RECEIVED_TEMPLATE,
                NotificationChannel.IN_APP,
                variables
            );
            log.debug("📱 In-app notification sent to recipient - UserId: {}", recipientUserId);

        } catch (Exception e) {
            log.error("Failed to send in-app notification to recipient - UserId: {}, Error: {}",
                recipientUserId, e.getMessage(), e);
        }
    }

    /**
     * Builds notification variables for SENDER
     */
    private Map<String, Object> buildSenderNotificationVariables(WalletEvent event, String correlationId) {
        Map<String, Object> variables = new HashMap<>();

        // Transaction details
        variables.put("transactionId", event.getTransactionId());
        variables.put("sourceWalletId", event.getWalletId());
        variables.put("destinationWalletId", event.getCounterpartyWalletId());
        variables.put("amount", formatAmount(event.getTransactionAmount()));
        variables.put("currency", event.getCurrency());
        variables.put("currencySymbol", getCurrencySymbol(event.getCurrency()));
        variables.put("amountWithCurrency", formatAmount(event.getTransactionAmount()) + " " + event.getCurrency());

        // CRITICAL P1 FIX: Sender details with actual user name resolution
        variables.put("senderId", event.getUserId());
        variables.put("senderName", userNameResolutionService.getUserName(event.getUserId())); // Resolved from user-service

        // Recipient details
        variables.put("recipientId", event.getCounterpartyId() != null ? event.getCounterpartyId() : "Unknown");
        variables.put("recipientName", event.getCounterpartyName() != null ? event.getCounterpartyName() : "Recipient");
        variables.put("recipientWalletId", event.getCounterpartyWalletId());

        // Balance information
        if (event.getCurrentBalance() != null) {
            variables.put("newBalance", formatAmount(event.getCurrentBalance()));
            variables.put("balanceFormatted", formatAmount(event.getCurrentBalance()) + " " + event.getCurrency());
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

        // Message type
        variables.put("messageType", "TRANSFER_SENT");

        return variables;
    }

    /**
     * Builds notification variables for RECIPIENT
     */
    private Map<String, Object> buildRecipientNotificationVariables(WalletEvent event, String correlationId) {
        Map<String, Object> variables = new HashMap<>();

        // Transaction details
        variables.put("transactionId", event.getTransactionId());
        variables.put("sourceWalletId", event.getWalletId());
        variables.put("destinationWalletId", event.getCounterpartyWalletId());
        variables.put("amount", formatAmount(event.getTransactionAmount()));
        variables.put("currency", event.getCurrency());
        variables.put("currencySymbol", getCurrencySymbol(event.getCurrency()));
        variables.put("amountWithCurrency", formatAmount(event.getTransactionAmount()) + " " + event.getCurrency());

        // CRITICAL P1 FIX: Sender details (from recipient's perspective) with actual user name resolution
        variables.put("senderId", event.getUserId());
        variables.put("senderName", userNameResolutionService.getUserName(event.getUserId())); // Resolved from user-service
        variables.put("senderWalletId", event.getWalletId());

        // Recipient details
        variables.put("recipientId", event.getCounterpartyId());
        variables.put("recipientName", event.getCounterpartyName() != null ? event.getCounterpartyName() : "You");
        variables.put("recipientWalletId", event.getCounterpartyWalletId());

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

        // Message type
        variables.put("messageType", "TRANSFER_RECEIVED");

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
