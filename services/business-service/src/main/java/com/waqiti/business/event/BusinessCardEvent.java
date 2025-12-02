package com.waqiti.business.event;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Business card related events
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class BusinessCardEvent extends BusinessEvent {
    
    private String cardId;
    private String cardNumber;
    private String holderName;
    private String action; // CREATED, ACTIVATED, DEACTIVATED, BLOCKED, LIMIT_UPDATED, TRANSACTION
    private BigDecimal amount;
    private String currency;
    private BigDecimal currentLimit;
    private BigDecimal newLimit;
    private String merchantName;
    private String transactionId;
    private String reason;
    private LocalDateTime expiryDate;
    private String cardStatus;
    
    public BusinessCardEvent() {
        super("BUSINESS_CARD");
    }
    
    public static BusinessCardEvent cardCreated(String businessId, String cardId, String holderName, BigDecimal limit) {
        BusinessCardEvent event = new BusinessCardEvent();
        event.setBusinessId(businessId);
        event.setCardId(cardId);
        event.setHolderName(holderName);
        event.setAction("CREATED");
        event.setCurrentLimit(limit);
        event.setCardStatus("ACTIVE");
        return event;
    }
    
    public static BusinessCardEvent cardTransaction(String businessId, String cardId, String transactionId, 
                                                  BigDecimal amount, String currency, String merchantName) {
        BusinessCardEvent event = new BusinessCardEvent();
        event.setBusinessId(businessId);
        event.setCardId(cardId);
        event.setTransactionId(transactionId);
        event.setAction("TRANSACTION");
        event.setAmount(amount);
        event.setCurrency(currency);
        event.setMerchantName(merchantName);
        return event;
    }
    
    public static BusinessCardEvent cardBlocked(String businessId, String cardId, String reason) {
        BusinessCardEvent event = new BusinessCardEvent();
        event.setBusinessId(businessId);
        event.setCardId(cardId);
        event.setAction("BLOCKED");
        event.setReason(reason);
        event.setCardStatus("BLOCKED");
        return event;
    }
    
    public static BusinessCardEvent limitUpdated(String businessId, String cardId, BigDecimal oldLimit, BigDecimal newLimit) {
        BusinessCardEvent event = new BusinessCardEvent();
        event.setBusinessId(businessId);
        event.setCardId(cardId);
        event.setAction("LIMIT_UPDATED");
        event.setCurrentLimit(oldLimit);
        event.setNewLimit(newLimit);
        return event;
    }
}