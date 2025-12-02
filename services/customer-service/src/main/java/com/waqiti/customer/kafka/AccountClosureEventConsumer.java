package com.waqiti.customer.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.customer.service.AccountClosureService;
import com.waqiti.customer.service.ComplianceNotificationService;
import com.waqiti.customer.entity.AccountClosure;
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
 * Critical Event Consumer #128: Account Closure Event Consumer
 * Processes account closure requests with full regulatory compliance and fund disbursement
 * Implements 12-step zero-tolerance processing for account termination workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountClosureEventConsumer extends BaseKafkaConsumer {

    private final AccountClosureService accountClosureService;
    private final ComplianceNotificationService complianceNotificationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "account-closure-events", groupId = "account-closure-group")
    @CircuitBreaker(name = "account-closure-consumer")
    @Retry(name = "account-closure-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleAccountClosureEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "account-closure-event");
        
        try {
            log.info("Step 1: Processing account closure event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String accountId = eventData.path("accountId").asText();
            String customerId = eventData.path("customerId").asText();
            String closureReason = eventData.path("closureReason").asText();
            String closureType = eventData.path("closureType").asText();
            String disbursementMethod = eventData.path("disbursementMethod").asText();
            LocalDateTime requestDate = LocalDateTime.parse(eventData.path("requestDate").asText());
            
            log.info("Step 2: Extracted closure details: accountId={}, reason={}, type={}", 
                    accountId, closureReason, closureType);
            
            // Step 3: Validate account status and eligibility for closure
            boolean eligible = accountClosureService.validateClosureEligibility(accountId, closureType);
            
            if (!eligible) {
                log.error("Step 3: Account not eligible for closure: accountId={}", accountId);
                accountClosureService.rejectClosureRequest(accountId, "NOT_ELIGIBLE", requestDate);
                ack.acknowledge();
                return;
            }
            
            // Step 4: Check for pending transactions
            boolean pendingTransactions = accountClosureService.checkPendingTransactions(accountId);
            
            if (pendingTransactions) {
                log.warn("Step 4: Pending transactions exist, delaying closure: accountId={}", accountId);
                accountClosureService.scheduleDelayedClosure(accountId, requestDate);
                ack.acknowledge();
                return;
            }
            
            // Step 5: Calculate final balance and accrued interest
            BigDecimal finalBalance = accountClosureService.calculateFinalBalance(accountId, requestDate);
            BigDecimal accruedInterest = accountClosureService.calculateAccruedInterest(accountId, requestDate);
            BigDecimal totalDisbursement = finalBalance.add(accruedInterest);
            
            log.info("Step 5: Calculated final disbursement: balance={}, interest={}, total={}", 
                    finalBalance, accruedInterest, totalDisbursement);
            
            // Step 6: Process fee assessments and adjustments
            BigDecimal closureFees = accountClosureService.assessClosureFees(accountId, closureType, requestDate);
            totalDisbursement = totalDisbursement.subtract(closureFees);
            
            log.info("Step 6: Applied closure fees: fees={}, net disbursement={}", 
                    closureFees, totalDisbursement);
            
            // Step 7: Cancel recurring payments and scheduled transfers
            accountClosureService.cancelRecurringPayments(accountId, requestDate);
            accountClosureService.cancelScheduledTransfers(accountId, requestDate);
            
            log.info("Step 7: Cancelled recurring payments and scheduled transfers");
            
            // Step 8: Disburse final balance
            if (totalDisbursement.compareTo(BigDecimal.ZERO) > 0) {
                accountClosureService.disburseFinalBalance(accountId, customerId, totalDisbursement, 
                        disbursementMethod, requestDate);
                log.info("Step 8: Disbursed final balance: amount={}, method={}", 
                        totalDisbursement, disbursementMethod);
            }
            
            // Step 9: Generate final account statement
            accountClosureService.generateFinalStatement(accountId, finalBalance, accruedInterest, 
                    closureFees, totalDisbursement, requestDate);
            
            log.info("Step 9: Generated final account statement");
            
            // Step 10: Update compliance and regulatory records
            complianceNotificationService.notifyAccountClosure(accountId, customerId, closureReason, requestDate);
            
            // Step 11: Archive account data with 7-year retention (regulatory requirement)
            AccountClosure closure = accountClosureService.archiveAccountData(accountId, customerId, 
                    closureReason, closureType, totalDisbursement, requestDate);
            
            log.info("Step 11: Archived account data: closureId={}", closure.getClosureId());
            
            // Step 12: Mark account as closed
            accountClosureService.finalizeAccountClosure(accountId, requestDate);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed account closure event: eventId={}, accountId={}", 
                    eventId, accountId);
            
        } catch (Exception e) {
            log.error("Error processing account closure event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void handleProcessingError(ConsumerRecord<String, String> record, Exception e) {
//        TODO - properly implement with business logic, production-ready code, etc. added by aniix october, 28th 2025
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("accountId") || 
            !eventData.has("customerId") || !eventData.has("closureReason")) {
            throw new IllegalArgumentException("Invalid account closure event structure");
        }
    }
}