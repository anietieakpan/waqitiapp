package com.waqiti.investment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.investment.service.MarginAccountService;
import com.waqiti.investment.service.RiskManagementService;
import com.waqiti.investment.entity.MarginCall;
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
import java.util.UUID;

/**
 * Critical Event Consumer #171: Margin Call Event Consumer
 * Processes margin calls with Reg T and FINRA compliance
 * Implements 12-step zero-tolerance processing for margin requirement enforcement
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarginCallEventConsumer extends BaseKafkaConsumer {

    private final MarginAccountService marginAccountService;
    private final RiskManagementService riskManagementService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "margin-call-events", groupId = "margin-call-group")
    @CircuitBreaker(name = "margin-call-consumer")
    @Retry(name = "margin-call-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleMarginCallEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "margin-call-event");
        
        try {
            log.info("Step 1: Processing margin call event: partition={}, offset={}",
                    record.partition(), record.offset());

            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);

            String eventId = eventData.path("eventId").asText();
            String marginCallId = eventData.path("marginCallId").asText();

            // CRITICAL SECURITY: Idempotency check - prevent duplicate margin call processing
            String idempotencyKey = "margin-call:" + marginCallId + ":" + eventId;
            if (!idempotencyService.startOperation(idempotencyKey, UUID.randomUUID(), Duration.ofHours(72))) {
                log.warn("SECURITY: Duplicate margin call event ignored: marginCallId={}, eventId={}",
                        marginCallId, eventId);
                ack.acknowledge();
                return;
            }
            String accountId = eventData.path("accountId").asText();
            BigDecimal accountEquity = new BigDecimal(eventData.path("accountEquity").asText());
            BigDecimal marginRequirement = new BigDecimal(eventData.path("marginRequirement").asText());
            BigDecimal deficiency = new BigDecimal(eventData.path("deficiency").asText());
            String callType = eventData.path("callType").asText();
            LocalDateTime callDeadline = LocalDateTime.parse(eventData.path("callDeadline").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted margin call details: accountId={}, deficiency={}, type={}", 
                    accountId, deficiency, callType);
            
            MarginCall marginCall = marginAccountService.createMarginCall(
                    marginCallId, accountId, accountEquity, marginRequirement, 
                    deficiency, callType, callDeadline, timestamp);
            
            log.info("Step 3: Created margin call record");
            
            marginAccountService.restrictAccountTrading(accountId, "MARGIN_CALL", timestamp);
            log.info("Step 4: Restricted account trading pending margin satisfaction");
            
            boolean regTCompliant = marginAccountService.verifyRegTCompliance(
                    accountId, marginRequirement, timestamp);
            
            log.info("Step 5: Reg T compliance verified: {}", regTCompliant);
            
            marginAccountService.sendMarginCallNotification(accountId, marginCallId, 
                    deficiency, callDeadline, timestamp);
            log.info("Step 6: Sent margin call notification to customer");
            
            riskManagementService.assessLiquidationRisk(accountId, accountEquity, 
                    marginRequirement, timestamp);
            log.info("Step 7: Assessed liquidation risk");
            
            if ("HOUSE_CALL".equals(callType)) {
                marginAccountService.calculateHouseRequirement(accountId, timestamp);
                log.info("Step 8: Calculated house margin requirement");
            } else if ("FED_CALL".equals(callType)) {
                marginAccountService.enforceFederalRequirement(accountId, timestamp);
                log.info("Step 8: Enforcing federal margin requirement (Reg T)");
            } else {
                log.info("Step 8: Standard margin call processing");
            }
            
            marginAccountService.monitorDepositActivity(accountId, deficiency, 
                    callDeadline, timestamp);
            log.info("Step 9: Monitoring for margin deposit");
            
            marginAccountService.prepareForceClosureOrders(accountId, deficiency, timestamp);
            log.info("Step 10: Prepared contingent force-closure orders");
            
            marginAccountService.reportToFINRA(marginCallId, accountId, deficiency,
                    callType, timestamp);
            log.info("Step 11: Reported margin call to FINRA");

            // CRITICAL SECURITY: Mark operation as completed in idempotency service
            idempotencyService.completeOperation(idempotencyKey, UUID.randomUUID(), marginCallId, Duration.ofHours(72));

            ack.acknowledge();
            log.info("Step 12: Successfully processed margin call: eventId={}", eventId);

        } catch (Exception e) {
            log.error("Error processing margin call event: {}", e.getMessage(), e);
            // CRITICAL SECURITY: Mark operation as failed for retry logic
            String idempotencyKey = "margin-call:" + record.key() + ":" + record.offset();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("marginCallId") || 
            !eventData.has("accountId") || !eventData.has("deficiency")) {
            throw new IllegalArgumentException("Invalid margin call event structure");
        }
    }
}