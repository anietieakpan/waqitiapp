package com.waqiti.support.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    public enum Role { USER, ASSISTANT, SYSTEM, AGENT }
    
    private Role role;
    private String content;
    private LocalDateTime timestamp;
    private String userId;
    private String agentId;
    private Map<String, Object> metadata;
    
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
    
    public static ChatMessage user(String content) {
        return ChatMessage.builder()
                .role(Role.USER)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static ChatMessage assistant(String content) {
        return ChatMessage.builder()
                .role(Role.ASSISTANT)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static ChatMessage agent(String content, String agentId) {
        return ChatMessage.builder()
                .role(Role.AGENT)
                .content(content)
                .agentId(agentId)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    public static ChatMessage system(String content) {
        return ChatMessage.builder()
                .role(Role.SYSTEM)
                .content(content)
                .timestamp(LocalDateTime.now())
                .build();
    }
}