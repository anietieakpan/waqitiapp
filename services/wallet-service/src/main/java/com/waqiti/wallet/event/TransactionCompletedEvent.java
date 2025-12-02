package com.waqiti.wallet.event;
import com.waqiti.common.event.AbstractDomainEvent;
import lombok.Getter;
import java.math.BigDecimal;
import java.util.UUID;

@Getter
public class TransactionCompletedEvent extends AbstractDomainEvent {
    private static final String TOPIC = "transaction-events";

    private final UUID transactionId;
    private final UUID sourceWalletId;
    private final UUID targetWalletId;
    private final UUID sourceUserId;
    private final UUID targetUserId;
    private final BigDecimal amount;
    private final String currency;
    private final String transactionType;

    public TransactionCompletedEvent(UUID transactionId, UUID sourceWalletId, UUID targetWalletId,
                                     UUID sourceUserId, UUID targetUserId, BigDecimal amount,
                                     String currency, String transactionType) {
        super(); // Call the no-args constructor
        this.transactionId = transactionId;
        this.sourceWalletId = sourceWalletId;
        this.targetWalletId = targetWalletId;
        this.sourceUserId = sourceUserId;
        this.targetUserId = targetUserId;
        this.amount = amount;
        this.currency = currency;
        this.transactionType = transactionType;
    }

    @Override
    public String getTopic() {
        return TOPIC;
    }
}