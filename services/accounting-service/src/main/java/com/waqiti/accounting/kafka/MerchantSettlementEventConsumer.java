package com.waqiti.accounting.kafka;

import com.waqiti.common.events.MerchantSettlementEvent;
import com.waqiti.common.events.SettlementProcessedEvent;
import com.waqiti.accounting.domain.MerchantAccount;
import com.waqiti.accounting.domain.SettlementEntry;
import com.waqiti.accounting.domain.SettlementStatus;
import com.waqiti.accounting.domain.AccountingEntry;
import com.waqiti.accounting.repository.MerchantAccountRepository;
import com.waqiti.accounting.repository.SettlementRepository;
import com.waqiti.accounting.repository.AccountingEntryRepository;
import com.waqiti.accounting.service.SettlementService;
import com.waqiti.accounting.service.BankTransferService;
import com.waqiti.accounting.exception.SettlementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * CRITICAL FIX: Consumer for MerchantSettlementEvent
 * This was missing and causing merchant payments to be lost
 * 
 * Responsibilities:
 * - Process merchant settlements
 * - Calculate fees and net amounts
 * - Initiate bank transfers
 * - Maintain settlement audit trail
 * 
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class MerchantSettlementEventConsumer {
    
    private final SettlementService settlementService;
    private final MerchantAccountRepository merchantAccountRepository;
    private final SettlementRepository settlementRepository;
    private final AccountingEntryRepository accountingEntryRepository;
    private final BankTransferService bankTransferService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final NotificationService notificationService;
    
    private static final String SETTLEMENT_PROCESSED_TOPIC = "settlement-processed-events";
    private static final String DLQ_TOPIC = "merchant-settlement-events-dlq";
    
    // Fee configuration (should be externalized)
    private static final BigDecimal STANDARD_FEE_PERCENTAGE = new BigDecimal("0.029"); // 2.9%
    private static final BigDecimal FIXED_FEE = new BigDecimal("0.30"); // $0.30
    
    /**
     * Processes merchant settlement events
     * 
     * CRITICAL: This ensures merchants receive their payments
     * Handles fee calculation, bank transfers, and accounting entries
     * 
     * @param event The merchant settlement event
     * @param partition Kafka partition
     * @param offset Message offset
     * @param acknowledgment Manual acknowledgment
     */
    @KafkaListener(
        topics = "merchant-settlement-events",
        groupId = "accounting-service-group",
        containerFactory = "kafkaListenerContainerFactory",
        concurrency = "3"
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Retryable(
        value = {SettlementException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 5000, multiplier = 2)
    )
    public void handleMerchantSettlement(
            @Payload MerchantSettlementEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        String correlationId = String.format("settlement-%s-p%d-o%d",
            event.getMerchantId(), partition, offset);
        
        log.info("Processing merchant settlement: merchantId={}, amount={}, period={}, correlation={}",
            event.getMerchantId(), event.getGrossAmount(), event.getSettlementPeriod(), correlationId);
        
        try {
            // Validate event
            validateEvent(event);
            
            // Check for duplicate processing
            if (settlementService.isSettlementProcessed(event.getSettlementId())) {
                log.info("Settlement already processed: {}", event.getSettlementId());
                acknowledgment.acknowledge();
                return;
            }
            
            // Find merchant account
            MerchantAccount merchantAccount = merchantAccountRepository.findById(event.getMerchantId())
                .orElseThrow(() -> new SettlementException(
                    "Merchant account not found: " + event.getMerchantId()));
            
            // Validate merchant account
            validateMerchantAccount(merchantAccount);
            
            // Process settlement
            SettlementResult result = processSettlement(event, merchantAccount, correlationId);
            
            // Initiate bank transfer
            initiateBankTransfer(result, merchantAccount);
            
            // Send notifications
            notifyMerchantOfSettlement(merchantAccount, result);
            
            // Publish completion event
            publishSettlementProcessed(result, correlationId);
            
            // Acknowledge message
            acknowledgment.acknowledge();
            
            log.info("Successfully processed merchant settlement: settlementId={}, merchantId={}, netAmount={}",
                result.getSettlement().getId(), event.getMerchantId(), result.getNetAmount());
            
        } catch (Exception e) {
            log.error("Failed to process merchant settlement: merchantId={}, settlementId={}, error={}",
                event.getMerchantId(), event.getSettlementId(), e.getMessage(), e);
            
            // Send to DLQ after max retries
            sendToDeadLetterQueue(event, e);
            acknowledgment.acknowledge();
            
            // Alert finance team
            alertService.sendFinanceAlert(
                "SETTLEMENT_FAILED",
                String.format("Failed to process settlement %s for merchant %s: %s",
                    event.getSettlementId(), event.getMerchantId(), e.getMessage()),
                AlertService.Priority.HIGH
            );
        }
    }
    
    /**
     * Process the settlement with fee calculations
     */
    private SettlementResult processSettlement(MerchantSettlementEvent event, 
                                              MerchantAccount merchantAccount,
                                              String correlationId) {
        
        // Calculate fees based on merchant agreement
        FeeCalculation fees = calculateFees(event, merchantAccount);
        
        // Create settlement entry
        SettlementEntry settlement = SettlementEntry.builder()
            .id(event.getSettlementId())
            .merchantId(event.getMerchantId())
            .settlementPeriod(event.getSettlementPeriod())
            .startDate(event.getPeriodStart())
            .endDate(event.getPeriodEnd())
            .transactionCount(event.getTransactionCount())
            .grossAmount(event.getGrossAmount())
            .processingFee(fees.getProcessingFee())
            .fixedFee(fees.getFixedFee())
            .additionalFees(fees.getAdditionalFees())
            .totalFees(fees.getTotalFees())
            .netAmount(fees.getNetAmount())
            .currency(event.getCurrency())
            .status(SettlementStatus.PROCESSING)
            .createdAt(LocalDateTime.now())
            .correlationId(correlationId)
            .build();
        
        // Save settlement
        settlement = settlementRepository.save(settlement);
        
        // Create accounting entries
        createAccountingEntries(settlement, merchantAccount);
        
        // Update merchant account balance
        updateMerchantBalance(merchantAccount, settlement);
        
        return SettlementResult.builder()
            .settlement(settlement)
            .fees(fees)
            .netAmount(fees.getNetAmount())
            .build();
    }
    
    /**
     * Calculate fees for the settlement
     */
    private FeeCalculation calculateFees(MerchantSettlementEvent event, MerchantAccount merchant) {
        BigDecimal grossAmount = event.getGrossAmount();
        
        // Get merchant-specific rates or use defaults
        BigDecimal feePercentage = merchant.getCustomFeeRate() != null ? 
            merchant.getCustomFeeRate() : STANDARD_FEE_PERCENTAGE;
        BigDecimal fixedFeePerTx = merchant.getCustomFixedFee() != null ? 
            merchant.getCustomFixedFee() : FIXED_FEE;
        
        // Calculate percentage-based fee
        BigDecimal processingFee = grossAmount.multiply(feePercentage);
        
        // Calculate fixed fees (per transaction)
        BigDecimal totalFixedFees = fixedFeePerTx.multiply(
            new BigDecimal(event.getTransactionCount())
        );
        
        // Check for volume discounts
        BigDecimal volumeDiscount = calculateVolumeDiscount(grossAmount, merchant);
        
        // Calculate additional fees (chargebacks, etc.)
        BigDecimal additionalFees = event.getChargebackAmount() != null ? 
            event.getChargebackAmount() : BigDecimal.ZERO;
        
        // Total fees
        BigDecimal totalFees = processingFee
            .add(totalFixedFees)
            .add(additionalFees)
            .subtract(volumeDiscount);
        
        // Net amount
        BigDecimal netAmount = grossAmount.subtract(totalFees);
        
        // Ensure net amount is not negative
        if (netAmount.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("Negative net amount calculated for merchant {}: gross={}, fees={}",
                merchant.getId(), grossAmount, totalFees);
            netAmount = BigDecimal.ZERO;
        }
        
        return FeeCalculation.builder()
            .processingFee(processingFee)
            .fixedFee(totalFixedFees)
            .additionalFees(additionalFees)
            .volumeDiscount(volumeDiscount)
            .totalFees(totalFees)
            .netAmount(netAmount)
            .build();
    }
    
    /**
     * Calculate volume discount if applicable
     */
    private BigDecimal calculateVolumeDiscount(BigDecimal amount, MerchantAccount merchant) {
        // Volume tiers (should be configurable)
        if (amount.compareTo(new BigDecimal("100000")) > 0) {
            return amount.multiply(new BigDecimal("0.002")); // 0.2% discount
        } else if (amount.compareTo(new BigDecimal("50000")) > 0) {
            return amount.multiply(new BigDecimal("0.001")); // 0.1% discount
        }
        return BigDecimal.ZERO;
    }
    
    /**
     * Create accounting entries for the settlement
     */
    private void createAccountingEntries(SettlementEntry settlement, MerchantAccount merchant) {
        List<AccountingEntry> entries = new ArrayList<>();
        
        // Debit: Merchant receivable account
        entries.add(AccountingEntry.builder()
            .id(UUID.randomUUID())
            .settlementId(settlement.getId())
            .accountNumber(merchant.getReceivableAccount())
            .debitAmount(settlement.getGrossAmount())
            .creditAmount(BigDecimal.ZERO)
            .description("Merchant settlement - gross amount")
            .entryDate(LocalDate.now())
            .createdAt(LocalDateTime.now())
            .build());
        
        // Credit: Fee revenue account
        if (settlement.getTotalFees().compareTo(BigDecimal.ZERO) > 0) {
            entries.add(AccountingEntry.builder()
                .id(UUID.randomUUID())
                .settlementId(settlement.getId())
                .accountNumber("REVENUE_PROCESSING_FEES")
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(settlement.getTotalFees())
                .description("Processing fees for settlement")
                .entryDate(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .build());
        }
        
        // Credit: Merchant payable account (net amount)
        entries.add(AccountingEntry.builder()
            .id(UUID.randomUUID())
            .settlementId(settlement.getId())
            .accountNumber(merchant.getPayableAccount())
            .debitAmount(BigDecimal.ZERO)
            .creditAmount(settlement.getNetAmount())
            .description("Merchant settlement - net amount")
            .entryDate(LocalDate.now())
            .createdAt(LocalDateTime.now())
            .build());
        
        // Save all entries
        accountingEntryRepository.saveAll(entries);
        
        log.debug("Created {} accounting entries for settlement {}", 
            entries.size(), settlement.getId());
    }
    
    /**
     * Update merchant account balance
     */
    private void updateMerchantBalance(MerchantAccount merchant, SettlementEntry settlement) {
        BigDecimal previousBalance = merchant.getAvailableBalance();
        merchant.setAvailableBalance(previousBalance.add(settlement.getNetAmount()));
        merchant.setLastSettlementDate(LocalDateTime.now());
        merchant.setLastSettlementAmount(settlement.getNetAmount());
        merchantAccountRepository.save(merchant);
        
        log.info("Updated merchant balance: merchantId={}, previousBalance={}, newBalance={}",
            merchant.getId(), previousBalance, merchant.getAvailableBalance());
    }
    
    /**
     * Initiate bank transfer for the settlement
     */
    private void initiateBankTransfer(SettlementResult result, MerchantAccount merchant) {
        try {
            SettlementEntry settlement = result.getSettlement();
            
            // Check if merchant has verified bank account
            if (merchant.getBankAccountId() == null) {
                log.warn("Merchant {} has no bank account configured, settlement held",
                    merchant.getId());
                settlement.setStatus(SettlementStatus.PENDING_BANK_ACCOUNT);
                settlementRepository.save(settlement);
                return;
            }
            
            // Initiate transfer
            BankTransferRequest transferRequest = BankTransferRequest.builder()
                .settlementId(settlement.getId())
                .merchantId(merchant.getId())
                .bankAccountId(merchant.getBankAccountId())
                .amount(settlement.getNetAmount())
                .currency(settlement.getCurrency())
                .description(String.format("Settlement for period %s", 
                    settlement.getSettlementPeriod()))
                .metadata(Map.of(
                    "transactionCount", settlement.getTransactionCount(),
                    "grossAmount", settlement.getGrossAmount(),
                    "fees", settlement.getTotalFees()
                ))
                .build();
            
            BankTransferResult transferResult = bankTransferService.initiateTransfer(transferRequest);
            
            // Update settlement with transfer details
            settlement.setTransferId(transferResult.getTransferId());
            settlement.setTransferInitiatedAt(LocalDateTime.now());
            settlement.setStatus(SettlementStatus.TRANSFER_INITIATED);
            settlementRepository.save(settlement);
            
            log.info("Bank transfer initiated for settlement: settlementId={}, transferId={}",
                settlement.getId(), transferResult.getTransferId());
            
        } catch (Exception e) {
            log.error("Failed to initiate bank transfer for settlement: {}",
                result.getSettlement().getId(), e);
            result.getSettlement().setStatus(SettlementStatus.TRANSFER_FAILED);
            result.getSettlement().setFailureReason(e.getMessage());
            settlementRepository.save(result.getSettlement());
            throw new SettlementException("Bank transfer initiation failed", e);
        }
    }
    
    /**
     * Notify merchant of settlement
     */
    private void notifyMerchantOfSettlement(MerchantAccount merchant, SettlementResult result) {
        try {
            SettlementEntry settlement = result.getSettlement();
            
            Map<String, Object> notificationData = Map.of(
                "settlementId", settlement.getId(),
                "period", settlement.getSettlementPeriod(),
                "grossAmount", settlement.getGrossAmount(),
                "fees", settlement.getTotalFees(),
                "netAmount", settlement.getNetAmount(),
                "transactionCount", settlement.getTransactionCount(),
                "status", settlement.getStatus()
            );
            
            notificationService.sendMerchantNotification(
                merchant.getId(),
                "Settlement Processed",
                String.format("Your settlement of %s %s has been processed. Net amount: %s %s",
                    settlement.getGrossAmount(), settlement.getCurrency(),
                    settlement.getNetAmount(), settlement.getCurrency()),
                notificationData
            );
            
        } catch (Exception e) {
            log.error("Failed to send settlement notification to merchant: {}",
                merchant.getId(), e);
            // Don't fail the settlement if notification fails
        }
    }
    
    /**
     * Validate merchant account
     */
    private void validateMerchantAccount(MerchantAccount account) {
        if (!account.isActive()) {
            throw new SettlementException("Merchant account is not active: " + account.getId());
        }
        if (account.isSettlementHold()) {
            throw new SettlementException("Settlement is on hold for merchant: " + account.getId());
        }
    }
    
    /**
     * Validate settlement event
     */
    private void validateEvent(MerchantSettlementEvent event) {
        if (event.getSettlementId() == null) {
            throw new IllegalArgumentException("Settlement ID is required");
        }
        if (event.getMerchantId() == null) {
            throw new IllegalArgumentException("Merchant ID is required");
        }
        if (event.getGrossAmount() == null || event.getGrossAmount().signum() < 0) {
            throw new IllegalArgumentException("Invalid gross amount: " + event.getGrossAmount());
        }
        if (event.getTransactionCount() == null || event.getTransactionCount() <= 0) {
            throw new IllegalArgumentException("Invalid transaction count: " + event.getTransactionCount());
        }
    }
    
    /**
     * Publish settlement processed event
     */
    private void publishSettlementProcessed(SettlementResult result, String correlationId) {
        SettlementProcessedEvent event = SettlementProcessedEvent.builder()
            .settlementId(result.getSettlement().getId())
            .merchantId(result.getSettlement().getMerchantId())
            .netAmount(result.getNetAmount())
            .status(result.getSettlement().getStatus().name())
            .processedAt(Instant.now())
            .correlationId(correlationId)
            .build();
        
        kafkaTemplate.send(SETTLEMENT_PROCESSED_TOPIC, event);
    }
    
    /**
     * Send failed events to dead letter queue
     */
    private void sendToDeadLetterQueue(MerchantSettlementEvent event, Exception error) {
        try {
            Map<String, Object> dlqMessage = new HashMap<>();
            dlqMessage.put("originalEvent", event);
            dlqMessage.put("errorMessage", error.getMessage());
            dlqMessage.put("errorClass", error.getClass().getName());
            dlqMessage.put("failedAt", Instant.now());
            dlqMessage.put("service", "accounting-service");
            
            kafkaTemplate.send(DLQ_TOPIC, dlqMessage);
            
            log.warn("Sent failed settlement event to DLQ: merchantId={}, settlementId={}",
                event.getMerchantId(), event.getSettlementId());
            
        } catch (Exception dlqError) {
            log.error("Failed to send settlement event to DLQ", dlqError);
        }
    }
    
    @lombok.Data
    @lombok.Builder
    private static class SettlementResult {
        private SettlementEntry settlement;
        private FeeCalculation fees;
        private BigDecimal netAmount;
    }
    
    @lombok.Data
    @lombok.Builder
    private static class FeeCalculation {
        private BigDecimal processingFee;
        private BigDecimal fixedFee;
        private BigDecimal additionalFees;
        private BigDecimal volumeDiscount;
        private BigDecimal totalFees;
        private BigDecimal netAmount;
    }
}