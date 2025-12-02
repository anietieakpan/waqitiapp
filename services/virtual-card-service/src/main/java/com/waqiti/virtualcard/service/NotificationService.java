package com.waqiti.virtualcard.service;

import com.waqiti.virtualcard.client.NotificationServiceClient;
import com.waqiti.virtualcard.client.NotificationServiceClient.*;
import com.waqiti.virtualcard.domain.CardTransaction;
import com.waqiti.virtualcard.domain.VirtualCard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Notification Service Wrapper
 *
 * Wraps NotificationServiceClient with business logic
 * Handles notification failures gracefully (non-blocking)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationServiceClient notificationServiceClient;

    /**
     * Send card created notification
     */
    public void sendCardCreatedNotification(String userId, VirtualCard card) {
        try {
            log.info("Sending card created notification to user {}", userId);

            CardNotificationRequest request = CardNotificationRequest.builder()
                .userId(userId)
                .cardId(card.getId())
                .message("Your virtual card has been created successfully")
                .data(buildCardData(card))
                .channels(new NotificationChannel[]{
                    NotificationChannel.PUSH,
                    NotificationChannel.EMAIL
                })
                .priority(NotificationPriority.NORMAL)
                .build();

            NotificationResponse response = notificationServiceClient.sendCardCreatedNotification(request);
            logNotificationResult(response, "card created");

        } catch (Exception e) {
            // Non-blocking: Log error but don't fail the operation
            log.error("Failed to send card created notification", e);
        }
    }

    /**
     * Send card status change notification
     */
    public void sendCardStatusNotification(String userId, VirtualCard card, boolean frozen) {
        try {
            String message = frozen ?
                "Your card has been frozen" :
                "Your card has been unfrozen";

            CardNotificationRequest request = CardNotificationRequest.builder()
                .userId(userId)
                .cardId(card.getId())
                .message(message)
                .data(buildCardData(card))
                .channels(new NotificationChannel[]{
                    NotificationChannel.PUSH,
                    NotificationChannel.SMS
                })
                .priority(NotificationPriority.HIGH)
                .build();

            NotificationResponse response = notificationServiceClient.sendCardStatusNotification(request);
            logNotificationResult(response, "card status");

        } catch (Exception e) {
            log.error("Failed to send card status notification", e);
        }
    }

    /**
     * Send transaction notification
     */
    public void sendTransactionNotification(String userId, VirtualCard card, CardTransaction transaction) {
        try {
            TransactionNotificationRequest request = TransactionNotificationRequest.builder()
                .userId(userId)
                .cardId(card.getId())
                .transactionId(transaction.getId())
                .amount(transaction.getAmount().toPlainString())
                .currency(transaction.getCurrency())
                .merchantName(transaction.getMerchantName())
                .message(String.format("Transaction of %s %s at %s",
                    transaction.getAmount(), transaction.getCurrency(), transaction.getMerchantName()))
                .data(buildTransactionData(transaction))
                .channels(new NotificationChannel[]{NotificationChannel.PUSH})
                .priority(NotificationPriority.NORMAL)
                .build();

            NotificationResponse response = notificationServiceClient.sendTransactionNotification(request);
            logNotificationResult(response, "transaction");

        } catch (Exception e) {
            log.error("Failed to send transaction notification", e);
        }
    }

    /**
     * Send security alert
     */
    public void sendSecurityAlert(String userId, String message) {
        try {
            log.warn("Sending security alert to user {}: {}", userId, message);

            SecurityAlertRequest request = SecurityAlertRequest.builder()
                .userId(userId)
                .alertType("CARD_ACCESS")
                .message(message)
                .severity("HIGH")
                .data(Map.of("timestamp", java.time.Instant.now().toString()))
                .channels(new NotificationChannel[]{
                    NotificationChannel.PUSH,
                    NotificationChannel.SMS,
                    NotificationChannel.EMAIL
                })
                .build();

            NotificationResponse response = notificationServiceClient.sendSecurityAlert(request);
            logNotificationResult(response, "security alert");

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send security alert", e);
        }
    }

    /**
     * Send card funded notification
     */
    public void sendCardFundedNotification(String userId, VirtualCard card, BigDecimal amount) {
        try {
            CardNotificationRequest request = CardNotificationRequest.builder()
                .userId(userId)
                .cardId(card.getId())
                .message(String.format("Your card has been funded with %s %s", amount, card.getCurrency()))
                .data(Map.of(
                    "amount", amount.toPlainString(),
                    "currency", card.getCurrency(),
                    "newBalance", card.getBalance().toPlainString()
                ))
                .channels(new NotificationChannel[]{NotificationChannel.PUSH})
                .priority(NotificationPriority.NORMAL)
                .build();

            NotificationResponse response = notificationServiceClient.sendCardFundedNotification(request);
            logNotificationResult(response, "card funded");

        } catch (Exception e) {
            log.error("Failed to send card funded notification", e);
        }
    }

    /**
     * Send card withdrawal notification
     */
    public void sendCardWithdrawalNotification(String userId, VirtualCard card, BigDecimal amount) {
        try {
            CardNotificationRequest request = CardNotificationRequest.builder()
                .userId(userId)
                .cardId(card.getId())
                .message(String.format("Withdrawal of %s %s from your card", amount, card.getCurrency()))
                .data(Map.of(
                    "amount", amount.toPlainString(),
                    "currency", card.getCurrency(),
                    "newBalance", card.getBalance().toPlainString()
                ))
                .channels(new NotificationChannel[]{NotificationChannel.PUSH})
                .priority(NotificationPriority.NORMAL)
                .build();

            NotificationResponse response = notificationServiceClient.sendCardWithdrawalNotification(request);
            logNotificationResult(response, "card withdrawal");

        } catch (Exception e) {
            log.error("Failed to send card withdrawal notification", e);
        }
    }

    /**
     * Send transaction declined notification
     */
    public void sendTransactionDeclinedNotification(String userId, VirtualCard card, CardTransaction transaction) {
        try {
            TransactionNotificationRequest request = TransactionNotificationRequest.builder()
                .userId(userId)
                .cardId(card.getId())
                .transactionId(transaction.getId())
                .amount(transaction.getAmount().toPlainString())
                .currency(transaction.getCurrency())
                .merchantName(transaction.getMerchantName())
                .message(String.format("Transaction declined: %s %s at %s. Reason: %s",
                    transaction.getAmount(), transaction.getCurrency(),
                    transaction.getMerchantName(), transaction.getDeclineReason()))
                .data(buildTransactionData(transaction))
                .channels(new NotificationChannel[]{
                    NotificationChannel.PUSH,
                    NotificationChannel.SMS
                })
                .priority(NotificationPriority.HIGH)
                .build();

            NotificationResponse response = notificationServiceClient.sendTransactionDeclinedNotification(request);
            logNotificationResult(response, "transaction declined");

        } catch (Exception e) {
            log.error("Failed to send transaction declined notification", e);
        }
    }

    /**
     * Send fraud alert notification
     */
    public void sendFraudAlert(String userId, VirtualCard card, Object webhook) {
        try {
            log.error("FRAUD ALERT: Sending fraud alert to user {}", userId);

            FraudAlertRequest request = FraudAlertRequest.builder()
                .userId(userId)
                .cardId(card.getId())
                .transactionId("FRAUD_DETECTED")
                .fraudReason("Suspicious transaction detected")
                .riskScore("HIGH")
                .data(Map.of("timestamp", java.time.Instant.now().toString()))
                .channels(new NotificationChannel[]{
                    NotificationChannel.PUSH,
                    NotificationChannel.SMS,
                    NotificationChannel.EMAIL
                })
                .build();

            NotificationResponse response = notificationServiceClient.sendFraudAlert(request);
            logNotificationResult(response, "fraud alert");

        } catch (Exception e) {
            log.error("CRITICAL: Failed to send fraud alert notification", e);
        }
    }

    /**
     * Send card closed notification
     */
    public void sendCardClosedNotification(String userId, VirtualCard card) {
        try {
            CardNotificationRequest request = CardNotificationRequest.builder()
                .userId(userId)
                .cardId(card.getId())
                .message("Your virtual card has been closed")
                .data(buildCardData(card))
                .channels(new NotificationChannel[]{
                    NotificationChannel.PUSH,
                    NotificationChannel.EMAIL
                })
                .priority(NotificationPriority.NORMAL)
                .build();

            NotificationResponse response = notificationServiceClient.sendCardClosedNotification(request);
            logNotificationResult(response, "card closed");

        } catch (Exception e) {
            log.error("Failed to send card closed notification", e);
        }
    }

    /**
     * Send card expired notification
     */
    public void sendCardExpiredNotification(String userId, VirtualCard card) {
        try {
            CardNotificationRequest request = CardNotificationRequest.builder()
                .userId(userId)
                .cardId(card.getId())
                .message("Your virtual card has expired")
                .data(buildCardData(card))
                .channels(new NotificationChannel[]{
                    NotificationChannel.PUSH,
                    NotificationChannel.EMAIL
                })
                .priority(NotificationPriority.NORMAL)
                .build();

            NotificationResponse response = notificationServiceClient.sendCardExpiredNotification(request);
            logNotificationResult(response, "card expired");

        } catch (Exception e) {
            log.error("Failed to send card expired notification", e);
        }
    }

    // Helper methods

    private Map<String, Object> buildCardData(VirtualCard card) {
        Map<String, Object> data = new HashMap<>();
        data.put("cardId", card.getId());
        data.put("lastFourDigits", card.getMaskedCardNumber());
        data.put("cardType", card.getCardType().toString());
        data.put("status", card.getStatus().toString());
        return data;
    }

    private Map<String, Object> buildTransactionData(CardTransaction transaction) {
        Map<String, Object> data = new HashMap<>();
        data.put("transactionId", transaction.getId());
        data.put("amount", transaction.getAmount().toPlainString());
        data.put("currency", transaction.getCurrency());
        data.put("merchantName", transaction.getMerchantName());
        data.put("merchantCategory", transaction.getMerchantCategory());
        data.put("status", transaction.getStatus().toString());
        return data;
    }

    private void logNotificationResult(NotificationResponse response, String notificationType) {
        if (response.isSuccess()) {
            log.info("Successfully sent {} notification. NotificationId: {}",
                notificationType, response.getNotificationId());
        } else {
            log.warn("Failed to send {} notification: {} - {}",
                notificationType, response.getErrorCode(), response.getMessage());
        }
    }
}
