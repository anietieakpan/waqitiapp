package com.waqiti.investment.consumer;

import com.waqiti.common.events.OrderExecutedEvent;
import com.waqiti.investment.service.PortfolioService;
import com.waqiti.investment.service.HoldingService;
import com.waqiti.investment.service.TaxService;
import com.waqiti.investment.service.NotificationService;
import com.waqiti.investment.repository.ProcessedEventRepository;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import com.waqiti.investment.domain.ProcessedEvent;
import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.domain.InvestmentHolding;
import com.waqiti.investment.domain.enums.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Consumer for OrderExecutedEvent - Critical for investment portfolio updates
 * Updates portfolios and holdings after successful order execution
 * ZERO TOLERANCE: All executed orders must update portfolios correctly
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class OrderExecutedEventConsumer {
    
    private final PortfolioService portfolioService;
    private final HoldingService holdingService;
    private final TaxService taxService;
    private final NotificationService notificationService;
    private final ProcessedEventRepository processedEventRepository;
    private final InvestmentOrderRepository orderRepository;
    
    @KafkaListener(
        topics = "investment.order.executed",
        groupId = "investment-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE) // Highest isolation for portfolio updates
    public void handleOrderExecuted(OrderExecutedEvent event) {
        log.info("Processing order execution: {} for {} shares of {} at ${}", 
            event.getOrderId(), event.getExecutedQuantity(), event.getSymbol(), event.getExecutionPrice());
        
        // IDEMPOTENCY CHECK - Critical for preventing duplicate portfolio updates
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Order execution already processed for event: {}", event.getEventId());
            return;
        }
        
        try {
            // Get and validate order
            InvestmentOrder order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new RuntimeException("Order not found: " + event.getOrderId()));
            
            // STEP 1: Update order status and execution details
            updateOrderExecution(order, event);
            
            // STEP 2: Update or create holding
            InvestmentHolding holding = updateHolding(order, event);
            
            // STEP 3: Update portfolio value and metrics
            updatePortfolio(order.getPortfolioId(), event);
            
            // STEP 4: Calculate and record tax implications
            processTaxImplications(order, event, holding);
            
            // STEP 5: Update cost basis and performance metrics
            updatePerformanceMetrics(order, event, holding);
            
            // STEP 6: Send user notifications
            sendExecutionNotifications(order, event);
            
            // STEP 7: Check and execute any dependent orders (stop-loss, take-profit)
            processDependentOrders(order, event);
            
            // STEP 8: Update auto-invest allocations if this was an auto-invest order
            if (order.isAutoInvestOrder()) {
                updateAutoInvestProgress(order, event);
            }
            
            // STEP 9: Record successful processing
            ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(event.getEventId())
                .eventType("OrderExecutedEvent")
                .processedAt(Instant.now())
                .orderId(event.getOrderId())
                .symbol(event.getSymbol())
                .executedQuantity(event.getExecutedQuantity())
                .executionPrice(event.getExecutionPrice())
                .totalValue(calculateTotalValue(event))
                .build();
                
            processedEventRepository.save(processedEvent);
            
            log.info("Successfully processed order execution: {} - Portfolio updated", 
                event.getOrderId());
                
        } catch (Exception e) {
            log.error("CRITICAL: Failed to process order execution: {}", 
                event.getOrderId(), e);
                
            // Create high-priority manual intervention record
            createManualInterventionRecord(event, e);
            
            throw new RuntimeException("Order execution processing failed", e);
        }
    }
    
    private void updateOrderExecution(InvestmentOrder order, OrderExecutedEvent event) {
        // Update order with execution details
        order.setExecutedQuantity(order.getExecutedQuantity().add(event.getExecutedQuantity()));
        order.setAveragePrice(calculateAveragePrice(order, event));
        order.setLastExecutionTime(event.getExecutionTime());
        order.setLastExecutionPrice(event.getExecutionPrice());
        
        // Update order status
        if (order.getExecutedQuantity().compareTo(order.getRequestedQuantity()) >= 0) {
            order.setStatus(OrderStatus.FILLED);
            order.setCompletedAt(Instant.now());
        } else {
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        }
        
        // Add execution record
        order.addExecution(
            event.getExecutedQuantity(),
            event.getExecutionPrice(),
            event.getExecutionTime(),
            event.getExecutionId()
        );
        
        orderRepository.save(order);
        
        log.info("Updated order {} status to {} with {} shares executed", 
            order.getId(), order.getStatus(), event.getExecutedQuantity());
    }
    
    private InvestmentHolding updateHolding(InvestmentOrder order, OrderExecutedEvent event) {
        InvestmentHolding holding = holdingService.findByPortfolioAndSymbol(
            order.getPortfolioId(), 
            event.getSymbol()
        ).orElse(null);
        
        if (holding == null) {
            // Create new holding
            holding = InvestmentHolding.builder()
                .portfolioId(order.getPortfolioId())
                .symbol(event.getSymbol())
                .quantity(BigDecimal.ZERO)
                .averageCostBasis(BigDecimal.ZERO)
                .totalCostBasis(BigDecimal.ZERO)
                .currentPrice(event.getExecutionPrice())
                .marketValue(BigDecimal.ZERO)
                .unrealizedGainLoss(BigDecimal.ZERO)
                .build();
        }
        
        // Update holding based on order type
        if (order.getOrderType().isBuyOrder()) {
            updateHoldingForBuy(holding, event);
        } else {
            updateHoldingForSell(holding, event);
        }
        
        // Update current market value
        holding.setCurrentPrice(event.getExecutionPrice());
        holding.setMarketValue(holding.getQuantity().multiply(event.getExecutionPrice()));
        holding.setUnrealizedGainLoss(
            holding.getMarketValue().subtract(holding.getTotalCostBasis())
        );
        holding.setLastUpdated(Instant.now());
        
        return holdingService.save(holding);
    }
    
    private void updateHoldingForBuy(InvestmentHolding holding, OrderExecutedEvent event) {
        BigDecimal executionValue = event.getExecutedQuantity().multiply(event.getExecutionPrice());
        BigDecimal newTotalCost = holding.getTotalCostBasis().add(executionValue);
        BigDecimal newQuantity = holding.getQuantity().add(event.getExecutedQuantity());
        
        // Calculate new average cost basis
        BigDecimal newAverageCost = newQuantity.compareTo(BigDecimal.ZERO) > 0 ?
            newTotalCost.divide(newQuantity, 4, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        
        holding.setQuantity(newQuantity);
        holding.setAverageCostBasis(newAverageCost);
        holding.setTotalCostBasis(newTotalCost);
        
        log.info("Updated holding for buy: {} shares of {} at average cost ${}", 
            newQuantity, event.getSymbol(), newAverageCost);
    }
    
    private void updateHoldingForSell(InvestmentHolding holding, OrderExecutedEvent event) {
        if (holding.getQuantity().compareTo(event.getExecutedQuantity()) < 0) {
            log.error("CRITICAL: Attempting to sell {} shares but only {} available for {}", 
                event.getExecutedQuantity(), holding.getQuantity(), event.getSymbol());
            throw new RuntimeException("Insufficient shares for sale");
        }
        
        BigDecimal soldValue = event.getExecutedQuantity().multiply(event.getExecutionPrice());
        BigDecimal costBasisSold = event.getExecutedQuantity().multiply(holding.getAverageCostBasis());
        BigDecimal realizedGainLoss = soldValue.subtract(costBasisSold);
        
        // Update holding quantities
        holding.setQuantity(holding.getQuantity().subtract(event.getExecutedQuantity()));
        holding.setTotalCostBasis(holding.getTotalCostBasis().subtract(costBasisSold));
        
        // Record realized gain/loss
        holding.addRealizedGainLoss(realizedGainLoss, event.getExecutionTime());
        
        log.info("Updated holding for sell: {} shares of {} sold, realized G/L: ${}", 
            event.getExecutedQuantity(), event.getSymbol(), realizedGainLoss);
    }
    
    private void updatePortfolio(String portfolioId, OrderExecutedEvent event) {
        portfolioService.recalculatePortfolioValue(portfolioId);
        portfolioService.updatePortfolioPerformanceMetrics(portfolioId);
        
        log.info("Portfolio {} value and metrics updated after order execution", portfolioId);
    }
    
    private void processTaxImplications(InvestmentOrder order, OrderExecutedEvent event, InvestmentHolding holding) {
        if (order.getOrderType().isSellOrder()) {
            // Calculate tax implications for sale
            TaxCalculationResult taxResult = taxService.calculateCapitalGainsTax(
                order.getUserId(),
                event.getSymbol(),
                event.getExecutedQuantity(),
                holding.getAverageCostBasis(),
                event.getExecutionPrice(),
                order.getPlacedAt(),
                event.getExecutionTime()
            );
            
            // Record tax liability
            taxService.recordTaxLiability(
                order.getUserId(),
                event.getOrderId(),
                taxResult
            );
            
            log.info("Tax implications calculated for sale: {} tax liability", 
                taxResult.getTaxOwed());
        }
    }
    
    private void updatePerformanceMetrics(InvestmentOrder order, OrderExecutedEvent event, InvestmentHolding holding) {
        // Update user's investment performance metrics
        performanceService.updateUserPerformance(
            order.getUserId(),
            event.getSymbol(),
            event.getExecutedQuantity(),
            event.getExecutionPrice(),
            order.getOrderType(),
            holding.getUnrealizedGainLoss()
        );
    }
    
    private void sendExecutionNotifications(InvestmentOrder order, OrderExecutedEvent event) {
        String orderTypeStr = order.getOrderType().toString().toLowerCase();
        String statusStr = order.getStatus().toString().toLowerCase().replace("_", " ");
        
        String message = String.format(
            "Your %s order for %s shares of %s has been %s at $%.2f per share. Total: $%.2f",
            orderTypeStr,
            event.getExecutedQuantity(),
            event.getSymbol(),
            statusStr,
            event.getExecutionPrice(),
            calculateTotalValue(event)
        );
        
        notificationService.sendOrderExecutionNotification(
            order.getUserId(),
            event.getOrderId(),
            "Order Executed",
            message,
            event.getSymbol(),
            event.getExecutedQuantity(),
            event.getExecutionPrice()
        );
    }
    
    private void processDependentOrders(InvestmentOrder order, OrderExecutedEvent event) {
        // Process stop-loss and take-profit orders
        dependentOrderService.processDependentOrders(
            order.getPortfolioId(),
            event.getSymbol(),
            event.getExecutionPrice(),
            event.getExecutionTime()
        );
    }
    
    private void updateAutoInvestProgress(InvestmentOrder order, OrderExecutedEvent event) {
        autoInvestService.recordAutoInvestExecution(
            order.getUserId(),
            order.getAutoInvestPlanId(),
            event.getSymbol(),
            event.getExecutedQuantity(),
            event.getExecutionPrice()
        );
    }
    
    private BigDecimal calculateAveragePrice(InvestmentOrder order, OrderExecutedEvent event) {
        BigDecimal currentTotal = order.getExecutedQuantity().multiply(order.getAveragePrice());
        BigDecimal newExecutionTotal = event.getExecutedQuantity().multiply(event.getExecutionPrice());
        BigDecimal totalQuantity = order.getExecutedQuantity().add(event.getExecutedQuantity());
        
        return currentTotal.add(newExecutionTotal).divide(totalQuantity, 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateTotalValue(OrderExecutedEvent event) {
        return event.getExecutedQuantity().multiply(event.getExecutionPrice());
    }
    
    private void createManualInterventionRecord(OrderExecutedEvent event, Exception exception) {
        manualInterventionService.createCriticalTask(
            "ORDER_EXECUTION_PROCESSING_FAILED",
            String.format(
                "CRITICAL: Failed to process order execution. " +
                "Order ID: %s, Symbol: %s, Quantity: %s, Price: %s. " +
                "Exception: %s. IMMEDIATE MANUAL INTERVENTION REQUIRED.",
                event.getOrderId(),
                event.getSymbol(),
                event.getExecutedQuantity(),
                event.getExecutionPrice(),
                exception.getMessage()
            ),
            "CRITICAL",
            event,
            exception
        );
    }
}