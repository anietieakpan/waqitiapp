package com.waqiti.support.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEvent {
    private ChatEventType eventType;
    private String sessionId;
    private String userId;
    private String agentId;
    private String channel;
    private Long duration;
    private Integer messageCount;
    private Integer satisfactionRating;
    private String fromAgent;
    private String toAgent;
    private Long timestamp;
}

