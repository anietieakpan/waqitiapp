package com.waqiti.messaging.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Size;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConversationRequest {
    
    @Size(max = 100)
    private String name;
    
    @Size(max = 500)
    private String description;
    
    private String avatarUrl;
    
    private Boolean ephemeralMessagesEnabled;
    
    private Integer defaultEphemeralDuration;
}