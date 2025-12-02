package com.waqiti.payment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.audit.AuditService;
import com.waqiti.payment.service.SwiftTransferService;
import com.waqiti.payment.service.ComplianceService;
import com.waqiti.payment.service.FraudDetectionService;
import com.waqiti.payment.service.NotificationService;
import com.waqiti.common.exception.PaymentProcessingException;
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
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka Consumer for SWIFT Transfer Events
 * Handles international SWIFT transfers with MT message processing and compliance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SwiftTransferEventsConsumer {
    
    private final SwiftTransferService swiftTransferService;
    private final ComplianceService complianceService;
    private final FraudDetectionService fraudDetectionService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    
    @KafkaListener(
        topics = {"swift-transfer-events", "swift-mt103-received", "swift-mt202-processed", "swift-confirmation-received"},
        groupId = "payment-service-swift-transfer-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "4",
        backoff = @Backoff(delay = 3000, multiplier = 2.0, maxDelay = 30000)
    )
    @Transactional
    public void handleSwiftTransferEvent(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        UUID swiftTransferId = null;
        UUID customerId = null;
        String eventType = null;
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            
            swiftTransferId = UUID.fromString((String) event.get("swiftTransferId"));
            customerId = UUID.fromString((String) event.get("customerId"));
            eventType = (String) event.get("eventType");
            String messageType = (String) event.get("messageType"); // MT103, MT202, MT199, etc.
            String senderSwiftCode = (String) event.get("senderSwiftCode");
            String receiverSwiftCode = (String) event.get("receiverSwiftCode");
            String intermediaryBankSwift = (String) event.get("intermediaryBankSwift");
            String orderingCustomer = (String) event.get("orderingCustomer");
            String beneficiaryCustomer = (String) event.get("beneficiaryCustomer");
            String beneficiaryBank = (String) event.get("beneficiaryBank");
            BigDecimal amount = new BigDecimal((String) event.get("amount"));
            String currency = (String) event.get("currency");
            String valueDate = (String) event.get("valueDate");
            String remittanceInfo = (String) event.get("remittanceInfo");
            String chargesBearer = (String) event.get("chargesBearer"); // OUR, BEN, SHA
            String purposeCode = (String) event.get("purposeCode");
            String regulatoryReporting = (String) event.get("regulatoryReporting");
            LocalDateTime timestamp = LocalDateTime.parse((String) event.get("timestamp"));
            
            // SWIFT specific fields
            String transactionReference = (String) event.get("transactionReference");
            String relatedReference = (String) event.get("relatedReference");
            String bankOperationCode = (String) event.get("bankOperationCode");
            String instructionCode = (String) event.get("instructionCode");
            String senderCorrespondent = (String) event.get("senderCorrespondent");
            String receiverCorrespondent = (String) event.get("receiverCorrespondent");
            
            log.info("Processing SWIFT transfer event - TransferId: {}, CustomerId: {}, MessageType: {}, Amount: {} {}", 
                    swiftTransferId, customerId, messageType, amount, currency);
            
            // Step 1: Validate SWIFT message format and content
            swiftTransferService.validateSwiftMessage(swiftTransferId, messageType, senderSwiftCode,
                    receiverSwiftCode, amount, currency, transactionReference);
            
            // Step 2: Comprehensive compliance screening
            Map<String, Object> complianceResult = complianceService.performSwiftCompliance(
                    swiftTransferId, customerId, orderingCustomer, beneficiaryCustomer,
                    beneficiaryBank, senderSwiftCode, receiverSwiftCode, amount, currency,
                    purposeCode, regulatoryReporting, timestamp);
            
            if ("BLOCKED".equals(complianceResult.get("status"))) {
                swiftTransferService.blockSwiftTransfer(swiftTransferId, 
                        (String) complianceResult.get("reason"), messageType, timestamp);
                log.warn("SWIFT transfer blocked due to compliance: {}", complianceResult.get("reason"));
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 3: Sanctions and PEP screening
            Map<String, Object> sanctionsResult = complianceService.performSanctionsScreening(
                    orderingCustomer, beneficiaryCustomer, beneficiaryBank, 
                    senderSwiftCode, receiverSwiftCode, timestamp);
            
            if ("HIT".equals(sanctionsResult.get("status"))) {
                swiftTransferService.escalateToCompliance(swiftTransferId, sanctionsResult, timestamp);
                log.warn("SWIFT transfer escalated for sanctions review");
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 4: Fraud detection for international transfers
            Map<String, Object> fraudAssessment = fraudDetectionService.assessSwiftFraudRisk(
                    swiftTransferId, customerId, amount, currency, senderSwiftCode,
                    receiverSwiftCode, beneficiaryBank, messageType, timestamp);
            
            String riskLevel = (String) fraudAssessment.get("riskLevel");
            if ("CRITICAL".equals(riskLevel)) {
                swiftTransferService.suspendForInvestigation(swiftTransferId, fraudAssessment, timestamp);
                log.warn("SWIFT transfer suspended - Critical risk detected");
                acknowledgment.acknowledge();
                return;
            }
            
            // Step 5: Process based on message type and event
            switch (eventType) {
                case "SWIFT_MT103_RECEIVED":
                    swiftTransferService.processMT103CustomerTransfer(swiftTransferId, customerId,
                            senderSwiftCode, receiverSwiftCode, orderingCustomer, beneficiaryCustomer,
                            amount, currency, valueDate, remittanceInfo, chargesBearer, 
                            transactionReference, timestamp);
                    break;
                    
                case "SWIFT_MT202_PROCESSED":
                    swiftTransferService.processMT202BankTransfer(swiftTransferId,
                            senderSwiftCode, receiverSwiftCode, intermediaryBankSwift,
                            amount, currency, valueDate, relatedReference, timestamp);
                    break;
                    
                case "SWIFT_CONFIRMATION_RECEIVED":
                    swiftTransferService.processSwiftConfirmation(swiftTransferId,
                            (String) event.get("confirmationReference"), 
                            (String) event.get("status"), timestamp);
                    break;
                    
                default:
                    swiftTransferService.processGenericSwiftEvent(swiftTransferId, eventType, 
                            messageType, event, timestamp);
            }
            
            // Step 6: Generate correspondent banking instructions
            if (intermediaryBankSwift != null) {
                swiftTransferService.generateCorrespondentInstructions(swiftTransferId,
                        intermediaryBankSwift, senderCorrespondent, receiverCorrespondent, timestamp);
            }
            
            // Step 7: Handle charges and fees calculation
            BigDecimal totalCharges = swiftTransferService.calculateSwiftCharges(swiftTransferId,
                    amount, currency, chargesBearer, senderSwiftCode, receiverSwiftCode, timestamp);
            
            // Step 8: Regulatory reporting
            swiftTransferService.generateRegulatoryReports(swiftTransferId, messageType,
                    amount, currency, purposeCode, regulatoryReporting, 
                    complianceResult, sanctionsResult, timestamp);
            
            // Step 9: Send notifications and confirmations
            notificationService.sendSwiftTransferNotification(swiftTransferId, customerId,
                    eventType, messageType, amount, currency, beneficiaryCustomer, timestamp);
            
            // Step 10: Update nostro/vostro accounts
            swiftTransferService.updateCorrespondentAccounts(swiftTransferId, senderSwiftCode,
                    receiverSwiftCode, amount, currency, chargesBearer, timestamp);
            
            // Step 11: Audit logging with enhanced details
            auditService.auditFinancialEvent(
                    "SWIFT_TRANSFER_EVENT_PROCESSED",
                    customerId.toString(),
                    String.format("SWIFT transfer event processed - Type: %s, Message: %s, Amount: %s %s", 
                            eventType, messageType, amount, currency),
                    Map.of(
                            "swiftTransferId", swiftTransferId.toString(),
                            "customerId", customerId.toString(),
                            "eventType", eventType,
                            "messageType", messageType,
                            "transactionReference", transactionReference,
                            "senderSwiftCode", senderSwiftCode,
                            "receiverSwiftCode", receiverSwiftCode,
                            "amount", amount.toString(),
                            "currency", currency,
                            "chargesBearer", chargesBearer,
                            "purposeCode", purposeCode,
                            "riskLevel", riskLevel,
                            "complianceStatus", complianceResult.get("status").toString(),
                            "sanctionsStatus", sanctionsResult.get("status").toString(),
                            "totalCharges", totalCharges.toString()
                    )
            );
            
            acknowledgment.acknowledge();
            log.info("Successfully processed SWIFT transfer event - TransferId: {}, MessageType: {}", 
                    swiftTransferId, messageType);
            
        } catch (Exception e) {
            log.error("SWIFT transfer event processing failed - TransferId: {}, EventType: {}, Error: {}", 
                    swiftTransferId, eventType, e.getMessage(), e);
            throw new PaymentProcessingException("SWIFT transfer event processing failed", e);
        }
    }
}