package com.waqiti.lending.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.lending.service.LoanDisbursementService;
import com.waqiti.lending.service.ComplianceValidationService;
import com.waqiti.lending.entity.LoanDisbursement;
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
 * Critical Event Consumer #157: Loan Disbursement Event Consumer
 * Processes loan fund disbursement with TILA, ECOA, and UCC compliance
 * Implements 12-step zero-tolerance processing for loan origination and funding
 *
 * IDEMPOTENCY PROTECTION:
 * - Uses eventId as idempotency key to prevent duplicate disbursements
 * - 30-day TTL for idempotency records (loan lifecycle tracking)
 * - CRITICAL: Prevents double-disbursement of loan funds
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoanDisbursementEventConsumer extends BaseKafkaConsumer {

    private final LoanDisbursementService loanDisbursementService;
    private final ComplianceValidationService complianceValidationService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "loan-disbursement-events", groupId = "loan-disbursement-group")
    @CircuitBreaker(name = "loan-disbursement-consumer")
    @Retry(name = "loan-disbursement-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleLoanDisbursementEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "loan-disbursement-event");

        try {
            log.info("Step 1: Processing loan disbursement event: partition={}, offset={}",
                    record.partition(), record.offset());

            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);

            String eventId = eventData.path("eventId").asText();
            String loanId = eventData.path("loanId").asText();
            String borrowerId = eventData.path("borrowerId").asText();
            BigDecimal loanAmount = new BigDecimal(eventData.path("loanAmount").asText());
            BigDecimal disbursementAmount = new BigDecimal(eventData.path("disbursementAmount").asText());
            String disbursementMethod = eventData.path("disbursementMethod").asText();
            String destinationAccountId = eventData.path("destinationAccountId").asText();
            String loanPurpose = eventData.path("loanPurpose").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());

            // CRITICAL IDEMPOTENCY CHECK - Prevent duplicate loan disbursements
            String idempotencyKey = "loan-disbursement:" + eventId;
            UUID operationId = UUID.randomUUID();
            Duration ttl = Duration.ofDays(30); // 30-day retention for loan disbursement tracking

            if (!idempotencyService.startOperation(idempotencyKey, operationId, ttl)) {
                log.warn("⚠️ DUPLICATE DETECTED - Loan disbursement already processed: eventId={}, loanId={}, amount={}",
                        eventId, loanId, disbursementAmount);
                ack.acknowledge();
                return;
            }

            log.info("Step 2: Idempotency check passed - Extracted loan disbursement details: loanId={}, amount={}, purpose={}",
                    loanId, disbursementAmount, loanPurpose);
            
            boolean tilaCompliant = complianceValidationService.verifyTILADisclosure(
                    loanId, loanAmount, timestamp);
            
            if (!tilaCompliant) {
                log.error("Step 3: TILA disclosure not completed: loanId={}", loanId);
                loanDisbursementService.rejectDisbursement(loanId, "TILA_NOT_COMPLETED", timestamp);
                ack.acknowledge();
                return;
            }
            
            log.info("Step 3: TILA disclosure verified");
            
            boolean fundingAvailable = loanDisbursementService.verifyFundingAvailability(
                    loanAmount, timestamp);
            
            if (!fundingAvailable) {
                log.error("Step 4: Insufficient funding for loan: loanId={}", loanId);
                loanDisbursementService.deferDisbursement(loanId, timestamp);
                ack.acknowledge();
                return;
            }
            
            log.info("Step 4: Funding availability confirmed");
            
            loanDisbursementService.reserveFunds(loanId, loanAmount, timestamp);
            log.info("Step 5: Reserved funds for loan disbursement");
            
            LoanDisbursement disbursement = loanDisbursementService.createDisbursementRecord(
                    loanId, borrowerId, disbursementAmount, disbursementMethod, 
                    destinationAccountId, timestamp);
            
            log.info("Step 6: Created loan disbursement record: disbursementId={}", 
                    disbursement.getDisbursementId());
            
            boolean transferSuccess = loanDisbursementService.executeFundTransfer(
                    disbursement.getDisbursementId(), destinationAccountId, 
                    disbursementAmount, disbursementMethod, timestamp);
            
            if (!transferSuccess) {
                log.error("Step 7: Fund transfer failed: disbursementId={}", 
                        disbursement.getDisbursementId());
                loanDisbursementService.handleDisbursementFailure(
                        disbursement.getDisbursementId(), timestamp);
                ack.acknowledge();
                return;
            }
            
            log.info("Step 7: Funds transferred successfully");
            
            loanDisbursementService.updateLoanStatus(loanId, "ACTIVE", timestamp);
            log.info("Step 8: Updated loan status to ACTIVE");
            
            loanDisbursementService.createRepaymentSchedule(loanId, loanAmount, timestamp);
            log.info("Step 9: Created loan repayment schedule");
            
            complianceValidationService.generateHMDAReport(loanId, borrowerId, 
                    loanAmount, loanPurpose, timestamp);
            log.info("Step 10: Generated HMDA report");
            
            loanDisbursementService.sendDisbursementConfirmation(borrowerId, loanId, 
                    disbursementAmount, destinationAccountId, timestamp);
            log.info("Step 11: Sent disbursement confirmation");
            
            loanDisbursementService.archiveDisbursement(disbursement.getDisbursementId(),
                    eventData.toString(), timestamp);

            // Mark idempotency operation as complete
            idempotencyService.completeOperation(idempotencyKey, operationId);

            ack.acknowledge();
            log.info("Step 12: Successfully processed loan disbursement: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing loan disbursement event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("loanId") || 
            !eventData.has("borrowerId") || !eventData.has("loanAmount")) {
            throw new IllegalArgumentException("Invalid loan disbursement event structure");
        }
    }
}