package com.waqiti.common.events.wallet;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Wallet Created Event
 * 
 * Published when a new wallet is successfully created for a user.
 * This event triggers welcome bonuses, analytics tracking, and notifications.
 * 
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletCreatedEvent {

    @JsonProperty("eventId")
    private String eventId;

    @JsonProperty("correlationId")
    private String correlationId;

    @JsonProperty("timestamp")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant timestamp;

    @JsonProperty("eventVersion")
    private String eventVersion;

    @JsonProperty("source")
    private String source;

    @JsonProperty("walletId")
    private UUID walletId;

    @JsonProperty("userId")
    private UUID userId;

    @JsonProperty("externalId")
    private String externalId;

    @JsonProperty("walletType")
    private String walletType;

    @JsonProperty("accountType")
    private String accountType;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("status")
    private String status;

    @JsonProperty("balance")
    private BigDecimal balance;

    @JsonProperty("availableBalance")
    private BigDecimal availableBalance;

    @JsonProperty("reservedBalance")
    private BigDecimal reservedBalance;

    @JsonProperty("dailyLimit")
    private BigDecimal dailyLimit;

    @JsonProperty("monthlyLimit")
    private BigDecimal monthlyLimit;

    @JsonProperty("minimumBalance")
    private BigDecimal minimumBalance;

    @JsonProperty("maximumBalance")
    private BigDecimal maximumBalance;

    @JsonProperty("creationSource")
    private String creationSource;

    @JsonProperty("createdAt")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonProperty("createdBy")
    private String createdBy;

    @JsonProperty("isActive")
    private Boolean isActive;

    @JsonProperty("isPrimary")
    private Boolean isPrimary;

    @JsonProperty("isVerified")
    private Boolean isVerified;

    @JsonProperty("allowsDeposits")
    private Boolean allowsDeposits;

    @JsonProperty("allowsWithdrawals")
    private Boolean allowsWithdrawals;

    @JsonProperty("allowsTransfers")
    private Boolean allowsTransfers;

    @JsonProperty("interestRate")
    private BigDecimal interestRate;

    @JsonProperty("rewardMultiplier")
    private BigDecimal rewardMultiplier;

    @JsonProperty("metadata")
    private Map<String, String> metadata;
}