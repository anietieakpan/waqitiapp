package com.waqiti.crypto.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "trade_pairs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TradePair {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "symbol", unique = true, nullable = false, length = 20)
    private String symbol; // e.g., "BTCUSD", "ETHUSD"
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_currency_id", nullable = false)
    private CryptoCurrency tradeCurrency; // Base cryptocurrency (BTC, ETH)
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "base_currency_id", nullable = false)
    private CryptoCurrency baseCurrency; // Quote currency (USD, EUR, BTC)
    
    @Column(name = "active", nullable = false)
    private Boolean active = true;
    
    @Column(name = "trading_enabled", nullable = false)
    private Boolean tradingEnabled = true;
    
    @Column(name = "min_trade_amount", precision = 19, scale = 8)
    private BigDecimal minTradeAmount;
    
    @Column(name = "max_trade_amount", precision = 19, scale = 8)
    private BigDecimal maxTradeAmount;
    
    @Column(name = "min_price", precision = 19, scale = 8)
    private BigDecimal minPrice;
    
    @Column(name = "max_price", precision = 19, scale = 8)
    private BigDecimal maxPrice;
    
    @Column(name = "tick_size", precision = 19, scale = 8)
    private BigDecimal tickSize; // Minimum price increment
    
    @Column(name = "lot_size", precision = 19, scale = 8)
    private BigDecimal lotSize; // Minimum quantity increment
    
    @Column(name = "maker_fee", precision = 5, scale = 4)
    private BigDecimal makerFee; // Fee for providing liquidity
    
    @Column(name = "taker_fee", precision = 5, scale = 4)
    private BigDecimal takerFee; // Fee for taking liquidity
    
    @Column(name = "price_precision")
    private Integer pricePrecision = 8;
    
    @Column(name = "quantity_precision")
    private Integer quantityPrecision = 8;
    
    @Column(name = "base_asset_precision")
    private Integer baseAssetPrecision = 8;
    
    @Column(name = "quote_precision")
    private Integer quotePrecision = 8;
    
    @Column(name = "order_types", length = 500)
    private String orderTypes; // Comma-separated list of supported order types
    
    @Column(name = "time_in_force", length = 200)
    private String timeInForce; // Comma-separated list of supported TIF values
    
    @Column(name = "iceberg_allowed")
    private Boolean icebergAllowed = false;
    
    @Column(name = "oco_allowed")
    private Boolean ocoAllowed = false;
    
    @Column(name = "quote_order_qty_market_allowed")
    private Boolean quoteOrderQtyMarketAllowed = true;
    
    @Column(name = "is_spot_trading_allowed")
    private Boolean isSpotTradingAllowed = true;
    
    @Column(name = "is_margin_trading_allowed")
    private Boolean isMarginTradingAllowed = false;
    
    @Type(type = "jsonb")
    @Column(name = "filters", columnDefinition = "jsonb")
    private Map<String, Object> filters; // Additional trading filters
    
    @Type(type = "jsonb")
    @Column(name = "permissions", columnDefinition = "jsonb")
    private Map<String, Object> permissions;
    
    @Type(type = "jsonb")
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;
    
    @Column(name = "display_name", length = 50)
    private String displayName;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "sort_order")
    private Integer sortOrder = 0;
    
    @Column(name = "launch_date")
    private LocalDateTime launchDate;
    
    @Column(name = "delisting_date")
    private LocalDateTime delistingDate;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private UUID createdBy;
    
    @Column(name = "updated_by")
    private UUID updatedBy;
    
    @Version
    private Long version;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        if (symbol == null && tradeCurrency != null && baseCurrency != null) {
            symbol = tradeCurrency.getSymbol() + baseCurrency.getSymbol();
        }
        
        if (displayName == null) {
            displayName = tradeCurrency.getSymbol() + "/" + baseCurrency.getSymbol();
        }
        
        // Set default fees if not specified
        if (makerFee == null) {
            makerFee = new BigDecimal("0.001"); // 0.1%
        }
        if (takerFee == null) {
            takerFee = new BigDecimal("0.001"); // 0.1%
        }
        
        // Set default order types
        if (orderTypes == null) {
            orderTypes = "LIMIT,MARKET,STOP_LOSS,STOP_LIMIT";
        }
        
        // Set default time in force options
        if (timeInForce == null) {
            timeInForce = "GTC,IOC,FOK";
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public boolean isValidOrderType(TradeOrderType orderType) {
        return orderTypes != null && orderTypes.contains(orderType.name());
    }
    
    public boolean isValidTimeInForce(String tif) {
        return timeInForce != null && timeInForce.contains(tif);
    }
    
    public boolean isValidPrice(BigDecimal price) {
        if (price == null) return false;
        
        if (minPrice != null && price.compareTo(minPrice) < 0) return false;
        if (maxPrice != null && price.compareTo(maxPrice) > 0) return false;
        
        // Check tick size
        if (tickSize != null && tickSize.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainder = price.remainder(tickSize);
            return remainder.compareTo(BigDecimal.ZERO) == 0;
        }
        
        return true;
    }
    
    public boolean isValidQuantity(BigDecimal quantity) {
        if (quantity == null) return false;
        
        if (minTradeAmount != null && quantity.compareTo(minTradeAmount) < 0) return false;
        if (maxTradeAmount != null && quantity.compareTo(maxTradeAmount) > 0) return false;
        
        // Check lot size
        if (lotSize != null && lotSize.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal remainder = quantity.remainder(lotSize);
            return remainder.compareTo(BigDecimal.ZERO) == 0;
        }
        
        return true;
    }
    
    public BigDecimal calculateFee(BigDecimal quantity, BigDecimal price, boolean isMaker) {
        BigDecimal feeRate = isMaker ? makerFee : takerFee;
        BigDecimal tradeValue = quantity.multiply(price);
        return tradeValue.multiply(feeRate);
    }
    
    public boolean isTradingAllowed() {
        return active && tradingEnabled && 
               (delistingDate == null || LocalDateTime.now().isBefore(delistingDate));
    }
}