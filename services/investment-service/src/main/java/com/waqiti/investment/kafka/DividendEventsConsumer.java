package com.waqiti.investment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.investment.service.DividendService;
import com.waqiti.investment.service.PortfolioService;
import com.waqiti.investment.service.TaxService;
import com.waqiti.common.exception.InvestmentProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DividendEventsConsumer {
    
    private final DividendService dividendService;
    private final PortfolioService portfolioService;
    private final TaxService taxService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;
    
    @KafkaListener(
        topics = {"dividend-events", "dividend-declared", "dividend-paid", "dividend-reinvested"},
        groupId = "investment-service-dividend-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000)
    )
    @Transactional
    public void handleDividendEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID dividendId = null;
        UUID accountId = null;
        String eventType = null;
        String idempotencyKey = null;
        UUID operationId = UUID.randomUUID();

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            dividendId = UUID.fromString((String) event.get("dividendId"));
            accountId = UUID.fromString((String) event.get("accountId"));
            eventType = (String) event.get("eventType");
            String symbol = (String) event.get("symbol");
            BigDecimal dividendAmount = new BigDecimal((String) event.get("dividendAmount"));
            BigDecimal sharesOwned = new BigDecimal((String) event.get("sharesOwned"));
            LocalDateTime payDate = LocalDateTime.parse((String) event.get("payDate"));
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));

            // CRITICAL SECURITY: Idempotency check - prevent duplicate dividend processing
            idempotencyKey = String.format("dividend-event:%s:%s:%s:%s",
                dividendId, accountId, eventType, timestamp);

            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate dividend event ignored: dividendId={}, accountId={}, eventType={}, idempotencyKey={}",
                        dividendId, accountId, eventType, idempotencyKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("SECURITY: Processing new dividend event with persistent idempotency: dividendId={}, accountId={}, eventType={}, symbol={}, idempotencyKey={}",
                dividendId, accountId, eventType, symbol, idempotencyKey);
            
            switch (eventType) {
                case "DIVIDEND_DECLARED":
                    dividendService.processDividendDeclaration(dividendId, accountId, symbol,
                            dividendAmount, sharesOwned, payDate, timestamp);
                    break;
                case "DIVIDEND_PAID":
                    dividendService.processDividendPayment(dividendId, accountId, symbol,
                            dividendAmount, sharesOwned, timestamp);
                    break;
                case "DIVIDEND_REINVESTED":
                    dividendService.processDividendReinvestment(dividendId, accountId, symbol,
                            dividendAmount, timestamp);
                    break;
                default:
                    dividendService.processGenericDividendEvent(dividendId, eventType, event, timestamp);
            }
            
            // Update portfolio
            portfolioService.updateDividendIncome(accountId, symbol, dividendAmount, timestamp);
            
            // Tax implications
            taxService.recordDividendTax(accountId, dividendId, dividendAmount, timestamp);

            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("dividendId", dividendId.toString(),
                       "accountId", accountId.toString(),
                       "eventType", eventType,
                       "symbol", symbol,
                       "dividendAmount", dividendAmount.toString(),
                       "sharesOwned", sharesOwned.toString(),
                       "status", "COMPLETED"), Duration.ofDays(7));

            auditService.auditFinancialEvent(
                    "DIVIDEND_EVENT_PROCESSED",
                    accountId.toString(),
                    String.format("Dividend event processed - Type: %s, Symbol: %s, Amount: %s",
                            eventType, symbol, dividendAmount),
                    Map.of(
                            "dividendId", dividendId.toString(),
                            "accountId", accountId.toString(),
                            "eventType", eventType,
                            "symbol", symbol,
                            "dividendAmount", dividendAmount.toString(),
                            "sharesOwned", sharesOwned.toString(),
                            "idempotencyKey", idempotencyKey
                    )
            );

            acknowledgment.acknowledge();
            log.info("Successfully processed dividend event - DividendId: {}, EventType: {}",
                    dividendId, eventType);
            
        } catch (Exception e) {
            log.error("SECURITY: Dividend event processing failed - DividendId: {}, AccountId: {}, Error: {}",
                    dividendId, accountId, e.getMessage(), e);

            // CRITICAL SECURITY: Mark operation as failed for retry logic
            if (idempotencyKey != null) {
                idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            }

            throw new InvestmentProcessingException("Dividend event processing failed", e);
        }
    }
}