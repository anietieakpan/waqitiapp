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
 * Kafka Consumer for Wallet Withdrawal Notifications
 *
 * CRITICAL PRODUCTION CONSUMER - Sends multi-channel notifications when customers
 * withdraw funds from their wallets.
 *
 * This consumer was identified as MISSING during forensic analysis, causing poor
 * customer experience. Withdrawal notifications are ESSENTIAL for:
 * - Instant confirmation of withdrawal request
 * - Security alerts (customer can spot unauthorized withdrawals)
 * - Processing status updates
 * - Settlement confirmation
 * - Transaction transparency
 * - Regulatory compliance (audit trail)
 *
 * Business Impact:
 * - Customer Experience: CRITICAL (immediate feedback expected)
 * - Fraud Detection: HIGH (customer can spot unauthorized activity)
 * - Trust & Transparency: HIGH
 * - Compliance: MEDIUM (withdrawal tracking required)
 *
 * Notification Channels:
 * - SMS: Instant alert with amount and status
 * - Email: Detailed withdrawal receipt with settlement info
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
 * - Security alerts for high-value withdrawals
 *
 * @author Waqiti Notification Team
 * @version 1.0.0
 * @since 2025-10-01
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WalletWithdrawalConsumer {

    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    // CRITICAL P1 FIX: Add user name resolution service for better UX
    private final UserNameResolutionService userNameResolutionService;

    private static final String IDEMPOTENCY_KEY_PREFIX = "notification:withdrawal:idempotency:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;
    private static final String WALLET_WITHDRAWAL_TEMPLATE_CODE = "WALLET_WITHDRAWAL";
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000.00");

    private Counter notificationsSentCounter;
    private Counter notificationsFailedCounter;
    private Counter notificationsDuplicateCounter;
    private Counter highValueWithdrawalCounter;
    private Timer processingTimer;

    @PostConstruct
    public void initMetrics() {
        notificationsSentCounter = Counter.builder("notification.wallet.withdrawal.sent")
            .description("Total wallet withdrawal notifications successfully sent")
            .tag("consumer", "wallet-withdrawal-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        notificationsFailedCounter = Counter.builder("notification.wallet.withdrawal.failed")
            .description("Total wallet withdrawal notifications that failed")
            .tag("consumer", "wallet-withdrawal-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        notificationsDuplicateCounter = Counter.builder("notification.wallet.withdrawal.duplicate")
            .description("Total duplicate wallet withdrawal notifications skipped")
            .tag("consumer", "wallet-withdrawal-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        highValueWithdrawalCounter = Counter.builder("notification.wallet.withdrawal.high_value")
            .description("Total high-value withdrawal notifications sent")
            .tag("consumer", "wallet-withdrawal-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        processingTimer = Timer.builder("notification.wallet.withdrawal.processing.duration")
            .description("Time taken to process wallet withdrawal notifications")
            .tag("consumer", "wallet-withdrawal-notification-consumer")
            .tag("service", "notification-service")
            .register(meterRegistry);

        log.info("✅ WalletWithdrawalConsumer initialized with metrics - Ready to send withdrawal notifications");
    }

    /**
     * Process wallet withdrawal events and send notifications
     *
     * @param event The wallet withdrawal event from wallet-service
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
        topics = "wallet-withdrawal-events",
        groupId = "notification-service-wallet-withdrawal-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleWalletWithdrawal(
            @Payload WalletEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(value = "correlation-id", required = false) String correlationId,
            Acknowledgment acknowledgment) {

        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            log.info("📥 Processing wallet withdrawal notification - WalletId: {}, UserId: {}, Amount: {} {}, TransactionId: {}, Partition: {}, Offset: {}",
                event.getWalletId(),
                event.getUserId(),
                event.getTransactionAmount(),
                event.getCurrency(),
                event.getTransactionId(),
                partition,
                offset);

            // Validate event data
            validateWalletWithdrawalEvent(event);

            // Idempotency check - prevent duplicate notifications
            String idempotencyKey = buildIdempotencyKey(event.getTransactionId(), event.getEventId());
            if (isAlreadyProcessed(idempotencyKey)) {
                log.warn("⚠️ Duplicate wallet withdrawal notification detected - TransactionId: {}, EventId: {} - Skipping to prevent spam",
                    event.getTransactionId(), event.getEventId());
                acknowledgment.acknowledge();
                notificationsDuplicateCounter.increment();
                sample.stop(processingTimer);
                return;
            }

            // Record idempotency before processing
            recordIdempotency(idempotencyKey);

            // Check if high-value withdrawal (requires enhanced security notification)
            boolean isHighValue = event.getTransactionAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0;
            if (isHighValue) {
                log.warn("⚠️ HIGH-VALUE WITHDRAWAL DETECTED - WalletId: {}, Amount: {} {}, TransactionId: {}",
                    event.getWalletId(),
                    event.getTransactionAmount(),
                    event.getCurrency(),
                    event.getTransactionId());
                highValueWithdrawalCounter.increment();
            }

            // Send multi-channel notifications
            sendWithdrawalNotifications(event, correlationId, isHighValue);

            log.info("✅ Wallet withdrawal notifications sent - WalletId: {}, UserId: {}, Amount: {} {}, HighValue: {}",
                event.getWalletId(),
                event.getUserId(),
                event.getTransactionAmount(),
                event.getCurrency(),
                isHighValue);

            // Manual acknowledgment
            acknowledgment.acknowledge();

            notificationsSentCounter.increment();
            sample.stop(processingTimer);

        } catch (IllegalArgumentException e) {
            log.error("❌ Invalid wallet withdrawal event data - TransactionId: {}, Error: {}",
                event.getTransactionId(), e.getMessage(), e);

            notificationsFailedCounter.increment();
            sample.stop(processingTimer);

            // Acknowledge invalid messages to prevent infinite retries
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("❌ Failed to send wallet withdrawal notification - TransactionId: {}, UserId: {}, Error: {}",
                event.getTransactionId(), event.getUserId(), e.getMessage(), e);

            notificationsFailedCounter.increment();
            sample.stop(processingTimer);

            // Don't acknowledge - message will be retried or sent to DLQ
            throw new RuntimeException("Failed to send wallet withdrawal notification", e);
        }
    }

    /**
     * Validates wallet withdrawal event has required fields
     */
    private void validateWalletWithdrawalEvent(WalletEvent event) {
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
     * Sends multi-channel notifications for wallet withdrawal
     */
    private void sendWithdrawalNotifications(WalletEvent event, String correlationId, boolean isHighValue) {
        Map<String, Object> variables = buildNotificationVariables(event, correlationId, isHighValue);

        try {
            // Send SMS notification - CRITICAL for security (always send)
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_WITHDRAWAL_TEMPLATE_CODE,
                NotificationChannel.SMS,
                variables
            );
            log.debug("✉️ SMS notification sent for wallet withdrawal - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send SMS notification for wallet withdrawal - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
            // SMS failure is critical for withdrawals - log as error but continue
        }

        try {
            // Send Email notification - Detailed receipt with settlement info
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_WITHDRAWAL_TEMPLATE_CODE,
                NotificationChannel.EMAIL,
                variables
            );
            log.debug("📧 Email notification sent for wallet withdrawal - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send email notification for wallet withdrawal - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
        }

        try {
            // Send Push notification - Real-time mobile alert
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_WITHDRAWAL_TEMPLATE_CODE,
                NotificationChannel.PUSH,
                variables
            );
            log.debug("🔔 Push notification sent for wallet withdrawal - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send push notification for wallet withdrawal - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
        }

        try {
            // Send In-App notification - Transaction history update
            notificationService.sendNotification(
                event.getUserId(),
                WALLET_WITHDRAWAL_TEMPLATE_CODE,
                NotificationChannel.IN_APP,
                variables
            );
            log.debug("📱 In-app notification sent for wallet withdrawal - UserId: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to send in-app notification for wallet withdrawal - UserId: {}, Error: {}",
                event.getUserId(), e.getMessage(), e);
        }
    }

    /**
     * Builds notification template variables
     */
    private Map<String, Object> buildNotificationVariables(WalletEvent event, String correlationId, boolean isHighValue) {
        Map<String, Object> variables = new HashMap<>();

        // Transaction details
        variables.put("transactionId", event.getTransactionId());
        variables.put("walletId", event.getWalletId());
        variables.put("amount", formatAmount(event.getTransactionAmount()));
        variables.put("currency", event.getCurrency());
        variables.put("currencySymbol", getCurrencySymbol(event.getCurrency()));
        variables.put("amountWithCurrency", formatAmount(event.getTransactionAmount()) + " " + event.getCurrency());

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

        // Destination information
        if (event.getBankAccountId() != null) {
            variables.put("destinationAccount", maskAccountNumber(event.getBankAccountId()));
        } else {
            variables.put("destinationAccount", "External Account");
        }

        // P2 QUICK WIN: Withdrawal method/channel - extract from metadata
        String withdrawalMethod = extractWithdrawalMethod(event);
        variables.put("withdrawalMethod", withdrawalMethod);

        // Settlement information
        LocalDateTime estimatedSettlement = LocalDateTime.now().plusDays(1); // T+1 settlement
        variables.put("estimatedSettlementDate", estimatedSettlement.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        variables.put("estimatedSettlementTime", "1-2 business days");

        // Timestamp
        LocalDateTime transactionTime = event.getTimestamp() != null
            ? LocalDateTime.ofInstant(event.getTimestamp(), ZoneOffset.UTC)
            : LocalDateTime.now();

        variables.put("transactionDate", transactionTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        variables.put("transactionTime", transactionTime.format(DateTimeFormatter.ofPattern("hh:mm a")));
        variables.put("transactionDateTime", transactionTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy hh:mm a")));

        // Security and high-value flags
        variables.put("isHighValue", isHighValue);
        variables.put("highValueThreshold", formatAmount(HIGH_VALUE_THRESHOLD));
        variables.put("securityAlert", isHighValue ? "HIGH VALUE WITHDRAWAL" : "Standard Withdrawal");

        // Status information
        variables.put("status", "PROCESSING");
        variables.put("statusMessage", "Your withdrawal is being processed");

        // Reference details
        variables.put("referenceNumber", event.getTransactionId());
        variables.put("eventId", event.getEventId());
        variables.put("correlationId", correlationId != null ? correlationId : event.getCorrelationId());

        // Support information
        variables.put("supportEmail", "support@example.com");
        variables.put("supportPhone", "+1-800-WAQITI");
        variables.put("platformName", "Waqiti");

        // Security message
        if (isHighValue) {
            variables.put("securityMessage",
                "This is a high-value withdrawal. If you did not authorize this transaction, please contact support immediately.");
        } else {
            variables.put("securityMessage",
                "If you did not authorize this withdrawal, please contact support immediately.");
        }

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

    /**
     * P2 QUICK WIN: Extract withdrawal method from event metadata
     *
     * Parses the event metadata to determine the withdrawal method/channel.
     * Provides better UX by showing actual method instead of generic "Bank Transfer".
     *
     * Supported methods:
     * - ACH (Automated Clearing House)
     * - Wire Transfer
     * - Instant Transfer (real-time payments)
     * - Card Withdrawal (debit card)
     * - Crypto Withdrawal
     * - Bank Transfer (default/fallback)
     *
     * @param event Wallet withdrawal event
     * @return Human-readable withdrawal method
     */
    private String extractWithdrawalMethod(WalletEvent event) {
        try {
            if (event.getMetadata() == null || event.getMetadata().isEmpty()) {
                return "Bank Transfer"; // Default
            }

            Map<String, Object> metadata = event.getMetadata();

            // Check for withdrawal method field
            if (metadata.containsKey("withdrawalMethod")) {
                String method = (String) metadata.get("withdrawalMethod");
                return formatWithdrawalMethod(method);
            }

            // Check for payment method type
            if (metadata.containsKey("paymentMethod")) {
                String method = (String) metadata.get("paymentMethod");
                return formatWithdrawalMethod(method);
            }

            // Check for transfer type
            if (metadata.containsKey("transferType")) {
                String type = (String) metadata.get("transferType");
                return formatWithdrawalMethod(type);
            }

            // Check for channel
            if (metadata.containsKey("channel")) {
                String channel = (String) metadata.get("channel");
                return formatWithdrawalMethod(channel);
            }

            // Default fallback
            return "Bank Transfer";

        } catch (Exception e) {
            log.warn("P2: Failed to extract withdrawal method from metadata - using default", e);
            return "Bank Transfer";
        }
    }

    /**
     * P2 QUICK WIN: Format withdrawal method for display
     */
    private String formatWithdrawalMethod(String method) {
        if (method == null || method.isBlank()) {
            return "Bank Transfer";
        }

        // Normalize and format
        String normalized = method.toUpperCase().trim();

        return switch (normalized) {
            case "ACH", "ACH_TRANSFER", "ACH_WITHDRAWAL" -> "ACH Transfer";
            case "WIRE", "WIRE_TRANSFER", "WIRE_WITHDRAWAL" -> "Wire Transfer";
            case "INSTANT", "INSTANT_TRANSFER", "RTP", "REAL_TIME_PAYMENT" -> "Instant Transfer";
            case "CARD", "DEBIT_CARD", "CARD_WITHDRAWAL" -> "Card Withdrawal";
            case "CRYPTO", "CRYPTOCURRENCY", "CRYPTO_WITHDRAWAL" -> "Crypto Withdrawal";
            case "CHECK", "CHEQUE" -> "Check";
            case "CASH" -> "Cash Withdrawal";
            case "PAYPAL" -> "PayPal";
            case "VENMO" -> "Venmo";
            case "ZELLE" -> "Zelle";
            default -> "Bank Transfer";
        };
    }
}
