package com.waqiti.payment.kafka;

import com.waqiti.common.kafka.GenericKafkaEvent;
import com.waqiti.payment.model.*;
import com.waqiti.payment.repository.SwiftTransactionRepository;
import com.waqiti.payment.service.*;
import com.waqiti.payment.wise.WiseApiClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Production-grade Kafka consumer for SWIFT reconciliation events
 * Handles international wire transfers, correspondent banking, and cross-border payments
 * 
 * Critical for: International payments, regulatory reporting, forex operations
 * SLA: Must reconcile within 15 minutes of SWIFT message receipt
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SwiftReconciliationConsumer {

    private final SwiftTransactionRepository swiftRepository;
    private final WiseApiClient wiseApiClient;
    private final PaymentService paymentService;
    private final ForexService forexService;
    private final ComplianceService complianceService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final CorrespondentBankService correspondentBankService;

    private static final BigDecimal FEE_THRESHOLD = new BigDecimal("5000.00");
    private static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("50000.00");
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final long RECONCILIATION_TIMEOUT_MS = 900000; // 15 minutes
    
    @KafkaListener(
        topics = "swift-reconciliation-queue",
        groupId = "swift-reconciliation-processor",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    @CircuitBreaker(name = "swift-reconciliation-processor", fallbackMethod = "handleSwiftReconciliationFailure")
    @Retry(name = "swift-reconciliation-processor")
    public void processSwiftReconciliationEvent(
            @Payload @Valid GenericKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String eventId = event.getEventId();
        log.info("Processing SWIFT reconciliation event: {} from topic: {} partition: {} offset: {}", 
                eventId, topic, partition, offset);

        long startTime = System.currentTimeMillis();
        
        try {
            Map<String, Object> payload = event.getPayload();
            SwiftTransaction swiftTx = extractSwiftTransaction(payload);
            
            // Validate SWIFT message
            validateSwiftMessage(swiftTx);
            
            // Check for duplicate processing
            if (isDuplicateTransaction(swiftTx)) {
                log.warn("Duplicate SWIFT transaction detected: {}, skipping", swiftTx.getMtn());
                acknowledgment.acknowledge();
                return;
            }
            
            // Perform sanctions screening
            performSanctionsScreening(swiftTx);
            
            // Match with internal transaction
            MatchingResult matchingResult = matchTransaction(swiftTx);
            
            // Process based on message type
            ReconciliationResult result = processSwiftMessage(swiftTx, matchingResult);
            
            // Handle fees and charges
            processFees(swiftTx, result);
            
            // Update transaction status
            updateTransactionStatus(swiftTx, result);
            
            // Generate regulatory reports
            generateRegulatoryReports(swiftTx, result);
            
            // Send notifications
            sendNotifications(swiftTx, result);
            
            // Audit trail
            auditSwiftReconciliation(swiftTx, result, event);
            
            // Record metrics
            recordMetrics(swiftTx, startTime);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed SWIFT reconciliation for MTN: {} in {}ms", 
                    swiftTx.getMtn(), System.currentTimeMillis() - startTime);
            
        } catch (ValidationException e) {
            log.error("Validation failed for SWIFT event: {}", eventId, e);
            handleValidationError(event, e);
            acknowledgment.acknowledge();
            
        } catch (ComplianceException e) {
            log.error("Compliance check failed for SWIFT event: {}", eventId, e);
            handleComplianceError(event, e);
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Failed to process SWIFT reconciliation event: {}", eventId, e);
            handleProcessingError(event, e, acknowledgment);
        }
    }

    private SwiftTransaction extractSwiftTransaction(Map<String, Object> payload) {
        return SwiftTransaction.builder()
            .mtn(extractString(payload, "mtn", null)) // Message Transaction Number
            .messageType(extractString(payload, "messageType", null))
            .senderBic(extractString(payload, "senderBic", null))
            .receiverBic(extractString(payload, "receiverBic", null))
            .orderingCustomer(extractString(payload, "orderingCustomer", null))
            .orderingAccount(extractString(payload, "orderingAccount", null))
            .beneficiaryCustomer(extractString(payload, "beneficiaryCustomer", null))
            .beneficiaryAccount(extractString(payload, "beneficiaryAccount", null))
            .amount(extractBigDecimal(payload, "amount"))
            .currency(extractString(payload, "currency", null))
            .valueDate(extractLocalDate(payload, "valueDate"))
            .executionDate(extractLocalDate(payload, "executionDate"))
            .remittanceInfo(extractString(payload, "remittanceInfo", null))
            .chargeBearer(extractString(payload, "chargeBearer", "SHA"))
            .charges(extractBigDecimal(payload, "charges"))
            .exchangeRate(extractBigDecimal(payload, "exchangeRate"))
            .intermediaryBank(extractString(payload, "intermediaryBank", null))
            .uetr(extractString(payload, "uetr", null)) // Unique End-to-end Transaction Reference
            .settlementMethod(extractString(payload, "settlementMethod", null))
            .priority(extractString(payload, "priority", "NORMAL"))
            .regulatoryReporting(extractString(payload, "regulatoryReporting", null))
            .metadata(extractMap(payload, "metadata"))
            .status(SwiftStatus.RECEIVED)
            .receivedAt(Instant.now())
            .build();
    }

    private void validateSwiftMessage(SwiftTransaction swiftTx) {
        // Validate required fields
        if (swiftTx.getMtn() == null || swiftTx.getMtn().isEmpty()) {
            throw new ValidationException("MTN (Message Transaction Number) is required");
        }
        
        if (swiftTx.getMessageType() == null || !isValidMessageType(swiftTx.getMessageType())) {
            throw new ValidationException("Invalid SWIFT message type: " + swiftTx.getMessageType());
        }
        
        // Validate BIC codes
        if (!isValidBic(swiftTx.getSenderBic())) {
            throw new ValidationException("Invalid sender BIC: " + swiftTx.getSenderBic());
        }
        
        if (!isValidBic(swiftTx.getReceiverBic())) {
            throw new ValidationException("Invalid receiver BIC: " + swiftTx.getReceiverBic());
        }
        
        // Validate amount and currency
        if (swiftTx.getAmount() == null || swiftTx.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationException("Invalid transaction amount: " + swiftTx.getAmount());
        }
        
        if (!isValidCurrency(swiftTx.getCurrency())) {
            throw new ValidationException("Invalid or unsupported currency: " + swiftTx.getCurrency());
        }
        
        // Validate value date
        if (swiftTx.getValueDate() != null && swiftTx.getValueDate().isBefore(LocalDate.now().minusDays(30))) {
            throw new ValidationException("Value date is too far in the past: " + swiftTx.getValueDate());
        }
        
        // Validate UETR format
        if (swiftTx.getUetr() != null && !isValidUetr(swiftTx.getUetr())) {
            throw new ValidationException("Invalid UETR format: " + swiftTx.getUetr());
        }
    }

    private boolean isDuplicateTransaction(SwiftTransaction swiftTx) {
        // Check by MTN
        if (swiftRepository.existsByMtn(swiftTx.getMtn())) {
            return true;
        }
        
        // Check by UETR if available
        if (swiftTx.getUetr() != null) {
            return swiftRepository.existsByUetr(swiftTx.getUetr());
        }
        
        // Check for recent similar transactions
        return swiftRepository.existsSimilarTransaction(
            swiftTx.getSenderBic(),
            swiftTx.getBeneficiaryAccount(),
            swiftTx.getAmount(),
            swiftTx.getCurrency(),
            Instant.now().minus(5, ChronoUnit.MINUTES)
        );
    }

    private void performSanctionsScreening(SwiftTransaction swiftTx) {
        log.info("Performing sanctions screening for SWIFT transaction: {}", swiftTx.getMtn());
        
        // Screen ordering customer
        SanctionsResult orderingResult = complianceService.screenEntity(
            swiftTx.getOrderingCustomer(),
            "SWIFT_ORDERING",
            swiftTx.getSenderBic()
        );
        
        if (orderingResult.isHit()) {
            throw new ComplianceException("Ordering customer failed sanctions screening: " + 
                orderingResult.getMatchDetails());
        }
        
        // Screen beneficiary
        SanctionsResult beneficiaryResult = complianceService.screenEntity(
            swiftTx.getBeneficiaryCustomer(),
            "SWIFT_BENEFICIARY",
            swiftTx.getReceiverBic()
        );
        
        if (beneficiaryResult.isHit()) {
            throw new ComplianceException("Beneficiary failed sanctions screening: " + 
                beneficiaryResult.getMatchDetails());
        }
        
        // Screen banks
        if (!complianceService.isBankApproved(swiftTx.getSenderBic())) {
            throw new ComplianceException("Sender bank is not approved: " + swiftTx.getSenderBic());
        }
        
        if (swiftTx.getIntermediaryBank() != null && 
            !complianceService.isBankApproved(swiftTx.getIntermediaryBank())) {
            throw new ComplianceException("Intermediary bank is not approved: " + 
                swiftTx.getIntermediaryBank());
        }
        
        // Country risk check
        String senderCountry = extractCountryFromBic(swiftTx.getSenderBic());
        String receiverCountry = extractCountryFromBic(swiftTx.getReceiverBic());
        
        if (complianceService.isHighRiskCountry(senderCountry) || 
            complianceService.isHighRiskCountry(receiverCountry)) {
            swiftTx.setRequiresManualReview(true);
            swiftTx.setRiskLevel("HIGH");
        }
    }

    private MatchingResult matchTransaction(SwiftTransaction swiftTx) {
        MatchingResult result = new MatchingResult();
        
        // Try to match by reference
        Optional<PaymentTransaction> matchedTx = Optional.empty();
        
        // Match by UETR
        if (swiftTx.getUetr() != null) {
            matchedTx = paymentService.findByUetr(swiftTx.getUetr());
        }
        
        // Match by remittance info
        if (!matchedTx.isPresent() && swiftTx.getRemittanceInfo() != null) {
            matchedTx = paymentService.findByReference(swiftTx.getRemittanceInfo());
        }
        
        // Fuzzy matching by amount and beneficiary
        if (!matchedTx.isPresent()) {
            matchedTx = paymentService.findByAmountAndBeneficiary(
                swiftTx.getAmount(),
                swiftTx.getCurrency(),
                swiftTx.getBeneficiaryAccount(),
                swiftTx.getValueDate()
            );
        }
        
        if (matchedTx.isPresent()) {
            result.setMatched(true);
            result.setInternalTransactionId(matchedTx.get().getId());
            result.setMatchConfidence(calculateMatchConfidence(swiftTx, matchedTx.get()));
        } else {
            result.setMatched(false);
            result.setReason("No matching internal transaction found");
        }
        
        return result;
    }

    private ReconciliationResult processSwiftMessage(SwiftTransaction swiftTx, MatchingResult matchingResult) {
        ReconciliationResult result = new ReconciliationResult();
        result.setMtn(swiftTx.getMtn());
        result.setStartTime(Instant.now());
        
        switch (swiftTx.getMessageType()) {
            case "MT103": // Single Customer Credit Transfer
                result = processMT103(swiftTx, matchingResult);
                break;
                
            case "MT202": // General Financial Institution Transfer
                result = processMT202(swiftTx, matchingResult);
                break;
                
            case "MT900": // Confirmation of Debit
                result = processMT900(swiftTx, matchingResult);
                break;
                
            case "MT910": // Confirmation of Credit
                result = processMT910(swiftTx, matchingResult);
                break;
                
            case "MT199": // Free Format Message
                result = processMT199(swiftTx, matchingResult);
                break;
                
            case "MT940": // Customer Statement Message
                result = processMT940(swiftTx, matchingResult);
                break;
                
            default:
                log.warn("Unsupported SWIFT message type: {}", swiftTx.getMessageType());
                result.setStatus(ReconciliationStatus.UNSUPPORTED);
        }
        
        result.setEndTime(Instant.now());
        result.setProcessingTimeMs(
            ChronoUnit.MILLIS.between(result.getStartTime(), result.getEndTime())
        );
        
        return result;
    }

    private ReconciliationResult processMT103(SwiftTransaction swiftTx, MatchingResult matchingResult) {
        ReconciliationResult result = new ReconciliationResult();
        
        if (matchingResult.isMatched()) {
            // Update internal transaction
            PaymentTransaction internalTx = paymentService.getTransaction(
                matchingResult.getInternalTransactionId()
            );
            
            // Verify amounts match
            BigDecimal expectedAmount = convertAmount(
                internalTx.getAmount(),
                internalTx.getCurrency(),
                swiftTx.getCurrency(),
                swiftTx.getExchangeRate()
            );
            
            BigDecimal difference = swiftTx.getAmount().subtract(expectedAmount).abs();
            
            if (difference.compareTo(new BigDecimal("0.01")) <= 0) {
                // Amounts match
                result.setStatus(ReconciliationStatus.MATCHED);
                result.setMatchedAmount(swiftTx.getAmount());
                
                // Update transaction status
                paymentService.updateTransactionStatus(
                    internalTx.getId(),
                    TransactionStatus.COMPLETED,
                    "SWIFT_CONFIRMED"
                );
                
            } else {
                // Amount mismatch
                result.setStatus(ReconciliationStatus.AMOUNT_MISMATCH);
                result.setExpectedAmount(expectedAmount);
                result.setActualAmount(swiftTx.getAmount());
                result.setDifference(difference);
                
                // Create investigation case
                createInvestigationCase(swiftTx, result);
            }
            
        } else {
            // No match found - could be incoming transfer
            if (isIncomingTransfer(swiftTx)) {
                processIncomingTransfer(swiftTx);
                result.setStatus(ReconciliationStatus.INCOMING_PROCESSED);
            } else {
                result.setStatus(ReconciliationStatus.UNMATCHED);
                result.setRequiresManualReview(true);
            }
        }
        
        return result;
    }

    private ReconciliationResult processMT202(SwiftTransaction swiftTx, MatchingResult matchingResult) {
        // Bank-to-bank transfer
        ReconciliationResult result = new ReconciliationResult();
        
        // Update nostro/vostro accounts
        correspondentBankService.updateCorrespondentAccount(
            swiftTx.getSenderBic(),
            swiftTx.getAmount(),
            swiftTx.getCurrency(),
            swiftTx.getValueDate()
        );
        
        result.setStatus(ReconciliationStatus.BANK_TRANSFER_PROCESSED);
        return result;
    }

    private ReconciliationResult processMT900(SwiftTransaction swiftTx, MatchingResult matchingResult) {
        // Confirmation of debit
        ReconciliationResult result = new ReconciliationResult();
        
        if (matchingResult.isMatched()) {
            // Confirm debit was applied
            paymentService.confirmDebit(
                matchingResult.getInternalTransactionId(),
                swiftTx.getAmount(),
                swiftTx.getValueDate()
            );
            result.setStatus(ReconciliationStatus.DEBIT_CONFIRMED);
        } else {
            result.setStatus(ReconciliationStatus.UNMATCHED_DEBIT);
            result.setRequiresManualReview(true);
        }
        
        return result;
    }

    private ReconciliationResult processMT910(SwiftTransaction swiftTx, MatchingResult matchingResult) {
        // Confirmation of credit
        ReconciliationResult result = new ReconciliationResult();
        
        if (matchingResult.isMatched()) {
            // Confirm credit was received
            paymentService.confirmCredit(
                matchingResult.getInternalTransactionId(),
                swiftTx.getAmount(),
                swiftTx.getValueDate()
            );
            result.setStatus(ReconciliationStatus.CREDIT_CONFIRMED);
        } else {
            // Process as incoming funds
            processIncomingCredit(swiftTx);
            result.setStatus(ReconciliationStatus.INCOMING_CREDIT_PROCESSED);
        }
        
        return result;
    }

    private ReconciliationResult processMT199(SwiftTransaction swiftTx, MatchingResult matchingResult) {
        // Free format message - could be status update or query
        ReconciliationResult result = new ReconciliationResult();
        
        String messageContent = swiftTx.getRemittanceInfo();
        
        if (messageContent != null && messageContent.contains("TRACE")) {
            // Payment trace request
            handleTraceRequest(swiftTx);
            result.setStatus(ReconciliationStatus.TRACE_PROCESSED);
        } else if (messageContent != null && messageContent.contains("CANCEL")) {
            // Cancellation request
            handleCancellationRequest(swiftTx, matchingResult);
            result.setStatus(ReconciliationStatus.CANCELLATION_PROCESSED);
        } else {
            // General message
            result.setStatus(ReconciliationStatus.MESSAGE_RECEIVED);
            result.setRequiresManualReview(true);
        }
        
        return result;
    }

    private ReconciliationResult processMT940(SwiftTransaction swiftTx, MatchingResult matchingResult) {
        // Customer statement - reconcile multiple transactions
        ReconciliationResult result = new ReconciliationResult();
        
        // Parse statement transactions
        List<StatementTransaction> transactions = parseStatementTransactions(swiftTx);
        
        int matched = 0;
        int unmatched = 0;
        
        for (StatementTransaction stmtTx : transactions) {
            if (reconcileStatementTransaction(stmtTx)) {
                matched++;
            } else {
                unmatched++;
            }
        }
        
        result.setStatus(ReconciliationStatus.STATEMENT_PROCESSED);
        result.setMatchedCount(matched);
        result.setUnmatchedCount(unmatched);
        
        return result;
    }

    private void processFees(SwiftTransaction swiftTx, ReconciliationResult result) {
        if (swiftTx.getCharges() == null || swiftTx.getCharges().compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        
        // Determine fee responsibility
        String feeBearer = determineFeeBearer(swiftTx);
        
        switch (swiftTx.getChargeBearer()) {
            case "OUR": // Sender pays all charges
                chargeSenderAccount(swiftTx, swiftTx.getCharges());
                break;
                
            case "BEN": // Beneficiary pays all charges
                chargeBeneficiaryAccount(swiftTx, swiftTx.getCharges());
                break;
                
            case "SHA": // Shared charges
                BigDecimal senderCharge = calculateSenderCharge(swiftTx);
                BigDecimal beneficiaryCharge = swiftTx.getCharges().subtract(senderCharge);
                
                chargeSenderAccount(swiftTx, senderCharge);
                chargeBeneficiaryAccount(swiftTx, beneficiaryCharge);
                break;
        }
        
        // Record fee transaction
        recordFeeTransaction(swiftTx, result);
    }

    private void updateTransactionStatus(SwiftTransaction swiftTx, ReconciliationResult result) {
        swiftTx.setProcessedAt(Instant.now());
        swiftTx.setReconciliationStatus(result.getStatus());
        swiftTx.setReconciliationDetails(result.toJson());
        
        if (result.getStatus() == ReconciliationStatus.MATCHED || 
            result.getStatus() == ReconciliationStatus.CREDIT_CONFIRMED) {
            swiftTx.setStatus(SwiftStatus.COMPLETED);
        } else if (result.isRequiresManualReview()) {
            swiftTx.setStatus(SwiftStatus.PENDING_REVIEW);
        } else {
            swiftTx.setStatus(SwiftStatus.PROCESSED);
        }
        
        swiftRepository.save(swiftTx);
    }

    private void generateRegulatoryReports(SwiftTransaction swiftTx, ReconciliationResult result) {
        // Check if regulatory reporting is required
        if (!requiresRegulatoryReporting(swiftTx)) {
            return;
        }
        
        // Generate reports based on amount and jurisdiction
        if (swiftTx.getAmount().compareTo(new BigDecimal("10000.00")) >= 0) {
            // CTR - Currency Transaction Report
            generateCTR(swiftTx);
        }
        
        if (swiftTx.isHighRisk() || result.hasSuspiciousIndicators()) {
            // SAR - Suspicious Activity Report
            generateSAR(swiftTx, result);
        }
        
        // FATF reporting for specific countries
        if (requiresFATFReporting(swiftTx)) {
            generateFATFReport(swiftTx);
        }
        
        // Generate audit report
        auditService.createRegulatoryAuditReport(
            swiftTx.getMtn(),
            swiftTx.getAmount(),
            swiftTx.getCurrency(),
            result.getStatus().toString()
        );
    }

    private void sendNotifications(SwiftTransaction swiftTx, ReconciliationResult result) {
        Map<String, Object> notificationData = Map.of(
            "mtn", swiftTx.getMtn(),
            "amount", swiftTx.getAmount(),
            "currency", swiftTx.getCurrency(),
            "status", result.getStatus().toString(),
            "valueDate", swiftTx.getValueDate()
        );
        
        // Send based on status
        if (result.getStatus() == ReconciliationStatus.MATCHED) {
            notificationService.sendSwiftConfirmation(notificationData);
            
        } else if (result.getStatus() == ReconciliationStatus.UNMATCHED || 
                   result.isRequiresManualReview()) {
            // Alert operations team
            notificationService.sendOperationsAlert(
                "SWIFT_RECONCILIATION_ISSUE",
                notificationData
            );
            
            // Create investigation ticket
            createInvestigationTicket(swiftTx, result);
            
        } else if (result.getStatus() == ReconciliationStatus.INCOMING_CREDIT_PROCESSED) {
            // Notify beneficiary of incoming funds
            notifyBeneficiary(swiftTx);
        }
        
        // Send high-value alert
        if (swiftTx.getAmount().compareTo(HIGH_VALUE_THRESHOLD) > 0) {
            notificationService.sendHighValueAlert(
                "SWIFT_HIGH_VALUE",
                notificationData
            );
        }
    }

    private void auditSwiftReconciliation(SwiftTransaction swiftTx, ReconciliationResult result, 
                                          GenericKafkaEvent event) {
        auditService.auditSwiftReconciliation(
            swiftTx.getMtn(),
            swiftTx.getMessageType(),
            swiftTx.getAmount(),
            swiftTx.getCurrency(),
            result.getStatus().toString(),
            event.getEventId()
        );
    }

    private void recordMetrics(SwiftTransaction swiftTx, long startTime) {
        long processingTime = System.currentTimeMillis() - startTime;
        
        metricsService.recordSwiftMetrics(
            swiftTx.getMessageType(),
            swiftTx.getCurrency(),
            swiftTx.getAmount(),
            processingTime,
            swiftTx.getStatus().toString()
        );
    }

    // Helper methods for validation
    private boolean isValidMessageType(String messageType) {
        return Arrays.asList("MT103", "MT202", "MT900", "MT910", "MT199", "MT940")
            .contains(messageType);
    }

    private boolean isValidBic(String bic) {
        return bic != null && bic.matches("^[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?$");
    }

    private boolean isValidCurrency(String currency) {
        return currency != null && currency.matches("^[A-Z]{3}$") && 
               forexService.isSupportedCurrency(currency);
    }

    private boolean isValidUetr(String uetr) {
        return uetr != null && uetr.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    private String extractCountryFromBic(String bic) {
        return bic != null && bic.length() >= 6 ? bic.substring(4, 6) : null;
    }

    private double calculateMatchConfidence(SwiftTransaction swiftTx, PaymentTransaction internalTx) {
        double confidence = 0.0;
        
        // Amount match (40% weight)
        if (swiftTx.getAmount().compareTo(internalTx.getAmount()) == 0) {
            confidence += 0.4;
        } else {
            BigDecimal diff = swiftTx.getAmount().subtract(internalTx.getAmount()).abs();
            BigDecimal percentage = diff.divide(swiftTx.getAmount(), 4, RoundingMode.HALF_UP);
            confidence += Math.max(0, 0.4 * (1 - percentage.doubleValue()));
        }
        
        // Currency match (20% weight)
        if (swiftTx.getCurrency().equals(internalTx.getCurrency())) {
            confidence += 0.2;
        }
        
        // Beneficiary match (20% weight)
        if (swiftTx.getBeneficiaryAccount().equals(internalTx.getBeneficiaryAccount())) {
            confidence += 0.2;
        }
        
        // Date proximity (20% weight)
        long daysDiff = ChronoUnit.DAYS.between(
            swiftTx.getValueDate(),
            internalTx.getValueDate()
        );
        if (daysDiff == 0) {
            confidence += 0.2;
        } else if (daysDiff <= 2) {
            confidence += 0.1;
        }
        
        return confidence;
    }

    private BigDecimal convertAmount(BigDecimal amount, String fromCurrency, String toCurrency, 
                                     BigDecimal providedRate) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }
        
        BigDecimal rate = providedRate != null ? providedRate : 
            forexService.getExchangeRate(fromCurrency, toCurrency);
        
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean isIncomingTransfer(SwiftTransaction swiftTx) {
        return paymentService.isOurAccount(swiftTx.getBeneficiaryAccount());
    }

    private void processIncomingTransfer(SwiftTransaction swiftTx) {
        paymentService.processIncomingSwiftTransfer(
            swiftTx.getBeneficiaryAccount(),
            swiftTx.getAmount(),
            swiftTx.getCurrency(),
            swiftTx.getMtn(),
            swiftTx.getOrderingCustomer(),
            swiftTx.getRemittanceInfo()
        );
    }

    private void processIncomingCredit(SwiftTransaction swiftTx) {
        paymentService.creditAccount(
            swiftTx.getBeneficiaryAccount(),
            swiftTx.getAmount(),
            swiftTx.getCurrency(),
            "SWIFT_CREDIT_" + swiftTx.getMtn()
        );
    }

    // Error handling methods
    private void handleValidationError(GenericKafkaEvent event, ValidationException e) {
        auditService.logValidationError(event.getEventId(), e.getMessage());
        kafkaTemplate.send("swift-reconciliation-validation-errors", event);
    }

    private void handleComplianceError(GenericKafkaEvent event, ComplianceException e) {
        complianceService.createComplianceAlert(
            "SWIFT_COMPLIANCE_FAILURE",
            event.getPayload(),
            e.getMessage()
        );
        kafkaTemplate.send("swift-reconciliation-compliance-errors", event);
    }

    private void handleProcessingError(GenericKafkaEvent event, Exception e, Acknowledgment acknowledgment) {
        String eventId = event.getEventId();
        Integer retryCount = event.getMetadataValue("retryCount", Integer.class, 0);
        
        if (retryCount < MAX_RETRY_ATTEMPTS) {
            long retryDelay = (long) Math.pow(2, retryCount) * 1000;
            
            log.warn("Retrying SWIFT reconciliation event {} after {}ms (attempt {})", 
                    eventId, retryDelay, retryCount + 1);
            
            event.setMetadataValue("retryCount", retryCount + 1);
            event.setMetadataValue("lastError", e.getMessage());
            
            scheduledExecutor.schedule(() -> {
                kafkaTemplate.send("swift-reconciliation-retry", event);
            }, retryDelay, TimeUnit.MILLISECONDS);
            
            acknowledgment.acknowledge();
        } else {
            log.error("Max retries exceeded for SWIFT event {}, sending to DLQ", eventId);
            sendToDLQ(event, e);
            acknowledgment.acknowledge();
        }
    }

    private void sendToDLQ(GenericKafkaEvent event, Exception e) {
        event.setMetadataValue("dlqReason", e.getMessage());
        event.setMetadataValue("dlqTimestamp", Instant.now());
        event.setMetadataValue("originalTopic", "swift-reconciliation-queue");
        
        kafkaTemplate.send("swift-reconciliation-queue.DLQ", event);
        
        alertingService.createDLQAlert(
            "swift-reconciliation-queue",
            event.getEventId(),
            e.getMessage()
        );
    }

    // Fallback method for circuit breaker
    public void handleSwiftReconciliationFailure(GenericKafkaEvent event, String topic, int partition,
                                                 long offset, Acknowledgment acknowledgment, Exception e) {
        log.error("Circuit breaker activated for SWIFT reconciliation: {}", e.getMessage());
        
        failedEventRepository.save(
            FailedEvent.builder()
                .eventId(event.getEventId())
                .topic(topic)
                .payload(event)
                .errorMessage(e.getMessage())
                .createdAt(Instant.now())
                .build()
        );
        
        alertingService.sendCriticalAlert(
            "SWIFT Reconciliation Circuit Breaker Open",
            "SWIFT reconciliation processing is failing. Manual intervention required."
        );
        
        acknowledgment.acknowledge();
    }

    // Helper extraction methods (simplified for brevity)
    private String extractString(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private BigDecimal extractBigDecimal(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return new BigDecimal(value.toString());
    }

    private LocalDate extractLocalDate(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) return null;
        if (value instanceof LocalDate) return (LocalDate) value;
        return LocalDate.parse(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new HashMap<>();
    }

    // Custom exceptions
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    public static class ComplianceException extends RuntimeException {
        public ComplianceException(String message) {
            super(message);
        }
    }
}