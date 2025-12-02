package com.waqiti.common.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Event published when an investment order is executed
 * 
 * This event triggers:
 * - General ledger recording in ledger-service (double-entry bookkeeping)
 * - Investment transaction history updates
 * - Capital gains/loss tracking
 * - Cost basis calculations
 * - Tax lot creation for IRS reporting
 * - Portfolio rebalancing notifications
 * 
 * ACCOUNTING TREATMENT:
 * BUY Order:
 *   DR: Investment Account (Asset increases)
 *   CR: Cash Account (Asset decreases)
 * 
 * SELL Order:
 *   DR: Cash Account (Asset increases)
 *   CR: Investment Account (Asset decreases)
 * 
 * Commission/Fees:
 *   DR: Commission Expense
 *   CR: Cash Account
 * 
 * @author Waqiti Investment Platform
 * @version 1.0
 * @since 2025-09-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentOrderExecutedEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Unique order identifier
     */
    @JsonProperty("order_id")
    private String orderId;
    
    /**
     * Order number (human-readable)
     */
    @JsonProperty("order_number")
    private String orderNumber;
    
    /**
     * Investment account ID
     */
    @JsonProperty("account_id")
    private String accountId;
    
    /**
     * User ID who placed the order
     */
    @JsonProperty("user_id")
    private String userId;
    
    /**
     * Security symbol (ticker): AAPL, TSLA, BTC, etc.
     */
    @JsonProperty("symbol")
    private String symbol;
    
    /**
     * Security name: Apple Inc., Tesla Inc., Bitcoin, etc.
     */
    @JsonProperty("security_name")
    private String securityName;
    
    /**
     * Instrument type: STOCK, ETF, BOND, CRYPTO, OPTION, MUTUAL_FUND
     */
    @JsonProperty("instrument_type")
    private String instrumentType;
    
    /**
     * Order side: BUY or SELL
     */
    @JsonProperty("order_side")
    private String orderSide;
    
    /**
     * Quantity executed
     */
    @JsonProperty("executed_quantity")
    private BigDecimal executedQuantity;
    
    /**
     * Execution price per share/unit
     */
    @JsonProperty("execution_price")
    private BigDecimal executionPrice;
    
    /**
     * Total execution amount (quantity * price)
     */
    @JsonProperty("execution_amount")
    private BigDecimal executionAmount;
    
    /**
     * Commission charged
     */
    @JsonProperty("commission")
    private BigDecimal commission;
    
    /**
     * Exchange fees (SEC/FINRA fees for US equities)
     */
    @JsonProperty("exchange_fees")
    private BigDecimal exchangeFees;
    
    /**
     * Total cost/proceeds = amount ± commission ± fees
     */
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;
    
    /**
     * Currency code (ISO 4217)
     */
    @JsonProperty("currency")
    private String currency;
    
    /**
     * Execution timestamp
     */
    @JsonProperty("executed_at")
    private LocalDateTime executedAt;
    
    /**
     * Exchange/venue where order was executed
     */
    @JsonProperty("execution_venue")
    private String executionVenue;
    
    /**
     * Market/exchange: NYSE, NASDAQ, CBOE, BINANCE, etc.
     */
    @JsonProperty("market")
    private String market;
    
    /**
     * Order type: MARKET, LIMIT, STOP, STOP_LIMIT
     */
    @JsonProperty("order_type")
    private String orderType;
    
    /**
     * Settlement date (T+2 for equities, T+0 for crypto)
     */
    @JsonProperty("settlement_date")
    private LocalDateTime settlementDate;
    
    /**
     * Trade date
     */
    @JsonProperty("trade_date")
    private LocalDateTime tradeDate;
    
    /**
     * Tax lot ID for cost basis tracking
     */
    @JsonProperty("tax_lot_id")
    private String taxLotId;
    
    /**
     * Cost basis per share (for capital gains calculation)
     */
    @JsonProperty("cost_basis_per_share")
    private BigDecimal costBasisPerShare;
    
    /**
     * Broker/dealer executing the trade
     */
    @JsonProperty("broker_dealer")
    private String brokerDealer;
    
    /**
     * Additional metadata (routing info, regulatory flags, etc.)
     */
    @JsonProperty("metadata")
    private Map<String, String> metadata;
}