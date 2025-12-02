package com.waqiti.virtualcard.event;

import com.waqiti.virtualcard.domain.CardType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event published when a virtual card is created
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CardCreatedEvent {
    
    private String cardId;
    private String userId;
    private CardType cardType;
    private LocalDateTime timestamp;
    private String eventId;
    private String eventType = "CARD_CREATED";
    private String source = "virtual-card-service";
    
    public CardCreatedEvent(String cardId, String userId, CardType cardType, LocalDateTime timestamp) {
        this.cardId = cardId;
        this.userId = userId;
        this.cardType = cardType;
        this.timestamp = timestamp;
        this.eventId = java.util.UUID.randomUUID().toString();
    }
}