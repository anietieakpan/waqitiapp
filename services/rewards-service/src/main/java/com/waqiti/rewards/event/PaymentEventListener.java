package com.waqiti.rewards.event;

import com.waqiti.common.events.payment.*;
import com.waqiti.rewards.dto.ProcessCashbackRequest;
import com.waqiti.rewards.dto.ProcessPointsRequest;
import com.waqiti.rewards.service.RewardsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final RewardsService rewardsService;

    @KafkaListener(topics = "payment-completed", groupId = "rewards-service")
    @Transactional
    public void handlePaymentCompletedEvent(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        log.info("Processing rewards for completed payment: {}", event.getPaymentId());
        
        try {
            // Process cashback if eligible
            if (isEligibleForCashback(event)) {
                ProcessCashbackRequest cashbackRequest = ProcessCashbackRequest.builder()
                        .userId(event.getUserId())
                        .transactionId(event.getTransactionId())
                        .merchantId(event.getMerchantId())
                        .merchantName(event.getMerchantName())
                        .merchantCategory(event.getMerchantCategory())
                        .transactionAmount(event.getAmount())
                        .currency(event.getCurrency())
                        .metadata(buildMetadata(event))
                        .build();
                
                rewardsService.processCashback(cashbackRequest);
            }
            
            // Process points
            ProcessPointsRequest pointsRequest = ProcessPointsRequest.builder()
                    .userId(event.getUserId())
                    .transactionId(event.getTransactionId())
                    .transactionAmount(event.getAmount())
                    .currency(event.getCurrency())
                    .merchantId(event.getMerchantId())
                    .merchantName(event.getMerchantName())
                    .merchantCategory(event.getMerchantCategory())
                    .metadata(buildMetadata(event))
                    .build();
            
            rewardsService.processPoints(pointsRequest);
            
            // Check for special campaigns
            checkAndApplySpecialCampaigns(event);
            
            // Acknowledge message after successful processing
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing rewards for payment: {}", event.getPaymentId(), e);
            // Don't acknowledge - let Kafka retry
            throw new RewardsProcessingException("Failed to process rewards", e);
        }
    }

    @KafkaListener(topics = "payment-refunded", groupId = "rewards-service")
    @Transactional
    public void handlePaymentRefundedEvent(
            @Payload PaymentRefundedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        log.info("Processing rewards reversal for refunded payment: {}", event.getPaymentId());
        
        try {
            // Reverse cashback
            rewardsService.reverseCashback(event.getPaymentId(), event.getRefundId());
            
            // Reverse points
            rewardsService.reversePoints(event.getTransactionId(), event.getRefundId());
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error reversing rewards for refund: {}", event.getRefundId(), e);
            throw new RewardsProcessingException("Failed to reverse rewards", e);
        }
    }

    @KafkaListener(topics = "transfer-completed", groupId = "rewards-service")
    @Transactional
    public void handleTransferCompletedEvent(
            @Payload TransferCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        log.info("Processing rewards for completed transfer: {}", event.getTransferId());
        
        try {
            // P2P transfers might have different rewards rules
            if (isEligibleForTransferRewards(event)) {
                ProcessPointsRequest pointsRequest = ProcessPointsRequest.builder()
                        .userId(event.getSenderId())
                        .transactionId(event.getTransferId())
                        .transactionAmount(event.getAmount())
                        .currency(event.getCurrency())
                        .merchantCategory("P2P_TRANSFER")
                        .metadata(Map.of("recipientId", event.getRecipientId()))
                        .build();
                
                rewardsService.processPoints(pointsRequest);
            }
            
            // Check for referral bonuses
            checkReferralBonus(event);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing rewards for transfer: {}", event.getTransferId(), e);
            throw new RewardsProcessingException("Failed to process transfer rewards", e);
        }
    }

    @KafkaListener(topics = "bill-payment-completed", groupId = "rewards-service")
    @Transactional
    public void handleBillPaymentCompletedEvent(
            @Payload BillPaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            Acknowledgment acknowledgment) {
        log.info("Processing rewards for bill payment: {}", event.getBillPaymentId());
        
        try {
            // Bill payments might have special cashback rates
            ProcessCashbackRequest cashbackRequest = ProcessCashbackRequest.builder()
                    .userId(event.getUserId())
                    .transactionId(event.getBillPaymentId())
                    .merchantId(event.getBillerId())
                    .merchantName(event.getBillerName())
                    .merchantCategory(event.getBillCategory())
                    .transactionAmount(event.getAmount())
                    .currency(event.getCurrency())
                    .metadata(Map.of(
                        "paymentMethod", "BILL_PAYMENT",
                        "billCategory", event.getBillCategory()
                    ))
                    .build();
            
            rewardsService.processCashback(cashbackRequest);
            
            // Process points with bill payment multiplier
            ProcessPointsRequest pointsRequest = ProcessPointsRequest.builder()
                    .userId(event.getUserId())
                    .transactionId(event.getBillPaymentId())
                    .transactionAmount(event.getAmount())
                    .currency(event.getCurrency())
                    .merchantCategory(event.getBillCategory())
                    .metadata(Map.of("transactionType", "BILL_PAYMENT"))
                    .build();
            
            rewardsService.processPoints(pointsRequest);
            
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing rewards for bill payment: {}", event.getBillPaymentId(), e);
            throw new RewardsProcessingException("Failed to process bill payment rewards", e);
        }
    }

    private boolean isEligibleForCashback(PaymentCompletedEvent event) {
        // Check if payment method and merchant are eligible
        return event.getMerchantId() != null && 
               !event.getPaymentMethod().equals("REWARDS_REDEMPTION") &&
               event.getAmount().compareTo(BigDecimal.ZERO) > 0;
    }

    private boolean isEligibleForTransferRewards(TransferCompletedEvent event) {
        // P2P transfers might have minimum amount for rewards
        BigDecimal minAmount = new BigDecimal("10.00");
        return event.getAmount().compareTo(minAmount) >= 0;
    }

    private String determineTransactionType(PaymentCompletedEvent event) {
        if (event.getMerchantId() != null) {
            return "MERCHANT_PAYMENT";
        } else if (event.getPaymentMethod().equals("QR_CODE")) {
            return "QR_PAYMENT";
        } else {
            return "STANDARD_PAYMENT";
        }
    }

    private void checkAndApplySpecialCampaigns(PaymentCompletedEvent event) {
        try {
            // Check for active campaigns that might provide bonus rewards
            rewardsService.applyActiveCampaigns(
                event.getUserId(),
                event.getPaymentId(),
                event.getMerchantId(),
                event.getAmount()
            );
        } catch (Exception e) {
            log.warn("Failed to apply special campaigns for payment: {}", event.getPaymentId(), e);
            // Don't fail the main processing for campaign failures
        }
    }

    private void checkReferralBonus(TransferCompletedEvent event) {
        try {
            // Check if this is a first transfer to a new user (referral)
            rewardsService.checkAndApplyReferralBonus(
                event.getSenderId(),
                event.getRecipientId(),
                event.getTransferId()
            );
        } catch (Exception e) {
            log.warn("Failed to check referral bonus for transfer: {}", event.getTransferId(), e);
        }
    }

    private Map<String, String> buildMetadata(PaymentCompletedEvent event) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("paymentId", event.getPaymentId());
        metadata.put("paymentMethod", event.getPaymentMethod());
        metadata.put("transactionDate", event.getTransactionDate().toString());
        if (event.getMetadata() != null) {
            metadata.putAll(event.getMetadata());
        }
        return metadata;
    }

    public static class RewardsProcessingException extends RuntimeException {
        public RewardsProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}