package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.payment.service.EscrowService;
import com.waqiti.payment.service.ComplianceService;
import com.waqiti.payment.service.AuthorizationService;
import com.waqiti.payment.service.RegulatoryReportingService;
import com.waqiti.payment.entity.EscrowAccount;
import com.waqiti.payment.entity.EscrowTransaction;
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
import java.util.List;
import java.util.UUID;

/**
 * Critical Event Consumer #110: Escrow Transaction Event Consumer
 * Processes escrow transactions with multi-party authorization and regulatory compliance
 * Implements 12-step zero-tolerance processing for secure escrow workflows
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EscrowTransactionEventConsumer extends BaseKafkaConsumer {

    private final EscrowService escrowService;
    private final ComplianceService complianceService;
    private final AuthorizationService authorizationService;
    private final RegulatoryReportingService regulatoryReportingService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "escrow-transaction-events", groupId = "escrow-transaction-group")
    @CircuitBreaker(name = "escrow-transaction-consumer")
    @Retry(name = "escrow-transaction-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleEscrowTransactionEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "escrow-transaction-event");
        
        try {
            log.info("Step 1: Processing escrow transaction event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String escrowId = eventData.path("escrowId").asText();
            String transactionType = eventData.path("transactionType").asText(); // DEPOSIT, RELEASE, REFUND
            String buyerId = eventData.path("buyerId").asText();
            String sellerId = eventData.path("sellerId").asText();
            String escrowAgentId = eventData.path("escrowAgentId").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String currency = eventData.path("currency").asText();
            String contractId = eventData.path("contractId").asText();
            List<String> releaseConditions = objectMapper.convertValue(
                eventData.path("releaseConditions"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            List<String> authorizedParties = objectMapper.convertValue(
                eventData.path("authorizedParties"), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            int requiredApprovals = eventData.path("requiredApprovals").asInt();
            LocalDateTime releaseDate = LocalDateTime.parse(eventData.path("releaseDate").asText());
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Step 2: Extracted escrow details: escrowId={}, type={}, amount={}, buyer={}, seller={}", 
                    escrowId, transactionType, amount, buyerId, sellerId);
            
            // Step 3: Validate escrow account and participants
            EscrowAccount escrowAccount = escrowService.validateEscrowAccount(
                escrowId, buyerId, sellerId, escrowAgentId, timestamp);
            
            log.info("Step 3: Validated escrow account: accountId={}, status={}", 
                    escrowAccount.getAccountId(), escrowAccount.getStatus());
            
            // Step 4: Perform regulatory compliance checks (BSA/AML, consumer protection)
            complianceService.performEscrowComplianceCheck(
                escrowId, buyerId, sellerId, amount, currency, 
                transactionType, contractId, timestamp);
            
            log.info("Step 4: Completed compliance checks for escrow transaction");
            
            // Step 5: Validate contract terms and release conditions
            boolean contractValid = escrowService.validateContractTerms(
                contractId, releaseConditions, releaseDate, timestamp);
            
            if (!contractValid) {
                log.warn("Step 5: Contract validation failed: contractId={}", contractId);
                throw new IllegalArgumentException("Invalid contract terms or conditions");
            }
            
            // Step 6: Verify multi-party authorization requirements
            boolean authorizationMet = authorizationService.verifyMultiPartyAuthorization(
                escrowId, authorizedParties, requiredApprovals, transactionType, timestamp);
            
            if (!authorizationMet) {
                log.warn("Step 6: Multi-party authorization not met: required={}, received={}", 
                        requiredApprovals, authorizedParties.size());
                escrowService.requestAdditionalAuthorizations(escrowId, authorizedParties, timestamp);
                throw new IllegalArgumentException("Insufficient authorizations for escrow transaction");
            }
            
            // Step 7: Execute escrow transaction based on type
            EscrowTransaction transaction = null;
            
            switch (transactionType) {
                case "DEPOSIT":
                    transaction = escrowService.depositToEscrow(
                        escrowId, buyerId, amount, currency, contractId, timestamp);
                    break;
                case "RELEASE":
                    transaction = escrowService.releaseFromEscrow(
                        escrowId, sellerId, amount, currency, releaseConditions, timestamp);
                    break;
                case "REFUND":
                    transaction = escrowService.refundFromEscrow(
                        escrowId, buyerId, amount, currency, timestamp);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid escrow transaction type: " + transactionType);
            }
            
            log.info("Step 7: Executed escrow transaction: transactionId={}, status={}", 
                    transaction.getTransactionId(), transaction.getStatus());
            
            // Step 8: Update escrow account balance and status
            escrowService.updateEscrowAccountBalance(
                escrowId, amount, transactionType, timestamp);
            
            if ("RELEASE".equals(transactionType) || "REFUND".equals(transactionType)) {
                escrowService.updateEscrowAccountStatus(escrowId, "COMPLETED", timestamp);
            }
            
            log.info("Step 8: Updated escrow account balance and status");
            
            // Step 9: Verify transaction against release conditions
            if ("RELEASE".equals(transactionType)) {
                escrowService.verifyReleaseConditionsFulfillment(
                    contractId, releaseConditions, timestamp);
            }
            
            // Step 10: Send notifications to all parties
            escrowService.sendEscrowNotifications(
                escrowId, buyerId, sellerId, escrowAgentId, 
                transaction, transactionType, timestamp);
            
            log.info("Step 10: Sent escrow notifications to all parties");
            
            // Step 11: Generate documentation and compliance reports
            escrowService.generateEscrowDocumentation(
                escrowId, transaction, contractId, timestamp);
            
            if (amount.compareTo(new BigDecimal("10000")) >= 0) {
                regulatoryReportingService.generateEscrowRegulatoryReports(
                    transaction, timestamp);
                
                log.info("Step 11: Generated regulatory reports for large escrow transaction");
            }
            
            // Step 12: Log escrow event for audit trail and update contract status
            escrowService.logEscrowTransactionEvent(
                escrowId, transactionType, buyerId, sellerId, escrowAgentId, 
                amount, currency, contractId, transaction.getStatus(), 
                authorizedParties, timestamp);
            
            if ("RELEASE".equals(transactionType)) {
                escrowService.updateContractStatus(contractId, "FULFILLED", timestamp);
            } else if ("REFUND".equals(transactionType)) {
                escrowService.updateContractStatus(contractId, "CANCELLED", timestamp);
            }
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed escrow transaction event: eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Error processing escrow transaction event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("escrowId") || 
            !eventData.has("transactionType") || !eventData.has("buyerId") ||
            !eventData.has("sellerId") || !eventData.has("escrowAgentId") ||
            !eventData.has("amount") || !eventData.has("currency") ||
            !eventData.has("contractId") || !eventData.has("releaseConditions") ||
            !eventData.has("authorizedParties") || !eventData.has("requiredApprovals") ||
            !eventData.has("releaseDate")) {
            throw new IllegalArgumentException("Invalid escrow transaction event structure");
        }
    }
}