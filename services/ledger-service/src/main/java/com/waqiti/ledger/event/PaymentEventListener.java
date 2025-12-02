package com.waqiti.ledger.event;

import com.waqiti.common.event.PaymentCompletedEvent;
import com.waqiti.ledger.dto.CreateJournalEntryRequest;
import com.waqiti.ledger.dto.JournalEntryLine;
import com.waqiti.ledger.service.LedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Event listener for payment-related events.
 * Automatically creates ledger entries for completed payments.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final LedgerService ledgerService;

    /**
     * Handles payment completed events and creates corresponding ledger entries.
     * This ensures automatic double-entry bookkeeping for all payments.
     */
    @KafkaListener(
        topics = "${kafka.topics.payment-completed:payment-completed}",
        groupId = "${spring.kafka.consumer.group-id:ledger-service}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void handlePaymentCompleted(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("Received payment-completed event: transactionId={}, amount={}, currency={}", 
            event.getTransactionId(), event.getAmount(), event.getCurrency());
        
        try {
            // Create journal entry for the payment
            CreateJournalEntryRequest journalEntry = createJournalEntryFromPayment(event);
            
            // Post to ledger
            ledgerService.createJournalEntry(journalEntry);
            
            log.info("Successfully created ledger entries for payment: transactionId={}", 
                event.getTransactionId());
            
            // Acknowledge the message after successful processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
            }
            
        } catch (Exception e) {
            log.error("Failed to create ledger entries for payment: transactionId={}", 
                event.getTransactionId(), e);
            
            // Re-throw to trigger retry mechanism
            throw new RuntimeException("Failed to process payment-completed event", e);
        }
    }

    /**
     * Creates a journal entry from a payment event.
     * Implements double-entry bookkeeping rules.
     */
    private CreateJournalEntryRequest createJournalEntryFromPayment(PaymentCompletedEvent event) {
        List<JournalEntryLine> lines = new ArrayList<>();
        
        // Determine account codes based on payment type
        String debitAccount;
        String creditAccount;
        String description;
        
        switch (event.getPaymentType()) {
            case "TRANSFER":
                // P2P Transfer
                debitAccount = getAccountCode("USER_WALLET", event.getToUserId());
                creditAccount = getAccountCode("USER_WALLET", event.getFromUserId());
                description = String.format("P2P Transfer from %s to %s", 
                    event.getFromUserId(), event.getToUserId());
                break;
                
            case "DEPOSIT":
                // Deposit to wallet
                debitAccount = getAccountCode("USER_WALLET", event.getToUserId());
                creditAccount = getAccountCode("BANK_CLEARING", null);
                description = String.format("Deposit to wallet %s", event.getToUserId());
                break;
                
            case "WITHDRAWAL":
                // Withdrawal from wallet
                debitAccount = getAccountCode("BANK_CLEARING", null);
                creditAccount = getAccountCode("USER_WALLET", event.getFromUserId());
                description = String.format("Withdrawal from wallet %s", event.getFromUserId());
                break;
                
            case "PAYMENT":
                // Merchant payment
                debitAccount = getAccountCode("MERCHANT_RECEIVABLE", event.getMerchantId());
                creditAccount = getAccountCode("USER_WALLET", event.getFromUserId());
                description = String.format("Payment to merchant %s", event.getMerchantId());
                break;
                
            case "FEE":
                // Transaction fee
                debitAccount = getAccountCode("FEE_REVENUE", null);
                creditAccount = getAccountCode("USER_WALLET", event.getFromUserId());
                description = "Transaction fee";
                break;
                
            default:
                // Default case for unknown payment types
                debitAccount = getAccountCode("SUSPENSE", null);
                creditAccount = getAccountCode("SUSPENSE", null);
                description = String.format("Unknown payment type: %s", event.getPaymentType());
                log.warn("Unknown payment type: {}", event.getPaymentType());
        }
        
        // Create debit entry
        lines.add(JournalEntryLine.builder()
            .accountCode(debitAccount)
            .debitAmount(event.getAmount())
            .creditAmount(BigDecimal.ZERO)
            .description(description)
            .build());
        
        // Create credit entry
        lines.add(JournalEntryLine.builder()
            .accountCode(creditAccount)
            .debitAmount(BigDecimal.ZERO)
            .creditAmount(event.getAmount())
            .description(description)
            .build());
        
        // Handle transaction fees if present
        if (event.getFeeAmount() != null && event.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            // Debit: Fee Revenue Account
            lines.add(JournalEntryLine.builder()
                .accountCode(getAccountCode("FEE_REVENUE", null))
                .debitAmount(event.getFeeAmount())
                .creditAmount(BigDecimal.ZERO)
                .description("Transaction fee revenue")
                .build());
            
            // Credit: User Wallet (fee deduction)
            lines.add(JournalEntryLine.builder()
                .accountCode(getAccountCode("USER_WALLET", event.getFromUserId()))
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(event.getFeeAmount())
                .description("Transaction fee deduction")
                .build());
        }
        
        return CreateJournalEntryRequest.builder()
            .date(event.getTimestamp() != null ? event.getTimestamp().toLocalDate() : LocalDateTime.now().toLocalDate())
            .description(description)
            .reference("TXN-" + event.getTransactionId())
            .lines(lines)
            .metadata(buildMetadata(event))
            .build();
    }
    
    /**
     * Generates account code based on account type and entity ID.
     */
    private String getAccountCode(String accountType, UUID entityId) {
        // This would typically look up the actual account code from a mapping
        // For now, using a simple convention
        String baseCode;
        
        switch (accountType) {
            case "USER_WALLET":
                baseCode = "1100"; // Asset: User Wallets
                break;
            case "BANK_CLEARING":
                baseCode = "1200"; // Asset: Bank Clearing Account
                break;
            case "MERCHANT_RECEIVABLE":
                baseCode = "1300"; // Asset: Merchant Receivables
                break;
            case "FEE_REVENUE":
                baseCode = "4100"; // Revenue: Transaction Fees
                break;
            case "SUSPENSE":
                baseCode = "9999"; // Suspense Account
                break;
            default:
                baseCode = "9999";
        }
        
        // Append entity ID if provided
        if (entityId != null) {
            return baseCode + "-" + entityId.toString().substring(0, 8).toUpperCase();
        }
        
        return baseCode;
    }
    
    /**
     * Builds metadata for the journal entry.
     */
    private java.util.Map<String, Object> buildMetadata(PaymentCompletedEvent event) {
        java.util.Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("transactionId", event.getTransactionId());
        metadata.put("paymentType", event.getPaymentType());
        metadata.put("currency", event.getCurrency());
        metadata.put("fromUserId", event.getFromUserId());
        metadata.put("toUserId", event.getToUserId());
        metadata.put("timestamp", event.getTimestamp());
        
        if (event.getMerchantId() != null) {
            metadata.put("merchantId", event.getMerchantId());
        }
        
        if (event.getPaymentMethodId() != null) {
            metadata.put("paymentMethodId", event.getPaymentMethodId());
        }
        
        return metadata;
    }
}