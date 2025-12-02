package com.waqiti.investment.kafka;

import com.waqiti.common.events.InvestmentOrderCancelledEvent;
import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.domain.InvestmentHolding;
import com.waqiti.investment.domain.enums.OrderStatus;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import com.waqiti.investment.repository.InvestmentHoldingRepository;
import com.waqiti.investment.service.OrderExecutionService;
import com.waqiti.investment.service.PortfolioService;
import com.waqiti.investment.service.NotificationService;
import com.waqiti.investment.service.RiskManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumer for InvestmentOrderCancelledEvent
 * Handles order cancellation cleanup, fund releases, position updates, and investor notifications
 * Critical for maintaining portfolio accuracy and investor confidence
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InvestmentOrderCancelledConsumer {
    
    private final InvestmentOrderRepository investmentOrderRepository;
    private final InvestmentHoldingRepository investmentHoldingRepository;
    private final OrderExecutionService orderExecutionService;
    private final PortfolioService portfolioService;
    private final NotificationService notificationService;
    private final RiskManagementService riskManagementService;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final com.waqiti.common.idempotency.IdempotencyService idempotencyService;

    // DEPRECATED: In-memory cache - replaced by persistent IdempotencyService
    // Kept for backwards compatibility during migration, will be removed
    @Deprecated
    private final Map<String, LocalDateTime> processedEvents = new HashMap<>();
    private static final int DEDUP_WINDOW_MINUTES = 10;
    
    @KafkaListener(
        topics = "investment.order.cancelled",
        groupId = "investment-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void handleInvestmentOrderCancelled(InvestmentOrderCancelledEvent event) {
        log.info("Processing investment order cancellation: Order ID: {}, User: {}, Symbol: {}, Reason: {}, Cancelled by: {}",
            event.getOrderId(), event.getUserId(), event.getSymbol(),
            event.getCancellationReason(), event.getCancelledBy());

        // CRITICAL SECURITY: Enhanced idempotency key with all unique identifiers
        String idempotencyKey = String.format("investment-order-cancelled:%s:%s:%s",
            event.getOrderId(), event.getUserId(), event.getEventId());
        UUID operationId = UUID.randomUUID();

        try {
            // STEP 1: CRITICAL SECURITY - Persistent idempotency check (survives service restarts)
            if (!idempotencyService.startOperation(idempotencyKey, operationId, Duration.ofDays(7))) {
                log.warn("SECURITY: Duplicate investment order cancellation event ignored - already processed: orderId={}, eventId={}",
                        event.getOrderId(), event.getEventId());
                return;
            }

            log.info("SECURITY: Processing new order cancellation with persistent idempotency: orderId={}, symbol={}, idempotencyKey={}",
                event.getOrderId(), event.getSymbol(), idempotencyKey);
            
            // STEP 2: Retrieve and validate order
            InvestmentOrder order = validateAndRetrieveOrder(event);

            // STEP 3: Process order cancellation
            processOrderCancellation(order, event);
            
            // STEP 4: Release held funds or securities
            releaseHeldResources(order, event);
            
            // STEP 5: Update portfolio and holdings
            updatePortfolioAndHoldings(order, event);
            
            // STEP 6: Process partial fill settlements
            if (event.isWasPartiallyFilled()) {
                processPartialFillSettlement(order, event);
            }
            
            // STEP 7: Handle refunds (fees, charges)
            processRefunds(order, event);
            
            // STEP 8: Update risk metrics and limits
            updateRiskMetricsAndLimits(order, event);
            
            // STEP 9: Compliance and audit trail
            recordComplianceAndAudit(order, event);
            
            // STEP 10: Send investor notifications
            sendInvestorNotifications(order, event);

            // STEP 11: CRITICAL SECURITY - Mark operation as completed in persistent storage
            idempotencyService.completeOperation(idempotencyKey, operationId,
                Map.of("orderId", event.getOrderId(), "userId", event.getUserId(),
                       "accountId", event.getAccountId(), "symbol", event.getSymbol(),
                       "quantity", event.getQuantity().toString(),
                       "orderAmount", event.getOrderAmount().toString(),
                       "cancellationReason", event.getCancellationReason(),
                       "cancelledBy", event.getCancelledBy(),
                       "wasPartiallyFilled", String.valueOf(event.isWasPartiallyFilled()),
                       "refundAmount", event.getRefundAmount() != null ? event.getRefundAmount().toString() : "0",
                       "status", "CANCELLED"), Duration.ofDays(7));

            // DEPRECATED: Old in-memory tracking (will be removed)
            markEventAsProcessed(event);

            log.info("Successfully processed order cancellation: Order ID: {}, Status: {}, Refund: ${}",
                event.getOrderId(), order.getStatus(), event.getRefundAmount());
                
        } catch (Exception e) {
            log.error("SECURITY: Failed to process order cancellation for order {}: {}",
                event.getOrderId(), e.getMessage(), e);

            // CRITICAL SECURITY: Mark operation as failed in persistent storage for retry logic
            idempotencyService.failOperation(idempotencyKey, operationId,
                String.format("Order cancellation failed: %s", e.getMessage()));

            // Create manual intervention alert
            createManualInterventionAlert(event, e);

            throw new RuntimeException("Investment order cancellation processing failed", e);
        }
    }
    
    private boolean isDuplicateEvent(InvestmentOrderCancelledEvent event) {
        // Clean up old entries
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(DEDUP_WINDOW_MINUTES);
        processedEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        
        String eventKey = event.getEventId();
        if (processedEvents.containsKey(eventKey)) {
            return true;
        }
        
        return false;
    }
    
    private InvestmentOrder validateAndRetrieveOrder(InvestmentOrderCancelledEvent event) {
        Optional<InvestmentOrder> orderOpt = investmentOrderRepository.findById(event.getOrderId());
        
        if (orderOpt.isEmpty()) {
            log.warn("Order not found for cancellation: {}, creating cancellation record", event.getOrderId());
            
            // Create a cancelled order record for audit trail
            InvestmentOrder cancelledOrder = InvestmentOrder.builder()
                .id(event.getOrderId())
                .userId(event.getUserId())
                .accountId(event.getAccountId())
                .symbol(event.getSymbol())
                .quantity(event.getQuantity())
                .orderType(com.waqiti.investment.domain.enums.OrderType.valueOf(event.getOrderType()))
                .orderAmount(event.getOrderAmount())
                .limitPrice(event.getLimitPrice())
                .stopPrice(event.getStopPrice())
                .timeInForce(event.getTimeInForce())
                .status(OrderStatus.CANCELLED)
                .cancellationReason(event.getCancellationReason())
                .cancelledBy(event.getCancelledBy())
                .cancelledAt(LocalDateTime.now())
                .build();
            
            return investmentOrderRepository.save(cancelledOrder);
        }
        
        InvestmentOrder order = orderOpt.get();
        
        // Validate order can be cancelled
        if (order.getStatus() == OrderStatus.FILLED) {
            log.error("Cannot cancel fully filled order: {}", event.getOrderId());
            throw new IllegalStateException("Cannot cancel filled order");
        }
        
        if (order.getStatus() == OrderStatus.CANCELLED) {
            log.warn("Order already cancelled: {}", event.getOrderId());
            // Allow idempotent processing
        }
        
        return order;
    }
    
    private void processOrderCancellation(InvestmentOrder order, InvestmentOrderCancelledEvent event) {
        log.debug("Processing cancellation for order: {}", event.getOrderId());
        
        // Update order status
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(event.getCancellationReason());
        order.setCancelledBy(event.getCancelledBy());
        order.setCancelledAt(LocalDateTime.now());
        order.setCancellationSource(event.getCancellationSource());
        
        // Set partial fill information if applicable
        if (event.isWasPartiallyFilled()) {
            order.setWasPartiallyFilled(true);
            order.setExecutedQuantity(event.getFilledQuantity());
            order.setRemainingQuantity(event.getQuantity().subtract(event.getFilledQuantity()));
            order.setExecutedAmount(event.getExecutedAmount());
        }
        
        // Set system rejection information
        if (event.isWasRejectedBySystem()) {
            order.setWasRejectedBySystem(true);
            order.setRejectionReason(event.getRejectionReason());
        }
        
        // Set compliance information
        if (event.isComplianceRelated()) {
            order.setComplianceRelated(true);
            order.setComplianceFlag(event.getComplianceFlag());
        }
        
        investmentOrderRepository.save(order);
        
        log.info("Order cancellation processed: {} - Status: {}, Reason: {}", 
            event.getOrderId(), order.getStatus(), order.getCancellationReason());
    }
    
    private void releaseHeldResources(InvestmentOrder order, InvestmentOrderCancelledEvent event) {
        log.debug("Releasing held resources for order: {}", event.getOrderId());
        
        try {
            if (event.getHeldAmount() != null && event.getHeldAmount().compareTo(BigDecimal.ZERO) > 0) {
                
                // Release held funds for buy orders
                if ("BUY".equals(event.getOrderType())) {
                    orderExecutionService.releaseHeldFunds(
                        event.getAccountId(),
                        event.getOrderId(),
                        event.getHeldAmount()
                    );
                    
                    log.info("Released held funds for cancelled buy order: {} - Amount: ${}", 
                        event.getOrderId(), event.getHeldAmount());
                }
                
                // Release held securities for sell orders
                else if ("SELL".equals(event.getOrderType()) || "SELL_SHORT".equals(event.getOrderType())) {
                    BigDecimal quantityToRelease = event.getQuantity();
                    
                    // If partially filled, only release unfilled quantity
                    if (event.isWasPartiallyFilled()) {
                        quantityToRelease = event.getQuantity().subtract(event.getFilledQuantity());
                    }
                    
                    orderExecutionService.releaseHeldSecurities(
                        event.getAccountId(),
                        event.getSymbol(),
                        quantityToRelease,
                        event.getOrderId()
                    );
                    
                    log.info("Released held securities for cancelled sell order: {} - Symbol: {}, Quantity: {}", 
                        event.getOrderId(), event.getSymbol(), quantityToRelease);
                }
            }
            
            // Release margin holds if applicable
            if (order.getMarginRequirement() != null && 
                order.getMarginRequirement().compareTo(BigDecimal.ZERO) > 0) {
                
                orderExecutionService.releaseMarginHold(
                    event.getAccountId(),
                    event.getOrderId(),
                    order.getMarginRequirement()
                );
                
                log.info("Released margin hold for cancelled order: {} - Margin: ${}", 
                    event.getOrderId(), order.getMarginRequirement());
            }
            
        } catch (Exception e) {
            log.error("Failed to release held resources for order {}: {}", 
                event.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("Resource release failed", e);
        }
    }
    
    private void updatePortfolioAndHoldings(InvestmentOrder order, InvestmentOrderCancelledEvent event) {
        log.debug("Updating portfolio for cancelled order: {}", event.getOrderId());
        
        try {
            // Recalculate portfolio metrics without the pending order
            portfolioService.recalculatePortfolioMetrics(
                event.getUserId(),
                event.getAccountId()
            );
            
            // Update portfolio pending orders count
            portfolioService.updatePendingOrdersCount(
                event.getUserId(),
                event.getAccountId(),
                -1 // Decrement
            );
            
            // If order was partially filled, update holdings with filled portion
            if (event.isWasPartiallyFilled() && event.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
                
                if ("BUY".equals(event.getOrderType())) {
                    // Add filled quantity to holdings
                    portfolioService.updateHolding(
                        event.getUserId(),
                        event.getAccountId(),
                        event.getSymbol(),
                        event.getFilledQuantity(),
                        order.getExecutionPrice()
                    );
                    
                    log.info("Updated holdings with partial fill: Symbol: {}, Quantity: {}", 
                        event.getSymbol(), event.getFilledQuantity());
                    
                } else if ("SELL".equals(event.getOrderType())) {
                    // Reduce holdings by filled quantity
                    portfolioService.reduceHolding(
                        event.getUserId(),
                        event.getAccountId(),
                        event.getSymbol(),
                        event.getFilledQuantity()
                    );
                    
                    log.info("Reduced holdings with partial fill: Symbol: {}, Quantity: {}", 
                        event.getSymbol(), event.getFilledQuantity());
                }
            }
            
            // Update available buying power
            portfolioService.recalculateAvailableBuyingPower(
                event.getUserId(),
                event.getAccountId()
            );
            
        } catch (Exception e) {
            log.error("Failed to update portfolio for cancelled order {}: {}", 
                event.getOrderId(), e.getMessage(), e);
            // Non-fatal - continue processing
        }
    }
    
    private void processPartialFillSettlement(InvestmentOrder order, InvestmentOrderCancelledEvent event) {
        log.info("Processing partial fill settlement for order: {} - Filled: {}/{}", 
            event.getOrderId(), event.getFilledQuantity(), event.getQuantity());
        
        try {
            // Generate partial fill settlement record
            String settlementId = orderExecutionService.generatePartialFillSettlement(
                event.getOrderId(),
                event.getSymbol(),
                event.getFilledQuantity(),
                event.getQuantity().subtract(event.getFilledQuantity()),
                event.getExecutedAmount(),
                order.getExecutionPrice()
            );
            
            order.setPartialFillSettlementId(settlementId);
            investmentOrderRepository.save(order);
            
            // Calculate pro-rated fees for partial fill
            if (order.getTotalFees() != null) {
                BigDecimal fillRatio = event.getFilledQuantity().divide(
                    event.getQuantity(), 6, RoundingMode.HALF_UP
                );
                
                BigDecimal appliedFees = order.getTotalFees().multiply(fillRatio);
                BigDecimal refundFees = order.getTotalFees().subtract(appliedFees);
                
                order.setAppliedFees(appliedFees);
                order.setRefundedFees(refundFees);
                
                log.info("Calculated pro-rated fees for partial fill: Applied: ${}, Refunded: ${}", 
                    appliedFees, refundFees);
            }
            
            // Generate trade confirmation for filled portion
            String confirmationId = orderExecutionService.generateTradeConfirmation(
                event.getOrderId(),
                event.getSymbol(),
                event.getFilledQuantity(),
                order.getExecutionPrice(),
                LocalDateTime.now()
            );
            
            order.setTradeConfirmationId(confirmationId);
            investmentOrderRepository.save(order);
            
            log.info("Partial fill settlement completed: Settlement ID: {}, Confirmation ID: {}", 
                settlementId, confirmationId);
                
        } catch (Exception e) {
            log.error("Failed to process partial fill settlement for order {}: {}", 
                event.getOrderId(), e.getMessage(), e);
            // Non-fatal - continue processing
        }
    }
    
    private void processRefunds(InvestmentOrder order, InvestmentOrderCancelledEvent event) {
        log.debug("Processing refunds for cancelled order: {}", event.getOrderId());
        
        try {
            BigDecimal totalRefund = BigDecimal.ZERO;
            
            // Refund order fees if applicable
            if (event.getRefundAmount() != null && event.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
                orderExecutionService.processRefund(
                    event.getAccountId(),
                    event.getOrderId(),
                    event.getRefundAmount(),
                    "Order cancellation refund"
                );
                
                totalRefund = totalRefund.add(event.getRefundAmount());
                
                log.info("Processed order cancellation refund: {} - Amount: ${}", 
                    event.getOrderId(), event.getRefundAmount());
            }
            
            // Refund pro-rated fees for partial fills
            if (order.getRefundedFees() != null && order.getRefundedFees().compareTo(BigDecimal.ZERO) > 0) {
                orderExecutionService.processRefund(
                    event.getAccountId(),
                    event.getOrderId(),
                    order.getRefundedFees(),
                    "Pro-rated fee refund for partial fill"
                );
                
                totalRefund = totalRefund.add(order.getRefundedFees());
                
                log.info("Processed pro-rated fee refund: {} - Amount: ${}", 
                    event.getOrderId(), order.getRefundedFees());
            }
            
            order.setTotalRefundAmount(totalRefund);
            investmentOrderRepository.save(order);
            
        } catch (Exception e) {
            log.error("Failed to process refunds for order {}: {}", 
                event.getOrderId(), e.getMessage(), e);
            // Non-fatal - continue processing
        }
    }
    
    private void updateRiskMetricsAndLimits(InvestmentOrder order, InvestmentOrderCancelledEvent event) {
        log.debug("Updating risk metrics for cancelled order: {}", event.getOrderId());
        
        try {
            // Recalculate portfolio risk without the pending order
            riskManagementService.recalculatePortfolioRisk(
                event.getUserId(),
                event.getAccountId()
            );
            
            // Update concentration limits
            riskManagementService.updateConcentrationMetrics(
                event.getUserId(),
                event.getAccountId(),
                event.getSymbol(),
                event.getOrderAmount().negate() // Remove order from concentration calc
            );
            
            // Update day trading counter if applicable
            if ("SELL".equals(event.getOrderType())) {
                riskManagementService.updateDayTradingCounter(
                    event.getUserId(),
                    event.getAccountId(),
                    event.getSymbol(),
                    false // Cancelled sell order
                );
            }
            
            log.info("Risk metrics updated for cancelled order: {}", event.getOrderId());
            
        } catch (Exception e) {
            log.error("Failed to update risk metrics for order {}: {}", 
                event.getOrderId(), e.getMessage(), e);
            // Non-fatal - continue processing
        }
    }
    
    private void recordComplianceAndAudit(InvestmentOrder order, InvestmentOrderCancelledEvent event) {
        log.debug("Recording compliance and audit trail for cancelled order: {}", event.getOrderId());
        
        try {
            // Create audit record
            Map<String, Object> auditData = new HashMap<>();
            auditData.put("orderId", event.getOrderId());
            auditData.put("userId", event.getUserId());
            auditData.put("accountId", event.getAccountId());
            auditData.put("symbol", event.getSymbol());
            auditData.put("quantity", event.getQuantity());
            auditData.put("orderType", event.getOrderType());
            auditData.put("orderAmount", event.getOrderAmount());
            auditData.put("cancellationReason", event.getCancellationReason());
            auditData.put("cancelledBy", event.getCancelledBy());
            auditData.put("cancellationSource", event.getCancellationSource());
            auditData.put("wasPartiallyFilled", event.isWasPartiallyFilled());
            auditData.put("filledQuantity", event.getFilledQuantity());
            auditData.put("wasRejectedBySystem", event.isWasRejectedBySystem());
            auditData.put("complianceRelated", event.isComplianceRelated());
            auditData.put("timestamp", event.getTimestamp());
            
            orderExecutionService.recordOrderCancellationAudit(auditData);
            
            // If compliance-related, create compliance record
            if (event.isComplianceRelated()) {
                orderExecutionService.recordComplianceCancellation(
                    event.getOrderId(),
                    event.getUserId(),
                    event.getComplianceFlag(),
                    event.getCancellationReason()
                );
                
                log.warn("Compliance-related order cancellation recorded: {} - Flag: {}", 
                    event.getOrderId(), event.getComplianceFlag());
            }
            
            log.info("Audit trail recorded for cancelled order: {}", event.getOrderId());
            
        } catch (Exception e) {
            log.error("Failed to record audit trail for order {}: {}", 
                event.getOrderId(), e.getMessage(), e);
            // Non-fatal - continue processing
        }
    }
    
    private void sendInvestorNotifications(InvestmentOrder order, InvestmentOrderCancelledEvent event) {
        log.debug("Sending investor notifications for cancelled order: {}", event.getOrderId());
        
        try {
            // Determine notification type based on cancellation reason
            if ("USER".equals(event.getCancelledBy())) {
                // User-initiated cancellation
                notificationService.sendOrderCancellationConfirmation(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getSymbol(),
                    event.getQuantity(),
                    event.getCancellationReason()
                );
                
            } else if ("SYSTEM".equals(event.getCancelledBy()) || "COMPLIANCE".equals(event.getCancelledBy())) {
                // System or compliance cancellation
                notificationService.sendSystemCancellationNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getSymbol(),
                    event.getQuantity(),
                    event.getCancellationReason(),
                    event.isComplianceRelated()
                );
                
            } else if ("ADMIN".equals(event.getCancelledBy())) {
                // Admin cancellation
                notificationService.sendAdminCancellationNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getSymbol(),
                    event.getQuantity(),
                    event.getCancellationReason()
                );
            }
            
            // Send partial fill notification if applicable
            if (event.isWasPartiallyFilled()) {
                notificationService.sendPartialFillCancellationNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getSymbol(),
                    event.getFilledQuantity(),
                    event.getQuantity(),
                    order.getExecutionPrice(),
                    event.getExecutedAmount()
                );
            }
            
            // Send refund notification if applicable
            if (event.getRefundAmount() != null && event.getRefundAmount().compareTo(BigDecimal.ZERO) > 0) {
                notificationService.sendRefundNotification(
                    event.getUserId(),
                    event.getOrderId(),
                    event.getRefundAmount(),
                    "Order cancellation"
                );
            }
            
            log.info("Investor notifications sent for cancelled order: {}", event.getOrderId());
            
        } catch (Exception e) {
            log.error("Failed to send investor notifications for order {}: {}", 
                event.getOrderId(), e.getMessage(), e);
            // Non-fatal - log and continue
        }
    }
    
    private void markEventAsProcessed(InvestmentOrderCancelledEvent event) {
        processedEvents.put(event.getEventId(), LocalDateTime.now());
        log.debug("Marked event as processed: {}", event.getEventId());
    }
    
    private void createManualInterventionAlert(InvestmentOrderCancelledEvent event, Exception exception) {
        try {
            Map<String, Object> alertData = new HashMap<>();
            alertData.put("eventType", "INVESTMENT_ORDER_CANCELLATION_FAILED");
            alertData.put("orderId", event.getOrderId());
            alertData.put("userId", event.getUserId());
            alertData.put("accountId", event.getAccountId());
            alertData.put("symbol", event.getSymbol());
            alertData.put("quantity", event.getQuantity());
            alertData.put("orderAmount", event.getOrderAmount());
            alertData.put("cancellationReason", event.getCancellationReason());
            alertData.put("errorMessage", exception.getMessage());
            alertData.put("severity", "CRITICAL");
            alertData.put("requiresImmediateAction", true);
            alertData.put("timestamp", LocalDateTime.now());
            
            kafkaTemplate.send("monitoring.manual-intervention.required", alertData);
            
            log.error("Created manual intervention alert for failed order cancellation: {}", event.getOrderId());
            
        } catch (Exception e) {
            log.error("Failed to create manual intervention alert: {}", e.getMessage());
        }
    }
}