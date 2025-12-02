package com.waqiti.crypto.trading;

import com.waqiti.common.cache.CacheService;
import com.waqiti.common.cache.DistributedLockService;
import com.waqiti.common.event.EventPublisher;
import com.waqiti.crypto.dto.request.BuyCryptocurrencyRequest;
import com.waqiti.crypto.dto.request.SellCryptocurrencyRequest;
import com.waqiti.crypto.dto.response.TradeResponse;
import com.waqiti.crypto.dto.response.TradingOrderResponse;
import com.waqiti.crypto.entity.CryptoCurrency;
import com.waqiti.crypto.entity.CryptoWallet;
import com.waqiti.crypto.entity.TradeOrder;
import com.waqiti.crypto.entity.TradeOrderStatus;
import com.waqiti.crypto.entity.TradeOrderType;
import com.waqiti.crypto.entity.TradePair;
import com.waqiti.crypto.event.TradeExecutedEvent;
import com.waqiti.crypto.event.TradeOrderCreatedEvent;
import com.waqiti.crypto.event.TradeOrderCancelledEvent;
import com.waqiti.crypto.exception.InsufficientBalanceException;
import com.waqiti.crypto.exception.InvalidTradePairException;
import com.waqiti.crypto.exception.TradeOrderNotFoundException;
import com.waqiti.crypto.pricing.CryptoPricingService;
import com.waqiti.crypto.repository.TradeOrderRepository;
import com.waqiti.crypto.repository.TradePairRepository;
import com.waqiti.crypto.service.CryptoWalletService;
import com.waqiti.crypto.trading.execution.OrderMatchingEngine;
import com.waqiti.crypto.trading.execution.TradeExecutionEngine;
import com.waqiti.crypto.trading.risk.TradingRiskManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CryptoTradingService {

    private final TradeOrderRepository tradeOrderRepository;
    private final TradePairRepository tradePairRepository;
    private final CryptoWalletService walletService;
    private final CryptoPricingService pricingService;
    private final OrderMatchingEngine matchingEngine;
    private final TradeExecutionEngine executionEngine;
    private final TradingRiskManager riskManager;
    private final CacheService cacheService;
    private final DistributedLockService lockService;
    private final EventPublisher eventPublisher;

    @Transactional
    public TradingOrderResponse placeBuyOrder(UUID userId, BuyCryptocurrencyRequest request) {
        log.info("Placing buy order for user: {} cryptocurrency: {} amount: {}", 
                userId, request.getCryptocurrency(), request.getAmount());

        String lockKey = "trading:user:" + userId;
        return lockService.executeWithLock(lockKey, Duration.ofMinutes(2), Duration.ofSeconds(30), () -> {
            
            // Validate trading pair
            TradePair tradePair = validateAndGetTradePair(request.getCryptocurrency(), request.getBaseCurrency());
            
            // Get current market price
            BigDecimal currentPrice = pricingService.getCurrentPrice(request.getCryptocurrency());
            
            // Determine order price based on order type
            BigDecimal orderPrice = calculateOrderPrice(request.getOrderType(), currentPrice, request.getPrice());
            
            // Calculate total cost including fees
            BigDecimal tradingFee = calculateTradingFee(request.getAmount(), orderPrice);
            BigDecimal totalCost = request.getAmount().multiply(orderPrice).add(tradingFee);
            
            // Validate user has sufficient balance
            validateSufficientBalance(userId, request.getBaseCurrency(), totalCost);
            
            // Perform risk checks
            riskManager.validateTradeRisk(userId, request.getCryptocurrency(), request.getAmount(), orderPrice);
            
            // Create trade order
            TradeOrder order = TradeOrder.builder()
                    .userId(userId)
                    .tradePairId(tradePair.getId())
                    .orderType(TradeOrderType.valueOf(request.getOrderType()))
                    .side("BUY")
                    .quantity(request.getAmount())
                    .price(orderPrice)
                    .totalValue(totalCost)
                    .tradingFee(tradingFee)
                    .status(TradeOrderStatus.PENDING)
                    .timeInForce(request.getTimeInForce())
                    .goodTillTime(request.getGoodTillTime())
                    .stopPrice(request.getStopPrice())
                    .metadata(request.getMetadata())
                    .build();

            order = tradeOrderRepository.save(order);
            
            // Reserve funds
            walletService.reserveBalance(userId, request.getBaseCurrency(), totalCost, order.getOrderId());
            
            // Cache order
            cacheTradeOrder(order);
            
            // Submit to matching engine
            if (order.getOrderType() == TradeOrderType.MARKET || 
                shouldExecuteImmediately(order, currentPrice)) {
                matchingEngine.submitOrder(order);
            }
            
            // Publish event
            eventPublisher.publish(TradeOrderCreatedEvent.builder()
                    .orderId(order.getId())
                    .userId(userId)
                    .cryptocurrency(request.getCryptocurrency())
                    .amount(request.getAmount())
                    .price(orderPrice)
                    .orderType(order.getOrderType().name())
                    .build());
            
            log.info("Buy order placed successfully: {}", order.getOrderId());
            
            return mapToTradingOrderResponse(order, tradePair);
        });
    }

    @Transactional
    public TradingOrderResponse placeSellOrder(UUID userId, SellCryptocurrencyRequest request) {
        log.info("Placing sell order for user: {} cryptocurrency: {} amount: {}", 
                userId, request.getCryptocurrency(), request.getAmount());

        String lockKey = "trading:user:" + userId;
        return lockService.executeWithLock(lockKey, Duration.ofMinutes(2), Duration.ofSeconds(30), () -> {
            
            // Validate trading pair
            TradePair tradePair = validateAndGetTradePair(request.getCryptocurrency(), request.getBaseCurrency());
            
            // Get current market price
            BigDecimal currentPrice = pricingService.getCurrentPrice(request.getCryptocurrency());
            
            // Determine order price based on order type
            BigDecimal orderPrice = calculateOrderPrice(request.getOrderType(), currentPrice, request.getPrice());
            
            // Calculate trading fee
            BigDecimal tradingFee = calculateTradingFee(request.getAmount(), orderPrice);
            BigDecimal totalValue = request.getAmount().multiply(orderPrice).subtract(tradingFee);
            
            // Validate user has sufficient cryptocurrency balance
            validateSufficientCryptoBalance(userId, request.getCryptocurrency(), request.getAmount());
            
            // Perform risk checks
            riskManager.validateTradeRisk(userId, request.getCryptocurrency(), request.getAmount(), orderPrice);
            
            // Create trade order
            TradeOrder order = TradeOrder.builder()
                    .userId(userId)
                    .tradePairId(tradePair.getId())
                    .orderType(TradeOrderType.valueOf(request.getOrderType()))
                    .side("SELL")
                    .quantity(request.getAmount())
                    .price(orderPrice)
                    .totalValue(totalValue)
                    .tradingFee(tradingFee)
                    .status(TradeOrderStatus.PENDING)
                    .timeInForce(request.getTimeInForce())
                    .goodTillTime(request.getGoodTillTime())
                    .stopPrice(request.getStopPrice())
                    .metadata(request.getMetadata())
                    .build();

            order = tradeOrderRepository.save(order);
            
            // Reserve cryptocurrency
            walletService.reserveCryptoBalance(userId, request.getCryptocurrency(), request.getAmount(), order.getOrderId());
            
            // Cache order
            cacheTradeOrder(order);
            
            // Submit to matching engine
            if (order.getOrderType() == TradeOrderType.MARKET || 
                shouldExecuteImmediately(order, currentPrice)) {
                matchingEngine.submitOrder(order);
            }
            
            // Publish event
            eventPublisher.publish(TradeOrderCreatedEvent.builder()
                    .orderId(order.getId())
                    .userId(userId)
                    .cryptocurrency(request.getCryptocurrency())
                    .amount(request.getAmount())
                    .price(orderPrice)
                    .orderType(order.getOrderType().name())
                    .build());
            
            log.info("Sell order placed successfully: {}", order.getOrderId());
            
            return mapToTradingOrderResponse(order, tradePair);
        });
    }

    @Transactional
    public void cancelOrder(UUID userId, String orderId) {
        log.info("Cancelling order: {} for user: {}", orderId, userId);

        TradeOrder order = tradeOrderRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new TradeOrderNotFoundException("Order not found: " + orderId));

        if (!order.getStatus().isCancellable()) {
            throw new IllegalStateException("Order cannot be cancelled in current status: " + order.getStatus());
        }

        // Remove from matching engine
        matchingEngine.cancelOrder(order);

        // Release reserved funds
        if ("BUY".equals(order.getSide())) {
            walletService.releaseReservedBalance(userId, order.getTradePair().getBaseCurrency().getSymbol(), 
                    order.getTotalValue(), orderId);
        } else {
            walletService.releaseReservedCryptoBalance(userId, order.getTradePair().getTradeCurrency().getSymbol(), 
                    order.getQuantity(), orderId);
        }

        // Update order status
        order.setStatus(TradeOrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        tradeOrderRepository.save(order);

        // Update cache
        cacheTradeOrder(order);

        // Publish event
        eventPublisher.publish(TradeOrderCancelledEvent.builder()
                .orderId(order.getId())
                .userId(userId)
                .reason("User requested cancellation")
                .build());

        log.info("Order cancelled successfully: {}", orderId);
    }

    @Transactional(readOnly = true)
    public TradingOrderResponse getOrder(UUID userId, String orderId) {
        String cacheKey = cacheService.buildKey("trade-order", orderId);
        
        TradeOrder cached = cacheService.get(cacheKey, TradeOrder.class);
        if (cached != null && cached.getUserId().equals(userId)) {
            return mapToTradingOrderResponse(cached, cached.getTradePair());
        }
        
        TradeOrder order = tradeOrderRepository.findByOrderIdAndUserId(orderId, userId)
                .orElseThrow(() -> new TradeOrderNotFoundException("Order not found: " + orderId));
        
        cacheTradeOrder(order);
        return mapToTradingOrderResponse(order, order.getTradePair());
    }

    @Transactional(readOnly = true)
    public Page<TradingOrderResponse> getUserOrders(UUID userId, String status, Pageable pageable) {
        Page<TradeOrder> orders;
        
        if (status != null) {
            orders = tradeOrderRepository.findByUserIdAndStatus(userId, TradeOrderStatus.valueOf(status), pageable);
        } else {
            orders = tradeOrderRepository.findByUserId(userId, pageable);
        }
        
        return orders.map(order -> mapToTradingOrderResponse(order, order.getTradePair()));
    }

    @Transactional(readOnly = true)
    public List<TradeResponse> getUserTrades(UUID userId, String cryptocurrency, Pageable pageable) {
        // Implementation for retrieving executed trades
        return executionEngine.getUserTrades(userId, cryptocurrency, pageable);
    }

    @Transactional(readOnly = true)
    public List<TradePair> getAvailableTradePairs() {
        String cacheKey = cacheService.buildKey("trade-pairs", "active");
        
        @SuppressWarnings("unchecked")
        List<TradePair> cached = (List<TradePair>) cacheService.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        
        List<TradePair> tradePairs = tradePairRepository.findByActiveTrue();
        cacheService.set(cacheKey, tradePairs, Duration.ofMinutes(30));
        
        return tradePairs;
    }

    @Transactional(readOnly = true)
    public BigDecimal getTradingFee(UUID userId, String cryptocurrency, BigDecimal amount) {
        BigDecimal currentPrice = pricingService.getCurrentPrice(cryptocurrency);
        return calculateTradingFee(amount, currentPrice);
    }

    private TradePair validateAndGetTradePair(String tradeCurrency, String baseCurrency) {
        return tradePairRepository.findByTradeCurrencySymbolAndBaseCurrencySymbolAndActiveTrue(tradeCurrency, baseCurrency)
                .orElseThrow(() -> new InvalidTradePairException("Invalid trading pair: " + tradeCurrency + "/" + baseCurrency));
    }

    private BigDecimal calculateOrderPrice(String orderType, BigDecimal currentPrice, BigDecimal requestedPrice) {
        switch (TradeOrderType.valueOf(orderType)) {
            case MARKET:
                return currentPrice;
            case LIMIT:
                return requestedPrice;
            case STOP_LOSS:
            case STOP_LIMIT:
                return requestedPrice != null ? requestedPrice : currentPrice;
            default:
                throw new IllegalArgumentException("Unsupported order type: " + orderType);
        }
    }

    private BigDecimal calculateTradingFee(BigDecimal amount, BigDecimal price) {
        // Base trading fee: 0.1%
        BigDecimal feeRate = new BigDecimal("0.001");
        BigDecimal tradeValue = amount.multiply(price);
        return tradeValue.multiply(feeRate).setScale(8, RoundingMode.HALF_UP);
    }

    private void validateSufficientBalance(UUID userId, String currency, BigDecimal amount) {
        BigDecimal availableBalance = walletService.getAvailableBalance(userId, currency);
        if (availableBalance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient " + currency + " balance. Required: " + amount + ", Available: " + availableBalance);
        }
    }

    private void validateSufficientCryptoBalance(UUID userId, String cryptocurrency, BigDecimal amount) {
        BigDecimal availableBalance = walletService.getAvailableCryptoBalance(userId, cryptocurrency);
        if (availableBalance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Insufficient " + cryptocurrency + " balance. Required: " + amount + ", Available: " + availableBalance);
        }
    }

    private boolean shouldExecuteImmediately(TradeOrder order, BigDecimal currentPrice) {
        if (order.getOrderType() == TradeOrderType.LIMIT) {
            if ("BUY".equals(order.getSide())) {
                return currentPrice.compareTo(order.getPrice()) <= 0;
            } else {
                return currentPrice.compareTo(order.getPrice()) >= 0;
            }
        }
        return false;
    }

    private void cacheTradeOrder(TradeOrder order) {
        String cacheKey = cacheService.buildKey("trade-order", order.getOrderId());
        cacheService.set(cacheKey, order, Duration.ofHours(1));
    }

    private TradingOrderResponse mapToTradingOrderResponse(TradeOrder order, TradePair tradePair) {
        return TradingOrderResponse.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .tradePair(tradePair.getTradeCurrency().getSymbol() + "/" + tradePair.getBaseCurrency().getSymbol())
                .orderType(order.getOrderType().name())
                .side(order.getSide())
                .quantity(order.getQuantity())
                .price(order.getPrice())
                .totalValue(order.getTotalValue())
                .filledQuantity(order.getFilledQuantity())
                .averagePrice(order.getAveragePrice())
                .tradingFee(order.getTradingFee())
                .status(order.getStatus().name())
                .timeInForce(order.getTimeInForce())
                .goodTillTime(order.getGoodTillTime())
                .stopPrice(order.getStopPrice())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    /**
     * Modify an existing trading order
     */
    @Transactional
    public TradingOrderResponse modifyOrder(UUID userId, String orderId, OrderModificationRequest request) {
        log.info("Modifying order: {} for user: {}", orderId, userId);

        String lockKey = "trading:order:" + orderId;
        return lockService.executeWithLock(lockKey, Duration.ofMinutes(2), Duration.ofSeconds(30), () -> {
            
            // Retrieve the existing order
            TradeOrder existingOrder = tradeOrderRepository.findByOrderIdAndUserId(orderId, userId)
                    .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
            
            // Validate order can be modified
            validateOrderCanBeModified(existingOrder);
            
            // Validate the modification request
            validateModificationRequest(existingOrder, request);
            
            // Calculate any balance adjustments needed
            BigDecimal balanceAdjustment = calculateBalanceAdjustment(existingOrder, request);
            
            // Check if user has sufficient balance for increased order size
            if (balanceAdjustment.compareTo(BigDecimal.ZERO) > 0) {
                String currency = existingOrder.getSide().equals("BUY") 
                    ? existingOrder.getTradePair().getBaseCurrency().getSymbol()
                    : existingOrder.getTradePair().getTradeCurrency().getSymbol();
                validateSufficientBalance(userId, currency, balanceAdjustment);
            }
            
            // Create modified order (preserve original order ID and creation time)
            TradeOrder modifiedOrder = createModifiedOrder(existingOrder, request);
            
            // Perform risk checks on the modified order
            if (request.getQuantity() != null || request.getPrice() != null) {
                BigDecimal orderPrice = request.getPrice() != null ? request.getPrice() : existingOrder.getPrice();
                BigDecimal orderQuantity = request.getQuantity() != null ? request.getQuantity() : existingOrder.getQuantity();
                riskManager.validateTradeRisk(userId, existingOrder.getTradePair().getTradeCurrency().getSymbol(), 
                                            orderQuantity, orderPrice);
            }
            
            // Adjust wallet reservations
            if (balanceAdjustment.compareTo(BigDecimal.ZERO) != 0) {
                adjustWalletReservations(userId, existingOrder, balanceAdjustment);
            }
            
            // Save the modified order
            TradeOrder savedOrder = tradeOrderRepository.save(modifiedOrder);
            
            // Update cache
            cacheTradeOrder(savedOrder);
            
            // Publish order modified event
            eventPublisher.publishEvent(new OrderModifiedEvent(savedOrder, existingOrder, request.getModificationReason()));
            
            // Check if the modified order should be matched immediately
            if (shouldExecuteImmediately(savedOrder, pricingService.getCurrentPrice(savedOrder.getTradePair().getTradeCurrency().getSymbol()))) {
                matchingEngine.processOrder(savedOrder);
            }
            
            log.info("Successfully modified order: {} for user: {}", orderId, userId);
            return mapToTradingOrderResponse(savedOrder, savedOrder.getTradePair());
        });
    }
    
    private void validateOrderCanBeModified(TradeOrder order) {
        if (order.getStatus() == OrderStatus.FILLED) {
            throw new OrderModificationException("Cannot modify a filled order");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderModificationException("Cannot modify a cancelled order");
        }
        if (order.getStatus() == OrderStatus.EXPIRED) {
            throw new OrderModificationException("Cannot modify an expired order");
        }
        if (order.getStatus() == OrderStatus.REJECTED) {
            throw new OrderModificationException("Cannot modify a rejected order");
        }
        
        // Allow modification of PENDING, PARTIALLY_FILLED orders only
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.PARTIALLY_FILLED) {
            throw new OrderModificationException("Order cannot be modified in current status: " + order.getStatus());
        }
    }
    
    private void validateModificationRequest(TradeOrder existingOrder, OrderModificationRequest request) {
        // Validate at least one field is being modified
        if (request.getQuantity() == null && request.getPrice() == null && 
            request.getStopPrice() == null && request.getOrderType() == null &&
            request.getTimeInForceMinutes() == null) {
            throw new IllegalArgumentException("At least one field must be specified for modification");
        }
        
        // Validate order type changes
        if (request.getOrderType() != null && !isValidOrderTypeChange(existingOrder.getOrderType(), request.getOrderType())) {
            throw new IllegalArgumentException("Invalid order type change from " + existingOrder.getOrderType() + " to " + request.getOrderType());
        }
        
        // Validate quantity is not reduced below filled amount
        if (request.getQuantity() != null && 
            request.getQuantity().compareTo(existingOrder.getFilledQuantity()) < 0) {
            throw new IllegalArgumentException("Cannot reduce quantity below already filled amount: " + existingOrder.getFilledQuantity());
        }
        
        // Validate price makes sense for the order type
        if (request.getOrderType() == TradeOrderType.MARKET && request.getPrice() != null) {
            throw new IllegalArgumentException("Market orders cannot have a specified price");
        }
        
        if ((request.getOrderType() == TradeOrderType.LIMIT || request.getOrderType() == TradeOrderType.STOP_LIMIT) && 
            request.getPrice() == null && existingOrder.getPrice() == null) {
            throw new IllegalArgumentException("Limit orders must have a price specified");
        }
    }
    
    private boolean isValidOrderTypeChange(TradeOrderType from, TradeOrderType to) {
        // Define valid order type transitions
        switch (from) {
            case LIMIT:
                return to == TradeOrderType.MARKET || to == TradeOrderType.STOP_LIMIT;
            case MARKET:
                return to == TradeOrderType.LIMIT;
            case STOP_LOSS:
                return to == TradeOrderType.STOP_LIMIT || to == TradeOrderType.MARKET;
            case STOP_LIMIT:
                return to == TradeOrderType.LIMIT || to == TradeOrderType.STOP_LOSS;
            default:
                // For complex order types, only allow same type modifications
                return from == to;
        }
    }
    
    private BigDecimal calculateBalanceAdjustment(TradeOrder existingOrder, OrderModificationRequest request) {
        if (request.getQuantity() == null && request.getPrice() == null) {
            return BigDecimal.ZERO;
        }
        
        BigDecimal newQuantity = request.getQuantity() != null ? request.getQuantity() : existingOrder.getQuantity();
        BigDecimal newPrice = request.getPrice() != null ? request.getPrice() : existingOrder.getPrice();
        
        BigDecimal newTotalValue = newQuantity.multiply(newPrice);
        BigDecimal existingTotalValue = existingOrder.getQuantity().multiply(existingOrder.getPrice());
        
        return newTotalValue.subtract(existingTotalValue);
    }
    
    private TradeOrder createModifiedOrder(TradeOrder existingOrder, OrderModificationRequest request) {
        TradeOrder.TradeOrderBuilder builder = existingOrder.toBuilder();
        
        // Update fields that are being modified
        if (request.getQuantity() != null) {
            builder.quantity(request.getQuantity())
                   .totalValue(request.getQuantity().multiply(
                       request.getPrice() != null ? request.getPrice() : existingOrder.getPrice()));
        }
        
        if (request.getPrice() != null) {
            builder.price(request.getPrice());
            if (request.getQuantity() == null) {
                builder.totalValue(existingOrder.getQuantity().multiply(request.getPrice()));
            }
        }
        
        if (request.getStopPrice() != null) {
            builder.stopPrice(request.getStopPrice());
        }
        
        if (request.getOrderType() != null) {
            builder.orderType(request.getOrderType());
        }
        
        if (request.getTimeInForceMinutes() != null) {
            builder.goodTillTime(LocalDateTime.now().plusMinutes(request.getTimeInForceMinutes()));
        }
        
        // Update modification metadata
        builder.updatedAt(LocalDateTime.now())
               .modifiedCount(existingOrder.getModifiedCount() + 1)
               .lastModificationReason(request.getModificationReason());
        
        return builder.build();
    }
    
    private void adjustWalletReservations(UUID userId, TradeOrder existingOrder, BigDecimal balanceAdjustment) {
        String currency = existingOrder.getSide().equals("BUY") 
            ? existingOrder.getTradePair().getBaseCurrency().getSymbol()
            : existingOrder.getTradePair().getTradeCurrency().getSymbol();
            
        if (balanceAdjustment.compareTo(BigDecimal.ZERO) > 0) {
            // Increase reservation
            walletService.reserveBalance(userId, currency, balanceAdjustment, existingOrder.getOrderId());
        } else if (balanceAdjustment.compareTo(BigDecimal.ZERO) < 0) {
            // Release excess reservation
            walletService.releaseReservation(userId, currency, balanceAdjustment.negate(), existingOrder.getOrderId());
        }
    }
}