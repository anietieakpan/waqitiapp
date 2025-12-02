package com.waqiti.card.service;

import com.waqiti.card.entity.Card;
import com.waqiti.card.entity.CardAuthorization;
import com.waqiti.card.entity.CardTransaction;
import com.waqiti.common.notification.NotificationService;
import com.waqiti.common.notification.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * CardNotificationService - Handles all card-related notifications
 *
 * Provides notification capabilities for:
 * - Card lifecycle events (creation, activation, blocking, replacement)
 * - Transaction notifications (authorizations, declines)
 * - Security alerts (fraud detection, PIN changes)
 * - Statement generation
 * - Limit changes
 *
 * Integrates with common notification service for email, SMS, and push notifications
 *
 * @author Waqiti Engineering Team
 * @version 2.0
 * @since 2025-11-19
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CardNotificationService {

    private final NotificationService notificationService;

    /**
     * Send card creation notification to user
     *
     * @param userId User ID
     * @param card Card details
     * @param email User's email address
     * @param phoneNumber User's phone number
     */
    @Async
    public CompletableFuture<NotificationResult> sendCardCreatedNotification(
            UUID userId,
            Card card,
            String email,
            String phoneNumber) {

        log.info("Sending card creation notification to user: {}", userId);

        Map<String, Object> variables = new HashMap<>();
        variables.put("cardType", card.getCardType());
        variables.put("cardLastFour", card.getCardNumberLastFour());
        variables.put("cardHolderName", card.getCardHolderName());
        variables.put("expiryDate", card.getExpiryDate());

        // Send multi-channel notification (email + SMS + push)
        MultiChannelNotificationRequest request = MultiChannelNotificationRequest.builder()
                .userId(userId.toString())
                .channels(java.util.List.of(
                        NotificationChannel.EMAIL,
                        NotificationChannel.SMS,
                        NotificationChannel.PUSH
                ))
                .email(email)
                .phoneNumber(phoneNumber)
                .templateId("CARD_CREATED")
                .variables(variables)
                .priority(NotificationPriority.HIGH)
                .build();

        return notificationService.sendMultiChannel(request)
                .thenApply(MultiChannelNotificationResult::getOverallResult)
                .exceptionally(ex -> {
                    log.error("Failed to send card creation notification for user: {}", userId, ex);
                    return NotificationResult.builder()
                            .success(false)
                            .message("Notification failed: " + ex.getMessage())
                            .build();
                });
    }

    /**
     * Send card activation notification
     *
     * @param userId User ID
     * @param card Card details
     * @param email User's email
     * @param phoneNumber User's phone number
     */
    @Async
    public CompletableFuture<NotificationResult> sendCardActivatedNotification(
            UUID userId,
            Card card,
            String email,
            String phoneNumber) {

        log.info("Sending card activation notification for card: {}", card.getCardId());

        Map<String, Object> variables = new HashMap<>();
        variables.put("cardLastFour", card.getCardNumberLastFour());
        variables.put("activationTime", LocalDateTime.now());
        variables.put("cardType", card.getCardType());

        MultiChannelNotificationRequest request = MultiChannelNotificationRequest.builder()
                .userId(userId.toString())
                .channels(java.util.List.of(
                        NotificationChannel.EMAIL,
                        NotificationChannel.SMS,
                        NotificationChannel.PUSH
                ))
                .email(email)
                .phoneNumber(phoneNumber)
                .templateId("CARD_ACTIVATED")
                .variables(variables)
                .priority(NotificationPriority.HIGH)
                .build();

        return notificationService.sendMultiChannel(request)
                .thenApply(MultiChannelNotificationResult::getOverallResult);
    }

    /**
     * Send card blocked/frozen notification
     *
     * @param userId User ID
     * @param card Card details
     * @param reason Reason for blocking
     * @param email User's email
     * @param phoneNumber User's phone number
     */
    @Async
    public CompletableFuture<NotificationResult> sendCardBlockedNotification(
            UUID userId,
            Card card,
            String reason,
            String email,
            String phoneNumber) {

        log.info("Sending card blocked notification for card: {}", card.getCardId());

        Map<String, Object> variables = new HashMap<>();
        variables.put("cardLastFour", card.getCardNumberLastFour());
        variables.put("reason", reason);
        variables.put("timestamp", LocalDateTime.now());

        // High priority security notification
        MultiChannelNotificationRequest request = MultiChannelNotificationRequest.builder()
                .userId(userId.toString())
                .channels(java.util.List.of(
                        NotificationChannel.EMAIL,
                        NotificationChannel.SMS,
                        NotificationChannel.PUSH
                ))
                .email(email)
                .phoneNumber(phoneNumber)
                .templateId("CARD_BLOCKED")
                .variables(variables)
                .priority(NotificationPriority.CRITICAL)
                .build();

        return notificationService.sendMultiChannel(request)
                .thenApply(MultiChannelNotificationResult::getOverallResult);
    }

    /**
     * Send transaction authorization notification
     *
     * @param userId User ID
     * @param authorization Authorization details
     * @param phoneNumber User's phone number for SMS
     */
    @Async
    public CompletableFuture<NotificationResult> sendAuthorizationNotification(
            UUID userId,
            CardAuthorization authorization,
            String phoneNumber) {

        log.info("Sending authorization notification for auth: {}", authorization.getAuthorizationId());

        Map<String, Object> variables = new HashMap<>();
        variables.put("amount", authorization.getAuthorizationAmount());
        variables.put("currency", authorization.getCurrency());
        variables.put("merchant", authorization.getMerchantName());
        variables.put("timestamp", authorization.getAuthorizationTime());
        variables.put("status", authorization.getAuthorizationStatus());

        // SMS notification for quick alerts
        SmsNotificationRequest request = SmsNotificationRequest.builder()
                .userId(userId.toString())
                .phoneNumber(phoneNumber)
                .templateId("CARD_AUTHORIZATION")
                .variables(variables)
                .priority(NotificationPriority.HIGH)
                .build();

        return notificationService.sendSms(request);
    }

    /**
     * Send transaction decline notification
     *
     * @param userId User ID
     * @param authorization Declined authorization
     * @param phoneNumber User's phone number
     */
    @Async
    public CompletableFuture<NotificationResult> sendDeclineNotification(
            UUID userId,
            CardAuthorization authorization,
            String phoneNumber) {

        log.info("Sending decline notification for auth: {}", authorization.getAuthorizationId());

        Map<String, Object> variables = new HashMap<>();
        variables.put("amount", authorization.getAuthorizationAmount());
        variables.put("merchant", authorization.getMerchantName());
        variables.put("reason", authorization.getDeclineReason());
        variables.put("timestamp", LocalDateTime.now());

        SmsNotificationRequest request = SmsNotificationRequest.builder()
                .userId(userId.toString())
                .phoneNumber(phoneNumber)
                .templateId("CARD_DECLINE")
                .variables(variables)
                .priority(NotificationPriority.MEDIUM)
                .build();

        return notificationService.sendSms(request);
    }

    /**
     * Send fraud alert notification
     *
     * @param userId User ID
     * @param card Card details
     * @param transaction Suspicious transaction
     * @param riskScore Risk score
     * @param email User's email
     * @param phoneNumber User's phone number
     */
    @Async
    public CompletableFuture<NotificationResult> sendFraudAlertNotification(
            UUID userId,
            Card card,
            CardTransaction transaction,
            BigDecimal riskScore,
            String email,
            String phoneNumber) {

        log.warn("Sending fraud alert for card: {}, transaction: {}, risk score: {}",
                 card.getCardId(), transaction.getTransactionId(), riskScore);

        Map<String, Object> variables = new HashMap<>();
        variables.put("cardLastFour", card.getCardNumberLastFour());
        variables.put("amount", transaction.getAmount());
        variables.put("merchant", transaction.getMerchantName());
        variables.put("location", transaction.getMerchantCity() + ", " + transaction.getMerchantCountry());
        variables.put("riskScore", riskScore);
        variables.put("timestamp", transaction.getTransactionTime());

        // Critical security alert - all channels
        MultiChannelNotificationRequest request = MultiChannelNotificationRequest.builder()
                .userId(userId.toString())
                .channels(java.util.List.of(
                        NotificationChannel.EMAIL,
                        NotificationChannel.SMS,
                        NotificationChannel.PUSH
                ))
                .email(email)
                .phoneNumber(phoneNumber)
                .templateId("FRAUD_ALERT")
                .variables(variables)
                .priority(NotificationPriority.CRITICAL)
                .build();

        return notificationService.sendMultiChannel(request)
                .thenApply(MultiChannelNotificationResult::getOverallResult);
    }

    /**
     * Send PIN change notification
     *
     * @param userId User ID
     * @param card Card details
     * @param email User's email
     * @param phoneNumber User's phone number
     */
    @Async
    public CompletableFuture<NotificationResult> sendPinChangeNotification(
            UUID userId,
            Card card,
            String email,
            String phoneNumber) {

        log.info("Sending PIN change notification for card: {}", card.getCardId());

        Map<String, Object> variables = new HashMap<>();
        variables.put("cardLastFour", card.getCardNumberLastFour());
        variables.put("timestamp", LocalDateTime.now());

        // Security notification
        MultiChannelNotificationRequest request = MultiChannelNotificationRequest.builder()
                .userId(userId.toString())
                .channels(java.util.List.of(
                        NotificationChannel.EMAIL,
                        NotificationChannel.SMS
                ))
                .email(email)
                .phoneNumber(phoneNumber)
                .templateId("PIN_CHANGED")
                .variables(variables)
                .priority(NotificationPriority.HIGH)
                .build();

        return notificationService.sendMultiChannel(request)
                .thenApply(MultiChannelNotificationResult::getOverallResult);
    }

    /**
     * Send card statement ready notification
     *
     * @param userId User ID
     * @param card Card details
     * @param statementPeriod Statement period
     * @param outstandingBalance Outstanding balance
     * @param email User's email
     */
    @Async
    public CompletableFuture<NotificationResult> sendStatementNotification(
            UUID userId,
            Card card,
            String statementPeriod,
            BigDecimal outstandingBalance,
            String email) {

        log.info("Sending statement notification for card: {}", card.getCardId());

        Map<String, Object> variables = new HashMap<>();
        variables.put("cardLastFour", card.getCardNumberLastFour());
        variables.put("statementPeriod", statementPeriod);
        variables.put("outstandingBalance", outstandingBalance);
        variables.put("currency", card.getCurrency());

        EmailNotificationRequest request = EmailNotificationRequest.builder()
                .userId(userId.toString())
                .email(email)
                .templateId("STATEMENT_READY")
                .variables(variables)
                .priority(NotificationPriority.MEDIUM)
                .build();

        return notificationService.sendEmail(request);
    }

    /**
     * Send card limit change notification
     *
     * @param userId User ID
     * @param card Card details
     * @param oldLimit Old credit limit
     * @param newLimit New credit limit
     * @param email User's email
     * @param phoneNumber User's phone number
     */
    @Async
    public CompletableFuture<NotificationResult> sendLimitChangeNotification(
            UUID userId,
            Card card,
            BigDecimal oldLimit,
            BigDecimal newLimit,
            String email,
            String phoneNumber) {

        log.info("Sending limit change notification for card: {}", card.getCardId());

        Map<String, Object> variables = new HashMap<>();
        variables.put("cardLastFour", card.getCardNumberLastFour());
        variables.put("oldLimit", oldLimit);
        variables.put("newLimit", newLimit);
        variables.put("currency", card.getCurrency());
        variables.put("timestamp", LocalDateTime.now());

        MultiChannelNotificationRequest request = MultiChannelNotificationRequest.builder()
                .userId(userId.toString())
                .channels(java.util.List.of(
                        NotificationChannel.EMAIL,
                        NotificationChannel.PUSH
                ))
                .email(email)
                .templateId("LIMIT_CHANGED")
                .variables(variables)
                .priority(NotificationPriority.MEDIUM)
                .build();

        return notificationService.sendMultiChannel(request)
                .thenApply(MultiChannelNotificationResult::getOverallResult);
    }

    /**
     * Send card replacement notification
     *
     * @param userId User ID
     * @param oldCard Old card details
     * @param newCard New card details
     * @param email User's email
     * @param phoneNumber User's phone number
     */
    @Async
    public CompletableFuture<NotificationResult> sendCardReplacementNotification(
            UUID userId,
            Card oldCard,
            Card newCard,
            String email,
            String phoneNumber) {

        log.info("Sending card replacement notification for user: {}", userId);

        Map<String, Object> variables = new HashMap<>();
        variables.put("oldCardLastFour", oldCard.getCardNumberLastFour());
        variables.put("newCardLastFour", newCard.getCardNumberLastFour());
        variables.put("estimatedDelivery", LocalDateTime.now().plusDays(7));

        MultiChannelNotificationRequest request = MultiChannelNotificationRequest.builder()
                .userId(userId.toString())
                .channels(java.util.List.of(
                        NotificationChannel.EMAIL,
                        NotificationChannel.SMS,
                        NotificationChannel.PUSH
                ))
                .email(email)
                .phoneNumber(phoneNumber)
                .templateId("CARD_REPLACEMENT")
                .variables(variables)
                .priority(NotificationPriority.HIGH)
                .build();

        return notificationService.sendMultiChannel(request)
                .thenApply(MultiChannelNotificationResult::getOverallResult);
    }

    /**
     * Send card expiring soon notification
     *
     * @param userId User ID
     * @param card Card details
     * @param email User's email
     */
    @Async
    public CompletableFuture<NotificationResult> sendCardExpiringNotification(
            UUID userId,
            Card card,
            String email) {

        log.info("Sending card expiring notification for card: {}", card.getCardId());

        Map<String, Object> variables = new HashMap<>();
        variables.put("cardLastFour", card.getCardNumberLastFour());
        variables.put("expiryDate", card.getExpiryDate());

        EmailNotificationRequest request = EmailNotificationRequest.builder()
                .userId(userId.toString())
                .email(email)
                .templateId("CARD_EXPIRING")
                .variables(variables)
                .priority(NotificationPriority.MEDIUM)
                .build();

        return notificationService.sendEmail(request);
    }
}
