package com.waqiti.support.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalationEvent {
    private String ticketId;
    private String userId;
    private String fromAgent;
    private String toAgent;
    private Integer escalationLevel;
    private String reason;
    private String priority;
    private Long timestamp;
}