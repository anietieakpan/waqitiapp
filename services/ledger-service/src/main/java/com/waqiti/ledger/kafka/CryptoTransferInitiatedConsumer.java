package com.waqiti.ledger.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.ledger.entity.LedgerEntry;
import com.waqiti.ledger.entity.JournalEntry;
import com.waqiti.ledger.service.LedgerService;
import com.waqiti.ledger.service.JournalEntryService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Kafka consumer for CryptoTransferInitiated events
 * Creates ledger entries for crypto transfers to maintain accurate financial records
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CryptoTransferInitiatedConsumer extends BaseKafkaConsumer {

    private final LedgerService ledgerService;
    private final JournalEntryService journalEntryService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "crypto-transfer-initiated", groupId = "ledger-service-group")
    @CircuitBreaker(name = "crypto-transfer-consumer")
    @Retry(name = "crypto-transfer-consumer")
    @Transactional
    public void handleCryptoTransferInitiated(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "crypto-transfer-initiated");
        
        try {
            log.info("Processing crypto transfer initiated event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            
            String transferId = eventData.path("transferId").asText();
            String fromAddress = eventData.path("fromAddress").asText();
            String toAddress = eventData.path("toAddress").asText();
            String cryptoCurrency = eventData.path("currency").asText();
            BigDecimal amount = new BigDecimal(eventData.path("amount").asText());
            BigDecimal feeAmount = new BigDecimal(eventData.path("feeAmount").asText("0"));
            String userId = eventData.path("userId").asText();
            LocalDateTime timestamp = LocalDateTime.parse(eventData.path("timestamp").asText());
            
            log.info("Creating ledger entries for crypto transfer: transferId={}, currency={}, amount={}", 
                    transferId, cryptoCurrency, amount);
            
            // Create ledger entries for crypto transfer
            createCryptoTransferLedgerEntries(transferId, fromAddress, toAddress, cryptoCurrency, 
                    amount, feeAmount, userId, timestamp);
            
            ack.acknowledge();
            log.info("Successfully processed crypto transfer event: transferId={}", transferId);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse crypto transfer event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            log.error("Error processing crypto transfer event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void createCryptoTransferLedgerEntries(String transferId, String fromAddress, String toAddress,
                                                   String cryptoCurrency, BigDecimal amount, BigDecimal feeAmount,
                                                   String userId, LocalDateTime timestamp) {
        
        // Create journal entry for the crypto transfer
        JournalEntry journalEntry = JournalEntry.builder()
                .id(UUID.randomUUID())
                .referenceId(transferId)
                .referenceType("CRYPTO_TRANSFER")
                .description(String.format("Crypto transfer: %s %s from %s to %s", 
                        amount, cryptoCurrency, fromAddress.substring(0, 8) + "...", toAddress.substring(0, 8) + "..."))
                .transactionDate(timestamp)
                .createdBy(userId)
                .status("PENDING")
                .build();
        
        journalEntry = journalEntryService.createJournalEntry(journalEntry);
        
        // Debit entry: Crypto asset leaving user's wallet
        LedgerEntry debitEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .journalEntryId(journalEntry.getId())
                .accountCode(getCryptoAssetAccountCode(cryptoCurrency))
                .accountName(String.format("%s Digital Asset", cryptoCurrency))
                .debitAmount(amount)
                .creditAmount(BigDecimal.ZERO)
                .description(String.format("Crypto transfer out: %s %s to %s", 
                        amount, cryptoCurrency, toAddress.substring(0, 8) + "..."))
                .referenceId(transferId)
                .userId(userId)
                .transactionDate(timestamp)
                .counterpartyAddress(toAddress)
                .cryptoCurrency(cryptoCurrency)
                .status("PENDING")
                .build();
        
        ledgerService.createLedgerEntry(debitEntry);
        
        // Credit entry: Crypto liability (pending confirmation)
        LedgerEntry creditEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .journalEntryId(journalEntry.getId())
                .accountCode(getCryptoPendingAccountCode(cryptoCurrency))
                .accountName(String.format("%s Transfer Pending", cryptoCurrency))
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(amount)
                .description(String.format("Crypto transfer pending: %s %s to %s", 
                        amount, cryptoCurrency, toAddress.substring(0, 8) + "..."))
                .referenceId(transferId)
                .userId(userId)
                .transactionDate(timestamp)
                .counterpartyAddress(toAddress)
                .cryptoCurrency(cryptoCurrency)
                .status("PENDING")
                .build();
        
        ledgerService.createLedgerEntry(creditEntry);
        
        // If there's a fee, record it
        if (feeAmount.compareTo(BigDecimal.ZERO) > 0) {
            createFeeEntry(journalEntry.getId(), transferId, cryptoCurrency, feeAmount, userId, timestamp);
        }
        
        log.info("Created ledger entries for crypto transfer: transferId={}, debit={}, credit={}", 
                transferId, debitEntry.getId(), creditEntry.getId());
    }
    
    private void createFeeEntry(UUID journalEntryId, String transferId, String cryptoCurrency, 
                               BigDecimal feeAmount, String userId, LocalDateTime timestamp) {
        
        // Debit entry: Fee expense
        LedgerEntry feeExpenseEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .journalEntryId(journalEntryId)
                .accountCode("6100")
                .accountName("Crypto Transfer Fees")
                .debitAmount(feeAmount)
                .creditAmount(BigDecimal.ZERO)
                .description(String.format("Transfer fee: %s %s", feeAmount, cryptoCurrency))
                .referenceId(transferId)
                .userId(userId)
                .transactionDate(timestamp)
                .cryptoCurrency(cryptoCurrency)
                .status("PENDING")
                .build();
        
        ledgerService.createLedgerEntry(feeExpenseEntry);
        
        // Credit entry: Fee payable
        LedgerEntry feePayableEntry = LedgerEntry.builder()
                .id(UUID.randomUUID())
                .journalEntryId(journalEntryId)
                .accountCode("2300")
                .accountName("Network Fees Payable")
                .debitAmount(BigDecimal.ZERO)
                .creditAmount(feeAmount)
                .description(String.format("Network fee payable: %s %s", feeAmount, cryptoCurrency))
                .referenceId(transferId)
                .userId(userId)
                .transactionDate(timestamp)
                .cryptoCurrency(cryptoCurrency)
                .status("PENDING")
                .build();
        
        ledgerService.createLedgerEntry(feePayableEntry);
    }
    
    private String getCryptoAssetAccountCode(String cryptoCurrency) {
        // Chart of accounts for crypto assets
        switch (cryptoCurrency.toUpperCase()) {
            case "BTC":
                return "1210";
            case "ETH":
                return "1211";
            case "USDC":
                return "1212";
            case "USDT":
                return "1213";
            default:
                return "1219"; // Other crypto assets
        }
    }
    
    private String getCryptoPendingAccountCode(String cryptoCurrency) {
        // Chart of accounts for pending crypto transfers
        switch (cryptoCurrency.toUpperCase()) {
            case "BTC":
                return "1310";
            case "ETH":
                return "1311";
            case "USDC":
                return "1312";
            case "USDT":
                return "1313";
            default:
                return "1319"; // Other pending crypto transfers
        }
    }
}