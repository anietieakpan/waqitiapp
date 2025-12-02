package com.waqiti.notification.kafka;

import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.common.metrics.MetricsCollector;
import com.waqiti.notification.domain.NotificationChannel;
import com.waqiti.notification.domain.NotificationPriority;
import com.waqiti.notification.domain.NotificationType;
import com.waqiti.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * CRITICAL FIX #9: InvestmentOrderFilledConsumer
 * Notifies investors when their orders are executed
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InvestmentOrderFilledConsumer {
    private final NotificationService notificationService;
    private final IdempotencyService idempotencyService;
    private final MetricsCollector metricsCollector;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "investment.order.filled", groupId = "notification-investment-filled")
    public void handle(OrderFilledEvent event, Acknowledgment ack) {
        try {
            String key = "investment:filled:" + event.getOrderId();
            if (!idempotencyService.tryAcquire(key, Duration.ofHours(24))) {
                ack.acknowledge();
                return;
            }

            String message = String.format("""
                Your investment order has been executed!

                Symbol: %s
                Quantity: %s shares
                Price: $%s per share
                Total: $%s
                Order Type: %s
                Executed At: %s

                View your portfolio in the app for complete details.
                """, event.getSymbol(), event.getQuantity(), event.getPrice(),
                event.getTotalAmount(), event.getOrderType(), event.getExecutedAt());

            notificationService.sendNotification(event.getInvestorId(), NotificationType.INVESTMENT_ORDER_FILLED,
                NotificationChannel.EMAIL, NotificationPriority.MEDIUM, "Investment Order Executed", message, Map.of());

            notificationService.sendNotification(event.getInvestorId(), NotificationType.INVESTMENT_ORDER_FILLED,
                NotificationChannel.PUSH, NotificationPriority.MEDIUM, "Order Filled",
                String.format("Your order for %s shares of %s has been filled at $%s",
                    event.getQuantity(), event.getSymbol(), event.getPrice()), Map.of());

            metricsCollector.incrementCounter("notification.investment.filled.sent");
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process order filled event", e);
            dlqHandler.sendToDLQ("investment.order.filled", event, e, "Processing failed");
            ack.acknowledge();
        }
    }

    private static class OrderFilledEvent {
        private UUID orderId, investorId;
        private String symbol, orderType;
        private BigDecimal quantity, price, totalAmount;
        private LocalDateTime executedAt;
        public UUID getOrderId() { return orderId; }
        public UUID getInvestorId() { return investorId; }
        public String getSymbol() { return symbol; }
        public String getOrderType() { return orderType; }
        public BigDecimal getQuantity() { return quantity; }
        public BigDecimal getPrice() { return price; }
        public BigDecimal getTotalAmount() { return totalAmount; }
        public LocalDateTime getExecutedAt() { return executedAt; }
    }
}
