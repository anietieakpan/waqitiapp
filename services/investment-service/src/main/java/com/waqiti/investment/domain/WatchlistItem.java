package com.waqiti.investment.domain;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "watchlist_items", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "symbol"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String customerId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String instrumentType; // STOCK, ETF, CRYPTO

    @Column(nullable = false)
    private String name;

    private String exchange;

    private String sector;

    private String industry;

    @Column(nullable = false)
    private BigDecimal currentPrice = BigDecimal.ZERO;

    private BigDecimal previousClose;

    private BigDecimal dayChange = BigDecimal.ZERO;

    private BigDecimal dayChangePercent = BigDecimal.ZERO;

    private BigDecimal dayLow;

    private BigDecimal dayHigh;

    private BigDecimal fiftyTwoWeekLow;

    private BigDecimal fiftyTwoWeekHigh;

    private Long volume;

    private Long averageVolume;

    private BigDecimal marketCap;

    private BigDecimal peRatio;

    private BigDecimal dividendYield;

    private BigDecimal beta;

    private BigDecimal targetPrice;

    private String notes;

    @Column(nullable = false)
    private Boolean alertsEnabled = false;

    private BigDecimal priceAlertAbove;

    private BigDecimal priceAlertBelow;

    private BigDecimal percentChangeAlert;

    private Boolean volumeAlert = false;

    private BigDecimal volumeAlertThreshold;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime lastPriceUpdate;

    private LocalDateTime lastAlertSent;

    @Version
    private Long version;

    @Column(columnDefinition = "TEXT")
    private String metadata;

    public void updatePrice(BigDecimal currentPrice, BigDecimal dayChange, BigDecimal dayChangePercent) {
        this.currentPrice = currentPrice;
        this.dayChange = dayChange;
        this.dayChangePercent = dayChangePercent;
        this.lastPriceUpdate = LocalDateTime.now();
    }

    public void updateMarketData(BigDecimal previousClose, BigDecimal dayLow, BigDecimal dayHigh,
                                BigDecimal fiftyTwoWeekLow, BigDecimal fiftyTwoWeekHigh,
                                Long volume, Long averageVolume, BigDecimal marketCap,
                                BigDecimal peRatio, BigDecimal dividendYield, BigDecimal beta) {
        this.previousClose = previousClose;
        this.dayLow = dayLow;
        this.dayHigh = dayHigh;
        this.fiftyTwoWeekLow = fiftyTwoWeekLow;
        this.fiftyTwoWeekHigh = fiftyTwoWeekHigh;
        this.volume = volume;
        this.averageVolume = averageVolume;
        this.marketCap = marketCap;
        this.peRatio = peRatio;
        this.dividendYield = dividendYield;
        this.beta = beta;
        this.lastPriceUpdate = LocalDateTime.now();
    }

    public void setPriceAlert(BigDecimal priceAbove, BigDecimal priceBelow) {
        this.priceAlertAbove = priceAbove;
        this.priceAlertBelow = priceBelow;
        this.alertsEnabled = true;
    }

    public void setPercentChangeAlert(BigDecimal percentChange) {
        this.percentChangeAlert = percentChange;
        this.alertsEnabled = true;
    }

    public void setVolumeAlert(BigDecimal threshold) {
        this.volumeAlertThreshold = threshold;
        this.volumeAlert = true;
        this.alertsEnabled = true;
    }

    public void alertSent() {
        this.lastAlertSent = LocalDateTime.now();
    }

    public boolean shouldTriggerPriceAlert() {
        if (!alertsEnabled) return false;
        
        if (priceAlertAbove != null && currentPrice.compareTo(priceAlertAbove) >= 0) {
            return true;
        }
        
        if (priceAlertBelow != null && currentPrice.compareTo(priceAlertBelow) <= 0) {
            return true;
        }
        
        if (percentChangeAlert != null && dayChangePercent != null && 
            dayChangePercent.abs().compareTo(percentChangeAlert) >= 0) {
            return true;
        }
        
        return false;
    }

    public boolean shouldTriggerVolumeAlert() {
        return volumeAlert && volumeAlertThreshold != null && 
               volume != null && new BigDecimal(volume).compareTo(volumeAlertThreshold) >= 0;
    }

    public boolean isNearFiftyTwoWeekHigh() {
        if (currentPrice == null || fiftyTwoWeekHigh == null) return false;
        BigDecimal threshold = fiftyTwoWeekHigh.multiply(new BigDecimal("0.95")); // Within 5%
        return currentPrice.compareTo(threshold) >= 0;
    }

    public boolean isNearFiftyTwoWeekLow() {
        if (currentPrice == null || fiftyTwoWeekLow == null) return false;
        BigDecimal threshold = fiftyTwoWeekLow.multiply(new BigDecimal("1.05")); // Within 5%
        return currentPrice.compareTo(threshold) <= 0;
    }
}