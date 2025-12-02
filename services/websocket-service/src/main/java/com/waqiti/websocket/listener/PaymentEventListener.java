package com.waqiti.websocket.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.websocket.dto.NotificationMessage;
import com.waqiti.websocket.dto.PaymentStatusUpdate;
import com.waqiti.websocket.service.NotificationBroadcastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final NotificationBroadcastService notificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "payment-events", groupId = "websocket-service")
    public void handlePaymentEvent(String paymentEventJson) {
        try {
            Map<String, Object> event = objectMapper.readValue(paymentEventJson, Map.class);
            String eventType = (String) event.get("eventType");
            
            log.debug("Received payment event: {}", eventType);
            
            switch (eventType) {
                case "PAYMENT_RECEIVED":
                    handlePaymentReceived(event);
                    break;
                case "PAYMENT_SENT":
                    handlePaymentSent(event);
                    break;
                case "PAYMENT_REQUEST_CREATED":
                    handlePaymentRequestCreated(event);
                    break;
                case "PAYMENT_STATUS_UPDATED":
                    handlePaymentStatusUpdated(event);
                    break;
                default:
                    log.debug("Unhandled payment event type: {}", eventType);
            }
        } catch (Exception e) {
            log.error("Error processing payment event", e);
        }
    }

    private void handlePaymentReceived(Map<String, Object> event) {
        String recipientId = (String) event.get("recipientId");
        String senderId = (String) event.get("senderId");
        String amount = (String) event.get("amount");
        String currency = (String) event.get("currency");
        
        NotificationMessage notification = NotificationMessage.builder()
            .type("PAYMENT_RECEIVED")
            .title("Payment Received")
            .message(String.format("You received %s %s from %s", amount, currency, senderId))
            .data(event)
            .timestamp(Instant.now())
            .priority("HIGH")
            .read(false)
            .build();
        
        notificationService.broadcastNotification(recipientId, notification);
    }

    private void handlePaymentSent(Map<String, Object> event) {
        String senderId = (String) event.get("senderId");
        String recipientId = (String) event.get("recipientId");
        String amount = (String) event.get("amount");
        String currency = (String) event.get("currency");
        
        NotificationMessage notification = NotificationMessage.builder()
            .type("PAYMENT_SENT")
            .title("Payment Sent")
            .message(String.format("You sent %s %s to %s", amount, currency, recipientId))
            .data(event)
            .timestamp(Instant.now())
            .priority("MEDIUM")
            .read(false)
            .build();
        
        notificationService.broadcastNotification(senderId, notification);
    }

    private void handlePaymentRequestCreated(Map<String, Object> event) {
        String requesterId = (String) event.get("requesterId");
        String requesteeId = (String) event.get("requesteeId");
        String amount = (String) event.get("amount");
        String currency = (String) event.get("currency");
        
        NotificationMessage notification = NotificationMessage.builder()
            .type("PAYMENT_REQUEST")
            .title("Payment Request")
            .message(String.format("%s is requesting %s %s", requesterId, amount, currency))
            .data(event)
            .timestamp(Instant.now())
            .priority("HIGH")
            .read(false)
            .build();
        
        notificationService.broadcastNotification(requesteeId, notification);
    }

    private void handlePaymentStatusUpdated(Map<String, Object> event) {
        String paymentId = (String) event.get("paymentId");
        String status = (String) event.get("status");
        String userId = (String) event.get("userId");
        
        PaymentStatusUpdate update = PaymentStatusUpdate.builder()
            .paymentId(paymentId)
            .status(status)
            .timestamp(Instant.now())
            .build();
        
        NotificationMessage notification = NotificationMessage.builder()
            .type("PAYMENT_STATUS_UPDATED")
            .title("Payment Update")
            .message(String.format("Payment status updated to: %s", status))
            .data(Map.of("paymentUpdate", update))
            .timestamp(Instant.now())
            .priority("MEDIUM")
            .read(false)
            .build();
        
        notificationService.broadcastNotification(userId, notification);
    }
}