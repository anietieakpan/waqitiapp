package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.crypto.staking.WaqitiStakingService;
import com.waqiti.crypto.compliance.ComplianceService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #270: Crypto Staking Event Consumer
 * Processes proof-of-stake rewards with tax compliance
 * Implements 12-step zero-tolerance processing for crypto staking
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoStakingEventConsumer extends BaseKafkaConsumer {

    private final WaqitiStakingService stakingService;
    private final ComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "crypto-staking-events", groupId = "crypto-staking-group")
    @CircuitBreaker(name = "crypto-staking-consumer")
    @Retry(name = "crypto-staking-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCryptoStakingEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "crypto-staking-event");
        
        try {
            log.info("Step 1: Processing crypto staking event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String userId = eventData.path("userId").asText();
            String cryptoCurrency = eventData.path("cryptoCurrency").asText();

            // CRITICAL SECURITY: Idempotency check
            String idempotencyKey = "crypto-staking:" + userId + ":" + cryptoCurrency + ":" + eventId;
            UUID operationId = UUID.randomUUID();
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate crypto staking event ignored: userId={}, eventId={}", userId, eventId);
                ack.acknowledge();
                return;
            }
            BigDecimal stakingAmount = new BigDecimal(eventData.path("stakingAmount").asText());
            String stakingAction = eventData.path("stakingAction").asText();
            String validatorAddress = eventData.path("validatorAddress").asText();
            int lockupPeriod = eventData.path("lockupPeriod").asInt();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted staking details: userId={}, crypto={}, amount={}, action={}", 
                    userId, cryptoCurrency, stakingAmount, stakingAction);
            
            // Step 3: Staking eligibility validation
            boolean eligible = stakingService.validateStakingEligibility(userId, 
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), stakingAmount, timestamp);
            if (!eligible) {
                log.error("Step 3: Staking eligibility failed for userId={}", userId);
                stakingService.rejectStaking(eventId, "NOT_ELIGIBLE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Staking eligibility validated");
            
            // Step 4: Validator reputation and security check
            boolean validatorSafe = stakingService.validateValidatorSecurity(validatorAddress, 
                    cryptoCurrency, timestamp);
            if (!validatorSafe) {
                log.error("Step 4: Validator security check failed");
                stakingService.rejectStaking(eventId, "UNSAFE_VALIDATOR", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 4: Validator security validated");
            
            // Step 5: Slashing risk assessment
            BigDecimal slashingRisk = stakingService.assessSlashingRisk(validatorAddress, 
                    cryptoCurrency, stakingAmount, timestamp);
            if (slashingRisk.compareTo(new BigDecimal("0.05")) > 0) {
                log.warn("Step 5: High slashing risk detected: {}", slashingRisk);
                stakingService.requireRiskAcknowledgment(eventId, userId, slashingRisk, timestamp);
            } else {
                log.info("Step 5: Slashing risk acceptable: {}", slashingRisk);
            }
            
            // Step 6: Execute staking transaction
            String stakingTransactionId;
            if ("STAKE".equals(stakingAction)) {
                stakingTransactionId = stakingService.executeStaking(eventId, userId, 
                        CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), stakingAmount, 
                        validatorAddress, lockupPeriod, timestamp);
            } else {
                stakingTransactionId = stakingService.executeUnstaking(eventId, userId, 
                        CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), stakingAmount, 
                        validatorAddress, timestamp);
            }
            log.info("Step 6: Staking transaction executed: transactionId={}", stakingTransactionId);
            
            // Step 7: Lockup period management
            if ("STAKE".equals(stakingAction) && lockupPeriod > 0) {
                stakingService.implementLockupRestrictions(userId, stakingTransactionId, 
                        lockupPeriod, timestamp);
                log.info("Step 7: Lockup restrictions implemented: {} days", lockupPeriod);
            } else {
                log.info("Step 7: No lockup restrictions required");
            }
            
            // Step 8: Reward calculation setup
            BigDecimal expectedAPY = stakingService.calculateExpectedAPY(
                    CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), validatorAddress, timestamp);
            stakingService.setupRewardCalculation(stakingTransactionId, stakingAmount, 
                    expectedAPY, timestamp);
            log.info("Step 8: Reward calculation setup: expectedAPY={}", expectedAPY);
            
            // Step 9: Tax basis recording for staking
            if ("STAKE".equals(stakingAction)) {
                BigDecimal stakingCostBasis = stakingService.getCurrentMarketValue(
                        CryptoCurrency.valueOf(cryptoCurrency.toUpperCase()), stakingAmount);
                complianceService.recordStakingTaxBasis(userId, cryptoCurrency, stakingAmount, 
                        stakingCostBasis, timestamp);
                log.info("Step 9: Staking tax basis recorded: {}", stakingCostBasis);
            } else {
                log.info("Step 9: Unstaking - no new tax basis required");
            }
            
            // Step 10: Network delegation
            stakingService.delegateToValidator(stakingTransactionId, validatorAddress, 
                    cryptoCurrency, stakingAmount, timestamp);
            log.info("Step 10: Network delegation completed");
            
            // Step 11: Staking confirmation and monitoring setup
            stakingService.sendStakingConfirmation(userId, stakingTransactionId, stakingAction,
                    cryptoCurrency, stakingAmount, expectedAPY, timestamp);
            stakingService.setupStakingMonitoring(stakingTransactionId, validatorAddress, timestamp);
            log.info("Step 11: Staking confirmation sent and monitoring setup");

            // CRITICAL SECURITY: Mark completed
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("userId", userId, "cryptoCurrency", cryptoCurrency, "stakingAmount", stakingAmount.toString(),
                       "stakingAction", stakingAction, "status", "COMPLETED"), Duration.ofDays(7));

            ack.acknowledge();
            log.info("Step 12: Successfully processed crypto staking: eventId={}, transactionId={}",
                    eventId, stakingTransactionId);

        } catch (Exception e) {
            log.error("SECURITY: Error processing crypto staking event: {}", e.getMessage(), e);
            String idempotencyKey = "crypto-staking:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("userId") || 
            !eventData.has("cryptoCurrency") || !eventData.has("stakingAmount") || !eventData.has("stakingAction")) {
            throw new IllegalArgumentException("Invalid crypto staking event structure");
        }
    }
}