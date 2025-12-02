package com.waqiti.payment.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waqiti.common.kafka.BaseKafkaConsumer;
import com.waqiti.payment.service.SettlementService;
import com.waqiti.payment.domain.Settlement;
import com.waqiti.payment.entity.PaymentTransaction;
import com.waqiti.payment.repository.PaymentTransactionRepository;
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
 * Kafka consumer for InvestmentOrderPlaced events
 * Handles settlement processing for investment orders to ensure proper fund transfer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvestmentOrderPlacedConsumer extends BaseKafkaConsumer {

    private final SettlementService settlementService;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "investment-order-placed", groupId = "settlement-service-group")
    @CircuitBreaker(name = "investment-order-consumer")
    @Retry(name = "investment-order-consumer")
    @Transactional
    public void handleInvestmentOrderPlaced(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);
        MDC.put("eventType", "investment-order-placed");
        
        try {
            log.info("Processing investment order placed event: partition={}, offset={}", 
                    record.partition(), record.offset());
            
            JsonNode eventData = objectMapper.readTree(record.value());
            
            String orderId = eventData.path("orderId").asText();
            String userId = eventData.path("userId").asText();
            String instrumentSymbol = eventData.path("instrumentSymbol").asText();
            String orderType = eventData.path("orderType").asText(); // BUY, SELL
            BigDecimal quantity = new BigDecimal(eventData.path("quantity").asText());
            BigDecimal price = new BigDecimal(eventData.path("price").asText());
            BigDecimal totalAmount = new BigDecimal(eventData.path("totalAmount").asText());
            String walletId = eventData.path("walletId").asText();
            LocalDateTime orderTime = LocalDateTime.parse(eventData.path("orderTime").asText());
            
            log.info("Processing settlement for investment order: orderId={}, type={}, symbol={}, amount={}", 
                    orderId, orderType, instrumentSymbol, totalAmount);
            
            // Create settlement for investment order
            processInvestmentOrderSettlement(orderId, userId, instrumentSymbol, orderType, 
                    quantity, price, totalAmount, walletId, orderTime);
            
            ack.acknowledge();
            log.info("Successfully processed investment order settlement: orderId={}", orderId);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to parse investment order event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } catch (Exception e) {
            log.error("Error processing investment order event: {}", e.getMessage(), e);
            handleProcessingError(record, e);
        } finally {
            MDC.clear();
        }
    }

    private void processInvestmentOrderSettlement(String orderId, String userId, String instrumentSymbol,
                                                  String orderType, BigDecimal quantity, BigDecimal price,
                                                  BigDecimal totalAmount, String walletId, LocalDateTime orderTime) {
        
        try {
            // For BUY orders: Need to settle funds FROM user wallet TO investment account
            // For SELL orders: Need to settle funds FROM investment account TO user wallet
            
            if ("BUY".equalsIgnoreCase(orderType)) {
                processBuyOrderSettlement(orderId, userId, instrumentSymbol, quantity, price, 
                        totalAmount, walletId, orderTime);
            } else if ("SELL".equalsIgnoreCase(orderType)) {
                processSellOrderSettlement(orderId, userId, instrumentSymbol, quantity, price, 
                        totalAmount, walletId, orderTime);
            } else {
                log.warn("Unknown order type for settlement: {} in order: {}", orderType, orderId);
                return;
            }
            
        } catch (Exception e) {
            log.error("Error processing investment order settlement: orderId={}, error={}", 
                    orderId, e.getMessage(), e);
            throw e;
        }
    }
    
    private void processBuyOrderSettlement(String orderId, String userId, String instrumentSymbol,
                                           BigDecimal quantity, BigDecimal price, BigDecimal totalAmount,
                                           String walletId, LocalDateTime orderTime) {
        
        // Create settlement record for buy order
        Settlement settlement = Settlement.builder()
                .id(UUID.randomUUID())
                .referenceId(orderId)
                .referenceType("INVESTMENT_BUY_ORDER")
                .userId(userId)
                .fromAccount(walletId)
                .toAccount(getInvestmentCustodyAccount(instrumentSymbol))
                .amount(totalAmount)
                .currency("USD") // Assuming USD for now
                .settlementDate(orderTime.toLocalDate())
                .status("PENDING")
                .description(String.format("Settlement for buy order: %s shares of %s at $%s", 
                        quantity, instrumentSymbol, price))
                .metadata(String.format("{ \"orderId\": \"%s\", \"symbol\": \"%s\", \"quantity\": \"%s\", \"price\": \"%s\" }", 
                        orderId, instrumentSymbol, quantity, price))
                .createdAt(orderTime)
                .build();
        
        // Process the settlement
        Settlement processedSettlement = settlementService.processSettlement(settlement);
        
        // Create payment transaction record for audit trail
        PaymentTransaction paymentTransaction = new PaymentTransaction();
        paymentTransaction.setId(UUID.randomUUID());
        paymentTransaction.setReferenceId(orderId);
        paymentTransaction.setUserId(userId);
        paymentTransaction.setAmount(totalAmount);
        paymentTransaction.setCurrency("USD");
        paymentTransaction.setStatus("SETTLEMENT_PENDING");
        paymentTransaction.setTransactionType("INVESTMENT_BUY");
        paymentTransaction.setDescription(String.format("Investment buy order settlement: %s", instrumentSymbol));
        paymentTransaction.setCreatedAt(orderTime);
        paymentTransaction.setUpdatedAt(LocalDateTime.now());
        
        paymentTransactionRepository.save(paymentTransaction);
        
        log.info("Created buy order settlement: orderId={}, settlementId={}, amount={}", 
                orderId, processedSettlement.getId(), totalAmount);
    }
    
    private void processSellOrderSettlement(String orderId, String userId, String instrumentSymbol,
                                            BigDecimal quantity, BigDecimal price, BigDecimal totalAmount,
                                            String walletId, LocalDateTime orderTime) {
        
        // Calculate net proceeds (total amount minus fees)
        BigDecimal tradingFee = totalAmount.multiply(new BigDecimal("0.001")); // 0.1% trading fee
        BigDecimal netProceeds = totalAmount.subtract(tradingFee);
        
        // Create settlement record for sell order
        Settlement settlement = Settlement.builder()
                .id(UUID.randomUUID())
                .referenceId(orderId)
                .referenceType("INVESTMENT_SELL_ORDER")
                .userId(userId)
                .fromAccount(getInvestmentCustodyAccount(instrumentSymbol))
                .toAccount(walletId)
                .amount(netProceeds)
                .currency("USD") // Assuming USD for now
                .settlementDate(orderTime.toLocalDate().plusDays(2)) // T+2 settlement
                .status("PENDING")
                .description(String.format("Settlement for sell order: %s shares of %s at $%s (net proceeds: $%s)", 
                        quantity, instrumentSymbol, price, netProceeds))
                .metadata(String.format("{ \"orderId\": \"%s\", \"symbol\": \"%s\", \"quantity\": \"%s\", \"price\": \"%s\", \"fee\": \"%s\" }", 
                        orderId, instrumentSymbol, quantity, price, tradingFee))
                .createdAt(orderTime)
                .build();
        
        // Process the settlement
        Settlement processedSettlement = settlementService.processSettlement(settlement);
        
        // Create payment transaction record for the proceeds
        PaymentTransaction proceedsTransaction = new PaymentTransaction();
        proceedsTransaction.setId(UUID.randomUUID());
        proceedsTransaction.setReferenceId(orderId);
        proceedsTransaction.setUserId(userId);
        proceedsTransaction.setAmount(netProceeds);
        proceedsTransaction.setCurrency("USD");
        proceedsTransaction.setStatus("SETTLEMENT_PENDING");
        proceedsTransaction.setTransactionType("INVESTMENT_SELL");
        proceedsTransaction.setDescription(String.format("Investment sell order settlement: %s", instrumentSymbol));
        proceedsTransaction.setCreatedAt(orderTime);
        proceedsTransaction.setUpdatedAt(LocalDateTime.now());
        
        paymentTransactionRepository.save(proceedsTransaction);
        
        // Create separate transaction record for the fee
        if (tradingFee.compareTo(BigDecimal.ZERO) > 0) {
            PaymentTransaction feeTransaction = new PaymentTransaction();
            feeTransaction.setId(UUID.randomUUID());
            feeTransaction.setReferenceId(orderId + "_FEE");
            feeTransaction.setUserId(userId);
            feeTransaction.setAmount(tradingFee);
            feeTransaction.setCurrency("USD");
            feeTransaction.setStatus("COMPLETED");
            feeTransaction.setTransactionType("INVESTMENT_TRADING_FEE");
            feeTransaction.setDescription(String.format("Trading fee for sell order: %s", instrumentSymbol));
            feeTransaction.setCreatedAt(orderTime);
            feeTransaction.setUpdatedAt(LocalDateTime.now());
            
            paymentTransactionRepository.save(feeTransaction);
        }
        
        log.info("Created sell order settlement: orderId={}, settlementId={}, netProceeds={}, fee={}", 
                orderId, processedSettlement.getId(), netProceeds, tradingFee);
    }
    
    private String getInvestmentCustodyAccount(String instrumentSymbol) {
        // Return custody account based on instrument type
        // In production, this would map to actual custodian accounts
        
        if (isETF(instrumentSymbol)) {
            return "CUSTODY_ETF_ACCOUNT";
        } else if (isStock(instrumentSymbol)) {
            return "CUSTODY_EQUITY_ACCOUNT";
        } else if (isBond(instrumentSymbol)) {
            return "CUSTODY_BOND_ACCOUNT";
        } else {
            return "CUSTODY_GENERAL_ACCOUNT";
        }
    }
    
    private boolean isETF(String symbol) {
        // List of common ETFs - in production this would be from a database or external service
        return symbol.matches("(SPY|QQQ|IWM|VTI|VOO|VEA|VWO|AGG|TLT|GLD|SLV)");
    }
    
    private boolean isStock(String symbol) {
        // For now, assume most symbols are stocks unless identified as ETF/Bond
        return !isETF(symbol) && !isBond(symbol);
    }
    
    private boolean isBond(String symbol) {
        // Simple check for bond-like symbols - in production use proper classification
        return symbol.contains("BOND") || symbol.endsWith("B") && symbol.length() <= 4;
    }
}