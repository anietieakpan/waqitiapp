package com.waqiti.investment.events.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Base event model for investment domain events
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private String eventId;
    private String eventType;
    private String eventVersion;
    private String orderId;
    private String accountId;
    private String userId;
    private String symbol;
    private String cusip;
    private String instrumentType;
    private String orderType;
    private String orderSide;
    private String bondType;
    private String fundName;
    private String etfName;
    private String underlyingSymbol;
    private String underlyingIndex;
    private String optionType;
    private String strategy;
    private String actionId;
    private String actionType;
    private String actionDescription;
    private String selectionId;
    private String selectionMethod;
    private String reportId;
    private String reportType;
    private String reportFormat;
    private String fundCategory;
    private String timeInForce;
    private String status;
    private String creditRating;
    private String expirationDate;
    private String electionRequired;
    private String taxYear;
    private BigDecimal quantity;
    private BigDecimal price;
    private BigDecimal yieldToMaturity;
    private BigDecimal dollarAmount;
    private BigDecimal shares;
    private BigDecimal nav;
    private BigDecimal expenseRatio;
    private BigDecimal strikePrice;
    private BigDecimal contracts;
    private BigDecimal premium;
    private BigDecimal cashAmount;
    private BigDecimal shareQuantity;
    private BigDecimal quantitySold;
    private BigDecimal realizedGainLoss;
    private BigDecimal shortTermGain;
    private BigDecimal longTermGain;
    private BigDecimal portfolioValue;
    private BigDecimal totalGainLoss;
    private BigDecimal dividendIncome;
    private Instant orderTimestamp;
    private Instant effectiveDate;
    private Instant payableDate;
    private Instant reportPeriodStart;
    private Instant reportPeriodEnd;
    private Instant timestamp;
    private String correlationId;
    private String version;
    private String maturityDate;
    private Long sequenceNumber;
    private Integer retryCount;
    private Map<String, Object> metadata;
    
    public void incrementRetryCount() {
        this.retryCount = (this.retryCount == null ? 0 : this.retryCount) + 1;
    }
}