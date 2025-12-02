package com.waqiti.wallet.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.kafka.dlq.UniversalDLQHandler;
import com.waqiti.wallet.service.WalletService;
import com.waqiti.wallet.service.WalletLimitService;
import com.waqiti.wallet.service.WalletComplianceService;
import com.waqiti.wallet.dto.WalletLimits;
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
 * Critical Event Consumer #263: Wallet Limit Management Event Consumer
 * Processes transaction limits and velocity checks with regulatory compliance
 * Implements 12-step zero-tolerance processing for limit management
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WalletLimitManagementEventConsumer extends BaseKafkaConsumer {

    private final WalletService walletService;
    private final WalletLimitService limitService;
    private final WalletComplianceService complianceService;
    private final ObjectMapper objectMapper;
    private final UniversalDLQHandler dlqHandler;

    @KafkaListener(topics = "wallet-limit-management-events", groupId = "wallet-limit-management-group")
    @CircuitBreaker(name = "wallet-limit-management-consumer")
    @Retry(name = "wallet-limit-management-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleWalletLimitManagementEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "wallet-limit-management-event");
        
        try {
            log.info("Step 1: Processing wallet limit management event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String walletId = eventData.path("walletId").asText();
            String limitType = eventData.path("limitType").asText();
            BigDecimal newLimit = new BigDecimal(eventData.path("newLimit").asText());
            String changeReason = eventData.path("changeReason").asText();
            String requestedBy = eventData.path("requestedBy").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted limit management details: walletId={}, limitType={}, newLimit={}", 
                    walletId, limitType, newLimit);
            
            // Step 3: Limit change authorization validation
            boolean authorized = limitService.validateLimitChangeAuthorization(walletId, limitType, 
                    newLimit, requestedBy, timestamp);
            if (!authorized) {
                log.error("Step 3: Limit change authorization failed for walletId={}", walletId);
                limitService.rejectLimitChange(eventId, "UNAUTHORIZED_CHANGE", timestamp);
                ack.acknowledge();
                return;
            }
            log.info("Step 3: Limit change authorization validated");
            
            // Step 4: Regulatory compliance check for limit increases
            if (limitService.isLimitIncrease(walletId, limitType, newLimit)) {
                boolean compliant = complianceService.validateLimitIncreaseCompliance(walletId, 
                        limitType, newLimit, timestamp);
                if (!compliant) {
                    log.error("Step 4: Regulatory compliance failed for limit increase");
                    limitService.escalateForApproval(eventId, "COMPLIANCE_REVIEW_REQUIRED", timestamp);
                    ack.acknowledge();
                    return;
                }
            }
            log.info("Step 4: Regulatory compliance validated");
            
            // Step 5: KYC level verification for high limits
            if (newLimit.compareTo(new BigDecimal("50000")) > 0) {
                boolean kycSufficient = complianceService.validateKYCForHighLimits(walletId, 
                        limitType, newLimit, timestamp);
                if (!kycSufficient) {
                    log.error("Step 5: KYC insufficient for high limit request");
                    limitService.requireAdditionalKYC(eventId, walletId, timestamp);
                    ack.acknowledge();
                    return;
                }
            }
            log.info("Step 5: KYC verification completed");
            
            // Step 6: Risk assessment for limit changes
            int riskScore = complianceService.assessLimitChangeRisk(walletId, limitType, 
                    newLimit, changeReason, timestamp);
            if (riskScore > 700) {
                log.warn("Step 6: High risk score for limit change: {}", riskScore);
                limitService.applyTemporaryRestrictions(walletId, eventId, timestamp);
            } else {
                log.info("Step 6: Risk assessment passed: score={}", riskScore);
            }
            
            // Step 7: Velocity pattern analysis
            boolean velocityNormal = complianceService.analyzeVelocityForLimitChange(walletId, 
                    limitType, timestamp);
            if (!velocityNormal) {
                log.warn("Step 7: Abnormal velocity patterns detected");
                limitService.implementCoolingPeriod(walletId, limitType, timestamp);
            } else {
                log.info("Step 7: Velocity patterns normal");
            }
            
            // Step 8: Execute limit change with audit trail
            WalletLimits updatedLimits = limitService.updateWalletLimits(walletId, limitType, 
                    newLimit, changeReason, requestedBy, timestamp);
            log.info("Step 8: Wallet limits updated successfully");
            
            // Step 9: Transaction queue re-evaluation
            limitService.reevaluatePendingTransactions(walletId, limitType, newLimit, timestamp);
            log.info("Step 9: Pending transactions re-evaluated");
            
            // Step 10: Customer notification
            limitService.sendLimitChangeNotification(walletId, limitType, newLimit, 
                    changeReason, timestamp);
            log.info("Step 10: Limit change notification sent");
            
            // Step 11: Monitoring rule updates
            limitService.updateMonitoringRules(walletId, updatedLimits, timestamp);
            log.info("Step 11: Monitoring rules updated");
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed wallet limit management: eventId={}, walletId={}", 
                    eventId, walletId);
            
        } catch (Exception e) {
            log.error("Error processing wallet limit management event: partition={}, offset={}, error={}",
                    record.partition(), record.offset(), e.getMessage(), e);

            dlqHandler.handleFailedMessage(record, e)
                .thenAccept(result -> log.info("Wallet limit management message sent to DLQ: offset={}, destination={}, category={}",
                        record.offset(), result.getDestinationTopic(), result.getFailureCategory()))
                .exceptionally(dlqError -> {
                    log.error("CRITICAL: DLQ handling failed for wallet limit management event - MESSAGE MAY BE LOST! " +
                            "partition={}, offset={}, error={}",
                            record.partition(), record.offset(), dlqError.getMessage(), dlqError);
                    return null;
                });

            throw new RuntimeException("Wallet limit management event processing failed", e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("walletId") || 
            !eventData.has("limitType") || !eventData.has("newLimit")) {
            throw new IllegalArgumentException("Invalid wallet limit management event structure");
        }
    }
}