package com.waqiti.common.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Event emitted for multi-currency account operations.
 *
 * @author Waqiti Engineering
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class MultiCurrencyAccountEvent extends FinancialEvent {

    private UUID accountId;
    private List<String> supportedCurrencies;
    private String baseCurrency;
    private Map<String, BigDecimal> balances;  // Currency -> Balance
    private String operationType;  // CURRENCY_ADDED, CURRENCY_REMOVED, CURRENCY_EXCHANGE
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal exchangeRate;
    private BigDecimal exchangeAmount;

    public static MultiCurrencyAccountEvent create(UUID accountId, UUID userId, String operationType) {
        return MultiCurrencyAccountEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType("MULTI_CURRENCY_ACCOUNT")
            .eventCategory("ACCOUNT")
            .accountId(accountId)
            .userId(userId)
            .operationType(operationType)
            .timestamp(Instant.now())
            .aggregateId(accountId)
            .build();
    }
}
