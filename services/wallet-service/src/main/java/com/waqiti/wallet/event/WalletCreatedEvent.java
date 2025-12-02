package com.waqiti.wallet.event;
import com.waqiti.common.event.AbstractDomainEvent;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class WalletCreatedEvent extends AbstractDomainEvent {
    private static final String TOPIC = "wallet-events";

    private final UUID walletId;
    private final UUID userId;
    private final String walletType;
    private final String accountType;
    private final String currency;
    private final BigDecimal initialBalance;

    public WalletCreatedEvent(UUID walletId, UUID userId, String walletType,
                              String accountType, String currency, BigDecimal initialBalance) {
        super(); // Call the no-args constructor
        this.walletId = walletId;
        this.userId = userId;
        this.walletType = walletType;
        this.accountType = accountType;
        this.currency = currency;
        this.initialBalance = initialBalance;
    }

    @Override
    public String getTopic() {
        return TOPIC;
    }
}