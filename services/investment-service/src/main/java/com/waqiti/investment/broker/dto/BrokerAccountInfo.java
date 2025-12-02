package com.waqiti.investment.broker.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Broker Account Information DTO
 *
 * @author Waqiti Platform Team
 * @since 2025-10-02
 */
@Data
@Builder
public class BrokerAccountInfo {
    /**
     * Broker account ID
     */
    private String accountId;

    /**
     * Account status (ACTIVE, SUSPENDED, etc.)
     */
    private String status;

    /**
     * Total cash balance
     */
    private BigDecimal cashBalance;

    /**
     * Buying power (cash + margin)
     */
    private BigDecimal buyingPower;

    /**
     * Total portfolio value (cash + holdings)
     */
    private BigDecimal portfolioValue;

    /**
     * Pattern day trader flag
     */
    private Boolean patternDayTrader;

    /**
     * Day trades remaining (if PDT flagged)
     */
    private Integer dayTradesRemaining;

    /**
     * Last updated timestamp
     */
    private LocalDateTime lastUpdated;
}
