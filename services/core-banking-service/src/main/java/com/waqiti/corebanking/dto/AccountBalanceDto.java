package com.waqiti.corebanking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Account balance information")
public class AccountBalanceDto {

    @Schema(description = "Account identifier", example = "acc-12345678")
    private String accountId;

    @Schema(description = "Account currency", example = "USD")
    private String currency;

    @Schema(description = "Current total balance", example = "1250.75")
    private BigDecimal currentBalance;

    @Schema(description = "Available balance for new transactions", example = "1150.75")
    private BigDecimal availableBalance;

    @Schema(description = "Pending transaction amount", example = "100.00")
    private BigDecimal pendingBalance;

    @Schema(description = "Reserved balance amount", example = "0.00")
    private BigDecimal reservedBalance;

    @Schema(description = "Credit limit if applicable", example = "1000.00")
    private BigDecimal creditLimit;

    @Schema(description = "Effective balance including credit limit", example = "2250.75")
    private BigDecimal effectiveBalance;

    @Schema(description = "Last transaction ID that affected balance", example = "txn-987654")
    private String lastTransactionId;

    @Schema(description = "Timestamp of last balance update", example = "2024-01-15T15:45:00Z")
    private Instant lastUpdated;

    @Schema(description = "Whether the account is frozen", example = "false")
    private Boolean isFrozen;

    @Schema(description = "Whether balance data is real-time", example = "true")
    private Boolean isRealTime;

    // Helper methods
    public boolean hasAvailableBalance(BigDecimal amount) {
        return availableBalance != null && availableBalance.compareTo(amount) >= 0;
    }

    public boolean hasCreditLimit() {
        return creditLimit != null && creditLimit.compareTo(BigDecimal.ZERO) > 0;
    }

    public BigDecimal calculateEffectiveBalance() {
        BigDecimal effective = currentBalance != null ? currentBalance : BigDecimal.ZERO;
        if (hasCreditLimit()) {
            effective = effective.add(creditLimit);
        }
        return effective;
    }
}