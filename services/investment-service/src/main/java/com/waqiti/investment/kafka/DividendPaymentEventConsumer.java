package com.waqiti.investment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.investment.service.DividendProcessingService;
import com.waqiti.investment.service.TaxWithholdingService;
import com.waqiti.investment.entity.DividendPayment;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Critical Event Consumer #169: Dividend Payment Event Consumer
 * Processes dividend distributions with tax withholding and regulatory reporting
 * Implements 12-step zero-tolerance processing for equity income distribution
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DividendPaymentEventConsumer extends BaseKafkaConsumer {

    private final DividendProcessingService dividendProcessingService;
    private final TaxWithholdingService taxWithholdingService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    @KafkaListener(topics = "dividend-payment-events", groupId = "dividend-payment-group")
    @CircuitBreaker(name = "dividend-payment-consumer")
    @Retry(name = "dividend-payment-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleDividendPaymentEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "dividend-payment-event");
        
        try {
            log.info("Step 1: Processing dividend payment event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String dividendId = eventData.path("dividendId").asText();
            String holdingId = eventData.path("holdingId").asText();
            String accountId = eventData.path("accountId").asText();
            String cusip = eventData.path("cusip").asText();
            BigDecimal sharesOwned = new BigDecimal(eventData.path("sharesOwned").asText());
            BigDecimal dividendPerShare = new BigDecimal(eventData.path("dividendPerShare").asText());
            LocalDate exDividendDate = LocalDate.parse(eventData.path("exDividendDate").asText());
            LocalDate paymentDate = LocalDate.parse(eventData.path("paymentDate").asText());
            String dividendType = eventData.path("dividendType").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());

            // CRITICAL SECURITY: Idempotency check - prevent duplicate dividend payments
            String idempotencyKey = "dividend-payment:" + dividendId + ":" + holdingId + ":" + eventId;
            UUID operationId = UUID.randomUUID();

            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate dividend payment event ignored: dividendId={}, holdingId={}, eventId={}",
                        dividendId, holdingId, eventId);
                ack.acknowledge();
                return;
            }

            BigDecimal grossDividend = sharesOwned.multiply(dividendPerShare);

            log.info("Step 2: Extracted dividend details: dividendId={}, gross={}, shares={}",
                    dividendId, grossDividend, sharesOwned);
            
            boolean eligible = dividendProcessingService.verifyOwnershipEligibility(
                    holdingId, accountId, sharesOwned, exDividendDate, timestamp);
            
            if (!eligible) {
                log.warn("Step 3: Not eligible for dividend: holdingId={}", holdingId);
                ack.acknowledge();
                return;
            }
            
            log.info("Step 3: Verified dividend eligibility");
            
            BigDecimal taxWithheld = taxWithholdingService.calculateTaxWithholding(
                    accountId, grossDividend, dividendType, timestamp);
            
            log.info("Step 4: Calculated tax withholding: amount={}", taxWithheld);
            
            BigDecimal netDividend = grossDividend.subtract(taxWithheld);
            
            DividendPayment payment = dividendProcessingService.createDividendPayment(
                    dividendId, holdingId, accountId, cusip, sharesOwned, 
                    dividendPerShare, grossDividend, taxWithheld, netDividend, 
                    dividendType, paymentDate, timestamp);
            
            log.info("Step 5: Created dividend payment record");
            
            dividendProcessingService.creditAccount(accountId, netDividend, dividendId, timestamp);
            log.info("Step 6: Credited account with net dividend: amount={}", netDividend);
            
            dividendProcessingService.updatePortfolioIncome(accountId, netDividend, timestamp);
            log.info("Step 7: Updated portfolio income tracking");
            
            taxWithholdingService.record1099DIVEntry(accountId, cusip, grossDividend, 
                    taxWithheld, dividendType, timestamp);
            log.info("Step 8: Recorded 1099-DIV tax entry");
            
            if ("REINVEST".equals(dividendType)) {
                dividendProcessingService.reinvestDividend(accountId, holdingId, 
                        netDividend, timestamp);
                log.info("Step 9: Reinvested dividend in shares");
            }
            
            dividendProcessingService.sendPaymentNotification(accountId, cusip, 
                    netDividend, dividendId, timestamp);
            log.info("Step 10: Sent dividend payment notification");
            
            dividendProcessingService.updateYieldMetrics(accountId, holdingId,
                    dividendPerShare, timestamp);
            log.info("Step 11: Updated yield metrics");

            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("dividendId", dividendId, "holdingId", holdingId,
                       "accountId", accountId, "cusip", cusip,
                       "grossDividend", grossDividend.toString(),
                       "taxWithheld", taxWithheld.toString(),
                       "netDividend", netDividend.toString(),
                       "dividendType", dividendType, "status", "PAID"), Duration.ofDays(7));

            ack.acknowledge();
            log.info("Step 12: Successfully processed dividend payment: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("SECURITY: Error processing dividend payment event: {}", e.getMessage(), e);
            // CRITICAL SECURITY: Mark operation as failed for retry logic
            String idempotencyKey = "dividend-payment:" + record.key();
            idempotencyService.failOperation(idempotencyKey, UUID.randomUUID(), e.getMessage());
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("dividendId") || 
            !eventData.has("holdingId") || !eventData.has("sharesOwned")) {
            throw new IllegalArgumentException("Invalid dividend payment event structure");
        }
    }
}