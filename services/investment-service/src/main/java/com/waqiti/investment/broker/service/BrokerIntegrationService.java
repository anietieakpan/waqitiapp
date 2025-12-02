package com.waqiti.investment.broker.service;

import com.waqiti.investment.broker.client.AlpacaBrokerClient;
import com.waqiti.investment.broker.client.BrokerClient;
import com.waqiti.investment.broker.dto.BrokerAccountInfo;
import com.waqiti.investment.broker.dto.BrokerOrderRequest;
import com.waqiti.investment.broker.dto.BrokerOrderResponse;
import com.waqiti.investment.broker.enums.BrokerProvider;
import com.waqiti.investment.broker.enums.ExecutionStatus;
import com.waqiti.investment.domain.InvestmentAccount;
import com.waqiti.investment.domain.InvestmentOrder;
import com.waqiti.investment.domain.enums.OrderStatus;
import com.waqiti.investment.repository.InvestmentAccountRepository;
import com.waqiti.investment.repository.InvestmentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Broker Integration Service
 *
 * Manages integration with multiple broker providers:
 * - Routes orders to appropriate broker
 * - Handles broker failover
 * - Syncs order status
 * - Manages broker account linking
 *
 * @author Waqiti Platform Team
 * @since 2025-10-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrokerIntegrationService {

    private final AlpacaBrokerClient alpacaBrokerClient;
    private final InvestmentOrderRepository orderRepository;
    private final InvestmentAccountRepository accountRepository;

    @Value("${waqiti.brokerage.default-provider:ALPACA}")
    private String defaultProvider;

    /**
     * Map of available broker clients
     */
    private Map<BrokerProvider, BrokerClient> brokerClients;

    /**
     * Initialize broker clients map
     */
    private Map<BrokerProvider, BrokerClient> getBrokerClients() {
        if (brokerClients == null) {
            brokerClients = new HashMap<>();
            brokerClients.put(BrokerProvider.ALPACA, alpacaBrokerClient);
            // Add other brokers as they are implemented
            // brokerClients.put(BrokerProvider.INTERACTIVE_BROKERS, interactiveBrokersClient);
            // brokerClients.put(BrokerProvider.SCHWAB, schwabClient);
        }
        return brokerClients;
    }

    /**
     * Submit order to broker for execution
     *
     * @param order Investment order entity
     * @return Broker response with execution details
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BrokerOrderResponse submitOrderToBroker(InvestmentOrder order) {
        log.info("Submitting order {} to broker for execution", order.getOrderNumber());

        // Determine which broker to use
        BrokerProvider provider = determineBrokerProvider(order);
        BrokerClient brokerClient = getBrokerClient(provider);

        if (brokerClient == null || !brokerClient.isAvailable()) {
            log.error("Broker {} is not available, attempting failover", provider);
            brokerClient = getFailoverBroker(provider);
            if (brokerClient == null) {
                return BrokerOrderResponse.builder()
                    .clientOrderId(order.getOrderNumber())
                    .status(ExecutionStatus.FAILED)
                    .rejectionReason("No broker available for execution")
                    .build();
            }
        }

        // Build broker request
        BrokerOrderRequest request = BrokerOrderRequest.builder()
            .clientOrderId(order.getOrderNumber())
            .symbol(order.getSymbol())
            .side(order.getSide())
            .type(order.getOrderType())
            .quantity(order.getQuantity())
            .limitPrice(order.getLimitPrice())
            .stopPrice(order.getStopPrice())
            .timeInForce(order.getTimeInForce())
            .extendedHours(false) // Configure based on requirements
            .brokerAccountId(order.getInvestmentAccount().getBrokerageAccountId())
            .build();

        // Submit to broker
        BrokerOrderResponse response = brokerClient.submitOrder(request);

        // Update order with broker information
        updateOrderWithBrokerResponse(order, response, provider);

        return response;
    }

    /**
     * Get order status from broker
     *
     * @param order Investment order
     * @return Current broker status
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public BrokerOrderResponse getOrderStatusFromBroker(InvestmentOrder order) {
        if (order.getBrokerageOrderId() == null) {
            log.warn("Order {} has no brokerage order ID", order.getOrderNumber());
            return null;
        }

        BrokerProvider provider = BrokerProvider.valueOf(order.getBrokerageProvider());
        BrokerClient brokerClient = getBrokerClient(provider);

        if (brokerClient == null) {
            log.error("No broker client available for provider {}", provider);
            return null;
        }

        return brokerClient.getOrderStatus(order.getBrokerageOrderId());
    }

    /**
     * Cancel order at broker
     *
     * @param order Investment order
     * @return Cancellation confirmation
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BrokerOrderResponse cancelOrderAtBroker(InvestmentOrder order) {
        if (order.getBrokerageOrderId() == null) {
            log.warn("Order {} has no brokerage order ID, cannot cancel at broker", order.getOrderNumber());
            return null;
        }

        BrokerProvider provider = BrokerProvider.valueOf(order.getBrokerageProvider());
        BrokerClient brokerClient = getBrokerClient(provider);

        if (brokerClient == null) {
            log.error("No broker client available for provider {}", provider);
            return null;
        }

        BrokerOrderResponse response = brokerClient.cancelOrder(order.getBrokerageOrderId());

        // Update order status
        if (response.getStatus() == ExecutionStatus.CANCELLED) {
            order.cancel();
            orderRepository.save(order);
        }

        return response;
    }

    /**
     * Sync order status with broker
     * Called periodically to ensure our records match broker's records
     *
     * @param order Investment order
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void syncOrderStatusWithBroker(InvestmentOrder order) {
        BrokerOrderResponse brokerStatus = getOrderStatusFromBroker(order);

        if (brokerStatus == null) {
            return;
        }

        // Update order based on broker status
        boolean updated = false;

        if (brokerStatus.getFilledQuantity() != null &&
            brokerStatus.getFilledQuantity().compareTo(order.getExecutedQuantity()) > 0) {

            BigDecimal newFillQuantity = brokerStatus.getFilledQuantity()
                .subtract(order.getExecutedQuantity());
            BigDecimal fillPrice = brokerStatus.getAverageFillPrice();

            if (fillPrice != null) {
                order.partialFill(newFillQuantity, fillPrice);
                updated = true;
                log.info("Order {} partially filled: {} shares at ${}", order.getOrderNumber(),
                    newFillQuantity, fillPrice);
            }
        }

        if (brokerStatus.getStatus() == ExecutionStatus.FILLED &&
            order.getStatus() != OrderStatus.FILLED) {
            order.fill(brokerStatus.getAverageFillPrice());
            updated = true;
            log.info("Order {} fully filled at ${}", order.getOrderNumber(),
                brokerStatus.getAverageFillPrice());
        }

        if (brokerStatus.getStatus() == ExecutionStatus.CANCELLED &&
            order.getStatus() != OrderStatus.CANCELLED) {
            order.cancel();
            updated = true;
            log.info("Order {} cancelled at broker", order.getOrderNumber());
        }

        if (brokerStatus.getStatus() == ExecutionStatus.REJECTED &&
            order.getStatus() != OrderStatus.REJECTED) {
            order.reject(brokerStatus.getRejectionReason());
            updated = true;
            log.error("Order {} rejected by broker: {}", order.getOrderNumber(),
                brokerStatus.getRejectionReason());
        }

        if (updated) {
            orderRepository.save(order);
        }
    }

    /**
     * Get account info from broker
     *
     * @param account Investment account
     * @return Broker account information
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public BrokerAccountInfo getAccountInfoFromBroker(InvestmentAccount account) {
        if (account.getBrokerageProvider() == null) {
            log.warn("Account {} has no brokerage provider configured", account.getAccountNumber());
            return null;
        }

        BrokerProvider provider = BrokerProvider.valueOf(account.getBrokerageProvider());
        BrokerClient brokerClient = getBrokerClient(provider);

        if (brokerClient == null) {
            log.error("No broker client available for provider {}", provider);
            return null;
        }

        return brokerClient.getAccountInfo(account.getBrokerageAccountId());
    }

    /**
     * Determine which broker to use for an order
     */
    private BrokerProvider determineBrokerProvider(InvestmentOrder order) {
        // Check if account has preferred broker
        InvestmentAccount account = order.getInvestmentAccount();
        if (account.getBrokerageProvider() != null) {
            return BrokerProvider.valueOf(account.getBrokerageProvider());
        }

        // Use default broker
        return BrokerProvider.valueOf(defaultProvider);
    }

    /**
     * Get broker client for provider
     */
    private BrokerClient getBrokerClient(BrokerProvider provider) {
        return getBrokerClients().get(provider);
    }

    /**
     * Get failover broker if primary is unavailable
     */
    private BrokerClient getFailoverBroker(BrokerProvider primaryProvider) {
        // Try other brokers in order of preference
        for (Map.Entry<BrokerProvider, BrokerClient> entry : getBrokerClients().entrySet()) {
            if (entry.getKey() != primaryProvider && entry.getValue().isAvailable()) {
                log.info("Using failover broker: {}", entry.getKey());
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * Update order with broker response
     */
    private void updateOrderWithBrokerResponse(
        InvestmentOrder order,
        BrokerOrderResponse response,
        BrokerProvider provider) {

        order.setBrokerageOrderId(response.getBrokerOrderId());
        order.setBrokerageProvider(provider.name());

        // Update order status based on broker response
        switch (response.getStatus()) {
            case ACCEPTED, PENDING -> order.accept();
            case FILLED -> {
                if (response.getAverageFillPrice() != null) {
                    order.fill(response.getAverageFillPrice());
                }
            }
            case PARTIALLY_FILLED -> {
                if (response.getFilledQuantity() != null && response.getAverageFillPrice() != null) {
                    order.partialFill(response.getFilledQuantity(), response.getAverageFillPrice());
                }
            }
            case REJECTED -> order.reject(response.getRejectionReason());
            case CANCELLED -> order.cancel();
            case EXPIRED -> order.expire();
            case FAILED -> order.reject("Broker execution failed: " + response.getRejectionReason());
        }

        // Update commission/fees if provided by broker
        if (response.getCommission() != null) {
            order.setCommission(response.getCommission());
        }
        if (response.getFees() != null) {
            order.setFees(response.getFees());
        }

        orderRepository.save(order);

        log.info("Order {} updated with broker response: status={}, brokerOrderId={}",
            order.getOrderNumber(), response.getStatus(), response.getBrokerOrderId());
    }

    /**
     * Check health of all broker connections
     *
     * @return Map of broker provider to availability status
     */
    public Map<BrokerProvider, Boolean> checkBrokerHealth() {
        Map<BrokerProvider, Boolean> healthStatus = new HashMap<>();

        for (Map.Entry<BrokerProvider, BrokerClient> entry : getBrokerClients().entrySet()) {
            boolean available = entry.getValue().isAvailable();
            healthStatus.put(entry.getKey(), available);
            log.info("Broker {} health check: {}", entry.getKey(), available ? "UP" : "DOWN");
        }

        return healthStatus;
    }
}
