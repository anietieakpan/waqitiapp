package com.waqiti.investment.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.investment.service.InvestmentTransactionService;
import com.waqiti.investment.service.PortfolioManagementService;
import com.waqiti.investment.service.InvestmentAccountingService;
import com.waqiti.investment.service.InvestmentNotificationService;
import com.waqiti.common.audit.AuditService;
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
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InvestmentTransactionConsumer {
    
    private final InvestmentTransactionService investmentTransactionService;
    private final PortfolioManagementService portfolioManagementService;
    private final InvestmentAccountingService investmentAccountingService;
    private final InvestmentNotificationService investmentNotificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;
    
    @KafkaListener(
        topics = {"investment-transactions", "investment-transaction-events"},
        groupId = "investment-service-transaction-group",
        containerFactory = "criticalFinancialKafkaListenerContainerFactory"
    )
    @RetryableTopic(
        attempts = "3",
        backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 20000),
        dltStrategy = org.springframework.kafka.retrytopic.DltStrategy.FAIL_ON_ERROR,
        include = {Exception.class},
        exclude = {IllegalArgumentException.class}
    )
    @Transactional
    public void handleInvestmentTransaction(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {
        
        log.info("INVESTMENT TRANSACTION: Processing investment transaction - Topic: {}, Partition: {}, Offset: {}",
                topic, partition, offset);

        LocalDateTime processingStartTime = LocalDateTime.now();
        UUID transactionId = null;
        UUID investorId = null;
        String transactionType = null;
        String idempotencyKey = null;
        UUID operationId = UUID.randomUUID();

        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);

            transactionId = UUID.fromString((String) event.get("transactionId"));
            investorId = UUID.fromString((String) event.get("investorId"));
            UUID portfolioId = UUID.fromString((String) event.get("portfolioId"));
            transactionType = (String) event.get("transactionType");

            // CRITICAL SECURITY: Idempotency check - prevent duplicate investment transaction processing
            idempotencyKey = String.format("investment-transaction:%s:%s:%s",
                transactionId, investorId, event.get("executionTimestamp"));

            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate investment transaction event ignored: transactionId={}, investorId={}, idempotencyKey={}",
                        transactionId, investorId, idempotencyKey);
                acknowledgment.acknowledge();
                return;
            }

            log.info("SECURITY: Processing new investment transaction with persistent idempotency: transactionId={}, investorId={}, type={}, idempotencyKey={}",
                transactionId, investorId, transactionType, idempotencyKey);
            String transactionStatus = (String) event.get("transactionStatus");
            String assetType = (String) event.get("assetType");
            String assetSymbol = (String) event.get("assetSymbol");
            String assetName = (String) event.get("assetName");
            BigDecimal quantity = new BigDecimal(event.get("quantity").toString());
            BigDecimal pricePerUnit = new BigDecimal(event.get("pricePerUnit").toString());
            BigDecimal totalAmount = new BigDecimal(event.get("totalAmount").toString());
            String currency = (String) event.get("currency");
            BigDecimal fees = event.containsKey("fees") ? 
                    new BigDecimal(event.get("fees").toString()) : BigDecimal.ZERO;
            BigDecimal taxes = event.containsKey("taxes") ? 
                    new BigDecimal(event.get("taxes").toString()) : BigDecimal.ZERO;
            LocalDateTime executionTimestamp = LocalDateTime.parse((String) event.get("executionTimestamp"));
            String orderType = (String) event.get("orderType");
            String exchangeName = (String) event.get("exchangeName");
            UUID orderId = event.containsKey("orderId") ? 
                    UUID.fromString((String) event.get("orderId")) : null;
            
            log.info("Investment transaction - TxnId: {}, InvestorId: {}, Type: {}, Status: {}, Asset: {} ({}), Qty: {}, Price: {} {}, Total: {} {}", 
                    transactionId, investorId, transactionType, transactionStatus, assetSymbol, 
                    assetType, quantity, pricePerUnit, currency, totalAmount, currency);
            
            validateInvestmentTransaction(transactionId, investorId, portfolioId, transactionType, 
                    transactionStatus, quantity, pricePerUnit, totalAmount);
            
            processTransactionByType(transactionId, investorId, portfolioId, transactionType, 
                    transactionStatus, assetType, assetSymbol, assetName, quantity, pricePerUnit, 
                    totalAmount, currency, fees, taxes, executionTimestamp, orderType, exchangeName, orderId);
            
            if ("EXECUTED".equals(transactionStatus)) {
                handleExecutedTransaction(transactionId, investorId, portfolioId, transactionType, 
                        assetType, assetSymbol, quantity, pricePerUnit, totalAmount, currency, 
                        fees, taxes, executionTimestamp);
            } else if ("FAILED".equals(transactionStatus)) {
                handleFailedTransaction(transactionId, investorId, portfolioId, transactionType, 
                        assetSymbol, totalAmount, currency);
            } else if ("PENDING".equals(transactionStatus)) {
                handlePendingTransaction(transactionId, investorId, portfolioId, transactionType, 
                        assetSymbol, quantity, pricePerUnit);
            }
            
            updatePortfolioHoldings(portfolioId, transactionType, assetType, assetSymbol, 
                    quantity, totalAmount, currency);
            
            recordInvestmentAccounting(transactionId, investorId, portfolioId, transactionType, 
                    assetType, totalAmount, currency, fees, taxes);
            
            calculatePortfolioMetrics(portfolioId, investorId);
            
            notifyInvestor(investorId, transactionId, transactionType, transactionStatus, 
                    assetSymbol, quantity, totalAmount, currency);
            
            updateInvestmentMetrics(transactionType, transactionStatus, assetType, totalAmount);
            
            // CRITICAL SECURITY: Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("transactionId", transactionId.toString(),
                       "investorId", investorId.toString(),
                       "portfolioId", portfolioId.toString(),
                       "transactionType", transactionType,
                       "transactionStatus", transactionStatus,
                       "assetSymbol", assetSymbol,
                       "quantity", quantity.toString(),
                       "totalAmount", totalAmount.toString(),
                       "currency", currency,
                       "status", "COMPLETED"), Duration.ofDays(7));

            auditInvestmentTransaction(transactionId, investorId, portfolioId, transactionType,
                    transactionStatus, assetSymbol, quantity, totalAmount, currency,
                    processingStartTime);

            long processingTimeMs = java.time.Duration.between(processingStartTime, LocalDateTime.now()).toMillis();

            log.info("Investment transaction processed - TxnId: {}, Type: {}, Status: {}, ProcessingTime: {}ms",
                    transactionId, transactionType, transactionStatus, processingTimeMs);

            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("SECURITY: Investment transaction processing failed - TxnId: {}, InvestorId: {}, Type: {}, Error: {}",
                    transactionId, investorId, transactionType, e.getMessage(), e);

            // CRITICAL SECURITY: Mark operation as failed for retry logic
            if (idempotencyKey != null) {
                idempotencyService.failOperation(idempotencyKey, operationId, e.getMessage());
            }
            
            if (transactionId != null && investorId != null) {
                handleTransactionFailure(transactionId, investorId, transactionType, e);
            }
            
            throw new RuntimeException("Investment transaction processing failed", e);
        }
    }
    
    private void validateInvestmentTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                              String transactionType, String transactionStatus,
                                              BigDecimal quantity, BigDecimal pricePerUnit,
                                              BigDecimal totalAmount) {
        if (transactionId == null || investorId == null || portfolioId == null) {
            throw new IllegalArgumentException("Transaction ID, Investor ID, and Portfolio ID are required");
        }
        
        if (transactionType == null || transactionType.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction type is required");
        }
        
        if (transactionStatus == null || transactionStatus.trim().isEmpty()) {
            throw new IllegalArgumentException("Transaction status is required");
        }
        
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid quantity");
        }
        
        if (pricePerUnit == null || pricePerUnit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid price per unit");
        }
        
        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid total amount");
        }
        
        log.debug("Investment transaction validation passed - TxnId: {}", transactionId);
    }
    
    private void processTransactionByType(UUID transactionId, UUID investorId, UUID portfolioId,
                                         String transactionType, String transactionStatus,
                                         String assetType, String assetSymbol, String assetName,
                                         BigDecimal quantity, BigDecimal pricePerUnit,
                                         BigDecimal totalAmount, String currency, BigDecimal fees,
                                         BigDecimal taxes, LocalDateTime executionTimestamp,
                                         String orderType, String exchangeName, UUID orderId) {
        try {
            switch (transactionType) {
                case "BUY" -> processBuyTransaction(transactionId, investorId, portfolioId, assetType, 
                        assetSymbol, assetName, quantity, pricePerUnit, totalAmount, currency, fees, 
                        taxes, executionTimestamp);
                
                case "SELL" -> processSellTransaction(transactionId, investorId, portfolioId, assetType, 
                        assetSymbol, quantity, pricePerUnit, totalAmount, currency, fees, taxes, 
                        executionTimestamp);
                
                case "DIVIDEND" -> processDividendTransaction(transactionId, investorId, portfolioId, 
                        assetSymbol, totalAmount, currency, executionTimestamp);
                
                case "INTEREST" -> processInterestTransaction(transactionId, investorId, portfolioId, 
                        assetSymbol, totalAmount, currency, executionTimestamp);
                
                case "DEPOSIT" -> processDepositTransaction(transactionId, investorId, portfolioId, 
                        totalAmount, currency, executionTimestamp);
                
                case "WITHDRAWAL" -> processWithdrawalTransaction(transactionId, investorId, portfolioId, 
                        totalAmount, currency, executionTimestamp);
                
                case "TRANSFER_IN" -> processTransferInTransaction(transactionId, investorId, portfolioId, 
                        assetType, assetSymbol, quantity, pricePerUnit, totalAmount, currency);
                
                case "TRANSFER_OUT" -> processTransferOutTransaction(transactionId, investorId, portfolioId, 
                        assetType, assetSymbol, quantity, pricePerUnit, totalAmount, currency);
                
                default -> {
                    log.warn("Unknown investment transaction type: {}", transactionType);
                    processGenericTransaction(transactionId, investorId, portfolioId, transactionType);
                }
            }
            
            log.debug("Transaction type processing completed - TxnId: {}, Type: {}", 
                    transactionId, transactionType);
            
        } catch (Exception e) {
            log.error("Failed to process transaction by type - TxnId: {}, Type: {}", 
                    transactionId, transactionType, e);
            throw new RuntimeException("Transaction type processing failed", e);
        }
    }
    
    private void processBuyTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                      String assetType, String assetSymbol, String assetName,
                                      BigDecimal quantity, BigDecimal pricePerUnit, BigDecimal totalAmount,
                                      String currency, BigDecimal fees, BigDecimal taxes,
                                      LocalDateTime executionTimestamp) {
        log.info("Processing BUY transaction - TxnId: {}, Asset: {}, Qty: {}, Price: {} {}", 
                transactionId, assetSymbol, quantity, pricePerUnit, currency);
        
        investmentTransactionService.processBuy(transactionId, investorId, portfolioId, assetType, 
                assetSymbol, assetName, quantity, pricePerUnit, totalAmount, currency, fees, taxes, 
                executionTimestamp);
    }
    
    private void processSellTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                       String assetType, String assetSymbol, BigDecimal quantity,
                                       BigDecimal pricePerUnit, BigDecimal totalAmount, String currency,
                                       BigDecimal fees, BigDecimal taxes, LocalDateTime executionTimestamp) {
        log.info("Processing SELL transaction - TxnId: {}, Asset: {}, Qty: {}, Price: {} {}", 
                transactionId, assetSymbol, quantity, pricePerUnit, currency);
        
        investmentTransactionService.processSell(transactionId, investorId, portfolioId, assetType, 
                assetSymbol, quantity, pricePerUnit, totalAmount, currency, fees, taxes, 
                executionTimestamp);
        
        BigDecimal capitalGain = investmentTransactionService.calculateCapitalGain(portfolioId, 
                assetSymbol, quantity, pricePerUnit);
        
        if (capitalGain.compareTo(BigDecimal.ZERO) != 0) {
            log.info("Capital gain calculated - TxnId: {}, Asset: {}, Gain: {} {}", 
                    transactionId, assetSymbol, capitalGain, currency);
        }
    }
    
    private void processDividendTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                           String assetSymbol, BigDecimal totalAmount, String currency,
                                           LocalDateTime executionTimestamp) {
        log.info("Processing DIVIDEND transaction - TxnId: {}, Asset: {}, Amount: {} {}", 
                transactionId, assetSymbol, totalAmount, currency);
        
        investmentTransactionService.processDividend(transactionId, investorId, portfolioId, 
                assetSymbol, totalAmount, currency, executionTimestamp);
    }
    
    private void processInterestTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                           String assetSymbol, BigDecimal totalAmount, String currency,
                                           LocalDateTime executionTimestamp) {
        log.info("Processing INTEREST transaction - TxnId: {}, Asset: {}, Amount: {} {}", 
                transactionId, assetSymbol, totalAmount, currency);
        
        investmentTransactionService.processInterest(transactionId, investorId, portfolioId, 
                assetSymbol, totalAmount, currency, executionTimestamp);
    }
    
    private void processDepositTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                          BigDecimal totalAmount, String currency,
                                          LocalDateTime executionTimestamp) {
        log.info("Processing DEPOSIT transaction - TxnId: {}, Amount: {} {}", 
                transactionId, totalAmount, currency);
        
        investmentTransactionService.processDeposit(transactionId, investorId, portfolioId, 
                totalAmount, currency, executionTimestamp);
    }
    
    private void processWithdrawalTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                             BigDecimal totalAmount, String currency,
                                             LocalDateTime executionTimestamp) {
        log.info("Processing WITHDRAWAL transaction - TxnId: {}, Amount: {} {}", 
                transactionId, totalAmount, currency);
        
        investmentTransactionService.processWithdrawal(transactionId, investorId, portfolioId, 
                totalAmount, currency, executionTimestamp);
    }
    
    private void processTransferInTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                             String assetType, String assetSymbol, BigDecimal quantity,
                                             BigDecimal pricePerUnit, BigDecimal totalAmount, String currency) {
        log.info("Processing TRANSFER_IN transaction - TxnId: {}, Asset: {}, Qty: {}", 
                transactionId, assetSymbol, quantity);
        
        investmentTransactionService.processTransferIn(transactionId, investorId, portfolioId, 
                assetType, assetSymbol, quantity, pricePerUnit, totalAmount, currency);
    }
    
    private void processTransferOutTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                              String assetType, String assetSymbol, BigDecimal quantity,
                                              BigDecimal pricePerUnit, BigDecimal totalAmount, String currency) {
        log.info("Processing TRANSFER_OUT transaction - TxnId: {}, Asset: {}, Qty: {}", 
                transactionId, assetSymbol, quantity);
        
        investmentTransactionService.processTransferOut(transactionId, investorId, portfolioId, 
                assetType, assetSymbol, quantity, pricePerUnit, totalAmount, currency);
    }
    
    private void processGenericTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                          String transactionType) {
        log.info("Processing generic transaction - TxnId: {}, Type: {}", transactionId, transactionType);
        
        investmentTransactionService.processGeneric(transactionId, investorId, portfolioId, 
                transactionType);
    }
    
    private void handleExecutedTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                          String transactionType, String assetType, String assetSymbol,
                                          BigDecimal quantity, BigDecimal pricePerUnit,
                                          BigDecimal totalAmount, String currency, BigDecimal fees,
                                          BigDecimal taxes, LocalDateTime executionTimestamp) {
        try {
            investmentTransactionService.recordExecutedTransaction(transactionId, investorId, 
                    portfolioId, transactionType, assetType, assetSymbol, quantity, pricePerUnit, 
                    totalAmount, currency, fees, taxes, executionTimestamp);
            
        } catch (Exception e) {
            log.error("Failed to handle executed transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleFailedTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                        String transactionType, String assetSymbol,
                                        BigDecimal totalAmount, String currency) {
        try {
            log.error("Processing failed transaction - TxnId: {}, Asset: {}", transactionId, assetSymbol);
            
            investmentTransactionService.recordFailedTransaction(transactionId, investorId, 
                    portfolioId, transactionType, assetSymbol, totalAmount, currency);
            
        } catch (Exception e) {
            log.error("Failed to handle failed transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handlePendingTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                         String transactionType, String assetSymbol,
                                         BigDecimal quantity, BigDecimal pricePerUnit) {
        try {
            log.info("Processing pending transaction - TxnId: {}, Asset: {}", transactionId, assetSymbol);
            
            investmentTransactionService.trackPendingTransaction(transactionId, investorId, 
                    portfolioId, transactionType, assetSymbol, quantity, pricePerUnit);
            
        } catch (Exception e) {
            log.error("Failed to handle pending transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void updatePortfolioHoldings(UUID portfolioId, String transactionType, String assetType,
                                        String assetSymbol, BigDecimal quantity,
                                        BigDecimal totalAmount, String currency) {
        try {
            portfolioManagementService.updateHoldings(portfolioId, transactionType, assetType, 
                    assetSymbol, quantity, totalAmount, currency);
            
            log.debug("Portfolio holdings updated - PortfolioId: {}, Asset: {}", 
                    portfolioId, assetSymbol);
            
        } catch (Exception e) {
            log.error("Failed to update portfolio holdings - PortfolioId: {}", portfolioId, e);
        }
    }
    
    private void recordInvestmentAccounting(UUID transactionId, UUID investorId, UUID portfolioId,
                                           String transactionType, String assetType,
                                           BigDecimal totalAmount, String currency,
                                           BigDecimal fees, BigDecimal taxes) {
        try {
            investmentAccountingService.recordTransactionAccounting(transactionId, investorId, 
                    portfolioId, transactionType, assetType, totalAmount, currency, fees, taxes);
            
            log.debug("Investment accounting recorded - TxnId: {}", transactionId);
            
        } catch (Exception e) {
            log.error("Failed to record investment accounting - TxnId: {}", transactionId, e);
        }
    }
    
    private void calculatePortfolioMetrics(UUID portfolioId, UUID investorId) {
        try {
            portfolioManagementService.calculatePortfolioMetrics(portfolioId, investorId);
            
            log.debug("Portfolio metrics calculated - PortfolioId: {}", portfolioId);
            
        } catch (Exception e) {
            log.error("Failed to calculate portfolio metrics - PortfolioId: {}", portfolioId, e);
        }
    }
    
    private void notifyInvestor(UUID investorId, UUID transactionId, String transactionType,
                               String transactionStatus, String assetSymbol, BigDecimal quantity,
                               BigDecimal totalAmount, String currency) {
        try {
            investmentNotificationService.sendTransactionNotification(investorId, transactionId, 
                    transactionType, transactionStatus, assetSymbol, quantity, totalAmount, currency);
            
            log.info("Investor notified - InvestorId: {}, TxnId: {}, Type: {}", 
                    investorId, transactionId, transactionType);
            
        } catch (Exception e) {
            log.error("Failed to notify investor - InvestorId: {}, TxnId: {}", 
                    investorId, transactionId, e);
        }
    }
    
    private void updateInvestmentMetrics(String transactionType, String transactionStatus,
                                        String assetType, BigDecimal totalAmount) {
        try {
            investmentTransactionService.updateTransactionMetrics(transactionType, transactionStatus, 
                    assetType, totalAmount);
        } catch (Exception e) {
            log.error("Failed to update investment metrics - Type: {}, Status: {}", 
                    transactionType, transactionStatus, e);
        }
    }
    
    private void auditInvestmentTransaction(UUID transactionId, UUID investorId, UUID portfolioId,
                                           String transactionType, String transactionStatus,
                                           String assetSymbol, BigDecimal quantity,
                                           BigDecimal totalAmount, String currency,
                                           LocalDateTime startTime) {
        try {
            long processingTimeMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
            
            auditService.auditFinancialEvent(
                    "INVESTMENT_TRANSACTION_PROCESSED",
                    investorId.toString(),
                    String.format("Investment transaction %s - Type: %s, Asset: %s, Qty: %s, Amount: %s %s", 
                            transactionStatus, transactionType, assetSymbol, quantity, totalAmount, currency),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "investorId", investorId.toString(),
                            "portfolioId", portfolioId.toString(),
                            "transactionType", transactionType,
                            "transactionStatus", transactionStatus,
                            "assetSymbol", assetSymbol,
                            "quantity", quantity.toString(),
                            "totalAmount", totalAmount.toString(),
                            "currency", currency,
                            "processingTimeMs", processingTimeMs
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to audit investment transaction - TxnId: {}", transactionId, e);
        }
    }
    
    private void handleTransactionFailure(UUID transactionId, UUID investorId, String transactionType,
                                         Exception error) {
        try {
            investmentTransactionService.handleTransactionFailure(transactionId, investorId, 
                    transactionType, error.getMessage());
            
            auditService.auditFinancialEvent(
                    "INVESTMENT_TRANSACTION_PROCESSING_FAILED",
                    investorId.toString(),
                    "Failed to process investment transaction: " + error.getMessage(),
                    Map.of(
                            "transactionId", transactionId.toString(),
                            "investorId", investorId.toString(),
                            "transactionType", transactionType != null ? transactionType : "UNKNOWN",
                            "error", error.getClass().getSimpleName(),
                            "errorMessage", error.getMessage()
                    )
            );
            
        } catch (Exception e) {
            log.error("Failed to handle transaction failure - TxnId: {}", transactionId, e);
        }
    }
    
    @KafkaListener(
        topics = {"investment-transactions.DLQ", "investment-transaction-events.DLQ"},
        groupId = "investment-service-transaction-dlq-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleDlq(
            @Payload String eventJson,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = "x-original-topic", required = false) String originalTopic,
            @Header(value = "x-error-message", required = false) String errorMessage,
            @Header(value = "x-error-class", required = false) String errorClass,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        log.error("CRITICAL: Investment transaction event sent to DLQ - OriginalTopic: {}, Error: {}, ErrorClass: {}, Event: {}", 
                originalTopic, errorMessage, errorClass, eventJson);
        
        try {
            Map<String, Object> event = objectMapper.readValue(eventJson, Map.class);
            UUID transactionId = event.containsKey("transactionId") ? 
                    UUID.fromString((String) event.get("transactionId")) : null;
            UUID investorId = event.containsKey("investorId") ? 
                    UUID.fromString((String) event.get("investorId")) : null;
            String transactionType = (String) event.get("transactionType");
            
            log.error("DLQ: Investment transaction failed permanently - TxnId: {}, InvestorId: {}, Type: {} - MANUAL REVIEW REQUIRED", 
                    transactionId, investorId, transactionType);
            
            if (transactionId != null && investorId != null) {
                investmentTransactionService.markForManualReview(transactionId, investorId, 
                        transactionType, "DLQ: " + errorMessage);
            }
            
        } catch (Exception e) {
            log.error("Failed to parse investment transaction DLQ event: {}", eventJson, e);
        }
    }
}