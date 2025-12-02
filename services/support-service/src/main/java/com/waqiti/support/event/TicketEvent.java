package com.waqiti.support.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketEvent {
    private TicketEventType eventType;
    private String ticketId;
    private String userId;
    private String status;
    private String priority;
    private String category;
    private String assignedAgent;
    private Long resolutionTime;
    private Integer satisfactionRating;
    private Long timestamp;
}

