package com.waqiti.billingorchestrator.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Billing Notification Service
 * Sends notifications for billing events
 * Migrated from billing-service
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillingNotificationService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String NOTIFICATION_TOPIC = "notification-events";

    public void sendOverdueNotification(UUID userId, UUID merchantId, UUID billId,
                                       BigDecimal totalAmountDue, String message) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "BILL_OVERDUE");
            notification.put("userId", userId.toString());
            notification.put("merchantId", merchantId != null ? merchantId.toString() : null);
            notification.put("billId", billId.toString());
            notification.put("totalAmountDue", totalAmountDue.toString());
            notification.put("message", message);
            notification.put("priority", "HIGH");
            notification.put("channel", "EMAIL,SMS,PUSH");

            kafkaTemplate.send(NOTIFICATION_TOPIC, userId.toString(), notification);

            log.info("Sent overdue notification: userId={}, billId={}, amount={}",
                    userId, billId, totalAmountDue);
        } catch (Exception e) {
            log.error("Failed to send overdue notification", e);
        }
    }

    public void sendPaymentFailedNotification(UUID userId, UUID billId, String failureReason) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "PAYMENT_FAILED");
            notification.put("userId", userId.toString());
            notification.put("billId", billId.toString());
            notification.put("reason", failureReason);
            notification.put("priority", "HIGH");
            notification.put("channel", "EMAIL,SMS");

            kafkaTemplate.send(NOTIFICATION_TOPIC, userId.toString(), notification);

            log.info("Sent payment failed notification: userId={}, billId={}", userId, billId);
        } catch (Exception e) {
            log.error("Failed to send payment failed notification", e);
        }
    }

    public void sendSubscriptionRenewalNotification(UUID userId, UUID subscriptionId,
                                                   BigDecimal amount, String nextBillingDate) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "SUBSCRIPTION_RENEWAL");
            notification.put("userId", userId.toString());
            notification.put("subscriptionId", subscriptionId.toString());
            notification.put("amount", amount.toString());
            notification.put("nextBillingDate", nextBillingDate);
            notification.put("priority", "MEDIUM");
            notification.put("channel", "EMAIL");

            kafkaTemplate.send(NOTIFICATION_TOPIC, userId.toString(), notification);

            log.info("Sent subscription renewal notification: userId={}, subscriptionId={}",
                    userId, subscriptionId);
        } catch (Exception e) {
            log.error("Failed to send subscription renewal notification", e);
        }
    }
}
