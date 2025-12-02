package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.crypto.pricing.CryptoPricingService;
import com.waqiti.crypto.service.CryptoTransactionService;
import com.waqiti.crypto.entity.CryptoCurrency;
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
 * Critical Event Consumer #273: Crypto Price Alert Event Consumer
 * Processes price alerts and market notifications with regulatory compliance
 * Implements 12-step zero-tolerance processing for price alerts
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoPriceAlertEventConsumer extends BaseKafkaConsumer {

    private final CryptoPricingService pricingService;
    private final CryptoTransactionService cryptoTransactionService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "crypto-price-alert-events", groupId = "crypto-price-alert-group")
    @CircuitBreaker(name = "crypto-price-alert-consumer")
    @Retry(name = "crypto-price-alert-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCryptoPriceAlertEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "crypto-price-alert-event");
        
        try {
            log.info("Step 1: Processing crypto price alert event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String userId = eventData.path("userId").asText();
            String cryptoCurrency = eventData.path("cryptoCurrency").asText();
            BigDecimal targetPrice = new BigDecimal(eventData.path("targetPrice").asText());
            String alertType = eventData.path("alertType").asText();
            String condition = eventData.path("condition").asText();
            BigDecimal currentPrice = new BigDecimal(eventData.path("currentPrice").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted price alert details: userId={}, crypto={}, target={}, current={}", 
                    userId, cryptoCurrency, targetPrice, currentPrice);
            
            // Step 3: Price alert validation
            boolean validated = pricingService.validatePriceAlert(userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), targetPrice, 
                    alertType, condition, timestamp);
            if (!validated) {
                log.error("Step 3: Price alert validation failed for userId={}", userId);
                pricingService.rejectAlert(eventId, "VALIDATION_FAILED", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Price alert validation successful");
            
            // Step 4: Market manipulation detection
            boolean manipulationDetected = pricingService.detectMarketManipulation(
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), currentPrice, 
                    targetPrice, timestamp);
            if (manipulationDetected) {
                log.warn("Step 4: Market manipulation detected, alert may be delayed");
                pricingService.flagManipulatedPrice(eventId, cryptoCurrency, currentPrice, timestamp);
            } else {
                log.info("Step 4: No market manipulation detected");
            }
            
            // Step 5: Price condition evaluation
            boolean conditionMet = pricingService.evaluatePriceCondition(currentPrice, 
                    targetPrice, condition, timestamp);
            if (!conditionMet) {
                log.info("Step 5: Price condition not yet met: current={}, target={}, condition={}", 
                        currentPrice, targetPrice, condition);
                pricingService.updateAlertStatus(eventId, "MONITORING", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 5: Price condition met, triggering alert");
            
            // Step 6: Portfolio impact assessment
            BigDecimal portfolioValue = cryptoTransactionService.getUserPortfolioValue(userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), timestamp);
            BigDecimal priceImpact = pricingService.calculatePriceImpact(portfolioValue, 
                    currentPrice, targetPrice, timestamp);
            log.info("Step 6: Portfolio impact calculated: portfolioValue={}, impact={}", 
                    portfolioValue, priceImpact);
            
            // Step 7: Automated trading trigger evaluation
            if ("AUTOMATED_TRADE".equals(alertType)) {
                boolean tradeExecuted = cryptoTransactionService.evaluateAutomatedTrade(userId, 
                        cryptoCurrency, currentPrice, condition, timestamp);
                if (tradeExecuted) {
                    log.info("Step 7: Automated trade triggered");
                } else {
                    log.info("Step 7: Automated trade conditions not met");
                }
            } else {
                log.info("Step 7: Manual alert type, no automated trading");
            }
            
            // Step 8: Risk management assessment
            int riskLevel = pricingService.assessAlertRiskLevel(userId, cryptoCurrency, 
                    currentPrice, targetPrice, priceImpact, timestamp);
            if (riskLevel > 7) {
                pricingService.escalateHighRiskAlert(eventId, userId, riskLevel, timestamp);
                log.warn("Step 8: High risk alert escalated: level={}", riskLevel);
            } else {
                log.info("Step 8: Risk level acceptable: {}", riskLevel);
            }
            
            // Step 9: Alert notification delivery
            String notificationId = pricingService.sendPriceAlert(userId, eventId, cryptoCurrency, 
                    currentPrice, targetPrice, condition, priceImpact, timestamp);
            log.info("Step 9: Price alert notification sent: notificationId={}", notificationId);
            
            // Step 10: Alert analytics and tracking
            pricingService.recordAlertAnalytics(eventId, userId, cryptoCurrency, currentPrice, 
                    targetPrice, alertType, priceImpact, timestamp);
            log.info("Step 10: Alert analytics recorded");
            
            // Step 11: Follow-up alert scheduling
            if ("RECURRING".equals(eventData.path("frequency").asText())) {
                pricingService.scheduleFollowUpAlert(userId, cryptoCurrency, targetPrice, 
                        alertType, condition, timestamp);
                log.info("Step 11: Follow-up alert scheduled");
            } else {
                pricingService.deactivateAlert(eventId, timestamp);
                log.info("Step 11: One-time alert deactivated");
            }
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed crypto price alert: eventId={}, notificationId={}", 
                    eventId, notificationId);
            
        } catch (Exception e) {
            log.error("Error processing crypto price alert event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("userId") || 
            !eventData.has("cryptoCurrency") || !eventData.has("targetPrice") || !eventData.has("currentPrice")) {
            throw new IllegalArgumentException("Invalid crypto price alert event structure");
        }
    }
}