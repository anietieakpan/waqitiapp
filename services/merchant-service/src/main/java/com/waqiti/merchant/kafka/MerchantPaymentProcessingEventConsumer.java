package com.waqiti.merchant.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.merchant.service.MerchantPaymentService;
import com.waqiti.merchant.service.MerchantRiskService;
import com.waqiti.merchant.domain.MerchantPayment;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Critical Event Consumer #275: Merchant Payment Processing Event Consumer
 * Processes point-of-sale transactions with fraud detection
 * Implements 12-step zero-tolerance processing for merchant payments
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MerchantPaymentProcessingEventConsumer extends BaseKafkaConsumer {

    private final MerchantPaymentService paymentService;
    private final MerchantRiskService riskService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "merchant-payment-processing-events", groupId = "merchant-payment-processing-group")
    @CircuitBreaker(name = "merchant-payment-processing-consumer")
    @Retry(name = "merchant-payment-processing-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleMerchantPaymentProcessingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "merchant-payment-processing-event");
        
        try {
            log.info("Step 1: Processing merchant payment event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String merchantId = eventData.path("merchantId").asText();
            String customerId = eventData.path("customerId").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String paymentMethodId = eventData.path("paymentMethodId").asText();
            String transactionType = eventData.path("transactionType").asText();
            String merchantLocationId = eventData.path("merchantLocationId").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted payment details: merchantId={}, customerId={}, amount={}", 
                    merchantId, customerId, amount);
            
            // Step 3: Merchant status and eligibility validation
            boolean merchantActive = paymentService.validateMerchantStatus(merchantId, timestamp);
            if (!merchantActive) {
                log.error("Step 3: Merchant not active or suspended");
                paymentService.rejectPayment(eventId, "MERCHANT_INACTIVE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Merchant status validated");
            
            // Step 4: Real-time fraud detection
            int fraudScore = riskService.calculatePaymentFraudScore(merchantId, customerId, 
                    amount, paymentMethodId, merchantLocationId, timestamp);
            if (fraudScore > 800) {
                log.error("Step 4: High fraud score detected: {}", fraudScore);
                paymentService.blockPayment(eventId, "HIGH_FRAUD_RISK", fraudScore, timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Fraud score acceptable: {}", fraudScore);
            
            // Step 5: Payment method verification
            boolean paymentMethodValid = paymentService.verifyPaymentMethod(paymentMethodId, 
                    customerId, amount, timestamp);
            if (!paymentMethodValid) {
                log.error("Step 5: Payment method verification failed");
                paymentService.rejectPayment(eventId, "INVALID_PAYMENT_METHOD", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 5: Payment method verified");
            
            // Step 6: Transaction limits validation
            boolean withinLimits = paymentService.validateTransactionLimits(merchantId, 
                    amount, transactionType, timestamp);
            if (!withinLimits) {
                log.error("Step 6: Transaction limits exceeded");
                paymentService.rejectPayment(eventId, "LIMIT_EXCEEDED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 6: Transaction limits validated");
            
            // Step 7: Payment authorization
            String authorizationCode = paymentService.authorizePayment(eventId, merchantId, 
                    customerId, amount, paymentMethodId, timestamp);
            log.info("Step 7: Payment authorized: authCode={}", authorizationCode);
            
            // Step 8: Payment processing and settlement
            MerchantPayment payment = paymentService.processPayment(eventId, merchantId, 
                    customerId, amount, paymentMethodId, authorizationCode, 
                    merchantLocationId, timestamp);
            log.info("Step 8: Payment processed: paymentId={}", payment.getId());
            
            // Step 9: Fee calculation and deduction
            BigDecimal processingFee = paymentService.calculateProcessingFee(merchantId, 
                    amount, transactionType, timestamp);
            BigDecimal netAmount = amount.subtract(processingFee);
            paymentService.applyMerchantFees(payment.getId(), processingFee, netAmount, timestamp);
            log.info("Step 9: Fees calculated: fee={}, net={}", processingFee, netAmount);
            
            // Step 10: Inventory and business logic integration
            paymentService.updateMerchantInventory(merchantId, payment.getId(), 
                    eventData.path("items").toString(), timestamp);
            log.info("Step 10: Merchant inventory updated");
            
            // Step 11: Receipt generation and delivery
            String receiptId = paymentService.generateDigitalReceipt(payment.getId(), 
                    merchantId, customerId, amount, timestamp);
            paymentService.deliverReceipt(receiptId, customerId, eventData.path("customerEmail").asText(), 
                    timestamp);
            log.info("Step 11: Receipt generated and delivered: receiptId={}", receiptId);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed merchant payment: eventId={}, paymentId={}", 
                    eventId, payment.getId());
            
        } catch (Exception e) {
            log.error("Error processing merchant payment event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("merchantId") || 
            !eventData.has("customerId") || !eventData.has("amount")) {
            throw new IllegalArgumentException("Invalid merchant payment processing event structure");
        }
    }
}