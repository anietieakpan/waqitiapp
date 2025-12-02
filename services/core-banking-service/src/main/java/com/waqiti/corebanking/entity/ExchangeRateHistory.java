package com.waqiti.corebanking.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Exchange Rate History Entity
 * Stores historical exchange rates for currency pairs
 */
@Entity
@Table(name = "exchange_rate_history", 
       indexes = {
           @Index(name = "idx_exchange_history_currencies_date", 
                  columnList = "from_currency, to_currency, rate_date"),
           @Index(name = "idx_exchange_history_date", 
                  columnList = "rate_date")
       })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeRateHistory {
    
    @Id
    @GeneratedValue
    private UUID id;
    
    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;
    
    @Column(name = "to_currency", nullable = false, length = 3)  
    private String toCurrency;
    
    @Column(name = "rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal rate;
    
    @Column(name = "mid_market_rate", precision = 19, scale = 8)
    private BigDecimal midMarketRate;
    
    @Column(name = "bid_rate", precision = 19, scale = 8)
    private BigDecimal bidRate;
    
    @Column(name = "ask_rate", precision = 19, scale = 8)
    private BigDecimal askRate;
    
    @Column(name = "rate_date", nullable = false)
    private LocalDateTime rateDate;
    
    @Column(name = "source", length = 50)
    private String source;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (rateDate == null) {
            rateDate = LocalDateTime.now();
        }
    }
}