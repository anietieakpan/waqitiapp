package com.waqiti.investment.kafka;

import com.waqiti.common.events.InvestmentOrderCancelledEvent;
import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.domain.enums.OrderStatus;
import com.waqiti.investment.domain.enums.OrderType;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import com.waqiti.investment.repository.InvestmentHoldingRepository;
import com.waqiti.investment.service.OrderExecutionService;
import com.waqiti.investment.service.PortfolioService;
import com.waqiti.investment.service.NotificationService;
import com.waqiti.investment.service.RiskManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestmentOrderCancelledConsumerTest {

    @Mock
    private InvestmentOrderRepository investmentOrderRepository;
    
    @Mock
    private InvestmentHoldingRepository investmentHoldingRepository;
    
    @Mock
    private OrderExecutionService orderExecutionService;
    
    @Mock
    private PortfolioService portfolioService;
    
    @Mock
    private NotificationService notificationService;
    
    @Mock
    private RiskManagementService riskManagementService;
    
    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;
    
    @InjectMocks
    private InvestmentOrderCancelledConsumer consumer;
    
    private InvestmentOrderCancelledEvent testEvent;
    private InvestmentOrder testOrder;
    
    @BeforeEach
    void setUp() {
        testEvent = InvestmentOrderCancelledEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .orderId("order-123")
            .userId("user-456")
            .accountId("account-789")
            .symbol("AAPL")
            .quantity(new BigDecimal("100"))
            .orderType("BUY")
            .orderAmount(new BigDecimal("15000.00"))
            .cancellationReason("User requested cancellation")
            .cancelledBy("USER")
            .heldAmount(new BigDecimal("15000.00"))
            .wasPartiallyFilled(false)
            .filledQuantity(BigDecimal.ZERO)
            .executedAmount(BigDecimal.ZERO)
            .refundAmount(new BigDecimal("50.00"))
            .complianceRelated(false)
            .build();
            
        testOrder = InvestmentOrder.builder()
            .id("order-123")
            .userId("user-456")
            .accountId("account-789")
            .symbol("AAPL")
            .quantity(new BigDecimal("100"))
            .orderType(OrderType.BUY)
            .orderAmount(new BigDecimal("15000.00"))
            .status(OrderStatus.PENDING)
            .totalFees(new BigDecimal("50.00"))
            .build();
    }
    
    @Test
    void shouldProcessOrderCancellationSuccessfully() {
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        ArgumentCaptor<InvestmentOrder> orderCaptor = ArgumentCaptor.forClass(InvestmentOrder.class);
        verify(investmentOrderRepository, atLeastOnce()).save(orderCaptor.capture());
        
        InvestmentOrder updatedOrder = orderCaptor.getValue();
        assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(updatedOrder.getCancellationReason()).isEqualTo("User requested cancellation");
        assertThat(updatedOrder.getCancelledBy()).isEqualTo("USER");
        assertThat(updatedOrder.getCancelledAt()).isNotNull();
        
        verify(orderExecutionService).releaseHeldFunds(
            eq("account-789"), eq("order-123"), eq(new BigDecimal("15000.00")));
        verify(portfolioService).recalculatePortfolioMetrics(eq("user-456"), eq("account-789"));
        verify(notificationService).sendOrderCancellationConfirmation(
            eq("user-456"), eq("order-123"), eq("AAPL"), 
            eq(new BigDecimal("100")), anyString());
    }
    
    @Test
    void shouldHandlePartiallyFilledOrder() {
        testEvent.setWasPartiallyFilled(true);
        testEvent.setFilledQuantity(new BigDecimal("60"));
        testEvent.setExecutedAmount(new BigDecimal("9000.00"));
        
        testOrder.setExecutionPrice(new BigDecimal("150.00"));
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(orderExecutionService.generatePartialFillSettlement(
            anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class),
            any(BigDecimal.class), any(BigDecimal.class)))
            .thenReturn("settlement-999");
        when(orderExecutionService.generateTradeConfirmation(
            anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class),
            any(LocalDateTime.class)))
            .thenReturn("confirmation-777");
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(orderExecutionService).generatePartialFillSettlement(
            eq("order-123"), eq("AAPL"), eq(new BigDecimal("60")),
            eq(new BigDecimal("40")), eq(new BigDecimal("9000.00")),
            eq(new BigDecimal("150.00")));
        verify(orderExecutionService).generateTradeConfirmation(
            eq("order-123"), eq("AAPL"), eq(new BigDecimal("60")),
            eq(new BigDecimal("150.00")), any(LocalDateTime.class));
        verify(portfolioService).updateHolding(
            eq("user-456"), eq("account-789"), eq("AAPL"),
            eq(new BigDecimal("60")), eq(new BigDecimal("150.00")));
        verify(notificationService).sendPartialFillCancellationNotification(
            eq("user-456"), eq("order-123"), eq("AAPL"),
            eq(new BigDecimal("60")), eq(new BigDecimal("100")),
            eq(new BigDecimal("150.00")), eq(new BigDecimal("9000.00")));
        
        ArgumentCaptor<InvestmentOrder> orderCaptor = ArgumentCaptor.forClass(InvestmentOrder.class);
        verify(investmentOrderRepository, atLeastOnce()).save(orderCaptor.capture());
        
        InvestmentOrder updatedOrder = orderCaptor.getValue();
        assertThat(updatedOrder.isWasPartiallyFilled()).isTrue();
        assertThat(updatedOrder.getExecutedQuantity()).isEqualTo(new BigDecimal("60"));
        assertThat(updatedOrder.getRemainingQuantity()).isEqualTo(new BigDecimal("40"));
    }
    
    @Test
    void shouldCalculateProRatedFeesForPartialFill() {
        testEvent.setWasPartiallyFilled(true);
        testEvent.setFilledQuantity(new BigDecimal("60"));
        testEvent.setExecutedAmount(new BigDecimal("9000.00"));
        
        testOrder.setTotalFees(new BigDecimal("100.00"));
        testOrder.setExecutionPrice(new BigDecimal("150.00"));
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(orderExecutionService.generatePartialFillSettlement(
            anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class),
            any(BigDecimal.class), any(BigDecimal.class)))
            .thenReturn("settlement-888");
        when(orderExecutionService.generateTradeConfirmation(
            anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class),
            any(LocalDateTime.class)))
            .thenReturn("confirmation-666");
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        ArgumentCaptor<InvestmentOrder> orderCaptor = ArgumentCaptor.forClass(InvestmentOrder.class);
        verify(investmentOrderRepository, atLeastOnce()).save(orderCaptor.capture());
        
        InvestmentOrder updatedOrder = orderCaptor.getValue();
        assertThat(updatedOrder.getAppliedFees()).isNotNull();
        assertThat(updatedOrder.getRefundedFees()).isNotNull();
        assertThat(updatedOrder.getAppliedFees().add(updatedOrder.getRefundedFees()))
            .isEqualByComparingTo(new BigDecimal("100.00"));
        
        verify(orderExecutionService).processRefund(
            eq("account-789"), eq("order-123"), 
            eq(updatedOrder.getRefundedFees()), contains("Pro-rated fee refund"));
    }
    
    @Test
    void shouldReleaseSellOrderSecurities() {
        testEvent.setOrderType("SELL");
        testEvent.setHeldAmount(null);
        
        testOrder.setOrderType(OrderType.SELL);
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(orderExecutionService).releaseHeldSecurities(
            eq("account-789"), eq("AAPL"), eq(new BigDecimal("100")), eq("order-123"));
        verify(orderExecutionService, never()).releaseHeldFunds(anyString(), anyString(), any(BigDecimal.class));
    }
    
    @Test
    void shouldReleasePartialQuantityForPartiallySoldOrder() {
        testEvent.setOrderType("SELL");
        testEvent.setWasPartiallyFilled(true);
        testEvent.setFilledQuantity(new BigDecimal("60"));
        testEvent.setHeldAmount(null);
        
        testOrder.setOrderType(OrderType.SELL);
        testOrder.setExecutionPrice(new BigDecimal("150.00"));
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        when(orderExecutionService.generatePartialFillSettlement(
            anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class),
            any(BigDecimal.class), any(BigDecimal.class)))
            .thenReturn("settlement-555");
        when(orderExecutionService.generateTradeConfirmation(
            anyString(), anyString(), any(BigDecimal.class), any(BigDecimal.class),
            any(LocalDateTime.class)))
            .thenReturn("confirmation-444");
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(orderExecutionService).releaseHeldSecurities(
            eq("account-789"), eq("AAPL"), eq(new BigDecimal("40")), eq("order-123"));
        verify(portfolioService).reduceHolding(
            eq("user-456"), eq("account-789"), eq("AAPL"), eq(new BigDecimal("60")));
    }
    
    @Test
    void shouldReleaseMarginHold() {
        testOrder.setMarginRequirement(new BigDecimal("5000.00"));
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(orderExecutionService).releaseMarginHold(
            eq("account-789"), eq("order-123"), eq(new BigDecimal("5000.00")));
    }
    
    @Test
    void shouldHandleSystemCancellation() {
        testEvent.setCancelledBy("SYSTEM");
        testEvent.setWasRejectedBySystem(true);
        testEvent.setRejectionReason("Insufficient funds");
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        ArgumentCaptor<InvestmentOrder> orderCaptor = ArgumentCaptor.forClass(InvestmentOrder.class);
        verify(investmentOrderRepository, atLeastOnce()).save(orderCaptor.capture());
        
        InvestmentOrder updatedOrder = orderCaptor.getValue();
        assertThat(updatedOrder.getCancelledBy()).isEqualTo("SYSTEM");
        assertThat(updatedOrder.isWasRejectedBySystem()).isTrue();
        assertThat(updatedOrder.getRejectionReason()).isEqualTo("Insufficient funds");
        
        verify(notificationService).sendSystemCancellationNotification(
            eq("user-456"), eq("order-123"), eq("AAPL"),
            eq(new BigDecimal("100")), eq("User requested cancellation"), eq(false));
    }
    
    @Test
    void shouldHandleComplianceCancellation() {
        testEvent.setCancelledBy("COMPLIANCE");
        testEvent.setComplianceRelated(true);
        testEvent.setComplianceFlag("SUSPICIOUS_PATTERN");
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        ArgumentCaptor<InvestmentOrder> orderCaptor = ArgumentCaptor.forClass(InvestmentOrder.class);
        verify(investmentOrderRepository, atLeastOnce()).save(orderCaptor.capture());
        
        InvestmentOrder updatedOrder = orderCaptor.getValue();
        assertThat(updatedOrder.isComplianceRelated()).isTrue();
        assertThat(updatedOrder.getComplianceFlag()).isEqualTo("SUSPICIOUS_PATTERN");
        
        verify(orderExecutionService).recordComplianceCancellation(
            eq("order-123"), eq("user-456"), eq("SUSPICIOUS_PATTERN"),
            eq("User requested cancellation"));
        verify(notificationService).sendSystemCancellationNotification(
            eq("user-456"), eq("order-123"), eq("AAPL"),
            eq(new BigDecimal("100")), anyString(), eq(true));
    }
    
    @Test
    void shouldHandleAdminCancellation() {
        testEvent.setCancelledBy("ADMIN");
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(notificationService).sendAdminCancellationNotification(
            eq("user-456"), eq("order-123"), eq("AAPL"),
            eq(new BigDecimal("100")), eq("User requested cancellation"));
    }
    
    @Test
    void shouldProcessRefunds() {
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(orderExecutionService).processRefund(
            eq("account-789"), eq("order-123"),
            eq(new BigDecimal("50.00")), eq("Order cancellation refund"));
        verify(notificationService).sendRefundNotification(
            eq("user-456"), eq("order-123"),
            eq(new BigDecimal("50.00")), eq("Order cancellation"));
    }
    
    @Test
    void shouldUpdatePortfolioMetrics() {
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(portfolioService).recalculatePortfolioMetrics(eq("user-456"), eq("account-789"));
        verify(portfolioService).updatePendingOrdersCount(eq("user-456"), eq("account-789"), eq(-1));
        verify(portfolioService).recalculateAvailableBuyingPower(eq("user-456"), eq("account-789"));
    }
    
    @Test
    void shouldUpdateRiskMetrics() {
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(riskManagementService).recalculatePortfolioRisk(eq("user-456"), eq("account-789"));
        verify(riskManagementService).updateConcentrationMetrics(
            eq("user-456"), eq("account-789"), eq("AAPL"),
            eq(new BigDecimal("15000.00").negate()));
    }
    
    @Test
    void shouldUpdateDayTradingCounterForSellOrders() {
        testEvent.setOrderType("SELL");
        testOrder.setOrderType(OrderType.SELL);
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(riskManagementService).updateDayTradingCounter(
            eq("user-456"), eq("account-789"), eq("AAPL"), eq(false));
    }
    
    @Test
    void shouldCreateAuditTrail() {
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        ArgumentCaptor<Map> auditCaptor = ArgumentCaptor.forClass(Map.class);
        verify(orderExecutionService).recordOrderCancellationAudit(auditCaptor.capture());
        
        Map<String, Object> auditData = auditCaptor.getValue();
        assertThat(auditData.get("orderId")).isEqualTo("order-123");
        assertThat(auditData.get("userId")).isEqualTo("user-456");
        assertThat(auditData.get("symbol")).isEqualTo("AAPL");
        assertThat(auditData.get("cancelledBy")).isEqualTo("USER");
        assertThat(auditData.get("wasPartiallyFilled")).isEqualTo(false);
    }
    
    @Test
    void shouldHandleIdempotentEvents() {
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(orderExecutionService, times(1)).releaseHeldFunds(
            anyString(), anyString(), any(BigDecimal.class));
        verify(notificationService, times(1)).sendOrderCancellationConfirmation(
            anyString(), anyString(), anyString(), any(BigDecimal.class), anyString());
    }
    
    @Test
    void shouldCreateOrderRecordWhenOrderNotFound() {
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.empty());
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        ArgumentCaptor<InvestmentOrder> orderCaptor = ArgumentCaptor.forClass(InvestmentOrder.class);
        verify(investmentOrderRepository, atLeastOnce()).save(orderCaptor.capture());
        
        InvestmentOrder createdOrder = orderCaptor.getValue();
        assertThat(createdOrder.getId()).isEqualTo("order-123");
        assertThat(createdOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(createdOrder.getCancellationReason()).isEqualTo("User requested cancellation");
    }
    
    @Test
    void shouldRejectCancellationOfFilledOrder() {
        testOrder.setStatus(OrderStatus.FILLED);
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        
        assertThrows(IllegalStateException.class, () -> {
            consumer.handleInvestmentOrderCancelled(testEvent);
        });
        
        verify(investmentOrderRepository, never()).save(any(InvestmentOrder.class));
        verify(orderExecutionService, never()).releaseHeldFunds(anyString(), anyString(), any(BigDecimal.class));
    }
    
    @Test
    void shouldHandleAlreadyCancelledOrder() {
        testOrder.setStatus(OrderStatus.CANCELLED);
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(investmentOrderRepository, atLeastOnce()).save(any(InvestmentOrder.class));
    }
    
    @Test
    void shouldCreateManualInterventionAlertOnFailure() {
        when(investmentOrderRepository.findById("order-123"))
            .thenThrow(new RuntimeException("Database connection failed"));
        
        assertThrows(RuntimeException.class, () -> {
            consumer.handleInvestmentOrderCancelled(testEvent);
        });
        
        verify(kafkaTemplate).send(eq("monitoring.manual-intervention.required"), anyMap());
    }
    
    @Test
    void shouldHandleSellShortOrders() {
        testEvent.setOrderType("SELL_SHORT");
        testEvent.setHeldAmount(null);
        
        testOrder.setOrderType(OrderType.SELL_SHORT);
        
        when(investmentOrderRepository.findById("order-123"))
            .thenReturn(Optional.of(testOrder));
        when(investmentOrderRepository.save(any(InvestmentOrder.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        consumer.handleInvestmentOrderCancelled(testEvent);
        
        verify(orderExecutionService).releaseHeldSecurities(
            eq("account-789"), eq("AAPL"), eq(new BigDecimal("100")), eq("order-123"));
    }
}