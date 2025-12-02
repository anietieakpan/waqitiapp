package com.waqiti.investment.domain.enums;

/**
 * Enumeration of options trading strategy types
 */
public enum OptionsStrategyType {
    /**
     * Covered Call - own stock, sell call option
     */
    COVERED_CALL,
    
    /**
     * Cash Secured Put - sell put with cash to cover assignment
     */
    CASH_SECURED_PUT,
    
    /**
     * Protective Put - own stock, buy put for protection
     */
    PROTECTIVE_PUT,
    
    /**
     * Bull Call Spread - buy low strike call, sell high strike call
     */
    BULL_CALL_SPREAD,
    
    /**
     * Bear Put Spread - buy high strike put, sell low strike put
     */
    BEAR_PUT_SPREAD,
    
    /**
     * Bull Put Spread - sell high strike put, buy low strike put
     */
    BULL_PUT_SPREAD,
    
    /**
     * Bear Call Spread - sell low strike call, buy high strike call
     */
    BEAR_CALL_SPREAD,
    
    /**
     * Long Straddle - buy call and put at same strike
     */
    LONG_STRADDLE,
    
    /**
     * Short Straddle - sell call and put at same strike
     */
    SHORT_STRADDLE,
    
    /**
     * Long Strangle - buy call and put at different strikes
     */
    LONG_STRANGLE,
    
    /**
     * Short Strangle - sell call and put at different strikes
     */
    SHORT_STRANGLE,
    
    /**
     * Iron Condor - sell call spread and put spread
     */
    IRON_CONDOR,
    
    /**
     * Iron Butterfly - sell straddle, buy strangle
     */
    IRON_BUTTERFLY,
    
    /**
     * Calendar Spread - buy/sell same strike different expirations
     */
    CALENDAR_SPREAD,
    
    /**
     * Diagonal Spread - buy/sell different strikes and expirations
     */
    DIAGONAL_SPREAD
}