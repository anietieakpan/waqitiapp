package com.waqiti.investment.broker.client;

import com.waqiti.investment.broker.dto.BrokerAccountInfo;
import com.waqiti.investment.broker.dto.BrokerOrderRequest;
import com.waqiti.investment.broker.dto.BrokerOrderResponse;

/**
 * Broker Client Interface
 *
 * Unified interface for all broker integrations
 * Implementations: Alpaca, Interactive Brokers, Schwab, TD Ameritrade
 *
 * @author Waqiti Platform Team
 * @since 2025-10-02
 */
public interface BrokerClient {

    /**
     * Submit an order to the broker
     *
     * @param request Order request
     * @return Order response with broker order ID and status
     */
    BrokerOrderResponse submitOrder(BrokerOrderRequest request);

    /**
     * Get order status from broker
     *
     * @param brokerOrderId Broker's order ID
     * @return Current order status
     */
    BrokerOrderResponse getOrderStatus(String brokerOrderId);

    /**
     * Cancel an order at the broker
     *
     * @param brokerOrderId Broker's order ID
     * @return Cancellation confirmation
     */
    BrokerOrderResponse cancelOrder(String brokerOrderId);

    /**
     * Get account information from broker
     *
     * @param brokerAccountId Broker account ID
     * @return Account info including balances
     */
    BrokerAccountInfo getAccountInfo(String brokerAccountId);

    /**
     * Check if broker is available and accessible
     *
     * @return true if broker API is reachable
     */
    boolean isAvailable();

    /**
     * Get broker provider name
     *
     * @return Provider enum
     */
    com.waqiti.investment.broker.enums.BrokerProvider getProvider();
}
