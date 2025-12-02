package com.waqiti.crypto.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Base event model for crypto domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CryptoEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String cryptoWalletId;
    private String userId;
    private String cryptoSymbol;
    private String cryptoName;
    private BigDecimal amount;
    private BigDecimal price;
    private BigDecimal totalValue;
    private String currency;
    private String orderId;
    private String transactionId;
    private String orderType;
    private String swapFromSymbol;
    private String swapToSymbol;
    private BigDecimal swapRate;
    private String stakingPoolId;
    private String stakingDuration;
    private BigDecimal stakingReward;
    private String withdrawalAddress;
    private String depositAddress;
    private String alertType;
    private BigDecimal alertThreshold;
    private String blockchainNetwork;
    private String transactionHash;
    private String status;
    private String reason;
    private Instant timestamp;
    private String correlationId;
    private String causationId;
    private String version;
    private String description;
    private Long sequenceNumber;
    private Integer retryCount;
    private Map<String, Object> metadata;
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
    
    /**
     * Check if this is a trading event
     */
    public boolean isTradingEvent() {
        return "CRYPTO_BUY_ORDER".equals(eventType) || 
               "CRYPTO_SELL_ORDER".equals(eventType) ||
               "CRYPTO_SWAP".equals(eventType);
    }
    
    /**
     * Check if this is a wallet movement event
     */
    public boolean isWalletMovementEvent() {
        return "CRYPTO_WITHDRAWAL".equals(eventType) || 
               "CRYPTO_DEPOSIT".equals(eventType);
    }
    
    /**
     * Check if this is a high-priority event
     */
    public boolean isHighPriorityEvent() {
        return "CRYPTO_PRICE_ALERT".equals(eventType) || 
               "CRYPTO_WALLET_CREATION".equals(eventType);
    }
    
    /**
     * Get event age in seconds
     */
    public long getAgeInSeconds() {
        if (timestamp == null) {
            return 0;
        }
        return Instant.now().getEpochSecond() - timestamp.getEpochSecond();
    }
}