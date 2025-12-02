package com.waqiti.investment.broker.enums;

/**
 * Supported Broker Providers
 *
 * @author Waqiti Platform Team
 * @since 2025-10-02
 */
public enum BrokerProvider {
    /**
     * Alpaca Markets - Commission-free stock trading API
     * Best for: Paper trading, rapid integration, modern API
     */
    ALPACA,

    /**
     * Interactive Brokers - Professional trading platform
     * Best for: Advanced traders, global markets, low commissions
     * Uses FIX protocol for order execution
     */
    INTERACTIVE_BROKERS,

    /**
     * Charles Schwab - Full-service brokerage
     * Best for: Institutional clients, research tools
     */
    SCHWAB,

    /**
     * TD Ameritrade - Retail and institutional trading
     * Best for: Options trading, thinkorswim platform
     */
    TD_AMERITRADE,

    /**
     * Internal execution (for testing/simulation)
     */
    INTERNAL
}
