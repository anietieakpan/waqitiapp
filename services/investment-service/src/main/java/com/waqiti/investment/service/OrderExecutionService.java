package com.waqiti.investment.service;

import com.waqiti.investment.domain.*;
import com.waqiti.investment.domain.enums.*;
import com.waqiti.investment.dto.request.CreateOrderRequest;
import com.waqiti.investment.dto.response.InvestmentOrderDto;
import com.waqiti.investment.exception.*;
import com.waqiti.investment.repository.*;
// CRITICAL P0 FIX: Add idempotency, fraud detection, and audit logging
import com.waqiti.common.idempotency.IdempotencyService;
import com.waqiti.common.audit.AuditService;
import com.waqiti.common.fraud.FraudDetectionClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service for executing investment orders
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderExecutionService {

    @Lazy
    private final OrderExecutionService self;
    private final InvestmentOrderRepository orderRepository;
    private final InvestmentAccountRepository accountRepository;
    private final InvestmentHoldingRepository holdingRepository;
    private final PortfolioService portfolioService;
    private final MarketDataService marketDataService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    // CRITICAL P0 FIX: Add required services for production readiness
    private final IdempotencyService idempotencyService;
    private final AuditService auditService;
    private final FraudDetectionClient fraudDetectionClient;

    private static final String ORDER_TOPIC = "investment-orders";
    private static final String ORDER_EXECUTION_TOPIC = "order-executions";
    private static final BigDecimal COMMISSION_RATE = new BigDecimal("0.001"); // 0.1% commission

    /**
     * CRITICAL P0 FIX: Create and submit a new investment order with idempotency, fraud detection, and audit logging
     */
    @Transactional
    public InvestmentOrderDto createOrder(CreateOrderRequest request) {
        log.info("Creating investment order: {}", request);

        // CRITICAL P0 FIX: Generate idempotency key to prevent duplicate orders
        String idempotencyKey = String.format("investment:order:%s:%s:%s:%s",
            request.getAccountId(), request.getSymbol(), request.getQuantity(), request.getOrderSide());

        // CRITICAL: Execute with idempotency protection to prevent duplicate orders
        return idempotencyService.executeIdempotentWithPersistence(
            "investment-service",
            "create-order",
            idempotencyKey,
            () -> createOrderInternal(request),
            Duration.ofHours(24) // Keep idempotency record for 24 hours
        );
    }

    /**
     * CRITICAL P0 FIX: Internal order creation with fraud detection and audit logging
     */
    @Transactional
    private InvestmentOrderDto createOrderInternal(CreateOrderRequest request) {
        // Step 1: Validate account
        InvestmentAccount account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new AccountNotFoundException("Investment account not found: " + request.getAccountId()));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvestmentException("Account is not active");
        }

        // Step 2: Get current market price
        StockQuoteDto quote = marketDataService.getStockQuote(request.getSymbol());
        BigDecimal currentPrice = quote.getPrice();

        // Step 3: Validate order
        validateOrder(account, request, currentPrice);

        // CRITICAL P0 FIX: Step 4: Fraud detection check
        performFraudCheck(account, request, currentPrice);

        // Step 5: Create order
        InvestmentOrder order = InvestmentOrder.builder()
                .orderNumber(generateOrderNumber())
                .account(account)
                .symbol(request.getSymbol().toUpperCase())
                .orderType(request.getOrderType())
                .orderSide(request.getOrderSide())
                .quantity(request.getQuantity())
                .price(determineOrderPrice(request, currentPrice))
                .timeInForce(request.getTimeInForce() != null ? request.getTimeInForce() : TimeInForce.DAY)
                .status(OrderStatus.PENDING)
                .submittedAt(LocalDateTime.now())
                .build();

        // Step 6: Calculate estimated cost/proceeds
        BigDecimal estimatedAmount = calculateOrderAmount(order);
        BigDecimal commission = calculateCommission(estimatedAmount);

        order.setCommission(commission);
        order.setEstimatedTotal(request.getOrderSide() == OrderSide.BUY ?
                estimatedAmount.add(commission) : estimatedAmount.subtract(commission));

        // Step 7: Save order
        order = orderRepository.save(order);

        // CRITICAL P0 FIX: Step 8: Audit logging for regulatory compliance
        logOrderCreationAudit(order, account);

        // Step 9: Send to order processing queue
        sendOrderToQueue(order);

        // Step 10: Process immediately if market order
        if (order.getOrderType() == OrderType.MARKET) {
            CompletableFuture.runAsync(() -> processMarketOrder(order.getId()));
        }

        log.info("Investment order created successfully - Order ID: {}, Order Number: {}, Symbol: {}, Quantity: {}",
            order.getId(), order.getOrderNumber(), order.getSymbol(), order.getQuantity());

        return mapToOrderDto(order);
    }

    /**
     * CRITICAL P0 FIX: Perform fraud detection check on investment order
     */
    private void performFraudCheck(InvestmentAccount account, CreateOrderRequest request, BigDecimal currentPrice) {
        try {
            log.info("FRAUD CHECK: Performing fraud detection on investment order - Account: {}, Symbol: {}, Quantity: {}",
                account.getId(), request.getSymbol(), request.getQuantity());

            BigDecimal orderValue = currentPrice.multiply(request.getQuantity());

            // Build fraud detection context
            Map<String, Object> context = new HashMap<>();
            context.put("accountId", account.getId().toString());
            context.put("userId", account.getUserId().toString());
            context.put("symbol", request.getSymbol());
            context.put("orderType", request.getOrderType().name());
            context.put("orderSide", request.getOrderSide().name());
            context.put("quantity", request.getQuantity());
            context.put("orderValue", orderValue);
            context.put("currentPrice", currentPrice);
            context.put("accountBalance", account.getCashBalance());

            // Call fraud detection service
            boolean isFraudulent = fraudDetectionClient.checkInvestmentOrderFraud(
                account.getUserId(),
                account.getId(),
                request.getSymbol(),
                orderValue,
                context
            );

            if (isFraudulent) {
                log.error("FRAUD DETECTED: Investment order failed fraud check - Account: {}, Symbol: {}, Value: {}",
                    account.getId(), request.getSymbol(), orderValue);

                // Audit the fraud detection
                auditService.logSecurityEvent(
                    account.getUserId(),
                    "INVESTMENT_ORDER_FRAUD_DETECTED",
                    String.format("Fraudulent investment order blocked - Symbol: %s, Value: %s", request.getSymbol(), orderValue)
                );

                throw new InvestmentException("Order failed fraud detection checks and has been blocked");
            }

            log.info("FRAUD CHECK: Investment order passed fraud detection - Account: {}", account.getId());

        } catch (InvestmentException e) {
            throw e; // Re-throw investment exceptions
        } catch (Exception e) {
            log.error("FRAUD CHECK: Error during fraud detection check - Account: {}, Symbol: {}",
                account.getId(), request.getSymbol(), e);
            // Don't block order if fraud detection service fails - log and continue
            auditService.logSecurityEvent(
                account.getUserId(),
                "INVESTMENT_ORDER_FRAUD_CHECK_FAILED",
                String.format("Fraud detection check failed for order - Symbol: %s, Error: %s",
                    request.getSymbol(), e.getMessage())
            );
        }
    }

    /**
     * CRITICAL P0 FIX: Log order creation for audit trail and regulatory compliance
     */
    private void logOrderCreationAudit(InvestmentOrder order, InvestmentAccount account) {
        try {
            auditService.logInvestmentOrderEvent(
                order.getId(),
                "INVESTMENT_ORDER_CREATED",
                String.format("Investment order created - Order: %s, Symbol: %s, Side: %s, Quantity: %s, Type: %s, Estimated Total: %s",
                    order.getOrderNumber(), order.getSymbol(), order.getOrderSide(),
                    order.getQuantity(), order.getOrderType(), order.getEstimatedTotal()),
                account.getUserId()
            );

            log.info("AUDIT: Investment order creation logged - Order ID: {}, Order Number: {}",
                order.getId(), order.getOrderNumber());

        } catch (Exception e) {
            log.error("AUDIT: Failed to log investment order creation - Order ID: {}, Order Number: {}",
                order.getId(), order.getOrderNumber(), e);
            // Don't fail order creation if audit logging fails
        }
    }

    /**
     * Cancel a pending order
     */
    @Transactional
    public InvestmentOrderDto cancelOrder(Long orderId) {
        InvestmentOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new InvestmentException("Cannot cancel order in status: " + order.getStatus());
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order = orderRepository.save(order);

        // Publish cancellation event
        publishOrderEvent(order, "ORDER_CANCELLED");

        return mapToOrderDto(order);
    }

    /**
     * Process a market order immediately
     */
    @Transactional
    public void processMarketOrder(Long orderId) {
        try {
            InvestmentOrder order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

            if (order.getStatus() != OrderStatus.PENDING) {
                log.warn("Order {} is not pending, skipping processing", orderId);
                return;
            }

            // Get current market price
            StockQuoteDto quote = marketDataService.getStockQuote(order.getSymbol());
            BigDecimal executionPrice = quote.getPrice();

            // Execute order
            executeOrder(order, executionPrice, order.getQuantity());

        } catch (Exception e) {
            log.error("Error processing market order {}: {}", orderId, e.getMessage(), e);
            markOrderFailed(orderId, e.getMessage());
        }
    }

    /**
     * Process limit orders based on market conditions
     */
    @Transactional
    public void processLimitOrders() {
        List<InvestmentOrder> pendingLimitOrders = orderRepository.findByStatusAndOrderType(
                OrderStatus.PENDING, OrderType.LIMIT);

        for (InvestmentOrder order : pendingLimitOrders) {
            try {
                StockQuoteDto quote = marketDataService.getStockQuote(order.getSymbol());
                BigDecimal currentPrice = quote.getPrice();

                boolean shouldExecute = false;
                if (order.getOrderSide() == OrderSide.BUY) {
                    // Buy limit order executes when market price <= limit price
                    shouldExecute = currentPrice.compareTo(order.getPrice()) <= 0;
                } else {
                    // Sell limit order executes when market price >= limit price
                    shouldExecute = currentPrice.compareTo(order.getPrice()) >= 0;
                }

                if (shouldExecute) {
                    executeOrder(order, currentPrice, order.getQuantity());
                }

            } catch (Exception e) {
                log.error("Error processing limit order {}: {}", order.getId(), e.getMessage());
            }
        }
    }

    /**
     * Execute an order
     */
    @Transactional
    protected void executeOrder(InvestmentOrder order, BigDecimal executionPrice, BigDecimal quantity) {
        log.info("Executing order {} at price {}", order.getId(), executionPrice);

        InvestmentAccount account = order.getAccount();
        
        // Calculate actual amounts
        BigDecimal amount = quantity.multiply(executionPrice);
        BigDecimal commission = calculateCommission(amount);
        BigDecimal totalCost = order.getOrderSide() == OrderSide.BUY ? 
                amount.add(commission) : amount.subtract(commission);

        // Update account balance
        if (order.getOrderSide() == OrderSide.BUY) {
            // Deduct from cash balance
            if (account.getCashBalance().compareTo(totalCost) < 0) {
                throw new InsufficientFundsException("Insufficient cash balance");
            }
            account.setCashBalance(account.getCashBalance().subtract(totalCost));
            
            // Add to holdings - portfolioService calls are OK, they're on different service
            portfolioService.addHolding(account.getPortfolio(), order.getSymbol(), 
                    quantity, executionPrice);
        } else {
            // Add to cash balance
            account.setCashBalance(account.getCashBalance().add(amount.subtract(commission)));
            
            // Remove from holdings - portfolioService calls are OK, they're on different service
            portfolioService.removeHolding(account.getPortfolio(), order.getSymbol(), quantity);
        }

        // Update order
        order.setStatus(OrderStatus.FILLED);
        order.setExecutedPrice(executionPrice);
        order.setExecutedQuantity(quantity);
        order.setCommission(commission);
        order.setFilledAt(LocalDateTime.now());
        
        accountRepository.save(account);
        orderRepository.save(order);

        // Publish execution event
        publishOrderExecutionEvent(order);
    }

    /**
     * Mark order as failed
     */
    @Transactional
    protected void markOrderFailed(Long orderId, String reason) {
        InvestmentOrder order = orderRepository.findById(orderId)
                .orElse(null);
        
        if (order != null) {
            order.setStatus(OrderStatus.REJECTED);
            order.setRejectionReason(reason);
            orderRepository.save(order);
            
            publishOrderEvent(order, "ORDER_FAILED");
        }
    }

    /**
     * Get order status
     */
    @Transactional(readOnly = true)
    public InvestmentOrderDto getOrder(Long orderId) {
        InvestmentOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        
        return mapToOrderDto(order);
    }

    /**
     * Get orders for account
     */
    @Transactional(readOnly = true)
    public List<InvestmentOrderDto> getAccountOrders(Long accountId, OrderStatus status) {
        List<InvestmentOrder> orders;
        
        if (status != null) {
            orders = orderRepository.findByAccountIdAndStatus(accountId, status);
        } else {
            orders = orderRepository.findByAccountId(accountId);
        }
        
        return orders.stream()
                .map(this::mapToOrderDto)
                .toList();
    }

    // Helper methods

    private void validateOrder(InvestmentAccount account, CreateOrderRequest request, BigDecimal currentPrice) {
        // Validate sufficient balance for buy orders
        if (request.getOrderSide() == OrderSide.BUY) {
            BigDecimal orderAmount = request.getQuantity().multiply(
                    request.getOrderType() == OrderType.LIMIT ? request.getLimitPrice() : currentPrice
            );
            BigDecimal totalCost = orderAmount.add(calculateCommission(orderAmount));
            
            if (account.getCashBalance().compareTo(totalCost) < 0) {
                throw new InsufficientFundsException("Insufficient funds for order");
            }
        } else {
            // Validate sufficient holdings for sell orders
            Portfolio portfolio = account.getPortfolio();
            if (portfolio == null) {
                throw new InvestmentException("No portfolio found for account");
            }
            
            InvestmentHolding holding = portfolio.getHoldings().stream()
                    .filter(h -> h.getSymbol().equals(request.getSymbol()))
                    .findFirst()
                    .orElseThrow(() -> new InvestmentException("No holdings found for symbol: " + request.getSymbol()));
            
            if (holding.getQuantity().compareTo(request.getQuantity()) < 0) {
                throw new InvestmentException("Insufficient holdings to sell");
            }
        }

        // Validate order price for limit orders
        if (request.getOrderType() == OrderType.LIMIT && request.getLimitPrice() == null) {
            throw new InvestmentException("Limit price required for limit orders");
        }

        // Validate order price for stop orders
        if (request.getOrderType() == OrderType.STOP && request.getStopPrice() == null) {
            throw new InvestmentException("Stop price required for stop orders");
        }
    }

    private BigDecimal determineOrderPrice(CreateOrderRequest request, BigDecimal currentPrice) {
        return switch (request.getOrderType()) {
            case MARKET -> currentPrice;
            case LIMIT -> request.getLimitPrice();
            case STOP -> request.getStopPrice();
            case STOP_LIMIT -> request.getLimitPrice();
        };
    }

    private BigDecimal calculateOrderAmount(InvestmentOrder order) {
        return order.getQuantity().multiply(order.getPrice());
    }

    private BigDecimal calculateCommission(BigDecimal amount) {
        BigDecimal commission = amount.multiply(COMMISSION_RATE);
        // Minimum commission of $1
        return commission.max(BigDecimal.ONE);
    }

    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private void sendOrderToQueue(InvestmentOrder order) {
        try {
            kafkaTemplate.send(ORDER_TOPIC, order.getOrderNumber(), order);
            log.info("Order {} sent to processing queue", order.getOrderNumber());
        } catch (Exception e) {
            log.error("Failed to send order to queue: {}", e.getMessage(), e);
        }
    }

    private void publishOrderEvent(InvestmentOrder order, String eventType) {
        OrderEvent event = OrderEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .accountId(order.getAccount().getId())
                .eventType(eventType)
                .timestamp(LocalDateTime.now())
                .build();
        
        kafkaTemplate.send(ORDER_EXECUTION_TOPIC, order.getOrderNumber(), event);
    }

    private void publishOrderExecutionEvent(InvestmentOrder order) {
        OrderExecutionEvent event = OrderExecutionEvent.builder()
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .accountId(order.getAccount().getId())
                .symbol(order.getSymbol())
                .side(order.getOrderSide())
                .quantity(order.getExecutedQuantity())
                .price(order.getExecutedPrice())
                .commission(order.getCommission())
                .executionTime(order.getFilledAt())
                .build();
        
        kafkaTemplate.send(ORDER_EXECUTION_TOPIC, order.getOrderNumber(), event);
    }

    private InvestmentOrderDto mapToOrderDto(InvestmentOrder order) {
        return InvestmentOrderDto.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .accountId(order.getAccount().getId())
                .symbol(order.getSymbol())
                .orderType(order.getOrderType())
                .orderSide(order.getOrderSide())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .executedPrice(order.getExecutedPrice())
                .executedQuantity(order.getExecutedQuantity())
                .status(order.getStatus())
                .timeInForce(order.getTimeInForce())
                .commission(order.getCommission())
                .estimatedTotal(order.getEstimatedTotal())
                .submittedAt(order.getSubmittedAt())
                .filledAt(order.getFilledAt())
                .cancelledAt(order.getCancelledAt())
                .rejectionReason(order.getRejectionReason())
                .build();
    }

    // Event classes
    @lombok.Data
    @lombok.Builder
    private static class OrderEvent {
        private Long orderId;
        private String orderNumber;
        private Long accountId;
        private String eventType;
        private LocalDateTime timestamp;
    }

    @lombok.Data
    @lombok.Builder
    private static class OrderExecutionEvent {
        private Long orderId;
        private String orderNumber;
        private Long accountId;
        private String symbol;
        private OrderSide side;
        private BigDecimal quantity;
        private BigDecimal price;
        private BigDecimal commission;
        private LocalDateTime executionTime;
    }
}