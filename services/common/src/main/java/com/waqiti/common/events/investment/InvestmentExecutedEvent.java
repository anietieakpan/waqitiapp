package com.waqiti.common.events.investment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvestmentExecutedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "Event ID is required")
    @JsonProperty("event_id")
    private String eventId;

    @NotBlank(message = "Correlation ID is required")
    @JsonProperty("correlation_id")
    private String correlationId;

    @NotNull(message = "Timestamp is required")
    @JsonProperty("timestamp")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    @JsonProperty("event_version")
    private String eventVersion = "1.0";

    @NotBlank(message = "Event source is required")
    @JsonProperty("source")
    private String source;

    @NotBlank(message = "Order ID is required")
    @JsonProperty("order_id")
    private String orderId;

    @NotBlank(message = "Account ID is required")
    @JsonProperty("account_id")
    private String accountId;

    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    private String userId;

    @NotBlank(message = "Symbol is required")
    @JsonProperty("symbol")
    private String symbol;

    @JsonProperty("company_name")
    private String companyName;

    @NotBlank(message = "Order type is required")
    @JsonProperty("order_type")
    private String orderType;

    @NotBlank(message = "Order side is required")
    @JsonProperty("order_side")
    private String orderSide;

    @NotNull(message = "Quantity is required")
    @JsonProperty("quantity")
    private BigDecimal quantity;

    @NotNull(message = "Executed price is required")
    @JsonProperty("executed_price")
    private BigDecimal executedPrice;

    @NotNull(message = "Total amount is required")
    @JsonProperty("total_amount")
    private BigDecimal totalAmount;

    @JsonProperty("commission")
    private BigDecimal commission;

    @NotBlank(message = "Currency is required")
    @JsonProperty("currency")
    private String currency;

    @NotNull(message = "Executed at timestamp is required")
    @JsonProperty("executed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime executedAt;

    @JsonProperty("asset_type")
    private String assetType;

    @JsonProperty("exchange")
    private String exchange;
}