package com.waqiti.crypto.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.crypto.service.CBDCService;
import com.waqiti.crypto.service.CryptoComplianceService;
import com.waqiti.crypto.service.CBDCValidationService;
import com.waqiti.crypto.entity.CBDCTransaction;
import com.waqiti.crypto.entity.CBDCWallet;
import com.waqiti.crypto.entity.CBDCTransactionStatus;
import com.waqiti.crypto.entity.CBDCTransactionType;
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
 * Critical Event Consumer #68: Central Bank Digital Currency (CBDC) Event Consumer
 * Processes CBDC transactions with full regulatory compliance and central bank integration
 * Implements 12-step zero-tolerance processing for digital sovereign currency transactions
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CentralBankDigitalCurrencyEventConsumer extends BaseKafkaConsumer {

    private final CBDCService cbdcService;
    private final CryptoComplianceService complianceService;
    private final CBDCValidationService validationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "central-bank-digital-currency-events", groupId = "cbdc-processing-group")
    @CircuitBreaker(name = "cbdc-consumer")
    @Retry(name = "cbdc-consumer")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleCentralBankDigitalCurrencyEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "central-bank-digital-currency-event");
        MDC.put("partition", String.valueOf(record.partition()));
        MDC.put("offset", String.valueOf(record.offset()));
        
        try {
            log.info("Step 1: Processing CBDC event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            // Step 2: Parse and validate event structure
            JsonNode eventData = objectMapper.readTree(record.value());
            validateEventStructure(eventData);
            
            // Step 3: Extract and validate event details
            String eventId = eventData.path("eventId").asText();
            String eventType = eventData.path("eventType").asText();
            String cbdcType = eventData.path("cbdcType").asText();
            String centralBankId = eventData.path("centralBankId").asText();
            String userId = eventData.path("userId").asText();
            String walletId = eventData.path("walletId").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            String currency = eventData.path("currency").asText();
            String counterpartyId = eventData.path("counterpartyId").asText();
            String transactionId = eventData.path("transactionId").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            String digitalSignature = eventData.path("digitalSignature").asText();
            String centralBankSignature = eventData.path("centralBankSignature").asText();
            
            MDC.put("eventId", eventId);
            MDC.put("cbdcType", cbdcType);
            MDC.put("centralBankId", centralBankId);
            MDC.put("userId", userId);
            
            log.info("Step 2: Extracted CBDC event details: eventId={}, type={}, cbdcType={}, amount={}, currency={}", 
                    eventId, eventType, cbdcType, amount, currency);
            
            // Step 4: Verify central bank digital signatures
            verifyCentralBankSignatures(eventId, digitalSignature, centralBankSignature, centralBankId);
            
            // Step 5: Validate CBDC transaction compliance
            validateCBDCCompliance(eventId, cbdcType, centralBankId, userId, amount, currency, counterpartyId);
            
            // Step 6: Check idempotency to prevent duplicate processing
            if (cbdcService.isTransactionProcessed(eventId, transactionId)) {
                log.warn("Step 6: CBDC transaction already processed: eventId={}, transactionId={}", eventId, transactionId);
                ack.acknowledge();
                return;
            }
            
            // Step 7: Validate CBDC wallet and balances
            validateCBDCWalletAndBalances(walletId, userId, amount, cbdcType, eventType);
            
            // Step 8: Process CBDC transaction based on type
            processCBDCTransaction(eventId, eventType, cbdcType, centralBankId, userId, walletId, 
                    amount, currency, counterpartyId, transactionId, timestamp, digitalSignature);
            
            // Step 9: Update central bank reporting
            updateCentralBankReporting(eventId, cbdcType, centralBankId, amount, currency, eventType, timestamp);
            
            // Step 10: Record regulatory compliance data
            recordRegulatoryCompliance(eventId, cbdcType, centralBankId, userId, amount, currency, eventType);
            
            // Step 11: Publish CBDC settlement events
            publishCBDCSettlementEvents(eventId, cbdcType, centralBankId, transactionId, amount, currency, eventType);
            
            // Step 12: Complete transaction and acknowledge
            cbdcService.markTransactionProcessed(eventId, transactionId);
            ack.acknowledge();
            
            log.info("Step 12: Successfully processed CBDC event: eventId={}, type={}, amount={}", 
                    eventId, eventType, amount);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse CBDC event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            log.error("Error processing CBDC event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void validateEventStructure(JsonNode eventData) {
        log.info("Step 2a: Validating CBDC event structure");
        
        if (!eventData.has("eventId") || eventData.path("eventId").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty eventId in CBDC event");
        }
        
        if (!eventData.has("eventType") || eventData.path("eventType").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty eventType in CBDC event");
        }
        
        if (!eventData.has("cbdcType") || eventData.path("cbdcType").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty cbdcType in CBDC event");
        }
        
        if (!eventData.has("centralBankId") || eventData.path("centralBankId").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty centralBankId in CBDC event");
        }
        
        if (!eventData.has("amount") || eventData.path("amount").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty amount in CBDC event");
        }
        
        if (!eventData.has("digitalSignature") || eventData.path("digitalSignature").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty digitalSignature in CBDC event");
        }
        
        if (!eventData.has("centralBankSignature") || eventData.path("centralBankSignature").asText().isEmpty()) {
            throw new IllegalArgumentException("Missing or empty centralBankSignature in CBDC event");
        }
        
        log.info("Step 2b: CBDC event structure validation successful");
    }

    private void verifyCentralBankSignatures(String eventId, String digitalSignature, 
                                           String centralBankSignature, String centralBankId) {
        log.info("Step 4: Verifying central bank digital signatures for eventId={}", eventId);
        
        try {
            // Verify user digital signature
            if (!validationService.verifyDigitalSignature(eventId, digitalSignature)) {
                throw new SecurityException("Invalid digital signature for CBDC transaction: " + eventId);
            }
            
            // Verify central bank signature
            if (!validationService.verifyCentralBankSignature(eventId, centralBankSignature, centralBankId)) {
                throw new SecurityException("Invalid central bank signature for CBDC transaction: " + eventId);
            }
            
            // Verify central bank authorization
            if (!validationService.isCentralBankAuthorized(centralBankId)) {
                throw new SecurityException("Unauthorized central bank for CBDC transaction: " + centralBankId);
            }
            
            log.info("Step 4: Central bank signature verification successful for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 4: Central bank signature verification failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new SecurityException("CBDC signature verification failed: " + e.getMessage(), e);
        }
    }

    private void validateCBDCCompliance(String eventId, String cbdcType, String centralBankId, 
                                      String userId, BigDecimal amount, String currency, String counterpartyId) {
        log.info("Step 5: Validating CBDC compliance for eventId={}", eventId);
        
        try {
            // Validate CBDC transaction limits
            if (!complianceService.validateCBDCTransactionLimits(cbdcType, amount, currency)) {
                throw new IllegalArgumentException("CBDC transaction exceeds regulatory limits: " + amount);
            }
            
            // Check sanctions compliance for all parties
            if (!complianceService.checkSanctionsCompliance(userId)) {
                throw new SecurityException("User " + userId + " is sanctioned for CBDC transactions");
            }
            
            if (counterpartyId != null && !complianceService.checkSanctionsCompliance(counterpartyId)) {
                throw new SecurityException("Counterparty " + counterpartyId + " is sanctioned for CBDC transactions");
            }
            
            // Validate central bank jurisdiction
            if (!complianceService.validateCentralBankJurisdiction(centralBankId, currency)) {
                throw new IllegalArgumentException("Central bank not authorized for currency: " + currency);
            }
            
            // Check AML/CTF requirements
            if (!complianceService.validateAMLCTFRequirements(userId, amount, currency, cbdcType)) {
                throw new SecurityException("CBDC transaction fails AML/CTF requirements");
            }
            
            log.info("Step 5: CBDC compliance validation successful for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 5: CBDC compliance validation failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new SecurityException("CBDC compliance validation failed: " + e.getMessage(), e);
        }
    }

    private void validateCBDCWalletAndBalances(String walletId, String userId, BigDecimal amount, 
                                             String cbdcType, String eventType) {
        log.info("Step 7: Validating CBDC wallet and balances for walletId={}", walletId);
        
        try {
            // Validate CBDC wallet exists and is active
            CBDCWallet wallet = cbdcService.getCBDCWallet(walletId);
            if (wallet == null) {
                throw new IllegalArgumentException("CBDC wallet not found: " + walletId);
            }
            
            if (!wallet.isActive()) {
                throw new IllegalArgumentException("CBDC wallet is not active: " + walletId);
            }
            
            if (!wallet.getUserId().equals(userId)) {
                throw new SecurityException("CBDC wallet does not belong to user: " + userId);
            }
            
            // Validate CBDC type support
            if (!wallet.supportsCBDCType(cbdcType)) {
                throw new IllegalArgumentException("CBDC wallet does not support type: " + cbdcType);
            }
            
            // For debit operations, validate sufficient balance
            if ("TRANSFER".equals(eventType) || "PAYMENT".equals(eventType) || "WITHDRAW".equals(eventType)) {
                BigDecimal balance = cbdcService.getCBDCBalance(walletId, cbdcType);
                if (balance.compareTo(amount) < 0) {
                    throw new IllegalArgumentException("Insufficient CBDC balance for transaction: " + balance + " < " + amount);
                }
            }
            
            log.info("Step 7: CBDC wallet and balance validation successful for walletId={}", walletId);
            
        } catch (Exception e) {
            log.error("Step 7: CBDC wallet validation failed for walletId={}: {}", walletId, e.getMessage(), e);
            throw new IllegalArgumentException("CBDC wallet validation failed: " + e.getMessage(), e);
        }
    }

    private void processCBDCTransaction(String eventId, String eventType, String cbdcType, String centralBankId,
                                      String userId, String walletId, BigDecimal amount, String currency,
                                      String counterpartyId, String transactionId, LocalDateTime timestamp,
                                      String digitalSignature) {
        log.info("Step 8: Processing CBDC transaction: eventId={}, type={}", eventId, eventType);
        
        try {
            CBDCTransaction transaction = CBDCTransaction.builder()
                    .id(UUID.randomUUID())
                    .eventId(eventId)
                    .transactionId(transactionId)
                    .type(CBDCTransactionType.valueOf(eventType.toUpperCase()))
                    .cbdcType(cbdcType)
                    .centralBankId(centralBankId)
                    .userId(userId)
                    .walletId(walletId)
                    .amount(amount)
                    .currency(currency)
                    .counterpartyId(counterpartyId)
                    .status(CBDCTransactionStatus.PROCESSING)
                    .timestamp(timestamp)
                    .digitalSignature(digitalSignature)
                    .processingStartTime(LocalDateTime.now())
                    .build();
            
            // Process based on transaction type
            switch (eventType.toUpperCase()) {
                case "ISSUANCE":
                    cbdcService.processCBDCIssuance(transaction);
                    break;
                case "TRANSFER":
                    cbdcService.processCBDCTransfer(transaction);
                    break;
                case "PAYMENT":
                    cbdcService.processCBDCPayment(transaction);
                    break;
                case "REDEMPTION":
                    cbdcService.processCBDCRedemption(transaction);
                    break;
                case "SETTLEMENT":
                    cbdcService.processCBDCSettlement(transaction);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported CBDC transaction type: " + eventType);
            }
            
            // Update transaction status
            transaction.setStatus(CBDCTransactionStatus.COMPLETED);
            transaction.setProcessingEndTime(LocalDateTime.now());
            cbdcService.saveCBDCTransaction(transaction);
            
            log.info("Step 8: CBDC transaction processed successfully: eventId={}, transactionId={}", 
                    eventId, transactionId);
            
        } catch (Exception e) {
            log.error("Step 8: CBDC transaction processing failed for eventId={}: {}", eventId, e.getMessage(), e);
            throw new IllegalStateException("CBDC transaction processing failed: " + e.getMessage(), e);
        }
    }

    private void updateCentralBankReporting(String eventId, String cbdcType, String centralBankId, 
                                          BigDecimal amount, String currency, String eventType, LocalDateTime timestamp) {
        log.info("Step 9: Updating central bank reporting for eventId={}", eventId);
        
        try {
            cbdcService.reportToCentralBank(
                    centralBankId,
                    eventId,
                    cbdcType,
                    eventType,
                    amount,
                    currency,
                    timestamp
            );
            
            // Update monetary policy statistics if required
            if (amount.compareTo(new BigDecimal("1000000")) >= 0) { // Large transactions
                cbdcService.updateMonetaryPolicyStatistics(centralBankId, cbdcType, amount, eventType);
            }
            
            log.info("Step 9: Central bank reporting updated successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 9: Central bank reporting failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for reporting issues, but log for manual review
        }
    }

    private void recordRegulatoryCompliance(String eventId, String cbdcType, String centralBankId, 
                                          String userId, BigDecimal amount, String currency, String eventType) {
        log.info("Step 10: Recording regulatory compliance for eventId={}", eventId);
        
        try {
            complianceService.recordCBDCTransaction(
                    eventId,
                    cbdcType,
                    centralBankId,
                    userId,
                    amount,
                    currency,
                    eventType,
                    LocalDateTime.now()
            );
            
            // Check if transaction requires additional regulatory reporting
            if (amount.compareTo(new BigDecimal("10000")) >= 0) {
                complianceService.triggerCTRReporting(eventId, userId, amount, currency);
            }
            
            log.info("Step 10: Regulatory compliance recorded successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 10: Regulatory compliance recording failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for compliance recording issues, but log for manual review
        }
    }

    private void publishCBDCSettlementEvents(String eventId, String cbdcType, String centralBankId, 
                                           String transactionId, BigDecimal amount, String currency, String eventType) {
        log.info("Step 11: Publishing CBDC settlement events for eventId={}", eventId);
        
        try {
            cbdcService.publishSettlementEvent(
                    eventId,
                    cbdcType,
                    centralBankId,
                    transactionId,
                    amount,
                    currency,
                    eventType,
                    LocalDateTime.now()
            );
            
            // Publish to real-time monitoring systems
            cbdcService.publishToMonitoringSystems(eventId, cbdcType, amount, eventType);
            
            log.info("Step 11: CBDC settlement events published successfully for eventId={}", eventId);
            
        } catch (Exception e) {
            log.error("Step 11: CBDC settlement event publishing failed for eventId={}: {}", eventId, e.getMessage(), e);
            // Don't fail the transaction for event publishing issues, but log for manual review
        }
    }
}