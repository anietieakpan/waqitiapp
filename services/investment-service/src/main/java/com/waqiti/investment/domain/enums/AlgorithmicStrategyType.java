package com.waqiti.investment.domain.enums;

/**
 * Enumeration of algorithmic trading strategy types
 */
public enum AlgorithmicStrategyType {
    /**
     * Time Weighted Average Price - spreads order execution over time
     */
    TWAP,
    
    /**
     * Volume Weighted Average Price - executes based on volume patterns
     */
    VWAP,
    
    /**
     * Iceberg order - hides large order size by showing small portions
     */
    ICEBERG,
    
    /**
     * Momentum trading - follows price trends and momentum indicators
     */
    MOMENTUM,
    
    /**
     * Mean reversion - trades against extreme price movements
     */
    MEAN_REVERSION,
    
    /**
     * Arbitrage - exploits price differences between markets
     */
    ARBITRAGE,
    
    /**
     * Pairs trading - long/short positions in correlated securities
     */
    PAIRS_TRADING,
    
    /**
     * Statistical arbitrage - uses statistical models to identify trades
     */
    STATISTICAL_ARBITRAGE,
    
    /**
     * Market making - provides liquidity by placing bid/ask orders
     */
    MARKET_MAKING,
    
    /**
     * Trend following - follows long-term price trends
     */
    TREND_FOLLOWING
}