package com.waqiti.bankintegration.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.bankintegration.service.SwiftMessageProcessingService;
import com.waqiti.bankintegration.service.InternationalTransferService;
import com.waqiti.bankintegration.service.SanctionsScreeningService;
import com.waqiti.bankintegration.service.ComplianceValidationService;
import com.waqiti.bankintegration.service.AuditService;
import com.waqiti.bankintegration.entity.SwiftMessage;
import com.waqiti.bankintegration.entity.InternationalTransfer;
import com.waqiti.bankintegration.entity.ComplianceCheck;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

/**
 * Critical Event Consumer #1: SWIFT Message Events Consumer
 * Processes international wire transfers, SWIFT MT messages, and cross-border payments
 * Implements 12-step zero-tolerance processing for SWIFT message handling
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SwiftMessageEventsConsumer extends BaseKafkaConsumer {

    private final SwiftMessageProcessingService swiftMessageService;
    private final InternationalTransferService transferService;
    private final SanctionsScreeningService sanctionsService;
    private final ComplianceValidationService complianceService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "swift-message-events", 
        groupId = "swift-message-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 2500, multiplier = 2.0, maxDelay = 25000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR
    )
    @CircuitBreaker(name = "swift-message-consumer")
    @Retry(name = "swift-message-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleSwiftMessageEvent(
            ConsumerRecord<String, String> record, 
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {
        
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "swift-message-event");
        MDC.put("partition", String.valueOf(partition));
        
        try {
            log.info("Step 1: Processing SWIFT message event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            String eventId = eventData.path("eventId").asText();
            String messageType = eventData.path("messageType").asText(); // MT103, MT202, MT900, etc
            String swiftReference = eventData.path("swiftReference").asText();
            String senderBIC = eventData.path("senderBIC").asText();
            String receiverBIC = eventData.path("receiverBIC").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String currency = eventData.path("currency").asText();
            String messageContent = eventData.path("messageContent").asText();
            LocalDateTime valueDate = LocalDateTime.parse(eventData.path("valueDate").asText());
            String priorityCode = eventData.path("priorityCode").asText(); // URGENT, NORMAL
            
            log.info("Step 2: Extracted SWIFT message details: type={}, reference={}, amount={} {}, sender={}", 
                    messageType, swiftReference, amount, currency, senderBIC);
            
            // Step 3: SWIFT message validation and format verification
            log.info("Step 3: Validating SWIFT message format and structure compliance");
            SwiftMessage swiftMessage = swiftMessageService.createSwiftMessage(eventData);
            
            swiftMessageService.validateMessageFormat(swiftMessage);
            swiftMessageService.validateBICCodes(senderBIC, receiverBIC);
            swiftMessageService.validateCurrencyCode(currency);
            swiftMessageService.verifyMessageIntegrity(messageContent);
            
            if (!swiftMessageService.isValidSwiftMessageType(messageType)) {
                throw new IllegalStateException("Invalid SWIFT message type: " + messageType);
            }
            
            // Step 4: International transfer creation and routing
            log.info("Step 4: Creating international transfer and determining routing path");
            InternationalTransfer transfer = transferService.createInternationalTransfer(swiftMessage);
            
            transferService.validateTransferLimits(transfer);
            transferService.calculateTransferFees(transfer);
            transferService.determineCorrespondentBanks(transfer);
            transferService.validateValueDate(transfer, valueDate);
            
            String routingPath = transferService.determineOptimalRouting(senderBIC, receiverBIC, currency);
            transferService.setRoutingPath(transfer, routingPath);
            
            // Step 5: Sanctions screening and compliance checks
            log.info("Step 5: Conducting comprehensive sanctions screening and compliance validation");
            ComplianceCheck complianceCheck = complianceService.createComplianceCheck(transfer);
            
            boolean senderScreened = sanctionsService.screenEntity(senderBIC, "BANK");
            boolean receiverScreened = sanctionsService.screenEntity(receiverBIC, "BANK");
            
            if (!senderScreened || !receiverScreened) {
                complianceService.flagSanctionsViolation(complianceCheck);
                transferService.blockTransfer(transfer, "SANCTIONS_VIOLATION");
                return;
            }
            
            sanctionsService.screenTransferParties(transfer);
            complianceService.validateAMLRequirements(transfer);
            complianceService.checkTerrorismFinancingLists(transfer);
            
            // Step 6: Regulatory compliance and reporting requirements
            log.info("Step 6: Ensuring regulatory compliance and fulfilling reporting obligations");
            complianceService.validateCTRRequirements(transfer);
            complianceService.checkSARRequirements(transfer);
            complianceService.validateBSACompliance(transfer);
            
            if (amount.compareTo(new BigDecimal("10000")) >= 0) {
                complianceService.generateCTRReport(transfer);
            }
            
            if (complianceService.requiresOFACReporting(transfer)) {
                complianceService.generateOFACReport(transfer);
            }
            
            complianceService.updateRegulatoryMetrics(transfer);
            
            // Step 7: Currency exchange and FX rate determination
            log.info("Step 7: Processing currency exchange and determining FX rates");
            if (transferService.requiresCurrencyExchange(transfer)) {
                BigDecimal fxRate = transferService.getCurrentFXRate(currency, transfer.getTargetCurrency());
                transferService.calculateExchangeAmount(transfer, fxRate);
                transferService.applyFXSpread(transfer);
                transferService.hedgeCurrencyRisk(transfer);
            }
            
            transferService.validateExchangeRates(transfer);
            transferService.calculateTotalTransferCost(transfer);
            
            // Step 8: Liquidity management and funding verification
            log.info("Step 8: Managing liquidity requirements and verifying funding availability");
            transferService.checkLiquidityRequirements(transfer);
            transferService.validateNostroAccountBalance(receiverBIC, currency);
            transferService.reserveFunds(transfer);
            
            if ("URGENT".equals(priorityCode)) {
                transferService.prioritizeTransfer(transfer);
                transferService.allocateUrgentLiquidity(transfer);
            }
            
            transferService.updateLiquidityMetrics(transfer);
            
            // Step 9: Risk assessment and fraud detection
            log.info("Step 9: Conducting comprehensive risk assessment and fraud detection");
            int riskScore = transferService.calculateTransferRiskScore(transfer);
            
            if (riskScore > 85) {
                transferService.escalateHighRiskTransfer(transfer);
                complianceService.requireManualReview(transfer);
            }
            
            transferService.detectFraudPatterns(transfer);
            transferService.validateBeneficiaryInformation(transfer);
            transferService.checkDuplicateTransfers(transfer);
            
            // Step 10: Message formatting and SWIFT network transmission
            log.info("Step 10: Formatting outbound SWIFT message and preparing for transmission");
            String outboundMessage = swiftMessageService.formatOutboundMessage(swiftMessage, transfer);
            swiftMessageService.validateOutboundFormat(outboundMessage);
            swiftMessageService.addMessageAuthentication(outboundMessage);
            
            if (transferService.isApproved(transfer)) {
                swiftMessageService.transmitToSwiftNetwork(outboundMessage);
                transferService.updateTransferStatus(transfer, "TRANSMITTED");
            } else {
                transferService.updateTransferStatus(transfer, "PENDING_APPROVAL");
            }
            
            // Step 11: Settlement processing and nostro account management
            log.info("Step 11: Processing settlement and managing correspondent banking relationships");
            if ("TRANSMITTED".equals(transfer.getStatus())) {
                transferService.initiateSettlement(transfer);
                transferService.updateNostroPosition(receiverBIC, currency, amount);
                transferService.generateSettlementInstructions(transfer);
                
                transferService.reconcileCorrespondentAccounts(transfer);
                transferService.updateLiquidityPool(currency, amount);
            }
            
            // Step 12: Audit trail and monitoring
            log.info("Step 12: Completing audit trail and updating monitoring systems");
            auditService.logSwiftMessageProcessing(swiftMessage);
            auditService.logInternationalTransfer(transfer);
            auditService.logComplianceActions(complianceCheck);
            
            transferService.updateTransferMetrics(transfer);
            swiftMessageService.updateSwiftStatistics(messageType);
            complianceService.updateComplianceMetrics(complianceCheck);
            
            auditService.generateProcessingReport(transfer);
            
            ack.acknowledge();
            log.info("Step 12: Successfully processed SWIFT message: reference={}, eventId={}, status={}", 
                    swiftReference, eventId, transfer.getStatus());
            
        } catch (Exception e) {
            log.error("Error processing SWIFT message event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        if (!eventData.has("eventId") || !eventData.has("messageType") || 
            !eventData.has("swiftReference") || !eventData.has("senderBIC") ||
            !eventData.has("receiverBIC") || !eventData.has("amount") ||
            !eventData.has("currency") || !eventData.has("messageContent") ||
            !eventData.has("valueDate") || !eventData.has("priorityCode")) {
            throw new IllegalArgumentException("Invalid SWIFT message event structure");
        }
    }
}