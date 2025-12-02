package com.waqiti.rewards.kafka;

import com.waqiti.common.events.PaymentCompletedEvent;
import com.waqiti.rewards.service.RewardsCalculationService;
import com.waqiti.rewards.service.CashbackService;
import com.waqiti.rewards.service.LoyaltyPointsService;
import com.waqiti.rewards.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;

/**
 * CRITICAL Missing Consumer: Payment Completed Event
 *
 * Processes completed payments to award rewards, cashback, and loyalty points.
 * This consumer was identified as ORPHANED in the comprehensive audit.
 *
 * Business Impact: Without this consumer, users don't receive rewards for completed payments
 * Priority: P1 - HIGH (customer satisfaction, revenue impact)
 *
 * Awards:
 * - Cashback based on merchant category and payment amount
 * - Loyalty points for transaction volume
 * - Bonus rewards for special promotions
 * - Referral bonuses if applicable
 *
 * @author Waqiti Rewards Team
 * @version 1.0
 * @since 2025-10-17
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentCompletedEventConsumer {

    private final RewardsCalculationService rewardsCalculationService;
    private final CashbackService cashbackService;
    private final LoyaltyPointsService loyaltyPointsService;
    private final ProcessedEventRepository processedEventRepository;

    @KafkaListener(
        topics = "payment.completed",
        groupId = "rewards-service-payment-completed",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void handlePaymentCompleted(PaymentCompletedEvent event, Acknowledgment ack) {
        log.info("Processing payment completed for rewards: paymentId={}, userId={}, amount={}",
            event.getPaymentId(), event.getUserId(), event.getAmount());

        try {
            // Idempotency check
            String idempotencyKey = generateIdempotencyKey(event);
            if (processedEventRepository.existsByIdempotencyKey(idempotencyKey)) {
                log.info("Payment already processed for rewards: {}", event.getPaymentId());
                ack.acknowledge();
                return;
            }

            // Calculate cashback (e.g., 1-5% based on merchant category)
            BigDecimal cashbackAmount = cashbackService.calculateCashback(
                event.getUserId(),
                event.getMerchantId(),
                event.getAmount(),
                event.getMerchantCategory()
            );

            if (cashbackAmount.compareTo(BigDecimal.ZERO) > 0) {
                cashbackService.awardCashback(
                    event.getUserId(),
                    event.getPaymentId(),
                    cashbackAmount,
                    event.getCurrency(),
                    String.format("Cashback for payment %s at %s",
                        event.getPaymentId(), event.getMerchantName())
                );

                log.info("Cashback awarded: userId={}, paymentId={}, amount={}",
                    event.getUserId(), event.getPaymentId(), cashbackAmount);
            }

            // Award loyalty points (e.g., 1 point per $1 spent)
            int loyaltyPoints = loyaltyPointsService.calculatePoints(
                event.getAmount(),
                event.getMerchantCategory()
            );

            if (loyaltyPoints > 0) {
                loyaltyPointsService.awardPoints(
                    event.getUserId(),
                    event.getPaymentId(),
                    loyaltyPoints,
                    String.format("Purchase at %s", event.getMerchantName())
                );

                log.info("Loyalty points awarded: userId={}, paymentId={}, points={}",
                    event.getUserId(), event.getPaymentId(), loyaltyPoints);
            }

            // Check for bonus rewards (promotions, first purchase, etc.)
            rewardsCalculationService.processBonusRewards(
                event.getUserId(),
                event.getPaymentId(),
                event.getAmount(),
                event.getMerchantId()
            );

            // Mark event as processed
            processedEventRepository.save(createProcessedEvent(idempotencyKey, event));

            // Acknowledge successful processing
            ack.acknowledge();

            log.info("Payment completed event processed successfully for rewards: paymentId={}, " +
                "cashback={}, points={}", event.getPaymentId(), cashbackAmount, loyaltyPoints);

        } catch (Exception e) {
            log.error("Failed to process payment completed event for rewards: paymentId={}",
                event.getPaymentId(), e);
            // Don't acknowledge - let Kafka retry
            throw new RuntimeException("Rewards processing failed", e);
        }
    }

    private String generateIdempotencyKey(PaymentCompletedEvent event) {
        return String.format("payment-completed:%s:%s",
            event.getPaymentId(), event.getCompletedAt().toString());
    }

    private com.waqiti.rewards.model.ProcessedEvent createProcessedEvent(
        String idempotencyKey, PaymentCompletedEvent event) {

        return com.waqiti.rewards.model.ProcessedEvent.builder()
            .idempotencyKey(idempotencyKey)
            .eventType("PAYMENT_COMPLETED")
            .eventId(event.getPaymentId())
            .userId(event.getUserId())
            .processedAt(java.time.LocalDateTime.now())
            .build();
    }
}
