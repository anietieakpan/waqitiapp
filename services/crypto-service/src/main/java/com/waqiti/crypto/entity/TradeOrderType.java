package com.waqiti.crypto.entity;

public enum TradeOrderType {
    MARKET,          // Execute immediately at current market price
    LIMIT,           // Execute only at specified price or better
    STOP_LOSS,       // Market order triggered when stop price is reached
    STOP_LIMIT,      // Limit order triggered when stop price is reached
    TAKE_PROFIT,     // Take profit order
    TRAILING_STOP,   // Trailing stop order
    FILL_OR_KILL,    // Execute entire order immediately or cancel
    IMMEDIATE_OR_CANCEL, // Execute partial order immediately, cancel remainder
    ALL_OR_NONE,     // Execute entire order or none
    ICEBERG,         // Large order split into smaller visible quantities
    BRACKET,         // Order with stop loss and take profit
    OCO              // One-Cancels-Other order
}